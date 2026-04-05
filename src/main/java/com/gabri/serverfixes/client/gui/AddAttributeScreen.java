package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.ItemEditorScreen.AttributeRow;
import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
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

    private final ItemEditorScreen parent;
    private final AttributeRow editingEntry;

    private EditBox nameBox;
    private EditBox amountBox;
    private Button operationButton;

    private int operation = 0;
    private String slot = "mainhand";

    private String pendingName = "";
    private String pendingAmount = "";

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
        int baseFieldWidth = Math.max(140, formRight - fieldX - 12);
        int nameFieldWidth = Math.max(160, baseFieldWidth - ACTION_BUTTON_WIDTH);
        int currentY = panelY + 40;

        this.nameBox = new SelectableEditBox(this.font, fieldX, currentY, nameFieldWidth, 20, Component.literal("ID do Atributo"));
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
        }).bounds(fieldX + nameFieldWidth + 6, currentY, ACTION_BUTTON_WIDTH, 20).build());

        currentY += FIELD_SPACING;
        int opWidth = 56;
        int amountWidth = Math.max(80, baseFieldWidth - opWidth - 6);
        this.amountBox = new SelectableEditBox(this.font, fieldX, currentY, amountWidth, 20, Component.literal("Valor"));
        this.amountBox.setMaxLength(32);
        this.amountBox.setValue(this.pendingAmount);
        this.amountBox.setResponder(value -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if ((c >= '0' && c <= '9') || c == '.' || c == '-') sb.append(c);
            }
            String filtered = sb.toString();
            this.pendingAmount = filtered;
            if (!filtered.equals(value)) {
                this.amountBox.setValue(filtered);
            }
        });
        this.addRenderableWidget(this.amountBox);
        // Operation button: cycles operation types on click. Tooltip explains each type.
        int opX = fieldX + amountWidth + 6;
        int opY = currentY;
        this.operationButton = Button.builder(Component.literal(Integer.toString(this.operation)), (btn) -> {
            // preserve current typed values then rebuild UI to update button label
            this.pendingName = this.nameBox.getValue();
            this.pendingAmount = this.amountBox != null ? this.amountBox.getValue() : this.pendingAmount;
            this.operation = (this.operation + 1) % 3;
            this.init();
        }).bounds(opX, opY, opWidth, 20).build();
        this.operationButton.setTooltip(Tooltip.create(Component.literal("0: Adição - soma direta; 1: Mult. Base - multiplica o valor base; 2: Mult. Total - multiplica o total")));
        this.addRenderableWidget(this.operationButton);

        currentY += FIELD_SPACING;
        List<String> slotOptions = collectSlotOptions();
        if (!slotOptions.contains(this.slot)) slotOptions.add(this.slot);
        // Button that opens a searchable slot selection screen (dropdown-like)
        this.addRenderableWidget(Button.builder(Component.literal(this.slot.toUpperCase(Locale.ROOT)), (btn) -> {
            this.pendingName = this.nameBox.getValue();
            this.pendingAmount = this.amountBox != null ? this.amountBox.getValue() : this.pendingAmount;
            this.minecraft.setScreen(new SlotSelectionScreen(this, slotOptions, (slotId) -> {
                this.slot = slotId;
                this.init();
            }));
        }).bounds(fieldX, currentY, baseFieldWidth, 20).build());

        int buttonY = panelY + PANEL_HEIGHT - 40;
        int saveX = midX - BUTTON_WIDTH - 10;
        int cancelX = midX + 10;

        this.addRenderableWidget(new PulsingButton(saveX, buttonY, BUTTON_WIDTH, 20, Component.literal(this.editingEntry == null ? "Salvar" : "Salvar alterações"), (btn) -> {
            try {
                double amount = Double.parseDouble(this.amountBox.getValue());
                String attrName = this.nameBox.getValue();
                if (attrName == null || attrName.isBlank()) {
                    ((PulsingButton)btn).pulseError(1000);
                    return;
                }
                if (!attrName.contains(":")) attrName = "minecraft:" + attrName;

                int op = this.operation;

                CompoundTag newAttr = new CompoundTag();
                newAttr.putString("AttributeName", attrName);
                newAttr.putString("Name", attrName + "_mod");
                newAttr.putDouble("Amount", amount);
                newAttr.putInt("Operation", op);
                newAttr.putString("Slot", this.slot);
                newAttr.putUUID("UUID", UUID.randomUUID());

                boolean targetIsCurio = !ItemEditorScreen.isVanillaSlot(this.slot);
                this.parent.upsertAttribute(this.editingEntry, newAttr, targetIsCurio);
                this.minecraft.setScreen(this.parent);
            } catch (Exception ignored) {
                ((PulsingButton)btn).pulseError(1000);
            }
        }));

        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> this.minecraft.setScreen(this.parent))
            .bounds(cancelX, buttonY, BUTTON_WIDTH, 20).build());
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
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Slot alvo", labelX, baseY + spacing * 3 - 12, 0xFFCCD9F1);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}