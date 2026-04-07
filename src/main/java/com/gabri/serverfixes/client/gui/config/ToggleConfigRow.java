package com.gabri.serverfixes.client.gui.config;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Linha de configuração Toggle (ON/OFF).
 */
public class ToggleConfigRow extends ConfigRow<Boolean> {
    private static final int TOGGLE_WIDTH = 60;

    public ToggleConfigRow(String key, String label, boolean value, boolean defaultValue,
                           Consumer<Boolean> onSave, List<String> tooltipLines) {
        super(key, label, value, onSave, tooltipLines);
    }

    @Override
    public void createWidget(int widgetX, int widgetY, int widgetWidth) {
        this.widget = CycleButton.<Boolean>builder(v -> v ? Component.literal("ON") : Component.literal("OFF"))
                .withValues(true, false)
                .withInitialValue(this.value) // Use current value
                .displayOnlyValue()
                .create(widgetX, widgetY, TOGGLE_WIDTH, 20, Component.empty(),
                        (btn, val) -> {
                            this.value = val;
                            if (onSave != null) onSave.accept(val);
                        });
    }

    @Override
    public FieldType getFieldType() {
        return FieldType.BOOLEAN;
    }
}
