package com.gabri.serverfixes.client.gui;
import com.gabri.serverfixes.client.gui.GuiLayoutUtils.VerticalLayoutBuilder;
import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import net.minecraft.client.gui.components.Tooltip;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleConsumer;

@SuppressWarnings("null")
public class SoundStudioScreen extends Screen {
    private static final ResourceLocation PLAY_ICON = ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/play.png");
    private static final ResourceLocation PAUSE_ICON = ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/pause.png");

    private final List<ResourceLocation> allSounds = new ArrayList<>();
    private final List<ResourceLocation> filteredSounds = new ArrayList<>();

    private SelectableEditBox searchBox;
    private SelectableEditBox commandOutputBox;

    private double leftScrollAmount = 0.0D;
    private int maxLeftScroll = 0;
    private final List<SoundButton> soundButtons = new ArrayList<>();
    private final Map<SoundButton, Integer> buttonOriginalY = new HashMap<>();
    private int selectedSoundIndex = 0;
    private ResourceLocation selectedSoundId;
    private boolean leftScrollbarDragging = false;
    private double leftScrollbarDragOffset = 0.0D;

    private CycleButton<SoundSource> categoryToggle;
    private DoubleParameterSlider volumeSlider;
    private DoubleParameterSlider pitchSlider;
    private IconButton playButton;
    private IconButton stopButton;
    private Button copyButton;
    private Button closeButton;

    private double rightScrollAmount = 0.0D;
    private int maxRightScroll = 0;
    private final List<AbstractWidget> rightPanelWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> widgetOriginalY = new HashMap<>();

    private int leftX;
    private int leftY;
    private int leftW;
    private int leftH;

    private int rightX;
    private int rightY;
    private int rightW;
    private int rightH;

    private int listAreaX;
    private int listAreaY;
    private int listAreaW;
    private int listAreaH;

    private double volume = 1.0D;
    private double pitch = 1.0D;
    private SoundSource category = SoundSource.MASTER;

    private String commandString = "";

