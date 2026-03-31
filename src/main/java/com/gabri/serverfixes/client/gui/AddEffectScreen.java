package com.gabri.serverfixes.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("null")
public class AddEffectScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 260;
    private static final int FIELD_SPACING = 34;
    private static final int BUTTON_WIDTH = 110;

    private final ItemEditorScreen parent;
    private final ItemEditorScreen.Category category;
    
    private EditBox idBox;
    private EditBox durationBox;
    private EditBox amplifierBox;
    private EditBox chanceBox;
    private boolean self = false;

    private String pendingId = "";
    private String pendingDur = "200";
    private String pendingAmp = "0";
    private String pendingChance = "1.0";

    public AddEffectScreen(ItemEditorScreen parent, ItemEditorScreen.Category category) {
        super(Component.literal("Adicionar Efeito"));
        this.parent = parent;
        this.category = category;
        if (category == ItemEditorScreen.Category.ON_HURT) this.self = true;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        int midX = this.width / 2;
        int panelX = midX - PANEL_WIDTH / 2;
        int panelY = 30;
        int fieldX = panelX + 20;
        int fieldWidth = PANEL_WIDTH - 40;
        int currentY = panelY + 40;

        // ID Row
        this.idBox = new EditBox(this.font, fieldX, currentY, fieldWidth - 40, 20, Component.literal("ID"));
        this.idBox.setMaxLength(64);
        this.idBox.setValue(this.pendingId);
        this.addRenderableWidget(this.idBox);

        this.addRenderableWidget(Button.builder(Component.literal("§6..."), (btn) -> {
            this.updatePending();
            this.minecraft.setScreen(new EffectSelectionScreen(this, (id) -> {
                this.pendingId = id;
                this.init();
            }));
        }).bounds(fieldX + fieldWidth - 30, currentY, 30, 20).build());

        currentY += FIELD_SPACING;

        // Duration Row
        this.durationBox = new EditBox(this.font, fieldX, currentY, fieldWidth, 20, Component.literal("Duração"));
        this.durationBox.setValue(this.pendingDur);
        this.addRenderableWidget(this.durationBox);

        currentY += FIELD_SPACING;

        // Amplifier Row
        this.amplifierBox = new EditBox(this.font, fieldX, currentY, fieldWidth, 20, Component.literal("Nível"));
        this.amplifierBox.setValue(this.pendingAmp);
        this.addRenderableWidget(this.amplifierBox);

        if (category == ItemEditorScreen.Category.ON_HIT || category == ItemEditorScreen.Category.ON_HURT) {
            // Chance Row
            currentY += FIELD_SPACING;
            this.chanceBox = new EditBox(this.font, fieldX, currentY, fieldWidth, 20, Component.literal("Chance"));
            this.chanceBox.setValue(this.pendingChance);
            this.addRenderableWidget(this.chanceBox);

            // Self/Target Row
            currentY += FIELD_SPACING;
            this.addRenderableWidget(CycleButton.builder((Boolean b) -> b ? Component.literal("SELF") : Component.literal("TARGET"))
                .withValues(true, false)
                .withInitialValue(this.self)
                .displayOnlyValue()
                .create(fieldX, currentY, fieldWidth, 20, Component.literal("Alvo"), (btn, val) -> this.self = val));
        }

        int buttonY = panelY + PANEL_HEIGHT - 40;
        this.addRenderableWidget(Button.builder(Component.literal("Adicionar"), (btn) -> addEffect())
            .bounds(midX - BUTTON_WIDTH - 10, buttonY, BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> this.minecraft.setScreen(this.parent))
            .bounds(midX + 10, buttonY, BUTTON_WIDTH, 20).build());
    }

    private void updatePending() {
        this.pendingId = this.idBox.getValue();
        this.pendingDur = this.durationBox.getValue();
        this.pendingAmp = this.amplifierBox.getValue();
        if (this.chanceBox != null) this.pendingChance = this.chanceBox.getValue();
    }

    private void addEffect() {
        try {
            String id = idBox.getValue();
            if (!id.contains(":")) id = "minecraft:" + id;
            int dur = Integer.parseInt(durationBox.getValue());
            int amp = Integer.parseInt(amplifierBox.getValue());

            if (category == ItemEditorScreen.Category.POTIONS) {
                ListTag list = parent.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
                CompoundTag entry = new CompoundTag();
                entry.putString("Id", id);
                entry.putInt("Duration", dur);
                entry.putInt("Amplifier", amp);
                list.add(entry);
                parent.currentTag.put("CustomPotionEffects", list);
            } else {
                CompoundTag sfTag = parent.currentTag.getCompound("SF_ItemEffects");
                String listKey = category == ItemEditorScreen.Category.ON_HIT ? "on_hit" : "on_hurt";
                ListTag list = sfTag.getList(listKey, Tag.TAG_COMPOUND);
                
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id);
                entry.putInt("duration", dur);
                entry.putInt("amplifier", amp);
                entry.putDouble("chance", Double.parseDouble(chanceBox.getValue()));
                entry.putBoolean("self", self);
                
                list.add(entry);
                sfTag.put(listKey, list);
                parent.currentTag.put("SF_ItemEffects", sfTag);
            }
            this.minecraft.setScreen(this.parent);
        } catch (Exception ignored) {}
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        int midX = this.width / 2;
        int panelX = midX - PANEL_WIDTH / 2;
        int panelY = 30;
        GuiLayoutUtils.drawPanel(graphics, panelX - 6, panelY - 6, PANEL_WIDTH + 12, PANEL_HEIGHT + 12, 0xAA050B12, 0xFF2C4B68);
        GuiLayoutUtils.drawPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 0xDD0F1925, 0xFF4E6D86);
        GuiLayoutUtils.drawTitleWithUnderline(graphics, this.font, this.title, midX, panelY + 16, 0xFFF1F1F1, 0xFF3ACAFF);

        int labelX = panelX + 18;
        int baseY = panelY + 40;
        int spacing = FIELD_SPACING;
        graphics.drawString(this.font, "ID do efeito", labelX, baseY - 12, 0xFFCCD9F1);
        graphics.drawString(this.font, "Duração (ticks)", labelX, baseY + spacing - 12, 0xFFCCD9F1);
        graphics.drawString(this.font, "Nível (0 = Lvl 1)", labelX, baseY + spacing * 2 - 12, 0xFFCCD9F1);
        if (category == ItemEditorScreen.Category.ON_HIT || category == ItemEditorScreen.Category.ON_HURT) {
            graphics.drawString(this.font, "Chance de aplicar", labelX, baseY + spacing * 3 - 12, 0xFFCCD9F1);
            graphics.drawString(this.font, "Alvo do efeito", labelX, baseY + spacing * 4 - 12, 0xFFCCD9F1);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
