package net.turtleboi.bytebuddies.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.network.payloads.ReloadData;
import net.turtleboi.bytebuddies.network.payloads.RoleData;
import net.turtleboi.bytebuddies.network.payloads.SleepData;
import net.turtleboi.bytebuddies.network.payloads.TeleportData;

@EventBusSubscriber(modid = ByteBuddies.MOD_ID)
public class ModNetworking {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");

        registrar.playToServer(
                SleepData.TYPE,
                SleepData.STREAM_CODEC,
                SleepData::handleAsleepData
        );

        registrar.playToServer(
                ReloadData.TYPE,
                ReloadData.STREAM_CODEC,
                ReloadData::handleReload
        );

        registrar.playToServer(
                TeleportData.TYPE,
                TeleportData.STREAM_CODEC,
                TeleportData::handleTeleport
        );

        registrar.playToServer(
                RoleData.TYPE,
                RoleData.STREAM_CODEC,
                RoleData::handleRoleData
        );
    }
}
