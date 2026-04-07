package com.gabri.serverfixes.recipe;

import com.gabri.serverfixes.client.gui.RecipeBasicDTO;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Motor de busca para o Recipe Studio.
 * Filtra receitas carregadas na memória com paginação server-side.
 */
public class RecipeStudioLogic {

    private static final int MAX_RESULTS = 150;

    /**
     * Retorna uma lista filtrada de receitas para exibição no Recipe Studio.
     *
     * @param server     O servidor Minecraft
     * @param modFilter  Filtro por namespace (ex: "minecraft", "create")
     * @param typeFilter Filtro por tipo de receita (ex: "crafting_shaped")
     * @param itemFilter Filtro por nome/ID do item resultado
     * @return Lista de RecipeBasicDTO (máximo 150 resultados)
     */
    public static List<RecipeBasicDTO> getFilteredRecipes(MinecraftServer server, String modFilter, String typeFilter, String itemFilter) {
        List<RecipeBasicDTO> result = new ArrayList<>();

        String modQuery = modFilter != null ? modFilter.trim().toLowerCase(Locale.ROOT) : "";
        String typeQuery = typeFilter != null ? typeFilter.trim().toLowerCase(Locale.ROOT) : "";
        String itemQuery = itemFilter != null ? itemFilter.trim().toLowerCase(Locale.ROOT) : "";

        var recipeManager = server.getRecipeManager();
        var allRecipes = recipeManager.getRecipes();

        for (Recipe<?> recipe : allRecipes) {
            // Limite de resultados para evitar crash de pacote de rede
            if (result.size() >= MAX_RESULTS) break;

            ResourceLocation recipeId = recipe.getId();
            RecipeType<?> recipeType = recipe.getType();

            // Filtro por mod (namespace)
            if (!modQuery.isEmpty()) {
                if (!recipeId.getNamespace().toLowerCase(Locale.ROOT).contains(modQuery)) {
                    continue;
                }
            }

            // Filtro por tipo de receita
            if (!typeQuery.isEmpty()) {
                ResourceLocation typeRl = ForgeRegistries.RECIPE_TYPES.getKey(recipeType);
                if (typeRl == null || !typeRl.getPath().toLowerCase(Locale.ROOT).contains(typeQuery)) {
                    continue;
                }
            }

            // Filtro por item resultado (MATCHING EXATO)
            if (!itemQuery.isEmpty()) {
                ItemStack resultStack = recipe.getResultItem(server.registryAccess());
                ResourceLocation resultItemRl = ForgeRegistries.ITEMS.getKey(resultStack.getItem());

                // Matching exato: ou pelo ID completo ou pelo nome traduzido exato
                boolean matchesItemId = resultItemRl != null && resultItemRl.toString().equals(itemQuery);
                boolean matchesItemName = resultStack.getHoverName().getString().equalsIgnoreCase(itemQuery);

                if (!matchesItemId && !matchesItemName) {
                    continue;
                }
            }

            // Receita passou em todos os filtros
            ResourceLocation typeRl = ForgeRegistries.RECIPE_TYPES.getKey(recipeType);
            if (typeRl == null) typeRl = new ResourceLocation("unknown", "unknown");

            ItemStack resultStack = recipe.getResultItem(server.registryAccess());
            result.add(new RecipeBasicDTO(recipeId, typeRl, resultStack));
        }

        return result;
    }
}
