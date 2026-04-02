package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.AbstractEditorScreen;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.SaveItemEditorPacket;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

@SuppressWarnings("null")
public class NbtEditorScreen extends AbstractEditorScreen {
    private static final int OUTER_MARGIN = 8;
    private static final int INNER_MARGIN = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int BAR_HEIGHT = 16;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_GAP = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 24;
    private static final int MAX_LENGTH = 32767;
    private static final double DRAG_SELECTION_THRESHOLD = 2.0D;
    private static final ResourceLocation INDENT_ICON = ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/indent-solid.png");

    private final CompoundTag originalTag;
    private MultiLineEditBox snbtInput;
    private Button saveButton;
    private Button indentButton;
    private Button openItemEditorButton;
    private Button currentSnbtButton;
    private Button copySnbtButton;
    private Button reloadFromHandButton;
    private Component statusMessage = Component.empty();
    private boolean syntaxValid = true;
    private boolean awaitingClickDragDecision;
    private double clickStartX;
    private double clickStartY;
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int headerY;
    private int barY;
    private int editorX;
    private int editorY;
    private int editorW;
    private int editorH;
    private int footerY;
    private int statusY;

    public NbtEditorScreen(CompoundTag sourceTag) {
        super(Component.literal("Editor Raw NBT"));
        this.originalTag = sourceTag != null ? sourceTag.copy() : new CompoundTag();
    }

