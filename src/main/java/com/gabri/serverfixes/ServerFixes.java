package com.gabri.serverfixes;

import com.gabri.serverfixes.commands.CuriosSelectorOptions;
import com.gabri.serverfixes.commands.EffectSelectorOptions;
import com.gabri.serverfixes.commands.ServerFixesCommands;
import com.gabri.serverfixes.config.ServerFixesConfig;
import com.gabri.serverfixes.events.AntiSwapExploitHandler;
import com.gabri.serverfixes.events.DamageDebugHandler;
import com.gabri.serverfixes.events.MalumScytheFix;
import com.gabri.serverfixes.events.VillagerHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("all")
@Mod(ServerFixes.MODID)
public class ServerFixes {
    public static final String MODID = "server_fixes";
    private static final Logger LOGGER = LogManager.getLogger();

    public ServerFixes() {
        LOGGER.info("[ServerFixes] --- STARTING INITIALIZATION ---");
        
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerFixesConfig.SPEC);
        
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(DamageDebugHandler.class);
        MinecraftForge.EVENT_BUS.register(VillagerHandler.class);
        MinecraftForge.EVENT_BUS.register(AntiSwapExploitHandler.class);
        
        
        LOGGER.info("[ServerFixes] --- INITIALIZATION COMPLETE ---");
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[ServerFixes] Registering entity selector options (@e[effect=...], @e[ring=...])...");
            EffectSelectorOptions.register();
            if (ModList.get().isLoaded("curios")) {
                CuriosSelectorOptions.registerAll();
            }
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // Fallback for dynamic Curios slots that might load after common setup
        if (ModList.get().isLoaded("curios")) {
            CuriosSelectorOptions.registerAll();
        }

        LOGGER.info("[ServerFixes] Registering commands...");
        ServerFixesCommands.register(event.getDispatcher(), event.getBuildContext());
        LOGGER.info("[ServerFixes] Commands registered successfully.");
    }
}
