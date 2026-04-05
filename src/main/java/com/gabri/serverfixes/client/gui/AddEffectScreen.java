package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@SuppressWarnings("null")
public class AddEffectScreen extends Screen {
    private static final int PANEL_MIN_WIDTH = 300;
    private static final int PANEL_MAX_WIDTH = 420;
    private static final int PANEL_MIN_HEIGHT = 250;
    private static final int PANEL_MAX_HEIGHT = 310;
    private static final int TAB_HEIGHT = 18;
    private static final int BUTTON_WIDTH = 108;
    private static final int ACTION_BUTTON_WIDTH = 22;
    private static final int RESET_BUTTON_WIDTH = 20;
    private static final int RESET_BUTTON_GAP = 4;
    private static final int SECTION_GAP = 6;
    private static final int LABEL_INPUT_GAP = 2;
    private static final int POTION_PREVIEW_SIZE = 56;
    private static final int POTION_PREVIEW_GAP = 8;
    private static final int LEVEL_INPUT_WIDTH = 42;
    private static final String ON_USE_MIRROR_TAG = "on_use_potion_mirror";
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(?i)^#[0-9a-f]{6}$");

    private final ItemEditorScreen parent;
    private final ItemEditorScreen.Category editingCategory;
    private final int editingIndex;
    private ItemEditorScreen.Category category;

    private EditBox idBox;
    private EditBox durationBox;
    private EditBox amplifierBox;
    private EditBox chanceBox;
    private EditBox onUseColorBox;
    private boolean pendingSelf;
    private boolean pendingAmbient = false;
    private boolean pendingShowParticles = true;
    private boolean pendingShowIcon = true;

    private String pendingId = "";
    private String pendingDur = "10";
    private String pendingAmp = "0";
    private String pendingChance = "1.0";
    private String pendingOnUseColor = "";
    private String pendingSlot = "any";
    private boolean loadedFromTag;

    private String originalId = "";
    private String originalChance = "1.0";
    private String originalSlot = "any";
    private boolean originalSelf;
    private boolean showPotionOnUseUi;
    private boolean showPotionPreview;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int potionPreviewX;
    private int potionPreviewY;
    private int idLabelY;
    private int statsLabelY;
    private int flagsLabelY;
    private int colorLabelY;
    private int idRowY;
    private int statsRowY;
    private int flagsRowY;
    private int colorRowY;
    
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

