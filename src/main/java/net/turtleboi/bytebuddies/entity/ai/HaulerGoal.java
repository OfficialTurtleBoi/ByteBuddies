package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.TaskType;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;

import static net.turtleboi.bytebuddies.block.custom.DockingStationBlock.OPEN;

public class HaulerGoal extends Goal {
    private final ByteBuddyEntity byteBuddy;

    @Nullable private BlockPos targetPos;
    @Nullable private BlockPos approachPos;
    private record Approach(BlockPos targetPos, Vec3 approachAnchor, double distSq, Path path) {}
    private List<Approach> approachPlans = Collections.emptyList();
    private int anchorIndex = 0;
    @Nullable private Vec3 targetAnchor = null;
    private boolean edgeAnchored = false;

    private Step step = Step.TO_SOURCE;
    private boolean stepInitialized = false;
    private enum Step {
        TO_SOURCE, TO_DEST
    }

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
    private static final double verticalTolerance = 1.66;

    private static final double finalApproachDist = 1.66;
    private static final double microDistMin = 0.15;
    private static final double microDistMax = 0.28;

    @Nullable private BlockPos claimedHaulPos = null;
    @Nullable private BlockPos firePos = null;
    @Nullable private BlockState firePreState = null;
    private static final int claimTimeOut = 120;
    private long nextClaimRenewHaul = 0L;

    private long animationStart = 0L;
    private long animationEnd = 0L;
    private boolean actionStarted = false;

    public HaulerGoal(ByteBuddyEntity byteBuddy) {
        this.byteBuddy = byteBuddy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        byteBuddy.setPathfindingMalus(PathType.WATER, 8.0F);
        byteBuddy.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public boolean canUse() {
        if (currentPhase == GoalPhase.ACTING) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getGameTime() <= animationEnd;
            }
            return true;
        }

        if (byteBuddy.getBuddyRole() != ByteBuddyEntity.BuddyRole.STORAGE) {
            failTask(BotDebug.FailReason.WRONG_ROLE, "role=" + byteBuddy.getBuddyRole());
            return false;
        }

