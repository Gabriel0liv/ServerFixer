package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin to specifically nerf the Turtle Master potion effects.
 * This intercepts the effect list retrieval, allowing us to override the amplifiers
 * for Resistance and Slowness without affecting other sources of these effects.
 */
@SuppressWarnings("all")
@Mixin(Potion.class)
public class PotionMixin {
    @Inject(method = "getEffects", at = @At("RETURN"), cancellable = true)
    private void serverfixes$onGetEffects(CallbackInfoReturnable<List<MobEffectInstance>> cir) {
        if (!ServerFixesConfig.NERF_TURTLE_MASTER.get()) {
            return;
        }

        Potion potion = (Potion) (Object) this;
        boolean isNormalTurtle = potion == Potions.TURTLE_MASTER || potion == Potions.LONG_TURTLE_MASTER;
        boolean isStrongTurtle = potion == Potions.STRONG_TURTLE_MASTER;

        if (isNormalTurtle || isStrongTurtle) {
            List<MobEffectInstance> originalEffects = cir.getReturnValue();
            List<MobEffectInstance> modifiedEffects = new ArrayList<>();

            int maxRes = isStrongTurtle ? ServerFixesConfig.STRONG_TURTLE_MASTER_RESISTANCE.get() : ServerFixesConfig.TURTLE_MASTER_RESISTANCE.get();
            int maxSlow = isStrongTurtle ? ServerFixesConfig.STRONG_TURTLE_MASTER_SLOWNESS.get() : ServerFixesConfig.TURTLE_MASTER_SLOWNESS.get();

            // Level to amplifier (e.g., Level 3 -> Amplifier 2)
            int targetResAmp = Math.max(0, maxRes - 1);
            int targetSlowAmp = Math.max(0, maxSlow - 1);

            boolean changed = false;
            for (MobEffectInstance effect : originalEffects) {
                var type = effect.getEffect();
                if (type == net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE && effect.getAmplifier() > targetResAmp) {
                    modifiedEffects.add(new MobEffectInstance(java.util.Objects.requireNonNull(type), effect.getDuration(), targetResAmp, effect.isAmbient(), effect.isVisible(), effect.showIcon()));
                    changed = true;
                } else if (type == net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN && effect.getAmplifier() > targetSlowAmp) {
                    modifiedEffects.add(new MobEffectInstance(java.util.Objects.requireNonNull(type), effect.getDuration(), targetSlowAmp, effect.isAmbient(), effect.isVisible(), effect.showIcon()));
                    changed = true;
                } else {
                    modifiedEffects.add(effect);
                }
            }
            
            if (changed) {
                cir.setReturnValue(List.copyOf(modifiedEffects));
            }
        }
    }
}
