package com.eymistaken.simplecps.api;

import com.eymistaken.simplecps.SimpleCPSClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public abstract class HudModule {

    protected final MinecraftClient client;

    public HudModule() {
        this.client = MinecraftClient.getInstance();
    }

    protected int x, y;
    private static float hue = 0;

    public void setRenderPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Called on client tick.
     */
    public void tick(MinecraftClient client) {}

    public abstract com.eymistaken.simplecps.SimpleCPSConfig.Position getPositionType();
    public abstract int getXOffset();
    public abstract int getYOffset();

    // Editable properties
    public void setPositionType(com.eymistaken.simplecps.SimpleCPSConfig.Position pos) {}
    public void setXOffset(int x) {}
    public void setYOffset(int y) {}
    public void setScale(int scale) {}
    public int getScale() { return 100; } // Default 100
    public void resetToDefaults() {}

    /**
     * @return true if the module should be rendered
     */
    public abstract boolean isEnabled();

    /**
     * @return the width of the module content
     */
    public abstract int getWidth();

    /**
     * @return the height of the module content
     */
    public abstract int getHeight();

    /**
     * Render the module content.
     * The context is already translated to the correct position (x, y) if the implementation uses x/y, 
     * but usually SimpleCPSClient handles the translation or the module uses this.x/this.y.
     * Note: SimpleCPSClient logic was: translate to x,y then draw at 0,0.
     * 
     * @param context DrawContext for rendering
     * @param tickDelta Partial tick time
     */
    public abstract void render(DrawContext context, float tickDelta);
    
    /**
     * Returns the name of the module for identification in bounds map.
     */
    public abstract String getName();

    protected int getRainbowColor() {
        hue += 0.5f; 
        if (hue > 360) hue = 0;
        int rgb = net.minecraft.util.math.MathHelper.hsvToRgb(hue / 360f, 1.0f, 1.0f);
        return rgb | 0xFF000000;
    }
}
