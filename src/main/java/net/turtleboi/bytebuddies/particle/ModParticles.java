package net.turtleboi.bytebuddies.particle;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.particle.custom.CyberSweepParticle;

import java.util.function.Supplier;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, ByteBuddies.MOD_ID);

    public static final Supplier<SimpleParticleType> CYBER_SWEEP_PARTICLE =
            PARTICLE_TYPES.register("cyber_sweep", () -> new SimpleParticleType(true));

    public static void register(IEventBus eventBus) {
        PARTICLE_TYPES.register(eventBus);
    }
}
