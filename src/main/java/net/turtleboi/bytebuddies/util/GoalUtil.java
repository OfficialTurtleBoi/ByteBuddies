package net.turtleboi.bytebuddies.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.TriState;
import net.turtleboi.bytebuddies.block.custom.DockingStationBlock;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.*;
import net.turtleboi.bytebuddies.util.ToolUtil.*;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static net.turtleboi.bytebuddies.util.ToolUtil.matchesToolType;

public class GoalUtil {
    public static void lockToAnchor(ByteBuddyEntity byteBuddy, Vec3 targetAnchor) {
        if (targetAnchor == null) return;
        byteBuddy.getNavigation().stop();

        var bb = byteBuddy.getBoundingBox();
        var move = targetAnchor.subtract(byteBuddy.position());
        var movedBB = bb.move(move);

        if (!byteBuddy.level().noCollision(movedBB)) {

            var upBB = movedBB.move(0.0, 0.0625, 0.0);
            if (!byteBuddy.level().noCollision(upBB)) {

                return;
            }

            targetAnchor = targetAnchor.add(0.0, 0.0625, 0.0);
        }


        byteBuddy.setPos(targetAnchor.x, targetAnchor.y, targetAnchor.z);
        byteBuddy.setDeltaMovement(0.0, 0.0, 0.0);
        byteBuddy.resetFallDistance();
    }

    public static boolean actionReady(ServerLevel serverLevel, long nextActionTick) {
        return serverLevel.getGameTime() >= nextActionTick;
    }

    public static double hDistSq(Vec3 posA, Vec3 posB) {
        double dx = posA.x - posB.x, dz = posA.z - posB.z;
        return dx * dx + dz * dz;
    }

    public static int toTicks(double seconds) {
        return (int)Math.round(seconds * 20.0);
    }

    @Nullable
    public static Vec3 getEdgeAnchor(BlockPos blockPos, BlockPos standingPos) {
        if (blockPos == null || standingPos == null) return null;
        Vec3 centerPos = blockPos.getCenter();
        Vec3 standingCenterPos = standingPos.getCenter();
        Vec3 subtracted = standingCenterPos.subtract(centerPos);
        if (subtracted.lengthSqr() < 1.0e-6) subtracted = new Vec3(1, 0, 0);
        subtracted = subtracted.normalize();
        double inset = 0.55;
        return new Vec3(centerPos.x + subtracted.x * inset, standingPos.getY(), centerPos.z + subtracted.z * inset);
    }

    @Nullable
    public static DockingStationBlockEntity dockBlockEntity(ByteBuddyEntity byteBuddy) {
        return (DockingStationBlockEntity) byteBuddy.getDock()
                .map(blockPos -> byteBuddy.level().getBlockEntity(blockPos))
                .filter(blockEntity -> blockEntity instanceof DockingStationBlockEntity)
                .orElse(null);
    }

    public static Direction backOfDock(DockingStationBlockEntity be) {
        BlockState state = be.getBlockState();
        Direction front = DockingStationBlock.getHorizontalFacing(state);
        return front.getOpposite();
    }


    public static void renewClaimIfNeeded(
            ByteBuddyEntity byteBuddy, ServerLevel serverLevel, ByteBuddyEntity.TaskType taskType, @Nullable BlockPos claimedPos,
            @Nullable BlockPos currentPos, long currentTime, int renewPeriod, int timeOutTicks, LongSupplier nextRenewGetter, LongConsumer nextRenewSetter) {
        if (currentPos == null || !currentPos.equals(claimedPos)) return;
        if (currentTime < nextRenewGetter.getAsLong()) return;
        DockingStationBlockEntity dockBlock = dockBlockEntity(byteBuddy);
        if (dockBlock == null) return;
        dockBlock.renewClaim(serverLevel, taskType, claimedPos, byteBuddy.getUUID(), timeOutTicks);
        nextRenewSetter.accept(currentTime + renewPeriod);
    }

    public static boolean isStandableTerrain(Level level, BlockPos blockPos) {
        if (!level.isLoaded(blockPos)) return false;
        BlockState below = level.getBlockState(blockPos.below());
        boolean solidFloor = !below.getCollisionShape(level, blockPos.below()).isEmpty();

        BlockState feet = level.getBlockState(blockPos);
        boolean feetFree = feet.getCollisionShape(level, blockPos).isEmpty();

        BlockState head = level.getBlockState(blockPos.above());
        boolean headFree = head.getCollisionShape(level, blockPos.above()).isEmpty();

        boolean noLiquid = level.getFluidState(blockPos).isEmpty()
                && level.getFluidState(blockPos.above()).isEmpty();

        return solidFloor && feetFree && headFree && noLiquid;
    }

    public static boolean isStandableForMove(ByteBuddyEntity byteBuddy, Level level, BlockPos blockPos) {
        if (!isStandableTerrain(level, blockPos)) return false;

        if (!(level instanceof ServerLevel serverLevel)) return true;
        BlockPos dockPos = byteBuddy.getDock().orElse(null);
        if (dockPos == null) return true;

        if (!(level.getBlockEntity(dockPos) instanceof DockingStationBlockEntity dockBlock)) return true;

        boolean reserved = dockBlock.isReserved(serverLevel, ByteBuddyEntity.TaskType.MOVE, blockPos);
        if (!reserved) return true;

        return dockBlock.isReservedBy(serverLevel, ByteBuddyEntity.TaskType.MOVE, blockPos, byteBuddy.getUUID());
    }

