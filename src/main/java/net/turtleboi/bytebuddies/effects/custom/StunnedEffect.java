package net.turtleboi.bytebuddies.effects.custom;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.turtleboi.bytebuddies.effects.ModEffects;
import net.turtleboi.bytebuddies.particle.ModParticles;
import net.turtleboi.bytebuddies.util.AttributeModifierUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class StunnedEffect extends MobEffect {
    private final String attributeModifierName = "stunned_movement_speed";
    public static final Map<UUID, StunPlayerData> stunnedPlayers = new HashMap<>();
    private final List<LivingEntity> storedEntities = new ArrayList<>();


    public StunnedEffect(MobEffectCategory mobEffectCategory, int color) {
        super(mobEffectCategory, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        Player stunningPlayer = null;
        if (pLivingEntity.getPersistentData().hasUUID("StunnedBy")) {
            stunningPlayer = pLivingEntity.level().getPlayerByUUID(pLivingEntity.getPersistentData().getUUID("StunnedBy"));
        }



        int duration = Objects.requireNonNull(pLivingEntity.getEffect(ModEffects.STUNNED)).getDuration();
        if (!pLivingEntity.level().isClientSide() && duration > 2) {
            //System.out.println("Stunning: " + pLivingEntity);
            //System.out.println("Duration: " + duration);
            if (pLivingEntity instanceof Player player) {
                if (!stunnedPlayers.containsKey(player.getUUID())) {
                    StunPlayerData data = new StunPlayerData(
                            player.getYRot(), player.getXRot(),
                            player.getAbilities().getWalkingSpeed(), player.getAbilities().getFlyingSpeed(),
                            player.getX(), player.getY(), player.getZ());
                    stunnedPlayers.put(player.getUUID(), data);
                    player.getAbilities().mayBuild = false;
                    player.getAbilities().flying = false;
                    player.getAbilities().setWalkingSpeed(0.0f);
                    if (!player.isCreative()) {
                        player.getAbilities().setFlyingSpeed(0);
                        player.getAbilities().invulnerable = false;
                    }
                    player.getAbilities().setWalkingSpeed(data.savedWalkingSpeed);
                    player.getAbilities().setFlyingSpeed(data.savedFlyingSpeed);
                    player.onUpdateAbilities();
                    player.setSprinting(false);
                    player.setJumping(false);
                }
                StunPlayerData data = stunnedPlayers.get(player.getUUID());

                if (data != null) {
                    player.setYRot(data.savedYaw);
                    player.setXRot(data.savedPitch);
                }
                if (data != null) {
                    player.setYRot(data.savedYaw);
                    player.setXRot(data.savedPitch);
                    player.teleportTo(data.savedX, data.savedY, data.savedZ);
                }

                player.hurtMarked = true;
                AttributeModifierUtil.applyPermanentModifier(
                        pLivingEntity,
                        Attributes.MOVEMENT_SPEED,
                        attributeModifierName,
                        -1,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

            } else if (pLivingEntity instanceof Mob mob) {
                mob.setNoAi(true);
                mob.hurtMarked = true;
                mob.setSprinting(false);
                mob.setJumping(false);
                mob.getNavigation().stop();
            }
        } else if (duration <= 3){
            //System.out.println("Unstunning: " + pLivingEntity);
            if (pLivingEntity instanceof Player player) {
                stunnedPlayers.remove(player.getUUID());
                player.getAbilities().mayBuild = true;
                if (player.isCreative()) {
                    player.getAbilities().invulnerable = true;
                }
                player.hurtMarked = true;
            } else if (pLivingEntity instanceof Mob mob) {
                mob.setNoAi(false);
                mob.hurtMarked = true;
            }


        }



        return super.applyEffectTick(pLivingEntity, pAmplifier);
    }


    @Override
    public boolean shouldApplyEffectTickThisTick(int pDuration, int pAmplifier) {
        return true;
    }

    @Override
    public void removeAttributeModifiers(@NotNull AttributeMap attributeMap) {
        super.removeAttributeModifiers(attributeMap);
        AttributeInstance instance = attributeMap.getInstance(Attributes.MOVEMENT_SPEED);
        if (instance != null) {
            instance.getModifiers().stream()
                    .map(AttributeModifier::id)
                    .filter(id -> id.getPath().equals(attributeModifierName))
                    .forEach(instance::removeModifier);
        }
    }

    @Override
    public ParticleOptions createParticleOptions(MobEffectInstance effect) {
        return ModParticles.STUNNED_PARTICLES.get();
    }

    public static class StunPlayerData {
        public final float savedYaw;
        public final float savedPitch;
        public final float savedWalkingSpeed;
        public final float savedFlyingSpeed;
        public final double savedX;
        public final double savedY;
        public final double savedZ;
        public StunPlayerData(float yaw, float pitch, float walkingSpeed, float flyingSpeed, double x, double y, double z) {
            this.savedYaw = yaw;
            this.savedPitch = pitch;
            this.savedWalkingSpeed = walkingSpeed;
            this.savedFlyingSpeed = flyingSpeed;
            this.savedX = x;
            this.savedY = y;
            this.savedZ = z;
        }
    }



}
