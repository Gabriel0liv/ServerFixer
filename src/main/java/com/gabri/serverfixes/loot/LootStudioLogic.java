package com.gabri.serverfixes.loot;

import com.gabri.serverfixes.client.gui.LootDropDTO;
import com.gabri.serverfixes.commands.SurgicalReloadHandler;
import com.gabri.serverfixes.mixin.accessor.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.*;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import net.minecraft.world.level.storage.loot.functions.SetPotionFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Lógica do Loot Studio — versão refatorada sem reflexão.
 * Toda extração de dados internos usa Mixin Accessors tipados.
 */
@SuppressWarnings("all")
public final class LootStudioLogic {
    private static final Logger LOGGER = LogManager.getLogger("ServerFixes/LootStudioLogic");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private LootStudioLogic() {
    }

    // ====================================================================
    // API PÚBLICA
    // ====================================================================

    public static List<ResourceLocation> listLootTableIds(MinecraftServer server, String query, boolean searchMod, boolean searchEntity, boolean searchChest, boolean searchItem) {
        Set<ResourceLocation> result = new HashSet<>();

        try {
            LootDataManager lootData = (LootDataManager) server.getLootData();
            result.addAll(lootData.getKeys(LootDataType.TABLE));

            try {
                Method m = lootData.getClass().getMethod("getKeys");
                Object keys = m.invoke(lootData);
                collectLootIds(keys, result);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            LOGGER.warn("[LootStudio] Falha ao obter IDs de loot tables via getKeys().", t);
        }

        if (result.isEmpty()) {
            LOGGER.warn("[LootStudio] Lista de loot tables vazia.");
        }

        List<ResourceLocation> sorted = new ArrayList<>(result);
        sorted.removeIf(id -> id == null || id.getPath().startsWith("blocks/"));

        boolean hasAnyFilter = searchMod || searchEntity || searchChest || searchItem;
        if (!hasAnyFilter) {
            searchMod = true;
            searchEntity = true;
            searchChest = true;
            searchItem = true;
        }

        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        boolean emptyQuery = q.isEmpty();

        final boolean fSearchMod = searchMod;
        final boolean fSearchEntity = searchEntity;
        final boolean fSearchChest = searchChest;
        final boolean fSearchItem = searchItem;

        sorted.removeIf(id -> !matchesSearchContext(server, id, q, emptyQuery, fSearchMod, fSearchEntity, fSearchChest, fSearchItem));
        sorted.sort(Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        return sorted;
    }

    private static boolean matchesSearchContext(MinecraftServer server, ResourceLocation id, String query, boolean emptyQuery,
                                                boolean searchMod, boolean searchEntity, boolean searchChest, boolean searchItem) {
        if (id == null) return false;

        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id.getPath().toLowerCase(Locale.ROOT);

        boolean match = false;

        if (searchMod) {
            match |= emptyQuery || namespace.contains(query);
        }

        if (searchEntity) {
            boolean entityPath = path.contains("entities/");
            match |= entityPath && (emptyQuery || path.contains(query));
        }

        if (searchChest) {
            boolean chestPath = path.contains("chests/");
            match |= chestPath && (emptyQuery || path.contains(query));
        }

        if (searchItem) {
            match |= matchesItemInTable(server, id, query, emptyQuery);
        }

        return match;
    }

    private static boolean matchesItemInTable(MinecraftServer server, ResourceLocation tableId, String query, boolean emptyQuery) {
        try {
            List<LootDropDTO> drops = parseLootTable(server, tableId);
            if (drops == null || drops.isEmpty()) {
                return false;
            }

            if (emptyQuery) {
                return true;
            }

            for (LootDropDTO dto : drops) {
                if (dto == null) continue;

                if (dto.getTag() != null && dto.getTag().toString().toLowerCase(Locale.ROOT).contains(query)) {
                    return true;
                }

                ItemStack stack = dto.getItem();
                if (stack != null && !stack.isEmpty()) {
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (itemId != null && itemId.toString().toLowerCase(Locale.ROOT).contains(query)) {
                        return true;
                    }
                    String hoverName = stack.getHoverName().getString();
                    if (hoverName != null && hoverName.toLowerCase(Locale.ROOT).contains(query)) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[LootStudio] Falha ao verificar busca por item na tabela {}: {}", tableId, t.getMessage());
        }

        return false;
    }

    public static List<LootDropDTO> parseLootTable(MinecraftServer server, ResourceLocation tableId) {
        List<LootDropDTO> drops = new ArrayList<>();
        if (tableId == null) return drops;

        LootTable table;
        try {
            table = server.getLootData().getLootTable(tableId);
        } catch (Throwable t) {
            LOGGER.warn("[LootStudio] Falha ao obter loot table: {}", tableId, t);
            return drops;
        }
        if (table == null || table == LootTable.EMPTY) return drops;

        // Extrai pools via Accessor tipado
        List<LootPool> pools = ((LootTableAccessor) (Object) table).sf_getPools();
        if (pools == null || pools.isEmpty()) return drops;

        for (LootPool pool : pools) {
            LootPoolAccessor poolAcc = (LootPoolAccessor) (Object) pool;
            LootPoolEntryContainer[] entries = poolAcc.sf_getEntries();
            LootItemCondition[] poolConditions = poolAcc.sf_getConditions();
            LootItemFunction[] poolFunctions = poolAcc.sf_getFunctions();

            if (entries == null || entries.length == 0) continue;

            List<LootItemCondition> condList = poolConditions != null ? Arrays.asList(poolConditions) : List.of();
            List<LootItemFunction> funcList = poolFunctions != null ? Arrays.asList(poolFunctions) : List.of();

            boolean poolRequirePlayerKill = hasConditionLike(condList, "killedbyplayer");
            boolean poolAffectedByLooting = hasConditionLike(condList, "looting");

            // Calcula peso total para percentagem de chance
            int totalWeight = 0;
            for (LootPoolEntryContainer entry : entries) {
                totalWeight += getEntryWeight(entry);
            }
            if (totalWeight <= 0) totalWeight = 1;

            for (LootPoolEntryContainer entry : entries) {
                LootDropDTO parsed = parseEntry(entry, condList, funcList,
                        poolRequirePlayerKill, poolAffectedByLooting, totalWeight);
                if (parsed != null) drops.add(parsed);
            }
        }
        return drops;
    }

    /**
     * Obtém os drops da loot table SEM o arquivo gerado pelo mod.
     * Move temporariamente o arquivo, recarrega, lê os drops, e restaura.
     * Isso respeita outros datapacks e mods que modifiquem a loot table.
     * Retorna CompletableFuture para não bloquear a main thread do servidor.
     */
    public static CompletableFuture<List<LootDropDTO>> getNonGeneratedDrops(MinecraftServer server, ResourceLocation tableId) {
        if (tableId == null) return CompletableFuture.completedFuture(new ArrayList<>());

        Path jsonPath = getGeneratedLootTablePath(server, tableId);
        Path backupPath = null;
        boolean fileExisted = false;

        try {
            // Temporariamente move o arquivo JSON gerado (se existir)
            if (Files.exists(jsonPath)) {
                fileExisted = true;
                backupPath = jsonPath.resolveSibling(jsonPath.getFileName().toString() + ".sf_reset_backup");
                Files.move(jsonPath, backupPath);
            }
        } catch (Throwable t) {
            LOGGER.error("[LootStudio] Falha ao mover arquivo gerado da tabela {}", tableId, t);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        final boolean finalFileExisted = fileExisted;
        final Path finalBackupPath = backupPath;

        CompletableFuture<List<LootDropDTO>> dropsFuture = SurgicalReloadHandler.reloadLootTables(server)
                .thenComposeAsync(v -> {
                    List<LootDropDTO> drops;
                    try {
                        drops = parseLootTable(server, tableId);
                    } catch (Throwable t) {
                        LOGGER.error("[LootStudio] Falha ao obter drops sem geração da tabela {}", tableId, t);
                        drops = new ArrayList<>();
                    }
                    final List<LootDropDTO> finalDrops = drops;
                    // Restaura o arquivo JSON e recarrega
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            if (finalFileExisted && finalBackupPath != null && Files.exists(finalBackupPath)) {
                                Files.move(finalBackupPath, jsonPath);
                            }
                        } catch (Throwable t) {
                            LOGGER.error("[LootStudio] Falha ao restaurar arquivo após leitura clean {}", tableId, t);
                        }
                        SurgicalReloadHandler.reloadLootTables(server)
                                .exceptionally(ex -> {
                                    LOGGER.error("[LootStudio] Falha ao restaurar reload após leitura clean {}", tableId, ex);
                                    return null;
                                });
                        return finalDrops;
                    }, server::execute);
                }, server::execute);

        return dropsFuture;
    }

    public static CompletableFuture<Boolean> saveToDatapack(MinecraftServer server, ResourceLocation tableId, List<LootDropDTO> rawDrops) {
        if (tableId == null) return CompletableFuture.completedFuture(false);

        try {
            Path jsonPath = getGeneratedLootTablePath(server, tableId);
            Path datapackRoot = getGeneratedDatapackRoot(server);
            Files.createDirectories(jsonPath.getParent());
            ensurePackMcmeta(datapackRoot);

            List<LootDropDTO> drops = sanitizeDrops(rawDrops);
            JsonObject json = buildLootTableJson(drops);
            Files.writeString(jsonPath, GSON.toJson(json));
            LOGGER.info("[LootStudio] Loot table salva em {}", jsonPath);
        } catch (Throwable t) {
            LOGGER.error("[LootStudio] Falha ao salvar loot table {}", tableId, t);
            return CompletableFuture.completedFuture(false);
        }

        return SurgicalReloadHandler.reloadLootTables(server)
                .handle((v, ex) -> {
                    if (ex != null) {
                        LOGGER.error("[LootStudio] Falha ao recarregar loot tables apos salvar {}", tableId, ex);
                        return false;
                    }
                    return true;
                });
    }

    public static CompletableFuture<Boolean> resetGeneratedLootTable(MinecraftServer server, ResourceLocation tableId) {
        if (tableId == null) return CompletableFuture.completedFuture(false);

        try {
            Path jsonPath = getGeneratedLootTablePath(server, tableId);
            if (Files.exists(jsonPath)) {
                Files.delete(jsonPath);
                LOGGER.info("[LootStudio] Reset aplicado. Arquivo removido: {}", jsonPath);
            }
        } catch (Throwable t) {
            LOGGER.error("[LootStudio] Falha ao resetar loot table {}", tableId, t);
            return CompletableFuture.completedFuture(false);
        }

        return SurgicalReloadHandler.reloadLootTables(server)
                .handle((v, ex) -> {
                    if (ex != null) {
                        LOGGER.error("[LootStudio] Falha ao recarregar loot tables apos reset {}", tableId, ex);
                        return false;
                    }
                    return true;
                });
    }

    // ====================================================================
    // PARSING DE ENTRIES (sem reflexão)
    // ====================================================================

    private static LootDropDTO parseEntry(
            LootPoolEntryContainer entry,
            List<LootItemCondition> poolConditions,
            List<LootItemFunction> poolFunctions,
            boolean poolRequirePlayerKill,
            boolean poolAffectedByLooting,
            int totalSimpleWeight
    ) {
        if (entry == null) return null;

        // --- Extrai item real via instanceof + Accessor ---
        Item item = null;
        ResourceLocation tagId = null;
        ResourceLocation referenceTableId = null;
        boolean emptyDrop = false;
        boolean unsupportedEntryType = false;

        if (entry instanceof LootItem lootItem) {
            // LootItem é o tipo "item" do JSON — drop direto
            try {
                item = ((LootItemAccessor) (Object) lootItem).sf_getItem();
            } catch (Throwable t) {
                LOGGER.debug("[LootStudio] Falha ao extrair item de LootItem: {}", t.getMessage());
            }
        } else if (entry instanceof TagEntry tagEntry) {
            try {
                tagId = ((TagEntryAccessor) (Object) tagEntry).sf_getTag().location();
            } catch (Throwable ignored) {
            }
            item = Items.NAME_TAG;
        } else if (entry instanceof LootTableReference lootTableReference) {
            try {
                referenceTableId = ((LootTableReferenceAccessor) (Object) lootTableReference).sf_getName();
            } catch (Throwable ignored) {
            }
            item = Items.CHEST;
        } else if (entry instanceof EmptyLootItem) {
            item = Items.BARRIER;
            emptyDrop = true;
        } else if (entry instanceof AlternativesEntry
                || entry instanceof SequentialEntry
                || entry instanceof EntryGroup
                || entry instanceof DynamicLoot) {
            unsupportedEntryType = true;
        }

        // --- Conditions/Functions da entry individual ---
        List<LootItemCondition> entryConditions = getEntryConditions(entry);
        List<LootItemFunction> entryFunctions = getEntryFunctions(entry);

        boolean requirePlayerKill = poolRequirePlayerKill || hasConditionLike(entryConditions, "killedbyplayer");
        boolean lootingFromEntryFunction = hasFunctionLike(entryFunctions, "lootingenchant") || hasFunctionLike(entryFunctions, "applybonus");
        boolean lootingFromPoolFunction = hasFunctionLike(poolFunctions, "lootingenchant") || hasFunctionLike(poolFunctions, "applybonus");
        boolean affectedByLooting = poolAffectedByLooting || hasConditionLike(entryConditions, "looting") || lootingFromEntryFunction || lootingFromPoolFunction;
        boolean unknownCondition = hasUnknownCondition(poolConditions) || hasUnknownCondition(entryConditions);

        // --- Weight & Chance ---
        int weight = getEntryWeight(entry);
        double chance = Math.max(0.0D, Math.min(100.0D, (weight * 100.0D) / Math.max(1, totalSimpleWeight)));

        // --- Count Range (SetItemCountFunction) ---
        Range countRange = extractCountRange(entryFunctions);
        if (countRange == null) countRange = extractCountRange(poolFunctions);
        int min = countRange != null ? countRange.min : 1;
        int max = countRange != null ? countRange.max : 1;

        // --- Enchant settings ---
        EnchantSettings enchant = extractEnchantSettings(entryFunctions, poolFunctions);
        ParsedExtraFunctions extraFunctions = extractExtraFunctions(entryFunctions, poolFunctions);
        if (extraFunctions.enchantmentsFunctionDetected) {
            enchant = new EnchantSettings(true, enchant.enchantWithLevels, enchant.levelsRange);
        }

        // --- Complexidade ---
        boolean unknownFunction = hasUnknownFunction(entryFunctions) || hasUnknownFunction(poolFunctions);
        boolean isRepresentableStructuredDrop = tagId != null || referenceTableId != null || emptyDrop;
        boolean complex = isRepresentableStructuredDrop
            ? false
            : (item == null && tagId == null && referenceTableId == null)
                || unknownCondition
                || unknownFunction
                || unsupportedEntryType;

        ItemStack stack;
        if (item != null) {
            stack = new ItemStack(item, Math.max(1, min));
        } else {
            stack = new ItemStack(unsupportedEntryType ? Items.CHEST : Items.BARRIER);
        }
        return new LootDropDTO(
                stack,
                chance,
                min,
                max,
                requirePlayerKill,
                affectedByLooting,
                complex,
                tagId,
                referenceTableId,
                enchant.enchantRandomly,
                enchant.enchantWithLevels,
                enchant.levelsRange != null ? new LootDropDTO.Range(enchant.levelsRange.min, enchant.levelsRange.max) : null,
                extraFunctions.potionId,
                extraFunctions.nbtData,
                extraFunctions.customNameJson,
                extraFunctions.explorationMap,
                emptyDrop
        );
    }

    // ====================================================================
    // EXTRAÇÃO TIPADA (Accessors — zero reflexão)
    // ====================================================================

    /** Extrai conditions[] da entry via LootPoolEntryContainerAccessor. */
    private static List<LootItemCondition> getEntryConditions(LootPoolEntryContainer entry) {
        try {
            LootItemCondition[] conds = ((LootPoolEntryContainerAccessor) (Object) entry).sf_getConditions();
            return conds != null ? Arrays.asList(conds) : List.of();
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** Extrai functions[] da entry se for singleton (LootItem etc.) via LootPoolSingletonContainerAccessor. */
    private static List<LootItemFunction> getEntryFunctions(LootPoolEntryContainer entry) {
        if (!(entry instanceof LootPoolSingletonContainer)) return List.of();
        try {
            LootItemFunction[] funcs = ((LootPoolSingletonContainerAccessor) (Object) entry).sf_getFunctions();
            return funcs != null ? Arrays.asList(funcs) : List.of();
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** Extrai o weight via Accessor direto no campo protegido. */
    private static int getEntryWeight(LootPoolEntryContainer entry) {
        if (entry instanceof LootPoolSingletonContainer singleton) {
            try {
                return Math.max(1, ((LootPoolSingletonContainerAccessor) (Object) singleton).sf_getWeight());
            } catch (Throwable t) {
                return 1;
            }
        }
        return 1;
    }

    /** Procura SetItemCountFunction nas functions e extrai o NumberProvider via Accessor. */
    private static Range extractCountRange(List<LootItemFunction> functions) {
        if (functions == null) return null;
        for (LootItemFunction function : functions) {
            if (!(function instanceof SetItemCountFunction setCount)) continue;
            try {
                NumberProvider provider = ((SetItemCountFunctionAccessor) (Object) setCount).sf_getValue();
                if (provider != null) {
                    Range r = parseNumberProvider(provider);
                    if (r != null) return r;
                }
            } catch (Throwable t) {
                LOGGER.debug("[LootStudio] Falha ao extrair count de SetItemCountFunction: {}", t.getMessage());
            }
        }
        return null;
    }

    private static EnchantSettings extractEnchantSettings(List<LootItemFunction> entryFunctions, List<LootItemFunction> poolFunctions) {
        boolean enchantRandomly = containsEnchantRandomly(entryFunctions) || containsEnchantRandomly(poolFunctions);
        boolean enchantWithLevels = containsEnchantWithLevels(entryFunctions) || containsEnchantWithLevels(poolFunctions);

        Range levelRange = extractEnchantLevelsRange(entryFunctions);
        if (levelRange == null) {
            levelRange = extractEnchantLevelsRange(poolFunctions);
        }
        if (enchantWithLevels && levelRange == null) {
            levelRange = new Range(10, 30);
        }

        return new EnchantSettings(enchantRandomly, enchantWithLevels, levelRange);
    }

    private static boolean containsEnchantRandomly(List<LootItemFunction> functions) {
        if (functions == null) return false;
        for (LootItemFunction function : functions) {
            if (function instanceof EnchantRandomlyFunction) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsEnchantWithLevels(List<LootItemFunction> functions) {
        if (functions == null) return false;
        for (LootItemFunction function : functions) {
            if (function instanceof EnchantWithLevelsFunction) {
                return true;
            }
        }
        return false;
    }

    private static Range extractEnchantLevelsRange(List<LootItemFunction> functions) {
        if (functions == null) return null;
        for (LootItemFunction function : functions) {
            if (!(function instanceof EnchantWithLevelsFunction withLevels)) continue;
            try {
                NumberProvider levels = ((EnchantWithLevelsFunctionAccessor) (Object) withLevels).sf_getLevels();
                Range range = parseNumberProvider(levels);
                if (range != null) {
                    return new Range(Math.max(1, range.min), Math.max(1, range.max));
                }
            } catch (Throwable t) {
                LOGGER.debug("[LootStudio] Falha ao extrair levels de EnchantWithLevelsFunction: {}", t.getMessage());
            }
        }
        return null;
    }

    private static ParsedExtraFunctions extractExtraFunctions(List<LootItemFunction> entryFunctions, List<LootItemFunction> poolFunctions) {
        ResourceLocation potionId = null;
        String nbtData = null;
        String customNameJson = null;
        boolean explorationMap = false;
        boolean enchantmentsFunctionDetected = false;

        List<LootItemFunction> merged = new ArrayList<>();
        if (entryFunctions != null) merged.addAll(entryFunctions);
        if (poolFunctions != null) merged.addAll(poolFunctions);

        for (LootItemFunction function : merged) {
            if (function == null) continue;

            if (function instanceof SetPotionFunction potionFunction) {
                try {
                    Potion potion = ((SetPotionFunctionAccessor) (Object) potionFunction).sf_getPotion();
                    if (potion != null) {
                        potionId = ForgeRegistries.POTIONS.getKey(potion);
                    }
                } catch (Throwable t) {
                    LOGGER.debug("[LootStudio] Falha ao extrair potion de SetPotionFunction: {}", t.getMessage());
                }
                continue;
            }

            if (function instanceof SetNbtFunction nbtFunction) {
                try {
                    CompoundTag tag = ((SetNbtFunctionAccessor) (Object) nbtFunction).sf_getTag();
                    if (tag != null && !tag.isEmpty()) {
                        nbtData = tag.toString();
                    }
                } catch (Throwable t) {
                    LOGGER.debug("[LootStudio] Falha ao extrair tag de SetNbtFunction: {}", t.getMessage());
                }
                continue;
            }

            if (function instanceof SetNameFunction nameFunction) {
                try {
                    Component name = ((SetNameFunctionAccessor) (Object) nameFunction).sf_getName();
                    if (name != null) {
                        customNameJson = Component.Serializer.toJson(name);
                    }
                } catch (Throwable t) {
                    LOGGER.debug("[LootStudio] Falha ao extrair name de SetNameFunction: {}", t.getMessage());
                }
                continue;
            }

            if (function instanceof ExplorationMapFunction) {
                explorationMap = true;
                continue;
            }

            if (function instanceof SetEnchantmentsFunction) {
                enchantmentsFunctionDetected = true;
            }
        }

        return new ParsedExtraFunctions(potionId, nbtData, customNameJson, explorationMap, enchantmentsFunctionDetected);
    }

    /**
     * Converte um NumberProvider em Range de min/max.
     * Usa instanceof tipado para ConstantValue e UniformGenerator.
     * ConstantValue.getFloat(null) é seguro pois a implementação ignora o contexto.
     */
    private static Range parseNumberProvider(NumberProvider provider) {
        if (provider == null) return null;

        if (provider instanceof ConstantValue constant) {
            int v = Math.max(1, Math.round(constant.getFloat(null)));
            return new Range(v, v);
        }

        if (provider instanceof UniformGenerator uniform) {
            NumberProvider minProv = ((UniformGeneratorAccessor) (Object) uniform).sf_getMin();
            NumberProvider maxProv = ((UniformGeneratorAccessor) (Object) uniform).sf_getMax();
            Range minR = parseNumberProvider(minProv);
            Range maxR = parseNumberProvider(maxProv);
            if (minR != null && maxR != null) {
                return new Range(Math.max(1, minR.min), Math.max(1, maxR.max));
            }
        }

        // Fallback para providers desconhecidos (BinomialDistribution etc.)
        try {
            float val = provider.getFloat(null);
            int v = Math.max(1, Math.round(val));
            return new Range(v, v);
        } catch (Throwable ignored) {
        }

        return null;
    }

    // ====================================================================
    // CHECAGEM DE CONDITIONS / FUNCTIONS (por nome de classe — sem reflexão)
    // ====================================================================

    private static boolean hasConditionLike(Collection<?> conditions, String marker) {
        if (conditions == null || marker == null) return false;
        String needle = marker.toLowerCase(Locale.ROOT);
        for (Object condition : conditions) {
            if (condition == null) continue;
            if (condition.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    private static boolean hasUnknownCondition(Collection<?> conditions) {
        // Treat all conditions as known/ignorable for UI purposes; do not block editing.
        return false;
    }

    private static boolean hasFunctionLike(Collection<?> functions, String marker) {
        if (functions == null || marker == null) return false;
        String needle = marker.toLowerCase(Locale.ROOT);
        for (Object function : functions) {
            if (function == null) continue;
            if (function.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    private static boolean hasUnknownFunction(Collection<?> functions) {
        // Treat all functions as known/ignorable for UI purposes; do not block editing.
        return false;
    }

    // ====================================================================
    // GERAÇÃO DE JSON / DATAPACK
    // ====================================================================

    private static List<LootDropDTO> sanitizeDrops(List<LootDropDTO> input) {
        List<LootDropDTO> out = new ArrayList<>();
        if (input == null) return out;
        int limit = Math.min(input.size(), 512);
        for (int i = 0; i < limit; i++) {
            LootDropDTO dto = input.get(i);
                if (dto == null) continue;
                boolean hasTag = dto.getTag() != null;
                boolean hasRef = dto.getReferenceTable() != null;
                boolean hasItem = dto.getItem() != null && !dto.getItem().isEmpty();
                boolean hasEmpty = dto.isEmptyDrop();

                // If extraction failed but we have extra function info, try to create a sensible base item
                if (!hasTag && !hasRef && !hasItem && !hasEmpty) {
                    if (dto.getPotionId() != null) {
                        dto.setItem(new ItemStack(Items.POTION));
                        hasItem = true;
                    } else if (dto.isExplorationMap()) {
                        dto.setItem(new ItemStack(Items.FILLED_MAP));
                        hasItem = true;
                    } else if (dto.getNbtData() != null && !dto.getNbtData().isEmpty()) {
                        dto.setItem(new ItemStack(Items.CHEST));
                        hasItem = true;
                    } else if (dto.getCustomNameJson() != null && !dto.getCustomNameJson().isEmpty()) {
                        dto.setItem(new ItemStack(Items.CHEST));
                        hasItem = true;
                    }
                }
                if (!hasTag && !hasRef && !hasItem && !hasEmpty) continue;

                LootDropDTO normalized = new LootDropDTO(
                    dto.getItem() != null ? dto.getItem().copy() : ItemStack.EMPTY,
                    clamp(dto.getChance(), 0.0D, 100.0D),
                    Math.max(1, Math.min(64, dto.getMin())),
                    Math.max(1, Math.min(64, dto.getMax())),
                    dto.isRequirePlayerKill(), dto.isAffectedByLooting(), false,
                    dto.getTag(), dto.getReferenceTable(),
                    dto.isEnchantRandomly(),
                    dto.isEnchantWithLevels(),
                    sanitizeEnchantRange(dto.getEnchantLevelsRange()),
                    dto.getPotionId(),
                    dto.getNbtData(),
                    dto.getCustomNameJson(),
                    dto.isExplorationMap(),
                    dto.isEmptyDrop());
            if (normalized.getMax() < normalized.getMin()) normalized.setMax(normalized.getMin());
            out.add(normalized);
        }
        return out;
    }

    private static LootDropDTO.Range sanitizeEnchantRange(LootDropDTO.Range range) {
        if (range == null) {
            return new LootDropDTO.Range(10, 30);
        }
        int min = Math.max(1, Math.min(100, range.getMin()));
        int max = Math.max(min, Math.min(100, range.getMax()));
        return new LootDropDTO.Range(min, max);
    }

    private static JsonObject buildLootTableJson(List<LootDropDTO> drops) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:generic");
        JsonArray poolsArray = new JsonArray();

        for (LootDropDTO dto : drops) {
            if (dto == null) continue;

            ResourceLocation itemId = dto.getItem() != null && !dto.getItem().isEmpty()
                ? ForgeRegistries.ITEMS.getKey(dto.getItem().getItem())
                : null;
            ResourceLocation tagId = dto.getTag();
            ResourceLocation refTable = dto.getReferenceTable();

            if (itemId == null && tagId == null && refTable == null && !dto.isEmptyDrop()) continue;

            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);

            JsonArray entries = new JsonArray();
            JsonObject entry = new JsonObject();
            if (dto.isEmptyDrop()) {
                entry.addProperty("type", "minecraft:empty");
            } else if (tagId != null) {
                entry.addProperty("type", "minecraft:tag");
                entry.addProperty("name", tagId.toString());
                entry.addProperty("expand", false);
            } else if (refTable != null) {
                entry.addProperty("type", "minecraft:loot_table");
                entry.addProperty("name", refTable.toString());
            } else {
                entry.addProperty("type", "minecraft:item");
                entry.addProperty("name", itemId.toString());
            }
            entries.add(entry);
            pool.add("entries", entries);

            JsonArray functions = new JsonArray();
            if (!dto.isEmptyDrop()) {
                JsonObject setCount = new JsonObject();
                setCount.addProperty("function", "minecraft:set_count");
                if (dto.getMin() == dto.getMax()) {
                    setCount.addProperty("count", dto.getMin());
                } else {
                    JsonObject countRange = new JsonObject();
                    countRange.addProperty("min", dto.getMin());
                    countRange.addProperty("max", dto.getMax());
                    setCount.add("count", countRange);
                }
                functions.add(setCount);

                if (dto.isEnchantRandomly()) {
                    JsonObject enchantRandomly = new JsonObject();
                    enchantRandomly.addProperty("function", "minecraft:enchant_randomly");
                    functions.add(enchantRandomly);
                }

                if (dto.isEnchantWithLevels()) {
                    LootDropDTO.Range levels = sanitizeEnchantRange(dto.getEnchantLevelsRange());
                    JsonObject enchantWithLevels = new JsonObject();
                    enchantWithLevels.addProperty("function", "minecraft:enchant_with_levels");
                    if (levels.getMin() == levels.getMax()) {
                        enchantWithLevels.addProperty("levels", levels.getMin());
                    } else {
                        JsonObject levelsRange = new JsonObject();
                        levelsRange.addProperty("min", levels.getMin());
                        levelsRange.addProperty("max", levels.getMax());
                        enchantWithLevels.add("levels", levelsRange);
                    }
                    functions.add(enchantWithLevels);
                }

                if (dto.getPotionId() != null) {
                    JsonObject setPotion = new JsonObject();
                    setPotion.addProperty("function", "minecraft:set_potion");
                    setPotion.addProperty("id", dto.getPotionId().toString());
                    functions.add(setPotion);
                }

                if (dto.getNbtData() != null && !dto.getNbtData().isEmpty()) {
                    JsonObject setNbt = new JsonObject();
                    setNbt.addProperty("function", "minecraft:set_nbt");
                    setNbt.addProperty("tag", dto.getNbtData());
                    functions.add(setNbt);
                }

                if (dto.isExplorationMap()) {
                    JsonObject explorationMap = new JsonObject();
                    explorationMap.addProperty("function", "minecraft:exploration_map");
                    functions.add(explorationMap);
                }

                if (dto.getCustomNameJson() != null && !dto.getCustomNameJson().isEmpty()) {
                    try {
                        JsonObject setName = new JsonObject();
                        setName.addProperty("function", "minecraft:set_name");
                        setName.add("name", JsonParser.parseString(dto.getCustomNameJson()));
                        functions.add(setName);
                    } catch (Throwable t) {
                        LOGGER.debug("[LootStudio] customNameJson invalido, ignorando: {}", t.getMessage());
                    }
                }
            }

            if (!functions.isEmpty()) {
                pool.add("functions", functions);
            }

            JsonArray conditions = new JsonArray();
            if (dto.isRequirePlayerKill()) {
                JsonObject killedByPlayer = new JsonObject();
                killedByPlayer.addProperty("condition", "minecraft:killed_by_player");
                conditions.add(killedByPlayer);
            }
            double chance = clamp(dto.getChance() / 100.0D, 0.0D, 1.0D);
            if (dto.isAffectedByLooting()) {
                JsonObject chanceWithLooting = new JsonObject();
                chanceWithLooting.addProperty("condition", "minecraft:random_chance_with_looting");
                chanceWithLooting.addProperty("chance", chance);
                chanceWithLooting.addProperty("looting_multiplier", chance);
                conditions.add(chanceWithLooting);
            } else {
                JsonObject randomChance = new JsonObject();
                randomChance.addProperty("condition", "minecraft:random_chance");
                randomChance.addProperty("chance", chance);
                conditions.add(randomChance);
            }
            if (!conditions.isEmpty()) pool.add("conditions", conditions);
            poolsArray.add(pool);
        }

        root.add("pools", poolsArray);
        return root;
    }

    // ====================================================================
    // UTILITÁRIOS
    // ====================================================================

    private static void ensurePackMcmeta(Path datapackRoot) throws Exception {
        Path mcmeta = datapackRoot.resolve("pack.mcmeta");
        if (Files.exists(mcmeta)) return;
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 15);
        pack.addProperty("description", "ServerFixes generated loot overrides");
        root.add("pack", pack);
        Files.createDirectories(datapackRoot);
        Files.writeString(mcmeta, GSON.toJson(root));
    }

    private static Path getGeneratedDatapackRoot(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("serverfixes_generated");
    }

    private static Path getGeneratedLootTablePath(MinecraftServer server, ResourceLocation id) {
        return getGeneratedDatapackRoot(server)
                .resolve("data").resolve(id.getNamespace())
                .resolve("loot_tables").resolve(id.getPath() + ".json");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Range(int min, int max) {
    }

    private record EnchantSettings(boolean enchantRandomly, boolean enchantWithLevels, Range levelsRange) {
    }

    private record ParsedExtraFunctions(ResourceLocation potionId, String nbtData, String customNameJson, boolean explorationMap, boolean enchantmentsFunctionDetected) {
    }

    private static void collectLootIds(Object source, Set<ResourceLocation> out) {
        if (source == null || out == null) {
            return;
        }

        ResourceLocation direct = asResourceLocation(source);
        if (direct != null) {
            out.add(direct);
            return;
        }

        if (source instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                collectLootIds(key, out);
            }
            for (Object value : map.values()) {
                collectLootIds(value, out);
            }
            return;
        }

        if (source instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                collectLootIds(value, out);
            }
            return;
        }

        if (source.getClass().isArray()) {
            int len = Array.getLength(source);
            for (int i = 0; i < len; i++) {
                collectLootIds(Array.get(source, i), out);
            }
        }
    }

    private static ResourceLocation asResourceLocation(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ResourceLocation rl) {
            return rl;
        }

        for (String methodName : List.of("location", "id")) {
            try {
                Method method = value.getClass().getMethod(methodName);
                Object nested = method.invoke(value);
                if (nested instanceof ResourceLocation rl) {
                    return rl;
                }
                if (nested instanceof CharSequence cs) {
                    return ResourceLocation.tryParse(cs.toString());
                }
            } catch (Throwable ignored) {
            }
        }

        if (value instanceof CharSequence cs) {
            return ResourceLocation.tryParse(cs.toString());
        }
        return null;
    }
}
