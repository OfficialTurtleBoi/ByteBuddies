package net.turtleboi.bytebuddies.entity.entities;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.particle.ModParticles;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class SwordSweepEntity extends Projectile {
    int lifespan = 40;
    private float projectileDamage = 0.0f;
    private final Set<UUID> hitEntities = new HashSet<>();
    private static final float originalWidth = 0.625f;
    private static final float originalHeight = 2.0f;
    private float entityWidth = originalWidth;
    private float entityHeight = originalHeight;
    private boolean traveling = false;
    private double travelDistance = 1.0;
    private double traveledDistance = 0.0;
    private int lastSize = -1;

    private static final EntityDataAccessor<Float> DATA_WIDTH  =
            SynchedEntityData.defineId(SwordSweepEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(SwordSweepEntity.class, EntityDataSerializers.FLOAT);

    public SwordSweepEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public SwordSweepEntity(Level level) {
        this(ModEntities.SWORD_SWEEP.get(), level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WIDTH, originalWidth);
        builder.define(DATA_HEIGHT, originalHeight);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataValues) {
        super.onSyncedDataUpdated(dataValues);
        if (dataValues == DATA_WIDTH || dataValues == DATA_HEIGHT) {
            this.entityWidth = this.entityData.get(DATA_WIDTH);
            this.entityHeight = this.entityData.get(DATA_HEIGHT);
            this.setOldPosAndRot();
            this.refreshDimensions();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            for (int i = 0; i < 1; i++) {
                level().addParticle(
                        ModParticles.SUPERCHARGED_PARTICLE.get(),
                        this.getX(), this.getY() + (this.getBbHeight()/2), this.getZ(),
                        0, 0, 0
                );
            }
        }

        for (Entity entity : level().getEntities(this, this.getBoundingBox().inflate(0.3))) {
            if (entity == this.getOwner() || !(entity instanceof LivingEntity target)) continue;
            if (hitEntities.contains(entity.getUUID())) continue;

            target.hurt(this.damageSources().thrown(this, this.getOwner()), getProjectileDamage());
            hitEntities.add(entity.getUUID());
        }

        Vec3 deltaMovement = this.getDeltaMovement();
        double nextX = this.getX() + deltaMovement.x;
        double nextY = this.getY() + deltaMovement.y;
        double nextZ = this.getZ() + deltaMovement.z;

        double decelRate = -0.125;
        double minSpeed = 0.125;
        Vec3 adjustedDelta = deltaMovement.scale(1.0 + decelRate);
        double speed = adjustedDelta.length();
        if (speed < minSpeed) {
            if (speed > 1.0E-9) {
                adjustedDelta = adjustedDelta.scale(minSpeed / speed);
            } else {
                Vec3 fallback = deltaMovement.lengthSqr() > 1.0E-12 ? deltaMovement : this.getLookAngle();
                adjustedDelta = fallback.normalize().scale(minSpeed);
            }
        }

        if (!traveling) {
            double initialSpeed = this.getDeltaMovement().length();
            double decelFactorPerTick = 1.0 + decelRate;

            int tickToStop;
            if (initialSpeed > minSpeed) {
                tickToStop = (int)Math.ceil(Math.log(minSpeed / initialSpeed) / Math.log(decelFactorPerTick));
                tickToStop = Math.max(0, Math.min(tickToStop, this.getLifespan()));
            } else {
                tickToStop = 0;
            }

            double distanceBeforeStop = 0.0;
            if (tickToStop > 0) {
                distanceBeforeStop = initialSpeed * (1.0 - Math.pow(decelFactorPerTick, tickToStop)) / (1.0 - decelFactorPerTick);
            }

            int remainingTicks = Math.max(0, this.getLifespan() - tickToStop);
            double distanceAfterStop = remainingTicks * minSpeed;

            travelDistance = Math.max(1.0E-6, distanceBeforeStop + distanceAfterStop);
            traveledDistance = 0.0;
            traveling = true;
        }

        traveledDistance += adjustedDelta.length();
        this.setDeltaMovement(adjustedDelta);
        this.hasImpulse = true;
        this.setPos(nextX, nextY, nextZ);

        updateSizeFromScale();

        if (this.tickCount > lifespan) {
            this.discard();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.scalable(entityWidth, entityHeight);
    }

    private void updateSizeFromScale() {
        float scaleMultiplier = getScaleMultiplier();
        float targetWidth  = originalWidth * scaleMultiplier;
        float targetHeight = originalHeight * scaleMultiplier;
        int scale = Mth.floor(scaleMultiplier * 32f);
        if (!level().isClientSide && scale != lastSize) {
            lastSize = scale;
            this.entityData.set(DATA_WIDTH,  targetWidth);
            this.entityData.set(DATA_HEIGHT, targetHeight);

            this.entityWidth = targetWidth;
            this.entityHeight = targetHeight;
            this.refreshDimensions();
        }
    }

    private float getScaleMultiplier() {
        double distanceProgress = travelDistance <= 0.0 ? 1.0 : Mth.clamp(traveledDistance / travelDistance, 0.0, 1.0);
        float progress = (float)distanceProgress;
        return (progress * 2) + 0.25f;
    }

    public int getLifespan() {
        return lifespan;
    }

    public void setProjectileDamage(float projectileDamage) {
        this.projectileDamage = projectileDamage;
    }

    public float getProjectileDamage() {
        return this.projectileDamage;
    }

    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) {
            this.onHitEntity((EntityHitResult) result);
        }

    }

    @Override
    protected void onHitEntity(EntityHitResult result) {

    }

    @Override
    protected void onHitBlock(BlockHitResult result) {

    }
}
