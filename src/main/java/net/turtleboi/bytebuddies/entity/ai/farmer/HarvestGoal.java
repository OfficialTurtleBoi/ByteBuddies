package net.turtleboi.bytebuddies.entity.ai.farmer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.api.SeedItemProvider;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.ai.TaskGoal;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.BuddyRole;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.TaskType;
import net.turtleboi.bytebuddies.item.custom.FloppyDiskItem;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;

public class HarvestGoal extends TaskGoal {

    @Nullable private BlockPos claimedHarvestPos = null;
    private long nextClaimRenewHarvest = 0L;
    private static final int harvestEnergyCost = 25;
    private boolean canPlantAfterHarvest = true;

    public HarvestGoal(ByteBuddyEntity byteBuddy) {
        super(byteBuddy, 160, 1.25, 0.08, 0.18, 1.25);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public HarvestGoal enablePostHarvestPlant(boolean enabled) {
        this.canPlantAfterHarvest = enabled;
        return this;
    }

    @Override
    protected String goalLabel() { return "FARMER"; }

    @Override
    protected void setActionActive(boolean active) { byteBuddy.setWorking(active); }

    @Override
    protected void releaseClaim() {
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (claimedHarvestPos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.HARVEST, claimedHarvestPos, byteBuddy.getUUID());
        }
        claimedHarvestPos = null;
    }

    @Override
    protected void renewClaim(ServerLevel serverLevel) {
        GoalUtil.renewClaimIfNeeded(byteBuddy, serverLevel, TaskType.HARVEST,
                claimedHarvestPos, targetPos,
                serverLevel.getGameTime(), 5, claimTimeOut,
                () -> nextClaimRenewHarvest,
                ticks -> nextClaimRenewHarvest = ticks);
    }

