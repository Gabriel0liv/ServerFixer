package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.mixin.ParticleEngineAccessor;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.DoubleConsumer;

@SuppressWarnings("null")
public class ParticleStudioScreen extends Screen {
    private static final int OUTER_MARGIN = 10;
    private static final int PANEL_GAP = 4;
    private static final int ROW_HEIGHT = 18;

    private final List<ResourceLocation> allParticles = new ArrayList<>();
    private final List<ResourceLocation> filteredParticles = new ArrayList<>();

    private SelectableEditBox searchBox;
    private SelectableEditBox commandOutputBox;

    private DoubleParameterSlider deltaXSlider;
    private DoubleParameterSlider deltaYSlider;
    private DoubleParameterSlider deltaZSlider;
    private DoubleParameterSlider speedSlider;
    private IntParameterSlider countSlider;
    private CycleButton<Boolean> forceToggle;

    private Button testButton;
    private Button copyButton;

    private ResourceLocation selectedParticleId;
    private TextureAtlasSprite selectedPreviewSprite;
    private ResourceLocation selectedPreviewSpriteId;
    private boolean previewSupported;
    private boolean previewHardcoded;

    private int scrollOffset = 0;

    private int leftX;
    private int leftY;
    private int leftW;
    private int leftH;

    private int centerX;
    private int centerY;
    private int centerW;
    private int centerH;

    private int rightX;
    private int rightY;
    private int rightW;
    private int rightH;

    private int listAreaX;
    private int listAreaY;
    private int listAreaW;
    private int listAreaH;

    private double deltaX = 0.5D;
    private double deltaY = 0.5D;
    private double deltaZ = 0.5D;
    private double speed = 0.01D;
    private int count = 25;
    private boolean force = false;

    private String commandString = "";

    public ParticleStudioScreen() {
        super(Component.literal("Particle Studio"));
        if (ForgeRegistries.PARTICLE_TYPES != null && ForgeRegistries.PARTICLE_TYPES.getKeys() != null) {
            this.allParticles.addAll(ForgeRegistries.PARTICLE_TYPES.getKeys());
        }
        this.allParticles.removeIf(Objects::isNull);
        this.allParticles.sort(Comparator
            .comparing(ResourceLocation::getNamespace)
            .thenComparing(ResourceLocation::getPath));
        this.filteredParticles.addAll(this.allParticles);
        if (!this.filteredParticles.isEmpty()) {
            this.selectedParticleId = this.filteredParticles.get(0);
        }
        // Do not touch texture atlas in constructor; resources may not be ready yet.
        updateCommandString();
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.font == null) {
            return;
        }
        this.clearWidgets();
        updateLayout();

