package com.gabri.serverfixes.commands;

import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashSet;
import java.util.Set;

/**
 * Dynamically registers Curios slots as entity selector options.
 * Example: @a[ring=minecraft:iron_ingot] or @a[curio=mod:item]
 */
@SuppressWarnings("all")
public class CuriosSelectorOptions {

    private static final Set<String> REGISTERED_OPTIONS = new HashSet<>();

    public static void registerAll() {
        if (!ModList.get().isLoaded("curios")) return;

        // 1. Primary selectors
        safeRegister("curios", "Filtra por qualquer item em qualquer slot do Curios", null);
        safeRegister("curio", "Filtra por qualquer item em qualquer slot do Curios", null);

        // 2. Standard Curios slots (Pre-registering these ensures they always show up)
        String[] standardSlots = {
            "ring", "belt", "necklace", "charm", "head", "body", "back", 
            "hands", "feet", "bracelet", "curio_slot"
        };
        for (String slot : standardSlots) {
            safeRegister(slot, "Filtra itens no slot de " + slot + " do Curios", slot);
        }

        // 3. Dynamic Discovery (Attempt to find any other custom slots from modpack)
        try {
            // Using the API version provided by the user
            Set<String> dynamicSlots = CuriosApi.getSlots(false).keySet();
            for (String slotId : dynamicSlots) {
                safeRegister(slotId, "Filtra itens no slot de " + slotId + " do Curios", slotId);
            }
        } catch (Exception ignored) {}
    }

    private static void safeRegister(String id, String description, String specificSlot) {
        if (isVanillaOption(id) || REGISTERED_OPTIONS.contains(id)) return;

        try {
            EntitySelectorOptions.register(id, (parser) -> {
                boolean invert = parser.shouldInvertValue();
                if (invert) parser.getReader().skip(); // Fix for negation character '!'
                
                // Add item suggestions
                parser.setSuggestions((builder, consumer) -> {
                    return SharedSuggestionProvider.suggestResource(ForgeRegistries.ITEMS.getKeys(), builder);
                });

                int cursorBefore = parser.getReader().getCursor();
                ResourceLocation itemRes = ResourceLocation.read(parser.getReader());
                Item item = ForgeRegistries.ITEMS.getValue(itemRes);
                
                parser.addPredicate(entity -> {
                    if (entity instanceof LivingEntity living) {
                        boolean hasItem = (specificSlot == null) ? 
                                          checkAnySlot(living, item) : 
                                          checkSpecificSlot(living, specificSlot, item);
                        return invert != hasItem;
                    }
                    return invert;
                });
            }, (parser) -> true, Component.literal(description));
            
            REGISTERED_OPTIONS.add(id);
        } catch (IllegalArgumentException e) {
            // Already registered
            REGISTERED_OPTIONS.add(id);
        }
    }

    private static boolean checkSpecificSlot(LivingEntity entity, String slotId, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(handler -> {
            return handler.getStacksHandler(slotId).map(stacksHandler -> {
                for (int i = 0; i < stacksHandler.getSlots(); i++) {
                    ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() == item) {
                        return true;
                    }
                }
                return false;
            }).orElse(false);
        }).orElse(false);
    }

    private static boolean checkAnySlot(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).map(handler -> {
            return handler.findFirstCurio(item).isPresent();
        }).orElse(false);
    }

    private static boolean isVanillaOption(String option) {
        return option.equals("name") || option.equals("distance") || option.equals("level") ||
               option.equals("x") || option.equals("y") || option.equals("z") ||
               option.equals("dx") || option.equals("dy") || option.equals("dz") ||
               option.equals("type") || option.equals("tag") || option.equals("team") ||
               option.equals("limit") || option.equals("sort") || option.equals("scores") ||
               option.equals("advancements") || option.equals("predicate") || option.equals("nbt");
    }
}
