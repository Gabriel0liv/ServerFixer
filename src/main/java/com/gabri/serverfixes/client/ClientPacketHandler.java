package com.gabri.serverfixes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class ClientPacketHandler {
    public static void handleOpenEditor(CompoundTag itemTag) {
        Minecraft.getInstance().setScreen(new com.gabri.serverfixes.client.gui.ItemEditorScreen(itemTag));
    }

    public static void handleSyncConfig(CompoundTag configData) {
        Minecraft.getInstance().setScreen(new com.gabri.serverfixes.client.gui.AdminPanelScreen(configData));
    }
}
