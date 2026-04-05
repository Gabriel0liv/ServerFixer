package com.gabri.serverfixes.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class PulsingButton extends Button {
    private boolean dirty;
    private boolean errorState = false;
    private long errorPulseUntil = 0L;
    private long pulsePeriod = 900L;

    public PulsingButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void setError(boolean error) {
        this.errorState = error;
    }

    public void pulseError(long durationMs) {
        this.errorPulseUntil = System.currentTimeMillis() + Math.max(0, durationMs);
    }

    public void setPulsePeriod(long ms) {
        this.pulsePeriod = Math.max(50L, ms);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        long now = System.currentTimeMillis();
        boolean errorActive = this.errorState || now < this.errorPulseUntil;
        int baseColor;
        if (errorActive) {
            double t = (now % this.pulsePeriod) / (double) this.pulsePeriod;
            double s = 0.5D + 0.5D * Math.sin(t * Math.PI * 2.0D);
            int r = Math.min(255, 0xB0 + (int) (40.0D * s));
            int g = Math.max(0, 0x30 - (int) (16.0D * s));
            int b = Math.max(0, 0x30 - (int) (16.0D * s));
            baseColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
        } else {
            baseColor = this.dirty ? 0xFF2D6A3E : 0xFF2E3A42;
            if (this.dirty) {
                double t = (now % this.pulsePeriod) / (double) this.pulsePeriod;
                int pulse = (int) (20.0D + 35.0D * Math.sin(t * Math.PI * 2.0D));
                int r = Math.min(255, 0x2D + pulse / 3);
                int g = Math.min(255, 0x6A + pulse / 2);
                int b = Math.min(255, 0x3E + pulse / 4);
                baseColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
        }

        int fill = !this.active ? 0xFF2B2B2B : (this.isHoveredOrFocused() ? lighten(baseColor, 0x18) : baseColor);
        int border = this.dirty ? 0xFF8EE6A0 : 0xFF4E6B8D;

        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);

        int textColor = 0xFFF4F7FF;
        int textX = x + (w - Minecraft.getInstance().font.width(this.getMessage())) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(Minecraft.getInstance().font, this.getMessage(), textX, textY, textColor);
    }

    private static int lighten(int argb, int delta) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >>> 16) & 0xFF) + delta);
        int g = Math.min(255, ((argb >>> 8) & 0xFF) + delta);
        int b = Math.min(255, (argb & 0xFF) + delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
