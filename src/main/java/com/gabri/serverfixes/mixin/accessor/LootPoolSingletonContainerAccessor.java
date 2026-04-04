package com.gabri.serverfixes.mixin.accessor;

import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Acessa functions[] e weight de entries singleton (LootItem, TagEntry, etc). */
@Mixin(LootPoolSingletonContainer.class)
public interface LootPoolSingletonContainerAccessor {
    @Accessor("functions")
    LootItemFunction[] sf_getFunctions();

    @Accessor("weight")
    int sf_getWeight();
}