        this.searchBox = new SelectableEditBox(this.font, this.leftX + 8, this.leftY + 24, this.leftW - 16, 18, Component.literal("Pesquisar particula"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setResponder(value -> applySearchFilter());
        this.addRenderableWidget(this.searchBox);

        // Keep widgets strictly inside the right panel to avoid leaking/cropping at high GUI scale.
        int sliderX = this.rightX + 5;
        int sliderW = this.rightW - 10;
        int sliderY = this.rightY + 28;

        this.deltaXSlider = new DoubleParameterSlider(sliderX, sliderY, sliderW, 18, "Delta X", 0.0D, 5.0D, this.deltaX, 2, value -> {
            this.deltaX = value;
            updateCommandString();
        });
        sliderY += 24;

        this.deltaYSlider = new DoubleParameterSlider(sliderX, sliderY, sliderW, 18, "Delta Y", 0.0D, 5.0D, this.deltaY, 2, value -> {
            this.deltaY = value;
            updateCommandString();
        });
        sliderY += 24;

        this.deltaZSlider = new DoubleParameterSlider(sliderX, sliderY, sliderW, 18, "Delta Z", 0.0D, 5.0D, this.deltaZ, 2, value -> {
            this.deltaZ = value;
            updateCommandString();
        });
        sliderY += 24;

        this.speedSlider = new DoubleParameterSlider(sliderX, sliderY, sliderW, 18, "Speed", 0.0D, 1.0D, this.speed, 2, value -> {
            this.speed = value;
            updateCommandString();
        });
        sliderY += 24;

        this.countSlider = new IntParameterSlider(sliderX, sliderY, sliderW, 18, "Count", 1, 1000, this.count, value -> {
            this.count = value;
            updateCommandString();
        });
        sliderY += 28;

        this.forceToggle = CycleButton.builder((Boolean value) -> Component.literal(value ? "FORCE" : "NORMAL"))
            .withValues(false, true)
            .withInitialValue(this.force)
            .displayOnlyValue()
            .create(sliderX, sliderY, sliderW, 18, Component.literal("Modo"), (btn, value) -> {
                this.force = value;
                updateCommandString();
            });
        sliderY += 24;

        this.commandOutputBox = new SelectableEditBox(this.font, sliderX, sliderY, sliderW, 18, Component.literal("Comando"));
        this.commandOutputBox.setEditable(false);
        this.commandOutputBox.setCanLoseFocus(true);
        this.addRenderableWidget(this.commandOutputBox);
        sliderY += 26;

        this.testButton = Button.builder(Component.literal("Testar"), btn -> testCurrentCommand())
            .bounds(sliderX, sliderY, (sliderW - 4) / 2, 20)
            .build();
        this.copyButton = Button.builder(Component.literal("Copiar"), btn -> copyCurrentCommand())
            .bounds(sliderX + ((sliderW - 4) / 2) + 4, sliderY, (sliderW - 4) / 2, 20)
            .build();

        this.addRenderableWidget(this.deltaXSlider);
        this.addRenderableWidget(this.deltaYSlider);
        this.addRenderableWidget(this.deltaZSlider);
        this.addRenderableWidget(this.speedSlider);
        this.addRenderableWidget(this.countSlider);
        this.addRenderableWidget(this.forceToggle);
        this.addRenderableWidget(this.testButton);
        this.addRenderableWidget(this.copyButton);

        this.addRenderableWidget(Button.builder(Component.literal("Fechar"), btn -> onClose())
            .bounds(this.rightX + this.rightW - 69, this.rightY + this.rightH - 24, 64, 18)
            .build());

        refreshSelectedSpriteSafe();
        updateCommandString();
    }

    private void updateLayout() {
        int contentY = 20;
        int contentH = this.height - 28;

        int desiredLeft = Math.max(100, this.width / 4);
        int desiredRight = Math.max(130, this.width / 4);
        int minCenter = 120;
        int total = desiredLeft + desiredRight + (PANEL_GAP * 2) + minCenter;

        if (total > this.width) {
            int overflow = total - this.width;
            int leftTrim = Math.min(Math.max(0, desiredLeft - 100), (overflow + 1) / 2);
            desiredLeft -= leftTrim;
            overflow -= leftTrim;

            int rightTrim = Math.min(Math.max(0, desiredRight - 130), overflow);
            desiredRight -= rightTrim;
        }

        this.leftW = desiredLeft;
        this.rightW = desiredRight;
        this.rightX = this.width - this.rightW;

        this.leftX = 0;
        this.centerX = this.leftW + PANEL_GAP;
        int centerRight = this.rightX - PANEL_GAP;
        this.centerW = Math.max(minCenter, centerRight - this.centerX);

        this.leftY = contentY;
        this.centerY = contentY;
        this.rightY = contentY;

        this.leftH = contentH;
        this.centerH = contentH;
        this.rightH = contentH;

        int searchY = this.leftY + 24;
        int searchH = 18;
        this.listAreaX = this.leftX + 8;
        this.listAreaY = searchY + searchH + 8;
        this.listAreaW = this.leftW - 16;
        this.listAreaH = this.leftH - (this.listAreaY - this.leftY) - 8;
    }

    private void applySearchFilter() {
        String query = this.searchBox != null ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        this.filteredParticles.clear();

        if (query.isEmpty()) {
            this.filteredParticles.addAll(this.allParticles);
        } else {
            for (ResourceLocation id : this.allParticles) {
                if (id == null) {
                    continue;
                }
                String full = id.toString().toLowerCase(Locale.ROOT);
                if (full.contains(query) || id.getNamespace().toLowerCase(Locale.ROOT).contains(query) || id.getPath().toLowerCase(Locale.ROOT).contains(query)) {
                    this.filteredParticles.add(id);
                }
            }
        }

        this.filteredParticles.removeIf(Objects::isNull);

        this.scrollOffset = 0;
        if (this.selectedParticleId == null || !this.filteredParticles.contains(this.selectedParticleId)) {
            this.selectedParticleId = this.filteredParticles.isEmpty() ? null : this.filteredParticles.get(0);
            refreshSelectedSpriteSafe();
        }
        updateCommandString();
    }

    private void refreshSelectedSpriteSafe() {
        this.previewSupported = false;
        this.previewHardcoded = false;
        this.selectedPreviewSprite = null;
        this.selectedPreviewSpriteId = null;
    }

    private void updateCommandString() {
        if (this.selectedParticleId == null) {
            this.commandString = "";
        } else {
            this.commandString = String.format(
                Locale.ROOT,
                "/particle %s ~ ~1 ~ %.2f %.2f %.2f %.2f %d %s",
                this.selectedParticleId,
                this.deltaX,
                this.deltaY,
                this.deltaZ,
                this.speed,
                this.count,
                this.force ? "force" : "normal"
            );
        }

        if (this.commandOutputBox != null) {
            this.commandOutputBox.setValue(this.commandString);
        }

        boolean hasSelection = this.selectedParticleId != null && !this.commandString.isBlank();
        if (this.testButton != null) this.testButton.active = hasSelection;
        if (this.copyButton != null) this.copyButton.active = hasSelection;
    }

    private void testCurrentCommand() {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.player.connection == null || this.commandString.isBlank()) {
            return;
        }

        String command = this.commandString.startsWith("/") ? this.commandString.substring(1) : this.commandString;
        this.minecraft.player.connection.sendCommand(command);
    }

