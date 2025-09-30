package net.turtleboi.bytebuddies.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.EnumSet;
import java.util.List;

public class RandomWaveAtFriendGoal extends Goal {
    private final ByteBuddyEntity byteBuddy;
    private final double radius;
    private final int cooldownTicks;
    private final int waveDurationTicks;
    private int cooldown;
    private int remaining;
    private LivingEntity target;

    public RandomWaveAtFriendGoal(ByteBuddyEntity byteBuddy, double radius, int cooldownTicks, int waveDurationTicks) {
        this.byteBuddy = byteBuddy;
        this.radius = radius;
        this.cooldownTicks = cooldownTicks;
        this.waveDurationTicks = waveDurationTicks;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    private boolean hasEyesOn(LivingEntity livingEntity) {
        var origin = byteBuddy.getEyePosition();
        var eyeDestination = livingEntity.getEyePosition();
        var hitResult = byteBuddy.level().clip( new ClipContext(
                origin, eyeDestination,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                byteBuddy
        ));
        return hitResult.getType() == HitResult.Type.MISS;
    }

    private boolean inFov(LivingEntity livingEntity) {
        var look = byteBuddy.getViewVector(1.0F).normalize();
        var dir = livingEntity.getEyePosition().subtract(byteBuddy.getEyePosition()).normalize();
        return look.dot(dir) > 0.2;
    }

    @Override
    public boolean canUse() {
        if (byteBuddy.isSleeping() || byteBuddy.isWaving() || byteBuddy.isWaking()) return false;
        if (cooldown > 0) { cooldown--; return false; }

        List<LivingEntity> candidates = byteBuddy.level().getEntitiesOfClass(
                LivingEntity.class,
                byteBuddy.getBoundingBox().inflate(radius),
                livingEntity ->
                                livingEntity.isAlive()
                                && livingEntity != byteBuddy
                                && !(livingEntity instanceof Monster)
                                && hasEyesOn(livingEntity)
                                && inFov(livingEntity)
        );

        if (candidates.isEmpty()) return false;
        if (byteBuddy.getRandom().nextInt(3) != 0) return false;

        target = candidates.get(byteBuddy.getRandom().nextInt(candidates.size()));
        remaining = waveDurationTicks;
        return target != null;
    }

    @Override
    public void start() {
        byteBuddy.setWaving(true);
        byteBuddy.getLookControl().setLookAt(target, 30, 30);
    }

    @Override
    public boolean canContinueToUse() {
        return remaining > 0 && byteBuddy.isAlive() && !byteBuddy.isSleeping() && target != null && target.isAlive();
    }

    @Override
    public void tick() {
        if (target != null) {
            byteBuddy.getLookControl().setLookAt(target, 30, 30);
        }

        if (--remaining <= 0) {

        }
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void stop() {
        byteBuddy.setWaving(false);
        cooldown = cooldownTicks;
        target = null;
        remaining = 0;
    }
}
