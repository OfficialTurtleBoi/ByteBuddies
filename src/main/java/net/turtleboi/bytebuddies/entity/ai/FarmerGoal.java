package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.*;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public class FarmerGoal extends Goal {
    private final ByteBuddyEntity byteBuddy;
    @Nullable private BlockPos targetCrop;
    @Nullable private BlockPos approachPos;
    private record Approach(BlockPos targetPos, Vec3 approachAnchor, double distSq, Path path) {}
    private List<Approach> approachPlans = Collections.emptyList();
    private int anchorIndex = 0;
    @Nullable private Vec3 targetAnchor = null;
    private boolean edgeAnchored = false;
    private long holdAnchorTick = 0L;

    @Nullable private BlockState queuedPlantState;
    @Nullable private Item queuedSeedItem;
    @Nullable private BlockPos queuedPlantPos;

    private static final int baseHarvestCooldown = 20;
    private static final int basePlantCooldown = 20;
    private long nextHarvestTick = 0L;
    private long nextPlantTick = 0L;

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
    private static final double reachDistanceMax = 1.15;
    private static final double verticalTolerance = 1.25;

    private static final double finalApproachDist = 1.75;
    private static final double microDistMin = 0.06;
    private static final double microDistMax = 0.14;

    @Nullable private BlockPos claimedPos = null;
    private static final int claimTimeOut = 120;
    private long nextClaimRenew = 0L;

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

        var targetLock = findTargetWithApproach();
        if (targetLock.isEmpty()) {
            failTask(FailReason.NO_TARGET, "radius=" + byteBuddy.effectiveRadius());
            return false;
        }

        this.targetCrop = targetLock.get().crop();
        this.approachPos = targetLock.get().targetPos();
        this.targetAnchor = cropEdgeAnchor(this.targetCrop, this.approachPos);
        this.edgeAnchored = false;
        this.holdAnchorTick = 0L;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "to edge " + approachPos.toShortString());
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return queuedPlantPos != null || targetCrop != null;
    }

    @Override
    public void tick() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            if (queuedPlantPos != null) {
                var plantPosCenter = center(queuedPlantPos);
                var buddyPos = byteBuddy.position();
                if (distSq(buddyPos, plantPosCenter) <= reachDistanceMin * reachDistanceMin && Math.abs(buddyPos.y - plantPosCenter.y) <= verticalTolerance) {
                    if (plantReady(serverLevel)) {
                        enterPhase(GoalPhase.ACTING, "planting at " + queuedPlantPos.toShortString());
                        boolean planted = plantAt(queuedPlantPos);
                        queuedPlantPos = null;
                        armHarvest(serverLevel);
                        enterPhase(GoalPhase.IDLE, "rescan");
                    }
                } else {
                    byteBuddy.getNavigation().moveTo(
                            queuedPlantPos.getX() + 0.5,
                            queuedPlantPos.getY(),
                            queuedPlantPos.getZ() + 0.5,
                            0.9 * byteBuddy.actionSpeedMultiplier()
                    );
                }
                return;
            }

            if (targetCrop == null || approachPos == null) return;

            if (claimedPos != null && claimedPos.equals(targetCrop)) {
                if (serverLevel.getGameTime() >= nextClaimRenew) {
                    var dockingStation = dockBlockEntity();
                    if (dockingStation != null) {
                        dockingStation.renewClaim(
                                serverLevel, TaskType.HARVEST, claimedPos, byteBuddy.getUUID(), claimTimeOut);
                    }
                    nextClaimRenew = serverLevel.getGameTime() + 5;
                }
            }

            BlockState cropBlockState = byteBuddy.level().getBlockState(targetCrop);
            if (!(cropBlockState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(cropBlockState)) {
                clearTarget();
                enterPhase(GoalPhase.IDLE, "target invalid, rescan");
                return;
            }

            var cropCenter = center(targetCrop);
            byteBuddy.getLookControl().setLookAt(cropCenter.x, cropCenter.y, cropCenter.z, 30.0f, 30.0f);

            if (currentPhase == GoalPhase.MOVING) {
                Vec3 progressToTarget = (targetAnchor != null) ? targetAnchor : center(approachPos);
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
                        if (tryFallbackHarvest(serverLevel, crop)) return;
                        clearTarget();
                        enterPhase(GoalPhase.IDLE, "MOVING stalled, rescan");
                        return;
                    }
                }

                if (timedOut(movingTimeout)) {
                    if (tryFallbackHarvest(serverLevel, crop)) return;
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
                        var targetLock = findTargetWithApproach();
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
                        lockToAnchor(serverLevel);
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
                var targetCenter = center(approachPos);
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
                if (!harvestReady(serverLevel)) {
                    return;
                }

                var dockingStation = dockBlockEntity();
                if (dockingStation != null && claimedPos != null) {
                    if (!dockingStation.isReservedBy(serverLevel, TaskType.HARVEST, claimedPos, byteBuddy.getUUID())) {
                        BotDebug.log(byteBuddy, "lost claim for " + targetCrop.toShortString() + ", aborting");
                        clearTarget();
                        enterPhase(GoalPhase.IDLE, "claim lost; rescan");
                        return;
                    }
                }

                enterPhase(GoalPhase.ACTING, "harvesting at " + targetCrop.toShortString());
                boolean hasHarvested = harvestAt(targetCrop, (CropBlock) cropBlockState.getBlock());
                if (hasHarvested) {
                    queuedPlantPos = targetCrop.immutable();
                    armPlant(serverLevel);
                }
                clearTarget();
                enterPhase(GoalPhase.IDLE, "queued plant & rescan");
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

    private record findTarget(BlockPos crop, BlockPos targetPos, Path path) {}
    private Optional<findTarget> findTargetWithApproach() {
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
                            this.claimedPos = potentialCandidate.immutable();
                            this.nextClaimRenew = serverLevel.getGameTime() + 5;
                        }

                        this.approachPlans = approachPlans;
                        this.anchorIndex = 0;
                        Approach approach = approachPlans.get(0);
                        this.approachPos = approach.targetPos();
                        return Optional.of(new findTarget(potentialCandidate.immutable(), approach.targetPos(), approach.path()));
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
            var anchor = cropEdgeAnchor(crop, targetPos);
            if (anchor == null) continue;

            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) ? pathNavigation.createPath(targetPos, 0) : null;
            if (path == null) continue;

            double distSq = distSq(byteBuddy.position(), anchor);
            approachList.add(new Approach(targetPos, anchor, distSq, path));
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

    private boolean harvestAt(BlockPos blockPos, CropBlock crop) {
        Level level = byteBuddy.level();
        double cx = blockPos.getX() + 0.5, cy = blockPos.getY() + 0.5, cz = blockPos.getZ() + 0.5;
        byteBuddy.getLookControl().setLookAt(cx, cy, cz, 30.0f, 30.0f);
        if (distSq(byteBuddy.position(), new Vec3(cx, cy, cz)) > (reachDistanceMin * reachDistanceMin)) {
            BotDebug.log(byteBuddy, "too far to harvest at " + blockPos.toShortString());
            return false;
        }

        this.queuedPlantState = crop.defaultBlockState();
        this.queuedSeedItem = ((SeedItemProvider) crop).bytebuddies$getSeedItem().asItem();

        playHarvestAnimation();

        int energyCost = 0;
        if (!byteBuddy.consumeEnergy(energyCost)) {
            failTask(FailReason.OUT_OF_ENERGY, "need=" + energyCost);
            if (level instanceof ServerLevel serverLevel) {
                nextHarvestTick = serverLevel.getGameTime() + 5;
            }
            return false;
        }

        BlockState blockState = level.getBlockState(blockPos);
        List<ItemStack> cropProduceList = Block.getDrops(blockState, (ServerLevel) level, blockPos, null, null, ItemStack.EMPTY);
        DiskHooks.applyPrimaryYieldBonus(cropProduceList, crop, byteBuddy, byteBuddy.yieldBonusChance());

        level.destroyBlock(blockPos, false);
        int inserted = 0, dropped = 0;
        for (ItemStack cropDrops : cropProduceList) {
            ItemStack collectedItems = InventoryUtil.mergeInto(byteBuddy.getMainInv(), cropDrops);
            if (!collectedItems.isEmpty()) {
                Containers.dropItemStack(level, cx, cy, cz, collectedItems);
                dropped += collectedItems.getCount();
            } else {
                inserted += cropDrops.getCount();
            }
        }

        byteBuddy.onTaskSuccess(TaskType.HARVEST, blockPos);
        BotDebug.log(byteBuddy, "harvested " + blockPos.toShortString() + " inserted=" + inserted + " dropped=" + dropped);
        BotDebug.mark(level, blockPos);

        if (level instanceof ServerLevel serverLevel){
            armHarvest(serverLevel);
        }

        return true;
    }

    private boolean plantAt(BlockPos blockPos) {
        Level level = byteBuddy.level();
        playPlantAnimation();

        if (queuedPlantState == null || queuedSeedItem == null) {
            BotDebug.log(byteBuddy, "no queued plant info at " + blockPos.toShortString());
            return false;
        }

        ItemStack seedForCrop = InventoryUtil.findItem(byteBuddy.getMainInv(), queuedSeedItem);
        if (seedForCrop.isEmpty()) {
            BotDebug.log(byteBuddy, "no seed item (" + queuedSeedItem + ") for replant at " + blockPos.toShortString());
            return false;
        }

        BlockState soil = level.getBlockState(blockPos.below());
        TriState canSustainPlant = soil.canSustainPlant(level, blockPos.below(), Direction.UP, queuedPlantState);
        boolean canPlantHere = canSustainPlant.isDefault() ? queuedPlantState.canSurvive(level, blockPos) : canSustainPlant.isTrue();
        if (!canPlantHere) {
            BotDebug.log(byteBuddy, "cannot plant here (soil/conditions) at " + blockPos.toShortString());
            return false;
        }

        BlockState plantableArea = level.getBlockState(blockPos);
        boolean soilClear = plantableArea.isAir() || plantableArea.getCollisionShape(level, blockPos).isEmpty();
        if (!soilClear) {
            BotDebug.log(byteBuddy, "space not clear for plant at " + blockPos.toShortString());
            return false;
        }

        level.setBlock(blockPos, queuedPlantState, 3);
        seedForCrop.shrink(1);

        byteBuddy.onTaskSuccess(TaskType.PLANT, blockPos);
        if (level instanceof ServerLevel serverLevel) {
            armPlant(serverLevel);
        }

        queuedPlantState = null; queuedSeedItem = null;
        return true;
    }

    private boolean tryFallbackHarvest(ServerLevel serverLevel, CropBlock crop) {
        if (targetCrop == null) return false;
        var targetCenter = center(targetCrop);
        var buddyPos = byteBuddy.position();
        boolean withinReach = distSq(buddyPos, targetCenter) <= (reachDistanceMax * reachDistanceMin)
                && Math.abs(buddyPos.y - targetCenter.y) <= verticalTolerance;
        if (withinReach && hasLineOfSightToCrop(byteBuddy, targetCrop) && harvestReady(serverLevel)) {
            BotDebug.log(byteBuddy, "fallback harvest (LOS + relaxed reach)");
            enterPhase(GoalPhase.ACTING, "fallback harvest");
            boolean hasHarvested = harvestAt(targetCrop, crop);
            if (hasHarvested) {
                queuedPlantPos = targetCrop.immutable();
                armPlant(serverLevel);
            }
            clearTarget();
            enterPhase(GoalPhase.IDLE, "after fallback");
            return true;
        }
        return false;
    }

    private boolean hasLineOfSightToCrop(ByteBuddyEntity byteBuddy, BlockPos cropPos) {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            Vec3 eyePos = new Vec3(byteBuddy.getX(), byteBuddy.getEyeY(), byteBuddy.getZ());
            Vec3 targetPos = cropAimPoint(cropPos);

            Vec3 lookDirection = targetPos.subtract(eyePos);
            if (lookDirection.lengthSqr() < 1.0e-6) return true;

            Vec3 startPos = eyePos.add(lookDirection.normalize().scale(0.05));

            ClipContext clipContext = new ClipContext(startPos, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, byteBuddy);
            BlockHitResult hitResult = serverLevel.clip(clipContext);

            if (hitResult.getType() == HitResult.Type.MISS) return true;
            return hitResult.getBlockPos().equals(cropPos);
        } else {
            return false;
        }
    }

    private static Vec3 cropAimPoint(BlockPos cropPos) {
        return new Vec3(cropPos.getX() + 0.5, cropPos.getY() + 0.75, cropPos.getZ() + 0.5);
    }

    private static Vec3 center(BlockPos blockPos) {
        return new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
    }

    private static double distSq(Vec3 posA, Vec3 posB) {
        double dx = posA.x - posB.x, dz = posA.z - posB.z; return dx*dx + dz*dz;
    }

    @Nullable
    private static Vec3 cropEdgeAnchor(@Nullable BlockPos crop, @Nullable BlockPos targetPos) {
        if (crop == null || targetPos == null) return null;
        var cropCenter = center(crop);
        var targetCenter = center(targetPos);
        var lookDirection = targetCenter.subtract(cropCenter);
        if (lookDirection.lengthSqr() < 1.0e-6) lookDirection = new Vec3(1, 0, 0);
        lookDirection = lookDirection.normalize();
        double inset = 0.55;
        return new Vec3(cropCenter.x + lookDirection.x * inset, targetPos.getY(), cropCenter.z + lookDirection.z * inset);
    }

    private void lockToAnchor(ServerLevel serverLevel) {
        if (targetAnchor == null) return;
        edgeAnchored = true;
        holdAnchorTick = serverLevel.getGameTime() + 8;
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

    private void releaseClaim() {
        if (claimedPos == null) return;
        var dockingStation = dockBlockEntity();
        if (dockingStation != null) {
            dockingStation.releaseClaim(
                    TaskType.HARVEST, claimedPos, byteBuddy.getUUID());
        }
        claimedPos = null;
    }


    private void clearTarget() {
        releaseClaim();
        targetCrop = null;
        approachPos = null;
        targetAnchor = null;
        edgeAnchored = false;
        holdAnchorTick = 0L;
        approachPlans = Collections.emptyList();
        anchorIndex = 0;
        lastMoveDistSq = Double.POSITIVE_INFINITY;
        lastAnchorDistSq = Double.POSITIVE_INFINITY;
    }

    @Override
    public void stop() {
        releaseClaim();
        super.stop();
    }

    private int scaledSpeed(int baseSpeed) {
        float speed = Math.max(0.25f, byteBuddy.actionSpeedMultiplier());
        return Math.max(4, Math.round(baseSpeed / speed));
    }

    private boolean harvestReady(ServerLevel serverLevel) {
        return serverLevel.getGameTime() >= nextHarvestTick;
    }

    private boolean plantReady(ServerLevel serverLevel) {
        return serverLevel.getGameTime() >= nextPlantTick;
    }

    private void armHarvest(ServerLevel serverLevel) {
        nextHarvestTick = serverLevel.getGameTime() + scaledSpeed(baseHarvestCooldown);
    }

    private void armPlant(ServerLevel serverLevel) {
        nextPlantTick = serverLevel.getGameTime() + scaledSpeed(basePlantCooldown);
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
            holdAnchorTick = 0L;
            if (targetAnchor != null) {
                byteBuddy.getNavigation().moveTo(targetAnchor.x, targetAnchor.y, targetAnchor.z, 1.0 * byteBuddy.actionSpeedMultiplier());
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private void playHarvestAnimation() {
        System.out.println("[ByteBuddies] harvesting!");
    }

    private void playPlantAnimation() {
        System.out.println("[ByteBuddies] planting!");
    }
}
