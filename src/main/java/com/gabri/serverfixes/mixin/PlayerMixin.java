package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import com.gabri.serverfixes.events.AntiSwapExploitHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player Mixin for Anti-Swap Exploit Protection (Ninja Cancellation)
 */
@SuppressWarnings("all")
@Mixin(Player.class)
public abstract class PlayerMixin {

    /**
     * Snapshot the held item at the start of every tick.
     */
    @Inject(method = "tick", at = @At("HEAD"), remap = true)
    private void onTickHead(CallbackInfo ci) {
        AntiSwapExploitHandler.onTickStart((Player) (Object) this);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void onAttackHead(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        
        // Skip client-side checks and respect configuration
        if (player.level().isClientSide || !ServerFixesConfig.ENABLE_ANTI_SWAP.get()) return;

        ItemStack snapshot = AntiSwapExploitHandler.getTickStartItem(player);
        ItemStack current = player.getMainHandItem();

        // Compare the snapshot from the start of the tick with the current hand.
        // We use matches() to check if they are the same item and have the same data.
        if (!ItemStack.matches(snapshot, current)) {
            // WEAPON SWAP DETECTED!
            
            // Debug message
            if (ServerFixesConfig.DEBUG_ANTISWAP.get() && player.getTags().contains("sf_debug_antiswap")) {
                net.minecraft.network.chat.MutableComponent message = Component.literal("[Anti-Swap] Ataque cancelado! Troca detectada: ").withStyle(ChatFormatting.RED);
                message.append(Component.literal(snapshot.getHoverName().getString()).withStyle(ChatFormatting.GRAY));
                message.append(Component.literal(" -> ").withStyle(ChatFormatting.WHITE));
                message.append(Component.literal(current.getHoverName().getString()).withStyle(ChatFormatting.GOLD));
                
                player.sendSystemMessage(message);
            }

            // Cancel the attack to prevent Frankenstein attributes or speed exploits.
            ci.cancel();
        }
    }
}
