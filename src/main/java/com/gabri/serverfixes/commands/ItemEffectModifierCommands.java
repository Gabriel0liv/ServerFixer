package com.gabri.serverfixes.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.registries.ForgeRegistries;

@SuppressWarnings("all")
public class ItemEffectModifierCommands {

    private static final SimpleCommandExceptionType MUST_BE_PLAYER = new SimpleCommandExceptionType(Component.literal("Este comando so pode ser usado por jogadores."));
    private static final SimpleCommandExceptionType NO_ITEM = new SimpleCommandExceptionType(Component.literal("Voce precisa estar segurando um item."));
    private static final String MAIN_TAG = "SF_ItemEffects";

    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent, CommandBuildContext buildContext) {
        parent.then(Commands.literal("effect_modifier")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("add")
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("on_hit");
                        builder.suggest("on_use");
                        builder.suggest("on_hurt");
                        return builder.buildFuture();
                    })
                    .then(Commands.argument("effect", ResourceArgument.resource(buildContext, Registries.MOB_EFFECT))
                        .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                            .then(Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                .executes(ItemEffectModifierCommands::addEffectWithDefaultTarget)
                                .then(Commands.argument("chance", DoubleArgumentType.doubleArg(0.0, 1.0))
                                    .then(Commands.argument("targetSelf", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("self");
                                            builder.suggest("target");
                                            return builder.buildFuture();
                                        })
                                        .executes(ItemEffectModifierCommands::addEffect)
                                    )
                                    .executes(ItemEffectModifierCommands::addEffectWithDefaultTarget)
                                )
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("list")
                .executes(ItemEffectModifierCommands::listEffects)
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("type", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        builder.suggest("on_use");
                        builder.suggest("on_hit");
                        builder.suggest("on_hurt");
                        return builder.buildFuture();
                    })
                    .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .executes(ItemEffectModifierCommands::removeEffect)
                    )
                )
            )
        );
    }

    private static int addEffectWithDefaultTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return addEffectInternal(context, null);
    }

    private static int addEffect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String targetStr = StringArgumentType.getString(context, "targetSelf");
        return addEffectInternal(context, targetStr);
    }

    private static int addEffectInternal(CommandContext<CommandSourceStack> context, String targetStr) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) throw MUST_BE_PLAYER.create();

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) throw NO_ITEM.create();

        String type = StringArgumentType.getString(context, "type").toLowerCase();
        if (type.equals("on_use") && !isOnUseSupportedItem(stack)) {
            source.sendFailure(Component.literal("on_use so pode ser aplicado em pocoes e itens consumiveis."));
            return 0;
        }

        MobEffect effect = ResourceArgument.getMobEffect(context, "effect").value();
        ResourceLocation effectId = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (effectId == null) return 0;

        int duration = IntegerArgumentType.getInteger(context, "duration") * 20; // Transform to ticks
        int amplifier = IntegerArgumentType.getInteger(context, "amplifier");
        double chance = 1.0D;
        try {
            chance = DoubleArgumentType.getDouble(context, "chance");
        } catch (IllegalArgumentException ignored) {
            // chance omitted
        }
        if (type.equals("on_use")) {
            chance = 1.0D;
        }
        
        // Default targets: on_hit -> target, on_hurt -> self. ON_USE ignores target split.
        boolean self = targetStr != null ? targetStr.equalsIgnoreCase("self") : type.equals("on_hurt");

        CompoundTag tag = stack.getOrCreateTag();
        CompoundTag mainTag = tag.getCompound(MAIN_TAG);
        ListTag list = mainTag.getList(type, 10);

        CompoundTag entry = new CompoundTag();
        entry.putString("id", effectId.toString());
        entry.putInt("duration", duration);
        entry.putInt("amplifier", amplifier);
        if (!type.equals("on_use")) {
            entry.putDouble("chance", chance);
            entry.putBoolean("self", self);
        }

        list.add(entry);
        mainTag.put(type, list);
        tag.put(MAIN_TAG, mainTag);

        source.sendSuccess(() -> Component.literal("Efeito adicionado ao item: ").withStyle(ChatFormatting.GREEN)
            .append(Component.literal(effectId.toString()).withStyle(ChatFormatting.YELLOW)), true);

        return 1;
    }

    private static int listEffects(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) throw MUST_BE_PLAYER.create();

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) throw NO_ITEM.create();

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MAIN_TAG, 10)) {
            source.sendSuccess(() -> Component.literal("O item nao possui modificadores de efeito."), false);
            return 0;
        }

        CompoundTag mainTag = tag.getCompound(MAIN_TAG);
        source.sendSuccess(() -> Component.literal("--- Modificadores de Efeito ---").withStyle(ChatFormatting.GOLD), false);

        for (String type : new String[]{"on_use", "on_hit", "on_hurt"}) {
            if (mainTag.contains(type, 9)) {
                ListTag list = mainTag.getList(type, 10);
                String label = type.toUpperCase() + ":";
                source.sendSuccess(() -> Component.literal(label).withStyle(ChatFormatting.YELLOW), false);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entry = list.getCompound(i);
                    int finalI = i;
                    String id = entry.getString("id");
                    int amp = entry.getInt("amplifier") + 1;
                    int dur = entry.getInt("duration") / 20; // Show in seconds
                    if (type.equals("on_use")) {
                        source.sendSuccess(() -> Component.literal("  " + finalI + ": ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(id).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" (Nivel " + amp + ", " + dur + "s)")), false);
                    } else {
                        double chv = entry.getDouble("chance") * 100;
                        boolean selfEntry = entry.getBoolean("self");
                        String targetName = selfEntry ? "SELF" : "TARGET";
                        source.sendSuccess(() -> Component.literal("  " + finalI + ": ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(id).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" (Nivel " + amp + ", " + dur + "s, " + chv + "%, Alvo: " + targetName + ")")), false);
                    }
                }
            }
        }

        return 1;
    }

    private static int removeEffect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) throw MUST_BE_PLAYER.create();

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) throw NO_ITEM.create();

        String type = StringArgumentType.getString(context, "type").toLowerCase();
        int index = IntegerArgumentType.getInteger(context, "index");

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(MAIN_TAG, 10)) return 0;

        CompoundTag mainTag = tag.getCompound(MAIN_TAG);
        if (mainTag.contains(type, 9)) {
            ListTag list = mainTag.getList(type, 10);
            if (index >= 0 && index < list.size()) {
                list.remove(index);
                if (list.isEmpty()) mainTag.remove(type);
                if (mainTag.isEmpty()) tag.remove(MAIN_TAG);
                source.sendSuccess(() -> Component.literal("Efeito removido.").withStyle(ChatFormatting.GREEN), true);
            }
        }

        return 1;
    }

    private static boolean isOnUseSupportedItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == Items.POTION || stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
            return true;
        }

        UseAnim anim = stack.getUseAnimation();
        return stack.isEdible() || anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }
}
