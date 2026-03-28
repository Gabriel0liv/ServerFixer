package com.gabri.serverfixes.commands;

import com.gabri.serverfixes.config.ServerFixesConfig;
import com.gabri.serverfixes.events.AntiSwapExploitHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@SuppressWarnings("all")
public class ServerFixesCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, net.minecraft.commands.CommandBuildContext buildContext) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("serverfixes")
            .requires(source -> source.hasPermission(2));

        // /serverfixes status ...
        root.then(Commands.literal("status")
            .executes(ctx -> showStatus(ctx.getSource(), null))
            .then(Commands.literal("villagers").executes(ctx -> showStatus(ctx.getSource(), "villagers")))
            .then(Commands.literal("antiswap").executes(ctx -> showStatus(ctx.getSource(), "antiswap")))
            .then(Commands.literal("infinitetrade").executes(ctx -> showStatus(ctx.getSource(), "infinitetrade")))
            .then(Commands.literal("debug").executes(ctx -> showStatus(ctx.getSource(), "debug")))
            .then(Commands.literal("throttle").executes(ctx -> showStatus(ctx.getSource(), "throttle"))));

        // /serverfixes antiswap <bool> | cooldown <ms> | reset
        root.then(Commands.literal("antiswap")
            .then(Commands.argument("enabled", BoolArgumentType.bool())
                .executes(ctx -> {
                    boolean value = BoolArgumentType.getBool(ctx, "enabled");
                    ServerFixesConfig.ENABLE_ANTI_SWAP.set(value);
                    ctx.getSource().sendSuccess(() -> Component.literal("Anti-Swap Exploit protection: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.valueOf(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                    return 1;
                }))
            .then(Commands.literal("cooldown")
                .then(Commands.argument("ms", LongArgumentType.longArg(0, 5000))
                    .executes(ctx -> {
                        long value = LongArgumentType.getLong(ctx, "ms");
                        ServerFixesConfig.ANTI_SWAP_COOLDOWN.set(value);
                        ctx.getSource().sendSuccess(() -> Component.literal("Anti-Swap Cooldown set to: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(value + "ms").withStyle(ChatFormatting.GOLD)), true);
                        return 1;
                    })))
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
                            ServerFixesConfig.DEBUG_DAMAGE_RECEIVED.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Damage Received Debug: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                            return 1;
                        })))
                .then(Commands.literal("dealt")
                    .then(Commands.argument("val", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean val = BoolArgumentType.getBool(ctx, "val");
                            ServerFixesConfig.DEBUG_DAMAGE_DEALT.set(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("Damage Dealt Debug: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
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
                        ServerFixesConfig.DEBUG_VILLAGERS.set(val);
                        ctx.getSource().sendSuccess(() -> Component.literal("Villager Debug: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                        return 1;
                    })))
            .then(Commands.literal("antiswap")
                .then(Commands.argument("val", BoolArgumentType.bool())
                    .executes(ctx -> {
                        boolean val = BoolArgumentType.getBool(ctx, "val");
                        ServerFixesConfig.DEBUG_ANTISWAP.set(val);
                        ctx.getSource().sendSuccess(() -> Component.literal("Anti-Swap Debug: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
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

        // /serverfixes effect ...
        EffectCommands.register(root, buildContext);

        dispatcher.register(root);
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
            source.sendSuccess(() -> Component.literal("Cooldown: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ServerFixesConfig.ANTI_SWAP_COOLDOWN.get() + "ms").withStyle(ChatFormatting.GOLD)), false);
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
        return 1;
    }
}
