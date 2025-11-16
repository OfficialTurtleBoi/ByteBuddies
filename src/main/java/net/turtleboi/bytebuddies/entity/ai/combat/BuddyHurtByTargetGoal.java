package net.turtleboi.bytebuddies.entity.ai.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.EnumSet;

public class BuddyHurtByTargetGoal extends TargetGoal {
    private final ByteBuddyEntity byteBuddy;
    private LivingEntity attacker;
    private int timestamp;

    public BuddyHurtByTargetGoal(ByteBuddyEntity byteBuddy) {
        super(byteBuddy, false);
        this.byteBuddy = byteBuddy;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        LivingEntity lastHurtBy = byteBuddy.getLastHurtByMob();
        int i = byteBuddy.getLastHurtByMobTimestamp();

        if (lastHurtBy == null || i == this.timestamp) {
            return false;
        }

        if (byteBuddy.isOwnedBy(lastHurtBy)) {
            return false;
        }

        if (lastHurtBy instanceof ByteBuddyEntity otherBuddy) {
            var myOwner = byteBuddy.getOwnerUUID();
            var buddyOwner = otherBuddy.getOwnerUUID();
            if (myOwner.isPresent() && buddyOwner.isPresent() && myOwner.get().equals(buddyOwner.get())) {
                return false;
            }
        }

        if (!this.canAttack(lastHurtBy, TargetingConditions.DEFAULT)) {
            return false;
        }

        this.attacker = lastHurtBy;
        return true;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.attacker);
        this.timestamp = byteBuddy.getLastHurtByMobTimestamp();
        super.start();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }
}
