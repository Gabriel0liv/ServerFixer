package com.gabri.serverfixes.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestBlockEditorPacket {
    private final BlockPos blockPos;

    public RequestBlockEditorPacket(BlockPos blockPos) {
        this.blockPos = blockPos != null ? blockPos : BlockPos.ZERO;
    }

    public RequestBlockEditorPacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
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

            if (sender.distanceToSqr(this.blockPos.getX() + 0.5D, this.blockPos.getY() + 0.5D, this.blockPos.getZ() + 0.5D) > 144.0D) {
                return;
            }

            BlockState blockState = sender.level().getBlockState(this.blockPos);
            if (blockState == null) {
                return;
            }

            CompoundTag stateValues = new CompoundTag();
            CompoundTag allowedValues = new CompoundTag();

            for (Property<?> property : blockState.getProperties()) {
                String propertyName = property.getName();
                stateValues.putString(propertyName, propertyValueName(property, blockState.getValue(property)));

                ListTag values = new ListTag();
                for (Comparable<?> possibleValue : property.getPossibleValues()) {
                    values.add(StringTag.valueOf(propertyValueName(property, possibleValue)));
                }
                allowedValues.put(propertyName, values);
            }

            BlockEntity blockEntity = sender.level().getBlockEntity(this.blockPos);
            CompoundTag blockEntityTag = blockEntity != null ? blockEntity.saveWithoutMetadata() : new CompoundTag();
            String blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();

            NetworkHandler.sendToPlayer(sender, new OpenBlockEditorPacket(this.blockPos, blockId, stateValues, allowedValues, blockEntityTag));
        });
        context.setPacketHandled(true);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String propertyValueName(Property<T> property, Comparable<?> value) {
        return property.getName((T) value);
    }
}
