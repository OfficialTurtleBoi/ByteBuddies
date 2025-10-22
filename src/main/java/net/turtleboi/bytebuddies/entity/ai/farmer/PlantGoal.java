package net.turtleboi.bytebuddies.entity.ai.farmer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.*;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;

import static net.turtleboi.bytebuddies.util.GoalUtil.dockBlockEntity;

public class PlantGoal extends Goal {
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

    @Nullable private BlockPos claimedPlantPos = null;
    @Nullable private BlockState claimedPlantState = null;
    @Nullable private Item claimedPlantSeed = null;
    @Nullable private BlockPos firePos = null;
    @Nullable private BlockState firePreState = null;
    private static final int claimTimeOut = 120;
    private long nextClaimRenewPlant = 0L;

    private long animationStart = 0L;
    private long animationEnd = 0L;
    private boolean actionStarted = false;

    private static final int plantEnergyCost = 25;

    public PlantGoal(ByteBuddyEntity byteBuddy) {
        this.byteBuddy = byteBuddy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        byteBuddy.setPathfindingMalus(PathType.WATER, 8.0F);
        byteBuddy.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public boolean canUse() {
        if (currentPhase == GoalPhase.ACTING) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getGameTime() < animationEnd;
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

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, plantEnergyCost, 1)) {
            return false;
        }

        PlantRequest plantRequest = byteBuddy.pollPlantRequest();
        PlantPlan plantPlan = null;
        if (plantRequest != null) {
            plantPlan = findPlantPlan(plantRequest.blockPos, plantRequest.seedItem, plantRequest.blockState, 0);
            if (plantPlan == null) {
                byteBuddy.requestImmediatePlant(plantRequest.blockPos, plantRequest.blockState, plantRequest.seedItem);
                return false;
            }
        }

        if (plantPlan == null) {
            plantPlan = findPlantPlan(null, null, null, byteBuddy.effectiveRadius());
        }

