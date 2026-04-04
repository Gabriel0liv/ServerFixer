package com.gabri.serverfixes.network;

import com.gabri.serverfixes.loot.LootStudioLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("null")
public class RequestLootDataPacket {

    public RequestLootDataPacket() {
    }

    public RequestLootDataPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) {
                return;
            }

            List<ResourceLocation> ids = LootStudioLogic.listLootTableIds(sender.server);
            NetworkHandler.sendToPlayer(sender, new SPSendLootListPacket(ids));
        });
        context.setPacketHandled(true);
    }
}
