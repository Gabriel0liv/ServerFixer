package com.gabri.serverfixes.client.gui.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SuppressWarnings("null")
public final class DisplayEditorFormModel {
    private String customName = "";
    private String customModelData = "";
    private final List<String> loreLines = new ArrayList<>();

    private DisplayEditorFormModel() {
    }

    public static DisplayEditorFormModel fromTag(CompoundTag itemTag) {
        DisplayEditorFormModel model = new DisplayEditorFormModel();
        if (itemTag == null) {
            return model;
        }

        if (itemTag.contains("CustomModelData", Tag.TAG_ANY_NUMERIC)) {
            model.customModelData = String.valueOf(itemTag.getInt("CustomModelData"));
        }

        if (!itemTag.contains(ItemStack.TAG_DISPLAY, Tag.TAG_COMPOUND)) {
            return model;
        }

        CompoundTag displayTag = itemTag.getCompound(ItemStack.TAG_DISPLAY);
        model.customName = decodeDisplayText(displayTag.getString(ItemStack.TAG_DISPLAY_NAME));

        ListTag lore = displayTag.getList("Lore", Tag.TAG_STRING);
        for (int i = 0; i < lore.size(); i++) {
            model.loreLines.add(decodeDisplayText(lore.getString(i)));
        }

        return model;
    }

    public String getCustomName() {
        return this.customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName != null ? customName : "";
    }

    public String getCustomModelData() {
        return this.customModelData;
    }

    public void setCustomModelData(String customModelData) {
        this.customModelData = customModelData != null ? customModelData : "";
    }

    public List<String> getLoreLines() {
        return this.loreLines;
    }

    public void setLoreLine(int index, String value) {
        if (index < 0 || index >= this.loreLines.size()) {
            return;
        }
        this.loreLines.set(index, value != null ? value : "");
    }

    public void addLoreLine(String value) {
        this.loreLines.add(value != null ? value : "");
    }

    public void removeLoreLine(int index) {
        if (index < 0 || index >= this.loreLines.size()) {
            return;
        }
        this.loreLines.remove(index);
    }

    public void applyTo(CompoundTag itemTag) {
        if (itemTag == null) {
            return;
        }

        if (this.customModelData == null || this.customModelData.isBlank()) {
            itemTag.remove("CustomModelData");
        } else {
            try {
                itemTag.putInt("CustomModelData", Integer.parseInt(this.customModelData.trim()));
            } catch (NumberFormatException ignored) {
                // Ignore invalid number input and keep current tag value.
            }
        }

        CompoundTag displayTag = itemTag.contains(ItemStack.TAG_DISPLAY, Tag.TAG_COMPOUND)
            ? itemTag.getCompound(ItemStack.TAG_DISPLAY)
            : new CompoundTag();

        if (this.customName == null || this.customName.isBlank()) {
            displayTag.remove(ItemStack.TAG_DISPLAY_NAME);
        } else {
            displayTag.putString(ItemStack.TAG_DISPLAY_NAME, Component.Serializer.toJson(parseLegacyFormatting(this.customName)));
        }

        if (this.loreLines.isEmpty()) {
            displayTag.remove("Lore");
        } else {
            ListTag lore = new ListTag();
            for (String loreLine : this.loreLines) {
                String line = loreLine != null ? loreLine : "";
                lore.add(StringTag.valueOf(Component.Serializer.toJson(parseLegacyFormatting(line))));
            }
            displayTag.put("Lore", lore);
        }

        if (displayTag.isEmpty()) {
            itemTag.remove(ItemStack.TAG_DISPLAY);
        } else {
            itemTag.put(ItemStack.TAG_DISPLAY, displayTag);
        }
    }

