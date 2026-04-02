package com.gabri.serverfixes.client.gui.editor;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;

public final class SnbtFormatUtils {
    private SnbtFormatUtils() {
    }

    public static CompoundTag parseCompound(String raw) throws CommandSyntaxException {
        if (raw == null || raw.isBlank()) {
            return new CompoundTag();
        }
        return TagParser.parseTag(raw);
    }

    public static String prettyPrint(String raw) throws CommandSyntaxException {
        CompoundTag parsed = parseCompound(raw);
        return prettyPrint(parsed);
    }

    public static String prettyPrint(CompoundTag tag) {
        try {
            return new SnbtPrinterTagVisitor("  ", 0, new ArrayList<>()).visit(tag);
        } catch (Exception ignored) {
            return fallbackPrettyPrint(tag != null ? tag.toString() : "{}");
        }
    }

    private static String fallbackPrettyPrint(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 64);
        int indent = 0;
        boolean inString = false;
        char stringQuote = 0;
        boolean escaped = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == stringQuote) {
                    inString = false;
                    stringQuote = 0;
                }
                continue;
            }

            if (c == '\"' || c == '\'') {
                inString = true;
                stringQuote = c;
                out.append(c);
                continue;
            }

            if (c == '{' || c == '[') {
                out.append(c).append('\n');
                indent++;
                appendIndent(out, indent);
                continue;
            }

            if (c == '}' || c == ']') {
                out.append('\n');
                indent = Math.max(0, indent - 1);
                appendIndent(out, indent);
                out.append(c);
                continue;
            }

            if (c == ',') {
                out.append(c).append('\n');
                appendIndent(out, indent);
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }

    private static void appendIndent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }
}
