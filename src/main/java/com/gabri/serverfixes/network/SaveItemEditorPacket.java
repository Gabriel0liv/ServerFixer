package com.gabri.serverfixes.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SaveItemEditorPacket {
    private final CompoundTag fullItemNbt;
    private final int containerId;
    private final int slotIndex;

    public SaveItemEditorPacket(CompoundTag itemTag) {
        this.fullItemNbt = new CompoundTag();
        this.containerId = -1;
        this.slotIndex = -1;
    }

    public SaveItemEditorPacket(ItemStack stack, int containerId, int slotIndex) {
        this.fullItemNbt = serializeStack(stack);
        this.containerId = containerId;
        this.slotIndex = slotIndex;
    }

    public SaveItemEditorPacket(FriendlyByteBuf buf) {
        this.fullItemNbt = buf.readNbt();
        this.containerId = buf.readInt();
        this.slotIndex = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(this.fullItemNbt);
        buf.writeInt(this.containerId);
        buf.writeInt(this.slotIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;
        
        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = context.getSender();
            if (sender != null && sender.isCreative() && sender.hasPermissions(2)) {
                ItemStack newStack = deserializeStack(this.fullItemNbt);
                if (newStack.isEmpty()) {
                    net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal("§cFalha ao salvar: item inválido no pacote.");
                    if (msg != null) sender.sendSystemMessage(msg);
                    return;
                }

                boolean hasExplicitSlotTarget = this.containerId >= 0 && this.slotIndex >= 0;
                boolean updated = false;
                if (hasExplicitSlotTarget) {
                    if (sender.containerMenu != null && sender.containerMenu.containerId == this.containerId && this.slotIndex < sender.containerMenu.slots.size()) {
                        net.minecraft.world.inventory.Slot slot = sender.containerMenu.slots.get(this.slotIndex);
                        ItemStack slotStack = sanitizeStackForSlot(newStack, slot);
                        slot.set(slotStack);
                        slot.setChanged();
                        sender.containerMenu.broadcastChanges();
                        sender.inventoryMenu.broadcastChanges();
                        updated = true;
                    }
                } else {
                    ItemStack handStack = newStack.copy();
                    clampStackValues(handStack, Math.max(1, handStack.getMaxStackSize()));
                    sender.setItemInHand(InteractionHand.MAIN_HAND, handStack);
                    sender.containerMenu.broadcastChanges();
                    sender.inventoryMenu.broadcastChanges();
                    updated = true;
                }

                if (updated) {
                    net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal("§aModificações salvas com sucesso no item.");
                    if (msg != null) sender.sendSystemMessage(msg);
                } else if (hasExplicitSlotTarget) {
                    net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal("§cFalha ao salvar: alvo do inventário não está mais disponível.");
                    if (msg != null) sender.sendSystemMessage(msg);
                }
            } else if (sender != null) {
                net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal("§cAcesso negado: requer OP e modo Criativo.");
                if (msg != null) sender.sendSystemMessage(msg);
            }
        });
        context.setPacketHandled(true);
    }

    private static CompoundTag serializeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag out = new CompoundTag();
        stack.copy().save(out);
        return out;
    }

    private static ItemStack deserializeStack(CompoundTag fullItemNbt) {
        if (fullItemNbt == null || fullItemNbt.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = ItemStack.of(fullItemNbt.copy());
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        clampStackValues(stack, Math.max(1, stack.getMaxStackSize()));
        return stack;
    }

    private static ItemStack sanitizeStackForSlot(ItemStack source, net.minecraft.world.inventory.Slot slot) {
        ItemStack stack = source.copy();
        int slotMax = Math.max(1, slot.getMaxStackSize(stack));
        clampStackValues(stack, slotMax);
        return stack;
    }

    private static void clampStackValues(ItemStack stack, int maxCount) {
        int clampedCount = Mth.clamp(stack.getCount(), 1, Math.max(1, maxCount));
        stack.setCount(clampedCount);

        if (stack.isDamageableItem()) {
            int maxDamage = Math.max(0, stack.getMaxDamage());
            stack.setDamageValue(Mth.clamp(stack.getDamageValue(), 0, maxDamage));
        }
    }
}
