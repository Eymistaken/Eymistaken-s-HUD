package com.eymistaken.simplecps;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import java.util.UUID;

public class ComboTracker {
    private static int combo = 0;
    private static long lastHitTime = 0;
    private static int currentTargetId = -1;
    private static UUID currentTargetUuid = null;
    private static long lastDecayTime = 0;

    private static int lastHurtTime = 0;
    private static UUID lastAttackerUuid = null;

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
        currentTargetId = target.getId();
        lastAttackerUuid = currentTargetUuid; // Update the attacker UUID
        lastHitTime = now;
        lastDecayTime = now + timeoutMs; // Reset decay timer
        combo++;
    }

    public static void onTick(net.minecraft.client.MinecraftClient client) {
        if (client.player != null) {
            int currentHurtTime = client.player.hurtTime;
            if (lastHurtTime == 0 && currentHurtTime > 0) {
                SimpleCPSConfig config = SimpleCPSConfig.instance;
                if (config.comboResetOnAnyDamage) {
                    reset();
                } else if (lastAttackerUuid != null && currentTargetUuid != null && lastAttackerUuid.equals(currentTargetUuid)) {
                    reset();
                }
            }
            lastHurtTime = currentHurtTime;
        }

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
        if (combo > 0 && currentTargetId != -1) {
            boolean lost = false;
            
            Entity currentTarget = null;
            if (client.world != null) {
                currentTarget = client.world.getEntityById(currentTargetId);
                if (currentTarget == null || !currentTarget.getUuid().equals(currentTargetUuid)) {
                    currentTarget = null;
                }
            }
            
            // Distance Check (>20 blocks) (Only in Advanced Mode)
            if (!config.comboResetOnAnyDamage && client.player != null && currentTarget != null) {
                if (client.player.distanceTo(currentTarget) > 20) lost = true;
            }
            
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
        currentTargetId = -1;
    }

    public static int getCombo() {
        return combo; // Decay handles resetting now
    }
}
