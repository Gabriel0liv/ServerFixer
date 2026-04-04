package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("null")
public class SPSendLootListPacket {
    private final List<ResourceLocation> tableIds;

    public SPSendLootListPacket(List<ResourceLocation> tableIds) {
        this.tableIds = tableIds != null ? new ArrayList<>(tableIds) : new ArrayList<>();
    }

    public SPSendLootListPacket(FriendlyByteBuf buf) {
        this.tableIds = LootStudioPacketCodec.readResourceLocationList(buf);
    }

    public void toBytes(FriendlyByteBuf buf) {
        LootStudioPacketCodec.writeResourceLocationList(buf, this.tableIds);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleLootTableList(this.tableIds)
        ));
        context.setPacketHandled(true);
    }
}