    private static String normalizeHexColor(String raw) {
        if (raw == null) return null;
        String trimmed = stripWrappingQuotes(raw).trim();
        if (!HEX_COLOR_PATTERN.matcher(trimmed).matches()) return null;
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static int parseHexColor(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }

    private static String colorIntToHex(int color) {
        return String.format(Locale.ROOT, "#%06x", color & 0xFFFFFF);
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
        this(parent, category, -1);
    }

    public AddEffectScreen(ItemEditorScreen parent, ItemEditorScreen.Category category, int editingIndex) {
        super(Component.literal(editingIndex >= 0 ? "Editar Efeito" : "Adicionar Efeito"));
        this.parent = parent;
        this.editingCategory = category;
        this.category = category;
        this.editingIndex = editingIndex;
        this.pendingSelf = category == ItemEditorScreen.Category.ON_HURT;
        this.originalSelf = this.pendingSelf;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int maxPanelWidth = Math.max(220, this.width - 12);
        int maxPanelHeight = Math.max(200, this.height - 12);
        this.panelWidth = Math.min(PANEL_MAX_WIDTH, Math.max(PANEL_MIN_WIDTH, this.width - 32));
        this.panelHeight = Math.min(PANEL_MAX_HEIGHT, Math.max(PANEL_MIN_HEIGHT, this.height - 48));
        this.panelWidth = Math.min(this.panelWidth, maxPanelWidth);
        this.panelHeight = Math.min(this.panelHeight, maxPanelHeight);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;

        this.showPotionOnUseUi = this.category == ItemEditorScreen.Category.ON_USE && isHeldPotionItem();
        this.showPotionPreview = this.showPotionOnUseUi && this.panelWidth >= 350;

        loadEditedValuesIfNeeded();

        int midX = this.width / 2;
        int formRight = this.panelX + this.panelWidth - 22;
        int fieldX = this.panelX + 24;
        int previewReservedWidth = this.showPotionPreview ? (POTION_PREVIEW_SIZE + POTION_PREVIEW_GAP) : 0;
        int baseFieldWidth = Math.max(148, formRight - fieldX - RESET_BUTTON_WIDTH - 10 - previewReservedWidth);
        int idFieldWidth = Math.max(132, baseFieldWidth - ACTION_BUTTON_WIDTH - RESET_BUTTON_GAP);
        int tabsY = this.panelY + 28;
        int cursorY = tabsY + TAB_HEIGHT + 10;

        this.idLabelY = cursorY;
        this.idRowY = this.idLabelY + this.font.lineHeight + LABEL_INPUT_GAP;
        cursorY = this.idRowY + 20 + SECTION_GAP;

        this.statsLabelY = cursorY;
        this.statsRowY = this.statsLabelY + this.font.lineHeight + LABEL_INPUT_GAP;
        cursorY = this.statsRowY + 20 + SECTION_GAP;

        this.flagsLabelY = cursorY;
        this.flagsRowY = this.flagsLabelY + this.font.lineHeight + LABEL_INPUT_GAP;
        cursorY = this.flagsRowY + 20 + SECTION_GAP;

        this.colorLabelY = cursorY;
        this.colorRowY = this.colorLabelY + this.font.lineHeight + LABEL_INPUT_GAP;

        this.potionPreviewX = fieldX + baseFieldWidth + POTION_PREVIEW_GAP;
        this.potionPreviewY = this.idRowY;

        addModeTabs(fieldX, tabsY, formRight - fieldX);

        this.originalId = this.pendingId;
        this.originalChance = this.pendingChance;
        this.originalSlot = this.pendingSlot;
        this.originalSelf = this.pendingSelf;

        this.idBox = new SelectableEditBox(this.font, fieldX, this.idRowY, idFieldWidth, 20, Component.literal("ID"));
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
        }).bounds(fieldX + idFieldWidth + RESET_BUTTON_GAP, this.idRowY, ACTION_BUTTON_WIDTH, 20).build());
        // Do not show reset button for potion editing modes (poções/on-use-potion UI)
        if (!(this.category == ItemEditorScreen.Category.POTIONS || (this.category == ItemEditorScreen.Category.ON_USE && this.showPotionOnUseUi))) {
            this.addResetButton(fieldX + idFieldWidth + RESET_BUTTON_GAP + ACTION_BUTTON_WIDTH + RESET_BUTTON_GAP, this.idRowY, () -> {
                this.pendingId = this.originalId;
                this.init();
            });
        }

        int durationFieldWidth = Math.max(96, baseFieldWidth - LEVEL_INPUT_WIDTH - 6);
        this.durationBox = new SelectableEditBox(this.font, fieldX, this.statsRowY, durationFieldWidth, 20, Component.literal("Duração (segundos)"));
        this.durationBox.setValue(this.pendingDur);
        this.durationBox.setFilter(this::isValidSecondsInput);
        this.durationBox.setResponder(value -> this.pendingDur = value);
        this.addRenderableWidget(this.durationBox);

        this.amplifierBox = new SelectableEditBox(this.font, fieldX + durationFieldWidth + 6, this.statsRowY, LEVEL_INPUT_WIDTH, 20, Component.literal("Nível"));
        this.amplifierBox.setMaxLength(3);
        this.amplifierBox.setValue(this.pendingAmp);
        this.amplifierBox.setResponder(value -> this.pendingAmp = value);
        this.addRenderableWidget(this.amplifierBox);

        if (category == ItemEditorScreen.Category.POTIONS) {
            int toggleWidth = Math.max(78, (baseFieldWidth - 8) / 3);
            this.addRenderableWidget(createToggleButton(fieldX, this.flagsRowY, toggleWidth, "Ambient", this.pendingAmbient,
                value -> this.pendingAmbient = value));
            this.addRenderableWidget(createToggleButton(fieldX + toggleWidth + 4, this.flagsRowY, toggleWidth, "Partículas", this.pendingShowParticles,
                value -> this.pendingShowParticles = value));
            this.addRenderableWidget(createToggleButton(fieldX + (toggleWidth + 4) * 2, this.flagsRowY, toggleWidth, "Ícone", this.pendingShowIcon,
                value -> this.pendingShowIcon = value));
            this.chanceBox = null;
        } else if (category == ItemEditorScreen.Category.ON_USE) {
            // On Use behaves like normal potions, always applicable.
            int toggleWidth = Math.max(78, (baseFieldWidth - 8) / 3);
            this.addRenderableWidget(createToggleButton(fieldX, this.flagsRowY, toggleWidth, "Ambient", this.pendingAmbient,
                value -> this.pendingAmbient = value));
            this.addRenderableWidget(createToggleButton(fieldX + toggleWidth + 4, this.flagsRowY, toggleWidth, "Partículas", this.pendingShowParticles,
                value -> this.pendingShowParticles = value));
            this.addRenderableWidget(createToggleButton(fieldX + (toggleWidth + 4) * 2, this.flagsRowY, toggleWidth, "Ícone", this.pendingShowIcon,
                value -> this.pendingShowIcon = value));
            this.chanceBox = null;

            if (this.showPotionOnUseUi) {
                int colorFieldWidth = Math.max(120, baseFieldWidth - ACTION_BUTTON_WIDTH - RESET_BUTTON_GAP);
                this.onUseColorBox = new SelectableEditBox(this.font, fieldX, this.colorRowY, colorFieldWidth, 20, Component.literal("Cor da poção (#RRGGBB)"));
                this.onUseColorBox.setMaxLength(7);
                this.onUseColorBox.setValue(this.pendingOnUseColor);
                this.onUseColorBox.setResponder(value -> this.pendingOnUseColor = value);
                this.addRenderableWidget(this.onUseColorBox);

                this.addRenderableWidget(Button.builder(Component.literal("§b..."), btn -> {
                    this.updatePending();
                    String initial = normalizeHexColor(this.pendingOnUseColor);
                    if (initial == null) {
                        initial = "#ffffff";
                    }
                    this.minecraft.setScreen(new HexColorPickerScreen(this, initial, hex -> {
                        this.pendingOnUseColor = hex;
                        this.init();
                    }));
                }).bounds(fieldX + colorFieldWidth + RESET_BUTTON_GAP, this.colorRowY, ACTION_BUTTON_WIDTH, 20).build());
                // Removed reset button for potion color — resets should live in admin/config UI
            } else {
                this.onUseColorBox = null;
            }
        } else if (category == ItemEditorScreen.Category.ON_EQUIP) {
            this.onUseColorBox = null;
            this.chanceBox = null;

            int toggleWidth = Math.max(78, (baseFieldWidth - 8) / 3);
            this.addRenderableWidget(createToggleButton(fieldX, this.flagsRowY, toggleWidth, "Ambient", this.pendingAmbient,
                value -> this.pendingAmbient = value));
            this.addRenderableWidget(createToggleButton(fieldX + toggleWidth + 4, this.flagsRowY, toggleWidth, "Partículas", this.pendingShowParticles,
                value -> this.pendingShowParticles = value));
            this.addRenderableWidget(createToggleButton(fieldX + (toggleWidth + 4) * 2, this.flagsRowY, toggleWidth, "Ícone", this.pendingShowIcon,
                value -> this.pendingShowIcon = value));

            List<String> slotOptions = collectSlotOptions();
            if (!slotOptions.contains(this.pendingSlot)) slotOptions.add(this.pendingSlot);
            this.addRenderableWidget(Button.builder(Component.literal(this.pendingSlot.toUpperCase(Locale.ROOT)), (btn) -> {
                this.updatePending();
                this.minecraft.setScreen(new SlotSelectionScreen(this, slotOptions, (slotId) -> {
                    this.pendingSlot = slotId;
                    this.init();
                }));
            }).bounds(fieldX, this.colorRowY, baseFieldWidth, 20).build());

            this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, this.colorRowY, () -> {
                this.pendingSlot = this.originalSlot;
                this.init();
            });
        } else {
            this.onUseColorBox = null;
            this.chanceBox = new SelectableEditBox(this.font, fieldX, this.flagsRowY, baseFieldWidth, 20, Component.literal("Chance"));
            this.chanceBox.setValue(this.pendingChance);
            this.chanceBox.setResponder(value -> this.pendingChance = value);
            this.addRenderableWidget(this.chanceBox);
            this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, this.flagsRowY, () -> {
                this.pendingChance = this.originalChance;
                this.init();
            });

            this.addRenderableWidget(CycleButton.builder((Boolean b) -> b ? Component.literal("SELF") : Component.literal("TARGET"))
                .withValues(true, false)
                .withInitialValue(this.pendingSelf)
                .displayOnlyValue()
                .create(fieldX, this.colorRowY, baseFieldWidth, 20, Component.literal("Alvo"), (btn, val) -> this.pendingSelf = val));
            this.addResetButton(fieldX + baseFieldWidth + RESET_BUTTON_GAP, this.colorRowY, () -> {
                this.pendingSelf = this.originalSelf;
                this.init();
            });
        }

        int buttonY = this.panelY + this.panelHeight - 32;
        this.addRenderableWidget(new PulsingButton(midX - BUTTON_WIDTH - 10, buttonY, BUTTON_WIDTH, 20, Component.literal("Salvar"), (btn) -> {
            // basic validation before applying
            updatePending();
            if (!isValidSecondsInput(this.pendingDur)) {
                ((PulsingButton)btn).pulseError(1200);
                return;
            }
            addEffect();
        }));
        this.addRenderableWidget(Button.builder(Component.literal("Cancelar"), (btn) -> this.minecraft.setScreen(this.parent))
            .bounds(midX + 10, buttonY, BUTTON_WIDTH, 20).build());
    }

    private void updatePending() {
        this.pendingId = this.idBox.getValue();
        this.pendingDur = this.durationBox.getValue();
        this.pendingAmp = this.amplifierBox.getValue();
        if (this.chanceBox != null) this.pendingChance = this.chanceBox.getValue();
        if (this.onUseColorBox != null) this.pendingOnUseColor = this.onUseColorBox.getValue();
    }

    private void addEffect() {
        try {
            updatePending();

            String id = normalizeEffectId(this.pendingId);
            int dur = parseDurationSecondsToTicks(this.pendingDur);
            int amp = parseIntInput(this.pendingAmp, 0);

            if (category == ItemEditorScreen.Category.POTIONS) {
                ListTag list = parent.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
                CompoundTag entry = new CompoundTag();
                writePotionEffectId(entry, id);
                entry.putInt("Duration", dur);
                entry.putInt("Amplifier", amp);
                entry.putBoolean("Ambient", this.pendingAmbient);
                entry.putBoolean("ShowParticles", this.pendingShowParticles);
                entry.putBoolean("ShowIcon", this.pendingShowIcon);
                upsertEntry(list, entry);
                parent.currentTag.put("CustomPotionEffects", list);
            } else {
                CompoundTag sfTag = parent.currentTag.getCompound("SF_ItemEffects");
                String listKey;
                if (category == ItemEditorScreen.Category.ON_HIT) listKey = "on_hit";
                else if (category == ItemEditorScreen.Category.ON_HURT) listKey = "on_hurt";
                else if (category == ItemEditorScreen.Category.ON_EQUIP) listKey = "on_equip";
                else listKey = "on_use";
                ListTag list = sfTag.getList(listKey, Tag.TAG_COMPOUND);
                
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id);
                entry.putInt("duration", dur);
                entry.putInt("amplifier", amp);

                boolean handled = false;

                // For potion items in ON_USE, persist to vanilla potion tags so tooltip/behavior is vanilla-compatible.
                if (category == ItemEditorScreen.Category.ON_USE) {
                    entry.putBoolean("Ambient", this.pendingAmbient);
                    entry.putBoolean("ShowParticles", this.pendingShowParticles);
                    entry.putBoolean("ShowIcon", this.pendingShowIcon);

                    boolean useVanillaPotionStorage = this.showPotionOnUseUi
                        || parent.currentTag.contains("Potion", Tag.TAG_STRING)
                        || parent.currentTag.contains("CustomPotionEffects", Tag.TAG_LIST);

                    if (useVanillaPotionStorage) {
                        String normalizedColor = normalizeHexColor(this.pendingOnUseColor);
                        if (normalizedColor != null) {
                            int potionColor = parseHexColor(normalizedColor);
                            sfTag.putInt("on_use_color", potionColor);
                            parent.currentTag.putInt("CustomPotionColor", potionColor);
                        } else {
                            sfTag.remove("on_use_color");
                            parent.currentTag.remove("CustomPotionColor");
                        }

                        ListTag custom = parent.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
                        CompoundTag customEntry = new CompoundTag();
                        writePotionEffectId(customEntry, id);
                        customEntry.putInt("Duration", dur);
                        customEntry.putInt("Amplifier", amp);
                        customEntry.putBoolean("Ambient", this.pendingAmbient);
                        customEntry.putBoolean("ShowParticles", this.pendingShowParticles);
                        customEntry.putBoolean("ShowIcon", this.pendingShowIcon);

                        if (this.editingIndex >= 0 && this.editingIndex < custom.size()) {
                            custom.set(this.editingIndex, customEntry);
                        } else {
                            custom.add(customEntry);
                        }

                        parent.currentTag.put("CustomPotionEffects", custom);
                        // Critical: neutralize vanilla base potion to prevent hidden/reverting base effects.
                        parent.currentTag.putString("Potion", "minecraft:water");

                        // ON_USE for potion items should be fully represented by vanilla NBT.
                        sfTag.put(listKey, new ListTag());
                        sfTag.remove(ON_USE_MIRROR_TAG);
                        parent.currentTag.put("SF_ItemEffects", sfTag);
                        handled = true;
                    } else {
                        sfTag.remove(ON_USE_MIRROR_TAG);
                    }
                } else if (category == ItemEditorScreen.Category.ON_EQUIP) {
                    entry.putString("Slot", this.pendingSlot == null || this.pendingSlot.isBlank() ? "any" : this.pendingSlot.toLowerCase(Locale.ROOT));
                    entry.putBoolean("Ambient", this.pendingAmbient);
                    entry.putBoolean("ShowParticles", this.pendingShowParticles);
                    entry.putBoolean("ShowIcon", this.pendingShowIcon);
                } else {
                    entry.putDouble("chance", parseDoubleInput(this.pendingChance, 1.0D));
                    entry.putBoolean("self", this.pendingSelf);
                }

                if (!handled) {
                    upsertEntry(list, entry);
                    sfTag.put(listKey, list);
                    parent.currentTag.put("SF_ItemEffects", sfTag);
                }
            }
            this.minecraft.setScreen(this.parent);
        } catch (Exception ignored) {}
    }

    private void upsertEntry(ListTag list, CompoundTag entry) {
        if (this.editingIndex >= 0 && this.category == this.editingCategory && this.editingIndex < list.size()) {
            list.set(this.editingIndex, entry);
        } else {
            list.add(entry);
        }
    }

    private void loadEditedValuesIfNeeded() {
        if (this.loadedFromTag || this.editingIndex < 0) {
            return;
        }

        if (this.editingCategory == ItemEditorScreen.Category.POTIONS) {
            ListTag list = this.parent.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
            if (this.editingIndex >= list.size()) {
                this.loadedFromTag = true;
                return;
            }
            CompoundTag entry = list.getCompound(this.editingIndex);
            this.pendingId = resolvePotionEffectId(entry);
            this.pendingAmp = Integer.toString(entry.getInt("Amplifier"));
            this.pendingDur = formatSecondsFromTicks(entry.getInt("Duration"));
            this.pendingAmbient = entry.contains("Ambient", Tag.TAG_BYTE) && entry.getBoolean("Ambient");
            this.pendingShowParticles = !entry.contains("ShowParticles", Tag.TAG_BYTE) || entry.getBoolean("ShowParticles");
            this.pendingShowIcon = !entry.contains("ShowIcon", Tag.TAG_BYTE) || entry.getBoolean("ShowIcon");
        } else {
            boolean useVanillaPotionStorage = this.editingCategory == ItemEditorScreen.Category.ON_USE
                && (this.parent.currentTag.contains("Potion", Tag.TAG_STRING)
                    || this.parent.currentTag.contains("CustomPotionEffects", Tag.TAG_LIST)
                    || this.showPotionOnUseUi);

            if (useVanillaPotionStorage) {
                ListTag custom = this.parent.currentTag.getList("CustomPotionEffects", Tag.TAG_COMPOUND);
                if (this.editingIndex >= custom.size()) {
                    this.loadedFromTag = true;
                    return;
                }
                CompoundTag entry = custom.getCompound(this.editingIndex);
                this.pendingId = resolvePotionEffectId(entry);
                this.pendingAmp = Integer.toString(entry.getInt("Amplifier"));
                this.pendingDur = formatSecondsFromTicks(entry.getInt("Duration"));
                this.pendingAmbient = entry.contains("Ambient", Tag.TAG_BYTE) && entry.getBoolean("Ambient");
                this.pendingShowParticles = !entry.contains("ShowParticles", Tag.TAG_BYTE) || entry.getBoolean("ShowParticles");
                this.pendingShowIcon = !entry.contains("ShowIcon", Tag.TAG_BYTE) || entry.getBoolean("ShowIcon");
                this.pendingChance = "1.0";

                CompoundTag sfTag = this.parent.currentTag.getCompound("SF_ItemEffects");
                if (sfTag.contains("on_use_color", Tag.TAG_ANY_NUMERIC)) {
                    this.pendingOnUseColor = colorIntToHex(sfTag.getInt("on_use_color"));
                } else if (this.parent.currentTag.contains("CustomPotionColor", Tag.TAG_ANY_NUMERIC)) {
                    this.pendingOnUseColor = colorIntToHex(this.parent.currentTag.getInt("CustomPotionColor"));
                } else {
                    this.pendingOnUseColor = "";
                }

                this.loadedFromTag = true;
                return;
            }

            CompoundTag sfTag = this.parent.currentTag.getCompound("SF_ItemEffects");
            String listKey;
            if (this.editingCategory == ItemEditorScreen.Category.ON_HIT) listKey = "on_hit";
            else if (this.editingCategory == ItemEditorScreen.Category.ON_HURT) listKey = "on_hurt";
            else if (this.editingCategory == ItemEditorScreen.Category.ON_EQUIP) listKey = "on_equip";
            else listKey = "on_use";
            ListTag list = sfTag.getList(listKey, Tag.TAG_COMPOUND);
            if (this.editingIndex >= list.size()) {
                // If ON_USE and potion preview is enabled, index may point to a potion/base effect.
                if (this.editingCategory == ItemEditorScreen.Category.ON_USE && this.showPotionOnUseUi) {
                    int potionIndex = this.editingIndex - list.size();
                    ItemStack potionStack = new ItemStack(Items.POTION);
                    if (this.parent.currentTag != null) potionStack.setTag(this.parent.currentTag.copy());
                    java.util.List<MobEffectInstance> potionEffects = PotionUtils.getMobEffects(potionStack);
                    if (potionIndex < 0 || potionIndex >= potionEffects.size()) {
                        this.loadedFromTag = true;
                        return;
                    }
                    MobEffectInstance inst = potionEffects.get(potionIndex);
                    ResourceLocation key = ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
                    this.pendingId = key != null ? key.toString() : Integer.toString(MobEffect.getId(inst.getEffect()));
                    this.pendingAmp = Integer.toString(inst.getAmplifier());
                    this.pendingDur = formatSecondsFromTicks(inst.getDuration());
                    this.pendingAmbient = inst.isAmbient();
                    this.pendingShowParticles = inst.isVisible();
                    this.pendingShowIcon = inst.showIcon();
                    if (this.parent.currentTag.contains("CustomPotionColor", Tag.TAG_ANY_NUMERIC)) {
                        this.pendingOnUseColor = colorIntToHex(this.parent.currentTag.getInt("CustomPotionColor"));
                    } else {
                        this.pendingOnUseColor = "";
                    }
                    this.loadedFromTag = true;
                    return;
                }
                this.loadedFromTag = true;
                return;
            }

            CompoundTag entry = list.getCompound(this.editingIndex);
            this.pendingId = entry.getString("id");
            this.pendingAmp = Integer.toString(entry.getInt("amplifier"));
            this.pendingDur = formatSecondsFromTicks(entry.getInt("duration"));
            if (this.editingCategory == ItemEditorScreen.Category.ON_EQUIP) {
                this.pendingChance = "1.0";
                this.pendingSlot = entry.contains("Slot", Tag.TAG_STRING) ? entry.getString("Slot") : "any";
            } else if (this.editingCategory != ItemEditorScreen.Category.ON_USE) {
                this.pendingChance = Double.toString(entry.contains("chance", Tag.TAG_DOUBLE) ? entry.getDouble("chance") : 1.0D);
                this.pendingSelf = entry.contains("self", Tag.TAG_BYTE) && entry.getBoolean("self");
            } else {
                this.pendingChance = "1.0";
                if (this.showPotionOnUseUi && sfTag.contains("on_use_color", Tag.TAG_ANY_NUMERIC)) {
                    this.pendingOnUseColor = colorIntToHex(sfTag.getInt("on_use_color"));
                } else if (this.showPotionOnUseUi && this.parent.currentTag.contains("CustomPotionColor", Tag.TAG_ANY_NUMERIC)) {
                    this.pendingOnUseColor = colorIntToHex(this.parent.currentTag.getInt("CustomPotionColor"));
                } else {
                    this.pendingOnUseColor = "";
                }
            }
            // Read optional potion-style flags if present (for ON_USE entries)
            this.pendingAmbient = entry.contains("Ambient", Tag.TAG_BYTE) && entry.getBoolean("Ambient");
            this.pendingShowParticles = !entry.contains("ShowParticles", Tag.TAG_BYTE) || entry.getBoolean("ShowParticles");
            this.pendingShowIcon = !entry.contains("ShowIcon", Tag.TAG_BYTE) || entry.getBoolean("ShowIcon");
        }

        this.loadedFromTag = true;
    }




    private static String formatSecondsFromTicks(int ticks) {
        if (ticks <= 0) {
            return "0.05";
        }
        if (ticks % 20 == 0) {
            return Integer.toString(ticks / 20);
        }
        double seconds = ticks / 20.0D;
        return String.format(java.util.Locale.US, "%.2f", seconds);
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
        return "minecraft:speed";
    }

    private void addModeTabs(int startX, int y, int totalWidth) {
        int gap = 4;
        int tabsCount = 4;
        int tabWidth = Math.max(64, (totalWidth - (tabsCount - 1) * gap) / tabsCount);
        this.addRenderableWidget(createModeTabButton(startX, y, tabWidth, "On Use", ItemEditorScreen.Category.ON_USE));
        this.addRenderableWidget(createModeTabButton(startX + (tabWidth + gap), y, tabWidth, "On Hit", ItemEditorScreen.Category.ON_HIT));
        this.addRenderableWidget(createModeTabButton(startX + (tabWidth + gap) * 2, y, tabWidth, "On Hurt", ItemEditorScreen.Category.ON_HURT));
        this.addRenderableWidget(createModeTabButton(startX + (tabWidth + gap) * 3, y, tabWidth, "On Equip", ItemEditorScreen.Category.ON_EQUIP));
    }

    private Button createModeTabButton(int x, int y, int width, String label, ItemEditorScreen.Category targetCategory) {
        String prefix = this.category == targetCategory ? "§6" : "§7";
        return Button.builder(Component.literal(prefix + label), btn -> {
            updatePending();
            this.category = targetCategory;
            if (this.category == ItemEditorScreen.Category.ON_HURT && !this.pendingSelf) {
                this.pendingSelf = true;
            }
            this.init();
        }).bounds(x, y, width, TAB_HEIGHT).build();
    }

    private CycleButton<Boolean> createToggleButton(int x, int y, int width, String label, boolean initial, java.util.function.Consumer<Boolean> onChange) {
        return CycleButton.builder((Boolean value) -> Component.literal(value ? "ON" : "OFF"))
            .withValues(true, false)
            .withInitialValue(initial)
            .create(x, y, width, 20, Component.literal(label), (btn, value) -> onChange.accept(value));
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
        GuiLayoutUtils.drawPanel(graphics, this.panelX - 6, this.panelY - 6, this.panelWidth + 12, this.panelHeight + 12, 0xAA050B12, 0xFF2C4B68);
        GuiLayoutUtils.drawPanel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, 0xDD0F1925, 0xFF4E6D86);
        GuiLayoutUtils.drawTitleWithUnderline(graphics, this.font, this.title, midX, this.panelY + 12, 0xFFF1F1F1, 0xFF3ACAFF);

        int tabsY = this.panelY + 28;
        graphics.fill(this.panelX + 20, tabsY + TAB_HEIGHT + 4, this.panelX + this.panelWidth - 20, tabsY + TAB_HEIGHT + 5, 0x556D93B2);

        int labelX = this.panelX + 18;
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "ID do efeito", labelX, this.idLabelY, 0xFFCCD9F1);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Duração e nível", labelX, this.statsLabelY, 0xFFCCD9F1);
        if (category == ItemEditorScreen.Category.POTIONS) {
            GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Flags de render (estilo IBE)", labelX, this.flagsLabelY, 0xFFCCD9F1);
        } else if (category == ItemEditorScreen.Category.ON_USE) {
            GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Flags de render (estilo IBE)", labelX, this.flagsLabelY, 0xFFCCD9F1);
            if (this.showPotionOnUseUi) {
                GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Cor da poção", labelX, this.colorLabelY, 0xFFCCD9F1);
                if (this.showPotionPreview) {
                    renderPotionMiniPreview(graphics);
                }
            }
        } else if (category == ItemEditorScreen.Category.ON_EQUIP) {
            GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Flags de render", labelX, this.flagsLabelY, 0xFFCCD9F1);
            GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Slot de ativação", labelX, this.colorLabelY, 0xFFCCD9F1);
        } else {
            GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Chance de aplicar", labelX, this.flagsLabelY, 0xFFCCD9F1);
            GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Alvo do efeito", labelX, this.colorLabelY, 0xFFCCD9F1);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderPotionMiniPreview(GuiGraphics graphics) {
        if (!this.showPotionPreview) return;
        ItemStack previewStack = buildPotionPreviewStack();
        if (previewStack.isEmpty()) return;

        int x = this.potionPreviewX;
        int y = this.potionPreviewY;
        int right = x + POTION_PREVIEW_SIZE;
        int bottom = y + POTION_PREVIEW_SIZE;

        graphics.fill(x - 2, y - 10, right + 2, bottom + 2, 0x66203040);
        graphics.fill(x - 2, y - 10, right + 2, y - 9, 0xFF4E6B8D);
        graphics.drawString(this.font, "Preview", x + 8, y - 8, 0xFFD0DEEE);

        graphics.fill(x, y, right, bottom, 0xAA101820);
        graphics.fill(x, y, right, y + 1, 0xFF2F4D6C);
        graphics.fill(x, bottom - 1, right, bottom, 0xFF2F4D6C);
        graphics.fill(x, y, x + 1, bottom, 0xFF2F4D6C);
        graphics.fill(right - 1, y, right, bottom, 0xFF2F4D6C);

        int itemX = x + (POTION_PREVIEW_SIZE / 2) - 8;
        int itemY = y + (POTION_PREVIEW_SIZE / 2) - 8;
        graphics.renderItem(previewStack, itemX, itemY);
    }

    private ItemStack buildPotionPreviewStack() {
        if (!this.showPotionOnUseUi || this.minecraft == null || this.minecraft.player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack held = this.minecraft.player.getMainHandItem();
        if (held.isEmpty() || !isPotionStack(held)) {
            return ItemStack.EMPTY;
        }

        ItemStack preview = held.copy();
        preview.setCount(1);
        CompoundTag tagCopy = this.parent.currentTag.copy();
        String normalizedColor = normalizeHexColor(this.pendingOnUseColor);
        if (normalizedColor != null) {
            tagCopy.putInt("CustomPotionColor", parseHexColor(normalizedColor));
        }
        preview.setTag(tagCopy);
        return preview;
    }

    private boolean isHeldPotionItem() {
        if (this.minecraft == null || this.minecraft.player == null) return false;
        return isPotionStack(this.minecraft.player.getMainHandItem());
    }

    private static boolean isPotionStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() == Items.POTION || stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION) {
            return true;
        }
        if (stack.getItem() instanceof PotionItem) {
            return true;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && (tag.contains("Potion", Tag.TAG_STRING) || tag.contains("CustomPotionEffects", Tag.TAG_LIST));
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
}
