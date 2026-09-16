package com.eymistaken.simplecps.util;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates the key numbers Minecraft reported before 26.3 into the ones it reports now.
 *
 * <p>26.3 moved the client from GLFW to SDL, and the two number keys differently: GLFW used
 * its own codes (W is 87, left shift 340), SDL reports USB scancodes (W is 26, left shift
 * 225). A {@link SimpleCPSConfig.KeyButtonData#keyCode} is that number written straight into
 * {@code simplecps.json}, so every config, preset and share code written by an older build
 * names the wrong keys until it is translated — which is what this does, once, on load.
 *
 * <p>Mouse buttons were renumbered too: GLFW called the left button 0, SDL calls it 1.
 */
public final class InputCodes {

    /** What an unbound key button carries: SDL's "no scancode". GLFW spelled it -1. */
    public static final int UNBOUND = 0;

    /** SDL's Application key, which GLFW called MENU. {@link InputConstants} has no name for it. */
    public static final int KEY_MENU = 101;

    /** GLFW key code -> SDL scancode. Anything missing was a key SDL does not report. */
    private static final Map<Integer, Integer> LEGACY_KEYS = new HashMap<>();

    static {
        put(32, InputConstants.KEY_SPACE);
        put(39, InputConstants.KEY_APOSTROPHE);
        put(44, InputConstants.KEY_COMMA);
        put(45, InputConstants.KEY_MINUS);
        put(46, InputConstants.KEY_PERIOD);
        put(47, InputConstants.KEY_SLASH);
        put(59, InputConstants.KEY_SEMICOLON);
        put(61, InputConstants.KEY_EQUALS);
        put(91, InputConstants.KEY_LBRACKET);
        put(92, InputConstants.KEY_BACKSLASH);
        put(93, InputConstants.KEY_RBRACKET);
        put(96, InputConstants.KEY_GRAVE);

        // 1-9 run on from KEY_1, but SDL puts 0 after 9 rather than before 1.
        put(48, InputConstants.KEY_0);
        for (int i = 1; i <= 9; i++) put(48 + i, InputConstants.KEY_1 + i - 1);
        // A-Z and F1-F24 are contiguous in both numberings.
        for (int i = 0; i < 26; i++) put(65 + i, InputConstants.KEY_A + i);
        for (int i = 0; i < 12; i++) put(290 + i, InputConstants.KEY_F1 + i);
        for (int i = 0; i < 12; i++) put(302 + i, InputConstants.KEY_F13 + i);
        // GLFW's F25 has no SDL counterpart and is left unmapped.

        put(256, InputConstants.KEY_ESCAPE);
        put(257, InputConstants.KEY_RETURN);
        put(258, InputConstants.KEY_TAB);
        put(259, InputConstants.KEY_BACKSPACE);
        put(260, InputConstants.KEY_INSERT);
        put(261, InputConstants.KEY_DELETE);
        put(262, InputConstants.KEY_RIGHT);
        put(263, InputConstants.KEY_LEFT);
        put(264, InputConstants.KEY_DOWN);
        put(265, InputConstants.KEY_UP);
        put(266, InputConstants.KEY_PAGEUP);
        put(267, InputConstants.KEY_PAGEDOWN);
        put(268, InputConstants.KEY_HOME);
        put(269, InputConstants.KEY_END);
        put(280, InputConstants.KEY_CAPSLOCK);
        put(281, InputConstants.KEY_SCROLLLOCK);
        put(282, InputConstants.KEY_NUMLOCK);
        put(283, InputConstants.KEY_PRINTSCREEN);
        put(284, InputConstants.KEY_PAUSE);

        // The keypad: SDL again orders 0 after 9, and names only some of the operators.
        put(320, InputConstants.KEY_NUMPAD0);
        for (int i = 1; i <= 9; i++) put(320 + i, InputConstants.KEY_NUMPAD1 + i - 1);
        put(330, 99);  // KP_DECIMAL
        put(331, 84);  // KP_DIVIDE
        put(332, InputConstants.KEY_MULTIPLY);
        put(333, 86);  // KP_SUBTRACT
        put(334, InputConstants.KEY_ADD);
        put(335, InputConstants.KEY_NUMPADENTER);
        put(336, InputConstants.KEY_NUMPADEQUALS);

        put(340, InputConstants.KEY_LSHIFT);
        put(341, InputConstants.KEY_LCONTROL);
        put(342, InputConstants.KEY_LALT);
        put(343, InputConstants.KEY_LGUI);
        put(344, InputConstants.KEY_RSHIFT);
        put(345, InputConstants.KEY_RCONTROL);
        put(346, InputConstants.KEY_RALT);
        put(347, InputConstants.KEY_RGUI);
        put(348, KEY_MENU);
    }

    private InputCodes() {}

    private static void put(int glfwKey, int scancode) {
        LEGACY_KEYS.put(glfwKey, scancode);
    }

    /** @return the SDL scancode for a GLFW key code, or {@link #UNBOUND} if SDL has none. */
    public static int fromLegacyKey(int glfwKey) {
        return LEGACY_KEYS.getOrDefault(glfwKey, UNBOUND);
    }

    /**
     * @return the SDL button for a GLFW mouse button. GLFW counted from zero with the right
     *     button second; SDL counts from one with the middle button second.
     */
    public static int fromLegacyMouse(int glfwButton) {
        return switch (glfwButton) {
            case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
            case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
            case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
            // The rest keep their order and only shift by one.
            default -> glfwButton < 0 ? InputConstants.MOUSE_BUTTON_LEFT : glfwButton + 1;
        };
    }

    /** Translate a whole keystrokes layout in place. */
    public static void migrate(List<SimpleCPSConfig.KeyButtonData> layout) {
        if (layout == null) return;
        for (SimpleCPSConfig.KeyButtonData key : layout) {
            if (key == null) continue;
            key.keyCode = key.isMouse ? fromLegacyMouse(key.keyCode) : fromLegacyKey(key.keyCode);
        }
    }
}
