package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Entity Culling para Botania - Mana Bursts e Alfheim Portals.
 * 
 * - Só aplica culling se enableEntityCulling = true E cullBotania = true
 * - ManaBursts: pausa partículas e sync quando player longe
 * - AlfheimPortal: pausa transformação de entities quando player longe
 * - NÃO afeta funcionamento quando players estão perto
 */
@Pseudo
@Mixin(targets = "vazkii.botania.common.entity.ManaBurstEntity")
public abstract class ManaBurstCullingMixin {
    
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void serverfixes$applyCulling(CallbackInfo ci) {
        if (!ServerFixesConfig.ENABLE_ENTITY_CULLING.get()) return;
        if (!ServerFixesConfig.CULL_BOTANIA.get()) return;
        
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        
        if (level.isClientSide()) return;
        
        int cullDistance = ServerFixesConfig.ENTITY_CULLING_DISTANCE.get();
        AABB searchArea = self.getBoundingBox().inflate(cullDistance);
        
        List<Player> players = level.getEntitiesOfClass(Player.class, searchArea);
        if (players.isEmpty()) {
            // Nenhum player perto - mana burst para de tickar
            // (vai ser removido naturalmente por timeout depois)
            ci.cancel();
        }
    }
}
