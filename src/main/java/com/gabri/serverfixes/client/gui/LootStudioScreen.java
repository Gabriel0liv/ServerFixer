package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.RequestLootDataPacket;
import com.gabri.serverfixes.network.RequestLootTablePacket;
import com.gabri.serverfixes.network.ResetLootTablePacket;
import com.gabri.serverfixes.network.SaveLootTablePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
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
    private IconButton filterButton;
    private boolean filterOverlayOpen = false;

    // left list widgets
    private double leftScrollAmount = 0.0D;
    private int maxLeftScroll = 0;
    private final List<TableButton> tableButtons = new ArrayList<>();
    private final Map<TableButton, Integer> buttonOriginalY = new HashMap<>();
    private int selectedTableIndex = 0;
    private ResourceLocation selectedTableId;

    // center loot list
    private final List<LootDropDTO> lootDrops = new ArrayList<>();
    private double centerScrollAmount = 0.0D;
    private int maxCenterScroll = 0;
    private boolean isDirty = false;
    private boolean searchMod = true;
    private boolean searchEntity = true;
    private boolean searchChest = true;
    private boolean searchItem = true;
    private boolean suppressSearchRequest = false;

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
    private Button addNewDropButton;
    private SaveApplyButton saveApplyButton;
    private IconButton resetButton;
    private LootDropEditorModal editorModal;

    private int editingDropIndex = -1;

    public LootStudioScreen() {
        super(Component.literal("Loot Studio"));
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

        this.centerScrollAmount = 0.0D;
        this.maxCenterScroll = 0;

        this.rightPanelWidgets.clear();
        this.widgetOriginalY.clear();
        this.rightScrollAmount = 0.0D;
        this.maxRightScroll = 0;

        updateLayout();

        int filterBtnSize = 14;
        int filterBtnGap = 4;
        int searchWidth = Math.max(30, this.listAreaW - (filterBtnSize + filterBtnGap));

        this.searchBox = new SelectableEditBox(this.font, this.leftX + 8, this.leftY + 24, searchWidth, 18, Component.literal("Pesquisar"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setResponder(value -> applySearchFilter());
        this.addRenderableWidget(this.searchBox);

        this.filterButton = new IconButton(
            this.searchBox.getX() + this.searchBox.getWidth() + filterBtnGap,
            this.searchBox.getY() + 2,
            filterBtnSize,
            filterBtnSize,
            ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/indent-solid.png"),
            12, 12,
            0xFF2E3A42,
            0xFF3E4A52,
            0xFF1E262A,
            btn -> this.filterOverlayOpen = !this.filterOverlayOpen
        );
        this.addRenderableWidget(this.filterButton);

        applySearchFilter();

        // Right panel actions (manual workflow)
        int actionW = this.rightW - 16;

        this.addNewDropButton = Button.builder(Component.literal("Adicionar Novo Drop"), btn -> {
            if (this.editorModal != null) {
                this.editorModal.openForCreate();
            }
        }).bounds(0, 0, actionW, 20).build();
        registerRightPanelWidget(this.addNewDropButton);

        this.saveApplyButton = new SaveApplyButton(0, 0, actionW, 22, Component.literal("SALVAR E APLICAR"), btn -> saveCurrentTableToServer());
        registerRightPanelWidget(this.saveApplyButton);

        this.resetButton = new IconButton(0, 0, 12, 12,
            ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/reset.png"),
            12, 12, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A,
            btn -> requestResetTable());
        this.addRenderableWidget(this.resetButton);

        this.editorModal = new LootDropEditorModal();
        this.editorModal.init();

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
        String query = this.searchBox != null ? this.searchBox.getValue().trim() : "";

        if (!this.suppressSearchRequest) {
            NetworkHandler.sendToServer(new RequestLootDataPacket(query, this.searchMod, this.searchEntity, this.searchChest, this.searchItem));
        }

        this.filteredTables.clear();
        this.filteredTables.addAll(this.allTables);

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

    public void applyLootTableListFromServer(List<ResourceLocation> ids) {
        this.allTables.clear();
        if (ids != null) {
            for (ResourceLocation id : ids) {
                if (id != null) {
                    this.allTables.add(id);
                }
            }
        }

        this.allTables.sort(Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        this.suppressSearchRequest = true;
        applySearchFilter();
        this.suppressSearchRequest = false;
    }

    public void applyLootDropsFromServer(ResourceLocation tableId, List<LootDropDTO> drops) {
        if (tableId == null || !Objects.equals(tableId, this.selectedTableId)) {
            return;
        }

        this.lootDrops.clear();
        this.centerScrollAmount = 0.0D;
        if (drops != null) {
            for (LootDropDTO dto : drops) {
                if (dto != null) {
                    this.lootDrops.add(copyDrop(dto));
                }
            }
        }

        this.isDirty = false;
        this.editingDropIndex = -1;
    }

    private void rebuildFilterOptions() {
        // Legacy method kept for compatibility with prior flow.
    }

    private String extractCategory(ResourceLocation id) {
        if (id == null) return "misc";
        String path = id.getPath();
        int slash = path.indexOf('/');
        return (slash > 0 ? path.substring(0, slash) : path).toLowerCase(Locale.ROOT);
    }

    private void renderFilterOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!this.filterOverlayOpen || this.font == null || this.filterButton == null) {
            return;
        }

        int x = this.filterButton.getX() - 4;
        int y = this.filterButton.getY() + this.filterButton.getHeight() + 4;
        int w = Math.min(220, this.leftW - 16);

        int h = 92;

        graphics.fill(x, y, x + w, y + h, 0xFB1A2434);
        graphics.fill(x, y, x + w, y + 1, 0xFF4E6B8D);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF4E6B8D);
        graphics.fill(x, y, x + 1, y + h, 0xFF4E6B8D);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF4E6B8D);

        int rowY = y + 8;
        graphics.drawString(this.font, "Contexto da Busca", x + 6, rowY, 0xFFF4F7FF);
        rowY += 16;

        drawOverlayCheckbox(graphics, x + 8, rowY, this.searchMod);
        graphics.drawString(this.font, "Mod (namespace)", x + 24, rowY + 2, 0xFFD4E0F0);
        rowY += 16;

        drawOverlayCheckbox(graphics, x + 8, rowY, this.searchEntity);
        graphics.drawString(this.font, "Entidade (entities/)", x + 24, rowY + 2, 0xFFD4E0F0);
        rowY += 16;

        drawOverlayCheckbox(graphics, x + 8, rowY, this.searchChest);
        graphics.drawString(this.font, "Bau (chests/)", x + 24, rowY + 2, 0xFFD4E0F0);
        rowY += 16;

        drawOverlayCheckbox(graphics, x + 8, rowY, this.searchItem);
        graphics.drawString(this.font, "Item/Tag (conteudo)", x + 24, rowY + 2, 0xFFD4E0F0);
    }

    private void drawOverlayCheckbox(GuiGraphics graphics, int x, int y, boolean selected) {
        graphics.fill(x, y, x + 12, y + 12, 0xFF4E6B8D);
        graphics.fill(x + 1, y + 1, x + 11, y + 11, 0xFF1A2434);
        if (selected) {
            graphics.fill(x + 3, y + 3, x + 9, y + 9, 0xFF8BD3FF);
        }
    }

    private boolean handleFilterOverlayClick(double mouseX, double mouseY, int button) {
        if (!this.filterOverlayOpen || button != 0 || this.filterButton == null) {
            return false;
        }

        int x = this.filterButton.getX() - 4;
        int y = this.filterButton.getY() + this.filterButton.getHeight() + 4;
        int w = Math.min(220, this.leftW - 16);
        int h = 92;

        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            if (!(mouseX >= this.filterButton.getX() && mouseX < this.filterButton.getX() + this.filterButton.getWidth()
                && mouseY >= this.filterButton.getY() && mouseY < this.filterButton.getY() + this.filterButton.getHeight())) {
                this.filterOverlayOpen = false;
            }
            return false;
        }

        int rowY = y + 24;
        if (mouseX >= x + 8 && mouseX < x + w - 8) {
            if (mouseY >= rowY && mouseY < rowY + 12) {
                this.searchMod = !this.searchMod;
                applySearchFilter();
                return true;
            }
            rowY += 16;

            if (mouseY >= rowY && mouseY < rowY + 12) {
                this.searchEntity = !this.searchEntity;
                applySearchFilter();
                return true;
            }
            rowY += 16;

            if (mouseY >= rowY && mouseY < rowY + 12) {
                this.searchChest = !this.searchChest;
                applySearchFilter();
                return true;
            }
            rowY += 16;

            if (mouseY >= rowY && mouseY < rowY + 12) {
                this.searchItem = !this.searchItem;
                applySearchFilter();
                return true;
            }
        }

        return true;
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
        if (Objects.equals(this.selectedTableId, id)) {
            return;
        }

        this.selectedTableId = id;
        this.lootDrops.clear();
        this.editingDropIndex = -1;

        if (id != null) {
            NetworkHandler.sendToServer(new RequestLootTablePacket(id));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.editorModal != null && this.editorModal.isOpen()) {
            if (this.editorModal.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return true;
        }

        if (handleFilterOverlayClick(mouseX, mouseY, button)) {
            return true;
        }

        if (this.maxLeftScroll > 0 && isInsideLeftScrollbar(mouseX, mouseY)) {
            startLeftScrollbarDrag(mouseY);
            return true;
        }
        if (isInsideLeftList(mouseX, mouseY)) {
            for (TableButton tb : this.tableButtons) {
                if (tb.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }

        if (handleCenterListClick(mouseX, mouseY)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.editorModal != null && this.editorModal.isOpen()) {
            return this.editorModal.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.editorModal != null && this.editorModal.isOpen()) {
            return this.editorModal.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
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
        if (this.editorModal != null && this.editorModal.isOpen()) {
            return true;
        }

        if (isInsideLeftList(mouseX, mouseY) && this.maxLeftScroll > 0) {
            this.leftScrollAmount -= scrollY * 15.0D;
            this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
            applyLeftScroll();
            return true;
        }
        if (isInsideCenterList(mouseX, mouseY) && this.maxCenterScroll > 0) {
            this.centerScrollAmount -= scrollY * 15.0D;
            this.centerScrollAmount = Mth.clamp(this.centerScrollAmount, 0.0D, this.maxCenterScroll);
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

    private boolean handleCenterListClick(double mouseX, double mouseY) {
        if (!isInsideCenterList(mouseX, mouseY)) {
            return false;
        }

        int startY = this.centerY + 28;
        int rowH = 28;
        for (int i = 0; i < this.lootDrops.size(); i++) {
            int rowY = startY + (i * rowH) - (int) this.centerScrollAmount;
            if (mouseY < rowY || mouseY >= rowY + rowH) {
                continue;
            }

            LootDropDTO dto = this.lootDrops.get(i);
            if (dto == null) {
                return true;
            }

            int editX = this.centerX + this.centerW - 40;
            int delX = this.centerX + this.centerW - 18;

            // delete always works
            if (mouseX >= delX && mouseX < delX + 14 && mouseY >= rowY && mouseY < rowY + 14) {
                this.lootDrops.remove(i);
                this.editingDropIndex = -1;
                this.isDirty = true;
                return true;
            }

            // edit always opens modal for any drop
            if (mouseX >= editX && mouseX < editX + 14 && mouseY >= rowY && mouseY < rowY + 14) {
                if (this.editorModal != null) this.editorModal.openForEdit(i, dto);
                return true;
            }

            // clicking elsewhere selects the row for focus but allows editing/deleting later
            this.editingDropIndex = i;
            return true;
        }

        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null || this.font == null) return;

        updateLayout();
        recalculateRightPanel();

        if (this.filterButton != null && this.searchBox != null) {
            this.filterButton.setX(this.searchBox.getX() + this.searchBox.getWidth() + 4);
            this.filterButton.setY(this.searchBox.getY() + 2);
        }

        if (this.resetButton != null) {
            this.resetButton.setX(this.centerX + this.centerW - 20);
            this.resetButton.setY(this.centerY + 6);
            this.resetButton.visible = this.selectedTableId != null;
            this.resetButton.active = this.selectedTableId != null;
        }

        graphics.fill(0, 0, this.width, this.height, 0xDD000000);
        graphics.fill(this.leftX, this.leftY, this.leftX + this.leftW, this.leftY + this.leftH, 0x44242D3D);
        graphics.fill(this.centerX, this.centerY, this.centerX + this.centerW, this.centerY + this.centerH, 0x66202A39);
        graphics.fill(this.rightX, this.rightY, this.rightX + this.rightW, this.rightY + this.rightH, 0x44242D3D);

        GuiLayoutUtils.drawPanel(graphics, this.leftX, this.leftY, this.leftW, this.leftH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.centerX, this.centerY, this.centerW, this.centerH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.rightX, this.rightY, this.rightW, this.rightH, 0x001A2434, 0xFF4E6B8D);

        graphics.drawString(this.font, "Loot Tables", this.leftX + 8, this.leftY + 8, 0xFFF4F7FF);
        String selectedTitle = this.selectedTableId != null ? this.selectedTableId.toString() : "<nenhuma>";
        graphics.drawString(this.font, "Loot de: " + this.font.plainSubstrByWidth(selectedTitle, Math.max(40, this.centerW - 44)), this.centerX + 8, this.centerY + 8, 0xFFF4F7FF);
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

        Map<AbstractWidget, Boolean> modalVisibilityMemory = new HashMap<>();
        boolean hideModalBeforeSuper = this.editorModal != null && this.editorModal.isOpen();
        if (hideModalBeforeSuper) {
            for (AbstractWidget widget : this.editorModal.getWidgets()) {
                modalVisibilityMemory.put(widget, widget.visible);
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
        renderCenterScrollbar(graphics);

        if (this.filterOverlayOpen) {
            RenderSystem.disableDepthTest();
            // ensure overlay draws above previously rendered items
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            renderFilterOverlay(graphics, mouseX, mouseY);
            RenderSystem.enableDepthTest();
        } else {
            renderFilterOverlay(graphics, mouseX, mouseY);
        }

        if (hideModalBeforeSuper) {
            for (AbstractWidget widget : this.editorModal.getWidgets()) {
                widget.visible = modalVisibilityMemory.getOrDefault(widget, true);
            }
        }
        if (this.editorModal != null && this.editorModal.isOpen()) {
            RenderSystem.disableDepthTest();
            // clear any depth values written by earlier item renders so modal is always on top
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            this.editorModal.render(graphics, mouseX, mouseY, partialTick);
            RenderSystem.enableDepthTest();
        }
    }

    private void renderCenterLootList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int startX = this.centerX + 8;
        int startY = this.centerY + 28;
        int rowH = 28;
        int contentHeight = this.lootDrops.size() * rowH;
        int visibleHeight = Math.max(1, (this.centerY + this.centerH - 8) - startY);
        this.maxCenterScroll = Math.max(0, contentHeight - visibleHeight);
        this.centerScrollAmount = Mth.clamp(this.centerScrollAmount, 0.0D, this.maxCenterScroll);
        int currentY = startY - (int) this.centerScrollAmount;

        graphics.enableScissor(this.centerX, this.centerY, this.centerX + this.centerW, this.centerY + this.centerH);

        for (int i = 0; i < this.lootDrops.size(); i++) {
            LootDropDTO dto = this.lootDrops.get(i);
            int iconX = startX;
            int iconY = currentY;

            int rowColor = i == this.editingDropIndex ? 0x5533618E : 0x3324303F;
            graphics.fill(this.centerX + 6, currentY - 1, this.centerX + this.centerW - 6, currentY + rowH - 2, rowColor);

            ItemStack rowIcon = dto.getItem();
            if (dto.isEmptyDrop()) {
                rowIcon = new ItemStack(Items.BARRIER);
            } else if ((rowIcon == null || rowIcon.isEmpty()) && dto.getTag() != null) {
                rowIcon = new ItemStack(Items.NAME_TAG);
            } else if ((rowIcon == null || rowIcon.isEmpty()) && dto.getReferenceTable() != null) {
                rowIcon = new ItemStack(Items.CHEST);
            }

            if (rowIcon != null && !rowIcon.isEmpty()) {
                graphics.renderItem(rowIcon, iconX, iconY);
                graphics.renderItemDecorations(this.font, rowIcon, iconX, iconY);
            }

            int textX = iconX + 20;

            // Only draw a textual label when we have a meaningful tag/table or a valid item.
            boolean drawDisplayName = true;
            String displayName = "";
            if (dto.isEmptyDrop()) {
                // show only the barrier icon for explicit empty drops
                drawDisplayName = false;
            } else if (dto.getTag() != null) {
                displayName = "#" + dto.getTag();
            } else if (dto.getReferenceTable() != null) {
                displayName = "@" + dto.getReferenceTable();
            } else if (dto.getItem() != null && !dto.getItem().isEmpty()) {
                displayName = dto.getItem().getHoverName().getString();
            } else {
                // invalid/unknown item without tag/ref: don't draw the id text
                drawDisplayName = false;
            }

            int displayColor = 0xFFB8C8DE;
            if (drawDisplayName) {
                graphics.drawString(this.font, displayName, textX, currentY + 2, displayColor);
            }

            String enchantMeta = "";
            if (dto.isEnchantWithLevels()) {
                LootDropDTO.Range levels = dto.getEnchantLevelsRange() != null ? dto.getEnchantLevelsRange() : new LootDropDTO.Range(10, 30);
                enchantMeta = " | Enc Niveis: " + levels.getMin() + "-" + levels.getMax();
            } else if (dto.isEnchantRandomly()) {
                enchantMeta = " | Enc Aleatorio";
            }
            String meta = String.format(Locale.ROOT, "Chance: %.2f%% | %d a %d | PK: %s%s", dto.getChance(), dto.getMin(), dto.getMax(), dto.isRequirePlayerKill() ? "Sim" : "Nao", enchantMeta);
            // If we didn't draw a display name, move the meta line up to occupy the primary text line
            int metaY = drawDisplayName ? (currentY + 14) : (currentY + 2);
            graphics.drawString(this.font, meta, textX, metaY, 0xFF9FB0C5);

            // draw edit/delete icons on the right for all drops
            int iconW = 14;
            int editX = this.centerX + this.centerW - 40;
            int delX = this.centerX + this.centerW - 18;

            IconButton editBtn = new IconButton(editX, currentY, iconW, iconW, ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/pen-to-square-solid.png"), 12, 12, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A, b -> {
            });
            editBtn.render(graphics, mouseX, mouseY, partialTick);

            IconButton delBtn = new IconButton(delX, currentY, 14, 14, ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/xmark-solid.png"), 12, 12, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A, b -> {
            });
            delBtn.render(graphics, mouseX, mouseY, partialTick);

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

    private boolean isInsideCenterList(double mouseX, double mouseY) {
        return mouseX >= this.centerX + 6
            && mouseX < this.centerX + this.centerW - 6
            && mouseY >= this.centerY + 28
            && mouseY < this.centerY + this.centerH - 6;
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

        if (this.addNewDropButton != null && this.addNewDropButton.visible) {
            this.addNewDropButton.setX(rowX);
            this.addNewDropButton.setY(currentY);
            this.addNewDropButton.setWidth(rowW);
            this.widgetOriginalY.put(this.addNewDropButton, currentY);
            currentY += this.addNewDropButton.getHeight() + gap;
        }

        if (this.saveApplyButton != null && this.saveApplyButton.visible) {
            this.saveApplyButton.setX(rowX);
            this.saveApplyButton.setY(currentY);
            this.saveApplyButton.setWidth(rowW);
            this.saveApplyButton.setDirty(this.isDirty);
            this.widgetOriginalY.put(this.saveApplyButton, currentY);
            currentY += this.saveApplyButton.getHeight() + gap;
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

    private void renderCenterScrollbar(GuiGraphics graphics) {
        if (this.maxCenterScroll <= 0) return;

        int top = this.centerY + 28;
        int bottom = this.centerY + this.centerH - 6;
        int trackHeight = Math.max(1, bottom - top);
        int barX = this.centerX + this.centerW - 7;

        graphics.fill(barX, top, barX + 5, bottom, 0xFF000000);

        int thumbHeight = Math.max(20, (int) ((trackHeight / (float) (trackHeight + this.maxCenterScroll)) * trackHeight));
        int available = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + (int) ((this.centerScrollAmount / Math.max(1, this.maxCenterScroll)) * available);
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

    private void saveCurrentTableToServer() {
        if (this.selectedTableId == null || !this.isDirty) {
            return;
        }
        NetworkHandler.sendToServer(new SaveLootTablePacket(this.selectedTableId, this.lootDrops));
        this.isDirty = false;
    }

    private void requestResetTable() {
        if (this.selectedTableId == null) {
            return;
        }
        NetworkHandler.sendToServer(new ResetLootTablePacket(this.selectedTableId));
    }

    private LootDropDTO copyDrop(LootDropDTO dto) {
        if (dto == null) {
            return new LootDropDTO(ItemStack.EMPTY, 0.0D, 1, 1, false, false, true);
        }
        return new LootDropDTO(
            dto.getItem() != null ? dto.getItem().copy() : ItemStack.EMPTY,
            dto.getChance(),
            dto.getMin(),
            dto.getMax(),
            dto.isRequirePlayerKill(),
            dto.isAffectedByLooting(),
            dto.isComplex(),
            dto.getTag(),
            dto.getReferenceTable(),
            dto.isEnchantRandomly(),
            dto.isEnchantWithLevels(),
            dto.getEnchantLevelsRange() != null
                ? new LootDropDTO.Range(dto.getEnchantLevelsRange().getMin(), dto.getEnchantLevelsRange().getMax())
                : null,
            dto.getPotionId(),
            dto.getNbtData(),
            dto.getCustomNameJson(),
            dto.isExplorationMap(),
            dto.isEmptyDrop()
        );
    }

    private static final class SaveApplyButton extends Button {
        private boolean dirty;

        private SaveApplyButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        private void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();

            int baseColor = this.dirty ? 0xFF2D6A3E : 0xFF2E3A42;
            if (this.dirty) {
                double t = (System.currentTimeMillis() % 900L) / 900.0D;
                int pulse = (int) (20.0D + 35.0D * Math.sin(t * Math.PI * 2.0D));
                int r = Math.min(255, 0x2D + pulse / 3);
                int g = Math.min(255, 0x6A + pulse / 2);
                int b = Math.min(255, 0x3E + pulse / 4);
                baseColor = (0xFF << 24) | (r << 16) | (g << 8) | b;
            }

            int fill = !this.active ? 0xFF2B2B2B : (this.isHoveredOrFocused() ? lighten(baseColor, 0x18) : baseColor);
            int border = this.dirty ? 0xFF8EE6A0 : 0xFF4E6B8D;

            graphics.fill(x, y, x + w, y + h, fill);
            graphics.fill(x, y, x + w, y + 1, border);
            graphics.fill(x, y + h - 1, x + w, y + h, border);
            graphics.fill(x, y, x + 1, y + h, border);
            graphics.fill(x + w - 1, y, x + w, y + h, border);

            int textColor = 0xFFF4F7FF;
            int textX = x + (w - Minecraft.getInstance().font.width(this.getMessage())) / 2;
            int textY = y + (h - 8) / 2;
            graphics.drawString(Minecraft.getInstance().font, this.getMessage(), textX, textY, textColor);
        }

        private static int lighten(int argb, int delta) {
            int a = (argb >>> 24) & 0xFF;
            int r = Math.min(255, ((argb >>> 16) & 0xFF) + delta);
            int g = Math.min(255, ((argb >>> 8) & 0xFF) + delta);
            int b = Math.min(255, (argb & 0xFF) + delta);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    private final class LootDropEditorModal {
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private SelectableEditBox idInput;
        private DoubleParameterSlider chanceSlider;
        private IntParameterSlider minSlider;
        private IntParameterSlider maxSlider;
        private TextureCheckbox requirePlayerKillCheckbox;
        private TextureCheckbox affectedByLootingCheckbox;
        private TextureCheckbox enchantRandomlyCheckbox;
        private TextureCheckbox enchantWithLevelsCheckbox;
        private IntParameterSlider enchantLevelsMinSlider;
        private IntParameterSlider enchantLevelsMaxSlider;
        private Button confirmButton;
        private Button cancelButton;

        private boolean open = false;
        private int editingIndex = -1;

        private ItemStack previewItem = new ItemStack(Items.DIAMOND);
        private boolean previewValid = true;
        private String previewHint = "";

        private int modalX, modalY, modalW, modalH;
        private int previewSlotX, previewSlotY, previewSlotSize;

        private void init() {
            this.modalW = 340;
            this.modalH = 292;
            this.previewSlotSize = 24;

            this.idInput = new SelectableEditBox(LootStudioScreen.this.font, 0, 0, 10, 18, Component.literal("item/tag"));
            this.idInput.setMaxLength(140);
            this.idInput.setResponder(value -> refreshPreview());

            this.chanceSlider = new DoubleParameterSlider(0, 0, 10, 18, "Chance", 0.0D, 100.0D, 50.0D, 1, value -> {});
            this.minSlider = new IntParameterSlider(0, 0, 10, 18, "Min", 1, 64, 1, value -> {});
            this.maxSlider = new IntParameterSlider(0, 0, 10, 18, "Max", 1, 64, 1, value -> {});
            this.requirePlayerKillCheckbox = new TextureCheckbox(0, 0, false, Component.literal("PK"), v -> {});
            this.affectedByLootingCheckbox = new TextureCheckbox(0, 0, false, Component.literal("Looting"), v -> {});
            this.enchantRandomlyCheckbox = new TextureCheckbox(0, 0, false, Component.literal("EnchantRand"), v -> {});
            this.enchantWithLevelsCheckbox = new TextureCheckbox(0, 0, false, Component.literal("EnchantLvl"), v -> updateEnchantControlsVisibility());
            this.enchantLevelsMinSlider = new IntParameterSlider(0, 0, 10, 18, "Enc Min", 1, 100, 10, value -> {});
            this.enchantLevelsMaxSlider = new IntParameterSlider(0, 0, 10, 18, "Enc Max", 1, 100, 30, value -> {});

            this.confirmButton = Button.builder(Component.literal("Confirmar"), btn -> confirm()).bounds(0, 0, 80, 20).build();
            this.cancelButton = Button.builder(Component.literal("Cancelar"), btn -> close()).bounds(0, 0, 80, 20).build();

            register(this.idInput);
            register(this.chanceSlider);
            register(this.minSlider);
            register(this.maxSlider);
            register(this.requirePlayerKillCheckbox);
            register(this.affectedByLootingCheckbox);
            register(this.enchantRandomlyCheckbox);
            register(this.enchantWithLevelsCheckbox);
            register(this.enchantLevelsMinSlider);
            register(this.enchantLevelsMaxSlider);
            register(this.confirmButton);
            register(this.cancelButton);

            setWidgetsVisible(false);
        }

        private void register(AbstractWidget widget) {
            this.widgets.add(widget);
            LootStudioScreen.this.addRenderableWidget(widget);
        }

        private List<AbstractWidget> getWidgets() {
            return this.widgets;
        }

        private boolean isOpen() {
            return this.open;
        }

        private void openForCreate() {
            this.editingIndex = -1;
            this.idInput.setValue(getCarriedOrFallbackId());
            this.chanceSlider.setCurrentValue(50.0D);
            this.minSlider.setCurrentValue(1);
            this.maxSlider.setCurrentValue(1);
            this.requirePlayerKillCheckbox.setSelected(false);
            this.affectedByLootingCheckbox.setSelected(false);
            this.enchantRandomlyCheckbox.setSelected(false);
            this.enchantWithLevelsCheckbox.setSelected(false);
            this.enchantLevelsMinSlider.setCurrentValue(10);
            this.enchantLevelsMaxSlider.setCurrentValue(30);
            refreshPreview();
            this.open = true;
            updateLayout();
            setWidgetsVisible(true);
            this.idInput.setFocused(true);
            LootStudioScreen.this.setFocused(this.idInput);
        }

        private void openForEdit(int index, LootDropDTO dto) {
            this.editingIndex = index;

            if (dto != null && dto.getTag() != null) {
                this.idInput.setValue("#" + dto.getTag());
            } else if (dto != null && dto.getReferenceTable() != null) {
                this.idInput.setValue("@" + dto.getReferenceTable());
            } else if (dto != null && dto.getItem() != null && !dto.getItem().isEmpty()) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(dto.getItem().getItem());
                this.idInput.setValue(key != null ? key.toString() : "minecraft:diamond");
            } else {
                this.idInput.setValue(getCarriedOrFallbackId());
            }

            this.chanceSlider.setCurrentValue(dto != null ? dto.getChance() : 50.0D);
            this.minSlider.setCurrentValue(dto != null ? dto.getMin() : 1);
            this.maxSlider.setCurrentValue(dto != null ? dto.getMax() : 1);
            this.requirePlayerKillCheckbox.setSelected(dto != null && dto.isRequirePlayerKill());
            this.affectedByLootingCheckbox.setSelected(dto != null && dto.isAffectedByLooting());
            this.enchantRandomlyCheckbox.setSelected(dto != null && dto.isEnchantRandomly());
            this.enchantWithLevelsCheckbox.setSelected(dto != null && dto.isEnchantWithLevels());
            LootDropDTO.Range levels = dto != null && dto.getEnchantLevelsRange() != null ? dto.getEnchantLevelsRange() : new LootDropDTO.Range(10, 30);
            this.enchantLevelsMinSlider.setCurrentValue(levels.getMin());
            this.enchantLevelsMaxSlider.setCurrentValue(levels.getMax());

            refreshPreview();
            this.open = true;
            updateLayout();
            setWidgetsVisible(true);
            this.idInput.setFocused(true);
            LootStudioScreen.this.setFocused(this.idInput);
        }

        private boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!this.open) return false;
            updateLayout();

            for (AbstractWidget widget : this.widgets) {
                if (widget.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }

            return true;
        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!this.open) return false;

            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                close();
                return true;
            }

            for (AbstractWidget widget : this.widgets) {
                if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            return false;
        }

        public boolean charTyped(char codePoint, int modifiers) {
            if (!this.open) return false;

            for (AbstractWidget widget : this.widgets) {
                if (widget.charTyped(codePoint, modifiers)) {
                    return true;
                }
            }
            return false;
        }

        private void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!this.open || LootStudioScreen.this.font == null) return;

            updateLayout();

            // less transparent overlay so modal content doesn't blend with underlying text
            graphics.fill(0, 0, LootStudioScreen.this.width, LootStudioScreen.this.height, 0xCC000000);
            // make modal panel fully opaque to avoid bleed-through
            graphics.fill(this.modalX, this.modalY, this.modalX + this.modalW, this.modalY + this.modalH, 0xFF1A2434);
            graphics.fill(this.modalX, this.modalY, this.modalX + this.modalW, this.modalY + 1, 0xFF4E6B8D);
            graphics.fill(this.modalX, this.modalY + this.modalH - 1, this.modalX + this.modalW, this.modalY + this.modalH, 0xFF4E6B8D);
            graphics.fill(this.modalX, this.modalY, this.modalX + 1, this.modalY + this.modalH, 0xFF4E6B8D);
            graphics.fill(this.modalX + this.modalW - 1, this.modalY, this.modalX + this.modalW, this.modalY + this.modalH, 0xFF4E6B8D);

            graphics.drawString(LootStudioScreen.this.font, "Editor de Drop", this.modalX + 8, this.modalY + 8, 0xFFF4F7FF);
            graphics.drawString(LootStudioScreen.this.font, "ID do Item/Tag (ex: minecraft:diamond, #forge:ingots)", this.modalX + 8, this.modalY + 24, 0xFFD4E0F0);

            for (AbstractWidget widget : this.widgets) {
                if (widget == this.confirmButton || widget == this.cancelButton) {
                    continue;
                }
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
            this.confirmButton.render(graphics, mouseX, mouseY, partialTick);
            this.cancelButton.render(graphics, mouseX, mouseY, partialTick);

            // highlight the id input when the parsed preview is invalid
            if (!this.previewValid && this.idInput != null) {
                int ix = this.idInput.getX();
                int iy = this.idInput.getY();
                int iw = this.idInput.getWidth();
                int ih = this.idInput.getHeight();
                int border = 0xFFFF7B7B; // red
                graphics.fill(ix, iy, ix + iw, iy + 1, border);
                graphics.fill(ix, iy + ih - 1, ix + iw, iy + ih, border);
                graphics.fill(ix, iy, ix + 1, iy + ih, border);
                graphics.fill(ix + iw - 1, iy, ix + iw, iy + ih, border);
            }

            graphics.fill(this.previewSlotX, this.previewSlotY, this.previewSlotX + this.previewSlotSize, this.previewSlotY + this.previewSlotSize, 0xFF4E6B8D);
            graphics.fill(this.previewSlotX + 1, this.previewSlotY + 1, this.previewSlotX + this.previewSlotSize - 1, this.previewSlotY + this.previewSlotSize - 1, 0xFF1A2434);
            if (this.previewItem != null && !this.previewItem.isEmpty()) {
                graphics.renderItem(this.previewItem, this.previewSlotX + 4, this.previewSlotY + 4);
                graphics.renderItemDecorations(LootStudioScreen.this.font, this.previewItem, this.previewSlotX + 4, this.previewSlotY + 4);
            }

            int hintColor = this.previewValid ? 0xFF9FC4EA : 0xFFFF7B7B;
            graphics.drawString(LootStudioScreen.this.font, this.previewHint, this.modalX + 12, this.modalY + 68, hintColor);
            graphics.drawString(LootStudioScreen.this.font, "Exigir Morte por Jogador", this.requirePlayerKillCheckbox.getX() + 18, this.requirePlayerKillCheckbox.getY() + 3, 0xFFD4E0F0);
            graphics.drawString(LootStudioScreen.this.font, "Afetado por Pilhagem", this.affectedByLootingCheckbox.getX() + 18, this.affectedByLootingCheckbox.getY() + 3, 0xFFD4E0F0);
            graphics.drawString(LootStudioScreen.this.font, "Encantamento Aleatorio", this.enchantRandomlyCheckbox.getX() + 18, this.enchantRandomlyCheckbox.getY() + 3, 0xFFD4E0F0);
            graphics.drawString(LootStudioScreen.this.font, "Encantamento por Niveis", this.enchantWithLevelsCheckbox.getX() + 18, this.enchantWithLevelsCheckbox.getY() + 3, 0xFFD4E0F0);
        }

        private void updateLayout() {
            this.modalX = (LootStudioScreen.this.width - this.modalW) / 2;
            this.modalY = (LootStudioScreen.this.height - this.modalH) / 2;

            int bodyX = this.modalX + 12;
            int bodyY = this.modalY + 42;

            this.idInput.setX(bodyX);
            this.idInput.setY(bodyY);
            this.idInput.setWidth(this.modalW - 24 - this.previewSlotSize - 8);

            this.previewSlotX = this.idInput.getX() + this.idInput.getWidth() + 8;
            this.previewSlotY = bodyY - 3;

            int sliderX = bodyX;
            int sliderW = this.modalW - 24;
            int currentY = bodyY + 32;

            this.chanceSlider.setX(sliderX);
            this.chanceSlider.setY(currentY);
            this.chanceSlider.setWidth(sliderW);
            currentY += 24;

            this.minSlider.setX(sliderX);
            this.minSlider.setY(currentY);
            this.minSlider.setWidth(sliderW);
            currentY += 24;

            this.maxSlider.setX(sliderX);
            this.maxSlider.setY(currentY);
            this.maxSlider.setWidth(sliderW);
            currentY += 24;

            this.requirePlayerKillCheckbox.setX(bodyX);
            this.requirePlayerKillCheckbox.setY(currentY);
            currentY += 20;

            this.affectedByLootingCheckbox.setX(bodyX);
            this.affectedByLootingCheckbox.setY(currentY);
            currentY += 20;

            this.enchantRandomlyCheckbox.setX(bodyX);
            this.enchantRandomlyCheckbox.setY(currentY);
            currentY += 20;

            this.enchantWithLevelsCheckbox.setX(bodyX);
            this.enchantWithLevelsCheckbox.setY(currentY);
            currentY += 20;

            boolean showEnchantLevels = this.enchantWithLevelsCheckbox.isSelected();

            this.enchantLevelsMinSlider.setX(bodyX + 18);
            this.enchantLevelsMinSlider.setY(currentY);
            this.enchantLevelsMinSlider.setWidth((sliderW - 22) / 2);

            this.enchantLevelsMaxSlider.setX(this.enchantLevelsMinSlider.getX() + this.enchantLevelsMinSlider.getWidth() + 4);
            this.enchantLevelsMaxSlider.setY(currentY);
            this.enchantLevelsMaxSlider.setWidth((sliderW - 22) / 2);

            if (showEnchantLevels) {
                currentY += 24;
            }

            updateEnchantControlsVisibility();

            int buttonY = currentY + 8;
            int maxButtonY = this.modalY + this.modalH - 26;
            if (buttonY > maxButtonY) {
                buttonY = maxButtonY;
            }
            int buttonW = (this.modalW - 30) / 2;
            this.confirmButton.setX(this.modalX + 10);
            this.confirmButton.setY(buttonY);
            this.confirmButton.setWidth(buttonW);
            this.cancelButton.setX(this.modalX + 20 + buttonW);
            this.cancelButton.setY(buttonY);
            this.cancelButton.setWidth(buttonW);
        }

        private void updateEnchantControlsVisibility() {
            boolean levels = this.enchantWithLevelsCheckbox.isSelected();
            this.enchantLevelsMinSlider.visible = levels;
            this.enchantLevelsMinSlider.active = levels;
            this.enchantLevelsMaxSlider.visible = levels;
            this.enchantLevelsMaxSlider.active = levels;
        }

        private void refreshPreview() {
            ParsedInputTarget target = parseInputTarget(this.idInput.getValue());
            if (target == null) {
                this.previewValid = false;
                this.previewItem = new ItemStack(Items.BARRIER);
                this.previewHint = "ID invalido";
                return;
            }

            this.previewValid = true;
            this.previewItem = target.preview;
            this.previewHint = target.hint;
        }

        private ResourceLocation tryParseResource(String value) {
            if (value == null) return null;
            String cleaned = value.trim();
            if (cleaned.isEmpty()) return null;
            return ResourceLocation.tryParse(cleaned);
        }

        private ParsedInputTarget parseInputTarget(String input) {
            if (input == null) {
                return null;
            }
            String raw = input.trim();
            if (raw.isEmpty()) {
                return null;
            }

            if (raw.startsWith("#")) {
                ResourceLocation tag = tryParseResource(raw.substring(1));
                if (tag == null) return null;
                return new ParsedInputTarget(TargetType.TAG, null, tag, null, new ItemStack(Items.CHEST), "Tag: #" + tag);
            }

            if (raw.startsWith("@")) {
                ResourceLocation table = tryParseResource(raw.substring(1));
                if (table == null) return null;
                return new ParsedInputTarget(TargetType.TABLE, null, null, table, new ItemStack(Items.CHEST), "Tabela: @" + table);
            }

            ResourceLocation itemId = tryParseResource(raw);
            if (itemId == null) return null;
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null || item == Items.AIR) {
                return null;
            }

            ItemStack stack = new ItemStack(item);
            stack.setCount(1);
            return new ParsedInputTarget(TargetType.ITEM, stack, null, null, stack.copy(), "Item: " + itemId);
        }

        private void confirm() {
            ParsedInputTarget target = parseInputTarget(this.idInput.getValue());
            if (target == null) {
                return;
            }

            int min = this.minSlider.getCurrentValue();
            int max = Math.max(min, this.maxSlider.getCurrentValue());
            this.maxSlider.setCurrentValue(max);

            int enchantMin = this.enchantLevelsMinSlider.getCurrentValue();
            int enchantMax = Math.max(enchantMin, this.enchantLevelsMaxSlider.getCurrentValue());
            this.enchantLevelsMaxSlider.setCurrentValue(enchantMax);

            ItemStack result = target.item != null ? target.item.copy() : new ItemStack(Items.CHEST);
            ResourceLocation tag = null;
            ResourceLocation reference = null;
            if (target.type == TargetType.TAG) {
                tag = target.tag;
            } else if (target.type == TargetType.TABLE) {
                reference = target.table;
            } else {
                result.setCount(1);
            }

            boolean enchantWithLevels = this.enchantWithLevelsCheckbox.isSelected();
            LootDropDTO.Range enchantRange = enchantWithLevels ? new LootDropDTO.Range(enchantMin, enchantMax) : null;

            LootDropDTO dto = new LootDropDTO(
                result,
                this.chanceSlider.getCurrentValue(),
                min,
                max,
                this.requirePlayerKillCheckbox.isSelected(),
                this.affectedByLootingCheckbox.isSelected(),
                false,
                tag,
                reference,
                this.enchantRandomlyCheckbox.isSelected(),
                enchantWithLevels,
                enchantRange
            );

            if (this.editingIndex >= 0 && this.editingIndex < LootStudioScreen.this.lootDrops.size()) {
                LootStudioScreen.this.lootDrops.set(this.editingIndex, dto);
                LootStudioScreen.this.editingDropIndex = this.editingIndex;
            } else {
                LootStudioScreen.this.lootDrops.add(dto);
                LootStudioScreen.this.editingDropIndex = LootStudioScreen.this.lootDrops.size() - 1;
            }

            LootStudioScreen.this.isDirty = true;
            close();
        }

        private void close() {
            this.open = false;
            this.editingIndex = -1;
            setWidgetsVisible(false);
        }

        private void setWidgetsVisible(boolean visible) {
            for (AbstractWidget widget : this.widgets) {
                widget.visible = visible;
                widget.active = visible;
            }
            if (visible) {
                updateEnchantControlsVisibility();
            }
        }

        private String getCarriedOrFallbackId() {
            ItemStack carried = getCarriedItemOrMainHand();
            if (!carried.isEmpty()) {
                ResourceLocation carriedId = ForgeRegistries.ITEMS.getKey(carried.getItem());
                if (carriedId != null) {
                    return carriedId.toString();
                }
            }
            return "minecraft:diamond";
        }

        private ItemStack getCarriedItemOrMainHand() {
            ItemStack carried = getCarriedItem();
            if (!carried.isEmpty()) return carried;
            if (LootStudioScreen.this.minecraft != null && LootStudioScreen.this.minecraft.player != null) {
                ItemStack hand = LootStudioScreen.this.minecraft.player.getMainHandItem();
                if (!hand.isEmpty()) return hand.copy();
            }
            return ItemStack.EMPTY;
        }

        private ItemStack getCarriedItem() {
            if (LootStudioScreen.this.minecraft == null || LootStudioScreen.this.minecraft.player == null || LootStudioScreen.this.minecraft.player.containerMenu == null) {
                return ItemStack.EMPTY;
            }
            ItemStack carried = LootStudioScreen.this.minecraft.player.containerMenu.getCarried();
            if (carried == null || carried.isEmpty()) return ItemStack.EMPTY;
            ItemStack copy = carried.copy();
            copy.setCount(1);
            return copy;
        }

        private enum TargetType {
            ITEM,
            TAG,
            TABLE
        }

        private record ParsedInputTarget(TargetType type, ItemStack item, ResourceLocation tag, ResourceLocation table, ItemStack preview, String hint) {
        }
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

        public double getCurrentValue() {
            double raw = this.min + (this.max - this.min) * this.value;
            double factor = Math.pow(10.0D, this.decimals);
            return Math.round(raw * factor) / factor;
        }

        public void setCurrentValue(double newValue) {
            this.value = normalize(newValue, this.min, this.max);
            applyValue();
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

        public int getCurrentValue() {
            if (this.max <= this.min) return this.min;
            return this.min + (int) Math.round((this.max - this.min) * this.value);
        }

        public void setCurrentValue(int newValue) {
            this.value = normalize(newValue, this.min, this.max);
            applyValue();
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
