package com.gabri.serverfixes.mixin.throttling;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.satisfy.vinery.core.block.entity.FermentationBarrelBlockEntity", remap = false)
public abstract class FermentationBarrelThrottlingMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void throttleTick(Level world, BlockPos pos, Object fermentationBarrelBlockEntity, CallbackInfo ci) {
        if (!ServerFixesConfig.THROTTLE_VINERY.get()) return;
        
        int rate = ServerFixesConfig.FERMENTATION_BARREL_TICK_RATE.get();
        if (rate > 1 && world.getGameTime() % rate != 0) {
            ci.cancel();
        }
    }
}