    @Override
    protected boolean validateTarget(ServerLevel serverLevel, BlockState targetState) {
        if (!(targetState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(targetState)) {
            clearTarget();
            enterPhase(GoalPhase.IDLE, "target invalid, rescan");
            return false;
        }
        return true;
    }

    @Override
    protected void onReadyToAct(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (!verifyClaimOrAbort(serverLevel, TaskType.HARVEST, claimedHarvestPos, pos)) return;

        firePos = pos;
        firePreState = state;

        int totalTicks = GoalUtil.toTicks(2.6);
        int startTicks = GoalUtil.toTicks(1.8);
        startTimedAnimation(totalTicks, startTicks, pos, state);

        BotDebug.log(byteBuddy, "HARVEST schedule: now=" + serverLevel.getGameTime() +
                " start=" + (serverLevel.getGameTime() + startTicks) +
                " end=" + (serverLevel.getGameTime() + totalTicks) +
                " firePos=" + firePos + " preState=" + state.getBlock().getName().getString());
    }

    @Override
    protected void performAction(BlockPos pos, BlockState pre) {
        performHarvest(pos, pre);
    }

    @Override
    protected void handleSeeking() {
        if (!timedOut(seekingTimeout)) return;

        if (targetReselectRetries++ < 2) {
            var plan = findHarvestPlan();
            if (plan.isPresent()) {
                targetPos = plan.get().crop();
                approachPos = plan.get().targetPos();
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

    @Override
    public boolean canUse() {
        if (currentPhase == GoalPhase.ACTING) {
            if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                return serverLevel.getGameTime() <= animationEnd;
            }
            return true;
        }

        if (targetPos != null) return true;

        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            if (byteBuddy.harvestOnHold(serverLevel)) return false;
        }

        if (byteBuddy.getBuddyRole() != BuddyRole.FARMER) {
            failTask(BotDebug.FailReason.WRONG_ROLE, "role=" + byteBuddy.getBuddyRole());
            return false;
        }

        if (byteBuddy.getDock().isEmpty()) {
            failTask(BotDebug.FailReason.NO_DOCK, "no station bound");
            return false;
        }

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, harvestEnergyCost, 1)) return false;

        var plan = findHarvestPlan();
        if (plan.isEmpty()) return false;

        this.targetPos = plan.get().crop();
        this.approachPos = plan.get().targetPos();
        this.targetAnchor = GoalUtil.getEdgeAnchor(this.targetPos, this.approachPos);
        this.edgeAnchored = false;
        resetProgress();
        enterPhase(GoalPhase.MOVING, "to edge " + approachPos.toShortString());
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
        return GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, harvestEnergyCost, 1);
    }

    private record HarvestPlan(BlockPos crop, BlockPos targetPos, Path path) {}

    private Optional<HarvestPlan> findHarvestPlan() {
        BlockPos dockPos = byteBuddy.getDock().orElse(null);
        if (dockPos == null) return Optional.empty();
        int effectiveRadius = byteBuddy.effectiveRadius();
        Level level = byteBuddy.level();
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -1; y <= 2; y++) {
            for (int x = -effectiveRadius; x <= effectiveRadius; x++) {
                for (int z = -effectiveRadius; z <= effectiveRadius; z++) {
                    cursor.set(dockPos.getX() + x, dockPos.getY() + y, dockPos.getZ() + z);
                    if (!byteBuddy.isBlockWithinTether(cursor.immutable())) continue;
                    BlockState blockState = level.getBlockState(cursor);
                    if (!(blockState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(blockState)) continue;
                    if (dockBlock != null && dockBlock.isReserved(serverLevel, TaskType.HARVEST, cursor)) continue;

                    var plans = buildApproachPlans(level, cursor.immutable());
                    if (plans.isEmpty()) continue;

                    if (dockBlock != null) {
                        if (!dockBlock.tryClaim(serverLevel, TaskType.HARVEST, cursor.immutable(), byteBuddy.getUUID(), claimTimeOut)) continue;
                        this.claimedHarvestPos = cursor.immutable();
                        this.nextClaimRenewHarvest = serverLevel.getGameTime() + 5;
                    }

                    this.approachPlans = plans;
                    this.anchorIndex = 0;
                    Approach approach = plans.get(0);
                    this.approachPos = approach.targetPos();
                    return Optional.of(new HarvestPlan(cursor.immutable(), approach.targetPos(), approach.path()));
                }
            }
        }
        return Optional.empty();
    }

    private List<Approach> buildApproachPlans(Level level, BlockPos cropPos) {
        ArrayList<Approach> list = new ArrayList<>(8);
        BlockPos[] sides = {
            cropPos.east(), cropPos.west(), cropPos.south(), cropPos.north(),
            cropPos.east().above(), cropPos.west().above(), cropPos.south().above(), cropPos.north().above()
        };
        for (BlockPos side : sides) {
            if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, side)) continue;
            Vec3 anchor = GoalUtil.getEdgeAnchor(cropPos, side);
            if (anchor == null) continue;
            Path path = (byteBuddy.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation nav) ? nav.createPath(side, 0) : null;
            if (path == null) continue;
            list.add(new Approach(side, anchor, GoalUtil.hDistSq(byteBuddy.position(), anchor), path));
        }
        list.sort(Comparator.comparingDouble(Approach::distSq));
        return list;
    }

    private void performHarvest(BlockPos cropPos, BlockState blockState) {
        Level level = byteBuddy.level();
        if (!(blockState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(blockState)) {
            BotDebug.log(byteBuddy, "HARVEST: no longer ripe at " + (cropPos != null ? cropPos.toShortString() : "null"));
            return;
        }

        if (!byteBuddy.consumeEnergy(harvestEnergyCost)) {
            BotDebug.log(byteBuddy, "HARVEST: out of energy");
            return;
        }

        List<ItemStack> drops = Block.getDrops(blockState, (ServerLevel) level, cropPos, null, byteBuddy, ItemStack.EMPTY);
        try {
            FloppyDiskItem.DiskHooks.applyPrimaryYieldBonus(drops, blockState.getBlock(), byteBuddy, byteBuddy.yieldBonusChance());
        } catch (Throwable ignored) {}

        level.destroyBlock(cropPos, false);
        Vec3 cropCenter = cropPos.getCenter();
        int inserted = 0, dropped = 0;
        for (ItemStack stack : drops) {
            ItemStack remainder = InventoryUtil.mergeInto(byteBuddy.getMainInv(), stack);
            if (!remainder.isEmpty()) {
                Containers.dropItemStack(level, cropCenter.x, cropCenter.y, cropCenter.z, remainder);
                dropped += remainder.getCount();
            } else {
                inserted += stack.getCount();
            }
        }

        byteBuddy.onTaskSuccess(TaskType.HARVEST, cropPos);
        BotDebug.log(byteBuddy, "HARVEST at " + cropPos.toShortString() + " inserted=" + inserted + " dropped=" + dropped);
        BotDebug.mark(level, cropPos);

        if (canPlantAfterHarvest) {
            tryEnqueueImmediatePlant(cropPos, blockState);
            if (level instanceof ServerLevel serverLevel) {
                byteBuddy.holdHarvestForReplant(serverLevel, 40);
            }
        }
    }

    private void tryEnqueueImmediatePlant(BlockPos plantPos, BlockState preBreakState) {
        Level level = byteBuddy.level();
        if (!(level instanceof ServerLevel serverLevel)) return;

        var plantBlock = preBreakState.getBlock();
        if (!(plantBlock instanceof SeedItemProvider seedItemProvider)) return;

        Item seedItem = seedItemProvider.bytebuddies$getSeedItem().asItem();
        BlockState plantState = plantBlock.defaultBlockState();
        if (!GoalUtil.canPlantAt(level, plantPos, plantState)) return;

        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (dockBlock == null) return;

        if (dockBlock.tryClaim(serverLevel, TaskType.PLANT, plantPos, byteBuddy.getUUID(), 120)) {
            byteBuddy.requestImmediatePlant(plantPos, plantState, seedItem);
        }
    }
}
