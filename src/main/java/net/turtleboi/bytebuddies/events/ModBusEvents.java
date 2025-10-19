package net.turtleboi.bytebuddies.events;

import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.block.entity.GeneratorBlockEntity;
import net.turtleboi.bytebuddies.block.entity.SolarPanelBlockEntity;
import net.turtleboi.bytebuddies.entity.ModEntities;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import javax.annotation.Nullable;

@EventBusSubscriber(modid = ByteBuddies.MOD_ID)
public class ModBusEvents {
    @SubscribeEvent
    public static void register(final RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.DOCKING_STATION_BE.get(),
                (DockingStationBlockEntity blockEntity, @Nullable Direction side) -> blockEntity.getMainInv()
        );

        event.registerEntity(
                Capabilities.ItemHandler.ENTITY,
                ModEntities.BYTEBUDDY.get(),
                (byteBuddy, side) -> byteBuddy.getMainInv()
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.DOCKING_STATION_BE.get(),
                (DockingStationBlockEntity blockEntity, @Nullable Direction side) -> blockEntity.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.GENERATOR_BE.get(),
                (GeneratorBlockEntity blockEntity, @Nullable Direction side) -> blockEntity.getEnergyStorage()
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.SOLAR_PANEL_BE.get(),
                (SolarPanelBlockEntity blockEntity, @Nullable Direction side) -> blockEntity.getEnergyStorage()
        );
    }
}
