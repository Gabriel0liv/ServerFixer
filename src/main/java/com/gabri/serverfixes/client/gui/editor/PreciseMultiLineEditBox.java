package com.gabri.serverfixes.client.gui.editor;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@SuppressWarnings("null")
public class PreciseMultiLineEditBox extends MultiLineEditBox {
    private static final long DOUBLE_CLICK_MS = 250L;
    private static final String WORD_SEPARATORS = " \t\n\r.,;:!?()[]{}<>\"'`~@#$%^&*-+=/\\|";

    private final Font textFont;
    private MultilineTextField cachedTextField;
    private Method beginIndexMethod;
    private Method endIndexMethod;
    private Function<String, FormattedCharSequence> lineFormatter;
    private boolean draggingSelection;
    private int dragAnchorIndex;
    private long lastClickTime;
    private int lastClickIndex;

    public PreciseMultiLineEditBox(Font font, int x, int y, int width, int height, Component message, Component placeholder) {
        super(font, x, y, width, height, message, placeholder);
        this.textFont = font;
        this.lastClickIndex = -1;
        this.lineFormatter = line -> Component.literal(line).getVisualOrderText();
    }

    public void setLineFormatter(Function<String, FormattedCharSequence> formatter) {
        if (formatter != null) {
            this.lineFormatter = formatter;
        }
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

    @Override
    protected void renderContents(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MultilineTextField textField = textField();
        if (textField == null) {
            return;
        }

        String value = this.getValue();
        List<LineView> lines = getDisplayLines(textField);
        if (lines.isEmpty()) {
            lines = List.of(new LineView(0, 0));
        }

        int contentX = this.getX() + this.innerPadding();
        int contentY = this.getY() + this.innerPadding();
        int lineH = lineHeight();

        int logicalClipY1 = this.getY() + (int) this.scrollAmount();
        int logicalClipY2 = logicalClipY1 + this.getHeight();

        LineView selection = textField.hasSelection() ? toLineView(textField.getSelected()) : null;
        int selStart = selection != null ? Math.min(selection.beginIndex(), selection.endIndex()) : -1;
        int selEnd = selection != null ? Math.max(selection.beginIndex(), selection.endIndex()) : -1;

        for (int i = 0; i < lines.size(); i++) {
            LineView line = lines.get(i);
            int drawY = contentY + i * lineH;
            if (drawY + lineH < logicalClipY1 || drawY > logicalClipY2) {
                continue;
            }

            int begin = Mth.clamp(line.beginIndex(), 0, value.length());
            int end = Mth.clamp(line.endIndex(), begin, value.length());
            String lineText = value.substring(begin, end);

            if (selection != null && selEnd > begin && selStart < end) {
                int rangeStart = Mth.clamp(selStart, begin, end);
                int rangeEnd = Mth.clamp(selEnd, begin, end);
                if (rangeEnd > rangeStart) {
                    int sx1 = contentX + this.textFont.width(lineText.substring(0, rangeStart - begin));
                    int sx2 = contentX + this.textFont.width(lineText.substring(0, rangeEnd - begin));
                    graphics.fill(sx1, drawY, sx2, drawY + lineH, 0x663A84D8);
                }
            }

            FormattedCharSequence formatted = this.lineFormatter.apply(lineText);
            graphics.drawString(this.textFont, formatted != null ? formatted : Component.literal(lineText).getVisualOrderText(), contentX, drawY, 0xFFFFFFFF);
        }

        if (this.isFocused() && (Util.getMillis() / 500L) % 2L == 0L) {
            int cursor = clampToValueRange(textField.cursor());
            int lineIndex = textField.getLineAtCursor();
            LineView cursorLine = toLineView(textField.getLineView(lineIndex));
            if (cursorLine != null) {
                int begin = Mth.clamp(cursorLine.beginIndex(), 0, value.length());
                int colEnd = Mth.clamp(cursor, begin, value.length());
                String linePrefix = value.substring(begin, colEnd);
                int cx = contentX + this.textFont.width(linePrefix);
                int cy = contentY + lineIndex * lineH;
                graphics.fill(cx, cy, cx + 1, cy + lineH, 0xFFD0D0D0);
            }
        }
    }

    protected int indexFromMouse(double mouseX, double mouseY) {
        String value = this.getValue();
        MultilineTextField textField = textField();
        if (textField == null) {
            return 0;
        }

        List<LineView> lines = getDisplayLines(textField);
        if (lines.isEmpty()) {
            return 0;
        }

        // Step A: Y relative to widget top.
        double relativeY = mouseY - this.getY();
        // Step B: subtract top padding.
        relativeY -= this.innerPadding();
        // Step C: include current scroll offset.
        relativeY += this.scrollAmount();
        // Step D/E: convert to clicked line and clamp to valid visible line range.
        int clickedLineIndex = (int) Math.floor(relativeY / Math.max(1, this.textFont.lineHeight));
        int lineIndex = Mth.clamp(clickedLineIndex, 0, lines.size() - 1);

        LineView line = lines.get(lineIndex);
        int begin = Mth.clamp(line.beginIndex(), 0, value.length());
        int end = Mth.clamp(line.endIndex(), begin, value.length());

        double xRel = mouseX - this.getX() - this.innerPadding();
        int xPixels = Math.max(0, (int) Math.floor(xRel));
        String lineText = value.substring(begin, end);
        int charsUntilClick = this.textFont.plainSubstrByWidth(lineText, xPixels).length();

        return Mth.clamp(begin + charsUntilClick, 0, value.length());
    }

    protected List<LineView> getDisplayLines() {
        MultilineTextField textField = textField();
        if (textField == null) {
            return List.of();
        }
        return getDisplayLines(textField);
    }

    protected List<LineView> getDisplayLines(MultilineTextField textField) {
        List<LineView> lines = new ArrayList<>();
        for (Object line : textField.iterateLines()) {
            LineView parsed = toLineView(line);
            if (parsed != null) {
                lines.add(parsed);
            }
        }
        return lines;
    }

    protected LineView toLineView(Object rawLine) {
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

    protected void setCursorAndClearSelection(int absoluteIndex) {
        MultilineTextField textField = textField();
        if (textField == null) {
            return;
        }
        int cursor = clampToValueRange(absoluteIndex);
        textField.setSelecting(false);
        textField.seekCursor(Whence.ABSOLUTE, cursor);
    }

    protected void moveCursorKeepingSelection(int absoluteIndex) {
        MultilineTextField textField = textField();
        if (textField == null) {
            return;
        }
        int cursor = clampToValueRange(absoluteIndex);
        textField.setSelecting(true);
        textField.seekCursor(Whence.ABSOLUTE, cursor);
    }

    protected void setSelectionRange(int startInclusive, int endExclusive) {
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

    protected int clampToValueRange(int index) {
        return Mth.clamp(index, 0, this.getValue().length());
    }

    protected int lineHeight() {
        return Math.max(1, this.textFont.lineHeight);
    }

    protected int cursorIndex() {
        MultilineTextField textField = textField();
        return textField != null ? textField.cursor() : 0;
    }

    protected Font font() {
        return this.textFont;
    }

    protected int[] cursorScreenPosition() {
        String value = this.getValue();
        MultilineTextField textField = textField();
        if (textField == null) {
            return new int[] {this.getX() + this.innerPadding(), this.getY() + this.innerPadding()};
        }

        int cursor = clampToValueRange(textField.cursor());
        int lineIndex = textField.getLineAtCursor();
        Object rawLine = textField.getLineView(lineIndex);
        LineView line = toLineView(rawLine);
        if (line == null) {
            return new int[] {this.getX() + this.innerPadding(), this.getY() + this.innerPadding()};
        }

        int begin = Mth.clamp(line.beginIndex(), 0, value.length());
        int colEnd = Mth.clamp(cursor, begin, value.length());
        String linePrefix = value.substring(begin, colEnd);

        int x = this.getX() + this.innerPadding() + this.textFont.width(linePrefix);
        int y = this.getY() + this.innerPadding() - (int) this.scrollAmount() + (lineIndex * lineHeight());
        return new int[] {x, y};
    }

    protected void replaceRange(int startInclusive, int endExclusive, String replacement) {
        String value = this.getValue();
        int start = clampToValueRange(startInclusive);
        int end = clampToValueRange(endExclusive);
        if (end < start) {
            int swap = start;
            start = end;
            end = swap;
        }

        String prefix = value.substring(0, start);
        String suffix = value.substring(end);
        String insert = replacement != null ? replacement : "";
        this.setValue(prefix + insert + suffix);
        setCursorAndClearSelection(start + insert.length());
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

    protected record LineView(int beginIndex, int endIndex) {
    }
}