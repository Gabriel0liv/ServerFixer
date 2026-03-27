package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.events.AntiSwapExploitHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("all")
@Mixin(Player.class)
public abstract class PlayerMixin {

    /**
     * At tick start, snapshot the held item (before any packets from this tick are processed).
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        AntiSwapExploitHandler.onTickStart((Player)(Object)this);
    }

    /**
     * At the start of an attack, temporarily restore original weapon attributes
     * so the damage formula can't use the swapped weapon's stats.
     */
    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttackHead(Entity target, CallbackInfo ci) {
        AntiSwapExploitHandler.applyOriginalAttributes((Player)(Object)this);
    }

    /**
     * Hijack all weapon checks DURING the Player.attack() method.
     * This ensures Enchantments, Knockback, Sweeping, etc. all use the weapon 
     * from the start of the tick, preventing attribute/enchantment-merging exploits.
     */
    @org.spongepowered.asm.mixin.injection.Redirect(
        method = "attack", 
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getMainHandItem()Lnet/minecraft/world/item/ItemStack;")
    )
    private net.minecraft.world.item.ItemStack hijackWeaponForAttack(Player instance) {
        net.minecraft.world.item.ItemStack original = AntiSwapExploitHandler.getTickStartItem(instance);
        if (original != null && !net.minecraft.world.item.ItemStack.matches(instance.getMainHandItem(), original)) {
            return original;
        }
        return instance.getMainHandItem();
    }

    /**
     * After the attack finishes (at any return point), restore the actual current 
     * item's attributes so the game returns to a consistent state.
     */
    @Inject(method = "attack", at = @At("RETURN"))
    private void onAttackReturn(Entity target, CallbackInfo ci) {
        AntiSwapExploitHandler.restoreCurrentAttributes((Player)(Object)this);
    }
}
