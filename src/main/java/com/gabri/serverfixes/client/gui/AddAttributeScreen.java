package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.ItemEditorScreen.AttributeRow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@SuppressWarnings("null")
public class AddAttributeScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 230;
    private static final int FIELD_SPACING = 34;
    private static final int BUTTON_WIDTH = 110;
    private static final int ACTION_BUTTON_WIDTH = 26;
    private static final int RESET_BUTTON_WIDTH = 24;
    private static final int RESET_BUTTON_GAP = 6;

    private final ItemEditorScreen parent;
    private final AttributeRow editingEntry;

    private EditBox nameBox;
    private EditBox amountBox;

    private int operation = 0;
    private String slot = "mainhand";

    private String pendingName = "";
    private String pendingAmount = "";
    private String originalName = "";
    private String originalAmount = "";
    private int originalOperation = 0;
    private String originalSlot = "mainhand";

    public AddAttributeScreen(ItemEditorScreen parent) {
        this(parent, null);
    }

    public AddAttributeScreen(ItemEditorScreen parent, AttributeRow editingEntry) {
        super(Component.literal(editingEntry == null ? "Adicionar Atributo" : "Editar Atributo"));
        this.parent = parent;
        this.editingEntry = editingEntry;
        if (editingEntry != null) {
            this.pendingName = editingEntry.getAttributeName();
            this.pendingAmount = formatAmount(editingEntry.getAmount());
            this.operation = editingEntry.getOperation();
            this.slot = editingEntry.getSlot();
        }
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
        int nameFieldWidth = Math.max(160, baseFieldWidth - ACTION_BUTTON_WIDTH - RESET_BUTTON_GAP);
        int currentY = panelY + 40;

        this.originalName = this.pendingName;
        this.originalAmount = this.pendingAmount;
        this.originalOperation = this.operation;
        this.originalSlot = this.slot;

        this.nameBox = new EditBox(this.font, fieldX, currentY, nameFieldWidth, 20, Component.literal("ID do Atributo"));
        this.nameBox.setMaxLength(64);
        this.nameBox.setValue(this.pendingName);
        this.nameBox.setResponder(value -> this.pendingName = value);
        this.addRenderableWidget(this.nameBox);

        this.addRenderableWidget(Button.builder(Component.literal("§6..."), (btn) -> {
            this.pendingName = this.nameBox.getValue();
            this.pendingAmount = this.amountBox != null ? this.amountBox.getValue() : this.pendingAmount;
            this.minecraft.setScreen(new AttributeSelectionScreen(this, (attrId) -> {
                this.pendingName = attrId;
                this.init();
            }));
        }).bounds(fieldX + nameFieldWidth + RESET_BUTTON_GAP, currentY, ACTION_BUTTON_WIDTH, 20).build());
        this.addResetButton(fieldX + nameFieldWidth + RESET_BUTTON_GAP + ACTION_BUTTON_WIDTH + RESET_BUTTON_GAP, currentY, () -> {
            this.pendingName = this.originalName;
            this.init();
        });

        currentY += FIELD_SPACING;
        this.amountBox = new EditBox(this.font, fieldX, currentY, baseFieldWidth, 20, Component.literal("Valor"));
        this.amountBox.setMaxLength(32);
        this.amountBox.setValue(this.pendingAmount);
        this.amountBox.setResponder(value -> this.pendingAmount = value);
        this.addRenderableWidget(this.amountBox);
        this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, currentY, () -> {
            this.pendingAmount = this.originalAmount;
            this.init();
        });

        currentY += FIELD_SPACING;
        this.addRenderableWidget(CycleButton.builder(this::getOperationLabel)
            .withValues(0, 1, 2)
            .withInitialValue(this.originalOperation)
            .displayOnlyValue()
            .create(fieldX, currentY, baseFieldWidth, 20, Component.literal("Operação"), (btn, val) -> this.operation = val));
        this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, currentY, () -> {
            this.operation = this.originalOperation;
            this.init();
        });

        currentY += FIELD_SPACING;
        List<String> slotOptions = collectSlotOptions();
        if (!slotOptions.contains(this.slot)) {
            slotOptions.add(this.slot);
        }
        String[] slots = slotOptions.toArray(new String[0]);

        this.addRenderableWidget(CycleButton.builder((String option) -> Component.literal(option.toUpperCase(Locale.ROOT)))
            .withValues(slots)
            .displayOnlyValue()
            .withInitialValue(this.slot)
            .create(fieldX, currentY, baseFieldWidth, 20, Component.literal("Slot"), (btn, val) -> this.slot = val));
        this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, currentY, () -> {
            this.slot = this.originalSlot;
            this.init();
        });

        int buttonY = panelY + PANEL_HEIGHT - 40;
        this.addRenderableWidget(Button.builder(Component.literal(this.editingEntry == null ? "Salvar" : "Salvar alterações"), (btn) -> {
            try {
                double amount = Double.parseDouble(this.amountBox.getValue());
                String attrName = this.nameBox.getValue();
                if (!attrName.contains(":")) attrName = "minecraft:" + attrName;

                CompoundTag newAttr = new CompoundTag();
                newAttr.putString("AttributeName", attrName);
                newAttr.putString("Name", attrName + "_mod");
                newAttr.putDouble("Amount", amount);
                newAttr.putInt("Operation", this.operation);
                newAttr.putString("Slot", this.slot);
                newAttr.putUUID("UUID", UUID.randomUUID());

                boolean targetIsCurio = !ItemEditorScreen.isVanillaSlot(this.slot);
                this.parent.upsertAttribute(this.editingEntry, newAttr, targetIsCurio);
                this.minecraft.setScreen(this.parent);
            } catch (Exception ignored) {
                // Ignore invalid numbers
            }
        }).bounds(midX - BUTTON_WIDTH - 10, buttonY, BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> this.minecraft.setScreen(this.parent))
            .bounds(midX + 10, buttonY, BUTTON_WIDTH, 20).build());
    }

    private void addResetButton(int x, int y, Runnable action) {
        this.addRenderableWidget(Button.builder(Component.literal("↺"), (btn) -> action.run())
            .bounds(x, y, RESET_BUTTON_WIDTH, 20)
            .build());
    }

    private Component getOperationLabel(int op) {
        if (op == 0) return Component.literal("Op 0 • Adição (+X)");
        if (op == 1) return Component.literal("Op 1 • Multiplicar Base (+X%)");
        return Component.literal("Op 2 • Multiplicar Total (xX)");
    }

    private static List<String> collectSlotOptions() {
        List<String> slotOptions = new ArrayList<>();
        for (EquipmentSlot equip : EquipmentSlot.values()) {
            String name = equip.getName();
            if (!slotOptions.contains(name)) {
                slotOptions.add(name);
            }
        }
        if (!slotOptions.contains("any")) {
            slotOptions.add("any");
        }
        if (ModList.get().isLoaded("curios")) {
            try {
                top.theillusivec4.curios.api.CuriosApi.getSlots(false).keySet().forEach(slotId -> {
                    if (!slotOptions.contains(slotId)) {
                        slotOptions.add(slotId);
                    }
                });
            } catch (Exception ignored) {
            }
        }
        return slotOptions;
    }

    private static String formatAmount(double value) {
        return String.format(Locale.US, "%.2f", value);
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
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Nome do atributo", labelX, baseY - 12, 0xFFCCD9F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Valor do atributo", labelX, baseY + spacing - 12, 0xFFCCD9F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Tipo de operação", labelX, baseY + spacing * 2 - 12, 0xFFCCD9F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Slot alvo", labelX, baseY + spacing * 3 - 12, 0xFFCCD9F1);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}