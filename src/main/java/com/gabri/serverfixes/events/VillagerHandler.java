package com.gabri.serverfixes.events;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles Merchant interactions and Infinite Trades using stable Forge events.
 * This replaces problematic Mixins for better production stability.
 */
@SuppressWarnings("all")
public class VillagerHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof AbstractVillager merchant) {
            Player player = event.getEntity();
            
            // 1. Interaction Debug
            if (ServerFixesConfig.DEBUG_VILLAGERS.get()) {
                Component debugMsg = Component.literal("[Merchant Debug] Interação detectada!")
                    .withStyle(ChatFormatting.GOLD);
                player.sendSystemMessage(debugMsg);
                
                Component infoMsg = Component.literal("Alvo: " + merchant.getName().getString() + " | Classe: " + merchant.getClass().getSimpleName())
                    .withStyle(ChatFormatting.GRAY);
                player.sendSystemMessage(infoMsg);
            }

            // 2. Infinite Trade Logic (Forge Event approach)
            if (ServerFixesConfig.ENABLE_INFINITE_TRADES.get()) {
                String tag = ServerFixesConfig.INFINITE_TRADE_TAG.get();
                if (player.getTags().contains(tag)) {
                    MerchantOffers offers = merchant.getOffers();
                    if (offers != null && !offers.isEmpty()) {
                        offers.forEach(offer -> offer.resetUses());
                        
                        if (ServerFixesConfig.DEBUG_VILLAGERS.get()) {
                            Component successMsg = Component.literal("[Infinite Trade] ✓ Trocas resetadas para este encontro!")
                                .withStyle(ChatFormatting.GREEN);
                            player.sendSystemMessage(successMsg);
                        }
                    }
                }
            }
        }
    }
}
