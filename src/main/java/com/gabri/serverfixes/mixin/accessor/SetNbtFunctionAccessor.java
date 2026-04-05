package com.gabri.serverfixes.mixin.accessor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SetNbtFunction.class)
public interface SetNbtFunctionAccessor {
    @Accessor("tag")
    CompoundTag sf_getTag();
}
