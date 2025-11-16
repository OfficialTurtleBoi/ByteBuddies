package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.EnumSet;

public final class AccessInventoryGoal extends Goal {
    private final ByteBuddyEntity byteBuddy;

    public AccessInventoryGoal(ByteBuddyEntity byteBuddy) {
        this.byteBuddy = byteBuddy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override public boolean canUse() {
        return !byteBuddy.level().isClientSide && byteBuddy.isInteracting();
    }

    @Override public boolean canContinueToUse() {
        return !byteBuddy.level().isClientSide && byteBuddy.isInteracting();
    }

    @Override public boolean isInterruptable() { return false; }

    @Override public void start() {
        byteBuddy.getNavigation().stop();
        byteBuddy.setAggressive(false);
        byteBuddy.setWorking(false);
        byteBuddy.setSlamming(false);
        byteBuddy.setSlicing(false);
    }

    @Override public void tick() {
        Player player = byteBuddy.getCurrentViewer();
        if (player != null && player.isAlive()) {
            byteBuddy.getNavigation().stop();
            byteBuddy.getLookControl().setLookAt(player, 30.0f, 30.0f);
            byteBuddy.setDeltaMovement(0.0, byteBuddy.getDeltaMovement().y * 0.0, 0.0);
            byteBuddy.setJumping(false);
        }
    }
}
