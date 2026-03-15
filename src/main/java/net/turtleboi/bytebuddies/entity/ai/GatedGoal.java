package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.util.BotDebug;

import java.util.function.BooleanSupplier;

public class GatedGoal extends Goal {
    private final ByteBuddyEntity byteBuddy;
    private final BooleanSupplier requirement;
    private final Goal lockedGoal;
    private final int cooldownTicks;

    public GatedGoal(ByteBuddyEntity byteBuddy, BooleanSupplier requirement, Goal lockedGoal, int cooldownTicks) {
        this.byteBuddy = byteBuddy;
        this.requirement = requirement;
        this.lockedGoal = lockedGoal;
        this.cooldownTicks = cooldownTicks;
        this.setFlags(lockedGoal.getFlags());
    }

    @Override
    public boolean canUse() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            if (!byteBuddy.canAct()) return false;
            if (byteBuddy.isOutsideTether()) return false;
            if (!requirement.getAsBoolean()) {
                BotDebug.log(byteBuddy, "[ByteBuddy: " + byteBuddy.getId() + "] GatedGoal " + lockedGoal.getClass().getSimpleName() + ": requirement = false");
                return false;
            }
            if (byteBuddy.cooldownActive(serverLevel)) {
                BotDebug.log(byteBuddy, "[ByteBuddy: " + byteBuddy.getId() + "] GatedGoal " + lockedGoal.getClass().getSimpleName() + ": cooldown active");
                return false;
            }
            boolean canUse = lockedGoal.canUse();
            if (!canUse) {
                BotDebug.log(byteBuddy, "[ByteBuddy: " + byteBuddy.getId() + "] GatedGoal " + lockedGoal.getClass().getSimpleName() + ": inner canUse = false");
            }
            return canUse;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            if (!byteBuddy.canAct()) return false;
            if (byteBuddy.isOutsideTether()) return false;
            if (!requirement.getAsBoolean()) return false;
            if (byteBuddy.cooldownActive(serverLevel)) return false;
            boolean continueToUse = lockedGoal.canContinueToUse();
            if (!continueToUse) {
                BotDebug.log(byteBuddy, "[ByteBuddy: " + byteBuddy.getId() + "] GatedGoal " + lockedGoal.getClass().getSimpleName() + ": canContinueToUse = false");
            }
            return continueToUse;
        }
        return false;
    }

    @Override
    public void start() {
        lockedGoal.start();
    }

    @Override
    public void tick() {
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            if (!byteBuddy.canAct() || byteBuddy.cooldownActive(serverLevel) || !requirement.getAsBoolean()) {
                lockedGoal.stop();
                return;
            }
            lockedGoal.tick();
        }
    }

    @Override
    public void stop() {
        lockedGoal.stop();
        if (byteBuddy.level() instanceof ServerLevel serverLevel) {
            byteBuddy.armCooldown(serverLevel, cooldownTicks);
        }
    }

    @Override
    public boolean isInterruptable() {
        return lockedGoal.isInterruptable();
    }
}
