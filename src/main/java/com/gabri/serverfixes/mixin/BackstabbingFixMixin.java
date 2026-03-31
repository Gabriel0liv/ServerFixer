package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to fix Backstabbing enchantment exploit.
 * Restricts the damage amplification to only apply to standard player melee attacks.
 */
@SuppressWarnings("all")
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.item.enchantment.BackstabbingEnchantment$BackstabbingEvent", remap = false)
public class BackstabbingFixMixin {

    @Inject(
        method = "onKnifeBackstab(Lnet/minecraftforge/event/entity/living/LivingHurtEvent;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void onKnifeBackstab(LivingHurtEvent event, CallbackInfo ci) {
        if (ServerFixesConfig.FIX_BACKSTABBING_EXPLOIT.get()) {
            // Only allow backstabbing if the damage type is standard player melee attack.
            // This prevents spells (SpellDamageSource), projectiles, and other modded damage from being amplified
            // even if the player is holding a knife with the enchantment.
            if (!event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
                ci.cancel();
            }
        }
    }
}
