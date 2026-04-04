package com.gabri.serverfixes.network;

import com.gabri.serverfixes.client.gui.LootDropDTO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public final class LootStudioPacketCodec {
    private LootStudioPacketCodec() {
    }

    public static void writeResourceLocationList(FriendlyByteBuf buf, List<ResourceLocation> ids) {
        List<ResourceLocation> safe = ids != null ? ids : List.of();
        int size = Math.min(safe.size(), 32767);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            ResourceLocation id = safe.get(i);
            if (id == null) {
                id = ResourceLocation.fromNamespaceAndPath("minecraft", "empty");
            }
            buf.writeResourceLocation(id);
        }
    }

    public static List<ResourceLocation> readResourceLocationList(FriendlyByteBuf buf) {
        int size = Math.max(0, Math.min(buf.readVarInt(), 32767));
        List<ResourceLocation> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(buf.readResourceLocation());
        }
        return ids;
    }

    public static void writeLootDropList(FriendlyByteBuf buf, List<LootDropDTO> drops) {
        List<LootDropDTO> safe = drops != null ? drops : List.of();
        int size = Math.min(safe.size(), 512);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writeLootDrop(buf, safe.get(i));
        }
    }

    public static List<LootDropDTO> readLootDropList(FriendlyByteBuf buf) {
        int size = Math.max(0, Math.min(buf.readVarInt(), 512));
        List<LootDropDTO> drops = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            drops.add(readLootDrop(buf));
        }
        return drops;
    }

    private static void writeLootDrop(FriendlyByteBuf buf, LootDropDTO dto) {
        LootDropDTO safe = dto != null
            ? dto
            : new LootDropDTO(ItemStack.EMPTY, 0.0D, 1, 1, false, false, true);

        ItemStack stack = safe.getItem() != null ? safe.getItem() : ItemStack.EMPTY;
        buf.writeItem(stack);
        buf.writeDouble(safe.getChance());
        buf.writeVarInt(safe.getMin());
        buf.writeVarInt(safe.getMax());
        buf.writeBoolean(safe.isRequirePlayerKill());
        buf.writeBoolean(safe.isAffectedByLooting());
        buf.writeBoolean(safe.isComplex());
    }

    private static LootDropDTO readLootDrop(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        double chance = buf.readDouble();
        int min = buf.readVarInt();
        int max = buf.readVarInt();
        boolean requirePlayerKill = buf.readBoolean();
        boolean affectedByLooting = buf.readBoolean();
        boolean complex = buf.readBoolean();
        return new LootDropDTO(stack, chance, min, max, requirePlayerKill, affectedByLooting, complex);
    }
}
