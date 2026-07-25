package com.eymistaken.simplecps.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shared rendering helpers. Colours are packed ARGB ints ({@code 0xAARRGGBB}),
 * matching what {@code GuiGraphicsExtractor.fill/text} expect.
 */
public final class RenderUtil {

    private RenderUtil() {}

    /**
     * Draw {@code text} with a 1px outline on all 8 sides (drawn shadowless),
     * then the fill text on top. Replaces the vanilla drop shadow with a crisp
     * border that reads better over busy PvP backgrounds.
     */
    public static void outlinedText(GuiGraphicsExtractor ctx, Font font, Component text, int x, int y, int color, int outlineColor) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                ctx.text(font, text, x + dx, y + dy, outlineColor, false);
            }
        }
        ctx.text(font, text, x, y, color, false);
    }

    /**
     * Scale an ARGB colour's alpha channel by {@code factor} (clamped to
     * {@code [0,1]}). Used to fade elements/menus in and out without touching
     * their RGB. {@code factor >= 1} returns the colour unchanged.
     */
    public static int withAlpha(int argb, float factor) {
        factor = Easings.clamp01(factor);
        int a = (argb >>> 24) & 0xFF;
        a = Math.round(a * factor);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Pack a background box colour the way the text modules do inline today:
     * {@code (opacity << 24) | (rgb & 0x00FFFFFF)}.
     *
     * @param color   RGB colour (upper alpha byte ignored)
     * @param opacity 0..255 alpha
     */
    public static int packBg(int color, int opacity) {
        return ((opacity & 0xFF) << 24) | (color & 0x00FFFFFF);
    }
}
