package com.eymistaken.simplecps.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * What is left of the HUD font picker, kept for plugins compiled against it.
 *
 * <p>The custom fonts were removed and everything now draws in the vanilla font, but
 * published plugins call this class directly: Eymistaken's Potion HUD 1.0.4 styles
 * its text through {@link #text(String)}. Deleting the class crashed the game the
 * moment such a plugin drew its first frame, so it stays, with every method answering
 * "vanilla". Do not remove it, or change these signatures, while any released plugin
 * still links against it.
 */
@Deprecated
public final class EymHudFonts {

    private EymHudFonts() {}

    /** Always {@link Style#EMPTY}: the vanilla font. */
    public static Style activeStyle() {
        return Style.EMPTY;
    }

    /** Plain text in the vanilla font. */
    public static Component text(String s) {
        return Component.literal(s);
    }

    /** Always false: the enchanting-table font no longer exists. */
    public static boolean isEnchantActive() {
        return false;
    }
}
