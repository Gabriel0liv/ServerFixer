package com.gabri.serverfixes.network;

import com.gabri.serverfixes.client.gui.FunctionEntry;
import com.gabri.serverfixes.function.FunctionStudioLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Packet client->server requisitando lista de funções com filtros.
 */
public class RequestFunctionListPacket {
    private final String namespaceFilter;
    private final String pathFilter;

    public RequestFunctionListPacket(String namespaceFilter, String pathFilter) {
        this.namespaceFilter = namespaceFilter;
        this.pathFilter = pathFilter;
    }

    public RequestFunctionListPacket(FriendlyByteBuf buf) {
        this.namespaceFilter = buf.readUtf(32767);
        this.pathFilter = buf.readUtf(32767);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.namespaceFilter, 32767);
        buf.writeUtf(this.pathFilter, 32767);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) return;

            var server = sender.getServer();
            if (server == null) return;

            List<FunctionEntry> functions = FunctionStudioLogic.getFilteredFunctions(
                server, this.namespaceFilter, this.pathFilter
            );

            NetworkHandler.sendToPlayer(sender, new SPSendFunctionListPacket(functions));
        });
        context.setPacketHandled(true);
    }
}
