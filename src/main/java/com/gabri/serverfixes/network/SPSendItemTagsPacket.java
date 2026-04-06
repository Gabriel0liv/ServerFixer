package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet server->client enviando a lista de tags de itens conhecidas pelo servidor.
 */
public class SPSendItemTagsPacket {
    private final List<ResourceLocation> tagIds;

    public SPSendItemTagsPacket(List<ResourceLocation> tagIds) {
        this.tagIds = new ArrayList<>();
        if (tagIds != null) {
            for (ResourceLocation rl : tagIds) {
                if (rl != null) {
                    this.tagIds.add(rl);
                }
            }
        }
    }

    public SPSendItemTagsPacket(FriendlyByteBuf buf) {
        this.tagIds = LootStudioPacketCodec.readResourceLocationList(buf);
    }

    public void toBytes(FriendlyByteBuf buf) {
        LootStudioPacketCodec.writeResourceLocationList(buf, this.tagIds);
    }

    public List<ResourceLocation> getTagIds() {
        return this.tagIds;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleItemTags(this.tagIds)
        ));
        context.setPacketHandled(true);
    }
}
