package com.gabri.serverfixes.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SaveItemEditorPacket {
    private final CompoundTag itemTag;
    private final int count;
    private final int damage;
    private final int containerId;
    private final int slotIndex;

    public SaveItemEditorPacket(CompoundTag itemTag) {
        this(itemTag, -1, -1, -1, -1);
    }

    public SaveItemEditorPacket(CompoundTag itemTag, int count, int damage, int containerId, int slotIndex) {
        this.itemTag = itemTag;
        this.count = count;
        this.damage = damage;
        this.containerId = containerId;
        this.slotIndex = slotIndex;
    }

    public SaveItemEditorPacket(FriendlyByteBuf buf) {
        this.itemTag = buf.readNbt();
        this.count = buf.readInt();
        this.damage = buf.readInt();
        this.containerId = buf.readInt();
        this.slotIndex = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(this.itemTag);
        buf.writeInt(this.count);
        buf.writeInt(this.damage);
        buf.writeInt(this.containerId);
        buf.writeInt(this.slotIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;
        
        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = context.getSender();
            if (sender != null && sender.isCreative() && sender.hasPermissions(2)) {
                boolean updated = false;
                if (this.containerId >= 0 && this.slotIndex >= 0 && sender.containerMenu != null && sender.containerMenu.containerId == this.containerId) {
                    if (this.slotIndex < sender.containerMenu.slots.size()) {
                        net.minecraft.world.inventory.Slot slot = sender.containerMenu.slots.get(this.slotIndex);
                        net.minecraft.world.item.ItemStack stack = slot.getItem();
                        if (!stack.isEmpty()) {
                            applyToStack(stack);
                            slot.setChanged();
                            sender.containerMenu.broadcastChanges();
                            updated = true;
                        }
                    }
                }

                if (!updated) {
                    net.minecraft.world.item.ItemStack stack = sender.getMainHandItem();
                    if (!stack.isEmpty()) {
                        applyToStack(stack);
                        updated = true;
                    }
                }

                if (updated) {
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

    private void applyToStack(net.minecraft.world.item.ItemStack stack) {
        stack.setTag(this.itemTag == null || this.itemTag.isEmpty() ? null : this.itemTag.copy());

        int maxStack = Math.max(1, stack.getMaxStackSize());
        int safeCount = this.count > 0 ? Math.min(this.count, maxStack) : stack.getCount();
        stack.setCount(Math.max(1, safeCount));

        if (stack.isDamageableItem()) {
            int maxDamage = Math.max(0, stack.getMaxDamage());
            int safeDamage = this.damage >= 0 ? Math.min(this.damage, maxDamage) : stack.getDamageValue();
            stack.setDamageValue(Math.max(0, safeDamage));
        }
    }
}
