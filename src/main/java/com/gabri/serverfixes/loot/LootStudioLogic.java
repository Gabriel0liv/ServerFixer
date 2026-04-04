package com.gabri.serverfixes.loot;

import com.gabri.serverfixes.client.gui.LootDropDTO;
import com.gabri.serverfixes.commands.SurgicalReloadHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("all")
public final class LootStudioLogic {
    private static final Logger LOGGER = LogManager.getLogger("ServerFixes/LootStudioLogic");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private LootStudioLogic() {
    }

    public static List<ResourceLocation> listLootTableIds(MinecraftServer server) {
        Set<ResourceLocation> result = new HashSet<>();
        Object lootData = server.getLootData();

        try {
            Method getKeys = lootData.getClass().getMethod("getKeys");
            collectIds(getKeys.invoke(lootData), result);
        } catch (Throwable ignored) {
        }

        try {
            Method getKeysTyped = lootData.getClass().getMethod("getKeys", LootDataType.class);
            collectIds(getKeysTyped.invoke(lootData, LootDataType.TABLE), result);
        } catch (Throwable ignored) {
        }

        // Fallback: algumas implementacoes retornam mapas internos ao inves de colecoes diretas.
        for (Field field : lootData.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                collectIds(field.get(lootData), result);
            } catch (Throwable ignored) {
            }
        }

        if (result.isEmpty()) {
            LOGGER.warn("[LootStudio] Nao foi possivel obter IDs via getKeys(). Lista de loot tables vazia.");
        }

        List<ResourceLocation> sorted = new ArrayList<>(result);
        sorted.removeIf(Objects::isNull);
        sorted.sort(Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        return sorted;
    }

    public static List<LootDropDTO> parseLootTable(MinecraftServer server, ResourceLocation tableId) {
        List<LootDropDTO> drops = new ArrayList<>();
        if (tableId == null) {
            return drops;
        }

        LootTable table;
        try {
            table = server.getLootData().getLootTable(tableId);
        } catch (Throwable t) {
            LOGGER.warn("[LootStudio] Falha ao obter loot table em RAM: {}", tableId, t);
            return drops;
        }

        if (table == null || table == LootTable.EMPTY) {
            return drops;
        }

        List<Object> pools = extractListField(table, "pool", "LootPool");
        if (pools.isEmpty()) {
            return drops;
        }

        for (Object pool : pools) {
            if (pool == null) {
                continue;
            }

            List<Object> entries = extractListField(pool, "entr", "LootPoolEntryContainer");
            if (entries.isEmpty()) {
                continue;
            }

            List<Object> poolConditions = extractListField(pool, "condition", "LootItemCondition");
            List<Object> poolFunctions = extractListField(pool, "function", "LootItemFunction");

            boolean poolRequirePlayerKill = hasConditionLike(poolConditions, "killedbyplayer");
            boolean poolAffectedByLooting = hasConditionLike(poolConditions, "looting");

            int totalSimpleWeight = 0;
            for (Object entry : entries) {
                totalSimpleWeight += Math.max(1, extractWeight(entry));
            }
            if (totalSimpleWeight <= 0) {
                totalSimpleWeight = 1;
            }

            for (Object entry : entries) {
                LootDropDTO parsed = parseEntry(entry, poolConditions, poolFunctions, poolRequirePlayerKill, poolAffectedByLooting, totalSimpleWeight);
                if (parsed != null) {
                    drops.add(parsed);
                }
            }
        }

        return drops;
    }

