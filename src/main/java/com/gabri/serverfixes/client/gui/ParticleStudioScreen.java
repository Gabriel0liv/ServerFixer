package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.mixin.ParticleEngineAccessor;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

@SuppressWarnings("null")
public class ParticleStudioScreen extends Screen {
    private static final int OUTER_MARGIN = 10;
    private static final int PANEL_GAP = 4;
    private static final int ROW_HEIGHT = 18;
    private static final Map<ResourceLocation, float[]> PARTICLE_TINTS = Map.ofEntries(
        Map.entry(ResourceLocation.fromNamespaceAndPath("minecraft", "totem_of_undying"), tint(0x63D46A)),
        Map.entry(ResourceLocation.fromNamespaceAndPath("minecraft", "dripping_water"), tint(0x3F76E4)),
        Map.entry(ResourceLocation.fromNamespaceAndPath("minecraft", "falling_water"), tint(0x3F76E4)),
        Map.entry(ResourceLocation.fromNamespaceAndPath("minecraft", "landing_water"), tint(0x3F76E4)),
        Map.entry(ResourceLocation.fromNamespaceAndPath("minecraft", "effect"), tint(0xA353FF)),
        Map.entry(ResourceLocation.fromNamespaceAndPath("minecraft", "ambient_entity_effect"), tint(0xA353FF)),
        Map.entry(ResourceLocation.fromNamespaceAndPath("minecraft", "entity_effect"), tint(0xA353FF)),
        Map.entry(ResourceLocation.fromNamespaceAndPath("minecraft", "instant_effect"), tint(0xB67CFF))
    );

    private final List<ResourceLocation> allParticles = new ArrayList<>();
    private final List<ResourceLocation> filteredParticles = new ArrayList<>();

    private SelectableEditBox searchBox;
    private ParticleList particleList;
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

