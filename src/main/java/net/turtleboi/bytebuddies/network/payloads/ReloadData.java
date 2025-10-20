package net.turtleboi.bytebuddies.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.entity.entities.ByteBuddyEntity;

public record ReloadData(int entityId) implements CustomPacketPayload {
    public static final Type<ReloadData> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ByteBuddies.MOD_ID, "reload_data"));


    public static final StreamCodec<FriendlyByteBuf, ReloadData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, ReloadData::entityId,
                    ReloadData::new
            );
    @Override
    public Type<? extends CustomPacketPayload> type() {

        return TYPE;
    }
    public static void handlereloadData(final ReloadData data, final IPayloadContext context) {
        context.enqueueWork(() ->{
            LivingEntity entity = (LivingEntity) context.player().level().getEntity(data.entityId());
            if (entity instanceof ByteBuddyEntity byteBuddyEntity) {
               byteBuddyEntity.reloadBuddy();
            }
        }).exceptionally(e -> {
            context.disconnect(Component.translatable("bytebuddies.networking.failed", e.getMessage()));
            return null;
        });
    }

    public static void setreload(LivingEntity livingEntity){
        ReloadData packet = new ReloadData(livingEntity.getId());
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendreloadSync(LivingEntity livingEntity) {
        int livingEntityId = livingEntity.getId();
        ByteBuddyEntity byteBuddyEntity = (ByteBuddyEntity) livingEntity;

        ReloadData payload = new ReloadData(livingEntityId);
        PacketDistributor.sendToServer(payload);
    }
}
