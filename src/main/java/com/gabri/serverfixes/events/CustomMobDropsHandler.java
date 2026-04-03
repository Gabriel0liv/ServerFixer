package com.gabri.serverfixes.events;

import com.gabri.serverfixes.ServerFixes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Adds custom drops configured through spawn egg EntityTag -> ForgeData -> SF_CustomDrops.
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ServerFixes.MODID)
public class CustomMobDropsHandler {
    private static final String CUSTOM_DROPS_TAG = "SF_CustomDrops";
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event == null || event.getEntity() == null || event.getEntity().level().isClientSide()) {
            return;
        }

        CompoundTag persistentData = event.getEntity().getPersistentData();
        ListTag customDrops = readCustomDrops(persistentData);
        if (customDrops.isEmpty()) {
            return;
        }

        double x = event.getEntity().getX();
        double y = event.getEntity().getY();
        double z = event.getEntity().getZ();

        for (int i = 0; i < customDrops.size(); i++) {
            CompoundTag dropEntry = customDrops.getCompound(i);
            if (!dropEntry.contains("Item", Tag.TAG_COMPOUND)) {
                continue;
            }

            float chance = dropEntry.contains("Chance", Tag.TAG_ANY_NUMERIC)
                ? Mth.clamp(dropEntry.getFloat("Chance"), 0.0F, 1.0F)
                : 1.0F;

            if (RANDOM.nextFloat() > chance) {
                continue;
            }

            ItemStack stack = ItemStack.of(dropEntry.getCompound("Item"));
            if (stack.isEmpty()) {
                continue;
            }

            event.getDrops().add(new ItemEntity(event.getEntity().level(), x, y, z, stack.copy()));
        }
    }

    private static ListTag readCustomDrops(CompoundTag persistentData) {
        if (persistentData.contains(CUSTOM_DROPS_TAG, Tag.TAG_LIST)) {
            return persistentData.getList(CUSTOM_DROPS_TAG, Tag.TAG_COMPOUND);
        }
        if (persistentData.contains("ForgeData", Tag.TAG_COMPOUND)) {
            CompoundTag forgeData = persistentData.getCompound("ForgeData");
            if (forgeData.contains(CUSTOM_DROPS_TAG, Tag.TAG_LIST)) {
                return forgeData.getList(CUSTOM_DROPS_TAG, Tag.TAG_COMPOUND);
            }
        }
        return new ListTag();
    }
}
