package com.gabri.serverfixes.mixin;

import com.gabri.serverfixes.config.ServerFixesConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Throttling para Contraptions do Create quando estão paradas (idle).
 * Reduz o processamento de contraptions que não estão se movendo, economizando TPS.
 * 
 * A taxa de throttle é configurável via contraptionIdleThrottleRate.
 * Ex: rate = 100 significa que a contraption só processa 1 tick a cada 100 ticks quando idle.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.contraptions.ContraptionEntity", remap = false)
public abstract class ContraptionIdleThrottlingMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void serverfixes$applyIdleThrottling(CallbackInfo ci) {
        // Verifica se throttling está ativo
        if (!ServerFixesConfig.THROTTLE_IDLE_CONTRAPTIONS.get()) return;

        int rate = ServerFixesConfig.CONTRAPTION_IDLE_THROTTLE_RATE.get();
        if (rate <= 1) return; // Sem throttling se rate <= 1

        // Verifica se é server-side via reflection (classe não está no classpath)
        try {
            Entity self = (Entity) (Object) this;
            Level level = self.level();
            if (level.isClientSide()) return;

            // Verifica se está stalled (travada) via reflection
            java.lang.reflect.Field stalledField = self.getClass().getDeclaredField("stalled");
            stalledField.setAccessible(true);
            long stalledValue = stalledField.getLong(self);

            // Verifica se tem passageiros
            @SuppressWarnings("unchecked")
            List<Entity> passengers = (List<Entity>) self.getPassengers();

            // Se está travada e sem passageiros, aplica throttling
            if (stalledValue > 0 && passengers.isEmpty()) {
                if (level.getGameTime() % rate != 0) {
                    ci.cancel();
                }
            }
        } catch (Exception ignored) {
            // Se não consegue acessar os campos, não aplica throttling (segurança)
        }
    }
}