    @Override
    protected void init() {
        super.init();
        if (this.font == null) return;
        this.clearWidgets();

        this.frameX = OUTER_MARGIN;
        this.frameY = OUTER_MARGIN;
        this.frameW = Math.max(220, this.width - OUTER_MARGIN * 2);
        this.frameH = Math.max(160, this.height - OUTER_MARGIN * 2);
        this.headerY = this.frameY + 6;
        this.barY = this.headerY + HEADER_HEIGHT + 4;
        this.footerY = this.frameY + this.frameH - FOOTER_HEIGHT;
        this.statusY = this.footerY - 12;

        this.editorX = this.frameX + INNER_MARGIN;
        this.editorY = this.barY + BAR_HEIGHT + 6;
        this.editorW = Math.max(120, this.frameW - INNER_MARGIN * 2);
        this.editorH = Math.max(90, this.statusY - this.editorY - 8);

        this.snbtInput = new MultiLineEditBox(this.font, this.editorX, this.editorY, this.editorW, this.editorH,
                Component.literal("SNBT"), Component.empty());
        this.snbtInput.setCharacterLimit(MAX_LENGTH);
        this.snbtInput.setValue(getFormattedSnbt(this.originalTag));
        this.snbtInput.setValueListener(this::validateInput);
        this.addRenderableWidget(this.snbtInput);

        int iconSize = 16;
        int iconX = this.frameX + INNER_MARGIN;
        int iconY = this.barY;
        this.indentButton = new IconButton(iconX, iconY, iconSize, iconSize, INDENT_ICON, 16, 16, 0xFF1E5D85, 0xFF2A7DB3, 0xFF8FD8FF, (b) -> formatInput());
        this.indentButton.setTooltip(Tooltip.create(Component.literal("Identar SNBT")));
        this.addRenderableWidget(this.indentButton);

        int barButtonH = 16;
        int leftBtnW = 78;
        int leftGap = 4;
        int leftBaseX = iconX + iconSize + 6;
        this.openItemEditorButton = Button.builder(Component.literal("Item Editor"), (b) -> openItemEditorFromInput())
            .bounds(leftBaseX, this.barY, leftBtnW, barButtonH).build();
        this.currentSnbtButton = Button.builder(Component.literal("Raw SNBT"), (b) -> {})
            .bounds(leftBaseX + leftBtnW + leftGap, this.barY, leftBtnW, barButtonH).build();
        this.currentSnbtButton.active = false;
        this.addRenderableWidget(this.openItemEditorButton);
        this.addRenderableWidget(this.currentSnbtButton);

        int rightBtnW = 72;
        int rightGap = 4;
        int right2X = this.frameX + this.frameW - INNER_MARGIN - rightBtnW;
        int right1X = right2X - rightGap - rightBtnW;
        this.copySnbtButton = Button.builder(Component.literal("Copiar"), (b) -> copySnbt())
            .bounds(right1X, this.barY, rightBtnW, barButtonH).build();
        this.reloadFromHandButton = Button.builder(Component.literal("Recarregar"), (b) -> reloadFromHand())
            .bounds(right2X, this.barY, rightBtnW, barButtonH).build();
        this.addRenderableWidget(this.copySnbtButton);
        this.addRenderableWidget(this.reloadFromHandButton);

        int totalButtonsW = BUTTON_WIDTH * 2 + BUTTON_GAP;
        int cancelX = this.frameX + (this.frameW - totalButtonsW) / 2;
        int rightX = cancelX + BUTTON_WIDTH + BUTTON_GAP;
        int buttonY = this.footerY;

        this.saveButton = Button.builder(Component.literal("§aSalvar"), btn -> saveTag())
                .bounds(rightX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
        this.addRenderableWidget(this.saveButton);
        this.addRenderableWidget(Button.builder(Component.literal("§cCancelar"), btn -> closeScreen())
                .bounds(cancelX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        validateInput(this.snbtInput.getValue());
    }

    private void saveTag() {
        if (!this.syntaxValid) {
            return;
        }
        try {
            CompoundTag parsed = parseInput();
            NetworkHandler.sendToServer(new SaveItemEditorPacket(normalizeForSave(parsed)));
            closeScreen();
        } catch (CommandSyntaxException ex) {
            this.syntaxValid = false;
            this.statusMessage = Component.literal("Sintaxe inválida: " + ex.getMessage());
            refreshSaveState();
        }
    }

    private void closeScreen() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private CompoundTag parseInput() throws CommandSyntaxException {
        if (this.snbtInput == null) return new CompoundTag();
        String raw = this.snbtInput.getValue();
        if (raw == null || raw.trim().isEmpty()) {
            return new CompoundTag();
        }
        return TagParser.parseTag(raw);
    }

    private CompoundTag normalizeForSave(CompoundTag parsed) {
        if (parsed == null) {
            return new CompoundTag();
        }
        // When user edits full ItemStack SNBT (id/Count/tag), save only the inner "tag"
        // because ServerFixes packet applies data via ItemStack#setTag.
        if (parsed.contains("tag", Tag.TAG_COMPOUND)
                && (parsed.contains("id", Tag.TAG_STRING) || parsed.contains("Count", Tag.TAG_ANY_NUMERIC))) {
            return parsed.getCompound("tag").copy();
        }
        return parsed;
    }

    private void openItemEditorFromInput() {
        if (this.minecraft == null) {
            return;
        }
        try {
            CompoundTag parsed = parseInput();
            CompoundTag itemTag = normalizeForSave(parsed);
            this.minecraft.setScreen(new ItemEditorScreen(itemTag));
        } catch (CommandSyntaxException ex) {
            this.statusMessage = Component.literal("Corrija a sintaxe antes de abrir o Item Editor.");
            this.syntaxValid = false;
            refreshSaveState();
        }
    }

    private void copySnbt() {
        if (this.minecraft == null || this.snbtInput == null) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(this.snbtInput.getValue());
        this.statusMessage = Component.literal("SNBT copiado para a área de transferência.");
    }

    private void reloadFromHand() {
        if (this.minecraft == null || this.minecraft.player == null || this.snbtInput == null) {
            return;
        }
        var stack = this.minecraft.player.getMainHandItem();
        if (stack.isEmpty()) {
            this.statusMessage = Component.literal("Nenhum item na mão principal.");
            return;
        }
        CompoundTag full = stack.save(new CompoundTag());
        this.snbtInput.setValue(getFormattedSnbt(full));
        this.syntaxValid = true;
        this.statusMessage = Component.literal("SNBT recarregado do item na mão.");
        refreshSaveState();
    }

    private void validateInput(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                this.syntaxValid = true;
                this.statusMessage = Component.literal("SNBT vazio -> item ficará sem tag.");
            } else {
                TagParser.parseTag(value);
                this.syntaxValid = true;
                this.statusMessage = Component.literal("Sintaxe válida.");
            }
        } catch (CommandSyntaxException ex) {
            this.syntaxValid = false;
            this.statusMessage = Component.literal("Sintaxe inválida: " + ex.getMessage());
        }
        refreshSaveState();
    }

    private void refreshSaveState() {
        if (this.saveButton != null) {
            this.saveButton.active = this.syntaxValid;
        }
        if (this.indentButton != null) {
            this.indentButton.active = this.syntaxValid;
        }
    }

    private static String getFormattedSnbt(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return "{}";
        }
        try {
            return createFormatter().visit(tag);
        } catch (Exception ignored) {
            return tag.toString();
        }
    }

