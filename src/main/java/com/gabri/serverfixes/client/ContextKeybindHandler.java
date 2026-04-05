package com.gabri.serverfixes.client;

import com.gabri.serverfixes.ServerFixes;
import com.gabri.serverfixes.client.gui.ItemEditorScreen;
import com.gabri.serverfixes.mixin.AbstractContainerScreenAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import com.gabri.serverfixes.network.ContextTargetType;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.RequestAdminPanelPacket;
import com.gabri.serverfixes.network.RequestBlockEditorPacket;
import com.gabri.serverfixes.network.RequestOpenContextEditorPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ServerFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("null")
public class ContextKeybindHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        while (ServerFixesKeybinds.ADMIN_PANEL.consumeClick()) {
            handleOpenAdminPanel(minecraft.player);
        }

        while (ServerFixesKeybinds.CONTEXT_EDITOR.consumeClick()) {
            handleOpenContextEditor(minecraft, minecraft.player, minecraft.level);
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressedPre(ScreenEvent.KeyPressed.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!canUsePrivilegedEditor(minecraft)) {
            return;
        }

        if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        if (!ServerFixesKeybinds.CONTEXT_EDITOR.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            return;
        }

        Slot slot = resolveHoveredSlot(containerScreen);
        if (slot == null || !slot.hasItem()) {
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        minecraft.setScreen(new ItemEditorScreen(slot.getItem().copy(), menu.containerId, slot.index));
        event.setCanceled(true);

        while (ServerFixesKeybinds.CONTEXT_EDITOR.consumeClick()) {
            // Drain pending clicks to avoid duplicate handling on the next client tick.
        }
    }

    private static void handleOpenAdminPanel(net.minecraft.client.player.LocalPlayer player) {
        if (!canUsePrivilegedEditor(Minecraft.getInstance())) {
            player.displayClientMessage(Component.literal("§cAcesso negado: requer OP e modo Criativo."), true);
            return;
        }

        NetworkHandler.sendToServer(new RequestAdminPanelPacket());
    }

    private static void handleOpenContextEditor(Minecraft minecraft, net.minecraft.client.player.LocalPlayer player, net.minecraft.world.level.Level level) {
        if (!canUsePrivilegedEditor(minecraft)) {
            player.displayClientMessage(Component.literal("§cAcesso negado: requer OP e modo Criativo."), true);
            return;
        }

        if (minecraft.screen instanceof AbstractContainerScreen<?> containerScreen) {
            Slot hoveredSlot = resolveHoveredSlot(containerScreen);
            if (hoveredSlot != null && hoveredSlot.hasItem()) {
                AbstractContainerMenu menu = containerScreen.getMenu();
                minecraft.setScreen(new ItemEditorScreen(hoveredSlot.getItem().copy(), menu.containerId, hoveredSlot.index));
                return;
            }
        }

        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState blockState = level.getBlockState(blockPos);

            if (!blockState.isAir()) {
                boolean hasProperties = !blockState.getProperties().isEmpty();
                boolean hasBlockEntity = level.getBlockEntity(blockPos) != null;
                if (hasProperties || hasBlockEntity) {
                    NetworkHandler.sendToServer(new RequestBlockEditorPacket(blockPos));
                    return;
                }
            }
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {
            NetworkHandler.sendToServer(new RequestOpenContextEditorPacket(
                ContextTargetType.ENTITY,
                -1,
                -1,
                entityHitResult.getEntity().getId(),
                net.minecraft.core.BlockPos.ZERO
            ));
            return;
        }

        if (!player.getMainHandItem().isEmpty()) {
            minecraft.setScreen(new ItemEditorScreen(player.getMainHandItem().copy()));
            return;
        }

        player.displayClientMessage(Component.literal("§7Nenhum alvo contextual disponível."), true);
    }

    private static Slot resolveHoveredSlot(AbstractContainerScreen<?> screen) {
        if (!(screen instanceof AbstractContainerScreenAccessor accessor)) {
            return null;
        }
        return accessor.getHoveredSlot();
    }

    private static boolean canUsePrivilegedEditor(Minecraft minecraft) {
        net.minecraft.client.player.LocalPlayer p = minecraft.player;
        return p != null && p.isCreative() && p.hasPermissions(2);
    }

    private static boolean canUseOperatorOnly(Minecraft minecraft) {
        net.minecraft.client.player.LocalPlayer p = minecraft.player;
        return p != null && p.hasPermissions(2);
    }
}
