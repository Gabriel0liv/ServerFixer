package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.UpdateServerConfigPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
public class AdminPanelScreen extends Screen {
    private final CompoundTag configData;
    private static final int SIDEBAR_WIDTH = 150;
    private static final int CONTENT_PADDING = 12;
    private static final int ROW_HEIGHT = 32;
    private static final int RESET_BUTTON_WIDTH = 24;
    private static final int RESET_BUTTON_GAP = 6;
    private static final int FIELD_LABEL_MARGIN = 10;
    private static final int FORM_RIGHT_MARGIN = 12;
    private static final int TOGGLE_WIDTH = 60;
    private final List<RowInfo> rowInfos = new ArrayList<>();
    private final Map<String, FieldType> fieldTypes = new LinkedHashMap<>();
    private final Map<String, Object> baselineValues = new LinkedHashMap<>();
    private final Map<String, Object> pendingValues = new LinkedHashMap<>();
    private final List<String> activeCategoryKeys = new ArrayList<>();
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
        int sidebarWidth = SIDEBAR_WIDTH;
        int contentStartX = sidebarWidth + CONTENT_PADDING;
        int contentWidth = this.width - contentStartX - CONTENT_PADDING;
        int y = 8;
        this.addRenderableWidget(Button.builder(Component.literal("§b✎ Editor de Itens"), (btn) -> openItemEditor())
            .bounds(8, y, sidebarWidth - 18, 22).build());
        y += 32;
        for (Category category : Category.values()) {
            if (category == Category.MAIN) continue;
            final Category cat = category;
            String text = cat.title;
            if (text == null) text = "Unknown";
            boolean active = currentCategory == cat;
            Component buttonText = Component.literal((active ? "§6> " : "") + text);
            Button btn = Button.builder(buttonText, (b) -> {
                this.currentCategory = cat;
                this.init();
            }).bounds(8, y, sidebarWidth - 18, 22).build();
            this.addRenderableWidget(btn);
            y += ROW_HEIGHT - 2;
        }

        int midContentX = contentStartX + (contentWidth / 2);

        if (currentCategory == Category.MAIN) {
            initMainPage(midContentX);
        } else {
            initCategoryPage(contentStartX, contentWidth);
        }

        if (currentCategory != Category.MAIN) {
            int saveButtonWidth = Math.min(180, contentWidth - 20);
            int saveX = contentStartX + contentWidth - saveButtonWidth;
            Button saveBtn = Button.builder(Component.literal("§bSalvar alterações"), (btn) -> savePendingChanges())
                .bounds(saveX, this.height - 32, saveButtonWidth, 24)
                .build();
            this.addRenderableWidget(saveBtn);
        }

        // Close Button
        Button closeBtn = Button.builder(Component.literal("§cFechar Menu"), (btn) -> {
            Minecraft client = this.minecraft;
            if (client != null) client.setScreen(null);
        }).bounds(5, this.height - 25, 100, 20).build();
        this.addRenderableWidget(closeBtn);
    }

    private void initMainPage(int midX) {
        Minecraft mc = this.minecraft;
        if (mc == null) return;
        
        int y = 50;
        // Item Editor Button
        Button editorBtn = Button.builder(Component.literal("§b✎ Editor de Itens"), (btn) -> openItemEditor())
            .bounds(midX - 100, y, 200, 20).build();
        this.addRenderableWidget(editorBtn);

        y += 30;
        Button labelBtn = Button.builder(Component.literal("§eAcesse as categorias ao lado"), (btn) -> {})
            .bounds(midX - 100, y, 200, 20).build();
        labelBtn.active = false;
        this.addRenderableWidget(labelBtn);
    }

    private void openItemEditor() {
        if (this.minecraft == null) return;
        LocalPlayer player = this.minecraft.player;
        if (player == null || player.getMainHandItem().isEmpty()) return;
        this.minecraft.setScreen(new ItemEditorScreen(player.getMainHandItem().getOrCreateTag()));
    }

    private void initCategoryPage(int contentStartX, int contentWidth) {
        this.activeCategoryKeys.clear();
        int labelX = contentStartX + FIELD_LABEL_MARGIN;
        int rowRight = contentStartX + contentWidth - FORM_RIGHT_MARGIN;
        int resetX = rowRight - RESET_BUTTON_WIDTH;
        int toggleX = resetX - RESET_BUTTON_GAP - TOGGLE_WIDTH;
        int availableForInput = Math.max(80, toggleX - labelX - RESET_BUTTON_GAP - 10);
        int inputWidth = Math.min(160, availableForInput);
        int inputX = toggleX - RESET_BUTTON_GAP - inputWidth;
        if (inputX < labelX + 60) {
            inputX = labelX + 60;
            inputWidth = Math.max(60, toggleX - RESET_BUTTON_GAP - inputX);
        }
        int y = 50;

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
        EditBox box = new EditBox(this.font, widgetX, y, width, 20, Component.literal(label));
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
        int sidebarWidth = SIDEBAR_WIDTH;
        int contentStartX = sidebarWidth + CONTENT_PADDING;
        int contentWidth = this.width - contentStartX - CONTENT_PADDING;
        int midContentX = contentStartX + (contentWidth / 2);

        GuiLayoutUtils.drawPanel(graphics, 4, 35, sidebarWidth + 6, this.height - 40, 0xC2081116, 0xFF3B4F64);
        GuiLayoutUtils.drawPanel(graphics, contentStartX - 6, 35, contentWidth + 10, this.height - 40, 0xD71C2330, 0xFF50677F);
        GuiLayoutUtils.drawTitleWithUnderline(graphics, this.font, Component.literal("§6§l" + currentCategory.title), midContentX, 15, 0xFFF5F5F5, 0xFF3ACAFF);

        if (currentCategory != Category.MAIN && !rowInfos.isEmpty()) {
            int x = contentStartX + 5;
            int rowWidth = contentWidth - 10;
            for (int i = 0; i < rowInfos.size(); i++) {
                RowInfo row = rowInfos.get(i);
                if (i % 2 == 0) {
                    graphics.fill(x, row.y - 2, x + rowWidth, row.y + ROW_HEIGHT - 4, 0x18FFFFFF);
                }
                graphics.drawString(this.font, row.label, x + 5, row.y + 4, 0xE3E6EE);
            }
        } else {
            graphics.drawCenteredString(this.font, "§7Selecione uma categoria à esquerda para configurar.", midContentX, this.height / 2 + 20, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
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
