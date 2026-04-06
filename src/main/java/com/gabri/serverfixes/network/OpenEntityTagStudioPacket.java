package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

@SuppressWarnings("null")
public class OpenEntityTagStudioPacket {

    public OpenEntityTagStudioPacket() {
    }

    public OpenEntityTagStudioPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleOpenEntityTagStudio()
        ));
        context.setPacketHandled(true);
    }
}
