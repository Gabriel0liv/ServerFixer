package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.commands.ItemAttributeCommands;
import com.gabri.serverfixes.config.ServerFixesConfig;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.SaveItemEditorPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public class ItemEditorScreen extends Screen {
    public CompoundTag currentTag;
    private Category currentCategory = Category.MENU;
    private List<AttributeRow> attributeRows = new ArrayList<>();

    private static final int SIDEBAR_WIDTH = 150;
    private static final int CONTENT_PADDING = 12;
    private static final int ROW_HEIGHT = 26;
    private static final int LIST_START_Y = 70;
    private static final int MAX_VISIBLE_ROWS = 10;

    public enum Category {
        MENU("Editor de Item"),
        ATTRIBUTES("Atributos"),
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
                if (currentCategory == Category.ATTRIBUTES) {
                    this.minecraft.setScreen(new AddAttributeScreen(this));
                } else {
                    this.minecraft.setScreen(new AddEffectScreen(this, currentCategory));
                }
            }).bounds(contentStartX + contentWidth - addWidth, 42, addWidth, 18).build());
        }

        int bottomY = this.height - 30;
        if (currentCategory == Category.MENU) {
            this.addRenderableWidget(Button.builder(Component.literal("Salvar no Item"), (btn) -> {
                normalizePotionEffectsForCompatibility();
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
    }

    private void initCategoryList(int contentStartX, int contentWidth) {
        if (currentCategory == Category.ATTRIBUTES) {
            initAttributeList(contentStartX, contentWidth);
            return;
        }

        String tagName = getTagName();
        if (tagName == null) return;

        ListTag list;
        final CompoundTag effectCompound;
        final String effectKey;
        if (currentCategory == Category.ON_HIT || currentCategory == Category.ON_HURT) {
            effectCompound = this.currentTag.getCompound("SF_ItemEffects");
            effectKey = currentCategory == Category.ON_HIT ? "on_hit" : "on_hurt";
            list = effectCompound.getList(effectKey, Tag.TAG_COMPOUND);
        } else {
            effectCompound = null;
            effectKey = null;
            list = this.currentTag.getList(tagName, Tag.TAG_COMPOUND);
        }

        int rows = Math.min(list.size(), MAX_VISIBLE_ROWS);
        int baseY = LIST_START_Y;
        int buttonWidth = 18;
        int deleteX = contentStartX + contentWidth - buttonWidth;
        for (int i = 0; i < rows; i++) {
            final int index = i;
            int yPos = baseY + (i * ROW_HEIGHT);
            this.addRenderableWidget(Button.builder(Component.literal("§cX"), (btn) -> {
                list.remove(index);
                if (effectCompound != null && effectKey != null) {
                    effectCompound.put(effectKey, list);
                    this.currentTag.put("SF_ItemEffects", effectCompound);
                } else {
                    persistList(tagName, list);
                }
                this.init();
            }).bounds(deleteX, yPos + 2, buttonWidth, 16).build());
        }
    }

    private void initAttributeList(int contentStartX, int contentWidth) {
        this.attributeRows = buildAttributeRows();
        int rows = Math.min(this.attributeRows.size(), MAX_VISIBLE_ROWS);
        int baseY = LIST_START_Y;
        int buttonWidth = 18;
        int buttonGap = 4;
        int deleteX = contentStartX + contentWidth - buttonWidth;
        int editX = deleteX - buttonWidth - buttonGap;
        for (int i = 0; i < rows; i++) {
            AttributeRow row = this.attributeRows.get(i);
            int yPos = baseY + (i * ROW_HEIGHT);
            this.addRenderableWidget(Button.builder(Component.literal("§e✎"), (btn) -> openEditAttribute(row))
                .bounds(editX, yPos + 2, buttonWidth, 16).build());
            this.addRenderableWidget(Button.builder(Component.literal("§cX"), (btn) -> {
                removeAttributeRow(row);
                this.init();
            }).bounds(deleteX, yPos + 2, buttonWidth, 16).build());
        }
    }

    private void openEditAttribute(AttributeRow row) {
        this.minecraft.setScreen(new AddAttributeScreen(this, row));
    }

    private void removeAttributeRow(AttributeRow row) {
        removeAttributeRowFromTag(row);
        rebuildAttributeBackup();
    }

    private void removeAttributeRowFromTag(AttributeRow row) {
        String tagName = row.getTagName();
        if (!this.currentTag.contains(tagName, Tag.TAG_LIST)) return;
        ListTag list = this.currentTag.getList(tagName, Tag.TAG_COMPOUND);
        if (row.getIndex() >= 0 && row.getIndex() < list.size()) {
            list.remove(row.getIndex());
            persistList(tagName, list);
        }
    }

    public void upsertAttribute(AttributeRow editingRow, CompoundTag attribute, boolean targetIsCurio) {
        if (editingRow != null) {
            removeAttributeRowFromTag(editingRow);
        }
        String targetTag = targetIsCurio ? ItemAttributeCommands.CURIOS_TAG : "AttributeModifiers";
        ListTag list = this.currentTag.contains(targetTag, Tag.TAG_LIST) ? this.currentTag.getList(targetTag, Tag.TAG_COMPOUND) : new ListTag();
        list.add(attribute);
        persistList(targetTag, list);
        rebuildAttributeBackup();
    }

    private List<AttributeRow> buildAttributeRows() {
        List<AttributeRow> rows = new ArrayList<>();
        if (this.currentTag.contains("AttributeModifiers", Tag.TAG_LIST)) {
            ListTag list = this.currentTag.getList("AttributeModifiers", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                rows.add(new AttributeRow(list.getCompound(i), false, i));
            }
        }
        if (this.currentTag.contains(ItemAttributeCommands.CURIOS_TAG, Tag.TAG_LIST)) {
            ListTag list = this.currentTag.getList(ItemAttributeCommands.CURIOS_TAG, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                rows.add(new AttributeRow(list.getCompound(i), true, i));
            }
        }
        return rows;
    }

    private void persistList(String tagName, ListTag list) {
        if (list.isEmpty()) {
            this.currentTag.remove(tagName);
        } else {
            this.currentTag.put(tagName, list);
        }
    }

    private void rebuildAttributeBackup() {
        if (!ServerFixesConfig.ENABLE_PERSISTENT_ATTRIBUTES.get()) {
            this.currentTag.remove(ItemAttributeCommands.BACKUP_TAG);
            return;
        }
        ListTag vanilla = this.currentTag.contains("AttributeModifiers", Tag.TAG_LIST)
            ? this.currentTag.getList("AttributeModifiers", Tag.TAG_COMPOUND)
            : new ListTag();
        if (vanilla.isEmpty()) {
            this.currentTag.remove(ItemAttributeCommands.BACKUP_TAG);
            return;
        }
        ListTag backup = new ListTag();
        for (int i = 0; i < vanilla.size(); i++) {
            CompoundTag copy = vanilla.getCompound(i).copy();
            copy.putBoolean("IsCurio", false);
            backup.add(copy);
        }
        this.currentTag.put(ItemAttributeCommands.BACKUP_TAG, backup);
    }

    private String getTagName() {
        switch (currentCategory) {
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
        if (currentCategory == Category.ATTRIBUTES) {
            renderAttributeTable(graphics, contentStartX, contentWidth);
            return;
        }

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

    private void renderAttributeTable(GuiGraphics graphics, int contentStartX, int contentWidth) {
        if (this.attributeRows.isEmpty()) {
            this.attributeRows = buildAttributeRows();
        }

        int listX = contentStartX + 8;
        int actionArea = 60;
        int tableWidth = Math.max(contentWidth - 16 - actionArea, 260);
        int rows = Math.min(this.attributeRows.size(), MAX_VISIBLE_ROWS);
        int panelRows = Math.max(rows, 2);
        int panelHeight = Math.min(panelRows * ROW_HEIGHT + 46, this.height - LIST_START_Y - 40);
        GuiLayoutUtils.drawPanel(graphics, listX - 6, LIST_START_Y - 10, tableWidth + 12, panelHeight + 16, 0xCC1C2532, 0xFF4E6B8D);

        int headerHeight = 20;
        graphics.fill(listX, LIST_START_Y - 8, listX + tableWidth - 6, LIST_START_Y + headerHeight - 8, 0x33FFFFFF);

        int nameX = listX + 8;
        int slotX = listX + tableWidth - 70;
        int valueX = slotX - 90;
        int headerY = LIST_START_Y - 6;

        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Atributo", nameX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Valor", valueX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Slot", slotX, headerY + 4, 0xFFF1F1F1);

        if (rows == 0) {
            graphics.drawString(this.font, "§7Nenhum atributo registrado", listX + 12, headerY + headerHeight + 4, 0xAAAACCFF);
            return;
        }

        for (int i = 0; i < rows; i++) {
            AttributeRow row = this.attributeRows.get(i);
            int rowY = LIST_START_Y + headerHeight + (i * ROW_HEIGHT);
            if (i % 2 == 0) {
                graphics.fill(listX, rowY - 2, listX + tableWidth - 6, rowY + ROW_HEIGHT - 4, 0x17FFFFFF);
            }
            graphics.drawString(this.font, getAttributeDisplay(row), nameX, rowY + 4, 0xDDFFFFFF);
            graphics.drawString(this.font, formatAttributeValue(row), valueX, rowY + 4, 0xFF90EEFF);
            graphics.drawString(this.font, row.getSlot(), slotX, rowY + 4, 0xFFB2CEFF);
        }
    }

    private String getAttributeDisplay(AttributeRow row) {
        String id = row.getAttributeName();
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location != null) {
            Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(location);
            if (attribute != null) {
                return Component.translatable(attribute.getDescriptionId()).getString();
            }
        }
        return id;
    }

    private static String formatAttributeValue(AttributeRow row) {
        return String.format(Locale.US, "%.2f", row.getAmount()) + getOperationSuffix(row.getOperation());
    }

    private static String getOperationSuffix(int op) {
        return op == 0 ? " (Adição)" : (op == 1 ? " (Mult. Base)" : " (Mult. Total)");
    }

    private String formatEntry(CompoundTag entry) {
        if (currentCategory == Category.POTIONS) {
            String id = resolvePotionEffectId(entry).replace("minecraft:", "");
            int amp = entry.getInt("Amplifier") + 1;
            return id + " Lvl " + amp;
        } else {
            String id = entry.getString("id").replace("minecraft:", "");
            int amp = entry.getInt("amplifier") + 1;
            int dur = entry.getInt("duration") / 20;
            return id + " Lvl " + amp + " (" + dur + "s)";
        }
    }

    private static String resolvePotionEffectId(CompoundTag entry) {
        if (entry.contains("IdString", Tag.TAG_STRING)) {
            return entry.getString("IdString");
        }
        if (entry.contains("Id", Tag.TAG_STRING)) {
            return entry.getString("Id");
        }
        if (entry.contains("Id", Tag.TAG_ANY_NUMERIC)) {
            MobEffect effect = MobEffect.byId(entry.getInt("Id"));
            if (effect != null) {
                ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(effect);
                if (key != null) {
                    return key.toString();
                }
            }
        }
        return "unknown";
    }

    private void normalizePotionEffectsForCompatibility() {
        if (!this.currentTag.contains("CustomPotionEffects", Tag.TAG_LIST)) {
            return;
        }

        ListTag list = this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
        boolean changed = false;

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String idString = null;

            if (entry.contains("IdString", Tag.TAG_STRING)) {
                idString = entry.getString("IdString");
            } else if (entry.contains("Id", Tag.TAG_STRING)) {
                idString = entry.getString("Id");
            }

            MobEffect effect = null;
            if (idString != null && !idString.isBlank()) {
                ResourceLocation rl = ResourceLocation.tryParse(idString);
                if (rl != null) {
                    effect = ForgeRegistries.MOB_EFFECTS.getValue(rl);
                }
            }

            if (effect == null && entry.contains("Id", Tag.TAG_ANY_NUMERIC)) {
                effect = MobEffect.byId(entry.getInt("Id"));
                if (effect != null && (idString == null || idString.isBlank())) {
                    ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(effect);
                    if (key != null) {
                        idString = key.toString();
                    }
                }
            }

            if (effect == null) {
                continue;
            }

            int numericId = MobEffect.getId(effect);
            if (!entry.contains("Id", Tag.TAG_ANY_NUMERIC) || entry.getInt("Id") != numericId) {
                entry.putInt("Id", numericId);
                changed = true;
            }

            if (idString != null && !idString.isBlank()) {
                if (!entry.contains("IdString", Tag.TAG_STRING) || !idString.equals(entry.getString("IdString"))) {
                    entry.putString("IdString", idString);
                    changed = true;
                }
            }
        }

        if (changed) {
            this.currentTag.put("CustomPotionEffects", list);
        }
    }

    public static boolean isVanillaSlot(String slot) {
        if (slot == null) return false;
        if ("any".equalsIgnoreCase(slot)) return true;
        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            if (equipmentSlot.getName().equalsIgnoreCase(slot)) return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static final class AttributeRow {
        private final CompoundTag entry;
        private final boolean isCurio;
        private final int index;

        public AttributeRow(CompoundTag entry, boolean isCurio, int index) {
            this.entry = entry;
            this.isCurio = isCurio;
            this.index = index;
        }

        public String getAttributeName() {
            return this.entry.getString("AttributeName");
        }

        public double getAmount() {
            return this.entry.getDouble("Amount");
        }

        public int getOperation() {
            return this.entry.getInt("Operation");
        }

        public String getSlot() {
            return this.entry.contains("Slot") ? this.entry.getString("Slot") : "any";
        }

        public String getTagName() {
            return this.isCurio ? ItemAttributeCommands.CURIOS_TAG : "AttributeModifiers";
        }

        public int getIndex() {
            return this.index;
        }
    }
}