package net.turtleboi.bytebuddies.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.block.ModBlockEntities;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DockingStationBlockEntity extends BlockEntity implements IEnergyStorage, MenuProvider {
    public final ItemStackHandler inventory = new ItemStackHandler(3) {
        @Override
        protected int getStackLimit(int slot, ItemStack itemStack) {
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

    public void tick(Level level, BlockPos blockPos, BlockState blockState) {
        if (level != null) {
            if (level instanceof ServerLevel serverLevel) {
                pruneReservations(serverLevel);
            }

            AABB boundingBox = new AABB(blockPos).inflate(8);
            for (ByteBuddyEntity byteBuddy : level.getEntitiesOfClass(ByteBuddyEntity.class, boundingBox)) {
                //int energyTransfer = Math.min(200, energyStorage.getEnergyStored());
                //if (energyTransfer > 0) {
                    //energyStorage.extractEnergy(energyTransfer, false);
                    byteBuddy.getEnergyStorage().receiveEnergy(25, false);
                //}
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
    public Component getDisplayName() {
        return Component.translatable("block.bytebuddies.docking_station");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return null;
    }

    public void drops() {
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    private record TaskKey(TaskType taskType, BlockPos blockPos) {}
    private static final class Reservation {
        final UUID buddyId;
        long expiresAt;
        Reservation(UUID buddyId, long expiresAt) {
            this.buddyId = buddyId;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<TaskKey, Reservation> reservations = new HashMap<>();
    private static long currentTime(ServerLevel serverLevel) {
        return serverLevel.getGameTime(); }

    private static long reservationTime(long time, int ticks) {
        return time + ticks;
    }

    public void pruneReservations(ServerLevel serverLevel) {
        long time = currentTime(serverLevel);
        reservations.entrySet().removeIf(entry -> entry.getValue().expiresAt <= time);
    }

    public boolean isReserved(ServerLevel serverLevel, TaskType taskType, BlockPos blockPos) {
        pruneReservations(serverLevel);
        return reservations.containsKey(new TaskKey(taskType, blockPos));
    }

    public boolean isReservedBy(ServerLevel serverLevel, TaskType task, BlockPos blockPos, UUID botId) {
        pruneReservations(serverLevel);
        var reservation = reservations.get(new TaskKey(task, blockPos));
        return reservation != null && reservation.buddyId.equals(botId);
    }

    public boolean tryClaim(ServerLevel serverLevel, TaskType taskType, BlockPos blockPos, UUID buddyId, int reservationTicks) {
        pruneReservations(serverLevel);
        TaskKey taskKey = new TaskKey(taskType, blockPos);
        Reservation reservation = reservations.get(taskKey);
        if (reservation != null && !reservation.buddyId.equals(buddyId)) return false;
        reservations.put(taskKey, new Reservation(buddyId, reservationTime(currentTime(serverLevel), reservationTicks)));
        return true;
    }

    public void renewClaim(ServerLevel serverLevel, TaskType taskType, BlockPos blockPos, UUID botId, int reservationTicks) {
        TaskKey taskKey = new TaskKey(taskType, blockPos);
        Reservation reservation = reservations.get(taskKey);
        if (reservation != null && reservation.buddyId.equals(botId)) {
            reservation.expiresAt = reservationTime(currentTime(serverLevel), reservationTicks);
        }
    }

    public void releaseClaim(TaskType taskType, BlockPos blockPos, UUID buddyId) {
        TaskKey taskKey = new TaskKey(taskType, blockPos);
        Reservation reservation = reservations.get(taskKey);
        if (reservation != null && reservation.buddyId.equals(buddyId)) reservations.remove(taskKey);
    }

    public void releaseAllFor(UUID buddyId) {
        reservations.entrySet().removeIf(e -> e.getValue().buddyId.equals(buddyId));
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithFullMetadata(registries);
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