    private double rightScrollAmount = 0.0D;
    private int maxRightScroll = 0;
    private final List<AbstractWidget> rightPanelWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> widgetOriginalY = new HashMap<>();

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
        updateCommandString();
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.font == null) {
            return;
        }
        this.clearWidgets();
        this.rightPanelWidgets.clear();
        this.widgetOriginalY.clear();
        this.rightScrollAmount = 0.0D;
        this.maxRightScroll = 0;
        updateLayout();

        this.searchBox = new SelectableEditBox(this.font, this.leftX + 8, this.leftY + 24, this.leftW - 16, 18, Component.literal("Pesquisar particula"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setResponder(value -> applySearchFilter());
        this.addRenderableWidget(this.searchBox);
        applySearchFilter();

        int sliderX = this.rightX + 5;
        int sliderW = this.rightW - 10;
        int currentY = this.rightY + 28;

        this.deltaXSlider = registerRightPanelWidget(new DoubleParameterSlider(sliderX, currentY, sliderW, 18, "Delta X", 0.0D, 5.0D, this.deltaX, 2, value -> {
            this.deltaX = value;
            updateCommandString();
        }));
        currentY += 24;

        this.deltaYSlider = registerRightPanelWidget(new DoubleParameterSlider(sliderX, currentY, sliderW, 18, "Delta Y", 0.0D, 5.0D, this.deltaY, 2, value -> {
            this.deltaY = value;
            updateCommandString();
        }));
        currentY += 24;

        this.deltaZSlider = registerRightPanelWidget(new DoubleParameterSlider(sliderX, currentY, sliderW, 18, "Delta Z", 0.0D, 5.0D, this.deltaZ, 2, value -> {
            this.deltaZ = value;
            updateCommandString();
        }));
        currentY += 24;

        this.speedSlider = registerRightPanelWidget(new DoubleParameterSlider(sliderX, currentY, sliderW, 18, "Speed", 0.0D, 1.0D, this.speed, 2, value -> {
            this.speed = value;
            updateCommandString();
        }));
        currentY += 24;

        this.countSlider = registerRightPanelWidget(new IntParameterSlider(sliderX, currentY, sliderW, 18, "Count", 1, 1000, this.count, value -> {
            this.count = value;
            updateCommandString();
        }));
        currentY += 28;

        this.forceToggle = registerRightPanelWidget(CycleButton.builder((Boolean value) -> Component.literal(value ? "FORCE" : "NORMAL"))
            .withValues(false, true)
            .withInitialValue(this.force)
            .displayOnlyValue()
            .create(sliderX, currentY, sliderW, 18, Component.literal("Modo"), (btn, value) -> {
                this.force = value;
                updateCommandString();
            }));
        currentY += 24;

        this.commandOutputBox = new SelectableEditBox(this.font, sliderX, currentY, sliderW, 18, Component.literal("Comando"));
        this.commandOutputBox.setEditable(false);
        this.commandOutputBox.setCanLoseFocus(true);
        registerRightPanelWidget(this.commandOutputBox);
        currentY += 26;

        this.testButton = Button.builder(Component.literal("Testar"), btn -> testCurrentCommand())
            .bounds(sliderX, currentY, (sliderW - 4) / 2, 20)
            .build();
        this.copyButton = Button.builder(Component.literal("Copiar"), btn -> copyCurrentCommand())
            .bounds(sliderX + ((sliderW - 4) / 2) + 4, currentY, (sliderW - 4) / 2, 20)
            .build();

        registerRightPanelWidget(this.testButton);
        registerRightPanelWidget(this.copyButton);

        currentY += 24;
        this.maxRightScroll = Math.max(0, currentY - this.height + 10);
        this.rightScrollAmount = Mth.clamp(this.rightScrollAmount, 0.0D, this.maxRightScroll);
        applyRightScroll();

        this.addRenderableWidget(Button.builder(Component.literal("Fechar"), btn -> onClose())
            .bounds(this.rightX + this.rightW - 69, this.rightY + this.rightH - 24, 64, 18)
            .build());

        refreshSelectedSpriteSafe();
        updateCommandString();
        if (this.particleList != null) {
            this.setFocused(this.particleList);
        }
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
        ResourceLocation nextSelection = this.selectedParticleId;
        if (nextSelection == null || !this.filteredParticles.contains(nextSelection)) {
            nextSelection = this.filteredParticles.isEmpty() ? null : this.filteredParticles.get(0);
        }
        this.selectedParticleId = nextSelection;
        rebuildParticleList();
        setSelectedParticle(nextSelection);
    }

    private void rebuildParticleList() {
        if (this.minecraft == null) {
            return;
        }
        if (this.particleList != null) {
            this.removeWidget(this.particleList);
        }

        this.particleList = new ParticleList(this.minecraft, this.listAreaW, this.height, this.listAreaY + 1, this.listAreaY + this.listAreaH - 1, ROW_HEIGHT, this.listAreaX);
        for (ResourceLocation id : this.filteredParticles) {
            if (id == null) {
                continue;
            }
            this.particleList.add(new ParticleEntry(id));
        }
        this.addRenderableWidget(this.particleList);

        if (this.selectedParticleId != null) {
            ParticleEntry entry = this.particleList.findEntry(this.selectedParticleId);
            if (entry != null) {
                this.particleList.setSelected(entry);
                this.particleList.ensureEntryVisible(entry);
            }
        } else if (!this.particleList.children().isEmpty()) {
            ParticleEntry entry = this.particleList.children().get(0);
            this.particleList.setSelected(entry);
            setSelectedParticle(entry.getParticleId());
        }
    }

    private void setSelectedParticle(ResourceLocation id) {
        this.selectedParticleId = id;
        refreshSelectedSpriteSafe();
        updateCommandString();

        if (this.particleList == null) {
            return;
        }
        if (id == null) {
            this.particleList.setSelected(null);
            return;
        }
        ParticleEntry entry = this.particleList.findEntry(id);
        if (entry != null) {
            this.particleList.setSelected(entry);
            this.particleList.ensureEntryVisible(entry);
        }
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
        if (this.testButton != null) {
            this.testButton.active = hasSelection;
        }
        if (this.copyButton != null) {
            this.copyButton.active = hasSelection;
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX >= (this.width - this.rightW) && this.maxRightScroll > 0) {
            this.rightScrollAmount -= scrollY * 15.0D;
            this.rightScrollAmount = Mth.clamp(this.rightScrollAmount, 0.0D, this.maxRightScroll);
            applyRightScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (this.particleList != null && !this.particleList.children().isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                List<ParticleEntry> entries = this.particleList.children();
                ParticleEntry current = this.particleList.getSelected();
                int currentIndex = current != null ? entries.indexOf(current) : 0;
                int newIndex = keyCode == GLFW.GLFW_KEY_UP
                    ? Math.max(0, currentIndex - 1)
                    : Math.min(entries.size() - 1, currentIndex + 1);

                ParticleEntry entry = entries.get(newIndex);
                this.particleList.setSelected(entry);
                setSelectedParticle(entry.getParticleId());
                this.particleList.ensureEntryVisible(entry);
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null || this.font == null) {
            return;
        }

        updateLayout();

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

        GuiLayoutUtils.drawPanel(graphics, this.listAreaX, this.listAreaY, this.listAreaW, this.listAreaH, 0xB1121924, 0xFF2E4258);

        renderPreviewPanel(graphics);

        boolean clipRightPanel = !this.rightPanelWidgets.isEmpty();
        if (clipRightPanel) {
            for (AbstractWidget widget : this.rightPanelWidgets) {
                widget.visible = false;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.particleList != null && this.particleList.entryCount() == 0) {
            graphics.drawCenteredString(this.font, "Nenhuma particula encontrada", this.listAreaX + (this.listAreaW / 2), this.listAreaY + 8, 0xFFFF8A8A);
        }

        graphics.fillGradient(this.listAreaX + 1, this.listAreaY + 1, this.listAreaX + this.listAreaW - 1, this.listAreaY + 9, 0xCC111824, 0x00111824);
        graphics.fillGradient(this.listAreaX + 1, this.listAreaY + this.listAreaH - 9, this.listAreaX + this.listAreaW - 1, this.listAreaY + this.listAreaH - 1, 0x00111824, 0xCC111824);

        if (clipRightPanel) {
            for (AbstractWidget widget : this.rightPanelWidgets) {
                widget.visible = true;
            }
            graphics.enableScissor(this.rightX, 0, this.width, this.height);
            for (AbstractWidget widget : this.rightPanelWidgets) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
            graphics.disableScissor();
        }

        renderRightScrollbar(graphics);
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
        float[] tint = this.selectedParticleId != null ? PARTICLE_TINTS.get(this.selectedParticleId) : null;
        if (tint != null) {
            graphics.setColor(tint[0], tint[1], tint[2], 1.0F);
        }
        graphics.blit(0, 0, 0, spriteW, spriteH, animatedSprite);
        if (tint != null) {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
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

    private void applyRightScroll() {
        for (AbstractWidget widget : this.rightPanelWidgets) {
            Integer baseY = this.widgetOriginalY.get(widget);
            if (baseY != null) {
                widget.setY(baseY - (int) this.rightScrollAmount);
            }
        }
    }

    private <T extends AbstractWidget> T registerRightPanelWidget(T widget) {
        this.rightPanelWidgets.add(widget);
        this.widgetOriginalY.put(widget, widget.getY());
        this.addRenderableWidget(widget);
        return widget;
    }

    private void renderRightScrollbar(GuiGraphics graphics) {
        if (this.maxRightScroll <= 0) {
            return;
        }
        graphics.fill(this.width - 6, 0, this.width, this.height, 0xFF000000);

        int thumbHeight = Math.max(20, (int) ((this.height / (float) (this.height + this.maxRightScroll)) * this.height));
        int thumbY = (int) ((this.rightScrollAmount / this.maxRightScroll) * (this.height - thumbHeight));
        graphics.fill(this.width - 5, thumbY, this.width - 1, thumbY + thumbHeight, 0xFF888888);
    }

    private static float[] tint(int rgb) {
        return new float[] {
            ((rgb >> 16) & 0xFF) / 255.0F,
            ((rgb >> 8) & 0xFF) / 255.0F,
            (rgb & 0xFF) / 255.0F
        };
    }

    private final class ParticleList extends ObjectSelectionList<ParticleEntry> {
        private final int leftX;
        private final int listWidth;

        private ParticleList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight, int leftX) {
            super(mc, width, height, y0, y1, itemHeight);
            this.leftX = leftX;
            this.listWidth = width;
            this.x0 = leftX;
            this.x1 = leftX + width;
        }

        private void add(ParticleEntry entry) {
            this.addEntry(entry);
        }

        private int entryCount() {
            return this.getItemCount();
        }

        private ParticleEntry findEntry(ResourceLocation id) {
            for (ParticleEntry entry : this.children()) {
                if (entry.getParticleId().equals(id)) {
                    return entry;
                }
            }
            return null;
        }

        private void ensureEntryVisible(ParticleEntry entry) {
            int index = this.children().indexOf(entry);
            if (index < 0) {
                return;
            }

            int rowTop = this.y0 + 4 - (int) this.getScrollAmount() + index * this.itemHeight;
            int rowBottom = rowTop + this.itemHeight;
            if (rowTop < this.y0) {
                this.setScrollAmount(this.getScrollAmount() - (this.y0 - rowTop));
            } else if (rowBottom > this.y1) {
                this.setScrollAmount(this.getScrollAmount() + (rowBottom - this.y1));
            }
        }

        @Override
        protected int getScrollbarPosition() {
            return this.leftX + this.listWidth - 6;
        }

        @Override
        public int getRowWidth() {
            return this.listWidth - 15;
        }
    }

    private final class ParticleEntry extends ObjectSelectionList.Entry<ParticleEntry> {
        private final ResourceLocation id;

        private ParticleEntry(ResourceLocation id) {
            this.id = id;
        }

        private ResourceLocation getParticleId() {
            return this.id;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTicks) {
            boolean selected = this.id.equals(ParticleStudioScreen.this.selectedParticleId);
            int rowColor = selected ? 0xAA2B4E72 : (isMouseOver ? 0xAA263445 : 0x55202A36);
            graphics.fill(left - 4, top, left + width - 4, top + height - 1, rowColor);

            String namespace = this.id.getNamespace();
            String path = this.id.getPath();
            String prefix = namespace + ":";
            int prefixX = left + 4;
            int prefixW = Minecraft.getInstance().font.width(prefix);
            int pathX = prefixX + prefixW;
            int maxTextX = left + width - 6;
            int pathWidth = Math.max(0, maxTextX - pathX);
            String clippedPath = Minecraft.getInstance().font.plainSubstrByWidth(path, pathWidth);

            graphics.drawString(Minecraft.getInstance().font, prefix, prefixX, top + 5, 0xFFE8CC6D);
            graphics.drawString(Minecraft.getInstance().font, clippedPath, pathX, top + 5, 0xFFF2F6FF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            ParticleStudioScreen.this.setSelectedParticle(this.id);
            return true;
        }

        @Override
        public @NotNull Component getNarration() {
            return Component.literal(this.id.toString());
        }
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
