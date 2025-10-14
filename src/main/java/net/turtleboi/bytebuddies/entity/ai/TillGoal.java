package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.ToolHooks;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

import static net.turtleboi.bytebuddies.util.GoalUtil.dockBlockEntity;

public class TillGoal extends Goal {
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

    @Nullable private BlockPos claimedTillPos = null;
    @Nullable private BlockPos firePos = null;
    @Nullable private BlockState firePreState = null;
    private static final int claimTimeOut = 120;
    private long nextClaimRenewTill = 0L;

    private long animationStart = 0L;
    private long animationEnd = 0L;
    private boolean actionStarted = false;

    private static final int tillEnergyCost = 25;

    public TillGoal(ByteBuddyEntity byteBuddy) {
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

        var tillPlan = findTillPlan();
        if (tillPlan != null) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
                if (dockBlock != null) {
                    if (claimedTillPos == null || !claimedTillPos.equals(tillPlan.blockPos())) {
                        if (!dockBlock.tryClaim(serverLevel, ByteBuddyEntity.TaskType.TILL, tillPlan.blockPos(), byteBuddy.getUUID(), claimTimeOut)) {
                            return false;
                        }
                        claimedTillPos = tillPlan.blockPos();
                        nextClaimRenewTill = serverLevel.getGameTime() + 5;
                    }
                }
            }

            targetPos = tillPlan.blockPos();
            approachPos = tillPlan.approachPos();
            targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
            edgeAnchored = false;
            approachPlans = buildTillEdgeApproachPlans(byteBuddy.level(), targetPos);
            anchorIndex = 0;
            resetProgress();
            enterPhase(GoalPhase.MOVING, "to till site " + targetPos.toShortString());
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

