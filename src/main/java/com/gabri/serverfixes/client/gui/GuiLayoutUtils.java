package com.gabri.serverfixes.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("all")
public final class GuiLayoutUtils {
    private GuiLayoutUtils() {}

    public static void drawPanel(@NotNull GuiGraphics graphics, int x, int y, int width, int height, int fillColor, int outlineColor) {
        graphics.fill(x, y, x + width, y + height, fillColor);
        graphics.renderOutline(x, y, width, height, outlineColor);
    }

    public static void drawTitleWithUnderline(@NotNull GuiGraphics graphics, @NotNull Font font, @NotNull Component title, int centerX, int y, int textColor, int underlineColor) {
        graphics.drawCenteredString(font, title, centerX, y, textColor);
        int textWidth = font.width(title);
        int padding = 8;
        graphics.fill(centerX - textWidth / 2 - padding, y + 12, centerX + textWidth / 2 + padding, y + 16, underlineColor);
    }

    public static void drawFieldLabel(@NotNull GuiGraphics graphics, @NotNull Font font, @NotNull String label, int x, int y, int color) {
        graphics.drawString(font, label, x, y, color);
    }

    public static class VerticalLayoutBuilder {
        private final int startX;
        private int currentY;
        private final int width;
        private final int defaultGap;

        public VerticalLayoutBuilder(int startX, int startY, int width, int defaultGap) {
            this.startX = startX;
            this.currentY = startY;
            this.width = width;
            this.defaultGap = defaultGap;
        }

        public <T extends AbstractWidget> T add(T widget) {
            return add(widget, this.defaultGap);
        }

        public <T extends AbstractWidget> T add(T widget, int customGap) {
            widget.setX(this.startX);
            widget.setY(this.currentY);
            try {
                widget.setWidth(this.width);
            } catch (Exception ignored) {
            }
            this.currentY += widget.getHeight() + customGap;
            return widget;
        }

        public int getCurrentY() {
            return this.currentY;
        }
    }
}
