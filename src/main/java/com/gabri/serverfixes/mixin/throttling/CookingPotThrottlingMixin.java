package com.gabri.serverfixes.mixin.throttling;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity", remap = false)
public abstract class CookingPotThrottlingMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void throttleTick(Level world, BlockPos pos, BlockState state, Object blockEntity, CallbackInfo ci) {
        if (!ServerFixesConfig.THROTTLE_FARM_AND_CHARM.get()) return;
        
        int rate = ServerFixesConfig.COOKING_POT_TICK_RATE.get();
        if (rate > 1 && world.getGameTime() % rate != 0) {
            ci.cancel();
        }
    }
}
