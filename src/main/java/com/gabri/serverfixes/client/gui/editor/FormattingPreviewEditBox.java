package com.gabri.serverfixes.client.gui.editor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("null")
public class FormattingPreviewEditBox extends SelectableEditBox {
    private final List<FormattingSpan> formattings = new ArrayList<>();

    public FormattingPreviewEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        // Avoid the default EditBox cap so lore/name can be long.
        this.setMaxLength(32767);
        this.setFormatter((text, firstCharacterIndex) -> formatVisibleText(text, firstCharacterIndex));
    }

    @Override
    public void setValue(@org.jetbrains.annotations.NotNull String value) {
        super.setValue(value != null ? value : "");
        clampFormattings();
    }

    @Override
    public void insertText(@org.jetbrains.annotations.NotNull String textToWrite) {
        int oldCursorPos = this.getCursorPosition();
        int oldHighlightPos = this.getHighlightPositionRaw();
        String oldText = this.getValue();
        super.insertText(textToWrite);
        onTextUpdate(oldCursorPos, oldHighlightPos, oldText, this.getCursorPosition(), this.getValue());
    }

    @Override
    public void deleteChars(int characterOffset) {
        int oldCursorPos = this.getCursorPosition();
        int oldHighlightPos = this.getHighlightPositionRaw();
        String oldText = this.getValue();
        super.deleteChars(characterOffset);
        onTextUpdate(oldCursorPos, oldHighlightPos, oldText, this.getCursorPosition(), this.getValue());
    }

    public void setLegacyValue(String legacyValue) {
        ParseResult parsed = parseLegacy(legacyValue != null ? legacyValue : "");
        this.formattings.clear();
        this.formattings.addAll(parsed.formattings);
        super.setValue(parsed.text);
        this.setCursorPosition(Math.min(this.getCursorPosition(), this.getValue().length()));
        this.setHighlightPos(this.getCursorPosition());
    }

    public String getLegacyValue() {
        return serializeLegacy(this.getValue(), this.formattings);
    }

    public void applyFormattingToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        String trimmed = token.trim();
        if (isResetToken(trimmed)) {
            clearFormatting();
            return;
        }

        StyleType styleType = parseStyleToken(trimmed);
        if (styleType != null) {
            addStyleFormatting(styleType);
            return;
        }

        String color = parseColorToken(trimmed);
        if (color != null) {
            addColorFormatting(color);
        }
    }

    private FormattedCharSequence formatVisibleText(String text, int firstCharacterIndex) {
        if (text == null || text.isEmpty()) {
            return FormattedCharSequence.EMPTY;
        }
        TextOutputFormatter formatter = new TextOutputFormatter(Component.empty());
        formatter.format(text, firstCharacterIndex, this.formattings);
        return formatter.getText().getVisualOrderText();
    }

    private void onTextUpdate(int oldCursorPos, int oldHighlightPos, String oldText, int newCursorPos, String newText) {
        int oldLength = oldText.length();
        int newLength = newText.length();
        int amount = newLength - oldLength;
        if (amount == 0) {
            return;
        }

        if (oldCursorPos == oldHighlightPos && oldCursorPos != newCursorPos) {
            this.formattings.removeIf(formatting -> {
                if (oldCursorPos <= formatting.start) {
                    formatting.start += amount;
                    formatting.end += amount;
                } else if (oldCursorPos > formatting.start && oldCursorPos <= formatting.end) {
                    formatting.end += amount;
                }
                return formatting.start >= formatting.end;
            });
        } else {
            int pos = Math.min(oldCursorPos, oldHighlightPos);
            this.formattings.removeIf(formatting -> {
                if (pos < formatting.start) {
                    formatting.start += amount;
                    formatting.end += amount;
                } else if (pos >= formatting.start && pos <= formatting.end) {
                    formatting.end += amount;
                }
                return formatting.start >= formatting.end;
            });
        }

        clampFormattings();
    }

    public void removeColorFormatting() {
        int i = this.getCursorPosition();
        int j = this.getHighlightPositionRaw();
        if (i == j) {
            return;
        }
        resizeOtherColorFormattings(new ColorSpan(Math.min(i, j), Math.max(i, j), null), false);
    }

    public void addColorFormatting(String color) {
        int i = this.getCursorPosition();
        int j = this.getHighlightPositionRaw();
        if (i == j) {
            return;
        }

        String normalized = normalizeHex(color);
        if (normalized == null) {
            return;
        }

        ColorSpan formatting = new ColorSpan(Math.min(i, j), Math.max(i, j), normalized);
        if (this.formattings.contains(formatting)) {
            return;
        }

        mergeIdenticalFormattings(ColorSpan.class, other -> Objects.equals(other.color, normalized), formatting);
        resizeOtherColorFormattings(formatting, true);
    }

    public void addStyleFormatting(StyleType target) {
        int i = this.getCursorPosition();
        int j = this.getHighlightPositionRaw();
        if (i == j) {
            return;
        }

        StyleSpan formatting = new StyleSpan(Math.min(i, j), Math.max(i, j), target);
        if (this.formattings.contains(formatting)) {
            this.formattings.remove(formatting);
            return;
        }

        Optional<StyleSpan> surrounding = getSurroundingStyleFormatting(formatting);
        if (surrounding.isPresent()) {
            removeStyleFormatting(formatting, surrounding.get());
        } else {
            mergeIdenticalFormattings(StyleSpan.class, other -> other.type == target, formatting);
            this.formattings.add(formatting);
        }
    }

    public void clearFormatting() {
        int i = this.getCursorPosition();
        int j = this.getHighlightPositionRaw();
        if (i == j) {
            return;
        }
        int start = Math.min(i, j);
        int end = Math.max(i, j);
        resizeOtherColorFormattings(new ColorSpan(start, end, null), false);
        removeStyleFormattingInRange(start, end);
    }

    private void removeStyleFormattingInRange(int start, int end) {
        List<FormattingSpan> added = new ArrayList<>();
        Iterator<FormattingSpan> it = this.formattings.iterator();
        while (it.hasNext()) {
            FormattingSpan formatting = it.next();
            if (!(formatting instanceof StyleSpan style)) {
                continue;
            }
            if (end <= style.start || start >= style.end) {
                continue;
            }
            if (start <= style.start && end >= style.end) {
                it.remove();
                continue;
            }
            if (start <= style.start) {
                style.start = end;
            } else if (end >= style.end) {
                style.end = start;
            } else {
                int originalEnd = style.end;
                style.end = start;
                added.add(new StyleSpan(end, originalEnd, style.type));
            }
            if (style.start >= style.end) {
                it.remove();
            }
        }
        this.formattings.addAll(added);
    }

    private <T extends FormattingSpan> void mergeIdenticalFormattings(Class<T> formattingClass, java.util.function.Predicate<T> identicalPredicate, T formatting) {
        Iterator<FormattingSpan> it = this.formattings.iterator();
        while (it.hasNext()) {
            FormattingSpan current = it.next();
            if (formattingClass.isInstance(current)) {
                T other = formattingClass.cast(current);
                if (identicalPredicate.test(other)) {
                    boolean remove = false;
                    if (other.end >= formatting.start && other.end <= formatting.end) {
                        remove = true;
                        if (other.start < formatting.start) {
                            formatting.start = other.start;
                        }
                    }
                    if (other.start >= formatting.start && other.start <= formatting.end) {
                        remove = true;
                        if (other.end > formatting.end) {
                            formatting.end = other.end;
                        }
                    }
                    if (remove) {
                        it.remove();
                    }
                }
            }
        }
    }

    private void resizeOtherColorFormattings(ColorSpan formatting, boolean add) {
        List<FormattingSpan> addedFormattings = new ArrayList<>();
        if (add) {
            addedFormattings.add(formatting);
        }
        Iterator<FormattingSpan> it = this.formattings.iterator();
        while (it.hasNext()) {
            FormattingSpan current = it.next();
            if (current instanceof ColorSpan other) {
                if (!Objects.equals(other.color, formatting.color)) {
                    if (formatting.start <= other.start && formatting.end >= other.end) {
                        it.remove();
                        continue;
                    }
                    if (formatting.start <= other.start && formatting.end > other.start && formatting.end <= other.end) {
                        other.start = formatting.end;
                    }
                    if (formatting.end >= other.end && formatting.start < other.end && formatting.start >= other.start) {
                        other.end = formatting.start;
                    }
                    if (formatting.start >= other.start && formatting.end > other.start && formatting.end <= other.end) {
                        addedFormattings.add(new ColorSpan(formatting.end, other.end, other.color));
                        other.end = formatting.start;
                    }
                    if (other.start >= other.end) {
                        it.remove();
                    }
                }
            }
        }
        this.formattings.addAll(addedFormattings);
    }

    private Optional<StyleSpan> getSurroundingStyleFormatting(StyleSpan formatting) {
        return this.formattings.stream()
            .filter(StyleSpan.class::isInstance)
            .map(StyleSpan.class::cast)
            .filter(other -> other.type == formatting.type)
            .filter(other -> formatting.start >= other.start && formatting.end <= other.end)
            .findFirst();
    }

    private void removeStyleFormatting(StyleSpan formatting, StyleSpan other) {
        if (formatting.start == other.start) {
            other.start = formatting.end;
        } else if (formatting.end == other.end) {
            other.end = formatting.start;
        } else {
            int otherEnd = other.end;
            other.end = formatting.start;
            formatting.start = formatting.end;
            formatting.end = otherEnd;
            this.formattings.add(formatting);
        }
    }

    private void clampFormattings() {
        int len = this.getValue().length();
        this.formattings.removeIf(formatting -> {
            formatting.start = Math.max(0, Math.min(formatting.start, len));
            formatting.end = Math.max(0, Math.min(formatting.end, len));
            return formatting.start >= formatting.end;
        });
    }

    private static boolean isResetToken(String token) {
        String lower = token.toLowerCase();
        return lower.equals("&r") || lower.equals("§r") || lower.equals("r");
    }

    private static StyleType parseStyleToken(String token) {
        if (token.length() < 2) {
            return null;
        }
        char prefix = token.charAt(0);
        if (prefix != '&' && prefix != '§') {
            return null;
        }
        return switch (Character.toLowerCase(token.charAt(1))) {
            case 'l' -> StyleType.BOLD;
            case 'o' -> StyleType.ITALIC;
            case 'n' -> StyleType.UNDERLINED;
            case 'm' -> StyleType.STRIKETHROUGH;
            case 'k' -> StyleType.OBFUSCATED;
            default -> null;
        };
    }

    private static String parseColorToken(String token) {
        String normalized = token.trim();
        if (normalized.matches("(?i)^#[0-9a-f]{6}$")) {
            return normalized.toLowerCase();
        }
        if (normalized.matches("(?i)^&#[0-9a-f]{6}$")) {
            return ("#" + normalized.substring(2)).toLowerCase();
        }
        if (normalized.length() >= 2 && (normalized.charAt(0) == '&' || normalized.charAt(0) == '§')) {
            return legacyColorCodeToHex(Character.toLowerCase(normalized.charAt(1)));
        }
        return null;
    }

    private static String legacyColorCodeToHex(char c) {
        return switch (c) {
            case '0' -> "#000000";
            case '1' -> "#0000aa";
            case '2' -> "#00aa00";
            case '3' -> "#00aaaa";
            case '4' -> "#aa0000";
            case '5' -> "#aa00aa";
            case '6' -> "#ffaa00";
            case '7' -> "#aaaaaa";
            case '8' -> "#555555";
            case '9' -> "#5555ff";
            case 'a' -> "#55ff55";
            case 'b' -> "#55ffff";
            case 'c' -> "#ff5555";
            case 'd' -> "#ff55ff";
            case 'e' -> "#ffff55";
            case 'f' -> "#ffffff";
            default -> null;
        };
    }

    private static String normalizeHex(String color) {
        if (color == null) {
            return null;
        }
        String trimmed = color.trim();
        if (!trimmed.matches("(?i)^#[0-9a-f]{6}$")) {
            return null;
        }
        return trimmed.toLowerCase();
    }

    private static String serializeLegacy(String text, List<FormattingSpan> formattings) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        StyleState previous = StyleState.empty();

        for (int i = 0; i < text.length(); i++) {
            StyleState current = styleAtIndex(i, formattings);
            if (i == 0) {
                if (!current.isEmpty()) {
                    appendStyleCodes(out, current);
                }
            } else if (!current.equals(previous)) {
                if (current.isEmpty()) {
                    out.append("&r");
                } else {
                    out.append("&r");
                    appendStyleCodes(out, current);
                }
            }

            out.append(text.charAt(i));
            previous = current;
        }

        return out.toString();
    }

    private static StyleState styleAtIndex(int index, List<FormattingSpan> formattings) {
        StyleState state = StyleState.empty();
        for (FormattingSpan formatting : formattings) {
            if (index >= formatting.start && index < formatting.end) {
                formatting.apply(state);
            }
        }
        return state;
    }

    private static void appendStyleCodes(StringBuilder out, StyleState style) {
        if (style.color != null) {
            String legacyCode = hexToLegacy(style.color);
            if (legacyCode != null) {
                out.append('&').append(legacyCode);
            } else {
                out.append("&#").append(style.color.substring(1));
            }
        }
        if (style.bold) out.append("&l");
        if (style.italic) out.append("&o");
        if (style.underlined) out.append("&n");
        if (style.strikethrough) out.append("&m");
        if (style.obfuscated) out.append("&k");
    }

    private static String hexToLegacy(String hex) {
        String color = normalizeHex(hex);
        if (color == null) {
            return null;
        }
        return switch (color) {
            case "#000000" -> "0";
            case "#0000aa" -> "1";
            case "#00aa00" -> "2";
            case "#00aaaa" -> "3";
            case "#aa0000" -> "4";
            case "#aa00aa" -> "5";
            case "#ffaa00" -> "6";
            case "#aaaaaa" -> "7";
            case "#555555" -> "8";
            case "#5555ff" -> "9";
            case "#55ff55" -> "a";
            case "#55ffff" -> "b";
            case "#ff5555" -> "c";
            case "#ff55ff" -> "d";
            case "#ffff55" -> "e";
            case "#ffffff" -> "f";
            default -> null;
        };
    }

    private static ParseResult parseLegacy(String input) {
        String source = input == null ? "" : input;
        StringBuilder plain = new StringBuilder();
        List<StyleState> styles = new ArrayList<>();
        StyleState current = StyleState.empty();

        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if ((c == '&' || c == ChatFormatting.PREFIX_CODE) && i + 1 < source.length()) {
                int consumedHexChain = tryConsumeHexChain(source, i, current);
                if (consumedHexChain > 0) {
                    i += consumedHexChain;
                    continue;
                }

                int consumedShortHex = tryConsumeShortHex(source, i, current);
                if (consumedShortHex > 0) {
                    i += consumedShortHex;
                    continue;
                }

                ChatFormatting formatting = ChatFormatting.getByCode(source.charAt(i + 1));
                if (formatting != null) {
                    current = applyLegacyFormatting(current, formatting);
                    i += 2;
                    continue;
                }
            }

            plain.append(c);
            styles.add(current.copy());
            i++;
        }

        List<FormattingSpan> formattings = buildFormattingSpans(styles);
        return new ParseResult(plain.toString(), formattings);
    }

    private static int tryConsumeShortHex(String source, int index, StyleState state) {
        if (index + 7 >= source.length()) {
            return 0;
        }
        char prefix = source.charAt(index);
        if ((prefix != '&' && prefix != ChatFormatting.PREFIX_CODE) || source.charAt(index + 1) != '#') {
            return 0;
        }
        String hex = source.substring(index + 2, index + 8);
        if (!hex.matches("(?i)^[0-9a-f]{6}$")) {
            return 0;
        }
        state.setColor("#" + hex.toLowerCase());
        state.resetStyles();
        return 8;
    }

    private static int tryConsumeHexChain(String source, int index, StyleState state) {
        if (index + 13 >= source.length()) {
            return 0;
        }
        char prefix = source.charAt(index);
        if ((prefix != '&' && prefix != ChatFormatting.PREFIX_CODE) || Character.toLowerCase(source.charAt(index + 1)) != 'x') {
            return 0;
        }

        StringBuilder hex = new StringBuilder(6);
        int cursor = index + 2;
        for (int k = 0; k < 6; k++) {
            if (cursor + 1 >= source.length()) {
                return 0;
            }
            char p = source.charAt(cursor);
            char h = source.charAt(cursor + 1);
            if ((p != '&' && p != ChatFormatting.PREFIX_CODE) || !isHexChar(h)) {
                return 0;
            }
            hex.append(Character.toLowerCase(h));
            cursor += 2;
        }

        state.setColor("#" + hex);
        state.resetStyles();
        return cursor - index;
    }

    private static boolean isHexChar(char c) {
        char lower = Character.toLowerCase(c);
        return (lower >= '0' && lower <= '9') || (lower >= 'a' && lower <= 'f');
    }

    private static StyleState applyLegacyFormatting(StyleState base, ChatFormatting formatting) {
        StyleState next = base.copy();
        if (formatting == ChatFormatting.RESET) {
            return StyleState.empty();
        }
        if (formatting.isColor()) {
            String color = legacyColorCodeToHex(formatting.getChar());
            if (color != null) {
                next.setColor(color);
                next.resetStyles();
            }
            return next;
        }

        switch (formatting) {
            case BOLD -> next.bold = true;
            case ITALIC -> next.italic = true;
            case UNDERLINE -> next.underlined = true;
            case STRIKETHROUGH -> next.strikethrough = true;
            case OBFUSCATED -> next.obfuscated = true;
            default -> {
            }
        }
        return next;
    }

    private static List<FormattingSpan> buildFormattingSpans(List<StyleState> styles) {
        List<FormattingSpan> formattings = new ArrayList<>();
        addColorSpans(styles, formattings);
        addStyleSpans(styles, StyleType.BOLD, formattings);
        addStyleSpans(styles, StyleType.ITALIC, formattings);
        addStyleSpans(styles, StyleType.UNDERLINED, formattings);
        addStyleSpans(styles, StyleType.STRIKETHROUGH, formattings);
        addStyleSpans(styles, StyleType.OBFUSCATED, formattings);
        return formattings;
    }

    private static void addColorSpans(List<StyleState> styles, List<FormattingSpan> out) {
        int start = -1;
        String current = null;
        for (int i = 0; i <= styles.size(); i++) {
            String color = i < styles.size() ? styles.get(i).color : null;
            if (start < 0 && color != null) {
                start = i;
                current = color;
            } else if (start >= 0 && !Objects.equals(current, color)) {
                out.add(new ColorSpan(start, i, current));
                start = color != null ? i : -1;
                current = color;
            }
        }
    }

    private static void addStyleSpans(List<StyleState> styles, StyleType type, List<FormattingSpan> out) {
        int start = -1;
        for (int i = 0; i <= styles.size(); i++) {
            boolean enabled = i < styles.size() && styleEnabled(styles.get(i), type);
            if (enabled && start < 0) {
                start = i;
            } else if (!enabled && start >= 0) {
                out.add(new StyleSpan(start, i, type));
                start = -1;
            }
        }
    }

    private static boolean styleEnabled(StyleState state, StyleType type) {
        return switch (type) {
            case BOLD -> state.bold;
            case ITALIC -> state.italic;
            case UNDERLINED -> state.underlined;
            case STRIKETHROUGH -> state.strikethrough;
            case OBFUSCATED -> state.obfuscated;
        };
    }

    public enum StyleType {
        BOLD,
        ITALIC,
        UNDERLINED,
        STRIKETHROUGH,
        OBFUSCATED
    }

    private abstract static class FormattingSpan {
        int start;
        int end;

        FormattingSpan(int start, int end) {
            this.start = start;
            this.end = end;
        }

        abstract void apply(MutableComponent text);

        abstract void apply(StyleState state);

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FormattingSpan that = (FormattingSpan) o;
            return start == that.start && end == that.end;
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }
    }

    private static final class ColorSpan extends FormattingSpan {
        String color;

        ColorSpan(int start, int end, String color) {
            super(start, end);
            this.color = color;
        }

        @Override
        void apply(MutableComponent text) {
            if (this.color == null) {
                return;
            }
            TextColor parsedColor = TextColor.parseColor(this.color);
            if (parsedColor != null) {
                text.withStyle(style -> style.withColor(parsedColor));
            }
        }

        @Override
        void apply(StyleState state) {
            if (this.color != null) {
                state.color = this.color;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            ColorSpan colorSpan = (ColorSpan) o;
            return Objects.equals(color, colorSpan.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), color);
        }
    }

    private static final class StyleSpan extends FormattingSpan {
        StyleType type;

        StyleSpan(int start, int end, StyleType type) {
            super(start, end);
            this.type = type;
        }

        @Override
        void apply(MutableComponent text) {
            switch (this.type) {
                case BOLD -> text.withStyle(ChatFormatting.BOLD);
                case ITALIC -> text.withStyle(ChatFormatting.ITALIC);
                case UNDERLINED -> text.withStyle(ChatFormatting.UNDERLINE);
                case STRIKETHROUGH -> text.withStyle(ChatFormatting.STRIKETHROUGH);
                case OBFUSCATED -> text.withStyle(ChatFormatting.OBFUSCATED);
            }
        }

        @Override
        void apply(StyleState state) {
            switch (this.type) {
                case BOLD -> state.bold = true;
                case ITALIC -> state.italic = true;
                case UNDERLINED -> state.underlined = true;
                case STRIKETHROUGH -> state.strikethrough = true;
                case OBFUSCATED -> state.obfuscated = true;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            StyleSpan styleSpan = (StyleSpan) o;
            return type == styleSpan.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), type);
        }
    }

    private static final class StyleState {
        String color;
        boolean bold;
        boolean italic;
        boolean underlined;
        boolean strikethrough;
        boolean obfuscated;

        static StyleState empty() {
            StyleState state = new StyleState();
            state.color = null;
            return state;
        }

        void setColor(String color) {
            this.color = normalizeHex(color);
        }

        void resetStyles() {
            this.bold = false;
            this.italic = false;
            this.underlined = false;
            this.strikethrough = false;
            this.obfuscated = false;
        }

        boolean isEmpty() {
            return color == null && !bold && !italic && !underlined && !strikethrough && !obfuscated;
        }

        StyleState copy() {
            StyleState copy = new StyleState();
            copy.color = this.color;
            copy.bold = this.bold;
            copy.italic = this.italic;
            copy.underlined = this.underlined;
            copy.strikethrough = this.strikethrough;
            copy.obfuscated = this.obfuscated;
            return copy;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StyleState styleState = (StyleState) o;
            return bold == styleState.bold && italic == styleState.italic && underlined == styleState.underlined && strikethrough == styleState.strikethrough && obfuscated == styleState.obfuscated && Objects.equals(color, styleState.color);
        }

        @Override
        public int hashCode() {
            return Objects.hash(color, bold, italic, underlined, strikethrough, obfuscated);
        }
    }

    private static final class ParseResult {
        final String text;
        final List<FormattingSpan> formattings;

        ParseResult(String text, List<FormattingSpan> formattings) {
            this.text = text;
            this.formattings = formattings;
        }
    }

    private static final class TextOutputFormatter {
        private final MutableComponent rootText;
        private int currentFormattingIndex;
        private int previousTextIndex;
        private List<FormattingSpan> currentFormattings;

        TextOutputFormatter(MutableComponent rootText) {
            this.rootText = rootText;
        }

        void format(String text, int firstCharacterIndex, List<FormattingSpan> formattings) {
            initFormattingsForIndex(formattings, firstCharacterIndex);
            int currentTextIndex;
            for (currentTextIndex = 1; currentTextIndex <= text.length(); currentTextIndex++) {
                currentFormattingIndex++;
                List<FormattingSpan> changed = getChangedFormattingsForCurrentIndex(formattings);
                if (changed.isEmpty()) {
                    continue;
                }
                appendText(text.substring(previousTextIndex, currentTextIndex));
                changed.forEach(formatting -> {
                    if (formatting.start == currentFormattingIndex) {
                        currentFormattings.add(formatting);
                    } else {
                        currentFormattings.remove(formatting);
                    }
                });
                previousTextIndex = currentTextIndex;
            }
            if (previousTextIndex != currentTextIndex - 1) {
                appendText(text.substring(previousTextIndex, currentTextIndex - 1));
            }
        }

        private void initFormattingsForIndex(List<FormattingSpan> formattings, int index) {
            currentFormattingIndex = index;
            currentFormattings = formattings.stream()
                .filter(formatting -> formatting.start <= currentFormattingIndex && formatting.end > currentFormattingIndex)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }

        private List<FormattingSpan> getChangedFormattingsForCurrentIndex(List<FormattingSpan> formattings) {
            return formattings.stream()
                .filter(formatting -> formatting.start == currentFormattingIndex || formatting.end == currentFormattingIndex)
                .toList();
        }

        private void appendText(String s) {
            MutableComponent text = Component.literal(s);
            currentFormattings.forEach(formatting -> formatting.apply(text));
            rootText.append(text);
        }

        MutableComponent getText() {
            return rootText;
        }
    }
}