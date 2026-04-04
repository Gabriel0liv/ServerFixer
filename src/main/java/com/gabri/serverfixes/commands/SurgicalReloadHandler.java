package com.gabri.serverfixes.commands;

import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerFunctionLibrary;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerPlayer;
import com.gabri.serverfixes.mixin.accessor.ServerFunctionManagerAccessor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.storage.loot.LootDataManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Lida com o Reload Cirúrgico de categorias específicas de datapacks.
 *
 * <p>Em vez de disparar o /reload global (que bloqueia o servidor e recarrega
 * modelos, biomas, texturas e centenas de outros listeners), este sistema
 * isola e chama apenas o PreparableReloadListener do gerenciador alvo.</p>
 *
 * <p>Arquitetura:</p>
 * <ul>
 *   <li>Preparation: executada em background thread (Util.backgroundExecutor)</li>
 *   <li>Apply: executada na main thread do servidor (server.execute)</li>
 *   <li>Após apply de receitas: obrigatoriamente sincroniza os clientes</li>
 * </ul>
 */
@SuppressWarnings("all")
public class SurgicalReloadHandler {

    private static final Logger LOGGER = LogManager.getLogger("ServerFixes/SurgicalReload");

    // ====================================================================
    // RECIPE RELOAD
    // ====================================================================

    /**
     * Recarrega apenas o RecipeManager e sincroniza com todos os clientes online.
     *
     * <p>O cliente PRECISA ser notificado das receitas para evitar desync no JEI
     * e no crafting. Após o reload, enviamos ClientboundUpdateRecipesPacket e
     * ClientboundUpdateTagsPacket para todos os jogadores conectados.</p>
     */
    public static CompletableFuture<Void> reloadRecipes(MinecraftServer server) {
        // RecipeManager é acessível diretamente via API pública do MinecraftServer.
        // Ele implementa PreparableReloadListener natively.
        RecipeManager recipeManager = server.getRecipeManager();

        LOGGER.info("[SurgicalReload] Iniciando reload de RECEITAS em background...");

        return reloadSingleListener(recipeManager, server)
            .thenRunAsync(() -> {
                LOGGER.info("[SurgicalReload] RecipeManager recarregado. Sincronizando clientes...");
                syncRecipesWithClients(server);
            }, server::execute);
    }

    // ====================================================================
    // LOOT TABLE RELOAD
    // ====================================================================

    /**
     * Recarrega apenas o LootDataManager.
     *
     * <p>Loot tables são 100% processadas no servidor. Não é necessário
     * sincronizar com o cliente. O reload é transparente durante o jogo.</p>
     */
    public static CompletableFuture<Void> reloadLootTables(MinecraftServer server) {
        // server.getLootData() retorna LootDataResolver (interface), mas a implementação
        // concreta em Forge 1.20.1 é sempre LootDataManager, que implementa
        // PreparableReloadListener. O cast é seguro no ambiente vanilla/Forge.
        LootDataManager lootDataManager = (LootDataManager) server.getLootData();

        LOGGER.info("[SurgicalReload] Iniciando reload de LOOT TABLES em background...");

        return reloadSingleListener(lootDataManager, server)
            .thenRunAsync(() -> {
                LOGGER.info("[SurgicalReload] LootDataManager recarregado com sucesso.");
            }, server::execute);
    }

    // ====================================================================
    // FUNCTION RELOAD
    // ====================================================================

    /**
     * Recarrega apenas a ServerFunctionLibrary (arquivos .mcfunction).
     *
     * <p>Functions são executadas unicamente no servidor. Não há sync de cliente
     * necessário. server.getFunctions() retorna a instância acessível via API
     * pública do MinecraftServer em Forge 1.20.</p>
     */
    public static CompletableFuture<Void> reloadFunctions(MinecraftServer server) {
        // ServerFunctionManager é o gestor de execução de functions, acessível via
        // server.getFunctions(). Mas o listener de reload é a ServerFunctionLibrary
        // interna (campo 'library'), extraída via Mixin Accessor.
        ServerFunctionManager functionManager = server.getFunctions();
        ServerFunctionLibrary functionLibrary =
                ((ServerFunctionManagerAccessor) functionManager).sf_getFunctionLibrary();

        LOGGER.info("[SurgicalReload] Iniciando reload de FUNCTIONS em background...");

        return reloadSingleListener(functionLibrary, server)
            .thenRunAsync(() -> {
                LOGGER.info("[SurgicalReload] ServerFunctionLibrary recarregado com sucesso.");
            }, server::execute);
    }

