package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet client->server requisitando a lista de todas as tags de itens.
 */
public class RequestTagsPacket {

    public RequestTagsPacket() {
    }

    public RequestTagsPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            var sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) return;

            // Usa o overlay manager que combina Forge registry + tags custom
            var tags = com.gabri.serverfixes.loot.TagOverlayManager.getAllTags();
            NetworkHandler.sendToPlayer(sender, new SPSendTagsPacket(tags));
        });
        context.setPacketHandled(true);
    }

    public record TagDTO(String tagId, java.util.List<String> itemIds) {
        public TagDTO(FriendlyByteBuf buf) {
            this(buf.readUtf(32767), buf.readList(b -> b.readUtf(32767)));
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(this.tagId, 32767);
            buf.writeCollection(this.itemIds, (b, s) -> b.writeUtf(s, 32767));
        }
    }
}
