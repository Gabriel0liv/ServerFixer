package com.gabri.serverfixes.events;

import com.gabri.serverfixes.ServerFixes;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ServerFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ItemTooltipHandler {

    private static final String MAIN_TAG = "SF_ItemEffects";
    private static final String SF_HIDE_EFFECT_FLAGS_TAG = "SF_HideEffectFlags";
    private static final int HIDE_ON_USE = 1;
    private static final int HIDE_ON_HIT = 2;
    private static final int HIDE_ON_HURT = 4;
    private static final int HIDE_ON_EQUIP = 8;

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.hasTag()) {
            return;
        }

        CompoundTag itemTag = stack.getTag();
        if (itemTag == null || !itemTag.contains(MAIN_TAG, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag sf = itemTag.getCompound(MAIN_TAG);
        int hideEffectFlags = itemTag.contains(SF_HIDE_EFFECT_FLAGS_TAG, Tag.TAG_ANY_NUMERIC)
            ? itemTag.getInt(SF_HIDE_EFFECT_FLAGS_TAG)
            : 0;

        boolean hideOnUse = isHidden(hideEffectFlags, HIDE_ON_USE);
        boolean hideOnHit = isHidden(hideEffectFlags, HIDE_ON_HIT);
        boolean hideOnHurt = isHidden(hideEffectFlags, HIDE_ON_HURT);
        boolean hideOnEquip = isHidden(hideEffectFlags, HIDE_ON_EQUIP);

        if (!hideOnEquip) {
            injectOnEquipIntoModifierSections(event.getToolTip(), sf);
        }

        List<CompoundTag> onUse = hideOnUse ? List.of() : collectEntries(sf, "on_use");
        List<CompoundTag> onHitSelf = hideOnHit ? List.of() : collectEntriesBySelf(sf, "on_hit", true);
        List<CompoundTag> onHitTarget = hideOnHit ? List.of() : collectEntriesBySelf(sf, "on_hit", false);
        List<CompoundTag> onHurtSelf = hideOnHurt ? List.of() : collectEntriesBySelf(sf, "on_hurt", true);
        List<CompoundTag> onHurtTarget = hideOnHurt ? List.of() : collectEntriesBySelf(sf, "on_hurt", false);

        boolean hasAny = !onUse.isEmpty() || !onHitSelf.isEmpty() || !onHitTarget.isEmpty() || !onHurtSelf.isEmpty() || !onHurtTarget.isEmpty();
        if (!hasAny) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.empty());

        addGroup(tooltip, "Ao Usar:", onUse);
        addGroup(tooltip, "Ao Atacar, recebe:", onHitSelf);
        addGroup(tooltip, "Ao Atacar, aplica:", onHitTarget);
        addGroup(tooltip, "Ao Receber Dano, recebe:", onHurtSelf);
        addGroup(tooltip, "Ao Receber Dano, aplica:", onHurtTarget);
    }

    private static boolean isHidden(int mask, int flag) {
        return (mask & flag) != 0;
    }

    private static void injectOnEquipIntoModifierSections(List<Component> tooltip, CompoundTag sf) {
        List<CompoundTag> onEquip = collectEntries(sf, "on_equip");
        if (onEquip.isEmpty()) {
            return;
        }

        Map<String, List<CompoundTag>> groupedBySlot = new LinkedHashMap<>();
        for (CompoundTag entry : onEquip) {
            String slot = entry.contains("Slot", Tag.TAG_STRING) ? entry.getString("Slot").toLowerCase(Locale.ROOT) : "any";
            groupedBySlot.computeIfAbsent(slot, k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<String, List<CompoundTag>> slotGroup : groupedBySlot.entrySet()) {
            String slot = slotGroup.getKey();
            List<Component> equipLines = new ArrayList<>();
            for (CompoundTag entry : slotGroup.getValue()) {
                Component line = formatOnEquipLine(entry);
                if (line != null) {
                    equipLines.add(line);
                }
            }

            if (equipLines.isEmpty()) {
                continue;
            }

            String slotKey = slotToTooltipKey(slot);
            int headerIndex = findModifierHeaderIndex(tooltip, slotKey);
            if (headerIndex >= 0) {
                int insertIndex = findInsertIndexAfterModifierBlock(tooltip, headerIndex);
                tooltip.addAll(insertIndex, equipLines);
                continue;
            }

            Component header = createSlotHeader(slot);
            if (!tooltip.isEmpty() && !tooltip.get(tooltip.size() - 1).getString().isBlank()) {
                tooltip.add(Component.empty());
            }
            tooltip.add(header);
            tooltip.addAll(equipLines);
        }
    }

    private static int findModifierHeaderIndex(List<Component> tooltip, String slotKey) {
        if (slotKey == null) {
            return -1;
        }

        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            if (line.getContents() instanceof TranslatableContents translatable) {
                if (slotKey.equals(translatable.getKey())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int findInsertIndexAfterModifierBlock(List<Component> tooltip, int headerIndex) {
        int i = headerIndex + 1;
        while (i < tooltip.size()) {
            Component line = tooltip.get(i);
            if (isModifierHeader(line) || line.getString().isBlank()) {
                break;
            }
            i++;
        }
        return i;
    }

    private static boolean isModifierHeader(Component component) {
        if (!(component.getContents() instanceof TranslatableContents translatable)) {
            return false;
        }
        String key = translatable.getKey();
        return key.startsWith("item.modifiers.") || key.startsWith("curios.modifiers.");
    }

    private static String slotToTooltipKey(String slot) {
        if (slot == null || slot.isBlank() || "any".equalsIgnoreCase(slot)) {
            return null;
        }
        String normalized = slot.toLowerCase(Locale.ROOT);
        if ("mainhand".equals(normalized) || "offhand".equals(normalized)
            || "head".equals(normalized) || "chest".equals(normalized)
            || "legs".equals(normalized) || "feet".equals(normalized)) {
            return "item.modifiers." + normalized;
        }
        return "curios.modifiers." + normalized;
    }

    private static Component createSlotHeader(String slot) {
        String key = slotToTooltipKey(slot);
        if (key != null) {
            return Component.translatable(key).withStyle(ChatFormatting.GRAY);
        }
        return Component.literal("Quando equipado em qualquer slot:").withStyle(ChatFormatting.GRAY);
    }

    private static Component formatOnEquipLine(CompoundTag entry) {
        MobEffect effect = resolveEffect(entry);
        if (effect == null) {
            return null;
        }

        int amplifier = readInt(entry, "amplifier", "Amplifier", 0);
        ChatFormatting color = effect.getCategory() == MobEffectCategory.HARMFUL ? ChatFormatting.RED : ChatFormatting.BLUE;

        MutableComponent line = Component.literal(" ")
            .append(Component.literal("Nível de Efeito: "))
            .append(Component.translatable(effect.getDescriptionId()));

        if (amplifier > 0) {
            line.append(Component.literal(" " + toRoman(amplifier + 1)));
        }

        return line.withStyle(color);
    }

    private static void addGroup(List<Component> tooltip, String header, List<CompoundTag> entries) {
        if (entries.isEmpty()) {
            return;
        }

        tooltip.add(Component.literal(header).withStyle(ChatFormatting.GRAY));
        for (CompoundTag entry : entries) {
            Component line = formatEffectLine(entry);
            if (line != null) {
                tooltip.add(line);
            }
        }
    }

    private static Component formatEffectLine(CompoundTag entry) {
        MobEffect effect = resolveEffect(entry);
        if (effect == null) {
            return null;
        }

        int amplifier = readInt(entry, "amplifier", "Amplifier", 0);
        int durationTicks = readInt(entry, "duration", "Duration", 0);
        ChatFormatting color = effect.getCategory() == MobEffectCategory.HARMFUL ? ChatFormatting.RED : ChatFormatting.BLUE;

        MutableComponent line = Component.literal(" ").append(Component.translatable(effect.getDescriptionId()));

        if (amplifier > 0) {
            line.append(Component.literal(" " + toRoman(amplifier + 1)));
        }

        line.append(Component.literal(" (" + formatTicksToMinutesSeconds(durationTicks) + ")"));
        return line.withStyle(color);
    }

    private static List<CompoundTag> collectEntries(CompoundTag sf, String key) {
        List<CompoundTag> out = new ArrayList<>();
        if (!sf.contains(key, Tag.TAG_LIST)) {
            return out;
        }

        ListTag list = sf.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getCompound(i));
        }
        return out;
    }

    private static List<CompoundTag> collectEntriesBySelf(CompoundTag sf, String key, boolean self) {
        List<CompoundTag> out = new ArrayList<>();
        if (!sf.contains(key, Tag.TAG_LIST)) {
            return out;
        }

        ListTag list = sf.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            boolean entrySelf = entry.contains("self", Tag.TAG_BYTE) && entry.getBoolean("self");
            if (entrySelf == self) {
                out.add(entry);
            }
        }
        return out;
    }

    private static MobEffect resolveEffect(CompoundTag entry) {
        if (entry.contains("id", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id != null) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
                if (effect != null) {
                    return effect;
                }
            }
        }

        if (entry.contains("IdString", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("IdString"));
            if (id != null) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
                if (effect != null) {
                    return effect;
                }
            }
        }

        if (entry.contains("Id", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("Id"));
            if (id != null) {
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
                if (effect != null) {
                    return effect;
                }
            }
        }

        if (entry.contains("Id", Tag.TAG_ANY_NUMERIC)) {
            return MobEffect.byId(entry.getInt("Id"));
        }

        return null;
    }

    private static int readInt(CompoundTag entry, String primaryKey, String secondaryKey, int fallback) {
        if (entry.contains(primaryKey, Tag.TAG_ANY_NUMERIC)) {
            return entry.getInt(primaryKey);
        }
        if (entry.contains(secondaryKey, Tag.TAG_ANY_NUMERIC)) {
            return entry.getInt(secondaryKey);
        }
        return fallback;
    }

    private static String formatTicksToMinutesSeconds(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private static String toRoman(int value) {
        if (value <= 0) {
            return "I";
        }

        int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] romans = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

        StringBuilder out = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < numbers.length; i++) {
            while (remaining >= numbers[i]) {
                out.append(romans[i]);
                remaining -= numbers[i];
            }
        }
        return out.toString();
    }
}
