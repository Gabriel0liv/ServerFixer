package com.gabri.serverfixes.client.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * DTO básico para representar uma função .mcfunction na lista do Function Studio.
 */
public record FunctionEntry(
    ResourceLocation id,
    String namespace,
    String path,
    int lineCount,
    String source // "datapack", "mod", ou "custom"
) {
    public FunctionEntry(FriendlyByteBuf buf) {
        this(
            buf.readResourceLocation(),
            buf.readUtf(32767),
            buf.readUtf(32767),
            buf.readInt(),
            buf.readUtf(32767)
        );
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.id);
        buf.writeUtf(this.namespace, 32767);
        buf.writeUtf(this.path, 32767);
        buf.writeInt(this.lineCount);
        buf.writeUtf(this.source, 32767);
    }
}
