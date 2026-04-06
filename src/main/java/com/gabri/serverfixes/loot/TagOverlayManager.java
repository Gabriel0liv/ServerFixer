package com.gabri.serverfixes.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia tags customizadas em memória sem precisar de /reload.
 *
 * <p>Quando o Tag Studio salva uma tag, o JSON é gravado no disco E o
 * overlay em memória é atualizado. O Loot Studio e o Tag Studio consultam
 * este overlay para validação imediata, sem precisar recarregar o servidor.</p>
 *
 * <p>Nota: outros mods que consultem ForgeRegistries.ITEMS.tags() diretamente
 * não verão as tags customizadas até um /reload ou restart do servidor.</p>
 */
public class TagOverlayManager {
    private static final Logger LOGGER = LogManager.getLogger("ServerFixes/TagOverlay");

    // tagId -> lista de itemIds
    private static final Map<String, List<String>> overlay = new ConcurrentHashMap<>();

    /** Retorna todas as tags do overlay (tagId -> itemIds). */
    public static Map<String, List<String>> getOverlay() {
        return Collections.unmodifiableMap(overlay);
    }

    /** Define ou substitui uma tag no overlay. */
    public static void setTag(String tagId, List<String> itemIds) {
        overlay.put(tagId, new ArrayList<>(itemIds));
        LOGGER.debug("[TagOverlay] Tag '{}' atualizada com {} itens", tagId, itemIds.size());
    }

    /** Remove uma tag do overlay. */
    public static void removeTag(String tagId) {
        overlay.remove(tagId);
        LOGGER.debug("[TagOverlay] Tag '{}' removida do overlay", tagId);
    }

    /** Retorna os itens de uma tag (overlay primeiro, depois Forge registry). */
    public static List<String> getTagItems(String tagId) {
        // Primeiro verifica o overlay
        if (overlay.containsKey(tagId)) {
            return new ArrayList<>(overlay.get(tagId));
        }
        // Fallback para o Forge registry
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl != null) {
            var tagKey = net.minecraft.tags.ItemTags.create(rl);
            var tagManager = ForgeRegistries.ITEMS.tags();
            if (tagManager != null && tagManager.isKnownTagName(tagKey)) {
                List<String> items = new ArrayList<>();
                tagManager.getTag(tagKey).stream().forEach(item -> {
                    var itemRl = ForgeRegistries.ITEMS.getKey(item);
                    if (itemRl != null) items.add(itemRl.toString());
                });
                return items;
            }
        }
        return new ArrayList<>();
    }

    /** Verifica se uma tag existe (overlay OU Forge registry). */
    public static boolean isTagKnown(String tagId) {
        if (overlay.containsKey(tagId)) return true;
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl != null) {
            var tagKey = net.minecraft.tags.ItemTags.create(rl);
            var tagManager = ForgeRegistries.ITEMS.tags();
            if (tagManager != null && tagManager.isKnownTagName(tagKey)) return true;
        }
        return false;
    }

    /** Retorna todas as tags conhecidas (overlay + Forge registry). */
    public static List<com.gabri.serverfixes.network.RequestTagsPacket.TagDTO> getAllTags() {
        Map<String, List<String>> allTags = new LinkedHashMap<>();

        // Adiciona tags do Forge registry primeiro
        var tagManager = ForgeRegistries.ITEMS.tags();
        if (tagManager != null) {
            for (var tagKey : tagManager.getTagNames().toList()) {
                var tag = tagManager.getTag(tagKey);
                List<String> itemIds = new ArrayList<>();
                tag.stream().forEach(item -> {
                    var rl = ForgeRegistries.ITEMS.getKey(item);
                    if (rl != null) itemIds.add(rl.toString());
                });
                if (!itemIds.isEmpty()) {
                    allTags.put(tagKey.location().toString(), itemIds);
                }
            }
        }

        // Sobrescreve com tags do overlay (prioridade)
        for (Map.Entry<String, List<String>> entry : overlay.entrySet()) {
            allTags.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        List<com.gabri.serverfixes.network.RequestTagsPacket.TagDTO> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : allTags.entrySet()) {
            result.add(new com.gabri.serverfixes.network.RequestTagsPacket.TagDTO(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /** Limpa o overlay (usado no reload do servidor). */
    public static void clear() {
        overlay.clear();
    }
}
