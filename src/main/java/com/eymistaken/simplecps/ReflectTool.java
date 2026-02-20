package com.eymistaken.simplecps;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectTool {
    public static void main(String[] args) {
        System.out.println("=== RenderLayer Constants ===");
        try {
            Class<?> rlClass = Class.forName("net.minecraft.client.render.RenderLayer");
            for (java.lang.reflect.Field f : rlClass.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == rlClass) {
                    System.out.println(f.getName() + " -> " + f.getType().getName());
                }
            }
        } catch (Exception e) {}
        System.out.println("=== END ===");
    }
}
