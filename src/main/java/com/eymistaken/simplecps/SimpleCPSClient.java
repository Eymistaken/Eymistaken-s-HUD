package com.eymistaken.simplecps;

import com.eymistaken.simplecps.gui.HudEditorScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleCPSClient implements ClientModInitializer {

    private static final List<Long> leftClicks = new ArrayList<>();
    private static final List<Long> rightClicks = new ArrayList<>();
    private static boolean wasLeftPressed = false;
    private static boolean wasRightPressed = false;
    
    private static float hue = 0;
    
    // Ping Cache
    private static volatile int cachedPing = -1;
    private static PlayerListEntry cachedEntry = null;
    private static int lastHurtTime = 0; 

    // Animation Scales
    private static float scaleW = 1.0f;
    private static float scaleA = 1.0f;
    private static float scaleS = 1.0f;
    private static float scaleD = 1.0f;
    private static float scaleSpace = 1.0f;

    // Module Bounds for Editor
    public static class ModuleBounds {
        public int x, y, w, h;
        public ModuleBounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
    public static final Map<String, ModuleBounds> MODULE_BOUNDS = new HashMap<>();

    @Override
    public void onInitializeClient() {
        // Load Config
        SimpleCPSConfig.load();
        System.out.println("Eymistaken's HUD (Standalone) Initialized!");
    }
    
    public static void onClientTick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) {
            cachedPing = -1;
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

        // Only use UUID-based lookup - no name fallback
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());

        if (entry != null) {
            cachedEntry = entry;
            cachedPing = entry.getLatency();
        } else {
            // UUID bulunamadı - ping bilinmiyor
            cachedEntry = null;
            cachedPing = -1;
        }
    }

    public static void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return; // Check main HUD hidden
        
        boolean isEditor = client.currentScreen instanceof HudEditorScreen;
        if (client.options.hudHidden && !isEditor) return; 
        
        MODULE_BOUNDS.clear();

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
        int stackGap = 5;
        // The stacks accumulate height for items with 0 offsets
        int topLeftStack = stackGap;
        int topRightStack = stackGap;
        int bottomLeftStack = screenHeight - stackGap;
        int bottomRightStack = screenHeight - stackGap;
        int centerStack = (screenHeight) / 2; // Center stack usually not used but logic exists

        int gap = 4; // Between items in stack

        // --- 1. CPS ---
        if (config.enabled || isEditor) {
            String cpsText = String.valueOf(leftClicks.size());
            if (config.rightClickCps) cpsText += " | " + rightClicks.size();

            int color = config.rainbow ? getRainbowColor() : config.textColor;
            if ((color & 0xFF000000) == 0) color |= 0xFF000000;

            float scale = config.scale / 100f;
            int textHeight = (int)(client.textRenderer.fontHeight * scale);
            int textWidth = (int)(client.textRenderer.getWidth(cpsText) * scale);
            int padding = 2; 

            // Calculate Base Position
            int x = 0, y = 0;
            boolean detached = config.xOffset != 0 || config.yOffset != 0;
            
            // Stacking Logic
            int usedHeight = textHeight + (config.cpsShowBackground ? padding * 2 : 0);
            
            switch (config.position) {
                case TOP_LEFT -> { 
                    x = stackGap; 
                    y = detached ? stackGap : topLeftStack; 
                    if(!detached) topLeftStack += usedHeight + gap; 
                }
                case TOP_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                    y = detached ? stackGap : topRightStack; 
                    if(!detached) topRightStack += usedHeight + gap; 
                }
                case BOTTOM_LEFT -> { 
                    x = stackGap; 
                    // Bottom stack grows UPwards
                    if (!detached) bottomLeftStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomLeftStack;
                    if (!detached) bottomLeftStack -= gap;
                }
                case BOTTOM_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                     if (!detached) bottomRightStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomRightStack;
                    if (!detached) bottomRightStack -= gap;
                }
                case CENTER -> { 
                    x = (screenWidth - textWidth) / 2; 
                    y = (screenHeight - textHeight) / 2; 
                }
            }

            int finalX = x + config.xOffset;
            int finalY = y + config.yOffset;
            
            // Bounds
            int boundW = config.cpsShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
            int boundH = config.cpsShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
            int boundX = config.cpsShowBackground ? finalX - (int)(padding * scale) : finalX;
            int boundY = config.cpsShowBackground ? finalY - (int)(padding * scale) : finalY;
            
            MODULE_BOUNDS.put("CPS", new ModuleBounds(boundX, boundY, boundW, boundH));

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)finalX, (float)finalY);
            drawContext.getMatrices().scale(scale, scale);
            
            if (config.cpsShowBackground) {
                int bgX = -padding;
                int bgY = -padding;
                int bgW_local = (int)(textWidth / scale) + (padding * 2); 
                int bgH_local = (int)(textHeight / scale) + (padding * 2);
                int bgAlphaColor = (config.cpsBackgroundOpacity << 24) | (config.cpsBackgroundColor & 0x00FFFFFF);
                drawContext.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
            }
            
            drawContext.drawTextWithShadow(client.textRenderer, cpsText, 0, 0, color);
            drawContext.getMatrices().popMatrix();
        }

        // --- 2. PING ---
        if (config.showPing || isEditor) {
            int latency = cachedPing;
            if (cachedEntry != null) latency = cachedEntry.getLatency();
            String pingText = (latency < 0) ? "? ms" : (latency + " ms");
            
            float scale = 1.0f; 
            int textHeight = (int)(client.textRenderer.fontHeight * scale);
            int textWidth = (int)(client.textRenderer.getWidth(pingText) * scale);
            int padding = 2;
            int pColor = config.pingColor;
            if ((pColor & 0xFF000000) == 0) pColor |= 0xFF000000;

            int x = 0, y = 0;
            boolean detached = config.pingXOffset != 0 || config.pingYOffset != 0;
            int usedHeight = textHeight + (config.pingShowBackground ? padding * 2 : 0);

             switch (config.pingPosition) {
                case TOP_LEFT -> { 
                    x = stackGap; 
                    y = detached ? stackGap : topLeftStack; 
                    if(!detached) topLeftStack += usedHeight + gap; 
                }
                case TOP_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                    y = detached ? stackGap : topRightStack; 
                    if(!detached) topRightStack += usedHeight + gap; 
                }
                case BOTTOM_LEFT -> { 
                    x = stackGap; 
                    if (!detached) bottomLeftStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomLeftStack;
                    if (!detached) bottomLeftStack -= gap;
                }
                case BOTTOM_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                     if (!detached) bottomRightStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomRightStack;
                    if (!detached) bottomRightStack -= gap;
                }
                case CENTER -> { 
                    x = (screenWidth - textWidth) / 2; 
                    y = (screenHeight - textHeight) / 2; 
                }
            }
            
            int finalX = x + config.pingXOffset;
            int finalY = y + config.pingYOffset;

            int boundW = config.pingShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
            int boundH = config.pingShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
            int boundX = config.pingShowBackground ? finalX - (int)(padding * scale) : finalX;
            int boundY = config.pingShowBackground ? finalY - (int)(padding * scale) : finalY;
            
            MODULE_BOUNDS.put("Ping", new ModuleBounds(boundX, boundY, boundW, boundH));

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)finalX, (float)finalY);
            drawContext.getMatrices().scale(scale, scale);
            
            if (config.pingShowBackground) {
                int bgX = -padding;
                int bgY = -padding;
                int bgW_local = (int)(textWidth / scale) + (padding * 2);
                int bgH_local = (int)(textHeight / scale) + (padding * 2);
                int bgAlphaColor = (config.pingBackgroundOpacity << 24) | (config.pingBackgroundColor & 0x00FFFFFF);
                drawContext.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
            }

            drawContext.drawTextWithShadow(client.textRenderer, pingText, 0, 0, pColor);
            drawContext.getMatrices().popMatrix();
        }

        // --- 3. FPS ---
        if (config.showFps || isEditor) {
            String fpsStr = client.getCurrentFps() + " " + config.fpsText;
            
            float scale = config.fpsScale / 100f;
            int textHeight = (int)(client.textRenderer.fontHeight * scale);
            int textWidth = (int)(client.textRenderer.getWidth(fpsStr) * scale);
            int padding = 2;
            
            int color = config.fpsRainbow ? getRainbowColor() : config.fpsColor;
            if ((color & 0xFF000000) == 0) color |= 0xFF000000;

            int x = 0, y = 0;
            boolean detached = config.fpsXOffset != 0 || config.fpsYOffset != 0;
            int usedHeight = textHeight + (config.fpsShowBackground ? padding * 2 : 0);

            switch (config.fpsPosition) {
                case TOP_LEFT -> { 
                    x = stackGap; 
                    y = detached ? stackGap : topLeftStack; 
                    if(!detached) topLeftStack += usedHeight + gap; 
                }
                case TOP_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                    y = detached ? stackGap : topRightStack; 
                    if(!detached) topRightStack += usedHeight + gap; 
                }
                case BOTTOM_LEFT -> { 
                    x = stackGap; 
                    if (!detached) bottomLeftStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomLeftStack;
                    if (!detached) bottomLeftStack -= gap;
                }
                case BOTTOM_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                     if (!detached) bottomRightStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomRightStack;
                    if (!detached) bottomRightStack -= gap;
                }
                case CENTER -> { 
                    x = (screenWidth - textWidth) / 2; 
                    y = (screenHeight - textHeight) / 2; 
                }
            }
            
            int finalX = x + config.fpsXOffset;
            int finalY = y + config.fpsYOffset;

            int boundW = config.fpsShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
            int boundH = config.fpsShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
            int boundX = config.fpsShowBackground ? finalX - (int)(padding * scale) : finalX;
            int boundY = config.fpsShowBackground ? finalY - (int)(padding * scale) : finalY;
            
            MODULE_BOUNDS.put("FPS", new ModuleBounds(boundX, boundY, boundW, boundH));

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)finalX, (float)finalY);
            drawContext.getMatrices().scale(scale, scale);
            
            if (config.fpsShowBackground) {
                int bgX = -padding;
                int bgY = -padding;
                int bgW_local = (int)(textWidth / scale) + (padding * 2);
                int bgH_local = (int)(textHeight / scale) + (padding * 2);
                int bgAlphaColor = (config.fpsBackgroundOpacity << 24) | (config.fpsBackgroundColor & 0x00FFFFFF);
                drawContext.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
            }

            drawContext.drawTextWithShadow(client.textRenderer, fpsStr, 0, 0, color);
            drawContext.getMatrices().popMatrix();
        }

        // --- 4. COMBO ---
        boolean shouldRenderCombo = config.showCombo && (ComboTracker.getCombo() > 0 || !config.comboHideWhenInactive);
        if (shouldRenderCombo || isEditor) {
            String comboStr = (isEditor && ComboTracker.getCombo() == 0 ? "0" : ComboTracker.getCombo()) + " " + config.comboText;
            
            float scale = config.comboScale / 100f;
            int textHeight = (int)(client.textRenderer.fontHeight * scale);
            int textWidth = (int)(client.textRenderer.getWidth(comboStr) * scale);
            int padding = 2;

            int color = config.comboRainbow ? getRainbowColor() : config.comboColor;
            if ((color & 0xFF000000) == 0) color |= 0xFF000000;

            int x = 0, y = 0;
            boolean detached = config.comboXOffset != 0 || config.comboYOffset != 0;
            int usedHeight = textHeight + (config.comboShowBackground ? padding * 2 : 0);

            switch (config.comboPosition) {
                case TOP_LEFT -> { 
                    x = stackGap; 
                    y = detached ? stackGap : topLeftStack; 
                    if(!detached) topLeftStack += usedHeight + gap; 
                }
                case TOP_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                    y = detached ? stackGap : topRightStack; 
                    if(!detached) topRightStack += usedHeight + gap; 
                }
                case BOTTOM_LEFT -> { 
                    x = stackGap; 
                    if (!detached) bottomLeftStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomLeftStack;
                    if (!detached) bottomLeftStack -= gap;
                }
                case BOTTOM_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                     if (!detached) bottomRightStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomRightStack;
                    if (!detached) bottomRightStack -= gap;
                }
                case CENTER -> { 
                    x = (screenWidth - textWidth) / 2; 
                    y = (screenHeight - textHeight) / 2; 
                }
            }
            
            int finalX = x + config.comboXOffset;
            int finalY = y + config.comboYOffset;

            int boundW = config.comboShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
            int boundH = config.comboShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
            int boundX = config.comboShowBackground ? finalX - (int)(padding * scale) : finalX;
            int boundY = config.comboShowBackground ? finalY - (int)(padding * scale) : finalY;
            
            MODULE_BOUNDS.put("Combo", new ModuleBounds(boundX, boundY, boundW, boundH));

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)finalX, (float)finalY);
            drawContext.getMatrices().scale(scale, scale);

            if (config.comboShowBackground) {
                int bgX = -padding;
                int bgY = -padding;
                int bgW_local = (int)(textWidth / scale) + (padding * 2);
                int bgH_local = (int)(textHeight / scale) + (padding * 2);
                int bgAlphaColor = (config.comboBackgroundOpacity << 24) | (config.comboBackgroundColor & 0x00FFFFFF);
                drawContext.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
            }

            drawContext.drawTextWithShadow(client.textRenderer, comboStr, 0, 0, color);
            drawContext.getMatrices().popMatrix();
        }

        // --- 5. KEYSTROKES ---
        if (config.showKeystrokes || isEditor) {
            float kScale = config.keystrokesScale / 100f;
            int boxSize = 20;
            int spacing = 2;
            
            int totalWidth = (int)(((boxSize * 3) + (spacing * 2)) * kScale);
            int totalHeight = (int)(((boxSize * 2) + 12 + (spacing * 2)) * kScale); 

            int x = 0, y = 0;
            boolean detached = config.keystrokesXOffset != 0 || config.keystrokesYOffset != 0;
            
            switch (config.keystrokesPosition) {
                case TOP_LEFT -> { 
                    x = stackGap; 
                    y = detached ? stackGap : topLeftStack; 
                    if(!detached) topLeftStack += totalHeight + gap; 
                }
                case TOP_RIGHT -> { 
                    x = screenWidth - totalWidth - stackGap; 
                    y = detached ? stackGap : topRightStack; 
                    if(!detached) topRightStack += totalHeight + gap; 
                }
                case BOTTOM_LEFT -> { 
                    x = stackGap; 
                    if (!detached) bottomLeftStack -= totalHeight;
                    y = detached ? (screenHeight - totalHeight - stackGap) : bottomLeftStack;
                    if (!detached) bottomLeftStack -= gap;
                }
                case BOTTOM_RIGHT -> { 
                    x = screenWidth - totalWidth - stackGap; 
                    if (!detached) bottomRightStack -= totalHeight;
                    y = detached ? (screenHeight - totalHeight - stackGap) : bottomRightStack;
                    if (!detached) bottomRightStack -= gap;
                }
                case CENTER -> { 
                    x = (screenWidth - totalWidth) / 2; 
                    y = (screenHeight - totalHeight) / 2; 
                }
            }

            int finalX = x + config.keystrokesXOffset;
            int finalY = y + config.keystrokesYOffset;
            
            MODULE_BOUNDS.put("Keystrokes", new ModuleBounds(finalX, finalY, totalWidth, totalHeight));

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
            drawContext.getMatrices().translate((float)finalX, (float)finalY);
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

        // --- 6. REACH DISPLAY ---
        boolean shouldRenderReach = config.showReach && (ReachTracker.getReachDisplay() != null || isEditor);
        if (shouldRenderReach) {
            String reachText = ReachTracker.getReachDisplay();
            if (isEditor && reachText == null) reachText = "No Hit";
            
            float scale = config.reachScale / 100f;
            int textHeight = (int)(client.textRenderer.fontHeight * scale);
            int textWidth = (int)(client.textRenderer.getWidth(reachText) * scale);
            int padding = 2;
            
            int color = config.reachRainbow ? getRainbowColor() : config.reachColor;
            if ((color & 0xFF000000) == 0) color |= 0xFF000000;

            int x = 0, y = 0;
            boolean detached = config.reachXOffset != 0 || config.reachYOffset != 0;
            int usedHeight = textHeight + (config.reachShowBackground ? padding * 2 : 0);

             switch (config.reachPosition) {
                case TOP_LEFT -> { 
                    x = stackGap; 
                    y = detached ? stackGap : topLeftStack; 
                    if(!detached) topLeftStack += usedHeight + gap; 
                }
                case TOP_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                    y = detached ? stackGap : topRightStack; 
                    if(!detached) topRightStack += usedHeight + gap; 
                }
                case BOTTOM_LEFT -> { 
                    x = stackGap; 
                    if (!detached) bottomLeftStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomLeftStack;
                    if (!detached) bottomLeftStack -= gap;
                }
                case BOTTOM_RIGHT -> { 
                    x = screenWidth - textWidth - stackGap; 
                    if (!detached) bottomRightStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomRightStack;
                    if (!detached) bottomRightStack -= gap;
                }
                case CENTER -> { 
                    x = (screenWidth - textWidth) / 2; 
                    y = (screenHeight - textHeight) / 2; 
                    // Push down slightly if in center to avoid crosshair overlap by default
                    if (!detached) y += 15; 
                }
            }
            
            int finalX = x + config.reachXOffset;
            int finalY = y + config.reachYOffset;

            int boundW = config.reachShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
            int boundH = config.reachShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
            int boundX = config.reachShowBackground ? finalX - (int)(padding * scale) : finalX;
            int boundY = config.reachShowBackground ? finalY - (int)(padding * scale) : finalY;
            
            MODULE_BOUNDS.put("Reach", new ModuleBounds(boundX, boundY, boundW, boundH));

            drawContext.getMatrices().pushMatrix();
            drawContext.getMatrices().translate((float)finalX, (float)finalY);
            drawContext.getMatrices().scale(scale, scale);
            
            if (config.reachShowBackground) {
                int bgX = -padding;
                int bgY = -padding;
                int bgW_local = (int)(textWidth / scale) + (padding * 2);
                int bgH_local = (int)(textHeight / scale) + (padding * 2);
                int bgAlphaColor = (config.reachBackgroundOpacity << 24) | (config.reachBackgroundColor & 0x00FFFFFF);
                drawContext.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
            }

            drawContext.drawTextWithShadow(client.textRenderer, reachText, 0, 0, color);
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