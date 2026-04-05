package com.gabri.serverfixes.client;

import com.gabri.serverfixes.ServerFixes;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ServerFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
@SuppressWarnings("null")
public class ServerFixesKeybinds {
    private static final String CATEGORY = "key.categories.serverfixes";

    public static final KeyMapping ADMIN_PANEL = new KeyMapping(
        "key.serverfixes.admin_panel",
        InputConstants.UNKNOWN.getValue(),
        CATEGORY
    );

    public static final KeyMapping CONTEXT_EDITOR = new KeyMapping(
        "key.serverfixes.context_editor",
        InputConstants.UNKNOWN.getValue(),
        CATEGORY
    );

    @Deprecated
    public static final KeyMapping OPEN_ADMIN_PANEL = ADMIN_PANEL;

    @Deprecated
    public static final KeyMapping OPEN_CONTEXT_EDITOR = CONTEXT_EDITOR;

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ADMIN_PANEL);
        event.register(CONTEXT_EDITOR);
    }
}