    private static SnbtPrinterTagVisitor createFormatter() {
        // Matches IBE behavior: mutable path list is required by the formatter internals.
        return new SnbtPrinterTagVisitor("  ", 0, new ArrayList<>());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        renderEditorFrame(graphics, Component.literal("Editor Raw NBT"),
            this.frameX, this.frameY, this.frameW, this.frameH,
            this.headerY, this.barY, this.editorY, this.footerY);

        if (this.snbtInput != null) {
            this.snbtInput.render(graphics, mouseX, mouseY, partialTicks);
        }

        int statusColor = this.syntaxValid ? 0xFF88FF88 : 0xFFFF5555;
        graphics.drawString(this.font, this.statusMessage, this.editorX, this.statusY, statusColor);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (this.snbtInput != null && this.snbtInput.keyPressed(key, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.snbtInput != null && this.snbtInput.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean insideInput = isInsideInput(mouseX, mouseY);
        if (button == 0 && insideInput) {
            this.awaitingClickDragDecision = true;
            this.clickStartX = mouseX;
            this.clickStartY = mouseY;
        }

        boolean res = super.mouseClicked(mouseX, mouseY, button);
        // Match IBE-like UX: plain click moves caret and clears selection unless Shift is held.
        if (button == 0 && insideInput && !Screen.hasShiftDown()) {
            collapseSelectionToCaret();
        }
        return res;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean res = super.mouseDragged(mouseX, mouseY, button, dragX, dragY);

        if (button == 0 && this.awaitingClickDragDecision && !Screen.hasShiftDown() && isInsideInput(mouseX, mouseY)) {
            double movedX = Math.abs(mouseX - this.clickStartX);
            double movedY = Math.abs(mouseY - this.clickStartY);
            if (movedX <= DRAG_SELECTION_THRESHOLD && movedY <= DRAG_SELECTION_THRESHOLD) {
                // Ignore tiny jitter during click; keep caret behavior.
                collapseSelectionToCaret();
            } else {
                // Real drag should keep native selection behavior.
                this.awaitingClickDragDecision = false;
            }
        }

        return res;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.awaitingClickDragDecision = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean isInsideInput(double mouseX, double mouseY) {
        if (this.snbtInput == null) {
            return false;
        }
        int x = this.snbtInput.getX();
        int y = this.snbtInput.getY();
        int w = this.snbtInput.getWidth();
        int h = this.snbtInput.getHeight();
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void collapseSelectionToCaret() {
        if (this.snbtInput == null) {
            return;
        }

        try {
            var textFieldField = MultiLineEditBox.class.getDeclaredField("textField");
            textFieldField.setAccessible(true);
            Object textField = textFieldField.get(this.snbtInput);
            Class<?> textFieldClass = textField.getClass();

            var setSelecting = textFieldClass.getMethod("setSelecting", boolean.class);
            var cursorGetter = textFieldClass.getMethod("cursor");

            Class<?> whenceClass = Class.forName("net.minecraft.client.gui.components.Whence");
            Object absolute = Enum.valueOf((Class<? extends Enum>) whenceClass.asSubclass(Enum.class), "ABSOLUTE");
            var seekCursor = textFieldClass.getMethod("seekCursor", whenceClass, int.class);

            int cursor = (Integer) cursorGetter.invoke(textField);
            setSelecting.invoke(textField, false);
            seekCursor.invoke(textField, absolute, cursor);
        } catch (Exception ignored) {
        }
    }

    private void formatInput() {
        if (this.snbtInput == null) {
            return;
        }

        String raw = this.snbtInput.getValue();
        if (raw == null || raw.trim().isEmpty()) {
            this.snbtInput.setValue("{}");
            this.syntaxValid = true;
            this.statusMessage = Component.literal("SNBT identado com sucesso.");
            refreshSaveState();
            return;
        }

        try {
            String pretty = createFormatter().visit(TagParser.parseTag(raw));
            this.snbtInput.setValue(pretty);
            this.syntaxValid = true;
            this.statusMessage = Component.literal("SNBT identado com sucesso.");
        } catch (CommandSyntaxException ex) {
            this.syntaxValid = false;
            this.statusMessage = Component.literal("Não foi possível identar: sintaxe inválida.");
        }
        refreshSaveState();
    }
}

@SuppressWarnings("null")
class IconButton extends Button {
    private final ResourceLocation tex;
    private final int texW;
    private final int texH;
    private final int bgColor;
    private final int hoverColor;
    private final int borderColor;

    public IconButton(int x, int y, int width, int height, ResourceLocation tex, int texW, int texH, int bgColor, int hoverColor, int borderColor, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, (button) -> Component.empty());
        this.tex = tex;
        this.texW = texW;
        this.texH = texH;
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.borderColor = borderColor;
    }

    @Override
    protected void renderWidget(@org.jetbrains.annotations.NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        int fill = !this.active ? 0xFF38424A : (this.isHoveredOrFocused() ? this.hoverColor : this.bgColor);

        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, this.borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, this.borderColor);
        graphics.fill(x, y, x + 1, y + h, this.borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, this.borderColor);

        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = Math.max(0, w - 2);
        int innerH = Math.max(0, h - 2);

        int iconW = Math.min(innerW, this.texW);
        int iconH = Math.min(innerH, this.texH);
        int ix = innerX + (innerW - iconW) / 2;
        int iy = innerY + (innerH - iconH) / 2;
        try {
            graphics.blit(this.tex, ix, iy, 0, 0, iconW, iconH, this.texW, this.texH);
        } catch (Exception ignored) {}
    }
}