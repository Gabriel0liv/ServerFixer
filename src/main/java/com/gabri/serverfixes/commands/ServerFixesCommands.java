package com.gabri.serverfixes.commands;

import com.gabri.serverfixes.config.ServerFixesConfig;
import com.gabri.serverfixes.events.AntiSwapExploitHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@SuppressWarnings("all")
public class ServerFixesCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("serverfixes")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("status")
                .executes(context -> showStatus(context.getSource(), null))
                .then(Commands.literal("villagers").executes(context -> showStatus(context.getSource(), "villagers")))
                .then(Commands.literal("antiswap").executes(context -> showStatus(context.getSource(), "antiswap")))
                .then(Commands.literal("infinitetrade").executes(context -> showStatus(context.getSource(), "infinitetrade")))
                .then(Commands.literal("debug").executes(context -> showStatus(context.getSource(), "debug")))
                .then(Commands.literal("throttle").executes(context -> showStatus(context.getSource(), "throttle"))))
            
            .then(Commands.literal("antiswap")
                .then(Commands.literal("toggle")
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean value = BoolArgumentType.getBool(context, "value");
                            ServerFixesConfig.ENABLE_ANTI_SWAP.set(value);
                            context.getSource().sendSuccess(() -> Component.literal("Anti-Swap Exploit protection: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                            return 1;
                        })))
                .then(Commands.literal("cooldown")
                    .then(Commands.argument("ms", LongArgumentType.longArg(0, 5000))
                        .executes(context -> {
                            long value = LongArgumentType.getLong(context, "ms");
                            ServerFixesConfig.ANTI_SWAP_COOLDOWN.set(value);
                            context.getSource().sendSuccess(() -> Component.literal("Anti-Swap Cooldown set to: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(value + "ms").withStyle(ChatFormatting.GOLD)), true);
                            return 1;
                        })))
                .then(Commands.literal("reset")
                    .executes(context -> {
                        AntiSwapExploitHandler.resetAll();
                        context.getSource().sendSuccess(() -> Component.literal("Anti-Swap cache cleared.").withStyle(ChatFormatting.GREEN), true);
                        return 1;
                    })))

            .then(Commands.literal("villagerticks")
                .then(Commands.argument("num", IntegerArgumentType.integer(1, 100))
                    .executes(context -> {
                        int value = IntegerArgumentType.getInteger(context, "num");
                        ServerFixesConfig.VILLAGER_TICK_RATE.set(value);
                        context.getSource().sendSuccess(() -> Component.literal("Villager Tick Rate set to: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(String.valueOf(value)).withStyle(ChatFormatting.GOLD)), true);
                        return 1;
                    })))

            .then(Commands.literal("infinitetrade")
                .then(Commands.literal("toggle")
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean value = BoolArgumentType.getBool(context, "value");
                            ServerFixesConfig.ENABLE_INFINITE_TRADES.set(value);
                            context.getSource().sendSuccess(() -> Component.literal("Infinite Trades system: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                            return 1;
                        })))
                .then(Commands.literal("tag")
                    .then(Commands.argument("tagName", StringArgumentType.string())
                        .executes(context -> {
                            String value = StringArgumentType.getString(context, "tagName");
                            ServerFixesConfig.INFINITE_TRADE_TAG.set(value);
                            context.getSource().sendSuccess(() -> Component.literal("Infinite Trade Tag set to: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(value).withStyle(ChatFormatting.GOLD)), true);
                            return 1;
                        }))))

            .then(Commands.literal("debug")
                .then(Commands.literal("toggle")
                    .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean value = BoolArgumentType.getBool(context, "value");
                            ServerFixesConfig.ENABLE_DEBUG_LOGGING.set(value);
                            context.getSource().sendSuccess(() -> Component.literal("Debug logging: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(String.valueOf(value)).withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                            return 1;
                        }))))

            .then(Commands.literal("throttle")
                .then(Commands.literal("farmandcharm")
                    .then(Commands.literal("toggle")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> {
                                boolean val = BoolArgumentType.getBool(context, "value");
                                ServerFixesConfig.THROTTLE_FARM_AND_CHARM.set(val);
                                context.getSource().sendSuccess(() -> Component.literal("Farm & Charm Throttling: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                                return 1;
                            })))
                    .then(Commands.literal("cookingpot")
                        .then(Commands.argument("rate", IntegerArgumentType.integer(1, 1000))
                            .executes(context -> {
                                int val = IntegerArgumentType.getInteger(context, "rate");
                                ServerFixesConfig.COOKING_POT_TICK_RATE.set(val);
                                context.getSource().sendSuccess(() -> Component.literal("Cooking Pot Rate: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                                return 1;
                            })))
                    .then(Commands.literal("roaster")
                        .then(Commands.argument("rate", IntegerArgumentType.integer(1, 1000))
                            .executes(context -> {
                                int val = IntegerArgumentType.getInteger(context, "rate");
                                ServerFixesConfig.ROASTER_TICK_RATE.set(val);
                                context.getSource().sendSuccess(() -> Component.literal("Roaster Rate: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                                return 1;
                            }))))
                .then(Commands.literal("vinery")
                    .then(Commands.literal("toggle")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                            .executes(context -> {
                                boolean val = BoolArgumentType.getBool(context, "value");
                                ServerFixesConfig.THROTTLE_VINERY.set(val);
                                context.getSource().sendSuccess(() -> Component.literal("Vinery Throttling: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
                                return 1;
                            })))
                    .then(Commands.literal("barrel")
                        .then(Commands.argument("rate", IntegerArgumentType.integer(1, 1000))
                            .executes(context -> {
                                int val = IntegerArgumentType.getInteger(context, "rate");
                                ServerFixesConfig.FERMENTATION_BARREL_TICK_RATE.set(val);
                                context.getSource().sendSuccess(() -> Component.literal("Vinery Barrel Rate: ").withStyle(ChatFormatting.GRAY).append(Component.literal(String.valueOf(val)).withStyle(ChatFormatting.GOLD)), true);
                                return 1;
                            }))))
            )
        );
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
            boolean enabled = ServerFixesConfig.ENABLE_DEBUG_LOGGING.get();
            source.sendSuccess(() -> Component.literal("Combat Debug Logging: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(enabled)).withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
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
