package com.eymistaken.simplecps;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class ReachTracker {
    private static double lastReach = -1;
    private static long lastHitTime = 0;

    public static void onAttack(Entity target, Player attacker) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (config.reachOnlyPlayers && !(target instanceof Player)) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        
        // Calculate reach using crosshair target if available for precision
        double reach = -1;
        
        Vec3 cameraPos = attacker.getEyePosition(1.0F);
        
        if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY) {
            net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) client.hitResult;
            if (entityHit.getEntity() == target) {
                reach = entityHit.getLocation().distanceTo(cameraPos);
            }
        }
        
        if (reach == -1) {
            net.minecraft.world.phys.AABB box = target.getBoundingBox();
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
