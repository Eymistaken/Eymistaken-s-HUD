package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.ReachTracker;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.gui.DrawContext;

public class ReachModule extends HudModule {

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.showReach && (ReachTracker.getReachDisplay() != null || SimpleCPSConfig.instance.reachAlwaysShow); // Logic check
        // Note: SimpleCPSClient check was:
        // boolean shouldRenderReach = config.showReach && (ReachTracker.getReachDisplay() != null || isEditor);
        // I'll stick to config enabled here. Internal logic can decide to render empty if not active, or I return false/0 size.
        // Actually, if I return enabled=true but it's empty, it might take up padding space if I'm not careful.
        // Let's use getWidth/Height to return 0 if not showing.
    }
    
    private boolean shouldShow() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        return config.showReach && (ReachTracker.getReachDisplay() != null || config.reachAlwaysShow /* || isEditor? */);
    }
    
    // I will let isEnabled() assume true if config says so, effectively.
    // But Render/Size will rely on content.

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

    @Override
    public void render(DrawContext context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        // Re-check visibility logic just in case
        String reachText = ReachTracker.getReachDisplay();
        if (reachText == null) reachText = config.reachNoHitText; // Or "No Hit"
        
        // If valid or always show
        if (reachText == null && !config.reachAlwaysShow) return;

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
        if (!shouldShow()) return 0;
        
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String reachText = ReachTracker.getReachDisplay();
        if (reachText == null) reachText = config.reachNoHitText;
        
        float scale = config.reachScale / 100f;
        int textWidth = (int)(client.textRenderer.getWidth(reachText) * scale);
        int padding = 2;
        return config.reachShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    @Override
    public int getHeight() {
        if (!shouldShow()) return 0;

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.reachScale / 100f;
        int textHeight = (int)(client.textRenderer.fontHeight * scale);
        int padding = 2;
        return config.reachShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
    }
}
