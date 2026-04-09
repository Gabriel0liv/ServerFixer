package com.gabri.serverfixes.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Componente reutilizável para linhas de configuração no Admin Panel.
 * Usa renderização manual para respeitar scissor/clipping.
 */
public class AdminConfigRow implements Renderable {
    private final String key;
    private final String label;
    private final FieldType type;
    private final int width;
    private final int height = 32;
    
    // Posição (atualizada pelo container)
    private int x, y;
    
    // Estado
    private boolean booleanValue;
    private String stringValue;
    
    // Callbacks
    private final Consumer<Boolean> onBooleanChange;
    private final Consumer<String> onStringChange;
    private final Runnable onReset;
    
    // Componentes internos (criados sob demanda)
    private CycleButton<Boolean> toggleButton;
    private EditBox inputBox;
    private net.minecraft.client.gui.components.Button resetButton;
    
    // Constantes de layout
    private static final int TOGGLE_WIDTH = 50;
    private static final int RESET_BUTTON_WIDTH = 20;
    private static final int RESET_BUTTON_GAP = 4;
    private static final int INPUT_MAX_WIDTH = 160;
    private static final int FORM_RIGHT_MARGIN = 16;
    private static final int LABEL_MARGIN = 10;
    
    // Cores
    private static final int COLOR_TEXT = 0xE3E6EE;
    private static final int COLOR_BG_EVEN = 0x18FFFFFF;
    private static final int COLOR_RESET_BUTTON = 0xFF3D5564;
    private static final int COLOR_RESET_BUTTON_HOVER = 0xFF4E6B8D;
    
    public enum FieldType {
        BOOLEAN,
        STRING,
        INTEGER,
        LONG,
        SECONDS_TO_TICKS
    }

