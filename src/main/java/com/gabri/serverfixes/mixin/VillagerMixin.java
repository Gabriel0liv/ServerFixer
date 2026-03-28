package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optimized Villager Throttling.
 * This is the gold of TPS optimization.
 */
@SuppressWarnings("all")
@Mixin(Villager.class)
public abstract class VillagerMixin {

    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void onAiTick(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        int tickRate = ServerFixesConfig.VILLAGER_TICK_RATE.get();
        if (villager.tickCount % tickRate != 0) {
            ci.cancel();
        }
    }
}
