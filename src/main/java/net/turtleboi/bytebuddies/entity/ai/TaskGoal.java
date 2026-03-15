package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.TaskType;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public abstract class TaskGoal extends Goal {

    protected final ByteBuddyEntity byteBuddy;

    public record Approach(BlockPos targetPos, Vec3 approachAnchor, double distSq, Path path) {}

    @Nullable protected BlockPos targetPos;
    @Nullable protected BlockPos approachPos;
    protected List<Approach> approachPlans = Collections.emptyList();
    protected int anchorIndex = 0;
    @Nullable protected Vec3 targetAnchor = null;
    protected boolean edgeAnchored = false;

    protected long nextActionTick = 0L;
    protected static final int baseActionCooldown = 20;
    protected static final int seekingTimeout = 20;
    protected static final int actingTimeout = 60;
    protected static final int claimTimeOut = 120;
    protected static final double reachDistanceMin = 0.95;

    protected GoalPhase currentPhase = GoalPhase.IDLE;
    protected BotDebug.FailReason lastFail = BotDebug.FailReason.NONE;
    protected long phaseStartedTick = 0L;
    protected long phaseProgressTick = 0L;
    protected int repathRetries = 0;
    protected int anchorRotateRetries = 0;
    protected int targetReselectRetries = 0;

    protected final int movingTimeout;
    protected final double finalApproachDist;
    protected final double microDistMin;
    protected final double microDistMax;
    protected final double verticalTolerance;

    protected double lastMoveDistSq = Double.POSITIVE_INFINITY;
    protected double lastAnchorDistSq = Double.POSITIVE_INFINITY;

    @Nullable protected BlockPos firePos = null;
    @Nullable protected BlockState firePreState = null;
    protected long animationStart = 0L;
    protected long animationEnd = 0L;
    protected boolean actionStarted = false;

    protected TaskGoal(ByteBuddyEntity byteBuddy, int movingTimeout,
                       double finalApproachDist, double microDistMin, double microDistMax, double verticalTolerance) {
        this.byteBuddy = byteBuddy;
        this.movingTimeout = movingTimeout;
        this.finalApproachDist = finalApproachDist;
        this.microDistMin = microDistMin;
        this.microDistMax = microDistMax;
        this.verticalTolerance = verticalTolerance;
        byteBuddy.setPathfindingMalus(BlockPathTypes.WATER, 8.0F);
        byteBuddy.setPathfindingMalus(BlockPathTypes.WATER_BORDER, 4.0F);
    }

    protected abstract String goalLabel();

    protected abstract void setActionActive(boolean active);

    protected abstract void performAction(BlockPos pos, BlockState pre);

    protected abstract void onReadyToAct(ServerLevel serverLevel, BlockPos pos, BlockState state);

    protected void releaseClaim() {}

    protected void handleSeeking() {}

    protected void renewClaim(ServerLevel serverLevel) {}

    protected boolean validateTarget(ServerLevel serverLevel, BlockState targetState) {
        return true;
    }

    protected void onAnimationComplete() {
        clearTarget();
        enterPhase(GoalPhase.IDLE, goalLabel() + ": complete");
    }

    protected double lookAtY(BlockPos pos, Vec3 center) {
        return pos.getY();
    }

    protected boolean withinReach(Vec3 buddyPos, Vec3 targetCenter) {
        return GoalUtil.hDistSq(buddyPos, targetCenter) <= (reachDistanceMin * reachDistanceMin)
                && Math.abs(buddyPos.y - targetCenter.y) <= verticalTolerance;
    }

    @Override
    public boolean isInterruptable() {
        return false;
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
    public void tick() {
        if (!(byteBuddy.level() instanceof ServerLevel serverLevel)) return;

        tickTimedAnimation();
        if (targetPos == null || approachPos == null) return;

        Vec3 targetCenter = targetPos.getCenter();
        if (currentPhase == GoalPhase.ACTING) {
            byteBuddy.getLookControl().setLookAt(targetCenter.x, lookAtY(targetPos, targetCenter), targetCenter.z, 15f, 15f);
            return;
        }

        renewClaim(serverLevel);
        BlockState targetState = byteBuddy.level().getBlockState(targetPos);
        if (!validateTarget(serverLevel, targetState)) return;

        navigatePhases(serverLevel);

        if (targetAnchor != null) {
            double distToTarget = byteBuddy.position().distanceTo(targetAnchor);

            if (distToTarget > finalApproachDist) {
                if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
                    Path currentPath = pathNavigation.getPath();
                    boolean needsNewPath = currentPath == null || currentPath.isDone()
                            || approachPos == null || !currentPath.getTarget().equals(approachPos);
                    if (needsNewPath) {
                        GoalUtil.releaseCurrentPathIfAny(byteBuddy);
                        Path path = (approachPos != null) ? pathNavigation.createPath(approachPos, 0) : null;
                        if (path != null) {
                            pathNavigation.moveTo(path, byteBuddy.actionSpeedMultiplier());
                            GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
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
                    edgeAnchored = GoalUtil.lockToAnchor(byteBuddy, targetAnchor);
                    if (edgeAnchored) markProgress();
                    BotDebug.log(byteBuddy, goalLabel() + ": locked anchor=" + edgeAnchored);
                } else {
                    byteBuddy.getNavigation().stop();
                    byteBuddy.getMoveControl().setWantedPosition(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
                    if (distToTarget + 1.0e-3 < lastAnchorDistSq) {
                        lastAnchorDistSq = distToTarget;
                        markProgress();
                    }
                    BotDebug.log(byteBuddy, String.format("final-targetPos dH=%.3f to edge %s", distToTarget, approachPos.toShortString()));
                }
            } else {
                if (Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor)) > microDistMax) {
                    edgeAnchored = false;
                }
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
        if (withinReach(buddyPos, targetCenter)) {
            if (animationEnd > 0 || currentPhase == GoalPhase.ACTING) {
                byteBuddy.getLookControl().setLookAt(targetCenter.x, lookAtY(targetPos, targetCenter), targetCenter.z, 15f, 15f);
            } else {
                if (!GoalUtil.actionReady(serverLevel, nextActionTick)) return;
                onReadyToAct(serverLevel, targetPos, targetState);
            }
        }

        if (targetAnchor != null && !edgeAnchored) {
            byteBuddy.getMoveControl().setWantedPosition(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
        }
    }

    protected void navigatePhases(ServerLevel serverLevel) {
        switch (currentPhase) {
            case MOVING -> handleMoving(serverLevel);
            case ACTING -> handleActing();
            case SEEKING -> handleSeeking();
            default -> {}
        }
    }

    protected void handleMoving(ServerLevel serverLevel) {
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

    protected void handleActing() {
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

    protected boolean isWithinFinalApproach() {
        if (edgeAnchored) return true;
        if (targetAnchor == null) return false;
        return Math.sqrt(GoalUtil.hDistSq(byteBuddy.position(), targetAnchor)) <= finalApproachDist;
    }

    protected void updateApproachProgress() {
        if (approachPos != null) {
            final Vec3 progressToTarget = (targetAnchor != null) ? targetAnchor : approachPos.getCenter();
            final double distSq = GoalUtil.hDistSq(byteBuddy.position(), progressToTarget);
            if (distSq + 1.0e-3 < lastMoveDistSq) {
                lastMoveDistSq = distSq;
                markProgress();
            }
        }
    }

    protected void renewPathAheadIfNeeded(ServerLevel serverLevel, int lookahead) {
        if (!(byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation)) return;
        Path path = pathNavigation.getPath();
        if (path == null) return;
        if ((serverLevel.getGameTime() % 5L) != 0L) return;
        byteBuddy.renewPathAhead(serverLevel, path, lookahead);
    }

    protected boolean tryRecoverFromStall(ServerLevel serverLevel) {
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

    protected boolean repath() {
        if (!(byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation)) return false;
        if (repathRetries++ >= 2) return false;
        if (approachPos == null) return false;
        if (byteBuddy.level() instanceof ServerLevel) GoalUtil.releaseCurrentPathIfAny(byteBuddy);
        Path path = pathNavigation.createPath(approachPos, 0);
        if (path == null) return false;
        pathNavigation.moveTo(path, byteBuddy.actionSpeedMultiplier());
        if (byteBuddy.level() instanceof ServerLevel sl) GoalUtil.reserveCurrentPathIfAny(sl, byteBuddy, 5);
        return true;
    }

    protected boolean rotateAnchor() {
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

            approachPos = stand;
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

    protected void startTimedAnimation(int totalTicks, int startTick, @Nullable BlockPos fireAt, @Nullable BlockState preState) {
        if (!(byteBuddy.level() instanceof ServerLevel serverLevel)) return;
        byteBuddy.getNavigation().stop();
        Vec3 delta = byteBuddy.getDeltaMovement();
        byteBuddy.setDeltaMovement(delta.x * 0.1, delta.y * 0.1, delta.z * 0.1);
        setActionActive(true);

        this.actionStarted = false;
        long now = serverLevel.getGameTime();
        this.animationStart = now + Math.max(0, startTick);
        this.animationEnd = now + Math.max(1, totalTicks);
        this.firePos = fireAt;
        this.firePreState = preState;

        float speedMul = Math.max(0.25f, byteBuddy.actionSpeedMultiplier());
        this.nextActionTick = now + Math.max(4, Math.round(baseActionCooldown / speedMul));

        enterPhase(GoalPhase.ACTING, "animation: " + goalLabel() + " fire@" + startTick + " end@" + totalTicks);
    }

    protected void tickTimedAnimation() {
        if (!(byteBuddy.level() instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getGameTime();
        if (!actionStarted && now >= animationStart && firePos != null && firePreState != null) {
            actionStarted = true;
            BotDebug.log(byteBuddy, goalLabel() + " anim: now=" + now +
                    " start=" + animationStart + " end=" + animationEnd +
                    " fired=true firePos=" + (firePos != null));
            performAction(firePos, firePreState);
        }
        if (currentPhase == GoalPhase.ACTING && animationEnd > 0 && now >= animationEnd) {
            clearTimedAnimation();
            onAnimationComplete();
        }
    }

    protected void clearTimedAnimation() {
        actionStarted = false;
        animationStart = animationEnd = 0L;
        setActionActive(false);
    }

    protected boolean verifyClaimOrAbort(ServerLevel serverLevel, TaskType taskType,
                                         @Nullable BlockPos claimedPos, @Nullable BlockPos currentTaskPos) {
        if (claimedPos == null) return false;
        if (currentTaskPos != null && !currentTaskPos.equals(claimedPos)) return false;
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (dockBlock == null) return false;
        boolean reserved = dockBlock.isReservedBy(serverLevel, taskType, claimedPos, byteBuddy.getUUID());
        if (!reserved) {
            BotDebug.log(byteBuddy, "lost " + taskType + " claim at " + claimedPos.toShortString() + " — aborting");
            clearTarget();
            enterPhase(GoalPhase.IDLE, "claim lost; rescan");
        }
        return reserved;
    }

    protected void enterPhase(GoalPhase phase, String context) {
        currentPhase = phase;
        lastFail = BotDebug.FailReason.NONE;
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

        BotDebug.log(byteBuddy, goalLabel() + ": " + phase + (context.isEmpty() ? "" : " -> " + context));
    }

    protected void clearTarget() {
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

    protected void failTask(BotDebug.FailReason reason, String context) {
        lastFail = reason;
        currentPhase = GoalPhase.IDLE;
        BotDebug.log(byteBuddy, goalLabel() + " cannot start: " + reason + (context.isEmpty() ? "" : " (" + context + ")"));
        resetProgress();
        stop();
    }

    protected void resetProgress() {
        lastMoveDistSq = Double.POSITIVE_INFINITY;
        lastAnchorDistSq = Double.POSITIVE_INFINITY;
        repathRetries = 0;
        anchorRotateRetries = 0;
        targetReselectRetries = 0;
        phaseStartedTick = phaseProgressTick = GoalUtil.getCurrentTime(byteBuddy);
    }

    protected void markProgress() {
        phaseProgressTick = GoalUtil.getCurrentTime(byteBuddy);
    }

    protected boolean stalledFor(int stallTime) {
        return GoalUtil.getCurrentTime(byteBuddy) - phaseProgressTick > stallTime;
    }

    protected boolean timedOut(int timeLimit) {
        return GoalUtil.getCurrentTime(byteBuddy) - phaseStartedTick > timeLimit;
    }
}
