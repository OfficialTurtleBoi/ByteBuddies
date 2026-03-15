package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static net.turtleboi.bytebuddies.block.custom.DockingStationBlock.FACING;
import static net.turtleboi.bytebuddies.block.custom.DockingStationBlock.OPEN;

public class DepositToDockGoal extends TaskGoal {

    public DepositToDockGoal(ByteBuddyEntity byteBuddy) {
        super(byteBuddy, 160, 1.66, 0.15, 0.28, 1.66);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    protected String goalLabel() { return "DEPOSITOR"; }

    @Override
    protected void setActionActive(boolean active) { byteBuddy.setWorking(active); }

    @Override
    protected double lookAtY(BlockPos pos, Vec3 center) { return center.y; }

    @Override
    protected void onReadyToAct(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        firePos = pos;
        firePreState = state;

        startTimedAnimation(GoalUtil.toTicks(2.6), GoalUtil.toTicks(1.8), pos, state);

        if (serverLevel.getBlockEntity(pos) instanceof DockingStationBlockEntity dockBlock) {
            serverLevel.setBlock(pos, dockBlock.getBlockState().setValue(OPEN, Boolean.TRUE), Block.UPDATE_ALL);
        }

        BotDebug.log(byteBuddy, "DEPOSIT schedule now=" + serverLevel.getGameTime());
    }

    @Override
    protected void performAction(BlockPos pos, BlockState pre) {
        if (!(byteBuddy.level() instanceof ServerLevel serverLevel)) return;
        performDeposit(serverLevel, pos);
        if (serverLevel.getBlockEntity(pos) instanceof DockingStationBlockEntity dockBlock) {
            serverLevel.setBlock(pos, dockBlock.getBlockState().setValue(OPEN, Boolean.FALSE), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void onAnimationComplete() {
        clearTarget();
        enterPhase(GoalPhase.IDLE, "DEPOSIT complete");
    }

    @Override
    public boolean canUse() {
        if (currentPhase == GoalPhase.ACTING) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getGameTime() <= animationEnd;
            }
            return true;
        }

        if (byteBuddy.getDock().isEmpty()) return false;

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, 1, 64)) return false;

        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (dockBlock == null) return false;

        IItemHandler dockInventory = dockBlock.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (!canDepositAny(dockInventory)) return false;

        var plan = findDockPlan(dockBlock.getBlockPos());
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

    private record DockPlan(BlockPos targetPos, BlockPos approachPos, Path path) {}

    private Optional<DockPlan> findDockPlan(BlockPos dockPos) {
        Level level = byteBuddy.level();
        BlockPos approachPos = dockPos.relative(level.getBlockState(dockPos).getValue(FACING));

        if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, approachPos)) return Optional.empty();

        Path path = (byteBuddy.getNavigation() instanceof GroundPathNavigation nav)
                ? nav.createPath(approachPos, 0) : null;
        if (path == null) return Optional.empty();

        Vec3 edgeAnchor = GoalUtil.getEdgeAnchor(dockPos, approachPos);
        if (edgeAnchor == null) return Optional.empty();

        this.approachPlans = List.of(new Approach(approachPos, edgeAnchor, GoalUtil.hDistSq(byteBuddy.position(), edgeAnchor), path));
        this.anchorIndex = 0;

        return Optional.of(new DockPlan(dockPos, approachPos, path));
    }

    private void performDeposit(ServerLevel serverLevel, BlockPos dockPos) {
        BlockEntity dockBE = serverLevel.getBlockEntity(dockPos);
        if (dockBE == null) return;
        IItemHandler dockInventory = dockBE.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);

        int totalMoved = 0;
        for (int slot = 9; slot < byteBuddy.getMainInv().getSlots(); slot++) {
            ItemStack stack = byteBuddy.getMainInv().getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            ItemStack simRem = tryInsertAll(dockInventory, stack, true);
            int insertable = stack.getCount() - simRem.getCount();
            if (insertable <= 0) continue;

            ItemStack rem = tryInsertAll(dockInventory, stack.copyWithCount(insertable), false);
            int inserted = insertable - rem.getCount();
            if (inserted > 0) {
                byteBuddy.getMainInv().extractItem(slot, inserted, false);
                totalMoved += inserted;
            }
        }

        if (totalMoved > 0) {
            byteBuddy.consumeEnergy(totalMoved);
            BotDebug.log(byteBuddy, "DEPOSIT: moved " + totalMoved + " items to dock");
        }
    }

    private ItemStack tryInsertAll(IItemHandler inv, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < inv.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = inv.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    private boolean canDepositAny(IItemHandler dockInventory) {
        if (dockInventory == null) return false;
        IItemHandler buddyInv = byteBuddy.getMainInv();
        for (int slot = 9; slot < buddyInv.getSlots(); slot++) {
            ItemStack stack = buddyInv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (tryInsertAll(dockInventory, stack, true).getCount() < stack.getCount()) return true;
        }
        return false;
    }
}
