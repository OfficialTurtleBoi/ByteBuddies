package net.turtleboi.bytebuddies.entity.ai.farmer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.api.SeedItemProvider;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.*;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;

public class HarvestGoal extends Goal {
    private final ByteBuddyEntity byteBuddy;
    @Nullable private BlockPos targetPos;
    @Nullable private BlockPos approachPos;
    private record Approach(BlockPos targetPos, Vec3 approachAnchor, double distSq, Path path) {}
    private List<Approach> approachPlans = Collections.emptyList();
    private int anchorIndex = 0;
    @Nullable private Vec3 targetAnchor = null;
    private boolean edgeAnchored = false;

    private long nextActionTick = 0L;
    private static final int baseActionCooldown = 20;

    private GoalPhase currentPhase = GoalPhase.IDLE;
    private BotDebug.FailReason lastFail = BotDebug.FailReason.NONE;
    private long phaseStartedTick = 0L;
    private long phaseProgressTick = 0L;
    private int repathRetries = 0;
    private int anchorRotateRetries = 0;
    private int targetReselectRetries = 0;

    private static final int seekingTimout = 20;
    private static final int movingTimeout = 160;
    private static final int actingTimeout = 60;

    private double lastMoveDistSq = Double.POSITIVE_INFINITY;
    private double lastAnchorDistSq = Double.POSITIVE_INFINITY;

    private static final double reachDistanceMin = 0.95;
    private static final double verticalTolerance = 1.25;

    private static final double finalApproachDist = 1.25;
    private static final double microDistMin = 0.08;
    private static final double microDistMax = 0.18;

    @Nullable private BlockPos claimedHarvestPos = null;
    @Nullable private BlockPos firePos = null;
    @Nullable private BlockState firePreState = null;
    private static final int claimTimeOut = 120;
    private long nextClaimRenewHarvest = 0L;

    private long animationStart = 0L;
    private long animationEnd = 0L;
    private boolean actionStarted = false;

    private static final int harvestEnergyCost = 25;

    public HarvestGoal(ByteBuddyEntity byteBuddy) {
        this.byteBuddy = byteBuddy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        byteBuddy.setPathfindingMalus(PathType.WATER, 8.0F);
        byteBuddy.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public boolean canUse() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            if (byteBuddy.harvestOnHold(serverLevel)) {
                return false;
            }
        }

