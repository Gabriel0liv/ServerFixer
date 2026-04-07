package com.gabri.serverfixes.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Header de seção com configuração flexível por instância.
 */
public class GuiSectionHeader {
    private int dividerColor = 0x443ACAFF;
    private int titleColor = 0xFF3ACAFF;
    private int dividerOffset = 20;
    private int dividerHeight = 1;
    private final Font font;
    private String title;
    private int x, y, width, baseY;

    public GuiSectionHeader(Font font) {
        this.font = font;
    }

    public GuiSectionHeader withTitleColor(int color) { this.titleColor = color; return this; }
    public GuiSectionHeader withDividerColor(int color) { this.dividerColor = color; return this; }
    public GuiSectionHeader withDividerPosition(int offset, int height) { this.dividerOffset = offset; this.dividerHeight = height; return this; }
    public GuiSectionHeader withStylePreset(StylePreset preset) {
        return switch (preset) {
            case DEFAULT -> this;
            case GOLD -> withTitleColor(0xFFFFD700).withDividerColor(0x44FFD700);
            case RED -> withTitleColor(0xFFFF4444).withDividerColor(0x44FF4444);
            case GREEN -> withTitleColor(0xFF44FF44).withDividerColor(0x4444FF44);
            case BLUE -> withTitleColor(0xFF4488FF).withDividerColor(0x444488FF);
        };
    }

    public void set(String title, int x, int y, int width) {
        this.title = title;
        this.x = x;
        this.y = y;
        this.baseY = y;
        this.width = width;
    }

    public void setScrollOffset(int scrollOffset) {
        this.y = this.baseY - scrollOffset;
    }

    public void render(GuiGraphics graphics) {
        if (title == null) return;
        String plainTitle = title.replace("§6§l", "").replace("§r", "");
        graphics.drawString(font, "§l" + plainTitle, x, y, titleColor);
        graphics.fill(x, y + dividerOffset, x + width, y + dividerOffset + dividerHeight, dividerColor);
    }

    public int getHeight() { return dividerOffset + 4; }
    public String getTitle() { return title; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }

    public enum StylePreset { DEFAULT, GOLD, RED, GREEN, BLUE }
}
