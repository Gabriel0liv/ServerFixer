package com.gabri.serverfixes.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenBlockEditorPacket {
    private final BlockPos blockPos;
    private final String blockId;
    private final CompoundTag stateValues;
    private final CompoundTag allowedValues;
    private final CompoundTag blockEntityTag;

    public OpenBlockEditorPacket(BlockPos blockPos, String blockId, CompoundTag stateValues, CompoundTag allowedValues, CompoundTag blockEntityTag) {
        this.blockPos = blockPos != null ? blockPos : BlockPos.ZERO;
        this.blockId = blockId != null ? blockId : "minecraft:air";
        this.stateValues = stateValues != null ? stateValues : new CompoundTag();
        this.allowedValues = allowedValues != null ? allowedValues : new CompoundTag();
        this.blockEntityTag = blockEntityTag != null ? blockEntityTag : new CompoundTag();
    }

    public OpenBlockEditorPacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.blockId = buf.readUtf();
        CompoundTag readState = buf.readNbt();
        CompoundTag readAllowed = buf.readNbt();
        CompoundTag readNbt = buf.readNbt();
        this.stateValues = readState != null ? readState : new CompoundTag();
        this.allowedValues = readAllowed != null ? readAllowed : new CompoundTag();
        this.blockEntityTag = readNbt != null ? readNbt : new CompoundTag();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        buf.writeUtf(this.blockId);
        buf.writeNbt(this.stateValues);
        buf.writeNbt(this.allowedValues);
        buf.writeNbt(this.blockEntityTag);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleOpenBlockEditor(
                this.blockPos,
                this.blockId,
                this.stateValues,
                this.allowedValues,
                this.blockEntityTag
            )));
        context.setPacketHandled(true);
    }
}
