package com.gabri.serverfixes.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Fixes Malum scythe's double magic damage bug.
 *
 * FIX: Intercept SCYTHE_SWEEP LivingHurtEvents and subtract the attacker's MAGIC_DAMAGE
 * attribute value from the damage amount.
 *
 * We use EventPriority.LOWEST to ensure this runs AFTER Lodestone has added its damage.
 */
@SuppressWarnings("all")
public class MalumScytheFix {
    private static final Logger LOGGER = LogManager.getLogger("ServerFixes/MalumFix");

    private static final ResourceLocation SCYTHE_SWEEP_RL = new ResourceLocation("malum", "scythe_sweep");
    private static final ResourceLocation MAGIC_DAMAGE_RL = new ResourceLocation("lodestone", "magic_damage");

    private static Attribute magicDamageAttr = null;

    private static Attribute getMagicDamageAttribute() {
        // Allow retry if attribute wasn't found yet (no caching flag)
        if (magicDamageAttr == null) {
            magicDamageAttr = ForgeRegistries.ATTRIBUTES.getValue(MAGIC_DAMAGE_RL);
            if (magicDamageAttr == null) {
                LOGGER.warn("[MalumFix] lodestone:magic_damage attribute not found!");
            }
        }
        return magicDamageAttr;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event == null || event.getEntity() == null) return;
        
        // Must run on server side only
        if (event.getEntity().level().isClientSide()) return;

        // Retrieve the damage type location safely
        ResourceLocation damageTypeRL = event.getSource().typeHolder()
            .unwrapKey()
            .map(k -> k.location())
            .orElse(null);

        if (damageTypeRL == null || !SCYTHE_SWEEP_RL.equals(damageTypeRL)) return;

        // Get the attacker
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;

        // Get the MAGIC_DAMAGE attribute
        Attribute attr = getMagicDamageAttribute();
        if (attr == null) return;

        AttributeInstance inst = attacker.getAttribute(attr);
        if (inst == null) return;

        float magicDamage = (float) inst.getValue();
        if (magicDamage <= 0) return;

        // Subtract the magic damage that was double-applied to the sweep
        float originalAmount = event.getAmount();
        float corrected = Math.max(0.0f, originalAmount - magicDamage);
        event.setAmount(corrected);
        
        LOGGER.debug("[MalumFix] Corrected scythe sweep damage: {} -> {} (removed {} magic)",
            originalAmount, corrected, magicDamage);
    }
}
