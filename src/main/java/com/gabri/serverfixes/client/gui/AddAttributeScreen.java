package com.gabri.serverfixes.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@SuppressWarnings("null")
public class AddAttributeScreen extends Screen {
    private final ItemEditorScreen parent;
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 230;
    private static final int FIELD_SPACING = 34;
    private static final int BUTTON_WIDTH = 110;

    private EditBox nameBox;
    private EditBox amountBox;

    private int operation = 0;
    private String slot = "mainhand";

    private String pendingName = "";
    private String pendingAmount = "";

    public AddAttributeScreen(ItemEditorScreen parent) {
        super(Component.literal("Adicionar Atributo"));
        this.parent = parent;
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

        // Name Row
        this.nameBox = new EditBox(this.font, fieldX, currentY, fieldWidth - 40, 20, Component.literal("ID do Atributo"));
        this.nameBox.setMaxLength(64);
        this.nameBox.setValue(this.pendingName);
        this.addRenderableWidget(this.nameBox);

        // Selector Button
        this.addRenderableWidget(Button.builder(Component.literal("§6..."), (btn) -> {
            this.pendingName = this.nameBox.getValue();
            this.pendingAmount = this.amountBox.getValue();
            this.minecraft.setScreen(new AttributeSelectionScreen(this, (attrId) -> {
                this.pendingName = attrId;
                this.init();
            }));
        }).bounds(fieldX + fieldWidth - 30, currentY, 30, 20).build());

        // Amount Row
        currentY += FIELD_SPACING;

        this.amountBox = new EditBox(this.font, fieldX, currentY, fieldWidth, 20, Component.literal("Valor"));
        this.amountBox.setMaxLength(32);
        this.amountBox.setValue(this.pendingAmount);
        this.addRenderableWidget(this.amountBox);

        // Operation Row
        currentY += FIELD_SPACING;

        this.addRenderableWidget(CycleButton.builder(this::getOperationName)
            .withValues(0, 1, 2)
            .displayOnlyValue()
            .create(fieldX, currentY, fieldWidth, 20, Component.literal("Operação"), (btn, val) -> this.operation = val));

        // Slot CycleButton
        java.util.List<String> slotList = new java.util.ArrayList<>();
        
        // Filter slots based on category
        boolean isCurioCategory = parent.getCategory() == ItemEditorScreen.Category.ATTR_CURIOS;
        
        if (!isCurioCategory) {
            // Vanilla slots
            for (net.minecraft.world.entity.EquipmentSlot eSlot : net.minecraft.world.entity.EquipmentSlot.values()) {
                slotList.add(eSlot.getName());
            }
            slotList.add("any");
        } else {
            // Curio slots
            if (net.minecraftforge.fml.ModList.get().isLoaded("curios")) {
                try {
                    slotList.addAll(top.theillusivec4.curios.api.CuriosApi.getSlots(false).keySet());
                } catch (Exception ignored) {}
            }
            if (slotList.isEmpty()) slotList.add("any"); // Fallback
        }
        
        String[] slots = slotList.toArray(new String[0]);
        String initialSlot = isCurioCategory && slotList.size() > 0 ? slotList.get(0) : "mainhand";
        
        // Ensure this.slot is initialized to the first valid slot for the category
        if (this.slot.equals("mainhand") && isCurioCategory && slotList.size() > 0) {
            this.slot = initialSlot;
        } else if (!isCurioCategory && !isVanillaSlot(this.slot)) {
            this.slot = "mainhand";
        }

        currentY += FIELD_SPACING;

        this.addRenderableWidget(CycleButton.builder((String s) -> Component.literal("Slot: " + s))
            .withValues(slots)
            .displayOnlyValue()
            .withInitialValue(this.slot)
            .create(fieldX, currentY, fieldWidth, 20, Component.literal("Slot"), (btn, val) -> this.slot = val));

        // Add / Save
        int buttonY = panelY + PANEL_HEIGHT - 40;
        this.addRenderableWidget(Button.builder(Component.literal("Adicionar"), (btn) -> {
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

                boolean isCurio = parent.getCategory() == ItemEditorScreen.Category.ATTR_CURIOS;
                String tagName = isCurio ? "CurioAttributeModifiers" : "AttributeModifiers";

                ListTag list = this.parent.currentTag.getList(tagName, Tag.TAG_COMPOUND);
                list.add(newAttr);
                this.parent.currentTag.put(tagName, list);

                this.minecraft.setScreen(this.parent);
            } catch (Exception e) {
                // Ignore invalid numbers
            }
        }).bounds(midX - BUTTON_WIDTH - 10, buttonY, BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> {
            this.minecraft.setScreen(this.parent);
        }).bounds(midX + 10, buttonY, BUTTON_WIDTH, 20).build());
    }

    private boolean isVanillaSlot(String slot) {
        for (net.minecraft.world.entity.EquipmentSlot eSlot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (eSlot.getName().equalsIgnoreCase(slot)) return true;
        }
        return false;
    }

    private Component getOperationName(int op) {
        if (op == 0) return Component.literal("Op 0: Adição (+X)");
        if (op == 1) return Component.literal("Op 1: Multiplicar Base (+X%)");
        return Component.literal("Op 2: Multiplicar Total (xX)");
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
        graphics.drawString(this.font, "Nome do atributo", labelX, baseY - 12, 0xFFCCD9F1);
        graphics.drawString(this.font, "Valor do atributo", labelX, baseY + spacing - 12, 0xFFCCD9F1);
        graphics.drawString(this.font, "Tipo de operação", labelX, baseY + spacing * 2 - 12, 0xFFCCD9F1);
        graphics.drawString(this.font, "Slot alvo", labelX, baseY + spacing * 3 - 12, 0xFFCCD9F1);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
