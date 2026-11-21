package com.eymistaken.simplecps.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opening screens, reading the open one, and F1's hidden-HUD state.
 *
 * <p>All three moved between versions: 26.2 and 26.3 keep them on {@code Minecraft.gui},
 * 26.1 and 1.21.x on {@code Minecraft} itself and its options. Nothing outside
 * {@code compat} touches them directly.
 */
public final class Screens {

    private Screens() {}

    /** Show {@code screen}, or return to the game for {@code null}. */
    public static void open(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /** The screen on display, or {@code null} while playing. */
    public static Screen current() {
        return Minecraft.getInstance().gui.screen();
    }

    /** Whether F1 has the HUD hidden. */
    public static boolean hudHidden() {
        return Minecraft.getInstance().gui.hud.isHidden();
    }

    /** Hide or show the HUD the way F1 does. Does nothing if it is already that way. */
    public static void setHudHidden(boolean hidden) {
        if (hudHidden() != hidden) Minecraft.getInstance().gui.hud.toggle();
    }
}
