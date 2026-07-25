package com.eymistaken.simplecps.util;

import com.eymistaken.simplecps.SimpleCPSConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Resolves the user-selected HUD font. In MC 26.2 a font is chosen per text run
 * via a {@link FontDescription} on the {@link Style}, not by swapping the global
 * {@code Font} object — so this only affects text we explicitly render through
 * {@link #text(String)} (HUD modules + our own editor screens), never chat or
 * other GUIs.
 */
public final class EymHudFonts {

    private static final String NS = "simplecps";

    private EymHudFonts() {}

    public static Identifier idFor(SimpleCPSConfig.HudFont font) {
        // Null when a config names a font this build does not have. Reached on every
        // drawn string, so failing here would be a per-frame crash.
        if (font == null) return null;
        return switch (font) {
            case VANILLA -> null;
            case INTER -> Identifier.fromNamespaceAndPath(NS, "inter");
            case RUBIK -> Identifier.fromNamespaceAndPath(NS, "rubik");
            case JETBRAINS_MONO -> Identifier.fromNamespaceAndPath(NS, "jetbrains_mono");
            case BEBAS_NEUE -> Identifier.fromNamespaceAndPath(NS, "bebas_neue");
            case ENCHANT -> Identifier.fromNamespaceAndPath(NS, "enchant");
        };
    }

    /** Style carrying the active HUD font, or {@link Style#EMPTY} for vanilla. */
    public static Style activeStyle() {
        Identifier id = idFor(SimpleCPSConfig.instance.hudFont);
        if (id == null) return Style.EMPTY;
        return Style.EMPTY.withFont(new FontDescription.Resource(id));
    }

    /** Wrap plain text in a Component that renders in the active HUD font. */
    public static Component text(String s) {
        return Component.literal(s).setStyle(activeStyle());
    }

    /** True when the (deliberately unreadable) enchanting-table font is active. */
    public static boolean isEnchantActive() {
        return SimpleCPSConfig.instance.hudFont == SimpleCPSConfig.HudFont.ENCHANT;
    }
}
