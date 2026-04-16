package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingModule extends HudModule {

    private int cachedPing = -1;
    private PlayerInfo cachedEntry = null;

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.showPing;
    }

    @Override
    public SimpleCPSConfig.Position getPositionType() {
        return SimpleCPSConfig.instance.pingPosition;
    }

    @Override
    public int getXOffset() {
        return SimpleCPSConfig.instance.pingXOffset;
    }

    @Override
    public int getYOffset() {
        return SimpleCPSConfig.instance.pingYOffset;
    }

    @Override
    public String getName() {
        return "Ping";
    }

    @Override public void setPositionType(SimpleCPSConfig.Position pos) { SimpleCPSConfig.instance.pingPosition = pos; }
    @Override public void setXOffset(int x) { SimpleCPSConfig.instance.pingXOffset = x; }
    @Override public void setYOffset(int y) { SimpleCPSConfig.instance.pingYOffset = y; }
    // Ping has no scale config, so keep default.
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.pingPosition = SimpleCPSConfig.Position.TOP_LEFT;
        SimpleCPSConfig.instance.pingXOffset = 0;
        SimpleCPSConfig.instance.pingYOffset = 0;
    }
    
    @Override
    public void resetVisualDefaults() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        config.pingColor = 0xFFFFFF;
        config.pingShowBackground = false;
        config.pingBackgroundColor = 0x000000;
        config.pingBackgroundOpacity = 128;
    }

    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>(super.getContextMenuSettings());
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Ping", () -> config.showPing, v -> config.showPing = v),
            new com.eymistaken.simplecps.api.ColorSetting("Text Color", () -> config.pingColor, v -> config.pingColor = v)
        ));
        return settings;
    }

    @Override
    public void tick(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
            cachedPing = -1;
            cachedEntry = null;
            return;
        }

        PlayerInfo entry = client.getConnection().getPlayerInfo(client.player.getUUID());

        if (entry != null) {
            cachedEntry = entry;
            cachedPing = entry.getLatency();
        } else {
            cachedEntry = null;
            cachedPing = -1;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        int latency = cachedPing;
        if (cachedEntry != null) latency = cachedEntry.getLatency();
        String pingText = (latency < 0) ? "? ms" : (latency + " ms");
        
        float scale = 1.0f; 
        int textWidth = (int)(client.font.width(pingText) * scale);
        int textHeight = (int)(client.font.lineHeight * scale);
        int padding = 2;
        int pColor = config.pingColor;
        if ((pColor & 0xFF000000) == 0) pColor |= 0xFF000000;

        context.pose().pushMatrix();
        context.pose().translate((float)x, (float)y);
        context.pose().scale(scale, scale);
        
        if (config.pingShowBackground) {
            int bgX = -padding;
            int bgY = -padding;
            int bgW_local = (int)(textWidth / scale) + (padding * 2);
            int bgH_local = (int)(textHeight / scale) + (padding * 2);
            int bgAlphaColor = (config.pingBackgroundOpacity << 24) | (config.pingBackgroundColor & 0x00FFFFFF);
            context.fill(bgX, bgY, bgX + bgW_local, bgY + bgH_local, bgAlphaColor);
        }

        context.text(client.font, pingText, 0, 0, pColor);
        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        int latency = cachedPing;
        if (cachedEntry != null) latency = cachedEntry.getLatency();
        String pingText = (latency < 0) ? "? ms" : (latency + " ms");
        float scale = 1.0f;
        int textWidth = (int)(client.font.width(pingText) * scale);
        int padding = 2;
        return config.pingShowBackground ? (int)(((textWidth / scale) + padding * 2) * scale) : textWidth;
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float scale = 1.0f;
        int textHeight = (int)(client.font.lineHeight * scale);
        int padding = 2;
        return config.pingShowBackground ? (int)(((textHeight / scale) + padding * 2) * scale) : textHeight;
    }
}
