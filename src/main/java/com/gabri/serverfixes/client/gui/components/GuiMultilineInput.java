package com.gabri.serverfixes.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Componente de entrada de texto multilinha com configuração flexível por instância.
 * Permite customizar cores, fontes, espaçamentos e comportamento.
 */
public class GuiMultilineInput extends AbstractWidget {
    // Defaults
    private static final int DEFAULT_LINE_HEIGHT = 12;
    private static final int DEFAULT_PADDING = 6;
    private static final int DEFAULT_SCROLLBAR_WIDTH = 3;
    private static final int DEFAULT_BORDER_COLOR = 0xFF4E6B8D;
    private static final int DEFAULT_BG_COLOR = 0xAA1C2532;
    private static final int DEFAULT_CURSOR_COLOR = 0xFFFFFFFF;
    private static final int DEFAULT_SELECTION_COLOR = 0x443ACAFF;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;

    // Configuração por instância
    private int lineHeight = DEFAULT_LINE_HEIGHT;
    private int padding = DEFAULT_PADDING;
    private int scrollbarWidth = DEFAULT_SCROLLBAR_WIDTH;
    private int borderColor = DEFAULT_BORDER_COLOR;
    private int bgColor = DEFAULT_BG_COLOR;
    private int cursorColor = DEFAULT_CURSOR_COLOR;
    private int selectionColor = DEFAULT_SELECTION_COLOR;
    private int textColor = DEFAULT_TEXT_COLOR;
    private int scrollbarColor = 0xFF3ACAFF;

    // Estado
    private final Font font;
    private final List<String> lines = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxLines;
    private boolean isDragging = false;
    private boolean isFocused = false;

    // Cursor state
    private int cursorLine = 0;
    private int cursorPos = 0;
    private long lastCursorBlink = 0;

    // Selection state
    private int selectionStart = -1;

    private OnTextChangedListener onTextChanged;

    public interface OnTextChangedListener {
        void onChanged(List<String> lines);
    }

    public GuiMultilineInput(int x, int y, int width, int height, int maxLines, List<String> initialLines) {
        super(x, y, width, height, Component.empty());
        this.font = Minecraft.getInstance().font;
        this.maxLines = maxLines;
        if (initialLines != null && !initialLines.isEmpty()) {
            lines.addAll(initialLines);
        } else {
            lines.add("");
        }
    }

    // Métodos Fluentes para Configuração
    public GuiMultilineInput withColors(int border, int bg, int text, int cursor, int selection) {
        this.borderColor = border;
        this.bgColor = bg;
        this.textColor = text;
        this.cursorColor = cursor;
        this.selectionColor = selection;
        return this;
    }

    public GuiMultilineInput withLineHeight(int height) {
        this.lineHeight = height;
        return this;
    }

    public GuiMultilineInput withPadding(int padding) {
        this.padding = padding;
        return this;
    }

    public GuiMultilineInput withScrollbarWidth(int width) {
        this.scrollbarWidth = width;
        return this;
    }

    public GuiMultilineInput withScrollbarColor(int color) {
        this.scrollbarColor = color;
        return this;
    }

    public GuiMultilineInput withStylePreset(StylePreset preset) {
        return switch (preset) {
            case DEFAULT -> this;
            case DARK -> withColors(0xFF333333, 0xFF000000, 0xFFCCCCCC, 0xFFFFFFFF, 0x44FFFFFF);
            case LIGHT -> withColors(0xFFCCCCCC, 0xFFF5F5F5, 0xFF333333, 0xFF000000, 0x440000FF);
            case CODE -> withColors(0xFF555555, 0xFF1E1E1E, 0xFFD4D4D4, 0xFFAEAFAD, 0x44569CD6);
            case REDSTONE -> withColors(0xFFFF0000, 0x44110000, 0xFFFFAAAA, 0xFFFFFFFF, 0x44FF0000);
        };
    }

    public void setOnTextChanged(OnTextChangedListener listener) {
        this.onTextChanged = listener;
    }

    public List<String> getLines() {
        return new ArrayList<>(lines);
    }

