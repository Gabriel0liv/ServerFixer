package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Culling para o Alfheim Portal do Botania.
 * Pausa o processamento de transformação de itens quando não há jogadores por perto.
 * Isso reduz lag em farms de Alfheim Portal que ficam rodando sem ninguém olhar.
 */
@Pseudo
@Mixin(targets = "vazkii.botania.common.block.tile.TileAlfheimPortal", remap = false)
public abstract class AlfheimPortalCullingMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void serverfixes$applyCulling(Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state, Object tileEntity, CallbackInfo ci) {
        // Verifica se culling está ativo
        if (!ServerFixesConfig.ENABLE_ENTITY_CULLING.get()) return;
        if (!ServerFixesConfig.CULL_BOTANIA.get()) return;

        int cullDistance = ServerFixesConfig.ENTITY_CULLING_DISTANCE.get();
        
        // Verifica se tem player perto do portal
        List<Player> players = level.getEntitiesOfClass(Player.class, 
            net.minecraft.world.phys.AABB.ofSize(net.minecraft.world.phys.Vec3.atCenterOf(pos), cullDistance * 2, cullDistance * 2, cullDistance * 2));
        
        if (players.isEmpty()) {
            // Nenhum player perto - pula tick do portal
            ci.cancel();
        }
    }
}
