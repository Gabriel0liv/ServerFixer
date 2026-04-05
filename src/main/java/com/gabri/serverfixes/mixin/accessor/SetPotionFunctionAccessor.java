package com.gabri.serverfixes.mixin.accessor;

import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.storage.loot.functions.SetPotionFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SetPotionFunction.class)
public interface SetPotionFunctionAccessor {
    @Accessor("potion")
    Potion sf_getPotion();
}
