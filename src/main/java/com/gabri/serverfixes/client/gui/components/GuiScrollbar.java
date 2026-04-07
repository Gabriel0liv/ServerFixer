package com.gabri.serverfixes.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Scrollbar estilizada com configuração flexível por instância.
 * Permite customizar cores, tamanho e comportamento individualmente.
 */
public class GuiScrollbar {
    // Defaults
    private static final int DEFAULT_WIDTH = 4;
    private static final int DEFAULT_MIN_HEIGHT = 20;
    private static final int DEFAULT_TRACK_COLOR = 0x22FFFFFF;
    private static final int DEFAULT_THUMB_COLOR = 0xFF3ACAFF;

    // Configuração por instância
    private int width = DEFAULT_WIDTH;
    private int minThumbHeight = DEFAULT_MIN_HEIGHT;
    private int trackColor = DEFAULT_TRACK_COLOR;
    private int thumbColor = DEFAULT_THUMB_COLOR;

    // Estado
    private int x, y, trackHeight;
    private int scrollOffset, maxScroll, contentHeight;

    public GuiScrollbar(int x, int y, int trackHeight) {
        this.x = x;
        this.y = y;
        this.trackHeight = trackHeight;
    }

    // Métodos Fluentes para Configuração
    public GuiScrollbar withWidth(int width) {
        this.width = width;
        return this;
    }

    public GuiScrollbar withMinThumbHeight(int height) {
        this.minThumbHeight = height;
        return this;
    }

    public GuiScrollbar withTrackColor(int color) {
        this.trackColor = color;
        return this;
    }

    public GuiScrollbar withThumbColor(int color) {
        this.thumbColor = color;
        return this;
    }

    public GuiScrollbar withStyle(int trackColor, int thumbColor, int width) {
        this.trackColor = trackColor;
        this.thumbColor = thumbColor;
        this.width = width;
        return this;
    }

    // Atualização de Estado
    public void update(int scrollOffset, int maxScroll, int contentHeight) {
        this.scrollOffset = scrollOffset;
        this.maxScroll = maxScroll;
        this.contentHeight = contentHeight;
    }

    // Renderização
    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (maxScroll <= 0) return;

        // Track
        graphics.fill(x, y, x + width, y + trackHeight, trackColor);

        // Thumb
        float scrollRatio = (float) scrollOffset / maxScroll;
        int thumbHeight = Math.max(minThumbHeight, (int) (trackHeight * ((float) trackHeight / (trackHeight + maxScroll))));
        int thumbY = y + (int) (scrollRatio * (trackHeight - thumbHeight));
        graphics.fill(x, thumbY, x + width, thumbY + thumbHeight, thumbColor);
    }

    // Interação
    public boolean isMouseOver(int mouseX, int mouseY) {
        if (maxScroll <= 0) return false;
        float scrollRatio = (float) scrollOffset / maxScroll;
        int thumbHeight = Math.max(minThumbHeight, (int) (trackHeight * ((float) trackHeight / (trackHeight + maxScroll))));
        int thumbY = y + (int) (scrollRatio * (trackHeight - thumbHeight));
        return mouseX >= x && mouseX <= x + width && mouseY >= thumbY && mouseY <= thumbY + thumbHeight;
    }

    public boolean isTrackOver(int mouseX, int mouseY) {
        if (maxScroll <= 0) return false;
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + trackHeight;
    }

    public int getScrollOffsetFromMouseY(int mouseY) {
        if (maxScroll <= 0) return 0;
        float scrollRatio = (mouseY - y - 10f) / (trackHeight - 20);
        return Math.max(0, Math.min(maxScroll, (int) (scrollRatio * maxScroll)));
    }

    // Getters
    public int getWidth() { return width; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getTrackHeight() { return trackHeight; }
    public int getThumbY() {
        if (maxScroll <= 0) return y;
        float scrollRatio = (float) scrollOffset / maxScroll;
        int thumbHeight = Math.max(minThumbHeight, (int) (trackHeight * ((float) trackHeight / (trackHeight + maxScroll))));
        return y + (int) (scrollRatio * (trackHeight - thumbHeight));
    }
    public int getThumbHeight() {
        if (maxScroll <= 0) return trackHeight;
        return Math.max(minThumbHeight, (int) (trackHeight * ((float) trackHeight / (trackHeight + maxScroll))));
    }
}
