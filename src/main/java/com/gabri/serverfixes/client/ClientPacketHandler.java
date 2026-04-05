package com.gabri.serverfixes.client;

import com.gabri.serverfixes.network.ContextTargetType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

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

    public static void handleOpenBlockEditor(BlockPos blockPos, String blockId, CompoundTag stateValues, CompoundTag allowedValues, CompoundTag blockEntityTag) {
        Minecraft.getInstance().setScreen(new com.gabri.serverfixes.client.gui.BlockEditorScreen(
            blockPos,
            blockId,
            stateValues,
            allowedValues,
            blockEntityTag
        ));
    }

    public static void handleOpenParticleStudio() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isCreative() || !player.hasPermissions(2)) {
            if (player != null) {
                player.displayClientMessage(Component.literal("§cAcesso negado: requer OP e modo Criativo."), true);
            }
            return;
        }

        minecraft.setScreen(new com.gabri.serverfixes.client.gui.ParticleStudioScreen());
    }

    public static void handleOpenSoundStudio() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.isCreative() || !player.hasPermissions(2)) {
            if (player != null) {
                player.displayClientMessage(Component.literal("§cAcesso negado: requer OP e modo Criativo."), true);
            }
            return;
        }

        minecraft.setScreen(new com.gabri.serverfixes.client.gui.SoundStudioScreen());
    }

    public static void handleOpenLootStudio() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !player.hasPermissions(2)) {
            if (player != null) {
                player.displayClientMessage(Component.literal("§cAcesso negado: requer OP."), true);
            }
            return;
        }

        minecraft.setScreen(new com.gabri.serverfixes.client.gui.LootStudioScreen());
    }

    public static void handleLootTableList(List<ResourceLocation> tableIds) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.gabri.serverfixes.client.gui.LootStudioScreen lootStudioScreen) {
            lootStudioScreen.applyLootTableListFromServer(tableIds);
        }
    }

    public static void handleLootDrops(ResourceLocation tableId, List<com.gabri.serverfixes.client.gui.LootDropDTO> drops) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.gabri.serverfixes.client.gui.LootStudioScreen lootStudioScreen) {
            lootStudioScreen.applyLootDropsFromServer(tableId, drops);
        }
    }

    public static void handleSaveResult(boolean success, ResourceLocation tableId, String message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof com.gabri.serverfixes.client.gui.LootStudioScreen lootStudioScreen) {
            lootStudioScreen.handleSaveResult(success);
        }
        if (minecraft.player != null && message != null && !message.isEmpty()) {
            minecraft.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
