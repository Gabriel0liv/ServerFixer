package com.gabri.serverfixes.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia tags customizadas de entidades em memória sem precisar de /reload.
 *
 * <p>Quando o Entity Tag Studio salva uma tag, o JSON é gravado no disco E o
 * overlay em memória é atualizado. O sistema consulta este overlay para validação imediata.</p>
 */
public class EntityTagOverlayManager {
    private static final Logger LOGGER = LogManager.getLogger("ServerFixes/EntityTagOverlay");

    // tagId -> lista de entityIds
    private static final Map<String, List<String>> overlay = new ConcurrentHashMap<>();

    /** Retorna todas as tags do overlay (tagId -> entityIds). */
    public static Map<String, List<String>> getOverlay() {
        return Collections.unmodifiableMap(overlay);
    }

    /** Define ou substitui uma tag no overlay. */
    public static void setTag(String tagId, List<String> entityIds) {
        overlay.put(tagId, new ArrayList<>(entityIds));
        LOGGER.debug("[EntityTagOverlay] Tag '{}' atualizada com {} entidades", tagId, entityIds.size());
    }

    /** Remove uma tag do overlay. */
    public static void removeTag(String tagId) {
        overlay.remove(tagId);
        LOGGER.debug("[EntityTagOverlay] Tag '{}' removida do overlay", tagId);
    }

    /** Retorna as entidades de uma tag (overlay primeiro, depois Forge registry). */
    public static List<String> getTagEntities(String tagId) {
        // Primeiro verifica o overlay
        if (overlay.containsKey(tagId)) {
            return new ArrayList<>(overlay.get(tagId));
        }
        // Fallback para o Forge registry
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl != null) {
            var tagManager = ForgeRegistries.ENTITY_TYPES.tags();
            if (tagManager != null) {
                // Iterate through all tag names to find matching one
                for (var tagKey : tagManager.getTagNames().toList()) {
                    if (tagKey.location().equals(rl)) {
                        List<String> entities = new ArrayList<>();
                        tagManager.getTag(tagKey).stream().forEach(entityType -> {
                            var entityRl = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
                            if (entityRl != null) entities.add(entityRl.toString());
                        });
                        return entities;
                    }
                }
            }
        }
        return new ArrayList<>();
    }

    /** Verifica se uma tag existe (overlay OU Forge registry). */
    public static boolean isTagKnown(String tagId) {
        if (overlay.containsKey(tagId)) return true;
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl != null) {
            var tagManager = ForgeRegistries.ENTITY_TYPES.tags();
            if (tagManager != null) {
                for (var tagKey : tagManager.getTagNames().toList()) {
                    if (tagKey.location().equals(rl)) return true;
                }
            }
        }
        return false;
    }

    /** Retorna todas as tags conhecidas (overlay + Forge registry). */
    public static List<com.gabri.serverfixes.network.RequestEntityTagsPacket.EntityTagDTO> getAllTags() {
        Map<String, List<String>> allTags = new LinkedHashMap<>();

        // Adiciona tags do Forge registry primeiro
        var tagManager = ForgeRegistries.ENTITY_TYPES.tags();
        if (tagManager != null) {
            for (var tagKey : tagManager.getTagNames().toList()) {
                var tag = tagManager.getTag(tagKey);
                List<String> entityIds = new ArrayList<>();
                tag.stream().forEach(entityType -> {
                    var rl = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
                    if (rl != null) entityIds.add(rl.toString());
                });
                if (!entityIds.isEmpty()) {
                    allTags.put(tagKey.location().toString(), entityIds);
                }
            }
        }

        // Sobrescreve com tags do overlay (prioridade)
        for (Map.Entry<String, List<String>> entry : overlay.entrySet()) {
            allTags.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        List<com.gabri.serverfixes.network.RequestEntityTagsPacket.EntityTagDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : allTags.entrySet()) {
            result.add(new com.gabri.serverfixes.network.RequestEntityTagsPacket.EntityTagDTO(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /** Limpa o overlay (usado no reload do servidor). */
    public static void clear() {
        overlay.clear();
    }
}
