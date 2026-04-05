package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

@SuppressWarnings("null")
public class SPSaveResultPacket {
    private final boolean success;
    private final ResourceLocation tableId;
    private final String message;

    public SPSaveResultPacket(boolean success, ResourceLocation tableId, String message) {
        this.success = success;
        this.tableId = tableId;
        this.message = message == null ? "" : message;
    }

    public SPSaveResultPacket(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
        this.tableId = buf.readResourceLocation();
        this.message = buf.readUtf(32767);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(this.success);
        buf.writeResourceLocation(this.tableId);
        buf.writeUtf(this.message == null ? "" : this.message);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleSaveResult(this.success, this.tableId, this.message)
        ));
        context.setPacketHandled(true);
    }
}
