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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import java.lang.reflect.Method;
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
/**
 * Tela de edição e construção de partículas dentro do jogo.
 *
 * Layout:
 * - Coluna esquerda ("Particulas"): lista pesquisável de tipos de partículas registradas.
 * - Centro ("Preview 2D"): preview do sprite/estado da partícula selecionada.
 * - Direita ("Command Builder"): controles (sliders, campos e botões) para montar o
 *   comando `/particle` e testar/copiá-lo.
 *
 * Comportamento importante:
 * - A coluna esquerda implementa scroll manual (arraste do thumb + roda do mouse).
 * - A coluna direita calcula dinamicamente a posição vertical dos widgets visíveis
 *   e mantém `maxRightScroll`/`rightScrollAmount` para navegação por scroll.
 * - A visibilidade e disponibilidade de `extraArgsBox` e `colorButton` são
 *   decididas por `updateExtraArgsAvailability()` de acordo com o tipo de partícula.
 */
public class ParticleStudioScreen extends Screen {
    // Mapa de tint (RGB) para partículas específicas que precisam de coloração no preview.
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

    // Todas as partículas conhecidas (do registry) e a lista filtrada pela busca.
    private final List<ResourceLocation> allParticles = new ArrayList<>();
    private final List<ResourceLocation> filteredParticles = new ArrayList<>();

    // Caixas de texto usadas pela UI.
    private SelectableEditBox searchBox;      // caixa de busca (editável)
    private SelectableEditBox commandOutputBox; // campo somente-leitura com o comando gerado

    // Estado do scroll na coluna esquerda ("Particulas").
    // leftScrollAmount: deslocamento atual (pixels). maxLeftScroll: limite máximo.
    private double leftScrollAmount = 0.0D;
    private int maxLeftScroll = 0;
    private final List<ParticleButton> particleButtons = new ArrayList<>();
    private final Map<ParticleButton, Integer> buttonOriginalY = new HashMap<>(); // posição Y original (sem scroll)
    private int selectedParticleIndex = 0;
    private boolean leftScrollbarDragging = false;
    private double leftScrollbarDragOffset = 0.0D;

    // Widgets e controles da coluna direita (Command Builder)
    private Button testButton;
    private Button copyButton;
    private SelectableEditBox extraArgsBox;
    private Button colorButton;
    private DoubleParameterSlider dxSlider;
    private DoubleParameterSlider dySlider;
    private DoubleParameterSlider dzSlider;
    private DoubleParameterSlider speedSlider;
    private IntParameterSlider countSlider;
    private CycleButton<Boolean> forceToggle;

    // Seleção e preview
    private ResourceLocation selectedParticleId;      // ID da partícula selecionada
    private ResourceLocation selectedPreviewSpriteId; // ID do sprite usado no preview (se houver)
    private boolean previewHardcoded;                 // true se o preview não usa atlas

    // Estado do scroll na coluna direita (widgets dinamicamente posicionados)
    private double rightScrollAmount = 0.0D;
    private int maxRightScroll = 0;
    private final List<AbstractWidget> rightPanelWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> widgetOriginalY = new HashMap<>();

    // Posições e dimensões de cada painel; calculadas por updateLayout()
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

    // Parâmetros usados para montar o comando /particle
    private double deltaX = 0.5D;
    private double deltaY = 0.5D;
    private double deltaZ = 0.5D;
    private double speed = 0.01D;
    private int count = 25;
    private boolean force = false;

    // Comando gerado pela UI (exibido em `commandOutputBox`)
    private String commandString = "";