    public AdminConfigRow(String key, String label, FieldType type, int width,
                          boolean initialValue, Consumer<Boolean> onBooleanChange, Runnable onReset) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.width = width;
        this.booleanValue = initialValue;
        this.stringValue = null;
        this.onBooleanChange = onBooleanChange;
        this.onStringChange = null;
        this.onReset = onReset;
    }

    public AdminConfigRow(String key, String label, FieldType type, int width,
                          String initialValue, Consumer<String> onStringChange, Runnable onReset) {
        this.key = key;
        this.label = label;
        this.type = type;
        this.width = width;
        this.booleanValue = false;
        this.stringValue = initialValue != null ? initialValue : "";
        this.onBooleanChange = null;
        this.onStringChange = onStringChange;
        this.onReset = onReset;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        
        // Atualiza posições dos widgets internos
        updateWidgetPositions();
    }
    
    private void updateWidgetPositions() {
        if (toggleButton != null) {
            int toggleX = x + width - FORM_RIGHT_MARGIN - RESET_BUTTON_GAP - TOGGLE_WIDTH;
            toggleButton.setX(toggleX);
            toggleButton.setY(y);
        }
        if (inputBox != null) {
            int resetX = x + width - FORM_RIGHT_MARGIN - RESET_BUTTON_WIDTH;
            int inputRight = resetX - RESET_BUTTON_GAP;
            int minInputX = x + LABEL_MARGIN + 80;
            int availableForInput = Math.max(60, inputRight - minInputX);
            int inputWidth = Math.min(INPUT_MAX_WIDTH, availableForInput);
            int inputX = inputRight - inputWidth;
            inputBox.setX(inputX);
            inputBox.setY(y);
        }
        if (resetButton != null) {
            int resetX = x + width - FORM_RIGHT_MARGIN - RESET_BUTTON_WIDTH;
            resetButton.setX(resetX);
            resetButton.setY(y);
        }
    }

    public int getX() {
        return x;
    }

    public String getKey() {
        return key;
    }

    public int getHeight() {
        return height;
    }

    public int getY() {
        return y;
    }

    // Getters de valores
    public boolean getBooleanValue() {
        return booleanValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public FieldType getType() {
        return type;
    }

    // Inicializa componentes internos
    public void initComponents(Font font) {
        if (type == FieldType.BOOLEAN && toggleButton == null) {
            int toggleX = x + width - FORM_RIGHT_MARGIN - RESET_BUTTON_GAP - TOGGLE_WIDTH;
            toggleButton = CycleButton.builder((Boolean value) -> 
                    Component.literal(value ? "ON" : "OFF"))
                .withValues(true, false)
                .withInitialValue(booleanValue)
                .displayOnlyValue()
                .create(toggleX, y, TOGGLE_WIDTH, 20, Component.empty(), (btn, val) -> {
                    this.booleanValue = val;
                    if (onBooleanChange != null) onBooleanChange.accept(val);
                });
        } else if (type != FieldType.BOOLEAN && inputBox == null) {
            int resetX = x + width - FORM_RIGHT_MARGIN - RESET_BUTTON_WIDTH;
            int inputRight = resetX - RESET_BUTTON_GAP;
            int minInputX = x + LABEL_MARGIN + 80;
            int availableForInput = Math.max(60, inputRight - minInputX);
            int inputWidth = Math.min(INPUT_MAX_WIDTH, availableForInput);
            int inputX = inputRight - inputWidth;
            
            inputBox = new com.gabri.serverfixes.client.gui.editor.SelectableEditBox(
                font, inputX, y, inputWidth, 20, Component.literal(label));
            
            if (type == FieldType.INTEGER || type == FieldType.LONG) {
                inputBox.setFilter(s -> s.matches("\\d*"));
            } else if (type == FieldType.SECONDS_TO_TICKS) {
                inputBox.setFilter(s -> s.matches("\\d*"));
            }
            
            inputBox.setValue(stringValue != null ? stringValue : "");
            inputBox.setResponder(newValue -> {
                this.stringValue = newValue;
                if (onStringChange != null) onStringChange.accept(newValue);
            });
        }
        
        // Botão reset
        if (resetButton == null) {
            int resetX = x + width - FORM_RIGHT_MARGIN - RESET_BUTTON_WIDTH;
            resetButton = net.minecraft.client.gui.components.Button.builder(
                Component.literal("↺"), btn -> {
                    if (onReset != null) onReset.run();
                })
                .bounds(resetX, y, RESET_BUTTON_WIDTH, 20)
                .build();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        render(graphics, null, mouseX, mouseY, partialTick);
    }
    
    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
        if (font == null) return;
        
        // Background alternado (simplificado)
        // Label
        graphics.drawString(font, label, x + LABEL_MARGIN, y + 4, COLOR_TEXT);
        
        // Renderiza componentes internos
        if (toggleButton != null) {
            toggleButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (inputBox != null) {
            inputBox.render(graphics, mouseX, mouseY, partialTick);
        }
        if (resetButton != null) {
            resetButton.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (toggleButton != null && toggleButton.isMouseOver(mouseX, mouseY)) {
            toggleButton.onPress();
            return true;
        }
        if (inputBox != null && inputBox.isMouseOver(mouseX, mouseY)) {
            inputBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (resetButton != null && resetButton.isMouseOver(mouseX, mouseY)) {
            resetButton.onPress();
            return true;
        }
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (inputBox != null) {
            inputBox.mouseReleased(mouseX, mouseY, button);
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputBox != null && inputBox.isFocused()) {
            return inputBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public void setFocused(boolean focused) {
        if (inputBox != null && !focused) {
            inputBox.setFocused(false);
        }
    }

    public void updateValues(boolean booleanValue, String stringValue) {
        this.booleanValue = booleanValue;
        this.stringValue = stringValue;
        
        if (toggleButton != null) {
            toggleButton.setValue(booleanValue);
        }
        if (inputBox != null && stringValue != null) {
            inputBox.setValue(stringValue);
        }
    }
}
