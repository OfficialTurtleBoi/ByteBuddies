package net.turtleboi.bytebuddies.effects.custom;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.turtleboi.bytebuddies.particle.ModParticles;

public class OverChargedEffect extends MobEffect {
    public OverChargedEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
    @Override
    public ParticleOptions createParticleOptions(MobEffectInstance effect) {
        return ModParticles.OVERCHARGED_PARTICLE.get();
    }
}