    public static CompletableFuture<Boolean> saveToDatapack(MinecraftServer server, ResourceLocation tableId, List<LootDropDTO> rawDrops) {
        if (tableId == null) {
            return CompletableFuture.completedFuture(false);
        }

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
        if (tableId == null) {
            return CompletableFuture.completedFuture(false);
        }

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

    private static LootDropDTO parseEntry(
        Object entry,
        List<Object> poolConditions,
        List<Object> poolFunctions,
        boolean poolRequirePlayerKill,
        boolean poolAffectedByLooting,
        int totalSimpleWeight
    ) {
        if (entry == null) {
            return null;
        }

        Item item = extractItemFromEntry(entry);
        List<Object> entryConditions = extractListField(entry, "condition", "LootItemCondition");
        List<Object> entryFunctions = extractListField(entry, "function", "LootItemFunction");

        boolean requirePlayerKill = poolRequirePlayerKill || hasConditionLike(entryConditions, "killedbyplayer");
        boolean affectedByLooting = poolAffectedByLooting || hasConditionLike(entryConditions, "looting");

        boolean unknownCondition = hasUnknownCondition(poolConditions) || hasUnknownCondition(entryConditions);

        int weight = Math.max(1, extractWeight(entry));
        double chance = Math.max(0.0D, Math.min(100.0D, (weight * 100.0D) / Math.max(1, totalSimpleWeight)));

        Range countRange = extractCountRange(entryFunctions);
        if (countRange == null) {
            countRange = extractCountRange(poolFunctions);
        }

        int min = countRange != null ? countRange.min : 1;
        int max = countRange != null ? countRange.max : 1;

        boolean unsupportedEntryType = isReferenceLikeEntry(entry);
        boolean unknownFunction = hasUnknownFunction(entryFunctions) || hasUnknownFunction(poolFunctions);
        boolean complex = item == null || unknownCondition || unknownFunction || unsupportedEntryType;

        ItemStack stack;
        if (item != null) {
            stack = new ItemStack(item, Math.max(1, min));
        } else {
            stack = new ItemStack(unsupportedEntryType ? net.minecraft.world.item.Items.CHEST : net.minecraft.world.item.Items.BARRIER);
        }
        return new LootDropDTO(stack, chance, min, max, requirePlayerKill, affectedByLooting, complex);
    }

    private static List<LootDropDTO> sanitizeDrops(List<LootDropDTO> input) {
        List<LootDropDTO> out = new ArrayList<>();
        if (input == null) {
            return out;
        }

        int limit = Math.min(input.size(), 512);
        for (int i = 0; i < limit; i++) {
            LootDropDTO dto = input.get(i);
            if (dto == null || dto.isComplex() || dto.getItem() == null || dto.getItem().isEmpty()) {
                continue;
            }

            LootDropDTO normalized = new LootDropDTO(
                dto.getItem().copy(),
                clamp(dto.getChance(), 0.0D, 100.0D),
                Math.max(1, Math.min(64, dto.getMin())),
                Math.max(1, Math.min(64, dto.getMax())),
                dto.isRequirePlayerKill(),
                dto.isAffectedByLooting(),
                false
            );

            if (normalized.getMax() < normalized.getMin()) {
                normalized.setMax(normalized.getMin());
            }

            out.add(normalized);
        }
        return out;
    }

    private static JsonObject buildLootTableJson(List<LootDropDTO> drops) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:generic");

        JsonArray poolsArray = new JsonArray();
        for (LootDropDTO dto : drops) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(dto.getItem().getItem());
            if (itemId == null) {
                continue;
            }

            JsonObject pool = new JsonObject();
            pool.addProperty("rolls", 1);

            JsonArray entries = new JsonArray();
            JsonObject entry = new JsonObject();
            entry.addProperty("type", "minecraft:item");
            entry.addProperty("name", itemId.toString());
            entries.add(entry);
            pool.add("entries", entries);

            JsonArray functions = new JsonArray();
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
            pool.add("functions", functions);

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

            if (!conditions.isEmpty()) {
                pool.add("conditions", conditions);
            }

            poolsArray.add(pool);
        }

