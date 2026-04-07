package com.gabri.serverfixes.client.gui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Botão de sidebar com configuração flexível por instância.
 * Permite customizar cores, tamanhos e estilos individualmente.
 */
public class GuiSidebarButton extends Button {
    // Defaults
    private static final int DEFAULT_HEIGHT = 24;
    private static final int DEFAULT_ACTIVE_TEXT = 0xFFFFFFFF;
    private static final int DEFAULT_INACTIVE_TEXT = 0xFFA0A0A0;
    private static final int DEFAULT_INDICATOR_COLOR = 0xFF3ACAFF;

    // Configuração por instância
    private int height = DEFAULT_HEIGHT;
    private int activeTextColor = DEFAULT_ACTIVE_TEXT;
    private int inactiveTextColor = DEFAULT_INACTIVE_TEXT;
    private int indicatorColor = DEFAULT_INDICATOR_COLOR;
    private int hoverColor = 0x22FFFFFF;
    private String prefix = "";
    private String activeArrow = "§6> ";
    private String inactiveArrow = "  ";

    // Estado
    private boolean isActive;
    private String label;

    public GuiSidebarButton(int x, int y, int width, String label, boolean isActive, OnPress callback) {
        super(x, y, width, DEFAULT_HEIGHT, Component.literal(label), callback, DEFAULT_NARRATION);
        this.label = label;
        this.isActive = isActive;
        this.height = DEFAULT_HEIGHT;
    }

    // Métodos Fluentes para Configuração
    public GuiSidebarButton withHeight(int height) {
        this.height = height;
        return this;
    }

    public GuiSidebarButton withTextColors(int active, int inactive) {
        this.activeTextColor = active;
        this.inactiveTextColor = inactive;
        return this;
    }

    public GuiSidebarButton withIndicatorColor(int color) {
        this.indicatorColor = color;
        return this;
    }

    public GuiSidebarButton withHoverColor(int color) {
        this.hoverColor = color;
        return this;
    }

    public GuiSidebarButton withPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    public GuiSidebarButton withArrows(String active, String inactive) {
        this.activeArrow = active;
        this.inactiveArrow = inactive;
        return this;
    }

    public GuiSidebarButton withStyle(int indicatorColor, int activeText, int inactiveText) {
        this.indicatorColor = indicatorColor;
        this.activeTextColor = activeText;
        this.inactiveTextColor = inactiveText;
        return this;
    }

    public GuiSidebarButton withStylePreset(StylePreset preset) {
        return switch (preset) {
            case DEFAULT -> this;
            case REDSTONE -> withIndicatorColor(0xFFFF0000).withStyle(0xFFFF0000, 0xFFFF8888, 0xFF884444);
            case SUCCESS -> withIndicatorColor(0xFF00FF00).withStyle(0xFF00FF00, 0xFF88FF88, 0xFF448844);
            case WARNING -> withIndicatorColor(0xFFFFAA00).withStyle(0xFFFFAA00, 0xFFFFDD88, 0xFF886622);
            case VIP -> withIndicatorColor(0xFFFFD700).withStyle(0xFFFFD700, 0xFFFFE888, 0xFF887733);
        };
    }

    // Estado
    public void setActive(boolean active) {
        this.isActive = active;
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var font = Minecraft.getInstance().font;
        
        // Background hover effect
        boolean hovered = isMouseOver(mouseX, mouseY);
        if (hovered) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + height, hoverColor);
        }

        // Active indicator
        if (isActive) {
            graphics.fill(getX(), getY(), getX() + 2, getY() + height, indicatorColor);
        }

        // Text
        String arrow = isActive ? activeArrow : inactiveArrow;
        String text = prefix + (isActive ? "§f" : "§7") + label;
        int color = (hovered || isActive) ? activeTextColor : inactiveTextColor;
        
        graphics.drawString(font, arrow + text, getX() + 8, (int)(getY() + (height / 2f) - (font.lineHeight / 2f)), color);
    }

    public enum StylePreset {
        DEFAULT, REDSTONE, SUCCESS, WARNING, VIP
    }
}
