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
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Painel Administrativo do ServerFixes.
 * Usa ObjectSelectionList do vanilla para scrolling/clipping corretos.
 */
@SuppressWarnings("all")
public class AdminPanelScreen extends Screen {
    private final CompoundTag configData;

    // Layout Constants
    private static final int FRAME_PAD = 8;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SIDEBAR_WIDTH = 160;
    private static final int ROW_HEIGHT = 28;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int FOOTER_BTN_W = 90;
    private static final int FOOTER_GAP = 10;

    // Widget positioning offsets (relative to entry left edge x)
    private static final int LABEL_OFFSET = 5;
    private static final int TOGGLE_OFFSET = 130;
    private static final int RESET_BTN_SIZE = 20;
    private static final int INPUT_WIDTH = 80;
    private static final int TOGGLE_WIDTH = 44;

    // Dimensions
    private int frameX, frameY, frameW, frameH;
    private int sidebarX, sidebarY, sidebarW, sidebarH;
    private int contentX, contentY, contentW, contentH;
    private int footerY;
    private Category currentCategory = Category.MAIN;

    // Data
    private final List<String> activeCategoryKeys = new ArrayList<>();
    private final Map<String, FieldType> fieldTypes = new LinkedHashMap<>();
    private final Map<Object, List<String>> widgetTooltips = new java.util.IdentityHashMap<>();
    private final Map<String, Object> baselineValues = new LinkedHashMap<>();
    private final Map<String, Object> pendingValues = new LinkedHashMap<>();

    // Widgets
    private AdminContentList contentList;
    private Button backButton;
    private PulsingButton saveButton;
    private final List<Button> sidebarButtons = new ArrayList<>();

    public enum Category {
        MAIN("Painel Administrativo"),
        SERVER("Configurações do Servidor"),
        THROTTLE("Otimizações & Perf"),
        FIXES("Correções & Tweaks"),
        DEBUG("Depuração");

        final String title;
        Category(String title) { this.title = title; }
    }

    public AdminPanelScreen(CompoundTag configData) {
        super(Component.literal("Admin Panel"));
        this.configData = configData;
    }

