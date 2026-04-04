package com.gabri.serverfixes.client.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings("unused")
public class LootDropDTO {
    private ItemStack item;
    private double chance; // 0.0 - 100.0
    private int min; // 1 - 64
    private int max; // 1 - 64
    private boolean requirePlayerKill;
    private boolean affectedByLooting;
    private boolean isComplex;
    private ResourceLocation tag;
    private ResourceLocation referenceTable;
    private boolean enchantRandomly;
    private boolean enchantWithLevels;
    private Range enchantLevelsRange;

    public LootDropDTO(ItemStack item, double chance, int min, int max, boolean requirePlayerKill, boolean affectedByLooting, boolean isComplex) {
        this(item, chance, min, max, requirePlayerKill, affectedByLooting, isComplex, null, null, false, false, null);
    }

    public LootDropDTO(ItemStack item, double chance, int min, int max, boolean requirePlayerKill, boolean affectedByLooting, boolean isComplex, ResourceLocation tag, ResourceLocation referenceTable) {
        this(item, chance, min, max, requirePlayerKill, affectedByLooting, isComplex, tag, referenceTable, false, false, null);
    }

    public LootDropDTO(ItemStack item, double chance, int min, int max, boolean requirePlayerKill, boolean affectedByLooting, boolean isComplex,
                       ResourceLocation tag, ResourceLocation referenceTable,
                       boolean enchantRandomly, boolean enchantWithLevels, Range enchantLevelsRange) {
        this.item = item;
        this.chance = chance;
        this.min = min;
        this.max = max;
        this.requirePlayerKill = requirePlayerKill;
        this.affectedByLooting = affectedByLooting;
        this.isComplex = isComplex;
        this.tag = tag;
        this.referenceTable = referenceTable;
        this.enchantRandomly = enchantRandomly;
        this.enchantWithLevels = enchantWithLevels;
        this.enchantLevelsRange = enchantLevelsRange;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public boolean isRequirePlayerKill() {
        return requirePlayerKill;
    }

    public void setRequirePlayerKill(boolean requirePlayerKill) {
        this.requirePlayerKill = requirePlayerKill;
    }

    public boolean isAffectedByLooting() {
        return affectedByLooting;
    }

    public void setAffectedByLooting(boolean affectedByLooting) {
        this.affectedByLooting = affectedByLooting;
    }

    public boolean isComplex() {
        return isComplex;
    }

    public void setComplex(boolean complex) {
        isComplex = complex;
    }

    public ResourceLocation getTag() {
        return tag;
    }

    public void setTag(ResourceLocation tag) {
        this.tag = tag;
    }

    public ResourceLocation getReferenceTable() {
        return referenceTable;
    }

    public void setReferenceTable(ResourceLocation referenceTable) {
        this.referenceTable = referenceTable;
    }

    public boolean isEnchantRandomly() {
        return enchantRandomly;
    }

    public void setEnchantRandomly(boolean enchantRandomly) {
        this.enchantRandomly = enchantRandomly;
    }

    public boolean isEnchantWithLevels() {
        return enchantWithLevels;
    }

    public void setEnchantWithLevels(boolean enchantWithLevels) {
        this.enchantWithLevels = enchantWithLevels;
    }

    public Range getEnchantLevelsRange() {
        return enchantLevelsRange;
    }

    public void setEnchantLevelsRange(Range enchantLevelsRange) {
        this.enchantLevelsRange = enchantLevelsRange;
    }

    public static final class Range {
        private int min;
        private int max;

        public Range(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public int getMin() {
            return min;
        }

        public void setMin(int min) {
            this.min = min;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }
    }
}
