package com.gabri.serverfixes.client;

import com.gabri.serverfixes.ServerFixes;
import com.gabri.serverfixes.client.gui.ItemEditorScreen;
import com.gabri.serverfixes.network.ContextTargetType;
import com.gabri.serverfixes.network.NetworkHandler;
import com.gabri.serverfixes.network.RequestAdminPanelPacket;
import com.gabri.serverfixes.network.RequestOpenContextEditorPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ServerFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
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

        while (ServerFixesKeybinds.OPEN_ADMIN_PANEL.consumeClick()) {
            handleOpenAdminPanel(minecraft);
        }

        while (ServerFixesKeybinds.OPEN_CONTEXT_EDITOR.consumeClick()) {
            handleOpenContextEditor(minecraft);
        }
    }

    private static void handleOpenAdminPanel(Minecraft minecraft) {
        if (!canUsePrivilegedEditor(minecraft)) {
            minecraft.player.displayClientMessage(Component.literal("§cAcesso negado: requer OP e modo Criativo."), true);
            return;
        }

        NetworkHandler.sendToServer(new RequestAdminPanelPacket());
    }

    private static void handleOpenContextEditor(Minecraft minecraft) {
        if (!canUsePrivilegedEditor(minecraft)) {
            minecraft.player.displayClientMessage(Component.literal("§cAcesso negado: requer OP e modo Criativo."), true);
            return;
        }

        ContextRequest request = resolveContextRequest(minecraft);
        if (request == null) {
            if (!minecraft.player.getMainHandItem().isEmpty()) {
                minecraft.setScreen(new ItemEditorScreen(minecraft.player.getMainHandItem().getOrCreateTag()));
                return;
            }
            minecraft.player.displayClientMessage(Component.literal("§7Nenhum alvo contextual disponível."), true);
            return;
        }

        NetworkHandler.sendToServer(new RequestOpenContextEditorPacket(
            request.targetType,
            request.containerId,
            request.slotIndex,
            request.entityId,
            request.blockPos
        ));
    }

    private static ContextRequest resolveContextRequest(Minecraft minecraft) {
        if (minecraft.screen instanceof AbstractContainerScreen<?> containerScreen) {
            ContextRequest hoveredSlotRequest = resolveHoveredSlot(containerScreen, minecraft);
            if (hoveredSlotRequest != null) {
                return hoveredSlotRequest;
            }
        }

        HitResult hitResult = minecraft.hitResult;
        if (hitResult instanceof EntityHitResult entityHitResult) {
            return ContextRequest.forEntity(entityHitResult.getEntity().getId());
        }

        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos();
            if (minecraft.level != null && minecraft.level.getBlockEntity(pos) != null) {
                return ContextRequest.forBlockEntity(pos);
            }
        }

        return null;
    }

    private static ContextRequest resolveHoveredSlot(AbstractContainerScreen<?> screen, Minecraft minecraft) {
        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null) {
            return null;
        }

        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        double mouseX = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / (double) minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / (double) minecraft.getWindow().getScreenHeight();

        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) {
                continue;
            }
            int slotLeft = left + slot.x;
            int slotTop = top + slot.y;
            if (mouseX >= slotLeft && mouseX < slotLeft + 16 && mouseY >= slotTop && mouseY < slotTop + 16) {
                return ContextRequest.forContainerSlot(menu.containerId, slot.index);
            }
        }
        return null;
    }

    private static boolean canUsePrivilegedEditor(Minecraft minecraft) {
        return minecraft.player != null && minecraft.player.isCreative() && minecraft.player.hasPermissions(2);
    }

    private static class ContextRequest {
        private final ContextTargetType targetType;
        private final int containerId;
        private final int slotIndex;
        private final int entityId;
        private final BlockPos blockPos;

        private ContextRequest(ContextTargetType targetType, int containerId, int slotIndex, int entityId, BlockPos blockPos) {
            this.targetType = targetType;
            this.containerId = containerId;
            this.slotIndex = slotIndex;
            this.entityId = entityId;
            this.blockPos = blockPos;
        }

        private static ContextRequest forContainerSlot(int containerId, int slotIndex) {
            return new ContextRequest(ContextTargetType.CONTAINER_SLOT, containerId, slotIndex, -1, BlockPos.ZERO);
        }

        private static ContextRequest forEntity(int entityId) {
            return new ContextRequest(ContextTargetType.ENTITY, -1, -1, entityId, BlockPos.ZERO);
        }

        private static ContextRequest forBlockEntity(BlockPos pos) {
            return new ContextRequest(ContextTargetType.BLOCK_ENTITY, -1, -1, -1, pos != null ? pos : BlockPos.ZERO);
        }
    }
}
