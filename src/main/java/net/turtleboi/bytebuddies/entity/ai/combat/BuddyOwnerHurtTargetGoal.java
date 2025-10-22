package net.turtleboi.bytebuddies.entity.ai.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class BuddyOwnerHurtTargetGoal extends TargetGoal {
    private final ByteBuddyEntity byteBuddy;
    private LivingEntity ownerLastHurt;
    private int timestamp;

    public BuddyOwnerHurtTargetGoal(ByteBuddyEntity byteBuddy) {
        super(byteBuddy, false);
        this.byteBuddy = byteBuddy;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override public boolean canUse() {
        LivingEntity owner = byteBuddy.getOwner(levelAsServer());
        if (owner != null && byteBuddy.getDock().isEmpty()) {
            this.ownerLastHurt = owner.getLastHurtMob();
            int i = owner.getLastHurtMobTimestamp();
            return i != this.timestamp
                    && this.canAttack(this.ownerLastHurt, TargetingConditions.DEFAULT);
        }
        return false;
    }

    @Override public void start() {
        this.mob.setTarget(this.ownerLastHurt);
        LivingEntity livingentity = byteBuddy.getOwner(levelAsServer());
        if (livingentity != null) {
            this.timestamp = livingentity.getLastHurtMobTimestamp();
        }
        super.start();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    private @Nullable ServerLevel levelAsServer() {
        return (byteBuddy.level() instanceof ServerLevel serverLevel) ? serverLevel : null;
    }
}
