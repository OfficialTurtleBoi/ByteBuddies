package net.turtleboi.bytebuddies.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;

import java.util.function.Supplier;

public class ModBlockEntities{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ByteBuddies.MOD_ID);

    public static final Supplier<BlockEntityType<DockingStationBlockEntity>> DOCKING_STATION_BE =
            BLOCK_ENTITIES.register("docking_station_be", () -> BlockEntityType.Builder.of(
                    DockingStationBlockEntity::new, ModBlocks.DOCKING_STATION.get()).build(null));

    public static final Supplier<BlockEntityType<DockingStationBlockEntity>> GENERATOR_BE =
            BLOCK_ENTITIES.register("generator_be", () -> BlockEntityType.Builder.of(
                    DockingStationBlockEntity::new, ModBlocks.GENERATOR.get()).build(null));

    public static final Supplier<BlockEntityType<DockingStationBlockEntity>> SOLAR_PANEL_BE =
            BLOCK_ENTITIES.register("solar_panel_be", () -> BlockEntityType.Builder.of(
                    DockingStationBlockEntity::new, ModBlocks.SOLAR_PANEL.get()).build(null));

    public static void register(IEventBus eventBus){
        BLOCK_ENTITIES.register(eventBus);
    }
}
