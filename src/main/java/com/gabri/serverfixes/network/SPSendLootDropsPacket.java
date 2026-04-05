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
    private final List<LootDropDTO> cleanDrops;

    public SPSendLootDropsPacket(ResourceLocation tableId, List<LootDropDTO> drops) {
        this(tableId, drops, new ArrayList<>());
    }

    public SPSendLootDropsPacket(ResourceLocation tableId, List<LootDropDTO> drops, List<LootDropDTO> cleanDrops) {
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
                    dto.isComplex(),
                    dto.getTag(),
                    dto.getReferenceTable(),
                    dto.isEnchantRandomly(),
                    dto.isEnchantWithLevels(),
                    dto.getEnchantLevelsRange() != null
                        ? new LootDropDTO.Range(dto.getEnchantLevelsRange().getMin(), dto.getEnchantLevelsRange().getMax())
                        : null,
                    dto.getPotionId(),
                    dto.getNbtData(),
                    dto.getCustomNameJson(),
                    dto.isExplorationMap(),
                    dto.isEmptyDrop()
                ));
            }
        }
        this.cleanDrops = new ArrayList<>();
        if (cleanDrops != null) {
            for (LootDropDTO dto : cleanDrops) {
                if (dto == null) continue;
                this.cleanDrops.add(new LootDropDTO(
                    dto.getItem() != null ? dto.getItem().copy() : net.minecraft.world.item.ItemStack.EMPTY,
                    dto.getChance(),
                    dto.getMin(),
                    dto.getMax(),
                    dto.isRequirePlayerKill(),
                    dto.isAffectedByLooting(),
                    dto.isComplex(),
                    dto.getTag(),
                    dto.getReferenceTable(),
                    dto.isEnchantRandomly(),
                    dto.isEnchantWithLevels(),
                    dto.getEnchantLevelsRange() != null
                        ? new LootDropDTO.Range(dto.getEnchantLevelsRange().getMin(), dto.getEnchantLevelsRange().getMax())
                        : null,
                    dto.getPotionId(),
                    dto.getNbtData(),
                    dto.getCustomNameJson(),
                    dto.isExplorationMap(),
                    dto.isEmptyDrop()
                ));
            }
        }
    }

    public List<LootDropDTO> getCleanDrops() {
        return this.cleanDrops;
    }

    public SPSendLootDropsPacket(FriendlyByteBuf buf) {
        this.tableId = buf.readResourceLocation();
        this.drops = LootStudioPacketCodec.readLootDropList(buf);
        this.cleanDrops = LootStudioPacketCodec.readLootDropList(buf);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.tableId);
        LootStudioPacketCodec.writeLootDropList(buf, this.drops);
        LootStudioPacketCodec.writeLootDropList(buf, this.cleanDrops);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context == null) return;

        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
            com.gabri.serverfixes.client.ClientPacketHandler.handleLootDropsWithClean(this.tableId, this.drops, this.cleanDrops)
        ));
        context.setPacketHandled(true);
    }
}
