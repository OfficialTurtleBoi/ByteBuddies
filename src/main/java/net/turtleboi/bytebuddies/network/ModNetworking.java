package net.turtleboi.bytebuddies.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.network.payloads.ReloadData;
import net.turtleboi.bytebuddies.network.payloads.SleepData;

@EventBusSubscriber(modid = ByteBuddies.MOD_ID)
public class ModNetworking {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");

        registrar.playBidirectional(
                SleepData.TYPE,
                SleepData.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        SleepData::handleAsleepData,
                        SleepData::handleAsleepSync
                )
        );

        registrar.playBidirectional(
                ReloadData.TYPE,
                ReloadData.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        ReloadData::handlereloadData,
                        (data, context) -> {

                        }
                )
        );
    }
}
