package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.AbstractEditorScreen;
import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.UpdateServerConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("all")
public class AdminPanelScreen extends AbstractEditorScreen {
    private final CompoundTag configData;
    private static final int OUTER_MARGIN = 8;
    private static final int INNER_MARGIN = 8;
    private static final int HEADER_HEIGHT = 20;
    private static final int BAR_HEIGHT = 16;
    private static final int FOOTER_HEIGHT = 24;
    private static final int FIXED_SIDEBAR_WIDTH = 150;
    private static final int CATEGORY_BUTTON_HEIGHT = 20;
    private static final int CATEGORY_BUTTON_GAP = 4;
    private static final int ROW_HEIGHT = 32;
    private static final int RESET_BUTTON_WIDTH = 24;
    private static final int RESET_BUTTON_GAP = 6;
    private static final int FIELD_LABEL_MARGIN = 10;
    private static final int FORM_RIGHT_MARGIN = 12;
    private static final int TOGGLE_WIDTH = 60;
    private static final int FOOTER_BUTTON_WIDTH = 90;
    private static final int FOOTER_BUTTON_GAP = 20;
    private static final int SCROLLBAR_WIDTH = 6;
    private final List<RowInfo> rowInfos = new ArrayList<>();
    private final Map<String, FieldType> fieldTypes = new LinkedHashMap<>();
    private final Map<String, Object> baselineValues = new LinkedHashMap<>();
    private final Map<String, Object> pendingValues = new LinkedHashMap<>();
    private final List<String> activeCategoryKeys = new ArrayList<>();
    private int frameX, frameY, frameW, frameH, headerY, barY, sidebarX, sidebarY, sidebarW, sidebarH;
    private int contentX, contentY, contentW, contentH, footerY;
    private Category currentCategory = Category.MAIN;

    // Scroll
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int contentStartY = 0;
    private boolean isDraggingScrollbar = false;

    // Tooltips - mapeia widget -> tooltip
    private final Map<Object, List<String>> widgetTooltips = new java.util.IdentityHashMap<>();
    // Y original de cada widget (para scroll)
    private final Map<Object, Integer> widgetOriginalY = new java.util.IdentityHashMap<>();
    private List<String> currentHoverTooltip = null;

    public enum Category {
        MAIN("Painel Administrativo"),
        SERVER("Configurações do Servidor"),
        THROTTLE("Otimizações & Perf"),
        FIXES("Fixes & Balanço"),
        DEBUG("Debug & Status");

        public final String title;
        Category(String title) { this.title = title; }
    }

    public AdminPanelScreen(CompoundTag configData) {
        super(Component.literal("ServerFixes Admin Hub"));
        this.configData = configData;
        initTooltips();
    }

