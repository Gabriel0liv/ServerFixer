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

        ItemStack stack = safe.getItem() != null ? safe.getItem().copy() : ItemStack.EMPTY;
        int min = Math.max(1, safe.getMin());
        int max = Math.max(min, safe.getMax());
        double chance = Math.max(0.0D, Math.min(100.0D, safe.getChance()));

        buf.writeItem(stack);
        buf.writeDouble(chance);
        buf.writeVarInt(min);
        buf.writeVarInt(max);
        buf.writeBoolean(safe.isRequirePlayerKill());
        buf.writeBoolean(safe.isAffectedByLooting());
        buf.writeBoolean(safe.isComplex());

        boolean hasTag = safe.getTag() != null;
        buf.writeBoolean(hasTag);
        if (hasTag) {
            buf.writeResourceLocation(safe.getTag());
        }

        boolean hasRef = safe.getReferenceTable() != null;
        buf.writeBoolean(hasRef);
        if (hasRef) {
            buf.writeResourceLocation(safe.getReferenceTable());
        }

        buf.writeBoolean(safe.isEnchantRandomly());
        buf.writeBoolean(safe.isEnchantWithLevels());
        LootDropDTO.Range levels = safe.getEnchantLevelsRange() != null
            ? safe.getEnchantLevelsRange()
            : new LootDropDTO.Range(10, 30);
        buf.writeVarInt(Math.max(1, levels.getMin()));
        buf.writeVarInt(Math.max(1, Math.max(levels.getMin(), levels.getMax())));
    }

    private static LootDropDTO readLootDrop(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        double chance = Math.max(0.0D, Math.min(100.0D, buf.readDouble()));
        int min = Math.max(1, buf.readVarInt());
        int max = Math.max(min, buf.readVarInt());
        boolean requirePlayerKill = buf.readBoolean();
        boolean affectedByLooting = buf.readBoolean();
        boolean complex = buf.readBoolean();

        ResourceLocation tag = buf.readBoolean() ? buf.readResourceLocation() : null;
        ResourceLocation referenceTable = buf.readBoolean() ? buf.readResourceLocation() : null;

        boolean enchantRandomly = buf.readBoolean();
        boolean enchantWithLevels = buf.readBoolean();
        int enchantMin = Math.max(1, buf.readVarInt());
        int enchantMax = Math.max(enchantMin, buf.readVarInt());
        LootDropDTO.Range levels = enchantWithLevels ? new LootDropDTO.Range(enchantMin, enchantMax) : null;

        return new LootDropDTO(stack, chance, min, max, requirePlayerKill, affectedByLooting, complex, tag, referenceTable, enchantRandomly, enchantWithLevels, levels);
    }
}
