package com.gabri.serverfixes.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.function.Supplier;

@SuppressWarnings("null")
public class SaveBlockEditorPacket {
    private final BlockPos blockPos;
    private final CompoundTag stateValues;
    private final CompoundTag blockEntityTag;

    public SaveBlockEditorPacket(BlockPos blockPos, CompoundTag stateValues, CompoundTag blockEntityTag) {
        this.blockPos = blockPos != null ? blockPos : BlockPos.ZERO;
        this.stateValues = stateValues != null ? stateValues : new CompoundTag();
        this.blockEntityTag = blockEntityTag != null ? blockEntityTag : new CompoundTag();
    }

    public SaveBlockEditorPacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        CompoundTag readState = buf.readNbt();
        CompoundTag readNbt = buf.readNbt();
        this.stateValues = readState != null ? readState : new CompoundTag();
        this.blockEntityTag = readNbt != null ? readNbt : new CompoundTag();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        buf.writeNbt(this.stateValues);
        buf.writeNbt(this.blockEntityTag);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = context.getSender();
            if (sender == null || !sender.isCreative() || !sender.hasPermissions(2)) {
                return;
            }

            if (sender.distanceToSqr(this.blockPos.getX() + 0.5D, this.blockPos.getY() + 0.5D, this.blockPos.getZ() + 0.5D) > 144.0D) {
                return;
            }

            BlockState oldState = sender.level().getBlockState(this.blockPos);
            BlockState newState = applyStateValues(oldState, this.stateValues);
            if (newState != oldState) {
                sender.level().setBlock(this.blockPos, newState, 3);
            }

            BlockEntity blockEntity = sender.level().getBlockEntity(this.blockPos);
            if (blockEntity != null) {
                CompoundTag merged = blockEntity.serializeNBT();
                merged.merge(this.blockEntityTag);
                blockEntity.deserializeNBT(merged);
                blockEntity.setChanged();
            }

            BlockState currentState = sender.level().getBlockState(this.blockPos);
            sender.level().sendBlockUpdated(this.blockPos, oldState, currentState, 3);
        });
        context.setPacketHandled(true);
    }

    private static BlockState applyStateValues(BlockState baseState, CompoundTag stateValues) {
        BlockState state = baseState;
        for (Property<?> property : baseState.getProperties()) {
            String propertyName = property.getName();
            if (!stateValues.contains(propertyName, Tag.TAG_STRING)) {
                continue;
            }
            state = tryApplyPropertyValue(state, property, stateValues.getString(propertyName));
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState tryApplyPropertyValue(BlockState state, Property<T> property, String rawValue) {
        Optional<T> parsed = property.getValue(rawValue);
        if (parsed.isPresent()) {
            return state.setValue(property, parsed.get());
        }
        return state;
    }
}
