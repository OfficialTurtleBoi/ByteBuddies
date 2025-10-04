package net.turtleboi.bytebuddies.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DockingStationBlockEntity extends BlockEntity implements IEnergyStorage {
    private final Set<UUID> boundBuddies = new HashSet<>();
    private final EnergyStorage energyStorage = new EnergyStorage(400_000, 800, 800);

    public DockingStationBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.DOCKING_STATION_BE.get(), blockPos, blockState);
    }

    public void addBoundBuddy(ByteBuddyEntity byteBuddy) {
        if (boundBuddies.add(byteBuddy.getUUID())) {
            ByteBuddies.LOGGER.info("[ByteBuddies] dock {} added bot {}", worldPosition, byteBuddy.getUUID());
            setChanged();
        }
    }

    public void removeBoundBuddy(ByteBuddyEntity byteBuddy) {
        if (boundBuddies.remove(byteBuddy.getUUID())) {
            ByteBuddies.LOGGER.info("[ByteBuddies] dock {} removed bot {}", worldPosition, byteBuddy.getUUID());
            setChanged();
        }
    }

    public void onTick() {
        if (level != null) {
            AABB boundingBox = new AABB(worldPosition).inflate(1.5);
            for (ByteBuddyEntity byteBuddy : level.getEntitiesOfClass(ByteBuddyEntity.class, boundingBox)) {
                int energyTransfer = Math.min(200, energyStorage.getEnergyStored());
                if (energyTransfer > 0) {
                    energyStorage.extractEnergy(energyTransfer, false);
                    byteBuddy.getEnergy().receiveEnergy(energyTransfer, false);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbtData, HolderLookup.Provider registries) {
        super.saveAdditional(nbtData, registries);
        var dataList = new ListTag();
        for (UUID id : boundBuddies) dataList.add(StringTag.valueOf(id.toString()));
        nbtData.put("BoundBots", dataList);
    }

    @Override
    protected void loadAdditional(CompoundTag nbtData, HolderLookup.Provider registries) {
        super.loadAdditional(nbtData, registries);
        boundBuddies.clear();
        var dataList = nbtData.getList("BoundBots", Tag.TAG_STRING);
        for (int i = 0; i < dataList.size(); i++) {
            boundBuddies.add(UUID.fromString(dataList.getString(i)));
        }
    }

    @Override
    public int receiveEnergy(int toReceive, boolean transferable) {
        return 0;
    }

    @Override
    public int extractEnergy(int toExtract, boolean transferable) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return 0;
    }

    @Override
    public int getMaxEnergyStored() {
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return false;
    }
}
