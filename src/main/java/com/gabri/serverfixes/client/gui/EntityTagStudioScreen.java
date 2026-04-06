package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.OpenTagStudioPacket;
import com.gabri.serverfixes.network.RequestEntityTagsPacket;
import com.gabri.serverfixes.network.SaveEntityTagPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
public class EntityTagStudioScreen extends Screen {
    private final List<EntityTagEntry> allTags = new ArrayList<>();
    private final List<EntityTagEntry> filteredTags = new ArrayList<>();

    private SelectableEditBox searchBox;
    private SelectableEditBox newTagInput;
    private Button confirmNewTagButton;
    private IconButton filterButton;
    private boolean filterOverlayOpen = false;
    private boolean showNewTagInput = false;

    // Left panel
    private double leftScrollAmount = 0.0D;
    private int maxLeftScroll = 0;
    private final List<EntityTagButton> tagButtons = new ArrayList<>();
    private final Map<EntityTagButton, Integer> buttonOriginalY = new HashMap<>();
    private int selectedTagIndex = 0;
    private String selectedTagId = null;

    // Hierarchical tag display
    private final java.util.Set<String> collapsedParents = new java.util.HashSet<>();

    // Center panel - entities in tag
    private final List<String> tagEntities = new ArrayList<>();
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
    private EditBox newEntityInput;
    private PulsingButton saveApplyButton;
    private IconButton resetButton;

    // Toggle button to switch between Item and Entity Tag Studio
    private Button switchToItemTagButton;

    // Entity validation state
    private boolean entityInputValid = true;

    // Filters
    private boolean searchMod = true;
    private boolean searchVanilla = true;
    private boolean suppressSearchRequest = false;

    // Left scrollbar
    private boolean leftScrollbarDragging = false;

    // Default namespace for new tags
    private String defaultNamespace = "serverfixes";

