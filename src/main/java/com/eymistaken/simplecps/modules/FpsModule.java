package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.gui.DrawContext;

public class FpsModule extends HudModule {

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.showFps;
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

    @Override
    public void render(DrawContext context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String fpsStr = client.getCurrentFps() + " " + config.fpsText;
        
        float scale = config.fpsScale / 100f;
        int textWidth = (int)(client.textRenderer.getWidth(fpsStr) * scale);
        int textHeight = (int)(client.textRenderer.fontHeight * scale);
        int padding = 2;
        
        int color = config.fpsRainbow ? getRainbowColor() : config.fpsColor;
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)x, (float)y);
        context.getMatrices().scale(scale, scale);
        
        if (config.fpsShowBackground) {
            int bgX = -padding;
            int bgY = -padding;
            int bgW_local = (int)(textWidth / scale) + (padding * 2);
            int bgH_local = (int)(textHeight / scale) + (padding * 2);
            int bgAlphaColor = (config.fpsBackgroundOpacity << 24) | (config.fpsBackgroundColor & 0x00FFFFFF);
            context.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
        }

        context.drawTextWithShadow(client.textRenderer, fpsStr, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String fpsStr = client.getCurrentFps() + " " + config.fpsText;
        float scale = config.fpsScale / 100f;
        int textWidth = (int)(client.textRenderer.getWidth(fpsStr) * scale);
        int padding = 2;
        return config.fpsShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.fpsScale / 100f;
        int textHeight = (int)(client.textRenderer.fontHeight * scale);
        int padding = 2;
        return config.fpsShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
    }
}
