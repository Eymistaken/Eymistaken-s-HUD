package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public class CpsModule extends HudModule {

    public static final List<Long> leftClicks = new ArrayList<>();
    public static final List<Long> rightClicks = new ArrayList<>();
    private boolean wasLeftPressed = false;
    private boolean wasRightPressed = false;

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.enabled;
    }
    
    @Override
    public SimpleCPSConfig.Position getPositionType() {
        return SimpleCPSConfig.instance.position;
    }

    @Override
    public int getXOffset() {
        return SimpleCPSConfig.instance.xOffset;
    }

    @Override
    public int getYOffset() {
        return SimpleCPSConfig.instance.yOffset;
    }

    @Override
    public String getName() {
        return "CPS";
    }

    @Override public void setPositionType(SimpleCPSConfig.Position pos) { SimpleCPSConfig.instance.position = pos; }
    @Override public void setXOffset(int x) { SimpleCPSConfig.instance.xOffset = x; }
    @Override public void setYOffset(int y) { SimpleCPSConfig.instance.yOffset = y; }
    @Override public void setScale(int scale) { SimpleCPSConfig.instance.scale = scale; }
    @Override public int getScale() { return SimpleCPSConfig.instance.scale; }
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.position = SimpleCPSConfig.Position.TOP_LEFT;
        SimpleCPSConfig.instance.xOffset = 0;
        SimpleCPSConfig.instance.yOffset = 0;
    }

    public static void addLeftClick() {
        leftClicks.add(System.currentTimeMillis());
    }

    public static void addRightClick() {
        rightClicks.add(System.currentTimeMillis());
    }

    @Override
    public void tick(net.minecraft.client.MinecraftClient client) {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        leftClicks.removeIf(time -> now - time > 1000);
        rightClicks.removeIf(time -> now - time > 1000);
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;

        // Rendering
        String leftCps = String.valueOf(leftClicks.size());
        String rightCps = String.valueOf(rightClicks.size());
        
        String cpsText = config.cpsLeftText + leftCps;
        if (config.rightClickCps) {
            cpsText += config.cpsSeparator + config.cpsRightText + rightCps;
        }

        int color = config.rainbow ? getRainbowColor() : config.textColor;
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;

        float scale = config.scale / 100f;
        int textWidth = (int)(client.textRenderer.getWidth(cpsText) * scale);
        int textHeight = (int)(client.textRenderer.fontHeight * scale);
        int padding = 2; 

        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)x, (float)y);
        context.getMatrices().scale(scale, scale);
        
        if (config.cpsShowBackground) {
            int bgX = -padding;
            int bgY = -padding;
            int bgW_local = (int)(textWidth / scale) + (padding * 2); 
            int bgH_local = (int)(textHeight / scale) + (padding * 2);
            int bgAlphaColor = (config.cpsBackgroundOpacity << 24) | (config.cpsBackgroundColor & 0x00FFFFFF);
            context.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
        }
        
        context.drawTextWithShadow(client.textRenderer, cpsText, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String leftCps = String.valueOf(leftClicks.size());
        String rightCps = String.valueOf(rightClicks.size());
        String cpsText = config.cpsLeftText + leftCps;
        if (config.rightClickCps) {
            cpsText += config.cpsSeparator + config.cpsRightText + rightCps;
        }
        float scale = config.scale / 100f;
        int textWidth = (int)(client.textRenderer.getWidth(cpsText) * scale);
        int padding = 2;
        return config.cpsShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.scale / 100f;
        int textHeight = (int)(client.textRenderer.fontHeight * scale);
        int padding = 2;
        return config.cpsShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
    }
}
