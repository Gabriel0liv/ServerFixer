package com.gabri.serverfixes.client.gui.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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

    public SelectableEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.customFont = font;
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