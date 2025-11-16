package net.turtleboi.bytebuddies.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.function.Supplier;

public class ReloadBuddyC2SPacket {
    private final int entityId;
    public ReloadBuddyC2SPacket(int entityId) {
        this.entityId = entityId;
    }

    public ReloadBuddyC2SPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
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
                    byteBuddyEntity.reloadBuddy();
                }
            }
        });

        context.setPacketHandled(true);
        return true;
    }
}
