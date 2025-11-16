package net.turtleboi.bytebuddies.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.effects.custom.SuperChargedEffect;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ByteBuddies.MOD_ID);

    public static final RegistryObject<MobEffect> SUPERCHARGED = MOB_EFFECTS.register("super_charged",
            () -> new SuperChargedEffect(MobEffectCategory.BENEFICIAL, 0x36ebab));

    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
