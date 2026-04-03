package com.gabri.serverfixes.client.gui.editor;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@SuppressWarnings("null")
public class SyntaxNbtEditBox extends PreciseMultiLineEditBox {
    private static final int SUGGESTION_BG = 0xE01A1A1A;
    private static final int SUGGESTION_BORDER = 0xFF4E6B8D;
    private static final int SUGGESTION_SELECTED = 0x663A84D8;
    private static final int MAX_SUGGESTIONS = 8;

    private static final List<String> ITEM_ROOT_KEYS = List.of(
        "id",
        "Count",
        "tag"
    );

    private static final List<String> ROOT_TAG_KEYS = List.of(
        "SF_ItemEffects",
        "AttributeModifiers",
        "CurioAttributeModifiers",
        "IsCurio",
        "BackupModifiers",
        "Enchantments",
        "StoredEnchantments",
        "CustomPotionEffects",
        "Potion",
        "display",
        "Unbreakable",
        "CustomModelData",
        "HideFlags",
        "SF_HideEffectFlags"
    );

    private static final List<String> ATTRIBUTE_ENTRY_KEYS = List.of(
        "AttributeName",
        "Name",
        "Amount",
        "Operation",
        "UUID",
        "Slot"
    );

    private static final List<String> ENCHANT_ENTRY_KEYS = List.of(
        "id",
        "lvl"
    );

    private static final List<String> CUSTOM_EFFECT_ENTRY_KEYS = List.of(
        "Id",
        "IdString",
        "Duration",
        "Amplifier",
        "Ambient",
        "ShowParticles",
        "ShowIcon"
    );

    private static final List<String> SF_EFFECT_ENTRY_KEYS = List.of(
        "id",
        "duration",
        "amplifier",
        "chance",
        "self",
        "Slot",
        "Ambient",
        "ShowParticles",
        "ShowIcon"
    );

