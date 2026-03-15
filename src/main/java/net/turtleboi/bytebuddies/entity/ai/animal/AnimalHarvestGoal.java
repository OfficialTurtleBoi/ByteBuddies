package net.turtleboi.bytebuddies.entity.ai.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.IForgeShearable;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.util.GoalUtil;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public class AnimalHarvestGoal extends Goal {

    private static final String CLAIMED_BY = "bytebuddies:claimed_by";
    private static final int actionCooldownTicks = 40;
    private static final int harvestEnergyCost = 20;
    private static final double reachDistSq = 6.25;
    private static final double moveSpeed = 0.9;
    private static final int animDuration = 52;
    private static final int actionAtTick = 36;
    private static final int movingTimeout = 120;

    private final ByteBuddyEntity byteBuddy;
    private Animal target;
    private long nextActionTick;
    private int ticksStuck;
    private long animStart = -1;
    private long animEnd = -1;
    private boolean actionStarted;

    public AnimalHarvestGoal(ByteBuddyEntity byteBuddy) {
        this.byteBuddy = byteBuddy;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(byteBuddy.level() instanceof ServerLevel sl)) return false;
        if (byteBuddy.getDock().isEmpty()) return false;
        if (!byteBuddy.canAct()) return false;
        if (!GoalUtil.actionReady(sl, nextActionTick)) return false;

        ItemStack held = byteBuddy.getMainHandItem();
        boolean shearing = held.is(Items.SHEARS);
        boolean milking = held.is(Items.BUCKET);
        if (!shearing && !milking) return false;

        int radius = byteBuddy.effectiveRadius();
        if (radius <= 0) return false;
        BlockPos dock = byteBuddy.getDock().orElse(null);
        if (dock == null) return false;

        Animal found = findTarget(sl, new AABB(dock).inflate(radius), shearing);
        if (found == null) return false;

        claimAnimal(found);
        target = found;
        return true;
    }

    private Animal findTarget(ServerLevel sl, AABB box, boolean shearing) {
        if (shearing) {
            return sl.getEntitiesOfClass(Animal.class, box,
                            a -> a instanceof IForgeShearable fs
                                    && fs.isShearable(byteBuddy.getMainHandItem(), sl, a.blockPosition())
                                    && !isClaimedByOther(a))
                    .stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(byteBuddy))).orElse(null);
        }
        return sl.getEntitiesOfClass(Animal.class, box,
                        a -> isMilkable(a) && !isClaimedByOther(a))
                .stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(byteBuddy))).orElse(null);
    }

    private boolean isMilkable(Animal animal) {
        return animal instanceof Cow || animal instanceof MushroomCow;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && !target.isRemoved();
    }

    @Override
    public void start() {
        ticksStuck = 0;
        animStart = -1;
        animEnd = -1;
        actionStarted = false;
    }

    @Override
    public void stop() {
        if (target != null) releaseAnimal(target);
        target = null;
        byteBuddy.setWorking(false);
        byteBuddy.getNavigation().stop();
        animStart = -1;
        animEnd = -1;
        actionStarted = false;
    }

    @Override
    public void tick() {
        if (!(byteBuddy.level() instanceof ServerLevel sl)) return;
        if (target == null || !target.isAlive()) {
            stop();
            return;
        }

        if (animStart >= 0) {
            byteBuddy.setWorking(true);
            long t = sl.getGameTime();
            if (!actionStarted && t >= animStart + actionAtTick) {
                actionStarted = true;
                performHarvest(sl);
            }
            if (t >= animEnd) {
                byteBuddy.setWorking(false);
                animStart = -1;
                animEnd = -1;
                actionStarted = false;
                nextActionTick = sl.getGameTime() + actionCooldownTicks;
                releaseAnimal(target);
                target = null;
            }
            return;
        }

        if (++ticksStuck > movingTimeout) {
            nextActionTick = sl.getGameTime() + actionCooldownTicks;
            stop();
            return;
        }

        byteBuddy.getNavigation().moveTo(target, moveSpeed);
        byteBuddy.getLookControl().setLookAt(target, 30f, 30f);

        if (byteBuddy.distanceToSqr(target) <= reachDistSq) {
            ticksStuck = 0;
            byteBuddy.getNavigation().stop();
            long t = sl.getGameTime();
            animStart = t;
            animEnd = t + animDuration;
        }
    }

    private void performHarvest(ServerLevel sl) {
        if (target == null || !target.isAlive()) return;
        ItemStack held = byteBuddy.getMainHandItem();

        if (held.is(Items.SHEARS) && target instanceof IForgeShearable fs) {
            if (!byteBuddy.consumeEnergy(harvestEnergyCost)) return;
            int fortune = held.getEnchantmentLevel(Enchantments.BLOCK_FORTUNE);
            List<ItemStack> drops = fs.onSheared(null, held, sl, target.blockPosition(), fortune);
            for (ItemStack drop : drops) {
                ItemStack remainder = byteBuddy.addToCargo(drop);
                if (!remainder.isEmpty()) target.spawnAtLocation(remainder);
            }
            held.hurtAndBreak(1, byteBuddy, b -> {});

        } else if (held.is(Items.BUCKET) && isMilkable(target)) {
            ItemStack milkBucket = new ItemStack(Items.MILK_BUCKET);
            if (!byteBuddy.canFitInInventory(milkBucket)) return;
            if (!byteBuddy.consumeEnergy(harvestEnergyCost)) return;
            byteBuddy.addToCargo(milkBucket);
            sl.playSound(null, target, SoundEvents.COW_MILK, SoundSource.NEUTRAL, 1.0f, 1.0f);
        }
    }

    private void claimAnimal(Animal animal) {
        animal.getPersistentData().putUUID(CLAIMED_BY, byteBuddy.getUUID());
    }

    private void releaseAnimal(Animal animal) {
        animal.getPersistentData().remove(CLAIMED_BY);
    }

    private boolean isClaimedByOther(Animal animal) {
        CompoundTag data = animal.getPersistentData();
        if (!data.hasUUID(CLAIMED_BY)) return false;
        return !data.getUUID(CLAIMED_BY).equals(byteBuddy.getUUID());
    }
}
