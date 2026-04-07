package com.gabri.serverfixes.function;

import com.gabri.serverfixes.client.gui.FunctionEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Motor de busca e manipulação de funções para o Function Studio.
 * Escaneia arquivos .mcfunction no disco (datapacks).
 */
public class FunctionStudioLogic {

    private static final int MAX_RESULTS = 200;

    /**
     * Retorna uma lista filtrada de funções disponíveis no servidor.
     * Escaneia datapacks ativos e datapack custom do ServerFixes.
     */
    public static List<FunctionEntry> getFilteredFunctions(MinecraftServer server, String namespaceFilter, String pathFilter) {
        List<FunctionEntry> result = new ArrayList<>();
        String nsQuery = namespaceFilter != null ? namespaceFilter.trim().toLowerCase(Locale.ROOT) : "";
        String pathQuery = pathFilter != null ? pathFilter.trim().toLowerCase(Locale.ROOT) : "";

        Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR);

        // 1. Escanear datapack custom do ServerFixes primeiro
        scanDirectoryForFunctions(result, datapackDir.resolve("serverfixes_functions").resolve("data"), nsQuery, pathQuery, "custom", MAX_RESULTS);

        // 2. Escanear datapacks ativos
        if (result.size() < MAX_RESULTS) {
            scanDirectoryForFunctions(result, datapackDir.resolve("datapacks"), nsQuery, pathQuery, "datapack", MAX_RESULTS - result.size());
        }

        return result;
    }

    /**
     * Lê o conteúdo de uma função .mcfunction do disco.
     */
    public static List<String> readFunctionContent(MinecraftServer server, ResourceLocation functionId) {
        Path functionPath = findFunctionFile(server, functionId);
        if (functionPath == null || !Files.exists(functionPath)) {
            return List.of("# Function not found on disk: " + functionId, "# It may be embedded in a mod JAR.");
        }

        try {
            return Files.readAllLines(functionPath);
        } catch (IOException e) {
            return List.of("# Error reading function: " + e.getMessage());
        }
    }

    /**
     * Salva o conteúdo de uma função no datapack custom do servidor.
     */
    public static boolean saveFunction(MinecraftServer server, ResourceLocation functionId, List<String> lines) {
        try {
            Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR);
            Path functionPath = datapackDir
                    .resolve("serverfixes_functions")
                    .resolve("data")
                    .resolve(functionId.getNamespace())
                    .resolve("functions")
                    .resolve(functionId.getPath() + ".mcfunction");

            Files.createDirectories(functionPath.getParent());
            ensurePackMcmeta(datapackDir.resolve("serverfixes_functions"));
            Files.writeString(functionPath, String.join("\n", lines));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Deleta uma função customizada.
     */
    public static boolean deleteFunction(MinecraftServer server, ResourceLocation functionId) {
        try {
            Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR);
            Path functionPath = datapackDir
                    .resolve("serverfixes_functions")
                    .resolve("data")
                    .resolve(functionId.getNamespace())
                    .resolve("functions")
                    .resolve(functionId.getPath() + ".mcfunction");

            if (!Files.exists(functionPath)) return false;
            Files.delete(functionPath);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void scanDirectoryForFunctions(List<FunctionEntry> result, Path rootDir, String nsQuery, String pathQuery, String source, int maxResults) {
        if (!Files.exists(rootDir)) return;

        try (Stream<Path> stream = Files.walk(rootDir, 8)) {
            stream.filter(p -> p.toString().endsWith(".mcfunction"))
                  .limit(maxResults)
                  .forEach(path -> {
                      if (result.size() >= maxResults) return;

                      // Extrair namespace e path do arquivo
                      String relative = rootDir.relativize(path).toString().replace("\\", "/");
                      String[] parts = relative.split("/", 3);
                      if (parts.length < 3) return;

                      String namespace = parts[0];
                      String functionPath = parts[2].replace(".mcfunction", "");

                      // Filtros
                      if (!nsQuery.isEmpty() && !namespace.contains(nsQuery)) return;
                      if (!pathQuery.isEmpty() && !functionPath.contains(pathQuery)) return;

                      // Contar linhas
                      int lineCount = 0;
                      try {
                          lineCount = (int) Files.lines(path).count();
                      } catch (IOException ignored) {
                      }

                      ResourceLocation id = new ResourceLocation(namespace, functionPath);
                      result.add(new FunctionEntry(id, namespace, functionPath, lineCount, source));
                  });
        } catch (IOException ignored) {
        }
    }

    private static Path findFunctionFile(MinecraftServer server, ResourceLocation functionId) {
        Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR);

        // 1. Verificar datapack custom primeiro
        Path customPath = datapackDir
                .resolve("serverfixes_functions")
                .resolve("data")
                .resolve(functionId.getNamespace())
                .resolve("functions")
                .resolve(functionId.getPath() + ".mcfunction");

        if (Files.exists(customPath)) return customPath;

        // 2. Verificar datapacks ativos
        try (Stream<Path> stream = Files.walk(datapackDir.resolve("datapacks"), 6)) {
            Optional<Path> found = stream
                    .filter(p -> p.toString().endsWith(".mcfunction"))
                    .filter(p -> {
                        String relative = datapackDir.relativize(p).toString();
                        return relative.contains(functionId.getNamespace()) && relative.endsWith(functionId.getPath() + ".mcfunction");
                    })
                    .findFirst();
            return found.orElse(null);
        } catch (IOException ignored) {
        }

        return null;
    }

    private static void ensurePackMcmeta(Path datapackRoot) {
        Path packMcmeta = datapackRoot.resolve("pack.mcmeta");
        if (Files.exists(packMcmeta)) return;
        try {
            String content = "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"ServerFixes Generated Functions\"\n  }\n}";
            Files.writeString(packMcmeta, content);
        } catch (IOException ignored) {
        }
    }
}