    public SoundStudioScreen() {
        super(Component.literal("Sound Studio"));
        if (ForgeRegistries.SOUND_EVENTS != null && ForgeRegistries.SOUND_EVENTS.getKeys() != null) {
            this.allSounds.addAll(ForgeRegistries.SOUND_EVENTS.getKeys().stream().toList());
        }
        this.allSounds.removeIf(Objects::isNull);
        this.allSounds.sort(Comparator
            .comparing(ResourceLocation::getNamespace)
            .thenComparing(ResourceLocation::getPath));

        this.filteredSounds.addAll(this.allSounds);
        if (!this.filteredSounds.isEmpty()) {
            this.selectedSoundId = this.filteredSounds.get(0);
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
        this.soundButtons.clear();
        this.buttonOriginalY.clear();
        this.leftScrollAmount = 0.0D;
        this.maxLeftScroll = 0;
        this.selectedSoundIndex = 0;

        this.rightPanelWidgets.clear();
        this.widgetOriginalY.clear();
        this.rightScrollAmount = 0.0D;
        this.maxRightScroll = 0;

        updateLayout();

        this.searchBox = new SelectableEditBox(this.font, this.leftX + 8, this.leftY + 24, this.listAreaW, 18, Component.literal("Pesquisar som"));
        this.searchBox.setMaxLength(100);
        this.searchBox.setResponder(value -> applySearchFilter());
        this.addRenderableWidget(this.searchBox);
        applySearchFilter();

        int sliderX = this.rightX + 5;
        int sliderW = this.rightW - 10;
        VerticalLayoutBuilder rightBuilder = new VerticalLayoutBuilder(sliderX, this.rightY + 28, sliderW, 5);

        this.categoryToggle = rightBuilder.add(CycleButton.builder((SoundSource source) -> Component.literal(source.name()))
            .withValues(SoundSource.values())
            .withInitialValue(this.category)
            .displayOnlyValue()
            .create(sliderX, 0, sliderW, 18, Component.literal("Categoria"), (btn, value) -> {
                this.category = value;
                updateCommandString();
            }));
        registerRightPanelWidget(this.categoryToggle);

        this.volumeSlider = rightBuilder.add(new DoubleParameterSlider(sliderX, 0, sliderW, 18, "Volume", 0.0D, 2.0D, this.volume, 2, value -> {
            this.volume = value;
            updateCommandString();
        }));
        registerRightPanelWidget(this.volumeSlider);
        this.volumeSlider.setTooltip(Tooltip.create(Component.literal("Define o raio de alcance e a distância máxima em que o som pode ser ouvido.")));

        this.pitchSlider = rightBuilder.add(new DoubleParameterSlider(sliderX, 0, sliderW, 18, "Pitch", 0.0D, 2.0D, this.pitch, 2, value -> {
            this.pitch = value;
            updateCommandString();
        }));
        registerRightPanelWidget(this.pitchSlider);
        this.pitchSlider.setTooltip(Tooltip.create(Component.literal("Altera a tonalidade (grave ou agudo) e a velocidade de reprodução do áudio.")));

        this.playButton = new IconButton(0, 0, 40, 18, PLAY_ICON, 16, 16, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A, btn -> playSelectedSound());
        this.stopButton = new IconButton(0, 0, 40, 18, PAUSE_ICON, 16, 16, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A, btn -> stopAllSounds());
        registerRightPanelWidget(this.playButton);
        registerRightPanelWidget(this.stopButton);

        this.commandOutputBox = rightBuilder.add(new SelectableEditBox(this.font, 0, 0, sliderW, 18, Component.literal("Comando")));
        this.commandOutputBox.setEditable(false);
        this.commandOutputBox.setCanLoseFocus(true);
        registerRightPanelWidget(this.commandOutputBox);

        this.copyButton = Button.builder(Component.literal("Copiar"), btn -> copyCurrentCommand()).bounds(0, 0, 64, 18).build();
        registerRightPanelWidget(this.copyButton);

        this.closeButton = Button.builder(Component.literal("Fechar"), btn -> onClose()).bounds(0, 0, 64, 18).build();
        registerRightPanelWidget(this.closeButton);

        recalculateRightPanel();
        updateCommandString();
    }

    private void updateLayout() {
        int contentY = 20;
        int contentH = this.height - 28;

        int panelGap = 4;
        this.rightW = Math.max(220, this.width / 3);
        this.leftW = Math.max(140, this.width - this.rightW - panelGap);
        this.rightX = this.width - this.rightW;

        this.leftX = 0;

        this.leftY = contentY;
        this.rightY = contentY;

        this.leftH = contentH;
        this.rightH = contentH;

        int searchY = this.leftY + 24;
        int searchH = 18;
        this.listAreaX = this.leftX + 8;
        this.listAreaY = searchY + searchH + 8;
        this.listAreaW = this.leftW - 26;
        this.listAreaH = this.leftH - (this.listAreaY - this.leftY) - 8;
    }

    private void applySearchFilter() {
        String query = this.searchBox != null ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        this.filteredSounds.clear();

        if (query.isEmpty()) {
            this.filteredSounds.addAll(this.allSounds);
        } else {
            for (ResourceLocation id : this.allSounds) {
                if (id == null) {
                    continue;
                }
                String full = id.toString().toLowerCase(Locale.ROOT);
                if (full.contains(query)
                    || id.getNamespace().toLowerCase(Locale.ROOT).contains(query)
                    || id.getPath().toLowerCase(Locale.ROOT).contains(query)) {
                    this.filteredSounds.add(id);
                }
            }
        }

        this.filteredSounds.removeIf(Objects::isNull);
        ResourceLocation nextSelection = this.selectedSoundId;
        if (nextSelection == null || !this.filteredSounds.contains(nextSelection)) {
            nextSelection = this.filteredSounds.isEmpty() ? null : this.filteredSounds.get(0);
        }

        rebuildSoundButtons();

        if (nextSelection == null || this.filteredSounds.isEmpty()) {
            this.selectedSoundIndex = 0;
            setSelectedSoundId(null);
            return;
        }

        int index = this.filteredSounds.indexOf(nextSelection);
        setSelectedSoundIndex(index < 0 ? 0 : index);
    }

    private void rebuildSoundButtons() {
        this.soundButtons.clear();
        this.buttonOriginalY.clear();
        this.leftScrollAmount = 0.0D;

        int buttonX = this.leftX + 8;
        int buttonW = Math.max(10, this.listAreaW - 2);
        int buttonH = 20;
        int currentLeftY = this.listAreaY + 1;

        for (ResourceLocation id : this.filteredSounds) {
            if (id == null) {
                continue;
            }
            int index = this.soundButtons.size();
            SoundButton button = new SoundButton(buttonX, currentLeftY, buttonW, buttonH, id, btn -> setSelectedSoundIndex(index));
            this.soundButtons.add(button);
            this.buttonOriginalY.put(button, currentLeftY);
            currentLeftY += 22;
        }

        int listBottom = this.listAreaY + this.listAreaH - 10;
        this.maxLeftScroll = Math.max(0, currentLeftY - listBottom);
        this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private void setSelectedSoundIndex(int index) {
        if (this.filteredSounds.isEmpty()) {
            this.selectedSoundIndex = 0;
            setSelectedSoundId(null);
            return;
        }

        int clamped = Mth.clamp(index, 0, this.filteredSounds.size() - 1);
        this.selectedSoundIndex = clamped;
        setSelectedSoundId(this.filteredSounds.get(clamped));

        if (clamped >= 0 && clamped < this.soundButtons.size()) {
            ensureLeftButtonVisible(this.soundButtons.get(clamped));
        }
    }

    private void setSelectedSoundId(ResourceLocation id) {
        this.selectedSoundId = id;
        updateCommandString();
    }

    private void updateCommandString() {
        if (this.selectedSoundId == null) {
            this.commandString = "";
        } else {
            this.commandString = String.format(
                Locale.ROOT,
                "/playsound %s %s @s ~ ~ ~ %.2f %.2f",
                this.selectedSoundId,
                this.category.name().toLowerCase(Locale.ROOT),
                this.volume,
                this.pitch
            );
        }

        if (this.commandOutputBox != null) {
            this.commandOutputBox.setValue(this.commandString);
        }

        boolean hasSelection = this.selectedSoundId != null && !this.commandString.isBlank();
        if (this.playButton != null) this.playButton.active = hasSelection;
        if (this.copyButton != null) this.copyButton.active = hasSelection;
    }

    private void playSelectedSound() {
        if (this.minecraft == null || this.selectedSoundId == null) {
            return;
        }

        SimpleSoundInstance sound = new SimpleSoundInstance(
            this.selectedSoundId,
            this.category,
            (float) this.volume,
            (float) this.pitch,
            RandomSource.create(),
            false,
            0,
            SoundInstance.Attenuation.NONE,
            0.0D,
            0.0D,
            0.0D,
            true
        );
        this.minecraft.getSoundManager().play(sound);
    }

    private void stopAllSounds() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.getSoundManager().stop();
    }

    private void copyCurrentCommand() {
        if (this.minecraft == null || this.commandString.isBlank()) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(this.commandString);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.maxLeftScroll > 0 && isInsideLeftScrollbar(mouseX, mouseY)) {
            startLeftScrollbarDrag(mouseY);
            return true;
        }
        if (isInsideLeftList(mouseX, mouseY)) {
            for (SoundButton soundButton : this.soundButtons) {
                if (soundButton.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.leftScrollbarDragging) {
            updateLeftScrollbarDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.leftScrollbarDragging) {
            this.leftScrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (isInsideLeftList(mouseX, mouseY) && this.maxLeftScroll > 0) {
            this.leftScrollAmount -= scrollY * 15.0D;
            this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
            applyLeftScroll();
            return true;
        }
        if (isInsideRightPanel(mouseX, mouseY) && this.maxRightScroll > 0) {
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

        if (!this.soundButtons.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                int currentIndex = this.selectedSoundIndex;
                int newIndex = keyCode == GLFW.GLFW_KEY_UP
                    ? Math.max(0, currentIndex - 1)
                    : Math.min(this.soundButtons.size() - 1, currentIndex + 1);

                setSelectedSoundIndex(newIndex);
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
        recalculateRightPanel();

        graphics.fill(0, 0, this.width, this.height, 0xDD000000);
        graphics.fill(this.leftX, this.leftY, this.leftX + this.leftW, this.leftY + this.leftH, 0x44242D3D);
        graphics.fill(this.rightX, this.rightY, this.rightX + this.rightW, this.rightY + this.rightH, 0x44242D3D);

        GuiLayoutUtils.drawPanel(graphics, this.leftX, this.leftY, this.leftW, this.leftH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.rightX, this.rightY, this.rightW, this.rightH, 0x001A2434, 0xFF4E6B8D);

        graphics.drawString(this.font, "Sons", this.leftX + 8, this.leftY + 8, 0xFFF4F7FF);
        graphics.drawString(this.font, "Command Builder", this.rightX + 8, this.rightY + 8, 0xFFF4F7FF);

        GuiLayoutUtils.drawPanel(graphics, this.listAreaX, this.listAreaY, this.listAreaW, this.listAreaH, 0xB1121924, 0xFF2E4258);
        renderLeftButtons(graphics, mouseX, mouseY, partialTick);

        boolean clipRightPanel = !this.rightPanelWidgets.isEmpty();
        Map<AbstractWidget, Boolean> visibilityMemory = new HashMap<>();
        if (clipRightPanel) {
            for (AbstractWidget widget : this.rightPanelWidgets) {
                visibilityMemory.put(widget, widget.visible);
                widget.visible = false;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.soundButtons.isEmpty()) {
            graphics.drawCenteredString(this.font, "Nenhum som encontrado", this.listAreaX + (this.listAreaW / 2), this.listAreaY + 8, 0xFFFF8A8A);
        }

        graphics.fillGradient(this.listAreaX + 1, this.listAreaY + 1, this.listAreaX + this.listAreaW - 1, this.listAreaY + 9, 0xCC111824, 0x00111824);
        graphics.fillGradient(this.listAreaX + 1, this.listAreaY + this.listAreaH - 9, this.listAreaX + this.listAreaW - 1, this.listAreaY + this.listAreaH - 1, 0x00111824, 0xCC111824);

        if (clipRightPanel) {
            for (AbstractWidget widget : this.rightPanelWidgets) {
                widget.visible = visibilityMemory.getOrDefault(widget, true);
            }
            graphics.enableScissor(this.rightX, this.rightY, this.rightX + this.rightW, this.rightY + this.rightH);
            for (AbstractWidget widget : this.rightPanelWidgets) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
            graphics.disableScissor();
        }

        renderLeftScrollbar(graphics);
        renderRightScrollbar(graphics);
    }

    private void renderLeftButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.soundButtons.isEmpty()) {
            return;
        }
        graphics.enableScissor(this.leftX, this.listAreaY, this.leftX + this.leftW, this.listAreaY + this.listAreaH);
        for (SoundButton soundButton : this.soundButtons) {
            soundButton.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
    }

    private void applyLeftScroll() {
        for (SoundButton soundButton : this.soundButtons) {
            Integer baseY = this.buttonOriginalY.get(soundButton);
            if (baseY != null) {
                soundButton.setY(baseY - (int) this.leftScrollAmount);
            }
        }
    }

    private void ensureLeftButtonVisible(SoundButton button) {
        if (button == null) {
            return;
        }
        Integer baseY = this.buttonOriginalY.get(button);
        if (baseY == null) {
            return;
        }
        int topLimit = this.listAreaY;
        int bottomLimit = this.listAreaY + this.listAreaH - 10;
        int screenY = baseY - (int) this.leftScrollAmount;
        int buttonHeight = button.getHeight();

        if (screenY < topLimit) {
            this.leftScrollAmount = baseY - topLimit;
        } else if (screenY + buttonHeight > bottomLimit) {
            this.leftScrollAmount = (baseY + buttonHeight) - bottomLimit;
        }

        this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private boolean isInsideLeftList(double mouseX, double mouseY) {
        return mouseX >= this.leftX && mouseX < this.leftX + this.leftW
            && mouseY >= this.listAreaY && mouseY < this.listAreaY + this.listAreaH;
    }

    private boolean isInsideRightPanel(double mouseX, double mouseY) {
        return mouseX >= this.rightX && mouseX < this.rightX + this.rightW
            && mouseY >= this.rightY && mouseY < this.rightY + this.rightH;
    }

    private boolean isInsideLeftScrollbar(double mouseX, double mouseY) {
        int listRight = this.listAreaX + this.listAreaW;
        int rightEdge = this.leftX + this.leftW;
        int reserved = Math.max(0, rightEdge - listRight);
        int trackWidth = 6;
        if (reserved >= 10) trackWidth = Math.min(10, reserved - 2);
        int barX = listRight + (reserved - trackWidth) / 2;

        return mouseX >= barX && mouseX < barX + trackWidth
            && mouseY >= this.listAreaY && mouseY < this.listAreaY + this.listAreaH;
    }

    private void startLeftScrollbarDrag(double mouseY) {
        int trackTop = this.listAreaY;
        int trackHeight = this.listAreaH;
        int thumbHeight = getLeftScrollbarThumbHeight(trackHeight);
        int thumbY = getLeftScrollbarThumbY(trackTop, trackHeight, thumbHeight);

        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            this.leftScrollbarDragOffset = mouseY - thumbY;
        } else {
            this.leftScrollbarDragOffset = thumbHeight / 2.0D;
        }

        this.leftScrollbarDragging = true;
        updateLeftScrollbarDrag(mouseY);
    }

    private void updateLeftScrollbarDrag(double mouseY) {
        if (this.maxLeftScroll <= 0) {
            return;
        }
        int trackTop = this.listAreaY;
        int trackHeight = this.listAreaH;
        int thumbHeight = getLeftScrollbarThumbHeight(trackHeight);
        int thumbY = (int) Math.round(mouseY - this.leftScrollbarDragOffset);
        setLeftScrollFromThumbY(thumbY, trackTop, trackHeight, thumbHeight);
    }

    private int getLeftScrollbarThumbHeight(int trackHeight) {
        return Math.max(20, (int) ((trackHeight / (float) (trackHeight + this.maxLeftScroll)) * trackHeight));
    }

    private int getLeftScrollbarThumbY(int trackTop, int trackHeight, int thumbHeight) {
        if (this.maxLeftScroll <= 0) {
            return trackTop;
        }
        int available = Math.max(1, trackHeight - thumbHeight);
        return trackTop + (int) ((this.leftScrollAmount / this.maxLeftScroll) * available);
    }

    private void setLeftScrollFromThumbY(int thumbY, int trackTop, int trackHeight, int thumbHeight) {
        if (this.maxLeftScroll <= 0) {
            return;
        }
        int available = Math.max(1, trackHeight - thumbHeight);
        int clamped = Mth.clamp(thumbY, trackTop, trackTop + available);
        double ratio = (clamped - trackTop) / (double) available;
        this.leftScrollAmount = ratio * this.maxLeftScroll;
        this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private void applyRightScroll() {
        for (AbstractWidget widget : this.rightPanelWidgets) {
            Integer baseY = this.widgetOriginalY.get(widget);
            if (baseY != null) {
                widget.setY(baseY - (int) this.rightScrollAmount);
            }
        }
    }

    private void recalculateRightPanel() {
        int currentY = this.rightY + 28;
        int gap = 5;
        int rowX = this.rightX + 5;
        int rowW = this.rightW - 10;

        this.widgetOriginalY.clear();

        if (this.categoryToggle != null && this.categoryToggle.visible) {
            this.categoryToggle.setX(rowX);
            this.categoryToggle.setY(currentY);
            this.categoryToggle.setWidth(rowW);
            this.widgetOriginalY.put(this.categoryToggle, currentY);
            currentY += this.categoryToggle.getHeight() + gap;
        }

        if (this.volumeSlider != null && this.volumeSlider.visible) {
            this.volumeSlider.setX(rowX);
            this.volumeSlider.setY(currentY);
            this.volumeSlider.setWidth(rowW);
            this.widgetOriginalY.put(this.volumeSlider, currentY);
            currentY += this.volumeSlider.getHeight() + gap;
        }

        if (this.pitchSlider != null && this.pitchSlider.visible) {
            this.pitchSlider.setX(rowX);
            this.pitchSlider.setY(currentY);
            this.pitchSlider.setWidth(rowW);
            this.widgetOriginalY.put(this.pitchSlider, currentY);
            currentY += this.pitchSlider.getHeight() + gap;
        }

        if (this.playButton != null && this.stopButton != null) {
            int buttonGap = 5;
            int buttonW = Math.max(20, (rowW - buttonGap) / 2);

            this.playButton.setX(rowX);
            this.playButton.setY(currentY);
            this.playButton.setWidth(buttonW);
            this.stopButton.setX(rowX + buttonW + buttonGap);
            this.stopButton.setY(currentY);
            this.stopButton.setWidth(buttonW);

            this.widgetOriginalY.put(this.playButton, currentY);
            this.widgetOriginalY.put(this.stopButton, currentY);
            currentY += this.playButton.getHeight() + gap;
        }

        if (this.commandOutputBox != null && this.commandOutputBox.visible) {
            this.commandOutputBox.setX(rowX);
            this.commandOutputBox.setY(currentY);
            this.commandOutputBox.setWidth(rowW);
            this.widgetOriginalY.put(this.commandOutputBox, currentY);
            currentY += this.commandOutputBox.getHeight() + gap;
        }

        if (this.copyButton != null && this.closeButton != null) {
            int buttonGap = 5;
            int buttonW = Math.max(20, (rowW - buttonGap) / 2);

            this.copyButton.setX(rowX);
            this.copyButton.setY(currentY);
            this.copyButton.setWidth(buttonW);
            this.closeButton.setX(rowX + buttonW + buttonGap);
            this.closeButton.setY(currentY);
            this.closeButton.setWidth(buttonW);

            this.widgetOriginalY.put(this.copyButton, currentY);
            this.widgetOriginalY.put(this.closeButton, currentY);
            currentY += this.copyButton.getHeight() + gap;
        }

        int visibleBottom = this.rightY + this.rightH - 8;
        this.maxRightScroll = Math.max(0, currentY - visibleBottom);
        this.rightScrollAmount = Mth.clamp(this.rightScrollAmount, 0.0D, this.maxRightScroll);
        applyRightScroll();
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

        int top = this.rightY + 1;
        int bottom = this.rightY + this.rightH - 1;
        int trackHeight = Math.max(1, bottom - top);
        int barX = this.rightX + this.rightW - 7;

        graphics.fill(barX, top, barX + 5, bottom, 0xFF000000);

        int thumbHeight = Math.max(20, (int) ((trackHeight / (float) (trackHeight + this.maxRightScroll)) * trackHeight));
        int available = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + (int) ((this.rightScrollAmount / Math.max(1, this.maxRightScroll)) * available);
        graphics.fill(barX + 1, thumbY, barX + 4, thumbY + thumbHeight, 0xFF888888);
    }

    private void renderLeftScrollbar(GuiGraphics graphics) {
        if (this.maxLeftScroll <= 0) {
            return;
        }
        int listRight = this.listAreaX + this.listAreaW;
        int rightEdge = this.leftX + this.leftW;
        int reserved = Math.max(0, rightEdge - listRight);
        int trackWidth = 6;
        if (reserved >= 10) trackWidth = Math.min(10, reserved - 2);
        int barX = listRight + (reserved - trackWidth) / 2;

        int top = this.listAreaY;
        int bottom = this.listAreaY + this.listAreaH;
        int trackHeight = bottom - top;

        graphics.fill(barX, top, barX + trackWidth, bottom, 0xFF000000);

        int thumbHeight = getLeftScrollbarThumbHeight(trackHeight);
        int thumbY = getLeftScrollbarThumbY(top, trackHeight, thumbHeight);
        graphics.fill(barX + 1, thumbY, barX + trackWidth - 1, thumbY + thumbHeight, 0xFF888888);
    }

    private final class SoundButton extends Button {
        private final ResourceLocation soundId;

        private SoundButton(int x, int y, int width, int height, ResourceLocation soundId, OnPress onPress) {
            super(x, y, width, height, Component.literal(soundId.toString()), onPress, DEFAULT_NARRATION);
            this.soundId = soundId;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = this.soundId.equals(SoundStudioScreen.this.selectedSoundId);
            boolean hovered = this.isHoveredOrFocused();
            int rowColor = selected ? 0xAA2B4E72 : (hovered ? 0xAA263445 : 0x55202A36);
            int left = this.getX();
            int top = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            graphics.fill(left, top, left + width, top + height - 1, rowColor);

            String namespace = this.soundId.getNamespace();
            String path = this.soundId.getPath();
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
            if (max <= min) {
                return 0.0D;
            }
            return Mth.clamp((value - min) / (max - min), 0.0D, 1.0D);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}


