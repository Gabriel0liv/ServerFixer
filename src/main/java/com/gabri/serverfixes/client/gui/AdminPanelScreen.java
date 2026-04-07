package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.UpdateServerConfigPacket;
import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@SuppressWarnings("all")
public class AdminPanelScreen extends Screen {
    private final CompoundTag configData;
    
    // Layout Constants
    private static final int OUTER_MARGIN = 8;
    private static final int INNER_MARGIN = 8;
    private static final int CATEGORY_BUTTON_HEIGHT = 20;
    private static final int CATEGORY_BUTTON_GAP = 4;
    private static final int FOOTER_BUTTON_WIDTH = 100;
    private static final int FOOTER_BUTTON_GAP = 10;
    private static final int TOGGLE_WIDTH = 50;
    private static final int RESET_BUTTON_WIDTH = 20;
    private static final int RESET_BUTTON_GAP = 4;
    private static final int ROW_HEIGHT = 32;
    private static final int FORM_RIGHT_MARGIN = 16;
    private static final int FIELD_LABEL_MARGIN = 10;
    private static final int BAR_HEIGHT = 26;
    private static final int SCROLLBAR_WIDTH = 8;

    // Dimensions
    private int frameX, frameY, frameW, frameH, headerY, barY, sidebarX, sidebarY, sidebarW, sidebarH;
    private int contentX, contentY, contentW, contentH;
    private int footerY;
    private Category currentCategory = Category.MAIN;

    // Scroll State
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int contentStartY = 0;
    private boolean isDraggingScrollbar = false;

    // Widgets tracking
    private final List<RowInfo> rowInfos = new ArrayList<>();
    private final List<String> activeCategoryKeys = new ArrayList<>();
    private final Map<String, FieldType> fieldTypes = new LinkedHashMap<>();
    private final Map<Object, List<String>> widgetTooltips = new java.util.IdentityHashMap<>();
    private final Map<Object, Integer> widgetOriginalY = new java.util.IdentityHashMap<>();
    private final Map<String, Object> baselineValues = new LinkedHashMap<>();
    private final Map<String, Object> pendingValues = new LinkedHashMap<>();
    
    // Sidebar Buttons
    private final List<Button> sidebarButtons = new ArrayList<>();
    private List<String> currentHoverTooltip = null;

    public enum Category {
        MAIN("Painel Administrativo"),
        SERVER("Configurações do Servidor"),
        THROTTLE("Otimizações & Perf"),
        FIXES("Correções & Tweaks"),
        DEBUG("Depuração");

        final String title;
        Category(String title) { this.title = title; }
    }

    private static class RowInfo {
        final String label;
        final int y;
        RowInfo(String label, int y) { this.label = label; this.y = y; }
    }

    public AdminPanelScreen(CompoundTag configData) {
        super(Component.literal("§6§lAdmin Panel"));
        this.configData = configData;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null) return;
        this.clearWidgets();
        this.rowInfos.clear();
        
        computeLayout();
        buildSidebar();

