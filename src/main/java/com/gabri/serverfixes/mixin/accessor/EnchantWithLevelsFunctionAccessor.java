package com.gabri.serverfixes.mixin.accessor;

import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantWithLevelsFunction.class)
public interface EnchantWithLevelsFunctionAccessor {
    @Accessor("levels")
    NumberProvider sf_getLevels();
}
