package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Secondary Mixin to nerf Turtle Master effects specifically on hybrid servers like Archlight/Mohist.
 * Some servers bypass Potion.getEffects() and call PotionUtils directly or use their own NBT parsing.
 */
@Mixin(PotionUtils.class)
public abstract class PotionUtilsMixin {

    @Inject(method = "getAllEffects(Lnet/minecraft/nbt/CompoundTag;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private static void serverfixes$onGetAllEffects(CompoundTag nbt, CallbackInfoReturnable<List<MobEffectInstance>> cir) {
        if (!ServerFixesConfig.NERF_TURTLE_MASTER.get()) {
            return;
        }

        Potion potion = PotionUtils.getPotion(nbt);
        boolean isNormalTurtle = potion == Potions.TURTLE_MASTER || potion == Potions.LONG_TURTLE_MASTER;
        boolean isStrongTurtle = potion == Potions.STRONG_TURTLE_MASTER;

        if (isNormalTurtle || isStrongTurtle) {
            List<MobEffectInstance> originalEffects = cir.getReturnValue();
            List<MobEffectInstance> modifiedEffects = new ArrayList<>();

            int maxRes = isStrongTurtle ? ServerFixesConfig.STRONG_TURTLE_MASTER_RESISTANCE.get() : ServerFixesConfig.TURTLE_MASTER_RESISTANCE.get();
            int maxSlow = isStrongTurtle ? ServerFixesConfig.STRONG_TURTLE_MASTER_SLOWNESS.get() : ServerFixesConfig.TURTLE_MASTER_SLOWNESS.get();

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
                // System.out.println("[ServerFixes] Modified Turtle Master effects in PotionUtils (Hybrid Server fix)");
                cir.setReturnValue(List.copyOf(modifiedEffects));
            }
        }
    }
}
