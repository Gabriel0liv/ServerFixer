package com.gabri.serverfixes.network;

import com.gabri.serverfixes.ServerFixes;
import com.gabri.serverfixes.client.gui.RecipeBasicDTO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet server->client enviando a lista de receitas filtradas.
 */
public class SPSendRecipeListPacket {
    private final List<RecipeBasicDTO> recipes;

    public SPSendRecipeListPacket(List<RecipeBasicDTO> recipes) {
        this.recipes = new ArrayList<>();
        if (recipes != null) {
            for (RecipeBasicDTO dto : recipes) {
                if (dto != null) this.recipes.add(dto);
            }
        }
    }

    public SPSendRecipeListPacket(FriendlyByteBuf buf) {
        this.recipes = buf.readList(b -> new RecipeBasicDTO(b));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeCollection(this.recipes, (b, dto) -> dto.toBytes(b));
    }

    public List<RecipeBasicDTO> getRecipes() {
        return this.recipes;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleRecipeList(this.recipes)
        ));
        context.setPacketHandled(true);
    }
}
