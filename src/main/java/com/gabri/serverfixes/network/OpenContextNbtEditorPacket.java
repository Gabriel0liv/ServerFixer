package com.gabri.serverfixes.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

@SuppressWarnings("null")
public class OpenContextNbtEditorPacket {
    private final ContextTargetType targetType;
    private final int containerId;
    private final int slotIndex;
    private final int entityId;
    private final BlockPos blockPos;
    private final CompoundTag nbtTag;
    private final String title;

    public OpenContextNbtEditorPacket(ContextTargetType targetType, int containerId, int slotIndex, int entityId, BlockPos blockPos, CompoundTag nbtTag, String title) {
        this.targetType = targetType;
        this.containerId = containerId;
        this.slotIndex = slotIndex;
        this.entityId = entityId;
        this.blockPos = blockPos != null ? blockPos : BlockPos.ZERO;
        this.nbtTag = nbtTag != null ? nbtTag : new CompoundTag();
        this.title = title != null ? title : "Contexto";
    }

    public static OpenContextNbtEditorPacket forContainerSlot(int containerId, int slotIndex, CompoundTag nbtTag, String title) {
        return new OpenContextNbtEditorPacket(ContextTargetType.CONTAINER_SLOT, containerId, slotIndex, -1, BlockPos.ZERO, nbtTag, title);
    }

    public static OpenContextNbtEditorPacket forEntity(int entityId, CompoundTag nbtTag, String title) {
        return new OpenContextNbtEditorPacket(ContextTargetType.ENTITY, -1, -1, entityId, BlockPos.ZERO, nbtTag, title);
    }

    public static OpenContextNbtEditorPacket forBlockEntity(BlockPos blockPos, CompoundTag nbtTag, String title) {
        return new OpenContextNbtEditorPacket(ContextTargetType.BLOCK_ENTITY, -1, -1, -1, blockPos, nbtTag, title);
    }

    public OpenContextNbtEditorPacket(FriendlyByteBuf buf) {
        this.targetType = ContextTargetType.fromId(buf.readInt());
        this.containerId = buf.readInt();
        this.slotIndex = buf.readInt();
        this.entityId = buf.readInt();
        this.blockPos = buf.readBlockPos();
        CompoundTag readTag = buf.readNbt();
        this.nbtTag = readTag != null ? readTag : new CompoundTag();
        this.title = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(this.targetType.getId());
        buf.writeInt(this.containerId);
        buf.writeInt(this.slotIndex);
        buf.writeInt(this.entityId);
        buf.writeBlockPos(this.blockPos);
        buf.writeNbt(this.nbtTag);
        buf.writeUtf(this.title);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleOpenContextEditor(
                this.targetType,
                this.containerId,
                this.slotIndex,
                this.entityId,
                this.blockPos,
                this.nbtTag,
                this.title
            )));
        context.setPacketHandled(true);
    }
}
