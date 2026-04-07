package com.gabri.serverfixes.network;

import com.gabri.serverfixes.client.gui.RecipeBasicDTO;
import com.gabri.serverfixes.recipe.RecipeStudioLogic;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Packet client->server requisitando a lista de receitas com filtros.
 */
public class RequestRecipeListPacket {
    private final String modFilter;
    private final String typeFilter;
    private final String itemFilter;

    public RequestRecipeListPacket(String modFilter, String typeFilter, String itemFilter) {
        this.modFilter = modFilter;
        this.typeFilter = typeFilter;
        this.itemFilter = itemFilter;
    }

    public RequestRecipeListPacket(FriendlyByteBuf buf) {
        this.modFilter = buf.readUtf(32767);
        this.typeFilter = buf.readUtf(32767);
        this.itemFilter = buf.readUtf(32767);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.modFilter, 32767);
        buf.writeUtf(this.typeFilter, 32767);
        buf.writeUtf(this.itemFilter, 32767);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null || !sender.hasPermissions(2)) return;

            var server = sender.getServer();
            if (server == null) return;

            List<RecipeBasicDTO> recipes = RecipeStudioLogic.getFilteredRecipes(
                server, this.modFilter, this.typeFilter, this.itemFilter
            );

            NetworkHandler.sendToPlayer(sender, new SPSendRecipeListPacket(recipes));
        });
        context.setPacketHandled(true);
    }
}
