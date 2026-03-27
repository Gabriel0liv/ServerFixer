package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.events.AntiSwapExploitHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("all")
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PacketListenerMixin {
    @Shadow public ServerPlayer player;

    /**
     * When the player changes their selected hotbar slot, update the tick-start
     * snapshot so that normalizeAttributesForAttack() can detect the swap.
     * We store the item BEFORE the slot change happens (at HEAD).
     */
    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
    private void onHotbarSwap(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        // Force a snapshot update before the slot switches so the "previous" item is recorded
        AntiSwapExploitHandler.onTickStart(this.player);
    }

    // Offhand swap (F key) - also update snapshot
    @Inject(method = "handlePlayerAction", at = @At("HEAD"))
    private void onPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            AntiSwapExploitHandler.onTickStart(this.player);
        }
    }
}
