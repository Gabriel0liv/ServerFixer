package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.OpenEntityTagStudioPacket;
import com.gabri.serverfixes.network.RequestTagsPacket;
import com.gabri.serverfixes.network.SaveTagPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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

@SuppressWarnings("null")
public class TagStudioScreen extends Screen {
    private final List<TagEntry> allTags = new ArrayList<>();
    private final List<TagEntry> filteredTags = new ArrayList<>();

    private SelectableEditBox searchBox;
    private SelectableEditBox newTagInput;
    private Button confirmNewTagButton;
    private IconButton filterButton;
    private boolean filterOverlayOpen = false;
    private boolean showNewTagInput = false;

    // Left panel
    private double leftScrollAmount = 0.0D;
    private int maxLeftScroll = 0;
    private final List<TagButton> tagButtons = new ArrayList<>();
    private final Map<TagButton, Integer> buttonOriginalY = new HashMap<>();
    private int selectedTagIndex = 0;
    private String selectedTagId = null;

    // Hierarchical tag display
    private final java.util.Set<String> collapsedParents = new java.util.HashSet<>();

    // Center panel - items in tag
    private final List<String> tagItems = new ArrayList<>();
    private double centerScrollAmount = 0.0D;
    private int maxCenterScroll = 0;
    private boolean isDirty = false;

    // Right panel
    private final List<AbstractWidget> rightPanelWidgets = new ArrayList<>();
    private final Map<AbstractWidget, Integer> widgetOriginalY = new HashMap<>();
    private double rightScrollAmount = 0.0D;
    private int maxRightScroll = 0;

    // Layout
    private int leftX, leftY, leftW, leftH;
    private int centerX, centerY, centerW, centerH;
    private int rightX, rightY, rightW, rightH;
    private int listAreaX, listAreaY, listAreaW, listAreaH;

    // Right panel controls
    private EditBox newItemInput;
    private PulsingButton saveApplyButton;
    private IconButton resetButton;

    // Toggle button to switch between Item and Entity Tag Studio
    private Button switchToEntityTagButton;

    // Item validation state
    private boolean itemInputValid = true;

    // Filters
    private boolean searchMod = true;
    private boolean searchVanilla = true;
    private boolean suppressSearchRequest = false;

    // Left scrollbar
    private boolean leftScrollbarDragging = false;

    // Default namespace for new tags
    private String defaultNamespace = "serverfixes";

    public TagStudioScreen() {
        super(Component.literal("Tag Studio"));
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.font == null) return;

        this.clearWidgets();
        this.tagButtons.clear();
        this.buttonOriginalY.clear();
        this.leftScrollAmount = 0.0D;
        this.maxLeftScroll = 0;
        this.selectedTagIndex = 0;

