package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.GuiLayoutUtils.VerticalLayoutBuilder;
import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

@SuppressWarnings("null")
public class LootStudioScreen extends Screen {
    private final List<ResourceLocation> allTables = new ArrayList<>();
    private final List<ResourceLocation> filteredTables = new ArrayList<>();

    private SelectableEditBox searchBox;

    // left list widgets
    private double leftScrollAmount = 0.0D;
    private int maxLeftScroll = 0;
    private final List<TableButton> tableButtons = new ArrayList<>();
    private final Map<TableButton, Integer> buttonOriginalY = new HashMap<>();
    private int selectedTableIndex = 0;
    private ResourceLocation selectedTableId;

    // center loot list
    private final List<LootDropDTO> lootDrops = new ArrayList<>();

    // right panel widgets
    private final List<AbstractWidget> rightPanelWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> widgetOriginalY = new HashMap<>();
    private double rightScrollAmount = 0.0D;
    private int maxRightScroll = 0;

    // layout
    private int leftX, leftY, leftW, leftH;
    private int centerX, centerY, centerW, centerH;
    private int rightX, rightY, rightW, rightH;
    private int listAreaX, listAreaY, listAreaW, listAreaH;

    // right panel controls
    private Button selectItemButton;
    private DoubleParameterSlider chanceSlider;
    private IntParameterSlider minSlider;
    private IntParameterSlider maxSlider;
    private TextureCheckbox requirePlayerKillCheckbox;
    private TextureCheckbox affectedByLootingCheckbox;
    private Button injectButton;

