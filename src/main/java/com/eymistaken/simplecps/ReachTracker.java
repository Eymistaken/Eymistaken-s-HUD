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
        
        Vec3d cameraPos = attacker.getCameraPosVec(1.0F);
        
        if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            net.minecraft.util.hit.EntityHitResult entityHit = (net.minecraft.util.hit.EntityHitResult) client.crosshairTarget;
            if (entityHit.getEntity() == target) {
                reach = entityHit.getPos().distanceTo(cameraPos);
            }
        }
        
        if (reach == -1) {
            net.minecraft.util.math.Box box = target.getBoundingBox();
            double dx = Math.max(0.0, Math.max(box.minX - cameraPos.x, cameraPos.x - box.maxX));
            double dy = Math.max(0.0, Math.max(box.minY - cameraPos.y, cameraPos.y - box.maxY));
            double dz = Math.max(0.0, Math.max(box.minZ - cameraPos.z, cameraPos.z - box.maxZ));
            reach = Math.sqrt(dx * dx + dy * dy + dz * dz);
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
