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
public class SaveLootTablePacket {
    private final ResourceLocation tableId;
    private final List<LootDropDTO> drops;

    public SaveLootTablePacket(ResourceLocation tableId, List<LootDropDTO> drops) {
        this.tableId = tableId;
        this.drops = drops;
    }

    public SaveLootTablePacket(FriendlyByteBuf buf) {
        this.tableId = buf.readResourceLocation();
        this.drops = LootStudioPacketCodec.readLootDropList(buf);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.tableId);
        LootStudioPacketCodec.writeLootDropList(buf, this.drops);
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
            LootStudioLogic.saveToDatapack(server, this.tableId, this.drops)
                .thenAccept(success -> server.execute(() -> {
                    // always notify client via SPSaveResultPacket so UI can react
                    NetworkHandler.sendToPlayer(sender, new SPSaveResultPacket(success, this.tableId, success ? "Loot table salva: " + this.tableId : "Falha ao salvar loot table: " + this.tableId));

                    if (!success) {
                        sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cFalha ao salvar loot table: " + this.tableId));
                        return;
                    }

                    List<LootDropDTO> refreshed = LootStudioLogic.parseLootTable(server, this.tableId);
                    NetworkHandler.sendToPlayer(sender, new SPSendLootDropsPacket(this.tableId, refreshed));
                    sender.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aLoot table salva: " + this.tableId));
                }));
        });
        context.setPacketHandled(true);
    }
}
