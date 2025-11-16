package net.turtleboi.bytebuddies.effects.custom;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.turtleboi.bytebuddies.particle.ModParticles;
import net.turtleboi.turtlecore.network.CoreNetworking;
import net.turtleboi.turtlecore.network.packet.util.SendParticlesS2C;

public class SuperChargedEffect extends MobEffect {
    public SuperChargedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity pLivingEntity, int pAmplifier) {
        if (!pLivingEntity.level().isClientSide && pLivingEntity instanceof Player player) {
            if (pLivingEntity.tickCount % 20 == 0) {
                for (int i = 0; i < 5; i++) {
                    CoreNetworking.sendToNear(new SendParticlesS2C(
                            ModParticles.SUPERCHARGED_PARTICLE.get(),
                            pLivingEntity.getX() + (pLivingEntity.getRandom().nextDouble() - 0.5) * pLivingEntity.getBbWidth(),
                            pLivingEntity.getY() + pLivingEntity.getRandom().nextDouble() * pLivingEntity.getBbHeight(),
                            pLivingEntity.getZ() + (pLivingEntity.getRandom().nextDouble() - 0.5) * pLivingEntity.getBbWidth(),
                            0, 0, 0), player);
                }
            }
        }
        super.applyEffectTick(pLivingEntity, pAmplifier);
    }
}
