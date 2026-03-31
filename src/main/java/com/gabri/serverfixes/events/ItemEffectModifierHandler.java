package com.gabri.serverfixes.events;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;

/**
 * Handles custom combat effects defined in item NBT tags.
 * Tags: SF_ItemEffects: { on_hit: [], on_hurt: [] }
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = "server_fixes")
public class ItemEffectModifierHandler {

    public static final String MAIN_TAG = "SF_ItemEffects";
    public static final String BACKUP_TAG = "SF_BackupEffects";
    private static final String ON_HIT = "on_hit";
    private static final String ON_HURT = "on_hurt";
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!ServerFixesConfig.ENABLE_ITEM_EFFECT_MODIFIERS.get()) return;

        Entity source = event.getSource().getEntity();
        LivingEntity target = event.getEntity();

        // 1. Handle ON_HIT (Attacker has the item)
        if (source instanceof LivingEntity attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            if (!weapon.isEmpty() && weapon.hasTag()) {
                processEffects(weapon, ON_HIT, attacker, target); // Holder is attacker
            }
        }

        // 2. Handle ON_HURT (Target being hit has the item)
        if (target != null) {
            LivingEntity attacker = source instanceof LivingEntity ? (LivingEntity) source : null;
            for (ItemStack slotStack : target.getAllSlots()) {
                if (!slotStack.isEmpty() && slotStack.hasTag()) {
                    processEffects(slotStack, ON_HURT, target, attacker); // Holder is target
                }
            }
        }
    }

    private static void processEffects(ItemStack stack, String typeTag, LivingEntity holder, LivingEntity other) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MAIN_TAG, 10)) return;
        
        CompoundTag mainTag = tag.getCompound(MAIN_TAG);
        if (!mainTag.contains(typeTag, 9)) return;

        ListTag effectList = mainTag.getList(typeTag, 10);
        for (int i = 0; i < effectList.size(); i++) {
            CompoundTag entry = effectList.getCompound(i);
            
            double chance = entry.contains("chance") ? entry.getDouble("chance") : 1.0;
            if (RANDOM.nextDouble() > chance) continue;

            String effectId = entry.getString("id");
            // Use tryParse to avoid deprecation warnings
            ResourceLocation loc = ResourceLocation.tryParse(effectId);
            if (loc == null) continue;
            
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(loc);
            if (effect == null) continue;

            int duration = entry.getInt("duration");
            int amplifier = entry.getInt("amplifier");
            boolean targetSelf = entry.getBoolean("self");

            LivingEntity applierTarget = targetSelf ? holder : other;
            if (applierTarget != null) {
                applierTarget.addEffect(new MobEffectInstance(effect, duration, amplifier));
            }
        }
    }
}
