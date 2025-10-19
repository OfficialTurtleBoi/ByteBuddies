package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.turtleboi.bytebuddies.block.custom.DockingStationBlock;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;

import static net.turtleboi.bytebuddies.block.custom.DockingStationBlock.OPEN;

public class DepositToDockGoal extends Goal {
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

    private static final int movingTimeout = 160;
    private static final int actingTimeout = 60;

    private double lastMoveDistSq = Double.POSITIVE_INFINITY;
    private double lastAnchorDistSq = Double.POSITIVE_INFINITY;

    private static final double reachDistanceMin = 0.95;
    private static final double verticalTolerance = 1.66;

    private static final double finalApproachDist = 1.66;
    private static final double microDistMin = 0.15;
    private static final double microDistMax = 0.28;

    @Nullable private BlockPos firePos = null;
    @Nullable private BlockState firePreState = null;
    private long animationStart = 0L;
    private long animationEnd = 0L;
    private boolean actionStarted = false;

    public DepositToDockGoal(ByteBuddyEntity byteBuddy) {
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

        if (byteBuddy.getDock().isEmpty()) {
            failTask(BotDebug.FailReason.NO_DOCK, "no station bound");
            return false;
        }

        if (!isInventoryFull(byteBuddy.getMainInv())) {
            return false;
        }

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, 1, 64)) {
            return false;
        }

        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (dockBlock == null) return false;

        BlockPos dockPos = dockBlock.getBlockPos();
        var plan = findDockPlan(dockPos);
        if (plan.isEmpty()) return false;

        this.targetPos = plan.get().targetPos();
        this.approachPos = plan.get().approachPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(this.targetPos, this.approachPos);
        this.edgeAnchored = false;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "approach dock " + approachPos.toShortString());
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

        return GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, 1, 64);
    }

    @Override
    public void stop() {
        clearTimedAnimation();
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
        if (!(byteBuddy.level() instanceof ServerLevel serverLevel)) return;

        tickTimedAnimation();
        if (targetPos == null || approachPos == null) return;

        Vec3 targetCenter = targetPos.getCenter();
        if (currentPhase == GoalPhase.ACTING) {
            byteBuddy.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z, 15.0f, 15.0f);
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
                    GoalUtil.lockToAnchor(byteBuddy, targetAnchor);
                    edgeAnchored = true;
                    markProgress();
                    BotDebug.log(byteBuddy, "DEPOSIT: locked anchor");
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
        boolean withinReach = GoalUtil.hDistSq(buddyPos, targetCenter) <= (reachDistanceMin * reachDistanceMin)
                && Math.abs(buddyPos.y - targetCenter.y) <= verticalTolerance;

        if (withinReach) {
            if (animationEnd > 0 || currentPhase == GoalPhase.ACTING) {
                byteBuddy.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z, 15.0f, 15.0f);
            } else {
                if (!GoalUtil.actionReady(serverLevel, nextActionTick)) return;
                //if (!verifyClaimOrAbort(serverLevel, ByteBuddyEntity.TaskType.HAUL, claimedHaulPos, targetPos)) return;

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

                BotDebug.log(byteBuddy, "DEPOSIT schedule now=" + serverLevel.getGameTime());
            }
        }

        if (targetAnchor != null && !edgeAnchored) {
            byteBuddy.getMoveControl().setWantedPosition(targetAnchor.x, targetAnchor.y, targetAnchor.z, byteBuddy.actionSpeedMultiplier());
        }
    }

    private void navigatePhases(ServerLevel serverLevel) {
        switch (currentPhase) {
            case MOVING -> handleMoving(serverLevel);
            case ACTING -> handleActing();
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
                enterPhase(GoalPhase.IDLE, "MOVING stalled");
            }
            return;
        }

        if (timedOut(movingTimeout)) {
            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
            clearTarget();
            enterPhase(GoalPhase.IDLE, "MOVING timeout");
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
        if (!(byteBuddy.getNavigation() instanceof GroundPathNavigation navigation)) return;
        Path path = navigation.getPath();
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
            enterPhase(GoalPhase.IDLE, "abort deposit");
        }
    }

    private boolean repath() {
        if (byteBuddy.getNavigation() instanceof GroundPathNavigation navigation) {
            if (repathRetries++ >= 2) return false;
            if (approachPos == null) return false;
            if (byteBuddy.level() instanceof ServerLevel) GoalUtil.releaseCurrentPathIfAny(byteBuddy);
            Path path = navigation.createPath(approachPos, 0);
            if (path == null) return false;
            navigation.moveTo(path, byteBuddy.actionSpeedMultiplier());
            if (byteBuddy.level() instanceof ServerLevel serverLevel) GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);
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
            Approach candidate = approachPlans.get(anchorIndex);
            BlockPos stand = candidate.targetPos();
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, byteBuddy.level(), stand)) continue;

            Vec3 anchor = candidate.approachAnchor();
            if (anchor == null) continue;

            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation navigation) ? navigation.createPath(stand, 0) : null;
            if (path == null) continue;

            approachPos = stand;
            targetAnchor = anchor;
            edgeAnchored = false;

            if (byteBuddy.level() instanceof ServerLevel) GoalUtil.releaseCurrentPathIfAny(byteBuddy);
            byteBuddy.getNavigation().moveTo(path, byteBuddy.actionSpeedMultiplier());
            if (byteBuddy.level() instanceof ServerLevel serverLevel) GoalUtil.reserveCurrentPathIfAny(serverLevel, byteBuddy, 5);

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

            enterPhase(GoalPhase.ACTING, "animation: DEPOSIT");
        }
    }

    private void tickTimedAnimation() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {

            long currentTime = serverLevel.getGameTime();
            if (!actionStarted && currentTime >= animationStart && firePos != null && firePreState != null) {
                actionStarted = true;
                BotDebug.log(byteBuddy, "DEPOSIT anim: now=" + currentTime +
                        " start=" + animationStart + " end=" + animationEnd +
                        " fired=" + actionStarted + " firePos=" + (firePos != null));
                actionStarted = true;
                performDeposit(serverLevel, firePos);
                if (serverLevel.getBlockEntity(firePos) instanceof DockingStationBlockEntity dockBlock) {
                    serverLevel.setBlock(firePos, dockBlock.getBlockState().setValue(OPEN, Boolean.FALSE), Block.UPDATE_ALL);
                }
            }

            if (currentPhase == GoalPhase.ACTING && animationEnd > 0 && currentTime >= animationEnd) {
                clearTimedAnimation();
                clearTarget();
                enterPhase(GoalPhase.IDLE, "DEPOSIT complete");
            }
        }
    }

    private void clearTimedAnimation() {
        actionStarted = false;
        animationStart = animationEnd = 0L;
        byteBuddy.setWorking(false);
    }

    private record DockPlan(BlockPos targetPos, BlockPos approachPos, Path path) {}
    private Optional<DockPlan> findDockPlan(BlockPos dockPos) {
        Level level = byteBuddy.level();
        var blockState = level.getBlockState(dockPos);
        var facing = blockState.getValue(DockingStationBlock.FACING);
        BlockPos approachPos = dockPos.relative(facing);

        if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, approachPos)) {
            return Optional.empty();
        }

        Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) ? pathNavigation.createPath(approachPos, 0) : null;
        if (path == null) return Optional.empty();

        Vec3 edgeAnchor = GoalUtil.getEdgeAnchor(dockPos, approachPos);
        if (edgeAnchor == null) return Optional.empty();

        this.approachPlans = List.of(new Approach(approachPos, edgeAnchor, GoalUtil.hDistSq(byteBuddy.position(), edgeAnchor), path));
        this.anchorIndex = 0;

        return Optional.of(new DockPlan(dockPos, approachPos, path));
    }

    private void performDeposit(ServerLevel serverLevel, BlockPos dockPos) {
        IItemHandler dockInventory = serverLevel.getCapability(Capabilities.ItemHandler.BLOCK, dockPos, null);
        if (dockInventory == null) return;

        int totalMoved = 0;
        for (int slot = 0; slot < byteBuddy.getMainInv().getSlots(); slot++) {
            ItemStack stackInSlot = byteBuddy.getMainInv().getStackInSlot(slot);
            if (stackInSlot.isEmpty()) continue;

            ItemStack simulatedRemainder = tryInsertAll(dockInventory, stackInSlot, true);
            int insertableAmount = stackInSlot.getCount() - simulatedRemainder.getCount();
            if (insertableAmount <= 0) continue;

            ItemStack itemRemainder = tryInsertAll(dockInventory, stackInSlot.copyWithCount(insertableAmount), false);
            int insertedAmount = insertableAmount - itemRemainder.getCount();
            if (insertedAmount > 0) {
                byteBuddy.getMainInv().extractItem(slot, insertedAmount, false);
                totalMoved += insertedAmount;
            }
        }

        if (totalMoved > 0) {
            byteBuddy.consumeEnergy(totalMoved);
            BotDebug.log(byteBuddy, "DEPOSIT: moved " + totalMoved + " items to dock");
        }
    }

    private ItemStack tryInsertAll(IItemHandler destinationInventory, ItemStack itemStack, boolean simulate) {
        ItemStack itemRemainder = itemStack.copy();
        for (int slot = 0; slot < destinationInventory.getSlots(); slot++) {
            itemRemainder = destinationInventory.insertItem(slot, itemRemainder, simulate);
            if (itemRemainder.isEmpty()) break;
        }
        return itemRemainder;
    }

    private boolean isInventoryFull(IItemHandler inventory) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (inventory.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
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

        if (phase == GoalPhase.ACTING && byteBuddy.level() instanceof ServerLevel) {
            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
        }

        BotDebug.log(byteBuddy, "DEPOSITOR: " + phase + (context.isEmpty() ? "" : " -> " + context));
    }

    private void clearTarget() {
        if (byteBuddy.level() instanceof ServerLevel) {
            GoalUtil.releaseCurrentPathIfAny(byteBuddy);
        }
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
        BotDebug.log(byteBuddy, "DEPOSITOR cannot start: " + reason + (context.isEmpty() ? "" : " (" + context + ")"));
        resetProgress();
        stop();
    }

    private void resetProgress() {
        lastMoveDistSq = Double.POSITIVE_INFINITY;
        lastAnchorDistSq = Double.POSITIVE_INFINITY;
        repathRetries = 0;
        anchorRotateRetries = 0;
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
