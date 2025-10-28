package net.turtleboi.bytebuddies.entity.ai.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.item.ModItems;
import net.turtleboi.bytebuddies.util.BotDebug;
import net.turtleboi.bytebuddies.util.GoalUtil;
import net.turtleboi.bytebuddies.util.ToolUtil;
import net.turtleboi.bytebuddies.util.ToolUtil.ToolType;

import java.util.EnumSet;

public class BuddyMeleeAttackGoal extends Goal {
    private enum Phase {
        KITE, APPROACH, WINDUP
    }

    private final ByteBuddyEntity byteBuddy;
    private final double speed;
    private final boolean rememberTarget;

    private LivingEntity target;

    private int energyPerHit = 0;
    private static final ToolType requiredTool = ToolType.SWORD;

    private static final double safeDistance = 64.0;
    private static final float swellProgress = 0.55f;
    private boolean fleeingFromCreeper = false;

    private final double HIT_REACH;
    private static final double WINDUP_PAD = 0.5;

    private static final double KITE_TARGET = 2.5;
    private static final double KITE_ENTER = KITE_TARGET + 0.5;
    private static final double KITE_EXIT = KITE_TARGET + 0.5;
    private static final int REPATH_COOLDOWN = 2;

    private boolean requireKiteRadius = false;

    private int lastHurtTime = 0;
    private static final int HURT_COOLDOWN_TICKS = 12;
    private static final int MISS_COOLDOWN_TICKS = 10;

    private long hitTick = 0L;
    private long animEndTick = 0L;
    private boolean hitFired = false;

    private long nextAttackTick = 0L;

    private Phase phase = Phase.KITE;

    private long lastPathRecalcTick = 0L;

    public BuddyMeleeAttackGoal(ByteBuddyEntity byteBuddy, double speed, double hitReach, boolean rememberTarget) {
        this.byteBuddy = byteBuddy;
        this.speed = speed;
        this.HIT_REACH = hitReach;
        this.rememberTarget = rememberTarget;
        this.energyPerHit = (int) (4 * byteBuddy.getAttributeValue(Attributes.ATTACK_DAMAGE));
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        boolean result;
        target = byteBuddy.getTarget();
        if (target == null || !target.isAlive()) {
            result = false;
        } else {
            if (!GoalUtil.ensureUse(byteBuddy, requiredTool, energyPerHit, 1)) {
                result = false;
            } else {
                result = byteBuddy.getDock().isEmpty();
            }
        }
        return result;
    }

    @Override
    public boolean canContinueToUse() {
        boolean result;
        if (target == null || !target.isAlive()) {
            result = false;
        } else {
            if (rememberTarget) {
                boolean farOrNavigating = byteBuddy.distanceToSqr(target) > 3.0 || byteBuddy.getNavigation().isInProgress();
                boolean notKiting = phase != Phase.KITE || currentServerTime() < nextAttackTick;
                result = farOrNavigating || notKiting;
            } else {
                result = canUse();
            }
        }
        return result;
    }

    @Override
    public void start() {
        byteBuddy.setAggressive(true);
        clearAnim();
        phase = Phase.KITE;
        nextAttackTick = 0L;
        requireKiteRadius = false;
    }

