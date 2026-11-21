package com.eymistaken.simplecps.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;

/**
 * Held-key state and the few key numbers that differ between Minecraft versions.
 *
 * <p>Nothing outside {@code compat} reads the keyboard through
 * {@code InputConstants.isKeyDown} or names the super keys directly: 26.3 reads SDL
 * scancodes, older versions GLFW key codes and a window handle, and the super keys do not
 * share a constant name between them. Keeping every such call here is what lets one source
 * build against all of them.
 */
public final class Keys {

    /** The left Windows/Command key. A compile-time constant, so it works as a switch label. */
    public static final int LEFT_SUPER = InputConstants.KEY_LGUI;
    /** The right Windows/Command key. */
    public static final int RIGHT_SUPER = InputConstants.KEY_RGUI;

    private Keys() {}

    /** Whether the game sees {@code code} held right now. */
    public static boolean isKeyDown(int code) {
        return InputConstants.isKeyDown(code);
    }

    /** Either Ctrl key, read from the keyboard rather than from an event. */
    public static boolean ctrlDown() {
        return isKeyDown(InputConstants.KEY_LCONTROL) || isKeyDown(InputConstants.KEY_RCONTROL);
    }

    /** Either Shift key, read from the keyboard rather than from an event. */
    public static boolean shiftDown() {
        return isKeyDown(InputConstants.KEY_LSHIFT) || isKeyDown(InputConstants.KEY_RSHIFT);
    }

    /**
     * Ctrl as a shortcut modifier for this key press. On macOS this is Command, the way the
     * game's own text fields read it.
     */
    public static boolean ctrlDown(KeyEvent event) {
        return event.hasControlDownWithQuirk();
    }

    /** What the game calls a keyboard key in Controls, on this keyboard layout. */
    public static String displayName(int keyCode) {
        return InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString();
    }
}
