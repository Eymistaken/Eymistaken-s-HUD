package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class FpsModule extends HudModule {

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.showFps;
    }
    
    @Override
    public boolean supportsFade() {
        return true;
    }

    @Override
    public SimpleCPSConfig.Position getPositionType() {
        return SimpleCPSConfig.instance.fpsPosition;
    }

    @Override
    public int getXOffset() {
        return SimpleCPSConfig.instance.fpsXOffset;
    }

    @Override
    public int getYOffset() {
        return SimpleCPSConfig.instance.fpsYOffset;
    }

    @Override
    public String getName() {
        return "FPS";
    }

    @Override public void setPositionType(SimpleCPSConfig.Position pos) { SimpleCPSConfig.instance.fpsPosition = pos; }
    @Override public void setXOffset(int x) { SimpleCPSConfig.instance.fpsXOffset = x; }
    @Override public void setYOffset(int y) { SimpleCPSConfig.instance.fpsYOffset = y; }
    @Override public void setScale(int scale) { SimpleCPSConfig.instance.fpsScale = scale; }
    @Override public int getScale() { return SimpleCPSConfig.instance.fpsScale; }
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.fpsPosition = SimpleCPSConfig.Position.TOP_LEFT;
        SimpleCPSConfig.instance.fpsXOffset = 0;
        SimpleCPSConfig.instance.fpsYOffset = 0;
        SimpleCPSConfig.instance.fpsScale = 100;
    }

    @Override
    public void resetVisualDefaults() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        config.fpsScale = 100;
        config.fpsColor = 0xFFFFFF;
        config.fpsRainbow = false;
        config.fpsText = "FPS";
        config.fpsShowBackground = false;
        config.fpsBackgroundColor = 0x000000;
        config.fpsBackgroundOpacity = 128;
    }

    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>(super.getContextMenuSettings());
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable FPS", () -> config.showFps, v -> config.showFps = v),
            new com.eymistaken.simplecps.api.ColorSetting("Text Color", () -> config.fpsColor, v -> config.fpsColor = v)
        ));
        return settings;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String fpsStr = client.getFps() + " " + config.fpsText;
        
        float scale = config.fpsScale / 100f;
        int textWidth = (int)(textWidth(fpsStr) * scale);
        int textHeight = (int)(client.font.lineHeight * scale);
        int padding = 2;
        
        int color = config.fpsRainbow ? getRainbowColor() : config.fpsColor;
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;

        context.pose().pushMatrix();
        context.pose().translate((float)x, (float)y);
        context.pose().scale(scale, scale);
        
        if (config.fpsShowBackground) {
            int bgX = -padding;
            int bgY = -padding;
            int bgW_local = (int)(textWidth / scale) + (padding * 2);
            int bgH_local = (int)(textHeight / scale) + (padding * 2);
            int bgAlphaColor = (config.fpsBackgroundOpacity << 24) | (config.fpsBackgroundColor & 0x00FFFFFF);
            context.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, col(bgAlphaColor));
        }

        drawText(context, fpsStr, 0, 0, color);
        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String fpsStr = client.getFps() + " " + config.fpsText;
        float scale = config.fpsScale / 100f;
        int textWidth = (int)(textWidth(fpsStr) * scale);
        int padding = 2;
        return config.fpsShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.fpsScale / 100f;
        int textHeight = (int)(client.font.lineHeight * scale);
        int padding = 2;
        return config.fpsShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
    }
}
