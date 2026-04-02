package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.DisplayEditorFormModel;
import com.gabri.serverfixes.client.gui.editor.FormattingPreviewEditBox;
import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.commands.ItemAttributeCommands;
import com.gabri.serverfixes.config.ServerFixesConfig;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.SaveItemEditorPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@SuppressWarnings("null")
public class ItemEditorScreen extends Screen {
    public CompoundTag currentTag;
    private Category currentCategory = Category.MENU;
    // Active sub-tab used when the sidebar category is the single Effects entry
    private Category activeEffectTab = Category.ON_USE;
    private List<AttributeRow> attributeRows = new ArrayList<>();
    private DisplayEditorFormModel displayForm;
    private FormattingPreviewEditBox displayNameBox;
    private FormattingPreviewEditBox loreLineBox;
    private SelectableEditBox hexColorBox;
    private String customHexColor = "#ffffff";
    private int selectedLoreLine = -1;
    // Staged values: edits are applied to these and only committed on '+' or Save
    private String stagedName = null;
    private String stagedLoreText = null;
    private boolean showLoreEditor = false;
    private int loreScrollOffset = 0;
    private int attributeScrollOffset = 0;
    private int effectScrollOffset = 0;
    private int enchantmentScrollOffset = 0;
    private List<EnchantmentRow> enchantmentRows = new ArrayList<>();
    private ScrollTarget draggingScrollTarget = ScrollTarget.NONE;
    private int draggingThumbOffsetY = 0;
    private final boolean editingEnchantedBook;

