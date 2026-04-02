package com.gabri.serverfixes.events;

import com.gabri.serverfixes.ServerFixes;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import java.util.List;

@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ServerFixes.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ItemTooltipHandler {

    private static final String MAIN_TAG = "SF_ItemEffects";

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

        List<CompoundTag> onUse = collectEntries(sf, "on_use");
        List<CompoundTag> onHitSelf = collectEntriesBySelf(sf, "on_hit", true);
        List<CompoundTag> onHitTarget = collectEntriesBySelf(sf, "on_hit", false);
        List<CompoundTag> onHurtSelf = collectEntriesBySelf(sf, "on_hurt", true);
        List<CompoundTag> onHurtTarget = collectEntriesBySelf(sf, "on_hurt", false);

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
