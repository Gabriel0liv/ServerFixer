package com.gabri.serverfixes.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Fixes Malum scythe's double magic damage bug.
 *
 * ROOT CAUSE: Malum registers both SCYTHE_MELEE and SCYTHE_SWEEP as CAN_TRIGGER_MAGIC
 * in MalumDamageTypeTags. Lodestone's event system applies each player's MAGIC_DAMAGE
 * attribute for every LivingHurtEvent tagged as CAN_TRIGGER_MAGIC.
 *
 * When a scythe hits the primary target (SCYTHE_MELEE) it causes magic damage once.
 * The same hit then triggers a sweep (SCYTHE_SWEEP) and if the primary target is in
 * the sweep radius, they receive magic damage a second time.
 *
 * FIX: Intercept SCYTHE_SWEEP LivingHurtEvents and subtract the attacker's MAGIC_DAMAGE
 * attribute value from the damage amount, preventing double-application.
 *
 * Zero compile-time dependency on Malum or Lodestone — uses ResourceLocation lookups only.
 */
@SuppressWarnings("all")
public class MalumScytheFix {

    // Damage type RL for the sweep — derived from data/malum/damage_type/scythe_sweep.json
    private static final ResourceLocation SCYTHE_SWEEP_RL = new ResourceLocation("malum", "scythe_sweep");

    // Lodestone's magic damage attribute RL
    private static final ResourceLocation MAGIC_DAMAGE_RL = new ResourceLocation("lodestone", "magic_damage");

    // Cached attribute instance — null until first use or if Lodestone is absent
    private static Attribute magicDamageAttr = null;
    private static boolean lookedUp = false;

    private static Attribute getMagicDamageAttribute() {
        if (!lookedUp) {
            lookedUp = true;
            magicDamageAttr = ForgeRegistries.ATTRIBUTES.getValue(MAGIC_DAMAGE_RL);
        }
        return magicDamageAttr;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // Only process SCYTHE_SWEEP damage
        ResourceLocation damageTypeRL = event.getSource().typeHolder()
                .unwrapKey()
                .map(k -> k.location())
                .orElse(null);

        if (damageTypeRL == null || !SCYTHE_SWEEP_RL.equals(damageTypeRL)) return;

        // Get the attacker
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        // Get the MAGIC_DAMAGE attribute (gracefully absent if Lodestone not installed)
        Attribute attr = getMagicDamageAttribute();
        if (attr == null) return;

        AttributeInstance inst = attacker.getAttribute(attr);
        if (inst == null) return;

        float magicDamage = (float) inst.getValue();
        if (magicDamage <= 0) return;

        // Subtract the magic damage that Lodestone already applied via SCYTHE_MELEE
        float corrected = Math.max(0.0f, event.getAmount() - magicDamage);
        event.setAmount(corrected);
    }
}
