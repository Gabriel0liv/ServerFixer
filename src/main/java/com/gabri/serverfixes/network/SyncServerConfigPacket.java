package com.gabri.serverfixes.network;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncServerConfigPacket {
    private final CompoundTag configData;

    public SyncServerConfigPacket(CompoundTag configData) {
        this.configData = configData;
    }

    public SyncServerConfigPacket(FriendlyByteBuf buf) {
        this.configData = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(this.configData);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;
        
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.gabri.serverfixes.client.ClientPacketHandler.handleSyncConfig(this.configData));
        });
        context.setPacketHandled(true);
    }

    public static CompoundTag getCurrentConfig() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("enableAntiSwap", ServerFixesConfig.ENABLE_ANTI_SWAP.get());
        tag.putLong("antiSwapCooldown", ServerFixesConfig.ANTI_SWAP_COOLDOWN.get());
        tag.putBoolean("enableInfiniteTrades", ServerFixesConfig.ENABLE_INFINITE_TRADES.get());
        String infiniteTradeTag = ServerFixesConfig.INFINITE_TRADE_TAG.get();
        tag.putString("infiniteTradeTag", infiniteTradeTag != null ? infiniteTradeTag : "");
        tag.putInt("villagerTickRate", ServerFixesConfig.VILLAGER_TICK_RATE.get());
        tag.putBoolean("fixBackstabbingExploit", ServerFixesConfig.FIX_BACKSTABBING_EXPLOIT.get());
        tag.putBoolean("enableStringEffectIds", ServerFixesConfig.ENABLE_STRING_EFFECT_IDS.get());
        tag.putBoolean("nerfTurtleMaster", ServerFixesConfig.NERF_TURTLE_MASTER.get());
        tag.putInt("turtleMasterResistance", ServerFixesConfig.TURTLE_MASTER_RESISTANCE.get());
        tag.putInt("turtleMasterSlowness", ServerFixesConfig.TURTLE_MASTER_SLOWNESS.get());
        tag.putInt("strongTurtleMasterResistance", ServerFixesConfig.STRONG_TURTLE_MASTER_RESISTANCE.get());
        tag.putInt("strongTurtleMasterSlowness", ServerFixesConfig.STRONG_TURTLE_MASTER_SLOWNESS.get());
        tag.putBoolean("throttleFarmAndCharm", ServerFixesConfig.THROTTLE_FARM_AND_CHARM.get());
        tag.putInt("cookingPotTickRate", ServerFixesConfig.COOKING_POT_TICK_RATE.get());
        tag.putInt("roasterTickRate", ServerFixesConfig.ROASTER_TICK_RATE.get());
        tag.putBoolean("throttleVinery", ServerFixesConfig.THROTTLE_VINERY.get());
        tag.putInt("fermentationBarrelTickRate", ServerFixesConfig.FERMENTATION_BARREL_TICK_RATE.get());
        tag.putBoolean("debugDamageReceived", ServerFixesConfig.DEBUG_DAMAGE_RECEIVED.get());
        tag.putBoolean("debugDamageDealt", ServerFixesConfig.DEBUG_DAMAGE_DEALT.get());
        tag.putBoolean("debugDamageBreakdown", ServerFixesConfig.DEBUG_DAMAGE_BREAKDOWN.get());
        tag.putBoolean("debugVillagers", ServerFixesConfig.DEBUG_VILLAGERS.get());
        tag.putBoolean("debugAntiSwap", ServerFixesConfig.DEBUG_ANTISWAP.get());
        return tag;
    }
}