    public static boolean isAirOrReplaceableAbove(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        return (level.getBlockState(above).getCollisionShape(level, above).isEmpty()
                && level.getFluidState(above).isEmpty());
    }

    public static boolean isPlantable(BlockState blockState) {
        return blockState.getBlock() instanceof BushBlock
                || blockState.getBlock() instanceof CropBlock
                || blockState.is(BlockTags.CROPS);
    }

    public static boolean canPlantAt(Level level, BlockPos blockPos, BlockState plantState) {
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

    public static boolean canTillAt(Level level, BlockPos blockPos) {
        if (!level.isLoaded(blockPos)) return false;
        BlockState soilCandidate = level.getBlockState(blockPos);

        if (soilCandidate.is(Blocks.FARMLAND)) return false;
        if (!TILLABLE.contains(soilCandidate.getBlock())) return false;

        BlockPos aboveSoil = blockPos.above();
        BlockState aboveSoilState = level.getBlockState(aboveSoil);

        return (aboveSoilState.isAir() || aboveSoilState.canBeReplaced()) && level.getFluidState(aboveSoil).isEmpty();
    }

    public static boolean canMineAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        if (level.getBlockEntity(pos) instanceof DockingStationBlockEntity) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.is(Blocks.BEDROCK)) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return true;
    }

    public static long getCurrentTime(LivingEntity livingEntity) {
        return (livingEntity.level() instanceof ServerLevel serverLevel) ? serverLevel.getGameTime() : 0L;
    }

    public static void reserveCurrentPathIfAny(ServerLevel serverLevel, ByteBuddyEntity byteBuddy, int lookAhead) {
        Path path = byteBuddy.getNavigation().getPath();
        if (path != null) {
            ByteBuddyEntity.reservePathAhead(byteBuddy, serverLevel, path, lookAhead);
        }
    }

    public static void releaseCurrentPathIfAny(ByteBuddyEntity byteBuddy) {
        Path path = byteBuddy.getNavigation().getPath();
        if (path != null) {
            byteBuddy.releasePath(path);
        }
    }

    public static boolean hasRequiredTool(ByteBuddyEntity byteBuddy, ToolType toolType) {
        if (toolType == ToolType.EMPTY_HAND) return true;
        var buddyAugmentInventory = byteBuddy.getAugmentInv();
        int heldToolSlot = byteBuddy.getHeldToolSlot();
        ItemStack toolStack = heldToolSlot >= 0 && heldToolSlot < buddyAugmentInventory.getSlots()
                ? buddyAugmentInventory.getStackInSlot(heldToolSlot)
                : ItemStack.EMPTY;

        boolean hasRequiredTool = !toolStack.isEmpty() && matchesToolType(toolStack, toolType);
        if (!hasRequiredTool) {
            BotDebug.log(byteBuddy, "CHECK failed: required tool=" + toolType +
                    " heldSlot=" + heldToolSlot +
                    " stack=" + (toolStack.isEmpty() ? "EMPTY" : toolStack.getItem().toString()));
        }
        return hasRequiredTool;
    }

    public static boolean hasEnergyForUnit(ByteBuddyEntity byteBuddy, int energyPerUnit) {
        if (energyPerUnit <= 0) return true;
        int energyStored = byteBuddy.getEnergyStorage().getEnergyStored();
        boolean hasEnergyForUnit = energyStored >= energyPerUnit;
        if (!hasEnergyForUnit) {
            BotDebug.log(byteBuddy, "CHECK failed: energy per unit=" + energyPerUnit +
                    " energyStored=" + energyStored);
        }
        return hasEnergyForUnit;
    }

    public static boolean hasEnergyForMax(ByteBuddyEntity byteBuddy, int energyPerUnit, int maxUnits) {
        long neededEnergyLong = (long)Math.max(0, energyPerUnit) * (long)Math.max(1, maxUnits);
        int neededEnergy = neededEnergyLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)neededEnergyLong;
        int energyStored = byteBuddy.getEnergyStorage().getEnergyStored();
        boolean hasEnergyForMax = energyStored >= neededEnergy;
        if (!hasEnergyForMax) {
            BotDebug.log(byteBuddy, "CHECK failed: energy max batch need=" + neededEnergy +
                    " energyStored=" + energyStored +
                    " unit=" + energyPerUnit +
                    " maxUnits=" + maxUnits);
        }
        return hasEnergyForMax;
    }

    public static boolean ensureUse(ByteBuddyEntity byteBuddy, @Nullable ToolType requiredTool, int energyPerUnit, int maxUnits) {
        if (requiredTool != null && !hasRequiredTool(byteBuddy, requiredTool)) {
            BotDebug.log(byteBuddy, "PRECONDITION: missing required tool for goal start");
            return false;
        }
        if (!hasEnergyForMax(byteBuddy, energyPerUnit, maxUnits)) {
            BotDebug.log(byteBuddy, "PRECONDITION: insufficient energy to complete max batch");
            return false;
        }
        return true;
    }

}
