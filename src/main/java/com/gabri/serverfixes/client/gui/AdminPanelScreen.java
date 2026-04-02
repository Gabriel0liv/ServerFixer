package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.AbstractEditorScreen;
import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.UpdateServerConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
    private final List<RowInfo> rowInfos = new ArrayList<>();
    private final Map<String, FieldType> fieldTypes = new LinkedHashMap<>();
    private final Map<String, Object> baselineValues = new LinkedHashMap<>();
    private final Map<String, Object> pendingValues = new LinkedHashMap<>();
    private final List<String> activeCategoryKeys = new ArrayList<>();
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int headerY;
    private int barY;
    private int sidebarX;
    private int sidebarY;
    private int sidebarW;
    private int sidebarH;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private int footerY;
    private Category currentCategory = Category.MAIN;

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
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null) return;
        this.clearWidgets();
        this.rowInfos.clear();
        computeLayout();
        buildSidebar();
        buildTopBar();

        if (currentCategory == Category.MAIN) {
            initMainPage(this.contentX, this.contentY, this.contentW, this.contentH);
        } else {
            initCategoryPage(this.contentX, this.contentW, this.contentY + 12);
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

        Button saveBtn = Button.builder(Component.literal("§bSalvar"), (btn) -> savePendingChanges())
            .bounds(rightX, this.footerY, FOOTER_BUTTON_WIDTH, 20)
            .build();
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
                this.init();
            }).bounds(buttonX, y, buttonW, CATEGORY_BUTTON_HEIGHT).build());
            y += CATEGORY_BUTTON_HEIGHT + CATEGORY_BUTTON_GAP;
        }
    }

    private void buildTopBar() {
        int barButtonH = 16;
        int leftX = this.contentX + 2;
        this.addRenderableWidget(Button.builder(Component.literal("Item Editor"), (btn) -> openItemEditor())
            .bounds(leftX, this.barY, 96, barButtonH).build());
    }

    private void initMainPage(int contentStartX, int contentStartY, int contentWidth, int contentHeight) {
        // Home usa apenas o painel de conteúdo único para evitar redundância visual.
    }

    private void openItemEditor() {
        if (this.minecraft == null) return;
        LocalPlayer player = this.minecraft.player;
        if (player == null || player.getMainHandItem().isEmpty()) return;
        this.minecraft.setScreen(new ItemEditorScreen(player.getMainHandItem().getOrCreateTag()));
    }

    private void initCategoryPage(int contentStartX, int contentWidth, int startY) {
        this.activeCategoryKeys.clear();
        int labelX = contentStartX + FIELD_LABEL_MARGIN;
        int rowRight = contentStartX + contentWidth - FORM_RIGHT_MARGIN;
        int resetX = rowRight - RESET_BUTTON_WIDTH;
        int toggleX = resetX - RESET_BUTTON_GAP - TOGGLE_WIDTH;
        int inputRight = resetX - RESET_BUTTON_GAP;
        int minInputX = labelX + 60;
        int availableForInput = Math.max(60, inputRight - minInputX);
        int inputWidth = Math.min(160, availableForInput);
        int inputX = inputRight - inputWidth;
        int y = startY;

        switch (currentCategory) {
            case SERVER:
                addToggle("enableAntiSwap", "Anti-Swap Exploit", y, toggleX, resetX);
                y += ROW_HEIGHT;
                addInput("antiSwapCooldown", "Cooldown Anti-Swap (ms)", FieldType.LONG, y, inputX, inputWidth, resetX);
                y += ROW_HEIGHT;
                addToggle("enableInfiniteTrades", "Trocas Infinitas", y, toggleX, resetX);
                y += ROW_HEIGHT;
                addInput("infiniteTradeTag", "Tag p/ Trocas Infinitas", FieldType.STRING, y, inputX, inputWidth, resetX);
                break;
            case THROTTLE:
                addToggle("throttleFarmAndCharm", "Throttle Farm&Charm", y, toggleX, resetX); y += ROW_HEIGHT;
                addInput("cookingPotTickRate", "Rate Cooking Pot (Segundos)", FieldType.SECONDS_TO_TICKS, y, inputX, inputWidth, resetX); y += ROW_HEIGHT;
                addInput("roasterTickRate", "Rate Roaster (Segundos)", FieldType.SECONDS_TO_TICKS, y, inputX, inputWidth, resetX); y += ROW_HEIGHT;
                addToggle("throttleVinery", "Throttle Vinery", y, toggleX, resetX); y += ROW_HEIGHT;
                addInput("fermentationBarrelTickRate", "Rate Barrel (Segundos)", FieldType.SECONDS_TO_TICKS, y, inputX, inputWidth, resetX);
                break;
            case FIXES:
                addToggle("fixBackstabbingExploit", "Fix Backstabbing", y, toggleX, resetX); y += ROW_HEIGHT;
                addToggle("nerfTurtleMaster", "Nerf Turtle Master", y, toggleX, resetX); y += ROW_HEIGHT;
                addInput("turtleMasterResistance", "Res. Turtle (Padrão)", FieldType.INTEGER, y, inputX, inputWidth, resetX); y += ROW_HEIGHT;
                addInput("strongTurtleMasterResistance", "Res. Turtle (Forte)", FieldType.INTEGER, y, inputX, inputWidth, resetX);
                break;
            case DEBUG:
                addToggle("debugDamageReceived", "Debug Dano Recebido", y, toggleX, resetX); y += ROW_HEIGHT;
                addToggle("debugDamageDealt", "Debug Dano Causado", y, toggleX, resetX); y += ROW_HEIGHT;
                addToggle("debugDamageBreakdown", "Detalhamento de Dano", y, toggleX, resetX); y += ROW_HEIGHT;
                addToggle("debugVillagers", "Debug Villagers", y, toggleX, resetX); y += ROW_HEIGHT;
                addToggle("debugAntiSwap", "Debug Anti-Swap", y, toggleX, resetX);
                break;
            default: break;
        }
    }

    private void addToggle(String key, String label, int y, int toggleX, int resetX) {
        registerRow(label, y);
        activeCategoryKeys.add(key);
        FieldType type = FieldType.BOOLEAN;
        fieldTypes.put(key, type);
        boolean baseline = getBaselineBoolean(key);
        boolean pending = getPendingBoolean(key, baseline);
        this.addRenderableWidget(CycleButton.builder((Boolean value) -> value ? Component.literal("ON") : Component.literal("OFF"))
            .withValues(true, false)
            .withInitialValue(pending)
            .displayOnlyValue()
            .create(toggleX, y, TOGGLE_WIDTH, 20, Component.empty(), (btn, val) -> pendingValues.put(key, val)));
        this.addResetButton(resetX, y, key);
    }

    private void addInput(String key, String label, FieldType type, int y, int widgetX, int width, int resetX) {
        if (this.font == null) return;
        registerRow(label, y);
        activeCategoryKeys.add(key);
        fieldTypes.put(key, type);
        String baseline = getBaselineString(key, type);
        String pending = getPendingString(key, baseline);
        EditBox box = new SelectableEditBox(this.font, widgetX, y, width, 20, Component.literal(label));
        if (type == FieldType.INTEGER || type == FieldType.LONG) {
            box.setFilter(AdminPanelScreen::isValidIntegerInput);
        } else if (type == FieldType.SECONDS_TO_TICKS) {
            box.setFilter(AdminPanelScreen::isValidSecondsInput);
        }
        box.setValue(pending);
        box.setResponder(newValue -> pendingValues.put(key, newValue));
        this.addRenderableWidget(box);
        this.addResetButton(resetX, y, key);
    }

    private void addResetButton(int x, int y, String key) {
        this.addRenderableWidget(Button.builder(Component.literal("↺"), (btn) -> {
            Object baseline = baselineValues.get(key);
            if (baseline != null) {
                pendingValues.put(key, baseline);
            }
            this.init();
        }).bounds(x, y, RESET_BUTTON_WIDTH, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        renderEditorFrame(graphics, Component.literal("§6§l" + currentCategory.title),
            this.frameX, this.frameY, this.frameW, this.frameH,
            this.headerY, this.barY, this.contentY, this.footerY);

        GuiLayoutUtils.drawPanel(graphics, this.sidebarX, this.sidebarY, this.sidebarW, this.sidebarH, 0xBF071014, 0xFF3D5564);
        GuiLayoutUtils.drawPanel(graphics, this.contentX, this.contentY, this.contentW, this.contentH, 0xD9182432, 0xFF4E6B8D);

        if (currentCategory == Category.MAIN) {
            renderMainOverview(graphics);
        } else if (!rowInfos.isEmpty()) {
            int x = this.contentX + 8;
            int rowWidth = this.contentW - 16;
            for (int i = 0; i < rowInfos.size(); i++) {
                RowInfo row = rowInfos.get(i);
                if (i % 2 == 0) {
                    graphics.fill(x, row.y - 2, x + rowWidth, row.y + ROW_HEIGHT - 4, 0x18FFFFFF);
                }
                graphics.drawString(this.font, row.label, x + 5, row.y + 4, 0xE3E6EE);
            }
        } else {
            graphics.drawCenteredString(this.font, "§7Selecione uma categoria à esquerda para configurar.", this.contentX + this.contentW / 2, this.contentY + this.contentH / 2, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
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
        graphics.drawString(this.font, "§7Use o botão da barra superior para abrir o Item Editor.", textX, y, 0xFFBECBDD);
        y += 16;
        graphics.drawString(this.font, "§7A tela segue o formato do IBE: sidebar + area unica de conteudo.", textX, y, 0xFFBECBDD);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void registerRow(String label, int y) {
        this.rowInfos.add(new RowInfo(label, y));
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
            if (type == FieldType.BOOLEAN) {
                configData.putBoolean(key, pending instanceof Boolean && (Boolean) pending);
            } else if (type == FieldType.STRING) {
                configData.putString(key, formatted);
            } else if (type == FieldType.LONG) {
                configData.putLong(key, parseLongSafe(formatted, 0L));
            } else {
                configData.putInt(key, parseIntSafe(formatted, 0));
            }
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
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static int parseIntSafe(String value, int fallback) {
        try {
            return Integer.parseInt(stripWrappingQuotes(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseLongSafe(String value, long fallback) {
        try {
            return Long.parseLong(stripWrappingQuotes(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double parseDoubleSafe(String value, double fallback) {
        try {
            return Double.parseDouble(stripWrappingQuotes(value).replace(',', '.'));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean isValidIntegerInput(String value) {
        if (value == null || value.isEmpty() || "-".equals(value)) return true;
        try {
            Long.parseLong(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isValidSecondsInput(String value) {
        if (value == null || value.isEmpty()) return true;
        String normalized = value.replace(',', '.');
        if (".".equals(normalized)) return true;
        try {
            double seconds = Double.parseDouble(normalized);
            return seconds >= 0.05D;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int secondsToTicks(String value) {
        double seconds = parseDoubleSafe(value, 1.0D);
        if (seconds < 0.05D) seconds = 0.05D;
        int ticks = (int) Math.round(seconds * 20.0D);
        return Math.max(1, ticks);
    }

    private static String ticksToSecondsInput(int ticks) {
        double seconds = Math.max(1, ticks) / 20.0D;
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0000001D) {
            return String.valueOf((int) Math.rint(seconds));
        }
        String value = String.format(java.util.Locale.US, "%.2f", seconds);
        while (value.endsWith("0")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static final class RowInfo {
        private final String label;
        private final int y;

        private RowInfo(String label, int y) {
            this.label = label;
            this.y = y;
        }
    }

    private enum FieldType {
        BOOLEAN("bool") {
            @Override
            String format(Object value) {
                return String.valueOf(value instanceof Boolean ? value : false);
            }

            @Override
            String readInputValue(CompoundTag source, String key) {
                return String.valueOf(source != null && source.getBoolean(key));
            }
        },
        STRING("string") {
            @Override
            String format(Object value) {
                return stripWrappingQuotes(value != null ? value.toString() : "");
            }

            @Override
            String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "";
                if (source.contains(key, Tag.TAG_STRING)) {
                    return stripWrappingQuotes(source.getString(key));
                }
                Tag tag = source.get(key);
                return tag != null ? stripWrappingQuotes(tag.getAsString()) : "";
            }
        },
        INTEGER("int") {
            @Override
            String format(Object value) {
                return String.valueOf(parseIntSafe(value != null ? value.toString() : "", 0));
            }

            @Override
            String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "0";
                if (source.contains(key, Tag.TAG_INT) || source.contains(key, Tag.TAG_SHORT) || source.contains(key, Tag.TAG_BYTE) || source.contains(key, Tag.TAG_LONG)) {
                    return String.valueOf(source.getInt(key));
                }
                Tag tag = source.get(key);
                return String.valueOf(parseIntSafe(tag != null ? tag.getAsString() : "", 0));
            }
        },
        LONG("long") {
            @Override
            String format(Object value) {
                return String.valueOf(parseLongSafe(value != null ? value.toString() : "", 0L));
            }

            @Override
            String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "0";
                if (source.contains(key, Tag.TAG_LONG) || source.contains(key, Tag.TAG_INT) || source.contains(key, Tag.TAG_SHORT) || source.contains(key, Tag.TAG_BYTE)) {
                    return String.valueOf(source.getLong(key));
                }
                Tag tag = source.get(key);
                return String.valueOf(parseLongSafe(tag != null ? tag.getAsString() : "", 0L));
            }
        },
        SECONDS_TO_TICKS("int") {
            @Override
            String format(Object value) {
                return String.valueOf(secondsToTicks(value != null ? value.toString() : ""));
            }

            @Override
            String readInputValue(CompoundTag source, String key) {
                if (source == null || !source.contains(key)) return "1";
                int ticks;
                if (source.contains(key, Tag.TAG_INT) || source.contains(key, Tag.TAG_SHORT) || source.contains(key, Tag.TAG_BYTE) || source.contains(key, Tag.TAG_LONG)) {
                    ticks = source.getInt(key);
                } else {
                    Tag tag = source.get(key);
                    ticks = parseIntSafe(tag != null ? tag.getAsString() : "", 20);
                }
                return ticksToSecondsInput(ticks);
            }
        };

        private final String packetName;

        FieldType(String packetName) {
            this.packetName = packetName;
        }

        abstract String format(Object value);

        String readInputValue(CompoundTag source, String key) {
            if (source == null || !source.contains(key)) return "";
            Tag tag = source.get(key);
            return tag != null ? stripWrappingQuotes(tag.getAsString()) : "";
        }

        public String packetName() {
            return packetName;
        }
    }
}
