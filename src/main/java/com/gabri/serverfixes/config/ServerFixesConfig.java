package com.gabri.serverfixes.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerFixesConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_ANTI_SWAP;
    public static final ForgeConfigSpec.BooleanValue ENABLE_INFINITE_TRADES;
    public static final ForgeConfigSpec.IntValue VILLAGER_TICK_RATE;
    public static final ForgeConfigSpec.ConfigValue<String> INFINITE_TRADE_TAG;
    public static final ForgeConfigSpec.BooleanValue FIX_BACKSTABBING_EXPLOIT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_STRING_EFFECT_IDS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PERSISTENT_ATTRIBUTES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ITEM_EFFECT_MODIFIERS;

    // Potion Nerfs
    public static final ForgeConfigSpec.BooleanValue NERF_TURTLE_MASTER;
    public static final ForgeConfigSpec.IntValue TURTLE_MASTER_RESISTANCE;
    public static final ForgeConfigSpec.IntValue TURTLE_MASTER_SLOWNESS;
    public static final ForgeConfigSpec.IntValue STRONG_TURTLE_MASTER_RESISTANCE;
    public static final ForgeConfigSpec.IntValue STRONG_TURTLE_MASTER_SLOWNESS;

    // Debug Settings
    public static final ForgeConfigSpec.BooleanValue DEBUG_DAMAGE_RECEIVED;
    public static final ForgeConfigSpec.BooleanValue DEBUG_DAMAGE_DEALT;
    public static final ForgeConfigSpec.BooleanValue DEBUG_DAMAGE_BREAKDOWN;
    public static final ForgeConfigSpec.BooleanValue DEBUG_VILLAGERS;
    public static final ForgeConfigSpec.BooleanValue DEBUG_ANTISWAP;

    // Optimization Throttling
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

        VILLAGER_TICK_RATE = BUILDER
                .comment("Defines every how many ticks the villager brain processes (e.g., 20 = once per second).")
                .defineInRange("villagerTickRate", 20, 1, 100);

        INFINITE_TRADE_TAG = BUILDER
                .comment("The player tag that allows for infinite trading with villagers.")
                .define("infiniteTradeTag", "infinite_trades");

        FIX_BACKSTABBING_EXPLOIT = BUILDER
                .comment("If true, the Backstabbing enchantment from Farmer's Delight will only apply to physical player melee attacks (PLAYER_ATTACK).")
                .define("fixBackstabbingExploit", true);

        ENABLE_STRING_EFFECT_IDS = BUILDER
                .comment("If true, MobEffectInstance will support loading from NBT using string IDs (namespace:id).")
                .define("enableStringEffectIds", true);

        ENABLE_PERSISTENT_ATTRIBUTES = BUILDER
                .comment("If true, item attributes modified via ServerFixes commands will be backed up in a hidden tag and restored if lost (Fixes Archlight/Mohist attribute reset).")
                .define("enablePersistentAttributes", true);

        ENABLE_ITEM_EFFECT_MODIFIERS = BUILDER
                .comment("If true, allows administrators to add custom On-Hit and On-Hurt effects to items via NBT.")
                .define("enableItemEffectModifiers", true);

        BUILDER.push("potion_nerfs");
        NERF_TURTLE_MASTER = BUILDER
                .comment("If true, the Turtle Master potion effects will be limited to the levels below.")
                .define("nerfTurtleMaster", true);
        
        TURTLE_MASTER_RESISTANCE = BUILDER
                .comment("Maximum Resistance level for Standard and Long Turtle Master potions.")
                .defineInRange("turtleMasterResistance", 3, 1, 10);
        
        TURTLE_MASTER_SLOWNESS = BUILDER
                .comment("Maximum Slowness level for Standard and Long Turtle Master potions.")
                .defineInRange("turtleMasterSlowness", 4, 1, 10);
        
        STRONG_TURTLE_MASTER_RESISTANCE = BUILDER
                .comment("Maximum Resistance level for Strong Turtle Master potions.")
                .defineInRange("strongTurtleMasterResistance", 3, 1, 10);
        
        STRONG_TURTLE_MASTER_SLOWNESS = BUILDER
                .comment("Maximum Slowness level for Strong Turtle Master potions.")
                .defineInRange("strongTurtleMasterSlowness", 4, 1, 10);
        BUILDER.pop();

        BUILDER.pop();

        BUILDER.push("Debug Settings");

        DEBUG_DAMAGE_RECEIVED = BUILDER
                .comment("Enable or disable real-time debug messages for combat damage received by players.")
                .define("debugDamageReceived", false);

        DEBUG_DAMAGE_DEALT = BUILDER
                .comment("Enable or disable real-time debug messages for combat damage dealt by players.")
                .define("debugDamageDealt", false);

        DEBUG_DAMAGE_BREAKDOWN = BUILDER
                .comment("Enable or disable a detailed breakdown of damage reductions (Armor, Effects, Protection).")
                .define("debugDamageBreakdown", false);

        DEBUG_VILLAGERS = BUILDER
                .comment("Enable or disable debug logs for villager interactions and trades.")
                .define("debugVillagers", false);

        DEBUG_ANTISWAP = BUILDER
                .comment("Enable or disable debug logs for Anti-Swap attack cancellations.")
                .define("debugAntiSwap", false);

        BUILDER.pop();

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
