package com.gabri.serverfixes.client.gui;

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

    public LootDropDTO(ItemStack item, double chance, int min, int max, boolean requirePlayerKill, boolean affectedByLooting, boolean isComplex) {
        this.item = item;
        this.chance = chance;
        this.min = min;
        this.max = max;
        this.requirePlayerKill = requirePlayerKill;
        this.affectedByLooting = affectedByLooting;
        this.isComplex = isComplex;
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
}
