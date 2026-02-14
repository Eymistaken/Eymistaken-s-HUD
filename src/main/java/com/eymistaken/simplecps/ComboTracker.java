package com.eymistaken.simplecps;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import java.util.UUID;

public class ComboTracker {
    private static int combo = 0;
    private static long lastHitTime = 0;
    private static UUID currentTargetUuid = null;
    private static Entity currentTarget = null; // Direct reference for checks
    private static long lastDecayTime = 0;

    public static void registerHit(Entity target, PlayerEntity attacker) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (config.comboOnlyPlayers && !(target instanceof PlayerEntity)) return;
        
        // 1.9+ Modern Combat Logic
        if (config.combatMode == SimpleCPSConfig.CombatMode.MODERN) {
            float cooldown = attacker.getAttackCooldownProgress(0.5f);
            if (cooldown < 0.9f) return;
        }

        long now = System.currentTimeMillis();
        long timeoutMs = (long)(config.comboTimeout * 1000);

        // Check timeout
        if (now - lastHitTime > timeoutMs) {
            // Fix: If Decay is ON, do NOT reset combo on timeout, just continue
            // If Reset On Any Damage is ON (Legacy), we reset instantly on timeout.
            // If OFF (Advanced), we let decay handle it (so we skip this block).
            if (config.comboResetOnAnyDamage) {
                combo = 0;
            }
        }

        UUID targetId = target.getUuid();
        if (currentTargetUuid != null && !currentTargetUuid.equals(targetId)) {
            if (!config.comboContinueOnSwitch) {
                combo = 0;
            }
        }

        currentTargetUuid = targetId;
        currentTarget = target; // Update reference
        lastHitTime = now;
        lastDecayTime = now + timeoutMs; // Reset decay timer
        combo++;
    }

    public static void onTick(net.minecraft.client.MinecraftClient client) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        long now = System.currentTimeMillis();
        long timeoutMs = (long)(config.comboTimeout * 1000);

        // 1. Passive Decay (Only in Advanced Mode)
        if (!config.comboResetOnAnyDamage && combo > 0) {
            if (now > lastDecayTime) {
                if (now - lastDecayTime >= 1000) { // Every 1s
                    combo--;
                    lastDecayTime = now;
                    if (combo <= 0) reset();
                }
            }
        } else if (now - lastHitTime > timeoutMs && combo > 0) {
             // Standard Timeout Reset
             reset();
        }

        // 2. Target Validation (Anti-Run)
        if (combo > 0 && currentTarget != null) {
            boolean lost = false;
            
            // Distance Check (>20 blocks) (Only in Advanced Mode)
            if (!config.comboResetOnAnyDamage && client.player != null) {
                if (client.player.distanceTo(currentTarget) > 20) lost = true;
            }

            // Note: LOS Check removed per user request (prevents reset on wall placement)
            
            if (lost) {
                reset(); // Target Lost -> Reset Combo
            }
        }
    }

    public static void onDamage(Entity attacker) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        
        // Beta: Reset on ANY damage
        if (config.comboResetOnAnyDamage) {
            reset();
            return;
        }

        // Strict Logic
        // 1. If Target is ACTIVE (combo > 0): Only reset if damaged by TARGET
        if (combo > 0 && currentTargetUuid != null) {
            if (attacker != null && attacker.getUuid().equals(currentTargetUuid)) {
                reset(); // Damaged by Target -> Reset
            }
            // Damaged by others -> Ignore
        } 
        // 2. If Target is LOST/Inactive (combo 0): Reset freely (already 0)
        else {
            reset();
        }
    }

    public static void reset() {
        combo = 0;
        currentTargetUuid = null;
        currentTarget = null;
    }

    public static int getCombo() {
        return combo; // Decay handles resetting now
    }
}