        if (plantPlan != null) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
                if (dockBlock != null) {
                    if (claimedPlantPos == null || !claimedPlantPos.equals(plantPlan.plantPos())) {
                        if (!dockBlock.tryClaim(serverLevel, ByteBuddyEntity.TaskType.PLANT, plantPlan.plantPos(), byteBuddy.getUUID(), claimTimeOut)) {
                            return false;
                        }
                        claimedPlantPos = plantPlan.plantPos();
                        nextClaimRenewPlant = serverLevel.getGameTime() + 5;
                    }
                }
            }

            claimedPlantSeed = plantPlan.seedItem();
            claimedPlantState = plantPlan.plantState();

            targetPos = plantPlan.plantPos();
            approachPos = plantPlan.standPos();
            targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
            edgeAnchored = false;
            resetProgress();
            enterPhase(GoalPhase.MOVING, "to plant site " + targetPos.toShortString());
            return true;
        } else {
            return false;
        }
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

        return GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, plantEnergyCost, 1);
    }

    @Override
    public void stop() {
        clearTimedAnimation();
        releasePlantClaim();
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

            Vec3 cropCenter = targetPos.getCenter();
            if (currentPhase == GoalPhase.ACTING) {
                byteBuddy.getLookControl().setLookAt(cropCenter.x, targetPos.getBottomCenter().y, cropCenter.z, 15.0f, 15.0f);
                return;
            }

            GoalUtil.renewClaimIfNeeded(byteBuddy, serverLevel, ByteBuddyEntity.TaskType.PLANT,
                    claimedPlantPos, targetPos,
                    serverLevel.getGameTime(),
                    5, claimTimeOut,
                    () -> nextClaimRenewPlant,
                    ticks -> nextClaimRenewPlant = ticks);

            BlockState plantBlockState = byteBuddy.level().getBlockState(targetPos);
            BlockState desiredPlant = (claimedPlantState != null) ? claimedPlantState : (claimedPlantSeed != null ? seedToPlantState(claimedPlantSeed) : null);

            if (currentPhase != GoalPhase.ACTING) {
                if (desiredPlant == null || !GoalUtil.canPlantAt(byteBuddy.level(), targetPos, desiredPlant)) {
                    clearTarget();
                    enterPhase(GoalPhase.IDLE, "plant site invalid, rescan");
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
                        BotDebug.log(byteBuddy, "PLANT: locked anchor");
                    } else {
                        byteBuddy.getNavigation().stop();
                        byteBuddy.getMoveControl().setWantedPosition(
                                targetAnchor.x, targetAnchor.y, targetAnchor.z,
                                byteBuddy.actionSpeedMultiplier());
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
            boolean withinReachPlant = GoalUtil.hDistSq(buddyPos, cropCenter) <= (reachDistanceMin * reachDistanceMin)
                    && Math.abs(buddyPos.y - cropCenter.y) <= verticalTolerance;

            if (currentPhase != GoalPhase.ACTING) {
                if (desiredPlant == null || !GoalUtil.canPlantAt(byteBuddy.level(), targetPos, desiredPlant)) {
                    clearTarget();
                    enterPhase(GoalPhase.IDLE, "plant site invalid, rescan");
                    return;
                }
            }

            if (withinReachPlant) {
                if (animationEnd > 0 || currentPhase == GoalPhase.ACTING) {
                    byteBuddy.getLookControl().setLookAt(cropCenter.x, targetPos.getBottomCenter().y, cropCenter.z, 15.0f, 15.0f);
                } else {
                    if (!GoalUtil.actionReady(serverLevel, nextActionTick)) return;
                    if (!verifyClaimOrAbort(serverLevel, ByteBuddyEntity.TaskType.PLANT, claimedPlantPos, targetPos))
                        return;

                    firePos = targetPos;
                    firePreState = plantBlockState;

                    int totalTicks = GoalUtil.toTicks(2.0);
                    int startTicks = GoalUtil.toTicks(0.4);

                    startTimedAnimation(
                            totalTicks,
                            startTicks,
                            targetPos,
                            plantBlockState
                    );

                    BotDebug.log(byteBuddy, "PLANT schedule: now=" + serverLevel.getGameTime() +
                            " start=" + (serverLevel.getGameTime() + startTicks) +
                            " end=" + (serverLevel.getGameTime() + totalTicks) +
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
            case SEEKING -> handleSeeking(serverLevel);
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

    private void handleSeeking(ServerLevel serverLevel) {
        if (!timedOut(seekingTimout)) return;

        if (targetReselectRetries++ < 2) {
            PlantPlan plantPlan = findPlantPlan(null, null, null, byteBuddy.effectiveRadius());
            if (plantPlan != null) {
                DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
                if (dockBlock != null && !dockBlock.tryClaim(serverLevel, ByteBuddyEntity.TaskType.PLANT,
                        plantPlan.plantPos(), byteBuddy.getUUID(), claimTimeOut)) {
                    enterPhase(GoalPhase.SEEKING, "seek retry (claim failed)");
                    return;
                }
                claimedPlantPos = plantPlan.plantPos();
                nextClaimRenewPlant = serverLevel.getGameTime() + 5;

                claimedPlantSeed = plantPlan.seedItem();
                claimedPlantState = plantPlan.plantState();

                targetPos = plantPlan.plantPos();
                approachPos = plantPlan.standPos();
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

    private record PlantPlan(BlockPos plantPos, Item seedItem, BlockState plantState, BlockPos standPos, @Nullable Path path) {}
    private @Nullable PlantPlan findPlantPlan(@Nullable BlockPos plantPos, @Nullable Item plantSeed, @Nullable BlockState prefPlantState, int effectiveRadius) {
        BlockPos dockBlockPos = byteBuddy.getDock().orElse(null);
        if (dockBlockPos == null) return null;

        Level level = byteBuddy.level();
        if (level instanceof ServerLevel serverLevel) {
            DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
            if (plantPos != null && prefPlantState != null) {
                if (dockBlock != null && (claimedPlantPos == null || !claimedPlantPos.equals(plantPos))) {
                    if (!dockBlock.tryClaim(serverLevel, ByteBuddyEntity.TaskType.PLANT, plantPos, byteBuddy.getUUID(), claimTimeOut)) {
                        return null;
                    }
                    claimedPlantPos = plantPos;
                    nextClaimRenewPlant = serverLevel.getGameTime() + 5;
                }

                if (!GoalUtil.canPlantAt(level, plantPos, prefPlantState)) return null;
                BlockPos[] sides = new BlockPos[]{plantPos.east(), plantPos.west(), plantPos.south(), plantPos.north()};
                BlockPos bestStand = null;
                Path bestPath = null;
                double bestDist = Double.POSITIVE_INFINITY;

                if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
                    for (BlockPos side : sides) {
                        if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, side)) continue;
                        Path path = pathNavigation.createPath(side, 0);
                        if (path == null) continue;
                        double horizontalDist = GoalUtil.hDistSq(byteBuddy.position(), side.getCenter());
                        if (horizontalDist < bestDist) {
                            bestDist = horizontalDist;
                            bestStand = side;
                            bestPath = path;
                        }
                    }
                }
                if (bestStand == null) {
                    bestStand = plantPos;
                    bestPath = null;
                }

                Item useSeed = (plantSeed != null) ? plantSeed : null;
                return new PlantPlan(plantPos.immutable(), useSeed, prefPlantState, bestStand, bestPath);
            }

            var inventory = byteBuddy.getMainInv();
            ArrayList<Item> seeds = new ArrayList<>();
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stackInSlot = inventory.getStackInSlot(i);
                if (stackInSlot.isEmpty()) continue;
                Item itemInSlot = stackInSlot.getItem();
                BlockState seedCandidate = seedToPlantState(itemInSlot);
                if (seedCandidate != null) seeds.add(itemInSlot);
            }
            if (seeds.isEmpty()) return null;

            BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();
            for (int y = -1; y <= 2; y++) {
                for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
                    for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                        origin.set(dockBlockPos.getX() + dx, dockBlockPos.getY() + y, dockBlockPos.getZ() + dz);

                        if (dockBlock != null && dockBlock.isReserved(serverLevel, ByteBuddyEntity.TaskType.PLANT, origin))
                            continue;

                        for (Item seed : seeds) {
                            BlockState plantState = seedToPlantState(seed);
                            if (plantState == null) continue;
                            if (!GoalUtil.canPlantAt(level, origin, plantState)) continue;

                            BlockPos[] sides = new BlockPos[]{origin.east(), origin.west(), origin.south(), origin.north()};
                            BlockPos bestStand = null;
                            Path bestPath = null;
                            double bestDist = Double.POSITIVE_INFINITY;

                            if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
                                for (BlockPos side : sides) {
                                    if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, side)) continue;
                                    Path path = pathNavigation.createPath(side, 0);
                                    if (path == null) continue;
                                    double horizontalDist = GoalUtil.hDistSq(byteBuddy.position(), side.getCenter());
                                    if (horizontalDist < bestDist) {
                                        bestDist = horizontalDist;
                                        bestStand = side;
                                        bestPath = path;
                                    }
                                }
                            }
                            if (bestStand == null) {
                                bestStand = origin;
                                bestPath = null;
                            }

                            return new PlantPlan(origin.immutable(), seed, plantState, bestStand, bestPath);
                        }
                    }
                }
            }
        }
        return null;
    }

    private void performPlant(BlockPos blockPos) {
        Level level = byteBuddy.level();
        if (claimedPlantState == null || claimedPlantSeed == null) {
            BotDebug.log(byteBuddy, "no queued plant info at " + blockPos.toShortString());
            failTask(BotDebug.FailReason.NO_TARGET, "no queued plant info at " + blockPos.toShortString());
            return;
        }

        ItemStack seedStack = InventoryUtil.findItem(byteBuddy.getMainInv(), claimedPlantSeed);
        if (seedStack.isEmpty()) {
            BotDebug.log(byteBuddy, "no seed item (" + claimedPlantSeed + ") for replant at " + blockPos.toShortString());
            failTask(BotDebug.FailReason.NO_TARGET, "no seed item (" + claimedPlantSeed + ") for replant at " + blockPos.toShortString());
            return;
        }

        BlockState blockBelow = level.getBlockState(blockPos.below());
        TriState canSustainPlant = blockBelow.canSustainPlant(level, blockPos.below(), Direction.UP, claimedPlantState);
        boolean canPlantHere = canSustainPlant.isDefault() ? claimedPlantState.canSurvive(level, blockPos) : canSustainPlant.isTrue();
        if (!canPlantHere) {
            BotDebug.log(byteBuddy, "cannot plant here (plantPos/conditions) at " + blockPos.toShortString());
            failTask(BotDebug.FailReason.NO_TARGET, "cannot plant here (plantPos/conditions) at " + blockPos.toShortString());
            return;
        }

        BlockState aboveSoil = level.getBlockState(blockPos);
        boolean soilPlantable = aboveSoil.isAir() || aboveSoil.getCollisionShape(level, blockPos).isEmpty();
        if (!soilPlantable) {
            BotDebug.log(byteBuddy, "space not clear for plant at " + blockPos.toShortString());
            failTask(BotDebug.FailReason.NO_TARGET, "space not clear for plant at " + blockPos.toShortString());
            return;
        }

        if(GoalUtil.canPlantAt(level, blockPos, claimedPlantState)) {
            if (!byteBuddy.consumeEnergy(plantEnergyCost)) {
                failTask(BotDebug.FailReason.OUT_OF_ENERGY, "need=" + plantEnergyCost);
                releasePlantClaim();
                return;
            }

            level.playSound(
                    null, blockPos,
                    SoundEvents.CROP_PLANTED,
                    SoundSource.BLOCKS,
                    0.8f + (level.random.nextFloat() * 0.4f),
                    0.9f + (level.random.nextFloat() * 0.2f)
            );

            if (level instanceof ServerLevel serverLevel) {
                int particlePoints = 12;
                for (int i = 0; i < particlePoints; i++) {
                    double particleInterval = (Math.PI * 2 * i) / particlePoints;
                    serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(claimedPlantSeed)),
                            blockPos.getX() + 0.5 + Math.cos(particleInterval) * 0.5,
                            blockPos.getY() + 0.125,
                            blockPos.getZ() + 0.5 + Math.sin(particleInterval) * 0.5,
                            1, 0, 0, 0, 0.05);
                }
            }

            level.setBlock(blockPos, claimedPlantState, 3);
            seedStack.shrink(1);

            byteBuddy.onTaskSuccess(ByteBuddyEntity.TaskType.PLANT, blockPos);
            releasePlantClaim();

            claimedPlantState = null;
            claimedPlantSeed = null;
            claimedPlantPos = null;
            return;
        }

        failTask(BotDebug.FailReason.NO_TARGET, "cannot plant here (plantPos/conditions) at " + blockPos.toShortString());
    }

    private static @Nullable BlockState seedToPlantState(Item seedItem) {
        if (seedItem instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof CropBlock) {
                return block.defaultBlockState();
            }

            if (block instanceof BushBlock) {
                return block.defaultBlockState();
            }
        }
        return null;
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

            byteBuddy.setSlamming(true);

            this.actionStarted = false;
            long currentTime = serverLevel.getGameTime();
            this.animationStart = currentTime + Math.max(0, startTick);
            this.animationEnd   = currentTime + Math.max(1, totalTicks);

            this.firePos = fireAt;
            this.firePreState = preState;

            float speedMul = Math.max(0.25f, byteBuddy.actionSpeedMultiplier());
            this.nextActionTick = currentTime + Math.max(4, Math.round(baseActionCooldown / speedMul));

            enterPhase(GoalPhase.ACTING, "animation: PLANT fire@" + startTick + " end@" + totalTicks);
        }
    }

    private void tickTimedAnimation() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            long currentTime = serverLevel.getGameTime();
            if (!actionStarted && currentTime >= animationStart && firePos != null && firePreState != null) {
                actionStarted = true;
                BotDebug.log(byteBuddy, "PLANT anim: now=" + currentTime +
                        " start=" + animationStart + " end=" + animationEnd +
                        " fired=" + actionStarted + " firePos=" + (firePos != null));

                performPlant(firePos);
            }

            if (currentPhase == GoalPhase.ACTING && animationEnd > 0 && currentTime >= animationEnd) {
                clearTimedAnimation();
                clearTarget();
                enterPhase(GoalPhase.IDLE, "PLANT: complete");
            }
        }
    }

    private void clearTimedAnimation() {
        actionStarted = false;
        animationStart = animationEnd = 0L;
        byteBuddy.setSlamming(false);
    }

    private boolean verifyClaimOrAbort(ServerLevel serverLevel, ByteBuddyEntity.TaskType taskType, @Nullable BlockPos claimedPos, @Nullable BlockPos currentTaskPos) {
        if (claimedPos == null) return false;
        if (currentTaskPos != null && !currentTaskPos.equals(claimedPos)) return false;

        DockingStationBlockEntity dockBlock = dockBlockEntity(byteBuddy);
        if (dockBlock == null) return false;

        boolean buddyReserved = dockBlock.isReservedBy(serverLevel, taskType, claimedPos, byteBuddy.getUUID());
        if (!buddyReserved) {
            BotDebug.log(byteBuddy, "lost " + taskType + " claim at " + claimedPos.toShortString() + " — aborting");
            clearTarget();

            enterPhase(GoalPhase.IDLE, "claim lost; rescan");
        }
        return buddyReserved;
    }

    private void releasePlantClaim() {
        DockingStationBlockEntity dockBlock = dockBlockEntity(byteBuddy);
        if (claimedPlantPos != null && dockBlock != null) {
            dockBlock.releaseClaim(ByteBuddyEntity.TaskType.PLANT, claimedPlantPos, byteBuddy.getUUID());
        }
        claimedPlantPos = null;
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
        releasePlantClaim();
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
