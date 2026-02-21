package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.ComboTracker;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;

public class ComboModule extends HudModule {

    private int lastHurtTime = 0;

    @Override
    public boolean isEnabled() {
        boolean shouldRenderCombo = SimpleCPSConfig.instance.showCombo && (ComboTracker.getCombo() > 0 || !SimpleCPSConfig.instance.comboHideWhenInactive);
        // We return true if configured to show, but inside logic we check inactivity. 
        // SimpleCPSClient logic was: if (shouldRenderCombo || isEditor)
        // Here we just return checking the config, but the getWidth/Height should handle if it is empty/invisible?
        // Actually, HudModule.isEnabled is strict.
        return shouldRenderCombo; 
        // Note: isEditor check is handled by SimpleCPSClient forcing render or setting a flag?
        // SimpleCPSClient passed 'isEditor' boolean.
        // I might need to deal with 'isEditor' later. For now, assuming standard play.
        // Actually, isEnabled is called by SimpleCPSClient loop.
        // SimpleCPSClient logic: if (config.showCombo && (ComboTracker.getCombo() > 0 || !config.comboHideWhenInactive) || isEditor)
        // I will let SimpleCPSClient handle the "isEnabled" Check fully or replicate it here?
        // Let's replicate strict logic here. The "isEditor" part will be handled by SimpleCPSClient overriding 'enabled' check or I should add 'setEditorMode' to modules?
        // SimpleCPSClient can just check config.showCombo generally, and then the module can decide to render nothing if inactive.
        // But getWidth/Height needs to be consistent.
        // I'll stick to: enabled = config.showCombo.
        // And inside render/resize, handle visibility.
    }
    
    // Changing isEnabled to just config to allow 'tick' to run and internal logic to decide visibility?
    // But SimpleCPSClient loop skips if !isEnabled().
    // Let's update isEnabled to be smart.
    public boolean isReallyEnabled() {
       return SimpleCPSConfig.instance.showCombo;
    }
    
    @Override
    public SimpleCPSConfig.Position getPositionType() {
        return SimpleCPSConfig.instance.comboPosition;
    }

    @Override
    public int getXOffset() {
        return SimpleCPSConfig.instance.comboXOffset;
    }

    @Override
    public int getYOffset() {
        return SimpleCPSConfig.instance.comboYOffset;
    }

    @Override
    public String getName() {
        return "Combo";
    }

    @Override public void setPositionType(SimpleCPSConfig.Position pos) { SimpleCPSConfig.instance.comboPosition = pos; }
    @Override public void setXOffset(int x) { SimpleCPSConfig.instance.comboXOffset = x; }
    @Override public void setYOffset(int y) { SimpleCPSConfig.instance.comboYOffset = y; }
    @Override public void setScale(int scale) { SimpleCPSConfig.instance.comboScale = scale; }
    @Override public int getScale() { return SimpleCPSConfig.instance.comboScale; }
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.comboPosition = SimpleCPSConfig.Position.TOP_LEFT;
        SimpleCPSConfig.instance.comboXOffset = 0;
        SimpleCPSConfig.instance.comboYOffset = 0;
    }

    @Override
    public void tick(MinecraftClient client) {
        if (client.player == null) {
            ComboTracker.reset();
            return;
        }

        ComboTracker.onTick(client);

        if (client.player.hurtTime > lastHurtTime && client.player.hurtTime > 0) {
            Entity attacker = client.player.getAttacker();
            ComboTracker.onDamage(attacker);
        }
        lastHurtTime = client.player.hurtTime;
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        
        // Safety check if we got here but shouldn't render (double check)
        boolean shouldRenderCombo = (ComboTracker.getCombo() > 0 || !config.comboHideWhenInactive);
        // Note: We don't have 'isEditor' context here. If HudModule assumes In-Game rendering, this is fine.
        // If SimpleCPSClient calls render, we render.

        int currentCombo = ComboTracker.getCombo();
        String comboStr = currentCombo + " " + config.comboText;
        
        float scale = config.comboScale / 100f;
        int textWidth = (int)(client.textRenderer.getWidth(comboStr) * scale);
        int textHeight = (int)(client.textRenderer.fontHeight * scale);
        int padding = 2;

        int color = config.comboColor; 
        if (config.comboHeatmap) {
            int t1, t2, t3;
            switch (config.comboHeatmapMode) {
                case EASY -> { t1 = 3; t2 = 5; t3 = 7; }
                case HARD -> { t1 = 10; t2 = 40; t3 = 60; }
                default -> { t1 = 5; t2 = 8; t3 = 12; } // MEDIUM
            }
            
            if (currentCombo < t1) {
                 color = config.comboColor;
            } else if (currentCombo < t2) {
                 float progress = (float)(currentCombo - t1) / (float)(t2 - t1);
                 color = interpolateColor(config.comboColor, 0xFFFF0000, progress);
            } else {
                 float progress = Math.min((float)(currentCombo - t2) / (float)(t3 - t2), 1.0f);
                 color = interpolateColor(0xFFFF0000, 0xFF550000, progress);
            }
        } else if (config.comboRainbow) {
            color = getRainbowColor();
        }
        
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)x, (float)y);
        
        // Shake Effect
        int shakeThreshold = 12; // Default Medium
        if (config.comboHeatmap) {
             switch (config.comboHeatmapMode) {
                case EASY -> shakeThreshold = 7;
                case HARD -> shakeThreshold = 60;
                default -> shakeThreshold = 12;
            }
        }
        
        if (config.comboHeatmap && currentCombo > shakeThreshold) {
             float shake = (float)(Math.random() - 0.5) * 2f; // +/- 1
             context.getMatrices().translate(shake, shake);
        }
        
        context.getMatrices().scale(scale, scale);

        if (config.comboShowBackground) {
            int bgX = -padding;
            int bgY = -padding;
            int bgW_local = (int)(textWidth / scale) + (padding * 2);
            int bgH_local = (int)(textHeight / scale) + (padding * 2);
            int bgAlphaColor = (config.comboBackgroundOpacity << 24) | (config.comboBackgroundColor & 0x00FFFFFF);
            context.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
        }

        context.drawTextWithShadow(client.textRenderer, comboStr, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        int currentCombo = ComboTracker.getCombo();
        String comboStr = currentCombo + " " + config.comboText;
        float scale = config.comboScale / 100f;
        int textWidth = (int)(client.textRenderer.getWidth(comboStr) * scale);
        int padding = 2;
        return config.comboShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.comboScale / 100f;
        int textHeight = (int)(client.textRenderer.fontHeight * scale);
        int padding = 2;
        return config.comboShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
    }
    
    private int interpolateColor(int c1, int c2, float factor) {
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        
        int r = (int)(r1 + (r2 - r1) * factor);
        int g = (int)(g1 + (g2 - g1) * factor);
        int b = (int)(b1 + (b2 - b1) * factor);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
