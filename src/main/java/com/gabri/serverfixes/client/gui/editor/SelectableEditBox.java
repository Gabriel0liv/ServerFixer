package com.gabri.serverfixes.client.gui.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

@SuppressWarnings("null")
public class SelectableEditBox extends EditBox {
    private static final long DOUBLE_CLICK_WINDOW_MS = 250L;
    private static Field displayPosField;

    private final Font customFont;
    private boolean dragSelecting;
    private int selectionAnchor = -1;
    private int highlightPos = -1;
    private long lastClickTime;
    private boolean invalidState = false;

    // Colors
    private static final int COLOR_BG_BORDERED = 0xFF000000;
    private static final int COLOR_BG_NO_BORDER = 0xFF000000;
    private static final int COLOR_TEXT_DEFAULT = 0xFFE0E0E0;
    private static final int COLOR_TEXT_UNEDITABLE = 0xFF707070;
    private static final int COLOR_BORDER_DEFAULT = 0xFF707070;
    private static final int COLOR_BORDER_FOCUSED = 0xFFA0A0A0;
    private static final int COLOR_INVALID = 0xFFFF5555;
    private static final int COLOR_SELECTION = 0xFF336699;
    private static final int COLOR_SELECTION_INVALID = 0x66FF3333;
    private static final int COLOR_CURSOR_DEFAULT = 0xFFD0D0D0;

