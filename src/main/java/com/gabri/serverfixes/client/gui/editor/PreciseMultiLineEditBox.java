package com.gabri.serverfixes.client.gui.editor;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class PreciseMultiLineEditBox extends MultiLineEditBox {
    private static final long DOUBLE_CLICK_MS = 250L;
    private static final String WORD_SEPARATORS = " \t\n\r.,;:!?()[]{}<>\"'`~@#$%^&*-+=/\\|";

    private final Font textFont;
    private MultilineTextField cachedTextField;
    private Method beginIndexMethod;
    private Method endIndexMethod;
    private boolean draggingSelection;
    private int dragAnchorIndex;
    private long lastClickTime;
    private int lastClickIndex;

    public PreciseMultiLineEditBox(Font font, int x, int y, int width, int height, Component message, Component placeholder) {
        super(font, x, y, width, height, message, placeholder);
        this.textFont = font;
        this.lastClickIndex = -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (!this.withinContentAreaPoint(mouseX, mouseY)) {
            this.draggingSelection = false;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        this.setFocused(true);
        int clickIndex = indexFromMouse(mouseX, mouseY);
        long now = Util.getMillis();
        boolean isDoubleClick = (now - this.lastClickTime) < DOUBLE_CLICK_MS && Math.abs(clickIndex - this.lastClickIndex) <= 1;

        if (isDoubleClick) {
            selectWordAt(clickIndex);
        } else if (Screen.hasShiftDown()) {
            moveCursorKeepingSelection(clickIndex);
        } else {
            setCursorAndClearSelection(clickIndex);
        }

        this.dragAnchorIndex = clickIndex;
        this.draggingSelection = true;
        this.lastClickTime = now;
        this.lastClickIndex = clickIndex;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.draggingSelection) {
            int currentIndex = indexFromMouse(mouseX, mouseY);
            setSelectionRange(this.dragAnchorIndex, currentIndex);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingSelection = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int indexFromMouse(double mouseX, double mouseY) {
        String value = this.getValue();
        MultilineTextField textField = textField();
        if (textField == null) {
            return 0;
        }

        List<LineView> lines = displayLines(textField);
        if (lines.isEmpty()) {
            return 0;
        }

        // Convert click coordinates into logical text-space coordinates, including vertical scroll.
        double yRel = mouseY - this.getY() - this.innerPadding() + this.scrollAmount();
        int lineHeight = Math.max(1, this.textFont.lineHeight);
        int lineIndex = Mth.clamp((int) Math.floor(yRel / lineHeight), 0, lines.size() - 1);

        LineView line = lines.get(lineIndex);
        int begin = Mth.clamp(line.beginIndex(), 0, value.length());
        int end = Mth.clamp(line.endIndex(), begin, value.length());

        double xRel = mouseX - this.getX() - this.innerPadding();
        int xPixels = Math.max(0, (int) Math.floor(xRel));
        String lineText = value.substring(begin, end);
        int charsUntilClick = this.textFont.plainSubstrByWidth(lineText, xPixels).length();

        return Mth.clamp(begin + charsUntilClick, 0, value.length());
    }

    private List<LineView> displayLines(MultilineTextField textField) {
        List<LineView> lines = new ArrayList<>();
        for (Object line : textField.iterateLines()) {
            LineView parsed = toLineView(line);
            if (parsed != null) {
                lines.add(parsed);
            }
        }
        return lines;
    }

    private LineView toLineView(Object rawLine) {
        if (rawLine == null) {
            return null;
        }

        try {
            if (this.beginIndexMethod == null || this.endIndexMethod == null) {
                Class<?> lineClass = rawLine.getClass();
                this.beginIndexMethod = lineClass.getMethod("beginIndex");
                this.endIndexMethod = lineClass.getMethod("endIndex");
            }

            int begin = (Integer) this.beginIndexMethod.invoke(rawLine);
            int end = (Integer) this.endIndexMethod.invoke(rawLine);
            return new LineView(begin, end);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void selectWordAt(int index) {
        String value = this.getValue();
        int length = value.length();
        if (length == 0) {
            setCursorAndClearSelection(0);
            return;
        }

        int pivot = Mth.clamp(index, 0, length - 1);
        if (isWordSeparator(value.charAt(pivot)) && pivot > 0 && !isWordSeparator(value.charAt(pivot - 1))) {
            pivot--;
        }

        if (isWordSeparator(value.charAt(pivot))) {
            setCursorAndClearSelection(index);
            return;
        }

        int start = pivot;
        while (start > 0 && !isWordSeparator(value.charAt(start - 1))) {
            start--;
        }

        int end = pivot + 1;
        while (end < length && !isWordSeparator(value.charAt(end))) {
            end++;
        }

        setSelectionRange(start, end);
    }

    private boolean isWordSeparator(char c) {
        return Character.isWhitespace(c) || WORD_SEPARATORS.indexOf(c) >= 0;
    }

    private void setCursorAndClearSelection(int absoluteIndex) {
        MultilineTextField textField = textField();
        if (textField == null) {
            return;
        }
        int cursor = clampToValueRange(absoluteIndex);
        textField.setSelecting(false);
        textField.seekCursor(Whence.ABSOLUTE, cursor);
    }

    private void moveCursorKeepingSelection(int absoluteIndex) {
        MultilineTextField textField = textField();
        if (textField == null) {
            return;
        }
        int cursor = clampToValueRange(absoluteIndex);
        textField.setSelecting(true);
        textField.seekCursor(Whence.ABSOLUTE, cursor);
    }

    private void setSelectionRange(int startInclusive, int endExclusive) {
        MultilineTextField textField = textField();
        if (textField == null) {
            return;
        }

        int start = clampToValueRange(startInclusive);
        int end = clampToValueRange(endExclusive);

        textField.setSelecting(false);
        textField.seekCursor(Whence.ABSOLUTE, start);
        textField.setSelecting(true);
        textField.seekCursor(Whence.ABSOLUTE, end);
    }

    private int clampToValueRange(int index) {
        return Mth.clamp(index, 0, this.getValue().length());
    }

    private MultilineTextField textField() {
        if (this.cachedTextField != null) {
            return this.cachedTextField;
        }

        try {
            Field field = MultiLineEditBox.class.getDeclaredField("textField");
            field.setAccessible(true);
            Object raw = field.get(this);
            if (raw instanceof MultilineTextField multilineTextField) {
                this.cachedTextField = multilineTextField;
                return this.cachedTextField;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private record LineView(int beginIndex, int endIndex) {
    }
}