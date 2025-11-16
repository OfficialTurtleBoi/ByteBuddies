package net.turtleboi.bytebuddies.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

import java.util.function.Supplier;

public class SleepDataC2SPacket {
    private final int entityId;
    private final boolean asleep;

    public SleepDataC2SPacket(int entityId, boolean asleep) {
        this.entityId = entityId;
        this.asleep = asleep;
    }

    public SleepDataC2SPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        this.asleep = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeBoolean(this.asleep);
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
                    byteBuddyEntity.setSleeping(this.asleep);
                }
            }
        });
        context.setPacketHandled(true);
        return true;
    }
}