        if (currentCategory == Category.MAIN) {
            initMainPage(this.contentX, this.contentY, this.contentW, this.contentH);
        } else {
            initCategoryPage(this.contentX, this.contentW, this.contentY);
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
                this.scrollOffset = 0;
                this.init();
            }
        }).bounds(leftX, this.footerY, FOOTER_BUTTON_WIDTH, 20).build());

        PulsingButton saveBtn = new PulsingButton(rightX, this.footerY, FOOTER_BUTTON_WIDTH, 20, Component.literal("§bSalvar"), (btn) -> {
            if (!hasPendingChanges()) {
                ((PulsingButton)btn).pulseError(800);
                return;
            }
            savePendingChanges();
        });
        saveBtn.active = this.currentCategory != Category.MAIN && hasPendingChanges();
        this.addRenderableWidget(saveBtn);
    }

    private void computeLayout() {
        int screenWidth = this.width;
        int screenHeight = this.height;

        this.frameW = Math.min(900, screenWidth - OUTER_MARGIN * 2);
        this.frameH = Math.min(600, screenHeight - OUTER_MARGIN * 2);

        this.frameX = (screenWidth - this.frameW) / 2;
        this.frameY = (screenHeight - this.frameH) / 2;

        this.headerY = this.frameY;
        this.barY = this.frameY + BAR_HEIGHT;

        this.sidebarX = this.frameX + INNER_MARGIN;
        this.sidebarY = this.barY + INNER_MARGIN; 
        this.sidebarH = this.frameH - (this.barY - this.frameY) - INNER_MARGIN * 2;
        int FIXED_SIDEBAR_WIDTH = 160;
        int maxSidebarAllowed = (this.frameW / 3) - INNER_MARGIN;
        this.sidebarW = Math.min(FIXED_SIDEBAR_WIDTH, maxSidebarAllowed);

        this.contentX = this.sidebarX + this.sidebarW + INNER_MARGIN;
        this.contentY = this.barY + INNER_MARGIN;
        this.contentW = (this.frameX + this.frameW) - this.contentX - INNER_MARGIN;
        this.contentH = this.frameH - (this.barY - this.frameY) - INNER_MARGIN * 2;
        
        this.contentStartY = this.contentY; 
        this.footerY = this.frameY + this.frameH - 30;
    }

    private void buildSidebar() {
        int y = this.sidebarY + 4;
        int buttonX = this.sidebarX + 4;
        int buttonW = Math.max(80, this.sidebarW - 8);
        sidebarButtons.clear();
        
        for (Category category : Category.values()) {
            final Category cat = category;
            boolean active = this.currentCategory == cat;
            Component label = Component.literal((active ? "§6> " : "") + cat.title);
            
            Button btn = Button.builder(label, (btn2) -> {
                this.currentCategory = cat;
                this.scrollOffset = 0; 
                this.init();
            }).bounds(buttonX, y, buttonW, CATEGORY_BUTTON_HEIGHT).build();
            
            sidebarButtons.add(btn);
            y += CATEGORY_BUTTON_HEIGHT + CATEGORY_BUTTON_GAP;
        }
    }

    private void initMainPage(int contentStartX, int contentStartY, int contentWidth, int contentHeight) {
    }

    private void initCategoryPage(int contentStartX, int contentWidth, int startY) {
        this.activeCategoryKeys.clear();
        this.widgetOriginalY.clear();
        this.widgetTooltips.clear();
        
        loadBaselineForCategory();

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
                break;
            case THROTTLE:
                addSectionLabel("§6§lFarm & Charm", y); y += 24;
                addRow("throttleFarmAndCharm", "Throttle Farm&Charm", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("cookingPotTickRate", "Rate Cooking Pot (Seg)", FieldType.SECONDS_TO_TICKS, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("roasterTickRate", "Rate Roaster (Seg)", FieldType.SECONDS_TO_TICKS, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT + 4;

                addSectionLabel("§6§lVinery", y); y += 24;
                addRow("throttleVinery", "Throttle Vinery", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("fermentationBarrelTickRate", "Rate Barrel (Seg)", FieldType.SECONDS_TO_TICKS, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT + 4;

                addSectionLabel("§6§lEntity Culling", y); y += 24;
                addRow("enableEntityCulling", "Entity Culling", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("entityCullingDistance", "Distância (Blocos)", FieldType.INTEGER, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("cullSuperGlue", "Cull SuperGlue", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("cullBotania", "Cull Botania", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT + 4;

                addSectionLabel("§6§lCreate - Contraptions", y); y += 24;
                addRow("throttleIdleContraptions", "Throttle Contraptions AFK", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("contraptionIdleThrottleRate", "Rate Throttle AFK (Seg)", FieldType.SECONDS_TO_TICKS, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT + 12;
                totalContentHeight = y - startY;
                break;
            case FIXES:
                addRow("fixBackstabbingExploit", "Fix Backstabbing", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("nerfTurtleMaster", "Nerf Turtle Master", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("turtleMasterResistance", "Res. Turtle (Padrão)", FieldType.INTEGER, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("strongTurtleMasterResistance", "Res. Turtle (Forte)", FieldType.INTEGER, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                totalContentHeight = y - startY;
                break;
            case DEBUG:
                addRow("debugDamageReceived", "Debug Dano Recebido", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("debugDamageDealt", "Debug Dano Causado", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("debugDamageBreakdown", "Detalhamento de Dano", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("debugVillagers", "Debug Villagers", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                addRow("debugAntiSwap", "Debug Anti-Swap", FieldType.BOOLEAN, y, toggleX, resetX, inputX, inputWidth); y += ROW_HEIGHT;
                totalContentHeight = y - startY;
                break;
            default: break;
        }

        this.maxScroll = Math.max(0, totalContentHeight - this.contentH);
        this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        applyScroll();
    }

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
            widgetTooltips.put(cycleBtn, tooltips.getOrDefault(key, List.of("Configuração: " + label)));
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
            widgetTooltips.put(box, tooltips.getOrDefault(key, List.of("Configuração: " + label)));
        }

        Button resetBtn = Button.builder(Component.literal("↺"), (btn) -> {
            Object baseline = baselineValues.get(key);
            if (baseline != null) pendingValues.put(key, baseline);
            this.init();
        }).bounds(resetX, y, RESET_BUTTON_WIDTH, 20).build();
        this.addRenderableWidget(resetBtn);
        widgetOriginalY.put(resetBtn, y);
        widgetTooltips.put(resetBtn, List.of("Resetar para o padrão"));
    }

    private void addSectionLabel(String label, int y) {
        registerRow(label, y);
    }

    private void registerRow(String label, int y) {
        this.rowInfos.add(new RowInfo(label, y));
    }

    private void applyScroll() {
        int offset = -scrollOffset;
        for (var widget : this.children()) {
            if (widget instanceof AbstractWidget w) {
                Integer origY = widgetOriginalY.get(w);
                if (origY != null && origY >= this.contentStartY) {
                    w.setY(origY + offset);
                }
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        
        // 1. Fundo e Borda Externa
        renderEditorFrame(graphics, Component.literal("§6§l" + currentCategory.title),
            this.frameX, this.frameY, this.frameW, this.frameH,
            this.headerY, this.barY, this.contentY, this.footerY);
        
        // 2. PAINÉIS INTERNOS (Desenha os fundos da sidebar e conteúdo)
        GuiLayoutUtils.drawPanel(graphics, this.sidebarX, this.sidebarY, this.sidebarW, this.sidebarH, 0xBF071014, 0xFF3D5564);
        GuiLayoutUtils.drawPanel(graphics, this.contentX, this.contentY, this.contentW, this.contentH, 0xD9182432, 0xFF4E6B8D);
        
        // 3. Labels (com scroll offset)
        renderContentLabels(graphics);

        // 4. Widgets (botões, inputs, etc)
        super.render(graphics, mouseX, mouseY, partialTicks);
        
        // 5. Mascaramento para o Scroll (Cobre o que vaza para fora do painel)
        graphics.fill(this.contentX, this.frameY, this.contentX + this.contentW, this.contentY, 0xFF12161B);
        int bottomLimit = this.contentY + this.contentH;
        graphics.fill(this.contentX, bottomLimit, this.contentX + this.contentW, this.frameY + this.frameH - 30, 0xFF12161B);

        // 6. Scrollbar e Tooltip
        if (maxScroll > 0) {
            renderScrollbar(graphics);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderContentLabels(GuiGraphics graphics) {
        if (currentCategory != Category.MAIN && !rowInfos.isEmpty()) {
            int x = this.contentX + 8;
            int rowWidth = this.contentW - 16;
            int evenOdd = 0;
            
            int clipMinY = this.contentY;
            int clipMaxY = this.contentY + this.contentH;

            for (RowInfo row : rowInfos) {
                int drawY = row.y - scrollOffset;

                if (drawY + ROW_HEIGHT < clipMinY || drawY > clipMaxY) {
                    if (!row.label.startsWith("§6§l")) evenOdd++;
                    continue;
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
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int scrollbarX = this.contentX + this.contentW - SCROLLBAR_WIDTH; 
        int scrollTrackHeight = this.contentH;
        
        graphics.fill(scrollbarX, this.contentY, scrollbarX + SCROLLBAR_WIDTH, this.contentY + this.contentH, 0x33000000);
        
        float scrollRatio = (float) scrollOffset / maxScroll;
        int scrollbarHeight = Math.max(20, (int) (scrollTrackHeight * ((float) (this.contentH) / (this.contentH + maxScroll))));
        int scrollbarY = this.contentY + (int) (scrollRatio * (scrollTrackHeight - scrollbarHeight));
        
        int color = isDraggingScrollbar ? 0xCCFFFFFF : 0xAAFFFFFF;
        graphics.fill(scrollbarX + 1, scrollbarY, scrollbarX + SCROLLBAR_WIDTH - 1, scrollbarY + scrollbarHeight, color);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        currentHoverTooltip = null;
        
        for (Button btn : sidebarButtons) {
            if (btn.isMouseOver(mouseX, mouseY)) {
                currentHoverTooltip = List.of("Categoria: " + btn.getMessage().getString());
                break;
            }
        }
        
        if (currentHoverTooltip == null) {
            for (var child : this.children()) {
                if (child instanceof AbstractWidget w) {
                    int widgetScreenY = w.getY();
                    if (widgetScreenY < this.contentY || widgetScreenY + w.getHeight() > this.contentY + this.contentH) {
                        continue;
                    }
                    if (w.isMouseOver(mouseX, mouseY)) {
                        currentHoverTooltip = widgetTooltips.get(w);
                        if (currentHoverTooltip != null && !currentHoverTooltip.isEmpty()) break;
                    }
                }
            }
        }

        if (currentHoverTooltip != null && !currentHoverTooltip.isEmpty()) {
            List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
            for (String s : currentHoverTooltip) lines.add(Component.literal(s));
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Button btn : sidebarButtons) {
            if (btn.isMouseOver(mouseX, mouseY)) {
                btn.onPress();
                return true;
            }
        }

        if (maxScroll > 0 && button == 0) { 
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
            scrollOffset = (int) ((mouseY - this.contentY - scrollbarHeight / 2.0f) / (scrollTrackHeight - scrollbarHeight) * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            applyScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (maxScroll > 0 && mouseX >= this.contentX && mouseX < this.contentX + this.contentW
                && mouseY >= this.contentY && mouseY < this.contentY + this.contentH) {
            scrollOffset -= (int) (scrollY * 25.0D);
            scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
            applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    private boolean hasPendingChanges() {
        for (String key : activeCategoryKeys) {
            FieldType type = fieldTypes.get(key);
            if (type == null) continue;
            Object baseline = baselineValues.get(key);
            Object pending = pendingValues.get(key);
            if (!Objects.equals(baseline, pending)) return true;
        }
        return false;
    }

    private void savePendingChanges() {
        if (!hasPendingChanges()) return;
        for (String key : activeCategoryKeys) {
            FieldType type = fieldTypes.get(key);
            if (type == null) continue;
            Object baseline = baselineValues.get(key);
            Object pending = pendingValues.get(key);
            if (!Objects.equals(baseline, pending)) {
                sendUpdate(key, pending, type);
                baselineValues.put(key, pending);
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

    private void loadBaselineForCategory() {
        baselineValues.clear();
        pendingValues.clear();
        
        List<String> keys = new ArrayList<>();
        if (currentCategory == Category.SERVER) {
            keys.addAll(List.of("enableAntiSwap", "enableInfiniteTrades", "infiniteTradeTag"));
        } else if (currentCategory == Category.THROTTLE) {
            keys.addAll(List.of("throttleFarmAndCharm", "cookingPotTickRate", "roasterTickRate", 
                                "throttleVinery", "fermentationBarrelTickRate",
                                "enableEntityCulling", "entityCullingDistance", "cullSuperGlue", "cullBotania",
                                "throttleIdleContraptions", "contraptionIdleThrottleRate"));
        } else if (currentCategory == Category.FIXES) {
            keys.addAll(List.of("fixBackstabbingExploit", "nerfTurtleMaster", "turtleMasterResistance", "strongTurtleMasterResistance"));
        } else if (currentCategory == Category.DEBUG) {
            keys.addAll(List.of("debugDamageReceived", "debugDamageDealt", "debugDamageBreakdown", "debugVillagers", "debugAntiSwap"));
        }

        for (String key : keys) {
            if (configData != null && configData.contains(key)) {
                FieldType type = getTypeForKey(key);
                if (type == FieldType.BOOLEAN) baselineValues.put(key, configData.getBoolean(key));
                else if (type == FieldType.INTEGER) baselineValues.put(key, configData.getInt(key));
                else if (type == FieldType.LONG) baselineValues.put(key, configData.getLong(key));
                else baselineValues.put(key, configData.getString(key));
            } else {
                baselineValues.put(key, getTypeForKey(key).readDefaultValue());
            }
            pendingValues.put(key, baselineValues.get(key));
        }
    }
    
    private FieldType getTypeForKey(String key) {
        switch (key) {
            case "enableAntiSwap": case "enableInfiniteTrades": case "throttleFarmAndCharm": case "throttleVinery":
            case "enableEntityCulling": case "cullSuperGlue": case "cullBotania": case "throttleIdleContraptions":
            case "fixBackstabbingExploit": case "nerfTurtleMaster": 
            case "debugDamageReceived": case "debugDamageDealt": case "debugDamageBreakdown": case "debugVillagers": case "debugAntiSwap":
                return FieldType.BOOLEAN;
            case "cookingPotTickRate": case "roasterTickRate": case "fermentationBarrelTickRate":
            case "entityCullingDistance": case "turtleMasterResistance": case "strongTurtleMasterResistance":
            case "contraptionIdleThrottleRate":
                return FieldType.INTEGER;
            case "infiniteTradeTag": return FieldType.STRING;
            default: return FieldType.STRING;
        }
    }

    private final Map<String, List<String>> tooltips = new LinkedHashMap<>();
    {
        tooltips.put("enableAntiSwap", List.of("§bAnti-Swap", "§7Ativa proteção contra troca rápida de armas.", "§7Evita abusos de dano."));
        tooltips.put("throttleFarmAndCharm", List.of("§bThrottle Farm&Charm", "§7Reduz processamento de blocos deste mod.", "§7Melhora o TPS."));
    }

    private boolean getBaselineBoolean(String key) { Object o = baselineValues.get(key); return o instanceof Boolean && (Boolean) o; }
    private boolean getPendingBoolean(String key, boolean def) { Object o = pendingValues.get(key); return o instanceof Boolean ? (Boolean) o : def; }
    private String getBaselineString(String key, FieldType type) { Object o = baselineValues.get(key); return o != null ? o.toString() : type.readDefaultValue(); }
    private String getPendingString(String key, String def) { Object o = pendingValues.get(key); return o != null ? o.toString() : def; }
    
    private static int parseIntSafe(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
    private static long parseLongSafe(String s, long def) { try { return Long.parseLong(s); } catch (Exception e) { return def; } }
    private static boolean isValidIntegerInput(String s) { return s.matches("-?\\d*"); }
    private static boolean isValidSecondsInput(String s) { return s.matches("\\d+"); }

    private void renderEditorFrame(GuiGraphics graphics, Component title, int x, int y, int w, int h, int headerY, int barY, int contentY, int footerY) {
        graphics.fill(x, y, x + w, y + h, 0xCC12161B);
        graphics.fill(x, y, x + w, y + BAR_HEIGHT, 0xFF2C3440);
        graphics.drawString(this.font, title, x + 10, y + 8, 0xFFE5F6FF);
        graphics.renderOutline(x, y, w, h, 0xFF4E6B8D);
    }

    private enum FieldType {
        BOOLEAN("bool") {
            @Override String format(Object value) { return String.valueOf(value instanceof Boolean ? value : false); }
            @Override String readInputValue(CompoundTag source, String key) { return String.valueOf(source != null && source.getBoolean(key)); }
            @Override String readDefaultValue() { return "false"; }
        },
        STRING("string") {
            @Override String format(Object value) { return stripWrappingQuotes(value != null ? value.toString() : ""); }
            @Override String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "";
                if (source.contains(key, Tag.TAG_STRING)) return stripWrappingQuotes(source.getString(key));
                Tag tag = source.get(key);
                return tag != null ? stripWrappingQuotes(tag.getAsString()) : "";
            }
            @Override String readDefaultValue() { return ""; }
        },
        INTEGER("int") {
            @Override String format(Object value) { return String.valueOf(parseIntSafe(value != null ? value.toString() : "", 0)); }
            @Override String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "0";
                if (source.contains(key, Tag.TAG_INT) || source.contains(key, Tag.TAG_SHORT) || source.contains(key, Tag.TAG_BYTE)) return String.valueOf(source.getInt(key));
                Tag tag = source.get(key);
                return String.valueOf(parseIntSafe(tag != null ? tag.getAsString() : "", 0));
            }
            @Override String readDefaultValue() { return "0"; }
        },
        LONG("long") {
            @Override String format(Object value) { return String.valueOf(parseLongSafe(value != null ? value.toString() : "", 0L)); }
            @Override String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "0";
                if (source.contains(key, Tag.TAG_LONG) || source.contains(key, Tag.TAG_INT) || source.contains(key, Tag.TAG_SHORT) || source.contains(key, Tag.TAG_BYTE)) return String.valueOf(source.getLong(key));
                Tag tag = source.get(key);
                return String.valueOf(parseLongSafe(tag != null ? tag.getAsString() : "", 0L));
            }
            @Override String readDefaultValue() { return "0"; }
        },
        SECONDS_TO_TICKS("int") {
            @Override String format(Object value) { return String.valueOf(secondsToTicks(value != null ? value.toString() : "")); }
            @Override String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "1";
                int ticks;
                if (source.contains(key, Tag.TAG_INT) || source.contains(key, Tag.TAG_SHORT) || source.contains(key, Tag.TAG_BYTE) || source.contains(key, Tag.TAG_LONG)) ticks = source.getInt(key);
                else { Tag tag = source.get(key); ticks = parseIntSafe(tag != null ? tag.getAsString() : "", 20); }
                return String.valueOf(Math.max(1, ticks / 20));
            }
            @Override String readDefaultValue() { return "1"; }
        };

        private final String packetName;
        FieldType(String packetName) { this.packetName = packetName; }
        abstract String format(Object value);
        String readInputValue(CompoundTag source, String key) { if (source == null || !source.contains(key)) return ""; Tag tag = source.get(key); return tag != null ? stripWrappingQuotes(tag.getAsString()) : ""; }
        abstract String readDefaultValue();
        public String packetName() { return packetName; }
    }

    private static String stripWrappingQuotes(String s) { if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) return s.substring(1, s.length() - 1); return s; }
    private static String secondsToTicks(String s) { try { return String.valueOf(Integer.parseInt(s) * 20); } catch (Exception e) { return "20"; } }
    private static String ticksToSecondsInput(String ticks) { try { return String.valueOf(Math.max(1, Integer.parseInt(ticks) / 20)); } catch (Exception e) { return "1"; } }
}
