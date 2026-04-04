package com.gabri.serverfixes.mixin.accessor;

import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Acessa conditions[] de cada entry individual (base class). */
@Mixin(LootPoolEntryContainer.class)
public interface LootPoolEntryContainerAccessor {
    @Accessor("conditions")
    LootItemCondition[] sf_getConditions();
}
