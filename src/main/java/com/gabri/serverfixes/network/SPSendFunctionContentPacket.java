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
 * Packet server->client enviando conteúdo de uma função.
 */
public class SPSendFunctionContentPacket {
    private final ResourceLocation functionId;
    private final List<String> content;

    public SPSendFunctionContentPacket(ResourceLocation functionId, List<String> content) {
        this.functionId = functionId;
        this.content = new ArrayList<>();
        if (content != null) this.content.addAll(content);
    }

    public SPSendFunctionContentPacket(FriendlyByteBuf buf) {
        this.functionId = buf.readResourceLocation();
        this.content = buf.readList(b -> b.readUtf(32767));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.functionId);
        buf.writeCollection(this.content, (b, s) -> b.writeUtf(s, 32767));
    }

    public ResourceLocation getFunctionId() { return this.functionId; }
    public List<String> getContent() { return this.content; }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleFunctionContent(this.functionId, this.content)
        ));
        context.setPacketHandled(true);
    }
}
