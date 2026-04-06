package com.gabri.serverfixes.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Packet client->server para salvar alterações de tag de entidades no datapack gerado.
 */
public class SaveEntityTagPacket {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LogManager.getLogger("ServerFixes/EntityTagStudio");

    private final String tagId;
    private final List<String> entityIds;

    public SaveEntityTagPacket(String tagId, List<String> entityIds) {
        this.tagId = tagId;
        this.entityIds = entityIds;
    }

    public SaveEntityTagPacket(FriendlyByteBuf buf) {
        this.tagId = buf.readUtf(32767);
        this.entityIds = buf.readList(b -> b.readUtf(32767));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.tagId, 32767);
        buf.writeCollection(this.entityIds, (b, s) -> b.writeUtf(s, 32767));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) return;

            boolean success = saveTagToJson(sender.server, this.tagId, this.entityIds);

            // Atualiza o overlay em memória para disponibilidade imediata
            if (success) {
                com.gabri.serverfixes.loot.EntityTagOverlayManager.setTag(this.tagId, this.entityIds);

                // Envia lista atualizada de tags ao cliente
                var allTags = com.gabri.serverfixes.loot.EntityTagOverlayManager.getAllTags();
                NetworkHandler.sendToPlayer(sender, new SPSendEntityTagsPacket(allTags));

                sender.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§a[Entity Tag Studio] Tag '" + this.tagId + "' salva! §eRequer restart para funcionalidade funcionar."));
            } else {
                sender.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§c[Entity Tag Studio] Falha ao salvar tag: " + this.tagId));
            }
        });
        context.setPacketHandled(true);
    }

    private static boolean saveTagToJson(MinecraftServer server, String tagId, List<String> entityIds) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(tagId);
            if (rl == null) return false;

            Path root = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("serverfixes_generated");
            Path tagPath = root.resolve("data").resolve(rl.getNamespace())
                    .resolve("tags").resolve("entity_types").resolve(rl.getPath() + ".json");

            Files.createDirectories(tagPath.getParent());
            ensurePackMcmeta(root);

            JsonObject json = new JsonObject();
            JsonArray values = new JsonArray();
            for (String entityId : entityIds) {
                values.add(entityId);
            }
            json.add("values", values);

            Files.writeString(tagPath, GSON.toJson(json));
            return true;
        } catch (Throwable t) {
            LOGGER.error("[EntityTagStudio] Falha ao salvar tag {}: {}", tagId, t.getMessage());
            return false;
        }
    }

    private static void ensurePackMcmeta(Path datapackRoot) {
        Path packMcmeta = datapackRoot.resolve("pack.mcmeta");
        if (Files.exists(packMcmeta)) return;
        try {
            JsonObject pack = new JsonObject();
            JsonObject desc = new JsonObject();
            desc.addProperty("description", "ServerFixes Generated Entity Tags");
            pack.addProperty("pack_format", 15);
            pack.add("description", desc);
            Files.writeString(packMcmeta, new GsonBuilder().setPrettyPrinting().create().toJson(pack));
        } catch (Throwable ignored) {
        }
    }
}
