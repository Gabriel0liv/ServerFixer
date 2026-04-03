package com.gabri.serverfixes.commands;

import com.gabri.serverfixes.config.ServerFixesConfig;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;
import java.util.Locale;

/**
 * Advanced attribute editor for items with Archlight persistence.
 * Provides a chat-based UI and a hidden backup system.
 */
@SuppressWarnings("all")
public class ItemAttributeCommands {

    private static final SimpleCommandExceptionType MUST_BE_PLAYER = new SimpleCommandExceptionType(Component.literal("Este comando só pode ser usado por jogadores."));
    private static final SimpleCommandExceptionType NO_ITEM = new SimpleCommandExceptionType(Component.literal("Você precisa estar segurando um item na mão principal."));
    public static final String BACKUP_TAG = "SF_BackupAttributes";
    public static final String CURIOS_TAG = "CurioAttributeModifiers";

    public static void register(LiteralArgumentBuilder<CommandSourceStack> parent, net.minecraft.commands.CommandBuildContext buildContext) {
        parent.then(Commands.literal("item")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("attribute")
                .then(Commands.literal("list")
                    .executes(ItemAttributeCommands::listAttributes))
                .then(Commands.literal("remove")
                    .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemaining().toLowerCase();
                            ForgeRegistries.ATTRIBUTES.getKeys().forEach(attrId -> {
                                if (attrId.toString().contains(input)) builder.suggest(attrId.toString());
                            });
                            return builder.buildFuture();
                        })
                        .executes(ctx -> removeAttribute(ctx, ResourceArgument.getAttribute(ctx, "attribute").value()))))
                .then(Commands.literal("set")
                    .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemaining().toLowerCase();
                            ForgeRegistries.ATTRIBUTES.getKeys().forEach(attrId -> {
                                if (attrId.toString().contains(input)) builder.suggest(attrId.toString());
                            });
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("operation", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("ADDITION");
                                    builder.suggest("MULTIPLY_BASE");
                                    builder.suggest("MULTIPLY_TOTAL");
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("slot", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        String input = builder.getRemaining().toLowerCase();
                                        for (EquipmentSlot slot : EquipmentSlot.values()) {
                                            if (slot.getName().contains(input)) builder.suggest(slot.getName());
                                        }
                                        if ("any".contains(input)) builder.suggest("any");
                                        if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
                                            try {
                                                top.theillusivec4.curios.api.CuriosApi.getSlots(false).keySet().forEach(slotId -> {
                                                    if (slotId.contains(input)) builder.suggest(slotId);
                                                });
                                            } catch (Exception ignored) {}
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ItemAttributeCommands::setAttribute))))))));
    }

    private static int listAttributes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) throw MUST_BE_PLAYER.create();

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) throw NO_ITEM.create();

        source.sendSuccess(() -> Component.literal("=== Atributos do Item ===").withStyle(ChatFormatting.GOLD), false);

        boolean found = false;
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            // List Vanilla/Standard
            if (tag.contains("AttributeModifiers", 9)) {
                ListTag list = tag.getList("AttributeModifiers", 10);
                for (int i = 0; i < list.size(); i++) {
                    displayModifier(source, list.getCompound(i), false);
                    found = true;
                }
            }
            // List Curios
            if (tag.contains(CURIOS_TAG, 9)) {
                ListTag list = tag.getList(CURIOS_TAG, 10);
                for (int i = 0; i < list.size(); i++) {
                    displayModifier(source, list.getCompound(i), true);
                    found = true;
                }
            }
        }

        if (!found) {
            source.sendSuccess(() -> Component.literal("Nenhum atributo encontrado.").withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static void displayModifier(CommandSourceStack source, CompoundTag modifierTag, boolean isCurio) {
        String attrId = modifierTag.getString("AttributeName");
        double amount = modifierTag.getDouble("Amount");
        int op = modifierTag.getInt("Operation");
        String slot = modifierTag.contains("Slot") ? modifierTag.getString("Slot") : "any";
        
        String opName = op == 0 ? "Soma" : (op == 1 ? "Mult. Base" : "Mult. Total");
        
        MutableComponent line = Component.literal("- ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(attrId).withStyle(ChatFormatting.YELLOW));

        if (isCurio) {
            line.append(Component.literal(" [CURIOS]").withStyle(ChatFormatting.AQUA));
        }

        String formattedAmount = String.format(Locale.US, "%.2f", amount);
        line.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(formattedAmount).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" (" + opName + ")").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(" em " + slot).withStyle(ChatFormatting.BLUE));

        String suggestCmd = String.format(Locale.US, "/serverfixes item attribute set %s %s %s %s", 
            attrId, formattedAmount, op == 0 ? "ADDITION" : (op == 1 ? "MULTIPLY_BASE" : "MULTIPLY_TOTAL"), slot);
        
        line.withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestCmd))
                           .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Clique para editar este atributo"))));
        
        source.sendSuccess(() -> line, false);
    }

    private static int setAttribute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) throw MUST_BE_PLAYER.create();

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) throw NO_ITEM.create();

        Attribute attribute = ResourceArgument.getAttribute(context, "attribute").value();
        ResourceLocation attrId = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        if (attrId == null) return 0;

        double value = DoubleArgumentType.getDouble(context, "value");
        String opStr = StringArgumentType.getString(context, "operation").toUpperCase();
        String slotStr = StringArgumentType.getString(context, "slot").toLowerCase();

        AttributeModifier.Operation op = AttributeModifier.Operation.valueOf(opStr);
        
        // Remove existing first to avoid duplicates
        removeAttributeInternal(stack, attrId);

        // Determine if it's a Curios slot or Vanilla slot
        boolean isCurio = !isVanillaSlot(slotStr) && !slotStr.equals("any");

        CompoundTag tag = stack.getOrCreateTag();
        String tagName = isCurio ? CURIOS_TAG : "AttributeModifiers";
        ListTag list = tag.getList(tagName, 10);
        
        UUID uuid = UUID.randomUUID();
        CompoundTag modTag = new CompoundTag();
        modTag.putString("AttributeName", attrId.toString());
        modTag.putDouble("Amount", value);
        modTag.putInt("Operation", op.ordinal());
        modTag.putUUID("UUID", uuid);
        modTag.putString("Name", attrId.toString()); // Replicating user's example Name=AttributeName
        if (!slotStr.equals("any")) modTag.putString("Slot", slotStr);
        
        list.add(modTag);
        tag.put(tagName, list);

        // Add to backup tag for persistence
        if (ServerFixesConfig.ENABLE_PERSISTENT_ATTRIBUTES.get()) {
            ListTag backupList = tag.contains(BACKUP_TAG, 9) ? tag.getList(BACKUP_TAG, 10).copy() : new ListTag();
            CompoundTag backupEntry = modTag.copy();
            backupEntry.putBoolean("IsCurio", isCurio); // Marker for restoration
            backupList.add(backupEntry);
            tag.put(BACKUP_TAG, backupList);
        }

        source.sendSuccess(() -> Component.literal("Atributo ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(attrId.toString()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" definido para " + slotStr).withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.format(Locale.US, " : %.2f", value)).withStyle(ChatFormatting.WHITE)), true);

        return 1;
    }

    private static boolean isVanillaSlot(String slot) {
        for (EquipmentSlot eSlot : EquipmentSlot.values()) {
            if (eSlot.getName().equalsIgnoreCase(slot)) return true;
        }
        return false;
    }

    private static int removeAttribute(CommandContext<CommandSourceStack> context, Attribute attribute) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof Player player)) throw MUST_BE_PLAYER.create();

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) throw NO_ITEM.create();

        ResourceLocation attrId = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        if (attrId == null) return 0;

        removeAttributeInternal(stack, attrId);

        source.sendSuccess(() -> Component.literal("Atributo ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(attrId.toString()).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" removido.").withStyle(ChatFormatting.GRAY)), true);

        return 1;
    }

    private static void removeAttributeInternal(ItemStack stack, ResourceLocation attrId) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;

        // Remove from standard tag
        if (tag.contains("AttributeModifiers", 9)) {
            ListTag list = tag.getList("AttributeModifiers", 10);
            list.removeIf(nbt -> nbt instanceof CompoundTag cTag && cTag.getString("AttributeName").equals(attrId.toString()));
            if (list.isEmpty()) tag.remove("AttributeModifiers");
        }

        // Remove from Curios tag
        if (tag.contains(CURIOS_TAG, 9)) {
            ListTag list = tag.getList(CURIOS_TAG, 10);
            list.removeIf(nbt -> nbt instanceof CompoundTag cTag && cTag.getString("AttributeName").equals(attrId.toString()));
            if (list.isEmpty()) tag.remove(CURIOS_TAG);
        }

        // Remove from backup tag
        if (tag.contains(BACKUP_TAG, 9)) {
            ListTag list = tag.getList(BACKUP_TAG, 10);
            list.removeIf(nbt -> nbt instanceof CompoundTag cTag && cTag.getString("AttributeName").equals(attrId.toString()));
            if (list.isEmpty()) tag.remove(BACKUP_TAG);
        }
    }
}
