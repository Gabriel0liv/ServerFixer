package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.SaveItemEditorPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("null")
public class ItemEditorScreen extends Screen {
    public CompoundTag currentTag;
    private Category currentCategory = Category.MENU;

    public Category getCategory() {
        return this.currentCategory;
    }

    private static final int SIDEBAR_WIDTH = 150;
    private static final int CONTENT_PADDING = 12;
    private static final int ROW_HEIGHT = 26;
    private static final int LIST_START_Y = 70;
    private static final int MAX_VISIBLE_ROWS = 10;

    public enum Category {
        MENU("Editor de Item"),
        ATTR_VANILLA("Atributos Vanilla"),
        ATTR_CURIOS("Atributos Curios"),
        POTIONS("Efeitos de Poção"),
        ON_HIT("Efeitos Ao Atacar (On Hit)"),
        ON_HURT("Efeitos Ao Receber (On Hurt)");

        public final String title;
        Category(String title) { this.title = title; }
    }

    public ItemEditorScreen(CompoundTag itemTag) {
        super(Component.literal("Item Editor"));
        this.currentTag = itemTag.copy();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        int sidebarWidth = SIDEBAR_WIDTH;
        int contentStartX = sidebarWidth + CONTENT_PADDING;
        int contentWidth = this.width - contentStartX - CONTENT_PADDING;
        int contentMidX = contentStartX + (contentWidth / 2);

        // Sidebar Buttons
        int y = 40;
        int buttonWidth = sidebarWidth - 18;
        for (Category cat : Category.values()) {
            boolean active = currentCategory == cat;
            Component title = Component.literal((active ? "§6> " : "") + cat.title);

            this.addRenderableWidget(Button.builder(title, (btn) -> {
                this.currentCategory = cat;
                this.init();
            }).bounds(8, y, buttonWidth, 22).build());
            y += 28;
        }

        if (currentCategory != Category.MENU) {
            initCategoryList(contentStartX, contentWidth);
            int addWidth = 96;
            this.addRenderableWidget(Button.builder(Component.literal("§a+ Adicionar"), (btn) -> {
                if (currentCategory == Category.ATTR_VANILLA || currentCategory == Category.ATTR_CURIOS) {
                    this.minecraft.setScreen(new AddAttributeScreen(this));
                } else {
                    this.minecraft.setScreen(new AddEffectScreen(this, currentCategory));
                }
            }).bounds(contentStartX + contentWidth - addWidth, 42, addWidth, 18).build());
        }

        // Bottom Buttons
        int bottomY = this.height - 30;
        if (currentCategory == Category.MENU) {
            this.addRenderableWidget(Button.builder(Component.literal("Salvar no Item"), (btn) -> {
                NetworkHandler.sendToServer(new SaveItemEditorPacket(this.currentTag));
                this.minecraft.setScreen(null);
            }).bounds(contentMidX - 105, bottomY, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> this.minecraft.setScreen(null))
                .bounds(contentMidX + 5, bottomY, 100, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Voltar"), (btn) -> {
                this.currentCategory = Category.MENU;
                this.init();
            }).bounds(contentMidX - 50, bottomY, 100, 20).build());
        }
        // Duplicate Add button removed as it is now in init()
    }

    // initMenu(int midX) was removed as it is now integrated into init() sidebar logic.

    private void initCategoryList(int contentStartX, int contentWidth) {
        String tagName = getTagName();
        if (tagName == null) return;

        ListTag list;
        if (currentCategory == Category.ON_HIT || currentCategory == Category.ON_HURT) {
            CompoundTag main = this.currentTag.getCompound("SF_ItemEffects");
            list = main.getList(currentCategory == Category.ON_HIT ? "on_hit" : "on_hurt", Tag.TAG_COMPOUND);
        } else {
            list = this.currentTag.getList(tagName, Tag.TAG_COMPOUND);
        }

        int rows = Math.min(list.size(), MAX_VISIBLE_ROWS);
        int baseY = LIST_START_Y;
        int deleteX = contentStartX + contentWidth - 24;
        for (int i = 0; i < rows; i++) {
            final int index = i;
            int yPos = baseY + (i * ROW_HEIGHT);
            this.addRenderableWidget(Button.builder(Component.literal("§cX"), (btn) -> {
                list.remove(index);
                this.init();
            }).bounds(deleteX, yPos + 2, 15, 15).build());
        }
    }

    private String getTagName() {
        switch (currentCategory) {
            case ATTR_VANILLA: return "AttributeModifiers";
            case ATTR_CURIOS: return "CurioAttributeModifiers";
            case POTIONS: return "CustomPotionEffects";
            case ON_HIT: case ON_HURT: return "SF_ItemEffects";
            default: return null;
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        int sidebarWidth = SIDEBAR_WIDTH;
        int contentStartX = sidebarWidth + CONTENT_PADDING;
        int contentWidth = this.width - contentStartX - CONTENT_PADDING;
        int midContentX = contentStartX + (contentWidth / 2);

        GuiLayoutUtils.drawPanel(graphics, 4, 35, sidebarWidth + 6, this.height - 38, 0xBF071014, 0xFF3D5564);
        GuiLayoutUtils.drawPanel(graphics, contentStartX - 6, 35, contentWidth + 10, this.height - 38, 0xD9182432, 0xFF4E6B8D);
        GuiLayoutUtils.drawTitleWithUnderline(graphics, this.font, Component.literal("§6§l" + currentCategory.title), midContentX, 15, 0xFFF5F5F5, 0xFF3ACAFF);

        if (currentCategory != Category.MENU) {
            renderListEntries(graphics, contentStartX, contentWidth);
        } else {
            graphics.drawCenteredString(this.font, "§eEscolha uma categoria para editar este item", midContentX, this.height / 2, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderListEntries(GuiGraphics graphics, int contentStartX, int contentWidth) {
        String tagName = getTagName();
        if (tagName == null) return;

        ListTag list;
        if (currentCategory == Category.ON_HIT || currentCategory == Category.ON_HURT) {
            CompoundTag main = this.currentTag.getCompound("SF_ItemEffects");
            list = main.getList(currentCategory == Category.ON_HIT ? "on_hit" : "on_hurt", Tag.TAG_COMPOUND);
        } else {
            list = this.currentTag.getList(tagName, Tag.TAG_COMPOUND);
        }

        int rows = Math.min(list.size(), MAX_VISIBLE_ROWS);
        int listX = contentStartX + 8;
        int listWidth = Math.max(contentWidth - 16, 160);
        int panelRows = Math.max(rows, 4);
        int panelHeight = Math.min(panelRows * ROW_HEIGHT + 24, this.height - LIST_START_Y - 40);
        GuiLayoutUtils.drawPanel(graphics, listX - 6, LIST_START_Y - 10, listWidth + 12, panelHeight + 16, 0xCC1C2532, 0xFF4E6B8D);
        if (rows == 0) {
            graphics.drawCenteredString(this.font, "§7Nenhum item registrado", listX + listWidth / 2, LIST_START_Y + panelHeight / 2, 0xAAAACCFF);
            return;
        }

        for (int i = 0; i < rows; i++) {
            CompoundTag entry = list.getCompound(i);
            String text = formatEntry(entry);
            int rowY = LIST_START_Y + (i * ROW_HEIGHT);
            if (i % 2 == 0) {
                graphics.fill(listX, rowY - 2, listX + listWidth - 6, rowY + ROW_HEIGHT - 4, 0x17FFFFFF);
            }
            graphics.drawString(this.font, "§7- " + text, listX + 8, rowY + 4, 0x55FF55);
        }
    }

    private String formatEntry(CompoundTag entry) {
        if (currentCategory == Category.ATTR_VANILLA || currentCategory == Category.ATTR_CURIOS) {
            String name = entry.getString("AttributeName").replace("minecraft:", "");
            double amount = entry.getDouble("Amount");
            return name + " (" + amount + ")";
        } else if (currentCategory == Category.POTIONS) {
            String id = entry.getString("Id").replace("minecraft:", "");
            int amp = entry.getInt("Amplifier") + 1;
            return id + " Lvl " + amp;
        } else {
            String id = entry.getString("id").replace("minecraft:", "");
            int amp = entry.getInt("amplifier") + 1;
            int dur = entry.getInt("duration") / 20;
            return id + " Lvl " + amp + " (" + dur + "s)";
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
