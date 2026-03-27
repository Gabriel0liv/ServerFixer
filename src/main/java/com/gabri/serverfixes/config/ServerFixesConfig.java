package com.gabri.serverfixes.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerFixesConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_ANTI_SWAP;
    public static final ForgeConfigSpec.BooleanValue ENABLE_INFINITE_TRADES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING;
    public static final ForgeConfigSpec.LongValue ANTI_SWAP_COOLDOWN;
    public static final ForgeConfigSpec.IntValue VILLAGER_TICK_RATE;
    public static final ForgeConfigSpec.ConfigValue<String> INFINITE_TRADE_TAG;

    // Farm & Charm Throttling
    public static final ForgeConfigSpec.BooleanValue THROTTLE_FARM_AND_CHARM;
    public static final ForgeConfigSpec.IntValue COOKING_POT_TICK_RATE;
    public static final ForgeConfigSpec.IntValue ROASTER_TICK_RATE;

    // Vinery Throttling
    public static final ForgeConfigSpec.BooleanValue THROTTLE_VINERY;
    public static final ForgeConfigSpec.IntValue FERMENTATION_BARREL_TICK_RATE;

    static {
        BUILDER.push("General Settings");

        ENABLE_ANTI_SWAP = BUILDER
                .comment("Enable or disable the anti-swap exploit protection.")
                .define("enableAntiSwap", true);

        ENABLE_INFINITE_TRADES = BUILDER
                .comment("Enable or disable the infinite trading system for tagged players.")
                .define("enableInfiniteTrades", true);

        ENABLE_DEBUG_LOGGING = BUILDER
                .comment("Enable or disable real-time debug messages for combat snapshots.")
                .define("enableDebugLogging", false);

        ANTI_SWAP_COOLDOWN = BUILDER
                .comment("The duration (in milliseconds) of the damage block after swapping a main hand item.")
                .defineInRange("antiSwapCooldown", 800L, 0L, 5000L);

        VILLAGER_TICK_RATE = BUILDER
                .comment("Defines every how many ticks the villager brain processes (e.g., 20 = once per second).")
                .defineInRange("villagerTickRate", 20, 1, 100);

        INFINITE_TRADE_TAG = BUILDER
                .comment("The player tag that allows for infinite trading with villagers.")
                .define("infiniteTradeTag", "infinite_trades");

        BUILDER.push("Optimization Throttling");

        THROTTLE_FARM_AND_CHARM = BUILDER
                .comment("Enable or disable throttling for Farm & Charm blocks.")
                .define("throttleFarmAndCharm", true);

        COOKING_POT_TICK_RATE = BUILDER
                .comment("Tick rate for Cooking Pot (e.g., 20 = once per second).")
                .defineInRange("cookingPotTickRate", 20, 1, 1000);

        ROASTER_TICK_RATE = BUILDER
                .comment("Tick rate for Roaster (e.g., 20 = once per second).")
                .defineInRange("roasterTickRate", 20, 1, 1000);

        THROTTLE_VINERY = BUILDER
                .comment("Enable or disable throttling for Vinery blocks.")
                .define("throttleVinery", true);

        FERMENTATION_BARREL_TICK_RATE = BUILDER
                .comment("Tick rate for Fermentation Barrel (e.g., 20 = once per second).")
                .defineInRange("fermentationBarrelTickRate", 20, 1, 1000);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
