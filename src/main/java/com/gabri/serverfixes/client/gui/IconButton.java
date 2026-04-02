package com.gabri.serverfixes.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("null")
class IconButton extends Button {
    private final ResourceLocation tex;
    private final int texW;
    private final int texH;
    private final int bgColor;
    private final int hoverColor;
    private final int borderColor;

    public IconButton(int x, int y, int width, int height, ResourceLocation tex, int texW, int texH, int bgColor, int hoverColor, int borderColor, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, (button) -> Component.empty());
        this.tex = tex;
        this.texW = texW;
        this.texH = texH;
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.borderColor = borderColor;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        int fill = !this.active ? 0xFF38424A : (this.isHoveredOrFocused() ? this.hoverColor : this.bgColor);

        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, this.borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, this.borderColor);
        graphics.fill(x, y, x + 1, y + h, this.borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, this.borderColor);

        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = Math.max(0, w - 2);
        int innerH = Math.max(0, h - 2);

        int iconW = Math.min(innerW, this.texW);
        int iconH = Math.min(innerH, this.texH);
        int ix = innerX + (innerW - iconW) / 2;
        int iy = innerY + (innerH - iconH) / 2;
        try {
            graphics.blit(this.tex, ix, iy, 0, 0, iconW, iconH, this.texW, this.texH);
        } catch (Exception ignored) {
        }
    }
}