    private void initTooltips() {
        tooltips.clear();
        tooltips.put("throttleFarmAndCharm", List.of("§6Throttle Farm & Charm", "§7Reduz ticking dos blocos do mod Farm & Charm."));
        tooltips.put("cookingPotTickRate", List.of("§6Rate Cooking Pot", "§7Frequência que o Cooking Pot processa comida (segundos)."));
        tooltips.put("roasterTickRate", List.of("§6Rate Roaster", "§7Frequência que o Roaster processa comida (segundos)."));
        tooltips.put("throttleVinery", List.of("§6Throttle Vinery", "§7Reduz ticking do Barril de Fermentação do mod Vinery."));
        tooltips.put("fermentationBarrelTickRate", List.of("§6Rate Fermentation Barrel", "§7Frequência que o Barril fermenta (segundos)."));
        tooltips.put("enableEntityCulling", List.of("§6Entity Culling", "§7Entities param de tickar quando players estão longe."));
        tooltips.put("entityCullingDistance", List.of("§6Distância Culling", "§7Distância (blocos) para entities pararem de tickar."));
        tooltips.put("cullSuperGlue", List.of("§6Cull SuperGlue", "§7Pula tick de Super Glues do Create sem players perto."));
        tooltips.put("cullBotania", List.of("§6Cull Botania", "§7Pula tick de Mana Bursts e Portals sem players perto."));
        tooltips.put("throttleIdleContraptions", List.of("§6Throttle Contraptions AFK", "§7Reduz ticking de contraptions paradas sem players."));
        tooltips.put("contraptionIdleThrottleRate", List.of("§6Rate Throttle AFK", "§7Frequência que contraptions AFK são verificadas (segundos)."));
        tooltips.put("enableAntiSwap", List.of("§6Anti-Swap Exploit", "§7Previne troca de arma no mesmo tick para dano indevido."));
        tooltips.put("enableInfiniteTrades", List.of("§6Trocas Infinitas", "§7Players com tag resetam usos de villagers."));
        tooltips.put("infiniteTradeTag", List.of("§6Tag p/ Trocas Infinitas", "§7Tag NBT necessária no player."));
        tooltips.put("fixBackstabbingExploit", List.of("§6Fix Backstabbing", "§7Corrige bug de magias abusando Backstabbing."));
        tooltips.put("nerfTurtleMaster", List.of("§6Nerf Turtle Master", "§7Limita níveis de Resistance e Slowness."));
        tooltips.put("turtleMasterResistance", List.of("§6Res. Turtle (Padrão)", "§7Nível máximo de Resistance da poção normal."));
        tooltips.put("strongTurtleMasterResistance", List.of("§6Res. Turtle (Forte)", "§7Nível máximo de Resistance da poção forte."));
        tooltips.put("debugDamageReceived", List.of("§6Debug Dano Recebido", "§7Loga dano recebido no chat."));
        tooltips.put("debugDamageDealt", List.of("§6Debug Dano Causado", "§7Loga dano causado no chat."));
        tooltips.put("debugDamageBreakdown", List.of("§6Detalhamento de Dano", "§7Mostra reduções de armadura/proteção."));
        tooltips.put("debugVillagers", List.of("§6Debug Villagers", "§7Loga interações com villagers."));
        tooltips.put("debugAntiSwap", List.of("§6Debug Anti-Swap", "§7Loga ataques cancelados pelo Anti-Swap."));
    }

