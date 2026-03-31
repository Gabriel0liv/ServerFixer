package com.gabri.serverfixes.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Handles /serverfixes item subcommands for modifying held items.
 * Allows bypass of UI limitations in other editors by using String IDs.
 */
@SuppressWarnings("all")
public class ItemCommands {

    private static final SimpleCommandExceptionType MUST_BE_PLAYER = new SimpleCommandExceptionType(Component.literal("Este comando só pode ser usado por jogadores."));
    private static final SimpleCommandExceptionType NO_ITEM = new SimpleCommandExceptionType(Component.literal("Você precisa estar segurando um item na mão principal."));

    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent, net.minecraft.commands.CommandBuildContext buildContext) {
        parent.then(Commands.literal("item")
            .then(Commands.literal("add_effect")
                .then(Commands.argument("effect", ResourceArgument.resource(buildContext, Registries.MOB_EFFECT))
                    .suggests((ctx, builder) -> {
                        String remaining = builder.getRemaining().toLowerCase();
                        return SharedSuggestionProvider.suggest(ForgeRegistries.MOB_EFFECTS.getKeys().stream()
                            .map(ResourceLocation::toString)
                            .filter(id -> id.contains(remaining)), builder);
                    })
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 1000000))
                        .then(Commands.argument("amplifier", IntegerArgumentType.integer(1, 255))
                            .executes(ctx -> addEffectToItem(ctx, IntegerArgumentType.getInteger(ctx, "amplifier"))))
                        .executes(ctx -> addEffectToItem(ctx, 1))))));
    }

    private static int addEffectToItem(CommandContext<CommandSourceStack> context, int level) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) {
            throw MUST_BE_PLAYER.create();
        }

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            throw NO_ITEM.create();
        }

        MobEffect effect = ResourceArgument.getMobEffect(context, "effect").value();
        ResourceLocation effectId = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (effectId == null) return 0;

        int durationTicks = IntegerArgumentType.getInteger(context, "seconds") * 20;
        int amplifier = level - 1; // Level 1 is amplifier 0

        CompoundTag tag = stack.getOrCreateTag();
        ListTag effectsList;
        if (tag.contains("CustomPotionEffects", 9)) {
            effectsList = tag.getList("CustomPotionEffects", 10);
        } else {
            effectsList = new ListTag();
            tag.put("CustomPotionEffects", effectsList);
        }

        // Create new effect entry using STRING ID (namespace:id)
        // Keep vanilla-compatible numeric Id while preserving exact namespace id for modded loading.
        CompoundTag effectEntry = new CompoundTag();
        effectEntry.putInt("Id", MobEffect.getId(effect));
        effectEntry.putString("IdString", effectId.toString());
        effectEntry.putInt("Duration", durationTicks);
        effectEntry.putInt("Amplifier", amplifier);
        effectEntry.putBoolean("Ambient", false);
        effectEntry.putBoolean("ShowParticles", true);
        effectEntry.putBoolean("ShowIcon", true);

        effectsList.add(effectEntry);

        source.sendSuccess(() -> Component.literal("Efeito ").withStyle(ChatFormatting.GRAY)
            .append(Component.translatable(effect.getDescriptionId()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" (Lvl " + level + ") adicionado ao item na mão principal.").withStyle(ChatFormatting.GRAY)), true);

        return 1;
    }
}
