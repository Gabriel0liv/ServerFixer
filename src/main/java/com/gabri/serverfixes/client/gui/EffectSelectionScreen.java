package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("null")
public class EffectSelectionScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 290;
    private final Screen parent;
    private final Consumer<String> onSelect;
    private EditBox searchBox;
    private EffectList list;

    public EffectSelectionScreen(Screen parent, Consumer<String> onSelect) {
        super(Component.literal("Selecionar Efeito"));
        this.parent = parent;
        this.onSelect = onSelect;
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

        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 50, panelY + PANEL_HEIGHT - 28, 100, 20).build());
    }

    private void updateSearch(String query) {
        if (this.list != null) {
            this.removeWidget(this.list);
        }

        int panelY = this.height / 2 - PANEL_HEIGHT / 2;
        this.list = new EffectList(this.minecraft, this.width, this.height, panelY + 62, panelY + PANEL_HEIGHT - 36, 24);
        String lowerQuery = query.toLowerCase();

        List<EffectEntry> entries = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<MobEffect>, MobEffect> entry : ForgeRegistries.MOB_EFFECTS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            MobEffect effect = entry.getValue();
            Component name = effect.getDisplayName();
            if (id.toString().toLowerCase().contains(lowerQuery) || name.getString().toLowerCase().contains(lowerQuery)) {
                entries.add(new EffectEntry(id.toString(), effect, name));
            }
        }

        entries.sort(Comparator.comparing((EffectEntry effect) -> effect.name.getString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(effect -> effect.id, String.CASE_INSENSITIVE_ORDER));
        for (EffectEntry effectEntry : entries) {
            this.list.add(effectEntry);
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
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Buscar efeito", panelX + 20, panelY + 24, 0xFFCCD9F1);

        if (this.list != null) this.list.render(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);

        if (this.list != null && this.list.entryCount() == 0) {
            graphics.drawCenteredString(this.font, "§7Nenhum efeito encontrado", this.width / 2, panelY + PANEL_HEIGHT / 2, 0xAAAACCFF);
        }
    }

    class EffectList extends ObjectSelectionList<EffectEntry> {
        public EffectList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
            super(mc, width, height, y0, y1, itemHeight);
        }

        public void add(EffectEntry entry) {
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

    class EffectEntry extends ObjectSelectionList.Entry<EffectEntry> {
        private final String id;
        private final MobEffect effect;
        private final Component name;

        public EffectEntry(String id, MobEffect effect, Component name) {
            this.id = id;
            this.effect = effect;
            this.name = name;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isMouseOver, float partialTicks) {
            if (isMouseOver) {
                graphics.fill(left - 4, top - 1, left + width - 8, top + height - 1, 0x1FFFFFFF);
            }
            TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
            graphics.blit(left, top + 1, 0, 18, 18, sprite);
            graphics.drawString(Minecraft.getInstance().font, this.name, left + 22, top + 2, 0xFFFFFF);
            graphics.drawString(Minecraft.getInstance().font, this.id, left + 22, top + 12, 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            EffectSelectionScreen.this.onSelect.accept(this.id);
            EffectSelectionScreen.this.minecraft.setScreen(EffectSelectionScreen.this.parent);
            return true;
        }

        @Override
        public @NotNull Component getNarration() {
            return this.name;
        }
    }
}