    public ParticleStudioScreen() {
        super(Component.literal("Particle Studio"));
        /**
         * Construtor — recupera IDs de partículas do registro e ordena a lista.
         * Observação: não cria widgets; `init()` é responsável por construir a UI.
         */
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
        /**
         * Inicializa/reconstrói todos os widgets da tela.
         * - Limpa estados anteriores
         * - Calcula layout via `updateLayout()`
         * - Cria widgets da coluna direita e registra-os
         * - Aplica filtro de busca e atualiza disponibilidade de campos extras
         */
        super.init();
        if (this.minecraft == null || this.font == null) {
            return;
        }
        this.clearWidgets();
        this.particleButtons.clear();
        this.buttonOriginalY.clear();
        this.leftScrollAmount = 0.0D;
        this.maxLeftScroll = 0;
        this.selectedParticleIndex = 0;
        this.rightPanelWidgets.clear();
        this.widgetOriginalY.clear();
        this.rightScrollAmount = 0.0D;
        this.maxRightScroll = 0;
        updateLayout();
        // Right panel will be recalculated after widgets are created

        // Use `listAreaW` so the search box aligns with the constrained list width.
        this.searchBox = new SelectableEditBox(this.font, this.leftX + 8, this.leftY + 24, this.listAreaW, 18, Component.literal("Pesquisar particula"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setResponder(value -> applySearchFilter());
        this.addRenderableWidget(this.searchBox);
        applySearchFilter();

        int sliderX = this.rightX + 5;
        int sliderW = this.rightW - 10;

        GuiLayoutUtils.VerticalLayoutBuilder rightBuilder = new GuiLayoutUtils.VerticalLayoutBuilder(sliderX, this.rightY + 28, sliderW, 5);

        this.dxSlider = rightBuilder.add(new DoubleParameterSlider(sliderX, 0, sliderW, 18, "Delta X", 0.0D, 5.0D, this.deltaX, 2, value -> {
            this.deltaX = value;
            updateCommandString();
        }));
        registerRightPanelWidget(this.dxSlider);

        this.dySlider = rightBuilder.add(new DoubleParameterSlider(sliderX, 0, sliderW, 18, "Delta Y", 0.0D, 5.0D, this.deltaY, 2, value -> {
            this.deltaY = value;
            updateCommandString();
        }));
        registerRightPanelWidget(this.dySlider);

        this.dzSlider = rightBuilder.add(new DoubleParameterSlider(sliderX, 0, sliderW, 18, "Delta Z", 0.0D, 5.0D, this.deltaZ, 2, value -> {
            this.deltaZ = value;
            updateCommandString();
        }));
        registerRightPanelWidget(this.dzSlider);

        this.speedSlider = rightBuilder.add(new DoubleParameterSlider(sliderX, 0, sliderW, 18, "Speed", 0.0D, 1.0D, this.speed, 2, value -> {
            this.speed = value;
            updateCommandString();
        }));
        registerRightPanelWidget(this.speedSlider);

        this.countSlider = rightBuilder.add(new IntParameterSlider(sliderX, 0, sliderW, 18, "Count", 1, 1000, this.count, value -> {
            this.count = value;
            updateCommandString();
        }));
        registerRightPanelWidget(this.countSlider);

        this.forceToggle = rightBuilder.add(CycleButton.builder((Boolean value) -> Component.literal(value ? "FORCE" : "NORMAL"))
            .withValues(false, true)
            .withInitialValue(this.force)
            .displayOnlyValue()
            .create(sliderX, 0, sliderW, 18, Component.literal("Modo"), (btn, value) -> {
                this.force = value;
                updateCommandString();
            }));
        registerRightPanelWidget(this.forceToggle);

        // Extra Args box and Color button (disabled and invisible by default).
        this.extraArgsBox = rightBuilder.add(new SelectableEditBox(this.font, 0, 0, sliderW, 18, Component.literal("Parâmetros Extras")));
        this.extraArgsBox.setMaxLength(200);
        this.extraArgsBox.setResponder(value -> updateCommandString());
        this.extraArgsBox.setEditable(false);
        this.extraArgsBox.active = false;
        this.extraArgsBox.visible = false;
        registerRightPanelWidget(this.extraArgsBox);

        this.colorButton = rightBuilder.add(Button.builder(Component.literal("Definir Cor..."), btn -> {
            String initialHex = "#ffffff";
            this.minecraft.setScreen(new HexColorPickerScreen(this, initialHex, (selectedHex) -> {
                try {
                    int color = Integer.parseInt(selectedHex.substring(1), 16);
                    float r = ((color >> 16) & 0xFF) / 255.0F;
                    float g = ((color >> 8) & 0xFF) / 255.0F;
                    float b = (color & 0xFF) / 255.0F;
                    if (this.extraArgsBox != null) {
                        this.extraArgsBox.setValue(String.format(Locale.ROOT, "%.2f %.2f %.2f 1.0", r, g, b));
                    }
                    this.updateCommandString();
                } catch (Exception ignored) {
                }
                this.minecraft.setScreen(this);
            }));
        }).build());
        this.colorButton.active = false;
        this.colorButton.visible = false;
        registerRightPanelWidget(this.colorButton);

        this.commandOutputBox = rightBuilder.add(new SelectableEditBox(this.font, 0, 0, sliderW, 18, Component.literal("Comando")));
        this.commandOutputBox.setEditable(false);
        this.commandOutputBox.setCanLoseFocus(true);
        registerRightPanelWidget(this.commandOutputBox);

        this.testButton = rightBuilder.add(Button.builder(Component.literal("Testar"), btn -> testCurrentCommand()).build());
        registerRightPanelWidget(this.testButton);

        this.copyButton = rightBuilder.add(Button.builder(Component.literal("Copiar"), btn -> copyCurrentCommand()).build());
        registerRightPanelWidget(this.copyButton);

        this.maxRightScroll = Math.max(0, rightBuilder.getCurrentY() - this.height + 10);
        this.rightScrollAmount = Mth.clamp(this.rightScrollAmount, 0.0D, this.maxRightScroll);
        applyRightScroll();

        this.addRenderableWidget(Button.builder(Component.literal("Fechar"), btn -> onClose())
            .bounds(this.rightX + this.rightW - 69, this.rightY + this.rightH - 24, 64, 18)
            .build());

        refreshSelectedSpriteSafe();
        updateCommandString();
        updateExtraArgsAvailability();
    }

    private void updateLayout() {
        /**
         * Calcula as posições (X, Y) e larguras (W, H) dos painéis left/center/right.
         * Regras principais:
         * - `desiredLeft` e `desiredRight` têm limites mínimos para garantir usabilidade.
         * - Se a soma dos painéis exceder a largura da tela, reduz-se left e/ou right.
         */
        int contentY = 20;
        int contentH = this.height - 28;

        int desiredLeft = Math.max(100, this.width / 4) + 24;
        int desiredRight = Math.max(130, this.width / 4);
        int minCenter = 120;
        int panelGap = 4;
        int total = desiredLeft + desiredRight + (panelGap * 2) + minCenter;

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
        this.centerX = this.leftW + panelGap;
        int centerRight = this.rightX - panelGap;
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
        // Reserve additional right margin for the scrollbar thumb inside the left panel
        this.listAreaW = this.leftW - 26;
        this.listAreaH = this.leftH - (this.listAreaY - this.leftY) - 8;
    }

    private void applySearchFilter() {
        /**
         * Aplica o texto de `searchBox` sobre `allParticles`, montando `filteredParticles`.
         * Reconstrói os botões (`rebuildParticleButtons`) e preserva seleção quando possível.
         */
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
        rebuildParticleButtons();
        if (nextSelection == null || this.filteredParticles.isEmpty()) {
            this.selectedParticleIndex = 0;
            setSelectedParticleId(null);
            return;
        }

        int index = this.filteredParticles.indexOf(nextSelection);
        setSelectedParticleIndex(index < 0 ? 0 : index);
    }

    private void rebuildParticleButtons() {
        /**
         * (Re)cria `ParticleButton` para cada entrada de `filteredParticles`.
         * Cada botão registra sua posição original (antes do scroll) em `buttonOriginalY`.
         * Ao final calcula `maxLeftScroll` e aplica o deslocamento atual (`applyLeftScroll`).
         */
        this.particleButtons.clear();
        this.buttonOriginalY.clear();
        this.leftScrollAmount = 0.0D;

        int buttonX = this.leftX + 8;
        // Make button width fit the reduced inner list area and leave a small right padding
        int buttonW = Math.max(10, this.listAreaW - 2);
        int buttonH = 20;
        int currentLeftY = this.listAreaY + 1;

        for (ResourceLocation id : this.filteredParticles) {
            if (id == null) {
                continue;
            }
            int index = this.particleButtons.size();
            ParticleButton button = new ParticleButton(buttonX, currentLeftY, buttonW, buttonH, id, btn -> setSelectedParticleIndex(index));
            this.particleButtons.add(button);
            this.buttonOriginalY.put(button, currentLeftY);
            currentLeftY += 22;
        }

        int listBottom = this.listAreaY + this.listAreaH - 10;
        this.maxLeftScroll = Math.max(0, currentLeftY - listBottom);
        this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private void setSelectedParticleIndex(int index) {
        if (this.filteredParticles.isEmpty()) {
            this.selectedParticleIndex = 0;
            setSelectedParticleId(null);
            return;
        }

        int clamped = Mth.clamp(index, 0, this.filteredParticles.size() - 1);
        this.selectedParticleIndex = clamped;
        setSelectedParticleId(this.filteredParticles.get(clamped));

        if (clamped >= 0 && clamped < this.particleButtons.size()) {
            ensureLeftButtonVisible(this.particleButtons.get(clamped));
        }
    }

    private void setSelectedParticleId(ResourceLocation id) {
        this.selectedParticleId = id;
        refreshSelectedSpriteSafe();
        updateCommandString();
        updateExtraArgsAvailability();
    }

    private void refreshSelectedSpriteSafe() {
        this.previewHardcoded = false;
        this.selectedPreviewSpriteId = null;
    }

    private void updateCommandString() {
        /**
         * Monta a string do comando `/particle` com base na partícula selecionada,
         * parâmetros (deltaX/deltaY/deltaZ/speed/count/force) e em `extraArgsBox`
         * quando ele estiver ativo. Atualiza `commandOutputBox` e habilita/desabilita
         * os botões de testar/copiar conforme apropriado.
         */
        if (this.selectedParticleId == null) {
            this.commandString = "";
        } else {
            String selectedId = this.selectedParticleId.toString();
            String extraArgs = "";
            if (this.extraArgsBox != null && this.extraArgsBox.active) {
                extraArgs = this.extraArgsBox.getValue().trim();
            }
            String fullParticleId = selectedId + (extraArgs.isEmpty() ? "" : " " + extraArgs);

            this.commandString = String.format(
                Locale.ROOT,
                "/particle %s ~ ~1 ~ %.2f %.2f %.2f %.2f %d %s",
                fullParticleId,
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

    private void updateExtraArgsAvailability() {
        /**
         * Decide se a partícula selecionada necessita de parâmetros extras.
         * Exemplos: dust (RGB + size), block/item (id), vibration (pos + ticks).
         * Ajusta `extraArgsBox` e `colorButton` (visível/ativo) e sugere texto.
         */
        if (this.extraArgsBox == null || this.colorButton == null) {
            return;
        }

        boolean requiresExtraArgs = false;
        if (this.selectedParticleId != null) {
            ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(this.selectedParticleId);
            requiresExtraArgs = type != null && !(type instanceof SimpleParticleType);
        }
        this.extraArgsBox.setEditable(requiresExtraArgs);
        this.extraArgsBox.active = requiresExtraArgs;
        this.extraArgsBox.visible = requiresExtraArgs;
        this.colorButton.active = requiresExtraArgs;
        this.colorButton.visible = requiresExtraArgs;

        if (!requiresExtraArgs) {
            this.extraArgsBox.setValue("");
            try {
                Method m = this.extraArgsBox.getClass().getMethod("setSuggestion", String.class);
                if (m != null) m.invoke(this.extraArgsBox, "");
            } catch (Exception ignored) {
            }
        } else {
            try {
                Method m = this.extraArgsBox.getClass().getMethod("setSuggestion", String.class);
                if (m != null) m.invoke(this.extraArgsBox, "Ex: 1.0 0.0 0.0 1.0");
            } catch (Exception ignored) {
            }
        }
        // Recalculate the layout to immediately reflect visibility changes
        recalculateRightPanel();
        updateCommandString();
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
        if (this.maxLeftScroll > 0 && isInsideLeftScrollbar(mouseX, mouseY)) {
            startLeftScrollbarDrag(mouseY);
            return true;
        }
        if (isInsideLeftList(mouseX, mouseY)) {
            for (ParticleButton particleButton : this.particleButtons) {
                if (particleButton.mouseClicked(mouseX, mouseY, button)) {
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
        if (mouseX < this.leftW && this.maxLeftScroll > 0) {
            this.leftScrollAmount -= scrollY * 15.0D;
            this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
            applyLeftScroll();
            return true;
        }
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

        if (!this.particleButtons.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
                int currentIndex = this.selectedParticleIndex;
                int newIndex = keyCode == GLFW.GLFW_KEY_UP
                    ? Math.max(0, currentIndex - 1)
                    : Math.min(this.particleButtons.size() - 1, currentIndex + 1);

                setSelectedParticleIndex(newIndex);
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

        renderLeftButtons(graphics, mouseX, mouseY, partialTick);
        renderPreviewPanel(graphics);

        boolean clipRightPanel = !this.rightPanelWidgets.isEmpty();
        if (clipRightPanel) {
            for (AbstractWidget widget : this.rightPanelWidgets) {
                widget.visible = false;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.particleButtons.isEmpty()) {
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

        renderLeftScrollbar(graphics);
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
            this.previewHardcoded = false;
            this.selectedPreviewSpriteId = null;
            return null;
        }

        try {
            if (this.minecraft.particleEngine == null) {
                this.previewHardcoded = true;
                return null;
            }

            Map<ResourceLocation, SpriteSet> spriteSets = ((ParticleEngineAccessor) this.minecraft.particleEngine).getSpriteSets();
            if (spriteSets == null) {
                this.previewHardcoded = true;
                return null;
            }

            SpriteSet spriteSet = spriteSets.get(this.selectedParticleId);
            if (spriteSet == null) {
                this.previewHardcoded = true;
                return null;
            }

            int maxFrames = 8;
            int currentFrame = (int) ((Util.getMillis() / 100L) % maxFrames);
            TextureAtlasSprite sprite = spriteSet.get(currentFrame, maxFrames);
            if (isMissingSprite(sprite)) {
                this.previewHardcoded = false;
                return null;
            }

            this.previewHardcoded = false;
            this.selectedPreviewSpriteId = this.selectedParticleId;
            return sprite;
        } catch (Exception ignored) {
            // Keep UI alive even if a specific particle has no atlas sprite mapping.
        }

        this.previewHardcoded = false;
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

    private void renderLeftButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.particleButtons.isEmpty()) {
            return;
        }
        graphics.enableScissor(this.leftX, this.listAreaY, this.leftX + this.leftW, this.listAreaY + this.listAreaH);
        for (ParticleButton particleButton : this.particleButtons) {
            particleButton.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
    }

    private void applyLeftScroll() {
        for (ParticleButton particleButton : this.particleButtons) {
            Integer baseY = this.buttonOriginalY.get(particleButton);
            if (baseY != null) {
                particleButton.setY(baseY - (int) this.leftScrollAmount);
            }
        }
    }

    private void ensureLeftButtonVisible(ParticleButton button) {
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

    private boolean isInsideLeftScrollbar(double mouseX, double mouseY) {
        // Position the scrollbar track inside the reserved right margin of the left panel
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

    private void applyRightScroll() {
        for (AbstractWidget widget : this.rightPanelWidgets) {
            Integer baseY = this.widgetOriginalY.get(widget);
            if (baseY != null) {
                widget.setY(baseY - (int) this.rightScrollAmount);
            }
        }
    }

    /**
     * Reposiciona verticalmente os widgets ativos da coluna direita.
     * Atualiza `widgetOriginalY` com as posições base (sem scroll) e recalcula
     * `maxRightScroll` conforme a altura total necessária. Em seguida aplica o
     * deslocamento atual (`rightScrollAmount`) chamando `applyRightScroll()`.
     */
    private void recalculateRightPanel() {
        int currentY = this.rightY + 28;
        int gap = 5;

        java.util.List<net.minecraft.client.gui.components.AbstractWidget> order = new java.util.ArrayList<>();
        if (this.dxSlider != null) order.add(this.dxSlider);
        if (this.dySlider != null) order.add(this.dySlider);
        if (this.dzSlider != null) order.add(this.dzSlider);
        if (this.speedSlider != null) order.add(this.speedSlider);
        if (this.countSlider != null) order.add(this.countSlider);
        if (this.forceToggle != null) order.add(this.forceToggle);
        if (this.extraArgsBox != null) order.add(this.extraArgsBox);
        if (this.colorButton != null) order.add(this.colorButton);
        if (this.commandOutputBox != null) order.add(this.commandOutputBox);
        if (this.testButton != null) order.add(this.testButton);
        if (this.copyButton != null) order.add(this.copyButton);

        for (var widget : order) {
            if (widget != null && widget.visible) {
                widget.setX(this.rightX + 5);
                try { widget.setWidth(this.rightW - 10); } catch (Exception ignored) {}
                widget.setY(currentY);
                this.widgetOriginalY.put(widget, currentY);
                currentY += widget.getHeight() + gap;
            }
        }

        this.maxRightScroll = Math.max(0, currentY - this.height + 10);
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
        /**
         * Desenha o track e o thumb (polegar) do scrollbar da coluna direita.
         * O thumb só é desenhado se `maxRightScroll > 0` (conteúdo maior que a área).
         */
        if (this.maxRightScroll <= 0) {
            return;
        }
        // Draw track slightly inset from the absolute right edge
        graphics.fill(this.width - 8, 0, this.width - 2, this.height, 0xFF000000);

        int thumbHeight = Math.max(20, (int) ((this.height / (float) (this.height + this.maxRightScroll)) * this.height));
        int thumbY = (int) ((this.rightScrollAmount / Math.max(1, this.maxRightScroll)) * (this.height - thumbHeight));
        graphics.fill(this.width - 7, thumbY, this.width - 3, thumbY + thumbHeight, 0xFF888888);
    }

    private void renderLeftScrollbar(GuiGraphics graphics) {
        /**
         * Desenha o scrollbar da coluna esquerda (lista de partículas).
         * O track ocupa `barX .. barX+6` e o thumb é desenhado com altura proporcional.
         */
        if (this.maxLeftScroll <= 0) {
            return;
        }
        // Compute track inside reserved right margin next to the list area
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

    private static float[] tint(int rgb) {
        return new float[] {
            ((rgb >> 16) & 0xFF) / 255.0F,
            ((rgb >> 8) & 0xFF) / 255.0F,
            (rgb & 0xFF) / 255.0F
        };
    }

    private final class ParticleButton extends Button {
        private final ResourceLocation particleId;

        private ParticleButton(int x, int y, int width, int height, ResourceLocation particleId, OnPress onPress) {
            super(x, y, width, height, Component.literal(particleId.toString()), onPress, DEFAULT_NARRATION);
            this.particleId = particleId;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = this.particleId.equals(ParticleStudioScreen.this.selectedParticleId);
            boolean hovered = this.isHoveredOrFocused();
            int rowColor = selected ? 0xAA2B4E72 : (hovered ? 0xAA263445 : 0x55202A36);
            int left = this.getX();
            int top = this.getY();
            int width = this.getWidth();
            int height = this.getHeight();
            graphics.fill(left, top, left + width, top + height - 1, rowColor);

            String namespace = this.particleId.getNamespace();
            String path = this.particleId.getPath();
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
