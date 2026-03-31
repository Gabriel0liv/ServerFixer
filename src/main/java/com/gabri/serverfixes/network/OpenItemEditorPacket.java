package com.gabri.serverfixes.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenItemEditorPacket {
    private final CompoundTag itemTag;

    public OpenItemEditorPacket(CompoundTag itemTag) {
        this.itemTag = itemTag;
    }

    public OpenItemEditorPacket(FriendlyByteBuf buf) {
        this.itemTag = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(this.itemTag);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;
        
        context.enqueueWork(() -> {
            // Use DistExecutor to prevent ClassNotFoundException on dedicated servers
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.gabri.serverfixes.client.ClientPacketHandler.handleOpenEditor(this.itemTag));
        });
        context.setPacketHandled(true);
    }
}
