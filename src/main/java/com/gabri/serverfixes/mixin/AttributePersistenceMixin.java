package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.commands.ItemAttributeCommands;
import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures that item attributes are never lost on hybrid servers like Archlight.
 * It synchronizes the custom backup tag with the standard AttributeModifiers tag.
 */
@SuppressWarnings("null")
@Mixin(ItemStack.class)
public abstract class AttributePersistenceMixin {

    @Inject(method = "setTag", at = @At("HEAD"))
    private void serverfixes$onSetTag(CompoundTag nbt, CallbackInfo ci) {
        if (nbt != null) {
            serverfixes$syncAttributes(nbt);
            serverfixes$syncEffects(nbt);
        }
    }

    @Inject(method = "of", at = @At("RETURN"))
    private static void serverfixes$onLoad(CompoundTag nbt, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = cir.getReturnValue();
        if (stack != null && !stack.isEmpty() && stack.hasTag()) {
            serverfixes$syncAttributes(stack.getTag());
            serverfixes$syncEffects(stack.getTag());
        }
    }

    private static void serverfixes$syncAttributes(CompoundTag nbt) {
        if (nbt == null || !ServerFixesConfig.ENABLE_PERSISTENT_ATTRIBUTES.get()) return;
        
        if (nbt.contains(ItemAttributeCommands.BACKUP_TAG, 9)) {
            ListTag backupList = nbt.getList(ItemAttributeCommands.BACKUP_TAG, 10);
            if (backupList.isEmpty()) return;

            for (int i = 0; i < backupList.size(); i++) {
                CompoundTag backupMod = backupList.getCompound(i);
                if (!backupMod.hasUUID("UUID")) continue;
                
                boolean isCurio = backupMod.getBoolean("IsCurio");
                String targetTag = isCurio ? ItemAttributeCommands.CURIOS_TAG : "AttributeModifiers";
                
                ListTag standardList = nbt.contains(targetTag, 9) ? nbt.getList(targetTag, 10) : new ListTag();
                if (!nbt.contains(targetTag)) nbt.put(targetTag, standardList);

                String uuidStr = backupMod.getUUID("UUID").toString();
                
                boolean exists = false;
                for (int j = 0; j < standardList.size(); j++) {
                    CompoundTag stdMod = standardList.getCompound(j);
                    if (stdMod.hasUUID("UUID") && stdMod.getUUID("UUID").toString().equals(uuidStr)) {
                        exists = true;
                        break;
                    }
                }
                
                if (!exists) {
                    CompoundTag restoreTag = backupMod.copy();
                    restoreTag.remove("IsCurio"); // Clean up for vanilla/curios
                    standardList.add(restoreTag);
                }
            }
        }
    }

    // Handles persistence for On-Hit and On-Hurt effects
    private static void serverfixes$syncEffects(CompoundTag nbt) {
        if (nbt == null || !ServerFixesConfig.ENABLE_ITEM_EFFECT_MODIFIERS.get()) return;

        String mainTag = "SF_ItemEffects";
        String backupTag = "SF_BackupEffects";

        // If standard is missing but backup exists, restore standard
        if (nbt.contains(backupTag, 10) && !nbt.contains(mainTag, 10)) {
            nbt.put(mainTag, nbt.getCompound(backupTag).copy());
        } 
        // If standard exists, keep backup updated
        else if (nbt.contains(mainTag, 10)) {
            nbt.put(backupTag, nbt.getCompound(mainTag).copy());
        }
    }
}
