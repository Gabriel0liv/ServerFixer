package com.gabri.serverfixes.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ServerFixesMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null; // Uses the default refmap from the json config
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // Throttling Mixins for Farm & Charm
        if (mixinClassName.contains("throttling.CookingPot") || mixinClassName.contains("throttling.Roaster") || mixinClassName.contains("throttling.Stove")) {
            return isClassPresent("net.satisfy.farm_and_charm.core.registry.EntityTypeRegistry");
        }

        // Throttling Mixins for Vinery
        if (mixinClassName.contains("throttling.FermentationBarrel")) {
            return isClassPresent("net.satisfy.vinery.core.registry.EntityTypeRegistry");
        }

        // Entity Culling - Super Glue (Create)
        if (mixinClassName.contains("SuperGlueCullingMixin")) {
            return isClassPresent("com.simibubi.create.content.contraptions.glue.SuperGlueEntity");
        }

        // Entity Culling - Mana Burst (Botania)
        if (mixinClassName.contains("ManaBurstCullingMixin")) {
            return isClassPresent("vazkii.botania.common.entity.ManaBurstEntity");
        }

        // Entity Culling - Alfheim Portal (Botania)
        if (mixinClassName.contains("AlfheimPortalCullingMixin")) {
            return isClassPresent("vazkii.botania.common.block.tile.TileAlfheimPortal");
        }

        // Contraption Idle Throttling (Create)
        if (mixinClassName.contains("ContraptionIdleThrottlingMixin")) {
            return isClassPresent("com.simibubi.create.content.contraptions.ContraptionEntity");
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private boolean isClassPresent(String className) {
        try {
            // Safe way to check for a mod without touching FMLLoader directly during early mixin phases.
            Class.forName(className, false, this.getClass().getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