        if (byteBuddy.getDock().isEmpty()) {
            failTask(BotDebug.FailReason.NO_DOCK, "no station bound");
            return false;
        }

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, 1, 128)) {
            return false;
        }

        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (dockBlock == null) return false;

        BlockPos source = dockBlock.getFirstPos();
        BlockPos dest = dockBlock.getSecondPos();
        if (source == null || dest == null) {
            failTask(BotDebug.FailReason.NO_TARGET, "clipboard missing positions");
            return false;
        }

        if (targetPos == null) {
            if (!stepInitialized) {
                step = decideFirstStep();
                stepInitialized = true;
            }
            var plan = findHaulPlan(step == Step.TO_SOURCE ? source : dest);
            if (plan.isEmpty()) return false;

            this.targetPos = plan.get().targetPos();
            this.approachPos = plan.get().approachPos();
            this.targetAnchor = GoalUtil.getEdgeAnchor(this.targetPos, this.approachPos);
            this.edgeAnchored = false;
            resetProgress();
            enterPhase(GoalPhase.MOVING, "approach " + step + " " + approachPos.toShortString());
        }
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

        return GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, 1, 128);
    }

    @Override
    public void stop() {
        clearTimedAnimation();
        releaseClaim();
        targetPos = null;
        approachPos = null;
        targetAnchor = null;
        edgeAnchored = false;
        approachPlans = Collections.emptyList();
        anchorIndex = 0;
        super.stop();
    }

    @Override
    public boolean isInterruptable() { return false; }

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

            GoalUtil.renewClaimIfNeeded(byteBuddy, serverLevel, TaskType.HAUL,
                    claimedHaulPos, targetPos, serverLevel.getGameTime(),
                    5, claimTimeOut,
                    () -> nextClaimRenewHaul,
                    ticks -> nextClaimRenewHaul = ticks);

            DockingStationBlockEntity dock = GoalUtil.dockBlockEntity(byteBuddy);
            if (dock != null) {
                if (!targetStillViable(step, dock, serverLevel, targetPos)) {
                    swapStepAndRetarget(dock);
                    return;
                }
            } else {
                clearTarget();
                return;
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
                        BotDebug.log(byteBuddy, "HAUL: locked anchor");
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
                    double distanceToTarget = byteBuddy.position().distanceTo(targetAnchor);
                    if (distanceToTarget > microDistMax) edgeAnchored = false;
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
            boolean withinReach = buddyPos.distanceToSqr(targetCenter) <= (reachDistanceMin * reachDistanceMin)
                    && Math.abs(buddyPos.y - targetCenter.y) <= verticalTolerance;

            if (withinReach) {
                if (animationEnd > 0 || currentPhase == GoalPhase.ACTING) {
                    byteBuddy.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z, 15.0f, 15.0f);
                } else {
                    if (!GoalUtil.actionReady(serverLevel, nextActionTick)) return;
                    if (!verifyClaimOrAbort(serverLevel, TaskType.HAUL, claimedHaulPos, targetPos)) return;

                    firePos = targetPos;
                    firePreState = byteBuddy.level().getBlockState(targetPos);

                    startTimedAnimation(
                            GoalUtil.toTicks(2.6),
                            GoalUtil.toTicks(1.8),
                            targetPos,
                            firePreState
                    );

                    if (serverLevel.getBlockEntity(firePos) instanceof DockingStationBlockEntity dockBlock) {
                        serverLevel.setBlock(firePos, dockBlock.getBlockState().setValue(OPEN, Boolean.TRUE), Block.UPDATE_ALL);
                    }

                    BotDebug.log(byteBuddy, "HAUL schedule " + step + " now=" + serverLevel.getGameTime());
                }
            }

            if (targetAnchor != null && !edgeAnchored) {
                byteBuddy.getMoveControl().setWantedPosition(
                        targetAnchor.x, targetAnchor.y, targetAnchor.z,
                        byteBuddy.actionSpeedMultiplier());
            }
        }
    }


    private Step decideFirstStep() {
        boolean hasAnyItems = hasAnyItems(byteBuddy.getMainInv());
        return hasAnyItems ? Step.TO_DEST : Step.TO_SOURCE;
    }

    private record HaulPlan(BlockPos targetPos, BlockPos approachPos, Path path) {}
    private Optional<HaulPlan> findHaulPlan(BlockPos blockPos) {
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        Level level = byteBuddy.level();
        if (level instanceof ServerLevel serverLevel) {
            if (dockBlock != null && dockBlock.isReserved(serverLevel, TaskType.HAUL, blockPos)) return Optional.empty();

            var plans = buildApproachPlans(level, blockPos);
            if (plans.isEmpty()) return Optional.empty();

            if (dockBlock != null) {
                boolean targetViable = dockBlock.tryClaim(serverLevel, TaskType.HAUL,
                        blockPos.immutable(), byteBuddy.getUUID(), claimTimeOut);
                if (!targetViable) Optional.empty();
                this.claimedHaulPos = blockPos.immutable();
                this.nextClaimRenewHaul = serverLevel.getGameTime() + 5;
            }

            this.approachPlans = plans;
            this.anchorIndex = 0;
            Approach firstApproach = plans.get(0);
            return Optional.of(new HaulPlan(blockPos, firstApproach.targetPos(), firstApproach.path()));
        }
        return Optional.empty();
    }

    private List<Approach> buildApproachPlans(Level level, BlockPos targetPos) {
        ArrayList<Approach> approachList = new ArrayList<>(4);
        BlockPos[] sides = new BlockPos[]{ targetPos.east(), targetPos.west(), targetPos.south(), targetPos.north() };
        for (BlockPos approachPos : sides) {
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, approachPos)) continue;
            Vec3 edgeAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
            if (edgeAnchor == null) continue;
            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) ? pathNavigation.createPath(approachPos, 0) : null;
            if (path == null) continue;
            double distSq = GoalUtil.hDistSq(byteBuddy.position(), edgeAnchor);
            approachList.add(new Approach(approachPos, edgeAnchor, distSq, path));
        }
        approachList.sort(Comparator.comparingDouble(Approach::distSq));
        return approachList;
    }

    private void planForCurrentStep(DockingStationBlockEntity dockBlock) {
        BlockPos sourcePos = dockBlock.getFirstPos();
        BlockPos destinationPos = dockBlock.getSecondPos();
        if (sourcePos == null || destinationPos == null) {
            enterPhase(GoalPhase.IDLE, "clipboard missing positions");
            return;
        }
        BlockPos targetPos = (step == Step.TO_SOURCE) ? sourcePos : destinationPos;
        var plan = findHaulPlan(targetPos);
        if (plan.isEmpty()) {
            enterPhase(GoalPhase.IDLE, "no approach");
            return;
        }
        this.targetPos = plan.get().targetPos();
        this.approachPos = plan.get().approachPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(this.targetPos, this.approachPos);
        this.edgeAnchored = false;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "retarget " + step + " -> " + approachPos.toShortString());
    }

    private boolean targetStillViable(Step step, DockingStationBlockEntity dockBlock, ServerLevel serverLevel, BlockPos blockPos) {
        if (step == Step.TO_SOURCE) {
            IItemHandler sourceInventory = serverLevel.getCapability(Capabilities.ItemHandler.BLOCK, blockPos, null);
            if (sourceInventory == null) return false;
            if (findExtractableSlot(sourceInventory, dockBlock) < 0) return false;
            return buddyHasRoomForAnyFrom(sourceInventory);
        } else {
            return hasAnyItems(byteBuddy.getMainInv());
        }
    }

    private boolean buddyHasRoomForAnyFrom(IItemHandler sourceInventory) {
        IItemHandler buddyInventory = byteBuddy.getMainInv();
        for (int i = 0; i < sourceInventory.getSlots(); i++) {
            ItemStack stackInSlot = sourceInventory.getStackInSlot(i);
            if (stackInSlot.isEmpty()) continue;
            ItemStack simulatedStack = tryInsertAll(buddyInventory, stackInSlot, true);
            if (simulatedStack.getCount() < stackInSlot.getCount()) return true;
        }
        return false;
    }

    private void swapStepAndRetarget(DockingStationBlockEntity dockBlock) {
        BlockPos sourcePos = dockBlock.getFirstPos();
        BlockPos destinationPos = dockBlock.getSecondPos();
        if (sourcePos == null || destinationPos == null) { clearTarget(); enterPhase(GoalPhase.IDLE, "clipboard invalid"); return; }

        step = (step == Step.TO_SOURCE) ? Step.TO_DEST : Step.TO_SOURCE;
        BlockPos newTarget = (step == Step.TO_SOURCE) ? sourcePos : destinationPos;
        var plan = findHaulPlan(newTarget);
        if (plan.isEmpty()) { clearTarget(); enterPhase(GoalPhase.IDLE, "no approach"); return; }

        targetPos = plan.get().targetPos();
        approachPos = plan.get().approachPos();
        targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
        edgeAnchored = false;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "retarget " + step + " -> " + approachPos.toShortString());
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
        if (!(byteBuddy.getNavigation() instanceof GroundPathNavigation nav)) return;
        Path path = nav.getPath();
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
        DockingStationBlockEntity dock = GoalUtil.dockBlockEntity(byteBuddy);
        if (!(byteBuddy.level() instanceof ServerLevel serverLevel) || dock == null) {
            enterPhase(GoalPhase.IDLE, "seek timeout, no dock");
            return;
        }
        swapStepAndRetarget(dock);
    }

    private boolean repath() {
        if (byteBuddy.getNavigation() instanceof GroundPathNavigation nav) {
            if (repathRetries++ >= 2) return false;
            if (approachPos == null) return false;
            if (byteBuddy.level() instanceof ServerLevel) GoalUtil.releaseCurrentPathIfAny(byteBuddy);
            Path path = nav.createPath(approachPos, 0);
            if (path == null) return false;
            nav.moveTo(path, byteBuddy.actionSpeedMultiplier());
            if (byteBuddy.level() instanceof ServerLevel sl) GoalUtil.reserveCurrentPathIfAny(sl, byteBuddy, 5);
            return true;
        }
        return false;
    }

    private boolean rotateAnchor() {
        if (approachPlans.isEmpty() || targetPos == null) return false;
        if (anchorRotateRetries >= 3) return false;

        final int n = approachPlans.size();
        for (int tries = 0; tries < n; tries++) {
            anchorIndex = (anchorIndex + 1) % n;
            Approach cand = approachPlans.get(anchorIndex);
            BlockPos stand = cand.targetPos();
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, byteBuddy.level(), stand)) continue;

            Vec3 anchor = cand.approachAnchor();
            if (anchor == null) continue;

            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation nav) ? nav.createPath(stand, 0) : null;
            if (path == null) continue;

            approachPos  = stand;
            targetAnchor = anchor;
            edgeAnchored = false;

            if (byteBuddy.level() instanceof ServerLevel) GoalUtil.releaseCurrentPathIfAny(byteBuddy);
            byteBuddy.getNavigation().moveTo(path, byteBuddy.actionSpeedMultiplier());
            if (byteBuddy.level() instanceof ServerLevel sl) GoalUtil.reserveCurrentPathIfAny(sl, byteBuddy, 5);

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

            enterPhase(GoalPhase.ACTING, "animation: HAUL " + step);
        }
    }

    private void tickTimedAnimation() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            long currentTime = serverLevel.getGameTime();
            if (!actionStarted && currentTime >= animationStart && firePos != null && firePreState != null) {
                actionStarted = true;
                BotDebug.log(byteBuddy, "HAUL anim: now=" + currentTime +
                        " start=" + animationStart + " end=" + animationEnd +
                        " fired=" + actionStarted + " firePos=" + (firePos != null));

                performHaul(step, serverLevel, firePos);
                if (serverLevel.getBlockEntity(firePos) instanceof DockingStationBlockEntity dockBlock) {
                    serverLevel.setBlock(firePos, dockBlock.getBlockState().setValue(OPEN, Boolean.FALSE), Block.UPDATE_ALL);
                }
            }

            if (currentPhase == GoalPhase.ACTING && animationEnd > 0 && currentTime >= animationEnd) {
                clearTimedAnimation();
                DockingStationBlockEntity dock = GoalUtil.dockBlockEntity(byteBuddy);
                clearTarget();
                if (dock != null) {
                   planForCurrentStep(dock);

                } else {
                    enterPhase(GoalPhase.IDLE, "dock missing");
                }
            }
        }
    }

    private void clearTimedAnimation() {
        actionStarted = false;
        animationStart = animationEnd = 0L;
        byteBuddy.setWorking(false);
    }

    private boolean verifyClaimOrAbort(ServerLevel serverLevel, TaskType taskType, @Nullable BlockPos claimedPos, @Nullable BlockPos currentTaskPos) {
        BotDebug.log(byteBuddy, "claimedPos null");
        if (claimedPos == null) return false;
        BotDebug.log(byteBuddy, "claimedPos/currentTaskPos mismatch");
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

    private void performHaul(Step step, ServerLevel serverLevel, BlockPos blockPos) {
        if (step == Step.TO_SOURCE) {
            IItemHandler sourceInventory = serverLevel.getCapability(Capabilities.ItemHandler.BLOCK, blockPos, null);
            if (sourceInventory == null) return;

            int sourceSlot = findExtractableSlot(sourceInventory, serverLevel.getBlockEntity(blockPos));
            if (sourceSlot < 0) return;

            ItemStack simulatedExtract = sourceInventory.extractItem(sourceSlot, 64, true);
            if (simulatedExtract.isEmpty()) return;

            ItemStack simulatedRemainder = tryInsertAll(byteBuddy.getMainInv(), simulatedExtract, true);
            int insertableAmount = simulatedExtract.getCount() - simulatedRemainder.getCount();
            if (insertableAmount <= 0) {
                this.step = Step.TO_DEST; return;
            }

            if (!byteBuddy.consumeEnergy(insertableAmount)) return;

            ItemStack actuallyExtracted = sourceInventory.extractItem(sourceSlot, insertableAmount, false);
            if (actuallyExtracted.isEmpty()) return;

            ItemStack itemRemainder = tryInsertAll(byteBuddy.getMainInv(), actuallyExtracted, false);
            int insertedAmount = actuallyExtracted.getCount() - itemRemainder.getCount();
            if (insertedAmount > 0) {
                BotDebug.log(byteBuddy, "HAUL: took " + insertedAmount + "x " + actuallyExtracted.getDisplayName().getString());
                this.step = Step.TO_DEST;
            }

            if (!itemRemainder.isEmpty()) {
                for (int slot = 0; slot < sourceInventory.getSlots() && !itemRemainder.isEmpty(); slot++) {
                    itemRemainder = sourceInventory.insertItem(slot, itemRemainder, false);
                }

                if (!itemRemainder.isEmpty()) {
                    Containers.dropItemStack(serverLevel, blockPos.getX() + 0.5, blockPos.getY() + 1, blockPos.getZ() + 0.5, itemRemainder);
                }
            }
            return;
        }

        IItemHandler targetInventory = serverLevel.getCapability(Capabilities.ItemHandler.BLOCK, blockPos, null);
        if (targetInventory == null) return;

        for (int i = 0; i < byteBuddy.getMainInv().getSlots(); i++) {
            ItemStack stackInSlot = byteBuddy.getMainInv().getStackInSlot(i);
            if (stackInSlot.isEmpty()) continue;

            ItemStack simulatedDeposit = tryInsertAll(targetInventory, stackInSlot, true);
            int movableAmount = stackInSlot.getCount() - simulatedDeposit.getCount();
            if (movableAmount <= 0) continue;

            if (!byteBuddy.consumeEnergy(movableAmount)) continue;
            if (serverLevel.getBlockEntity(blockPos) instanceof DockingStationBlockEntity dockBlock) {
                serverLevel.setBlock(blockPos, dockBlock.getBlockState().setValue(OPEN, Boolean.TRUE), Block.UPDATE_ALL);
            }
            ItemStack toSend = stackInSlot.copyWithCount(movableAmount);
            ItemStack itemRemainder = tryInsertAll(targetInventory, toSend, false);
            int movedAmount = movableAmount - itemRemainder.getCount();
            if (movedAmount > 0) {
                byteBuddy.getMainInv().extractItem(i, movedAmount, false);
                BotDebug.log(byteBuddy, "HAUL: delivered " + movedAmount + "x " + stackInSlot.getDisplayName().getString());
                this.step = Step.TO_SOURCE;
                return;
            } else {
                byteBuddy.getEnergyStorage().receiveEnergy(movableAmount, false);
            }
        }

        this.step = Step.TO_SOURCE;
    }

    private int findExtractableSlot(IItemHandler sourceInventory, BlockEntity blockEntity) {
        if (blockEntity instanceof DockingStationBlockEntity) {
            for (int i = 2; i < sourceInventory.getSlots(); i++) {
                ItemStack itemStack = sourceInventory.getStackInSlot(i);
                if (!itemStack.isEmpty()) return i;
            }
        } else {
            for (int i = 0; i < sourceInventory.getSlots(); i++) {
                ItemStack itemStack = sourceInventory.getStackInSlot(i);
                if (!itemStack.isEmpty()) return i;
            }
        }
        return -1;
    }

    private boolean hasAnyItems(IItemHandler inventory) {
        for (int i = 0; i < inventory.getSlots(); i++) if (!inventory.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    private ItemStack tryInsertAll(IItemHandler destinationInventory, ItemStack itemStack, boolean simulate) {
        ItemStack remainder = itemStack.copy();
        for (int slot = 0; slot < destinationInventory.getSlots(); slot++) {
            remainder = destinationInventory.insertItem(slot, remainder, simulate);
            if (remainder.isEmpty()) break;
        }
        return remainder;
    }

    private void releaseClaim() {
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (claimedHaulPos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.HAUL /* or TaskType.HAUL */, claimedHaulPos, byteBuddy.getUUID());
        }
        claimedHaulPos = null;
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
        if (phase == GoalPhase.ACTING && byteBuddy.level() instanceof ServerLevel) {
            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
        }

        BotDebug.log(byteBuddy, "HAULER: " + phase + (context.isEmpty() ? "" : " -> " + context));
    }

    private void clearTarget() {
        if (byteBuddy.level() instanceof ServerLevel) {
            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
        }
        releaseClaim();
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
        BotDebug.log(byteBuddy, "HAULER cannot start: " + reason + (context.isEmpty() ? "" : " (" + context + ")"));
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

    private void markProgress() { phaseProgressTick = GoalUtil.getCurrentTime(byteBuddy); }

    private boolean stalledFor(int stallTime) {
        return GoalUtil.getCurrentTime(byteBuddy) - phaseProgressTick > stallTime;
    }

    private boolean timedOut(int timeLimit) {
        return GoalUtil.getCurrentTime(byteBuddy) - phaseStartedTick > timeLimit;
    }
}
