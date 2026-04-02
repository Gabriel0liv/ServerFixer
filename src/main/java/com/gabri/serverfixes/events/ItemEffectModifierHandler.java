package com.gabri.serverfixes.events;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.item.Items;

/**
 * Handles custom combat/equipment effects defined in item NBT tags.
 * Tags: SF_ItemEffects: { on_use: [], on_hit: [], on_hurt: [] }
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = "server_fixes")
public class ItemEffectModifierHandler {

    public static final String MAIN_TAG = "SF_ItemEffects";
    public static final String BACKUP_TAG = "SF_BackupEffects";
    private static final String ON_HIT = "on_hit";
    private static final String ON_HURT = "on_hurt";
    private static final String ON_USE = "on_use";
    private static final String ON_USE_COLOR = "on_use_color";
    private static final String ON_USE_MIRROR_TAG = "on_use_potion_mirror";
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

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!ServerFixesConfig.ENABLE_ITEM_EFFECT_MODIFIERS.get()) return;

        ItemStack stack = event.getItem();
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return;

        LivingEntity holder = event.getEntity();

        // Splash and lingering are handled by injecting ON_USE into thrown potion NBT
        // and then letting vanilla logic apply area/cloud behavior.
        if (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
            return;
        }

        if (stack.getItem() == Items.POTION && isOnUsePotionMirror(stack)) {
            // In mirror mode, vanilla potion behavior is authoritative for drinkable potions.
            return;
        }

        if (!isOnUseSupportedItem(stack)) {
            return;
        }

        // Drinkable potion or any other consumable item: apply ON_USE to consumer.
        processEffects(stack, ON_USE, holder, null);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!ServerFixesConfig.ENABLE_ITEM_EFFECT_MODIFIERS.get()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;

        ItemStack stack = potion.getItem();
        if (stack.isEmpty() || !stack.hasTag()) return;

        if (stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
            if (isOnUsePotionMirror(stack)) {
                return;
            }
            injectOnUseIntoPotionStack(stack);
        }
    }

    private static void injectOnUseIntoPotionStack(ItemStack potionStack) {
        CompoundTag tag = potionStack.getOrCreateTag();
        if (tag.getBoolean("SF_OnUseInjected")) return;

        CompoundTag sfTag = tag.getCompound(MAIN_TAG);
        if (sfTag.contains(ON_USE_COLOR, Tag.TAG_ANY_NUMERIC)) {
            tag.putInt("CustomPotionColor", sfTag.getInt(ON_USE_COLOR));
        }

        List<MobEffectInstance> onUseEffects = collectEffects(potionStack, ON_USE);
        if (onUseEffects.isEmpty()) return;

        ListTag customPotionEffects = tag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
        for (MobEffectInstance effect : onUseEffects) {
            customPotionEffects.add(effect.save(new CompoundTag()));
        }
        tag.put("CustomPotionEffects", customPotionEffects);
        tag.putBoolean("SF_OnUseInjected", true);
    }

    private static void processEffects(ItemStack stack, String typeTag, LivingEntity holder, LivingEntity other) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MAIN_TAG, 10)) return;

        CompoundTag mainTag = tag.getCompound(MAIN_TAG);
        if (!mainTag.contains(typeTag, 9)) return;

        ListTag effectList = mainTag.getList(typeTag, 10);
        for (int i = 0; i < effectList.size(); i++) {
            CompoundTag entry = effectList.getCompound(i);

            if (!ON_USE.equals(typeTag)) {
                double chance = entry.contains("chance") ? entry.getDouble("chance") : 1.0;
                if (RANDOM.nextDouble() > chance) continue;
            }

            MobEffectInstance effectInstance = createEffectInstance(entry);
            if (effectInstance == null) continue;

            LivingEntity applierTarget;
            if (ON_USE.equals(typeTag)) {
                // ON_USE never uses self/target split: consumable -> holder, area -> target in area.
                applierTarget = other != null ? other : holder;
            } else {
                boolean targetSelf = entry.getBoolean("self");
                applierTarget = targetSelf ? holder : other;
            }

            if (applierTarget != null) {
                applierTarget.addEffect(new MobEffectInstance(effectInstance));
            }
        }
    }

    private static List<MobEffectInstance> collectEffects(ItemStack stack, String typeTag) {
        List<MobEffectInstance> effects = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MAIN_TAG, 10)) return effects;
        
        CompoundTag mainTag = tag.getCompound(MAIN_TAG);
        if (!mainTag.contains(typeTag, 9)) return effects;

        ListTag effectList = mainTag.getList(typeTag, 10);
        for (int i = 0; i < effectList.size(); i++) {
            CompoundTag entry = effectList.getCompound(i);

            if (!ON_USE.equals(typeTag)) {
                double chance = entry.contains("chance") ? entry.getDouble("chance") : 1.0;
                if (RANDOM.nextDouble() > chance) continue;
            }

            MobEffectInstance effectInstance = createEffectInstance(entry);
            if (effectInstance != null) {
                effects.add(effectInstance);
            }
        }

        return effects;
    }

    private static MobEffectInstance createEffectInstance(CompoundTag entry) {
        MobEffect effect = resolveEffect(entry);
        if (effect == null) return null;

        int duration = entry.getInt("duration");
        int amplifier = entry.getInt("amplifier");
        boolean ambient = entry.contains("Ambient") && entry.getBoolean("Ambient");
        boolean showParticles = !entry.contains("ShowParticles") || entry.getBoolean("ShowParticles");
        boolean showIcon = !entry.contains("ShowIcon") || entry.getBoolean("ShowIcon");

        return new MobEffectInstance(effect, duration, amplifier, ambient, showParticles, showIcon);
    }

    private static MobEffect resolveEffect(CompoundTag entry) {
        if (entry.contains("id", Tag.TAG_STRING)) {
            ResourceLocation loc = ResourceLocation.tryParse(entry.getString("id"));
            if (loc != null) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(loc);
                if (effect != null) {
                    return effect;
                }
            }
        }

        if (entry.contains("IdString", Tag.TAG_STRING)) {
            ResourceLocation loc = ResourceLocation.tryParse(entry.getString("IdString"));
            if (loc != null) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(loc);
                if (effect != null) {
                    return effect;
                }
            }
        }

        if (entry.contains("Id", Tag.TAG_STRING)) {
            ResourceLocation loc = ResourceLocation.tryParse(entry.getString("Id"));
            if (loc != null) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(loc);
                if (effect != null) {
                    return effect;
                }
            }
        }

        if (entry.contains("Id", Tag.TAG_ANY_NUMERIC)) {
            return MobEffect.byId(entry.getInt("Id"));
        }

        return null;
    }

    private static boolean isOnUseSupportedItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == Items.POTION || stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
            return true;
        }

        UseAnim anim = stack.getUseAnimation();
        return stack.isEdible() || anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    private static boolean isOnUsePotionMirror(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MAIN_TAG, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag sfTag = tag.getCompound(MAIN_TAG);
        return sfTag.getBoolean(ON_USE_MIRROR_TAG);
    }
}
