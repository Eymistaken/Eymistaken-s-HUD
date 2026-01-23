package com.eymistaken.simplecps;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.awt.Color; // Renk hesaplaması için gerekli

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
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> renderCPS(drawContext));
    }

    private void renderCPS(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        updateClicks(client);
        
        int lCps = getCPS(leftClicks);
        int rCps = getCPS(rightClicks);
        String text = lCps + (SimpleCPSConfig.showRightClick ? " | " + rCps : "");

        // --- RENK HESAPLAMA ---
        int finalColor;
        
        switch (SimpleCPSConfig.colorMode) {
            case RED:       finalColor = 0xFFFF5555; break;
            case GREEN:     finalColor = 0xFF55FF55; break;
            case BLUE:      finalColor = 0xFF5555FF; break;
            case GOLD:      finalColor = 0xFFFFAA00; break;
            case CUSTOM:    finalColor = SimpleCPSConfig.color; break;
            case RAINBOW:
                // Gökkuşağı Matematiği: Zamanı (ms) 3 saniyeye bölüp renk tonu elde et
                float hue = (System.currentTimeMillis() % 3000) / 3000f;
                // Java'nın renk dönüştürücüsünü kullan (Saturation: 1, Brightness: 1)
                finalColor = Color.HSBtoRGB(hue, 1f, 1f);
                break;
            case WHITE:
            default:        finalColor = 0xFFFFFFFF; break;
        }

        // --- KONUM HESAPLAMA ---
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        int textWidth = client.textRenderer.getWidth(text);
        int textHeight = client.textRenderer.fontHeight;
        
        int offsetX = SimpleCPSConfig.x;
        int offsetY = SimpleCPSConfig.y;

        int finalX = 4;
        int finalY = 4;

        switch (SimpleCPSConfig.anchor) {
            case TOP_LEFT:      finalX = offsetX; finalY = offsetY; break;
            case TOP_RIGHT:     finalX = screenWidth - textWidth - offsetX; finalY = offsetY; break;
            case TOP_CENTER:    finalX = (screenWidth / 2) - (textWidth / 2) + offsetX; finalY = offsetY; break;
            case BOTTOM_LEFT:   finalX = offsetX; finalY = screenHeight - textHeight - offsetY; break;
            case BOTTOM_RIGHT:  finalX = screenWidth - textWidth - offsetX; finalY = screenHeight - textHeight - offsetY; break;
            case BOTTOM_CENTER: finalX = (screenWidth / 2) - (textWidth / 2) + offsetX; finalY = screenHeight - textHeight - offsetY; break;
        }

        context.drawText(client.textRenderer, text, finalX, finalY, finalColor, true);
    }

    private void updateClicks(MinecraftClient client) {
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

    private int getCPS(long[] clicks) {
        long time = System.currentTimeMillis();
        int cps = 0;
        for (long t : clicks) {
            if (time - t < 1000) cps++;
        }
        return cps;
    }
}