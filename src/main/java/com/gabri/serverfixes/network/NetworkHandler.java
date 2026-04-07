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

        INSTANCE.messageBuilder(RequestAdminPanelPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestAdminPanelPacket::new)
                .encoder(RequestAdminPanelPacket::toBytes)
                .consumerMainThread(RequestAdminPanelPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestOpenParticleStudioPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestOpenParticleStudioPacket::new)
                .encoder(RequestOpenParticleStudioPacket::toBytes)
                .consumerMainThread(RequestOpenParticleStudioPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenParticleStudioPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenParticleStudioPacket::new)
                .encoder(OpenParticleStudioPacket::toBytes)
                .consumerMainThread(OpenParticleStudioPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenSoundStudioPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenSoundStudioPacket::new)
                .encoder(OpenSoundStudioPacket::toBytes)
                .consumerMainThread(OpenSoundStudioPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenLootStudioPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenLootStudioPacket::new)
                .encoder(OpenLootStudioPacket::toBytes)
                .consumerMainThread(OpenLootStudioPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenTagStudioPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenTagStudioPacket::new)
                .encoder(OpenTagStudioPacket::toBytes)
                .consumerMainThread(OpenTagStudioPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestLootDataPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestLootDataPacket::new)
                .encoder(RequestLootDataPacket::toBytes)
                .consumerMainThread(RequestLootDataPacket::handle)
                .add();

        INSTANCE.messageBuilder(SPSendLootListPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SPSendLootListPacket::new)
                .encoder(SPSendLootListPacket::toBytes)
                .consumerMainThread(SPSendLootListPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestLootTablePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestLootTablePacket::new)
                .encoder(RequestLootTablePacket::toBytes)
                .consumerMainThread(RequestLootTablePacket::handle)
                .add();

        INSTANCE.messageBuilder(SPSendLootDropsPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SPSendLootDropsPacket::new)
                .encoder(SPSendLootDropsPacket::toBytes)
                .consumerMainThread(SPSendLootDropsPacket::handle)
                .add();

        INSTANCE.messageBuilder(SaveLootTablePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SaveLootTablePacket::new)
                .encoder(SaveLootTablePacket::toBytes)
                .consumerMainThread(SaveLootTablePacket::handle)
                .add();

        INSTANCE.messageBuilder(SPSaveResultPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SPSaveResultPacket::new)
                .encoder(SPSaveResultPacket::toBytes)
                .consumerMainThread(SPSaveResultPacket::handle)
                .add();

        INSTANCE.messageBuilder(ResetLootTablePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(ResetLootTablePacket::new)
                .encoder(ResetLootTablePacket::toBytes)
                .consumerMainThread(ResetLootTablePacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestOpenContextEditorPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestOpenContextEditorPacket::new)
                .encoder(RequestOpenContextEditorPacket::toBytes)
                .consumerMainThread(RequestOpenContextEditorPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenContextNbtEditorPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenContextNbtEditorPacket::new)
                .encoder(OpenContextNbtEditorPacket::toBytes)
                .consumerMainThread(OpenContextNbtEditorPacket::handle)
                .add();

        INSTANCE.messageBuilder(SaveContextNbtPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SaveContextNbtPacket::new)
                .encoder(SaveContextNbtPacket::toBytes)
                .consumerMainThread(SaveContextNbtPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestBlockEditorPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestBlockEditorPacket::new)
                .encoder(RequestBlockEditorPacket::toBytes)
                .consumerMainThread(RequestBlockEditorPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenBlockEditorPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenBlockEditorPacket::new)
                .encoder(OpenBlockEditorPacket::toBytes)
                .consumerMainThread(OpenBlockEditorPacket::handle)
                .add();

        INSTANCE.messageBuilder(SaveBlockEditorPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SaveBlockEditorPacket::new)
                .encoder(SaveBlockEditorPacket::toBytes)
                .consumerMainThread(SaveBlockEditorPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestItemTagsPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestItemTagsPacket::new)
                .encoder(RequestItemTagsPacket::toBytes)
                .consumerMainThread(RequestItemTagsPacket::handle)
                .add();

        INSTANCE.messageBuilder(SPSendItemTagsPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SPSendItemTagsPacket::new)
                .encoder(SPSendItemTagsPacket::toBytes)
                .consumerMainThread(SPSendItemTagsPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestTagsPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestTagsPacket::new)
                .encoder(RequestTagsPacket::toBytes)
                .consumerMainThread(RequestTagsPacket::handle)
                .add();

        INSTANCE.messageBuilder(SPSendTagsPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SPSendTagsPacket::new)
                .encoder(SPSendTagsPacket::toBytes)
                .consumerMainThread(SPSendTagsPacket::handle)
                .add();

        INSTANCE.messageBuilder(SaveTagPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SaveTagPacket::new)
                .encoder(SaveTagPacket::toBytes)
                .consumerMainThread(SaveTagPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestEntityTagsPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestEntityTagsPacket::new)
                .encoder(RequestEntityTagsPacket::toBytes)
                .consumerMainThread(RequestEntityTagsPacket::handle)
                .add();

        INSTANCE.messageBuilder(SPSendEntityTagsPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SPSendEntityTagsPacket::new)
                .encoder(SPSendEntityTagsPacket::toBytes)
                .consumerMainThread(SPSendEntityTagsPacket::handle)
                .add();

        INSTANCE.messageBuilder(SaveEntityTagPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(SaveEntityTagPacket::new)
                .encoder(SaveEntityTagPacket::toBytes)
                .consumerMainThread(SaveEntityTagPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenEntityTagStudioPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenEntityTagStudioPacket::new)
                .encoder(OpenEntityTagStudioPacket::toBytes)
                .consumerMainThread(OpenEntityTagStudioPacket::handle)
                .add();

        INSTANCE.messageBuilder(RequestRecipeListPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestRecipeListPacket::new)
                .encoder(RequestRecipeListPacket::toBytes)
                .consumerMainThread(RequestRecipeListPacket::handle)
                .add();

        INSTANCE.messageBuilder(SPSendRecipeListPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SPSendRecipeListPacket::new)
                .encoder(SPSendRecipeListPacket::toBytes)
                .consumerMainThread(SPSendRecipeListPacket::handle)
                .add();

        INSTANCE.messageBuilder(OpenRecipeStudioPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenRecipeStudioPacket::new)
                .encoder(OpenRecipeStudioPacket::toBytes)
                .consumerMainThread(OpenRecipeStudioPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        INSTANCE.sendToServer(message);
    }
}
