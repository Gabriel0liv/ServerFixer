package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("null")
public class SlotSelectionScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> onSelect;
    private SelectableEditBox searchBox;
    private SlotList list;
    private final List<String> slots;

    public SlotSelectionScreen(Screen parent, List<String> slots, Consumer<String> onSelect) {
        super(Component.literal("Selecionar Slot"));
        this.parent = parent;
        this.onSelect = onSelect;
        this.slots = slots;
    }

    @Override
    protected void init() {
        super.init();
        this.searchBox = new SelectableEditBox(this.font, this.width / 2 - 100, 20, 200, 20, Component.literal("Buscar"));
        this.searchBox.setResponder(this::updateSearch);
        this.searchBox.setFocused(true);
        this.setInitialFocus(this.searchBox);
        this.addRenderableWidget(this.searchBox);

        updateSearch("");

        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> this.minecraft.setScreen(this.parent))
            .bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private void updateSearch(String query) {
        if (this.list != null) this.removeWidget(this.list);
        this.list = new SlotList(this.minecraft, this.width, this.height, 50, this.height - 40, 24);
        String lower = query.toLowerCase();
        for (String s : this.slots) {
            if (s.toLowerCase().contains(lower)) this.list.add(new SlotEntry(s));
        }
        this.addRenderableWidget(this.list);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        if (this.list != null) this.list.render(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        graphics.drawString(this.font, "Buscar:", this.width / 2 - 145, 26, 0xAAAAAA);
    }

    class SlotList extends ObjectSelectionList<SlotEntry> {
        public SlotList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
            super(mc, width, height, y0, y1, itemHeight);
        }

        public void add(SlotEntry e) { this.addEntry(e); }

        @Override
        protected int getScrollbarPosition() { return this.width / 2 + 140; }

        @Override
        public int getRowWidth() { return 260; }
    }

    class SlotEntry extends ObjectSelectionList.Entry<SlotEntry> {
        private final String id;

        public SlotEntry(String id) { this.id = id; }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTicks) {
            graphics.drawString(Minecraft.getInstance().font, this.id, left + 4, top + 4, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            SlotSelectionScreen.this.onSelect.accept(this.id);
            SlotSelectionScreen.this.minecraft.setScreen(SlotSelectionScreen.this.parent);
            return true;
        }

        @Override
        public @NotNull Component getNarration() { return Component.literal(this.id); }
    }
}