    public SelectableEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.customFont = font;
    }

    public void setInvalidState(boolean invalid) {
        this.invalidState = invalid;
    }

    public boolean isInvalidState() {
        return this.invalidState;
    }

    @Override
    public void renderWidget(@NotNull net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) return;

        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        // Background
        graphics.fill(x, y, x + w, y + h, COLOR_BG_BORDERED);

        // Text color
        int textColor = this.invalidState ? COLOR_INVALID : (this.active ? COLOR_TEXT_DEFAULT : COLOR_TEXT_UNEDITABLE);

        String value = this.getValue();
        int displayPos = getDisplayPos();
        displayPos = Mth.clamp(displayPos, 0, value.length());

        // Selection highlight
        int selStart = this.getSelectionStart();
        int selEnd = this.getSelectionEnd();
        boolean hasSelection = selStart != selEnd;

        if (hasSelection) {
            int cStart = Mth.clamp(Math.min(selStart, selEnd), displayPos, value.length());
            int cEnd = Mth.clamp(Math.max(selStart, selEnd), displayPos, value.length());
            if (cStart < cEnd) {
                String visibleText = this.customFont.plainSubstrByWidth(value.substring(displayPos), w - 8);
                int visibleLen = visibleText.length();
                int rStart = Mth.clamp(cStart - displayPos, 0, visibleLen);
                int rEnd = Mth.clamp(cEnd - displayPos, 0, visibleLen);
                if (rStart < rEnd) {
                    String beforeSel = value.substring(displayPos, displayPos + rStart);
                    String selText = value.substring(displayPos + rStart, displayPos + rEnd);
                    int selX = this.customFont.width(beforeSel);
                    int selW = this.customFont.width(selText);
                    int selColor = this.invalidState ? COLOR_SELECTION_INVALID : COLOR_SELECTION;
                    graphics.fill(x + 4 + selX, y + 2, x + 4 + selX + selW, y + h - 2, selColor);
                }
            }
        }

        // Visible text
        String visibleText = this.customFont.plainSubstrByWidth(value.substring(displayPos), w - 8);
        graphics.drawString(this.customFont, visibleText, x + 4, y + (h - 8) / 2, textColor, false);

        // Cursor
        if (this.isFocused()) {
            long now = System.currentTimeMillis();
            boolean blink = (now / 300L) % 2 == 0;
            if (blink) {
                String beforeCursor = value.substring(displayPos, Mth.clamp(this.getCursorPosition(), displayPos, value.length()));
                int cursorX = x + 4 + this.customFont.width(beforeCursor);
                int cursorColor = this.invalidState ? COLOR_INVALID : COLOR_CURSOR_DEFAULT;
                graphics.fill(cursorX, y + 2, cursorX + 1, y + h - 2, cursorColor);
            }
        }

        // Border
        int borderColor = this.invalidState ? COLOR_INVALID : (this.isFocused() ? COLOR_BORDER_FOCUSED : COLOR_BORDER_DEFAULT);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inside = mouseX >= this.getX() && mouseX < this.getX() + this.getWidth()
            && mouseY >= this.getY() && mouseY < this.getY() + this.getHeight();

        boolean handled = super.mouseClicked(mouseX, mouseY, button);

        if (button == 0 && inside && this.isFocused()) {
            int clickedCursor = cursorFromMouseX(mouseX);
            this.setCursorPosition(clickedCursor);

            long now = System.currentTimeMillis();
            boolean isDoubleClick = (now - this.lastClickTime) <= DOUBLE_CLICK_WINDOW_MS;
            this.lastClickTime = now;

            if (isDoubleClick) {
                selectWordAt(clickedCursor);
                this.dragSelecting = false;
                this.selectionAnchor = this.getSelectionStart();
                return true;
            }

            this.dragSelecting = true;
            this.selectionAnchor = clickedCursor;
            if (!Screen.hasShiftDown()) {
                this.setHighlightPos(this.selectionAnchor);
            }
            return true;
        }

        this.dragSelecting = false;
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.dragSelecting && this.isFocused()) {
            int cursor = cursorFromMouseX(mouseX);
            this.setCursorPosition(cursor);
            if (this.selectionAnchor >= 0) {
                this.setHighlightPos(this.selectionAnchor);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragSelecting = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int cursorFromMouseX(double mouseX) {
        String value = this.getValue();
        if (value.isEmpty()) {
            return 0;
        }

        int displayPos = getDisplayPos();
        displayPos = Math.max(0, Math.min(displayPos, value.length()));

        int innerX = this.getX() + 4;
        int innerWidth = Math.max(1, this.getWidth() - 8);
        int relativeX = (int) Math.floor(mouseX) - innerX;

        if (relativeX <= 0) {
            return displayPos;
        }

        String visible = this.customFont.plainSubstrByWidth(value.substring(displayPos), innerWidth);
        if (visible.isEmpty()) {
            return value.length();
        }

        String before = this.customFont.plainSubstrByWidth(visible, relativeX);
        return Math.max(0, Math.min(value.length(), displayPos + before.length()));
    }

    private void selectWordAt(int cursor) {
        String value = this.getValue();
        if (value.isEmpty()) {
            this.setCursorPosition(0);
            this.setHighlightPos(0);
            return;
        }

        int len = value.length();
        int index = Math.max(0, Math.min(cursor, len));
        if (index == len && index > 0) {
            index--;
        }

        if (index < 0 || index >= len || isSeparator(value.charAt(index))) {
            this.setCursorPosition(cursor);
            this.setHighlightPos(cursor);
            return;
        }

        int start = index;
        int end = index + 1;

        while (start > 0 && !isSeparator(value.charAt(start - 1))) {
            start--;
        }
        while (end < len && !isSeparator(value.charAt(end))) {
            end++;
        }

        this.setCursorPosition(end);
        this.setHighlightPos(start);
    }

    private static boolean isSeparator(char c) {
        if (Character.isWhitespace(c)) {
            return true;
        }
        return !(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '/');
    }

    private static int getDisplayPosFromField(EditBox editBox) {
        try {
            if (displayPosField == null) {
                displayPosField = EditBox.class.getDeclaredField("displayPos");
                displayPosField.setAccessible(true);
            }
            return (Integer) displayPosField.get(editBox);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int getDisplayPos() {
        return getDisplayPosFromField(this);
    }

    protected int getDisplayPosValue() {
        return getDisplayPos();
    }

    protected Font getCustomFont() {
        return this.customFont;
    }

    @Override
    public void setHighlightPos(int pos) {
        super.setHighlightPos(pos);
        this.highlightPos = pos;
    }

    public int getSelectionStart() {
        int cursor = this.getCursorPosition();
        int anchor = this.highlightPos >= 0 ? this.highlightPos : this.selectionAnchor;
        return Math.min(cursor, Math.max(0, anchor));
    }

    public int getSelectionEnd() {
        int cursor = this.getCursorPosition();
        int anchor = this.highlightPos >= 0 ? this.highlightPos : this.selectionAnchor;
        return Math.max(cursor, Math.max(0, anchor));
    }

    public int getHighlightPositionRaw() {
        return this.highlightPos >= 0 ? this.highlightPos : this.getCursorPosition();
    }
}