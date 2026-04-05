package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.AbstractEditorScreen;
import com.gabri.serverfixes.client.gui.editor.SnbtFormatUtils;
import com.gabri.serverfixes.client.gui.editor.SyntaxNbtEditBox;
import com.gabri.serverfixes.network.ContextTargetType;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.SaveContextNbtPacket;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import net.minecraft.core.BlockPos;

@SuppressWarnings("null")
public class ContextNbtEditorScreen extends AbstractEditorScreen {
    private final ContextTargetType targetType;
    private final int containerId;
    private final int slotIndex;
    private final int entityId;
    private final BlockPos blockPos;
    private final String targetName;

    private CompoundTag originalTag;
    private CompoundTag workingTag;
    private SyntaxNbtEditBox nbtBox;

    private String rawNbtText;
    private String statusMessage = "Faça alterações e clique em Salvar.";
    private String parseError;

    public ContextNbtEditorScreen(ContextTargetType targetType, int containerId, int slotIndex, int entityId, BlockPos blockPos, CompoundTag nbtTag, String targetName) {
        super(Component.literal("Context NBT Editor"));
        this.targetType = targetType;
        this.containerId = containerId;
        this.slotIndex = slotIndex;
        this.entityId = entityId;
        this.blockPos = blockPos != null ? blockPos : BlockPos.ZERO;
        this.targetName = targetName != null && !targetName.isBlank() ? targetName : "Contexto";

        this.originalTag = nbtTag != null ? nbtTag.copy() : new CompoundTag();
        this.workingTag = this.originalTag.copy();
        this.rawNbtText = this.workingTag.toString();
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

        int editorX = frameX + 12;
        int editorY = barY + 14;
        int editorW = frameW - 24;
        int editorH = Math.max(80, footerY - editorY - 8);

        this.nbtBox = new SyntaxNbtEditBox(this.font, editorX, editorY, editorW, editorH,
            Component.literal("SNBT"), Component.literal("{...}"));
        this.nbtBox.setValue(this.rawNbtText != null ? this.rawNbtText : "{}");
        this.nbtBox.setValueListener(value -> {
            this.rawNbtText = value;
            this.parseError = null;
            this.statusMessage = "Alterações pendentes.";
        });
        this.addRenderableWidget(this.nbtBox);

        int topButtonsY = barY;
        this.addRenderableWidget(Button.builder(Component.literal("Formatar"), btn -> formatSnbt())
            .bounds(editorX, topButtonsY, 76, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> resetSnbt())
            .bounds(editorX + 82, topButtonsY, 64, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("Aplicar"), btn -> applySnbt())
            .bounds(editorX + 152, topButtonsY, 70, 16).build());

        int closeX = frameX + frameW / 2 - 96;
        int saveX = frameX + frameW / 2 + 6;
        this.addRenderableWidget(Button.builder(Component.literal("§cFechar"), btn -> onClose())
            .bounds(closeX, footerY, 90, 20).build());
        this.addRenderableWidget(new PulsingButton(saveX, footerY, 90, 20, Component.literal("§bSalvar"), (btn) -> {
            try {
                CompoundTag parsed = parseCurrentSnbt();
                this.workingTag = parsed.copy();
                this.parseError = null;
                this.statusMessage = "Alterações enviadas ao servidor.";
                NetworkHandler.sendToServer(new SaveContextNbtPacket(
                    this.targetType,
                    this.containerId,
                    this.slotIndex,
                    this.entityId,
                    this.blockPos,
                    this.workingTag
                ));
            } catch (Exception e) {
                this.parseError = e.getMessage();
                this.statusMessage = "Não foi possível salvar.";
                ((PulsingButton)btn).pulseError(1200);
            }
        }));
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
        int contentY = barY + 14;
        int footerY = frameY + frameH - 26;

        renderEditorFrame(graphics, Component.literal("§6§lContext Editor"), frameX, frameY, frameW, frameH, headerY, barY, contentY, footerY);

        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Alvo: " + this.targetName, frameX + 12, headerY + 2, 0xFFE5F6FF);
        GuiLayoutUtils.drawFieldLabel(graphics, this.font, "Tipo: " + this.targetType.name(), frameX + 12, barY - 10, 0xFFC2D8F1);

        int statusY = footerY - 14;
        if (this.parseError != null && !this.parseError.isBlank()) {
            graphics.drawString(this.font, "§cErro SNBT: " + trimForStatus(this.parseError), frameX + 12, statusY, 0xFFFF6666);
        } else {
            graphics.drawString(this.font, "§7" + this.statusMessage, frameX + 12, statusY, 0xFFBFD1E8);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void formatSnbt() {
        try {
            CompoundTag parsed = parseCurrentSnbt();
            String pretty = SnbtFormatUtils.prettyPrint(parsed.toString());
            this.rawNbtText = pretty;
            if (this.nbtBox != null) {
                this.nbtBox.setValue(pretty);
            }
            this.parseError = null;
            this.statusMessage = "SNBT formatado.";
        } catch (Exception e) {
            this.parseError = e.getMessage();
            this.statusMessage = "Falha ao formatar SNBT.";
        }
    }

    private void resetSnbt() {
        this.workingTag = this.originalTag.copy();
        this.rawNbtText = this.workingTag.toString();
        if (this.nbtBox != null) {
            this.nbtBox.setValue(this.rawNbtText);
        }
        this.parseError = null;
        this.statusMessage = "SNBT resetado para o estado recebido.";
    }

    private void applySnbt() {
        try {
            CompoundTag parsed = parseCurrentSnbt();
            this.workingTag = parsed.copy();
            this.rawNbtText = parsed.toString();
            if (this.nbtBox != null) {
                this.nbtBox.setValue(this.rawNbtText);
            }
            this.parseError = null;
            this.statusMessage = "SNBT válido aplicado localmente.";
        } catch (Exception e) {
            this.parseError = e.getMessage();
            this.statusMessage = "SNBT inválido.";
        }
    }

    private void saveToServer() {
        try {
            CompoundTag parsed = parseCurrentSnbt();
            this.workingTag = parsed.copy();
            this.parseError = null;
            this.statusMessage = "Alterações enviadas ao servidor.";
            NetworkHandler.sendToServer(new SaveContextNbtPacket(
                this.targetType,
                this.containerId,
                this.slotIndex,
                this.entityId,
                this.blockPos,
                this.workingTag
            ));
        } catch (Exception e) {
            this.parseError = e.getMessage();
            this.statusMessage = "Não foi possível salvar.";
        }
    }

    private CompoundTag parseCurrentSnbt() throws CommandSyntaxException {
        String raw = this.rawNbtText;
        if (raw == null || raw.isBlank()) {
            raw = "{}";
        }
        return TagParser.parseTag(raw);
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
