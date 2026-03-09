package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public class KeystrokesModule extends HudModule {

    public static final Set<Integer> pressedKeys = new HashSet<>();
    private final Map<Integer, Float> keyScales = new HashMap<>();

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.showKeystrokes;
    }

    @Override
    public SimpleCPSConfig.Position getPositionType() {
        return SimpleCPSConfig.instance.keystrokesPosition;
    }

    @Override
    public int getXOffset() {
        return SimpleCPSConfig.instance.keystrokesXOffset;
    }

    @Override
    public int getYOffset() {
        return SimpleCPSConfig.instance.keystrokesYOffset;
    }

    @Override
    public String getName() {
        return "Keystrokes";
    }

    @Override public void setPositionType(SimpleCPSConfig.Position pos) { SimpleCPSConfig.instance.keystrokesPosition = pos; }
    @Override public void setXOffset(int x) { SimpleCPSConfig.instance.keystrokesXOffset = x; }
    @Override public void setYOffset(int y) { SimpleCPSConfig.instance.keystrokesYOffset = y; }
    @Override public void setScale(int scale) { SimpleCPSConfig.instance.keystrokesScale = scale; }
    @Override public int getScale() { return SimpleCPSConfig.instance.keystrokesScale; }
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.keystrokesPosition = SimpleCPSConfig.Position.TOP_LEFT;
        SimpleCPSConfig.instance.keystrokesXOffset = 0;
        SimpleCPSConfig.instance.keystrokesYOffset = 0;
        SimpleCPSConfig.instance.keystrokesScale = 80;
    }

    @Override
    public void resetVisualDefaults() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        config.keystrokesScale = 80;
        config.keystrokesRainbow = false;
        config.keystrokesRainbowTarget = SimpleCPSConfig.RainbowTarget.TEXT;
        config.keystrokesColor = 0xFFFFFF;
        config.keystrokesPressedColor = 0x00FF00;
        config.keystrokesBackgroundColor = 0x000000;
        config.keystrokesBackgroundOpacity = 128;
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float kScale = config.keystrokesScale / 100f;
        
        // Stacking/Positioning logic handled by SimpleCPSClient using getWidth/getHeight and setRenderPosition
        // But getWidth/getHeight for Keystrokes is complex (dynamic layout).
        
        renderKeystrokes(context, client, config, x, y, kScale);
    }
    
    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float kScale = config.keystrokesScale / 100f;
        int maxX = 0;
        if (!config.keystrokesLayout.isEmpty()) {
            maxX = Integer.MIN_VALUE;
            for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
                maxX = Math.max(maxX, btn.x + btn.w);
            }
        } else {
             maxX = 64; 
        }
        return (int)(maxX * kScale);
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float kScale = config.keystrokesScale / 100f;
        int maxY = 0;
        if (!config.keystrokesLayout.isEmpty()) {
            maxY = Integer.MIN_VALUE;
            for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
                maxY = Math.max(maxY, btn.y + btn.h);
            }
        } else {
             maxY = 64; 
        }
        return (int)(maxY * kScale);
    }

    private void renderKeystrokes(DrawContext drawContext, MinecraftClient client, SimpleCPSConfig config, int x, int y, float scale) {
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
        drawContext.getMatrices().translate(x, y);
        drawContext.getMatrices().scale(scale, scale);

        long handle = client.getWindow().getHandle();

        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
             boolean isPressed = false;
             
             if (btn.isMouse) {
                 isPressed = pressedKeys.contains(btn.keyCode + 1000);
             } else {
                 isPressed = pressedKeys.contains(btn.keyCode);
             }
             
             // Animation
             float currentAnim = keyScales.getOrDefault(btn.keyCode + (btn.isMouse ? 1000 : 0), 1.0f);
             float newAnim = updateAnimation(currentAnim, isPressed);
             keyScales.put(btn.keyCode + (btn.isMouse ? 1000 : 0), newAnim);
             
             drawAnimatedKey(drawContext, client, btn, isPressed ? pressedColor : normalColor, baseBgColor, newAnim, isPressed);
        }

        drawContext.getMatrices().popMatrix();
    }
    
    private float updateAnimation(float currentScale, boolean isPressed) {
        float target = isPressed ? 0.85f : 1.0f;
        float speed = 0.2f;
        float diff = target - currentScale;
        if (Math.abs(diff) < 0.01f) return target;
        return currentScale + (diff * speed);
    }

    private void drawAnimatedKey(DrawContext context, MinecraftClient client, SimpleCPSConfig.KeyButtonData btn, int textColor, int bgColor, float animScale, boolean isPressed) {
        if (isPressed) {
            if (btn.btnPressedColor != -1 && btn.btnPressedColor != 0) {
                textColor = btn.btnPressedColor;
            }
        } else {
            if (btn.btnColor != -1 && btn.btnColor != 0) {
                textColor = btn.btnColor;
            }
        }
        context.getMatrices().pushMatrix();
        
        float centerX = btn.x + (btn.w / 2.0f);
        float centerY = btn.y + (btn.h / 2.0f);
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().scale(animScale, animScale);
        context.getMatrices().translate(-centerX, -centerY);
        
        context.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, bgColor);
        
        // Prepare Key Label
        net.minecraft.text.MutableText text = net.minecraft.text.Text.literal(btn.label);
        net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY;
        if (btn.bold) style = style.withBold(true);
        if (btn.italic) style = style.withItalic(true);
        if (btn.underlined) style = style.withUnderline(true);
        text.setStyle(style);
        
        int textWidth = client.textRenderer.getWidth(text);
        int textHeight = client.textRenderer.fontHeight;
        
        // Handle CPS Display Layout
        // If showing CPS, maybe move label up slightly?
        int yOffset = (btn.showCps && btn.isMouse) ? -4 : 0; // Move label up a bit if CPS is shown
        
        // Label Position
        int lx, ly;
        if (btn.labelX == -1) {
            lx = btn.x + (btn.w - textWidth) / 2;
        } else {
            lx = btn.x + btn.labelX;
        }
        
        if (btn.labelY == -1) {
            ly = btn.y + (btn.h - textHeight) / 2 + yOffset;
        } else {
            ly = btn.y + btn.labelY;
        }
        
        if (btn.shadow) {
            context.drawTextWithShadow(client.textRenderer, text, lx, ly, textColor);
        } else {
            context.drawText(client.textRenderer, text, lx, ly, textColor, false);
        }
        
        // CPS Counter
        if (btn.showCps && btn.isMouse) {
            int cps = 0;
            if (btn.keyCode == 0) cps = CpsModule.leftClicks.size(); // LMB
            else if (btn.keyCode == 1) cps = CpsModule.rightClicks.size(); // RMB
            
            String cpsStr = String.valueOf(cps);
            
            // Draw small
            context.getMatrices().pushMatrix();
            float smallScale = 0.6f;
            int cpsW = client.textRenderer.getWidth(cpsStr);
            
            // Position at bottom center (or customized?)
            // Center X relative to button
            // Bottom Y relative to button
            float cpsX = btn.x + (btn.w - (cpsW * smallScale)) / 2.0f;
            float cpsY = btn.y + btn.h - (client.textRenderer.fontHeight * smallScale) - 1;
            
            context.getMatrices().translate(cpsX, cpsY);
            context.getMatrices().scale(smallScale, smallScale);
            
            int cpsColor = textColor; 
            context.drawTextWithShadow(client.textRenderer, cpsStr, 0, 0, cpsColor);
            
            context.getMatrices().popMatrix();
        }
        
        context.getMatrices().popMatrix();
    }
    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>();
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Keystrokes", () -> config.showKeystrokes, v -> config.showKeystrokes = v),
            new com.eymistaken.simplecps.api.ActionSetting("Open Designer", () -> {
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(new com.eymistaken.simplecps.gui.KeystrokesDesignerScreen(client.currentScreen)));
            })
        ));
        return settings;
    }
}
