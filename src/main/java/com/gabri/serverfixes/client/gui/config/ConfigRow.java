package com.gabri.serverfixes.client.gui.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.function.Consumer;

/**
 * Linha de configuração genérica.
 */
public abstract class ConfigRow<T> {
    protected static final int ROW_HEIGHT = 32;
    protected static final int TEXT_PADDING = 8;
    protected static final int TEXT_COLOR = 0xFFFFFFFF;

    protected final String key;
    protected final String label;
    protected T value;
    protected final Consumer<T> onSave;
    protected List<String> tooltipLines;

    protected int x, y, width, baseY;
    protected Font font = Minecraft.getInstance().font;
    protected AbstractWidget widget;

    public ConfigRow(String key, String label, T value, Consumer<T> onSave, List<String> tooltipLines) {
        this.key = key;
        this.label = label;
        this.value = value;
        this.onSave = onSave;
        this.tooltipLines = tooltipLines;
    }

    public abstract void createWidget(int widgetX, int widgetY, int widgetWidth);

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.drawString(font, label, x + TEXT_PADDING, (int)(baseY + (ROW_HEIGHT / 2f) - (font.lineHeight / 2f)), TEXT_COLOR);
        if (widget != null) widget.render(graphics, mouseX, mouseY, partialTicks);
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= baseY && mouseY <= baseY + ROW_HEIGHT;
    }

    public boolean isWidgetMouseOver(int mouseX, int mouseY) {
        return widget != null && widget.isMouseOver(mouseX, mouseY);
    }

    public void setPosition(int x, int y, int width) {
        this.x = x; this.width = width;
        this.y = y; 
        this.baseY = y;
        if (widget != null) createWidget(widget.getX(), y, widget.getWidth());
    }

    public int getX() { return x; }
    public int getY() { return baseY; }
    public int getBaseY() { return baseY; }
    public int getWidth() { return width; }
    public String getKey() { return key; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; if (onSave != null) onSave.accept(value); }
    public AbstractWidget getWidget() { return widget; }
    public List<String> getTooltipLines() { return tooltipLines; }
    public int getHeight() { return ROW_HEIGHT; }
    
    public abstract FieldType getFieldType();

    public enum FieldType {
        BOOLEAN("bool") { @Override public String format(Object v) { return String.valueOf(v instanceof Boolean && (Boolean)v); } @Override public String readInputValue(CompoundTag s, String k) { return String.valueOf(s != null && s.getBoolean(k)); } },
        STRING("string") { @Override public String format(Object v) { return v != null ? v.toString() : ""; } @Override public String readInputValue(CompoundTag s, String k) { return s != null && s.contains(k) ? s.getString(k) : ""; } },
        INTEGER("int") { @Override public String format(Object v) { return String.valueOf(parseIntSafe(v != null ? v.toString() : "", 0)); } @Override public String readInputValue(CompoundTag s, String k) { return s != null ? String.valueOf(s.getInt(k)) : "0"; } },
        LONG("long") { @Override public String format(Object v) { return String.valueOf(parseLongSafe(v != null ? v.toString() : "", 0L)); } @Override public String readInputValue(CompoundTag s, String k) { return s != null ? String.valueOf(s.getLong(k)) : "0"; } },
        SECONDS_TO_TICKS("int") { @Override public String format(Object v) { return String.valueOf(secondsToTicks(v != null ? v.toString() : "")); } @Override public String readInputValue(CompoundTag s, String k) { return s != null ? String.valueOf(s.getInt(k)) : "20"; } };
        private final String packetName; FieldType(String p) { packetName = p; }
        public abstract String format(Object v); public String readInputValue(CompoundTag s, String k) { return s != null && s.contains(k) ? s.getString(k) : ""; }
        public String packetName() { return packetName; }
        
        private static int parseIntSafe(String v, int fb) { try { return Integer.parseInt(v.replaceAll("[^0-9-]", "")); } catch (Exception e) { return fb; } }
        private static long parseLongSafe(String v, long fb) { try { return Long.parseLong(v.replaceAll("[^0-9-]", "")); } catch (Exception e) { return fb; } }
        private static int secondsToTicks(String v) { double s = 1.0D; try { s = Double.parseDouble(v.replace(',', '.')); } catch(Exception e){} return Math.max(1, (int)Math.round(s * 20.0D)); }
    }
}
