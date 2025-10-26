package net.turtleboi.bytebuddies.effects;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.effects.custom.StunnedEffect;
import net.turtleboi.bytebuddies.effects.custom.SuperChargedEffect;

public class ModEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, ByteBuddies.MOD_ID);

    public static final Holder<MobEffect> SUPERCHARGED = MOB_EFFECTS.register("super_charged",
            () -> new SuperChargedEffect(MobEffectCategory.BENEFICIAL, 0x36ebab));
    public static final Holder<MobEffect> STUNNED = MOB_EFFECTS.register("stunned",
            () -> new StunnedEffect(MobEffectCategory.HARMFUL, 13676558));
    public static void register(IEventBus eventBus) {
        MOB_EFFECTS.register(eventBus);
    }
}