    private static String decodeDisplayText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            MutableComponent parsed = Component.Serializer.fromJson(raw);
            if (parsed != null) {
                return toLegacyFormatting(parsed);
            }
        } catch (Exception ignored) {
        }
        return raw.replace(ChatFormatting.PREFIX_CODE, '&');
    }

    private static MutableComponent parseLegacyFormatting(String input) {
        MutableComponent result = Component.empty();
        Style currentStyle = Style.EMPTY;
        StringBuilder chunk = new StringBuilder();

        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == ChatFormatting.PREFIX_CODE || c == '&') && i + 1 < input.length()) {
                if (!chunk.isEmpty()) {
                    result.append(Component.literal(chunk.toString()).setStyle(currentStyle));
                    chunk.setLength(0);
                }

                int consumedHexChain = tryConsumeHexChain(input, i);
                if (consumedHexChain > 0) {
                    String hex = extractHexFromChain(input, i);
                    if (hex != null) {
                        TextColor parsedColor = TextColor.parseColor(hex);
                        currentStyle = parsedColor != null ? Style.EMPTY.withColor(parsedColor) : Style.EMPTY;
                        i += consumedHexChain;
                        continue;
                    }
                }

                int consumedShortHex = tryConsumeShortHex(input, i);
                if (consumedShortHex > 0) {
                    String hex = "#" + input.substring(i + 2, i + 8).toLowerCase();
                    TextColor parsedColor = TextColor.parseColor(hex);
                    currentStyle = parsedColor != null ? Style.EMPTY.withColor(parsedColor) : Style.EMPTY;
                    i += consumedShortHex;
                    continue;
                }

                ChatFormatting formatting = ChatFormatting.getByCode(input.charAt(i + 1));
                if (formatting != null) {
                    currentStyle = applyFormatting(currentStyle, formatting);
                    i += 2;
                    continue;
                }
            }

            chunk.append(c);
            i++;
        }

        if (!chunk.isEmpty()) {
            result.append(Component.literal(chunk.toString()).setStyle(currentStyle));
        }

        return result;
    }

    private static Style applyFormatting(Style base, ChatFormatting formatting) {
        if (formatting == ChatFormatting.RESET) {
            return Style.EMPTY;
        }
        if (formatting.isColor()) {
            return Style.EMPTY.withColor(formatting);
        }

        if (formatting == ChatFormatting.BOLD) {
            return base.withBold(true);
        }
        if (formatting == ChatFormatting.ITALIC) {
            return base.withItalic(true);
        }
        if (formatting == ChatFormatting.UNDERLINE) {
            return base.withUnderlined(true);
        }
        if (formatting == ChatFormatting.STRIKETHROUGH) {
            return base.withStrikethrough(true);
        }
        if (formatting == ChatFormatting.OBFUSCATED) {
            return base.withObfuscated(true);
        }
        return base;
    }

    private static String toLegacyFormatting(Component component) {
        if (component == null) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        LegacyStyleState[] previous = new LegacyStyleState[] { LegacyStyleState.empty() };
        boolean[] firstSegment = new boolean[] { true };

        component.visit((style, text) -> {
            if (text == null || text.isEmpty()) {
                return Optional.empty();
            }

            LegacyStyleState current = LegacyStyleState.fromStyle(style);
            if (firstSegment[0]) {
                appendStyleCodes(out, current);
                firstSegment[0] = false;
            } else {
                appendStyleTransition(out, previous[0], current);
            }

            out.append(text);
            previous[0] = current;
            return Optional.empty();
        }, Style.EMPTY);

        return out.toString();
    }

    private static void appendStyleTransition(StringBuilder out, LegacyStyleState previous, LegacyStyleState current) {
        if (previous.equals(current)) {
            return;
        }
        if (!previous.isEmpty()) {
            out.append('&').append(ChatFormatting.RESET.getChar());
        }
        appendStyleCodes(out, current);
    }

    private static void appendStyleCodes(StringBuilder out, LegacyStyleState style) {
        TextColor color = style.color();
        if (color != null) {
            ChatFormatting formattingColor = mapColor(color);
            if (formattingColor != null) {
                out.append('&').append(formattingColor.getChar());
            } else {
                String hex = color.serialize();
                if (hex != null && hex.matches("(?i)^#[0-9a-f]{6}$")) {
                    out.append("&#").append(hex.substring(1));
                }
            }
        }

        if (style.bold()) {
            out.append('&').append(ChatFormatting.BOLD.getChar());
        }
        if (style.italic()) {
            out.append('&').append(ChatFormatting.ITALIC.getChar());
        }
        if (style.underlined()) {
            out.append('&').append(ChatFormatting.UNDERLINE.getChar());
        }
        if (style.strikethrough()) {
            out.append('&').append(ChatFormatting.STRIKETHROUGH.getChar());
        }
        if (style.obfuscated()) {
            out.append('&').append(ChatFormatting.OBFUSCATED.getChar());
        }
    }

    private static ChatFormatting mapColor(TextColor color) {
        int rgb = color.getValue();
        for (ChatFormatting formatting : ChatFormatting.values()) {
            Integer formattingColor = formatting.getColor();
            if (formattingColor != null && formattingColor == rgb) {
                return formatting;
            }
        }
        return null;
    }

    private static int tryConsumeShortHex(String input, int index) {
        if (index + 7 >= input.length()) {
            return 0;
        }
        char prefix = input.charAt(index);
        if ((prefix != '&' && prefix != ChatFormatting.PREFIX_CODE) || input.charAt(index + 1) != '#') {
            return 0;
        }
        String hex = input.substring(index + 2, index + 8);
        return hex.matches("(?i)^[0-9a-f]{6}$") ? 8 : 0;
    }

    private static int tryConsumeHexChain(String input, int index) {
        if (index + 13 >= input.length()) {
            return 0;
        }
        char prefix = input.charAt(index);
        if ((prefix != '&' && prefix != ChatFormatting.PREFIX_CODE) || Character.toLowerCase(input.charAt(index + 1)) != 'x') {
            return 0;
        }
        int cursor = index + 2;
        for (int k = 0; k < 6; k++) {
            if (cursor + 1 >= input.length()) {
                return 0;
            }
            char p = input.charAt(cursor);
            char h = input.charAt(cursor + 1);
            if ((p != '&' && p != ChatFormatting.PREFIX_CODE) || !isHexChar(h)) {
                return 0;
            }
            cursor += 2;
        }
        return cursor - index;
    }

    private static String extractHexFromChain(String input, int index) {
        StringBuilder hex = new StringBuilder(6);
        int cursor = index + 2;
        for (int k = 0; k < 6; k++) {
            hex.append(Character.toLowerCase(input.charAt(cursor + 1)));
            cursor += 2;
        }
        return "#" + hex;
    }

    private static boolean isHexChar(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f');
    }

    private record LegacyStyleState(TextColor color,
                                    boolean bold,
                                    boolean italic,
                                    boolean underlined,
                                    boolean strikethrough,
                                    boolean obfuscated) {
        private static LegacyStyleState empty() {
            return new LegacyStyleState(null, false, false, false, false, false);
        }

        private static LegacyStyleState fromStyle(Style style) {
            if (style == null) {
                return empty();
            }
            return new LegacyStyleState(
                style.getColor(),
                Boolean.TRUE.equals(style.isBold()),
                Boolean.TRUE.equals(style.isItalic()),
                Boolean.TRUE.equals(style.isUnderlined()),
                Boolean.TRUE.equals(style.isStrikethrough()),
                Boolean.TRUE.equals(style.isObfuscated())
            );
        }

        private boolean isEmpty() {
            return color == null && !bold && !italic && !underlined && !strikethrough && !obfuscated;
        }
    }
}
