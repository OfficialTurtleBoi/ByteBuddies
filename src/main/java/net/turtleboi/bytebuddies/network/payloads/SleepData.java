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

public record SleepData(int entityId, boolean asleep) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SleepData> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "sleep_data"));

    public static final StreamCodec<FriendlyByteBuf, SleepData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SleepData::entityId,
                    ByteBufCodecs.BOOL, SleepData::asleep,
                    SleepData::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleAsleepData(final SleepData data, final IPayloadContext context) {
        context.enqueueWork(() ->{
            Player player = context.player();
            if (player.level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(data.entityId());
                if (entity instanceof ByteBuddyEntity byteBuddyEntity) {
                    if (byteBuddyEntity.isOwnedBy(player)) {
                        byteBuddyEntity.setSleeping(data.asleep);
                    }
                }
            }
        }).exceptionally(e -> {
            context.disconnect(Component.translatable("bytebuddies.networking.failed", e.getMessage()));
            return null;
        });
    }

    public static void setSleepingStatus(LivingEntity livingEntity, boolean asleep){
        SleepData packet = new SleepData(livingEntity.getId(), asleep);
        PacketDistributor.sendToServer(packet);
    }
}
