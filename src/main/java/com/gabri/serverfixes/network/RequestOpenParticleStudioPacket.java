package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestOpenParticleStudioPacket {

    public RequestOpenParticleStudioPacket() {
    }

    public RequestOpenParticleStudioPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) {
                return;
            }
            NetworkHandler.sendToPlayer(sender, new OpenParticleStudioPacket());
        });
        context.setPacketHandled(true);
    }
}
