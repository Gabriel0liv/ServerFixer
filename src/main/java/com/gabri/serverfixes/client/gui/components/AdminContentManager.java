package com.gabri.serverfixes.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;

import java.util.ArrayList;
import java.util.List;

/**
 * Gerenciador de conteúdo para Admin Panel.
 * Mantém lista de rows e headers, gerencia posicionamento e renderização.
 */
public class AdminContentManager {
    private final List<Renderable> components = new ArrayList<>();
    private final List<AdminConfigRow> configRows = new ArrayList<>();
    private final List<AdminSectionHeader> headers = new ArrayList<>();
    private int currentY = 0;
    private int contentWidth;
    private Font font;

    public AdminContentManager(int contentWidth, Font font) {
        this.contentWidth = contentWidth;
        this.font = font;
    }

    public void clear() {
        components.clear();
        configRows.clear();
        headers.clear();
        currentY = 0;
    }

    public void setFont(Font font) {
        this.font = font;
    }

    public int getCurrentY() {
        return currentY;
    }

    public void setCurrentY(int y) {
        this.currentY = y;
    }

    // Adiciona header de seção
    public void addSection(String title) {
        AdminSectionHeader header = new AdminSectionHeader(title, contentWidth);
        header.setPosition(0, currentY);
        headers.add(header);
        components.add(header);
        currentY += header.getHeight() + 4;
    }

    // Adiciona row booleana
    public void addBooleanRow(String key, String label, boolean initialValue,
                              java.util.function.Consumer<Boolean> onChange, Runnable onReset) {
        AdminConfigRow row = new AdminConfigRow(key, label, AdminConfigRow.FieldType.BOOLEAN,
                contentWidth, initialValue, onChange, onReset);
        row.initComponents(font); // Inicializa widgets PRIMEIRO
        row.setPosition(0, currentY); // Depois posiciona (atualiza widgets)
        configRows.add(row);
        components.add(row);
        currentY += row.getHeight();
    }

    // Adiciona row de string
    public void addStringRow(String key, String label, String initialValue,
                             java.util.function.Consumer<String> onChange, Runnable onReset) {
        AdminConfigRow row = new AdminConfigRow(key, label, AdminConfigRow.FieldType.STRING,
                contentWidth, initialValue, onChange, onReset);
        row.initComponents(font);
        row.setPosition(0, currentY);
        configRows.add(row);
        components.add(row);
        currentY += row.getHeight();
    }

    // Adiciona row de inteiro
    public void addIntegerRow(String key, String label, int initialValue,
                              java.util.function.Consumer<String> onChange, Runnable onReset) {
        AdminConfigRow row = new AdminConfigRow(key, label, AdminConfigRow.FieldType.INTEGER,
                contentWidth, String.valueOf(initialValue), onChange, onReset);
        row.initComponents(font);
        row.setPosition(0, currentY);
        configRows.add(row);
        components.add(row);
        currentY += row.getHeight();
    }

    // Adiciona row de long/segundos
    public void addLongRow(String key, String label, long initialValue,
                           java.util.function.Consumer<String> onChange, Runnable onReset) {
        AdminConfigRow row = new AdminConfigRow(key, label, AdminConfigRow.FieldType.LONG,
                contentWidth, String.valueOf(initialValue), onChange, onReset);
        row.initComponents(font);
        row.setPosition(0, currentY);
        configRows.add(row);
        components.add(row);
        currentY += row.getHeight();
    }

    // Espaçamento
    public void addSpacing(int pixels) {
        currentY += pixels;
    }

    // Retorna altura total do conteúdo
    public int getTotalHeight() {
        return currentY;
    }

    // Lista de config rows para acesso direto
    public List<AdminConfigRow> getConfigRows() {
        return configRows;
    }

    // Lista de headers
    public List<AdminSectionHeader> getHeaders() {
        return headers;
    }

    // Lista de todos os componentes
    public List<Renderable> getComponents() {
        return components;
    }
}