    public void setLines(List<String> newLines) {
        lines.clear();
        lines.addAll(newLines.isEmpty() ? List.of("") : newLines);
        clampCursor();
        if (onTextChanged != null) onTextChanged.onChanged(lines);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        isFocused = true;
        setFocused(true);

        int clickedLine = (int) ((mouseY - getY() - padding) / lineHeight) + scrollOffset;
        clickedLine = Math.max(0, Math.min(lines.size() - 1, clickedLine));
        cursorLine = clickedLine;
        String line = lines.get(cursorLine);

        int charX = (int) (mouseX - getX() - padding);
        cursorPos = 0;
        int currentX = 0;
        for (int i = 0; i < line.length(); i++) {
            int charWidth = font.width(String.valueOf(line.charAt(i)));
            if (currentX + charWidth / 2 > charX) {
                cursorPos = i;
                break;
            }
            currentX += charWidth;
            cursorPos = i + 1;
        }
        cursorPos = Math.max(0, Math.min(line.length(), cursorPos));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isFocused) return false;

        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (cursorLine > 0) { cursorLine--; cursorPos = Math.min(cursorPos, lines.get(cursorLine).length()); }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (cursorLine < lines.size() - 1) { cursorLine++; cursorPos = Math.min(cursorPos, lines.get(cursorLine).length()); }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (cursorPos > 0) cursorPos--;
            else if (cursorLine > 0) { cursorLine--; cursorPos = lines.get(cursorLine).length(); }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            String line = lines.get(cursorLine);
            if (cursorPos < line.length()) cursorPos++;
            else if (cursorLine < lines.size() - 1) { cursorLine++; cursorPos = 0; }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (lines.size() < maxLines) {
                String line = lines.get(cursorLine);
                lines.set(cursorLine, line.substring(0, cursorPos));
                lines.add(cursorLine + 1, line.substring(cursorPos));
                cursorLine++; cursorPos = 0;
                if (onTextChanged != null) onTextChanged.onChanged(lines);
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorPos > 0) {
                String line = lines.get(cursorLine);
                lines.set(cursorLine, line.substring(0, cursorPos - 1) + line.substring(cursorPos));
                cursorPos--;
                if (onTextChanged != null) onTextChanged.onChanged(lines);
            } else if (cursorLine > 0) {
                String prev = lines.get(cursorLine - 1);
                String curr = lines.get(cursorLine);
                cursorPos = prev.length();
                lines.set(cursorLine - 1, prev + curr);
                lines.remove(cursorLine);
                cursorLine--;
                if (onTextChanged != null) onTextChanged.onChanged(lines);
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            String line = lines.get(cursorLine);
            if (cursorPos < line.length()) {
                lines.set(cursorLine, line.substring(0, cursorPos) + line.substring(cursorPos + 1));
                if (onTextChanged != null) onTextChanged.onChanged(lines);
            } else if (cursorLine < lines.size() - 1) {
                lines.set(cursorLine, line + lines.get(cursorLine + 1));
                lines.remove(cursorLine + 1);
                if (onTextChanged != null) onTextChanged.onChanged(lines);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!isFocused || codePoint < 32) return false;
        String line = lines.get(cursorLine);
        lines.set(cursorLine, line.substring(0, cursorPos) + codePoint + line.substring(cursorPos));
        cursorPos++;
        if (onTextChanged != null) onTextChanged.onChanged(lines);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        this.isFocused = focused;
        super.setFocused(focused);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {}

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // Background
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);

        // Border
        graphics.fill(getX() - 1, getY() - 1, getX() + getWidth() + 1, getY(), borderColor);
        graphics.fill(getX() - 1, getY() + getHeight(), getX() + getWidth() + 1, getY() + getHeight() + 1, borderColor);
        graphics.fill(getX() - 1, getY(), getX(), getY() + getHeight(), borderColor);
        graphics.fill(getX() + getWidth(), getY(), getX() + getWidth() + 1, getY() + getHeight(), borderColor);

        // Content area
        int contentX = getX() + padding;
        int contentY = getY() + padding;
        int contentW = getWidth() - padding * 2 - scrollbarWidth;
        int contentH = getHeight() - padding * 2;

        graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int visibleLines = contentH / lineHeight;
        scrollOffset = Math.max(0, Math.min(lines.size() - visibleLines, scrollOffset));

        for (int i = scrollOffset; i < lines.size(); i++) {
            int lineIndex = i - scrollOffset;
            int y = contentY + lineIndex * lineHeight;

            if (selectionStart != -1 && cursorLine == i) {
                int start = Math.min(selectionStart, cursorPos);
                int end = Math.max(selectionStart, cursorPos);
                String line = lines.get(i);
                int startX = contentX + font.width(line.substring(0, Math.min(start, line.length())));
                int endX = contentX + font.width(line.substring(0, Math.min(end, line.length())));
                graphics.fill(startX, y + 2, endX, y + lineHeight - 2, selectionColor);
            }

            graphics.drawString(font, lines.get(i), contentX, y + 1, textColor);

            if (isFocused && i == cursorLine && System.currentTimeMillis() - lastCursorBlink < 500) {
                String line = lines.get(i);
                int cursorX = contentX + font.width(line.substring(0, Math.min(cursorPos, line.length())));
                graphics.fill(cursorX, y + 1, cursorX + 1, y + lineHeight - 2, cursorColor);
            }
        }

        graphics.disableScissor();

        // Scrollbar
        if (lines.size() > visibleLines) {
            int scrollX = contentX + contentW;
            int scrollBarHeight = Math.max(10, contentH * contentH / (lines.size() * lineHeight));
            float scrollRatio = (float) scrollOffset / (lines.size() - visibleLines);
            int scrollY = contentY + (int) (scrollRatio * (contentH - scrollBarHeight));
            graphics.fill(scrollX, scrollY, scrollX + scrollbarWidth, scrollY + scrollBarHeight, scrollbarColor);
        }

        if (isFocused && System.currentTimeMillis() - lastCursorBlink > 1000) {
            lastCursorBlink = System.currentTimeMillis();
        }
    }

    private void clampCursor() {
        cursorLine = Math.max(0, Math.min(lines.size() - 1, cursorLine));
        if (cursorLine < lines.size()) {
            cursorPos = Math.max(0, Math.min(lines.get(cursorLine).length(), cursorPos));
        }
    }

    public int getScrollOffset() { return scrollOffset; }
    public void setScrollOffset(int offset) { this.scrollOffset = offset; }

    public enum StylePreset {
        DEFAULT, DARK, LIGHT, CODE, REDSTONE
    }
}
