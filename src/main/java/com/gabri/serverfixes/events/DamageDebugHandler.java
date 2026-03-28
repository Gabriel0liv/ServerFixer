package com.gabri.serverfixes.events;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Combat Audit Tool with Noise Reduction (v1.3.6).
 * Filters out irrelevant attributes to focus on actual defense and mitigation.
 */
@SuppressWarnings("all")
public class DamageDebugHandler {

    private static final Map<LivingEntity, Float> RAW_DAMAGE_TRACKER = new WeakHashMap<>();
    
    // Keywords for defensive/combat-relevant attributes
    private static final List<String> DEFENSIVE_WHITELIST = Arrays.asList(
        "ward", "shield", "defense", "protection", "resistance", "armor", "toughness", 
        "mitigation", "resilience", "absorb", "physical", "magic", "damage"
    );

    // Keywords for irrelevant/noisy attributes
    private static final List<String> NOISE_BLACKLIST = Arrays.asList(
        "mana", "max", "speed", "proficiency", "level", "recoil", "range", "cooldown", "gravity"
    );

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!ServerFixesConfig.DEBUG_DAMAGE_RECEIVED.get() && !ServerFixesConfig.DEBUG_DAMAGE_DEALT.get()) return;
        RAW_DAMAGE_TRACKER.put(event.getEntity(), event.getAmount());
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        float actualFinal = event.getAmount();

        if (!RAW_DAMAGE_TRACKER.containsKey(target)) return;
        float raw = RAW_DAMAGE_TRACKER.remove(target);

        if (target instanceof Player victim && ServerFixesConfig.DEBUG_DAMAGE_RECEIVED.get()) {
            auditCombat(victim, "Recebido", target, source, raw, actualFinal);
        }

        if (source.getEntity() instanceof Player attacker && ServerFixesConfig.DEBUG_DAMAGE_DEALT.get()) {
            auditCombat(attacker, "Causado", target, source, raw, actualFinal);
        }
    }

    private static void auditCombat(Player player, String prefix, LivingEntity target, DamageSource source, float raw, float actualFinal) {
        if (ServerFixesConfig.DEBUG_DAMAGE_BREAKDOWN.get()) {
            sendDeepAudit(player, prefix, target, source, raw, actualFinal);
        } else {
            sendSimpleDebug(player, prefix, target, source, raw);
        }
    }

    private static void sendSimpleDebug(Player player, String prefix, LivingEntity target, DamageSource source, float amount) {
        ResourceLocation typeRL = getDamageType(source);
        ChatFormatting color = prefix.equals("Recebido") ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD;
        MutableComponent msg = Component.literal("[" + prefix + "Debug] ").withStyle(color);
        msg.append(Component.literal("Tipo: ").withStyle(ChatFormatting.GRAY)).append(Component.literal(String.valueOf(typeRL)).withStyle(ChatFormatting.YELLOW));
        msg.append(Component.literal(" | Bruto: ").withStyle(ChatFormatting.GRAY)).append(Component.literal(String.format("%.2f", amount)).withStyle(ChatFormatting.WHITE));
        if (prefix.equals("Causado")) {
            msg.append(Component.literal(" | Alvo: ").withStyle(ChatFormatting.GRAY)).append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.AQUA));
        }
        player.sendSystemMessage(msg);
    }

    private static void sendDeepAudit(Player player, String prefix, LivingEntity target, DamageSource source, float raw, float actualFinal) {
        ResourceLocation typeRL = getDamageType(source);

        float armor = target.getArmorValue();
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float afterArmor = CombatRules.getDamageAfterAbsorb(raw, armor, toughness);
        float armorRed = raw - afterArmor;

        float afterResistance = afterArmor;
        if (target.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            int amp = target.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1;
            afterResistance = afterArmor * (1.0f - Math.min(amp, 5) * 0.2f);
        }
        float resRed = afterArmor - afterResistance;

        int epf = EnchantmentHelper.getDamageProtection(target.getArmorSlots(), source);
        float expectedFinal = CombatRules.getDamageAfterMagicAbsorb(afterResistance, (float) epf);
        float protRed = afterResistance - expectedFinal;

        float delta = expectedFinal - actualFinal;

        ChatFormatting color = prefix.equals("Recebido") ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD;
        player.sendSystemMessage(Component.literal("--- [" + prefix + "] Combat Audit (v1.3.6) ---").withStyle(color));
        player.sendSystemMessage(formatLine("Dano Inicial", raw, ChatFormatting.WHITE));

        if (Math.abs(armorRed) > 0.01f) {
            player.sendSystemMessage(formatLine("Redução Armadura", -armorRed, ChatFormatting.RED)
                .append(Component.literal(" (Armor: " + (int)armor + ", Tough: " + (int)toughness + ")").withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (Math.abs(resRed) > 0.01f) {
            player.sendSystemMessage(formatLine("Redução Resistência", -resRed, ChatFormatting.BLUE));
        }

        if (Math.abs(protRed) > 0.01f) {
            player.sendSystemMessage(formatLine("Redução Proteção (EPF)", -protRed, ChatFormatting.GREEN)
                .append(Component.literal(" (EPF: " + epf + ")").withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (Math.abs(delta) > 0.01f) {
            ChatFormatting deltaColor = delta > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_RED;
            player.sendSystemMessage(formatLine(delta > 0 ? "Mudança Mods (Redução)" : "Mudança Mods (Aumento)", -delta, deltaColor));
        }

        List<String> auditResults = scanCombatRelevantStats(target);
        if (!auditResults.isEmpty()) {
            player.sendSystemMessage(Component.literal("Probabilidade de Origem:").withStyle(ChatFormatting.DARK_AQUA));
            for (String res : auditResults) {
                player.sendSystemMessage(Component.literal("  > " + res).withStyle(ChatFormatting.GRAY));
            }
        }

        if (!target.getActiveEffects().isEmpty()) {
            String effects = target.getActiveEffects().stream()
                .map(e -> e.getDescriptionId().replace("effect.", "").replace("minecraft.", ""))
                .collect(Collectors.joining(", "));
            player.sendSystemMessage(Component.literal("Efeitos Ativos: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("[" + effects + "]").withStyle(ChatFormatting.GRAY)));
        }

        player.sendSystemMessage(Component.literal("---------------------------------").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(formatLine("Dano Final", actualFinal, ChatFormatting.YELLOW)
            .append(Component.literal(" (Tipo: " + typeRL + ")").withStyle(ChatFormatting.DARK_GRAY)));
        
        if (prefix.equals("Causado")) {
            player.sendSystemMessage(Component.literal("Alvo: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(target.getName().getString()).withStyle(ChatFormatting.AQUA)));
        }
    }

    private static List<String> scanCombatRelevantStats(LivingEntity entity) {
        List<String> results = new ArrayList<>();
        entity.getAttributes().getSyncableAttributes().forEach(instance -> {
            ResourceLocation attrId = ForgeRegistries.ATTRIBUTES.getKey(instance.getAttribute());
            if (attrId == null) return;

            String name = attrId.getPath().toLowerCase();
            boolean isModded = !attrId.getNamespace().equals("minecraft");
            
            // Relevancy Filter
            boolean isRelevant = DEFENSIVE_WHITELIST.stream().anyMatch(name::contains);
            boolean isNoisy = NOISE_BLACKLIST.stream().anyMatch(name::contains);

            if (isRelevant && !isNoisy) {
                double val = instance.getValue();
                if (Math.abs(val) > 0.001) {
                    results.add(attrId.getNamespace() + ":" + attrId.getPath() + ": " + String.format("%.2f", val));
                }
            }

            // Check Modifiers for relevant names (e.g. Origins powers)
            for (AttributeModifier modifier : instance.getModifiers()) {
                String modName = modifier.getName().toLowerCase();
                if (modName.length() > 3 && !modName.startsWith("b22") && !modName.contains("modifier")) {
                    if (DEFENSIVE_WHITELIST.stream().anyMatch(modName::contains) && NOISE_BLACKLIST.stream().noneMatch(modName::contains)) {
                        results.add(attrId.getPath() + " modificador: '" + modifier.getName() + "' (" + String.format("%.2f", modifier.getAmount()) + ")");
                    }
                }
            }
        });
        return results.stream().distinct().limit(5).collect(Collectors.toList());
    }

    private static MutableComponent formatLine(String label, float val, ChatFormatting valColor) {
        return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.format("%.2f", val)).withStyle(valColor));
    }

    private static ResourceLocation getDamageType(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> k.location()).orElse(null);
    }
}
