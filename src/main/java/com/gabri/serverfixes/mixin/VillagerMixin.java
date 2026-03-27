package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@SuppressWarnings("all")
@Mixin(Villager.class)
public abstract class VillagerMixin {

    @Shadow @Nullable private Player lastTradedPlayer;

    @Shadow @Nullable public abstract Player getTradingPlayer();

    /**
     * Throttles the villager brain to save TPS.
     */
    @Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
    private void onAiTick(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        int tickRate = ServerFixesConfig.VILLAGER_TICK_RATE.get();
        if (villager.tickCount % tickRate != 0) {
            ci.cancel();
        }
    }

    /**
     * Resets trade uses if infinite trades are enabled for the player.
     * We use the named method 'rewardTrade' which will be remapped by Forge/Mixin.
     */
    @Inject(method = "rewardTrade", at = @At("TAIL"), remap = true)
    private void onRewardTrade(MerchantOffer offer, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        Player player = this.getTradingPlayer();
        
        if (player == null) {
            player = this.lastTradedPlayer;
        }

        if (player == null) return;

        // Debug messages
        if (ServerFixesConfig.ENABLE_DEBUG_LOGGING.get()) {
            player.sendSystemMessage(Component.literal("[Trade] Interceptado!")
                .withStyle(ChatFormatting.GOLD));
            
            String requiredTag = ServerFixesConfig.INFINITE_TRADE_TAG.get();
            boolean hasTag = player.getTags().contains(requiredTag);
            
            player.sendSystemMessage(Component.literal("[Trade] Player: " + player.getName().getString() + " | Tag: " + requiredTag + " | Tem? " + hasTag)
                .withStyle(ChatFormatting.AQUA));
        }

        if (ServerFixesConfig.ENABLE_INFINITE_TRADES.get()) {
            String tag = ServerFixesConfig.INFINITE_TRADE_TAG.get();
            if (player.getTags().contains(tag)) {
                offer.resetUses();
                if (ServerFixesConfig.ENABLE_DEBUG_LOGGING.get()) {
                    player.sendSystemMessage(Component.literal("[Trade] ✓ Usos resetados!")
                        .withStyle(ChatFormatting.GREEN));
                }
            }
        }
    }
}
