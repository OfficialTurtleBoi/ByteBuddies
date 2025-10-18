package net.turtleboi.bytebuddies.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.item.custom.BatteryItem;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import net.turtleboi.bytebuddies.util.ModTags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class SolarPanelBlockEntity extends BlockEntity implements IEnergyStorage, MenuProvider {
    private final EnergyStorage energyStorage = new EnergyStorage(48000, 640, 640);
    private final ItemStackHandler batterySlot = new ItemStackHandler(1){
        @Override
        public boolean isItemValid(int slot, ItemStack itemStack) {
            if (itemStack.isEmpty()) return false;
            return InventoryUtil.isBattery(itemStack);
        }

        @Override
        protected int getStackLimit(int slot, @NotNull ItemStack itemStack) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if(!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };
    private int tickCount = 0;

    public SolarPanelBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.SOLAR_PANEL_BE.get(), pos, blockState);
    }

    @Override
    public Component getDisplayName() {
        return null;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return null;
    }

    public void tick(Level level, BlockPos blockPos, BlockState blockState) {
        if (level != null) {
            if (level.dimensionType().hasSkyLight()) {
                BlockPos abovePos = blockPos.above();
                int skyBrightness = level.getBrightness(LightLayer.SKY, abovePos);
                if (skyBrightness > 0 && level.isDay()) {
                    int energyGenerated = 8;
                    if (level.isRainingAt(abovePos)) {
                        energyGenerated = energyGenerated / 2;
                    }
                    generateEnergy(energyGenerated);
                }
            }

            tickCount++;
            if (tickCount % 20 == 0) {
                giveBatteryEnergy();
                pushEnergyToNeighbors();
            }
        }
    }

    public void drops() {
        SimpleContainer inventory = new SimpleContainer(batterySlot.getSlots());
        for(int i = 0; i < batterySlot.getSlots(); i++) {
            inventory.setItem(i, batterySlot.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inventory);
    }

    public ItemStackHandler getBatterySlot() {
        return this.batterySlot;
    }

    public IEnergyStorage getEnergyStorage() {
        return this.energyStorage;
    }

    private void setEnergyUnsafe(int value) {
        try {
            var storedEnergy = EnergyStorage.class.getDeclaredField("energy");
            storedEnergy.setAccessible(true);
            storedEnergy.setInt(this.energyStorage, Mth.clamp(value, 0, this.energyStorage.getMaxEnergyStored()));
        } catch (Exception ignored) {}
    }

    @Override
    protected void saveAdditional(CompoundTag nbtData, HolderLookup.Provider registries) {
        super.saveAdditional(nbtData, registries);
        nbtData.put("BatterySlot", this.batterySlot.serializeNBT(registries));
        nbtData.putInt("Energy", this.energyStorage.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag nbtData, HolderLookup.Provider registries) {
        super.loadAdditional(nbtData, registries);
        if (nbtData.contains("BatterySlot")) {
            this.batterySlot.deserializeNBT(registries, nbtData.getCompound("BatterySlot"));
        }
        setChanged();
        if (nbtData.contains("Energy")) {
            setEnergyUnsafe(nbtData.getInt("Energy"));
        }
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

    public int generateEnergy(int energyGenerated) {
        int energyStored = energyStorage.getEnergyStored();
        int maxEnergy = energyStorage.getMaxEnergyStored();
        if (energyStored < maxEnergy) {
            int potentialEnergy = maxEnergy - energyStored;
            int energyActuallyGenerated = Math.min(potentialEnergy, Math.max(0, energyGenerated));
            int receivedEnergy = energyStorage.receiveEnergy(energyActuallyGenerated, false);
            if (receivedEnergy > 0) {
                setChanged();
            }
            return receivedEnergy;
        }
        return 0;
    }

    public int giveBatteryEnergy() {
        int energyMoved = 0;
        if (level == null || level.isClientSide) return 0;

        IEnergyStorage energySource = getEnergyStorage();
        ItemStack batteryInSlot = getBatterySlot().getStackInSlot(0);

        if (energySource != null && energySource.getEnergyStored() > 0 && !batteryInSlot.isEmpty() && batteryInSlot.getItem() instanceof BatteryItem batteryItem) {
            int missingEnergy  = batteryItem.getCapacity() - batteryItem.getEnergy(batteryInSlot);
            int requestedEnergy  = Math.min(missingEnergy, batteryItem.getIoRate());

            if (missingEnergy > 0 && requestedEnergy > 0) {
                int canExtract = energySource.extractEnergy(requestedEnergy, true);
                if (canExtract > 0) {
                    int acceptedEnergy = batteryItem.receive(batteryInSlot, canExtract, false);
                    if (acceptedEnergy > 0) {
                        int energyDrained = energySource.extractEnergy(acceptedEnergy,false);
                        if (energyDrained > 0) {
                            energyMoved = energyDrained;
                            setChanged();
                        }
                    }
                }
            }
        }

        return energyMoved;
    }

    private void pushEnergyToNeighbors() {
        if (level == null || level.isClientSide) return;
        if (energyStorage.getEnergyStored() <= 0) return;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            if (level.getBlockState(neighborPos).is(ModTags.Blocks.GENERATORS)) continue;
            IEnergyStorage neighbor = level.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, direction.getOpposite());
            if (neighbor == null || !neighbor.canReceive()) continue;

            int canExtract = this.energyStorage.extractEnergy(640, true);
            if (canExtract <= 0) continue;

            int accepted = neighbor.receiveEnergy(canExtract, false);
            if (accepted <= 0) continue;

            this.energyStorage.extractEnergy(accepted, false);
            setChanged();

            if (this.energyStorage.getEnergyStored() <= 0) break;
        }
    }
}
