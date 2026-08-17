package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.ComboTracker;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ComboModule extends HudModule {

    // Last combo shown while > 0, so a fade-out keeps the last value instead of
    // snapping to "0 Combo" the instant the streak resets.
    private int lastCombo = 0;

    @Override
    public boolean supportsFade() {
        return true;
    }

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
        SimpleCPSConfig.instance.comboScale = 100;
    }
    
    @Override
    public void resetVisualDefaults() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        config.comboScale = 100;
        config.comboColor = 0xFFFFFF;
        config.comboRainbow = false;
        config.comboText = "Combo";
        config.comboShowBackground = false;
        config.comboBackgroundColor = 0x000000;
        config.comboBackgroundOpacity = 128;
        config.comboTimeout = 3.0;
        config.comboResetOnAnyDamage = true;
        config.comboContinueOnSwitch = true;
        config.comboHideWhenInactive = false;
        config.comboHeatmap = false;
        config.comboHeatmapMode = SimpleCPSConfig.HeatmapMode.MEDIUM;
        config.comboOnlyPlayers = true;
        config.combatMode = SimpleCPSConfig.CombatMode.MODERN;
    }

    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>(super.getContextMenuSettings());
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Combo", () -> config.showCombo, v -> config.showCombo = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Always Show", () -> !config.comboHideWhenInactive, v -> config.comboHideWhenInactive = !v),
            new com.eymistaken.simplecps.api.ColorSetting("Text Color", () -> config.comboColor, v -> config.comboColor = v),
            new com.eymistaken.simplecps.api.TextSetting("Suffix Text", () -> config.comboText, v -> config.comboText = v)
        ));
        return settings;
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null) {
            ComboTracker.reset();
            return;
        }

        ComboTracker.onTick(client);
    }

    @Override
    public com.eymistaken.simplecps.api.HudPreview getPreview() {
        return com.eymistaken.simplecps.api.HudPreview.ofModule(this);
    }

    /**
     * Streak the preview shows. Taken from the active heatmap rather than a fixed number
     * so it lands in the same tier whatever the difficulty is set to: high enough that the
     * heatmap colouring is visible, below the top tier so the preview does not shake.
     */
    private int displayCombo() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (isPreviewing()) {
            return com.eymistaken.simplecps.api.ComboHeatmap.of(config.comboHeatmapMode).tier2();
        }
        int currentCombo = ComboTracker.getCombo();
        if (currentCombo > 0) lastCombo = currentCombo;
        // While fading out (alpha < 1) the streak has already reset to 0; show the
        // last real value so the module fades on "12 Combo", not "0 Combo".
        return (currentCombo == 0 && renderAlpha < 1f) ? lastCombo : currentCombo;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;

        int displayCombo = displayCombo();
        String comboStr = displayCombo + " " + config.comboText;

        float scale = config.comboScale / 100f;
        int textWidth = (int)(textWidth(comboStr) * scale);
        int textHeight = (int)(client.font.lineHeight * scale);
        int padding = 2;

        int color = config.comboColor; 
        if (config.comboHeatmap) {
            com.eymistaken.simplecps.api.ComboHeatmap heatmap =
                com.eymistaken.simplecps.api.ComboHeatmap.of(config.comboHeatmapMode);
            int t1 = heatmap.tier1();
            int t2 = heatmap.tier2();
            int t3 = heatmap.tier3();

            if (displayCombo < t1) {
                 color = config.comboColor;
            } else if (displayCombo < t2) {
                 float progress = (float)(displayCombo - t1) / (float)(t2 - t1);
                 color = interpolateColor(config.comboColor, 0xFFFF0000, progress);
            } else {
                 float progress = Math.min((float)(displayCombo - t2) / (float)(t3 - t2), 1.0f);
                 color = interpolateColor(0xFFFF0000, 0xFF550000, progress);
            }
        } else if (config.comboRainbow) {
            color = getRainbowColor();
        }
        
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;

        context.pose().pushMatrix();
        context.pose().translate((float)x, (float)y);
        
        // Shake Effect — starts at the top tier, i.e. the same threshold the heatmap
        // uses for its darkest colour.
        int shakeThreshold = com.eymistaken.simplecps.api.ComboHeatmap
            .of(config.comboHeatmapMode).tier3();

        if (config.comboHeatmap && displayCombo > shakeThreshold) {
             float shake = (float)(Math.random() - 0.5) * 2f; // +/- 1
             context.pose().translate(shake, shake);
        }

        context.pose().scale(scale, scale);

        if (config.comboShowBackground) {
            int bgX = -padding;
            int bgY = -padding;
            int bgW_local = (int)(textWidth / scale) + (padding * 2);
            int bgH_local = (int)(textHeight / scale) + (padding * 2);
            int bgAlphaColor = (config.comboBackgroundOpacity << 24) | (config.comboBackgroundColor & 0x00FFFFFF);
            context.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, col(bgAlphaColor));
        }

        drawText(context, comboStr, 0, 0, color);
        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String comboStr = displayCombo() + " " + config.comboText;
        float scale = config.comboScale / 100f;
        int textWidth = (int)(textWidth(comboStr) * scale);
        int padding = 2;
        return config.comboShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.comboScale / 100f;
        int textHeight = (int)(client.font.lineHeight * scale);
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
