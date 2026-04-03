package com.gabri.serverfixes.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;

/**
 * Handles /serverfixes effect subcommands.
 */
@SuppressWarnings("all")
public class EffectCommands {

    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent, net.minecraft.commands.CommandBuildContext buildContext) {
        parent.then(Commands.literal("effect")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("info")
                .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.entities())
                    .executes(EffectCommands::showInfo)))
            .then(Commands.literal("modify")
                .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.entities())
                    .then(Commands.argument("effect", net.minecraft.commands.arguments.ResourceArgument.resource(buildContext, Registries.MOB_EFFECT))
                        .suggests((ctx, builder) -> {
                            String remaining = builder.getRemaining().toLowerCase();
                            return SharedSuggestionProvider.suggest(ForgeRegistries.MOB_EFFECTS.getKeys().stream()
                                .map(ResourceLocation::toString)
                                .filter(id -> id.contains(remaining)), builder);
                        })
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(-1000000, 1000000))
                            .then(Commands.argument("amplifier_delta", IntegerArgumentType.integer(-255, 255))
                                .executes(EffectCommands::modifyEffect))
                            .executes(context -> modifyEffect(context, 0)))))));
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<? extends Entity> targets = net.minecraft.commands.arguments.EntityArgument.getEntities(context, "targets");
        CommandSourceStack source = context.getSource();

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                MutableComponent header = Component.literal("--- Efeitos de: ").withStyle(ChatFormatting.AQUA)
                        .append(entity.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" ---").withStyle(ChatFormatting.AQUA));
                source.sendSuccess(() -> header, false);

                Collection<MobEffectInstance> effects = living.getActiveEffects();
                if (effects.isEmpty()) {
                    source.sendSuccess(() -> Component.literal("Nenhum efeito ativo.").withStyle(ChatFormatting.GRAY), false);
                } else {
                    for (MobEffectInstance inst : effects) {
                        MutableComponent line = Component.literal("- ").withStyle(ChatFormatting.GRAY)
                                .append(Component.translatable(inst.getDescriptionId()).withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal(" Lvl " + (inst.getAmplifier() + 1)).withStyle(ChatFormatting.GOLD))
                                .append(Component.literal(" | Tempo: ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(formatTime(inst.getDuration())).withStyle(ChatFormatting.WHITE));
                        source.sendSuccess(() -> line, false);
                    }
                }
            } else {
                source.sendFailure(Component.literal(entity.getDisplayName().getString() + " não é uma entidade viva."));
            }
        }
        return targets.size();
    }

    private static int modifyEffect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int delta;
        try {
            delta = IntegerArgumentType.getInteger(context, "amplifier_delta");
        } catch (IllegalArgumentException e) {
            delta = 0;
        }
        return modifyEffect(context, delta);
    }

    private static int modifyEffect(CommandContext<CommandSourceStack> context, int amplifierDelta) throws CommandSyntaxException {
        Collection<? extends Entity> targets = net.minecraft.commands.arguments.EntityArgument.getEntities(context, "targets");
        MobEffect effect = net.minecraft.commands.arguments.ResourceArgument.getMobEffect(context, "effect").value();
        int secondsToModify = IntegerArgumentType.getInteger(context, "seconds");
        int ticksToModify = secondsToModify * 20;

        int count = 0;
        for (Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                MobEffectInstance current = living.getEffect(effect);
                
                int currentDuration = current != null ? current.getDuration() : 0;
                int currentAmplifier = current != null ? current.getAmplifier() : -1; // -1 means no effect (Level 0)

                int newDuration = currentDuration + ticksToModify;
                int newAmplifier = currentAmplifier + amplifierDelta;

                // Remove existing to allow downgrading (vanilla addEffect prevents downgrades)
                living.removeEffect(effect);

                if (newDuration > 0 && newAmplifier >= 0) {
                    MobEffectInstance newInst = new MobEffectInstance(effect, newDuration, newAmplifier, false, true, true);
                    living.addEffect(newInst);
                }
                count++;
            }
        }

        final int finalCount = count;
        context.getSource().sendSuccess(() -> Component.literal("Modificado efeito ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(effect.getDescriptionId()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" para " + finalCount + " entidades.").withStyle(ChatFormatting.GRAY)), true);

        return count;
    }

    private static String formatTime(int ticks) {
        int seconds = ticks / 20;
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%dm %ds", m, s) + " (" + ticks + " ticks)";
    }
}
