package net.turtleboi.bytebuddies.entity.ai.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.util.GoalUtil;

import java.util.*;

public class AnimalManageGoal extends Goal {

    private static final int defaultMin = 4;
    private static final int defaultMax = 10;
    private static final int actionEnergyCost = 25;
    private static final double reachDistSq = 4.0;
    private static final double moveSpeed = 0.9;
    private static final int movingTimeout = 120;
    private static final int attackCooldownTicks = 20;
    private static final int actionCooldownTicks = 60;

    private static final Map<ResourceLocation, int[]> SPECIES_THRESHOLDS = new HashMap<>();

    private final ByteBuddyEntity byteBuddy;
    private Animal cullTarget;
    private Animal breedTarget1;
    private Animal breedTarget2;
    private boolean cullMode;
    private boolean firstFed;
    private int ticksStuck;
    private long nextAttackTick;
    private long nextActionTick;

    public AnimalManageGoal(ByteBuddyEntity byteBuddy) {
        this.byteBuddy = byteBuddy;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    public static void setThresholds(ResourceLocation entityType, int min, int max) {
        SPECIES_THRESHOLDS.put(entityType, new int[]{min, max});
    }

    @Override
    public boolean canUse() {
        if (!(byteBuddy.level() instanceof ServerLevel sl)) return false;
        if (byteBuddy.getDock().isEmpty()) return false;
        if (!byteBuddy.canAct()) return false;
        if (!GoalUtil.actionReady(sl, nextActionTick)) return false;

        int radius = byteBuddy.effectiveRadius();
        if (radius <= 0) return false;
        BlockPos dock = byteBuddy.getDock().orElse(null);
        if (dock == null) return false;

        DockingStationBlockEntity dockBE = GoalUtil.dockBlockEntity(byteBuddy);

        AABB box = new AABB(dock).inflate(radius);
        List<Animal> adults = sl.getEntitiesOfClass(Animal.class, box, a -> !a.isBaby());

        Map<EntityType<?>, List<Animal>> byType = new LinkedHashMap<>();
        for (Animal a : adults) byType.computeIfAbsent(a.getType(), k -> new ArrayList<>()).add(a);

        for (Map.Entry<EntityType<?>, List<Animal>> entry : byType.entrySet()) {
            List<Animal> group = entry.getValue();
            int count = group.size();
            int[] thresholds = getThresholds(entry.getKey());

            if (count > thresholds[1]) {
                Animal candidate = group.stream()
                        .filter(a -> !a.isInLove())
                        .min(Comparator.comparingInt(a -> a.tickCount))
                        .orElse(null);
                if (candidate != null) {
                    cullTarget = candidate;
                    cullMode = true;
                    return true;
                }
            } else if (count >= 2 && count < thresholds[0] && dockBE != null) {
                List<Animal> candidates = group.stream().filter(Animal::canFallInLove).toList();
                if (candidates.size() >= 2 && hasFoodFor(dockBE, candidates.get(0))) {
                    breedTarget1 = candidates.get(0);
                    breedTarget2 = candidates.get(1);
                    firstFed = false;
                    cullMode = false;
                    return true;
                }
            }
        }
        return false;
    }

    private int[] getThresholds(EntityType<?> type) {
        return SPECIES_THRESHOLDS.getOrDefault(EntityType.getKey(type), new int[]{defaultMin, defaultMax});
    }

    private boolean hasFoodFor(DockingStationBlockEntity dockBE, Animal animal) {
        ItemStackHandler inv = dockBE.getMainInv();
        for (int slot = DockingStationBlockEntity.clipboardSlot + 1; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && animal.isFood(stack)) return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (!byteBuddy.canAct()) return false;
        if (cullMode) return cullTarget != null && cullTarget.isAlive();
        Animal active = firstFed ? breedTarget2 : breedTarget1;
        return active != null && active.isAlive();
    }

    @Override
    public void start() {
        ticksStuck = 0;
        nextAttackTick = 0;
    }

    @Override
    public void stop() {
        byteBuddy.getNavigation().stop();
        cullTarget = null;
        breedTarget1 = null;
        breedTarget2 = null;
        firstFed = false;
        cullMode = false;
    }

    @Override
    public void tick() {
        if (!(byteBuddy.level() instanceof ServerLevel sl)) return;
        if (cullMode) tickCull(sl);
        else tickBreed(sl);
    }

    private void tickCull(ServerLevel sl) {
        if (cullTarget == null || !cullTarget.isAlive()) {
            nextActionTick = sl.getGameTime() + actionCooldownTicks;
            stop();
            return;
        }
        double dist = byteBuddy.distanceToSqr(cullTarget);
        if (++ticksStuck > movingTimeout && dist > reachDistSq) {
            nextActionTick = sl.getGameTime() + actionCooldownTicks;
            stop();
            return;
        }
        byteBuddy.getNavigation().moveTo(cullTarget, moveSpeed);
        byteBuddy.getLookControl().setLookAt(cullTarget, 30f, 30f);
        if (dist <= reachDistSq) {
            ticksStuck = 0;
            if (sl.getGameTime() >= nextAttackTick && byteBuddy.consumeEnergy(actionEnergyCost)) {
                byteBuddy.doHurtTarget(cullTarget);
                nextAttackTick = sl.getGameTime() + attackCooldownTicks;
            }
        }
    }

    private void tickBreed(ServerLevel sl) {
        Animal current = firstFed ? breedTarget2 : breedTarget1;
        if (current == null || !current.isAlive()) {
            nextActionTick = sl.getGameTime() + actionCooldownTicks;
            stop();
            return;
        }
        if (++ticksStuck > movingTimeout) {
            nextActionTick = sl.getGameTime() + actionCooldownTicks;
            stop();
            return;
        }
        byteBuddy.getNavigation().moveTo(current, moveSpeed);
        byteBuddy.getLookControl().setLookAt(current, 30f, 30f);
        if (byteBuddy.distanceToSqr(current) <= reachDistSq) {
            ticksStuck = 0;
            if (!feedAnimal(sl, current)) {
                nextActionTick = sl.getGameTime() + actionCooldownTicks;
                stop();
                return;
            }
            if (!firstFed) {
                firstFed = true;
                ticksStuck = 0;
            } else {
                nextActionTick = sl.getGameTime() + actionCooldownTicks;
                stop();
            }
        }
    }

    private boolean feedAnimal(ServerLevel sl, Animal animal) {
        DockingStationBlockEntity dockBE = GoalUtil.dockBlockEntity(byteBuddy);
        if (dockBE == null) return false;
        ItemStackHandler inv = dockBE.getMainInv();
        for (int slot = DockingStationBlockEntity.clipboardSlot + 1; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && animal.isFood(stack)) {
                if (!byteBuddy.consumeEnergy(actionEnergyCost)) return false;
                inv.extractItem(slot, 1, false);
                animal.setInLove(null);
                return true;
            }
        }
        return false;
    }
}
