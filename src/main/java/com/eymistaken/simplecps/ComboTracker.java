package com.eymistaken.simplecps;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import java.util.UUID;

public class ComboTracker {
    private static int combo = 0;
    private static long lastHitTime = 0;
    private static UUID currentTargetUuid = null;

    public static void registerHit(Entity target, PlayerEntity attacker) {
        if (!(target instanceof PlayerEntity)) return;

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        
        // 1.9+ Modern Combat Logic
        if (config.combatMode == SimpleCPSConfig.CombatMode.MODERN) {
            float cooldown = attacker.getAttackCooldownProgress(0.5f);
            if (cooldown < 0.9f) {
                // Ignore spam/weak hits
                return;
            }
        }

        long now = System.currentTimeMillis();
        long timeoutMs = (long)(config.comboTimeout * 1000);

        // Check timeout
        if (now - lastHitTime > timeoutMs) {
            combo = 0;
        }

        UUID targetId = target.getUuid();
        // Check Target Switch
        if (currentTargetUuid != null && !currentTargetUuid.equals(targetId)) {
            if (!config.comboContinueOnSwitch) {
                combo = 0;
            }
        }

        currentTargetUuid = targetId;
        lastHitTime = now;
        combo++;
    }

    public static void onDamage(Entity attacker) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        
        if (config.comboResetOnAnyDamage) {
            reset();
            return;
        }

        // Reset only if damaged by current target
        if (attacker != null && currentTargetUuid != null) {
            if (attacker.getUuid().equals(currentTargetUuid)) {
                reset();
            }
        }
    }

    public static void reset() {
        combo = 0;
        currentTargetUuid = null;
    }

    public static int getCombo() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        long now = System.currentTimeMillis();
        long timeoutMs = (long)(config.comboTimeout * 1000);

        if (now - lastHitTime > timeoutMs) {
            return 0;
        }
        return combo;
    }
}
