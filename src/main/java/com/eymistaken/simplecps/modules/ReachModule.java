package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.ReachTracker;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.gui.DrawContext;

public class ReachModule extends HudModule {

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.showReach;
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

    private String getDisplayText() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String text = ReachTracker.getReachDisplay();
        
        boolean isEditor = client.currentScreen instanceof com.eymistaken.simplecps.gui.HudEditorScreen;
        
        if (text == null) {
            if (isEditor) {
                return "3.00 blocks"; // Dummy text for HUD Editor
            } else if (config.reachAlwaysShow) {
                return config.reachNoHitText;
            } else {
                return null;
            }
        }
        return text;
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        String reachText = getDisplayText();
        if (reachText == null) return;

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.reachScale / 100f;
        int textWidth = (int)(client.textRenderer.getWidth(reachText) * scale);
        int textHeight = (int)(client.textRenderer.fontHeight * scale);
        int padding = 2;
        
        int color = config.reachRainbow ? getRainbowColor() : config.reachColor;
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)x, (float)y);
        context.getMatrices().scale(scale, scale);
        
        if (config.reachShowBackground) {
            int bgX = -padding;
            int bgY = -padding;
            int bgW_local = (int)(textWidth / scale) + (padding * 2);
            int bgH_local = (int)(textHeight / scale) + (padding * 2);
            int bgAlphaColor = (config.reachBackgroundOpacity << 24) | (config.reachBackgroundColor & 0x00FFFFFF);
            context.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
        }

        context.drawTextWithShadow(client.textRenderer, reachText, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    @Override
    public int getWidth() {
        String reachText = getDisplayText();
        if (reachText == null) return 0;
        
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.reachScale / 100f;
        int textWidth = (int)(client.textRenderer.getWidth(reachText) * scale);
        int padding = 2;
        return config.reachShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    @Override
    public int getHeight() {
        String reachText = getDisplayText();
        if (reachText == null) return 0;

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.reachScale / 100f;
        int textHeight = (int)(client.textRenderer.fontHeight * scale);
        int padding = 2;
        return config.reachShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
    }
}