        if (currentPhase == GoalPhase.ACTING) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getGameTime() <= animationEnd;
            }
            return true;
        }

        if (targetPos != null) {
            return true;
        }

        if (byteBuddy.getBuddyRole() != ByteBuddyEntity.BuddyRole.FARMER) {
            failTask(BotDebug.FailReason.WRONG_ROLE, "role=" + byteBuddy.getBuddyRole());
            return false;
        }

        if (byteBuddy.getDock().isEmpty()) {
            failTask(BotDebug.FailReason.NO_DOCK, "no station bound");
            return false;
        }

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, harvestEnergyCost, 1)) {
            return false;
        }

        var targetLock = findHarvestPlan();
        if (targetLock.isEmpty()) {
            return false;
        }

        this.targetPos = targetLock.get().crop();
        this.approachPos = targetLock.get().targetPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(this.targetPos, this.approachPos);
        this.edgeAnchored = false;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "to edge " + approachPos.toShortString());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (currentPhase == GoalPhase.ACTING) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getGameTime() <= animationEnd;
            }
            return true;
        }

        if (targetPos == null) return false;

        return GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, harvestEnergyCost, 1);
    }

    @Override
    public void stop() {
        clearTimedAnimation();
        releaseHarvestClaim();
        targetPos = null;
        approachPos = null;
        targetAnchor = null;
        edgeAnchored = false;
        approachPlans = Collections.emptyList();
        anchorIndex = 0;
        super.stop();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void tick() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            tickTimedAnimation();

            if (targetPos == null || approachPos == null) return;

            Vec3 targetCenter = targetPos.getCenter();
            if (currentPhase == GoalPhase.ACTING) {
                byteBuddy.getLookControl().setLookAt(targetCenter.x, targetPos.getBottomCenter().y, targetCenter.z, 15.0f, 15.0f);
                return;
            }

            GoalUtil.renewClaimIfNeeded(byteBuddy, serverLevel, ByteBuddyEntity.TaskType.HARVEST,
                    claimedHarvestPos, targetPos,
                    serverLevel.getGameTime(),
                    5, claimTimeOut,
                    () -> nextClaimRenewHarvest,
                    ticks -> nextClaimRenewHarvest = ticks);

            BlockState targetState = byteBuddy.level().getBlockState(targetPos);
            if (currentPhase != GoalPhase.ACTING) {
                if (!(targetState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(targetState)) {
                    clearTarget();
                    enterPhase(GoalPhase.IDLE, "target invalid, rescan");
                    return;
                }
            }

            navigatePhases(serverLevel);

            if (targetAnchor != null) {
                double distToTarget = byteBuddy.position().distanceTo(targetAnchor);

                if (distToTarget > finalApproachDist) {
                    if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
                        Path currentPath = pathNavigation.getPath();
                        boolean needsNewPath = currentPath == null || currentPath.isDone() || approachPos == null || !currentPath.getTarget().equals(approachPos);
                        if (needsNewPath) {
                            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                            Path path = (approachPos != null) ? pathNavigation.createPath(approachPos, 0) : null;
                            if (path != null) {
                                pathNavigation.moveTo(path, byteBuddy.actionSpeedMultiplier());
                                GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy,5);
                            } else {
                                byteBuddy.getNavigation().moveTo(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
                            }
                        }
                    } else {
                        byteBuddy.getNavigation().moveTo(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
                    }
                    return;
                }

                if (!edgeAnchored) {
                    GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                    if (distToTarget <= microDistMin) {
                        GoalUtil.lockToAnchor(byteBuddy, targetAnchor);
                        edgeAnchored = true;
                        markProgress();
                        BotDebug.log(byteBuddy, "HARVEST: locked anchor");
                    } else {
                        byteBuddy.getNavigation().stop();
                        byteBuddy.getMoveControl().setWantedPosition(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
                        if (distToTarget + 1.0e-3 < lastAnchorDistSq) {
                            lastAnchorDistSq = distToTarget;
                            markProgress();
                        }
                        BotDebug.log(byteBuddy, String.format("final-targetPos dH=%.3f to edge %s",
                                distToTarget, approachPos.toShortString()));
                    }
                } else {
                    double distSq = Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor));
                    if (distSq > microDistMax) edgeAnchored = false;
                }
            } else {
                if (approachPos != null && byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
                    GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                    Path path = pathNavigation.createPath(approachPos, 0);
                    pathNavigation.moveTo(path, byteBuddy.actionSpeedMultiplier());
                    GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
                    return;
                }
            }

            Vec3 buddyPos = byteBuddy.position();
            boolean withinReach = GoalUtil.hDistSq(buddyPos, targetCenter) <= (reachDistanceMin * reachDistanceMin)
                    && Math.abs(buddyPos.y - targetCenter.y) <= verticalTolerance;

            if (withinReach) {
                if (animationEnd > 0 || currentPhase == GoalPhase.ACTING) {
                    byteBuddy.getLookControl().setLookAt(targetCenter.x, targetPos.getBottomCenter().y, targetCenter.z, 15.0f, 15.0f);
                } else {
                    if (!GoalUtil.actionReady(serverLevel, nextActionTick)) return;
                    if (!verifyClaimOrAbort(serverLevel, ByteBuddyEntity.TaskType.HARVEST, claimedHarvestPos, targetPos))return;

                    firePos = targetPos;
                    firePreState = targetState;

                    startTimedAnimation(
                            GoalUtil.toTicks(2.6),
                            GoalUtil.toTicks(1.8),
                            targetPos,
                            firePreState
                    );

                    BotDebug.log(byteBuddy, "HARVEST schedule: now=" + serverLevel.getGameTime() +
                            " start=" + (serverLevel.getGameTime() + GoalUtil.toTicks(1.8)) +
                            " end=" + (serverLevel.getGameTime() + GoalUtil.toTicks(2.6)) +
                            " firePos=" + firePos + " preState=" + firePreState.getBlock().getName().getString());
                    return;
                }
            }

            if (targetAnchor != null && !edgeAnchored) {
                byteBuddy.getMoveControl().setWantedPosition(
                        targetAnchor.x, targetAnchor.y, targetAnchor.z,
                        byteBuddy.actionSpeedMultiplier());
            }
        }
    }

    private void navigatePhases(ServerLevel serverLevel) {
        switch (currentPhase) {
            case MOVING -> handleMoving(serverLevel);
            case ACTING -> handleActing();
            case SEEKING -> handleSeeking();
            default -> {}
        }
    }

    private void handleMoving(ServerLevel serverLevel) {
        if (isWithinFinalApproach()) {
            renewPathAheadIfNeeded(serverLevel, 5);
            markProgress();
            return;
        }

        updateApproachProgress();
        renewPathAheadIfNeeded(serverLevel, 5);

        if (stalledFor(movingTimeout / 10)) {
            if (tryRecoverFromStall(serverLevel)) {
                markProgress();
            } else {
                GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                clearTarget();
                enterPhase(GoalPhase.IDLE, "MOVING stalled, rescan");
            }
            return;
        }

        if (timedOut(movingTimeout)) {
            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
            clearTarget();
            enterPhase(GoalPhase.IDLE, "MOVING timeout, rescan");
        }
    }

    private boolean isWithinFinalApproach() {
        if (edgeAnchored) return true;
        if (targetAnchor == null) return false;
        return Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor)) <= finalApproachDist;
    }

    private void updateApproachProgress() {
        if (approachPos != null) {
            final Vec3 progressToTarget = (targetAnchor != null) ? targetAnchor : approachPos.getCenter();
            final double distSq = GoalUtil.hDistSq(byteBuddy.position(), progressToTarget);
            if (distSq + 1.0e-3 < lastMoveDistSq) {
                lastMoveDistSq = distSq;
                markProgress();
            }
        }
    }

    private void renewPathAheadIfNeeded(ServerLevel serverLevel, int lookahead) {
        if (!(byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation)) return;
        Path path = pathNavigation.getPath();
        if (path == null) return;
        if ((serverLevel.getGameTime() % 5L) != 0L) return;
        byteBuddy.renewPathAhead(serverLevel, path, lookahead);
    }

    private boolean tryRecoverFromStall(ServerLevel serverLevel) {
        GoalUtil.releaseCurrentPathIfAny(byteBuddy);

        if (repath()) {
            GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
            BotDebug.log(byteBuddy, "MOVING: repath");
            return true;
        }

        if (rotateAnchor()) {
            GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
            BotDebug.log(byteBuddy, "MOVING: rotate targetPos side");
            return true;
        }

        return false;
    }

    private void handleActing() {
        if (animationEnd > 0) {
            markProgress();
            return;
        }
        if (timedOut(actingTimeout)) {
            BotDebug.log(byteBuddy, "ACTING timeout; abort");
            clearTarget();
            enterPhase(GoalPhase.IDLE, "abort act");
        }
    }

    private void handleSeeking() {
        if (!timedOut(seekingTimout)) return;

        if (targetReselectRetries++ < 2) {
            var plan = findHarvestPlan();
            if (plan.isPresent()) {
                targetPos = plan.get().crop();
                approachPos = plan.get().targetPos();
                targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
                resetProgress();
                enterPhase(GoalPhase.MOVING, "retry seek -> moving");
            } else {
                enterPhase(GoalPhase.IDLE, "seek timeout, no target");
            }
        } else {
            enterPhase(GoalPhase.IDLE, "seek timeout (exhausted)");
        }
    }

    private record HarvestPlan(BlockPos crop, BlockPos targetPos, Path path) {}
    private Optional<HarvestPlan> findHarvestPlan() {
        BlockPos dockPos = byteBuddy.getDock().orElse(null);
        if (dockPos == null) return Optional.empty();
        int effectiveRadius = byteBuddy.effectiveRadius();
        Level level = byteBuddy.level();
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);

        if (level instanceof ServerLevel serverLevel) {
            BlockPos.MutableBlockPos potentialCandidate = new BlockPos.MutableBlockPos();
            for (int y = -1; y <= 2; y++) {
                for (int x = -effectiveRadius; x <= effectiveRadius; x++) {
                    for (int z = -effectiveRadius; z <= effectiveRadius; z++) {
                        potentialCandidate.set(dockPos.getX() + x, dockPos.getY() + y, dockPos.getZ() + z);
                        BlockState blockState = level.getBlockState(potentialCandidate);
                        if (!(blockState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(blockState)) continue;
                        if (dockBlock != null && dockBlock.isReserved(serverLevel, ByteBuddyEntity.TaskType.HARVEST, potentialCandidate)) continue;

                        var approachPlans = buildApproachPlans(level, potentialCandidate.immutable());
                        if (approachPlans.isEmpty()) continue;

                        if (dockBlock != null) {
                            boolean targetViable = dockBlock.tryClaim(serverLevel, ByteBuddyEntity.TaskType.HARVEST,
                                    potentialCandidate.immutable(), byteBuddy.getUUID(), claimTimeOut);
                            if (!targetViable) continue;
                            this.claimedHarvestPos = potentialCandidate.immutable();
                            this.nextClaimRenewHarvest = serverLevel.getGameTime() + 5;
                        }

                        this.approachPlans = approachPlans;
                        this.anchorIndex = 0;
                        Approach approach = approachPlans.get(0);
                        this.approachPos = approach.targetPos();
                        return Optional.of(new HarvestPlan(potentialCandidate.immutable(), approach.targetPos(), approach.path()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private List<Approach> buildApproachPlans(Level level, BlockPos cropPos) {
        ArrayList<Approach> approachList = new ArrayList<>(4);
        BlockPos[] targetSides = new BlockPos[]{ cropPos.east(), cropPos.west(), cropPos.south(), cropPos.north() };

        for (BlockPos targetPos : targetSides) {
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, targetPos)) continue;

            Vec3 cropEdgeAnchor = GoalUtil.getEdgeAnchor(cropPos, targetPos);
            if (cropEdgeAnchor == null) continue;

            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) ? pathNavigation.createPath(targetPos, 0) : null;
            if (path == null) continue;

            double distSq = GoalUtil.hDistSq(byteBuddy.position(), cropEdgeAnchor);
            approachList.add(new Approach(targetPos, cropEdgeAnchor, distSq, path));
        }

        approachList.sort(Comparator.comparingDouble(Approach::distSq));
        return approachList;
    }

    private void performHarvest(BlockPos cropPos, BlockState blockState) {
        Level level = byteBuddy.level();
        if (!(blockState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(blockState)) {
            BotDebug.log(byteBuddy, "HARVEST: no longer ripe at " + (cropPos != null ? cropPos.toShortString() : "null"));
            return;
        }

        if (!byteBuddy.consumeEnergy(harvestEnergyCost)) {
            BotDebug.log(byteBuddy, "HARVEST: out of energy");
            return;
        }

        List<ItemStack> drops = Block.getDrops(blockState, (ServerLevel) level, cropPos, null, byteBuddy, ItemStack.EMPTY);
        try {
            FloppyDiskItem.DiskHooks.applyPrimaryYieldBonus(drops, blockState.getBlock(), byteBuddy, byteBuddy.yieldBonusChance());
        } catch (Throwable ignored) { }


        level.destroyBlock(cropPos, false);
        Vec3 cropCenter = cropPos.getCenter();
        int inserted = 0, dropped = 0;
        for (ItemStack stack : drops) {
            ItemStack remainder = InventoryUtil.mergeInto(byteBuddy.getMainInv(), stack);
            if (!remainder.isEmpty()) {
                Containers.dropItemStack(level, cropCenter.x, cropCenter.y, cropCenter.z, remainder);
                dropped += remainder.getCount();
            } else {
                inserted += stack.getCount();
            }
        }

        byteBuddy.onTaskSuccess(TaskType.HARVEST, cropPos);
        BotDebug.log(byteBuddy, "HARVEST at " + cropPos.toShortString() + " inserted=" + inserted + " dropped=" + dropped);
        BotDebug.mark(level, cropPos);

        if (canPlantAfterHarvest) {
            tryEnqueueImmediatePlantFromPreState(cropPos, blockState);
            if (level instanceof ServerLevel serverLevel) {
                byteBuddy.holdHarvestForReplant(serverLevel, 40);
            }
        }
    }

    private boolean canPlantAfterHarvest = true;
    public HarvestGoal enablePostHarvestPlant(boolean enabled) {
        this.canPlantAfterHarvest = enabled;
        return this;
    }

    private void tryEnqueueImmediatePlantFromPreState(BlockPos plantPos, BlockState preBreakState) {
        Level level = byteBuddy.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        var plantBlock = preBreakState.getBlock();
        if (!(plantBlock instanceof SeedItemProvider seedItemProvider)) return;

        Item seedItem = seedItemProvider.bytebuddies$getSeedItem().asItem();
        BlockState plantState = plantBlock.defaultBlockState();

        if (!GoalUtil.canPlantAt(level, plantPos, plantState)) return;

        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (dockBlock == null) return;

        if (dockBlock.tryClaim(serverLevel, ByteBuddyEntity.TaskType.PLANT, plantPos, byteBuddy.getUUID(), 120)) {
            byteBuddy.requestImmediatePlant(plantPos, plantState, seedItem);
        }
    }

    private boolean repath() {
        if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
            if (repathRetries++ >= 2) return false;
            if (approachPos == null) return false;

            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                GoalUtil.releaseCurrentPathIfAny(byteBuddy);
            }

            Path path = pathNavigation.createPath(approachPos, 0);
            if (path == null) return false;

            pathNavigation.moveTo(path, byteBuddy.actionSpeedMultiplier());
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
            }
            return true;
        }
        return false;
    }


    private boolean rotateAnchor() {
        if (approachPlans.isEmpty() || targetPos == null) return false;
        if (anchorRotateRetries >= 3) return false;

        final int approaches = approachPlans.size();
        for (int tries = 0; tries < approaches; tries++) {
            anchorIndex = (anchorIndex + 1) % approaches;
            Approach approachCandidates = approachPlans.get(anchorIndex);
            BlockPos standPos = approachCandidates.targetPos();
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, byteBuddy.level(), standPos)) {
                continue;
            }

            Vec3 anchor = approachCandidates.approachAnchor();
            if (anchor == null) {
                continue;
            }

            Path path = null;
            if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
                path = pathNavigation.createPath(standPos, 0);
            }
            if (path == null) {
                continue;
            }

            approachPos  = standPos;
            targetAnchor = anchor;
            edgeAnchored = false;

            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                GoalUtil.releaseCurrentPathIfAny(byteBuddy);
            }

            byteBuddy.getNavigation().moveTo(path, byteBuddy.actionSpeedMultiplier());

            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
            }

            anchorRotateRetries++;
            markProgress();
            lastMoveDistSq = Double.POSITIVE_INFINITY;
            lastAnchorDistSq = Double.POSITIVE_INFINITY;
            return true;
        }
        return false;
    }

    private void startTimedAnimation(int totalTicks, int startTick, @Nullable BlockPos fireAt, @Nullable BlockState preState) {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            byteBuddy.getNavigation().stop();
            Vec3 deltaMovement = byteBuddy.getDeltaMovement();
            byteBuddy.setDeltaMovement(
                    deltaMovement.x * 0.1,
                    deltaMovement.y * 0.1,
                    deltaMovement.z * 0.1
            );

            byteBuddy.setWorking(true);

            this.actionStarted = false;
            long currentTime = serverLevel.getGameTime();
            this.animationStart = currentTime + Math.max(0, startTick);
            this.animationEnd = currentTime + Math.max(1, totalTicks);

            this.firePos = fireAt;
            this.firePreState = preState;

            float speedMultiplier = Math.max(0.25f, byteBuddy.actionSpeedMultiplier());
            this.nextActionTick = currentTime + Math.max(4, Math.round(baseActionCooldown / speedMultiplier));

            enterPhase(GoalPhase.ACTING, "animation: HARVEST fire@" + startTick + " end@" + totalTicks);
        }
    }

    private void tickTimedAnimation() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            long currentTime = serverLevel.getGameTime();
            if (!actionStarted && currentTime >= animationStart && firePos != null && firePreState != null) {
                actionStarted = true;
                BotDebug.log(byteBuddy, "HARVEST anim: now=" + currentTime +
                        " start=" + animationStart + " end=" + animationEnd +
                        " fired=" + actionStarted + " firePos=" + (firePos != null));

                performHarvest(firePos, firePreState);
            }

            if (currentPhase == GoalPhase.ACTING && animationEnd > 0 && currentTime >= animationEnd) {
                clearTimedAnimation();
                clearTarget();
                enterPhase(GoalPhase.IDLE, "HARVEST: complete");
            }
        }
    }

    private void clearTimedAnimation() {
        actionStarted = false;
        animationStart = animationEnd = 0L;
        byteBuddy.setWorking(false);
    }

    private boolean verifyClaimOrAbort(ServerLevel serverLevel, TaskType taskType, @Nullable BlockPos claimedPos, @Nullable BlockPos currentTaskPos) {
        if (claimedPos == null) return false;
        if (currentTaskPos != null && !currentTaskPos.equals(claimedPos)) return false;

        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (dockBlock == null) return false;

        boolean buddyReserved = dockBlock.isReservedBy(serverLevel, taskType, claimedPos, byteBuddy.getUUID());
        if (!buddyReserved) {
            BotDebug.log(byteBuddy, "lost " + taskType + " claim at " + claimedPos.toShortString() + " — aborting");
            clearTarget();

            enterPhase(GoalPhase.IDLE, "claim lost; rescan");
        }
        return buddyReserved;
    }

    private void releaseHarvestClaim() {
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (claimedHarvestPos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.HARVEST, claimedHarvestPos, byteBuddy.getUUID());
        }
        claimedHarvestPos = null;
    }

    private void enterPhase(GoalPhase phase, String context) {
        currentPhase = phase; lastFail = BotDebug.FailReason.NONE;
        phaseStartedTick = phaseProgressTick = GoalUtil.getCurrentTime(byteBuddy);

        if (phase == GoalPhase.MOVING) {
            repathRetries = 0;
            anchorRotateRetries = 0;
            lastMoveDistSq = Double.POSITIVE_INFINITY;
            lastAnchorDistSq = Double.POSITIVE_INFINITY;
        }

        if (phase == GoalPhase.SEEKING) {
            repathRetries = 0;
            anchorRotateRetries = 0;
            targetReselectRetries = 0;
        }

        if (phase == GoalPhase.ACTING && byteBuddy.level() instanceof ServerLevel serverLevel) {
            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
        }

        BotDebug.log(byteBuddy, "FARMER: " + phase + (context.isEmpty() ? "" : " -> " + context));
    }

    private void clearTarget() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
        }
        releaseHarvestClaim();
        targetPos = null;
        approachPos = null;
        targetAnchor = null;
        edgeAnchored = false;
        firePos = null;
        firePreState = null;
        approachPlans = Collections.emptyList();
        anchorIndex = 0;
        lastMoveDistSq = Double.POSITIVE_INFINITY;
        lastAnchorDistSq = Double.POSITIVE_INFINITY;
    }

    private void failTask(BotDebug.FailReason reason, String context) {
        lastFail = reason;
        currentPhase = GoalPhase.IDLE;
        BotDebug.log(byteBuddy, "FARMER cannot start: " + reason + (context.isEmpty() ? "" : " (" + context + ")"));
        resetProgress();
        stop();
    }

    private void resetProgress() {
        lastMoveDistSq = Double.POSITIVE_INFINITY;
        lastAnchorDistSq = Double.POSITIVE_INFINITY;
        repathRetries = 0;
        anchorRotateRetries = 0;
        targetReselectRetries = 0;
        phaseStartedTick = phaseProgressTick = GoalUtil.getCurrentTime(byteBuddy);
    }

    private void markProgress() {
        phaseProgressTick = GoalUtil.getCurrentTime(byteBuddy);
    }

    private boolean stalledFor(int stallTime) {
        return GoalUtil.getCurrentTime(byteBuddy) - phaseProgressTick > stallTime;
    }

    private boolean timedOut(int timeLimit) {
        return GoalUtil.getCurrentTime(byteBuddy) - phaseStartedTick > timeLimit;
    }
}
