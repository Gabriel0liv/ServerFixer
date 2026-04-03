package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestAdminPanelPacket {

    public RequestAdminPanelPacket() {
    }

    public RequestAdminPanelPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = context.getSender();
            if (sender == null || !sender.isCreative() || !sender.hasPermissions(2)) {
                return;
            }
            NetworkHandler.sendToPlayer(sender, new SyncServerConfigPacket(SyncServerConfigPacket.getCurrentConfig()));
        });
        context.setPacketHandled(true);
    }
}
