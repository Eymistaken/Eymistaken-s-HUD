package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.ReachTracker;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ReachModule extends HudModule {

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.showReach;
    }

    @Override
    public boolean supportsFade() {
        return true;
    }

    @Override
    public SimpleCPSConfig.Position getPositionType() {
        return SimpleCPSConfig.instance.reachPosition;
    }

    @Override
    public int getXOffset() {
        return SimpleCPSConfig.instance.reachXOffset;
    }

    @Override
    public int getYOffset() {
        return SimpleCPSConfig.instance.reachYOffset;
    }

    @Override
    public String getName() {
        return "Reach";
    }

    @Override public void setPositionType(SimpleCPSConfig.Position pos) { SimpleCPSConfig.instance.reachPosition = pos; }
    @Override public void setXOffset(int x) { SimpleCPSConfig.instance.reachXOffset = x; }
    @Override public void setYOffset(int y) { SimpleCPSConfig.instance.reachYOffset = y; }
    @Override public void setScale(int scale) { SimpleCPSConfig.instance.reachScale = scale; }
    @Override public int getScale() { return SimpleCPSConfig.instance.reachScale; }
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.reachPosition = SimpleCPSConfig.Position.TOP_LEFT;
        SimpleCPSConfig.instance.reachXOffset = 0;
        SimpleCPSConfig.instance.reachYOffset = 0;
        SimpleCPSConfig.instance.reachScale = 100;
    }

    @Override
    public void resetVisualDefaults() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        config.reachScale = 100;
        config.reachColor = 0xFFFFFF;
        config.reachRainbow = false;
        config.reachTimeout = 3.0;
        config.reachShowBackground = false;
        config.reachBackgroundColor = 0x000000;
        config.reachBackgroundOpacity = 128;
        config.reachOnlyPlayers = true;
        config.reachAlwaysShow = false;
        config.reachNoHitText = "No Hit";
    }

    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>(super.getContextMenuSettings());
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Reach", () -> config.showReach, v -> config.showReach = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Always Show", () -> config.reachAlwaysShow, v -> config.reachAlwaysShow = v),
            new com.eymistaken.simplecps.api.ColorSetting("Text Color", () -> config.reachColor, v -> config.reachColor = v),
            new com.eymistaken.simplecps.api.TextSetting("No Hit Text", () -> config.reachNoHitText, v -> config.reachNoHitText = v)
        ));
        return settings;
    }

    @Override
    public com.eymistaken.simplecps.api.HudPreview getPreview() {
        return com.eymistaken.simplecps.api.HudPreview.ofModule(this);
    }

    private String getDisplayText() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String text = ReachTracker.getReachDisplay();

        // The settings preview wants the same stand-in the editor gets: without a recent
        // hit there is no reach to report, and an empty box shows nothing to configure.
        boolean placeholder = isPreviewing()
            || client.gui.screen() instanceof com.eymistaken.simplecps.gui.HudEditorScreen;

        if (text == null) {
            if (placeholder) {
                return "3.00 blocks"; // Dummy text for HUD Editor
            } else if (config.reachAlwaysShow) {
                return config.reachNoHitText;
            } else {
                return null;
            }
        }
        return text;
    }

    private int measureWidth(String reachText) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.reachScale / 100f;
        int textWidth = (int)(textWidth(reachText) * scale);
        int padding = 2;
        return config.reachShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    private int measureHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.reachScale / 100f;
        int textHeight = (int)(client.font.lineHeight * scale);
        int padding = 2;
        return config.reachShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        String reachText = getDisplayText();
        if (reachText == null) return;

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.reachScale / 100f;
        int textWidth = (int)(textWidth(reachText) * scale);
        int textHeight = (int)(client.font.lineHeight * scale);
        int padding = 2;
        
        int color = config.reachRainbow ? getRainbowColor() : config.reachColor;
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;

        context.pose().pushMatrix();
        context.pose().translate((float)x, (float)y);
        context.pose().scale(scale, scale);
        
        if (config.reachShowBackground) {
            int bgX = -padding;
            int bgY = -padding;
            int bgW_local = (int)(textWidth / scale) + (padding * 2);
            int bgH_local = (int)(textHeight / scale) + (padding * 2);
            int bgAlphaColor = (config.reachBackgroundOpacity << 24) | (config.reachBackgroundColor & 0x00FFFFFF);
            context.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, col(bgAlphaColor));
        }

        drawText(context, reachText, 0, 0, color);
        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        String reachText = getDisplayText();
        if (reachText == null) return 0;
        return measureWidth(reachText);
    }

    @Override
    public int getHeight() {
        String reachText = getDisplayText();
        if (reachText == null) return 0;
        return measureHeight();
    }

    @Override
    public int getLayoutWidth() {
        String reachText = getDisplayText();
        if (reachText == null) {
            reachText = "3.00 blocks";
        }
        return measureWidth(reachText);
    }

    @Override
    public int getLayoutHeight() {
        return measureHeight();
    }
}
