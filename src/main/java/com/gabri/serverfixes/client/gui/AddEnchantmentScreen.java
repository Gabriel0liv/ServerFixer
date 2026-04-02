package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@SuppressWarnings("null")
public class AddEnchantmentScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 290;

    private final ItemEditorScreen parent;
    private EditBox searchBox;
    private EnchantmentList list;

    public AddEnchantmentScreen(ItemEditorScreen parent) {
        super(Component.literal("Adicionar Encantamento"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = this.width / 2 - PANEL_WIDTH / 2;
        int panelY = this.height / 2 - PANEL_HEIGHT / 2;

        this.searchBox = new SelectableEditBox(this.font, panelX + 20, panelY + 34, PANEL_WIDTH - 40, 20, Component.literal("Buscar"));
        this.searchBox.setResponder(this::updateSearch);
        this.searchBox.setFocused(true);
        this.setInitialFocus(this.searchBox);
        this.addRenderableWidget(this.searchBox);

        updateSearch("");

        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), btn -> this.minecraft.setScreen(this.parent))
            .bounds(this.width / 2 - 50, panelY + PANEL_HEIGHT - 28, 100, 20)
            .build());
    }

    private void updateSearch(String query) {
        if (this.list != null) {
            this.removeWidget(this.list);
        }

        int panelY = this.height / 2 - PANEL_HEIGHT / 2;
        this.list = new EnchantmentList(this.minecraft, this.width, this.height, panelY + 62, panelY + PANEL_HEIGHT - 36, 24);

        String lowerQuery = query.toLowerCase();
        List<EnchantmentEntry> entries = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Enchantment>, Enchantment> entry : ForgeRegistries.ENCHANTMENTS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            Enchantment enchantment = entry.getValue();
            Component name = Component.translatable(enchantment.getDescriptionId());
            if (id.toString().toLowerCase().contains(lowerQuery) || name.getString().toLowerCase().contains(lowerQuery)) {
                entries.add(new EnchantmentEntry(id.toString(), name));
            }
        }

        entries.sort(Comparator.comparing((EnchantmentEntry e) -> e.name.getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(e -> e.id, String.CASE_INSENSITIVE_ORDER));

        for (EnchantmentEntry entry : entries) {
            this.list.add(entry);
        }

        this.addRenderableWidget(this.list);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int panelX = this.width / 2 - PANEL_WIDTH / 2;
        int panelY = this.height / 2 - PANEL_HEIGHT / 2;
        GuiLayoutUtils.drawPanel(graphics, panelX - 6, panelY - 6, PANEL_WIDTH + 12, PANEL_HEIGHT + 12, 0xAA050B12, 0xFF2C4B68);
        GuiLayoutUtils.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xDD0F1925, 0xFF4E6D86);
        GuiLayoutUtils.drawTitleWithUnderline(graphics, this.font, this.title, this.width / 2, panelY + 12, 0xFFF1F1F1, 0xFF3ACAFF);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Buscar encantamento", panelX + 20, panelY + 24, 0xFFCCD9F1);

        if (this.list != null) {
            this.list.render(graphics, mouseX, mouseY, partialTicks);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (this.list != null && this.list.entryCount() == 0) {
            graphics.drawCenteredString(this.font, "§7Nenhum encantamento encontrado", this.width / 2, panelY + PANEL_HEIGHT / 2, 0xAAAACCFF);
        }
    }

    class EnchantmentList extends ObjectSelectionList<EnchantmentEntry> {
        public EnchantmentList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
            super(mc, width, height, y0, y1, itemHeight);
        }

        public void add(EnchantmentEntry entry) {
            this.addEntry(entry);
        }

        public int entryCount() {
            return this.getItemCount();
        }

        @Override
        protected int getScrollbarPosition() {
            return (this.width / 2) + 160;
        }

        @Override
        public int getRowWidth() {
            return 320;
        }
    }

    class EnchantmentEntry extends ObjectSelectionList.Entry<EnchantmentEntry> {
        private final String id;
        private final Component name;

        public EnchantmentEntry(String id, Component name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTicks) {
            if (isMouseOver) {
                graphics.fill(left - 4, top - 1, left + width - 8, top + height - 1, 0x1FFFFFFF);
            }
            graphics.drawString(Minecraft.getInstance().font, this.name, left + 4, top + 2, 0xFFFFFF);
            graphics.drawString(Minecraft.getInstance().font, this.id, left + 4, top + 12, 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            AddEnchantmentScreen.this.parent.addEnchantment(this.id);
            AddEnchantmentScreen.this.minecraft.setScreen(AddEnchantmentScreen.this.parent);
            return true;
        }

        @Override
        public @NotNull Component getNarration() {
            return this.name;
        }
    }
}