    public EntityTagStudioScreen() {
        super(Component.literal("Entity Tag Studio"));
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
        this.tagEntities.clear();

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

        // Request entity tags from server
        NetworkHandler.sendToServer(new RequestEntityTagsPacket());

        // Build initial tag list (will be filtered when server responds)
        applySearchFilter();

        // Right panel
        int actionW = this.rightW - 16;

        this.newEntityInput = new SelectableEditBox(this.font, 0, 0, actionW, 18, Component.literal("minecraft:zombie"));
        this.newEntityInput.setMaxLength(128);
        this.newEntityInput.setResponder(this::validateEntityInput);
        registerRightPanelWidget(this.newEntityInput);

        Button addButton = Button.builder(Component.literal("Adicionar Entidade"), btn -> addEntityToTag()).bounds(0, 0, actionW, 18).build();
        registerRightPanelWidget(addButton);

        this.saveApplyButton = new PulsingButton(0, 0, actionW, 22, Component.literal("SALVAR E APLICAR"), btn -> saveCurrentTagToServer());
        registerRightPanelWidget(this.saveApplyButton);

        this.resetButton = new IconButton(0, 0, 12, 12,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/reset.png"),
            12, 12, 0xFF2E3A42, 0xFF3E4A52, 0xFF1E262A,
            btn -> requestResetTag());
        this.addRenderableWidget(this.resetButton);

        // Button to switch to Item Tag Studio
        this.switchToItemTagButton = Button.builder(Component.literal("<< Itens"), btn -> {
            if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.hasPermissions(2)) {
                this.minecraft.setScreen(new TagStudioScreen());
            }
        }).bounds(this.width - 120, 2, 118, 16).build();
        this.addRenderableWidget(this.switchToItemTagButton);

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
            // Filtra por tag ID OU por entidade contida na tag
            this.filteredTags.removeIf(tag -> {
                // Match por ID da tag
                if (tag.tagId().toLowerCase(Locale.ROOT).contains(query)) return false;
                // Match por entidade dentro da tag
                for (String entityId : tag.entityIds()) {
                    if (entityId.toLowerCase(Locale.ROOT).contains(query)) return false;
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

        this.filteredTags.sort(Comparator.comparing(EntityTagEntry::tagId));

        boolean selectionExists = false;
        for (EntityTagEntry entry : this.filteredTags) {
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

    public void applyTagsFromServer(List<RequestEntityTagsPacket.EntityTagDTO> tags) {
        this.allTags.clear();
        if (tags != null) {
            for (RequestEntityTagsPacket.EntityTagDTO dto : tags) {
                if (dto != null) {
                    boolean isVanilla = dto.tagId().startsWith("minecraft:");
                    this.allTags.add(new EntityTagEntry(dto.tagId(), dto.entityIds(), isVanilla));
                }
            }
        }
        this.allTags.sort(Comparator.comparing(EntityTagEntry::tagId));
        this.suppressSearchRequest = true;
        applySearchFilter();
        this.suppressSearchRequest = false;
    }

    private void rebuildTagButtons() {
        this.tagButtons.clear();
        this.buttonOriginalY.clear();
        // NÃO resetar leftScrollAmount aqui para manter posição ao abrir/fechar pais

        // Build parent-child relationships
        java.util.Map<String, List<EntityTagEntry>> childrenMap = new java.util.HashMap<>();
        java.util.Set<String> allTagIds = new java.util.HashSet<>();
        java.util.Map<String, EntityTagEntry> tagById = new java.util.HashMap<>();

        for (EntityTagEntry entry : this.filteredTags) {
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
        for (List<EntityTagEntry> children : childrenMap.values()) {
            for (EntityTagEntry child : children) {
                childTags.add(child.tagId());
            }
        }
        java.util.List<EntityTagEntry> rootTags = new ArrayList<>();
        for (EntityTagEntry entry : this.filteredTags) {
            if (!childTags.contains(entry.tagId())) {
                rootTags.add(entry);
            }
        }
        rootTags.sort(Comparator.comparing(EntityTagEntry::tagId));

        // Render tree recursively
        int[] currentY = new int[]{this.listAreaY + 1};
        for (EntityTagEntry root : rootTags) {
            renderTagTree(root, childrenMap, tagById, currentY, 0);
        }

        int listBottom = this.listAreaY + this.listAreaH - 20;
        this.maxLeftScroll = Math.max(0, currentY[0] - listBottom);
        this.leftScrollAmount = Mth.clamp(this.leftScrollAmount, 0.0D, this.maxLeftScroll);
        applyLeftScroll();
    }

    private void renderTagTree(EntityTagEntry entry, java.util.Map<String, List<EntityTagEntry>> childrenMap,
            java.util.Map<String, EntityTagEntry> tagById, int[] currentY, int depth) {

        String tagId = entry.tagId();
        boolean hasChildren = childrenMap.containsKey(tagId);
        boolean isCollapsed = hasChildren && this.collapsedParents.contains(tagId);
        int indent = depth * 10;
        int btnX = this.listAreaX + 1 + indent;
        int btnW = Math.max(10, this.listAreaW - 4 - indent);

        int index = this.tagButtons.size();
        EntityTagButton button = new EntityTagButton(btnX, currentY[0], btnW, 20, tagId, hasChildren, isCollapsed, btn -> {
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
            List<EntityTagEntry> children = childrenMap.get(tagId);
            if (children != null) {
                // Sort children by tagId
                children.sort(Comparator.comparing(EntityTagEntry::tagId));
                for (EntityTagEntry child : children) {
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
        this.tagEntities.clear();
        this.centerScrollAmount = 0.0D;
        this.isDirty = false;

        if (id != null) {
            for (EntityTagEntry entry : this.filteredTags) {
                if (entry.tagId().equals(id)) {
                    this.tagEntities.addAll(entry.entityIds());
                    break;
                }
            }
        }
    }

    private void addEntityToTag() {
        if (this.selectedTagId == null) return;
        String entityId = this.newEntityInput != null ? this.newEntityInput.getValue().trim() : "";
        if (entityId.isEmpty()) return;

        ResourceLocation rl = ResourceLocation.tryParse(entityId);
        if (rl == null) {
            this.entityInputValid = false;
            updateEntityInputValidation();
            return;
        }

        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (entityType == null) {
            this.entityInputValid = false;
            updateEntityInputValidation();
            return;
        }

        // Check if already in list
        for (String existing : this.tagEntities) {
            if (existing.equals(entityId)) {
                this.entityInputValid = false;
                updateEntityInputValidation();
                return;
            }
        }

        this.entityInputValid = true;
        updateEntityInputValidation();
        this.tagEntities.add(entityId);
        this.isDirty = true;
        if (this.newEntityInput != null) this.newEntityInput.setValue("");
    }

    private void validateEntityInput(String value) {
        if (value == null || value.trim().isEmpty()) {
            this.entityInputValid = true;
            updateEntityInputValidation();
            return;
        }
        
        ResourceLocation rl = ResourceLocation.tryParse(value.trim());
        if (rl == null) {
            this.entityInputValid = false;
            updateEntityInputValidation();
            return;
        }

        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (entityType == null) {
            this.entityInputValid = false;
        } else {
            this.entityInputValid = true;
        }
        updateEntityInputValidation();
    }

    private void updateEntityInputValidation() {
        if (this.newEntityInput instanceof SelectableEditBox selectableBox) {
            selectableBox.setInvalidState(!this.entityInputValid);
        }
    }

    private void requestResetTag() {
        if (this.selectedTagId == null) return;
        // Revert to original state
        this.tagEntities.clear();
        for (EntityTagEntry entry : this.filteredTags) {
            if (entry.tagId().equals(this.selectedTagId)) {
                this.tagEntities.addAll(entry.entityIds());
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
        for (EntityTagEntry entry : this.allTags) {
            if (entry.tagId().equals(tagId)) { cancelNewTag(); return; }
        }

        EntityTagEntry newEntry = new EntityTagEntry(tagId, new ArrayList<>(), tagId.startsWith("minecraft:"));
        this.allTags.add(newEntry);
        this.allTags.sort(Comparator.comparing(EntityTagEntry::tagId));

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
        NetworkHandler.sendToServer(new SaveEntityTagPacket(this.selectedTagId, new ArrayList<>(this.tagEntities)));
        this.isDirty = false;
    }

    private void registerRightPanelWidget(AbstractWidget widget) {
        this.rightPanelWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    private void recalculateRightPanel() {
        int padding = 8;
        int gap = 6;
        int startY = this.rightY + 28;
        int currentY = startY;
        int actionW = this.rightW - (padding * 2);

        for (AbstractWidget widget : this.rightPanelWidgets) {
            widget.setX(this.rightX + padding);
            widget.setY(currentY);
            widget.setWidth(actionW);
            this.widgetOriginalY.put(widget, currentY);
            currentY += widget.getHeight() + gap;
        }

        int bottom = this.rightY + this.rightH - 8;
        this.maxRightScroll = Math.max(0, currentY - bottom);
        this.rightScrollAmount = Mth.clamp(this.rightScrollAmount, 0.0D, this.maxRightScroll);
        applyRightScroll();
    }

    private void applyRightScroll() {
        for (AbstractWidget widget : this.rightPanelWidgets) {
            int originalY = this.widgetOriginalY.get(widget);
            widget.setY(originalY - (int) this.rightScrollAmount);
        }
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

        graphics.drawString(this.font, "Tags de Entidades", this.leftX + 8, this.leftY + 8, 0xFFF4F7FF);

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
        graphics.drawString(this.font, "Entidades da Tag: " + this.font.plainSubstrByWidth(selectedTitle, Math.max(40, this.centerW - 44)), this.centerX + 8, this.centerY + 8, 0xFFF4F7FF);
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

        renderCenterEntityList(graphics, mouseX, mouseY, partialTick);

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
            // Label for the entity input
            if (this.newEntityInput != null) {
                graphics.drawString(this.font, "ID da Entidade:", this.rightX + 8, this.newEntityInput.getY() - 10, 0xFFD4E0F0);
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
            renderFilterOverlay(graphics, mouseX, mouseY, partialTick);
        }

        if (this.showNewTagInput && this.newTagInput != null) {
            this.setFocused(this.newTagInput);
            this.newTagInput.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderCenterEntityList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.tagEntities.isEmpty()) return;
        int startY = this.centerY + 28;
        int rowH = 20;

        graphics.enableScissor(this.centerX + 2, startY, this.centerX + this.centerW - 2, this.centerY + this.centerH - 2);

        for (int i = 0; i < this.tagEntities.size(); i++) {
            int rowY = startY + (i * rowH) - (int) this.centerScrollAmount;
            if (rowY + rowH < this.centerY + 28 || rowY > this.centerY + this.centerH - 2) continue;

            String entityId = this.tagEntities.get(i);
            boolean hovered = mouseY >= rowY && mouseY < rowY + rowH && mouseX >= this.centerX + 8 && mouseX < this.centerX + this.centerW - 8;

            graphics.fill(this.centerX + 4, rowY, this.centerX + this.centerW - 4, rowY + rowH - 1,
                hovered ? 0x442B4E72 : 0x22202A36);

            ResourceLocation rl = ResourceLocation.tryParse(entityId);
            EntityType<?> entityType = rl != null ? ForgeRegistries.ENTITY_TYPES.getValue(rl) : null;
            
            // Try to render entity icon or fallback to barrier
            String displayName = entityId;
            int lastSlash = displayName.lastIndexOf('/');
            if (lastSlash >= 0) {
                displayName = displayName.substring(lastSlash + 1);
            }
            
            graphics.drawString(this.font, displayName, this.centerX + 8, rowY + 6, 0xFFD4E0F0);

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
            EntityTagButton tb = this.tagButtons.get(i);
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

        double thumbH = Math.max(8, (double) barH / (this.maxLeftScroll + barH) * barH);
        double thumbY = barY + (this.maxLeftScroll > 0 ? (this.leftScrollAmount / this.maxLeftScroll) * (barH - thumbH) : 0);

        int thumbColor = this.leftScrollbarDragging ? 0xFF666666 : 0xFF555555;
        graphics.fill(barX, (int) thumbY, barX + barW, (int) (thumbY + thumbH), thumbColor);
    }

    private boolean isInsideCenterScrollbar(double mouseX, double mouseY) {
        int barX = this.centerX + this.centerW - 10;
        int barY = this.centerY + 28;
        int barW = 6;
        int barH = this.centerH - 36;
        return mouseX >= barX && mouseX < barX + barW && mouseY >= barY && mouseY < barY + barH;
    }

    private boolean centerScrollbarDragging = false;

    private void startCenterScrollbarDrag(double mouseY) {
        this.centerScrollbarDragging = true;
    }

    private void updateCenterScrollbarDrag(double mouseY) {
        if (!this.centerScrollbarDragging || this.maxCenterScroll <= 0) return;
        int barY = this.centerY + 28;
        int barH = this.centerH - 36;
        double ratio = (mouseY - barY) / barH;
        this.centerScrollAmount = Mth.clamp(ratio * this.maxCenterScroll, 0.0D, this.maxCenterScroll);
    }

    private void renderCenterScrollbar(GuiGraphics graphics) {
        if (this.maxCenterScroll <= 0) return;
        int barX = this.centerX + this.centerW - 10;
        int barY = this.centerY + 28;
        int barW = 6;
        int barH = this.centerH - 36;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);

        double thumbH = Math.max(8, (double) barH / (this.maxCenterScroll + barH) * barH);
        double thumbY = barY + (this.maxCenterScroll > 0 ? (this.centerScrollAmount / this.maxCenterScroll) * (barH - thumbH) : 0);

        int thumbColor = this.centerScrollbarDragging ? 0xFF666666 : 0xFF555555;
        graphics.fill(barX, (int) thumbY, barX + barW, (int) (thumbY + thumbH), thumbColor);
    }

    // ===================== MOUSE EVENTS =====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check scrollbars FIRST (before tag buttons)
            if (this.maxLeftScroll > 0 && isInsideLeftScrollbar(mouseX, mouseY)) {
                startLeftScrollbarDrag(mouseY);
                return true;
            }

            if (this.maxCenterScroll > 0 && isInsideCenterScrollbar(mouseX, mouseY)) {
                startCenterScrollbarDrag(mouseY);
                return true;
            }

            // Check tag buttons
            for (EntityTagButton tb : this.tagButtons) {
                if (tb.mouseClicked(mouseX, mouseY, button)) return true;
            }

            // Check "+" button for new tag
            int newTagBtnX = this.listAreaX + this.listAreaW - 4;
            int newTagBtnY = this.leftY + 6;
            if (mouseX >= newTagBtnX && mouseX < newTagBtnX + 16 && mouseY >= newTagBtnY && mouseY < newTagBtnY + 16) {
                this.showNewTagInput = true;
                this.newTagInput.visible = true;
                this.confirmNewTagButton.visible = true;
                this.newTagInput.setValue(this.defaultNamespace + ":");
                this.setFocused(this.newTagInput);
                return true;
            }

            // Check delete buttons in center panel
            if (this.selectedTagId != null) {
                int startY = this.centerY + 28;
                int rowH = 20;
                for (int i = 0; i < this.tagEntities.size(); i++) {
                    int rowY = startY + (i * rowH) - (int) this.centerScrollAmount;
                    int delX = this.centerX + this.centerW - 22;
                    if (mouseX >= delX && mouseX < delX + 14 && mouseY >= rowY + 3 && mouseY < rowY + 17) {
                        this.tagEntities.remove(i);
                        this.isDirty = true;
                        return true;
                    }
                }
            }

            // Click outside filter overlay closes it
            if (this.filterOverlayOpen && this.filterButton != null) {
                int overlayX = this.filterButton.getX() + this.filterButton.getWidth() + 4;
                int overlayY = this.filterButton.getY();
                int overlayW = 120;
                int overlayH = 60;
                if (mouseX < overlayX || mouseX >= overlayX + overlayW || mouseY < overlayY || mouseY >= overlayY + overlayH) {
                    this.filterOverlayOpen = false;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            if (this.leftScrollbarDragging) {
                updateLeftScrollbarDrag(mouseY);
                return true;
            }
            if (this.centerScrollbarDragging) {
                updateCenterScrollbarDrag(mouseY);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.leftScrollbarDragging = false;
            this.centerScrollbarDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        // Check if mouse is over left panel
        if (mouseX >= this.leftX && mouseX < this.leftX + this.leftW &&
            mouseY >= this.listAreaY && mouseY < this.listAreaY + this.listAreaH) {
            if (this.maxLeftScroll > 0) {
                this.leftScrollAmount = Mth.clamp(this.leftScrollAmount - scrollY * 12.0D, 0.0D, this.maxLeftScroll);
                applyLeftScroll();
            }
            return true;
        }

        // Check if mouse is over center panel
        if (mouseX >= this.centerX && mouseX < this.centerX + this.centerW &&
            mouseY >= this.centerY + 28 && mouseY < this.centerY + this.centerH - 8) {
            if (this.maxCenterScroll > 0) {
                this.centerScrollAmount = Mth.clamp(this.centerScrollAmount - scrollY * 12.0D, 0.0D, this.maxCenterScroll);
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    // ===================== FILTER OVERLAY =====================

    private void renderFilterOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.filterButton == null) return;
        int overlayX = this.filterButton.getX() + this.filterButton.getWidth() + 4;
        int overlayY = this.filterButton.getY();
        int overlayW = 120;
        int overlayH = 60;

        graphics.fill(overlayX, overlayY, overlayX + overlayW, overlayY + overlayH, 0xDD1A2434);
        graphics.fill(overlayX, overlayY, overlayX + overlayW, overlayY + 1, 0xFF4E6B8D);
        graphics.fill(overlayX, overlayY + overlayH - 1, overlayX + overlayW, overlayY + overlayH, 0xFF4E6B8D);
        graphics.fill(overlayX, overlayY, overlayX + 1, overlayY + overlayH, 0xFF4E6B8D);
        graphics.fill(overlayX + overlayW - 1, overlayY, overlayX + overlayW, overlayY + overlayH, 0xFF4E6B8D);

        // Mod toggle
        boolean modHover = mouseX >= overlayX + 4 && mouseX < overlayX + overlayW - 4 &&
                           mouseY >= overlayY + 4 && mouseY < overlayY + 18;
        graphics.fill(overlayX + 4, overlayY + 4, overlayX + overlayW - 4, overlayY + 18,
            modHover ? 0x442B4E72 : 0x22202A36);
        graphics.drawString(this.font, "Mod: " + (this.searchMod ? "ON" : "OFF"), overlayX + 8, overlayY + 8,
            this.searchMod ? 0xFF44FF44 : 0xFFFF4444);

        // Vanilla toggle
        boolean vanHover = mouseX >= overlayX + 4 && mouseX < overlayX + overlayW - 4 &&
                           mouseY >= overlayY + 24 && mouseY < overlayY + 38;
        graphics.fill(overlayX + 4, overlayY + 24, overlayX + overlayW - 4, overlayY + 38,
            vanHover ? 0x442B4E72 : 0x22202A36);
        graphics.drawString(this.font, "Vanilla: " + (this.searchVanilla ? "ON" : "OFF"), overlayX + 8, overlayY + 28,
            this.searchVanilla ? 0xFF44FF44 : 0xFFFF4444);

        if (modHover || vanHover) {
            // Tooltip hint
            graphics.drawString(this.font, "Clique para alternar", overlayX + 8, overlayY + 44, 0xFF888888);
        }
    }

    // ===================== TAG BUTTONS =====================

    private void renderTagButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.tagButtons.isEmpty()) return;
        graphics.enableScissor(this.listAreaX, this.listAreaY, this.listAreaX + this.listAreaW, this.listAreaY + this.listAreaH);
        for (EntityTagButton tb : this.tagButtons) {
            tb.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
    }

    // ===================== TAG ENTRY =====================

    private record EntityTagEntry(String tagId, List<String> entityIds, boolean isVanilla) {
    }

    // ===================== TAG BUTTON CLASS =====================

    private final class EntityTagButton extends Button {
        private final String id;
        private final boolean isParent;
        private final boolean isCollapsed;

        private EntityTagButton(int x, int y, int width, int height, String id, boolean isParent, boolean isCollapsed, OnPress onPress) {
            super(x, y, width, height, Component.literal(id), onPress, DEFAULT_NARRATION);
            this.id = id;
            this.isParent = isParent;
            this.isCollapsed = isCollapsed;
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            boolean selected = Objects.equals(this.id, EntityTagStudioScreen.this.selectedTagId);
            boolean hovered = this.isHoveredOrFocused();
            int color = selected ? 0xAA2B4E72 : (hovered ? 0xAA263445 : 0x55202A36);
            graphics.fill(this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), color);

            // Expand/collapse icon for parent tags
            if (this.isParent) {
                String arrow = this.isCollapsed ? "▶ " : "▼ ";
                graphics.drawString(EntityTagStudioScreen.this.font, arrow, this.getX() + 2, this.getY() + 6, 0xFF8BD3FF);
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
            graphics.drawString(EntityTagStudioScreen.this.font, displayName, textX, this.getY() + 6, textColor);
        }
    }
}
