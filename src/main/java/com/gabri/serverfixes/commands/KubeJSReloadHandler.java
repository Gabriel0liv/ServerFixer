package com.gabri.serverfixes.commands;

import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Handler para reload cirúrgico dos scripts do KubeJS.
 *
 * <p>Re-executa apenas os scripts de {@code server_scripts/} do KubeJS
 * sem acionar o /reload vanilla completo. Após o reload, sincroniza
 * receitas e tags com todos os clientes online.</p>
 *
 * <p>Compatível com KubeJS Forge 2001.6.x (1.20.1). Usa reflection
 * para acessar {@code ScriptManager.reload(ScriptType.SERVER)}, garantindo
 * que o mod funcione mesmo sem o KubeJS instalado.</p>
 */
@SuppressWarnings("all")
public class KubeJSReloadHandler {

    private static final Logger LOGGER = LogManager.getLogger("ServerFixes/KubeJSReload");

    // Classes internas do KubeJS 2001.6.x (1.20.1 Forge)
    private static final String KUBEJS_SCRIPT_MANAGER = "dev.latvian.mods.kubejs.script.ScriptManager";
    private static final String KUBEJS_SCRIPT_TYPE = "dev.latvian.mods.kubejs.script.ScriptType";
    private static final String KUBEJS_KUBEJS = "dev.latvian.mods.kubejs.KubeJS";

    /**
     * Verifica se o KubeJS está carregado no runtime.
     */
    public static boolean isKubeJSLoaded() {
        return ModList.get().isLoaded("kubejs");
    }

    /**
     * Recarrega os server_scripts do KubeJS e sincroniza com os clientes.
     *
     * <p>Fluxo:</p>
     * <ol>
     *   <li>Verifica se KubeJS está presente</li>
     *   <li>Acessa {@code ScriptManager.INSTANCE.reload(ScriptType.SERVER)}</li>
     *   <li>Re-executa todos os scripts do {@code server_scripts/}</li>
     *   <li>Sincroniza recipes e tags com todos os clients online</li>
     * </ol>
     *
     * @param server Instância do MinecraftServer
     * @return CompletableFuture que completa quando o reload termina
     */
    public static CompletableFuture<Void> reloadKubeJS(MinecraftServer server) {
        if (!isKubeJSLoaded()) {
            LOGGER.error("[KubeJSReload] KubeJS não está instalado! Não é possível recarregar os scripts.");
            return CompletableFuture.failedFuture(
                    new IllegalStateException("KubeJS não está instalado. Adicione o mod KubeJS para usar este comando.")
            );
        }

        LOGGER.info("[KubeJSReload] Iniciando reload dos server_scripts do KubeJS...");

        return CompletableFuture.runAsync(() -> {
            try {
                invokeKubeJSReload();
            } catch (Exception e) {
                LOGGER.error("[KubeJSReload] Falha ao recarregar scripts do KubeJS:", e);
                throw new RuntimeException("Falha no reload do KubeJS: " + e.getMessage(), e);
            }
        }, net.minecraft.Util.backgroundExecutor()).thenRunAsync(() -> {
            LOGGER.info("[KubeJSReload] Scripts do KubeJS recarregados. Sincronizando clientes...");
            syncRecipesWithClients(server);
        }, server::execute);
    }

    /**
     * Invoca {@code ScriptManager.INSTANCE.reload(ScriptType.SERVER)} via reflection.
     *
     * <p>Na versão 2001.6.5-build.16 (1.20.1 Forge), o ScriptManager fica em:
     * {@code dev.latvian.mods.kubejs.script.ScriptManager}</p>
     *
     * <p>O método {@code reload(ScriptType)} está na linha 72 do JAR.</p>
     */
    private static void invokeKubeJSReload() throws Exception {
        // 1. Acessa ScriptManager.INSTANCE
        Class<?> scriptManagerClass = Class.forName(KUBEJS_SCRIPT_MANAGER);
        java.lang.reflect.Field instanceField = scriptManagerClass.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        Object scriptManagerInstance = instanceField.get(null);

        // 2. Obtém o enum ScriptType.SERVER
        Class<?> scriptTypeClass = Class.forName(KUBEJS_SCRIPT_TYPE);
        Object scriptTypeServer = Enum.valueOf((Class<Enum>) scriptTypeClass, "SERVER");

        // 3. Encontra e chama reload(ScriptType)
        Method reloadMethod = scriptManagerClass.getMethod("reload", scriptTypeClass);

        LOGGER.info("[KubeJSReload] Chamando ScriptManager.INSTANCE.reload(ScriptType.SERVER)...");

        reloadMethod.invoke(scriptManagerInstance, scriptTypeServer);

        LOGGER.info("[KubeJSReload] reload(ScriptType.SERVER) concluído com sucesso.");
    }

    /**
     * Envia pacotes de atualização de receitas e tags para todos os jogadores online.
     *
     * <p>OBRIGATÓRIO após reload de recipes. Sem isso, o cliente mantém o
     * estado antigo da RecipeBook e o JEI exibe "itens fantasmas".</p>
     */
    private static void syncRecipesWithClients(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        if (players.isEmpty()) {
            LOGGER.info("[KubeJSReload] Nenhum jogador online. Sync de receitas dispensado.");
            return;
        }

        // Pacote 1: Atualiza a lista completa de receitas no cliente
        ClientboundUpdateRecipesPacket recipesPacket =
                new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes());

        // Pacote 2: Atualiza as tags de todos os registries no cliente
        ClientboundUpdateTagsPacket tagsPacket =
                new ClientboundUpdateTagsPacket(
                        TagNetworkSerialization.serializeTagsToNetwork(server.registries())
                );

        for (ServerPlayer player : players) {
            player.connection.send(recipesPacket);
            player.connection.send(tagsPacket);
            LOGGER.debug("[KubeJSReload] Sync enviado para: {}", player.getName().getString());
        }

        LOGGER.info("[KubeJSReload] Receitas e tags sincronizadas com {} jogador(es).", players.size());
    }
}
