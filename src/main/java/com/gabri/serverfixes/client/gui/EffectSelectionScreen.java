package com.gabri.serverfixes.client.gui;

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

import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("null")
public class EffectSelectionScreen extends Screen {
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
        this.searchBox = new EditBox(this.font, this.width / 2 - 100, 20, 200, 20, Component.literal("Buscar"));
        this.searchBox.setResponder(this::updateSearch);
        this.searchBox.setFocused(true);
        this.setInitialFocus(this.searchBox);
        this.addRenderableWidget(this.searchBox);

        updateSearch("");

        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    private void updateSearch(String query) {
        if (this.list != null) {
            this.removeWidget(this.list);
        }
        this.list = new EffectList(this.minecraft, this.width, this.height, 50, this.height - 40, 24);
        String lowerQuery = query.toLowerCase();
        for (Map.Entry<net.minecraft.resources.ResourceKey<MobEffect>, MobEffect> entry : ForgeRegistries.MOB_EFFECTS.getEntries()) {
            ResourceLocation id = entry.getKey().location();
            MobEffect effect = entry.getValue();
            Component name = effect.getDisplayName();
            if (id.toString().toLowerCase().contains(lowerQuery) || name.getString().toLowerCase().contains(lowerQuery)) {
                this.list.add(new EffectEntry(id.toString(), effect, name));
            }
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

    class EffectList extends ObjectSelectionList<EffectEntry> {
        public EffectList(Minecraft mc, int width, int height, int y0, int y1, int itemHeight) {
            super(mc, width, height, y0, y1, itemHeight);
        }

        public void add(EffectEntry entry) {
            this.addEntry(entry);
        }
        
        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 140;
        }

        @Override
        public int getRowWidth() {
            return 260;
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
            TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
            graphics.blit(left, top, 0, 18, 18, sprite);
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
