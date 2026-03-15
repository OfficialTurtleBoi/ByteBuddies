package net.turtleboi.bytebuddies.entity.ai.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.ai.TaskGoal;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.TaskType;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;

import static net.turtleboi.bytebuddies.block.custom.DockingStationBlock.OPEN;

public class HaulerGoal extends TaskGoal {
    private static final int buddyHaulStart = 9;

    private Step step = Step.TO_SOURCE;
    private boolean stepInitialized = false;

    private @Nullable BlockPos claimedHaulPos = null;
    private long nextClaimRenewHaul = 0L;

    private enum Step {
        TO_SOURCE, TO_DEST
    }

    public HaulerGoal(ByteBuddyEntity byteBuddy) {
        super(byteBuddy, 160, 1.66, 0.15, 0.28, 1.66);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    protected String goalLabel() {
        return "HAULER";
    }

    @Override
    protected void setActionActive(boolean active) { byteBuddy.setWorking(active); }

    @Override
    protected double lookAtY(BlockPos pos, Vec3 center) { return center.y; }

    @Override
    protected boolean withinReach(Vec3 buddyPos, Vec3 targetCenter) {
        return buddyPos.distanceToSqr(targetCenter) <= (reachDistanceMin * reachDistanceMin)
                && Math.abs(buddyPos.y - targetCenter.y) <= verticalTolerance;
    }

    @Override
    protected void releaseClaim() {
        claimedHaulPos = null;
    }

    @Override
    protected void renewClaim(ServerLevel serverLevel) {
        GoalUtil.renewClaimIfNeeded(byteBuddy, serverLevel, TaskType.HAUL,
                claimedHaulPos, targetPos, serverLevel.getGameTime(),
                5, claimTimeOut,
                () -> nextClaimRenewHaul,
                ticks -> nextClaimRenewHaul = ticks);
    }

    @Override
    protected boolean validateTarget(ServerLevel serverLevel, BlockState targetState) {
        BlockEntity blockEntity = serverLevel.getBlockEntity(targetPos);
        if (blockEntity == null) {
            clearTarget();
            return false;
        }
        if (!targetStillViable(blockEntity, serverLevel)) {
            swapStepAndRetarget();
            return false;
        }
        return true;
    }

    @Override
    protected void onReadyToAct(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        firePos = pos;
        firePreState = state;

        startTimedAnimation(GoalUtil.toTicks(2.6), GoalUtil.toTicks(1.8), pos, state);

        if (serverLevel.getBlockEntity(pos) instanceof DockingStationBlockEntity dockBlock) {
            serverLevel.setBlock(pos, dockBlock.getBlockState().setValue(OPEN, Boolean.TRUE), Block.UPDATE_ALL);
        }

        BotDebug.log(byteBuddy, "HAUL schedule " + step + " now=" + serverLevel.getGameTime());
    }

    @Override
    protected void performAction(BlockPos pos, BlockState pre) {
        if (!(byteBuddy.level() instanceof ServerLevel serverLevel)) return;
        performHaul(serverLevel, pos);
        if (serverLevel.getBlockEntity(pos) instanceof DockingStationBlockEntity dockBlock) {
            serverLevel.setBlock(pos, dockBlock.getBlockState().setValue(OPEN, Boolean.FALSE), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void onAnimationComplete() {
        clearTarget();
        planForCurrentStep();
    }

    @Override
    protected void handleSeeking() {
        if (!timedOut(seekingTimeout)) return;
        swapStepAndRetarget();
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

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, 1, 128)) return false;

        BlockPos source = byteBuddy.getFirstPos();
        BlockPos dest = byteBuddy.getSecondPos();
        if (source == null || dest == null) {
            failTask(BotDebug.FailReason.NO_TARGET, "clipboard missing positions");
            return false;
        }

        if (targetPos == null) {
            if (!stepInitialized) {
                step = hasAnyItems(byteBuddy.getMainInv()) ? Step.TO_DEST : Step.TO_SOURCE;
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
        stepInitialized = false;
        super.stop();
    }

    private record HaulPlan(BlockPos targetPos, BlockPos approachPos, Path path) {}

    private Optional<HaulPlan> findHaulPlan(BlockPos blockPos) {
        Level level = byteBuddy.level();
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();

        var plans = buildApproachPlans(level, blockPos);
        if (plans.isEmpty()) return Optional.empty();

        this.claimedHaulPos = blockPos.immutable();
        this.nextClaimRenewHaul = serverLevel.getGameTime() + 5;
        this.approachPlans = plans;
        this.anchorIndex = 0;
        Approach first = plans.get(0);
        return Optional.of(new HaulPlan(blockPos, first.targetPos(), first.path()));
    }

    private List<Approach> buildApproachPlans(Level level, BlockPos pos) {
        ArrayList<Approach> list = new ArrayList<>(4);
        BlockPos[] sides = { pos.east(), pos.west(), pos.south(), pos.north() };
        for (BlockPos side : sides) {
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, side)) continue;
            Vec3 anchor = GoalUtil.getEdgeAnchor(pos, side);
            if (anchor == null) continue;
            Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation nav) ? nav.createPath(side, 0) : null;
            if (path == null) continue;
            list.add(new Approach(side, anchor, GoalUtil.hDistSq(byteBuddy.position(), anchor), path));
        }
        list.sort(Comparator.comparingDouble(Approach::distSq));
        return list;
    }

    private void planForCurrentStep() {
        BlockPos source = byteBuddy.getFirstPos();
        BlockPos dest = byteBuddy.getSecondPos();
        if (source == null || dest == null) {
            enterPhase(GoalPhase.IDLE, "clipboard missing positions");
            return;
        }
        BlockPos target = (step == Step.TO_SOURCE) ? source : dest;
        var plan = findHaulPlan(target);
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

    private void swapStepAndRetarget() {
        BlockPos source = byteBuddy.getFirstPos();
        BlockPos dest = byteBuddy.getSecondPos();
        if (source == null || dest == null) {
            clearTarget();
            enterPhase(GoalPhase.IDLE, "clipboard invalid");
            return;
        }
        step = (step == Step.TO_SOURCE) ? Step.TO_DEST : Step.TO_SOURCE;
        BlockPos newTarget = (step == Step.TO_SOURCE) ? source : dest;
        var plan = findHaulPlan(newTarget);
        if (plan.isEmpty()) {
            clearTarget();
            enterPhase(GoalPhase.IDLE, "no approach");
            return;
        }
        targetPos = plan.get().targetPos();
        approachPos = plan.get().approachPos();
        targetAnchor = GoalUtil.getEdgeAnchor(targetPos, approachPos);
        edgeAnchored = false;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "retarget " + step + " -> " + approachPos.toShortString());
    }

    private boolean targetStillViable(BlockEntity blockEntity, ServerLevel serverLevel) {
        if (step == Step.TO_SOURCE) {
            IItemHandler sourceInv = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            if (findExtractableSlot(sourceInv, blockEntity) < 0) return false;
            return buddyHasRoomForAnyFrom(sourceInv);
        }
        return hasAnyItems(byteBuddy.getMainInv());
    }

    private boolean buddyHasRoomForAnyFrom(IItemHandler sourceInv) {
        IItemHandler buddyInv = byteBuddy.getMainInv();
        for (int i = 0; i < sourceInv.getSlots(); i++) {
            ItemStack stack = sourceInv.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (tryInsertAllRespectingBuddy(buddyInv, stack, true).getCount() < stack.getCount()) return true;
        }
        return false;
    }

    private void performHaul(ServerLevel serverLevel, BlockPos blockPos) {
        if (step == Step.TO_SOURCE) {
            BlockEntity be = serverLevel.getBlockEntity(blockPos);
            if (be == null) return;
            IItemHandler sourceInv = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
            int slot = findExtractableSlot(sourceInv, be);
            if (slot < 0) return;

            ItemStack simExtract = sourceInv.extractItem(slot, 64, true);
            if (simExtract.isEmpty()) return;

            ItemStack simRemainder = tryInsertAllRespectingBuddy(byteBuddy.getMainInv(), simExtract, true);
            int insertable = simExtract.getCount() - simRemainder.getCount();
            if (insertable <= 0) { this.step = Step.TO_DEST; return; }

            if (!byteBuddy.consumeEnergy(insertable)) return;

            ItemStack extracted = sourceInv.extractItem(slot, insertable, false);
            if (extracted.isEmpty()) return;

            ItemStack remainder = tryInsertAllRespectingBuddy(byteBuddy.getMainInv(), extracted, false);
            int inserted = extracted.getCount() - remainder.getCount();
            if (inserted > 0) {
                BotDebug.log(byteBuddy, "HAUL: took " + inserted + "x " + extracted.getDisplayName().getString());
                this.step = Step.TO_DEST;
            }

            if (!remainder.isEmpty()) {
                for (int s = 0; s < sourceInv.getSlots() && !remainder.isEmpty(); s++) {
                    remainder = sourceInv.insertItem(s, remainder, false);
                }
                if (!remainder.isEmpty()) {
                    Containers.dropItemStack(serverLevel, blockPos.getX() + 0.5, blockPos.getY() + 1, blockPos.getZ() + 0.5, remainder);
                }
            }
            return;
        }

        BlockEntity targetBE = serverLevel.getBlockEntity(blockPos);
        if (targetBE == null) return;
        IItemHandler targetInv = targetBE.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        IItemHandler buddy = byteBuddy.getMainInv();

        for (int i = buddyHaulStart; i < buddy.getSlots(); i++) {
            ItemStack stack = buddy.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack simRem = tryInsertAll(targetInv, stack, true);
            int movable = stack.getCount() - simRem.getCount();
            if (movable <= 0) continue;

            if (!byteBuddy.consumeEnergy(movable)) continue;

            if (targetBE instanceof DockingStationBlockEntity dockBlock) {
                serverLevel.setBlock(blockPos, dockBlock.getBlockState().setValue(OPEN, Boolean.TRUE), Block.UPDATE_ALL);
            }

            ItemStack toSend = stack.copyWithCount(movable);
            ItemStack itemRem = tryInsertAll(targetInv, toSend, false);
            int moved = movable - itemRem.getCount();
            if (moved > 0) {
                buddy.extractItem(i, moved, false);
                BotDebug.log(byteBuddy, "HAUL: delivered " + moved + "x " + stack.getDisplayName().getString());
                this.step = Step.TO_SOURCE;
                return;
            } else {
                byteBuddy.getEnergyStorage().receiveEnergy(movable, false);
            }
        }
        this.step = Step.TO_SOURCE;
    }

    private int findExtractableSlot(IItemHandler inv, BlockEntity be) {
        int start = (be instanceof DockingStationBlockEntity) ? 2 : 0;
        for (int i = start; i < inv.getSlots(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean hasAnyItems(IItemHandler inv) {
        for (int i = buddyHaulStart; i < inv.getSlots(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) return true;
        }
        return false;
    }

    private ItemStack tryInsertAll(IItemHandler inv, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < inv.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = inv.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    private ItemStack tryInsertAllRespectingBuddy(IItemHandler inv, ItemStack stack, boolean simulate) {
        if (inv == byteBuddy.getMainInv()) {
            ItemStack remainder = stack.copy();
            for (int slot = buddyHaulStart; slot < inv.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = inv.insertItem(slot, remainder, simulate);
            }
            return remainder;
        }
        return tryInsertAll(inv, stack, simulate);
    }
}
