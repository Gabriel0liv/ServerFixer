package com.gabri.serverfixes.network;

import com.gabri.serverfixes.commands.SurgicalReloadHandler;
import com.gabri.serverfixes.function.FunctionStudioLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet client->server para salvar/criar uma função.
 */
public class SaveFunctionPacket {
    private final ResourceLocation functionId;
    private final List<String> content;
    private final boolean reloadAfterSave;

    public SaveFunctionPacket(ResourceLocation functionId, List<String> content, boolean reloadAfterSave) {
        this.functionId = functionId;
        this.content = content;
        this.reloadAfterSave = reloadAfterSave;
    }

    public SaveFunctionPacket(FriendlyByteBuf buf) {
        this.functionId = buf.readResourceLocation();
        this.content = new ArrayList<>();
        buf.readList(b -> this.content.add(b.readUtf(32767)));
        this.reloadAfterSave = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.functionId);
        buf.writeCollection(this.content, (b, s) -> b.writeUtf(s, 32767));
        buf.writeBoolean(this.reloadAfterSave);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) return;

            var server = sender.getServer();
            if (server == null) return;

            boolean success = FunctionStudioLogic.saveFunction(server, this.functionId, this.content);

            if (success) {
                if (this.reloadAfterSave) {
                    SurgicalReloadHandler.reloadFunctions(server);
                    sender.sendSystemMessage(Component.literal("§a[Function Studio] Função salva e recarregada: " + this.functionId));
                } else {
                    sender.sendSystemMessage(Component.literal("§a[Function Studio] Função salva: " + this.functionId));
                }
            } else {
                sender.sendSystemMessage(Component.literal("§c[Function Studio] Falha ao salvar função: " + this.functionId));
            }
        });
        context.setPacketHandled(true);
    }
}
