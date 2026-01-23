package com.eymistaken.simplecps;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.awt.Color;

public class SimpleCPSClient implements ClientModInitializer {

    private static final long[] leftClicks = new long[20];
    private static final long[] rightClicks = new long[20];
    private static int leftIndex = 0;
    private static int rightIndex = 0;
    private static boolean wasLeftPressed = false;
    private static boolean wasRightPressed = false;

    @Override
    public void onInitializeClient() {
        SimpleCPSConfig.load();

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && !client.options.hudHidden) {
                renderCPS(drawContext);
            }
        });
    }

    private static void renderCPS(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.player != null) updateClicks(client);
        
        int lCps = getCPS(leftClicks);
        int rCps = getCPS(rightClicks);
        String text = lCps + (SimpleCPSConfig.showRightClick ? " | " + rCps : "");

        // --- COLORS ---
        int finalColor;
        switch (SimpleCPSConfig.colorMode) {
            case RED:       finalColor = 0xFFFF5555; break;
            case GREEN:     finalColor = 0xFF55FF55; break;
            case BLUE:      finalColor = 0xFF5555FF; break;
            case GOLD:      finalColor = 0xFFFFAA00; break;
            case CUSTOM:    finalColor = SimpleCPSConfig.color; break;
            case RAINBOW:
                float hue = (System.currentTimeMillis() % 3000) / 3000f;
                finalColor = Color.HSBtoRGB(hue, 1f, 1f);
                break;
            case WHITE:
            default:        finalColor = 0xFFFFFFFF; break;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        // --- SCALING (HATA ÇÖZÜLDÜ: SADECE X VE Y) ---
        context.getMatrices().pushMatrix();
        
        float scale = SimpleCPSConfig.scale;
        context.getMatrices().scale(scale, scale); // 1.0f kaldırıldı!

        int textWidth = client.textRenderer.getWidth(text);
        int textHeight = client.textRenderer.fontHeight;

        int scaledScreenWidth = (int) (screenWidth / scale);
        int scaledScreenHeight = (int) (screenHeight / scale);

        int finalX = 4;
        int finalY = 4;
        int offsetX = SimpleCPSConfig.x;
        int offsetY = SimpleCPSConfig.y;

        switch (SimpleCPSConfig.anchor) {
            case TOP_LEFT:      finalX = offsetX; finalY = offsetY; break;
            case TOP_RIGHT:     finalX = scaledScreenWidth - textWidth - offsetX; finalY = offsetY; break;
            case TOP_CENTER:    finalX = (scaledScreenWidth / 2) - (textWidth / 2) + offsetX; finalY = offsetY; break;
            case BOTTOM_LEFT:   finalX = offsetX; finalY = scaledScreenHeight - textHeight - offsetY; break;
            case BOTTOM_RIGHT:  finalX = scaledScreenWidth - textWidth - offsetX; finalY = scaledScreenHeight - textHeight - offsetY; break;
            case BOTTOM_CENTER: finalX = (scaledScreenWidth / 2) - (textWidth / 2) + offsetX; finalY = scaledScreenHeight - textHeight - offsetY; break;
        }

        context.drawText(client.textRenderer, text, finalX, finalY, finalColor, true);
        
        context.getMatrices().popMatrix();
    }

    private static void updateClicks(MinecraftClient client) {
        boolean isLeft = client.mouse.wasLeftButtonClicked();
        if (isLeft && !wasLeftPressed) {
            leftClicks[leftIndex] = System.currentTimeMillis();
            leftIndex = (leftIndex + 1) % leftClicks.length;
        }
        wasLeftPressed = isLeft;

        boolean isRight = client.mouse.wasRightButtonClicked();
        if (isRight && !wasRightPressed) {
            rightClicks[rightIndex] = System.currentTimeMillis();
            rightIndex = (rightIndex + 1) % rightClicks.length;
        }
        wasRightPressed = isRight;
    }

    private static int getCPS(long[] clicks) {
        long time = System.currentTimeMillis();
        int cps = 0;
        for (long t : clicks) {
            if (time - t < 1000) cps++;
        }
        return cps;
    }
}