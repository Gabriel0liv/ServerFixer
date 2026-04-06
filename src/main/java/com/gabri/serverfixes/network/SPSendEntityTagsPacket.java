package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet server->client enviando a lista de tags de entidades com suas entidades.
 */
public class SPSendEntityTagsPacket {
    private final List<RequestEntityTagsPacket.EntityTagDTO> tags;

    public SPSendEntityTagsPacket(List<RequestEntityTagsPacket.EntityTagDTO> tags) {
        this.tags = new ArrayList<>();
        if (tags != null) {
            for (RequestEntityTagsPacket.EntityTagDTO dto : tags) {
                if (dto != null) this.tags.add(dto);
            }
        }
    }

    public SPSendEntityTagsPacket(FriendlyByteBuf buf) {
        this.tags = buf.readList(b -> new RequestEntityTagsPacket.EntityTagDTO(b));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeCollection(this.tags, (b, dto) -> dto.toBytes(b));
    }

    public List<RequestEntityTagsPacket.EntityTagDTO> getTags() {
        return this.tags;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleEntityTags(this.tags)
        ));
        context.setPacketHandled(true);
    }
}