    private static final int SIDEBAR_WIDTH = 150;
    private static final int CONTENT_PADDING = 12;
    private static final int ROW_HEIGHT = 26;
    private static final int LIST_START_Y = 70;
    private static final int MAX_VISIBLE_ROWS = 10;
    private static final int TOOLBAR_BUTTON_WIDTH = 16;
    private static final int TOOLBAR_GAP = 2;
    private static final int COLOR_BUTTON_SIZE = 8;
    private static final int COLOR_GRID_GAP = 0;
    private static final int DISPLAY_TOP_Y = 42;
    private static final int DISPLAY_LABEL_INPUT_GAP = 2;
    private static final int DISPLAY_SECTION_GAP = 8;
    private static final int EFFECT_TAB_Y = 42;
    private static final int EFFECT_TAB_HEIGHT = 18;
    private static final int EFFECT_TAB_GAP = 4;
    private static final int LIST_BOTTOM_PADDING = 42;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_GUTTER = 12;
    private static final int EFFECT_ICON_SIZE = 18;
    private static final int EFFECT_ICON_GAP = 6;
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)^#[0-9a-f]{6}$");
    private static final ResourceLocation EDIT_ICON = ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/pen-to-square-solid.png");
    private static final ResourceLocation DELETE_ICON = ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/xmark-solid.png");
    private static final ResourceLocation ADD_ICON = ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/add.png");
    private static final ResourceLocation SAVE_ICON = ResourceLocation.fromNamespaceAndPath("serverfixes", "textures/gui/save.png");
    private static final String HIDE_FLAGS_TAG = "HideFlags";
    private static final String SF_HIDE_EFFECT_FLAGS_TAG = "SF_HideEffectFlags";

    public enum Category {
        MENU("Editor de Item"),
        DISPLAY("Display"),
        ATTRIBUTES("Atributos"),
        ENCHANTMENTS("Encantamentos"),
        HIDE_FLAGS("Hide Flags"),
        POTIONS("Efeitos"),
        ON_USE("Efeitos Ao Usar (On Use)"),
        ON_HIT("Efeitos Ao Atacar (On Hit)"),
        ON_HURT("Efeitos Ao Receber (On Hurt)"),
        ON_EQUIP("Efeitos Ao Equipar (On Equip)");

        public final String title;
        Category(String title) { this.title = title; }
    }

    public ItemEditorScreen(CompoundTag itemTag) {
        super(Component.literal("Item Editor"));
        this.currentTag = itemTag.copy();
        this.editingEnchantedBook = detectEnchantedBookContext(itemTag);
        this.displayForm = DisplayEditorFormModel.fromTag(this.currentTag);
    }

    private static boolean detectEnchantedBookContext(CompoundTag itemTag) {
        if (itemTag != null && itemTag.contains("StoredEnchantments", Tag.TAG_LIST)) {
            return true;
        }
        if (itemTag != null && itemTag.contains("Enchantments", Tag.TAG_LIST)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            ItemStack mainHand = minecraft.player.getMainHandItem();
            return !mainHand.isEmpty() && mainHand.is(Items.ENCHANTED_BOOK);
        }
        return false;
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
            // Skip adding duplicate sidebar entries for effect sub-tabs (they live under the single Effects sidebar)
            if (cat == Category.ON_HIT || cat == Category.ON_HURT || cat == Category.ON_USE || cat == Category.ON_EQUIP) continue;
            boolean active = currentCategory == cat;
            Component title = Component.literal((active ? "§6> " : "") + cat.title);
            this.addRenderableWidget(Button.builder(title, (btn) -> {
                if (this.currentCategory == Category.DISPLAY && cat != Category.DISPLAY) {
                    applyDisplayChanges();
                    this.selectedLoreLine = -1;
                }
                this.currentCategory = cat;
                // when opening the effects sidebar, default to On Use tab
                if (cat == Category.POTIONS) this.activeEffectTab = Category.ON_USE;
                this.init();
            }).bounds(8, y, buttonWidth, 22).build());
            y += 28;
        }

        if (currentCategory != Category.MENU) {
            if (currentCategory == Category.DISPLAY) {
                initDisplayCategory(contentStartX, contentWidth);
            } else {
                if (isEffectsCategory(this.currentCategory)) {
                    addEffectCategoryTabs(contentStartX, contentWidth);
                }
                initCategoryList(contentStartX, contentWidth);
                boolean showAddButton = this.currentCategory == Category.ATTRIBUTES
                    || this.currentCategory == Category.ENCHANTMENTS
                    || isEffectsCategory(this.currentCategory);
                if (showAddButton) {
                    int addWidth = 22;
                    int addButtonY = isEffectsCategory(this.currentCategory) ? EFFECT_TAB_Y : 42;
                    // Use IconButton with add asset for consistency
                    this.addRenderableWidget(new IconButton(contentStartX + contentWidth - addWidth, addButtonY, addWidth, 18,
                        ADD_ICON, 16, 16,
                        0xFF2E7D32, 0xFF43A047, 0xFF66BB6A,
                        (btn) -> {
                            if (currentCategory == Category.ATTRIBUTES) {
                                this.minecraft.setScreen(new AddAttributeScreen(this));
                            } else if (currentCategory == Category.ENCHANTMENTS) {
                                this.minecraft.setScreen(new AddEnchantmentScreen(this));
                            } else {
                                Category viewCat = isEffectsCategory(this.currentCategory) ? this.activeEffectTab : this.currentCategory;
                                if (shouldUseVanillaOnUseStorage(viewCat)) {
                                    canonicalizeOnUsePotionStorage();
                                }
                                this.minecraft.setScreen(new AddEffectScreen(this, viewCat));
                            }
                        }));
                }
            }
        }

        int bottomY = this.height - 30;
        if (currentCategory == Category.MENU) {
            this.addRenderableWidget(Button.builder(Component.literal("Salvar no Item"), (btn) -> {
                applyDisplayChanges();
                normalizePotionEffectsForCompatibility();
                NetworkHandler.sendToServer(new SaveItemEditorPacket(this.currentTag));
                this.minecraft.setScreen(null);
            }).bounds(contentMidX - 105, bottomY, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> this.minecraft.setScreen(null))
                .bounds(contentMidX + 5, bottomY, 100, 20).build());
        } else if (currentCategory == Category.DISPLAY) {
            this.addRenderableWidget(Button.builder(Component.literal("Salvar no Item"), (btn) -> {
                applyDisplayChanges();
                normalizePotionEffectsForCompatibility();
                NetworkHandler.sendToServer(new SaveItemEditorPacket(this.currentTag));
                // Close the screen after saving (match MENU behavior)
                this.minecraft.setScreen(null);
            }).bounds(contentMidX - 105, bottomY, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Voltar"), (btn) -> {
                applyDisplayChanges();
                this.currentCategory = Category.MENU;
                this.init();
            }).bounds(contentMidX + 5, bottomY, 100, 20).build());
        } else if (currentCategory == Category.ATTRIBUTES) {
            // On attributes screen show Save and Back
            this.addRenderableWidget(Button.builder(Component.literal("Salvar no Item"), (btn) -> {
                normalizePotionEffectsForCompatibility();
                NetworkHandler.sendToServer(new SaveItemEditorPacket(this.currentTag));
                this.minecraft.setScreen(null);
            }).bounds(contentMidX - 105, bottomY, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Voltar"), (btn) -> {
                this.currentCategory = Category.MENU;
                this.init();
            }).bounds(contentMidX + 5, bottomY, 100, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Salvar no Item"), (btn) -> {
                normalizePotionEffectsForCompatibility();
                NetworkHandler.sendToServer(new SaveItemEditorPacket(this.currentTag));
                this.minecraft.setScreen(null);
            }).bounds(contentMidX - 105, bottomY, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Voltar"), (btn) -> {
                this.currentCategory = Category.MENU;
                this.init();
            }).bounds(contentMidX + 5, bottomY, 100, 20).build());
        }
    }

    private void initCategoryList(int contentStartX, int contentWidth) {
        if (currentCategory == Category.DISPLAY) {
            return;
        }
        if (currentCategory == Category.ATTRIBUTES) {
            initAttributeList(contentStartX, contentWidth);
            return;
        }
        if (currentCategory == Category.ENCHANTMENTS) {
            initEnchantmentList(contentStartX, contentWidth);
            return;
        }
        if (currentCategory == Category.HIDE_FLAGS) {
            initHideFlagsList(contentStartX, contentWidth);
            return;
        }

        String tagName = getTagName();
        if (tagName == null) return;

        Category viewCat = effectiveCategory();
        final boolean useVanillaOnUse = shouldUseVanillaOnUseStorage(viewCat);
        if (useVanillaOnUse) {
            canonicalizeOnUsePotionStorage();
        }

        ListTag list;
        final CompoundTag effectCompound;
        final String effectKey;
        if (useVanillaOnUse) {
            effectCompound = null;
            effectKey = null;
            list = this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
        } else if (viewCat == Category.ON_HIT || viewCat == Category.ON_HURT || viewCat == Category.ON_USE || viewCat == Category.ON_EQUIP) {
            effectCompound = this.currentTag.getCompound("SF_ItemEffects");
            if (viewCat == Category.ON_HIT) effectKey = "on_hit";
            else if (viewCat == Category.ON_HURT) effectKey = "on_hurt";
            else if (viewCat == Category.ON_EQUIP) effectKey = "on_equip";
            else effectKey = "on_use";
            list = effectCompound.getList(effectKey, Tag.TAG_COMPOUND);
        } else {
            effectCompound = null;
            effectKey = null;
            list = this.currentTag.getList(tagName, Tag.TAG_COMPOUND);
        }

        final ListTag displayList = list != null ? list : new ListTag();

        EffectTableLayout layout = computeEffectTableLayout(contentStartX, contentWidth);
        int total = displayList.size();
        int firstRowY = LIST_START_Y + layout.headerHeight;
        int visibleSlots = computeVisibleRows(firstRowY);
        this.effectScrollOffset = clampScrollOffset(this.effectScrollOffset, total, visibleSlots);
        int visible = Math.min(visibleSlots, Math.max(0, total - this.effectScrollOffset));
        for (int i = 0; i < visible; i++) {
            final int index = this.effectScrollOffset + i;
            int yPos = LIST_START_Y + layout.headerHeight + (i * ROW_HEIGHT);
            this.addRenderableWidget(createEditActionButton(layout.editButtonX, yPos + 2, 18, 16, (btn) ->
                this.minecraft.setScreen(new AddEffectScreen(this, viewCat, index)), "Editar"));
            this.addRenderableWidget(createDeleteActionButton(layout.deleteButtonX, yPos + 2, 18, 16, (btn) -> {
                if (useVanillaOnUse) {
                    ListTag custom = this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
                    if (index >= 0 && index < custom.size()) {
                        custom.remove(index);
                    }
                    if (custom.isEmpty()) {
                        this.currentTag.remove("CustomPotionEffects");
                        this.currentTag.putString("Potion", "minecraft:water");
                    } else {
                        this.currentTag.put("CustomPotionEffects", custom);
                        this.currentTag.putString("Potion", "minecraft:water");
                    }
                } else {
                    ListTag target = displayList;
                    target.remove(index);
                    if (effectCompound != null && effectKey != null) {
                        effectCompound.put(effectKey, target);
                        this.currentTag.put("SF_ItemEffects", effectCompound);
                    } else {
                        persistList(tagName, target);
                    }
                }
                int newTotal = useVanillaOnUse
                    ? this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND).size()
                    : displayList.size();
                this.effectScrollOffset = clampScrollOffset(this.effectScrollOffset, newTotal, visibleSlots);
                this.init();
            }, "Deletar"));
        }
    }

    private boolean isEffectsCategory(Category category) {
        // The sidebar exposes a single 'Effects' entry (`POTIONS`). Sub-tabs are managed with `activeEffectTab`.
        return category == Category.POTIONS;
    }

    private Category effectiveCategory() {
        return isEffectsCategory(this.currentCategory) ? this.activeEffectTab : this.currentCategory;
    }

    private void addEffectCategoryTabs(int contentStartX, int contentWidth) {
        int tabAreaWidth = Math.max(240, contentWidth - 34);
        int tabsCount = 4;
        int tabWidth = (tabAreaWidth - (EFFECT_TAB_GAP * (tabsCount - 1))) / tabsCount;
        int startX = contentStartX + 10;
        this.addRenderableWidget(createEffectCategoryTab(startX, EFFECT_TAB_Y, tabWidth, "On Use", Category.ON_USE));
        this.addRenderableWidget(createEffectCategoryTab(startX + (tabWidth + EFFECT_TAB_GAP), EFFECT_TAB_Y, tabWidth, "On Hit", Category.ON_HIT));
        this.addRenderableWidget(createEffectCategoryTab(startX + (tabWidth + EFFECT_TAB_GAP) * 2, EFFECT_TAB_Y, tabWidth, "On Hurt", Category.ON_HURT));
        this.addRenderableWidget(createEffectCategoryTab(startX + (tabWidth + EFFECT_TAB_GAP) * 3, EFFECT_TAB_Y, tabWidth, "On Equip", Category.ON_EQUIP));
    }

    private Button createEffectCategoryTab(int x, int y, int width, String label, Category category) {
        String prefix = this.activeEffectTab == category ? "§6" : "§7";
        return Button.builder(Component.literal(prefix + label), (btn) -> {
            this.activeEffectTab = category;
            this.init();
        }).bounds(x, y, width, EFFECT_TAB_HEIGHT).build();
    }

    private void initAttributeList(int contentStartX, int contentWidth) {
        this.attributeRows = buildAttributeRows();
        AttributeTableLayout layout = computeAttributeTableLayout(contentStartX, contentWidth);
        int total = this.attributeRows.size();
        int firstRowY = LIST_START_Y + layout.headerHeight;
        int visibleSlots = computeVisibleRows(firstRowY);
        this.attributeScrollOffset = clampScrollOffset(this.attributeScrollOffset, total, visibleSlots);
        int visible = Math.min(visibleSlots, Math.max(0, total - this.attributeScrollOffset));
        for (int i = 0; i < visible; i++) {
            final int index = this.attributeScrollOffset + i;
            AttributeRow row = this.attributeRows.get(index);
            int rowY = LIST_START_Y + layout.headerHeight + (i * ROW_HEIGHT);
            this.addRenderableWidget(createEditActionButton(layout.editButtonX, rowY + 2, 18, 16, (btn) -> openEditAttribute(row), "Editar"));
            this.addRenderableWidget(createDeleteActionButton(layout.deleteButtonX, rowY + 2, 18, 16, (btn) -> {
                removeAttributeRow(row);
                // adjust offset
                int newTotal = this.attributeRows.size();
                this.attributeScrollOffset = clampScrollOffset(this.attributeScrollOffset, newTotal, visibleSlots);
                this.init();
            }, "Deletar"));
        }
    }

    private void initDisplayCategory(int contentStartX, int contentWidth) {
        if (this.font == null) {
            return;
        }
        if (this.displayForm == null) {
            this.displayForm = DisplayEditorFormModel.fromTag(this.currentTag);
        }

        DisplayLayout layout = computeDisplayLayout(contentStartX, contentWidth);
        addTextFormattingToolbar(layout.toolbarX, layout.toolbarY);
        addHexFormattingControls(layout.hexInputX, layout.toolbarY);

        // Name input sizing: when lore editor hidden, shrink name field to show small '+' next to it.
        int nameFieldWidth = layout.fieldWidth;
        if (!this.showLoreEditor) {
            nameFieldWidth = Math.max(120, layout.fieldWidth - 26);
        }

        this.displayNameBox = new FormattingPreviewEditBox(this.font, layout.fieldX, layout.nameInputY, nameFieldWidth, 20, Component.literal("Nome customizado"));
        if (this.stagedName == null) this.stagedName = this.displayForm.getCustomName();
        this.displayNameBox.setLegacyValue(this.stagedName);
        // Update staged value only; commit when saving the display section
        this.displayNameBox.setResponder(value -> this.stagedName = this.displayNameBox.getLegacyValue());
        this.addRenderableWidget(this.displayNameBox);

        if (!this.showLoreEditor) {
            // Small '+' next to name that reveals the lore input area
            this.addRenderableWidget(new IconButton(layout.fieldX + nameFieldWidth + 4, layout.nameInputY, 22, 20,
                ADD_ICON, 16, 16,
                0xFF2E7D32, 0xFF43A047, 0xFF66BB6A,
                (btn) -> {
                    // show lore editor and prepare staged lore
                    this.showLoreEditor = true;
                    this.stagedLoreText = "";
                    this.init();
                }));
        } else {
            // Show lore input and its own '+' to add/save lore
            this.loreLineBox = new FormattingPreviewEditBox(this.font, layout.fieldX, layout.loreInputY, layout.loreInputWidth, 20, Component.literal("Lore"));
            String selectedValue = "";
            if (this.selectedLoreLine >= 0 && this.selectedLoreLine < this.displayForm.getLoreLines().size()) {
                selectedValue = this.displayForm.getLoreLines().get(this.selectedLoreLine);
            }
            if (this.stagedLoreText == null) this.stagedLoreText = selectedValue;
            this.loreLineBox.setLegacyValue(this.stagedLoreText);
            // Update staged lore only; commit when '+' is pressed
            this.loreLineBox.setResponder(value -> this.stagedLoreText = this.loreLineBox.getLegacyValue());
            this.addRenderableWidget(this.loreLineBox);

            // Use SAVE icon when editing an existing line
            ResourceLocation loreButtonIcon = (this.selectedLoreLine >= 0) ? SAVE_ICON : ADD_ICON;
            this.addRenderableWidget(new IconButton(layout.loreAddButtonX, layout.loreInputY, 22, 20,
                loreButtonIcon, 16, 16,
                0xFF2E7D32, 0xFF43A047, 0xFF66BB6A,
                (btn) -> {
                    if (this.selectedLoreLine >= 0 && this.selectedLoreLine < this.displayForm.getLoreLines().size()) {
                        // Save changes to selected line
                        this.displayForm.setLoreLine(this.selectedLoreLine, this.stagedLoreText != null ? this.stagedLoreText : "");
                        // After saving, hide lore editor and deselect
                        this.selectedLoreLine = -1;
                        this.showLoreEditor = false;
                    } else {
                        // Add a new lore line (allow empty string for spacing)
                        this.displayForm.addLoreLine(this.stagedLoreText != null ? this.stagedLoreText : "");
                        // After adding, hide lore editor
                        this.showLoreEditor = false;
                    }
                    // Clear staged input after insert/save
                    this.stagedLoreText = null;
                    applyDisplayChanges();
                    this.init();
                }));
        }

        int totalLore = this.displayForm.getLoreLines().size();
        int firstRowY = layout.loreRowsStartY;
        int visibleSlots = computeVisibleRows(firstRowY);
        this.loreScrollOffset = clampScrollOffset(this.loreScrollOffset, totalLore, visibleSlots);
        int visibleLore = Math.min(visibleSlots, Math.max(0, totalLore - this.loreScrollOffset));
        int rows = visibleLore;
        int buttonWidth = 18;
        int gap = 4;
        int deleteX = layout.rowDeleteButtonX;
        int editX = deleteX - buttonWidth - gap;
        for (int i = 0; i < rows; i++) {
            final int index = this.loreScrollOffset + i;
            int rowY = layout.loreRowsStartY + (i * ROW_HEIGHT);
            this.addRenderableWidget(createEditActionButton(editX, rowY + 2, buttonWidth, 16, (btn) -> {
                this.selectedLoreLine = index;
                // Open lore editor and preload the selected line text for editing
                this.showLoreEditor = true;
                this.stagedLoreText = this.displayForm.getLoreLines().get(index);
                // ensure selected is visible
                if (this.selectedLoreLine < this.loreScrollOffset) this.loreScrollOffset = this.selectedLoreLine;
                if (this.selectedLoreLine >= this.loreScrollOffset + visibleSlots) this.loreScrollOffset = this.selectedLoreLine - visibleSlots + 1;
                this.init();
            }, this.selectedLoreLine == index ? "Editar linha selecionada" : "Editar"));

            this.addRenderableWidget(createDeleteActionButton(deleteX, rowY + 2, buttonWidth, 16, (btn) -> {
                this.displayForm.removeLoreLine(index);
                if (this.selectedLoreLine == index) {
                    this.selectedLoreLine = -1;
                } else if (this.selectedLoreLine > index) {
                    this.selectedLoreLine--;
                }
                // adjust scroll offset if out of bounds
                int newTotal = this.displayForm.getLoreLines().size();
                this.loreScrollOffset = clampScrollOffset(this.loreScrollOffset, newTotal, visibleSlots);
                applyDisplayChanges();
                this.init();
            }, "Deletar"));
        }

    }

    private void addTextFormattingToolbar(int x, int y) {
        int cursor = x;
        cursor = addFormattingButton(cursor, y, Component.literal("§lB"), "&l", "Negrito");
        cursor = addFormattingButton(cursor, y, Component.literal("§oI"), "&o", "Itálico");
        cursor = addFormattingButton(cursor, y, Component.literal("§nU"), "&n", "Sublinhado");
        cursor = addFormattingButton(cursor, y, Component.literal("§mS"), "&m", "Tachado");
        cursor = addFormattingButton(cursor, y, Component.literal("§kK"), "&k", "Ofuscado");
        addFormattingButton(cursor + TOOLBAR_GAP, y, Component.literal("R"), "&r", "Resetar formatação");

        int paletteX = x + (TOOLBAR_BUTTON_WIDTH + TOOLBAR_GAP) * 7 + 8;
        addColorPaletteButtons(paletteX, y);
    }

    private void addHexFormattingControls(int x, int y) {
        this.hexColorBox = new SelectableEditBox(this.font, x, y, 72, 16, Component.literal("#RRGGBB"));
        this.hexColorBox.setValue(this.customHexColor);
        this.hexColorBox.setResponder(value -> this.customHexColor = value != null ? value.trim() : "#ffffff");
        this.addRenderableWidget(this.hexColorBox);

        this.addRenderableWidget(Button.builder(Component.literal("Hex"), btn -> {
            String value = this.hexColorBox != null ? this.hexColorBox.getValue().trim() : this.customHexColor;
            if (HEX_COLOR_PATTERN.matcher(value).matches()) {
                this.customHexColor = value.toLowerCase(Locale.ROOT);
                applyFormattingToken(this.customHexColor);
            }
        }).bounds(x + 76, y, 30, 16).build());

        this.addRenderableWidget(Button.builder(Component.literal("..."), btn -> {
            this.minecraft.setScreen(new HexColorPickerScreen(this, this.customHexColor, hex -> {
                this.customHexColor = hex;
                applyFormattingToken(hex);
            }));
        }).bounds(x + 110, y, 18, 16).build());
    }

    private void addColorPaletteButtons(int x, int y) {
        ColorToken[] colors = new ColorToken[] {
            // Linha superior: tons escuros
            new ColorToken("&0", "Preto", 0xFF000000),
            new ColorToken("&1", "Azul Escuro", 0xFF0000AA),
            new ColorToken("&2", "Verde Escuro", 0xFF00AA00),
            new ColorToken("&3", "Ciano Escuro", 0xFF00AAAA),
            new ColorToken("&4", "Vermelho Escuro", 0xFFAA0000),
            new ColorToken("&5", "Roxo Escuro", 0xFFAA00AA),
            new ColorToken("&8", "Cinza Escuro", 0xFF555555),
            new ColorToken("&7", "Cinza", 0xFFAAAAAA),
            // Linha inferior: tons claros
            new ColorToken("&6", "Dourado", 0xFFFFAA00),
            new ColorToken("&9", "Azul", 0xFF5555FF),
            new ColorToken("&a", "Verde", 0xFF55FF55),
            new ColorToken("&b", "Ciano", 0xFF55FFFF),
            new ColorToken("&c", "Vermelho", 0xFFFF5555),
            new ColorToken("&d", "Rosa", 0xFFFF55FF),
            new ColorToken("&e", "Amarelo", 0xFFFFFF55),
            new ColorToken("&f", "Branco", 0xFFFFFFFF)
        };

        for (int i = 0; i < colors.length; i++) {
            int col = i % 8;
            int row = i / 8;
            int bx = x + col * (COLOR_BUTTON_SIZE + COLOR_GRID_GAP);
            int by = y + row * (COLOR_BUTTON_SIZE + COLOR_GRID_GAP);
            ColorToken color = colors[i];
            Button button = new ColorSwatchButton(bx, by, COLOR_BUTTON_SIZE, COLOR_BUTTON_SIZE, color.rgb(), (btn) -> applyFormattingToken(color.token()));
            button.setTooltip(Tooltip.create(Component.literal(color.tooltip())));
            this.addRenderableWidget(button);
        }
    }

    private int addFormattingButton(int x, int y, Component text, String token, String tooltipText) {
        Button button = Button.builder(text, (btn) -> applyFormattingToken(token))
            .bounds(x, y, TOOLBAR_BUTTON_WIDTH, 16)
            .build();
        button.setTooltip(Tooltip.create(Component.literal(tooltipText)));
        this.addRenderableWidget(button);
        return x + TOOLBAR_BUTTON_WIDTH + TOOLBAR_GAP;
    }

    private void applyFormattingToken(String token) {
        EditBox target = getFocusedDisplayTextField();
        if (target != null) {
            if (target instanceof FormattingPreviewEditBox preview) {
                preview.applyFormattingToken(token);
                if (preview == this.displayNameBox) {
                    this.displayForm.setCustomName(preview.getLegacyValue());
                } else if (preview == this.loreLineBox && this.selectedLoreLine >= 0 && this.selectedLoreLine < this.displayForm.getLoreLines().size()) {
                    this.displayForm.setLoreLine(this.selectedLoreLine, preview.getLegacyValue());
                }
                return;
            }
            target.insertText(token);
        }
    }

    private FormattingPreviewEditBox getFocusedDisplayTextField() {
        if (this.displayNameBox != null && this.displayNameBox.isFocused()) {
            return this.displayNameBox;
        }
        if (this.loreLineBox != null && this.loreLineBox.isFocused()) {
            return this.loreLineBox;
        }
        return this.loreLineBox != null ? this.loreLineBox : this.displayNameBox;
    }

    private void applyDisplayChanges() {
        if (this.displayForm == null) {
            return;
        }
        // Commit staged name and staged lore (if a line is selected)
        if (this.stagedName != null) {
            this.displayForm.setCustomName(this.stagedName);
        } else if (this.displayNameBox != null) {
            this.displayForm.setCustomName(this.displayNameBox.getLegacyValue());
        }
        if (this.stagedLoreText != null && this.selectedLoreLine >= 0 && this.selectedLoreLine < this.displayForm.getLoreLines().size()) {
            this.displayForm.setLoreLine(this.selectedLoreLine, this.stagedLoreText);
        }
        this.displayForm.applyTo(this.currentTag);
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

    private static String legacyToMinecraftFormatting(String source) {
        if (source == null || source.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if ((c == '&' || c == net.minecraft.ChatFormatting.PREFIX_CODE) && i + 1 < source.length()) {
                // Handle short hex '&#rrggbb'
                if (source.charAt(i + 1) == '#' && i + 7 < source.length()) {
                    String hex = source.substring(i + 2, i + 8);
                    if (hex.matches("(?i)^[0-9a-f]{6}$")) {
                        // expand to §x§R§R§G§G§B§B
                        out.append(net.minecraft.ChatFormatting.PREFIX_CODE).append('x');
                        for (int k = 0; k < 6; k++) {
                            out.append(net.minecraft.ChatFormatting.PREFIX_CODE).append(hex.charAt(k));
                        }
                        i += 8; // consumed & # and 6 hex
                        continue;
                    }
                }
                // Default: convert '&' to '§' and keep next char
                out.append(net.minecraft.ChatFormatting.PREFIX_CODE);
                out.append(source.charAt(i + 1));
                i += 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
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

    public void addEnchantment(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return;
        }

        String normalized = rawId.contains(":") ? rawId : "minecraft:" + rawId;
        ResourceLocation enchantmentId = ResourceLocation.tryParse(normalized);
        if (enchantmentId == null) {
            return;
        }

        Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(enchantmentId);
        if (enchantment == null) {
            return;
        }

        String tagName = getEnchantmentTagName();
        ListTag list = this.currentTag.getList(tagName, Tag.TAG_COMPOUND);
        String idText = enchantmentId.toString();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag existing = list.getCompound(i);
            if (idText.equals(existing.getString("id"))) {
                if (!existing.contains("lvl", Tag.TAG_ANY_NUMERIC)) {
                    existing.putShort("lvl", (short) 1);
                    list.set(i, existing);
                    this.currentTag.put(tagName, list);
                }
                return;
            }
        }

        CompoundTag entry = new CompoundTag();
        entry.putString("id", idText);
        entry.putShort("lvl", (short) 1);
        list.add(entry);
        this.currentTag.put(tagName, list);
    }

    private String getEnchantmentTagName() {
        return this.editingEnchantedBook ? "StoredEnchantments" : "Enchantments";
    }

    private List<EnchantmentRow> buildEnchantmentRows() {
        List<EnchantmentRow> rows = new ArrayList<>();
        String tagName = getEnchantmentTagName();
        if (!this.currentTag.contains(tagName, Tag.TAG_LIST)) {
            return rows;
        }

        ListTag list = this.currentTag.getList(tagName, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            rows.add(new EnchantmentRow(list.getCompound(i), tagName, i));
        }
        return rows;
    }

    private void initEnchantmentList(int contentStartX, int contentWidth) {
        this.enchantmentRows = buildEnchantmentRows();
        EnchantmentTableLayout layout = computeEnchantmentTableLayout(contentStartX, contentWidth);

        int total = this.enchantmentRows.size();
        int firstRowY = LIST_START_Y + layout.headerHeight;
        int visibleSlots = computeVisibleRows(firstRowY);
        this.enchantmentScrollOffset = clampScrollOffset(this.enchantmentScrollOffset, total, visibleSlots);
        int visible = Math.min(visibleSlots, Math.max(0, total - this.enchantmentScrollOffset));

        for (int i = 0; i < visible; i++) {
            final EnchantmentRow row = this.enchantmentRows.get(this.enchantmentScrollOffset + i);
            int rowY = LIST_START_Y + layout.headerHeight + (i * ROW_HEIGHT);

            SelectableEditBox levelBox = new SelectableEditBox(this.font, layout.levelInputX, rowY + 2, layout.levelInputWidth, 18, Component.literal("Lvl"));
            levelBox.setMaxLength(5);
            levelBox.setFilter(value -> value.isEmpty() || value.matches("\\d{1,5}"));
            levelBox.setValue(Short.toString((short) row.getLevel()));
            levelBox.setResponder(value -> updateEnchantmentLevel(row, value));
            this.addRenderableWidget(levelBox);

            this.addRenderableWidget(createDeleteActionButton(layout.deleteButtonX, rowY + 2, 18, 16, btn -> {
                ListTag list = this.currentTag.getList(row.getTagName(), Tag.TAG_COMPOUND);
                if (row.getIndex() >= 0 && row.getIndex() < list.size()) {
                    list.remove(row.getIndex());
                    persistList(row.getTagName(), list);
                    this.enchantmentRows = buildEnchantmentRows();
                    this.enchantmentScrollOffset = clampScrollOffset(this.enchantmentScrollOffset, this.enchantmentRows.size(), visibleSlots);
                    this.init();
                }
            }, "Deletar"));
        }
    }

    private void updateEnchantmentLevel(EnchantmentRow row, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (Exception ignored) {
            return;
        }

        int level = Mth.clamp(parsed, 1, 32767);
        ListTag list = this.currentTag.getList(row.getTagName(), Tag.TAG_COMPOUND);
        if (row.getIndex() < 0 || row.getIndex() >= list.size()) {
            return;
        }

        CompoundTag entry = list.getCompound(row.getIndex());
        entry.putShort("lvl", (short) level);
        list.set(row.getIndex(), entry);
        this.currentTag.put(row.getTagName(), list);
    }

    private EnchantmentTableLayout computeEnchantmentTableLayout(int contentStartX, int contentWidth) {
        int listX = contentStartX + 8;
        int tableWidth = Math.max(contentWidth - 16, 320);
        int headerHeight = 20;
        int actionsX = listX + tableWidth - 26 - SCROLLBAR_GUTTER;
        int deleteButtonX = actionsX + 2;
        int levelInputWidth = 56;
        int levelInputX = actionsX - levelInputWidth - 8;
        int nameX = listX + 8;
        return new EnchantmentTableLayout(listX, tableWidth, headerHeight, nameX, levelInputX, levelInputWidth, actionsX, deleteButtonX);
    }

    private void renderEnchantmentTable(GuiGraphics graphics, int contentStartX, int contentWidth) {
        if (this.enchantmentRows.isEmpty()) {
            this.enchantmentRows = buildEnchantmentRows();
        }

        EnchantmentTableLayout layout = computeEnchantmentTableLayout(contentStartX, contentWidth);
        int total = this.enchantmentRows.size();
        int firstRowY = LIST_START_Y + layout.headerHeight;
        int visibleSlots = computeVisibleRows(firstRowY);
        this.enchantmentScrollOffset = clampScrollOffset(this.enchantmentScrollOffset, total, visibleSlots);
        int rows = Math.min(visibleSlots, Math.max(0, total - this.enchantmentScrollOffset));
        int panelRows = Math.max(rows, 2);
        int panelHeight = Math.min(panelRows * ROW_HEIGHT + 46, this.height - LIST_START_Y - 40);

        GuiLayoutUtils.drawPanel(graphics, layout.listX - 6, LIST_START_Y - 10, layout.tableWidth + 12, panelHeight + 16, 0xCC1C2532, 0xFF4E6B8D);
        graphics.fill(layout.listX, LIST_START_Y - 8, layout.listX + layout.tableWidth, LIST_START_Y + layout.headerHeight - 8, 0x33FFFFFF);

        int headerY = LIST_START_Y - 6;
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Encantamento", layout.nameX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Nível", layout.levelInputX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Ações", layout.actionsX + 3, headerY + 4, 0xFFF1F1F1);

        if (total == 0) {
            graphics.drawString(this.font, "§7Nenhum encantamento registrado", layout.listX + 12, headerY + layout.headerHeight + 4, 0xAAAACCFF);
            return;
        }

        int clipTop = firstRowY - 2;
        int clipBottom = clipTop + (rows * ROW_HEIGHT);
        graphics.enableScissor(layout.listX, clipTop, layout.listX + layout.tableWidth, clipBottom);

        for (int i = 0; i < rows; i++) {
            EnchantmentRow row = this.enchantmentRows.get(this.enchantmentScrollOffset + i);
            int rowY = LIST_START_Y + layout.headerHeight + (i * ROW_HEIGHT);
            if (i % 2 == 0) {
                graphics.fill(layout.listX, rowY - 2, layout.listX + layout.tableWidth, rowY + ROW_HEIGHT - 4, 0x17FFFFFF);
            }

            String displayName = row.getDisplayName();
            if (displayName.length() > 40) {
                displayName = displayName.substring(0, 40) + "...";
            }
            graphics.drawString(this.font, displayName, layout.nameX, rowY + 4, 0xDDFFFFFF);
        }
        graphics.disableScissor();

        if (total > visibleSlots) {
            drawVanillaLikeScrollbar(graphics, layout.listX + layout.tableWidth - SCROLLBAR_WIDTH, firstRowY, rows, total, this.enchantmentScrollOffset);
        }
    }

    private void initHideFlagsList(int contentStartX, int contentWidth) {
        int listWidth = Math.min(460, Math.max(320, contentWidth - 70));
        int startX = contentStartX + (contentWidth - listWidth) / 2;
        int startY = 74;
        int buttonsTopMargin = 12; // extra gap between title and first button
        int buttonsStartY = startY + buttonsTopMargin;
        int rowHeight = 22;
        int colGap = 8;
        int buttonWidth = (listWidth - colGap) / 2;
        int rows = (HideFlagOption.ORDERED.length + 1) / 2;

        for (int i = 0; i < HideFlagOption.ORDERED.length; i++) {
            HideFlagOption option = HideFlagOption.ORDERED[i];
            int col = i % 2;
            int row = i / 2;
            int x = startX + col * (buttonWidth + colGap);
            int y = buttonsStartY + row * rowHeight;
            boolean enabled = isHideFlagEnabled(option);

            String marker = enabled ? "[ X ] " : "[   ] ";
            String color = enabled ? "§f" : "§7";
            Component label = Component.literal(color + marker + option.label());

            this.addRenderableWidget(Button.builder(label, btn -> {
                toggleHideFlag(option);
                this.init();
            }).bounds(x, y, buttonWidth, 18).build());
        }
    }

    private void renderHideFlagsPanel(GuiGraphics graphics, int contentStartX, int contentWidth) {
        int listWidth = Math.min(460, Math.max(320, contentWidth - 70));
        int startX = contentStartX + (contentWidth - listWidth) / 2;
        int startY = 74;
        int buttonsTopMargin = 12; // gap between title and first row of buttons
        int buttonsStartY = startY + buttonsTopMargin;
        int rowHeight = 22;
        int rows = (HideFlagOption.ORDERED.length + 1) / 2;
        int panelHeight = buttonsTopMargin + rows * rowHeight + 22;

        GuiLayoutUtils.drawPanel(graphics, startX - 8, startY - 14, listWidth + 16, panelHeight, 0xCC1C2532, 0xFF4E6B8D);
        int vanillaMask = getCurrentHideFlags(HIDE_FLAGS_TAG);
        int effectsMask = getCurrentHideFlags(SF_HIDE_EFFECT_FLAGS_TAG);
        graphics.drawCenteredString(this.font, "Vanilla: " + vanillaMask + " | Efeitos: " + effectsMask, contentStartX + (contentWidth / 2), startY - 6, 0xFFCCD9F1);
    }

    private int getCurrentHideFlags(String tagName) {
        return this.currentTag.contains(tagName, Tag.TAG_ANY_NUMERIC) ? this.currentTag.getInt(tagName) : 0;
    }

    private boolean isHideFlagEnabled(HideFlagOption option) {
        int hideFlags = getCurrentHideFlags(option.tagName());
        return (hideFlags & option.value()) != 0;
    }

    private void toggleHideFlag(HideFlagOption option) {
        int hideFlags = getCurrentHideFlags(option.tagName());
        if ((hideFlags & option.value()) != 0) {
            hideFlags &= ~option.value();
        } else {
            hideFlags |= option.value();
        }

        if (hideFlags == 0) {
            this.currentTag.remove(option.tagName());
        } else {
            this.currentTag.putInt(option.tagName(), hideFlags);
        }
    }

    private String getTagName() {
        switch (currentCategory) {
            case POTIONS: return "CustomPotionEffects";
            case ON_USE: case ON_HIT: case ON_HURT: case ON_EQUIP: return "SF_ItemEffects";
            case ENCHANTMENTS: return getEnchantmentTagName();
            case HIDE_FLAGS: return HIDE_FLAGS_TAG;
            default: return null;
        }
    }

    private boolean shouldUseVanillaOnUseStorage(Category viewCat) {
        if (viewCat != Category.ON_USE) return false;
        // Potion-like items are represented by Potion/CustomPotionEffects NBT.
        return this.currentTag.contains("Potion", Tag.TAG_STRING) || this.currentTag.contains("CustomPotionEffects", Tag.TAG_LIST);
    }

    private void canonicalizeOnUsePotionStorage() {
        if (!shouldUseVanillaOnUseStorage(effectiveCategory()) && !this.currentTag.contains("Potion", Tag.TAG_STRING)) {
            return;
        }

        ListTag custom = this.currentTag.contains("CustomPotionEffects", Tag.TAG_LIST)
            ? this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND).copy()
            : new ListTag();

        Potion basePotion = Potions.WATER;
        if (this.currentTag.contains("Potion", Tag.TAG_STRING)) {
            ResourceLocation potionId = ResourceLocation.tryParse(this.currentTag.getString("Potion"));
            if (potionId != null) {
                Potion resolved = ForgeRegistries.POTIONS.getValue(potionId);
                if (resolved != null) {
                    basePotion = resolved;
                }
            }
        }

        if (basePotion != Potions.WATER && basePotion != Potions.EMPTY) {
            for (MobEffectInstance inst : basePotion.getEffects()) {
                CompoundTag vanillaEntry = new CompoundTag();
                ResourceLocation effectKey = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
                if (effectKey != null) vanillaEntry.putString("IdString", effectKey.toString());
                else vanillaEntry.putInt("Id", MobEffect.getId(inst.getEffect()));
                vanillaEntry.putInt("Duration", inst.getDuration());
                vanillaEntry.putInt("Amplifier", inst.getAmplifier());
                vanillaEntry.putBoolean("Ambient", inst.isAmbient());
                vanillaEntry.putBoolean("ShowParticles", inst.isVisible());
                vanillaEntry.putBoolean("ShowIcon", inst.showIcon());
                custom.add(vanillaEntry);
            }
        }

        // Once ON_USE is edited as custom effects, base potion must be neutral to avoid hidden fallback effects.
        this.currentTag.putString("Potion", "minecraft:water");

        if (custom.isEmpty()) {
            this.currentTag.remove("CustomPotionEffects");
        } else {
            this.currentTag.put("CustomPotionEffects", custom);
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
        if (currentCategory == Category.DISPLAY) {
            renderDisplayForm(graphics, contentStartX, contentWidth);
            return;
        }

        if (currentCategory == Category.ATTRIBUTES) {
            renderAttributeTable(graphics, contentStartX, contentWidth);
            return;
        }

        if (currentCategory == Category.ENCHANTMENTS) {
            renderEnchantmentTable(graphics, contentStartX, contentWidth);
            return;
        }

        if (currentCategory == Category.HIDE_FLAGS) {
            renderHideFlagsPanel(graphics, contentStartX, contentWidth);
            return;
        }

        String tagName = getTagName();
        if (tagName == null) return;

        Category viewCat = effectiveCategory();
        boolean useVanillaOnUse = shouldUseVanillaOnUseStorage(viewCat);
        ListTag list;
        if (useVanillaOnUse) {
            canonicalizeOnUsePotionStorage();
            list = this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
        } else if (viewCat == Category.ON_HIT || viewCat == Category.ON_HURT || viewCat == Category.ON_USE || viewCat == Category.ON_EQUIP) {
            CompoundTag main = this.currentTag.getCompound("SF_ItemEffects");
            if (viewCat == Category.ON_HIT) list = main.getList("on_hit", Tag.TAG_COMPOUND);
            else if (viewCat == Category.ON_HURT) list = main.getList("on_hurt", Tag.TAG_COMPOUND);
            else if (viewCat == Category.ON_EQUIP) list = main.getList("on_equip", Tag.TAG_COMPOUND);
            else list = main.getList("on_use", Tag.TAG_COMPOUND);
        } else {
            list = this.currentTag.getList(tagName, Tag.TAG_COMPOUND);
        }

        renderEffectTable(graphics, list, contentStartX, contentWidth);
    }

    private void renderEffectTable(GuiGraphics graphics, ListTag list, int contentStartX, int contentWidth) {
        EffectTableLayout layout = computeEffectTableLayout(contentStartX, contentWidth);

        // Determine which logical category we're showing (might be a sub-tab when 'Effects' sidebar is active)
        Category viewCat = effectiveCategory();
        ListTag displayList = (list != null) ? list : new ListTag();

        int total = displayList.size();
        int firstRowY = LIST_START_Y + layout.headerHeight;
        int visibleSlots = computeVisibleRows(firstRowY);
        this.effectScrollOffset = clampScrollOffset(this.effectScrollOffset, total, visibleSlots);
        int rows = Math.min(visibleSlots, Math.max(0, total - this.effectScrollOffset));
        int panelRows = Math.max(rows, 2);
        int panelHeight = Math.min(panelRows * ROW_HEIGHT + 46, this.height - LIST_START_Y - 40);

        GuiLayoutUtils.drawPanel(graphics, layout.listX - 6, LIST_START_Y - 10, layout.tableWidth + 12, panelHeight + 16, 0xCC1C2532, 0xFF4E6B8D);
        graphics.fill(layout.listX, LIST_START_Y - 8, layout.listX + layout.tableWidth, LIST_START_Y + layout.headerHeight - 8, 0x33FFFFFF);

        int headerY = LIST_START_Y - 6;
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Efeito", layout.effectX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Duração", layout.durationX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Nível", layout.amplifierX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Extras", layout.extrasX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Ações", layout.actionsX + 3, headerY + 4, 0xFFF1F1F1);

        if (total == 0) {
            graphics.drawString(this.font, "§7Nenhum efeito registrado", layout.listX + 12, headerY + layout.headerHeight + 4, 0xAAAACCFF);
            return;
        }

        int clipTop = firstRowY - 2;
        int clipBottom = clipTop + (rows * ROW_HEIGHT);
        graphics.enableScissor(layout.listX, clipTop, layout.listX + layout.tableWidth, clipBottom);

        for (int i = 0; i < rows; i++) {
            int index = this.effectScrollOffset + i;
            CompoundTag entry = displayList.getCompound(index);
            int rowY = LIST_START_Y + layout.headerHeight + (i * ROW_HEIGHT);
            if (i % 2 == 0) {
                graphics.fill(layout.listX, rowY - 2, layout.listX + layout.tableWidth, rowY + ROW_HEIGHT - 4, 0x17FFFFFF);
            }

            // Resolve effect id (supports both potion-style entries and SF_ItemEffects entries)
            String effectId;
            if (entry.contains("IdString", Tag.TAG_STRING) || entry.contains("Id", Tag.TAG_ANY_NUMERIC)) {
                effectId = resolvePotionEffectId(entry);
            } else if (entry.contains("id", Tag.TAG_STRING)) {
                effectId = entry.getString("id");
            } else {
                effectId = "unknown";
            }

            ResourceLocation rl = ResourceLocation.tryParse(effectId);
            MobEffect mob = null;
            if (rl != null) mob = ForgeRegistries.MOB_EFFECTS.getValue(rl);
            if (mob == null && entry.contains("Id", Tag.TAG_ANY_NUMERIC)) mob = MobEffect.byId(entry.getInt("Id"));

            int iconColor = 0xFF777777;
            if (mob != null) {
                try {
                    iconColor = 0xFF000000 | (mob.getColor() & 0xFFFFFF);
                } catch (Exception ignored) {}
            }

            // Draw the effect sprite when available, otherwise a colored square
            int ix = layout.iconX;
            int iy = rowY + (ROW_HEIGHT - EFFECT_ICON_SIZE) / 2;
            TextureAtlasSprite sprite = null;
            if (mob != null) {
                try {
                    sprite = Minecraft.getInstance().getMobEffectTextures().get(mob);
                } catch (Exception ignored) {}
            }
            if (sprite != null) {
                graphics.blit(ix, iy, 0, EFFECT_ICON_SIZE, EFFECT_ICON_SIZE, sprite);
            } else {
                graphics.fill(ix, iy, ix + EFFECT_ICON_SIZE, iy + EFFECT_ICON_SIZE, iconColor);
                int border = 0xFF000000;
                graphics.fill(ix, iy, ix + EFFECT_ICON_SIZE, iy + 1, border);
                graphics.fill(ix, iy + EFFECT_ICON_SIZE - 1, ix + EFFECT_ICON_SIZE, iy + EFFECT_ICON_SIZE, border);
                graphics.fill(ix, iy, ix + 1, iy + EFFECT_ICON_SIZE, border);
                graphics.fill(ix + EFFECT_ICON_SIZE - 1, iy, ix + EFFECT_ICON_SIZE, iy + EFFECT_ICON_SIZE, border);
            }

            String shortEffect = effectId.replace("minecraft:", "");
            if (shortEffect.length() > 22) {
                shortEffect = shortEffect.substring(0, 22) + "...";
            }

            int durationTicks = entry.contains("Duration", Tag.TAG_ANY_NUMERIC) ? entry.getInt("Duration") : entry.contains("duration", Tag.TAG_ANY_NUMERIC) ? entry.getInt("duration") : 0;
            int rawAmp = entry.contains("Amplifier", Tag.TAG_ANY_NUMERIC) ? entry.getInt("Amplifier") : (entry.contains("amplifier", Tag.TAG_ANY_NUMERIC) ? entry.getInt("amplifier") : 0);
            int amplifier = rawAmp + 1;
            String extras =
                (viewCat == Category.ON_USE) ? formatOnUseExtras(entry)
                : (viewCat == Category.ON_EQUIP) ? formatOnEquipExtras(entry)
                : formatTriggerExtras(entry);

            graphics.drawString(this.font, shortEffect, layout.effectX, rowY + 4, 0xDDFFFFFF);
            graphics.drawString(this.font, formatEffectDuration(durationTicks), layout.durationX, rowY + 4, 0xFF90EEFF);
            graphics.drawString(this.font, "Lvl " + amplifier, layout.amplifierX, rowY + 4, 0xFFB2CEFF);
            graphics.drawString(this.font, extras, layout.extrasX, rowY + 4, 0xFFC8D7EA);
        }
        graphics.disableScissor();

        if (total > visibleSlots) {
            drawVanillaLikeScrollbar(graphics, layout.listX + layout.tableWidth - SCROLLBAR_WIDTH, firstRowY, rows, total, this.effectScrollOffset);
        }
    }

    private static String formatEffectDuration(int ticks) {
        if (ticks <= 0) {
            return "0s";
        }
        if (ticks % 20 == 0) {
            return (ticks / 20) + "s";
        }
        return String.format(Locale.US, "%.2fs", ticks / 20.0D);
    }

    private static String formatPotionExtras(CompoundTag entry) {
        boolean ambient = entry.contains("Ambient", Tag.TAG_BYTE) && entry.getBoolean("Ambient");
        boolean particles = !entry.contains("ShowParticles", Tag.TAG_BYTE) || entry.getBoolean("ShowParticles");
        boolean icon = !entry.contains("ShowIcon", Tag.TAG_BYTE) || entry.getBoolean("ShowIcon");
        return "A:" + (ambient ? "Y" : "N") + " P:" + (particles ? "Y" : "N") + " I:" + (icon ? "Y" : "N");
    }

    private static String formatTriggerExtras(CompoundTag entry) {
        double chance = entry.contains("chance", Tag.TAG_DOUBLE) ? entry.getDouble("chance") : 1.0D;
        String target = entry.contains("self", Tag.TAG_BYTE) && entry.getBoolean("self") ? "SELF" : "TARGET";
        return String.format(Locale.US, "C:%.2f %s", chance, target);
    }

    private static String formatOnUseExtras(CompoundTag entry) {
        return formatPotionExtras(entry);
    }

    private static String formatOnEquipExtras(CompoundTag entry) {
        String slot = entry.contains("Slot", Tag.TAG_STRING) ? entry.getString("Slot") : "any";
        boolean particles = !entry.contains("ShowParticles", Tag.TAG_BYTE) || entry.getBoolean("ShowParticles");
        boolean icon = !entry.contains("ShowIcon", Tag.TAG_BYTE) || entry.getBoolean("ShowIcon");
        return "S:" + slot + " P:" + (particles ? "Y" : "N") + " I:" + (icon ? "Y" : "N");
    }

    private void renderDisplayForm(GuiGraphics graphics, int contentStartX, int contentWidth) {
        DisplayLayout layout = computeDisplayLayout(contentStartX, contentWidth);

        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Nome customizado", layout.fieldX, layout.nameLabelY, 0xFFE0E8F5);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Lore (selecione uma linha e use a toolbar)", layout.fieldX, layout.loreLabelY, 0xFFE0E8F5);

        int total = this.displayForm != null ? this.displayForm.getLoreLines().size() : 0;
        int visibleSlots = computeVisibleRows(layout.loreRowsStartY);
        this.loreScrollOffset = clampScrollOffset(this.loreScrollOffset, total, visibleSlots);
        int rows = this.displayForm != null ? Math.min(visibleSlots, Math.max(0, total - this.loreScrollOffset)) : 0;
        int panelRows = Math.max(rows, 2);
        int panelHeight = Math.min(panelRows * ROW_HEIGHT + 20, this.height - layout.loreRowsStartY - 38);
        GuiLayoutUtils.drawPanel(graphics, layout.fieldX - 6, layout.loreRowsStartY - 10, layout.fieldWidth + 12, panelHeight + 16, 0xCC1C2532, 0xFF4E6B8D);

        if (this.displayForm == null || rows == 0) {
            graphics.drawString(this.font, "§7Nenhuma linha de lore. Use '+' para criar.", layout.fieldX + 8, layout.loreRowsStartY + 6, 0xAAAACCFF);
            return;
        }

        int clipTop = layout.loreRowsStartY - 2;
        int clipBottom = clipTop + (rows * ROW_HEIGHT);
        graphics.enableScissor(layout.fieldX, clipTop, layout.fieldX + layout.fieldWidth, clipBottom);

        for (int i = 0; i < rows; i++) {
            int index = this.loreScrollOffset + i;
            int rowY = layout.loreRowsStartY + (i * ROW_HEIGHT);
            if (i % 2 == 0) {
                graphics.fill(layout.fieldX, rowY - 2, layout.fieldX + layout.fieldWidth - 4, rowY + ROW_HEIGHT - 4, 0x17FFFFFF);
            }

            String text = this.displayForm.getLoreLines().get(index);
            String converted = legacyToMinecraftFormatting(text);
            String line = converted.length() > 52 ? converted.substring(0, 52) + "..." : converted;
            int color = index == this.selectedLoreLine ? 0xFFFFE08A : 0xFFBECBDD;
            graphics.drawString(this.font, (index + 1) + ". " + line, layout.fieldX + 8, rowY + 4, color);
        }
        graphics.disableScissor();

        if (total > visibleSlots) {
            drawVanillaLikeScrollbar(graphics, layout.fieldX + layout.fieldWidth - SCROLLBAR_WIDTH, layout.loreRowsStartY, rows, total, this.loreScrollOffset);
        }
    }

    private DisplayLayout computeDisplayLayout(int contentStartX, int contentWidth) {
        int fieldX = contentStartX + 10;
        int fieldWidth = Math.max(220, contentWidth - 20);

        int toolbarX = fieldX;
        int toolbarY = DISPLAY_TOP_Y;
        int paletteX = toolbarX + (TOOLBAR_BUTTON_WIDTH + TOOLBAR_GAP) * 7 + 8;
        int hexInputX = paletteX + (COLOR_BUTTON_SIZE * 8) + 8;
        int toolbarHeight = Math.max(16, COLOR_BUTTON_SIZE * 2 + COLOR_GRID_GAP);

        int nameLabelY = toolbarY + toolbarHeight + DISPLAY_SECTION_GAP;
        int nameInputY = nameLabelY + DISPLAY_LABEL_INPUT_GAP + 10;

        int loreLabelY = nameInputY + 20 + DISPLAY_SECTION_GAP;
        int loreInputY = loreLabelY + DISPLAY_LABEL_INPUT_GAP + 10;

        int loreAddButtonX = fieldX + fieldWidth - 22;
        int loreInputWidth = Math.max(120, loreAddButtonX - fieldX - 4);

        int loreRowsStartY = loreInputY + 24 + DISPLAY_SECTION_GAP;
        int rowDeleteButtonX = fieldX + fieldWidth - (22 + SCROLLBAR_GUTTER);

        return new DisplayLayout(fieldX, fieldWidth, toolbarX, toolbarY, hexInputX,
            nameLabelY, nameInputY,
            loreLabelY, loreInputY, loreInputWidth, loreAddButtonX,
            loreRowsStartY, rowDeleteButtonX);
    }

    private record ColorToken(String token, String tooltip, int rgb) {
    }

    private record DisplayLayout(int fieldX, int fieldWidth,
                                 int toolbarX, int toolbarY, int hexInputX,
                                 int nameLabelY, int nameInputY,
                                 int loreLabelY, int loreInputY, int loreInputWidth, int loreAddButtonX,
                                 int loreRowsStartY, int rowDeleteButtonX) {
    }

    private record AttributeTableLayout(int listX, int tableWidth, int headerHeight,
                                        int nameX, int valueX, int slotX,
                                        int actionsX, int editButtonX, int deleteButtonX) {
    }

    private record EnchantmentTableLayout(int listX, int tableWidth, int headerHeight,
                                          int nameX, int levelInputX, int levelInputWidth,
                                          int actionsX, int deleteButtonX) {
    }

    private record EffectTableLayout(int listX, int tableWidth, int headerHeight,
                                     int iconX, int effectX, int durationX, int amplifierX, int extrasX,
                                     int actionsX, int editButtonX, int deleteButtonX) {
    }

    private enum ScrollTarget {
        NONE,
        LORE,
        ATTRIBUTES,
        ENCHANTMENTS,
        EFFECTS
    }

    private record ScrollbarGeometry(int x, int y, int width, int height, int thumbY, int thumbHeight, int maxOffset) {
        int thumbBottom() {
            return this.thumbY + this.thumbHeight;
        }
    }

    private Button createEditActionButton(int x, int y, int width, int height, Button.OnPress onPress, String tooltip) {
        Button button = new IconButton(x, y, width, height, EDIT_ICON, 16, 16,
            0xFF8B6D26, 0xFFA68433, 0xFFD7BC6E, onPress);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return button;
    }

    private Button createDeleteActionButton(int x, int y, int width, int height, Button.OnPress onPress, String tooltip) {
        Button button = new IconButton(x, y, width, height, DELETE_ICON, 16, 16,
            0xFF7A2424, 0xFF9B3232, 0xFFCF7D7D, onPress);
        button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return button;
    }

    private static class ColorSwatchButton extends Button {
        private final int color;

        protected ColorSwatchButton(int x, int y, int width, int height, int color, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, Button.DEFAULT_NARRATION);
            this.color = color;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int left = getX();
            int top = getY();
            int right = left + getWidth();
            int bottom = top + getHeight();
            graphics.fill(left, top, right, bottom, this.color);

            int border = this.isHoveredOrFocused() ? 0xFFFFFFFF : 0x66000000;
            graphics.fill(left, top, right, top + 1, border);
            graphics.fill(left, bottom - 1, right, bottom, border);
            graphics.fill(left, top, left + 1, bottom, border);
            graphics.fill(right - 1, top, right, bottom, border);
        }
    }

    private void renderAttributeTable(GuiGraphics graphics, int contentStartX, int contentWidth) {
        if (this.attributeRows.isEmpty()) {
            this.attributeRows = buildAttributeRows();
        }

        AttributeTableLayout layout = computeAttributeTableLayout(contentStartX, contentWidth);
        int listX = layout.listX;
        int tableWidth = layout.tableWidth;
        int total = this.attributeRows.size();
        int firstRowY = LIST_START_Y + layout.headerHeight;
        int visibleSlots = computeVisibleRows(firstRowY);
        this.attributeScrollOffset = clampScrollOffset(this.attributeScrollOffset, total, visibleSlots);
        int rows = Math.min(visibleSlots, Math.max(0, total - this.attributeScrollOffset));
        int panelRows = Math.max(rows, 2);
        int panelHeight = Math.min(panelRows * ROW_HEIGHT + 46, this.height - LIST_START_Y - 40);
        GuiLayoutUtils.drawPanel(graphics, listX - 6, LIST_START_Y - 10, tableWidth + 12, panelHeight + 16, 0xCC1C2532, 0xFF4E6B8D);

        int headerHeight = layout.headerHeight;
        graphics.fill(listX, LIST_START_Y - 8, listX + tableWidth, LIST_START_Y + headerHeight - 8, 0x33FFFFFF);

        int nameX = layout.nameX;
        int valueX = layout.valueX;
        int slotX = layout.slotX;
        int actionsHeaderX = layout.actionsX + 3;
        int headerY = LIST_START_Y - 6;

        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Atributo", nameX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Valor", valueX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Slot", slotX, headerY + 4, 0xFFF1F1F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Ações", actionsHeaderX, headerY + 4, 0xFFF1F1F1);

        if (total == 0) {
            graphics.drawString(this.font, "§7Nenhum atributo registrado", listX + 12, headerY + headerHeight + 4, 0xAAAACCFF);
            return;
        }

        int clipTop = firstRowY - 2;
        int clipBottom = clipTop + (rows * ROW_HEIGHT);
        graphics.enableScissor(listX, clipTop, listX + tableWidth, clipBottom);

        for (int i = 0; i < rows; i++) {
            AttributeRow row = this.attributeRows.get(this.attributeScrollOffset + i);
            int rowY = LIST_START_Y + headerHeight + (i * ROW_HEIGHT);
            if (i % 2 == 0) {
                graphics.fill(listX, rowY - 2, listX + tableWidth, rowY + ROW_HEIGHT - 4, 0x17FFFFFF);
            }
            graphics.drawString(this.font, getAttributeDisplay(row), nameX, rowY + 4, 0xDDFFFFFF);
            graphics.drawString(this.font, formatAttributeValue(row), valueX, rowY + 4, 0xFF90EEFF);
            graphics.drawString(this.font, row.getSlot(), slotX, rowY + 4, 0xFFB2CEFF);
        }
        graphics.disableScissor();

        if (total > visibleSlots) {
            drawVanillaLikeScrollbar(graphics, listX + tableWidth - SCROLLBAR_WIDTH, firstRowY, rows, total, this.attributeScrollOffset);
        }
    }

    private AttributeTableLayout computeAttributeTableLayout(int contentStartX, int contentWidth) {
        int listX = contentStartX + 8;
        int tableWidth = Math.max(contentWidth - 16, 320);
        int headerHeight = 20;
        int actionsX = listX + tableWidth - 50 - SCROLLBAR_GUTTER;
        int editButtonX = actionsX + 2;
        int deleteButtonX = actionsX + 24;
        int slotX = actionsX - 58;
        int valueX = slotX - 90;
        int nameX = listX + 8;
        return new AttributeTableLayout(listX, tableWidth, headerHeight, nameX, valueX, slotX, actionsX, editButtonX, deleteButtonX);
    }

    private EffectTableLayout computeEffectTableLayout(int contentStartX, int contentWidth) {
        int listX = contentStartX + 8;
        int tableWidth = Math.max(contentWidth - 16, 320);
        int headerHeight = 20;
        int actionsX = listX + tableWidth - 50 - SCROLLBAR_GUTTER;
        int editButtonX = actionsX + 2;
        int deleteButtonX = actionsX + 24;
        int extrasX = actionsX - 108;
        int amplifierX = extrasX - 64;
        int durationX = amplifierX - 74;
        int iconX = listX + 8;
        int effectX = iconX + EFFECT_ICON_SIZE + EFFECT_ICON_GAP;
        return new EffectTableLayout(listX, tableWidth, headerHeight, iconX, effectX, durationX, amplifierX, extrasX, actionsX, editButtonX, deleteButtonX);
    }

    private int computeVisibleRows(int firstRowY) {
        int availableHeight = (this.height - LIST_BOTTOM_PADDING) - firstRowY;
        if (availableHeight <= 0) return 0;
        return Math.max(1, Math.min(MAX_VISIBLE_ROWS, availableHeight / ROW_HEIGHT));
    }

    private static int clampScrollOffset(int offset, int totalRows, int visibleRows) {
        return Mth.clamp(offset, 0, Math.max(0, totalRows - Math.max(1, visibleRows)));
    }

    private ScrollbarGeometry buildScrollbarGeometry(int barX, int firstRowY, int visibleRows, int totalRows, int offset) {
        if (visibleRows <= 0 || totalRows <= visibleRows) {
            return null;
        }
        int trackHeight = Math.max(8, visibleRows * ROW_HEIGHT);
        int thumbHeight = Mth.clamp((trackHeight * visibleRows) / totalRows, 20, trackHeight - 2);
        int maxOffset = Math.max(1, totalRows - visibleRows);
        int thumbY = firstRowY + (int) ((trackHeight - thumbHeight) * (offset / (double) maxOffset));
        return new ScrollbarGeometry(barX, firstRowY, SCROLLBAR_WIDTH, trackHeight, thumbY, thumbHeight, maxOffset);
    }

    private void drawVanillaLikeScrollbar(GuiGraphics graphics, int barX, int firstRowY, int visibleRows, int totalRows, int offset) {
        ScrollbarGeometry geometry = buildScrollbarGeometry(barX, firstRowY, visibleRows, totalRows, offset);
        if (geometry == null) return;

        int trackRight = geometry.x() + geometry.width();
        int trackBottom = geometry.y() + geometry.height();
        graphics.fill(geometry.x(), geometry.y(), trackRight, trackBottom, 0xFF101018);
        graphics.fill(geometry.x(), geometry.thumbY(), trackRight, geometry.thumbBottom(), 0xFF6B6B6B);
        graphics.fill(geometry.x(), geometry.thumbY(), trackRight - 1, geometry.thumbBottom() - 1, 0xFFA0A0A0);
    }

    private boolean inside(ScrollbarGeometry geometry, double mouseX, double mouseY) {
        return geometry != null
            && mouseX >= geometry.x() && mouseX <= geometry.x() + geometry.width()
            && mouseY >= geometry.y() && mouseY <= geometry.y() + geometry.height();
    }

    private boolean insideThumb(ScrollbarGeometry geometry, double mouseX, double mouseY) {
        return geometry != null
            && mouseX >= geometry.x() && mouseX <= geometry.x() + geometry.width()
            && mouseY >= geometry.thumbY() && mouseY <= geometry.thumbBottom();
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

    private ScrollbarGeometry getLoreScrollbarGeometry() {
        if (this.currentCategory != Category.DISPLAY || this.displayForm == null) return null;
        int contentStartX = SIDEBAR_WIDTH + CONTENT_PADDING;
        int contentWidth = this.width - contentStartX - CONTENT_PADDING;
        DisplayLayout layout = computeDisplayLayout(contentStartX, contentWidth);
        int total = this.displayForm.getLoreLines().size();
        int visible = computeVisibleRows(layout.loreRowsStartY);
        int barX = layout.fieldX + layout.fieldWidth - SCROLLBAR_WIDTH;
        return buildScrollbarGeometry(barX, layout.loreRowsStartY, visible, total, this.loreScrollOffset);
    }

    private ScrollbarGeometry getAttributeScrollbarGeometry() {
        if (this.currentCategory != Category.ATTRIBUTES) return null;
        int contentStartX = SIDEBAR_WIDTH + CONTENT_PADDING;
        int contentWidth = this.width - contentStartX - CONTENT_PADDING;
        AttributeTableLayout layout = computeAttributeTableLayout(contentStartX, contentWidth);
        int total = this.attributeRows.isEmpty() ? buildAttributeRows().size() : this.attributeRows.size();
        int firstRowY = LIST_START_Y + layout.headerHeight;
        int visible = computeVisibleRows(firstRowY);
        int barX = layout.listX + layout.tableWidth - SCROLLBAR_WIDTH;
        return buildScrollbarGeometry(barX, firstRowY, visible, total, this.attributeScrollOffset);
    }

    private ScrollbarGeometry getEnchantmentScrollbarGeometry() {
        if (this.currentCategory != Category.ENCHANTMENTS) return null;
        int contentStartX = SIDEBAR_WIDTH + CONTENT_PADDING;
        int contentWidth = this.width - contentStartX - CONTENT_PADDING;
        EnchantmentTableLayout layout = computeEnchantmentTableLayout(contentStartX, contentWidth);
        int total = this.enchantmentRows.isEmpty() ? buildEnchantmentRows().size() : this.enchantmentRows.size();
        int firstRowY = LIST_START_Y + layout.headerHeight;
        int visible = computeVisibleRows(firstRowY);
        int barX = layout.listX + layout.tableWidth - SCROLLBAR_WIDTH;
        return buildScrollbarGeometry(barX, firstRowY, visible, total, this.enchantmentScrollOffset);
    }

    private ScrollbarGeometry getEffectScrollbarGeometry() {
        if (!isEffectsCategory(this.currentCategory)) return null;
        int contentStartX = SIDEBAR_WIDTH + CONTENT_PADDING;
        int contentWidth = this.width - contentStartX - CONTENT_PADDING;
        EffectTableLayout layout = computeEffectTableLayout(contentStartX, contentWidth);
        Category viewCat = effectiveCategory();

        int total = 0;
        if (shouldUseVanillaOnUseStorage(viewCat)) {
            canonicalizeOnUsePotionStorage();
            total = this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND).size();
        } else if (viewCat == Category.ON_HIT || viewCat == Category.ON_HURT || viewCat == Category.ON_USE || viewCat == Category.ON_EQUIP) {
            CompoundTag main = this.currentTag.getCompound("SF_ItemEffects");
            if (viewCat == Category.ON_HIT) total = main.getList("on_hit", Tag.TAG_COMPOUND).size();
            else if (viewCat == Category.ON_HURT) total = main.getList("on_hurt", Tag.TAG_COMPOUND).size();
            else if (viewCat == Category.ON_EQUIP) total = main.getList("on_equip", Tag.TAG_COMPOUND).size();
            else total = main.getList("on_use", Tag.TAG_COMPOUND).size();
        } else {
            total = this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND).size();
        }

        int firstRowY = LIST_START_Y + layout.headerHeight;
        int visible = computeVisibleRows(firstRowY);
        int barX = layout.listX + layout.tableWidth - SCROLLBAR_WIDTH;
        return buildScrollbarGeometry(barX, firstRowY, visible, total, this.effectScrollOffset);
    }

    private int offsetFromDrag(ScrollbarGeometry geometry, double mouseY, int thumbGrabOffsetY) {
        int thumbTrack = Math.max(1, geometry.height() - geometry.thumbHeight());
        int desiredThumbTop = Mth.clamp((int) mouseY - thumbGrabOffsetY, geometry.y(), geometry.y() + thumbTrack);
        double ratio = (desiredThumbTop - geometry.y()) / (double) thumbTrack;
        return Mth.clamp((int) Math.round(ratio * geometry.maxOffset()), 0, geometry.maxOffset());
    }

    private boolean beginOrJumpScroll(ScrollTarget target, ScrollbarGeometry geometry, double mouseY) {
        if (geometry == null) return false;
        if (insideThumb(geometry, geometry.x() + 1, mouseY)) {
            this.draggingScrollTarget = target;
            this.draggingThumbOffsetY = (int) mouseY - geometry.thumbY();
            return true;
        }
        int newOffset = offsetFromDrag(geometry, mouseY, geometry.thumbHeight() / 2);
        switch (target) {
            case LORE -> this.loreScrollOffset = newOffset;
            case ATTRIBUTES -> this.attributeScrollOffset = newOffset;
            case ENCHANTMENTS -> this.enchantmentScrollOffset = newOffset;
            case EFFECTS -> this.effectScrollOffset = newOffset;
            default -> {}
        }
        this.init();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ScrollbarGeometry lore = getLoreScrollbarGeometry();
            if (inside(lore, mouseX, mouseY)) {
                return beginOrJumpScroll(ScrollTarget.LORE, lore, mouseY);
            }
            ScrollbarGeometry attrs = getAttributeScrollbarGeometry();
            if (inside(attrs, mouseX, mouseY)) {
                return beginOrJumpScroll(ScrollTarget.ATTRIBUTES, attrs, mouseY);
            }
            ScrollbarGeometry enchantments = getEnchantmentScrollbarGeometry();
            if (inside(enchantments, mouseX, mouseY)) {
                return beginOrJumpScroll(ScrollTarget.ENCHANTMENTS, enchantments, mouseY);
            }
            ScrollbarGeometry effects = getEffectScrollbarGeometry();
            if (inside(effects, mouseX, mouseY)) {
                return beginOrJumpScroll(ScrollTarget.EFFECTS, effects, mouseY);
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.draggingScrollTarget != ScrollTarget.NONE) {
            ScrollbarGeometry geometry = switch (this.draggingScrollTarget) {
                case LORE -> getLoreScrollbarGeometry();
                case ATTRIBUTES -> getAttributeScrollbarGeometry();
                case ENCHANTMENTS -> getEnchantmentScrollbarGeometry();
                case EFFECTS -> getEffectScrollbarGeometry();
                default -> null;
            };
            if (geometry != null) {
                int newOffset = offsetFromDrag(geometry, mouseY, this.draggingThumbOffsetY);
                switch (this.draggingScrollTarget) {
                    case LORE -> this.loreScrollOffset = newOffset;
                    case ATTRIBUTES -> this.attributeScrollOffset = newOffset;
                    case ENCHANTMENTS -> this.enchantmentScrollOffset = newOffset;
                    case EFFECTS -> this.effectScrollOffset = newOffset;
                    default -> {}
                }
                this.init();
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingScrollTarget = ScrollTarget.NONE;
            this.draggingThumbOffsetY = 0;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.currentCategory == Category.DISPLAY && this.displayForm != null) {
            DisplayLayout layout = computeDisplayLayout(SIDEBAR_WIDTH + CONTENT_PADDING, this.width - (SIDEBAR_WIDTH + CONTENT_PADDING) - CONTENT_PADDING);
            int total = this.displayForm.getLoreLines().size();
            int visible = computeVisibleRows(layout.loreRowsStartY);
            int listTop = layout.loreRowsStartY;
            int listBottom = listTop + visible * ROW_HEIGHT;
            if (total > visible && mouseX >= layout.fieldX && mouseX <= layout.fieldX + layout.fieldWidth && mouseY >= listTop && mouseY <= listBottom) {
                int step = delta > 0 ? -1 : 1;
                this.loreScrollOffset = clampScrollOffset(this.loreScrollOffset + step, total, visible);
                this.init();
                return true;
            }
        }

        if (this.currentCategory == Category.ATTRIBUTES) {
            int contentStartX = SIDEBAR_WIDTH + CONTENT_PADDING;
            int contentWidth = this.width - contentStartX - CONTENT_PADDING;
            AttributeTableLayout layout = computeAttributeTableLayout(contentStartX, contentWidth);
            int total = this.attributeRows.isEmpty() ? buildAttributeRows().size() : this.attributeRows.size();
            int visible = computeVisibleRows(LIST_START_Y + layout.headerHeight);
            int listTop = LIST_START_Y + layout.headerHeight;
            int listBottom = listTop + visible * ROW_HEIGHT;
            if (total > visible && mouseX >= layout.listX && mouseX <= layout.listX + layout.tableWidth && mouseY >= listTop && mouseY <= listBottom) {
                int step = delta > 0 ? -1 : 1;
                this.attributeScrollOffset = clampScrollOffset(this.attributeScrollOffset + step, total, visible);
                this.init();
                return true;
            }
        }

        if (this.currentCategory == Category.ENCHANTMENTS) {
            int contentStartX = SIDEBAR_WIDTH + CONTENT_PADDING;
            int contentWidth = this.width - contentStartX - CONTENT_PADDING;
            EnchantmentTableLayout layout = computeEnchantmentTableLayout(contentStartX, contentWidth);
            int total = this.enchantmentRows.isEmpty() ? buildEnchantmentRows().size() : this.enchantmentRows.size();
            int visible = computeVisibleRows(LIST_START_Y + layout.headerHeight);
            int listTop = LIST_START_Y + layout.headerHeight;
            int listBottom = listTop + visible * ROW_HEIGHT;
            if (total > visible && mouseX >= layout.listX && mouseX <= layout.listX + layout.tableWidth && mouseY >= listTop && mouseY <= listBottom) {
                int step = delta > 0 ? -1 : 1;
                this.enchantmentScrollOffset = clampScrollOffset(this.enchantmentScrollOffset + step, total, visible);
                this.init();
                return true;
            }
        }

        if (isEffectsCategory(this.currentCategory)) {
            int contentStartX = SIDEBAR_WIDTH + CONTENT_PADDING;
            int contentWidth = this.width - contentStartX - CONTENT_PADDING;
            EffectTableLayout layout = computeEffectTableLayout(contentStartX, contentWidth);
            Category viewCat = effectiveCategory();

            int total = 0;
            if (shouldUseVanillaOnUseStorage(viewCat)) {
                canonicalizeOnUsePotionStorage();
                total = this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND).size();
            } else if (viewCat == Category.ON_HIT || viewCat == Category.ON_HURT || viewCat == Category.ON_USE || viewCat == Category.ON_EQUIP) {
                CompoundTag main = this.currentTag.getCompound("SF_ItemEffects");
                if (viewCat == Category.ON_HIT) total = main.getList("on_hit", Tag.TAG_COMPOUND).size();
                else if (viewCat == Category.ON_HURT) total = main.getList("on_hurt", Tag.TAG_COMPOUND).size();
                else if (viewCat == Category.ON_EQUIP) total = main.getList("on_equip", Tag.TAG_COMPOUND).size();
                else total = main.getList("on_use", Tag.TAG_COMPOUND).size();
            } else {
                total = this.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND).size();
            }

            int visible = computeVisibleRows(LIST_START_Y + layout.headerHeight);
            int listTop = LIST_START_Y + layout.headerHeight;
            int listBottom = listTop + visible * ROW_HEIGHT;
            if (total > visible && mouseX >= layout.listX && mouseX <= layout.listX + layout.tableWidth && mouseY >= listTop && mouseY <= listBottom) {
                int step = delta > 0 ? -1 : 1;
                this.effectScrollOffset = clampScrollOffset(this.effectScrollOffset + step, total, visible);
                this.init();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
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

    public static final class EnchantmentRow {
        private final CompoundTag entry;
        private final String tagName;
        private final int index;

        public EnchantmentRow(CompoundTag entry, String tagName, int index) {
            this.entry = entry;
            this.tagName = tagName;
            this.index = index;
        }

        public int getLevel() {
            if (this.entry.contains("lvl", Tag.TAG_ANY_NUMERIC)) {
                return this.entry.getShort("lvl") & 0xFFFF;
            }
            return 1;
        }

        public String getId() {
            return this.entry.getString("id");
        }

        public String getTagName() {
            return this.tagName;
        }

        public int getIndex() {
            return this.index;
        }

        public String getDisplayName() {
            ResourceLocation id = ResourceLocation.tryParse(getId());
            if (id != null) {
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
                if (enchantment != null) {
                    return Component.translatable(enchantment.getDescriptionId()).getString();
                }
            }
            return getId().isBlank() ? "unknown" : getId();
        }
    }

    private enum HideFlagOption {
        ENCHANTMENTS(HIDE_FLAGS_TAG, 1, "Encantamentos"),
        MODIFIERS(HIDE_FLAGS_TAG, 2, "Modificadores"),
        UNBREAKABLE(HIDE_FLAGS_TAG, 4, "Inquebrável"),
        CAN_DESTROY(HIDE_FLAGS_TAG, 8, "Pode Quebrar"),
        CAN_PLACE(HIDE_FLAGS_TAG, 16, "Pode ser Colocado em"),
        MISCELLANEOUS(HIDE_FLAGS_TAG, 32, "Outros"),
        DYE(HIDE_FLAGS_TAG, 64, "Cor"),
        UPGRADES(HIDE_FLAGS_TAG, 128, "Upgrades"),
        EFFECT_ON_USE(SF_HIDE_EFFECT_FLAGS_TAG, 1, "Efeito On Use"),
        EFFECT_ON_HIT(SF_HIDE_EFFECT_FLAGS_TAG, 2, "Efeito On Hit"),
        EFFECT_ON_HURT(SF_HIDE_EFFECT_FLAGS_TAG, 4, "Efeito On Hurt"),
        EFFECT_ON_EQUIP(SF_HIDE_EFFECT_FLAGS_TAG, 8, "Efeito On Equip");

        static final HideFlagOption[] ORDERED = {
            ENCHANTMENTS,
            MODIFIERS,
            UNBREAKABLE,
            CAN_DESTROY,
            CAN_PLACE,
            MISCELLANEOUS,
            DYE,
            UPGRADES,
            EFFECT_ON_USE,
            EFFECT_ON_HIT,
            EFFECT_ON_HURT,
            EFFECT_ON_EQUIP
        };

        private final String tagName;
        private final int value;
        private final String label;

        HideFlagOption(String tagName, int value, String label) {
            this.tagName = tagName;
            this.value = value;
            this.label = label;
        }

        String tagName() {
            return this.tagName;
        }

        int value() {
            return this.value;
        }

        String label() {
            return this.label;
        }
    }
}