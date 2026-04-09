package com.gabri.serverfixes.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;

/**
 * Componente reutilizável para headers de seção no Admin Panel.
 * Ex: "§6§lFarm & Charm", "§6§lVinery"
 */
public class AdminSectionHeader implements Renderable {
    private final String title;
    private final int width;
    private final int height = 24;
    private int x, y;
    
    private static final int COLOR_TITLE = 0xFFFFD700;
    private static final int COLOR_LINE = 0x44FFD700;
    private static final int LABEL_MARGIN = 10;

    public AdminSectionHeader(String title, int width) {
        this.title = title;
        this.width = width;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getHeight() {
        return height;
    }

    public int getY() {
        return y;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        render(graphics, null, mouseX, mouseY, partialTick);
    }
    
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        if (font == null) return;
        
        // Remove formatação para extrair texto limpo
        String plainTitle = title.replace("§6§l", "").replace("§r", "");
        
        // Desenha título
        graphics.drawString(font, plainTitle, x + LABEL_MARGIN, y + 4, COLOR_TITLE);
        
        // Linha decorativa abaixo
        graphics.fill(x, y + 18, x + width, y + 19, COLOR_LINE);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false; // Header não é clicável
    }
}
