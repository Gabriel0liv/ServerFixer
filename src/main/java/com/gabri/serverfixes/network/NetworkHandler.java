package com.gabri.serverfixes.network;

import com.gabri.serverfixes.ServerFixes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

@SuppressWarnings("removal")
public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ServerFixes.MODID + ":main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerPackets() {
        INSTANCE.messageBuilder(OpenItemEditorPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenItemEditorPacket::new)
                .encoder(OpenItemEditorPacket::toBytes)
                .consumerMainThread(OpenItemEditorPacket::handle)
                .add();

        INSTANCE.messageBuilder(SaveItemEditorPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SaveItemEditorPacket::new)
                .encoder(SaveItemEditorPacket::toBytes)
                .consumerMainThread(SaveItemEditorPacket::handle)
                .add();

        INSTANCE.messageBuilder(SyncServerConfigPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncServerConfigPacket::new)
                .encoder(SyncServerConfigPacket::toBytes)
                .consumerMainThread(SyncServerConfigPacket::handle)
                .add();

        INSTANCE.messageBuilder(UpdateServerConfigPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateServerConfigPacket::new)
                .encoder(UpdateServerConfigPacket::toBytes)
                .consumerMainThread(UpdateServerConfigPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        INSTANCE.sendToServer(message);
    }
}
