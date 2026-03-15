package net.turtleboi.bytebuddies.entity.ai.farmer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.IPlantable;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.ai.TaskGoal;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.BuddyRole;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.PlantRequest;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.TaskType;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.BotDebug.GoalPhase;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;

import javax.annotation.Nullable;
import java.util.*;

public class PlantGoal extends TaskGoal {

    @Nullable private BlockPos claimedPlantPos = null;
    @Nullable private BlockState claimedPlantState = null;
    @Nullable private Item claimedPlantSeed = null;
    private long nextClaimRenewPlant = 0L;
    private static final int plantEnergyCost = 25;

    public PlantGoal(ByteBuddyEntity byteBuddy) {
        super(byteBuddy, 160, 1.25, 0.08, 0.18, 1.25);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    protected String goalLabel() { return "FARMER"; }

    @Override
    protected void setActionActive(boolean active) { byteBuddy.setSlamming(active); }

    @Override
    protected void releaseClaim() {
        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
        if (claimedPlantPos != null && dockBlock != null) {
            dockBlock.releaseClaim(TaskType.PLANT, claimedPlantPos, byteBuddy.getUUID());
        }
        claimedPlantPos = null;
    }

    @Override
    protected void renewClaim(ServerLevel serverLevel) {
        GoalUtil.renewClaimIfNeeded(byteBuddy, serverLevel, TaskType.PLANT,
                claimedPlantPos, targetPos,
                serverLevel.getGameTime(), 5, claimTimeOut,
                () -> nextClaimRenewPlant,
                ticks -> nextClaimRenewPlant = ticks);
    }

    @Override
    protected boolean validateTarget(ServerLevel serverLevel, BlockState targetState) {
        BlockState desired = (claimedPlantState != null) ? claimedPlantState
                : (claimedPlantSeed != null ? seedToPlantState(claimedPlantSeed) : null);
        if (desired == null || !GoalUtil.canPlantAt(byteBuddy.level(), targetPos, desired)) {
            clearTarget();
            enterPhase(GoalPhase.IDLE, "plant site invalid, rescan");
            return false;
        }
        return true;
    }

    @Override
    protected void onReadyToAct(ServerLevel serverLevel, BlockPos pos, BlockState state) {
        if (!verifyClaimOrAbort(serverLevel, TaskType.PLANT, claimedPlantPos, pos)) return;

        firePos = pos;
        firePreState = state;

        int totalTicks = GoalUtil.toTicks(2.0);
        int startTicks = GoalUtil.toTicks(0.4);
        startTimedAnimation(totalTicks, startTicks, pos, state);

        BotDebug.log(byteBuddy, "PLANT schedule: now=" + serverLevel.getGameTime() +
                " start=" + (serverLevel.getGameTime() + startTicks) +
                " end=" + (serverLevel.getGameTime() + totalTicks) +
                " firePos=" + firePos + " preState=" + state.getBlock().getName().getString());
    }

    @Override
    protected void performAction(BlockPos pos, BlockState pre) {
        performPlant(pos);
    }

    @Override
    protected void handleSeeking() {
        if (!timedOut(seekingTimeout)) return;

        if (targetReselectRetries++ < 2) {
            PlantPlan plan = findPlantPlan(null, null, null, byteBuddy.effectiveRadius());
            if (plan != null) {
                if (byteBuddy.level() instanceof ServerLevel serverLevel) {
                    DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
                    if (dockBlock != null && !dockBlock.tryClaim(serverLevel, TaskType.PLANT,
                            plan.plantPos(), byteBuddy.getUUID(), claimTimeOut)) {
                        enterPhase(GoalPhase.SEEKING, "seek retry (claim failed)");
                        return;
                    }
                    claimedPlantPos = plan.plantPos();
                    nextClaimRenewPlant = serverLevel.getGameTime() + 5;
                }
                claimedPlantSeed = plan.seedItem();
                claimedPlantState = plan.plantState();
                targetPos = plan.plantPos();
                approachPos = plan.standPos();
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

        if (byteBuddy.getBuddyRole() != BuddyRole.FARMER) {
            failTask(BotDebug.FailReason.WRONG_ROLE, "role=" + byteBuddy.getBuddyRole());
            return false;
        }

        if (byteBuddy.getDock().isEmpty()) {
            failTask(BotDebug.FailReason.NO_DOCK, "no station bound");
            return false;
        }

        if (!GoalUtil.ensureUse(byteBuddy, ToolUtil.ToolType.EMPTY_HAND, plantEnergyCost, 1)) return false;

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

        if (plantPlan == null) return false;

        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);
            if (dockBlock != null) {
                if (claimedPlantPos == null || !claimedPlantPos.equals(plantPlan.plantPos())) {
                    if (!dockBlock.tryClaim(serverLevel, TaskType.PLANT, plantPlan.plantPos(), byteBuddy.getUUID(), claimTimeOut)) {
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
        claimedPlantSeed = null;
        claimedPlantState = null;
        super.stop();
    }

    private record PlantPlan(BlockPos plantPos, Item seedItem, BlockState plantState, BlockPos standPos, @Nullable Path path) {}

    private @Nullable PlantPlan findPlantPlan(@Nullable BlockPos plantPos, @Nullable Item plantSeed,
                                               @Nullable BlockState prefPlantState, int effectiveRadius) {
        BlockPos dockBlockPos = byteBuddy.getDock().orElse(null);
        if (dockBlockPos == null) return null;

        Level level = byteBuddy.level();
        if (!(level instanceof ServerLevel serverLevel)) return null;

        DockingStationBlockEntity dockBlock = GoalUtil.dockBlockEntity(byteBuddy);

        if (plantPos != null && prefPlantState != null) {
            if (dockBlock != null && (claimedPlantPos == null || !claimedPlantPos.equals(plantPos))) {
                if (!dockBlock.tryClaim(serverLevel, TaskType.PLANT, plantPos, byteBuddy.getUUID(), claimTimeOut)) return null;
                claimedPlantPos = plantPos;
                nextClaimRenewPlant = serverLevel.getGameTime() + 5;
            }
            if (!GoalUtil.canPlantAt(level, plantPos, prefPlantState)) return null;

            BlockPos bestStand = findBestStand(level, plantPos);
            return new PlantPlan(plantPos.immutable(), plantSeed, prefPlantState, bestStand, null);
        }

        var inventory = byteBuddy.getMainInv();
        ArrayList<Item> seeds = new ArrayList<>();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (seedToPlantState(stack.getItem()) != null) seeds.add(stack.getItem());
        }
        if (seeds.isEmpty()) return null;

        BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();
        for (int y = -1; y <= 2; y++) {
            for (int dx = -effectiveRadius; dx <= effectiveRadius; dx++) {
                for (int dz = -effectiveRadius; dz <= effectiveRadius; dz++) {
                    origin.set(dockBlockPos.getX() + dx, dockBlockPos.getY() + y, dockBlockPos.getZ() + dz);
                    if (!byteBuddy.isBlockWithinTether(origin.immutable())) continue;
                    if (dockBlock != null && dockBlock.isReserved(serverLevel, TaskType.PLANT, origin)) continue;

                    for (Item seed : seeds) {
                        BlockState plantState = seedToPlantState(seed);
                        if (plantState == null) continue;
                        if (!GoalUtil.canPlantAt(level, origin, plantState)) continue;

                        BlockPos bestStand = findBestStand(level, origin.immutable());
                        return new PlantPlan(origin.immutable(), seed, plantState, bestStand, null);
                    }
                }
            }
        }
        return null;
    }

    private BlockPos findBestStand(Level level, BlockPos plantPos) {
        BlockPos[] sides = { plantPos.east(), plantPos.west(), plantPos.south(), plantPos.north() };
        BlockPos bestStand = null;
        double bestDist = Double.POSITIVE_INFINITY;

        if (byteBuddy.getNavigation() instanceof GroundPathNavigation pathNavigation) {
            for (BlockPos side : sides) {
                if (!ByteBuddyEntity.isStandableForMove(byteBuddy, level, side)) continue;
                Path path = pathNavigation.createPath(side, 0);
                if (path == null) continue;
                double dist = GoalUtil.hDistSq(byteBuddy.position(), side.getCenter());
                if (dist < bestDist) {
                    bestDist = dist;
                    bestStand = side;
                }
            }
        }
        return bestStand != null ? bestStand : plantPos;
    }

    private void performPlant(BlockPos blockPos) {
        Level level = byteBuddy.level();
        if (claimedPlantState == null || claimedPlantSeed == null) {
            failTask(BotDebug.FailReason.NO_TARGET, "no queued plant info at " + blockPos.toShortString());
            return;
        }

        ItemStack seedStack = InventoryUtil.findItem(byteBuddy.getMainInv(), claimedPlantSeed);
        if (seedStack.isEmpty()) {
            failTask(BotDebug.FailReason.NO_TARGET, "no seed item (" + claimedPlantSeed + ") for replant at " + blockPos.toShortString());
            return;
        }

        BlockState blockBelow = level.getBlockState(blockPos.below());
        boolean canSustain = blockBelow.canSustainPlant(level, blockPos.below(), Direction.UP, (IPlantable) claimedPlantState.getBlock());
        if (!canSustain || !claimedPlantState.canSurvive(level, blockPos)) {
            failTask(BotDebug.FailReason.NO_TARGET, "cannot plant here (conditions) at " + blockPos.toShortString());
            return;
        }

        BlockState above = level.getBlockState(blockPos);
        if (!above.isAir() && !above.getCollisionShape(level, blockPos).isEmpty()) {
            failTask(BotDebug.FailReason.NO_TARGET, "space not clear at " + blockPos.toShortString());
            return;
        }

        if (!GoalUtil.canPlantAt(level, blockPos, claimedPlantState)) {
            failTask(BotDebug.FailReason.NO_TARGET, "cannot plant at " + blockPos.toShortString());
            return;
        }

        if (!byteBuddy.consumeEnergy(plantEnergyCost)) {
            failTask(BotDebug.FailReason.OUT_OF_ENERGY, "need=" + plantEnergyCost);
            releaseClaim();
            return;
        }

        level.playSound(null, blockPos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS,
                0.8f + (level.random.nextFloat() * 0.4f), 0.9f + (level.random.nextFloat() * 0.2f));

        if (level instanceof ServerLevel serverLevel) {
            int pts = 12;
            for (int i = 0; i < pts; i++) {
                double angle = (Math.PI * 2 * i) / pts;
                serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(claimedPlantSeed)),
                        blockPos.getX() + 0.5 + Math.cos(angle) * 0.5, blockPos.getY() + 0.125,
                        blockPos.getZ() + 0.5 + Math.sin(angle) * 0.5, 1, 0, 0, 0, 0.05);
            }
        }

        level.setBlock(blockPos, claimedPlantState, 3);
        seedStack.shrink(1);

        byteBuddy.onTaskSuccess(TaskType.PLANT, blockPos);
        releaseClaim();
        claimedPlantState = null;
        claimedPlantSeed = null;
    }

    private static @Nullable BlockState seedToPlantState(Item seedItem) {
        if (seedItem instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof CropBlock || block instanceof BushBlock) {
                return block.defaultBlockState();
            }
        }
        return null;
    }
}
