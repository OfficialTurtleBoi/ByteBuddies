package net.turtleboi.bytebuddies.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.turtleboi.bytebuddies.ByteBuddies;
import net.turtleboi.bytebuddies.network.packets.*;

public class ModNetworking {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }
    public static void register () {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(ByteBuddies.MOD_ID, "networking"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(ReloadBuddyC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ReloadBuddyC2SPacket::new)
                .encoder(ReloadBuddyC2SPacket::toBytes)
                .consumerMainThread(ReloadBuddyC2SPacket::handle)
                .add();

        net.messageBuilder(RoleDataC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RoleDataC2SPacket::new)
                .encoder(RoleDataC2SPacket::toBytes)
                .consumerMainThread(RoleDataC2SPacket::handle)
                .add();

        net.messageBuilder(SleepDataC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SleepDataC2SPacket::new)
                .encoder(SleepDataC2SPacket::toBytes)
                .consumerMainThread(SleepDataC2SPacket::handle)
                .add();

        net.messageBuilder(TeleportDataC2SPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(TeleportDataC2SPacket::new)
                .encoder(TeleportDataC2SPacket::toBytes)
                .consumerMainThread(TeleportDataC2SPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer (MSG message) {
        INSTANCE.sendToServer(message);
    }
}
