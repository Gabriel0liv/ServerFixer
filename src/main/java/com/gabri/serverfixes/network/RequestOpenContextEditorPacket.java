package com.gabri.serverfixes.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestOpenContextEditorPacket {
    private final ContextTargetType targetType;
    private final int containerId;
    private final int slotIndex;
    private final int entityId;
    private final BlockPos blockPos;

    public RequestOpenContextEditorPacket(ContextTargetType targetType, int containerId, int slotIndex, int entityId, BlockPos blockPos) {
        this.targetType = targetType;
        this.containerId = containerId;
        this.slotIndex = slotIndex;
        this.entityId = entityId;
        this.blockPos = blockPos != null ? blockPos : BlockPos.ZERO;
    }

    public RequestOpenContextEditorPacket(FriendlyByteBuf buf) {
        this.targetType = ContextTargetType.fromId(buf.readInt());
        this.containerId = buf.readInt();
        this.slotIndex = buf.readInt();
        this.entityId = buf.readInt();
        this.blockPos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.targetType.getId());
        buf.writeInt(this.containerId);
        buf.writeInt(this.slotIndex);
        buf.writeInt(this.entityId);
        buf.writeBlockPos(this.blockPos);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = context.getSender();
            if (sender == null || !sender.isCreative() || !sender.hasPermissions(2)) {
                return;
            }

            switch (this.targetType) {
                case CONTAINER_SLOT -> openContainerSlot(sender);
                case ENTITY -> openEntity(sender);
                case BLOCK_ENTITY -> openBlockEntity(sender);
            }
        });
        context.setPacketHandled(true);
    }

    private void openContainerSlot(net.minecraft.server.level.ServerPlayer sender) {
        if (sender.containerMenu == null || sender.containerMenu.containerId != this.containerId) {
            return;
        }
        if (this.slotIndex < 0 || this.slotIndex >= sender.containerMenu.slots.size()) {
            return;
        }

        Slot slot = sender.containerMenu.slots.get(this.slotIndex);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            return;
        }

        CompoundTag tag = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
        String title = stack.getHoverName().getString();
        NetworkHandler.sendToPlayer(sender, OpenContextNbtEditorPacket.forContainerSlot(this.containerId, this.slotIndex, tag, title));
    }

    private void openEntity(net.minecraft.server.level.ServerPlayer sender) {
        Entity entity = sender.level().getEntity(this.entityId);
        if (entity == null || entity.distanceToSqr(sender) > 144.0D) {
            return;
        }

        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        String title = entity.getDisplayName().getString();
        NetworkHandler.sendToPlayer(sender, OpenContextNbtEditorPacket.forEntity(entity.getId(), tag, title));
    }

    private void openBlockEntity(net.minecraft.server.level.ServerPlayer sender) {
        if (sender.distanceToSqr(this.blockPos.getX() + 0.5D, this.blockPos.getY() + 0.5D, this.blockPos.getZ() + 0.5D) > 144.0D) {
            return;
        }

        BlockEntity blockEntity = sender.level().getBlockEntity(this.blockPos);
        if (blockEntity == null) {
            return;
        }

        CompoundTag tag = blockEntity.saveWithoutMetadata();
        String title = blockEntity.getBlockState().getBlock().getName().getString();
        NetworkHandler.sendToPlayer(sender, OpenContextNbtEditorPacket.forBlockEntity(this.blockPos, tag, title));
    }
}
