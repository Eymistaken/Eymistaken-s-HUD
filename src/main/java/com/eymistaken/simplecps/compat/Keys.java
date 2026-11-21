package com.eymistaken.simplecps.compat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
//? if <26.3
//import net.minecraft.client.Minecraft;
//? if <26.1
//import net.minecraft.client.input.InputQuirks;

/**
 * Held-key state and the few key numbers that differ between Minecraft versions.
 *
 * <p>Nothing outside {@code compat} reads the keyboard through
 * {@code InputConstants.isKeyDown} or names the super keys directly: 26.3 reads SDL
 * scancodes, older versions GLFW key codes and a window handle, and the super keys do not
 * share a constant name between them. Keeping every such call here is what lets one source
 * build against all of them.
 *
 * <p>Every key number the mod handles at run time is this game's own: SDL scancodes on 26.3,
 * GLFW key codes before it. {@link com.eymistaken.simplecps.util.InputCodes} translates
 * between the two where a number crosses from one game to another, in a config or a share code.
 */
public final class Keys {

    // The left and right Windows/Command keys. Compile-time constants, so they work as
    // switch labels. SDL calls them GUI, GLFW SUPER.
    //? if >=26.3 {
    public static final int LEFT_SUPER = InputConstants.KEY_LGUI;
    public static final int RIGHT_SUPER = InputConstants.KEY_RGUI;
    //?} else {
    /*public static final int LEFT_SUPER = InputConstants.KEY_LSUPER;
    public static final int RIGHT_SUPER = InputConstants.KEY_RSUPER;
    *///?}

    private Keys() {}

    /** Whether the game sees {@code code} held right now. */
    public static boolean isKeyDown(int code) {
        //? if >=26.3 {
        return InputConstants.isKeyDown(code);
        //?} else
        //return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), code);
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
        //? if >=26.1 {
        return event.hasControlDownWithQuirk();
        //?} else {
        /*// The 1.21.x jar runs on two meanings of hasControlDown: 1.21.9 and 1.21.10 count
        // Command on macOS, 1.21.11 only Ctrl and adds hasControlDownWithQuirk for the old
        // reading. Testing the modifier both read gives the same answer on all three.
        return (event.modifiers() & InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER) != 0;
        *///?}
    }

    /** What the game calls a keyboard key in Controls, on this keyboard layout. */
    public static String displayName(int keyCode) {
        //? if >=26.3 {
        return InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString();
        //?} else
        //return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
    }
}
