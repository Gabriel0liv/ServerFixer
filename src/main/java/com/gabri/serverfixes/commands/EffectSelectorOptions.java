package com.gabri.serverfixes.commands;

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Registers "effect" and "effectlvl" as valid target selector arguments.
 */
@SuppressWarnings("all")
public class EffectSelectorOptions {

    private static final Map<EntitySelectorParser, MobEffect> LAST_EFFECT = new WeakHashMap<>();

    private static final DynamicCommandExceptionType INVALID_EFFECT = new DynamicCommandExceptionType((id) -> {
        return Component.literal("Efeito inválido ou desconhecido: " + id);
    });

    private static final SimpleCommandExceptionType NO_EFFECT_CONTEXT = new SimpleCommandExceptionType(
            Component.literal("O argumento 'effectlvl' deve ser usado após um argumento 'effect'.")
    );

    public static void register() {
        // Main effect selector
        EntitySelectorOptions.register("effect", (parser) -> {
            boolean negated = parser.shouldInvertValue();
            
            // Add suggestions for effects
            parser.setSuggestions((builder, consumer) -> {
                String remaining = builder.getRemaining().toLowerCase();
                return SharedSuggestionProvider.suggestResource(ForgeRegistries.MOB_EFFECTS.getKeys(), builder);
            });

            int cursorBefore = parser.getReader().getCursor();
            ResourceLocation effectId = ResourceLocation.read(parser.getReader());
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);

            if (effect == null) {
                parser.getReader().setCursor(cursorBefore);
                throw INVALID_EFFECT.createWithContext(parser.getReader(), effectId.toString());
            }

            // Save for context-aware effectlvl
            LAST_EFFECT.put(parser, effect);

            parser.addPredicate((entity) -> {
                if (entity instanceof LivingEntity living) {
                    boolean has = living.hasEffect(effect);
                    return has != negated;
                }
                return negated;
            });
            
        }, (parser) -> true, Component.literal("Filtro por efeito de poção (ex: effect=minecraft:speed)"));

        // Level selector (amplifier)
        EntitySelectorOptions.register("effectlvl", (parser) -> {
            boolean negated = parser.shouldInvertValue();
            MobEffect lastEffect = LAST_EFFECT.get(parser);
            
            if (lastEffect == null) {
                throw NO_EFFECT_CONTEXT.createWithContext(parser.getReader());
            }

            MinMaxBounds.Ints range = MinMaxBounds.Ints.fromReader(parser.getReader());

            parser.addPredicate((entity) -> {
                if (entity instanceof LivingEntity living) {
                    MobEffectInstance inst = living.getEffect(lastEffect);
                    int amp = (inst != null) ? inst.getAmplifier() : -1;
                    return range.matches(amp) != negated;
                }
                return negated;
            });

        }, (parser) -> true, Component.literal("Filtro pelo nível do efeito anterior (ex: effectlvl=0)"));
    }
}
