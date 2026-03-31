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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("null")
public class AddEffectScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 260;
    private static final int FIELD_SPACING = 34;
    private static final int BUTTON_WIDTH = 110;
    private static final int ACTION_BUTTON_WIDTH = 26;
    private static final int RESET_BUTTON_WIDTH = 24;
    private static final int RESET_BUTTON_GAP = 6;

    private final ItemEditorScreen parent;
    private final ItemEditorScreen.Category category;
    
    private EditBox idBox;
    private EditBox durationBox;
    private EditBox amplifierBox;
    private EditBox chanceBox;
    private boolean self = false;

    private String pendingId = "";
    private String pendingDur = "10";
    private String pendingAmp = "0";
    private String pendingChance = "1.0";
    private String originalId = "";
    private String originalDur = "10";
    private String originalAmp = "0";
    private String originalChance = "1.0";
    private boolean originalSelf = false;
    
    private boolean isValidSecondsInput(String value) {
        if (value == null || value.isEmpty()) return true;
        String normalized = value.replace(',', '.');
        if (normalized.equals(".")) return true;
        try {
            double parsed = Double.parseDouble(normalized);
            return parsed >= 0.05D;
        } catch (Exception ignored) {
            return false;
        }
    }
    
    private static String stripWrappingQuotes(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
    
    private static int parseIntInput(String value, int fallback) {
        try {
            return Integer.parseInt(stripWrappingQuotes(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
    
    private static double parseDoubleInput(String value, double fallback) {
        try {
            return Double.parseDouble(stripWrappingQuotes(value).replace(',', '.'));
        } catch (Exception ignored) {
            return fallback;
        }
    }
    
    private static int parseDurationSecondsToTicks(String secondsInput) {
        double seconds = parseDoubleInput(secondsInput, 1.0D);
        if (seconds < 0.05D) {
            seconds = 0.05D;
        }
        return Math.max(1, (int) Math.round(seconds * 20.0D));
    }

    private static String normalizeEffectId(String rawId) {
        String id = stripWrappingQuotes(rawId);
        if (!id.contains(":")) id = "minecraft:" + id;
        return id;
    }

    private static void writePotionEffectId(CompoundTag entry, String normalizedId) {
        ResourceLocation effectRl = ResourceLocation.tryParse(normalizedId);
        if (effectRl != null) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectRl);
            if (effect != null) {
                entry.putInt("Id", MobEffect.getId(effect));
                entry.putString("IdString", effectRl.toString());
                return;
            }
        }
        entry.putString("Id", normalizedId);
    }

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
        int formRight = panelX + PANEL_WIDTH - 20;
        int fieldX = panelX + 24;
        int baseFieldWidth = Math.max(140, formRight - fieldX - RESET_BUTTON_WIDTH - 12);
        int idFieldWidth = Math.max(160, baseFieldWidth - ACTION_BUTTON_WIDTH - RESET_BUTTON_GAP);
        int currentY = panelY + 40;

        this.originalId = this.pendingId;
        this.originalDur = this.pendingDur;
        this.originalAmp = this.pendingAmp;
        this.originalChance = this.pendingChance;
        this.originalSelf = this.self;

        this.idBox = new EditBox(this.font, fieldX, currentY, idFieldWidth, 20, Component.literal("ID"));
        this.idBox.setMaxLength(64);
        this.idBox.setValue(this.pendingId);
        this.idBox.setResponder(value -> this.pendingId = value);
        this.addRenderableWidget(this.idBox);

        this.addRenderableWidget(Button.builder(Component.literal("§6..."), (btn) -> {
            this.updatePending();
            this.minecraft.setScreen(new EffectSelectionScreen(this, (id) -> {
                this.pendingId = id;
                this.init();
            }));
        }).bounds(fieldX + idFieldWidth + RESET_BUTTON_GAP, currentY, ACTION_BUTTON_WIDTH, 20).build());
        this.addResetButton(fieldX + idFieldWidth + RESET_BUTTON_GAP + ACTION_BUTTON_WIDTH + RESET_BUTTON_GAP, currentY, () -> {
            this.pendingId = this.originalId;
            this.init();
        });

        currentY += FIELD_SPACING;
        this.durationBox = new EditBox(this.font, fieldX, currentY, baseFieldWidth, 20, Component.literal("Duração (segundos)"));
        this.durationBox.setValue(this.pendingDur);
        this.durationBox.setFilter(this::isValidSecondsInput);
        this.durationBox.setResponder(value -> this.pendingDur = value);
        this.addRenderableWidget(this.durationBox);
        this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, currentY, () -> {
            this.pendingDur = this.originalDur;
            this.init();
        });

        currentY += FIELD_SPACING;
        this.amplifierBox = new EditBox(this.font, fieldX, currentY, baseFieldWidth, 20, Component.literal("Nível"));
        this.amplifierBox.setValue(this.pendingAmp);
        this.amplifierBox.setResponder(value -> this.pendingAmp = value);
        this.addRenderableWidget(this.amplifierBox);
        this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, currentY, () -> {
            this.pendingAmp = this.originalAmp;
            this.init();
        });

        if (category == ItemEditorScreen.Category.ON_HIT || category == ItemEditorScreen.Category.ON_HURT) {
            currentY += FIELD_SPACING;
            this.chanceBox = new EditBox(this.font, fieldX, currentY, baseFieldWidth, 20, Component.literal("Chance"));
            this.chanceBox.setValue(this.pendingChance);
            this.chanceBox.setResponder(value -> this.pendingChance = value);
            this.addRenderableWidget(this.chanceBox);
            this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, currentY, () -> {
                this.pendingChance = this.originalChance;
                this.init();
            });

            currentY += FIELD_SPACING;
            this.addRenderableWidget(CycleButton.builder((Boolean b) -> b ? Component.literal("SELF") : Component.literal("TARGET"))
                .withValues(true, false)
                .withInitialValue(this.self)
                .displayOnlyValue()
                .create(fieldX, currentY, baseFieldWidth, 20, Component.literal("Alvo"), (btn, val) -> this.self = val));
            this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, currentY, () -> {
                this.self = this.originalSelf;
                this.init();
            });
        } else {
            this.chanceBox = null;
        }

        int buttonY = panelY + PANEL_HEIGHT - 40;
        this.addRenderableWidget(Button.builder(Component.literal("Salvar"), (btn) -> addEffect())
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
            String id = normalizeEffectId(idBox.getValue());
            int dur = parseDurationSecondsToTicks(durationBox.getValue());
            int amp = parseIntInput(amplifierBox.getValue(), 0);

            if (category == ItemEditorScreen.Category.POTIONS) {
                ListTag list = parent.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
                CompoundTag entry = new CompoundTag();
                writePotionEffectId(entry, id);
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
                entry.putDouble("chance", parseDoubleInput(chanceBox.getValue(), 1.0D));
                entry.putBoolean("self", self);
                
                list.add(entry);
                sfTag.put(listKey, list);
                parent.currentTag.put("SF_ItemEffects", sfTag);
            }
            this.minecraft.setScreen(this.parent);
        } catch (Exception ignored) {}
    }

    private void addResetButton(int x, int y, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal("↺"), (btn) -> action.run())
            .bounds(x, y, RESET_BUTTON_WIDTH, 20)
            .build());
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
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "ID do efeito", labelX, baseY - 12, 0xFFCCD9F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Duração (segundos)", labelX, baseY + spacing - 12, 0xFFCCD9F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Nível (0 = Lvl 1)", labelX, baseY + spacing * 2 - 12, 0xFFCCD9F1);
        if (category == ItemEditorScreen.Category.ON_HIT || category == ItemEditorScreen.Category.ON_HURT) {
            GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Chance de aplicar", labelX, baseY + spacing * 3 - 12, 0xFFCCD9F1);
            GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Alvo do efeito", labelX, baseY + spacing * 4 - 12, 0xFFCCD9F1);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
