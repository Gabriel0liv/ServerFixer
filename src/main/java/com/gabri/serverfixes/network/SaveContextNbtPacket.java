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

@SuppressWarnings("null")
public class SaveContextNbtPacket {
    private final ContextTargetType targetType;
    private final int containerId;
    private final int slotIndex;
    private final int entityId;
    private final BlockPos blockPos;
    private final CompoundTag nbtTag;

    public SaveContextNbtPacket(ContextTargetType targetType, int containerId, int slotIndex, int entityId, BlockPos blockPos, CompoundTag nbtTag) {
        this.targetType = targetType;
        this.containerId = containerId;
        this.slotIndex = slotIndex;
        this.entityId = entityId;
        this.blockPos = blockPos != null ? blockPos : BlockPos.ZERO;
        this.nbtTag = nbtTag != null ? nbtTag : new CompoundTag();
    }

    public SaveContextNbtPacket(FriendlyByteBuf buf) {
        this.targetType = ContextTargetType.fromId(buf.readInt());
        this.containerId = buf.readInt();
        this.slotIndex = buf.readInt();
        this.entityId = buf.readInt();
        this.blockPos = buf.readBlockPos();
        CompoundTag readTag = buf.readNbt();
        this.nbtTag = readTag != null ? readTag : new CompoundTag();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.targetType.getId());
        buf.writeInt(this.containerId);
        buf.writeInt(this.slotIndex);
        buf.writeInt(this.entityId);
        buf.writeBlockPos(this.blockPos);
        buf.writeNbt(this.nbtTag);
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
                case CONTAINER_SLOT -> saveContainerSlot(sender);
                case ENTITY -> saveEntity(sender);
                case BLOCK_ENTITY -> saveBlockEntity(sender);
            }
        });
        context.setPacketHandled(true);
    }

    private void saveContainerSlot(net.minecraft.server.level.ServerPlayer sender) {
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

        stack.setTag(this.nbtTag.isEmpty() ? null : this.nbtTag.copy());
        slot.setChanged();
        sender.containerMenu.broadcastChanges();
    }

    private void saveEntity(net.minecraft.server.level.ServerPlayer sender) {
        Entity entity = sender.level().getEntity(this.entityId);
        if (entity == null || entity.distanceToSqr(sender) > 144.0D) {
            return;
        }

        CompoundTag merged = entity.serializeNBT();
        merged.merge(this.nbtTag);
        entity.deserializeNBT(merged);
    }

    private void saveBlockEntity(net.minecraft.server.level.ServerPlayer sender) {
        if (sender.distanceToSqr(this.blockPos.getX() + 0.5D, this.blockPos.getY() + 0.5D, this.blockPos.getZ() + 0.5D) > 144.0D) {
            return;
        }

        BlockEntity blockEntity = sender.level().getBlockEntity(this.blockPos);
        if (blockEntity == null) {
            return;
        }

        CompoundTag merged = blockEntity.serializeNBT();
        merged.merge(this.nbtTag);
        blockEntity.deserializeNBT(merged);
        blockEntity.setChanged();

        net.minecraft.world.level.block.state.BlockState state = sender.level().getBlockState(this.blockPos);
        sender.level().sendBlockUpdated(this.blockPos, state, state, 3);
    }
}
