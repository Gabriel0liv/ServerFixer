package com.gabri.serverfixes.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import net.minecraft.client.gui.narration.NarrationElementOutput;

@SuppressWarnings("null")
public class TextureCheckbox extends AbstractButton {
    private static final ResourceLocation CHECK_ICON = ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/check.png");

    private static final int BORDER_COLOR = 0xFF4E6B8D;
    private static final int BORDER_HOVER = 0xFF6B8DB5;
    private static final int INNER_COLOR = 0xFF1A2434;
    private static final int INNER_HOVER = 0xFF2B3746;

    private boolean selected;
    private final Consumer<Boolean> onToggle;

    /**
     * Texture-only checkbox sized 14x14. The provided {@code narrationMessage}
     * is forwarded to the super constructor for accessibility, but is not
     * rendered visually.
     *
     * @param x                 x position
     * @param y                 y position
     * @param initialValue      initial selected state
     * @param narrationMessage  message passed to super for narration only
     * @param onToggle          callback called with the new state when toggled
     */
    public TextureCheckbox(int x, int y, boolean initialValue, Component narrationMessage, Consumer<Boolean> onToggle) {
        super(x, y, 14, 14, narrationMessage);
        this.selected = initialValue;
        this.onToggle = onToggle;
    }

    @Override
    public void onPress() {
        if (!this.active) return;
        this.selected = !this.selected;
        if (this.onToggle != null) {
            try {
                this.onToggle.accept(this.selected);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        boolean hovered = this.isHoveredOrFocused();

        int border = hovered ? BORDER_HOVER : BORDER_COLOR;
        int inner = hovered ? INNER_HOVER : INNER_COLOR;

        // Outer border (14x14)
        graphics.fill(x, y, x + w, y + h, border);

        // Inner background (12x12) centered inside outer border
        int innerX = x + 1;
        int innerY = y + 1;
        graphics.fill(innerX, innerY, innerX + 12, innerY + 12, inner);

        // Draw check texture when selected (centered within inner box)
        if (this.selected) {
            try {
                graphics.blit(CHECK_ICON, innerX, innerY, 0, 0, 12, 12, 12, 12);
            } catch (Exception ignored) {
            }
        }
    }

    /** Getter for the selected state. */
    public boolean isSelected() {
        return this.selected;
    }

    /** Alias getter for compatibility. */
    public boolean getSelected() {
        return isSelected();
    }

    /** Setter for the selected state. */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }
}
