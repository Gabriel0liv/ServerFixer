package com.gabri.serverfixes.mixin.accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Acessa o campo 'item' de LootItem.
 * Em Forge 1.20.1 Mojang mappings, o tipo é Holder&lt;Item&gt;.
 */
@Mixin(LootItem.class)
public interface LootItemAccessor {
    @Accessor("item")
    Item sf_getItem();
}
