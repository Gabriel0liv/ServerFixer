package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@SuppressWarnings("null")
public class HexColorPickerScreen extends Screen {
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)^#[0-9a-f]{6}$");

    private final Screen parent;
    private final Consumer<String> onConfirm;

    private SelectableEditBox hexInput;
    private RgbSlider redSlider;
    private RgbSlider greenSlider;
    private RgbSlider blueSlider;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public HexColorPickerScreen(Screen parent, String initialHex, Consumer<String> onConfirm) {
        super(Component.literal("Selecionar Cor Hex"));
        this.parent = parent;
        this.onConfirm = onConfirm;
        String normalized = normalizeHex(initialHex);
        if (normalized == null) {
            normalized = "#ffffff";
        }
        int[] rgb = hexToRgb(normalized);
        this.redSlider = new RgbSlider(0, 0, 0, 16, "R", rgb[0], this::updateHexFromSliders);
        this.greenSlider = new RgbSlider(0, 0, 0, 16, "G", rgb[1], this::updateHexFromSliders);
        this.blueSlider = new RgbSlider(0, 0, 0, 16, "B", rgb[2], this::updateHexFromSliders);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        this.panelWidth = 300;
        this.panelHeight = 224;
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        int left = this.panelX + 12;
        int top = this.panelY + 22;
        int contentWidth = this.panelWidth - 24;

        this.hexInput = new SelectableEditBox(this.font, left, top, 108, 16, Component.literal("#RRGGBB"));
        this.hexInput.setValue(String.format(Locale.ROOT, "#%02x%02x%02x", this.redSlider.getChannel(), this.greenSlider.getChannel(), this.blueSlider.getChannel()));
        this.hexInput.setResponder(this::updateSlidersFromHex);
        this.addRenderableWidget(this.hexInput);

        this.redSlider.setPosition(left, top + 28, contentWidth);
        this.greenSlider.setPosition(left, top + 52, contentWidth);
        this.blueSlider.setPosition(left, top + 76, contentWidth);
        this.addRenderableWidget(this.redSlider);
        this.addRenderableWidget(this.greenSlider);
        this.addRenderableWidget(this.blueSlider);

        int buttonsY = this.panelY + this.panelHeight - 26;
        this.addRenderableWidget(Button.builder(Component.literal("Aplicar"), btn -> {
            String hex = normalizeHex(this.hexInput.getValue());
            if (hex != null) {
                this.onConfirm.accept(hex);
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(this.panelX + this.panelWidth - 138, buttonsY, 60, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), btn -> this.minecraft.setScreen(this.parent))
            .bounds(this.panelX + this.panelWidth - 72, buttonsY, 60, 18).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(this.panelX, this.panelY, this.panelX + this.panelWidth, this.panelY + this.panelHeight, 0xD7182432);
        graphics.fill(this.panelX - 1, this.panelY - 1, this.panelX + this.panelWidth + 1, this.panelY, 0xFF4E6B8D);
        graphics.fill(this.panelX - 1, this.panelY + this.panelHeight, this.panelX + this.panelWidth + 1, this.panelY + this.panelHeight + 1, 0xFF4E6B8D);
        graphics.fill(this.panelX - 1, this.panelY - 1, this.panelX, this.panelY + this.panelHeight + 1, 0xFF4E6B8D);
        graphics.fill(this.panelX + this.panelWidth, this.panelY - 1, this.panelX + this.panelWidth + 1, this.panelY + this.panelHeight + 1, 0xFF4E6B8D);

        graphics.drawString(this.font, "Seletor Hex", this.panelX + 10, this.panelY + 8, 0xFFF5F5F5);
        int left = this.panelX + 12;
        int top = this.panelY + 22;
        int contentWidth = this.panelWidth - 24;

        graphics.drawString(this.font, "Hex", left + 114, top + 4, 0xFFBECBDD);
        graphics.drawString(this.font, "Preview", left, top + 102, 0xFFBECBDD);

        String hex = normalizeHex(this.hexInput != null ? this.hexInput.getValue() : null);
        int previewColor = hex != null ? parseHexColor(hex) : 0xFF333333;
        int previewOpaque = previewColor | 0xFF000000;
        int previewX = left;
        int previewY = top + 112;
        int previewWidth = contentWidth;
        int previewHeight = 34;
        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, previewOpaque);
        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + 1, 0xFF111111);
        graphics.fill(previewX, previewY + previewHeight - 1, previewX + previewWidth, previewY + previewHeight, 0xFF111111);
        graphics.fill(previewX, previewY, previewX + 1, previewY + previewHeight, 0xFF111111);
        graphics.fill(previewX + previewWidth - 1, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF111111);

        // Base text strip below the color block to preview readability with the chosen color.
        int sampleY = previewY + previewHeight + 6;
        graphics.fill(previewX, sampleY, previewX + previewWidth, sampleY + 18, 0xFF121820);
        graphics.fill(previewX, sampleY, previewX + previewWidth, sampleY + 1, 0xFF111111);
        graphics.fill(previewX, sampleY + 17, previewX + previewWidth, sampleY + 18, 0xFF111111);
        graphics.fill(previewX, sampleY, previewX + 1, sampleY + 18, 0xFF111111);
        graphics.fill(previewX + previewWidth - 1, sampleY, previewX + previewWidth, sampleY + 18, 0xFF111111);

        String sampleText = "Texto base AaBb123";
        int sampleTextColor = previewOpaque;
        graphics.drawString(this.font, sampleText, previewX + 4, sampleY + 5, sampleTextColor);

        int infoY = sampleY + 22;
        String rgbInfo = String.format(Locale.ROOT, "R:%d G:%d B:%d", this.redSlider.getChannel(), this.greenSlider.getChannel(), this.blueSlider.getChannel());
        graphics.drawString(this.font, hex != null ? hex : "#------", previewX, infoY, 0xFFBECBDD);
        graphics.drawString(this.font, rgbInfo, previewX, infoY + 10, 0xFF8EA6C0);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateHexFromSliders() {
        if (this.hexInput == null) {
            return;
        }
        String hex = String.format(Locale.ROOT, "#%02x%02x%02x", this.redSlider.getChannel(), this.greenSlider.getChannel(), this.blueSlider.getChannel());
        this.hexInput.setValue(hex);
    }

    private void updateSlidersFromHex(String value) {
        String hex = normalizeHex(value);
        if (hex == null) {
            return;
        }
        int[] rgb = hexToRgb(hex);
        this.redSlider.setChannel(rgb[0]);
        this.greenSlider.setChannel(rgb[1]);
        this.blueSlider.setChannel(rgb[2]);
    }

    private static String normalizeHex(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (!HEX_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static int[] hexToRgb(String hex) {
        int color = parseHexColor(hex);
        return new int[] {
            (color >> 16) & 0xFF,
            (color >> 8) & 0xFF,
            color & 0xFF
        };
    }

    private static int parseHexColor(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }

    private static class RgbSlider extends AbstractSliderButton {
        private final String label;
        private final Runnable onChange;

        RgbSlider(int x, int y, int width, int height, String label, int initial, Runnable onChange) {
            super(x, y, width, height, Component.literal(""), clamp(initial) / 255.0);
            this.label = label;
            this.onChange = onChange;
            this.updateMessage();
        }

        void setPosition(int x, int y, int width) {
            this.setX(x);
            this.setY(y);
            this.setWidth(width);
        }

        int getChannel() {
            return (int) Math.round(this.value * 255.0);
        }

        void setChannel(int value) {
            this.value = clamp(value) / 255.0;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(this.label + ": " + getChannel()));
        }

        @Override
        protected void applyValue() {
            this.updateMessage();
            if (this.onChange != null) {
                this.onChange.run();
            }
        }

        private static int clamp(int value) {
            return Math.max(0, Math.min(255, value));
        }
    }
}
