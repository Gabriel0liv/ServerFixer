package com.gabri.serverfixes.network;

import com.gabri.serverfixes.client.gui.LootDropDTO;
import com.gabri.serverfixes.loot.LootStudioLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("null")
public class RequestLootTablePacket {
    private final ResourceLocation tableId;

    public RequestLootTablePacket(ResourceLocation tableId) {
        this.tableId = tableId;
    }

    public RequestLootTablePacket(FriendlyByteBuf buf) {
        this.tableId = buf.readResourceLocation();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.tableId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) {
                return;
            }

            List<LootDropDTO> drops = LootStudioLogic.parseLootTable(sender.server, this.tableId);
            LootStudioLogic.getNonGeneratedDrops(sender.server, this.tableId)
                    .thenAccept(cleanDrops -> {
                        sender.server.execute(() ->
                            NetworkHandler.sendToPlayer(sender, new SPSendLootDropsPacket(this.tableId, drops, cleanDrops))
                        );
                    });
        });
        context.setPacketHandled(true);
    }
}
