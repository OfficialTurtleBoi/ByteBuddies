package net.turtleboi.bytebuddies.entity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.HologramBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.SwordSweepEntity;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ByteBuddies.MOD_ID);

    public static final Supplier<EntityType<ByteBuddyEntity>> BYTEBUDDY =
            ENTITY_TYPES.register("bytebuddy", () -> EntityType.Builder.of(ByteBuddyEntity::new, MobCategory.CREATURE)
                    .sized(0.5f, 0.5f).build("bytebuddy"));

    public static final Supplier<EntityType<HologramBuddyEntity>> HOLOBUDDY =
            ENTITY_TYPES.register("holobuddy", () -> EntityType.Builder.of(HologramBuddyEntity::new, MobCategory.CREATURE)
                    .sized(0.5f, 0.5f).build("holobuddy"));

    public static final Supplier<EntityType<SwordSweepEntity>> SWORD_SWEEP =
            ENTITY_TYPES.register("sword_sweep", () -> EntityType.Builder.<SwordSweepEntity>of(SwordSweepEntity::new, MobCategory.MISC)
                    .sized(0.625f, 2f).build("sword_sweep"));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