    // ================================================================
    // INIT
    // ================================================================
    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null) return;
        this.clearWidgets();
        this.sidebarButtons.clear();
        this.activeCategoryKeys.clear();
        this.fieldTypes.clear();
        this.widgetTooltips.clear();

        computeLayout();
        buildSidebar();

        this.contentList = new AdminContentList(this.minecraft, contentW, contentH, contentX, contentY, ROW_HEIGHT);
        this.addRenderableWidget(this.contentList);

        if (currentCategory != Category.MAIN) {
            populateContentList();
        } else {
            contentList.clearEntries();
        }

        buildFooter();
    }

    private void computeLayout() {
        int sw = this.width;
        int sh = this.height;

        int targetW = Math.max(600, (int) (sw * 0.85));
        int targetH = Math.max(450, (int) (sh * 0.85));
        this.frameW = Math.min(targetW, sw - 20);
        this.frameH = Math.min(targetH, sh - 20);
        this.frameX = (sw - frameW) / 2;
        this.frameY = (sh - frameH) / 2;

        this.sidebarX = frameX + FRAME_PAD;
        this.sidebarY = frameY + HEADER_HEIGHT + FRAME_PAD;
        this.sidebarW = SIDEBAR_WIDTH;
        this.sidebarH = frameH - HEADER_HEIGHT - FOOTER_HEIGHT - FRAME_PAD * 2;

        this.contentX = sidebarX + sidebarW + FRAME_PAD;
        this.contentY = sidebarY;
        this.contentW = (frameX + frameW) - contentX - FRAME_PAD;
        this.contentH = sidebarH;

        this.footerY = frameY + frameH - FOOTER_HEIGHT;
    }

    private void buildSidebar() {
        int y = sidebarY + 4;
        int btnX = sidebarX + 4;
        int btnW = sidebarW - 8;

        for (Category cat : Category.values()) {
            boolean active = this.currentCategory == cat;
            Component label = Component.literal((active ? "§6> " : "  ") + cat.title);
            Button btn = Button.builder(label, (b) -> {
                this.currentCategory = cat;
                this.init();
            }).bounds(btnX, y, btnW, 20).build();

            sidebarButtons.add(btn);
            this.addRenderableWidget(btn);
            y += 24;
        }
    }

    private void populateContentList() {
        loadBaselineForCategory();
        contentList.clearEntries();

        switch (currentCategory) {
            case SERVER:
                addConfigEntry("enableAntiSwap", "Anti-Swap Exploit", FieldType.BOOLEAN);
                addConfigEntry("enableInfiniteTrades", "Trocas Infinitas", FieldType.BOOLEAN);
                addConfigEntry("infiniteTradeTag", "Tag p/ Trocas Infinitas", FieldType.STRING);
                break;
            case THROTTLE:
                contentList.addEntry(new SectionEntry("Farm & Charm"));
                addConfigEntry("throttleFarmAndCharm", "Throttle Farm&Charm", FieldType.BOOLEAN);
                addConfigEntry("cookingPotTickRate", "Rate Cooking Pot (Seg)", FieldType.SECONDS_TO_TICKS);
                addConfigEntry("roasterTickRate", "Rate Roaster (Seg)", FieldType.SECONDS_TO_TICKS);
                contentList.addEntry(new SectionEntry("Vinery"));
                addConfigEntry("throttleVinery", "Throttle Vinery", FieldType.BOOLEAN);
                addConfigEntry("fermentationBarrelTickRate", "Rate Barrel (Seg)", FieldType.SECONDS_TO_TICKS);
                contentList.addEntry(new SectionEntry("Entity Culling"));
                addConfigEntry("enableEntityCulling", "Entity Culling", FieldType.BOOLEAN);
                addConfigEntry("entityCullingDistance", "Distância (Blocos)", FieldType.INTEGER);
                addConfigEntry("cullSuperGlue", "Cull SuperGlue", FieldType.BOOLEAN);
                addConfigEntry("cullBotania", "Cull Botania", FieldType.BOOLEAN);
                contentList.addEntry(new SectionEntry("Create - Contraptions"));
                addConfigEntry("throttleIdleContraptions", "Throttle Contraptions AFK", FieldType.BOOLEAN);
                addConfigEntry("contraptionIdleThrottleRate", "Rate Throttle AFK (Seg)", FieldType.SECONDS_TO_TICKS);
                break;
            case FIXES:
                addConfigEntry("fixBackstabbingExploit", "Fix Backstabbing", FieldType.BOOLEAN);
                addConfigEntry("nerfTurtleMaster", "Nerf Turtle Master", FieldType.BOOLEAN);
                addConfigEntry("turtleMasterResistance", "Res. Turtle (Padrão)", FieldType.INTEGER);
                addConfigEntry("strongTurtleMasterResistance", "Res. Turtle (Forte)", FieldType.INTEGER);
                break;
            case DEBUG:
                addConfigEntry("debugDamageReceived", "Debug Dano Recebido", FieldType.BOOLEAN);
                addConfigEntry("debugDamageDealt", "Debug Dano Causado", FieldType.BOOLEAN);
                addConfigEntry("debugDamageBreakdown", "Detalhamento de Dano", FieldType.BOOLEAN);
                addConfigEntry("debugVillagers", "Debug Villagers", FieldType.BOOLEAN);
                addConfigEntry("debugAntiSwap", "Debug Anti-Swap", FieldType.BOOLEAN);
                break;
            default: break;
        }
    }

    private void addConfigEntry(String key, String label, FieldType type) {
        activeCategoryKeys.add(key);
        fieldTypes.put(key, type);

        if (type == FieldType.BOOLEAN) {
            boolean baseline = getBaselineBoolean(key);
            boolean pending = getPendingBoolean(key, baseline);
            CycleButton<Boolean> cycleBtn = CycleButton.builder(
                (Boolean value) -> value ? Component.literal("§aON") : Component.literal("§cOFF")
            ).withValues(true, false)
             .withInitialValue(pending)
             .displayOnlyValue()
             .create(0, 0, TOGGLE_WIDTH, 20, Component.empty(), (btn, val) -> pendingValues.put(key, val));

            final Object bl = baselineValues.get(key);
            Button resetBtn = Button.builder(Component.literal("§7↺"), (btn) -> {
                if (bl != null) pendingValues.put(key, bl);
                this.init();
            }).bounds(0, 0, RESET_BTN_SIZE, 20).build();

            contentList.addEntry(new ConfigEntry(label, cycleBtn, null, resetBtn, key));
        } else {
            String baseline = getBaselineString(key, type);
            String pending = getPendingString(key, baseline);
            EditBox box = new SelectableEditBox(this.font, 0, 0, INPUT_WIDTH, 20, Component.literal(label));
            if (type == FieldType.INTEGER || type == FieldType.LONG)
                box.setFilter(AdminPanelScreen::isValidIntegerInput);
            else if (type == FieldType.SECONDS_TO_TICKS)
                box.setFilter(AdminPanelScreen::isValidSecondsInput);
            box.setValue(pending);
            box.setResponder(newValue -> pendingValues.put(key, newValue));

            final Object bl = baselineValues.get(key);
            Button resetBtn = Button.builder(Component.literal("§7↺"), (btn) -> {
                if (bl != null) pendingValues.put(key, bl);
                this.init();
            }).bounds(0, 0, RESET_BTN_SIZE, 20).build();

            contentList.addEntry(new ConfigEntry(label, null, box, resetBtn, key));
        }
    }

    private void buildFooter() {
        int totalW = FOOTER_BTN_W * 2 + FOOTER_GAP;
        int leftX = frameX + (frameW - totalW) / 2;
        int rightX = leftX + FOOTER_BTN_W + FOOTER_GAP;
        int fy = footerY + 4;

        this.backButton = Button.builder(
            Component.literal(currentCategory == Category.MAIN ? "§cFechar" : "§eVoltar"),
            (btn) -> {
                if (this.currentCategory == Category.MAIN) {
                    if (this.minecraft != null) this.minecraft.setScreen(null);
                } else {
                    this.currentCategory = Category.MAIN;
                    this.init();
                }
            }
        ).bounds(leftX, fy, FOOTER_BTN_W, 20).build();

        this.saveButton = new PulsingButton(rightX, fy, FOOTER_BTN_W, 20,
            Component.literal("§bSalvar"), (btn) -> {
                if (!hasPendingChanges()) {
                    ((PulsingButton) btn).pulseError(800);
                    return;
                }
                savePendingChanges();
            });
        this.saveButton.active = this.currentCategory != Category.MAIN && hasPendingChanges();

        this.addRenderableWidget(this.backButton);
        this.addRenderableWidget(this.saveButton);
    }

    // ================================================================
    // RENDER
    // ================================================================
    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int fr = frameX + frameW;
        int fb = frameY + frameH;
        graphics.fill(frameX, frameY, fr, fb, 0xFF0D1117);
        graphics.fill(frameX, frameY, fr, frameY + 2, 0xFF30363D);
        graphics.fill(frameX, fb - 2, fr, fb, 0xFF30363D);
        graphics.fill(frameX, frameY, frameX + 2, fb, 0xFF30363D);
        graphics.fill(fr - 2, frameY, fr, fb, 0xFF30363D);

        graphics.fill(frameX + 2, frameY + 2, fr - 2, frameY + HEADER_HEIGHT, 0xFF161B22);
        graphics.drawString(this.font, "§6§l" + currentCategory.title, frameX + 12, frameY + 7, 0xFFFFAA00, false);

        int sb = sidebarY + sidebarH;
        int sr = sidebarX + sidebarW;
        graphics.fill(sidebarX, sidebarY, sr, sb, 0xBF0D1117);
        graphics.hLine(sidebarX, sr, sidebarY - 1, 0xFF30363D);
        graphics.hLine(sidebarX, sr, sb, 0xFF30363D);
        graphics.vLine(sidebarX - 1, sidebarY, sb, 0xFF30363D);
        graphics.vLine(sr, sidebarY, sb, 0xFF30363D);

        int cr = contentX + contentW;
        int cb = contentY + contentH;
        graphics.fill(contentX, contentY, cr, cb, 0xBF0D1117);
        graphics.hLine(contentX, cr, contentY - 1, 0xFF30363D);
        graphics.hLine(contentX, cr, cb, 0xFF30363D);
        graphics.vLine(contentX - 1, contentY, cb, 0xFF30363D);
        graphics.vLine(cr, contentY, cb, 0xFF30363D);

        graphics.fill(frameX + 2, footerY, fr - 2, footerY + FOOTER_HEIGHT, 0xFF161B22);
        graphics.hLine(frameX + 2, fr - 2, footerY - 1, 0xFF30363D);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    // ================================================================
    // CONTENT LIST (vanilla ObjectSelectionList)
    // ================================================================
    public class AdminContentList extends ObjectSelectionList<AdminEntry> {
        public AdminContentList(Minecraft mc, int w, int h, int x, int y, int ih) {
            super(mc, w, h, y, y + h, ih);
            setLeftPos(x);
            setRenderHeader(false, 0);
        }

        @Override
        public int addEntry(AdminEntry entry) {
            return super.addEntry(entry);
        }

        public void clearEntries() {
            children().clear();
            setScrollAmount(0);
        }

        // Remover dirt — override renderList() em vez de renderBackground()
        @Override
        protected void renderList(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            // NÃO chamar super — vanilla desenha dirt aqui
            int y = this.y0 + 4 - (int) this.getScrollAmount();
            int rowWidth = this.getRowWidth();
            for (int i = 0; i < this.children().size(); ++i) {
                AdminEntry entry = this.children().get(i);
                int entryTop = y;
                int rowLeft = this.getRowLeft();
                boolean hovered = mouseX >= rowLeft && mouseX <= rowLeft + rowWidth && mouseY >= entryTop && mouseY <= entryTop + this.itemHeight;
                entry.render(graphics, i, entryTop, rowLeft, rowWidth, this.itemHeight, mouseX, mouseY, hovered, partialTicks);
                y += this.itemHeight;
            }
        }

        // Delegar cliques para widgets filhos primeiro
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            for (AdminEntry entry : children()) {
                if (entry.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            for (AdminEntry entry : children()) entry.mouseReleased(mouseX, mouseY, button);
            return super.mouseReleased(mouseX, mouseY, button);
        }
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            for (AdminEntry entry : children()) entry.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            for (AdminEntry entry : children()) {
                if (entry.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            for (AdminEntry entry : children()) {
                if (entry.charTyped(codePoint, modifiers)) return true;
            }
            return super.charTyped(codePoint, modifiers);
        }

        @Override
        protected int getScrollbarPosition() {
            return getRight() - SCROLLBAR_WIDTH;
        }

        @Override
        public int getRowWidth() {
            return Math.max(50, width - SCROLLBAR_WIDTH - 8);
        }

        @Override
        protected void renderBackground(@NotNull GuiGraphics graphics) {
            // Sem background de dirt
        }
    }

    // ================================================================
    // ENTRIES
    // ================================================================
    public abstract class AdminEntry extends ObjectSelectionList.Entry<AdminEntry> {
    }

    public class SectionEntry extends AdminEntry {
        private final String title;
        public SectionEntry(String title) { this.title = title; }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            graphics.drawString(AdminPanelScreen.this.font, title, x + LABEL_OFFSET, y + 4, 0xFFFFD700, false);
            graphics.fill(x + LABEL_OFFSET, y + 18, x + entryWidth - LABEL_OFFSET, y + 19, 0x44FFD700);
        }
        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
        @Override public net.minecraft.network.chat.MutableComponent getNarration() { return Component.literal(title); }
    }

    public class ConfigEntry extends AdminEntry {
        private final String label;
        @Nullable private final AbstractWidget toggleBtn;
        @Nullable private final AbstractWidget inputBox;
        private final AbstractWidget resetBtn;
        private final String key;
        private int hoverTimer;

        public ConfigEntry(String label, @Nullable AbstractWidget toggleBtn, @Nullable AbstractWidget inputBox, AbstractWidget resetBtn, String key) {
            this.label = label; this.toggleBtn = toggleBtn; this.inputBox = inputBox; this.resetBtn = resetBtn; this.key = key;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (index % 2 == 0) graphics.fill(x, y, x + entryWidth, y + entryHeight, 0x18FFFFFF);

            // Label
            graphics.drawString(AdminPanelScreen.this.font, label, x + LABEL_OFFSET, y + 4, 0xE3E6EE, false);

            // Widgets posicionados RELATIVAMENTE ao entry (x + offset)
            int toggleX = x + TOGGLE_OFFSET;
            int resetX = x + entryWidth - RESET_BTN_SIZE - LABEL_OFFSET;
            int inputX = resetX - INPUT_WIDTH - 4;

            if (toggleBtn != null) {
                toggleBtn.setPosition(toggleX, y + 4);
                toggleBtn.setWidth(TOGGLE_WIDTH);
                toggleBtn.render(graphics, mouseX, mouseY, tickDelta);
            }
            if (inputBox != null) {
                inputBox.setPosition(inputX, y + 4);
                inputBox.setWidth(INPUT_WIDTH);
                inputBox.render(graphics, mouseX, mouseY, tickDelta);
            }
            resetBtn.setPosition(resetX, y + 4);
            resetBtn.setWidth(RESET_BTN_SIZE);
            resetBtn.render(graphics, mouseX, mouseY, tickDelta);

            // Tooltip
            if (hovered) {
                hoverTimer++;
                if (hoverTimer > 10) {
                    List<Component> lines = new ArrayList<>();
                    for (String s : widgetTooltips.getOrDefault(key, List.of("Configuração: " + label))) lines.add(Component.literal(s));
                    graphics.renderComponentTooltip(AdminPanelScreen.this.font, lines, mouseX, mouseY);
                }
            } else hoverTimer = 0;
        }

        @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (toggleBtn != null && toggleBtn.mouseClicked(mouseX, mouseY, button)) return true;
            if (inputBox != null && inputBox.mouseClicked(mouseX, mouseY, button)) return true;
            return resetBtn.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (toggleBtn != null) toggleBtn.mouseReleased(mouseX, mouseY, button);
            if (inputBox != null) inputBox.mouseReleased(mouseX, mouseY, button);
            resetBtn.mouseReleased(mouseX, mouseY, button);
            return false;
        }
        @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (toggleBtn != null && toggleBtn.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
            if (inputBox != null && inputBox.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
            return resetBtn.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return inputBox != null && inputBox.isFocused() && inputBox.keyPressed(keyCode, scanCode, modifiers);
        }
        @Override public boolean charTyped(char codePoint, int modifiers) {
            return inputBox != null && inputBox.isFocused() && inputBox.charTyped(codePoint, modifiers);
        }
        public @Nullable net.minecraft.client.gui.components.events.GuiEventListener getFocused() {
            return inputBox != null && inputBox.isFocused() ? inputBox : null;
        }
        @Override public net.minecraft.network.chat.MutableComponent getNarration() { return Component.literal(label); }
    }

    // ================================================================
    // DATA / SAVE
    // ================================================================
    private boolean hasPendingChanges() {
        for (String key : activeCategoryKeys) {
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
        if (currentCategory == Category.SERVER) keys.addAll(List.of("enableAntiSwap", "enableInfiniteTrades", "infiniteTradeTag"));
        else if (currentCategory == Category.THROTTLE) keys.addAll(List.of("throttleFarmAndCharm", "cookingPotTickRate", "roasterTickRate", "throttleVinery", "fermentationBarrelTickRate", "enableEntityCulling", "entityCullingDistance", "cullSuperGlue", "cullBotania", "throttleIdleContraptions", "contraptionIdleThrottleRate"));
        else if (currentCategory == Category.FIXES) keys.addAll(List.of("fixBackstabbingExploit", "nerfTurtleMaster", "turtleMasterResistance", "strongTurtleMasterResistance"));
        else if (currentCategory == Category.DEBUG) keys.addAll(List.of("debugDamageReceived", "debugDamageDealt", "debugDamageBreakdown", "debugVillagers", "debugAntiSwap"));

        for (String key : keys) {
            Tag tag = configData != null ? configData.get(key) : null;
            if (tag instanceof ByteTag bt) baselineValues.put(key, ((Byte) bt.getAsByte()) != 0);
            else if (tag instanceof StringTag st) baselineValues.put(key, st.getAsString());
            else if (tag instanceof IntTag it) baselineValues.put(key, it.getAsInt());
            else if (tag instanceof LongTag lt) baselineValues.put(key, lt.getAsLong());
        }
    }

    private boolean getBaselineBoolean(String key) {
        Object val = baselineValues.get(key);
        return val instanceof Boolean && (Boolean) val;
    }

    private String getBaselineString(String key, FieldType type) {
        Object val = baselineValues.get(key);
        if (val instanceof String s) return s;
        if (val instanceof Number n) return String.valueOf(n);
        return "";
    }

    private boolean getPendingBoolean(String key, boolean baseline) {
        Object val = pendingValues.get(key);
        if (val instanceof Boolean b) return b;
        return baseline;
    }

    private String getPendingString(String key, String baseline) {
        Object val = pendingValues.get(key);
        if (val instanceof String s) return s;
        return baseline;
    }

    private static boolean isValidIntegerInput(String s) { return s.matches("\\d*"); }
    private static boolean isValidSecondsInput(String s) { return s.matches("\\d*"); }
    private int parseIntSafe(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
    private long parseLongSafe(String s, long def) { try { return Long.parseLong(s); } catch (Exception e) { return def; } }

    // ================================================================
    // FIELD TYPE
    // ================================================================
    public enum FieldType {
        BOOLEAN("boolean"), STRING("string"), INTEGER("int"), LONG("long"), SECONDS_TO_TICKS("long");
        final String packetName;
        FieldType(String packetName) { this.packetName = packetName; }
        public String packetName() { return packetName; }
        public String format(Object value) {
            return switch (this) {
                case BOOLEAN -> String.valueOf(value instanceof Boolean && (Boolean) value);
                case STRING -> String.valueOf(value);
                case LONG, SECONDS_TO_TICKS -> String.valueOf(value instanceof Number n ? n.longValue() : 0);
                case INTEGER -> String.valueOf(value instanceof Number n ? n.intValue() : 0);
            };
        }
    }

    // ================================================================
    // TOOLTIPS
    // ================================================================
    private static final Map<String, List<String>> tooltips = new HashMap<>() {{
        put("enableAntiSwap", List.of("Previne troca de arma no mesmo tick"));
        put("enableInfiniteTrades", List.of("Permite trocas infinitas com villagers"));
        put("infiniteTradeTag", List.of("Tag NBT para identificar itens com trocas infinitas"));
        put("throttleFarmAndCharm", List.of("Limita tick rate de Cooking Pots e Roasters"));
        put("cookingPotTickRate", List.of("Intervalo em segundos entre ticks do Cooking Pot"));
        put("roasterTickRate", List.of("Intervalo em segundos entre ticks do Roaster"));
        put("throttleVinery", List.of("Limita tick rate de Fermentation Barrels"));
        put("fermentationBarrelTickRate", List.of("Intervalo em segundos entre ticks do Barrel"));
        put("enableEntityCulling", List.of("Não renderiza entidades fora da tela"));
        put("entityCullingDistance", List.of("Distância máxima para culling de entidades"));
        put("cullSuperGlue", List.of("Cull entidades SuperGlue do Create"));
        put("cullBotania", List.of("Cull entidades/efeitos do Botania"));
        put("throttleIdleContraptions", List.of("Limita tick de contraptions AFK do Create"));
        put("contraptionIdleThrottleRate", List.of("Intervalo em segundos para throttle de contraptions"));
        put("fixBackstabbingExploit", List.of("Corrige exploit de backstabbing do Farmer's Delight"));
        put("nerfTurtleMaster", List.of("Reduz eficácia da poção Turtle Master"));
        put("turtleMasterResistance", List.of("Resistência aplicada por Turtle Master normal"));
        put("strongTurtleMasterResistance", List.of("Resistência aplicada por Turtle Master II"));
        put("debugDamageReceived", List.of("Mostra dano recebido no chat"));
        put("debugDamageDealt", List.of("Mostra dano causado no chat"));
        put("debugDamageBreakdown", List.of("Detalhamento de mitigação de dano"));
        put("debugVillagers", List.of("Debug de IA de villagers"));
        put("debugAntiSwap", List.of("Debug de detecção de anti-swap"));
    }};
}
