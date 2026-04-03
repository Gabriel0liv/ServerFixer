package com.gabri.serverfixes.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;
import java.util.Map;

@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor("spriteSets")
    @Nullable
    Map<ResourceLocation, SpriteSet> getSpriteSets();
}
