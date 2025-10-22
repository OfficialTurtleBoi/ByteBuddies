package net.turtleboi.bytebuddies.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

public record TeleportData(int entityId, double blockX, double blockY, double blockZ) implements CustomPacketPayload {
    public static final Type<TeleportData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "teleport_data"));


    public static final StreamCodec<FriendlyByteBuf, TeleportData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, TeleportData::entityId,
                    ByteBufCodecs.DOUBLE, TeleportData::blockX,
                    ByteBufCodecs.DOUBLE, TeleportData::blockY,
                    ByteBufCodecs.DOUBLE, TeleportData::blockZ,
                    TeleportData::new
            );
    @Override
    public Type<? extends CustomPacketPayload> type() {

        return TYPE;
    }

    public static void handleTeleport(final TeleportData data, final IPayloadContext context) {
        context.enqueueWork(() ->{
            Player player = context.player();
            if (player.level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(data.entityId());
                if (entity instanceof ByteBuddyEntity byteBuddyEntity) {
                    if (byteBuddyEntity.isOwnedBy(player)) {
                        ((ByteBuddyEntity) entity).lookAt(player, 15.0f, 15.0f);
                        entity.teleportTo(data.blockX(), data.blockY(), data.blockZ());
                    }
                }
            }
        }).exceptionally(e -> {
            context.disconnect(Component.translatable("bytebuddies.networking.failed", e.getMessage()));
            return null;
        });
    }

    public static void teleportBuddy(LivingEntity livingEntity, double blockX, double blockY, double blockZ){
        TeleportData packet = new TeleportData(livingEntity.getId(), blockX, blockY, blockZ);
        PacketDistributor.sendToServer(packet);
    }
}
