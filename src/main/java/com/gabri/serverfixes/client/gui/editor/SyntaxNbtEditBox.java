package com.gabri.serverfixes.client.gui.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("null")
public class SyntaxNbtEditBox extends PreciseMultiLineEditBox {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"(?:\\\\.|[^\\\"])*\"|'(?:\\\\.|[^'])*'|[-+]?\\d+(?:\\.\\d+)?[bBsSlLfFdD]?|[{}\\[\\],:]|[A-Za-z0-9_:+./-]+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?[bBsSlLfFdD]?");

    private static final int COLOR_DEFAULT = 0xE0E0E0;
    private static final int COLOR_KEY = 0xFFAA00;
    private static final int COLOR_STRING = 0x55FF55;
    private static final int COLOR_NUMBER = 0xFF55FF;
    private static final int COLOR_BRACKET = 0xAAAAAA;

    private static final int SUGGESTION_BG = 0xE01A1A1A;
    private static final int SUGGESTION_BORDER = 0xFF4E6B8D;
    private static final int SUGGESTION_SELECTED = 0x663A84D8;
    private static final int MAX_SUGGESTIONS = 8;

    private static final List<String> ROOT_TAG_SUGGESTIONS = List.of(
        "SF_ItemEffects",
        "AttributeModifiers",
        "IsCurio",
        "BackupModifiers",
        "HideFlags",
        "SF_HideEffectFlags"
    );

    private static final List<String> SF_ITEM_EFFECTS_KEYS = List.of(
        "on_use",
        "on_hit",
        "on_hurt",
        "on_equip"
    );

    private static final List<String> SLOT_SUGGESTIONS = List.of(
        "any",
        "mainhand",
        "offhand",
        "head",
        "chest",
        "legs",
        "feet",
        "curio",
        "ring",
        "necklace",
        "belt",
        "charm",
        "back",
        "body",
        "hands"
    );

    private final List<String> registryKeys = new ArrayList<>();
    private final List<String> suggestions = new ArrayList<>();
    private boolean suggestionsVisible;
    private int selectedSuggestion;
    private int tokenStart;
    private int tokenEnd;

    public SyntaxNbtEditBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height, Component message, Component placeholder) {
        super(font, x, y, width, height, message, placeholder);
        loadRegistryKeys();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = super.charTyped(codePoint, modifiers);
        if (handled) {
            updateSuggestions();
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.suggestionsVisible) {
            if (keyCode == 265) { // UP
                this.selectedSuggestion = (this.selectedSuggestion - 1 + this.suggestions.size()) % this.suggestions.size();
                return true;
            }
            if (keyCode == 264) { // DOWN
                this.selectedSuggestion = (this.selectedSuggestion + 1) % this.suggestions.size();
                return true;
            }
            if (keyCode == 258 || keyCode == 257) { // TAB or ENTER
                acceptSuggestion();
                return true;
            }
            if (keyCode == 256) { // ESC
                hideSuggestions();
                return true;
            }
        }

        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled) {
            updateSuggestions();
        }
        return handled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        updateSuggestions();
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean handled = super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        updateSuggestions();
        return handled;
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderContents(graphics, mouseX, mouseY, partialTick);
        renderSyntaxOverlay(graphics);
    }

    public void renderSuggestionOverlay(GuiGraphics graphics) {
        if (!this.suggestionsVisible || this.suggestions.isEmpty()) {
            return;
        }

        int[] cursorPos = cursorScreenPosition();
        int boxX = cursorPos[0];
        int boxY = cursorPos[1] + lineHeight() + 2;
        int maxWidth = 160;
        for (String s : this.suggestions) {
            maxWidth = Math.max(maxWidth, font().width(s) + 10);
        }

        int listHeight = this.suggestions.size() * 12 + 4;
        int minX = this.getX() + 2;
        int maxX = this.getX() + this.getWidth() - maxWidth - 2;
        boxX = Mth.clamp(boxX, minX, Math.max(minX, maxX));

        int maxY = this.getY() + this.getHeight() - listHeight - 2;
        if (boxY > maxY) {
            boxY = cursorPos[1] - listHeight - 2;
        }
        boxY = Math.max(this.getY() + 2, boxY);

        graphics.fill(boxX, boxY, boxX + maxWidth, boxY + listHeight, SUGGESTION_BG);
        graphics.fill(boxX, boxY, boxX + maxWidth, boxY + 1, SUGGESTION_BORDER);
        graphics.fill(boxX, boxY + listHeight - 1, boxX + maxWidth, boxY + listHeight, SUGGESTION_BORDER);
        graphics.fill(boxX, boxY, boxX + 1, boxY + listHeight, SUGGESTION_BORDER);
        graphics.fill(boxX + maxWidth - 1, boxY, boxX + maxWidth, boxY + listHeight, SUGGESTION_BORDER);

        for (int i = 0; i < this.suggestions.size(); i++) {
            int rowY = boxY + 2 + i * 12;
            if (i == this.selectedSuggestion) {
                graphics.fill(boxX + 1, rowY - 1, boxX + maxWidth - 1, rowY + 10, SUGGESTION_SELECTED);
            }
            graphics.drawString(font(), this.suggestions.get(i), boxX + 5, rowY, 0xFFE8F1FF);
        }
    }

    public void refreshSuggestionsNow() {
        updateSuggestions();
    }

    private void renderSyntaxOverlay(GuiGraphics graphics) {
        List<LineView> lines = getDisplayLines();
        if (lines.isEmpty()) {
            return;
        }

        String value = this.getValue();
        int contentX = this.getX() + this.innerPadding();
        int contentY = this.getY() + this.innerPadding() - (int) this.scrollAmount();
        int lineH = lineHeight();

        int clipX1 = this.getX() + this.innerPadding();
        int clipY1 = this.getY() + this.innerPadding();
        int clipX2 = this.getX() + this.getWidth() - this.innerPadding();
        int clipY2 = this.getY() + this.getHeight() - this.innerPadding();
        graphics.enableScissor(clipX1, clipY1, clipX2, clipY2);

        for (int i = 0; i < lines.size(); i++) {
            int drawY = contentY + i * lineH;
            if (drawY + lineH < clipY1 || drawY > clipY2) {
                continue;
            }

            LineView line = lines.get(i);
            int begin = Mth.clamp(line.beginIndex(), 0, value.length());
            int end = Mth.clamp(line.endIndex(), begin, value.length());
            if (begin == end) {
                continue;
            }

            String text = value.substring(begin, end);
            int x = contentX;
            Matcher matcher = TOKEN_PATTERN.matcher(text);
            int cursor = 0;
            while (matcher.find()) {
                if (matcher.start() > cursor) {
                    String gap = text.substring(cursor, matcher.start());
                    graphics.drawString(font(), gap, x, drawY, COLOR_DEFAULT);
                    x += font().width(gap);
                }

                String token = matcher.group();
                int color = colorForToken(text, token, matcher.end());
                graphics.drawString(font(), token, x, drawY, color);
                x += font().width(token);
                cursor = matcher.end();
            }

            if (cursor < text.length()) {
                String tail = text.substring(cursor);
                graphics.drawString(font(), tail, x, drawY, COLOR_DEFAULT);
            }
        }

        graphics.disableScissor();
    }

    private int colorForToken(String line, String token, int tokenEnd) {
        if (token.length() == 1 && "{}[],:".indexOf(token.charAt(0)) >= 0) {
            return COLOR_BRACKET;
        }
        if ((token.startsWith("\"") && token.endsWith("\"")) || (token.startsWith("'") && token.endsWith("'"))) {
            if (isKeyToken(line, tokenEnd)) {
                return COLOR_KEY;
            }
            return COLOR_STRING;
        }
        if (NUMBER_PATTERN.matcher(token).matches()) {
            return COLOR_NUMBER;
        }
        if (isKeyToken(line, tokenEnd)) {
            return COLOR_KEY;
        }
        return COLOR_DEFAULT;
    }

    private boolean isKeyToken(String line, int tokenEnd) {
        for (int i = tokenEnd; i < line.length(); i++) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == ':';
        }
        return false;
    }

    private void updateSuggestions() {
        String value = this.getValue();
        int cursor = clampToValueRange(cursorIndex());

        if (cursor <= 0 || cursor > value.length()) {
            hideSuggestions();
            return;
        }

        int start = cursor;
        while (start > 0) {
            char c = value.charAt(start - 1);
            if (!isTokenChar(c)) {
                break;
            }
            start--;
        }

        int end = cursor;
        while (end < value.length() && isTokenChar(value.charAt(end))) {
            end++;
        }

        String token = value.substring(start, cursor).toLowerCase(Locale.ROOT);
        if (token.length() < 2) {
            hideSuggestions();
            return;
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (isRootContext(value, cursor)) {
            addMatches(merged, ROOT_TAG_SUGGESTIONS, token);
        }
        if (isInsideSfItemEffects(value, cursor)) {
            addMatches(merged, SF_ITEM_EFFECTS_KEYS, token);
        }
        if (isSlotContext(value, start)) {
            addMatches(merged, SLOT_SUGGESTIONS, token);
        }
        addMatches(merged, this.registryKeys, token);

        this.suggestions.clear();
        for (String candidate : merged) {
            this.suggestions.add(candidate);
            if (this.suggestions.size() >= MAX_SUGGESTIONS) {
                break;
            }
        }

        if (this.suggestions.isEmpty()) {
            hideSuggestions();
            return;
        }

        this.tokenStart = start;
        this.tokenEnd = end;
        this.selectedSuggestion = Mth.clamp(this.selectedSuggestion, 0, this.suggestions.size() - 1);
        this.suggestionsVisible = true;
    }

    private void acceptSuggestion() {
        if (!this.suggestionsVisible || this.suggestions.isEmpty()) {
            return;
        }

        String pick = this.suggestions.get(this.selectedSuggestion);
        replaceRange(this.tokenStart, this.tokenEnd, pick);
        hideSuggestions();
        updateSuggestions();
    }

    private void hideSuggestions() {
        this.suggestionsVisible = false;
        this.suggestions.clear();
        this.selectedSuggestion = 0;
    }

    private boolean isTokenChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '/' || c == '.' || c == '-';
    }

    private void addMatches(Set<String> out, Iterable<String> source, String tokenLower) {
        for (String candidate : source) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.startsWith(tokenLower) || lower.contains(tokenLower)) {
                out.add(candidate);
            }
        }
    }

    private boolean isRootContext(String value, int cursor) {
        return braceDepth(value, cursor) <= 1;
    }

    private boolean isInsideSfItemEffects(String value, int cursor) {
        int keyPos = value.lastIndexOf("SF_ItemEffects", Math.max(0, cursor - 1));
        if (keyPos < 0) {
            return false;
        }
        int objectStart = value.indexOf('{', keyPos);
        if (objectStart < 0 || objectStart >= cursor) {
            return false;
        }
        return braceDepth(value, cursor) > braceDepth(value, objectStart);
    }

    private boolean isSlotContext(String value, int tokenStart) {
        int from = Math.max(0, tokenStart - 64);
        String ctx = value.substring(from, Math.min(value.length(), tokenStart)).toLowerCase(Locale.ROOT);
        return ctx.contains("slot") || ctx.contains("curio") || ctx.contains("equip");
    }

    private int braceDepth(String text, int endExclusive) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;

        int max = Math.min(endExclusive, text.length());
        for (int i = 0; i < max; i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == quote) {
                    inString = false;
                }
                continue;
            }

            if (c == '\"' || c == '\'') {
                inString = true;
                quote = c;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return depth;
    }

    private void loadRegistryKeys() {
        Set<String> unique = new HashSet<>();
        addRegistryKeys(unique, ForgeRegistries.MOB_EFFECTS.getKeys());
        addRegistryKeys(unique, ForgeRegistries.ENCHANTMENTS.getKeys());
        addRegistryKeys(unique, ForgeRegistries.ATTRIBUTES.getKeys());
        addRegistryKeys(unique, ForgeRegistries.ITEMS.getKeys());
        this.registryKeys.addAll(unique);
        this.registryKeys.sort(String::compareTo);
    }

    private void addRegistryKeys(Set<String> out, Iterable<ResourceLocation> keys) {
        for (ResourceLocation key : keys) {
            out.add(key.toString().toLowerCase(Locale.ROOT));
        }
    }
}