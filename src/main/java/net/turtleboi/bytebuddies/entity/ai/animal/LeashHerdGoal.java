package net.turtleboi.bytebuddies.entity.ai.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.item.custom.ClipboardItem;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class LeashHerdGoal extends Goal {

    private enum Phase { LEASHING, DELIVERING, RELEASING, RETURNING }

    private static final double leashReachDistSq = 4.0;
    private static final double deliveryReachDistSq = 9.0;
    private static final double returnStopDistSq = 16.0;
    private static final double scanRadius = 8.0;
    private static final double moveSpeed = 1.05;
    private static final int movingTimeout = 200;

    private final ByteBuddyEntity byteBuddy;
    private Phase phase;
    private Animal leashTarget;
    private BlockPos destination;
    private int ticksStuck;

    public LeashHerdGoal(ByteBuddyEntity byteBuddy) {
        this.byteBuddy = byteBuddy;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(byteBuddy.level() instanceof ServerLevel sl)) return false;
        if (byteBuddy.getDock().isPresent()) return false;
        if (!byteBuddy.canAct()) return false;
        LivingEntity owner = byteBuddy.getOwner(sl);
        if (owner == null || owner.isSpectator() || owner.level() != byteBuddy.level()) return false;
        if (!hasLeadInCargo()) return false;
        Animal animal = findLeashTarget(sl);
        if (animal == null) return false;
        leashTarget = animal;
        phase = Phase.LEASHING;
        ticksStuck = 0;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!byteBuddy.canAct()) return false;
        return phase != null;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {
        phase = null;
        leashTarget = null;
        destination = null;
        byteBuddy.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (!(byteBuddy.level() instanceof ServerLevel sl)) return;
        if (phase == null) return;
        switch (phase) {
            case LEASHING -> tickLeashing(sl);
            case DELIVERING -> tickDelivering(sl);
            case RELEASING -> tickReleasing(sl);
            case RETURNING -> tickReturning(sl);
        }
    }

    private void tickLeashing(ServerLevel sl) {
        if (leashTarget == null || !leashTarget.isAlive()) {
            phase = null;
            return;
        }
        if (++ticksStuck > movingTimeout) {
            phase = null;
            return;
        }
        byteBuddy.getNavigation().moveTo(leashTarget, moveSpeed);
        byteBuddy.getLookControl().setLookAt(leashTarget, 30f, 30f);
        if (byteBuddy.distanceToSqr(leashTarget) <= leashReachDistSq) {
            ticksStuck = 0;
            applyLeash(leashTarget);
            leashTarget = null;
            BlockPos dest = getClipboardDestination();
            if (dest != null) {
                destination = dest;
                phase = Phase.DELIVERING;
                ticksStuck = 0;
            } else {
                phase = null;
            }
        }
    }

    private void tickDelivering(ServerLevel sl) {
        if (destination == null) {
            phase = Phase.RELEASING;
            return;
        }
        if (++ticksStuck > movingTimeout * 3) {
            phase = Phase.RELEASING;
            return;
        }
        Vec3 dest = Vec3.atCenterOf(destination);
        byteBuddy.getNavigation().moveTo(dest.x, dest.y, dest.z, moveSpeed);
        byteBuddy.getLookControl().setLookAt(dest.x, dest.y, dest.z, 30f, 30f);
        if (byteBuddy.distanceToSqr(dest) <= deliveryReachDistSq) {
            ticksStuck = 0;
            phase = Phase.RELEASING;
        }
    }

    private void tickReleasing(ServerLevel sl) {
        releaseAllLeashed(sl);
        LivingEntity owner = byteBuddy.getOwner(sl);
        if (owner != null && byteBuddy.distanceToSqr(owner) > returnStopDistSq) {
            phase = Phase.RETURNING;
            ticksStuck = 0;
        } else {
            phase = null;
        }
    }

    private void tickReturning(ServerLevel sl) {
        LivingEntity owner = byteBuddy.getOwner(sl);
        if (owner == null || owner.level() != byteBuddy.level()) {
            phase = null;
            return;
        }
        if (++ticksStuck > movingTimeout * 4) {
            phase = null;
            return;
        }
        byteBuddy.getNavigation().moveTo(owner, moveSpeed);
        byteBuddy.getLookControl().setLookAt(owner, 30f, 30f);
        if (byteBuddy.distanceToSqr(owner) <= returnStopDistSq) {
            phase = null;
        }
    }

    private void applyLeash(Animal animal) {
        ItemStackHandler inv = byteBuddy.getMainInv();
        int first = byteBuddy.getFirstCargoSlot();
        int last = byteBuddy.getLastCargoSlot();
        for (int slot = first; slot <= last; slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(Items.LEAD)) {
                inv.extractItem(slot, 1, false);
                animal.setLeashedTo(byteBuddy, true);
                return;
            }
        }
    }

    private void releaseAllLeashed(ServerLevel sl) {
        List<Animal> leashed = getMyLeashed(sl);
        for (Animal a : leashed) {
            a.dropLeash(true, false);
            byteBuddy.addToCargo(new ItemStack(Items.LEAD));
        }
    }

    private List<Animal> getMyLeashed(ServerLevel sl) {
        AABB box = byteBuddy.getBoundingBox().inflate(12.0);
        return sl.getEntitiesOfClass(Animal.class, box,
                a -> a.isLeashed() && byteBuddy.equals(a.getLeashHolder()));
    }

    private Animal findLeashTarget(ServerLevel sl) {
        AABB box = byteBuddy.getBoundingBox().inflate(scanRadius);
        return sl.getEntitiesOfClass(Animal.class, box,
                        a -> !a.isLeashed()
                                && !(a instanceof TamableAnimal ta && ta.getOwnerUUID() != null))
                .stream()
                .min(Comparator.comparingDouble(a -> a.distanceToSqr(byteBuddy)))
                .orElse(null);
    }

    private boolean hasLeadInCargo() {
        ItemStackHandler inv = byteBuddy.getMainInv();
        int first = byteBuddy.getFirstCargoSlot();
        int last = byteBuddy.getLastCargoSlot();
        for (int slot = first; slot <= last; slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(Items.LEAD)) return true;
        }
        return false;
    }

    private BlockPos getClipboardDestination() {
        ItemStack clipboard = byteBuddy.getClipboardStack();
        if (clipboard.isEmpty()) return null;
        return ClipboardItem.getFirstPosition(clipboard).orElse(null);
    }
}
