package net.turtleboi.turtlecore.spells;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShockwaveSpell {
    private static final ConcurrentHashMap<Long, Set<UUID>> hitPerTick = new ConcurrentHashMap<>();
    public static void triggerShockwave(LivingEntity sourceEntity, int radius, int scalingLevel) {
        if (sourceEntity.level() instanceof ServerLevel serverLevel) {
            BlockPos center = sourceEntity.blockPosition().below();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;

                    BlockPos blockPos = center.offset(dx, 0, dz);
                    BlockState blockState = serverLevel.getBlockState(blockPos);
                    if (blockState.isAir() || blockState.getFluidState().is(FluidTags.WATER)) continue;

                    double offsetX = blockPos.getX() + 0.5 - sourceEntity.getX();
                    double offsetZ = blockPos.getZ() + 0.5 - sourceEntity.getZ();
                    double centerDist = Math.sqrt(offsetX * offsetX + offsetZ * offsetZ);

                    long delayTicks = (long) centerDist;
                    SpellScheduler.schedule(serverLevel, delayTicks, () -> {
                        long gameTick = serverLevel.getGameTime();
                        Set<UUID> hitSet = hitPerTick.computeIfAbsent(gameTick, k -> new HashSet<>());
                        applyAtBlockOnce(serverLevel, sourceEntity, center, blockPos, radius, centerDist, scalingLevel, hitSet);
                    });
                }
            }
        }
    }

    private static void applyAtBlockOnce(ServerLevel serverLevel, LivingEntity sourceEntity, BlockPos center, BlockPos blockPos,
                                         int maxRadius, double centerDist, int scalingLevel,
                                         Set<UUID> hitThisTick) {
        double blockY = blockPos.getY() + 1.0;
        AABB hitBox = new AABB(blockPos.getX(), blockY - 0.25, blockPos.getZ(),
                blockPos.getX() + 1.0, blockY + 0.75, blockPos.getZ() + 1.0).inflate(0.5);

        List<Entity> nearbyEntities = serverLevel.getEntitiesOfClass(Entity.class, hitBox);
        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity livingEntity && !livingEntity.is(sourceEntity)) {
                UUID uuid = livingEntity.getUUID();
                if (!hitThisTick.add(uuid)) {
                    continue;
                }

                Vec3 fromCenter = livingEntity.position().subtract(center.getX() + 0.5, sourceEntity.getY(), center.getZ() + 0.5);
                Vec3 horizontal = new Vec3(fromCenter.x, 0.0, fromCenter.z);
                if (horizontal.lengthSqr() < 1.0e-6) continue;
                Vec3 normal = horizontal.normalize();

                double knockbackStrength = computeKnockbackStrength(centerDist, maxRadius, scalingLevel);
                double verticalBoost = 0.15 + 0.05 * serverLevel.random.nextDouble();

                Vec3 currentMotion = livingEntity.getDeltaMovement();
                Vec3 addedMotion = new Vec3(normal.x * knockbackStrength, verticalBoost, normal.z * knockbackStrength);
                livingEntity.setDeltaMovement(currentMotion.add(addedMotion));
                livingEntity.hurtMarked = true;

                float damageAmount = computeDamageAmount(centerDist, scalingLevel);
                if (damageAmount > 0.01f) {
                    livingEntity.hurt(serverLevel.damageSources().magic(), damageAmount);
                }
            }
        }
    }

    private static double computeKnockbackStrength(double ringDistance, int maxRadius, int scalingLevel) {
        double distScale = 1.0 - Mth.clamp(ringDistance / Math.max(1.0, maxRadius), 0.0, 1.0);
        double base = 0.25 + 0.35 * distScale;
        double scalar = Math.max(1.0, 1.0 + 0.25 * scalingLevel);
        return base * scalar;
    }

    private static float computeDamageAmount(double ringDistance, int scalingLevel) {
        float base = 3.0f;
        float falloff = Math.max(1.0f, (float)(3.0 - ringDistance));
        float levelScale = Math.max(1.0f, 1.0f + 0.5f * scalingLevel);
        float damage = base * falloff * levelScale;
        return Math.min(damage, 12.0f);
    }
}
