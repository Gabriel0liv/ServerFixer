package com.gabri.serverfixes.network;

import com.gabri.serverfixes.client.gui.LootDropDTO;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("null")
public class SPSendLootDropsPacket {
    private final ResourceLocation tableId;
    private final List<LootDropDTO> drops;

    public SPSendLootDropsPacket(ResourceLocation tableId, List<LootDropDTO> drops) {
        this.tableId = tableId;
        this.drops = new ArrayList<>();
        if (drops != null) {
            for (LootDropDTO dto : drops) {
                if (dto == null) continue;
                this.drops.add(new LootDropDTO(
                    dto.getItem() != null ? dto.getItem().copy() : net.minecraft.world.item.ItemStack.EMPTY,
                    dto.getChance(),
                    dto.getMin(),
                    dto.getMax(),
                    dto.isRequirePlayerKill(),
                    dto.isAffectedByLooting(),
                    dto.isComplex()
                ));
            }
        }
    }

    public SPSendLootDropsPacket(FriendlyByteBuf buf) {
        this.tableId = buf.readResourceLocation();
        this.drops = LootStudioPacketCodec.readLootDropList(buf);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.tableId);
        LootStudioPacketCodec.writeLootDropList(buf, this.drops);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleLootDrops(this.tableId, this.drops)
        ));
        context.setPacketHandled(true);
    }
}
