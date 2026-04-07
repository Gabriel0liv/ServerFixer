package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.RequestFunctionContentPacket;
import com.gabri.serverfixes.network.RequestFunctionListPacket;
import com.gabri.serverfixes.network.SaveFunctionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public class FunctionStudioScreen extends Screen {
    private final List<FunctionEntry> foundFunctions = new ArrayList<>();
    private int selectedFunctionIndex = -1;
    private FunctionEntry selectedFunction = null;

    // Filtros
    private EditBox namespaceFilterBox;
    private EditBox pathFilterBox;
    private Button searchButton;

    // Lista de resultados
    private double listScrollAmount = 0.0D;
    private int maxListScroll = 0;
    private boolean listScrollbarDragging = false;

    // Editor (painel central)
    private SelectableEditBox codeEditor;
    private boolean isDirty = false;

    // Layout
    private int searchX, searchY, searchW, searchH;
    private int listX, listY, listW, listH;
    private int editorX, editorY, editorW, editorH;
    private int actionsX, actionsY, actionsW, actionsH;

    // Função sendo editada
    private ResourceLocation currentEditingFunction = null;

    // Botões de ação
    private Button btnSave;
    private Button btnSaveReload;
    private Button btnCreateNew;
    private Button btnDelete;

    public FunctionStudioScreen() {
        super(Component.literal("Function Studio"));
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.font == null) return;

        this.clearWidgets();
        this.foundFunctions.clear();
        this.selectedFunctionIndex = -1;
        this.selectedFunction = null;
        this.listScrollAmount = 0.0D;
        this.maxListScroll = 0;
        this.isDirty = false;
        this.currentEditingFunction = null;

        updateLayout();

        // Filtros (dentro do painel esquerdo)
        this.namespaceFilterBox = new EditBox(this.font, this.searchX, this.searchY, this.searchW / 2 - 4, this.searchH, Component.literal("Namespace"));
        this.namespaceFilterBox.setMaxLength(64);
        this.namespaceFilterBox.setHint(Component.literal("minecraft..."));
        this.addRenderableWidget(this.namespaceFilterBox);

        this.pathFilterBox = new EditBox(this.font, this.searchX + this.searchW / 2 + 4, this.searchY, this.searchW / 2 - 28, this.searchH, Component.literal("Path"));
        this.pathFilterBox.setMaxLength(128);
        this.pathFilterBox.setHint(Component.literal("tick..."));
        this.addRenderableWidget(this.pathFilterBox);

        this.searchButton = Button.builder(Component.literal("🔍"), btn -> requestSearch())
            .bounds(this.searchX + this.searchW - 22, this.searchY, 22, this.searchH)
            .build();
        this.addRenderableWidget(this.searchButton);

        // Editor de código (painel central)
        this.codeEditor = new SelectableEditBox(this.font, this.editorX + 8, this.editorY + 28, this.editorW - 16, this.editorH - 36, Component.literal("Código da função"));
        this.codeEditor.setMaxLength(65536);
        this.codeEditor.setBordered(false);
        this.codeEditor.setResponder(val -> this.isDirty = true);
        this.addRenderableWidget(this.codeEditor);

        // Botões de ação (painel direito)
        int btnY = this.actionsY + 28;
        int btnW = this.actionsW - 16;
        int btnX = this.actionsX + 8;
        int gap = 6;

        this.btnSave = Button.builder(Component.literal("Salvar"), btn -> saveCurrentFunction())
            .bounds(btnX, btnY, btnW, 22).build();
        this.addRenderableWidget(this.btnSave);

        this.btnSaveReload = Button.builder(Component.literal("Salvar e /reload"), btn -> saveAndReloadFunction())
            .bounds(btnX, btnY + 28 + gap, btnW, 22).build();
        this.addRenderableWidget(this.btnSaveReload);

        this.btnCreateNew = Button.builder(Component.literal("+ Nova Função"), btn -> createNewFunction())
            .bounds(btnX, btnY + 2 * (28 + gap), btnW, 22).build();
        this.addRenderableWidget(this.btnCreateNew);

        this.btnDelete = Button.builder(Component.literal("Deletar"), btn -> deleteCurrentFunction())
            .bounds(btnX, btnY + 3 * (28 + gap), btnW, 22).build();
        this.addRenderableWidget(this.btnDelete);

        // Requisitar lista inicial
        requestSearch();
    }

    private void updateLayout() {
        int padding = 8;
        int gap = 6;

        // Painel esquerdo (Lista)
        this.listX = padding;
        this.listY = 28;
        this.listW = Math.min(220, this.width / 4);
        this.listH = this.height - 64;

        // Search (topo do painel esquerdo)
        this.searchX = this.listX;
        this.searchY = this.listY + 8;
        this.searchW = this.listW - 8;
        this.searchH = 20;

        // Lista (abaixo do search)
        this.listY = this.searchY + this.searchH + gap;
        this.listH = this.height - this.listY - 36;

        // Editor (Centro)
        this.editorX = this.listX + this.listW + gap;
        this.editorY = 28;
        this.editorW = (this.width - this.listW - gap * 2 - padding * 2) / 2;
        this.editorH = this.height - 64;

        // Ações (Direita)
        this.actionsX = this.editorX + this.editorW + gap;
        this.actionsY = 28;
        this.actionsW = this.editorW;
        this.actionsH = this.height - 64;
    }

    private void requestSearch() {
        String nsFilter = this.namespaceFilterBox != null ? this.namespaceFilterBox.getValue().trim() : "";
        String pathFilter = this.pathFilterBox != null ? this.pathFilterBox.getValue().trim() : "";

        NetworkHandler.sendToServer(new RequestFunctionListPacket(nsFilter, pathFilter));
    }

    public void applyFunctionListFromServer(List<FunctionEntry> functions) {
        this.foundFunctions.clear();
        if (functions != null) {
            for (FunctionEntry dto : functions) {
                if (dto != null) {
                    this.foundFunctions.add(dto);
                }
            }
        }
        this.listScrollAmount = 0.0D;
        this.maxListScroll = Math.max(0, this.foundFunctions.size() * 24 - this.listH);
        this.selectedFunctionIndex = -1;
        this.selectedFunction = null;
        this.codeEditor.setValue("");
        updateButtonStates();
    }

    public void applyFunctionContentFromServer(ResourceLocation functionId, List<String> content) {
        if (this.selectedFunction != null && this.selectedFunction.id().equals(functionId)) {
            this.codeEditor.setValue(String.join("\n", content));
            this.isDirty = false;
            this.currentEditingFunction = functionId;
            updateButtonStates();
        }
    }

    private void saveCurrentFunction() {
        if (this.currentEditingFunction == null) return;
        List<String> lines = List.of(this.codeEditor.getValue().split("\n"));
        NetworkHandler.sendToServer(new SaveFunctionPacket(this.currentEditingFunction, lines, false));
        this.isDirty = false;
        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal("§a[Function Studio] Função salva!"), true);
        }
    }

    private void saveAndReloadFunction() {
        if (this.currentEditingFunction == null) return;
        List<String> lines = List.of(this.codeEditor.getValue().split("\n"));
        NetworkHandler.sendToServer(new SaveFunctionPacket(this.currentEditingFunction, lines, true));
        this.isDirty = false;
        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal("§a[Function Studio] Função salva e recarregada!"), true);
        }
    }

    private void createNewFunction() {
        // Abre input para nova função (simplificado: cria com nome padrão)
        String newId = "serverfixes:new_function";
        this.currentEditingFunction = ResourceLocation.tryParse(newId);
        this.selectedFunction = new FunctionEntry(this.currentEditingFunction, "serverfixes", "new_function", 0, "custom");
        this.foundFunctions.add(0, this.selectedFunction);
        this.selectedFunctionIndex = 0;
        this.codeEditor.setValue("# Nova função\n");
        this.isDirty = true;
        updateButtonStates();
    }

    private void deleteCurrentFunction() {
        if (this.currentEditingFunction == null || !"custom".equals(this.selectedFunction.source())) return;
        // Enviar packet de deleção (implementar se necessário)
        this.codeEditor.setValue("");
        this.currentEditingFunction = null;
        this.selectedFunction = null;
        this.isDirty = false;
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasSelection = this.currentEditingFunction != null;
        boolean isCustom = hasSelection && "custom".equals(this.selectedFunction.source());
        
        if (this.btnSave != null) this.btnSave.active = hasSelection;
        if (this.btnSaveReload != null) this.btnSaveReload.active = hasSelection;
        if (this.btnDelete != null) this.btnDelete.active = isCustom;
    }

    // ===================== RENDERING =====================

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null || this.font == null) return;

        updateLayout();

        // Background
        graphics.fill(0, 0, this.width, this.height, 0xDD000000);

        // Painéis (mesmo estilo dos outros studios)
        GuiLayoutUtils.drawPanel(graphics, this.listX - 8, this.listY - 8, this.listW + 16, this.listH + 16, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.editorX, this.editorY, this.editorW, this.editorH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.actionsX, this.actionsY, this.actionsW, this.actionsH, 0x001A2434, 0xFF4E6B8D);

        // Títulos
        graphics.drawString(this.font, "Resultados", this.listX, this.listY - 10, 0xFFF4F7FF);
        graphics.drawString(this.font, "Editor de Função", this.editorX + 8, this.editorY + 8, 0xFFF4F7FF);
        graphics.drawString(this.font, "Ações", this.actionsX + 8, this.actionsY + 8, 0xFFF4F7FF);

        // Lista
        renderFunctionList(graphics, mouseX, mouseY, partialTick);

        // Editor info
        if (this.currentEditingFunction != null) {
            String funcName = this.currentEditingFunction.toString();
            graphics.drawString(this.font, "Editando: " + funcName, this.editorX + 8, this.editorY + 16, 0xFFAABB88);
            if (this.isDirty) {
                graphics.drawString(this.font, "● Alterações pendentes", this.editorX + this.editorW - 150, this.editorY + 16, 0xFFFFAA44);
            }
        }

        // Scrollbar
        renderListScrollbar(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderFunctionList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.foundFunctions.isEmpty()) {
            graphics.drawCenteredString(this.font, "Nenhuma função encontrada",
                this.listX + this.listW / 2, this.listY + this.listH / 2, 0xFF888888);
            return;
        }

        graphics.enableScissor(this.listX, this.listY, this.listX + this.listW, this.listY + this.listH);

        for (int i = 0; i < this.foundFunctions.size(); i++) {
            int rowY = this.listY + (i * 24) - (int) this.listScrollAmount;
            if (rowY + 24 < this.listY || rowY > this.listY + this.listH) continue;

            FunctionEntry func = this.foundFunctions.get(i);
            boolean selected = (i == this.selectedFunctionIndex);
            boolean hovered = (mouseX >= this.listX && mouseX < this.listX + this.listW &&
                             mouseY >= rowY && mouseY < rowY + 24);

            int bgColor = selected ? 0xAA2B4E72 : (hovered ? 0x662B4E72 : 0x33202A36);
            graphics.fill(this.listX + 2, rowY, this.listX + this.listW - 2, rowY + 23, bgColor);

            // Ícone
            String icon = switch (func.source()) {
                case "custom" -> "📝";
                case "datapack" -> "📦";
                default -> "⚙️";
            };
            graphics.drawString(this.font, icon, this.listX + 4, rowY + 5, 0xFFAAAAAA);

            // Nome
            String name = func.path();
            int lastSlash = name.lastIndexOf('/');
            if (lastSlash >= 0) name = name.substring(lastSlash + 1);
            if (name.length() > 18) name = name.substring(0, 15) + "...";
            graphics.drawString(this.font, name, this.listX + 20, rowY + 5, selected ? 0xFF8BD3FF : 0xFFC0C8D0);

            // Namespace
            String ns = func.namespace();
            if (ns.length() > 12) ns = ns.substring(0, 9) + "...";
            graphics.drawString(this.font, ns, this.listX + 20, rowY + 15, 0xFF667788);
        }

        graphics.disableScissor();
    }

    private void renderListScrollbar(GuiGraphics graphics) {
        if (this.maxListScroll <= 0) return;

        int barX = this.listX + this.listW - 8;
        int barY = this.listY;
        int barW = 6;
        int barH = this.listH;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);

        double thumbH = Math.max(8, (double) barH / (this.maxListScroll + barH) * barH);
        double thumbY = barY + (this.maxListScroll > 0 ? (this.listScrollAmount / this.maxListScroll) * (barH - thumbH) : 0);

        int thumbColor = this.listScrollbarDragging ? 0xFF666666 : 0xFF555555;
        graphics.fill(barX, (int) thumbY, barX + barW, (int) (thumbY + thumbH), thumbColor);
    }

    // ===================== MOUSE =====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Clique na lista
            if (mouseX >= this.listX && mouseX < this.listX + this.listW &&
                mouseY >= this.listY && mouseY < this.listY + this.listH) {
                
                int index = (int) ((mouseY - this.listY + this.listScrollAmount) / 24);
                if (index >= 0 && index < this.foundFunctions.size()) {
                    this.selectedFunctionIndex = index;
                    this.selectedFunction = this.foundFunctions.get(index);
                    this.currentEditingFunction = this.selectedFunction.id();
                    
                    NetworkHandler.sendToServer(new RequestFunctionContentPacket(this.currentEditingFunction));
                    return true;
                }
            }

            // Scrollbar
            if (this.maxListScroll > 0 && isInsideListScrollbar(mouseX, mouseY)) {
                this.listScrollbarDragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.listScrollbarDragging) {
            updateListScrollbarDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.listScrollbarDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX >= this.listX && mouseX < this.listX + this.listW &&
            mouseY >= this.listY && mouseY < this.listY + this.listH) {
            if (this.maxListScroll > 0) {
                this.listScrollAmount = Mth.clamp(this.listScrollAmount - scrollY * 12.0D, 0.0D, this.maxListScroll);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    private boolean isInsideListScrollbar(double mouseX, double mouseY) {
        int barX = this.listX + this.listW - 8;
        int barY = this.listY;
        return mouseX >= barX && mouseX < barX + 10 && mouseY >= barY && mouseY < barY + this.listH;
    }

    private void updateListScrollbarDrag(double mouseY) {
        if (!this.listScrollbarDragging || this.maxListScroll <= 0) return;
        double ratio = (mouseY - this.listY) / this.listH;
        this.listScrollAmount = Mth.clamp(ratio * this.maxListScroll, 0.0D, this.maxListScroll);
    }

    // ===================== KEYBOARD =====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && (this.namespaceFilterBox.isFocused() || this.pathFilterBox.isFocused())) {
            requestSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
