package com.eymistaken.simplecps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class ReachTracker {
    private static double lastReach = -1;
    private static long lastHitTime = 0;

    public static void onAttack(Entity target, PlayerEntity attacker) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (config.reachOnlyPlayers && !(target instanceof PlayerEntity)) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        
        // Calculate reach using crosshair target if available for precision
        double reach = -1;
        
        if (client.crosshairTarget != null && client.crosshairTarget.getType() != HitResult.Type.MISS) {
            Vec3d cameraPos = attacker.getCameraPosVec(1.0F);
            reach = client.crosshairTarget.getPos().distanceTo(cameraPos);
        } else {
             // Fallback to simple distance if crosshair miss (should rarely happen on successful attack)
             reach = attacker.distanceTo(target);
        }

        lastReach = reach;
        lastHitTime = System.currentTimeMillis();
    }

    public static String getReachDisplay() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        long now = System.currentTimeMillis();
        long timeoutMs = (long)(config.reachTimeout * 1000);

        if (now - lastHitTime > timeoutMs) {
             if (config.reachAlwaysShow) {
                return config.reachNoHitText;
             }
            return null;
        }

        return String.format("%.2f blocks", lastReach);
    }
    
    public static double getLastReach() {
        return lastReach;
    }
}
