package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet server->client enviando a lista de tags de itens com seus itens.
 */
public class SPSendTagsPacket {
    private final List<RequestTagsPacket.TagDTO> tags;

    public SPSendTagsPacket(List<RequestTagsPacket.TagDTO> tags) {
        this.tags = new ArrayList<>();
        if (tags != null) {
            for (RequestTagsPacket.TagDTO dto : tags) {
                if (dto != null) this.tags.add(dto);
            }
        }
    }

    public SPSendTagsPacket(FriendlyByteBuf buf) {
        this.tags = buf.readList(b -> new RequestTagsPacket.TagDTO(b));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeCollection(this.tags, (b, dto) -> dto.toBytes(b));
    }

    public List<RequestTagsPacket.TagDTO> getTags() {
        return this.tags;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleTags(this.tags)
        ));
        context.setPacketHandled(true);
    }
}
