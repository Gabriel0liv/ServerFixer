package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

/**
 * Packet client->server requisitando a lista de tags de itens conhecidas.
 */
public class RequestItemTagsPacket {

    public RequestItemTagsPacket() {
    }

    public RequestItemTagsPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            var sender = context.getSender();
            if (sender == null) return;

            // Coleta tags do Forge registry + overlay custom
            java.util.List<ResourceLocation> knownTags = new java.util.ArrayList<>();

            // Tags do Forge registry
            var tagManager = net.minecraftforge.registries.ForgeRegistries.ITEMS.tags();
            if (tagManager != null) {
                tagManager.getTagNames().forEach(tagKey -> knownTags.add(tagKey.location()));
            }

            // Tags do overlay (adiciona as que nao estao no Forge registry)
            for (String tagId : com.gabri.serverfixes.loot.TagOverlayManager.getOverlay().keySet()) {
                ResourceLocation rl = ResourceLocation.tryParse(tagId);
                if (rl != null && !knownTags.contains(rl)) {
                    knownTags.add(rl);
                }
            }

            NetworkHandler.sendToPlayer(sender, new SPSendItemTagsPacket(knownTags));
        });
        context.setPacketHandled(true);
    }
}