    @Override
    public void stop() {
        byteBuddy.setAggressive(false);
        target = null;
        byteBuddy.getNavigation().stop();
        clearAnim();
        phase = Phase.KITE;
        requireKiteRadius = false;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void tick() {
        ServerLevel serverLevel = (byteBuddy.level() instanceof ServerLevel levelServer) ? levelServer : null;
        long currentTick = (serverLevel != null) ? serverLevel.getGameTime() : 0L;

        if (target != null && target.isAlive()) {
            LivingEntity latest = byteBuddy.getTarget();
            if (latest != null) {
                target = latest;
            }
            tickAnim(currentTick);
            byteBuddy.getLookControl().setLookAt(target, 90.0f, 90.0f);

            boolean creeperHandled = handleCreeper(currentTick);
            if (!creeperHandled) {
                lowHealthFlee(byteBuddy, currentTick);

                double distanceSquared = byteBuddy.distanceToSqr(target);
                if (phase == Phase.KITE) {
                    boolean cooldownReady = currentTick >= nextAttackTick;
                    if (requireKiteRadius && distanceSquared < (KITE_EXIT * KITE_EXIT)) {
                        kiteTowardRadius(KITE_TARGET, Math.max(1.0, speed * 1.66), currentTick);
                    } else {
                        requireKiteRadius = false;
                        if (!cooldownReady || distanceSquared < (KITE_ENTER * KITE_ENTER)) {
                            kiteTowardRadius(KITE_TARGET, Math.max(1.0, speed * 1.66), currentTick);
                        } else {
                            phase = Phase.APPROACH;
                        }
                    }
                } else if (phase == Phase.APPROACH) {
                    if (currentTick < nextAttackTick) {
                        phase = Phase.KITE;
                        requireKiteRadius = true;
                    } else {
                        throttledMoveToTarget(currentTick, speed);
                        if (inWindupRangeAabb()) {
                            if (byteBuddy.getEnergyStorage().getEnergyStored() >= energyPerHit) {
                                startWindup(currentTick);
                                phase = Phase.WINDUP;
                                //byteBuddy.getNavigation().stop();
                            } else {
                                strafeAround(target, Math.max(1.0, speed), currentTick);
                            }
                        }
                    }
                } else if (phase == Phase.WINDUP) {
                    if (animEndTick <= 0 || currentTick >= animEndTick) {
                        phase = Phase.KITE;
                    }
                }
            }
        }
    }

    private void throttledMoveToTarget(long currentTick, double runSpeed) {
        if (currentTick - lastPathRecalcTick >= REPATH_COOLDOWN) {
            byteBuddy.getNavigation().moveTo(target, runSpeed);
            lastPathRecalcTick = currentTick;
        }
    }

    private boolean handleCreeper(long currentTick) {
        boolean result;
        if (!(target instanceof Creeper creeper)) {
            fleeingFromCreeper = false;
            result = false;
        } else {
            double distanceSquared = byteBuddy.distanceToSqr(target);
            float swelling = creeper.getSwelling(0.0F);
            boolean dangerousNow = creeper.isIgnited() || swelling >= swellProgress;

            if (!fleeingFromCreeper) {
                if (dangerousNow && distanceSquared < safeDistance) {
                    fleeingFromCreeper = true;
                    byteBuddy.getNavigation().stop();
                }
            } else {
                boolean calmed = !creeper.isIgnited() && swelling <= 0.35f;
                if (calmed && distanceSquared >= safeDistance) {
                    fleeingFromCreeper = false;
                }
            }

            if (fleeingFromCreeper) {
                fleeFrom(target, Math.max(1.0, speed * 1.66));
                ensureKiteUntil(currentTick + 10);
                result = true;
            } else {
                result = false;
            }
        }
        return result;
    }

    private void lowHealthFlee(ByteBuddyEntity byteBuddy, long currentTick) {
        if (byteBuddy.getHealth() < (byteBuddy.getMaxHealth() * 0.4)) {
            if (justGotHit()) {
                nextAttackTick = Math.max(nextAttackTick, currentTick + HURT_COOLDOWN_TICKS);
                requireKiteRadius = true;
            }
        }
    }

    private void ensureKiteUntil(long absoluteTick) {
        nextAttackTick = Math.max(nextAttackTick, absoluteTick);
        phase = Phase.KITE;
    }

    private void kiteTowardRadius(double targetRadius, double runSpeed, long currentTick) {
        if (target != null) {
            double distance = Math.sqrt(byteBuddy.distanceToSqr(target));
            boolean alreadyOutside = distance >= KITE_TARGET;
            if (alreadyOutside) {
                strafeAround(target, runSpeed, currentTick);
            } else {
                double delta = Mth.clamp(targetRadius - distance, 2.0, 12.0);
                Vec3 away = byteBuddy.position().subtract(target.position());
                if (away.lengthSqr() < 1.0e-4) {
                    away = new Vec3(1, 0, 0);
                }
                away = away.normalize().scale(delta);
                Vec3 destination = byteBuddy.position().add(away.x, 0, away.z);
                Vec3 random = DefaultRandomPos.getPosAway(byteBuddy, 14, 6, target.position());
                if (random != null) {
                    destination = random;
                }
                if (currentTick - lastPathRecalcTick >= REPATH_COOLDOWN) {
                    byteBuddy.getNavigation().moveTo(destination.x, destination.y, destination.z, runSpeed);
                    lastPathRecalcTick = currentTick;
                }
            }
        }
    }

    private void strafeAround(LivingEntity pivot, double runSpeed, long currentTick) {
        Vec3 toMe = byteBuddy.position().subtract(pivot.position());
        if (toMe.lengthSqr() < 1.0e-4) {
            toMe = new Vec3(1, 0, 0);
        }
        Vec3 perpendicular = new Vec3(-toMe.z, 0, toMe.x).normalize().scale(2.5);
        Vec3 destination = byteBuddy.position().add(perpendicular);
        if (currentTick - lastPathRecalcTick >= REPATH_COOLDOWN) {
            byteBuddy.getNavigation().moveTo(destination.x, destination.y, destination.z, Math.max(1.0, runSpeed));
            lastPathRecalcTick = currentTick;
        }
    }

    private void fleeFrom(LivingEntity threat, double runSpeed) {
        Vec3 away = byteBuddy.position().subtract(threat.position());
        if (away.lengthSqr() < 1.0e-4) {
            away = new Vec3(1, 0, 0);
        }
        away = away.normalize().scale(8.0);
        Vec3 destination = byteBuddy.position().add(away.x, 0, away.z);
        Vec3 random = DefaultRandomPos.getPosAway(byteBuddy, 12, 6, threat.position());
        if (random != null) {
            destination = random;
        }
        byteBuddy.getNavigation().moveTo(destination.x, destination.y, destination.z, runSpeed);
    }

    private boolean justGotHit() {
        boolean result;
        int hurtTime = byteBuddy.hurtTime;
        boolean edge = (hurtTime > 0 && lastHurtTime <= 0);
        lastHurtTime = hurtTime;
        result = edge;
        return result;
    }

    private double toolReachBonus() {
        if (byteBuddy.getHeldTool().is(ModItems.BUSTER_SWORD.get()) || byteBuddy.getHeldTool().is(ModItems.TERRABLADE.get())) {
            return 1;
        }
        return 0.0;
    }

    private boolean inMeleeRangeAabb() {
        if (target == null) {
            return false;
        }

        double reach = HIT_REACH + toolReachBonus();
        return byteBuddy.getBoundingBox()
                .inflate(reach / 2, byteBuddy.getBbWidth(), reach / 2)
                .intersects(target.getBoundingBox());
    }

    private boolean inWindupRangeAabb() {
        if (target == null) {
            return false;
        }

        double reach = HIT_REACH + toolReachBonus() + WINDUP_PAD;
        return byteBuddy.getBoundingBox()
                .inflate(reach / 2, byteBuddy.getBbWidth(), reach / 2)
                .intersects(target.getBoundingBox());
    }

    private long currentServerTime() {
        return (byteBuddy.level() instanceof ServerLevel serverLevel) ? serverLevel.getGameTime() : 0L;
    }

    private void startWindup(long currentTick) {
        hitFired = false;
        int totalTicks = GoalUtil.toTicks(1.6);
        int startTicks = GoalUtil.toTicks(0.7);
        hitTick = currentTick + Math.max(0, startTicks);
        animEndTick = currentTick + Math.max(1, totalTicks);
        byteBuddy.setSlicing(true);
        double attackSpeed = Math.max(0.1, byteBuddy.getAttributeValue(Attributes.ATTACK_SPEED));
        int cooldownTicks = Mth.clamp(Mth.ceil(20.0 / attackSpeed), 4, 40);
        nextAttackTick = currentTick + cooldownTicks;
    }

    private void clearAnim() {
        hitFired = false;
        hitTick = 0L;
        animEndTick = 0L;
        byteBuddy.setSlicing(false);
    }

    private void tickAnim(long currentTick) {
        if (animEndTick > 0) {
            if (!hitFired && currentTick >= hitTick) {
                hitFired = true;
                LivingEntity latest = byteBuddy.getTarget();
                if (latest != null) {
                    target = latest;
                }
                if (target != null && target.isAlive()) {
                    if (inMeleeRangeAabb() && byteBuddy.consumeEnergy(energyPerHit)) {
                        byteBuddy.swing(InteractionHand.MAIN_HAND, true);
                        byteBuddy.doHurtTarget(target);
                        ToolUtil.applyToolWear(byteBuddy, requiredTool, 1.0f);
                        byteBuddy.onTaskSuccess(ByteBuddyEntity.TaskType.COMBAT, byteBuddy.getOnPos());
                        phase = Phase.KITE;
                        requireKiteRadius = true;
                    } else {
                        BotDebug.log(byteBuddy, "COMBAT: miss/energy fail at hit frame");
                        phase = Phase.KITE;
                        requireKiteRadius = true;
                        nextAttackTick = Math.max(nextAttackTick, currentTick + MISS_COOLDOWN_TICKS);
                    }
                } else {
                    phase = Phase.KITE;
                    requireKiteRadius = true;
                }
            }

            if (currentTick >= animEndTick) {
                clearAnim();
            }
        }
    }
}
