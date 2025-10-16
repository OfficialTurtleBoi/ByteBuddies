package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.TaskType;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;

public class QuarryGoal extends Goal {
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

    @Nullable private BlockPos claimedMinePos = null;
    @Nullable private BlockPos firePos = null;
    @Nullable private BlockState firePreState = null;
    private static final int claimTimeout = 120;
    private long nextClaimRenewMine = 0L;

    private long animationStart = 0L;
    private long animationEnd = 0L;
    private boolean actionStarted = false;

    private static final int mineEnergyCost = 30;

    public QuarryGoal(ByteBuddyEntity byteBuddy) {
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

        if (targetPos != null) return true;

        if (byteBuddy.getBuddyRole() != ByteBuddyEntity.BuddyRole.MINER) {
            failTask(BotDebug.FailReason.WRONG_ROLE, "role=" + byteBuddy.getBuddyRole());
            return false;
        }

        if (byteBuddy.getDock().isEmpty()) {
            failTask(BotDebug.FailReason.NO_DOCK, "no station bound");
            return false;
        }

        var plan = findMinePlan();
        if (plan.isEmpty()) return false;

        this.targetPos = plan.get().breakPos();
        this.approachPos = plan.get().standPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
        this.edgeAnchored = false;

        resetProgress();
        enterPhase(GoalPhase.MOVING, "to mine " + targetPos.toShortString());
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

        return targetPos != null;
    }

