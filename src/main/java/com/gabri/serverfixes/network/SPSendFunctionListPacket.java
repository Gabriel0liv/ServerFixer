package com.gabri.serverfixes.network;

import com.gabri.serverfixes.client.gui.FunctionEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet server->client enviando lista de funções filtradas.
 */
public class SPSendFunctionListPacket {
    private final List<FunctionEntry> functions;

    public SPSendFunctionListPacket(List<FunctionEntry> functions) {
        this.functions = new ArrayList<>();
        if (functions != null) {
            for (FunctionEntry dto : functions) {
                if (dto != null) this.functions.add(dto);
            }
        }
    }

    public SPSendFunctionListPacket(FriendlyByteBuf buf) {
        this.functions = buf.readList(b -> new FunctionEntry(b));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeCollection(this.functions, (b, dto) -> dto.toBytes(b));
    }

    public List<FunctionEntry> getFunctions() {
        return this.functions;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleFunctionList(this.functions)
        ));
        context.setPacketHandled(true);
    }
}