    // ====================================================================
    // MÉTODOS INTERNOS
    // ====================================================================

    /**
     * Executa o ciclo completo de reload de um único PreparableReloadListener.
     *
     * <p>Simula o comportamento interno do SimpleReloadInstance do Minecraft:
     * a barreira de preparação sincroniza as fases prepare (background) e
     * apply (main thread), garantindo thread-safety.</p>
     *
     * <p>Usa anonymous class para o barrier porque {@code wait()} é genérico
     * ({@code <T> CompletableFuture<T> wait(T)}), o que impede o uso de lambda.</p>
     *
     * @param listener O listener a ser recarregado isoladamente
     * @param server   A instância do MinecraftServer (fornece executors e ResourceManager)
     * @return CompletableFuture que completa na main thread após o apply
     */
    private static CompletableFuture<Void> reloadSingleListener(
            PreparableReloadListener listener,
            MinecraftServer server
    ) {
        ResourceManager resourceManager = server.getResourceManager();
        Executor prepareExecutor = net.minecraft.Util.backgroundExecutor();
        Executor applyExecutor = server::execute;

        // A "barreira de preparação" separa as fases prepare e apply.
        // Quando o listener chama barrier.wait(data), ele sinaliza que a
        // preparação de dados em background está completa. O apply só ocorre
        // depois que esse sinal é recebido pela main thread.
        //
        // Nota: anonymous class é obrigatória aqui. Um lambda não funciona porque
        // PreparationBarrier.wait() é um método genérico (<T> CompletableFuture<T> wait(T)),
        // e Java não permite lambdas para métodos genéricos em interfaces funcionais.
        PreparableReloadListener.PreparationBarrier barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override
            public <T> CompletableFuture<T> wait(T preparedData) {
                // Ao receber preparedData da fase prepare, agendamos o apply
                // na main thread do servidor e repassamos os dados preparados.
                return CompletableFuture.supplyAsync(() -> preparedData, applyExecutor);
            }
        };

        return listener.reload(
                barrier,
                resourceManager,
                InactiveProfiler.INSTANCE, // prepare profiler (desabilitado)
                InactiveProfiler.INSTANCE, // apply profiler (desabilitado)
                prepareExecutor,
                applyExecutor
        ).exceptionally(ex -> {
            LOGGER.error("[SurgicalReload] Falha durante o reload do listener '{}': {}",
                    listener.getClass().getSimpleName(), ex.getMessage(), ex);
            return null;
        });
    }

    /**
     * Envia pacotes de atualização de receitas e tags para todos os jogadores online.
     *
     * <p>OBRIGATÓRIO após reload de receitas. Sem isso, o cliente mantém o
     * estado antigo da RecipeBook e o JEI exibe "itens fantasmas" de receitas
     * removidas ou não mostra receitas novas adicionadas.</p>
     *
     * <p>Tags são enviadas junto para garantir integridade: muitas receitas
     * dependem de tags (ex: #minecraft:planks), e o JEI valida ambos.</p>
     */
    private static void syncRecipesWithClients(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        if (players.isEmpty()) {
            LOGGER.info("[SurgicalReload] Nenhum jogador online. Sync de receitas dispensado.");
            return;
        }

        // Pacote 1: Atualiza a lista completa de receitas no cliente
        ClientboundUpdateRecipesPacket recipesPacket =
                new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes());

        // Pacote 2: Atualiza as tags de todos os registries no cliente.
        // serializeTagsToNetwork espera LayeredRegistryAccess<RegistryLayer>,
        // que é obtido via server.registries() (NÃO server.registryAccess()).
        ClientboundUpdateTagsPacket tagsPacket =
                new ClientboundUpdateTagsPacket(
                        TagNetworkSerialization.serializeTagsToNetwork(server.registries())
                );

        for (ServerPlayer player : players) {
            player.connection.send(recipesPacket);
            player.connection.send(tagsPacket);
            LOGGER.debug("[SurgicalReload] Sync enviado para: {}", player.getName().getString());
        }

        LOGGER.info("[SurgicalReload] Receitas e tags sincronizadas com {} jogador(es).", players.size());
    }
}