    private static final List<String> SF_ITEM_EFFECTS_KEYS = List.of(
        "on_use",
        "on_hit",
        "on_hurt",
        "on_equip",
        "on_use_color",
        "on_use_potion_mirror"
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

    private static final Object CACHE_LOCK = new Object();
    private static volatile List<String> cachedAttributeIds = Collections.emptyList();
    private static volatile List<String> cachedEnchantmentIds = Collections.emptyList();
    private static volatile List<String> cachedEffectIds = Collections.emptyList();
    private static volatile boolean cacheReady;

    private final List<String> suggestions = new ArrayList<>();
    private boolean suggestionsVisible;
    private int selectedSuggestion;
    private int tokenStart;
    private int tokenEnd;
    private int suggestionBoxX;
    private int suggestionBoxY;
    private int suggestionBoxWidth;
    private int suggestionBoxHeight;

    public SyntaxNbtEditBox(net.minecraft.client.gui.Font font, int x, int y, int width, int height, Component message, Component placeholder) {
        super(font, x, y, width, height, message, placeholder);
        ensureRegistryCacheLoaded();
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = super.charTyped(codePoint, modifiers);
        if (handled) {
            if (isTokenChar(codePoint)) {
                updateSuggestions();
            } else {
                hideSuggestions();
            }
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
        if (handled && isSuggestionEditKey(keyCode)) {
            updateSuggestions();
        } else if (handled) {
            hideSuggestions();
        }
        return handled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.suggestionsVisible && !isInsideSuggestionBox(mouseX, mouseY)) {
            hideSuggestions();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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

        this.suggestionBoxX = boxX;
        this.suggestionBoxY = boxY;
        this.suggestionBoxWidth = maxWidth;
        this.suggestionBoxHeight = listHeight;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);

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

        graphics.pose().popPose();
    }

    public void refreshSuggestionsNow() {
        updateSuggestions();
    }

    private void updateSuggestions() {
        ensureRegistryCacheLoaded();

        String value = this.getValue();
        int cursor = clampToValueRange(cursorIndex());

        if (cursor < 0 || cursor > value.length()) {
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

        String prefix = value.substring(0, start);
        ContextSnapshot context = ContextSnapshot.scan(prefix);
        String token = value.substring(start, cursor).toLowerCase(Locale.ROOT);
        if (token.isBlank()) {
            hideSuggestions();
            return;
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>();

        if (context.isExpectingKey()) {
            if (context.isTopLevelObject()) {
                addMatches(merged, ITEM_ROOT_KEYS, token);
            } else if (context.isInsideDirectTagObject()) {
                addMatches(merged, ROOT_TAG_KEYS, token);
            }
            if (context.isInsideSfItemEffectsRoot()) {
                addMatches(merged, SF_ITEM_EFFECTS_KEYS, token);
            }
            if (context.isInsideAttributeEntry()) {
                addMatches(merged, ATTRIBUTE_ENTRY_KEYS, token);
            }
            if (context.isInsideEnchantmentEntry()) {
                addMatches(merged, ENCHANT_ENTRY_KEYS, token);
            }
            if (context.isInsideCustomPotionEntry()) {
                addMatches(merged, CUSTOM_EFFECT_ENTRY_KEYS, token);
            }
            if (context.isInsideSfEffectEntry()) {
                addMatches(merged, SF_EFFECT_ENTRY_KEYS, token);
            }
        }

        if (context.isInsideSfItemEffectsRoot()) {
            addMatches(merged, SF_ITEM_EFFECTS_KEYS, token);
        }

        if (context.isAttributeIdValueContext()) {
            addMatches(merged, cachedAttributeIds, token);
        }
        if (context.isEnchantmentIdValueContext()) {
            addMatches(merged, cachedEnchantmentIds, token);
        }
        if (context.isEffectIdValueContext()) {
            addMatches(merged, cachedEffectIds, token);
        }
        if (context.isSlotValueContext()) {
            addMatches(merged, SLOT_SUGGESTIONS, token);
        }

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
        this.tokenStart = 0;
        this.tokenEnd = 0;
        this.suggestionBoxX = 0;
        this.suggestionBoxY = 0;
        this.suggestionBoxWidth = 0;
        this.suggestionBoxHeight = 0;
    }

    private boolean isSuggestionEditKey(int keyCode) {
        return keyCode == 259 // backspace
            || keyCode == 261 // delete
            || (Screen.hasControlDown() && (keyCode == 86 || keyCode == 88)); // ctrl+v / ctrl+x
    }

    private boolean isInsideSuggestionBox(double mouseX, double mouseY) {
        if (!this.suggestionsVisible || this.suggestionBoxWidth <= 0 || this.suggestionBoxHeight <= 0) {
            return false;
        }
        return mouseX >= this.suggestionBoxX
            && mouseX <= this.suggestionBoxX + this.suggestionBoxWidth
            && mouseY >= this.suggestionBoxY
            && mouseY <= this.suggestionBoxY + this.suggestionBoxHeight;
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

    private void ensureRegistryCacheLoaded() {
        if (cacheReady) {
            return;
        }

        synchronized (CACHE_LOCK) {
            if (cacheReady) {
                return;
            }

            List<String> attributes = collectRegistryKeys(ForgeRegistries.ATTRIBUTES.getKeys());
            List<String> enchantments = collectRegistryKeys(ForgeRegistries.ENCHANTMENTS.getKeys());
            List<String> effects = collectRegistryKeys(ForgeRegistries.MOB_EFFECTS.getKeys());

            if (attributes.isEmpty() && enchantments.isEmpty() && effects.isEmpty()) {
                return;
            }

            cachedAttributeIds = attributes;
            cachedEnchantmentIds = enchantments;
            cachedEffectIds = effects;
            cacheReady = true;
        }
    }

    private List<String> collectRegistryKeys(Iterable<ResourceLocation> keys) {
        TreeSet<String> unique = new TreeSet<>();
        for (ResourceLocation key : keys) {
            unique.add(key.toString().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(unique);
    }

    private static String normalizeKeyToken(String token) {
        if (token == null) {
            return "";
        }
        String trimmed = token.trim();
        if (trimmed.length() >= 2) {
            char first = trimmed.charAt(0);
            char last = trimmed.charAt(trimmed.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return trimmed.substring(1, trimmed.length() - 1);
            }
        }
        return trimmed;
    }

    private static String asLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static final class ContextSnapshot {
        private final List<String> path;
        private final boolean expectingKey;
        private final String activeValueKey;

        private ContextSnapshot(List<String> path, boolean expectingKey, String activeValueKey) {
            this.path = path;
            this.expectingKey = expectingKey;
            this.activeValueKey = activeValueKey;
        }

        static ContextSnapshot scan(String prefix) {
            Deque<ContextFrame> stack = new ArrayDeque<>();

            int i = 0;
            while (i < prefix.length()) {
                char c = prefix.charAt(i);

                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }

                if (c == '"' || c == '\'') {
                    int end = i + 1;
                    boolean escaped = false;
                    StringBuilder sb = new StringBuilder();
                    while (end < prefix.length()) {
                        char ch = prefix.charAt(end);
                        if (escaped) {
                            sb.append(ch);
                            escaped = false;
                            end++;
                            continue;
                        }
                        if (ch == '\\') {
                            escaped = true;
                            end++;
                            continue;
                        }
                        if (ch == c) {
                            break;
                        }
                        sb.append(ch);
                        end++;
                    }
                    consumeToken(stack, sb.toString());
                    i = Math.min(end + 1, prefix.length());
                    continue;
                }

                if (c == '{') {
                    openContainer(stack, true);
                    i++;
                    continue;
                }

                if (c == '[') {
                    openContainer(stack, false);
                    i++;
                    continue;
                }

                if (c == '}' || c == ']') {
                    if (!stack.isEmpty()) {
                        stack.pop();
                    }
                    i++;
                    continue;
                }

                if (c == ':') {
                    onColon(stack);
                    i++;
                    continue;
                }

                if (c == ',') {
                    onComma(stack);
                    i++;
                    continue;
                }

                if (isDelimiter(c)) {
                    i++;
                    continue;
                }

                int start = i;
                while (i < prefix.length() && !isDelimiter(prefix.charAt(i))) {
                    i++;
                }
                consumeToken(stack, prefix.substring(start, i));
            }

            if (stack.isEmpty()) {
                return new ContextSnapshot(List.of(), true, null);
            }

            ContextFrame top = stack.peek();
            List<String> path = new ArrayList<>();
            for (ContextFrame frame : stack) {
                if (frame.keyFromParent != null && !frame.keyFromParent.isBlank()) {
                    path.add(frame.keyFromParent);
                }
            }
            Collections.reverse(path);
            return new ContextSnapshot(path, top.object && top.expectingKey, top.object ? top.activeValueKey : null);
        }

        boolean isExpectingKey() {
            return this.expectingKey;
        }


        boolean isTopLevelObject() {
            return this.path.isEmpty();
        }

        boolean isInsideDirectTagObject() {
            return this.path.size() == 1 && "tag".equals(this.path.get(0));
        }

        boolean isInsideSfItemEffectsRoot() {
            return this.path.contains("SF_ItemEffects");
        }

        boolean isInsideAttributeEntry() {
            return this.path.contains("AttributeModifiers")
                || this.path.contains("CurioAttributeModifiers")
                || this.path.contains("BackupModifiers");
        }

        boolean isInsideEnchantmentEntry() {
            return this.path.contains("Enchantments") || this.path.contains("StoredEnchantments");
        }

        boolean isInsideCustomPotionEntry() {
            return this.path.contains("CustomPotionEffects");
        }

        boolean isInsideSfEffectEntry() {
            return this.path.contains("on_use")
                || this.path.contains("on_hit")
                || this.path.contains("on_hurt")
                || this.path.contains("on_equip");
        }

        boolean isAttributeIdValueContext() {
            String key = asLower(this.activeValueKey);
            if (!("attributename".equals(key) || "name".equals(key))) {
                return false;
            }
            return isInsideAttributeEntry();
        }

        boolean isEnchantmentIdValueContext() {
            String key = asLower(this.activeValueKey);
            return "id".equals(key) && isInsideEnchantmentEntry();
        }

        boolean isEffectIdValueContext() {
            String key = asLower(this.activeValueKey);
            if (!("id".equals(key) || "idstring".equals(key))) {
                return false;
            }
            return isInsideSfEffectEntry() || isInsideCustomPotionEntry();
        }

        boolean isSlotValueContext() {
            String key = asLower(this.activeValueKey);
            return "slot".equals(key) || "equipslot".equals(key);
        }

        private static boolean isDelimiter(char c) {
            return Character.isWhitespace(c)
                || c == '{' || c == '}' || c == '[' || c == ']'
                || c == ':' || c == ',' || c == '"' || c == '\'';
        }

        private static void openContainer(Deque<ContextFrame> stack, boolean object) {
            String keyFromParent = null;
            if (!stack.isEmpty()) {
                ContextFrame parent = stack.peek();
                if (parent.object && parent.activeValueKey != null) {
                    keyFromParent = parent.activeValueKey;
                    parent.activeValueKey = null;
                }
            }
            stack.push(new ContextFrame(object, keyFromParent));
        }

        private static void onColon(Deque<ContextFrame> stack) {
            if (stack.isEmpty()) {
                return;
            }
            ContextFrame top = stack.peek();
            if (!top.object || top.pendingKey == null) {
                return;
            }
            top.activeValueKey = top.pendingKey;
            top.pendingKey = null;
        }

        private static void onComma(Deque<ContextFrame> stack) {
            if (stack.isEmpty()) {
                return;
            }
            ContextFrame top = stack.peek();
            if (!top.object) {
                return;
            }
            top.expectingKey = true;
            top.pendingKey = null;
            top.activeValueKey = null;
        }

        private static void consumeToken(Deque<ContextFrame> stack, String token) {
            if (token == null || token.isBlank()) {
                return;
            }

            if (stack.isEmpty()) {
                stack.push(new ContextFrame(true, null));
            }

            ContextFrame top = stack.peek();
            if (!top.object) {
                return;
            }

            if (top.expectingKey) {
                top.pendingKey = normalizeKeyToken(token);
                top.expectingKey = false;
                return;
            }

            if (top.activeValueKey != null) {
                top.activeValueKey = null;
            }
        }
    }

    private static final class ContextFrame {
        private final boolean object;
        private final String keyFromParent;
        private boolean expectingKey;
        private String pendingKey;
        private String activeValueKey;

        private ContextFrame(boolean object, String keyFromParent) {
            this.object = object;
            this.keyFromParent = keyFromParent;
            this.expectingKey = object;
        }
    }
}