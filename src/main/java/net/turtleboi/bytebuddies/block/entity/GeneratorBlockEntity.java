package net.turtleboi.bytebuddies.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import net.turtleboi.bytebuddies.item.custom.BatteryItem;
import net.turtleboi.bytebuddies.util.InventoryUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GeneratorBlockEntity extends BlockEntity implements IEnergyStorage, MenuProvider {
    private int progress = 0;
    private int maxProgress = 80;
    private final EnergyStorage energyStorage = new EnergyStorage(48000, 640, 640);
    private final ItemStackHandler fuelSlot = new ItemStackHandler(1){
        @Override
        public boolean isItemValid(int slot, ItemStack itemStack) {
            if (itemStack.isEmpty()) return false;
            return AbstractFurnaceBlockEntity.isFuel(itemStack);
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

    public void tick(Level level, BlockPos blockPos, BlockState blockState) {
        if (level != null) {
            ItemStack fuelItemStack = getFuelSlot().getStackInSlot(0);
            if (!fuelItemStack.isEmpty() && progress <= 0) {
                this.maxProgress = fuelItemStack.getBurnTime(RecipeType.SMELTING);
                this.progress++;
                setChanged(level, blockPos, blockState);
                fuelItemStack.shrink(1);
            } else if (progress < maxProgress) {
                this.progress++;
                generateEnergy(16);
            } else {
                resetProgress();
            }

            tickCount++;
            if (tickCount % 20 == 0) {
                giveBatteryEnergy();
            }
        }
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    private void resetProgress() {
        this.progress = 0;
        this.maxProgress = 100;
        setChanged();
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

    public ItemStackHandler getFuelSlot() {
        return this.fuelSlot;
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
        nbtData.putInt("progress", this.progress);
        nbtData.putInt("maxProgress", this.maxProgress);
        nbtData.put("BatterySlot", this.batterySlot.serializeNBT(registries));
        nbtData.put("FuelSlot", this.fuelSlot.serializeNBT(registries));
        nbtData.putInt("Energy", this.energyStorage.getEnergyStored());
    }

    @Override
    protected void loadAdditional(CompoundTag nbtData, HolderLookup.Provider registries) {
        super.loadAdditional(nbtData, registries);
        progress = nbtData.getInt("progress");
        maxProgress = nbtData.getInt("maxProgress");
        if (nbtData.contains("BatterySlot")) {
            this.batterySlot.deserializeNBT(registries, nbtData.getCompound("BatterySlot"));
        }
        if (nbtData.contains("FuelSlot")) {
            this.fuelSlot.deserializeNBT(registries, nbtData.getCompound("FuelSlot"));
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
}
