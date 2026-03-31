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
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("all")
public class AdminPanelScreen extends Screen {
    private final CompoundTag configData;
    private static final int SIDEBAR_WIDTH = 150;
    private static final int CONTENT_PADDING = 12;
    private static final int ROW_HEIGHT = 32;
    private final List<RowInfo> rowInfos = new ArrayList<>();
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

        // Sidebar Buttons
        int y = 40;
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
        Button editorBtn = Button.builder(Component.literal("§b✎ Editor de Itens"), (btn) -> {
            LocalPlayer player = mc.player;
            if (player != null && !player.getMainHandItem().isEmpty()) {
                mc.setScreen(new ItemEditorScreen(player.getMainHandItem().getOrCreateTag()));
            }
        }).bounds(midX - 100, y, 200, 20).build();
        this.addRenderableWidget(editorBtn);

        y += 30;
        Button labelBtn = Button.builder(Component.literal("§eAcesse as categorias ao lado"), (btn) -> {})
            .bounds(midX - 100, y, 200, 20).build();
        labelBtn.active = false;
        this.addRenderableWidget(labelBtn);
    }

    private void initCategoryPage(int contentStartX, int contentWidth) {
        int labelX = contentStartX + 10;
        int inputWidth = Math.min(140, contentWidth / 2);
        int toggleX = contentStartX + contentWidth - 70;
        int inputX = toggleX - inputWidth - 20;
        if (inputX < labelX + 120) inputX = labelX + 120;
        int y = 50;

        switch (currentCategory) {
            case SERVER:
                addToggle("enableAntiSwap", "Anti-Swap Exploit", y, toggleX);
                y += ROW_HEIGHT;
                addInput("antiSwapCooldown", "Cooldown Anti-Swap (ms)", y, inputX, inputWidth);
                y += ROW_HEIGHT;
                addToggle("enableInfiniteTrades", "Trocas Infinitas", y, toggleX);
                y += ROW_HEIGHT;
                addInput("infiniteTradeTag", "Tag p/ Trocas Infinitas", y, inputX, inputWidth);
                break;
            case THROTTLE:
                addToggle("throttleFarmAndCharm", "Throttle Farm&Charm", y, toggleX); y += ROW_HEIGHT;
                addInput("cookingPotTickRate", "Rate Cooking Pot (Ticks)", y, inputX, inputWidth); y += ROW_HEIGHT;
                addInput("roasterTickRate", "Rate Roaster (Ticks)", y, inputX, inputWidth); y += ROW_HEIGHT;
                addToggle("throttleVinery", "Throttle Vinery", y, toggleX); y += ROW_HEIGHT;
                addInput("fermentationBarrelTickRate", "Rate Barrel (Ticks)", y, inputX, inputWidth);
                break;
            case FIXES:
                addToggle("fixBackstabbingExploit", "Fix Backstabbing", y, toggleX); y += ROW_HEIGHT;
                addToggle("nerfTurtleMaster", "Nerf Turtle Master", y, toggleX); y += ROW_HEIGHT;
                addInput("turtleMasterResistance", "Res. Turtle (Padrão)", y, inputX, inputWidth); y += ROW_HEIGHT;
                addInput("strongTurtleMasterResistance", "Res. Turtle (Forte)", y, inputX, inputWidth);
                break;
            case DEBUG:
                addToggle("debugDamageReceived", "Debug Dano Recebido", y, toggleX); y += ROW_HEIGHT;
                addToggle("debugDamageDealt", "Debug Dano Causado", y, toggleX); y += ROW_HEIGHT;
                addToggle("debugDamageBreakdown", "Detalhamento de Dano", y, toggleX); y += ROW_HEIGHT;
                addToggle("debugVillagers", "Debug Villagers", y, toggleX); y += ROW_HEIGHT;
                addToggle("debugAntiSwap", "Debug Anti-Swap", y, toggleX);
                break;
            default: break;
        }
    }

    private void addToggle(String key, String label, int y, int widgetX) {
        CompoundTag data = this.configData;
        if (data == null) return;
        registerRow(label, y);
        boolean current = data.getBoolean(key);
        this.addRenderableWidget(CycleButton.onOffBuilder(current)
            .create(widgetX, y, 60, 20, Component.empty(), (btn, val) -> {
                data.putBoolean(key, val);
                NetworkHandler.sendToServer(new UpdateServerConfigPacket(key, String.valueOf(val), "bool"));
            }));
    }

    private void addInput(String key, String label, int y, int widgetX, int width) {
        if (this.font == null || this.configData == null) return;
        registerRow(label, y);
        EditBox box = new EditBox(this.font, widgetX, y, width, 20, Component.literal(label));
        Object val = this.configData.get(key);
        box.setValue(val != null ? String.valueOf(val) : "");
        box.setResponder(newValue -> {
            if (this.configData != null) {
                this.configData.put(key, net.minecraft.nbt.StringTag.valueOf(newValue));
                NetworkHandler.sendToServer(new UpdateServerConfigPacket(key, newValue, "string"));
            }
        });
        this.addRenderableWidget(box);
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

    private static final class RowInfo {
        private final String label;
        private final int y;

        private RowInfo(String label, int y) {
            this.label = label;
            this.y = y;
        }
    }
}
