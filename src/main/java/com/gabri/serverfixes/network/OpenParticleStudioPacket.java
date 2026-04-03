package com.gabri.serverfixes.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenParticleStudioPacket {

    public OpenParticleStudioPacket() {
    }

    public OpenParticleStudioPacket(FriendlyByteBuf buf) {
    }

    public void toBytes(FriendlyByteBuf buf) {
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                com.gabri.serverfixes.client.ClientPacketHandler.handleOpenParticleStudio();
            } catch (Exception e) {
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§cFalha ao abrir Particle Studio: " + e.getClass().getSimpleName()),
                        true
                    );
                }
            }
        }));
        context.setPacketHandled(true);
    }
}
