package com.gabri.serverfixes.mixin.accessor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SetNameFunction.class)
public interface SetNameFunctionAccessor {
    @Accessor("name")
    Component sf_getName();
}
