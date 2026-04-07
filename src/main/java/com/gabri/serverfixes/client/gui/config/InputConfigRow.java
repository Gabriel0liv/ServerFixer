package com.gabri.serverfixes.client.gui.config;

import com.gabri.serverfixes.client.gui.editor.SelectableEditBox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Linha de configuração Input (texto/número).
 */
public class InputConfigRow extends ConfigRow<String> {
    private static final int INPUT_MIN_WIDTH = 100;
    private static final int INPUT_MAX_WIDTH = 160;

    public InputConfigRow(String key, String label, String value, String defaultValue,
                          Consumer<String> onSave, List<String> tooltipLines) {
        super(key, label, value, onSave, tooltipLines);
    }

    @Override
    public void createWidget(int widgetX, int widgetY, int widgetWidth) {
        int width = Math.max(INPUT_MIN_WIDTH, Math.min(INPUT_MAX_WIDTH, widgetWidth));
        EditBox box = new SelectableEditBox(font, widgetX, widgetY, width, 20, Component.literal(label));
        box.setResponder(v -> {
            this.value = v;
            if (onSave != null) onSave.accept(v);
        });
        box.setValue(value);
        this.widget = box;
    }

    /** Factory para input numérico inteiro */
    public static InputConfigRow integer(String key, String label, int value, int defaultValue,
                                         Consumer<Integer> onSave, List<String> tooltipLines) {
        return new InputConfigRow(key, label, String.valueOf(value), String.valueOf(defaultValue),
                v -> { try { onSave.accept(Integer.parseInt(v.replaceAll("[^0-9-]", ""))); } catch(Exception e){} },
                tooltipLines) {
            @Override
            public void createWidget(int widgetX, int widgetY, int widgetWidth) {
                super.createWidget(widgetX, widgetY, widgetWidth);
                if (widget instanceof EditBox eb) {
                    eb.setFilter(InputConfigRow::isValidIntegerInput);
                }
            }
            @Override
            public FieldType getFieldType() { return FieldType.INTEGER; }
        };
    }

    /** Factory para input de segundos (converte para ticks) */
    public static InputConfigRow seconds(String key, String label, int ticks, int defaultTicks,
                                         Consumer<Integer> onSave, List<String> tooltipLines) {
        double seconds = Math.max(1, ticks) / 20.0;
        double defaultSeconds = Math.max(1, defaultTicks) / 20.0;
        
        return new InputConfigRow(key, label,
                String.valueOf(seconds), String.valueOf(defaultSeconds),
                v -> {
                    try {
                        double s = Double.parseDouble(v.replace(',', '.'));
                        if (s >= 0.05) onSave.accept((int) Math.round(s * 20));
                    } catch(Exception e){}
                },
                tooltipLines) {
            @Override
            public void createWidget(int widgetX, int widgetY, int widgetWidth) {
                super.createWidget(widgetX, widgetY, widgetWidth);
                if (widget instanceof EditBox eb) {
                    eb.setFilter(InputConfigRow::isValidSecondsInput);
                }
            }
            @Override
            public FieldType getFieldType() { return FieldType.SECONDS_TO_TICKS; }
        };
    }

    private static boolean isValidIntegerInput(String v) {
        if (v == null || v.isEmpty() || "-".equals(v)) return true;
        try { Long.parseLong(v); return true; } catch (Exception ignored) { return false; }
    }

    private static boolean isValidSecondsInput(String v) {
        if (v == null || v.isEmpty()) return true;
        String n = v.replace(',', '.');
        if (".".equals(n)) return true;
        try { return Double.parseDouble(n) >= 0.05D; } catch (Exception ignored) { return false; }
    }

    @Override
    public FieldType getFieldType() {
        return FieldType.STRING;
    }
}
