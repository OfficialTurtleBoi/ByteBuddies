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
            LivingEntity entity = (LivingEntity) context.player().level().getEntity(data.entityId());
            if (entity != null && entity instanceof ByteBuddyEntity byteBuddyEntity) {
                byteBuddyEntity.setSleeping(data.asleep);
            }
        }).exceptionally(e -> {
            context.disconnect(Component.translatable("bytebuddies.networking.failed", e.getMessage()));
            return null;
        });
    }
    public static void handleAsleepSync(final SleepData data, final IPayloadContext context) {
        context.enqueueWork(() -> {
            LivingEntity entity = (LivingEntity) context.player().level().getEntity(data.entityId());
            if (entity != null && entity instanceof ByteBuddyEntity byteBuddyEntity) {
                boolean serverAsleep = byteBuddyEntity.isSleeping();
                boolean clientAsleep = data.asleep();
                if (serverAsleep != clientAsleep) {


                    context.reply(new SleepData(data.entityId(), false));
                }
            }
        }).exceptionally(e -> {
            context.disconnect(Component.translatable("bytebuddies.networking.failed", e.getMessage()));
            return null;
        });
    }

    public static void setAsleep(LivingEntity livingEntity, boolean asleep){
        SleepData packet = new SleepData(livingEntity.getId(), asleep);
        PacketDistributor.sendToAllPlayers(packet);
    }

    public static void sendAsleepSync(LivingEntity livingEntity) {
        int livingEntityId = livingEntity.getId();
        ByteBuddyEntity byteBuddyEntity = (ByteBuddyEntity) livingEntity;

        boolean isAsleep = byteBuddyEntity.isSleeping();
        SleepData payload = new SleepData(livingEntityId, isAsleep);
        PacketDistributor.sendToServer(payload);
    }
}
