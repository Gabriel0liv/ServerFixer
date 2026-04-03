package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

@SuppressWarnings("null")
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
            if (sender == null) {
                return;
            }
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cAcesso negado: requer OP."));
                return;
            }
            NetworkHandler.sendToPlayer(sender, new OpenParticleStudioPacket());
            sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aAbrindo Particle Studio..."));
        });
        context.setPacketHandled(true);
    }
}