    private void copyCurrentCommand() {
        if (this.minecraft == null || this.commandString.isBlank()) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(this.commandString);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInsideListArea(mouseX, mouseY)) {
            int localY = (int) mouseY - this.listAreaY;
            int row = localY / ROW_HEIGHT;
            int index = this.scrollOffset + row;
            if (row >= 0 && index >= 0 && index < this.filteredParticles.size()) {
                this.selectedParticleId = this.filteredParticles.get(index);
                refreshSelectedSpriteSafe();
                updateCommandString();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (isInsideListArea(mouseX, mouseY)) {
            int visibleRows = Math.max(1, this.listAreaH / ROW_HEIGHT);
            int maxOffset = Math.max(0, this.filteredParticles.size() - visibleRows);
            int next = this.scrollOffset - (int) Math.signum(scrollY);
            this.scrollOffset = Mth.clamp(next, 0, maxOffset);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null || this.font == null) {
            return;
        }

        updateLayout();

        // Dark world overlay + dashboard panels (avoid vanilla dirt background)
        graphics.fill(0, 0, this.width, this.height, 0xDD000000);

        graphics.fill(this.leftX, this.leftY, this.leftX + this.leftW, this.leftY + this.leftH, 0x44242D3D);
        graphics.fillGradient(this.centerX, this.centerY, this.centerX + this.centerW, this.centerY + this.centerH, 0x66202A39, 0x662C3C52);
        graphics.fill(this.rightX, this.rightY, this.rightX + this.rightW, this.rightY + this.rightH, 0x44242D3D);

        GuiLayoutUtils.drawPanel(graphics, this.leftX, this.leftY, this.leftW, this.leftH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.centerX, this.centerY, this.centerW, this.centerH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.rightX, this.rightY, this.rightW, this.rightH, 0x001A2434, 0xFF4E6B8D);

        graphics.drawString(this.font, "Particulas", this.leftX + 8, this.leftY + 8, 0xFFF4F7FF);
        graphics.drawString(this.font, "Preview 2D", this.centerX + 8, this.centerY + 8, 0xFFF4F7FF);
        graphics.drawString(this.font, "Command Builder", this.rightX + 8, this.rightY + 8, 0xFFF4F7FF);

        renderParticleList(graphics, mouseX, mouseY);
        renderPreviewPanel(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderParticleList(GuiGraphics graphics, int mouseX, int mouseY) {
        GuiLayoutUtils.drawPanel(graphics, this.listAreaX, this.listAreaY, this.listAreaW, this.listAreaH, 0xB1121924, 0xFF2E4258);

        int visibleRows = Math.max(1, this.listAreaH / ROW_HEIGHT);
        int maxOffset = Math.max(0, this.filteredParticles.size() - visibleRows);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, maxOffset);

        graphics.enableScissor(this.listAreaX + 1, this.listAreaY + 1, this.listAreaX + this.listAreaW - 1, this.listAreaY + this.listAreaH - 1);
        for (int i = 0; i < visibleRows; i++) {
            int index = this.scrollOffset + i;
            if (index >= this.filteredParticles.size()) {
                break;
            }

            ResourceLocation id = this.filteredParticles.get(index);
            if (id == null) {
                continue;
            }
            int rowY = this.listAreaY + (i * ROW_HEIGHT);
            boolean selected = id.equals(this.selectedParticleId);
            boolean hovered = isInsideListArea(mouseX, mouseY) && ((int) mouseY - this.listAreaY) / ROW_HEIGHT == i;

            int rowColor = selected ? 0xAA2B4E72 : (hovered ? 0xAA263445 : 0x55202A36);
            graphics.fill(this.listAreaX + 1, rowY, this.listAreaX + this.listAreaW - 1, rowY + ROW_HEIGHT - 1, rowColor);

            String namespace = id.getNamespace();
            String path = id.getPath();
            String prefix = namespace + ":";
            int prefixX = this.listAreaX + 6;
            int prefixW = this.font.width(prefix);
            int pathX = prefixX + prefixW;
            int maxTextX = this.listAreaX + this.listAreaW - 8;
            int pathWidth = Math.max(0, maxTextX - pathX);
            String clippedPath = this.font.plainSubstrByWidth(path, pathWidth);

            graphics.drawString(this.font, prefix, prefixX, rowY + 5, 0xFFE8CC6D);
            graphics.drawString(this.font, clippedPath, pathX, rowY + 5, 0xFFF2F6FF);
        }
        graphics.disableScissor();

        // Soft fade masks at top/bottom to make list overflow clipping smoother.
        graphics.fillGradient(this.listAreaX + 1, this.listAreaY + 1, this.listAreaX + this.listAreaW - 1, this.listAreaY + 9, 0xCC111824, 0x00111824);
        graphics.fillGradient(this.listAreaX + 1, this.listAreaY + this.listAreaH - 9, this.listAreaX + this.listAreaW - 1, this.listAreaY + this.listAreaH - 1, 0x00111824, 0xCC111824);

        if (this.filteredParticles.isEmpty()) {
            graphics.drawCenteredString(this.font, "Nenhuma particula encontrada", this.listAreaX + (this.listAreaW / 2), this.listAreaY + 8, 0xFFFF8A8A);
        }
    }

    private void renderPreviewPanel(GuiGraphics graphics) {
        int boxX = this.centerX + 12;
        int boxY = this.centerY + 30;
        int boxW = this.centerW - 24;
        int boxH = this.centerH - 42;

        GuiLayoutUtils.drawPanel(graphics, boxX, boxY, boxW, boxH, 0xE10C1017, 0xFF2E4258);

        if (this.selectedParticleId == null) {
            graphics.drawCenteredString(this.font, "Selecione uma particula", boxX + (boxW / 2), boxY + (boxH / 2), 0xFFAAAAAA);
            return;
        }
        graphics.drawString(this.font, this.selectedParticleId.toString(), boxX + 8, boxY + 8, 0xFFB8C8DE);

        TextureAtlasSprite animatedSprite = resolveAnimatedPreviewSprite();
        if (animatedSprite == null) {
            String message = this.previewHardcoded
                ? "Preview dinamica/codigo (Sem sprite fixo)"
                : "Preview nao suportada";
            graphics.drawCenteredString(this.font, message, boxX + (boxW / 2), boxY + (boxH / 2) - 4, 0xFFFF7D7D);
            return;
        }

        float scale = 4.0F;
        int spriteW = Math.max(1, animatedSprite.contents().width());
        int spriteH = Math.max(1, animatedSprite.contents().height());
        int scaledW = (int) (spriteW * scale);
        int scaledH = (int) (spriteH * scale);
        int drawX = boxX + (boxW - scaledW) / 2;
        int drawY = boxY + (boxH - scaledH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(0, 0, 0, spriteW, spriteH, animatedSprite);
        graphics.pose().popPose();

        if (this.selectedPreviewSpriteId != null) {
            graphics.drawCenteredString(this.font, this.selectedPreviewSpriteId.toString(), boxX + (boxW / 2), boxY + boxH - 14, 0xFF8FA5C2);
        }
    }

    private TextureAtlasSprite resolveAnimatedPreviewSprite() {
        if (this.selectedParticleId == null || this.minecraft == null) {
            this.previewSupported = false;
            this.previewHardcoded = false;
            this.selectedPreviewSprite = null;
            this.selectedPreviewSpriteId = null;
            return null;
        }

        try {
            if (this.minecraft.particleEngine == null) {
                this.previewSupported = false;
                this.previewHardcoded = true;
                return null;
            }

            Map<ResourceLocation, SpriteSet> spriteSets = ((ParticleEngineAccessor) this.minecraft.particleEngine).getSpriteSets();
            if (spriteSets == null) {
                this.previewSupported = false;
                this.previewHardcoded = true;
                return null;
            }

            SpriteSet spriteSet = spriteSets.get(this.selectedParticleId);
            if (spriteSet == null) {
                this.previewSupported = false;
                this.previewHardcoded = true;
                return null;
            }

            int maxFrames = 8;
            int currentFrame = (int) ((Util.getMillis() / 100L) % maxFrames);
            TextureAtlasSprite sprite = spriteSet.get(currentFrame, maxFrames);
            if (isMissingSprite(sprite)) {
                this.previewSupported = false;
                this.previewHardcoded = false;
                return null;
            }

            this.previewSupported = true;
            this.previewHardcoded = false;
            this.selectedPreviewSprite = sprite;
            this.selectedPreviewSpriteId = this.selectedParticleId;
            return sprite;
        } catch (Exception ignored) {
            // Keep UI alive even if a specific particle has no atlas sprite mapping.
        }

        this.previewSupported = false;
        this.previewHardcoded = false;
        this.selectedPreviewSprite = null;
        this.selectedPreviewSpriteId = null;
        return null;
    }

    private boolean isMissingSprite(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return true;
        }
        try {
            String path = sprite.contents().name().getPath();
            return path != null && path.contains("missingno");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isInsideListArea(double mouseX, double mouseY) {
        return mouseX >= this.listAreaX && mouseX < this.listAreaX + this.listAreaW
            && mouseY >= this.listAreaY && mouseY < this.listAreaY + this.listAreaH;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class DoubleParameterSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final int decimals;
        private final DoubleConsumer onChange;

        private DoubleParameterSlider(int x, int y, int width, int height, String label, double min, double max, double initial, int decimals, DoubleConsumer onChange) {
            super(x, y, width, height, Component.literal(""), normalize(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.onChange = onChange;
            updateMessage();
        }

        private double getCurrentValue() {
            double raw = this.min + (this.max - this.min) * this.value;
            double factor = Math.pow(10.0D, this.decimals);
            return Math.round(raw * factor) / factor;
        }

        @Override
        protected void updateMessage() {
            String fmt = "%s: %." + this.decimals + "f";
            this.setMessage(Component.literal(String.format(Locale.ROOT, fmt, this.label, getCurrentValue())));
        }

        @Override
        protected void applyValue() {
            updateMessage();
            if (this.onChange != null) {
                this.onChange.accept(getCurrentValue());
            }
        }

        private static double normalize(double value, double min, double max) {
            if (max <= min) return 0.0D;
            return Mth.clamp((value - min) / (max - min), 0.0D, 1.0D);
        }
    }

    private static final class IntParameterSlider extends AbstractSliderButton {
        private final String label;
        private final int min;
        private final int max;
        private final IntConsumer onChange;

        private IntParameterSlider(int x, int y, int width, int height, String label, int min, int max, int initial, IntConsumer onChange) {
            super(x, y, width, height, Component.literal(""), normalize(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private int getCurrentValue() {
            if (this.max <= this.min) return this.min;
            return this.min + (int) Math.round((this.max - this.min) * this.value);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(this.label + ": " + getCurrentValue()));
        }

        @Override
        protected void applyValue() {
            updateMessage();
            if (this.onChange != null) {
                this.onChange.accept(getCurrentValue());
            }
        }

        private static double normalize(int value, int min, int max) {
            if (max <= min) return 0.0D;
            return Mth.clamp((double) (value - min) / (double) (max - min), 0.0D, 1.0D);
        }
    }
}
