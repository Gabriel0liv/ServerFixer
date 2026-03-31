package com.gabri.serverfixes.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
}
