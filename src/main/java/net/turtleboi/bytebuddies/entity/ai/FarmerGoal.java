package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.*;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.turtleboi.bytebuddies.api.SeedItemProvider;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.*;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem.DiskHooks;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.FailReason;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import net.turtleboi.bytebuddies.util.ToolHooks;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public class FarmerGoal extends Goal {
    private final ByteBuddyEntity byteBuddy;
    @Nullable private BlockPos targetCrop;
    @Nullable private BlockPos approachPos;
    private record Approach(BlockPos targetPos, Vec3 approachAnchor, double distSq, Path path) {}
    private List<Approach> approachPlans = Collections.emptyList();
    private int anchorIndex = 0;
    @Nullable private Vec3 targetAnchor = null;
    private boolean edgeAnchored = false;

    @Nullable private BlockState queuedPlantState;
    @Nullable private Item queuedSeedItem;
    @Nullable private BlockPos queuedPlantPos;

    @Nullable private BlockPos queuedTillPos;

    private long nextActionTick = 0L;
    private static final int baseActionCooldown = 20;

    private GoalPhase currentPhase = GoalPhase.IDLE;
    private FailReason lastFail = FailReason.NONE;
    private long phaseStartedTick = 0L;
    private long phaseProgressTick = 0L;

    private int repathRetries = 0;
    private int anchorRotateRetries = 0;
    private int targetReselectRetries = 0;

    private static final int seekingTimout = 20;
    private static final int movingTimeout = 160;
    private static final int actingTimeout = 40;

    private double lastMoveDistSq = Double.POSITIVE_INFINITY;
    private double lastAnchorDistSq = Double.POSITIVE_INFINITY;

    private static final double reachDistanceMin = 0.95;
    private static final double verticalTolerance = 1.25;

    private static final double finalApproachDist = 1.75;
    private static final double microDistMin = 0.06;
    private static final double microDistMax = 0.14;

    @Nullable private BlockPos claimedHarvestPos = null;
    @Nullable private BlockPos claimedPlantPos   = null;
    @Nullable private BlockPos claimedTillPos = null;
    private static final int claimTimeOut = 120;
    private long nextClaimRenewHarvest = 0L;
    private long nextClaimRenewPlant = 0L;
    private long nextClaimRenewTill = 0L;

    private enum PendingAction {
        NONE, HARVEST, PLANT, TILL
    }
    private PendingAction pendingAction = PendingAction.NONE;
    private long animationStart = 0L;
    private long animationEnd = 0L;
    private boolean actionStarted = false;

    public FarmerGoal(ByteBuddyEntity byteBuddy) {
        this.byteBuddy = byteBuddy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        byteBuddy.setPathfindingMalus(PathType.WATER, 8.0F);
        byteBuddy.setPathfindingMalus(PathType.WATER_BORDER, 4.0F);
    }

    @Override
    public boolean canUse() {
        if (byteBuddy.getBuddyRole() != BuddyRole.FARMER) {
            failTask(FailReason.WRONG_ROLE, "role=" + byteBuddy.getBuddyRole());
            return false;
        }
        if (byteBuddy.getDock().isEmpty()) {
            failTask(FailReason.NO_DOCK, "no station bound");
            return false;
        }
        if (queuedPlantPos != null) return true;

        var targetLock = findHarvestPlan();
        if (targetLock.isEmpty()) {
            BlockPos dockPosition = byteBuddy.getDock().orElse(null);
            if (dockPosition != null) {
                PlantPlan plantPlan = findPlantPlan(dockPosition, byteBuddy.effectiveRadius());
                if (plantPlan != null) {
                    this.queuedPlantPos   = plantPlan.pos();
                    this.queuedPlantState = plantPlan.plantState();
                    this.queuedSeedItem   = plantPlan.seedItem();

                    if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                        DockingStationBlockEntity dockBlock = dockBlockEntity();
                        if (dockBlock != null && queuedPlantPos != null) {
                            boolean claimedPos = dockBlock.tryClaim(serverLevel, TaskType.PLANT, queuedPlantPos, byteBuddy.getUUID(), claimTimeOut);
                            if (claimedPos) {
                                claimedPlantPos = queuedPlantPos;
                                nextClaimRenewPlant = serverLevel.getGameTime() + 5;
                            } else {
                                queuedPlantPos = null;
                                queuedPlantState = null;
                                queuedSeedItem = null;
                            }
                        }
                    }


                    if (queuedPlantPos != null) {
                        Approach plantApproach = nearestApproachFor(queuedPlantPos);
                        if (plantApproach != null) {
                            this.approachPos  = plantApproach.targetPos();
                            this.targetAnchor = plantApproach.approachAnchor();
                        } else {
                            this.approachPos  = queuedPlantPos;
                            this.targetAnchor = queuedPlantPos.getCenter();
                        }
                        enterPhase(GoalPhase.MOVING, "to plant site " + queuedPlantPos.toShortString());
                        return true;
                    }
                }

                var tillPick = findTillPlan();
                if (tillPick.isPresent()) {
                    this.targetCrop = null;
                    this.queuedPlantPos = null;
                    enterPhase(GoalPhase.MOVING, "to till site " + tillPick.get().blockPos().toShortString());
                    return true;
                }
            }

            failTask(FailReason.NO_TARGET, "radius=" + byteBuddy.effectiveRadius());
            return false;
        }

        this.targetCrop = targetLock.get().crop();
        this.approachPos = targetLock.get().targetPos();
        this.targetAnchor = cropEdgeAnchor(this.targetCrop, this.approachPos);
        this.edgeAnchored = false;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "to edge " + approachPos.toShortString());
        return true;
    }


    @Override
    public boolean canContinueToUse() {
        return pendingAction != PendingAction.NONE || queuedPlantPos != null || queuedTillPos != null ||targetCrop != null;
    }

    private boolean verifyClaimOrAbort(ServerLevel serverLevel, TaskType taskType, @Nullable BlockPos claimedPos, @Nullable BlockPos currentTaskPos) {
        if (claimedPos == null) return false;
        if (currentTaskPos != null && !currentTaskPos.equals(claimedPos)) return false;

        DockingStationBlockEntity dockBlock = dockBlockEntity();
        if (dockBlock == null) return false;

        boolean buddyReserved = dockBlock.isReservedBy(serverLevel, taskType, claimedPos, byteBuddy.getUUID());
        if (!buddyReserved) {
            BotDebug.log(byteBuddy, "lost " + taskType + " claim at " + claimedPos.toShortString() + " — aborting");
            switch (taskType) {
                case HARVEST -> clearTarget();
                case PLANT -> {
                    queuedPlantPos = null; claimedPlantPos = null;
                }
                case TILL -> {
                    queuedTillPos = null; claimedTillPos = null;
                }
                default -> {}
            }
            enterPhase(GoalPhase.IDLE, "claim lost; rescan");
        }
        return buddyReserved;
    }

    private void renewClaimIfNeeded(ServerLevel serverLevel, TaskType taskType, @Nullable BlockPos claimedPos, @Nullable BlockPos currentTaskPos,
                                    long currenTime, int renewPeriod, int timeoutTicks, LongSupplier getNextRenew, LongConsumer setNextRenew) {
        if (claimedPos == null) return;
        if (currentTaskPos != null && !currentTaskPos.equals(claimedPos)) return;
        if (currenTime < getNextRenew.getAsLong()) return;

        DockingStationBlockEntity dockBlock = dockBlockEntity();
        if (dockBlock == null) return;

        dockBlock.renewClaim(serverLevel, taskType, claimedPos, byteBuddy.getUUID(), timeoutTicks);
        setNextRenew.accept(currenTime + renewPeriod);
    }

    @Override
    public void tick() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            tickTimedAnimation();

            if (pendingAction != PendingAction.NONE) {
                return;
            }

            if (queuedTillPos != null) {
                Vec3 tillPosCenter = queuedTillPos.getCenter();
                Vec3 buddyPos = byteBuddy.position();
                if (targetAnchor != null) {
                    double distSq = distSq(buddyPos, targetAnchor);
                    double distance = Math.sqrt(distSq);

                    if (distance > finalApproachDist) {
                        this.byteBuddy.getNavigation().moveTo(
                                targetAnchor.x,
                                targetAnchor.y,
                                targetAnchor.z,
                                0.9 * byteBuddy.actionSpeedMultiplier());
                        return;
                    }

                    if (!edgeAnchored) {
                        if (distance <= microDistMin) {
                            lockToAnchor();
                        } else {
                            byteBuddy.getNavigation().stop();
                            byteBuddy.getMoveControl().setWantedPosition(
                                    targetAnchor.x,
                                    targetAnchor.y,
                                    targetAnchor.z,
                                    0.9 * byteBuddy.actionSpeedMultiplier());
                            return;
                        }
                    } else {
                        if (distance > microDistMax) {
                            edgeAnchored = false;
                            return;
                        }
                    }

                    boolean withinReach = distSq(buddyPos, tillPosCenter) <= (reachDistanceMin * reachDistanceMin)
                            && Math.abs(buddyPos.y - tillPosCenter.y) <= verticalTolerance;

                    if (withinReach) {
                        if (!verifyClaimOrAbort(serverLevel, TaskType.TILL, claimedTillPos, queuedTillPos)) {
                            queuedTillPos = null;
                            return;
                        }
                        if (actionReady(serverLevel)) {
                            startTimedAnimation(
                                    PendingAction.TILL,
                                    toTicks(2.0),
                                    toTicks(0.4),
                                    queuedTillPos,
                                    null
                            );
                        }
                    }
                    return;
                }

                Approach tillApproach = nearestTillApproachFor(queuedTillPos);
                if (tillApproach != null) {
                    this.approachPos  = tillApproach.targetPos();
                    this.targetAnchor = tillApproach.approachAnchor();
                    this.edgeAnchored = false;

                    Vec3 approachAnchor = tillApproach.approachAnchor();
                    boolean moving = this.byteBuddy.getNavigation().moveTo(
                            approachAnchor.x,
                            approachAnchor.y,
                            approachAnchor.z,
                            0.9 * byteBuddy.actionSpeedMultiplier());
                    if (!moving) {
                        this.byteBuddy.getNavigation().stop();
                        this.byteBuddy.getMoveControl().setWantedPosition(
                                approachAnchor.x,
                                approachAnchor.y,
                                approachAnchor.z,
                                0.9 * byteBuddy.actionSpeedMultiplier());
                    }
                    BotDebug.log(this.byteBuddy, "MOVING: approachPos till edge " + tillApproach.targetPos().toShortString());
                } else {
                    this.byteBuddy.getNavigation().moveTo(
                            tillPosCenter.x,
                            queuedTillPos.getY(),
                            tillPosCenter.z,
                            0.8 * byteBuddy.actionSpeedMultiplier());
                    BotDebug.log(this.byteBuddy, "MOVING: no standable edge/ledge for till; nudging center");
                }
                return;
            }


            if (queuedPlantPos != null) {
                Vec3 plantPosCenter = queuedPlantPos.getCenter();
                Vec3 buddyPos = byteBuddy.position();
                boolean withinReach = distSq(buddyPos, plantPosCenter) <= (reachDistanceMin * reachDistanceMin)
                        && Math.abs(buddyPos.y - plantPosCenter.y) <= verticalTolerance;
                if (withinReach) {
                    if (!verifyClaimOrAbort(serverLevel, TaskType.PLANT, claimedPlantPos, queuedPlantPos)) {
                        return;
                    }

                    if (actionReady(serverLevel)) {
                        startTimedAnimation(
                                PendingAction.PLANT,
                                toTicks(2.0),
                                toTicks(0.4),
                                queuedPlantPos,
                                null
                        );
                    }
                    return;
                }

                Approach plantApproach = nearestApproachFor(queuedPlantPos);
                if (plantApproach != null) {
                    Vec3 approachAnchor = plantApproach.approachAnchor();
                    boolean moving = byteBuddy.getNavigation().moveTo(
                            approachAnchor.x,
                            approachAnchor.y,
                            approachAnchor.z,
                            0.9 * byteBuddy.actionSpeedMultiplier());
                    if (!moving) {
                        byteBuddy.getNavigation().stop();
                        byteBuddy.getMoveControl().setWantedPosition(
                                approachAnchor.x,
                                approachAnchor.y,
                                approachAnchor.z,
                                0.9 * byteBuddy.actionSpeedMultiplier());
                    }
                    BotDebug.log(byteBuddy, "MOVING: approachPos plant edge " + plantApproach.targetPos().toShortString());
                } else {
                    byteBuddy.getNavigation().moveTo(
                            plantPosCenter.x,
                            queuedPlantPos.getY(),
                            plantPosCenter.z,
                            0.8 * byteBuddy.actionSpeedMultiplier());
                    BotDebug.log(byteBuddy, "MOVING: no standable edge; nudging center");
                }
                return;
            }

            if (targetCrop == null || approachPos == null) return;

            renewClaimIfNeeded(serverLevel, TaskType.HARVEST,
                    claimedHarvestPos,
                    targetCrop,
                    serverLevel.getGameTime(),
                    5,
                    claimTimeOut,
                    () -> nextClaimRenewHarvest,
                    time -> nextClaimRenewHarvest = time);

            renewClaimIfNeeded(serverLevel, TaskType.PLANT,
                    claimedPlantPos,
                    queuedPlantPos,
                    serverLevel.getGameTime(),
                    5,
                    claimTimeOut,
                    () -> nextClaimRenewPlant,
                    time -> nextClaimRenewPlant = time);

            renewClaimIfNeeded(serverLevel, TaskType.TILL,
                    claimedTillPos,
                    queuedTillPos,
                    serverLevel.getGameTime(),
                    5,
                    claimTimeOut,
                    () -> nextClaimRenewTill,
                    time -> nextClaimRenewTill = time);


            BlockState cropBlockState = byteBuddy.level().getBlockState(targetCrop);
            if (!(cropBlockState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(cropBlockState)) {
                clearTarget();
                enterPhase(GoalPhase.IDLE, "target invalid, rescan");
                return;
            }

            Vec3 cropCenter = targetCrop.getCenter();
            byteBuddy.getLookControl().setLookAt(cropCenter.x, cropCenter.y, cropCenter.z, 30.0f, 30.0f);

            if (currentPhase == GoalPhase.MOVING) {
                Vec3 progressToTarget = (targetAnchor != null) ? targetAnchor : approachPos.getCenter();
                double distSq = distSq(byteBuddy.position(), progressToTarget);

                if (distSq + 1.0e-3 < lastMoveDistSq) {
                    lastMoveDistSq = distSq;
                    markProgress();
                }

                if (stalledFor(movingTimeout / 10)) {
                    if (repath()) {
                        BotDebug.log(byteBuddy, "MOVING: repath");
                        markProgress();
                    } else if (rotateAnchor()) {
                        BotDebug.log(byteBuddy, "MOVING: rotate targetPos side");
                        markProgress();
                    } else {
                        clearTarget();
                        enterPhase(GoalPhase.IDLE, "MOVING stalled, rescan");
                        return;
                    }
                }

                if (timedOut(movingTimeout)) {
                    clearTarget();
                    enterPhase(GoalPhase.IDLE, "MOVING timeout, rescan");
                    return;
                }
            } else if (currentPhase == GoalPhase.ACTING) {
                if (timedOut(actingTimeout)) {
                    BotDebug.log(byteBuddy, "ACTING timeout; abort");
                    clearTarget();
                    enterPhase(GoalPhase.IDLE, "abort act");
                    return;
                }
            } else if (currentPhase == GoalPhase.SEEKING) {
                if (timedOut(seekingTimout)) {
                    if (targetReselectRetries++ < 2) {
                        var targetLock = findHarvestPlan();
                        if (targetLock.isPresent()) {
                            this.targetCrop = targetLock.get().crop();
                            this.approachPos = targetLock.get().targetPos();
                            this.targetAnchor = cropEdgeAnchor(this.targetCrop, this.approachPos);
                            resetProgress();
                            enterPhase(GoalPhase.MOVING, "retry seek -> moving");
                        } else {
                            enterPhase(GoalPhase.IDLE, "seek timeout, no target");
                        }
                    } else {
                        enterPhase(GoalPhase.IDLE, "seek timeout (exhausted)");
                    }
                    return;
                }
            }

            if (targetAnchor != null) {
                double distanceToTarget = Math.sqrt(distSq(byteBuddy.position(), targetAnchor));

                if (distanceToTarget > finalApproachDist) {
                    byteBuddy.getNavigation().moveTo(
                            targetAnchor.x,
                            targetAnchor.y,
                            targetAnchor.z,
                            byteBuddy.actionSpeedMultiplier()
                    );
                    return;
                }

                if (!edgeAnchored) {
                    if (distanceToTarget <= microDistMin) {
                        lockToAnchor();
                    } else {
                        byteBuddy.getNavigation().stop();
                        byteBuddy.getMoveControl().setWantedPosition(
                                targetAnchor.x,
                                targetAnchor.y,
                                targetAnchor.z,
                                byteBuddy.actionSpeedMultiplier());
                        if (distanceToTarget + 1.0e-3 < lastAnchorDistSq) {
                            lastAnchorDistSq = distanceToTarget;
                            markProgress();
                        }
                        BotDebug.log(byteBuddy, String.format("final-targetPos dH=%.3f to edge %s", distanceToTarget, approachPos.toShortString()));
                        return;
                    }
                } else {
                    double distanceToFinalTarget = Math.sqrt(distSq(byteBuddy.position(), targetAnchor));
                    if (distanceToFinalTarget > microDistMax) {
                        edgeAnchored = false;
                        return;
                    }
                }
            } else {
                var targetCenter = approachPos.getCenter();
                byteBuddy.getNavigation().moveTo(
                        targetCenter.x,
                        approachPos.getY(),
                        targetCenter.z,
                        byteBuddy.actionSpeedMultiplier());
                return;
            }


            var buddyPos = byteBuddy.position();
            boolean withinReach = distSq(buddyPos, cropCenter) <= (reachDistanceMin * reachDistanceMin)
                    && Math.abs(buddyPos.y - cropCenter.y) <= verticalTolerance;

            if (withinReach) {
                if (!actionReady(serverLevel)) {
                    return;
                }

                if (!verifyClaimOrAbort(serverLevel, TaskType.HARVEST, claimedHarvestPos, targetCrop)) {
                    return;
                }

                startTimedAnimation(
                        PendingAction.HARVEST,
                        toTicks(2.6),
                        toTicks(1.8),
                        targetCrop,
                        (CropBlock) cropBlockState.getBlock()
                );
                return;
            }

            if (targetAnchor != null && !edgeAnchored) {
                byteBuddy.getMoveControl().setWantedPosition(
                        targetAnchor.x,
                        targetAnchor.y,
                        targetAnchor.z,
                        byteBuddy.actionSpeedMultiplier());
            }
        }
    }

    @Nullable
    private DockingStationBlockEntity dockBlockEntity() {
        return (DockingStationBlockEntity) byteBuddy.getDock()
                .map(blockPos -> byteBuddy.level().getBlockEntity(blockPos))
                .filter(blockEntity -> blockEntity instanceof DockingStationBlockEntity)
                .orElse(null);
    }

    private record HarvestPlan(BlockPos crop, BlockPos targetPos, Path path) {}

    @Nullable
    private Approach nearestApproachFor(BlockPos target) {
        List<Approach> plans = buildApproachPlans(byteBuddy.level(), target);
        return plans.isEmpty() ? null : plans.get(0);
    }

    private Optional<HarvestPlan> findHarvestPlan() {
        BlockPos dockPos = byteBuddy.getDock().orElse(null);
        if (dockPos == null) return Optional.empty();
        int effectiveRadius = byteBuddy.effectiveRadius();
        Level level = byteBuddy.level();
        var dockingStation = dockBlockEntity();

        if (level instanceof ServerLevel serverLevel) {
            MutableBlockPos potentialCandidate = new MutableBlockPos();
            for (int y = -1; y <= 2; y++) {
                for (int x = -effectiveRadius; x <= effectiveRadius; x++) {
                    for (int z = -effectiveRadius; z <= effectiveRadius; z++) {
                        potentialCandidate.set(dockPos.getX() + x, dockPos.getY() + y, dockPos.getZ() + z);
                        BlockState blockState = level.getBlockState(potentialCandidate);
                        if (!(blockState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(blockState)) continue;
                        if (dockingStation != null && dockingStation.isReserved(serverLevel, TaskType.HARVEST, potentialCandidate)) continue;

                        var approachPlans = buildApproachPlans(level, potentialCandidate.immutable());
                        if (approachPlans.isEmpty()) continue;

                        if (dockingStation != null) {
                            boolean targetViable = dockingStation.tryClaim(serverLevel, TaskType.HARVEST,
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

    private List<Approach> buildApproachPlans(Level level, BlockPos crop) {
        ArrayList<Approach> approachList = new ArrayList<>(4);
        BlockPos[] targetSides = new BlockPos[]{ crop.east(), crop.west(), crop.south(), crop.north() };

        for (BlockPos targetPos : targetSides) {
            if (!isStandable(level, targetPos)) continue;
            var cropEdgeAnchor = cropEdgeAnchor(crop, targetPos);
            if (cropEdgeAnchor == null) continue;

            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) ? pathNavigation.createPath(targetPos, 0) : null;
            if (path == null) continue;

            double distSq = distSq(byteBuddy.position(), cropEdgeAnchor);
            approachList.add(new Approach(targetPos, cropEdgeAnchor, distSq, path));
        }

        approachList.sort(java.util.Comparator.comparingDouble(Approach::distSq));
        return approachList;
    }

    private boolean isStandable(Level level, BlockPos blockPos) {
        BlockState below = level.getBlockState(blockPos.below());
        boolean solidFloor = !below.getCollisionShape(level, blockPos.below()).isEmpty();
        boolean feetFree = level.getBlockState(blockPos).getCollisionShape(level, blockPos).isEmpty();
        boolean headFree = level.getBlockState(blockPos.above()).getCollisionShape(level, blockPos.above()).isEmpty();
        boolean noLiquid = level.getFluidState(blockPos).isEmpty() && level.getFluidState(blockPos.above()).isEmpty();
        return solidFloor && feetFree && headFree && noLiquid;
    }

    private void performHarvest(BlockPos blockPos, BlockState crop) {
        Level level = byteBuddy.level();
        Vec3 center = blockPos.getCenter();
        byteBuddy.getLookControl().setLookAt(
                center.x,
                center.y - 0.25,
                center.z,
                15.0f,
                15.0f
        );
        if (distSq(byteBuddy.position(), center) > (reachDistanceMin * reachDistanceMin)) {
            BotDebug.log(byteBuddy, "too far to harvest at " + blockPos.toShortString());
            return;
        }

        this.queuedPlantState = crop;
        if (crop.getBlock() instanceof CropBlock cropBlock) {
            this.queuedSeedItem = ((SeedItemProvider) cropBlock).bytebuddies$getSeedItem().asItem();
        }

        int cost = 25;
        if (!byteBuddy.consumeEnergy(cost)) {
            failTask(FailReason.OUT_OF_ENERGY, "need=" + cost);
            releaseHarvestClaim();
            return;
        }

        BlockState blockState = level.getBlockState(blockPos);
        List<ItemStack> drops = Block.getDrops(blockState, (ServerLevel) level, blockPos, null, null, ItemStack.EMPTY);
        DiskHooks.applyPrimaryYieldBonus(drops, crop.getBlock(), byteBuddy, byteBuddy.yieldBonusChance());

        level.destroyBlock(blockPos, false);
        int inserted = 0, dropped = 0;
        for (ItemStack drop : drops) {
            ItemStack itemsProduced = InventoryUtil.mergeInto(byteBuddy.getMainInv(), drop);
            if (!itemsProduced.isEmpty()) {
                Containers.dropItemStack(level, center.x, center.y, center.z, itemsProduced);
                dropped += itemsProduced.getCount();
            } else {
                inserted += drop.getCount();
            }
        }

        byteBuddy.onTaskSuccess(TaskType.HARVEST, blockPos);
        BotDebug.log(byteBuddy, "harvested " + blockPos.toShortString() + " inserted=" + inserted + " dropped=" + dropped);
        BotDebug.mark(level, blockPos);

        if (level instanceof ServerLevel serverLevel) {
            queuedPlantPos = blockPos.immutable();

            DockingStationBlockEntity dockBlock = dockBlockEntity();
            if (dockBlock != null) {
                boolean claimedPos = dockBlock.tryClaim(serverLevel, TaskType.PLANT, queuedPlantPos, byteBuddy.getUUID(), claimTimeOut);
                if (claimedPos) {
                    claimedPlantPos = queuedPlantPos;
                    nextClaimRenewHarvest = serverLevel.getGameTime() + 5;
                    nextClaimRenewPlant = serverLevel.getGameTime() + 5;
                    nextClaimRenewTill = serverLevel.getGameTime() + 5;
                } else {
                    queuedPlantPos = null;
                }
            }

            armAction(serverLevel);
        }

        clearTarget();
        enterPhase(GoalPhase.IDLE, "harvest fired; queued plant");
    }

    private record PlantPlan(BlockPos pos, Item seedItem, BlockState plantState) {}
    private @Nullable PlantPlan findPlantPlan(BlockPos blockPos, int radius) {
        Level level = byteBuddy.level();
        if (!(level instanceof ServerLevel serverLevel)) return null;
        DockingStationBlockEntity dockBlock = dockBlockEntity();

        var buddyInventory = byteBuddy.getMainInv();
        ArrayList<Item> seedItems = new ArrayList<>();
        for (int i = 0; i < buddyInventory.getSlots(); i++) {
            ItemStack stackInSlot = buddyInventory.getStackInSlot(i);
            if (stackInSlot.isEmpty()) continue;
            Item item = stackInSlot.getItem();
            if (seedToPlantState(item) == null) continue;
            seedItems.add(item);
        }
        if (seedItems.isEmpty()) return null;

        MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -1; y <= 2; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(blockPos.getX() + dx, blockPos.getY() + y, blockPos.getZ() + dz);
                    if (dockBlock != null && dockBlock.isReserved(serverLevel, TaskType.PLANT, cursor)) continue;

                    for (Item seed : seedItems) {
                        BlockState plantCandidate = seedToPlantState(seed);
                        if (plantCandidate == null) continue;
                        if (!canPlantAt(level, cursor, plantCandidate)) continue;
                        return new PlantPlan(cursor.immutable(), seed, plantCandidate);
                    }
                }
            }
        }
        return null;
    }

    private void performPlant(BlockPos blockPos) {
        Level level = byteBuddy.level();
        if (queuedPlantState == null || queuedSeedItem == null) {
            BotDebug.log(byteBuddy, "no queued plant info at " + blockPos.toShortString());
            failTask(FailReason.NO_TARGET, "no queued plant info at " + blockPos.toShortString());
            return;
        }

        ItemStack seedStack = InventoryUtil.findItem(byteBuddy.getMainInv(), queuedSeedItem);
        if (seedStack.isEmpty()) {
            BotDebug.log(byteBuddy, "no seed item (" + queuedSeedItem + ") for replant at " + blockPos.toShortString());
            failTask(FailReason.NO_TARGET, "no seed item (" + queuedSeedItem + ") for replant at " + blockPos.toShortString());
            return;
        }

        BlockState blockBelow = level.getBlockState(blockPos.below());
        TriState canSustainPlant = blockBelow.canSustainPlant(level, blockPos.below(), Direction.UP, queuedPlantState);
        boolean canPlantHere = canSustainPlant.isDefault() ? queuedPlantState.canSurvive(level, blockPos) : canSustainPlant.isTrue();
        if (!canPlantHere) {
            BotDebug.log(byteBuddy, "cannot plant here (blockPos/conditions) at " + blockPos.toShortString());
            failTask(FailReason.NO_TARGET, "cannot plant here (blockPos/conditions) at " + blockPos.toShortString());
            return;
        }

        BlockState aboveSoil = level.getBlockState(blockPos);
        boolean soilPlantable = aboveSoil.isAir() || aboveSoil.getCollisionShape(level, blockPos).isEmpty();
        if (!soilPlantable) {
            BotDebug.log(byteBuddy, "space not clear for plant at " + blockPos.toShortString());
            failTask(FailReason.NO_TARGET, "space not clear for plant at " + blockPos.toShortString());
            return;
        }

        if(canPlantAt(level, blockPos, queuedPlantState)) {
            int energyCost = 25;
            if (!byteBuddy.consumeEnergy(energyCost)) {
                failTask(FailReason.OUT_OF_ENERGY, "need=" + energyCost);
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
                    serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(queuedSeedItem)),
                            blockPos.getX() + 0.5 + Math.cos(particleInterval) * 0.5,
                            blockPos.getY() + 0.125,
                            blockPos.getZ() + 0.5 + Math.sin(particleInterval) * 0.5,
                            1, 0, 0, 0, 0.05);
                }
            }

            level.setBlock(blockPos, queuedPlantState, 3);
            seedStack.shrink(1);

            byteBuddy.onTaskSuccess(TaskType.PLANT, blockPos);
            if (level instanceof ServerLevel serverLevel) {
                DockingStationBlockEntity dockBlock = dockBlockEntity();
                if (dockBlock != null && claimedPlantPos != null) {
                    dockBlock.releaseClaim(TaskType.PLANT, claimedPlantPos, byteBuddy.getUUID());
                }
                claimedPlantPos = null;
                armAction(serverLevel);
            }

            queuedPlantState = null;
            queuedSeedItem = null;
            queuedPlantPos = null;
            return;
        }

        failTask(FailReason.NO_TARGET, "cannot plant here (blockPos/conditions) at " + blockPos.toShortString());
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

    private static boolean isPlantable(BlockState blockState) {
        return blockState.getBlock() instanceof BushBlock
                || blockState.getBlock() instanceof CropBlock
                || blockState.is(BlockTags.CROPS);
    }

    private static boolean canPlantAt(Level level, BlockPos blockPos, BlockState plantState) {
        if (!level.isLoaded(blockPos)) return false;
        BlockState plantCandidate = level.getBlockState(blockPos);
        if (isPlantable(plantCandidate)) return false;
        if (!(plantCandidate.isAir() || plantCandidate.canBeReplaced())) return false;
        if (!level.getFluidState(blockPos).isEmpty()) return false;

        BlockPos soilPos = blockPos.below();
        BlockState soilCandidate = level.getBlockState(soilPos);
        TriState canSustainPlant = soilCandidate.canSustainPlant(level, soilPos, Direction.UP, plantState);
        if (canSustainPlant.isFalse()) return false;

        if (canSustainPlant.isDefault() && !plantState.canSurvive(level, blockPos)) return false;

        return plantState.canSurvive(level, blockPos);
    }


    private static final Set<Block> TILLABLE = Set.of(
            Blocks.DIRT,
            Blocks.GRASS_BLOCK,
            Blocks.COARSE_DIRT
    );

    private boolean isTillable(Level level, BlockPos blockPos) {
        if (!level.isLoaded(blockPos)) return false;
        BlockState soilCandidate = level.getBlockState(blockPos);

        if (soilCandidate.is(Blocks.FARMLAND)) return false;
        if (!TILLABLE.contains(soilCandidate.getBlock())) return false;

        BlockPos aboveSoil = blockPos.above();
        BlockState aboveSoilState = level.getBlockState(aboveSoil);

        return (aboveSoilState.isAir() || aboveSoilState.canBeReplaced()) && level.getFluidState(aboveSoil).isEmpty();
    }


    private static final int hydrationRadius = 4;
    private int waterDistance(Level level, BlockPos blockPos) {
        final int radius = hydrationRadius;
        MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int topCandidateScore = 999;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutableBlockPos.set(blockPos.getX() + dx, blockPos.getY() + dy, blockPos.getZ() + dz);
                    if (level.getFluidState(mutableBlockPos).is(FluidTags.WATER)) {
                        int candidateScore = Math.max(Math.abs(dx), Math.abs(dz));
                        if (candidateScore < topCandidateScore) topCandidateScore = candidateScore;
                        if (topCandidateScore == 0) return 0;
                    }
                }
            }
        }
        return topCandidateScore;
    }

    private static final double dryPenalty = 10000.0;
    private static final double waterDistanceBonus = 500.0;
    private static final double dockDistanceBonus = 1.0;
    private static final double approachDistanceBonus = 25.0;

    private record ScoredTillPlan(TillPlan tillPlan, double candidateScore) {}
    private record TillPlan(BlockPos blockPos, BlockPos approachPos, Path path, boolean isHydrated) {}

    private Optional<TillPlan> findTillPlan() {
        BlockPos dockPos = byteBuddy.getDock().orElse(null);
        if (dockPos == null) return Optional.empty();

        final int radius = byteBuddy.effectiveRadius();
        Level level = byteBuddy.level();
        DockingStationBlockEntity dockBlock = dockBlockEntity();
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();

        MutableBlockPos mutableDockPos = new MutableBlockPos();
        List<ScoredTillPlan> scores = new ArrayList<>();
        Vec3 buddyPos = byteBuddy.position();

        for (int y = -1; y <= 2; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutableDockPos.set(dockPos.getX() + dx, dockPos.getY() + y, dockPos.getZ() + dz);

                    if (!isTillable(level, mutableDockPos)) continue;
                    if (dockBlock != null && dockBlock.isReserved(serverLevel, TaskType.TILL, mutableDockPos)) continue;
                    int waterDistance = waterDistance(level, mutableDockPos);
                    if (waterDistance == 999) continue;

                    var approachPlans = buildTillEdgeApproachPlans(level, mutableDockPos.immutable());
                    if (approachPlans.isEmpty()) continue;
                    var best = approachPlans.get(0);

                    boolean isHydrated = (waterDistance <= hydrationRadius);

                    double score = 0.0;
                    if (!isHydrated) {
                        score += dryPenalty;
                    } else {
                        score += waterDistance * waterDistanceBonus;
                    }

                    double dockDistanceX = mutableDockPos.getX() - dockPos.getX();
                    double dockDistanceZ = mutableDockPos.getZ() - dockPos.getZ();
                    score += (dockDistanceX * dockDistanceX + dockDistanceZ * dockDistanceZ) * dockDistanceBonus;

                    Vec3 approachAnchor = best.approachAnchor();
                    double distanceToAnchorX = buddyPos.x - approachAnchor.x;
                    double distanceToAnchorZ = buddyPos.z - approachAnchor.z;
                    score += (distanceToAnchorX * distanceToAnchorX + distanceToAnchorZ * distanceToAnchorZ) * approachDistanceBonus;

                    scores.add(new ScoredTillPlan(
                            new TillPlan(mutableDockPos.immutable(), best.targetPos(), best.path(), isHydrated),
                            score
                    ));
                }
            }
        }

        if (scores.isEmpty()) return Optional.empty();

        scores.sort(Comparator.comparingDouble(ScoredTillPlan::candidateScore));
        var pick = scores.get(0).tillPlan();

        if (dockBlock != null) {
            if (!dockBlock.tryClaim(serverLevel, TaskType.TILL, pick.blockPos(), byteBuddy.getUUID(), claimTimeOut)) {
                return Optional.empty();
            }
            claimedTillPos = pick.blockPos();
            nextClaimRenewTill = serverLevel.getGameTime() + 5;
        }

        this.queuedTillPos = pick.blockPos();
        this.approachPlans = buildApproachPlans(level, pick.blockPos());
        this.anchorIndex = 0;
        this.approachPos  = pick.approachPos();
        this.targetAnchor = cropEdgeAnchor(pick.blockPos(), pick.approachPos());
        this.edgeAnchored = false;

        return Optional.of(pick);
    }

    @Nullable
    private Approach nearestTillApproachFor(BlockPos blockPos) {
        List<Approach> plans = buildTillEdgeApproachPlans(byteBuddy.level(), blockPos);
        return plans.isEmpty() ? null : plans.get(0);
    }

    private List<Approach> buildTillEdgeApproachPlans(Level level, BlockPos blockPos) {
        ArrayList<Approach> list = new ArrayList<>(8);
        BlockPos[] horizontal = new BlockPos[] {
                blockPos.east(), blockPos.west(), blockPos.north(), blockPos.south()
        };

        BlockPos[] horizontalPlusOne = new BlockPos[] {
                blockPos.east().above(), blockPos.west().above(), blockPos.north().above(), blockPos.south().above()
        };

        Consumer<BlockPos> posCondidates = standable -> {
            if (!isStandable(level, standable)) return;
            Vec3 cropEdgeAnchor = cropEdgeAnchor(blockPos, standable);
            if (cropEdgeAnchor == null) return;

            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation)
                    ? pathNavigation.createPath(standable, 0) : null;
            if (path == null) return;

            double distSq = distSq(byteBuddy.position(), cropEdgeAnchor);
            list.add(new Approach(standable, cropEdgeAnchor, distSq, path));
        };

        for (BlockPos horizontalPos : horizontal) posCondidates.accept(horizontalPos);
        for (BlockPos horizontalPosPlusOne : horizontalPlusOne) posCondidates.accept(horizontalPosPlusOne);

        list.sort(Comparator.comparingDouble(Approach::distSq));
        return list;
    }

    private void performTill(BlockPos blockPos) {
        Level level = byteBuddy.level();
        if (level instanceof ServerLevel serverLevel) {
            if (!verifyClaimOrAbort(serverLevel, TaskType.TILL, claimedTillPos, blockPos)) return;
        }

        if (!isTillable(level, blockPos)) {
            BotDebug.log(byteBuddy, "TILL invalid at " + blockPos.toShortString());
            releaseTillClaim();
            return;
        }

        int energyCost = 25;
        if (!byteBuddy.consumeEnergy(energyCost)) {
            failTask(FailReason.OUT_OF_ENERGY, "need=" + energyCost);
            releaseTillClaim();
            return;
        }

        ToolHooks.applyToolWear(byteBuddy, ToolHooks.ToolType.HOE, byteBuddy.toolWearMultiplier());

        level.playSound(
                null,
                blockPos,
                SoundEvents.HOE_TILL,
                SoundSource.BLOCKS,
                0.8f + (level.random.nextFloat() * 0.4f),
                0.9f + (level.random.nextFloat() * 0.2f)
        );


        if (level instanceof ServerLevel serverLevel) {
            int particlePoints = 12;
            for (int i = 0; i < particlePoints; i++) {
                double particleInterval = (Math.PI * 2 * i) / particlePoints;
                serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPos)),
                        blockPos.getX() + 0.5 + Math.cos(particleInterval) * 0.5,
                        blockPos.getY() + 0.125,
                        blockPos.getZ() + 0.5 + Math.sin(particleInterval) * 0.5,
                        1, 0, 0, 0, 0.05);
            }

            armAction(serverLevel);
        }

        BlockState soilCandidate = level.getBlockState(blockPos);
        if (soilCandidate.is(Blocks.COARSE_DIRT)) {
            level.setBlock(blockPos, Blocks.DIRT.defaultBlockState(), 3);
        } else {
            level.setBlock(blockPos, Blocks.FARMLAND.defaultBlockState(), 3);
        }

        byteBuddy.onTaskSuccess(TaskType.TILL, blockPos);
        releaseTillClaim();
    }

    private static double distSq(Vec3 posA, Vec3 posB) {
        double dx = posA.x - posB.x, dz = posA.z - posB.z;
        return dx*dx + dz*dz;
    }

    @Nullable
    private static Vec3 cropEdgeAnchor(@Nullable BlockPos crop, @Nullable BlockPos targetPos) {
        if (crop == null || targetPos == null) return null;
        var cropCenter = crop.getCenter();
        var targetCenter = targetPos.getCenter();
        var lookDirection = targetCenter.subtract(cropCenter);
        if (lookDirection.lengthSqr() < 1.0e-6) lookDirection = new Vec3(1, 0, 0);
        lookDirection = lookDirection.normalize();
        double inset = 0.55;
        return new Vec3(cropCenter.x + lookDirection.x * inset, targetPos.getY(), cropCenter.z + lookDirection.z * inset);
    }

    private void lockToAnchor() {
        if (targetAnchor == null) return;
        edgeAnchored = true;
        byteBuddy.getNavigation().stop();
        byteBuddy.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
        var buddyDelta = byteBuddy.getDeltaMovement();
        byteBuddy.setDeltaMovement(buddyDelta.x * 0.2, buddyDelta.y * 0.2, buddyDelta.z * 0.2);
        enterPhase(GoalPhase.MOVING, "locked edge");
    }

    private void enterPhase(GoalPhase phase, String context) {
        currentPhase = phase; lastFail = FailReason.NONE;
        phaseStartedTick = phaseProgressTick = getCurrentTime();

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

        BotDebug.log(byteBuddy, "FARMER: " + phase + (context.isEmpty() ? "" : " -> " + context));
    }

    private void failTask(FailReason reason, String context) {
        lastFail = reason;
        currentPhase = GoalPhase.IDLE;
        BotDebug.log(byteBuddy, "FARMER cannot start: " + reason + (context.isEmpty() ? "" : " (" + context + ")"));
        resetProgress();
        stop();
    }

    private long getCurrentTime() {
        return (byteBuddy.level() instanceof ServerLevel serverLevel) ? serverLevel.getGameTime() : 0L;
    }

    private void markProgress() {
        phaseProgressTick = getCurrentTime();
    }

    private boolean stalledFor(int stallTime) {
        return getCurrentTime() - phaseProgressTick > stallTime;
    }

    private boolean timedOut(int timeLimit) {
        return getCurrentTime() - phaseStartedTick > timeLimit;
    }

    private void resetProgress() {
        lastMoveDistSq = Double.POSITIVE_INFINITY;
        lastAnchorDistSq = Double.POSITIVE_INFINITY;
        repathRetries = 0;
        anchorRotateRetries = 0;
        targetReselectRetries = 0;
        phaseStartedTick = phaseProgressTick = getCurrentTime();
    }

    private void releaseHarvestClaim() {
        DockingStationBlockEntity dockBlock = dockBlockEntity();
        if (claimedHarvestPos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.HARVEST, claimedHarvestPos, byteBuddy.getUUID());
        }
        claimedHarvestPos = null;
    }

    private void releasePlantClaim() {
        DockingStationBlockEntity dockBlock = dockBlockEntity();
        if (claimedPlantPos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.PLANT, claimedPlantPos, byteBuddy.getUUID());
        }
        claimedPlantPos = null;
    }

    private void releaseTillClaim() {
        DockingStationBlockEntity dockBlock = dockBlockEntity();
        if (claimedTillPos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.TILL, claimedTillPos, byteBuddy.getUUID());
        }
        claimedTillPos = null;
    }

    private void releaseAllClaims() {
        releaseHarvestClaim();
        releasePlantClaim();
        releaseTillClaim();
    }

    private void clearTarget() {
        releaseHarvestClaim();
        targetCrop = null;
        approachPos = null;
        targetAnchor = null;
        edgeAnchored = false;
        approachPlans = Collections.emptyList();
        anchorIndex = 0;
        lastMoveDistSq = Double.POSITIVE_INFINITY;
        lastAnchorDistSq = Double.POSITIVE_INFINITY;
    }

    @Override
    public void stop() {
        clearTimedAnimation();
        releaseAllClaims();
        targetCrop = null;
        approachPos = null;
        targetAnchor = null;
        edgeAnchored = false;
        approachPlans = Collections.emptyList();
        anchorIndex = 0;

        queuedPlantPos = null;
        queuedPlantState = null;
        queuedSeedItem = null;

        queuedTillPos = null;
        super.stop();
    }

    private int scaledSpeed(int baseSpeed) {
        float speed = Math.max(0.25f, byteBuddy.actionSpeedMultiplier());
        return Math.max(4, Math.round(baseSpeed / speed));
    }

    private boolean actionReady(ServerLevel serverLevel) {
        return serverLevel.getGameTime() >= nextActionTick;
    }

    private void armAction(ServerLevel serverLevel) {
        nextActionTick = serverLevel.getGameTime() + scaledSpeed(baseActionCooldown);
    }

    private boolean repath() {
        if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
            if (repathRetries++ >= 2) return false;
            if (approachPos == null) return false;
            Path path = pathNavigation.createPath(approachPos, 0);
            byteBuddy.getNavigation().moveTo(path, 1.0 * byteBuddy.actionSpeedMultiplier());
            return true;
        } else {
            return false;
        }
    }

    private boolean rotateAnchor() {
        if (approachPlans.isEmpty() || targetCrop == null) return false;
        if (anchorRotateRetries++ >= 3) return false;

        int approaches = approachPlans.size();
        for (int tries = 0; tries < approaches; tries++) {
            anchorIndex = (anchorIndex + 1) % approaches;
            Approach approachCandidates = approachPlans.get(anchorIndex);
            if (!isStandable(byteBuddy.level(), approachCandidates.targetPos())) continue;
            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation)
                    ? pathNavigation.createPath(approachCandidates.targetPos(), 0) : null;
            if (path == null) continue;

            approachPos  = approachCandidates.targetPos();
            targetAnchor = approachCandidates.approachAnchor();
            edgeAnchored = false;
            if (targetAnchor != null) {
                byteBuddy.getNavigation().moveTo(targetAnchor.x, targetAnchor.y, targetAnchor.z, 1.0 * byteBuddy.actionSpeedMultiplier());
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private void playHarvestAnimation(ByteBuddyEntity byteBuddy) {
        byteBuddy.setWorking(true);
    }

    private void playPlantAnimation(ByteBuddyEntity byteBuddy) {
        byteBuddy.setSlamming(true);
    }

    private static int toTicks(double seconds) {
        return (int)Math.round(seconds * 20.0);
    }

    private void startTimedAnimation(PendingAction pendingAction, int totalTicks, int startTick, @Nullable BlockPos blockPos, @Nullable CropBlock crop) {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            byteBuddy.getNavigation().stop();
            Vec3 deltaMovement = byteBuddy.getDeltaMovement();
            byteBuddy.setDeltaMovement(
                    deltaMovement.x * 0.1,
                    deltaMovement.y * 0.1,
                    deltaMovement.z * 0.1
            );

            switch (pendingAction) {
                case TILL -> playPlantAnimation(byteBuddy);
                case HARVEST -> playHarvestAnimation(byteBuddy);
                case PLANT -> playPlantAnimation(byteBuddy);
            }

            this.pendingAction = pendingAction;
            this.actionStarted = false;
            this.animationStart = serverLevel.getGameTime() + startTick;
            this.animationEnd = serverLevel.getGameTime() + totalTicks;

            if (pendingAction == PendingAction.HARVEST || pendingAction == PendingAction.PLANT) {
                this.queuedPlantPos = blockPos;
            }

            if (crop != null && pendingAction == PendingAction.HARVEST) {
                this.queuedPlantState = crop.defaultBlockState();
            }

            enterPhase(GoalPhase.ACTING, "anim " + pendingAction + " fire@" + startTick + " end@" + totalTicks);
        }
    }

    private void tickTimedAnimation() {
        if (pendingAction != PendingAction.NONE && byteBuddy.level() instanceof ServerLevel serverLevel) {
            long currentTime = serverLevel.getGameTime();
            if (queuedPlantPos != null) {
                Vec3 posCenter = queuedPlantPos.getCenter();
                byteBuddy.getLookControl().setLookAt(posCenter.x, posCenter.y - 0.25, posCenter.z, 15.0f, 15.0f);
            }

            if (queuedTillPos != null) {
                Vec3 posCenter = queuedTillPos.getCenter();
                byteBuddy.getLookControl().setLookAt(posCenter.x, posCenter.y - 0.25, posCenter.z, 15.0f, 15.0f);
            }

            if (!actionStarted && currentTime >= animationStart) {
                actionStarted = true;
                if (pendingAction == PendingAction.TILL && queuedTillPos != null) {
                    performTill(queuedTillPos);
                } else if (pendingAction == PendingAction.HARVEST && queuedPlantState != null && queuedPlantPos != null) {
                    performHarvest(queuedPlantPos, queuedPlantState);
                } else if (pendingAction == PendingAction.PLANT && queuedPlantPos != null) {
                    performPlant(queuedPlantPos);
                }
            }

            if (currentTime >= animationEnd) {
                clearTimedAnimation();
            }
        }
    }

    private void clearTimedAnimation() {
        pendingAction = PendingAction.NONE;
        actionStarted = false;
        animationStart = animationEnd = 0L;
        byteBuddy.setWorking(false);
        byteBuddy.setSlamming(false);
    }

}
