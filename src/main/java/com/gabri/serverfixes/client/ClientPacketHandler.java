package com.gabri.serverfixes.client;

import com.gabri.serverfixes.network.ContextTargetType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class ClientPacketHandler {
    public static void handleOpenEditor(CompoundTag itemTag) {
        Minecraft.getInstance().setScreen(new com.gabri.serverfixes.client.gui.ItemEditorScreen(itemTag));
    }

    public static void handleSyncConfig(CompoundTag configData) {
        Minecraft.getInstance().setScreen(new com.gabri.serverfixes.client.gui.AdminPanelScreen(configData));
    }

    public static void handleOpenContextEditor(ContextTargetType targetType, int containerId, int slotIndex, int entityId, BlockPos blockPos, CompoundTag nbtTag, String title) {
        Minecraft.getInstance().setScreen(new com.gabri.serverfixes.client.gui.ContextNbtEditorScreen(
            targetType,
            containerId,
            slotIndex,
            entityId,
            blockPos,
            nbtTag,
            title
        ));
    }
}
