package net.turtleboi.bytebuddies.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

public class GeneratorBlockEntity extends BlockEntity implements IEnergyStorage, MenuProvider {
    private final EnergyStorage energyStorage = new EnergyStorage(48000, 640, 640);

    public GeneratorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.GENERATOR_BE.get(), pos, blockState);
    }

    @Override
    public Component getDisplayName() {
        return null;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return null;
    }

    @Override public int receiveEnergy(int toReceive, boolean simulate) {
        return energyStorage.receiveEnergy(toReceive, simulate);
    }

    @Override public int extractEnergy(int toExtract, boolean simulate) {
        return energyStorage.extractEnergy(toExtract, simulate);
    }

    @Override public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    @Override public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
    }

    @Override public boolean canExtract() {
        return energyStorage.canExtract();
    }

    @Override public boolean canReceive() {
        return energyStorage.canReceive();
    }
}
