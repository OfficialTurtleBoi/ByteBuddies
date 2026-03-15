package net.turtleboi.bytebuddies.network.packets;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import net.turtleboi.bytebuddies.block.entity.DockingStationBlockEntity;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.Optional;

import java.util.function.Supplier;

public class TeleportDataC2SPacket {
    public static final int TELEPORT_ENERGY_COST = 50;

    private final int entityId;
    private final double blockX;
    private final double blockY;
    private final double blockZ;

    public TeleportDataC2SPacket(int entityId, double blockX, double blockY, double blockZ) {
        this.entityId = entityId;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    public TeleportDataC2SPacket(FriendlyByteBuf buf) {
        this.entityId  = buf.readVarInt();
        this.blockX = buf.readDouble();
        this.blockY = buf.readDouble();
        this.blockZ = buf.readDouble();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeDouble(this.blockX);
        buf.writeDouble(this.blockY);
        buf.writeDouble(this.blockZ);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ServerLevel serverLevel = player.serverLevel();
            Entity entity = serverLevel.getEntity(this.entityId);
            if (entity instanceof ByteBuddyEntity byteBuddyEntity) {
                if (byteBuddyEntity.isOwnedBy(player)) {
                    Optional<BlockPos> dockOpt = byteBuddyEntity.getDock();
                    if (dockOpt.isPresent()) {
                        BlockPos dockPos = dockOpt.get();
                        if (serverLevel.getBlockEntity(dockPos) instanceof DockingStationBlockEntity dockBlock) {
                            if (dockBlock.getEnergyStorage().getEnergyStored() < TELEPORT_ENERGY_COST) return;
                            dockBlock.getEnergyStorage().extractEnergy(TELEPORT_ENERGY_COST, false);
                        }
                    }
                    byteBuddyEntity.lookAt(player, 15.0f, 15.0f);
                    entity.teleportTo(this.blockX, this.blockY, this.blockZ);
                }
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
