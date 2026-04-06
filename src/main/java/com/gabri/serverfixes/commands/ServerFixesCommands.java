package com.gabri.serverfixes.commands;

import com.gabri.serverfixes.config.ServerFixesConfig;
import com.gabri.serverfixes.events.AntiSwapExploitHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("all")
public class ServerFixesCommands {

    private static final Logger LOGGER = LogManager.getLogger("ServerFixes/Commands");
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext buildContext) {
        // Nível 3 = Admin. Corresponde ao @a[level=3] no Arclight/Spigot.
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("serverfixes")
            .requires(source -> source.hasPermission(3));

        // /serverfixes status ...
        root.then(Commands.literal("status")
            .executes(ctx -> showStatus(ctx.getSource(), null))
            .then(Commands.literal("villagers").executes(ctx -> showStatus(ctx.getSource(), "villagers")))
            .then(Commands.literal("antiswap").executes(ctx -> showStatus(ctx.getSource(), "antiswap")))
            .then(Commands.literal("infinitetrade").executes(ctx -> showStatus(ctx.getSource(), "infinitetrade")))
            .then(Commands.literal("debug").executes(ctx -> showStatus(ctx.getSource(), "debug")))
            .then(Commands.literal("throttle").executes(ctx -> showStatus(ctx.getSource(), "throttle"))));

        // /serverfixes antiswap <bool> | reset
        root.then(Commands.literal("antiswap")
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(ctx -> {
                    boolean value = BoolArgumentType.getBool(ctx, "enabled");
                    ServerFixesConfig.ENABLE_ANTI_SWAP.set(value);
                    ctx.getSource().sendSuccess(() -> Component.literal("Anti-Swap Exploit protection: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.valueOf(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                    return 1;
                }))
            .then(Commands.literal("reset")
                .executes(ctx -> {
                    AntiSwapExploitHandler.resetAll();
                    ctx.getSource().sendSuccess(() -> Component.literal("Anti-Swap cache cleared.").withStyle(ChatFormatting.GREEN), true);
                    return 1;
                })));

        // /serverfixes villagerticks <num>
        root.then(Commands.literal("villagerticks")
            .then(Commands.argument("num", IntegerArgumentType.integer(1, 100))
                .executes(ctx -> {
                    int value = IntegerArgumentType.getInteger(ctx, "num");
                    ServerFixesConfig.VILLAGER_TICK_RATE.set(value);
                    ctx.getSource().sendSuccess(() -> Component.literal("Villager Tick Rate set to: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.GOLD)), true);
                    return 1;
                })));

        // /serverfixes infinitetrade <bool> | tag <string>
        root.then(Commands.literal("infinitetrade")
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(ctx -> {
                    boolean value = BoolArgumentType.getBool(ctx, "enabled");
                    ServerFixesConfig.ENABLE_INFINITE_TRADES.set(value);
                    ctx.getSource().sendSuccess(() -> Component.literal("Infinite Trades system: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.valueOf(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                    return 1;
                }))
            .then(Commands.literal("tag")
                .then(Commands.argument("tagName", StringArgumentType.string())
                    .executes(ctx -> {
                        String value = StringArgumentType.getString(ctx, "tagName");
                        ServerFixesConfig.INFINITE_TRADE_TAG.set(value);
                        ctx.getSource().sendSuccess(() -> Component.literal("Infinite Trade Tag set to: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(value).withStyle(ChatFormatting.GOLD)), true);
                        return 1;
                    }))));

        // /serverfixes debug damage received <bool> | damage dealt <bool> | damage breakdown <bool> | villagers <bool> | antiswap <bool>
        root.then(Commands.literal("debug")
            .then(Commands.literal("damage")
                .then(Commands.literal("received")
                    .then(Commands.argument("val", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean val = BoolArgumentType.getBool(ctx, "val");
                            if (ctx.getSource().getEntity() instanceof Player player) {
                                if (val) player.addTag("sf_debug_damage_received");
                                else player.removeTag("sf_debug_damage_received");
                                ctx.getSource().sendSuccess(() -> Component.literal("Personal Damage Received Debug: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                            }
                            return 1;
                        })))
                .then(Commands.literal("dealt")
                    .then(Commands.argument("val", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean val = BoolArgumentType.getBool(ctx, "val");
                            if (ctx.getSource().getEntity() instanceof Player player) {
                                if (val) player.addTag("sf_debug_damage_dealt");
                                else player.removeTag("sf_debug_damage_dealt");
                                ctx.getSource().sendSuccess(() -> Component.literal("Personal Damage Dealt Debug: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                            }
                            return 1;
                        })))
                .then(Commands.literal("breakdown")
                    .then(Commands.argument("val", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean val = BoolArgumentType.getBool(ctx, "val");
                            ServerFixesConfig.DEBUG_DAMAGE_BREAKDOWN.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Damage Breakdown Debug: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                            return 1;
                        }))))
            .then(Commands.literal("villagers")
                .then(Commands.argument("val", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean val = BoolArgumentType.getBool(ctx, "val");
                        if (ctx.getSource().getEntity() instanceof Player player) {
                            if (val) player.addTag("sf_debug_villagers");
                            else player.removeTag("sf_debug_villagers");
                            ctx.getSource().sendSuccess(() -> Component.literal("Personal Villager Debug: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                        }
                        return 1;
                    })))
            .then(Commands.literal("antiswap")
                .then(Commands.argument("val", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean val = BoolArgumentType.getBool(ctx, "val");
                        if (ctx.getSource().getEntity() instanceof Player player) {
                            if (val) player.addTag("sf_debug_antiswap");
                            else player.removeTag("sf_debug_antiswap");
                            ctx.getSource().sendSuccess(() -> Component.literal("Personal Anti-Swap Debug: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                        }
                        return 1;
                    }))));

        // /serverfixes throttle ...
        root.then(Commands.literal("throttle")
            .then(Commands.literal("farmandcharm")
                .then(Commands.argument("val", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean val = BoolArgumentType.getBool(ctx, "val");
                        ServerFixesConfig.THROTTLE_FARM_AND_CHARM.set(val);
                        ctx.getSource().sendSuccess(() -> Component.literal("Farm & Charm Throttling: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                        return 1;
                    }))
                .then(Commands.literal("cookingpot")
                    .then(Commands.argument("rate", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> {
                            int val = IntegerArgumentType.getInteger(ctx, "rate");
                            ServerFixesConfig.COOKING_POT_TICK_RATE.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Cooking Pot Rate: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                            return 1;
                        })))
                .then(Commands.literal("roaster")
                    .then(Commands.argument("rate", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> {
                            int val = IntegerArgumentType.getInteger(ctx, "rate");
                            ServerFixesConfig.ROASTER_TICK_RATE.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Roaster Rate: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                            return 1;
                        }))))
            .then(Commands.literal("vinery")
                .then(Commands.argument("val", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean val = BoolArgumentType.getBool(ctx, "val");
                        ServerFixesConfig.THROTTLE_VINERY.set(val);
                        ctx.getSource().sendSuccess(() -> Component.literal("Vinery Throttling: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                        return 1;
                    }))
                .then(Commands.literal("barrel")
                    .then(Commands.argument("rate", IntegerArgumentType.integer(1, 1000))
                        .executes(ctx -> {
                            int val = IntegerArgumentType.getInteger(ctx, "rate");
                            ServerFixesConfig.FERMENTATION_BARREL_TICK_RATE.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Vinery Barrel Rate: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                            return 1;
                        }))))
        );

        // /serverfixes fix backstabbing | turtle ...
        root.then(Commands.literal("fix")
            .then(Commands.literal("backstabbing")
                .then(Commands.argument("val", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean val = BoolArgumentType.getBool(ctx, "val");
                        ServerFixesConfig.FIX_BACKSTABBING_EXPLOIT.set(val);
                        ctx.getSource().sendSuccess(() -> Component.literal("Fix Backstabbing Exploit: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                        return 1;
                    })))
            .then(Commands.literal("turtle")
                .then(Commands.literal("enabled")
                    .then(Commands.argument("val", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean val = BoolArgumentType.getBool(ctx, "val");
                            ServerFixesConfig.NERF_TURTLE_MASTER.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Nerf Turtle Master: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                            return 1;
                        })))
                .then(Commands.literal("res")
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> {
                            int val = IntegerArgumentType.getInteger(ctx, "level");
                            ServerFixesConfig.TURTLE_MASTER_RESISTANCE.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Turtle Master Resistance (Standard): ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                            return 1;
                        })))
                .then(Commands.literal("slow")
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> {
                            int val = IntegerArgumentType.getInteger(ctx, "level");
                            ServerFixesConfig.TURTLE_MASTER_SLOWNESS.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Turtle Master Slowness (Standard): ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                            return 1;
                        })))
                .then(Commands.literal("strong_res")
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> {
                            int val = IntegerArgumentType.getInteger(ctx, "level");
                            ServerFixesConfig.STRONG_TURTLE_MASTER_RESISTANCE.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Turtle Master Resistance (Strong): ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                            return 1;
                        })))
                .then(Commands.literal("strong_slow")
                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> {
                            int val = IntegerArgumentType.getInteger(ctx, "level");
                            ServerFixesConfig.STRONG_TURTLE_MASTER_SLOWNESS.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Turtle Master Slowness (Strong): ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                            return 1;
                        }))))
            .then(Commands.literal("effectids")
                .then(Commands.argument("val", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean val = BoolArgumentType.getBool(ctx, "val");
                        ServerFixesConfig.ENABLE_STRING_EFFECT_IDS.set(val);
                        ctx.getSource().sendSuccess(() -> Component.literal("String Effect IDs support: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                        return 1;
                    })))
        );

        // /serverfixes item ...
        ItemCommands.register(root, buildContext);
        ItemAttributeCommands.register(root, buildContext);
        ItemEffectModifierCommands.register(root, buildContext);

        // /serverfixes effect ...
        EffectCommands.register(root, buildContext);

        // ================================================================
        // /serverfixes reload <categoria>
        // Reload Cirúrgico: recarrega apenas a categoria alvo do datapack
        // sem acionar o /reload global e sem travar o servidor.
        // ================================================================
        root.then(Commands.literal("reload")
            .then(Commands.literal("recipes")
                .executes(ctx -> executeReload(ctx.getSource(), "recipes")))
            .then(Commands.literal("loot")
                .executes(ctx -> executeReload(ctx.getSource(), "loot")))
            .then(Commands.literal("functions")
                .executes(ctx -> executeReload(ctx.getSource(), "functions"))));

        // /serverfixes admin_ui
        root.then(Commands.literal("admin_ui")
            .executes(ctx -> {
                if (ctx.getSource().getEntity() instanceof Player player) {
                    com.gabri.serverfixes.network.NetworkHandler.sendToPlayer((net.minecraft.server.level.ServerPlayer) player, 
                        new com.gabri.serverfixes.network.SyncServerConfigPacket(com.gabri.serverfixes.network.SyncServerConfigPacket.getCurrentConfig()));
                }
                return 1;
            }));

        // /serverfixes particle_studio
        root.then(Commands.literal("particle_studio")
            .executes(ctx -> openParticleStudio(ctx.getSource())));

        // /serverfixes sound_studio
        root.then(Commands.literal("sound_studio")
            .executes(ctx -> openSoundStudio(ctx.getSource())));

        // /serverfixes loot_studio
        root.then(Commands.literal("loot_studio")
            .executes(ctx -> openLootStudio(ctx.getSource())));

        // /serverfixes tag_studio
        root.then(Commands.literal("tag_studio")
            .executes(ctx -> openTagStudio(ctx.getSource())));

        LiteralCommandNode<CommandSourceStack> baseNode = dispatcher.register(root);

        // Alias /sfx -> redireciona para /serverfixes sem duplicar a árvore
        dispatcher.register(
            Commands.literal("sfx")
                .requires(source -> source.hasPermission(3))
                .redirect(baseNode)
        );
    }

    /**
     * Ponto de entrada para os subcomandos /sfx reload <categoria>.
     *
     * Envia feedback imediato ao admin (amarelo = "a recarregar...") antes de
     * iniciar o processo assíncrono. Quando o reload completa (ou falha), o
     * resultado final é enviado ao servidor no contexto da main thread.
     *
     * @param source   Fonte do comando (admin)
     * @param category "recipes", "loot", ou "functions"
     */
    private static int executeReload(CommandSourceStack source, String category) {
        MinecraftServer server = source.getServer();

        // Feedback imediato antes do processamento assíncrono
        source.sendSuccess(() -> Component.literal("[ServerFixes] ")
            .withStyle(ChatFormatting.DARK_AQUA)
            .append(Component.literal("Iniciando reload cirúrgico de: ")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal(category.toUpperCase())
                .withStyle(ChatFormatting.YELLOW)), false);

        java.util.concurrent.CompletableFuture<Void> reloadFuture;

        switch (category) {
            case "recipes"   -> reloadFuture = SurgicalReloadHandler.reloadRecipes(server);
            case "loot"      -> reloadFuture = SurgicalReloadHandler.reloadLootTables(server);
            case "functions" -> reloadFuture = SurgicalReloadHandler.reloadFunctions(server);
            default -> {
                source.sendFailure(Component.literal("Categoria desconhecida: " + category));
                return 0;
            }
        }

        // Resposta final na main thread após o reload assíncrono completar
        reloadFuture.thenRunAsync(() ->
            source.sendSuccess(() -> Component.literal("[ServerFixes] ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("✔ Reload de ")
                    .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(category.toUpperCase())
                    .withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" concluído com sucesso!")
                    .withStyle(ChatFormatting.GREEN)), true),
            server::execute
        ).exceptionally(ex -> {
            source.sendFailure(Component.literal(
                "[ServerFixes] Falha no reload de " + category + ": " + ex.getMessage()));
            LOGGER.error("[SurgicalReload] Erro ao recarregar '{}':", category, ex);
            return null;
        });

        return 1;
    }

    private static int openParticleStudio(CommandSourceStack source) {
        if (source.getEntity() instanceof Player player) {
            if (!player.isCreative()) {
                source.sendFailure(Component.literal("Este comando só pode ser usado no modo Criativo."));
                return 0;
            }
            com.gabri.serverfixes.network.NetworkHandler.sendToPlayer((net.minecraft.server.level.ServerPlayer) player,
                new com.gabri.serverfixes.network.OpenParticleStudioPacket());
            source.sendSuccess(() -> Component.literal("Abrindo Particle Studio...").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.literal("Este comando só pode ser usado por jogadores."));
        return 0;
    }

    private static int openSoundStudio(CommandSourceStack source) {
        if (source.getEntity() instanceof Player player) {
            if (!player.isCreative()) {
                source.sendFailure(Component.literal("Este comando só pode ser usado no modo Criativo."));
                return 0;
            }
            com.gabri.serverfixes.network.NetworkHandler.sendToPlayer((net.minecraft.server.level.ServerPlayer) player,
                new com.gabri.serverfixes.network.OpenSoundStudioPacket());
            source.sendSuccess(() -> Component.literal("Abrindo Sound Studio...").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.literal("Este comando só pode ser usado por jogadores."));
        return 0;
    }

    private static int openLootStudio(CommandSourceStack source) {
        if (source.getEntity() instanceof Player player) {
            if (!player.isCreative()) {
                source.sendFailure(Component.literal("Este comando só pode ser usado no modo Criativo."));
                return 0;
            }
            com.gabri.serverfixes.network.NetworkHandler.sendToPlayer((net.minecraft.server.level.ServerPlayer) player,
                new com.gabri.serverfixes.network.OpenLootStudioPacket());
            source.sendSuccess(() -> Component.literal("Abrindo Loot Studio...").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.literal("Este comando só pode ser usado por jogadores."));
        return 0;
    }

    private static int openTagStudio(CommandSourceStack source) {
        if (source.getEntity() instanceof Player player) {
            if (!player.isCreative()) {
                source.sendFailure(Component.literal("Este comando só pode ser usado no modo Criativo."));
                return 0;
            }
            com.gabri.serverfixes.network.NetworkHandler.sendToPlayer((net.minecraft.server.level.ServerPlayer) player,
                new com.gabri.serverfixes.network.OpenTagStudioPacket());
            source.sendSuccess(() -> Component.literal("Abrindo Tag Studio...").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.literal("Este comando só pode ser usado por jogadores."));
        return 0;
    }

    private static int showStatus(CommandSourceStack source, String module) {
        if (module == null || module.equals("villagers")) {
            source.sendSuccess(() -> Component.literal("--- Villager Status ---").withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal("Tick Rate: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(ServerFixesConfig.VILLAGER_TICK_RATE.get())).withStyle(ChatFormatting.GOLD)), false);
        }
        if (module == null || module.equals("antiswap")) {
            source.sendSuccess(() -> Component.literal("--- Anti-Swap Status ---").withStyle(ChatFormatting.AQUA), false);
            boolean enabled = ServerFixesConfig.ENABLE_ANTI_SWAP.get();
            source.sendSuccess(() -> Component.literal("Enabled: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(enabled)).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        }
        if (module == null || module.equals("infinitetrade")) {
            source.sendSuccess(() -> Component.literal("--- Infinite Trade Status ---").withStyle(ChatFormatting.AQUA), false);
            boolean enabled = ServerFixesConfig.ENABLE_INFINITE_TRADES.get();
            source.sendSuccess(() -> Component.literal("Enabled: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(enabled)).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            source.sendSuccess(() -> Component.literal("Required Tag: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ServerFixesConfig.INFINITE_TRADE_TAG.get()).withStyle(ChatFormatting.GOLD)), false);
        }
        if (module == null || module.equals("debug")) {
            source.sendSuccess(() -> Component.literal("--- Debug Status ---").withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal("Damage Received: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(ServerFixesConfig.DEBUG_DAMAGE_RECEIVED.get())).withStyle(ServerFixesConfig.DEBUG_DAMAGE_RECEIVED.get() ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            source.sendSuccess(() -> Component.literal("Damage Dealt: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(ServerFixesConfig.DEBUG_DAMAGE_DEALT.get())).withStyle(ServerFixesConfig.DEBUG_DAMAGE_DEALT.get() ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            source.sendSuccess(() -> Component.literal("Damage Breakdown: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(ServerFixesConfig.DEBUG_DAMAGE_BREAKDOWN.get())).withStyle(ServerFixesConfig.DEBUG_DAMAGE_BREAKDOWN.get() ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            source.sendSuccess(() -> Component.literal("Villager Debug: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(ServerFixesConfig.DEBUG_VILLAGERS.get())).withStyle(ServerFixesConfig.DEBUG_VILLAGERS.get() ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            source.sendSuccess(() -> Component.literal("Anti-Swap Debug: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(ServerFixesConfig.DEBUG_ANTISWAP.get())).withStyle(ServerFixesConfig.DEBUG_ANTISWAP.get() ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        }
        if (module == null || module.equals("throttle")) {
            source.sendSuccess(() -> Component.literal("--- Throttling Status ---").withStyle(ChatFormatting.AQUA), false);
            source.sendSuccess(() -> Component.literal("F&C Enabled: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(ServerFixesConfig.THROTTLE_FARM_AND_CHARM.get())).withStyle(ServerFixesConfig.THROTTLE_FARM_AND_CHARM.get() ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            source.sendSuccess(() -> Component.literal("Cooking Pot: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(ServerFixesConfig.COOKING_POT_TICK_RATE.get())).withStyle(ChatFormatting.GOLD)), false);
            source.sendSuccess(() -> Component.literal("Roaster: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(ServerFixesConfig.ROASTER_TICK_RATE.get())).withStyle(ChatFormatting.GOLD)), false);
            source.sendSuccess(() -> Component.literal("Vinery Enabled: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(ServerFixesConfig.THROTTLE_VINERY.get())).withStyle(ServerFixesConfig.THROTTLE_VINERY.get() ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            source.sendSuccess(() -> Component.literal("Ferment. Barrel: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(ServerFixesConfig.FERMENTATION_BARREL_TICK_RATE.get())).withStyle(ChatFormatting.GOLD)), false);
        }
        if (module == null || module.equals("fix")) {
            source.sendSuccess(() -> Component.literal("--- Fixes Status ---").withStyle(ChatFormatting.AQUA), false);
            boolean backstab = ServerFixesConfig.FIX_BACKSTABBING_EXPLOIT.get();
            source.sendSuccess(() -> Component.literal("Backstabbing Exploit Fix: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(backstab)).withStyle(backstab ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            
            boolean turtle = ServerFixesConfig.NERF_TURTLE_MASTER.get();
            source.sendSuccess(() -> Component.literal("Turtle Master Nerf: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(turtle)).withStyle(turtle ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            if (turtle) {
                source.sendSuccess(() -> Component.literal("  - Standard: Res ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(ServerFixesConfig.TURTLE_MASTER_RESISTANCE.get())).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(", Slow ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.valueOf(ServerFixesConfig.TURTLE_MASTER_SLOWNESS.get())).withStyle(ChatFormatting.GOLD)), false);
                source.sendSuccess(() -> Component.literal("  - Strong: Res ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.valueOf(ServerFixesConfig.STRONG_TURTLE_MASTER_RESISTANCE.get())).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(", Slow ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.valueOf(ServerFixesConfig.STRONG_TURTLE_MASTER_SLOWNESS.get())).withStyle(ChatFormatting.GOLD)), false);
            }

            boolean stringIds = ServerFixesConfig.ENABLE_STRING_EFFECT_IDS.get();
            source.sendSuccess(() -> Component.literal("String Effect IDs: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(stringIds)).withStyle(stringIds ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        }
        return 1;
    }
}
