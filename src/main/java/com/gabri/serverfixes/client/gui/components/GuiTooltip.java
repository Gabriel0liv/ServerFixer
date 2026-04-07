package com.gabri.serverfixes.client.gui.components;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;

import java.util.List;

/**
 * Sistema de tooltips com configuração flexível por instância.
 * Permite customizar cores, padding e posição individualmente.
 */
public class GuiTooltip {
    // Defaults
    private static final int DEFAULT_PADDING_X = 8;
    private static final int DEFAULT_PADDING_Y = 6;
    private static final int DEFAULT_ACCENT_COLOR = 0xFF3ACAFF;
    private static final int DEFAULT_BG_DARK = 0xAA000000;
    private static final int DEFAULT_BG_MID = 0xAA1C2532;
    private static final int DEFAULT_BG_LIGHT = 0xAA1C2532;
    private static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;

    // Configuração por instância
    private int paddingX = DEFAULT_PADDING_X;
    private int paddingY = DEFAULT_PADDING_Y;
    private int accentColor = DEFAULT_ACCENT_COLOR;
    private int bgDark = DEFAULT_BG_DARK;
    private int bgMid = DEFAULT_BG_MID;
    private int bgLight = DEFAULT_BG_LIGHT;
    private int textColor = DEFAULT_TEXT_COLOR;

    // Estado
    private final Font font;
    private List<String> lines;
    private int offsetX = 12;
    private int offsetY = -8;

    public GuiTooltip(Font font) {
        this.font = font;
    }

    // Métodos Fluentes para Configuração
    public GuiTooltip withPadding(int x, int y) {
        this.paddingX = x;
        this.paddingY = y;
        return this;
    }

    public GuiTooltip withColors(int accent, int bgDark, int bgMid, int bgLight, int text) {
        this.accentColor = accent;
        this.bgDark = bgDark;
        this.bgMid = bgMid;
        this.bgLight = bgLight;
        this.textColor = text;
        return this;
    }

    public GuiTooltip withAccentColor(int color) {
        this.accentColor = color;
        return this;
    }

    public GuiTooltip withBackgroundColors(int dark, int mid, int light) {
        this.bgDark = dark;
        this.bgMid = mid;
        this.bgLight = light;
        return this;
    }

    public GuiTooltip withTextColor(int color) {
        this.textColor = color;
        return this;
    }

    public GuiTooltip withOffset(int x, int y) {
        this.offsetX = x;
        this.offsetY = y;
        return this;
    }

    public GuiTooltip withStylePreset(StylePreset preset) {
        return switch (preset) {
            case YACL -> this; // Padrão já é YACL
            case REDSTONE -> withAccentColor(0xFFFF0000).withBackgroundColors(0xAA000000, 0xAA220000, 0xAA110000);
            case SUCCESS -> withAccentColor(0xFF00FF00).withBackgroundColors(0xAA000000, 0xAA002200, 0xAA001100);
            case WARNING -> withAccentColor(0xFFFFAA00).withBackgroundColors(0xAA000000, 0xAA221100, 0xAA110800);
        };
    }

    // Estado
    public void setLines(List<String> lines) {
        this.lines = lines;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (lines == null || lines.isEmpty()) return;

        int maxWidth = 0;
        for (String line : lines) {
            int w = font.width(stripColors(line));
            if (w > maxWidth) maxWidth = w;
        }

        int tooltipWidth = maxWidth + paddingX * 2;
        int tooltipHeight = lines.size() * (font.lineHeight + 2) + paddingY * 2;

        int tooltipX = mouseX + offsetX;
        int tooltipY = mouseY + offsetY - tooltipHeight;
        if (tooltipY < 4) tooltipY = mouseY + offsetY + 12;
        if (tooltipX + tooltipWidth > screenWidth) tooltipX = mouseX - tooltipWidth - offsetX;
        if (tooltipX < 4) tooltipX = 4;
        if (tooltipY + tooltipHeight > screenHeight) tooltipY = screenHeight - tooltipHeight - 4;

        // Background layers
        graphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipWidth + 2, tooltipY + tooltipHeight + 2, bgDark);
        graphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, bgMid);
        graphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, bgLight);

        // Top accent bar
        graphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 2, accentColor);

        // Text
        int textY = tooltipY + paddingY;
        for (String line : lines) {
            graphics.drawString(font, line, tooltipX + paddingX, textY, textColor);
            textY += font.lineHeight + 2;
        }
    }

    public boolean hasLines() {
        return lines != null && !lines.isEmpty();
    }

    private static String stripColors(String text) {
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    public enum StylePreset {
        YACL, REDSTONE, SUCCESS, WARNING
    }
}