        this.centerScrollAmount = 0.0D;
        this.maxCenterScroll = 0;
        this.tagItems.clear();

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
            filterBtnSize, filterBtnSize,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/indent-solid.png"),
            12, 12, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A,
            btn -> this.filterOverlayOpen = !this.filterOverlayOpen
        );
        this.addRenderableWidget(this.filterButton);

        // New tag input (hidden by default, shown when clicking "+" button)
        this.newTagInput = new SelectableEditBox(this.font, this.listAreaX, this.listAreaY - 4, this.listAreaW - 22, 16, Component.literal(this.defaultNamespace + ":nome_da_tag"));
        this.newTagInput.setMaxLength(128);
        this.newTagInput.setResponder(value -> {});
        this.newTagInput.visible = false;
        this.addRenderableWidget(this.newTagInput);

        this.confirmNewTagButton = Button.builder(Component.literal("✓"), btn -> createNewTag())
            .bounds(this.listAreaX + this.listAreaW - 20, this.listAreaY - 4, 18, 16)
            .build();
        this.confirmNewTagButton.visible = false;
        this.addRenderableWidget(this.confirmNewTagButton);

        // Auto-set default namespace for new tags
        this.defaultNamespace = "serverfixes";

        // Request tags from server
        NetworkHandler.sendToServer(new RequestTagsPacket());

        // Build initial tag list (will be filtered when server responds)
        applySearchFilter();

        // Right panel
        int actionW = this.rightW - 16;

        this.newItemInput = new SelectableEditBox(this.font, 0, 0, actionW, 18, Component.literal("minecraft:diamond"));
        this.newItemInput.setMaxLength(128);
        this.newItemInput.setResponder(this::validateItemInput);
        registerRightPanelWidget(this.newItemInput);

        Button addButton = Button.builder(Component.literal("Adicionar Item"), btn -> addItemToTag()).bounds(0, 0, actionW, 18).build();
        registerRightPanelWidget(addButton);

        this.saveApplyButton = new PulsingButton(0, 0, actionW, 22, Component.literal("SALVAR E APLICAR"), btn -> saveCurrentTagToServer());
        registerRightPanelWidget(this.saveApplyButton);

        this.resetButton = new IconButton(0, 0, 12, 12,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/reset.png"),
            12, 12, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A,
            btn -> requestResetTag());
        this.addRenderableWidget(this.resetButton);

        // Button to switch to Entity Tag Studio
        this.switchToEntityTagButton = Button.builder(Component.literal("Entidades >>"), btn -> {
            if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2)) {
                this.minecraft.setScreen(new EntityTagStudioScreen());
            }
        }).bounds(this.width - 120, 2, 118, 16).build();
        this.addRenderableWidget(this.switchToEntityTagButton);

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

        // Position new tag input
        if (this.newTagInput != null) {
            this.newTagInput.setX(this.listAreaX);
            this.newTagInput.setY(this.listAreaY + 2);
            this.newTagInput.setWidth(this.listAreaW - 22);
        }
        if (this.confirmNewTagButton != null) {
            this.confirmNewTagButton.setX(this.listAreaX + this.listAreaW - 20);
            this.confirmNewTagButton.setY(this.listAreaY + 2);
        }
    }

    private void applySearchFilter() {
        String query = this.searchBox != null ? this.searchBox.getValue().trim().toLowerCase(Locale.ROOT) : "";

        this.filteredTags.clear();
        this.filteredTags.addAll(this.allTags);

        if (!query.isEmpty()) {
            // Filtra por tag ID OU por item contido na tag
            this.filteredTags.removeIf(tag -> {
                // Match por ID da tag
                if (tag.tagId().toLowerCase(Locale.ROOT).contains(query)) return false;
                // Match por item dentro da tag
                for (String itemId : tag.itemIds()) {
                    if (itemId.toLowerCase(Locale.ROOT).contains(query)) return false;
                }
                return true;
            });
        }
        if (!this.searchMod) {
            this.filteredTags.removeIf(tag -> !tag.isVanilla());
        }
        if (!this.searchVanilla) {
            this.filteredTags.removeIf(tag -> tag.isVanilla());
        }

        this.filteredTags.sort(Comparator.comparing(TagEntry::tagId));

        boolean selectionExists = false;
        for (TagEntry entry : this.filteredTags) {
            if (entry.tagId().equals(this.selectedTagId)) { selectionExists = true; break; }
        }
        String resolvedSelection = selectionExists ? this.selectedTagId : (this.filteredTags.isEmpty() ? null : this.filteredTags.get(0).tagId());

        rebuildTagButtons();

        if (resolvedSelection == null || this.filteredTags.isEmpty()) {
            this.selectedTagIndex = 0;
            setSelectedTagId(null);
            return;
        }

        int index = -1;
        for (int i = 0; i < this.filteredTags.size(); i++) {
            if (this.filteredTags.get(i).tagId().equals(resolvedSelection)) { index = i; break; }
        }
        setSelectedTagIndex(index < 0 ? 0 : index);
    }

    public void applyTagsFromServer(List<RequestTagsPacket.TagDTO> tags) {
        this.allTags.clear();
        if (tags != null) {
            for (RequestTagsPacket.TagDTO dto : tags) {
                if (dto != null) {
                    boolean isVanilla = dto.tagId().startsWith("minecraft:");
                    this.allTags.add(new TagEntry(dto.tagId(), dto.itemIds(), isVanilla));
                }
            }
        }
        this.allTags.sort(Comparator.comparing(TagEntry::tagId));
        this.suppressSearchRequest = true;
        applySearchFilter();
        this.suppressSearchRequest = false;
    }

    private void rebuildTagButtons() {
        this.tagButtons.clear();
        this.buttonOriginalY.clear();
        // NÃO resetar leftScrollAmount aqui para manter posição ao abrir/fechar pais

        // Build parent-child relationships
        java.util.Map<String, List<TagEntry>> childrenMap = new java.util.HashMap<>();
        java.util.Set<String> allTagIds = new java.util.HashSet<>();
        java.util.Map<String, TagEntry> tagById = new java.util.HashMap<>();

        for (TagEntry entry : this.filteredTags) {
            String tagId = entry.tagId();
            allTagIds.add(tagId);
            tagById.put(tagId, entry);

            int colonIdx = tagId.indexOf(':');
            int lastSlash = tagId.lastIndexOf('/');
            if (colonIdx >= 0 && lastSlash > colonIdx + 1) {
                String parentPath = tagId.substring(0, lastSlash);
                childrenMap.computeIfAbsent(parentPath, k -> new ArrayList<>()).add(entry);
            }
        }

        // Find root tags (tags that are NOT children of any other tag)
        java.util.Set<String> childTags = new java.util.HashSet<>();
        for (List<TagEntry> children : childrenMap.values()) {
            for (TagEntry child : children) {
                childTags.add(child.tagId());
            }
        }
        java.util.List<TagEntry> rootTags = new ArrayList<>();
        for (TagEntry entry : this.filteredTags) {
            if (!childTags.contains(entry.tagId())) {
                rootTags.add(entry);
            }
        }
        rootTags.sort(Comparator.comparing(TagEntry::tagId));

        // Render tree recursively
        int[] currentY = new int[]{this.listAreaY + 1};
        for (TagEntry root : rootTags) {
            renderTagTree(root, childrenMap, tagById, currentY, 0);
        }

        int listBottom = this.listAreaY + this.listAreaH - 20;
        this.maxLeftScroll = Math.max(0, currentY[0] - listBottom);
        this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private void renderTagTree(TagEntry entry, java.util.Map<String, List<TagEntry>> childrenMap,
            java.util.Map<String, TagEntry> tagById, int[] currentY, int depth) {

        String tagId = entry.tagId();
        boolean hasChildren = childrenMap.containsKey(tagId);
        boolean isCollapsed = hasChildren && this.collapsedParents.contains(tagId);
        int indent = depth * 10;
        int btnX = this.listAreaX + 1 + indent;
        int btnW = Math.max(10, this.listAreaW - 4 - indent);

        int index = this.tagButtons.size();
        TagButton button = new TagButton(btnX, currentY[0], btnW, 20, tagId, hasChildren, isCollapsed, btn -> {
            if (hasChildren) {
                if (this.collapsedParents.contains(tagId)) {
                    this.collapsedParents.remove(tagId);
                } else {
                    this.collapsedParents.add(tagId);
                }
                rebuildTagButtons();
            } else {
                setSelectedTagById(tagId);
            }
        });
        this.tagButtons.add(button);
        this.buttonOriginalY.put(button, currentY[0]);
        currentY[0] += 22;

        // Render children if expanded
        if (hasChildren && !isCollapsed) {
            List<TagEntry> children = childrenMap.get(tagId);
            if (children != null) {
                // Sort children by tagId
                children.sort(Comparator.comparing(TagEntry::tagId));
                for (TagEntry child : children) {
                    renderTagTree(child, childrenMap, tagById, currentY, depth + 1);
                }
            }
        }
    }

    private void setSelectedTagById(String tagId) {
        if (tagId == null) {
            this.selectedTagIndex = 0;
            setSelectedTagId(null);
            return;
        }
        
        // Find the index in filteredTags
        for (int i = 0; i < this.filteredTags.size(); i++) {
            if (this.filteredTags.get(i).tagId().equals(tagId)) {
                this.selectedTagIndex = i;
                setSelectedTagId(tagId);
                return;
            }
        }
        
        // Tag not found in filtered list
        this.selectedTagIndex = 0;
        setSelectedTagId(null);
    }

    private void setSelectedTagIndex(int index) {
        if (this.filteredTags.isEmpty()) {
            this.selectedTagIndex = 0;
            setSelectedTagId(null);
            return;
        }
        int clamped = Mth.clamp(index, 0, this.filteredTags.size() - 1);
        this.selectedTagIndex = clamped;
        setSelectedTagId(this.filteredTags.get(clamped).tagId());
    }

    private void setSelectedTagId(String id) {
        if (Objects.equals(this.selectedTagId, id)) return;
        this.selectedTagId = id;
        this.tagItems.clear();
        this.centerScrollAmount = 0.0D;
        this.isDirty = false;

        if (id != null) {
            for (TagEntry entry : this.filteredTags) {
                if (entry.tagId().equals(id)) {
                    this.tagItems.addAll(entry.itemIds());
                    break;
                }
            }
        }
    }

    private void addItemToTag() {
        if (this.selectedTagId == null) return;
        String itemId = this.newItemInput != null ? this.newItemInput.getValue().trim() : "";
        if (itemId.isEmpty()) return;

        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            this.itemInputValid = false;
            updateItemInputValidation();
            return;
        }

        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) {
            this.itemInputValid = false;
            updateItemInputValidation();
            return;
        }

        // Check if already in list
        for (String existing : this.tagItems) {
            if (existing.equals(itemId)) {
                this.itemInputValid = false;
                updateItemInputValidation();
                return;
            }
        }

        this.itemInputValid = true;
        updateItemInputValidation();
        this.tagItems.add(itemId);
        this.isDirty = true;
        if (this.newItemInput != null) this.newItemInput.setValue("");
    }

    private void validateItemInput(String value) {
        if (value == null || value.trim().isEmpty()) {
            this.itemInputValid = true;
            updateItemInputValidation();
            return;
        }
        
        ResourceLocation rl = ResourceLocation.tryParse(value.trim());
        if (rl == null) {
            this.itemInputValid = false;
            updateItemInputValidation();
            return;
        }

        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null || item == Items.AIR) {
            this.itemInputValid = false;
        } else {
            this.itemInputValid = true;
        }
        updateItemInputValidation();
    }

    private void updateItemInputValidation() {
        if (this.newItemInput instanceof SelectableEditBox selectableBox) {
            selectableBox.setInvalidState(!this.itemInputValid);
        }
    }

    private void requestResetTag() {
        if (this.selectedTagId == null) return;
        // Revert to original state
        this.tagItems.clear();
        for (TagEntry entry : this.filteredTags) {
            if (entry.tagId().equals(this.selectedTagId)) {
                this.tagItems.addAll(entry.itemIds());
                break;
            }
        }
        this.centerScrollAmount = 0.0D;
        this.isDirty = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.showNewTagInput) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                createNewTag();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                cancelNewTag();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void createNewTag() {
        if (this.newTagInput == null) return;
        String tagId = this.newTagInput.getValue().trim();
        if (tagId.isEmpty() || tagId.equals(this.defaultNamespace + ":")) { cancelNewTag(); return; }

        // Auto-prepend namespace if missing
        if (!tagId.contains(":")) {
            tagId = this.defaultNamespace + ":" + tagId;
        }

        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl == null) { cancelNewTag(); return; }

        // Check if tag already exists
        for (TagEntry entry : this.allTags) {
            if (entry.tagId().equals(tagId)) { cancelNewTag(); return; }
        }

        TagEntry newEntry = new TagEntry(tagId, new ArrayList<>(), tagId.startsWith("minecraft:"));
        this.allTags.add(newEntry);
        this.allTags.sort(Comparator.comparing(TagEntry::tagId));

        cancelNewTag();
        applySearchFilter();

        // Auto-select the new tag
        for (int i = 0; i < this.filteredTags.size(); i++) {
            if (this.filteredTags.get(i).tagId().equals(tagId)) {
                setSelectedTagIndex(i);
                break;
            }
        }
    }

    private void cancelNewTag() {
        this.showNewTagInput = false;
        this.newTagInput.visible = false;
        this.confirmNewTagButton.visible = false;
        this.newTagInput.setValue(this.defaultNamespace + ":");
    }

    private void saveCurrentTagToServer() {
        if (this.selectedTagId == null || !this.isDirty) return;
        NetworkHandler.sendToServer(new SaveTagPacket(this.selectedTagId, new ArrayList<>(this.tagItems)));
        this.isDirty = false;
    }

    // ===================== RENDERING =====================

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
            this.resetButton.visible = this.selectedTagId != null;
            this.resetButton.active = this.selectedTagId != null;
        }

        graphics.fill(0, 0, this.width, this.height, 0xDD000000);
        graphics.fill(this.leftX, this.leftY, this.leftX + this.leftW, this.leftY + this.leftH, 0x44242D3D);
        graphics.fill(this.centerX, this.centerY, this.centerX + this.centerW, this.centerY + this.centerH, 0x66202A39);
        graphics.fill(this.rightX, this.rightY, this.rightX + this.rightW, this.rightY + this.rightH, 0x44242D3D);

        GuiLayoutUtils.drawPanel(graphics, this.leftX, this.leftY, this.leftW, this.leftH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.centerX, this.centerY, this.centerW, this.centerH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.rightX, this.rightY, this.rightW, this.rightH, 0x001A2434, 0xFF4E6B8D);

        graphics.drawString(this.font, "Tags de Itens", this.leftX + 8, this.leftY + 8, 0xFFF4F7FF);

        // Botão "+" para criar nova tag
        int newTagBtnX = this.listAreaX + this.listAreaW - 4;
        int newTagBtnY = this.leftY + 6;
        boolean isHoveringNewTag = mouseX >= newTagBtnX && mouseX < newTagBtnX + 16 && mouseY >= newTagBtnY && mouseY < newTagBtnY + 16;
        graphics.fill(newTagBtnX, newTagBtnY, newTagBtnX + 16, newTagBtnY + 16,
            isHoveringNewTag ? 0xFF4E6B8D : 0x882E4258);
        graphics.drawString(this.font, "+", newTagBtnX + 5, newTagBtnY + 3, 0xFF8BD3FF);

        // Render new tag input overlay if visible
        if (this.showNewTagInput) {
            int inputX = this.listAreaX - 4;
            int inputY = this.listAreaY;
            int inputW = this.listAreaW + 6;
            int inputH = 22;
            // Dark overlay behind input
            graphics.fill(this.leftX, this.listAreaY - 4, this.leftX + this.leftW, this.listAreaY + inputH + 8, 0xCC000000);
            // Input background
            graphics.fill(inputX, inputY, inputX + inputW, inputY + inputH, 0xFF1A2434);
            graphics.fill(inputX, inputY, inputX + inputW, inputY + 1, 0xFF4E6B8D);
            graphics.fill(inputX, inputY + inputH - 1, inputX + inputW, inputY + inputH, 0xFF4E6B8D);
            graphics.fill(inputX, inputY, inputX + 1, inputY + inputH, 0xFF4E6B8D);
            graphics.fill(inputX + inputW - 1, inputY, inputX + inputW, inputY + inputH, 0xFF4E6B8D);
        }

        String selectedTitle = this.selectedTagId != null ? this.selectedTagId : "<nenhuma>";
        graphics.drawString(this.font, "Itens da Tag: " + this.font.plainSubstrByWidth(selectedTitle, Math.max(40, this.centerW - 44)), this.centerX + 8, this.centerY + 8, 0xFFF4F7FF);
        graphics.drawString(this.font, "Editor", this.rightX + 8, this.rightY + 8, 0xFFF4F7FF);

        GuiLayoutUtils.drawPanel(graphics, this.listAreaX, this.listAreaY, this.listAreaW, this.listAreaH, 0xB1121924, 0xFF2E4258);

        // Adjust list area when new tag input is visible (push list down)
        if (this.showNewTagInput) {
            graphics.enableScissor(this.listAreaX, this.listAreaY + 24, this.listAreaX + this.listAreaW, this.listAreaY + this.listAreaH);
        }
        renderTagButtons(graphics, mouseX, mouseY, partialTick);
        if (this.showNewTagInput) {
            graphics.disableScissor();
        }

        renderCenterTagList(graphics, mouseX, mouseY, partialTick);

        // Clip right panel widgets so they stay within bounds
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
            // Label for the item input
            if (this.newItemInput != null) {
                graphics.drawString(this.font, "ID do Item:", this.rightX + 8, this.newItemInput.getY() - 10, 0xFFD4E0F0);
            }
            graphics.disableScissor();
        }

        renderLeftScrollbar(graphics);
        renderCenterScrollbar(graphics);

        // Render new tag input OVER everything (after scissor is disabled)
        if (this.showNewTagInput) {
            int inputX = this.listAreaX;
            int inputY = this.listAreaY + 2;
            int inputW = this.listAreaW - 22;
            int inputH = 18;
            // Dark overlay behind input
            graphics.fill(this.leftX + 2, inputY - 2, this.leftX + this.leftW - 2, inputY + inputH + 6, 0xCC000000);
            // Input background with border
            graphics.fill(inputX, inputY, inputX + inputW, inputY + inputH, 0xFF1A2434);
            graphics.fill(inputX, inputY, inputX + inputW, inputY + 1, 0xFF4E6B8D);
            graphics.fill(inputX, inputY + inputH - 1, inputX + inputW, inputY + inputH, 0xFF4E6B8D);
            graphics.fill(inputX, inputY, inputX + 1, inputY + inputH, 0xFF4E6B8D);
            graphics.fill(inputX + inputW - 1, inputY, inputX + inputW, inputY + inputH, 0xFF4E6B8D);
            // Render the input text manually (not using widget.render)
            String val = this.newTagInput.getValue();
            graphics.drawString(this.font, val, inputX + 4, inputY + 4, 0xFFE0E0E0);
            // Render confirm button
            this.confirmNewTagButton.render(graphics, mouseX, mouseY, partialTick);
        }

        if (this.filterOverlayOpen) {
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
            renderFilterOverlay(graphics, mouseX, mouseY);
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        } else {
            renderFilterOverlay(graphics, mouseX, mouseY);
        }
    }

    private void renderTagButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.tagButtons.isEmpty()) return;
        graphics.enableScissor(this.listAreaX, this.listAreaY, this.listAreaX + this.listAreaW, this.listAreaY + this.listAreaH);
        for (TagButton tb : this.tagButtons) {
            tb.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
    }

    private void renderCenterTagList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.tagItems.isEmpty()) return;
        int startY = this.centerY + 28;
        int rowH = 20;

        graphics.enableScissor(this.centerX + 2, startY, this.centerX + this.centerW - 2, this.centerY + this.centerH - 2);

        for (int i = 0; i < this.tagItems.size(); i++) {
            int rowY = startY + (i * rowH) - (int) this.centerScrollAmount;
            if (rowY + rowH < this.centerY + 28 || rowY > this.centerY + this.centerH - 2) continue;

            String itemId = this.tagItems.get(i);
            boolean hovered = mouseY >= rowY && mouseY < rowY + rowH && mouseX >= this.centerX + 8 && mouseX < this.centerX + this.centerW - 8;

            graphics.fill(this.centerX + 4, rowY, this.centerX + this.centerW - 4, rowY + rowH - 1,
                hovered ? 0x442B4E72 : 0x22202A36);

            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            Item item = rl != null ? ForgeRegistries.ITEMS.getValue(rl) : null;
            ItemStack stack = item != null && item != Items.AIR ? new ItemStack(item) : new ItemStack(Items.BARRIER);

            graphics.renderItem(stack, this.centerX + 8, rowY + 2);
            graphics.drawString(this.font, itemId, this.centerX + 30, rowY + 6, 0xFFD4E0F0);

            // Delete button
            int delX = this.centerX + this.centerW - 22;
            graphics.fill(delX, rowY + 3, delX + 14, rowY + 17, 0x55CC3333);
            graphics.drawString(this.font, "X", delX + 4, rowY + 5, 0xFFFF5555);
        }

        graphics.disableScissor();
    }

    // ===================== SCROLLBARS =====================

    private boolean isInsideLeftScrollbar(double mouseX, double mouseY) {
        int barX = this.leftX + this.leftW - 10;
        int barY = this.listAreaY;
        int barW = 6;
        int barH = this.listAreaH;
        return mouseX >= barX && mouseX < barX + barW && mouseY >= barY && mouseY < barY + barH;
    }

    private void startLeftScrollbarDrag(double mouseY) {
        this.leftScrollbarDragging = true;
    }

    private void updateLeftScrollbarDrag(double mouseY) {
        if (!this.leftScrollbarDragging || this.maxLeftScroll <= 0) return;
        int barX = this.leftX + this.leftW - 10;
        int barY = this.listAreaY;
        int barH = this.listAreaH;
        double ratio = (mouseY - barY) / barH;
        this.leftScrollAmount = Mth.clamp(ratio * this.maxLeftScroll, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private void applyLeftScroll() {
        for (int i = 0; i < this.tagButtons.size(); i++) {
            TagButton tb = this.tagButtons.get(i);
            int originalY = this.buttonOriginalY.get(tb);
            tb.setY(originalY - (int) this.leftScrollAmount);
        }
    }

    private void renderLeftScrollbar(GuiGraphics graphics) {
        if (this.maxLeftScroll <= 0) return;
        int barX = this.leftX + this.leftW - 10;
        int barY = this.listAreaY;
        int barW = 6;
        int barH = this.listAreaH;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);

        int trackHeight = barH;
        int thumbHeight = Math.max(10, (int) ((double) barH / (this.maxLeftScroll + barH) * trackHeight));
        int thumbY = barY + (int) ((this.leftScrollAmount / this.maxLeftScroll) * (trackHeight - thumbHeight));
        graphics.fill(barX + 1, thumbY, barX + barW - 1, thumbY + thumbHeight, 0xFF888888);
    }

    private void renderCenterScrollbar(GuiGraphics graphics) {
        if (this.tagItems.isEmpty()) return;
        int rowH = 20;
        int contentHeight = this.tagItems.size() * rowH;
        if (contentHeight <= this.centerH - 56) return;

        this.maxCenterScroll = contentHeight - (this.centerH - 56);
        this.centerScrollAmount = Mth.clamp(this.centerScrollAmount, 0.0D, this.maxCenterScroll);

        int barX = this.centerX + this.centerW - 8;
        int barY = this.centerY + 28;
        int barW = 6;
        int barH = this.centerH - 36;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);

        int thumbHeight = Math.max(10, (int) ((double) barH / (this.maxCenterScroll + barH) * barH));
        int thumbY = barY + (int) ((this.centerScrollAmount / this.maxCenterScroll) * (barH - thumbHeight));
        graphics.fill(barX + 1, thumbY, barX + barW - 1, thumbY + thumbHeight, 0xFF888888);
    }

    // ===================== MOUSE/KEYBOARD =====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleFilterOverlayClick(mouseX, mouseY, button)) return true;

        // Check scrollbar FIRST (before tag buttons)
        if (this.maxLeftScroll > 0 && isInsideLeftScrollbar(mouseX, mouseY)) {
            startLeftScrollbarDrag(mouseY);
            return true;
        }

        // Handle "+" button click for new tag
        int newTagBtnX = this.listAreaX + this.listAreaW - 4;
        int newTagBtnY = this.leftY + 6;
        if (!this.showNewTagInput && mouseX >= newTagBtnX && mouseX < newTagBtnX + 16 && mouseY >= newTagBtnY && mouseY < newTagBtnY + 16) {
            this.showNewTagInput = true;
            this.newTagInput.visible = true;
            this.confirmNewTagButton.visible = true;
            this.newTagInput.setValue(this.defaultNamespace + ":");
            this.newTagInput.setFocused(true);
            // Set cursor after the colon
            int len = this.newTagInput.getValue().length();
            this.newTagInput.setCursorPosition(len);
            this.newTagInput.setHighlightPos(len);
            setFocused(this.newTagInput);
            return true;
        }

        // Click outside input cancels creation
        if (this.showNewTagInput) {
            boolean insideInput = mouseX >= this.listAreaX && mouseX < this.listAreaX + this.listAreaW
                && mouseY >= this.listAreaY && mouseY < this.listAreaY + 22;
            boolean insideConfirm = mouseX >= this.confirmNewTagButton.getX() && mouseX < this.confirmNewTagButton.getX() + this.confirmNewTagButton.getWidth()
                && mouseY >= this.confirmNewTagButton.getY() && mouseY < this.confirmNewTagButton.getY() + this.confirmNewTagButton.getHeight();
            if (!insideInput && !insideConfirm) {
                cancelNewTag();
                return true;
            }
        }

        if (isInsideCenterList(mouseX, mouseY)) {
            int startY = this.centerY + 28;
            int rowH = 20;
            for (int i = 0; i < this.tagItems.size(); i++) {
                int rowY = startY + (i * rowH) - (int) this.centerScrollAmount;
                if (mouseY >= rowY && mouseY < rowY + rowH) {
                    int delX = this.centerX + this.centerW - 22;
                    if (mouseX >= delX && mouseX < delX + 14) {
                        this.tagItems.remove(i);
                        this.isDirty = true;
                        return true;
                    }
                }
            }
        }

        if (isInsideLeftList(mouseX, mouseY) && !this.showNewTagInput) {
            for (TagButton tb : this.tagButtons) {
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
        if (isInsideCenterList(mouseX, mouseY) && this.maxCenterScroll > 0) {
            this.centerScrollAmount -= scrollY * 15.0D;
            this.centerScrollAmount = Mth.clamp(this.centerScrollAmount, 0.0D, this.maxCenterScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    private boolean isInsideLeftList(double mouseX, double mouseY) {
        return mouseX >= this.listAreaX && mouseX < this.listAreaX + this.listAreaW
            && mouseY >= this.listAreaY && mouseY < this.listAreaY + this.listAreaH;
    }

    private boolean isInsideCenterList(double mouseX, double mouseY) {
        return mouseX >= this.centerX + 8 && mouseX < this.centerX + this.centerW - 8
            && mouseY >= this.centerY + 28 && mouseY < this.centerY + this.centerH - 8;
    }

    // ===================== FILTER OVERLAY =====================

    private void renderFilterOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!this.filterOverlayOpen || this.font == null || this.filterButton == null) return;

        int x = this.filterButton.getX() - 4;
        int y = this.filterButton.getY() + this.filterButton.getHeight() + 4;
        int w = Math.min(220, this.leftW - 16);
        int h = 64;

        graphics.fill(x, y, x + w, y + h, 0xFB1A2434);
        graphics.fill(x, y, x + w, y + 1, 0xFF4E6B8D);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF4E6B8D);
        graphics.fill(x, y, x + 1, y + h, 0xFF4E6B8D);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF4E6B8D);

        int rowY = y + 8;
        graphics.drawString(this.font, "Filtro de Tags", x + 6, rowY, 0xFFF4F7FF);
        rowY += 16;

        drawOverlayCheckbox(graphics, x + 8, rowY, this.searchMod);
        graphics.drawString(this.font, "Mods (namespace)", x + 24, rowY + 2, 0xFFD4E0F0);
        rowY += 16;

        drawOverlayCheckbox(graphics, x + 8, rowY, this.searchVanilla);
        graphics.drawString(this.font, "Vanilla (minecraft:)", x + 24, rowY + 2, 0xFFD4E0F0);
    }

    private void drawOverlayCheckbox(GuiGraphics graphics, int x, int y, boolean selected) {
        graphics.fill(x, y, x + 12, y + 12, 0xFF4E6B8D);
        graphics.fill(x + 1, y + 1, x + 11, y + 11, 0xFF1A2434);
        if (selected) graphics.fill(x + 3, y + 3, x + 9, y + 9, 0xFF8BD3FF);
    }

    private boolean handleFilterOverlayClick(double mouseX, double mouseY, int button) {
        if (!this.filterOverlayOpen || button != 0 || this.filterButton == null) return false;

        int x = this.filterButton.getX() - 4;
        int y = this.filterButton.getY() + this.filterButton.getHeight() + 4;
        int w = Math.min(220, this.leftW - 16);
        int h = 64;

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
                this.searchVanilla = !this.searchVanilla;
                applySearchFilter();
                return true;
            }
        }
        return true;
    }

    // ===================== HELPERS =====================

    private void registerRightPanelWidget(AbstractWidget widget) {
        this.rightPanelWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    private void recalculateRightPanel() {
        if (this.newItemInput == null) return;
        int actionW = this.rightW - 16;
        int bodyX = this.rightX + 8;
        int currentY = this.rightY + 32;

        this.newItemInput.setX(bodyX);
        this.newItemInput.setY(currentY);
        this.newItemInput.setWidth(actionW);
        currentY += 24;

        for (int i = 0; i < this.rightPanelWidgets.size(); i++) {
            AbstractWidget widget = this.rightPanelWidgets.get(i);
            if (widget == this.newItemInput) continue;
            widget.setX(bodyX);
            widget.setY(currentY);
            widget.setWidth(actionW);
            if (widget instanceof Button) currentY += 22;
            else if (widget instanceof EditBox) currentY += 24;
            else currentY += 20;
        }
    }

    // ===================== NESTED CLASSES =====================

    public record TagEntry(String tagId, List<String> itemIds, boolean isVanilla) {}

    private final class TagButton extends Button {
        private final String id;
        private final boolean isParent;
        private final boolean isCollapsed;

        private TagButton(int x, int y, int width, int height, String id, boolean isParent, boolean isCollapsed, OnPress onPress) {
            super(x, y, width, height, Component.literal(id), onPress, DEFAULT_NARRATION);
            this.id = id;
            this.isParent = isParent;
            this.isCollapsed = isCollapsed;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            boolean selected = Objects.equals(this.id, TagStudioScreen.this.selectedTagId);
            boolean hovered = this.isHoveredOrFocused();
            int color = selected ? 0xAA2B4E72 : (hovered ? 0xAA263445 : 0x55202A36);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), color);

            // Expand/collapse icon for parent tags
            if (this.isParent) {
                String arrow = this.isCollapsed ? "▶ " : "▼ ";
                graphics.drawString(TagStudioScreen.this.font, arrow, this.getX() + 2, this.getY() + 6, 0xFF8BD3FF);
            }

            // Show only the last part of the path for display
            String displayName = this.id;
            int lastSlash = displayName.lastIndexOf('/');
            if (lastSlash >= 0) {
                displayName = displayName.substring(lastSlash + 1);
            }
            if (displayName.length() > 28) displayName = displayName.substring(0, 25) + "...";

            int textColor = selected ? 0xFF8BD3FF : 0xFFC0C8D0;
            int textX = this.isParent ? this.getX() + 16 : this.getX() + 4;
            graphics.drawString(TagStudioScreen.this.font, displayName, textX, this.getY() + 6, textColor);
        }
    }
}