        return targetPos != null;
    }

    @Override
    public void stop() {
        clearTimedAnimation();
        releaseTillClaim();
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

            GoalUtil.renewClaimIfNeeded(byteBuddy, serverLevel, ByteBuddyEntity.TaskType.TILL,
                    claimedTillPos, targetPos,
                    serverLevel.getGameTime(),
                    5, claimTimeOut,
                    () -> nextClaimRenewTill,
                    ticks -> nextClaimRenewTill = ticks);

            BlockState tillBlockState = byteBuddy.level().getBlockState(targetPos);
            if (currentPhase != GoalPhase.ACTING) {
                if (!GoalUtil.canTillAt(byteBuddy.level(), targetPos)) {
                    clearTarget();
                    enterPhase(GoalPhase.IDLE, "till site invalid, rescan");
                    return;
                }
            }

            if (currentPhase == GoalPhase.MOVING) {
                if (edgeAnchored || (targetAnchor != null && Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor)) <= finalApproachDist)) {
                    if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
                        Path path = pathNavigation.getPath();
                        if (path != null && (serverLevel.getGameTime() % 5L) == 0L) {
                            byteBuddy.renewPathAhead(serverLevel, path, 5);
                        }
                    }
                    markProgress();
                } else {
                    Vec3 progressToTarget = (targetAnchor != null) ? targetAnchor : approachPos.getCenter();
                    double distSq = GoalUtil.hDistSq(byteBuddy.position(), progressToTarget);
                    if (distSq + 1.0e-3 < lastMoveDistSq) {
                        lastMoveDistSq = distSq;
                        markProgress();
                    }
                    if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
                        Path path = pathNavigation.getPath();
                        if (path != null && (serverLevel.getGameTime() % 5L) == 0L) {
                            byteBuddy.renewPathAhead(serverLevel, path, 5);
                        }
                    }
                    if (stalledFor(movingTimeout / 10)) {
                        GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                        if (repath()) {
                            GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
                            BotDebug.log(byteBuddy, "MOVING: repath");
                            markProgress();
                        } else if (rotateAnchor()) {
                            GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
                            BotDebug.log(byteBuddy, "MOVING: rotate targetPos side");
                            markProgress();
                        } else {
                            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                            clearTarget();
                            enterPhase(GoalPhase.IDLE, "MOVING stalled, rescan");
                            return;
                        }
                    }
                    if (timedOut(movingTimeout)) {
                        GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                        clearTarget();
                        enterPhase(GoalPhase.IDLE, "MOVING timeout, rescan");
                        return;
                    }
                }
            } else if (currentPhase == GoalPhase.ACTING) {
                if (animationEnd > 0) {
                    markProgress();
                } else if (timedOut(actingTimeout)) {
                    BotDebug.log(byteBuddy, "ACTING timeout; abort");
                    clearTarget();
                    enterPhase(GoalPhase.IDLE, "abort act");
                    return;
                }
            } else if (currentPhase == GoalPhase.SEEKING) {
                if (timedOut(seekingTimout)) {
                    if (targetReselectRetries++ < 2) {
                        TillPlan tillPlan = findTillPlan();
                        if (tillPlan != null) {
                            DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
                            if (dockBlock != null && !dockBlock.tryClaim(serverLevel, ByteBuddyEntity.TaskType.TILL,
                                    tillPlan.blockPos(), byteBuddy.getUUID(), claimTimeOut)) {
                                enterPhase(GoalPhase.SEEKING, "seek retry (claim failed)");
                                return;
                            }
                            claimedTillPos = tillPlan.blockPos();
                            nextClaimRenewTill = serverLevel.getGameTime() + 5;

                            targetPos = tillPlan.blockPos();
                            approachPos = tillPlan.approachPos();
                            targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
                            approachPlans = buildTillEdgeApproachPlans(byteBuddy.level(), targetPos);
                            anchorIndex = 0;
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
                double horizontalDist = Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor));

                if (horizontalDist > finalApproachDist) {
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
                    if (horizontalDist <= microDistMin) {
                        GoalUtil.lockToAnchor(byteBuddy, targetAnchor);
                        edgeAnchored = true;
                        markProgress();
                        BotDebug.log(byteBuddy, "TILL: locked anchor");
                    } else {
                        byteBuddy.getNavigation().stop();
                        byteBuddy.getMoveControl().setWantedPosition(
                                targetAnchor.x, targetAnchor.y, targetAnchor.z,
                                byteBuddy.actionSpeedMultiplier());
                        if (horizontalDist + 1.0e-3 < lastAnchorDistSq) {
                            lastAnchorDistSq = horizontalDist;
                            markProgress();
                        }
                        BotDebug.log(byteBuddy, String.format("final-targetPos dH=%.3f to edge %s",
                                horizontalDist, approachPos.toShortString()));
                    }
                } else {
                    double distSq = Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor));
                    if (distSq > microDistMax) edgeAnchored = false;
                }
            } else {
                if (approachPos != null && byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
                    GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                    Path path = pathNavigation.createPath(approachPos, 0);
                    if (path != null) {
                        pathNavigation.moveTo(path, byteBuddy.actionSpeedMultiplier());
                        GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
                    } else {
                        Vec3 approachCenter = approachPos.getCenter();
                        byteBuddy.getNavigation().moveTo(approachCenter.x, approachPos.getY(), approachCenter.z, byteBuddy.actionSpeedMultiplier());
                    }
                    return;
                }
            }

            Vec3 buddyPos = byteBuddy.position();
            boolean withinReachTill = GoalUtil.hDistSq(buddyPos, cropCenter) <= (reachDistanceMin * reachDistanceMin)
                    && Math.abs(buddyPos.y - cropCenter.y) <= verticalTolerance;

            if (currentPhase != GoalPhase.ACTING) {
                if (!GoalUtil.canTillAt(byteBuddy.level(), targetPos)) {
                    clearTarget();
                    enterPhase(GoalPhase.IDLE, "till site invalid, rescan");
                    return;
                }
            }

            if (withinReachTill) {
                if (animationEnd > 0 || currentPhase == GoalPhase.ACTING) {
                    byteBuddy.getLookControl().setLookAt(cropCenter.x, targetPos.getBottomCenter().y, cropCenter.z, 15.0f, 15.0f);
                } else {
                    if (!GoalUtil.actionReady(serverLevel, nextActionTick)) return;
                    if (!verifyClaimOrAbort(serverLevel, ByteBuddyEntity.TaskType.TILL, claimedTillPos, targetPos))
                        return;

                    firePos = targetPos;
                    firePreState = tillBlockState;

                    int totalTicks = GoalUtil.toTicks(2.0);
                    int startTicks = GoalUtil.toTicks(0.4);

                    startTimedAnimation(
                            totalTicks,
                            startTicks,
                            targetPos,
                            tillBlockState
                    );

                    BotDebug.log(byteBuddy, "TILL schedule: now=" + serverLevel.getGameTime() +
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

    @Nullable
    private TillPlan findTillPlan() {
        BlockPos dockPos = byteBuddy.getDock().orElse(null);
        if (dockPos == null) return null;

        final int radius = byteBuddy.effectiveRadius();
        Level level = byteBuddy.level();
        DockingStationBlockEntity dockBlock = dockBlockEntity(byteBuddy);
        if (!(level instanceof ServerLevel serverLevel)) return null;

        MutableBlockPos mutableDockPos = new MutableBlockPos();
        List<ScoredTillPlan> scores = new ArrayList<>();
        Vec3 buddyPos = byteBuddy.position();

        for (int y = -1; y <= 2; y++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutableDockPos.set(dockPos.getX() + dx, dockPos.getY() + y, dockPos.getZ() + dz);

                    if (!GoalUtil.canTillAt(level, mutableDockPos)) continue;
                    if (dockBlock != null && dockBlock.isReserved(serverLevel, ByteBuddyEntity.TaskType.TILL, mutableDockPos)) continue;
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
                        score += Math.min(waterDistance, hydrationRadius) * waterDistanceBonus;
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

        if (scores.isEmpty()) return null;

        scores.sort(Comparator.comparingDouble(ScoredTillPlan::candidateScore));
        var chosenPlan = scores.get(0).tillPlan();

        if (dockBlock != null) {
            if (!dockBlock.tryClaim(serverLevel, ByteBuddyEntity.TaskType.TILL, chosenPlan.blockPos(), byteBuddy.getUUID(), claimTimeOut)) {
                return null;
            }
            claimedTillPos = chosenPlan.blockPos();
            nextClaimRenewTill = serverLevel.getGameTime() + 5;
        }

        this.approachPlans = buildTillEdgeApproachPlans(level, chosenPlan.blockPos());
        this.anchorIndex = 0;
        this.approachPos  = chosenPlan.approachPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(chosenPlan.blockPos(), chosenPlan.approachPos());
        this.edgeAnchored = false;

        return chosenPlan;
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

        Consumer<BlockPos> addIfGood = standable -> {
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, standable)) return;
            Vec3 edgeAnchor = GoalUtil.getEdgeAnchor(blockPos, standable);
            if (edgeAnchor == null) return;

            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) ? pathNavigation.createPath(standable, 0) : null;
            if (path == null) return;

            double horizontalDist = GoalUtil.hDistSq(byteBuddy.position(), edgeAnchor);
            list.add(new Approach(standable, edgeAnchor, horizontalDist, path));
        };

        for (BlockPos blockPositions : horizontal) addIfGood.accept(blockPositions);
        for (BlockPos blockPositionsPlusOne : horizontalPlusOne) addIfGood.accept(blockPositionsPlusOne);

        list.sort(Comparator.comparingDouble(Approach::distSq));
        return list;
    }

    private void performTill(BlockPos blockPos) {
        Level level = byteBuddy.level();
        if (level instanceof ServerLevel serverLevel) {
            if (!verifyClaimOrAbort(serverLevel, ByteBuddyEntity.TaskType.TILL, claimedTillPos, blockPos)) return;
        }

        if (!GoalUtil.canTillAt(level, blockPos)) {
            BotDebug.log(byteBuddy, "TILL invalid at " + blockPos.toShortString());
            releaseTillClaim();
            return;
        }

        int energyCost = 25;
        if (!byteBuddy.consumeEnergy(energyCost)) {
            failTask(BotDebug.FailReason.OUT_OF_ENERGY, "need=" + energyCost);
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
        }

        BlockState soilCandidate = level.getBlockState(blockPos);
        if (soilCandidate.is(Blocks.COARSE_DIRT)) {
            level.setBlock(blockPos, Blocks.DIRT.defaultBlockState(), 3);
        } else {
            level.setBlock(blockPos, Blocks.FARMLAND.defaultBlockState(), 3);
        }

        byteBuddy.onTaskSuccess(ByteBuddyEntity.TaskType.TILL, blockPos);
        releaseTillClaim();
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
            TillGoal.Approach approachCandidates = approachPlans.get(anchorIndex);
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

            enterPhase(GoalPhase.ACTING, "animation: TILL fire@" + startTick + " end@" + totalTicks);
        }
    }

    private void tickTimedAnimation() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            long currentTime = serverLevel.getGameTime();
            if (!actionStarted && currentTime >= animationStart && firePos != null && firePreState != null) {
                actionStarted = true;
                BotDebug.log(byteBuddy, "TILL anim: now=" + currentTime +
                        " start=" + animationStart + " end=" + animationEnd +
                        " fired=" + actionStarted + " firePos=" + (firePos != null));

                performTill(firePos);
            }

            if (currentPhase == GoalPhase.ACTING && animationEnd > 0 && currentTime >= animationEnd) {
                clearTimedAnimation();
                clearTarget();
                enterPhase(GoalPhase.IDLE, "TILL: complete");
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

    private void releaseTillClaim() {
        DockingStationBlockEntity dockBlock = dockBlockEntity(byteBuddy);
        if (claimedTillPos != null && dockBlock != null) {
            dockBlock.releaseClaim(ByteBuddyEntity.TaskType.TILL, claimedTillPos, byteBuddy.getUUID());
        }
        claimedTillPos = null;
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
        releaseTillClaim();
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