        root.add("pools", poolsArray);
        return root;
    }

    private static void ensurePackMcmeta(Path datapackRoot) throws Exception {
        Path mcmeta = datapackRoot.resolve("pack.mcmeta");
        if (Files.exists(mcmeta)) {
            return;
        }

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
            .resolve("data")
            .resolve(id.getNamespace())
            .resolve("loot_tables")
            .resolve(id.getPath() + ".json");
    }

    private static void collectIds(Object source, Set<ResourceLocation> out) {
        if (source == null || out == null) {
            return;
        }

        if (source instanceof ResourceLocation rl) {
            out.add(rl);
            return;
        }

        if (source instanceof java.util.Map<?, ?> map) {
            for (Object key : map.keySet()) {
                collectIds(key, out);
            }
            for (Object value : map.values()) {
                collectIds(value, out);
            }
            return;
        }

        if (source instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectIds(element, out);
            }
            return;
        }

        if (source.getClass().isArray()) {
            int len = Array.getLength(source);
            for (int i = 0; i < len; i++) {
                collectIds(Array.get(source, i), out);
            }
            return;
        }

        ResourceLocation id = resolveIdLike(source);
        if (id != null) {
            out.add(id);
        }
    }

    private static ResourceLocation resolveIdLike(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ResourceLocation rl) {
            return rl;
        }

        for (String methodName : new String[]{"location", "id"}) {
            try {
                Method m = value.getClass().getMethod(methodName);
                Object out = m.invoke(value);
                ResourceLocation resolved = resolveIdLike(out);
                if (resolved != null) return resolved;
            } catch (Throwable ignored) {
            }
        }

        if (value instanceof CharSequence charSequence) {
            try {
                return ResourceLocation.tryParse(charSequence.toString());
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static List<Object> extractListField(Object holder, String fieldNameHint, String elementTypeHint) {
        List<Object> empty = List.of();
        if (holder == null) {
            return empty;
        }

        for (Class<?> type = holder.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(holder);
                    if (value == null) {
                        continue;
                    }

                    if (value instanceof List<?> list) {
                        String fieldName = field.getName().toLowerCase(Locale.ROOT);
                        if (fieldNameHint != null && !fieldNameHint.isBlank() && fieldName.contains(fieldNameHint.toLowerCase(Locale.ROOT))) {
                            return new ArrayList<>(list);
                        }

                        Object sample = firstNonNull(list);
                        if (sample != null && matchesTypeHint(sample.getClass(), elementTypeHint)) {
                            return new ArrayList<>(list);
                        }

                        if (sample == null && fieldNameHint != null && !fieldNameHint.isBlank()) {
                            if (fieldName.contains(fieldNameHint.toLowerCase(Locale.ROOT))) {
                                return new ArrayList<>(list);
                            }
                        }
                    }

                    if (value.getClass().isArray()) {
                        int len = Array.getLength(value);
                        List<Object> arrayAsList = new ArrayList<>(len);
                        for (int i = 0; i < len; i++) {
                            Object element = Array.get(value, i);
                            arrayAsList.add(element);
                        }

                        Object sample = firstNonNull(arrayAsList);
                        if (sample != null && matchesTypeHint(sample.getClass(), elementTypeHint)) {
                            return arrayAsList;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return empty;
    }

    private static Item extractItemFromEntry(Object entry) {
        if (entry == null) {
            return null;
        }

        // Primeiro tenta por metodo publico comum em wrappers/holders.
        for (String methodName : new String[]{"getItem", "item", "value"}) {
            try {
                Method method = entry.getClass().getMethod(methodName);
                Object out = method.invoke(entry);
                Item resolved = resolveItemFromUnknown(out);
                if (resolved != null) {
                    return resolved;
                }
            } catch (Throwable ignored) {
            }
        }

        for (Class<?> type = entry.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(entry);
                    Item resolved = resolveItemFromUnknown(value);
                    if (resolved != null) {
                        return resolved;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }

    private static Item resolveItemFromUnknown(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Item item) {
            return item;
        }
        if (value instanceof ItemStack stack && !stack.isEmpty()) {
            return stack.getItem();
        }

        try {
            if (value instanceof net.minecraft.world.level.ItemLike itemLike) {
                return itemLike.asItem();
            }
        } catch (Throwable ignored) {
        }

        // Holder<T> costuma expor value().
        for (String mName : new String[]{"value", "get"}) {
            try {
                Method method = value.getClass().getMethod(mName);
                Object out = method.invoke(value);
                if (out != value) {
                    Item nested = resolveItemFromUnknown(out);
                    if (nested != null) {
                        return nested;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static int extractWeight(Object entry) {
        if (entry == null) {
            return 1;
        }

        try {
            Method getWeight = entry.getClass().getMethod("getWeight", float.class);
            Object value = getWeight.invoke(entry, 0.0F);
            if (value instanceof Integer i) {
                return Math.max(1, i);
            }
        } catch (Throwable ignored) {
        }

        for (Class<?> type = entry.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    if (field.getType() != int.class) {
                        continue;
                    }
                    field.setAccessible(true);
                    int value = field.getInt(entry);
                    String name = field.getName().toLowerCase(Locale.ROOT);
                    if (name.contains("weight")) {
                        return Math.max(1, value);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return 1;
    }

    private static boolean hasConditionLike(Collection<?> conditions, String marker) {
        if (conditions == null || marker == null) {
            return false;
        }
        String needle = marker.toLowerCase(Locale.ROOT);
        for (Object condition : conditions) {
            if (condition == null) {
                continue;
            }
            String name = condition.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (name.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnknownCondition(Collection<?> conditions) {
        if (conditions == null) {
            return false;
        }

        for (Object condition : conditions) {
            if (condition == null) {
                continue;
            }
            String n = condition.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            boolean known = n.contains("killedbyplayer")
                || n.contains("randomchance")
                || n.contains("looting")
                || n.contains("bonuslevel")
                || n.contains("inverted");
            if (!known) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnknownFunction(Collection<?> functions) {
        if (functions == null) {
            return false;
        }

        for (Object function : functions) {
            if (function == null) {
                continue;
            }
            String n = function.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (!n.contains("setitemcount") && !n.contains("setcount")) {
                return true;
            }
        }

        return false;
    }

    private static Range extractCountRange(Collection<?> functions) {
        if (functions == null) {
            return null;
        }

        for (Object function : functions) {
            if (function == null) {
                continue;
            }

            String n = function.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (!n.contains("setitemcount") && !n.contains("setcount")) {
                continue;
            }

            Object provider = findFirstFieldValue(function, "net.minecraft.world.level.storage.loot.providers.number.NumberProvider");
            if (provider == null) {
                continue;
            }

            Range r = parseNumberProvider(provider);
            if (r != null) {
                return r;
            }
        }

        return null;
    }

    private static boolean isReferenceLikeEntry(Object entry) {
        if (entry == null) {
            return false;
        }
        String n = entry.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return n.contains("tag")
            || n.contains("table")
            || n.contains("reference")
            || n.contains("alternatives")
            || n.contains("sequence")
            || n.contains("group")
            || n.contains("dynamic");
    }

    private static Object firstNonNull(Collection<?> values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean matchesTypeHint(Class<?> type, String typeHint) {
        if (type == null || typeHint == null || typeHint.isBlank()) {
            return false;
        }
        return type.getName().contains(typeHint)
            || type.getSimpleName().contains(typeHint);
    }

    private static Range parseNumberProvider(Object provider) {
        if (provider == null) {
            return null;
        }

        String n = provider.getClass().getSimpleName().toLowerCase(Locale.ROOT);

        if (n.contains("constant")) {
            Double value = extractFirstNumber(provider);
            if (value != null) {
                int v = Math.max(1, (int) Math.round(value));
                return new Range(v, v);
            }
        }

        if (n.contains("uniform")) {
            List<Object> nestedProviders = findFieldsByType(provider, "net.minecraft.world.level.storage.loot.providers.number.NumberProvider");
            if (nestedProviders.size() >= 2) {
                Range minRange = parseNumberProvider(nestedProviders.get(0));
                Range maxRange = parseNumberProvider(nestedProviders.get(1));
                if (minRange != null && maxRange != null) {
                    return new Range(Math.max(1, minRange.min), Math.max(1, maxRange.max));
                }
            }
        }

        Double fallback = extractFirstNumber(provider);
        if (fallback != null) {
            int v = Math.max(1, (int) Math.round(fallback));
            return new Range(v, v);
        }

        return null;
    }

    private static Object findFirstFieldValue(Object holder, String typeName) {
        if (holder == null) {
            return null;
        }

        for (Field field : holder.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object val = field.get(holder);
                if (val != null && (val.getClass().getName().equals(typeName) || typeName.endsWith(val.getClass().getSimpleName()))) {
                    return val;
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static List<Object> findFieldsByType(Object holder, String typeName) {
        List<Object> out = new ArrayList<>();
        if (holder == null) {
            return out;
        }

        for (Field field : holder.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object val = field.get(holder);
                if (val != null && (val.getClass().getName().equals(typeName) || typeName.endsWith(val.getClass().getSimpleName()))) {
                    out.add(val);
                }
            } catch (Throwable ignored) {
            }
        }

        return out;
    }

    private static Double extractFirstNumber(Object holder) {
        if (holder == null) {
            return null;
        }

        for (Field field : holder.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Class<?> type = field.getType();
                if (type == float.class || type == Float.class) {
                    return (double) field.getFloat(holder);
                }
                if (type == double.class || type == Double.class) {
                    return field.getDouble(holder);
                }
                if (type == int.class || type == Integer.class) {
                    return (double) field.getInt(holder);
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Range(int min, int max) {
    }
}
