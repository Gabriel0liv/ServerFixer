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
    private final String query;
    private final boolean searchMod;
    private final boolean searchEntity;
    private final boolean searchChest;
    private final boolean searchItem;

    public RequestLootDataPacket() {
        this("", true, true, true, true);
    }

    public RequestLootDataPacket(String query, boolean searchMod, boolean searchEntity, boolean searchChest, boolean searchItem) {
        this.query = query != null ? query : "";
        this.searchMod = searchMod;
        this.searchEntity = searchEntity;
        this.searchChest = searchChest;
        this.searchItem = searchItem;
    }

    public RequestLootDataPacket(FriendlyByteBuf buf) {
        this.query = buf.readUtf(120);
        this.searchMod = buf.readBoolean();
        this.searchEntity = buf.readBoolean();
        this.searchChest = buf.readBoolean();
        this.searchItem = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.query, 120);
        buf.writeBoolean(this.searchMod);
        buf.writeBoolean(this.searchEntity);
        buf.writeBoolean(this.searchChest);
        buf.writeBoolean(this.searchItem);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) {
                return;
            }

            List<ResourceLocation> ids = LootStudioLogic.listLootTableIds(sender.server, this.query, this.searchMod, this.searchEntity, this.searchChest, this.searchItem);
            NetworkHandler.sendToPlayer(sender, new SPSendLootListPacket(ids));
        });
        context.setPacketHandled(true);
    }
}
