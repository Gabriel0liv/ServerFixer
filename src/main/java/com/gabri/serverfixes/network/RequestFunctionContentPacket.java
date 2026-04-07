package com.gabri.serverfixes.network;

import com.gabri.serverfixes.function.FunctionStudioLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Packet client->server requisitando conteúdo de uma função específica.
 */
public class RequestFunctionContentPacket {
    private final ResourceLocation functionId;

    public RequestFunctionContentPacket(ResourceLocation functionId) {
        this.functionId = functionId;
    }

    public RequestFunctionContentPacket(FriendlyByteBuf buf) {
        this.functionId = buf.readResourceLocation();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.functionId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) return;

            var server = sender.getServer();
            if (server == null) return;

            List<String> content = FunctionStudioLogic.readFunctionContent(server, this.functionId);
            NetworkHandler.sendToPlayer(sender, new SPSendFunctionContentPacket(this.functionId, content));
        });
        context.setPacketHandled(true);
    }
}
