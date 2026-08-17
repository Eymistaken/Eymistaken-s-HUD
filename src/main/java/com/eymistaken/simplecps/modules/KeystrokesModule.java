package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import org.lwjgl.glfw.GLFW;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class KeystrokesModule extends HudModule {

    public static final Set<Integer> pressedKeys = new HashSet<>();
    private final Map<Integer, Float> keyScales = new HashMap<>();
    private final Map<Integer, float[]> fillRadii = new HashMap<>();
    private final Map<Integer, Boolean> prevPressed = new HashMap<>();

    // The squish and ripple effects are stepped once per draw with no delta time, so the
    // preview cannot share them: the settings screen renders over a live HUD, and both
    // passes stepping the same maps would run the real keystrokes at double speed.
    private final Map<Integer, Float> previewKeyScales = new HashMap<>();
    private final Map<Integer, float[]> previewFillRadii = new HashMap<>();
    private final Map<Integer, Boolean> previewPrevPressed = new HashMap<>();

    private Map<Integer, Float> keyScales() {
        return isPreviewing() ? previewKeyScales : keyScales;
    }

    private Map<Integer, float[]> fillRadii() {
        return isPreviewing() ? previewFillRadii : fillRadii;
    }

    private Map<Integer, Boolean> prevPressed() {
        return isPreviewing() ? previewPrevPressed : prevPressed;
    }

    @Override
    public com.eymistaken.simplecps.api.HudPreview getPreview() {
        return com.eymistaken.simplecps.api.HudPreview.ofModule(this);
    }

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
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
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

    private void renderKeystrokes(GuiGraphicsExtractor drawContext, Minecraft client, SimpleCPSConfig config, int x, int y, float scale) {
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

        drawContext.pose().pushMatrix();
        drawContext.pose().translate(x, y);
        drawContext.pose().scale(scale, scale);

        long handle = client.getWindow().handle();

        // No key is faked as held. Forcing one showed the pressed color, but which key got
        // it was arbitrary — the layout is user-defined — and it left a single key wearing
        // a background none of its neighbours had, which read as a rendering fault rather
        // than a pressed state. Real presses still register while the menu is open.

        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
             boolean isPressed = false;

             if (btn.isMouse) {
                 isPressed = pressedKeys.contains(btn.keyCode + 1000);
             } else {
                 isPressed = pressedKeys.contains(btn.keyCode);
             }

             // Animation
             float currentAnim = keyScales().getOrDefault(btn.keyCode + (btn.isMouse ? 1000 : 0), 1.0f);
             float newAnim;
             boolean animOn = btn.animationEnabled == null || btn.animationEnabled;
             boolean squishActive = config.keystrokesEffectMode == 1 || config.keystrokesEffectMode == 3;
             if (!squishActive || !animOn) {
                 newAnim = 1.0f;
             } else {
                 newAnim = updateAnimation(currentAnim, isPressed);
             }
             keyScales().put(btn.keyCode + (btn.isMouse ? 1000 : 0), newAnim);

             int fillKey = btn.keyCode + (btn.isMouse ? 1000 : 0);
             boolean wasPressedBefore = prevPressed().getOrDefault(fillKey, false);
             prevPressed().put(fillKey, isPressed);
             
             boolean rippleActive = config.keystrokesEffectMode == 2 || config.keystrokesEffectMode == 3;
             float maxFillRadius = (float) Math.sqrt(btn.w * btn.w + btn.h * btn.h) / 2f * 1.05f;
             float outerRadius = 0f;
             float innerRadius = 0f;
             
             if (rippleActive) {
                 float[] state = fillRadii().getOrDefault(fillKey, new float[]{0f, 0f});
                 if (isPressed) {
                     // Filling: outer grows to max, inner stays 0
                     state[0] = Math.min(state[0] + maxFillRadius * 0.18f, maxFillRadius);
                     state[1] = 0f;
                 } else {
                     // Emptying: outer stays at max, inner grows outward
                     if (state[0] > 0f) {
                         state[0] = maxFillRadius; // lock outer at max
                         state[1] = Math.min(state[1] + maxFillRadius * 0.15f, maxFillRadius);
                     }
                 }
                 if (state[0] > 0f && state[1] < maxFillRadius) {
                     fillRadii().put(fillKey, state);
                 } else {
                     fillRadii().remove(fillKey);
                     state[0] = 0f;
                     state[1] = 0f;
                 }
                 outerRadius = state[0];
                 innerRadius = state[1];
             } else {
                 fillRadii().remove(fillKey);
             }
             
             drawAnimatedKey(drawContext, client, btn, isPressed ? pressedColor : normalColor, baseBgColor, newAnim, isPressed, outerRadius, innerRadius);
        }

        drawContext.pose().popMatrix();
    }
    
    private float updateAnimation(float currentScale, boolean isPressed) {
        float target = isPressed ? 0.85f : 1.0f;
        float speed = 0.2f;
        float diff = target - currentScale;
        if (Math.abs(diff) < 0.01f) return target;
        return currentScale + (diff * speed);
    }

    private void drawAnimatedKey(GuiGraphicsExtractor context, Minecraft client, SimpleCPSConfig.KeyButtonData btn, int textColor, int bgColor, float animScale, boolean isPressed, float outerRadius, float innerRadius) {
        if (isPressed) {
            if (btn.btnPressedColor != -1 && btn.btnPressedColor != 0) {
                textColor = btn.btnPressedColor;
            }
        } else {
            if (btn.btnColor != -1 && btn.btnColor != 0) {
                textColor = btn.btnColor;
            }
        }
        context.pose().pushMatrix();
        
        float centerX = btn.x + (btn.w / 2.0f);
        float centerY = btn.y + (btn.h / 2.0f);
        context.pose().translate(centerX, centerY);
        context.pose().scale(animScale, animScale);
        context.pose().translate(-centerX, -centerY);
        
        context.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, bgColor);

        if (outerRadius > 0f) {
            int fillColor = 0x40FFFFFF;
            int cx = btn.x + btn.w / 2;
            int cy = btn.y + btn.h / 2;
            int rOuter = (int) outerRadius;
            int rInner = (int) innerRadius;
            for (int sy = Math.max(btn.y, cy - rOuter); sy < Math.min(btn.y + btn.h, cy + rOuter); sy++) {
                int dy = sy - cy;
                int dxOuter = (int) Math.sqrt(Math.max(0.0, (double)rOuter * rOuter - (double)dy * dy));
                int x1 = Math.max(btn.x, cx - dxOuter);
                int x2 = Math.min(btn.x + btn.w, cx + dxOuter);
                if (rInner > 0) {
                    int dxInner = (int) Math.sqrt(Math.max(0.0, (double)rInner * rInner - (double)dy * dy));
                    // Left segment
                    int lx1 = x1;
                    int lx2 = Math.min(x2, cx - dxInner);
                    if (lx2 > lx1) context.fill(lx1, sy, lx2, sy + 1, fillColor);
                    // Right segment
                    int rx1 = Math.max(x1, cx + dxInner);
                    int rx2 = x2;
                    if (rx2 > rx1) context.fill(rx1, sy, rx2, sy + 1, fillColor);
                } else {
                    if (x2 > x1) context.fill(x1, sy, x2, sy + 1, fillColor);
                }
            }
        }
        
        // Prepare Key Label
        net.minecraft.network.chat.MutableComponent text = net.minecraft.network.chat.Component.literal(btn.label);
        // Start from the active HUD font so key labels follow the font selection,
        // then layer on the per-key bold/italic/underline styling.
        net.minecraft.network.chat.Style style = com.eymistaken.simplecps.util.EymHudFonts.activeStyle();
        if (btn.bold) style = style.withBold(true);
        if (btn.italic) style = style.withItalic(true);
        if (btn.underlined) style = style.withUnderlined(true);
        text.setStyle(style);
        
        int textWidth = client.font.width(text);
        int textHeight = client.font.lineHeight;
        
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
            context.text(client.font, text, lx, ly, textColor);
        } else {
            context.text(client.font, text, lx, ly, textColor, false);
        }
        
        // CPS Counter
        if (btn.showCps && btn.isMouse) {
            int cps = 0;
            if (btn.keyCode == 0) cps = CpsModule.leftClicks.size(); // LMB
            else if (btn.keyCode == 1) cps = CpsModule.rightClicks.size(); // RMB
            
            String cpsStr = String.valueOf(cps);
            
            // Draw small
            context.pose().pushMatrix();
            float smallScale = 0.6f;
            int cpsW = client.font.width(com.eymistaken.simplecps.util.EymHudFonts.text(cpsStr));
            
            // Position at bottom center (or customized?)
            // Center X relative to button
            // Bottom Y relative to button
            float cpsX = btn.x + (btn.w - (cpsW * smallScale)) / 2.0f;
            float cpsY = btn.y + btn.h - (client.font.lineHeight * smallScale) - 1;
            
            context.pose().translate(cpsX, cpsY);
            context.pose().scale(smallScale, smallScale);
            
            int cpsColor = textColor; 
            context.text(client.font, com.eymistaken.simplecps.util.EymHudFonts.text(cpsStr), 0, 0, cpsColor);
            
            context.pose().popMatrix();
        }
        
        context.pose().popMatrix();
    }
    
    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>();
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Keystrokes", () -> config.showKeystrokes, v -> config.showKeystrokes = v),
            new com.eymistaken.simplecps.api.CycleSetting("Effect",
                java.util.Arrays.asList("None", "Squish", "Ripple", "Both"),
                () -> config.keystrokesEffectMode,
                v -> { config.keystrokesEffectMode = v; com.eymistaken.simplecps.SimpleCPSConfig.save(); }
            ),
            new com.eymistaken.simplecps.api.ActionSetting("Open Designer", () -> {
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                client.execute(() -> client.gui.setScreen(new com.eymistaken.simplecps.gui.KeystrokesDesignerScreen(client.gui.screen())));
            })
        ));
        return settings;
    }
}