    private final Map<String, List<String>> tooltips = new LinkedHashMap<>();

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null) return;
        this.clearWidgets();
        this.rowInfos.clear();
        this.widgetTooltips.clear();
        this.widgetOriginalY.clear();
        computeLayout();
        buildSidebar();

        if (currentCategory == Category.MAIN) {
            initMainPage(this.contentX, this.contentY, this.contentW, this.contentH);
        } else {
            initCategoryPage(this.contentX, this.contentW, this.contentY + 16);
        }

        int totalButtonsW = FOOTER_BUTTON_WIDTH * 2 + FOOTER_BUTTON_GAP;
        int leftX = this.frameX + (this.frameW - totalButtonsW) / 2;
        int rightX = leftX + FOOTER_BUTTON_WIDTH + FOOTER_BUTTON_GAP;

        this.addRenderableWidget(Button.builder(Component.literal(currentCategory == Category.MAIN ? "§cFechar" : "§eVoltar"), (btn) -> {
            if (this.currentCategory == Category.MAIN) {
                Minecraft client = this.minecraft;
                if (client != null) client.setScreen(null);
            } else {
                this.currentCategory = Category.MAIN;
                this.init();
            }
        }).bounds(leftX, this.footerY, FOOTER_BUTTON_WIDTH, 20).build());

        PulsingButton saveBtn = new PulsingButton(rightX, this.footerY, FOOTER_BUTTON_WIDTH, 20, Component.literal("§bSalvar"), (btn) -> {
            if (!hasPendingChanges()) { ((PulsingButton)btn).pulseError(800); return; }
            savePendingChanges();
        });
        saveBtn.active = this.currentCategory != Category.MAIN && hasPendingChanges();
        this.addRenderableWidget(saveBtn);
    }

    private void computeLayout() {
        this.frameX = OUTER_MARGIN;
        this.frameY = OUTER_MARGIN;
        this.frameW = Math.max(240, this.width - OUTER_MARGIN * 2);
        this.frameH = Math.max(170, this.height - OUTER_MARGIN * 2);
        this.headerY = this.frameY + 6;
        this.barY = this.headerY + HEADER_HEIGHT + 4;
        this.footerY = this.frameY + this.frameH - FOOTER_HEIGHT;

        this.sidebarX = this.frameX + INNER_MARGIN;
        this.sidebarY = this.barY + BAR_HEIGHT + 6;
        this.sidebarH = Math.max(90, this.footerY - this.sidebarY - 8);

        int maxSidebarAllowed = Math.max(120, this.frameW - 260);
        this.sidebarW = Math.min(FIXED_SIDEBAR_WIDTH, maxSidebarAllowed);

        this.contentX = this.sidebarX + this.sidebarW + INNER_MARGIN;
        this.contentY = this.sidebarY;
        this.contentW = Math.max(140, this.frameX + this.frameW - INNER_MARGIN - this.contentX);
        this.contentH = Math.max(90, this.footerY - this.contentY - 8);
    }

    private void buildSidebar() {
        int y = this.sidebarY + 4;
        int buttonX = this.sidebarX + 4;
        int buttonW = Math.max(80, this.sidebarW - 8);
        for (Category category : Category.values()) {
            final Category cat = category;
            boolean active = this.currentCategory == cat;
            Component label = Component.literal((active ? "§6> " : "") + cat.title);
            this.addRenderableWidget(Button.builder(label, (btn) -> {
                this.currentCategory = cat;
                this.scrollOffset = 0; // Reset scroll ao trocar de categoria
                this.init();
            }).bounds(buttonX, y, buttonW, CATEGORY_BUTTON_HEIGHT).build());
            y += CATEGORY_BUTTON_HEIGHT + CATEGORY_BUTTON_GAP;
        }
    }

    private void initMainPage(int contentStartX, int contentStartY, int contentWidth, int contentHeight) {
        // Home usa apenas o painel de conteúdo único para evitar redundância visual.
    }

    private void initCategoryPage(int contentStartX, int contentWidth, int startY) {
        this.activeCategoryKeys.clear();
        int y = startY;
        this.contentStartY = startY;
        int totalContentHeight = 0;

        int labelX = contentStartX + FIELD_LABEL_MARGIN;
        int rowRight = contentStartX + contentWidth - FORM_RIGHT_MARGIN;
        int resetX = rowRight - RESET_BUTTON_WIDTH;
        int toggleX = resetX - RESET_BUTTON_GAP - TOGGLE_WIDTH;
        int inputRight = resetX - RESET_BUTTON_GAP;
        int minInputX = labelX + 80;
        int availableForInput = Math.max(60, inputRight - minInputX);
        int inputWidth = Math.min(160, availableForInput);
        int inputX = inputRight - inputWidth;

        switch (currentCategory) {
            case SERVER:
                addRow("enableAntiSwap", "Anti-Swap Exploit", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("enableInfiniteTrades", "Trocas Infinitas", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("infiniteTradeTag", "Tag p/ Trocas Infinitas", FieldType.STRING, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                totalContentHeight = y - startY;
                this.maxScroll = Math.max(0, totalContentHeight - this.contentH);
                this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                applyScroll();
                break;
            case THROTTLE:
                // Farm & Charm
                addSectionLabel("§6§lFarm & Charm", y); y += 24;
                addRow("throttleFarmAndCharm", "Throttle Farm&Charm", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("cookingPotTickRate", "Rate Cooking Pot", FieldType.SECONDS_TO_TICKS, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("roasterTickRate", "Rate Roaster", FieldType.SECONDS_TO_TICKS, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT + 4;

                // Vinery
                addSectionLabel("§6§lVinery", y); y += 24;
                addRow("throttleVinery", "Throttle Vinery", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("fermentationBarrelTickRate", "Rate Barrel", FieldType.SECONDS_TO_TICKS, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT + 4;

                // Entity Culling
                addSectionLabel("§6§lEntity Culling", y); y += 24;
                addRow("enableEntityCulling", "Entity Culling", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("entityCullingDistance", "Distância Culling (Blocos)", FieldType.INTEGER, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("cullSuperGlue", "Cull SuperGlue", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("cullBotania", "Cull Botania", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT + 4;

                // Create Contraptions
                addSectionLabel("§6§lCreate - Contraptions", y); y += 24;
                addRow("throttleIdleContraptions", "Throttle Contraptions AFK", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("contraptionIdleThrottleRate", "Rate Throttle AFK", FieldType.SECONDS_TO_TICKS, y, toggleX, resetX, inputX, inputWidth);
                totalContentHeight = y - startY + ROW_HEIGHT;

                this.maxScroll = Math.max(0, totalContentHeight - this.contentH);
                this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

                // Aplica scroll a todos os widgets
                applyScroll();
                break;
            case FIXES:
                addRow("fixBackstabbingExploit", "Fix Backstabbing", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("nerfTurtleMaster", "Nerf Turtle Master", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("turtleMasterResistance", "Res. Turtle (Padrão)", FieldType.INTEGER, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("strongTurtleMasterResistance", "Res. Turtle (Forte)", FieldType.INTEGER, y, toggleX, resetX, inputX, inputWidth);
                totalContentHeight = y - startY;
                this.maxScroll = Math.max(0, totalContentHeight - this.contentH);
                this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                applyScroll();
                break;
            case DEBUG:
                addRow("debugDamageReceived", "Debug Dano Recebido", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("debugDamageDealt", "Debug Dano Causado", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("debugDamageBreakdown", "Detalhamento de Dano", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("debugVillagers", "Debug Villagers", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("debugAntiSwap", "Debug Anti-Swap", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth);
                totalContentHeight = y - startY;
                this.maxScroll = Math.max(0, totalContentHeight - this.contentH);
                this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                applyScroll();
                break;
            default:
                this.maxScroll = 0;
                this.scrollOffset = 0;
                break;
        }
    }

    private void addSectionLabel(String label, int y) {
        registerRow(label, y);
    }

    /** Adiciona uma linha completa: label + widget (toggle ou input) + reset */
    private void addRow(String key, String label, FieldType type, int y, int toggleX, int resetX, int inputX, int inputWidth) {
        registerRow(label, y);
        activeCategoryKeys.add(key);
        fieldTypes.put(key, type);

        if (type == FieldType.BOOLEAN) {
            boolean baseline = getBaselineBoolean(key);
            boolean pending = getPendingBoolean(key, baseline);
            CycleButton<Boolean> cycleBtn = CycleButton.builder((Boolean value) -> value ? Component.literal("ON") : Component.literal("OFF"))
                .withValues(true, false)
                .withInitialValue(pending)
                .displayOnlyValue()
                .create(toggleX, y, TOGGLE_WIDTH, 20, Component.empty(), (btn, val) -> pendingValues.put(key, val));
            this.addRenderableWidget(cycleBtn);
            widgetOriginalY.put(cycleBtn, y);
            widgetTooltips.put(cycleBtn, tooltips.get(key));
        } else {
            String baseline = getBaselineString(key, type);
            String pending = getPendingString(key, baseline);
            EditBox box = new SelectableEditBox(this.font, inputX, y, inputWidth, 20, Component.literal(label));
            if (type == FieldType.INTEGER || type == FieldType.LONG) box.setFilter(AdminPanelScreen::isValidIntegerInput);
            else if (type == FieldType.SECONDS_TO_TICKS) box.setFilter(AdminPanelScreen::isValidSecondsInput);
            box.setValue(pending);
            box.setResponder(newValue -> pendingValues.put(key, newValue));
            this.addRenderableWidget(box);
            widgetOriginalY.put(box, y);
            widgetTooltips.put(box, tooltips.get(key));
        }

        Button resetBtn = Button.builder(Component.literal("↺"), (btn) -> {
            Object baseline = baselineValues.get(key);
            if (baseline != null) pendingValues.put(key, baseline);
            this.init();
        }).bounds(resetX, y, RESET_BUTTON_WIDTH, 20).build();
        this.addRenderableWidget(resetBtn);
        widgetOriginalY.put(resetBtn, y);
        widgetTooltips.put(resetBtn, tooltips.get(key));
    }

    private void applyScroll() {
        for (var widget : this.children()) {
            if (widget instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                Integer origY = widgetOriginalY.get(w);
                if (origY != null && origY >= this.contentStartY) {
                    w.setY(origY - scrollOffset);
                }
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        renderEditorFrame(graphics, Component.literal("§6§l" + currentCategory.title),
            this.frameX, this.frameY, this.frameW, this.frameH,
            this.headerY, this.barY, this.contentY, this.footerY);

        // Painéis de fundo (UMA VEZ SÓ)
        GuiLayoutUtils.drawPanel(graphics, this.sidebarX, this.sidebarY, this.sidebarW, this.sidebarH, 0xBF071014, 0xFF3D5564);
        GuiLayoutUtils.drawPanel(graphics, this.contentX, this.contentY, this.contentW, this.contentH, 0xD9182432, 0xFF4E6B8D);

        boolean hasScroll = maxScroll > 0;

        // Renderiza labels (clipping manual)
        if (currentCategory == Category.MAIN) {
            renderMainOverview(graphics);
        } else if (!rowInfos.isEmpty()) {
            int x = this.contentX + 8;
            int rowWidth = this.contentW - 16 - SCROLLBAR_WIDTH;
            int evenOdd = 0;

            for (int i = 0; i < rowInfos.size(); i++) {
                RowInfo row = rowInfos.get(i);
                int drawY = row.y;
                if (hasScroll) {
                    drawY = row.y - scrollOffset;
                    if (drawY < this.contentY - ROW_HEIGHT || drawY > this.contentY + this.contentH + ROW_HEIGHT) {
                        continue;
                    }
                }

                boolean isSection = row.label.startsWith("§6§l");
                if (!isSection) {
                    if (evenOdd % 2 == 0) {
                        graphics.fill(x, drawY - 2, x + rowWidth, drawY + ROW_HEIGHT - 4, 0x18FFFFFF);
                    }
                    evenOdd++;
                    graphics.drawString(this.font, row.label, x + 5, drawY + 4, 0xE3E6EE);
                } else {
                    String plainLabel = row.label.replace("§6§l", "").replace("§r", "");
                    graphics.drawString(this.font, plainLabel, x + 5, drawY + 4, 0xFFFFD700);
                    graphics.fill(x, drawY + 18, x + rowWidth, drawY + 19, 0x44FFD700);
                }
            }
        } else {
            graphics.drawCenteredString(this.font, "§7Selecione uma categoria à esquerda para configurar.", this.contentX + this.contentW / 2, this.contentY + this.contentH / 2, 0xAAAAAA);
        }

        // Aplica scissor em toda a área do frame (inclui sidebar + conteúdo + footer)
        // Isso corta widgets que vazaram para fora do frame, mas mantém sidebar/footer visíveis
        if (hasScroll) {
            graphics.enableScissor(this.frameX, this.sidebarY, this.frameX + this.frameW, this.footerY + FOOTER_HEIGHT);
        }

        // Renderiza TODOS os widgets (com scissor ativo se necessário)
        super.render(graphics, mouseX, mouseY, partialTicks);

        // Remove scissor
        if (hasScroll) {
            graphics.disableScissor();

            // Desenha scrollbar por cima
            int scrollbarX = this.contentX + this.contentW - SCROLLBAR_WIDTH;
            int scrollTrackHeight = this.contentH;
            float scrollRatio = (float) scrollOffset / maxScroll;
            int scrollbarHeight = Math.max(20, (int) (scrollTrackHeight * ((float) (this.contentH) / (this.contentH + maxScroll))));
            int scrollbarY = this.contentY + (int) (scrollRatio * (scrollTrackHeight - scrollbarHeight));

            graphics.fill(scrollbarX, this.contentY, scrollbarX + SCROLLBAR_WIDTH, this.contentY + this.contentH, 0x33000000);
            graphics.fill(scrollbarX + 1, scrollbarY, scrollbarX + SCROLLBAR_WIDTH - 1, scrollbarY + scrollbarHeight, 0xAAFFFFFF);
        }

        // Detecta tooltip (só widgets na área de conteúdo)
        currentHoverTooltip = null;
        for (var child : this.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                Integer origY = widgetOriginalY.get(w);
                if (origY != null && origY >= this.contentStartY) {
                    if (hasScroll) {
                        int widgetScreenY = w.getY();
                        if (widgetScreenY < this.contentY - 2 || widgetScreenY + w.getHeight() > this.contentY + this.contentH + 2) {
                            continue;
                        }
                    }
                    if (w.isMouseOver(mouseX, mouseY)) {
                        currentHoverTooltip = widgetTooltips.get(w);
                        if (currentHoverTooltip != null) break;
                    }
                }
            }
        }

        // Renderiza tooltip POR ÚLTIMO (apenas o tooltip, sem limpar fundo)
        if (currentHoverTooltip != null && !currentHoverTooltip.isEmpty()) {
            renderTooltip(graphics, currentHoverTooltip, mouseX, mouseY);
        }
    }

    private void renderTooltip(GuiGraphics graphics, List<String> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) return;
        int maxWidth = 0;
        for (String line : lines) {
            String plain = line.replaceAll("§[0-9a-fk-or]", "");
            int w = this.font.width(plain);
            if (w > maxWidth) maxWidth = w;
        }
        int paddingX = 8, paddingY = 6;
        int lineHeight = this.font.lineHeight + 2;
        int tooltipWidth = maxWidth + paddingX * 2;
        int tooltipHeight = lines.size() * lineHeight + paddingY * 2;

        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - tooltipHeight - 8;
        if (tooltipY < this.contentY) tooltipY = mouseY + 12;
        if (tooltipX + tooltipWidth > this.width) tooltipX = mouseX - tooltipWidth - 12;
        if (tooltipX < 0) tooltipX = 4;
        if (tooltipY + tooltipHeight > this.height) tooltipY = this.height - tooltipHeight - 4;

        graphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, 0xEE000000);
        graphics.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xDD1A1A2E);
        graphics.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth, tooltipY, 0x665588FF);
        graphics.fill(tooltipX - 1, tooltipY, tooltipX, tooltipY + tooltipHeight, 0x665588FF);
        graphics.fill(tooltipX + tooltipWidth, tooltipY, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight, 0x665588FF);
        graphics.fill(tooltipX - 1, tooltipY + tooltipHeight, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, 0x665588FF);

        int textY = tooltipY + paddingY;
        for (String line : lines) {
            graphics.drawString(this.font, line, tooltipX + paddingX, textY, 0xFFFFFFFF);
            textY += lineHeight;
        }
    }

    private void renderMainOverview(GuiGraphics graphics) {
        int areaX = this.contentX + 10;
        int areaY = this.contentY + 12;
        int areaW = Math.max(120, this.contentW - 20);
        int areaH = Math.max(80, this.contentH - 22);
        GuiLayoutUtils.drawPanel(graphics, areaX, areaY, areaW, areaH, 0xCC1C2532, 0xFF4E6B8D);

        int textX = areaX + 12;
        int y = areaY + 12;
        graphics.drawString(this.font, "§bVisão Geral", textX, y, 0xFFE5F6FF);
        y += 22;
        graphics.drawString(this.font, "§7Use a coluna da esquerda para navegar entre categorias.", textX, y, 0xFFBECBDD);
        y += 16;
        graphics.drawString(this.font, "§7Painel administrativo separado do editor de itens.", textX, y, 0xFFBECBDD);
        y += 16;
        graphics.drawString(this.font, "§7A tela segue o formato do IBE: sidebar + area unica de conteudo.", textX, y, 0xFFBECBDD);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (maxScroll > 0) { // Genérico: qualquer categoria com scroll
            int scrollAmount = (int) (scrollY * 15);
            int oldScroll = scrollOffset;
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollAmount));
            if (oldScroll != scrollOffset) applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (maxScroll > 0 && button == 0) { // Genérico
            int scrollbarX = this.contentX + this.contentW - SCROLLBAR_WIDTH;
            int scrollTrackHeight = this.contentH;
            float scrollRatio = maxScroll > 0 ? (float) scrollOffset / maxScroll : 0;
            int scrollbarHeight = Math.max(20, (int) (scrollTrackHeight * ((float) (this.contentH) / (this.contentH + maxScroll))));
            int scrollbarY = this.contentY + (int) (scrollRatio * (scrollTrackHeight - scrollbarHeight));

            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH
                    && mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight) {
                isDraggingScrollbar = true;
                return true;
            }
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH
                    && mouseY >= this.contentY && mouseY <= this.contentY + this.contentH) {
                scrollOffset = Math.max(0, Math.min(maxScroll, (int) ((mouseY - this.contentY) / (float) scrollTrackHeight * maxScroll)));
                applyScroll();
                return true;
            }
        }
        isDraggingScrollbar = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar && maxScroll > 0) {
            int scrollTrackHeight = this.contentH;
            int scrollbarHeight = Math.max(20, (int) (scrollTrackHeight * ((float) (this.contentH) / (this.contentH + maxScroll))));
            int oldScroll = scrollOffset;
            scrollOffset = (int) ((mouseY - this.contentY - scrollbarHeight / 2.0f) / (scrollTrackHeight - scrollbarHeight) * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            applyScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void registerRow(String label, int y) { this.rowInfos.add(new RowInfo(label, y)); }

    private boolean hasPendingChanges() {
        for (String key : activeCategoryKeys) {
            FieldType type = fieldTypes.get(key);
            if (type == null) continue;
            if (!Objects.equals(baselineValues.get(key), pendingValues.get(key))) return true;
        }
        return false;
    }

    private void savePendingChanges() {
        if (!hasPendingChanges()) return;
        for (String key : activeCategoryKeys) {
            FieldType type = fieldTypes.get(key);
            if (type == null) continue;
            if (!Objects.equals(baselineValues.get(key), pendingValues.get(key))) {
                sendUpdate(key, pendingValues.get(key), type);
                baselineValues.put(key, pendingValues.get(key));
            }
        }
        this.init();
    }

    private void sendUpdate(String key, Object pending, FieldType type) {
        String formatted = type.format(pending);
        NetworkHandler.sendToServer(new UpdateServerConfigPacket(key, formatted, type.packetName()));
        if (configData != null) {
            if (type == FieldType.BOOLEAN) configData.putBoolean(key, pending instanceof Boolean && (Boolean) pending);
            else if (type == FieldType.STRING) configData.putString(key, formatted);
            else if (type == FieldType.LONG) configData.putLong(key, parseLongSafe(formatted, 0L));
            else configData.putInt(key, parseIntSafe(formatted, 0));
        }
    }

    private boolean getBaselineBoolean(String key) {
        Object stored = baselineValues.get(key);
        if (stored instanceof Boolean) return (Boolean) stored;
        boolean value = configData != null ? configData.getBoolean(key) : false;
        baselineValues.put(key, value);
        return value;
    }

    private boolean getPendingBoolean(String key, boolean fallback) {
        Object stored = pendingValues.get(key);
        if (stored instanceof Boolean) return (Boolean) stored;
        pendingValues.put(key, fallback);
        return fallback;
    }

    private String getBaselineString(String key, FieldType type) {
        Object stored = baselineValues.get(key);
        if (stored instanceof String) return (String) stored;
        String value = type.readInputValue(configData, key);
        baselineValues.put(key, value);
        return value;
    }

    private String getPendingString(String key, String fallback) {
        Object stored = pendingValues.get(key);
        if (stored instanceof String) return (String) stored;
        String safe = fallback != null ? fallback : "";
        pendingValues.put(key, safe);
        return safe;
    }

    private static String stripWrappingQuotes(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\""))
            return trimmed.substring(1, trimmed.length() - 1);
        return trimmed;
    }

    private static int parseIntSafe(String value, int fallback) {
        try { return Integer.parseInt(stripWrappingQuotes(value)); } catch (Exception ignored) { return fallback; }
    }

    private static long parseLongSafe(String value, long fallback) {
        try { return Long.parseLong(stripWrappingQuotes(value)); } catch (Exception ignored) { return fallback; }
    }

    private static double parseDoubleSafe(String value, double fallback) {
        try { return Double.parseDouble(stripWrappingQuotes(value).replace(',', '.')); } catch (Exception ignored) { return fallback; }
    }

    private static boolean isValidIntegerInput(String value) {
        if (value == null || value.isEmpty() || "-".equals(value)) return true;
        try { Long.parseLong(value); return true; } catch (Exception ignored) { return false; }
    }

    private static boolean isValidSecondsInput(String value) {
        if (value == null || value.isEmpty()) return true;
        String normalized = value.replace(',', '.');
        if (".".equals(normalized)) return true;
        try { double seconds = Double.parseDouble(normalized); return seconds >= 0.05D; } catch (Exception ignored) { return false; }
    }

    private static int secondsToTicks(String value) {
        double seconds = parseDoubleSafe(value, 1.0D);
        if (seconds < 0.05D) seconds = 0.05D;
        return Math.max(1, (int) Math.round(seconds * 20.0D));
    }

    private static String ticksToSecondsInput(int ticks) {
        double seconds = Math.max(1, ticks) / 20.0D;
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0000001D) return String.valueOf((int) Math.rint(seconds));
        String value = String.format(java.util.Locale.US, "%.2f", seconds);
        while (value.endsWith("0")) value = value.substring(0, value.length() - 1);
        if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static final class RowInfo {
        private final String label;
        private final int y;
        private RowInfo(String label, int y) { this.label = label; this.y = y; }
    }

    private enum FieldType {
        BOOLEAN("bool") {
            @Override String format(Object value) { return String.valueOf(value instanceof Boolean ? value : false); }
            @Override String readInputValue(CompoundTag source, String key) { return String.valueOf(source != null && source.getBoolean(key)); }
        },
        STRING("string") {
            @Override String format(Object value) { return stripWrappingQuotes(value != null ? value.toString() : ""); }
            @Override String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "";
                if (source.contains(key, Tag.TAG_STRING)) return stripWrappingQuotes(source.getString(key));
                Tag tag = source.get(key);
                return tag != null ? stripWrappingQuotes(tag.getAsString()) : "";
            }
        },
        INTEGER("int") {
            @Override String format(Object value) { return String.valueOf(parseIntSafe(value != null ? value.toString() : "", 0)); }
            @Override String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "0";
                return String.valueOf(source.getInt(key));
            }
        },
        LONG("long") {
            @Override String format(Object value) { return String.valueOf(parseLongSafe(value != null ? value.toString() : "", 0L)); }
            @Override String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "0";
                return String.valueOf(source.getLong(key));
            }
        },
        SECONDS_TO_TICKS("int") {
            @Override String format(Object value) { return String.valueOf(secondsToTicks(value != null ? value.toString() : "")); }
            @Override String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "1";
                int ticks = source.contains(key, Tag.TAG_INT) ? source.getInt(key) : 20;
                return ticksToSecondsInput(ticks);
            }
        };

        private final String packetName;
        FieldType(String packetName) { this.packetName = packetName; }
        abstract String format(Object value);
        String readInputValue(CompoundTag source, String key) { if (source == null || !source.contains(key)) return ""; Tag tag = source.get(key); return tag != null ? stripWrappingQuotes(tag.getAsString()) : ""; }
        public String packetName() { return packetName; }
    }
}
