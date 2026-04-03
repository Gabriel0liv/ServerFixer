package com.gabri.serverfixes.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SaveItemEditorPacket {
    private final CompoundTag itemTag;

    public SaveItemEditorPacket(CompoundTag itemTag) {
        this.itemTag = itemTag;
    }

    public SaveItemEditorPacket(FriendlyByteBuf buf) {
        this.itemTag = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(this.itemTag);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;
        
        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = context.getSender();
            if (sender != null && sender.isCreative() && sender.hasPermissions(2)) {
                net.minecraft.world.item.ItemStack stack = sender.getMainHandItem();
                if (!stack.isEmpty()) {
                    stack.setTag(this.itemTag);
                    net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal("§aModificações salvas com sucesso no item.");
                    if (msg != null) sender.sendSystemMessage(msg);
                }
            } else if (sender != null) {
                net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal("§cAcesso negado: requer OP e modo Criativo.");
                if (msg != null) sender.sendSystemMessage(msg);
            }
        });
        context.setPacketHandled(true);
    }
}
