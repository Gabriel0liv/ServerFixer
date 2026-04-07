package com.gabri.serverfixes.client.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * DTO básico para representar uma receita na lista do Recipe Studio.
 * Contém apenas informações essenciais para exibição na UI.
 */
public record RecipeBasicDTO(
    ResourceLocation id,
    ResourceLocation type,
    ItemStack result
) {
    public RecipeBasicDTO(FriendlyByteBuf buf) {
        this(
            buf.readResourceLocation(),
            buf.readResourceLocation(),
            buf.readItem()
        );
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.id);
        buf.writeResourceLocation(this.type);
        buf.writeItem(this.result);
    }
}