    public LootStudioScreen() {
        super(Component.literal("Loot Studio"));
        // mock some loot tables
        this.allTables.add(ResourceLocation.fromNamespaceAndPath("minecraft", "entities/zombie"));
        this.allTables.add(ResourceLocation.fromNamespaceAndPath("minecraft", "entities/skeleton"));
        this.allTables.add(ResourceLocation.fromNamespaceAndPath("minecraft", "entities/creeper"));
        this.allTables.add(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/stone"));
        this.allTables.add(ResourceLocation.fromNamespaceAndPath("modid", "entities/custom_mob"));

        this.allTables.sort(Comparator
            .comparing(ResourceLocation::getNamespace)
            .thenComparing(ResourceLocation::getPath));

        this.filteredTables.addAll(this.allTables);
        if (!this.filteredTables.isEmpty()) this.selectedTableId = this.filteredTables.get(0);

        refreshLootDropsMock();
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.font == null) return;

        this.clearWidgets();
        this.tableButtons.clear();
        this.buttonOriginalY.clear();
        this.leftScrollAmount = 0.0D;
        this.maxLeftScroll = 0;
        this.selectedTableIndex = 0;

        this.rightPanelWidgets.clear();
        this.widgetOriginalY.clear();
        this.rightScrollAmount = 0.0D;
        this.maxRightScroll = 0;

        updateLayout();

        this.searchBox = new SelectableEditBox(this.font, this.leftX + 8, this.leftY + 24, this.listAreaW, 18, Component.literal("Pesquisar"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setResponder(value -> applySearchFilter());
        this.addRenderableWidget(this.searchBox);
        applySearchFilter();

        // Right panel widgets
        int sliderX = this.rightX + 8;
        int sliderW = this.rightW - 16;

        this.selectItemButton = Button.builder(Component.literal("Item: <nenhum>"), btn -> System.out.println("Select item clicked"))
            .bounds(0, 0, sliderW, 18).build();
        registerRightPanelWidget(this.selectItemButton);

        this.chanceSlider = new DoubleParameterSlider(0, 0, sliderW, 18, "Chance", 0.0D, 100.0D, 50.0D, 1, value -> {});
        registerRightPanelWidget(this.chanceSlider);

        this.minSlider = new IntParameterSlider(0, 0, sliderW, 18, "Min", 1, 64, 1, value -> {});
        registerRightPanelWidget(this.minSlider);

        this.maxSlider = new IntParameterSlider(0, 0, sliderW, 18, "Max", 1, 64, 3, value -> {});
        registerRightPanelWidget(this.maxSlider);

        this.requirePlayerKillCheckbox = new TextureCheckbox(0, 0, false, Component.literal(""), val -> {});
        registerRightPanelWidget(this.requirePlayerKillCheckbox);

        this.affectedByLootingCheckbox = new TextureCheckbox(0, 0, false, Component.literal(""), val -> {});
        registerRightPanelWidget(this.affectedByLootingCheckbox);

        this.injectButton = Button.builder(Component.literal("Salvar e Injetar"), btn -> System.out.println("Inject"))
            .bounds(0, 0, sliderW, 20).build();
        registerRightPanelWidget(this.injectButton);

        recalculateRightPanel();
    }

    private void updateLayout() {
        int contentY = 20;
        int contentH = this.height - 28;

        int panelGap = 4;
        this.leftW = this.width / 4;
        this.rightW = Math.max(160, this.width / 4 + 20);
        this.rightX = this.width - this.rightW;

        this.leftX = 0;
        this.centerX = this.leftW + panelGap;
        int centerRight = this.rightX - panelGap;
        this.centerW = Math.max(120, centerRight - this.centerX);

        this.leftY = contentY;
        this.centerY = contentY;
        this.rightY = contentY;

        this.leftH = contentH;
        this.centerH = contentH;
        this.rightH = contentH;

        int searchY = this.leftY + 24;
        int searchH = 18;
        this.listAreaX = this.leftX + 8;
        this.listAreaY = searchY + searchH + 8;
        this.listAreaW = this.leftW - 26;
        this.listAreaH = this.leftH - (this.listAreaY - this.leftY) - 8;
    }

    private void applySearchFilter() {
        String query = this.searchBox != null ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";
        this.filteredTables.clear();

        if (query.isEmpty()) {
            this.filteredTables.addAll(this.allTables);
        } else {
            for (ResourceLocation id : this.allTables) {
                if (id == null) continue;
                String full = id.toString().toLowerCase(Locale.ROOT);
                if (full.contains(query) || id.getNamespace().toLowerCase(Locale.ROOT).contains(query) || id.getPath().toLowerCase(Locale.ROOT).contains(query)) {
                    this.filteredTables.add(id);
                }
            }
        }

        this.filteredTables.removeIf(Objects::isNull);
        ResourceLocation nextSelection = this.selectedTableId;
        if (nextSelection == null || !this.filteredTables.contains(nextSelection)) nextSelection = this.filteredTables.isEmpty() ? null : this.filteredTables.get(0);

        rebuildTableButtons();

        if (nextSelection == null || this.filteredTables.isEmpty()) {
            this.selectedTableIndex = 0;
            setSelectedTableId(null);
            return;
        }

        int index = this.filteredTables.indexOf(nextSelection);
        setSelectedTableIndex(index < 0 ? 0 : index);
    }

    private void rebuildTableButtons() {
        this.tableButtons.clear();
        this.buttonOriginalY.clear();
        this.leftScrollAmount = 0.0D;

        int buttonX = this.leftX + 8;
        int buttonW = Math.max(10, this.listAreaW - 2);
        int buttonH = 20;
        int currentLeftY = this.listAreaY + 1;

        for (ResourceLocation id : this.filteredTables) {
            if (id == null) continue;
            int index = this.tableButtons.size();
            TableButton button = new TableButton(buttonX, currentLeftY, buttonW, buttonH, id, btn -> setSelectedTableIndex(index));
            this.tableButtons.add(button);
            this.buttonOriginalY.put(button, currentLeftY);
            currentLeftY += 22;
        }

        int listBottom = this.listAreaY + this.listAreaH - 10;
        this.maxLeftScroll = Math.max(0, currentLeftY - listBottom);
        this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private void setSelectedTableIndex(int index) {
        if (this.filteredTables.isEmpty()) {
            this.selectedTableIndex = 0;
            setSelectedTableId(null);
            return;
        }

        int clamped = Mth.clamp(index, 0, this.filteredTables.size() - 1);
        this.selectedTableIndex = clamped;
        setSelectedTableId(this.filteredTables.get(clamped));
        if (clamped >= 0 && clamped < this.tableButtons.size()) ensureLeftButtonVisible(this.tableButtons.get(clamped));
    }

    private void setSelectedTableId(ResourceLocation id) {
        this.selectedTableId = id;
        refreshLootDropsMock();
    }

    private void refreshLootDropsMock() {
        this.lootDrops.clear();
        // create a few mock drops
        this.lootDrops.add(new LootDropDTO(new ItemStack(Items.DIAMOND), 5.0D, 1, 1, true, true, false));
        this.lootDrops.add(new LootDropDTO(new ItemStack(Items.IRON_INGOT), 20.0D, 1, 3, false, true, false));
        this.lootDrops.add(new LootDropDTO(new ItemStack(Items.COAL), 50.0D, 1, 5, false, false, false));
        this.lootDrops.add(new LootDropDTO(new ItemStack(Items.BREAD), 2.0D, 1, 2, true, false, true)); // complex
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.maxLeftScroll > 0 && isInsideLeftScrollbar(mouseX, mouseY)) {
            startLeftScrollbarDrag(mouseY);
            return true;
        }
        if (isInsideLeftList(mouseX, mouseY)) {
            for (TableButton tb : this.tableButtons) {
                if (tb.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.leftScrollbarDragging) {
            updateLeftScrollbarDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.leftScrollbarDragging) {
            this.leftScrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (isInsideLeftList(mouseX, mouseY) && this.maxLeftScroll > 0) {
            this.leftScrollAmount -= scrollY * 15.0D;
            this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
            applyLeftScroll();
            return true;
        }
        if (isInsideRightPanel(mouseX, mouseY) && this.maxRightScroll > 0) {
            this.rightScrollAmount -= scrollY * 15.0D;
            this.rightScrollAmount = Mth.clamp(this.rightScrollAmount, 0.0D, this.maxRightScroll);
            applyRightScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null || this.font == null) return;

        updateLayout();
        recalculateRightPanel();

        graphics.fill(0, 0, this.width, this.height, 0xDD000000);
        graphics.fill(this.leftX, this.leftY, this.leftX + this.leftW, this.leftY + this.leftH, 0x44242D3D);
        graphics.fill(this.centerX, this.centerY, this.centerX + this.centerW, this.centerY + this.centerH, 0x66202A39);
        graphics.fill(this.rightX, this.rightY, this.rightX + this.rightW, this.rightY + this.rightH, 0x44242D3D);

        GuiLayoutUtils.drawPanel(graphics, this.leftX, this.leftY, this.leftW, this.leftH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.centerX, this.centerY, this.centerW, this.centerH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.rightX, this.rightY, this.rightW, this.rightH, 0x001A2434, 0xFF4E6B8D);

        graphics.drawString(this.font, "Loot Tables", this.leftX + 8, this.leftY + 8, 0xFFF4F7FF);
        graphics.drawString(this.font, "Loot de: " + (this.selectedTableId != null ? this.selectedTableId.toString() : "<nenhuma>"), this.centerX + 8, this.centerY + 8, 0xFFF4F7FF);
        graphics.drawString(this.font, "Editor", this.rightX + 8, this.rightY + 8, 0xFFF4F7FF);

        GuiLayoutUtils.drawPanel(graphics, this.listAreaX, this.listAreaY, this.listAreaW, this.listAreaH, 0xB1121924, 0xFF2E4258);
        renderLeftButtons(graphics, mouseX, mouseY, partialTick);

        renderCenterLootList(graphics, mouseX, mouseY, partialTick);

        boolean clipRightPanel = !this.rightPanelWidgets.isEmpty();
        Map<AbstractWidget, Boolean> visibilityMemory = new HashMap<>();
        if (clipRightPanel) {
            for (AbstractWidget widget : this.rightPanelWidgets) {
                visibilityMemory.put(widget, widget.visible);
                widget.visible = false;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (clipRightPanel) {
            for (AbstractWidget widget : this.rightPanelWidgets) {
                widget.visible = visibilityMemory.getOrDefault(widget, true);
            }
            graphics.enableScissor(this.rightX, this.rightY, this.rightX + this.rightW, this.rightY + this.rightH);
            for (AbstractWidget widget : this.rightPanelWidgets) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
            graphics.disableScissor();
        }

        renderLeftScrollbar(graphics);
        renderRightScrollbar(graphics);
    }

    private void renderCenterLootList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int startX = this.centerX + 8;
        int startY = this.centerY + 28;
        int rowH = 28;
        int currentY = startY;

        graphics.enableScissor(this.centerX, this.centerY, this.centerX + this.centerW, this.centerY + this.centerH);

        for (LootDropDTO dto : this.lootDrops) {
            int iconX = startX;
            int iconY = currentY;

            if (dto.getItem() != null && !dto.getItem().isEmpty()) {
                graphics.renderItem(dto.getItem(), iconX, iconY);
                graphics.renderItemDecorations(this.font, dto.getItem(), iconX, iconY);
            }

            int textX = iconX + 20;
            if (dto.isComplex()) {
                graphics.drawString(this.font, "[Drop Complexo - Apenas Leitura]", textX, currentY + 4, 0xFFFF8A8A);
            } else {
                String idText = dto.getItem() != null && !dto.getItem().isEmpty() ? dto.getItem().getItem().toString() : "<vazio>";
                graphics.drawString(this.font, idText, textX, currentY + 2, 0xFFB8C8DE);
                String meta = String.format(Locale.ROOT, "Chance: %.2f%% | %d a %d | PK: %s", dto.getChance(), dto.getMin(), dto.getMax(), dto.isRequirePlayerKill() ? "Sim" : "Não");
                graphics.drawString(this.font, meta, textX, currentY + 14, 0xFF9FB0C5);

                // draw edit/delete icons on the right
                int iconW = 14;
                int editX = this.centerX + this.centerW - 40;
                int delX = this.centerX + this.centerW - 18;

                IconButton editBtn = new IconButton(editX, currentY, iconW, iconW, ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/pen-to-square-solid.png"), 12, 12, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A, b -> {
                });
                editBtn.render(graphics, mouseX, mouseY, partialTick);

                IconButton delBtn = new IconButton(delX, currentY, iconW, iconW, ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/xmark-solid.png"), 12, 12, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A, b -> {
                });
                delBtn.render(graphics, mouseX, mouseY, partialTick);
            }

            currentY += rowH;
        }

        graphics.disableScissor();
    }

    private void renderLeftButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.tableButtons.isEmpty()) return;
        graphics.enableScissor(this.leftX, this.listAreaY, this.leftX + this.leftW, this.listAreaY + this.listAreaH);
        for (TableButton tb : this.tableButtons) tb.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
    }

    private void applyLeftScroll() {
        for (TableButton tb : this.tableButtons) {
            Integer baseY = this.buttonOriginalY.get(tb);
            if (baseY != null) tb.setY(baseY - (int) this.leftScrollAmount);
        }
    }

    private void ensureLeftButtonVisible(TableButton button) {
        if (button == null) return;
        Integer baseY = this.buttonOriginalY.get(button);
        if (baseY == null) return;
        int topLimit = this.listAreaY;
        int bottomLimit = this.listAreaY + this.listAreaH - 10;
        int screenY = baseY - (int) this.leftScrollAmount;
        int buttonHeight = button.getHeight();

        if (screenY < topLimit) this.leftScrollAmount = baseY - topLimit;
        else if (screenY + buttonHeight > bottomLimit) this.leftScrollAmount = (baseY + buttonHeight) - bottomLimit;

        this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private boolean isInsideLeftList(double mouseX, double mouseY) {
        return mouseX >= this.leftX && mouseX < this.leftX + this.leftW && mouseY >= this.listAreaY && mouseY < this.listAreaY + this.listAreaH;
    }

    private boolean isInsideRightPanel(double mouseX, double mouseY) {
        return mouseX >= this.rightX && mouseX < this.rightX + this.rightW && mouseY >= this.rightY && mouseY < this.rightY + this.rightH;
    }

    private boolean isInsideLeftScrollbar(double mouseX, double mouseY) {
        int listRight = this.listAreaX + this.listAreaW;
        int rightEdge = this.leftX + this.leftW;
        int reserved = Math.max(0, rightEdge - listRight);
        int trackWidth = 6;
        if (reserved >= 10) trackWidth = Math.min(10, reserved - 2);
        int barX = listRight + (reserved - trackWidth) / 2;

        return mouseX >= barX && mouseX < barX + trackWidth && mouseY >= this.listAreaY && mouseY < this.listAreaY + this.listAreaH;
    }

    private void startLeftScrollbarDrag(double mouseY) {
        int trackTop = this.listAreaY;
        int trackHeight = this.listAreaH;
        int thumbHeight = getLeftScrollbarThumbHeight(trackHeight);
        int thumbY = getLeftScrollbarThumbY(trackTop, trackHeight, thumbHeight);

        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) this.leftScrollbarDragOffset = mouseY - thumbY;
        else this.leftScrollbarDragOffset = thumbHeight / 2.0D;

        this.leftScrollbarDragging = true;
        updateLeftScrollbarDrag(mouseY);
    }

    private void updateLeftScrollbarDrag(double mouseY) {
        if (this.maxLeftScroll <= 0) return;
        int trackTop = this.listAreaY;
        int trackHeight = this.listAreaH;
        int thumbHeight = getLeftScrollbarThumbHeight(trackHeight);
        int thumbY = (int) Math.round(mouseY - this.leftScrollbarDragOffset);
        setLeftScrollFromThumbY(thumbY, trackTop, trackHeight, thumbHeight);
    }

    private int getLeftScrollbarThumbHeight(int trackHeight) {
        return Math.max(20, (int) ((trackHeight / (float) (trackHeight + this.maxLeftScroll)) * trackHeight));
    }

    private int getLeftScrollbarThumbY(int trackTop, int trackHeight, int thumbHeight) {
        if (this.maxLeftScroll <= 0) return trackTop;
        int available = Math.max(1, trackHeight - thumbHeight);
        return trackTop + (int) ((this.leftScrollAmount / this.maxLeftScroll) * available);
    }

    private void setLeftScrollFromThumbY(int thumbY, int trackTop, int trackHeight, int thumbHeight) {
        if (this.maxLeftScroll <= 0) return;
        int available = Math.max(1, trackHeight - thumbHeight);
        int clamped = Mth.clamp(thumbY, trackTop, trackTop + available);
        double ratio = (clamped - trackTop) / (double) available;
        this.leftScrollAmount = ratio * this.maxLeftScroll;
        this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private void applyRightScroll() {
        for (AbstractWidget w : this.rightPanelWidgets) {
            Integer baseY = this.widgetOriginalY.get(w);
            if (baseY != null) w.setY(baseY - (int) this.rightScrollAmount);
        }
    }

    private void recalculateRightPanel() {
        int currentY = this.rightY + 28;
        int gap = 6;
        int rowX = this.rightX + 8;
        int rowW = this.rightW - 16;

        this.widgetOriginalY.clear();

        if (this.selectItemButton != null && this.selectItemButton.visible) {
            this.selectItemButton.setX(rowX);
            this.selectItemButton.setY(currentY);
            this.selectItemButton.setWidth(rowW);
            this.widgetOriginalY.put(this.selectItemButton, currentY);
            currentY += this.selectItemButton.getHeight() + gap;
        }

        if (this.chanceSlider != null && this.chanceSlider.visible) {
            this.chanceSlider.setX(rowX);
            this.chanceSlider.setY(currentY);
            this.chanceSlider.setWidth(rowW);
            this.widgetOriginalY.put(this.chanceSlider, currentY);
            currentY += this.chanceSlider.getHeight() + gap;
        }

        if (this.minSlider != null && this.minSlider.visible) {
            this.minSlider.setX(rowX);
            this.minSlider.setY(currentY);
            this.minSlider.setWidth(rowW);
            this.widgetOriginalY.put(this.minSlider, currentY);
            currentY += this.minSlider.getHeight() + gap;
        }

        if (this.maxSlider != null && this.maxSlider.visible) {
            this.maxSlider.setX(rowX);
            this.maxSlider.setY(currentY);
            this.maxSlider.setWidth(rowW);
            this.widgetOriginalY.put(this.maxSlider, currentY);
            currentY += this.maxSlider.getHeight() + gap;
        }

        if (this.requirePlayerKillCheckbox != null && this.requirePlayerKillCheckbox.visible) {
            this.requirePlayerKillCheckbox.setX(rowX);
            this.requirePlayerKillCheckbox.setY(currentY);
            this.widgetOriginalY.put(this.requirePlayerKillCheckbox, currentY);
            currentY += this.requirePlayerKillCheckbox.getHeight() + gap;
        }

        if (this.affectedByLootingCheckbox != null && this.affectedByLootingCheckbox.visible) {
            this.affectedByLootingCheckbox.setX(rowX);
            this.affectedByLootingCheckbox.setY(currentY);
            this.widgetOriginalY.put(this.affectedByLootingCheckbox, currentY);
            currentY += this.affectedByLootingCheckbox.getHeight() + gap;
        }

        if (this.injectButton != null && this.injectButton.visible) {
            this.injectButton.setX(rowX);
            this.injectButton.setY(currentY);
            this.injectButton.setWidth(rowW);
            this.widgetOriginalY.put(this.injectButton, currentY);
            currentY += this.injectButton.getHeight() + gap;
        }

        int visibleBottom = this.rightY + this.rightH - 8;
        this.maxRightScroll = Math.max(0, currentY - visibleBottom);
        this.rightScrollAmount = Mth.clamp(this.rightScrollAmount, 0.0D, this.maxRightScroll);
        applyRightScroll();
    }

    private <T extends AbstractWidget> T registerRightPanelWidget(T widget) {
        this.rightPanelWidgets.add(widget);
        this.widgetOriginalY.put(widget, widget.getY());
        this.addRenderableWidget(widget);
        return widget;
    }

    private void renderRightScrollbar(GuiGraphics graphics) {
        if (this.maxRightScroll <= 0) return;
        int top = this.rightY + 1;
        int bottom = this.rightY + this.rightH - 1;
        int trackHeight = Math.max(1, bottom - top);
        int barX = this.rightX + this.rightW - 7;

        graphics.fill(barX, top, barX + 5, bottom, 0xFF000000);

        int thumbHeight = Math.max(20, (int) ((trackHeight / (float) (trackHeight + this.maxRightScroll)) * trackHeight));
        int available = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + (int) ((this.rightScrollAmount / Math.max(1, this.maxRightScroll)) * available);
        graphics.fill(barX + 1, thumbY, barX + 4, thumbY + thumbHeight, 0xFF888888);
    }

    private void renderLeftScrollbar(GuiGraphics graphics) {
        if (this.maxLeftScroll <= 0) return;
        int listRight = this.listAreaX + this.listAreaW;
        int rightEdge = this.leftX + this.leftW;
        int reserved = Math.max(0, rightEdge - listRight);
        int trackWidth = 6;
        if (reserved >= 10) trackWidth = Math.min(10, reserved - 2);
        int barX = listRight + (reserved - trackWidth) / 2;

        int top = this.listAreaY;
        int bottom = this.listAreaY + this.listAreaH;
        int trackHeight = bottom - top;

        graphics.fill(barX, top, barX + trackWidth, bottom, 0xFF000000);

        int thumbHeight = getLeftScrollbarThumbHeight(trackHeight);
        int thumbY = getLeftScrollbarThumbY(top, trackHeight, thumbHeight);
        graphics.fill(barX + 1, thumbY, barX + trackWidth - 1, thumbY + thumbHeight, 0xFF888888);
    }

    //==================== Nested helper classes ====================
    private final class TableButton extends Button {
        private final ResourceLocation id;

        private TableButton(int x, int y, int width, int height, ResourceLocation id, OnPress onPress) {
            super(x, y, width, height, Component.literal(id.toString()), onPress, DEFAULT_NARRATION);
            this.id = id;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            boolean selected = Objects.equals(this.id, LootStudioScreen.this.selectedTableId);
            boolean hovered = this.isHoveredOrFocused();
            int rowColor = selected ? 0xAA2B4E72 : (hovered ? 0xAA263445 : 0x55202A36);
            int left = this.getX();
            int top = this.getY();
            int width = this.getWidth();
            graphics.fill(left, top, left + width, top + this.getHeight() - 1, rowColor);

            String namespace = this.id.getNamespace();
            String path = this.id.getPath();
            String prefix = namespace + ":";
            int prefixX = left + 4;
            int prefixW = Minecraft.getInstance().font.width(prefix);
            int pathX = prefixX + prefixW;
            int maxTextX = left + width - 6;
            int pathWidth = Math.max(0, maxTextX - pathX);
            String clippedPath = Minecraft.getInstance().font.plainSubstrByWidth(path, pathWidth);

            graphics.drawString(Minecraft.getInstance().font, prefix, prefixX, top + 5, 0xFFE8CC6D);
            graphics.drawString(Minecraft.getInstance().font, clippedPath, pathX, top + 5, 0xFFF2F6FF);
        }
    }

    private static final class DoubleParameterSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final int decimals;
        private final DoubleConsumer onChange;

        private DoubleParameterSlider(int x, int y, int width, int height, String label, double min, double max, double initial, int decimals, DoubleConsumer onChange) {
            super(x, y, width, height, Component.literal(""), normalize(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.decimals = decimals;
            this.onChange = onChange;
            updateMessage();
        }

        private double getCurrentValue() {
            double raw = this.min + (this.max - this.min) * this.value;
            double factor = Math.pow(10.0D, this.decimals);
            return Math.round(raw * factor) / factor;
        }

        @Override
        protected void updateMessage() {
            String fmt = "%s: %." + this.decimals + "f";
            this.setMessage(Component.literal(String.format(Locale.ROOT, fmt, this.label, getCurrentValue())));
        }

        @Override
        protected void applyValue() {
            updateMessage();
            if (this.onChange != null) this.onChange.accept(getCurrentValue());
        }

        private static double normalize(double value, double min, double max) {
            if (max <= min) return 0.0D;
            return Mth.clamp((value - min) / (max - min), 0.0D, 1.0D);
        }
    }

    private static final class IntParameterSlider extends AbstractSliderButton {
        private final String label;
        private final int min;
        private final int max;
        private final IntConsumer onChange;

        private IntParameterSlider(int x, int y, int width, int height, String label, int min, int max, int initial, IntConsumer onChange) {
            super(x, y, width, height, Component.literal(""), normalize(initial, min, max));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private int getCurrentValue() {
            if (this.max <= this.min) return this.min;
            return this.min + (int) Math.round((this.max - this.min) * this.value);
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(this.label + ": " + getCurrentValue()));
        }

        @Override
        protected void applyValue() {
            updateMessage();
            if (this.onChange != null) this.onChange.accept(getCurrentValue());
        }

        private static double normalize(int value, int min, int max) {
            if (max <= min) return 0.0D;
            return Mth.clamp((double) (value - min) / (double) (max - min), 0.0D, 1.0D);
        }
    }

    // left scrollbar state
    private boolean leftScrollbarDragging = false;
    private double leftScrollbarDragOffset = 0.0D;

    @Override
    public boolean isPauseScreen() { return false; }
}
