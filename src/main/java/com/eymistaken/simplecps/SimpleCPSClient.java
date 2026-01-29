package com.eymistaken.simplecps;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SimpleCPSClient implements ClientModInitializer {

    private static final List<Long> leftClicks = new ArrayList<>();
    private static final List<Long> rightClicks = new ArrayList<>();
    private static boolean wasLeftPressed = false;
    private static boolean wasRightPressed = false;
    
    private static float hue = 0;
    
    // Ping Cache
    private static volatile int cachedPing = 0;
    private static PlayerListEntry cachedEntry = null;
    private static int lastHurtTime = 0; 

    // Animation Scales
    private static float scaleW = 1.0f;
    private static float scaleA = 1.0f;
    private static float scaleS = 1.0f;
    private static float scaleD = 1.0f;
    private static float scaleSpace = 1.0f;

    @Override
    public void onInitializeClient() {
        // Load Config
        SimpleCPSConfig.load();
        System.out.println("Eymistaken's HUD (Standalone) Initialized!");
    }
    
    public static void onClientTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            cachedPing = 0;
            cachedEntry = null;
            return;
        }
        
        // Damage Detection Logic
        if (client.player != null) {
            if (client.player.hurtTime > lastHurtTime && client.player.hurtTime > 0) {
                Entity attacker = client.player.getAttacker();
                ComboTracker.onDamage(attacker);
            }
            lastHurtTime = client.player.hurtTime;
        }

        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        if (entry != null) {
            cachedEntry = entry;
            cachedPing = entry.getLatency();
        } else {
            String myName = client.player.getName().getString();
            Collection<PlayerListEntry> allPlayers = client.getNetworkHandler().getPlayerList();
            for (PlayerListEntry p : allPlayers) {
                if (p.getProfile() != null) {
                    String profileDump = p.getProfile().toString();
                    if (profileDump.contains("name='" + myName + "'") || profileDump.contains("name=" + myName)) {
                        cachedEntry = p;
                        cachedPing = p.getLatency();
                        break;
                    }
                }
            }
        }
    }

    public static void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        // Input
        boolean isLeftPressed = client.options.attackKey.isPressed();
        if (isLeftPressed && !wasLeftPressed) leftClicks.add(System.currentTimeMillis());
        wasLeftPressed = isLeftPressed;

        boolean isRightPressed = client.options.useKey.isPressed();
        if (isRightPressed && !wasRightPressed) rightClicks.add(System.currentTimeMillis());
        wasRightPressed = isRightPressed;

        long now = System.currentTimeMillis();
        leftClicks.removeIf(time -> now - time > 1000);
        rightClicks.removeIf(time -> now - time > 1000);

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();

        // Stacking Variables
        int topLeftY = 5, topRightY = 5, bottomLeftY = screenHeight - 5, bottomRightY = screenHeight - 5;
        int gap = 4;

        // --- 1. CPS ÇİZİMİ ---
        if (config.enabled) {
            String cpsText = String.valueOf(leftClicks.size());
            if (config.rightClickCps) cpsText += " | " + rightClicks.size();

            int color = config.rainbow ? getRainbowColor() : config.textColor;
            if ((color & 0xFF000000) == 0) color |= 0xFF000000;

            float scale = config.scale / 100f;
            int textHeight = (int)(client.textRenderer.fontHeight * scale);
            int textWidth = (int)(client.textRenderer.getWidth(cpsText) * scale);
            int padding = 2; 

            int x = 0, y = 0;
            switch (config.position) {
                case TOP_LEFT -> { x = 5; y = topLeftY; topLeftY += textHeight + gap + (config.cpsShowBackground ? padding * 2 : 0); }
                case TOP_RIGHT -> { x = screenWidth - textWidth - 5; y = topRightY; topRightY += textHeight + gap + (config.cpsShowBackground ? padding * 2 : 0); }
                case BOTTOM_LEFT -> { y = bottomLeftY - textHeight; bottomLeftY -= (textHeight + gap + (config.cpsShowBackground ? padding * 2 : 0)); x = 5; }
                case BOTTOM_RIGHT -> { y = bottomRightY - textHeight; bottomRightY -= (textHeight + gap + (config.cpsShowBackground ? padding * 2 : 0)); x = screenWidth - textWidth - 5; }
                case CENTER -> { x = (screenWidth - textWidth) / 2; y = (screenHeight - textHeight) / 2; }
            }

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)(x + config.xOffset), (float)(y + config.yOffset));
            drawContext.getMatrices().scale(scale, scale);
            
            if (config.cpsShowBackground) {
                int bgX = -padding;
                int bgY = -padding;
                int bgW = (int)(textWidth / scale) + (padding * 2); 
                int bgH = (int)(textHeight / scale) + (padding * 2);
                int bgAlphaColor = (config.cpsBackgroundOpacity << 24) | (config.cpsBackgroundColor & 0x00FFFFFF);
                drawContext.fill(bgX, bgY, bgX + bgW, bgY + bgH, bgAlphaColor);
            }
            
            drawContext.drawTextWithShadow(client.textRenderer, cpsText, 0, 0, color);
            drawContext.getMatrices().popMatrix();
        }

        // --- 2. PING ÇİZİMİ ---
        if (config.showPing) {
            int latency = cachedPing;
            if (cachedEntry != null) latency = cachedEntry.getLatency();
            String pingText = latency + " ms";
            
            float scale = 1.0f;
            int textHeight = (int)(client.textRenderer.fontHeight * scale);
            int textWidth = (int)(client.textRenderer.getWidth(pingText) * scale);
            int padding = 2;
            int pColor = config.pingColor;
            if ((pColor & 0xFF000000) == 0) pColor |= 0xFF000000;

            int x = 0, y = 0;
            switch (config.pingPosition) {
                case TOP_LEFT -> { x = 5; y = topLeftY; topLeftY += textHeight + gap + (config.pingShowBackground ? padding * 2 : 0); }
                case TOP_RIGHT -> { x = screenWidth - textWidth - 5; y = topRightY; topRightY += textHeight + gap + (config.pingShowBackground ? padding * 2 : 0); }
                case BOTTOM_LEFT -> { y = bottomLeftY - textHeight; bottomLeftY -= (textHeight + gap + (config.pingShowBackground ? padding * 2 : 0)); x = 5; }
                case BOTTOM_RIGHT -> { y = bottomRightY - textHeight; bottomRightY -= (textHeight + gap + (config.pingShowBackground ? padding * 2 : 0)); x = screenWidth - textWidth - 5; }
                case CENTER -> { x = (screenWidth - textWidth) / 2; y = (screenHeight - textHeight) / 2; }
            }

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)(x + config.pingXOffset), (float)(y + config.pingYOffset));
            drawContext.getMatrices().scale(scale, scale);
            
            if (config.pingShowBackground) {
                int bgX = -padding;
                int bgY = -padding;
                int bgW = (int)(textWidth / scale) + (padding * 2);
                int bgH = (int)(textHeight / scale) + (padding * 2);
                int bgAlphaColor = (config.pingBackgroundOpacity << 24) | (config.pingBackgroundColor & 0x00FFFFFF);
                drawContext.fill(bgX, bgY, bgX + bgW, bgY + bgH, bgAlphaColor);
            }

            drawContext.drawTextWithShadow(client.textRenderer, pingText, 0, 0, pColor);
            drawContext.getMatrices().popMatrix();
        }

        // --- 3. FPS ÇİZİMİ ---
        if (config.showFps) {
            String fpsStr = client.getCurrentFps() + " " + config.fpsText;
            
            float scale = config.fpsScale / 100f;
            int textHeight = (int)(client.textRenderer.fontHeight * scale);
            int textWidth = (int)(client.textRenderer.getWidth(fpsStr) * scale);
            int padding = 2;
            
            int color = config.fpsRainbow ? getRainbowColor() : config.fpsColor;
            if ((color & 0xFF000000) == 0) color |= 0xFF000000;

            int x = 0, y = 0;
            switch (config.fpsPosition) {
                case TOP_LEFT -> { x = 5; y = topLeftY; topLeftY += textHeight + gap + (config.fpsShowBackground ? padding * 2 : 0); }
                case TOP_RIGHT -> { x = screenWidth - textWidth - 5; y = topRightY; topRightY += textHeight + gap + (config.fpsShowBackground ? padding * 2 : 0); }
                case BOTTOM_LEFT -> { y = bottomLeftY - textHeight; bottomLeftY -= (textHeight + gap + (config.fpsShowBackground ? padding * 2 : 0)); x = 5; }
                case BOTTOM_RIGHT -> { y = bottomRightY - textHeight; bottomRightY -= (textHeight + gap + (config.fpsShowBackground ? padding * 2 : 0)); x = screenWidth - textWidth - 5; }
                case CENTER -> { x = (screenWidth - textWidth) / 2; y = (screenHeight - textHeight) / 2; }
            }

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)(x + config.fpsXOffset), (float)(y + config.fpsYOffset));
            drawContext.getMatrices().scale(scale, scale);
            
            if (config.fpsShowBackground) {
                int bgX = -padding;
                int bgY = -padding;
                int bgW = (int)(textWidth / scale) + (padding * 2);
                int bgH = (int)(textHeight / scale) + (padding * 2);
                int bgAlphaColor = (config.fpsBackgroundOpacity << 24) | (config.fpsBackgroundColor & 0x00FFFFFF);
                drawContext.fill(bgX, bgY, bgX + bgW, bgY + bgH, bgAlphaColor);
            }

            drawContext.drawTextWithShadow(client.textRenderer, fpsStr, 0, 0, color);
            drawContext.getMatrices().popMatrix();
        }

        // --- 4. COMBO ÇİZİMİ ---
        boolean shouldRenderCombo = config.showCombo && (ComboTracker.getCombo() > 0 || !config.comboHideWhenInactive);
        if (shouldRenderCombo) {
            String comboStr = ComboTracker.getCombo() + " " + config.comboText;
            
            float scale = config.comboScale / 100f;
            int textHeight = (int)(client.textRenderer.fontHeight * scale);
            int textWidth = (int)(client.textRenderer.getWidth(comboStr) * scale);
            int padding = 2;

            int color = config.comboRainbow ? getRainbowColor() : config.comboColor;
            if ((color & 0xFF000000) == 0) color |= 0xFF000000;

            int x = 0, y = 0;
            switch (config.comboPosition) {
                case TOP_LEFT -> { x = 5; y = topLeftY; topLeftY += textHeight + gap + (config.comboShowBackground ? padding * 2 : 0); }
                case TOP_RIGHT -> { x = screenWidth - textWidth - 5; y = topRightY; topRightY += textHeight + gap + (config.comboShowBackground ? padding * 2 : 0); }
                case BOTTOM_LEFT -> { y = bottomLeftY - textHeight; bottomLeftY -= (textHeight + gap + (config.comboShowBackground ? padding * 2 : 0)); x = 5; }
                case BOTTOM_RIGHT -> { y = bottomRightY - textHeight; bottomRightY -= (textHeight + gap + (config.comboShowBackground ? padding * 2 : 0)); x = screenWidth - textWidth - 5; }
                case CENTER -> { x = (screenWidth - textWidth) / 2; y = (screenHeight - textHeight) / 2; }
            }

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)(x + config.comboXOffset), (float)(y + config.comboYOffset));
            drawContext.getMatrices().scale(scale, scale);

            if (config.comboShowBackground) {
                int bgX = -padding;
                int bgY = -padding;
                int bgW = (int)(textWidth / scale) + (padding * 2);
                int bgH = (int)(textHeight / scale) + (padding * 2);
                int bgAlphaColor = (config.comboBackgroundOpacity << 24) | (config.comboBackgroundColor & 0x00FFFFFF);
                drawContext.fill(bgX, bgY, bgX + bgW, bgY + bgH, bgAlphaColor);
            }

            drawContext.drawTextWithShadow(client.textRenderer, comboStr, 0, 0, color);
            drawContext.getMatrices().popMatrix();
        }

        // --- 5. KEYSTROKES ÇİZİMİ ---
        if (config.showKeystrokes) {
            float kScale = config.keystrokesScale / 100f;
            int boxSize = 20;
            int spacing = 2;
            
            int totalWidth = (int)(((boxSize * 3) + (spacing * 2)) * kScale);
            int totalHeight = (int)(((boxSize * 2) + 12 + (spacing * 2)) * kScale); 

            int x = 0, y = 0;
            switch (config.keystrokesPosition) {
                case TOP_LEFT -> { x = 5; y = topLeftY; topLeftY += totalHeight + gap; }
                case TOP_RIGHT -> { x = screenWidth - totalWidth - 5; y = topRightY; topRightY += totalHeight + gap; }
                case BOTTOM_LEFT -> { y = bottomLeftY - totalHeight; bottomLeftY -= (totalHeight + gap); x = 5; }
                case BOTTOM_RIGHT -> { y = bottomRightY - totalHeight; bottomRightY -= (totalHeight + gap); x = screenWidth - totalWidth - 5; }
                case CENTER -> { x = (screenWidth - totalWidth) / 2; y = (screenHeight - totalHeight) / 2; }
            }

            x += config.keystrokesXOffset;
            y += config.keystrokesYOffset;

            String txtW = "W", txtA = "A", txtS = "S", txtD = "D", txtSp = "----";
            if (config.keystrokesMode == SimpleCPSConfig.KeystrokesMode.ARROWS) {
                txtW = "▲"; txtA = "◀"; txtS = "▼"; txtD = "▶";
            } else if (config.keystrokesMode == SimpleCPSConfig.KeystrokesMode.CUSTOM) {
                txtW = config.customW; txtA = config.customA; txtS = config.customS; 
                txtD = config.customD; txtSp = config.customSpace;
            }

            int normalColor = config.keystrokesColor;
            if ((normalColor & 0xFF000000) == 0) normalColor |= 0xFF000000;
            
            int pressedColor = config.keystrokesPressedColor;
            if ((pressedColor & 0xFF000000) == 0) pressedColor |= 0xFF000000;
            
            int baseBgColor = (config.keystrokesBackgroundOpacity << 24) | (config.keystrokesBackgroundColor & 0x00FFFFFF);

            if (config.keystrokesRainbow) {
                int rainbow = getRainbowColor();
                if (config.keystrokesRainbowTarget == SimpleCPSConfig.RainbowTarget.TEXT) {
                    normalColor = rainbow;
                    pressedColor = rainbow; 
                } else {
                    baseBgColor = (config.keystrokesBackgroundOpacity << 24) | (rainbow & 0x00FFFFFF);
                }
            }

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)x, (float)y);
            drawContext.getMatrices().scale(kScale, kScale);

            boolean wPressed = client.options.forwardKey.isPressed();
            scaleW = updateAnimation(scaleW, wPressed);
            drawAnimatedKey(drawContext, client, txtW, boxSize + spacing, 0, boxSize, boxSize, wPressed ? pressedColor : normalColor, baseBgColor, scaleW);

            boolean aPressed = client.options.leftKey.isPressed();
            scaleA = updateAnimation(scaleA, aPressed);
            drawAnimatedKey(drawContext, client, txtA, 0, boxSize + spacing, boxSize, boxSize, aPressed ? pressedColor : normalColor, baseBgColor, scaleA);

            boolean sPressed = client.options.backKey.isPressed();
            scaleS = updateAnimation(scaleS, sPressed);
            drawAnimatedKey(drawContext, client, txtS, boxSize + spacing, boxSize + spacing, boxSize, boxSize, sPressed ? pressedColor : normalColor, baseBgColor, scaleS);

            boolean dPressed = client.options.rightKey.isPressed();
            scaleD = updateAnimation(scaleD, dPressed);
            drawAnimatedKey(drawContext, client, txtD, (boxSize + spacing) * 2, boxSize + spacing, boxSize, boxSize, dPressed ? pressedColor : normalColor, baseBgColor, scaleD);
            
            boolean spacePressed = client.options.jumpKey.isPressed();
            scaleSpace = updateAnimation(scaleSpace, spacePressed);
            int spaceY = (boxSize + spacing) * 2;
            int spaceWidth = (boxSize * 3) + (spacing * 2);
            drawAnimatedKey(drawContext, client, txtSp, 0, spaceY, spaceWidth, 12, spacePressed ? pressedColor : normalColor, baseBgColor, scaleSpace);

            drawContext.getMatrices().popMatrix();
        }
    }

    private static float updateAnimation(float currentScale, boolean isPressed) {
        float target = isPressed ? 0.85f : 1.0f;
        float speed = 0.2f;
        float diff = target - currentScale;
        if (Math.abs(diff) < 0.01f) return target;
        return currentScale + (diff * speed);
    }

    private static void drawAnimatedKey(DrawContext context, MinecraftClient client, String key, int x, int y, int w, int h, int textColor, int bgColor, float animScale) {
        context.getMatrices().pushMatrix();
        
        float centerX = x + (w / 2.0f);
        float centerY = y + (h / 2.0f);
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().scale(animScale, animScale);
        context.getMatrices().translate(-centerX, -centerY);
        
        context.fill(x, y, x + w, y + h, bgColor);
        
        int textWidth = client.textRenderer.getWidth(key);
        int textHeight = client.textRenderer.fontHeight;
        context.drawText(client.textRenderer, key, x + (w - textWidth) / 2, y + (h - textHeight) / 2, textColor, false);
        
        context.getMatrices().popMatrix();
    }

    private static int getRainbowColor() {
        hue += 0.5f; 
        if (hue > 360) hue = 0;
        int rgb = MathHelper.hsvToRgb(hue / 360f, 1.0f, 1.0f);
        return rgb | 0xFF000000;
    }
}