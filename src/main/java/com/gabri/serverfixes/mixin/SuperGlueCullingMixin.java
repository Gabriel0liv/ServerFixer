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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entity Culling para SuperGlue - COM CACHE GLOBAL por chunk de player.
 * 
 * - Players são buscados UMA VEZ por tick (não por glue!)
 * - Chunks ao redor dos players são marcados como "ativos"
 * - Glues consultam: meu chunk está ativo? Se não = pula tick
 * - Overhead: 1 busca/tick (todos os players) + N leituras de cache (glues)
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.glue.SuperGlueEntity")
public abstract class SuperGlueCullingMixin {
    
    // Cache: chunks que TEM players perto (chave: "dim:chunkX,chunkZ")
    private static final Set<String> serverfixes$activeChunks = ConcurrentHashMap.newKeySet();
    private static long serverfixes$lastUpdateTick = 0;
    
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void serverfixes$applyCulling(CallbackInfo ci) {
        if (!ServerFixesConfig.ENABLE_ENTITY_CULLING.get()) return;
        if (!ServerFixesConfig.CULL_SUPER_GLUE.get()) return;
        
        Entity self = (Entity) (Object) this;
        Level level = self.level();
        if (level.isClientSide()) return;
        
        long gameTime = level.getGameTime();
        int cullDistance = ServerFixesConfig.ENTITY_CULLING_DISTANCE.get();
        
        // Atualiza cache UNA VEZ por tick (todas as glues compartilham)
        if (gameTime != serverfixes$lastUpdateTick) {
            synchronized (serverfixes$activeChunks) {
                if (gameTime != serverfixes$lastUpdateTick) {
                    serverfixes$lastUpdateTick = gameTime;
                    serverfixes$updateActiveChunks(level, cullDistance);
                }
            }
        }
        
        // Verifica se chunk desta glue está ativo
        int glueChunkX = self.getBlockX() >> 4;
        int glueChunkZ = self.getBlockZ() >> 4;
        String dimId = level.dimension().location().toString();
        String chunkKey = dimId + ":" + glueChunkX + "," + glueChunkZ;
        
        if (!serverfixes$activeChunks.contains(chunkKey)) {
            ci.cancel(); // Nenhum player perto deste chunk - pula tick
        }
    }
    
    private static void serverfixes$updateActiveChunks(Level level, int cullDistance) {
        serverfixes$activeChunks.clear();
        
        // Quantos chunks de raio (64 blocos = 4 chunks)
        int chunkRadius = (cullDistance >> 4) + 1;
        String dimId = level.dimension().location().toString();
        
        // Coleta chunks dos players UMA VEZ
        // Set automaticamente evita duplicatas
        int playerCount = 0;
        for (Player player : level.players()) {
            int playerChunkX = player.getBlockX() >> 4;
            int playerChunkZ = player.getBlockZ() >> 4;
            playerCount++;
            
            // Marca chunks ao redor do player
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    String key = dimId + ":" + (playerChunkX + dx) + "," + (playerChunkZ + dz);
                    serverfixes$activeChunks.add(key);
                }
            }
        }
        
        // Se não tem players, limpa tudo (todas as glues pulam)
        if (playerCount == 0) {
            serverfixes$activeChunks.clear();
        }
    }
}
