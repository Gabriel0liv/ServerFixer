package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to allow MobEffectInstance to load from NBT using string IDs (namespace:id).
 * This fixed the issue where users had to guess numeric IDs for modded effects.
 */
@SuppressWarnings("all")
@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceMixin {

    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private static void serverfixes$onLoad(CompoundTag nbt, CallbackInfoReturnable<MobEffectInstance> cir) {
        if (!ServerFixesConfig.ENABLE_STRING_EFFECT_IDS.get()) {
            return;
        }
        // Tag types: 8 is String, 99 is any Numeric (Byte, Short, Int, Long)
        if (nbt.contains("Id", 8)) {
            String idStr = nbt.getString("Id");
            ResourceLocation rl = ResourceLocation.tryParse(idStr);
            if (rl != null) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(rl);
                if (effect != null) {
                    int amplifier = nbt.getByte("Amplifier");
                    int duration = nbt.getInt("Duration");
                    boolean ambient = nbt.getBoolean("Ambient");
                    boolean visible = nbt.contains("ShowParticles") ? nbt.getBoolean("ShowParticles") : true;
                    boolean showIcon = nbt.contains("ShowIcon") ? nbt.getBoolean("ShowIcon") : visible;
                    
                    MobEffectInstance inst = new MobEffectInstance(effect, duration, amplifier, ambient, visible, showIcon);
                    
                    // Handle vanilla "HiddenEffect" (used for overlapping effects)
                    if (nbt.contains("HiddenEffect", 10)) {
                        MobEffectInstance hidden = MobEffectInstance.load(nbt.getCompound("HiddenEffect"));
                        ((MobEffectInstanceAccessor) inst).setHiddenEffect(hidden);
                    }
                    
                    cir.setReturnValue(inst);
                }
            }
        }
    }
}
