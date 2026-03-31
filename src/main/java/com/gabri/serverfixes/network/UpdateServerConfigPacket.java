package com.gabri.serverfixes.network;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateServerConfigPacket {
    private final String key;
    private final String value;
    private final String type;

    public UpdateServerConfigPacket(String key, String value, String type) {
        this.key = key;
        this.value = value;
        this.type = type;
    }

    public UpdateServerConfigPacket(FriendlyByteBuf buf) {
        this.key = buf.readUtf();
        this.value = buf.readUtf();
        this.type = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.key != null ? this.key : "");
        buf.writeUtf(this.value != null ? this.value : "");
        buf.writeUtf(this.type != null ? this.type : "");
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;
        
        context.enqueueWork(() -> {
            try {
                switch (key) {
                    case "enableAntiSwap": ServerFixesConfig.ENABLE_ANTI_SWAP.set(Boolean.parseBoolean(value)); break;
                    case "antiSwapCooldown": ServerFixesConfig.ANTI_SWAP_COOLDOWN.set(Long.parseLong(value)); break;
                    case "enableInfiniteTrades": ServerFixesConfig.ENABLE_INFINITE_TRADES.set(Boolean.parseBoolean(value)); break;
                    case "infiniteTradeTag": ServerFixesConfig.INFINITE_TRADE_TAG.set(normalizeStringValue(value)); break;
                    case "villagerTickRate": ServerFixesConfig.VILLAGER_TICK_RATE.set(Integer.parseInt(value)); break;
                    case "fixBackstabbingExploit": ServerFixesConfig.FIX_BACKSTABBING_EXPLOIT.set(Boolean.parseBoolean(value)); break;
                    case "enableStringEffectIds": ServerFixesConfig.ENABLE_STRING_EFFECT_IDS.set(Boolean.parseBoolean(value)); break;
                    case "nerfTurtleMaster": ServerFixesConfig.NERF_TURTLE_MASTER.set(Boolean.parseBoolean(value)); break;
                    case "turtleMasterResistance": ServerFixesConfig.TURTLE_MASTER_RESISTANCE.set(Integer.parseInt(value)); break;
                    case "turtleMasterSlowness": ServerFixesConfig.TURTLE_MASTER_SLOWNESS.set(Integer.parseInt(value)); break;
                    case "strongTurtleMasterResistance": ServerFixesConfig.STRONG_TURTLE_MASTER_RESISTANCE.set(Integer.parseInt(value)); break;
                    case "strongTurtleMasterSlowness": ServerFixesConfig.STRONG_TURTLE_MASTER_SLOWNESS.set(Integer.parseInt(value)); break;
                    case "throttleFarmAndCharm": ServerFixesConfig.THROTTLE_FARM_AND_CHARM.set(Boolean.parseBoolean(value)); break;
                    case "cookingPotTickRate": ServerFixesConfig.COOKING_POT_TICK_RATE.set(Integer.parseInt(value)); break;
                    case "roasterTickRate": ServerFixesConfig.ROASTER_TICK_RATE.set(Integer.parseInt(value)); break;
                    case "throttleVinery": ServerFixesConfig.THROTTLE_VINERY.set(Boolean.parseBoolean(value)); break;
                    case "fermentationBarrelTickRate": ServerFixesConfig.FERMENTATION_BARREL_TICK_RATE.set(Integer.parseInt(value)); break;
                    case "debugDamageReceived": ServerFixesConfig.DEBUG_DAMAGE_RECEIVED.set(Boolean.parseBoolean(value)); break;
                    case "debugDamageDealt": ServerFixesConfig.DEBUG_DAMAGE_DEALT.set(Boolean.parseBoolean(value)); break;
                    case "debugDamageBreakdown": ServerFixesConfig.DEBUG_DAMAGE_BREAKDOWN.set(Boolean.parseBoolean(value)); break;
                    case "debugVillagers": ServerFixesConfig.DEBUG_VILLAGERS.set(Boolean.parseBoolean(value)); break;
                    case "debugAntiSwap": ServerFixesConfig.DEBUG_ANTISWAP.set(Boolean.parseBoolean(value)); break;
                }
            } catch (Exception ignored) {}
        });
        context.setPacketHandled(true);
    }

    private static String normalizeStringValue(String rawValue) {
        if (rawValue == null) return "";
        String trimmed = rawValue.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
