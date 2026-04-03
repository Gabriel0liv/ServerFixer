package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.AbstractEditorScreen;
import com.gabri.serverfixes.client.gui.editor.SnbtFormatUtils;
import com.gabri.serverfixes.client.gui.editor.SyntaxNbtEditBox;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.SaveBlockEditorPacket;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlockEditorScreen extends AbstractEditorScreen {
    private final BlockPos blockPos;
    private final String blockId;
    private final CompoundTag stateValues;
    private final CompoundTag allowedValues;
    private CompoundTag blockEntityTag;

    private SyntaxNbtEditBox rawNbtBox;
    private String rawNbtText;
    private String statusMessage = "Edite e clique em Salvar.";
    private String parseError;

    private Tab currentTab = Tab.BLOCK_STATES;

    private enum Tab {
        BLOCK_STATES,
        RAW_NBT
    }

    public BlockEditorScreen(BlockPos blockPos, String blockId, CompoundTag stateValues, CompoundTag allowedValues, CompoundTag blockEntityTag) {
        super(Component.literal("Block Editor"));
        this.blockPos = blockPos != null ? blockPos : BlockPos.ZERO;
        this.blockId = blockId != null ? blockId : "minecraft:air";
        this.stateValues = stateValues != null ? stateValues.copy() : new CompoundTag();
        this.allowedValues = allowedValues != null ? allowedValues.copy() : new CompoundTag();
        this.blockEntityTag = blockEntityTag != null ? blockEntityTag.copy() : new CompoundTag();
        this.rawNbtText = this.blockEntityTag.toString();
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int frameX = 8;
        int frameY = 8;
        int frameW = this.width - 16;
        int frameH = this.height - 16;
        int headerY = frameY + 6;
        int barY = headerY + 20 + 4;
        int footerY = frameY + frameH - 26;

        int tabX = frameX + 12;
        this.addRenderableWidget(Button.builder(Component.literal((this.currentTab == Tab.BLOCK_STATES ? "§6" : "§7") + "BlockStates"), btn -> {
            this.currentTab = Tab.BLOCK_STATES;
            this.init();
        }).bounds(tabX, barY, 100, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal((this.currentTab == Tab.RAW_NBT ? "§6" : "§7") + "Raw NBT"), btn -> {
            this.currentTab = Tab.RAW_NBT;
            this.init();
        }).bounds(tabX + 104, barY, 90, 18).build());

        if (this.currentTab == Tab.BLOCK_STATES) {
            initStateRows(frameX + 12, barY + 24, frameW - 24, footerY - (barY + 24) - 10);
        } else {
            initRawNbt(frameX + 12, barY + 24, frameW - 24, footerY - (barY + 24) - 10);
        }

        int closeX = frameX + frameW / 2 - 96;
        int saveX = frameX + frameW / 2 + 6;
        this.addRenderableWidget(Button.builder(Component.literal("§cFechar"), btn -> onClose())
            .bounds(closeX, footerY, 90, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("§bSalvar"), btn -> saveBlockChanges())
            .bounds(saveX, footerY, 90, 20).build());
    }

    private void initStateRows(int x, int y, int width, int height) {
        List<String> keys = new ArrayList<>(this.stateValues.getAllKeys());
        Collections.sort(keys);

        int rowY = y;
        int rowStep = 22;
        int limitY = y + Math.max(0, height - rowStep);

        for (String key : keys) {
            if (rowY > limitY) {
                break;
            }

            String value = this.stateValues.getString(key);
            this.addRenderableWidget(Button.builder(Component.literal(key + " = " + value), btn -> {
                cyclePropertyValue(key);
                this.init();
            }).bounds(x, rowY, width, 18).build());

            rowY += rowStep;
        }
    }

    private void initRawNbt(int x, int y, int width, int height) {
        int topButtonsY = y;
        int editorY = y + 20;

        this.addRenderableWidget(Button.builder(Component.literal("Formatar"), btn -> formatRawNbt())
            .bounds(x, topButtonsY, 76, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Aplicar"), btn -> applyRawNbt())
            .bounds(x + 82, topButtonsY, 70, 16).build());

        this.rawNbtBox = new SyntaxNbtEditBox(this.font, x, editorY, width, Math.max(60, height - 20),
            Component.literal("BlockEntity SNBT"), Component.literal("{}"));
        this.rawNbtBox.setValue(this.rawNbtText != null ? this.rawNbtText : "{}");
        this.rawNbtBox.setValueListener(value -> {
            this.rawNbtText = value;
            this.parseError = null;
            this.statusMessage = "Alterações pendentes no NBT.";
        });
        this.addRenderableWidget(this.rawNbtBox);
    }

    private void cyclePropertyValue(String propertyName) {
        String currentValue = this.stateValues.getString(propertyName);
        List<String> allowed = getAllowedValues(propertyName);
        if (allowed.isEmpty()) {
            return;
        }

        int currentIndex = allowed.indexOf(currentValue);
        int nextIndex = currentIndex >= 0 ? (currentIndex + 1) % allowed.size() : 0;
        this.stateValues.putString(propertyName, allowed.get(nextIndex));
    }

    private List<String> getAllowedValues(String propertyName) {
        if (!this.allowedValues.contains(propertyName, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag list = this.allowedValues.getList(propertyName, Tag.TAG_STRING);
        List<String> values = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Tag entry = list.get(i);
            if (entry instanceof StringTag stringTag) {
                values.add(stringTag.getAsString());
            }
        }
        return values;
    }

    private void formatRawNbt() {
        try {
            CompoundTag parsed = parseRawNbt();
            String pretty = SnbtFormatUtils.prettyPrint(parsed.toString());
            this.rawNbtText = pretty;
            if (this.rawNbtBox != null) {
                this.rawNbtBox.setValue(pretty);
            }
            this.parseError = null;
            this.statusMessage = "Raw NBT formatado.";
        } catch (Exception e) {
            this.parseError = e.getMessage();
            this.statusMessage = "Falha ao formatar Raw NBT.";
        }
    }

    private void applyRawNbt() {
        try {
            CompoundTag parsed = parseRawNbt();
            this.blockEntityTag = parsed.copy();
            this.rawNbtText = parsed.toString();
            if (this.rawNbtBox != null) {
                this.rawNbtBox.setValue(this.rawNbtText);
            }
            this.parseError = null;
            this.statusMessage = "Raw NBT aplicado localmente.";
        } catch (Exception e) {
            this.parseError = e.getMessage();
            this.statusMessage = "Raw NBT inválido.";
        }
    }

    private CompoundTag parseRawNbt() throws CommandSyntaxException {
        String raw = this.rawNbtBox != null ? this.rawNbtBox.getValue() : this.rawNbtText;
        if (raw == null || raw.isBlank()) {
            raw = "{}";
        }
        return TagParser.parseTag(raw);
    }

    private void saveBlockChanges() {
        if (this.currentTab == Tab.RAW_NBT) {
            try {
                this.blockEntityTag = parseRawNbt();
                this.parseError = null;
            } catch (Exception e) {
                this.parseError = e.getMessage();
                this.statusMessage = "Não foi possível salvar: Raw NBT inválido.";
                return;
            }
        }

        NetworkHandler.sendToServer(new SaveBlockEditorPacket(this.blockPos, this.stateValues.copy(), this.blockEntityTag.copy()));
        this.statusMessage = "Alterações enviadas ao servidor.";
        this.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);

        int frameX = 8;
        int frameY = 8;
        int frameW = this.width - 16;
        int frameH = this.height - 16;
        int headerY = frameY + 6;
        int barY = headerY + 20 + 4;
        int contentY = barY + 24;
        int footerY = frameY + frameH - 26;

        renderEditorFrame(graphics, Component.literal("§6§lBlock Editor"), frameX, frameY, frameW, frameH, headerY, barY, contentY, footerY);

        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Bloco: " + this.blockId, frameX + 12, headerY + 2, 0xFFE5F6FF);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Pos: " + this.blockPos.toShortString(), frameX + 12, barY - 10, 0xFFCCD9F1);

        if (this.currentTab == Tab.BLOCK_STATES && this.stateValues.getAllKeys().isEmpty()) {
            graphics.drawString(this.font, "§7Este bloco não possui BlockStates editáveis.", frameX + 16, contentY + 8, 0xFFBFD1E8);
        }

        int statusY = footerY - 14;
        if (this.parseError != null && !this.parseError.isBlank()) {
            graphics.drawString(this.font, "§cErro SNBT: " + trimForStatus(this.parseError), frameX + 12, statusY, 0xFFFF6666);
        } else {
            graphics.drawString(this.font, "§7" + this.statusMessage, frameX + 12, statusY, 0xFFBFD1E8);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (this.currentTab == Tab.RAW_NBT && this.rawNbtBox != null) {
            this.rawNbtBox.renderSuggestionOverlay(graphics);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String trimForStatus(String error) {
        if (error == null) return "";
        String clean = error.replace('\n', ' ').replace('\r', ' ');
        if (clean.length() <= 140) {
            return clean;
        }
        return clean.substring(0, 140) + "...";
    }
}
