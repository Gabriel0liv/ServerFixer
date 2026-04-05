package com.gabri.serverfixes.network;

import com.gabri.serverfixes.client.gui.LootDropDTO;
import com.gabri.serverfixes.loot.LootStudioLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("null")
public class ResetLootTablePacket {
    private final ResourceLocation tableId;

    public ResetLootTablePacket(ResourceLocation tableId) {
        this.tableId = tableId;
    }

    public ResetLootTablePacket(FriendlyByteBuf buf) {
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
            if (sender == null || !sender.hasPermissions(2) || this.tableId == null) {
                return;
            }

            MinecraftServer server = sender.server;
            LootStudioLogic.resetGeneratedLootTable(server, this.tableId)
                .thenAccept(success -> server.execute(() -> {
                    if (!success) {
                        sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cFalha ao resetar loot table: " + this.tableId));
                        return;
                    }
                    // Do NOT send drops back automatically — client must request reload explicitly
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aLoot table resetada: " + this.tableId + " — Clique em salvar para aplicar."));
                }));
        });
        context.setPacketHandled(true);
    }
}
