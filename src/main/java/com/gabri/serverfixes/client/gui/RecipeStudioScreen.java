package com.gabri.serverfixes.client.gui;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.RequestRecipeListPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public class RecipeStudioScreen extends Screen {
    private final List<RecipeBasicDTO> foundRecipes = new ArrayList<>();
    private int selectedRecipeIndex = -1;
    private RecipeBasicDTO selectedRecipe = null;

    // Busca
    private EditBox searchBox;
    private Button searchButton;

    // Lista de resultados
    private double listScrollAmount = 0.0D;
    private int maxListScroll = 0;
    private boolean listScrollbarDragging = false;

    // Layout
    private int searchX, searchY, searchW, searchH;
    private int listX, listY, listW, listH;
    private int recipeListX, recipeListY, recipeListW, recipeListH;
    private int previewX, previewY, previewW, previewH;
    private int actionsX, actionsY, actionsW, actionsH;

    public RecipeStudioScreen() {
        super(Component.literal("Recipe Studio"));
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft == null || this.font == null) return;

        this.clearWidgets();
        this.foundRecipes.clear();
        this.selectedRecipeIndex = -1;
        this.selectedRecipe = null;
        this.listScrollAmount = 0.0D;
        this.maxListScroll = 0;

        updateLayout();

        // Busca
        this.searchBox = new EditBox(this.font, this.searchX, this.searchY, this.searchW - 24, this.searchH, Component.literal("ID do item resultado"));
        this.searchBox.setMaxLength(128);
        this.searchBox.setHint(Component.literal("Ex: minecraft:diamond_sword"));
        this.addRenderableWidget(this.searchBox);

        // Botão Buscar (ícone de lupa)
        this.searchButton = Button.builder(Component.literal("🔍"), btn -> requestSearch())
            .bounds(this.searchX + this.searchW - 22, this.searchY, 22, this.searchH)
            .build();
        this.addRenderableWidget(this.searchButton);

        // Enter na busca
        this.searchBox.setResponder(val -> {
            if (val.length() >= 3) {
                // Opcional: auto-search após 3 chars (desativado para evitar spam)
            }
        });
    }

    private void updateLayout() {
        int padding = 8;
        int gap = 6;

        // Painel esquerdo (Search + Lista)
        this.listX = padding;
        this.listY = 28;
        this.listW = Math.min(220, this.width / 4);
        this.listH = this.height - 64;

        // Search (NO TOPO da lista)
        this.searchX = this.listX;
        this.searchY = this.listY;
        this.searchW = this.listW;
        this.searchH = 20;

        // Área da lista (começa DEPOIS do search)
        this.recipeListX = this.listX;
        this.recipeListY = this.searchY + this.searchH + gap;
        this.recipeListW = this.listW;
        this.recipeListH = this.listH - this.searchH - gap;

        // Preview (Centro)
        this.previewX = this.listX + this.listW + gap;
        this.previewY = 28;
        this.previewW = (this.width - this.listW - gap * 2 - padding * 2) / 2;
        this.previewH = this.height - 64;

        // Ações (Direita)
        this.actionsX = this.previewX + this.previewW + gap;
        this.actionsY = 28;
        this.actionsW = this.previewW;
        this.actionsH = this.height - 64;
    }

    private void requestSearch() {
        String query = this.searchBox != null ? this.searchBox.getValue().trim() : "";
        if (query.isEmpty()) return;

        // Busca por item no resultado
        NetworkHandler.sendToServer(new RequestRecipeListPacket("", "", query));
    }

    public void applyRecipeListFromServer(List<RecipeBasicDTO> recipes) {
        this.foundRecipes.clear();
        if (recipes != null) {
            for (RecipeBasicDTO dto : recipes) {
                if (dto != null) {
                    this.foundRecipes.add(dto);
                }
            }
        }
        this.listScrollAmount = 0.0D;
        this.maxListScroll = Math.max(0, this.foundRecipes.size() * 24 - this.recipeListH);
        this.selectedRecipeIndex = -1;
        this.selectedRecipe = null;
    }

    // ===================== RENDERING =====================

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft == null || this.font == null) return;

        updateLayout();

        // Background
        graphics.fill(0, 0, this.width, this.height, 0xDD000000);

        // Painéis
        GuiLayoutUtils.drawPanel(graphics, this.listX - 8, this.listY - 8, this.listW + 16, this.listH + 16, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.previewX, this.previewY, this.previewW, this.previewH, 0x001A2434, 0xFF4E6B8D);
        GuiLayoutUtils.drawPanel(graphics, this.actionsX, this.actionsY, this.actionsW, this.actionsH, 0x001A2434, 0xFF4E6B8D);

        // Títulos
        graphics.drawString(this.font, "Preview da Receita", this.previewX + 8, this.previewY + 8, 0xFFF4F7FF);
        graphics.drawString(this.font, "Ações", this.actionsX + 8, this.actionsY + 8, 0xFFF4F7FF);

        // Lista
        renderRecipeList(graphics, mouseX, mouseY, partialTick);

        // Preview
        renderRecipePreview(graphics);

        // Ações
        renderActionPanel(graphics);

        // Scrollbar
        renderListScrollbar(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRecipeList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.foundRecipes.isEmpty()) {
            graphics.drawCenteredString(this.font, "Nenhuma receita encontrada",
                this.recipeListX + this.recipeListW / 2, this.recipeListY + this.recipeListH / 2, 0xFF888888);
            return;
        }

        graphics.enableScissor(this.recipeListX, this.recipeListY, this.recipeListX + this.recipeListW, this.recipeListY + this.recipeListH);

        for (int i = 0; i < this.foundRecipes.size(); i++) {
            int rowY = this.recipeListY + (i * 24) - (int) this.listScrollAmount;
            if (rowY + 24 < this.recipeListY || rowY > this.recipeListY + this.recipeListH) continue;

            RecipeBasicDTO recipe = this.foundRecipes.get(i);
            boolean selected = (i == this.selectedRecipeIndex);
            boolean hovered = (mouseX >= this.recipeListX && mouseX < this.recipeListX + this.recipeListW &&
                             mouseY >= rowY && mouseY < rowY + 24);

            int bgColor = selected ? 0xAA2B4E72 : (hovered ? 0x662B4E72 : 0x33202A36);
            graphics.fill(this.recipeListX + 2, rowY, this.recipeListX + this.recipeListW - 2, rowY + 23, bgColor);

            // Ícone
            ItemStack result = recipe.result();
            if (!result.isEmpty() && result.getItem() != Items.AIR) {
                graphics.renderFakeItem(result, this.recipeListX + 4, rowY + 4);
            }

            // Nome
            String name = recipe.id().toString();
            int lastSlash = name.lastIndexOf('/');
            if (lastSlash >= 0) name = name.substring(lastSlash + 1);
            if (name.length() > 24) name = name.substring(0, 21) + "...";

            graphics.drawString(this.font, name, this.recipeListX + 28, rowY + 5, selected ? 0xFF8BD3FF : 0xFFC0C8D0);

            // Tipo
            String type = recipe.type().getPath();
            graphics.drawString(this.font, type, this.recipeListX + 28, rowY + 15, 0xFF667788);
        }

        graphics.disableScissor();
    }

    private void renderRecipePreview(GuiGraphics graphics) {
        if (this.selectedRecipe == null) {
            graphics.drawCenteredString(this.font, "Selecione uma receita",
                this.previewX + this.previewW / 2, this.previewY + this.previewH / 2, 0xFF888888);
            return;
        }

        RecipeBasicDTO recipe = this.selectedRecipe;
        int cx = this.previewX + this.previewW / 2;
        int cy = this.previewY + 40;

        // Tipo da receita
        String type = recipe.type().getPath();
        graphics.drawCenteredString(this.font, "Tipo: " + type, cx, cy, 0xFF8BD3FF);
        cy += 16;

        // Determina qual bloco faz a receita
        String stationName = getStationName(recipe.type());
        if (stationName != null) {
            graphics.drawCenteredString(this.font, "Estação: " + stationName, cx, cy, 0xFFAABB88);
            cy += 16;
        }

        // Preview dos ingredientes (depende do tipo)
        if (type.contains("crafting")) {
            renderCraftingPreview(graphics, recipe, cx, cy);
        } else if (type.contains("smelting") || type.contains("smoking") || type.contains("blasting")) {
            renderFurnacePreview(graphics, recipe, cx, cy);
        } else if (type.contains("stonecutting")) {
            renderStonecuttingPreview(graphics, recipe, cx, cy);
        } else if (type.contains("smithing")) {
            renderSmithingPreview(graphics, recipe, cx, cy);
        } else if (type.contains("campfire")) {
            renderCampfirePreview(graphics, recipe, cx, cy);
        } else {
            renderGenericPreview(graphics, recipe, cx, cy);
        }
    }

    private String getStationName(ResourceLocation type) {
        if (type == null) return null;
        String ns = type.getNamespace();
        String path = type.getPath();

        return switch (path) {
            case "crafting_shaped", "crafting_shapeless" -> "Mesa de Crafting";
            case "smelting" -> "Fornalha";
            case "blasting" -> "Alto-Forno";
            case "smoking" -> "Defumador";
            case "campfire_cooking" -> "Fogueira";
            case "stonecutting" -> "Cortador de Pedras";
            case "smithing" -> "Bancada de Ferraria";
            case "smithing_transform", "smithing_trim" -> "Bancada de Ferraria (1.20)";
            // Create
            case "mixing" -> "Misturador (Basin)";
            case "milling" -> "Moinho (Millstone)";
            case "pressing" -> "Prensa (Press)";
            case "crushing" -> "Triturador (Crusher)";
            case "compacting" -> "Compactador";
            case "fan_washing", "fan_smoking", "fan_blasting" -> "Ventilador (Fan)";
            case "deploying" -> "Implantador (Deployer)";
            case "mechanical_crafting" -> "Mesa Mecânica";
            case "splashing" -> "Lavador (Splashing)";
            // Farmer's Delight
            case "cooking" -> "Panela (Cooking Pot)";
            case "cutting" -> "Tábua de Corte";
            case "decomposition" -> "Compostagem";
            // Vinery
            case "apple_fermentation" -> "Barril de Fermentação";
            case "wine_fermentation" -> "Barril de Vinho";
            default -> null;
        };
    }

    private void renderCraftingPreview(GuiGraphics graphics, RecipeBasicDTO recipe, int cx, int cy) {
        ItemStack result = recipe.result();
        if (!result.isEmpty()) {
            graphics.renderFakeItem(result, cx - 8, cy);
        }
        cy += 32;
        graphics.drawCenteredString(this.font, "Grid de craft em breve", cx, cy, 0xFF667788);
    }

    private void renderFurnacePreview(GuiGraphics graphics, RecipeBasicDTO recipe, int cx, int cy) {
        ItemStack result = recipe.result();
        if (!result.isEmpty()) {
            graphics.drawString(this.font, "?", cx - 40, cy + 4, 0xFF888888);
            graphics.drawString(this.font, "→", cx - 12, cy + 4, 0xFFAAAAAA);
            graphics.renderFakeItem(result, cx + 8, cy);
        }
    }

    private void renderStonecuttingPreview(GuiGraphics graphics, RecipeBasicDTO recipe, int cx, int cy) {
        ItemStack result = recipe.result();
        if (!result.isEmpty()) {
            graphics.drawString(this.font, "?", cx - 40, cy + 4, 0xFF888888);
            graphics.drawString(this.font, "🔪 →", cx - 16, cy + 4, 0xFFAAAAAA);
            graphics.renderFakeItem(result, cx + 12, cy);
        }
    }

    private void renderSmithingPreview(GuiGraphics graphics, RecipeBasicDTO recipe, int cx, int cy) {
        ItemStack result = recipe.result();
        if (!result.isEmpty()) {
            graphics.drawString(this.font, "? + ?", cx - 40, cy + 4, 0xFF888888);
            graphics.drawString(this.font, "→", cx + 8, cy + 4, 0xFFAAAAAA);
            graphics.renderFakeItem(result, cx + 24, cy);
        }
    }

    private void renderCampfirePreview(GuiGraphics graphics, RecipeBasicDTO recipe, int cx, int cy) {
        ItemStack result = recipe.result();
        if (!result.isEmpty()) {
            graphics.drawString(this.font, "?", cx - 40, cy + 4, 0xFF888888);
            graphics.drawString(this.font, "🔥 →", cx - 16, cy + 4, 0xFFAAAAAA);
            graphics.renderFakeItem(result, cx + 12, cy);
        }
    }

    private void renderGenericPreview(GuiGraphics graphics, RecipeBasicDTO recipe, int cx, int cy) {
        ItemStack result = recipe.result();
        if (!result.isEmpty()) {
            graphics.renderFakeItem(result, cx - 8, cy);
        }
        cy += 32;
        graphics.drawCenteredString(this.font, "Preview detalhado", cx, cy, 0xFF667788);
        graphics.drawCenteredString(this.font, "indisponível p/ este tipo", cx, cy + 12, 0xFF667788);
    }

    private void renderActionPanel(GuiGraphics graphics) {
        if (this.selectedRecipe == null) {
            graphics.drawString(this.font, "Nenhuma receita selecionada", this.actionsX + 8, this.actionsY + 28, 0xFF888888);
            return;
        }

        int padding = this.actionsX + 8;
        int contentW = this.actionsW - 16;
        int y = this.actionsY + 28;

        // Título: Mod
        String modName = this.selectedRecipe.id().getNamespace();
        graphics.drawString(this.font, "Mod: " + modName, padding, y, 0xFFAABB88);
        y += 16;

        // Tipo
        graphics.drawString(this.font, "Tipo: " + this.selectedRecipe.type().getPath(), padding, y, 0xFF667788);
        y += 16;

        // ID completo
        graphics.drawString(this.font, "ID:", padding, y, 0xFF8BD3FF);
        y += 12;
        String idStr = this.selectedRecipe.id().toString();
        graphics.drawString(this.font, this.font.plainSubstrByWidth(idStr, contentW - 8), padding + 4, y, 0xFFD4E0F0);
        y += 20;

        // Resultado
        ItemStack result = this.selectedRecipe.result();
        if (!result.isEmpty() && result.getItem() != Items.AIR) {
            graphics.drawString(this.font, "Resultado:", padding, y, 0xFF8BD3FF);
            y += 16;
            graphics.renderFakeItem(result, padding + 4, y);
            String resultName = result.getHoverName().getString();
            graphics.drawString(this.font, resultName, padding + 24, y + 2, 0xFFD4E0F0);
            y += 24;

            ResourceLocation itemRl = ForgeRegistries.ITEMS.getKey(result.getItem());
            if (itemRl != null) {
                graphics.drawString(this.font, itemRl.toString(), padding + 4, y, 0xFF667788);
                y += 16;
            }
        }

        // Separador
        graphics.fill(padding, y, padding + contentW, y + 1, 0x44FFFFFF);
        y += 8;

        // Botão Deletar
        graphics.fill(padding, y, padding + contentW, y + 22, 0xAA552222);
        graphics.drawCenteredString(this.font, "Deletar Receita", padding + contentW / 2, y + 7, 0xFFFF5555);
        y += 30;

        // Botão Editar
        graphics.fill(padding, y, padding + contentW, y + 22, 0xAA225522);
        graphics.drawCenteredString(this.font, "Editar Receita", padding + contentW / 2, y + 7, 0xFF55FF55);
    }

    // ===================== SCROLLBAR =====================

    private void renderListScrollbar(GuiGraphics graphics) {
        if (this.maxListScroll <= 0) return;

        int barX = this.recipeListX + this.recipeListW - 8;
        int barY = this.recipeListY;
        int barW = 6;
        int barH = this.recipeListH;

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
            if (mouseX >= this.recipeListX && mouseX < this.recipeListX + this.recipeListW &&
                mouseY >= this.recipeListY && mouseY < this.recipeListY + this.recipeListH) {

                int index = (int) ((mouseY - this.recipeListY + this.listScrollAmount) / 24);
                if (index >= 0 && index < this.foundRecipes.size()) {
                    this.selectedRecipeIndex = index;
                    this.selectedRecipe = this.foundRecipes.get(index);
                    return true;
                }
            }

            // Scrollbar da lista
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
        if (mouseX >= this.recipeListX && mouseX < this.recipeListX + this.recipeListW &&
            mouseY >= this.recipeListY && mouseY < this.recipeListY + this.recipeListH) {
            if (this.maxListScroll > 0) {
                this.listScrollAmount = Mth.clamp(this.listScrollAmount - scrollY * 12.0D, 0.0D, this.maxListScroll);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    private boolean isInsideListScrollbar(double mouseX, double mouseY) {
        int barX = this.recipeListX + this.recipeListW - 8;
        int barY = this.recipeListY;
        return mouseX >= barX && mouseX < barX + 10 && mouseY >= barY && mouseY < barY + this.recipeListH;
    }

    private void updateListScrollbarDrag(double mouseY) {
        if (!this.listScrollbarDragging || this.maxListScroll <= 0) return;
        double ratio = (mouseY - this.recipeListY) / this.recipeListH;
        this.listScrollAmount = Mth.clamp(ratio * this.maxListScroll, 0.0D, this.maxListScroll);
    }

    // ===================== KEYBOARD =====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && this.searchBox.isFocused()) { // Enter
            requestSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