    @Override
    public void stop() {
        clearTimedAnimation();
        releaseMineClaim();
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

            GoalUtil.renewClaimIfNeeded(
                    byteBuddy, serverLevel, TaskType.MINE,
                    claimedMinePos, targetPos,
                    serverLevel.getGameTime(), 5, claimTimeout,
                    () -> nextClaimRenewMine, t -> nextClaimRenewMine = t
            );

            BlockState state = byteBuddy.level().getBlockState(targetPos);
            if (currentPhase != GoalPhase.ACTING && !GoalUtil.canMineAt(byteBuddy.level(), targetPos)) {
                clearTarget();
                enterPhase(GoalPhase.IDLE, "target invalid");
                return;
            }

            Vec3 center = targetPos.getCenter();

            if (currentPhase == GoalPhase.MOVING) {
                if (edgeAnchored || (targetAnchor != null && Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor)) <= finalApproachDist)) {
                    maybeRenewPathAhead(serverLevel);
                    markProgress();
                } else {
                    Vec3 goal = (targetAnchor != null) ? targetAnchor : approachPos.getCenter();
                    double d2 = GoalUtil.hDistSq(byteBuddy.position(), goal);
                    if (d2 + 1e-3 < lastMoveDistSq) {
                        lastMoveDistSq = d2;
                        markProgress();
                    }
                    maybeRenewPathAhead(serverLevel);

                    if (stalledFor(movingTimeout / 10)) {
                        GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                        if (repath()) {
                            GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
                            markProgress();
                        } else if (rotateAnchor()) {
                            GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
                            markProgress();
                        } else {
                            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                            clearTarget();
                            enterPhase(GoalPhase.IDLE, "stalled");
                            return;
                        }
                    }
                    if (timedOut(movingTimeout)) {
                        GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                        clearTarget();
                        enterPhase(GoalPhase.IDLE, "move timeout");
                        return;
                    }
                }
            } else if (currentPhase == GoalPhase.SEEKING) {
                if (timedOut(seekingTimout)) {
                    if (anchorRotateRetries++ < 2) {
                        var plan = findMinePlan();
                        if (plan.isPresent()) {
                            targetPos = plan.get().breakPos();
                            approachPos = plan.get().standPos();
                            targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
                            resetProgress();
                            enterPhase(GoalPhase.MOVING, "retry seek -> moving");
                        } else enterPhase(GoalPhase.IDLE, "seek exhausted");
                    } else enterPhase(GoalPhase.IDLE, "seek timeout");
                    return;
                }
            }

            if (targetAnchor != null) {
                double dH = Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor));
                if (dH > finalApproachDist) {
                    ensureMovingToAnchor(serverLevel);
                    return;
                }

                if (!edgeAnchored) {
                    GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                    if (dH <= microDistMin) {
                        GoalUtil.lockToAnchor(byteBuddy, targetAnchor);
                        edgeAnchored = true;
                        markProgress();
                    } else {
                        byteBuddy.getNavigation().stop();
                        byteBuddy.getMoveControl().setWantedPosition(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
                        if (dH + 1e-3 < lastAnchorDistSq) {
                            lastAnchorDistSq = dH;
                            markProgress();
                        }
                    }
                } else {
                    double back = Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor));
                    if (back > microDistMax) edgeAnchored = false;
                }
            } else {
                // fallback: move to standPos center
                if (approachPos != null && byteBuddy.getNavigation() instanceof GroundPathNavigation nav) {
                    GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                    Path p = nav.createPath(approachPos, 0);
                    if (p != null) {
                        nav.moveTo(p, byteBuddy.actionSpeedMultiplier());
                        GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
                    } else {
                        Vec3 ac = approachPos.getCenter();
                        byteBuddy.getNavigation().moveTo(ac.x, approachPos.getY(), ac.z, byteBuddy.actionSpeedMultiplier());
                    }
                    return;
                }
            }

            // within reach to mine?
            Vec3 bp = byteBuddy.position();
            boolean withinReach = GoalUtil.hDistSq(bp, center) <= (reachDistanceMin * reachDistanceMin) && Math.abs(bp.y - center.y) <= verticalTolerance;

            if (withinReach) {
                if (animationEnd > 0 || currentPhase == GoalPhase.ACTING) {
                    byteBuddy.getLookControl().setLookAt(center.x, targetPos.getBottomCenter().y, center.z, 15f, 15f);
                } else {
                    if (!GoalUtil.actionReady(serverLevel, nextActionTick)) return;
                    if (!verifyClaimOrAbort(serverLevel, TaskType.MINE, claimedMinePos, targetPos)) return;

                    firePos = targetPos;
                    firePreState = state;

                    int totalTicks = GoalUtil.toTicks(2.0);
                    int startTicks = GoalUtil.toTicks(0.4);
                    startTimedAnimation(totalTicks, startTicks, targetPos, state);

                    BotDebug.log(byteBuddy, "MINE schedule: now=" + serverLevel.getGameTime() +
                            " start=" + (serverLevel.getGameTime() + startTicks) +
                            " end=" + (serverLevel.getGameTime() + totalTicks) +
                            " firePos=" + firePos + " pre=" + state.getBlock().getName().getString());
                    return;
                }
            }

            if (targetAnchor != null && !edgeAnchored) {
                byteBuddy.getMoveControl().setWantedPosition(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
            }
        }
    }

    private record MinePlan(BlockPos breakPos, BlockPos standPos, @Nullable Path path, double score) {}
    private Optional<MinePlan> findMinePlan() {
        BlockPos dock = byteBuddy.getDock().orElse(null);
        if (dock == null) return Optional.empty();
        Level level = byteBuddy.level();
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();

        int r = byteBuddy.effectiveRadius();
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);

        ArrayList<MinePlan> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = 2; y >= -64; y--) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(dock.getX() + dx, dock.getY() + y, dock.getZ() + dz);

                    BlockState state = level.getBlockState(cursor);
                    if (!GoalUtil.canMineAt(level, cursor)) continue;

                    if (dockBlock != null && dockBlock.isReserved(serverLevel, TaskType.MINE, cursor)) continue;

                    if (!level.getBlockState(cursor.above()).getCollisionShape(level, cursor.above()).isEmpty()) continue;
                    var approaches = buildMineEdgeApproachPlans(level, cursor.immutable());
                    if (approaches.isEmpty()) continue;

                    Approach best = approaches.get(0);

                    double score = 0.0;
                    score += GoalUtil.hDistSq(byteBuddy.position(), best.approachAnchor());
                    score += (dock.getY() - cursor.getY()) * 4.0;
                    if (!level.getFluidState(cursor).isEmpty()) score += 1e6;

                    candidates.add(new MinePlan(cursor.immutable(), best.targetPos(), best.path(), score));
                }
            }
            if (!candidates.isEmpty()) break;
        }

        if (candidates.isEmpty()) return Optional.empty();
        candidates.sort(Comparator.comparingDouble(MinePlan::score));
        MinePlan pick = candidates.get(0);

        if (dockBlock != null) {
            if (!dockBlock.tryClaim(serverLevel, TaskType.MINE, pick.breakPos(), byteBuddy.getUUID(), claimTimeout)) return Optional.empty();
            this.claimedMinePos = pick.breakPos();
            this.nextClaimRenewMine = serverLevel.getGameTime() + 5;
        }

        this.approachPlans = buildMineEdgeApproachPlans(level, pick.breakPos());
        this.anchorIndex = 0;
        this.approachPos  = pick.standPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(pick.breakPos(), pick.standPos());
        this.edgeAnchored = false;

        return Optional.of(pick);
    }

    private List<Approach> buildMineEdgeApproachPlans(Level level, BlockPos blockPos) {
        ArrayList<Approach> list = new ArrayList<>(8);
        BlockPos[] horizontal = new BlockPos[] {
                blockPos.east(), blockPos.west(), blockPos.north(), blockPos.south()
        };
        BlockPos[] horizontalPlusOne = new BlockPos[] {
                blockPos.east().above(), blockPos.west().above(), blockPos.north().above(), blockPos.south().above()
        };

        java.util.function.Consumer<BlockPos> addIfGood = standable -> {
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, standable)) return;
            Vec3 edgeAnchor = GoalUtil.getEdgeAnchor(blockPos, standable);
            if (edgeAnchor == null) return;

            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation)
                    ? pathNavigation.createPath(standable, 0)
                    : null;
            if (path == null) return;

            double horizontalDist = GoalUtil.hDistSq(byteBuddy.position(), edgeAnchor);
            list.add(new Approach(standable, edgeAnchor, horizontalDist, path));
        };

        for (BlockPos blockPositions : horizontal) addIfGood.accept(blockPositions);
        for (BlockPos blockPositionsPlusOne : horizontalPlusOne) addIfGood.accept(blockPositionsPlusOne);

        list.sort(Comparator.comparingDouble(Approach::distSq));
        return list;
    }

    private void performMine(BlockPos pos, BlockState pre) {
        Level level = byteBuddy.level();
        if (!GoalUtil.canMineAt(level, pos)) {
            BotDebug.log(byteBuddy, "MINE invalid at " + pos.toShortString());
            releaseMineClaim(); return;
        }

        if (!byteBuddy.consumeEnergy(mineEnergyCost)) {
            releaseMineClaim(); return;
        }
        ToolUtil.applyToolWear(byteBuddy, ToolUtil.ToolType.PICKAXE, byteBuddy.toolWearMultiplier());

        if (level instanceof ServerLevel serverLevel) {
            var drops = Block.getDrops(pre, serverLevel, pos, null, byteBuddy, ItemStack.EMPTY);
            Vec3 c = pos.getCenter();
            int inserted = 0, dropped = 0;
            for (ItemStack stack : drops) {
                ItemStack rem = InventoryUtil.mergeInto(byteBuddy.getMainInv(), stack);
                if (!rem.isEmpty()) { Containers.dropItemStack(level, c.x, c.y, c.z, rem); dropped += rem.getCount(); }
                else inserted += stack.getCount();
            }
            level.destroyBlock(pos, false);
            BuddyDebugLog(pos, inserted, dropped);
        }

        byteBuddy.onTaskSuccess(TaskType.MINE, pos);
        releaseMineClaim();
    }

    private void BuddyDebugLog(BlockPos pos, int inserted, int dropped) {
        BotDebug.log(byteBuddy, "MINE at " + pos.toShortString() + " inserted=" + inserted + " dropped=" + dropped);
        BotDebug.mark(byteBuddy.level(), pos);
    }


    private void maybeRenewPathAhead(ServerLevel serverLevel) {
        if (byteBuddy.getNavigation() instanceof GroundPathNavigation nav) {
            Path p = nav.getPath();
            if (p != null && (serverLevel.getGameTime() % 5L) == 0L) byteBuddy.renewPathAhead(serverLevel, p, 5);
        }
    }

    private void ensureMovingToAnchor(ServerLevel serverLevel) {
        if (byteBuddy.getNavigation() instanceof GroundPathNavigation nav) {
            Path cur = nav.getPath();
            boolean needsNew = cur == null || cur.isDone() || approachPos == null || !cur.getTarget().equals(approachPos);
            if (needsNew) {
                GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                Path p = (approachPos != null) ? nav.createPath(approachPos, 0) : null;
                if (p != null) { nav.moveTo(p, byteBuddy.actionSpeedMultiplier()); GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5); }
                else byteBuddy.getNavigation().moveTo(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
            }
        } else {
            byteBuddy.getNavigation().moveTo(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
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

            byteBuddy.setSlamming(true);

            this.actionStarted = false;
            long currentTime = serverLevel.getGameTime();
            this.animationStart = currentTime + Math.max(0, startTick);
            this.animationEnd = currentTime + Math.max(1, totalTicks);

            this.firePos = fireAt;
            this.firePreState = preState;

            float speedMultiplier = Math.max(0.25f, byteBuddy.actionSpeedMultiplier());
            this.nextActionTick = currentTime + Math.max(4, Math.round(baseActionCooldown / speedMultiplier));

            enterPhase(GoalPhase.ACTING, "animation: MINE fire@" + startTick + " end@" + totalTicks);
        }
    }

    private void tickTimedAnimation() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            long currentTime = serverLevel.getGameTime();
            if (!actionStarted && currentTime >= animationStart && firePos != null && firePreState != null) {
                actionStarted = true;
                BotDebug.log(byteBuddy, "MINE anim: now=" + currentTime +
                        " start=" + animationStart + " end=" + animationEnd +
                        " fired=" + actionStarted + " firePos=" + (firePos != null));

                performMine(firePos, firePreState);
            }

            if (currentPhase == GoalPhase.ACTING && animationEnd > 0 && currentTime >= animationEnd) {
                clearTimedAnimation();
                clearTarget();
                enterPhase(GoalPhase.IDLE, "MINE: complete");
            }
        }
    }

    private void clearTimedAnimation() {
        actionStarted = false;
        animationStart = animationEnd = 0L;
        byteBuddy.setSlamming(false);
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

    private void releaseMineClaim() {
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (claimedMinePos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.MINE, claimedMinePos, byteBuddy.getUUID());
        }
        claimedMinePos = null;
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

        BotDebug.log(byteBuddy, "MINER: " + phase + (context.isEmpty() ? "" : " -> " + context));
    }

    private void clearTarget() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
        }
        releaseMineClaim();
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
        BotDebug.log(byteBuddy, "MINER cannot start: " + reason + (context.isEmpty() ? "" : " (" + context + ")"));
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

