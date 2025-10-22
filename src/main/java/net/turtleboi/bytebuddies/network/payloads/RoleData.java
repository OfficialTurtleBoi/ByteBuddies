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

public record RoleData(int entityId, int roleId) implements CustomPacketPayload {
    public static final Type<RoleData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "role_data"));


    public static final StreamCodec<FriendlyByteBuf, RoleData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, RoleData::entityId,
                    ByteBufCodecs.INT, RoleData::roleId,
                    RoleData::new
            );
    @Override
    public Type<? extends CustomPacketPayload> type() {

        return TYPE;
    }

    public static void handleRoleData(final RoleData data, final IPayloadContext context) {
        context.enqueueWork(() ->{
            Player player = context.player();
            if (player.level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(data.entityId());
                if (entity instanceof ByteBuddyEntity byteBuddyEntity) {
                    if (byteBuddyEntity.isOwnedBy(player)) {
                        byteBuddyEntity.setBuddyRoleById(data.roleId);
                    }
                }
            }
        }).exceptionally(e -> {
            context.disconnect(Component.translatable("bytebuddies.networking.failed", e.getMessage()));
            return null;
        });
    }

    public static void setBuddyRole(LivingEntity livingEntity, int roleOrdinal){
        RoleData packet = new RoleData(livingEntity.getId(), roleOrdinal);
        PacketDistributor.sendToServer(packet);
    }
}
