package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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
    public boolean supportsFade() {
        return true;
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

    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>(super.getContextMenuSettings());
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable CPS", () -> config.enabled, v -> config.enabled = v),
            new com.eymistaken.simplecps.api.ColorSetting("Text Color", () -> config.textColor, v -> config.textColor = v),
            new com.eymistaken.simplecps.api.SliderSetting("Scale %", 50, 300, 100, () -> config.scale, v -> config.scale = v)
        ));
        return settings;
    }

    public static void addLeftClick() {
        leftClicks.add(System.currentTimeMillis());
    }

    public static void addRightClick() {
        rightClicks.add(System.currentTimeMillis());
    }
    
    @Override
    public void resetVisualDefaults() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        config.scale = 100;
        config.textColor = 0xFFFFFF;
        config.rightClickCps = true;
        config.rainbow = false;
        config.cpsShowBackground = false;
        config.cpsBackgroundColor = 0x000000;
        config.cpsBackgroundOpacity = 128;
        config.cpsLeftText = "";
        config.cpsRightText = "";
        config.cpsSeparator = " | ";
    }

    @Override
    public void tick(net.minecraft.client.Minecraft client) {
        long now = System.currentTimeMillis();
        leftClicks.removeIf(time -> now - time > 1000);
        rightClicks.removeIf(time -> now - time > 1000);
    }

    @Override
    public com.eymistaken.simplecps.api.HudPreview getPreview() {
        return com.eymistaken.simplecps.api.HudPreview.ofModule(this);
    }

    /** Stand-in click rates for the preview; nobody is clicking while the menu is open. */
    private static final int PREVIEW_LEFT = 12;
    private static final int PREVIEW_RIGHT = 4;

    /**
     * The string this module draws, built in one place because render, {@link #getWidth()}
     * and {@link #getHeight()} all need it to agree — the preview would otherwise be
     * measured for live clicks and drawn with sample ones.
     */
    private String displayText() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String leftCps = String.valueOf(isPreviewing() ? PREVIEW_LEFT : leftClicks.size());
        String rightCps = String.valueOf(isPreviewing() ? PREVIEW_RIGHT : rightClicks.size());

        String cpsText = config.cpsLeftText + leftCps;
        if (config.rightClickCps) {
            cpsText += config.cpsSeparator + rightCps + config.cpsRightText;
        }
        return cpsText;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;

        String cpsText = displayText();

        int color = config.rainbow ? getRainbowColor() : config.textColor;
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;

        float scale = config.scale / 100f;
        int textWidth = (int)(textWidth(cpsText) * scale);
        int textHeight = (int)(client.font.lineHeight * scale);
        int padding = 2; 

        context.pose().pushMatrix();
        context.pose().translate((float)x, (float)y);
        context.pose().scale(scale, scale);
        
        if (config.cpsShowBackground) {
            int bgX = -padding;
            int bgY = -padding;
            int bgW_local = (int)(textWidth / scale) + (padding * 2); 
            int bgH_local = (int)(textHeight / scale) + (padding * 2);
            int bgAlphaColor = (config.cpsBackgroundOpacity << 24) | (config.cpsBackgroundColor & 0x00FFFFFF);
            context.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, col(bgAlphaColor));
        }

        drawText(context, cpsText, 0, 0, color);
        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        String cpsText = displayText();
        float scale = config.scale / 100f;
        int textWidth = (int)(textWidth(cpsText) * scale);
        int padding = 2;
        return config.cpsShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = config.scale / 100f;
        int textHeight = (int)(client.font.lineHeight * scale);
        int padding = 2;
        return config.cpsShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
    }
}
