package com.eymistaken.simplecps.util;

import com.eymistaken.simplecps.SimpleCPSConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates key numbers between the two numberings Minecraft has reported them in.
 *
 * <p>26.3 moved the client from GLFW to SDL, and the two number keys differently: GLFW used
 * its own codes (W is 87, left shift 340), SDL reports USB scancodes (W is 26, left shift
 * 225). A {@link SimpleCPSConfig.KeyButtonData#keyCode} is that number written straight into
 * {@code simplecps.json}, so a config, preset or share code names the right keys only in the
 * numbering it was written in. Each jar keeps its keys in its own game's numbering,
 * {@link #NATIVE}, and this translates on the way in and, for share codes, on the way out.
 *
 * <p>Mouse buttons were renumbered too: GLFW called the left button 0, SDL calls it 1.
 *
 * <p>The table is written as plain numbers on both sides rather than through
 * {@code InputConstants}: those constants are whichever numbering the jar was compiled for,
 * and this needs both at once.
 */
public final class InputCodes {

    /** The numbering this jar's game reports: SDL from 26.3 on, GLFW before it. */
    //? if >=26.3 {
    public static final int NATIVE = SimpleCPSConfig.KEY_CODES_SDL;
    //?} else
    //public static final int NATIVE = SimpleCPSConfig.KEY_CODES_GLFW;

    /** SDL's "no scancode", what an unbound key carries on 26.3. */
    public static final int SDL_UNBOUND = 0;
    /** GLFW's "unknown key", what an unbound key carries before 26.3. */
    public static final int GLFW_UNBOUND = -1;

    /** What an unbound key button carries in this game's numbering. */
    //? if >=26.3 {
    public static final int UNBOUND = SDL_UNBOUND;
    //?} else
    //public static final int UNBOUND = GLFW_UNBOUND;

    /**
     * The Application key, which GLFW calls MENU, in this game's numbering. Neither version
     * of {@code InputConstants} has a name for it.
     */
    //? if >=26.3 {
    public static final int KEY_MENU = 101;
    //?} else
    //public static final int KEY_MENU = 348;

    /** GLFW key code -> SDL scancode. Anything missing is a key the other side does not report. */
    private static final Map<Integer, Integer> GLFW_TO_SDL = new HashMap<>();
    /** SDL scancode -> GLFW key code, the same pairs read the other way. */
    private static final Map<Integer, Integer> SDL_TO_GLFW = new HashMap<>();

    static {
        put(32, 44);   // space
        put(39, 52);   // apostrophe
        put(44, 54);   // comma
        put(45, 45);   // minus
        put(46, 55);   // period
        put(47, 56);   // slash
        put(59, 51);   // semicolon
        put(61, 46);   // equals
        put(91, 47);   // left bracket
        put(92, 49);   // backslash
        put(93, 48);   // right bracket
        put(96, 53);   // grave

        // 1-9 run on from 1, but SDL puts 0 after 9 rather than before 1.
        put(48, 39);
        for (int i = 1; i <= 9; i++) put(48 + i, 30 + i - 1);
        // A-Z and F1-F24 are contiguous in both numberings.
        for (int i = 0; i < 26; i++) put(65 + i, 4 + i);
        for (int i = 0; i < 12; i++) put(290 + i, 58 + i);
        for (int i = 0; i < 12; i++) put(302 + i, 104 + i);
        // GLFW's F25 has no SDL counterpart and is left unmapped.

        put(256, 41);  // escape
        put(257, 40);  // return
        put(258, 43);  // tab
        put(259, 42);  // backspace
        put(260, 73);  // insert
        put(261, 76);  // delete
        put(262, 79);  // right
        put(263, 80);  // left
        put(264, 81);  // down
        put(265, 82);  // up
        put(266, 75);  // page up
        put(267, 78);  // page down
        put(268, 74);  // home
        put(269, 77);  // end
        put(280, 57);  // caps lock
        put(281, 71);  // scroll lock
        put(282, 83);  // num lock
        put(283, 70);  // print screen
        put(284, 72);  // pause

        // The keypad: SDL again orders 0 after 9.
        put(320, 98);
        for (int i = 1; i <= 9; i++) put(320 + i, 89 + i - 1);
        put(330, 99);  // decimal
        put(331, 84);  // divide
        put(332, 85);  // multiply
        put(333, 86);  // subtract
        put(334, 87);  // add
        put(335, 88);  // enter
        put(336, 103); // equals

        put(340, 225); // left shift
        put(341, 224); // left control
        put(342, 226); // left alt
        put(343, 227); // left super / GUI
        put(344, 229); // right shift
        put(345, 228); // right control
        put(346, 230); // right alt
        put(347, 231); // right super / GUI
        put(348, 101); // menu / application
    }

    private InputCodes() {}

    private static void put(int glfwKey, int scancode) {
        GLFW_TO_SDL.put(glfwKey, scancode);
        SDL_TO_GLFW.put(scancode, glfwKey);
    }

    /**
     * The numbering a stored {@code keyCodeScheme} stands for. Null is a config from before
     * Minecraft 26.3, which only knew GLFW; anything at or above SDL's marker is SDL, the way
     * the 26.3 builds that wrote only SDL have always read it.
     */
    public static int schemeOf(Integer stored) {
        return stored == null || stored < SimpleCPSConfig.KEY_CODES_SDL
            ? SimpleCPSConfig.KEY_CODES_GLFW
            : SimpleCPSConfig.KEY_CODES_SDL;
    }

    /** @return {@code code} in the {@code to} numbering, or that numbering's unbound value. */
    public static int key(int code, int from, int to) {
        if (from == to) return code;
        return to == SimpleCPSConfig.KEY_CODES_SDL
            ? GLFW_TO_SDL.getOrDefault(code, SDL_UNBOUND)
            : SDL_TO_GLFW.getOrDefault(code, GLFW_UNBOUND);
    }

    /**
     * @return mouse button {@code button} in the {@code to} numbering. GLFW counts from zero
     *     with the right button second; SDL counts from one with the middle button second.
     *     Beyond the first three both keep their order and differ by one.
     */
    public static int mouse(int button, int from, int to) {
        if (from == to) return button;
        if (to == SimpleCPSConfig.KEY_CODES_SDL) {
            return switch (button) {
                case 0 -> 1;
                case 1 -> 3;
                case 2 -> 2;
                default -> button < 0 ? 1 : button + 1;
            };
        }
        return switch (button) {
            case 1 -> 0;
            case 3 -> 1;
            case 2 -> 2;
            default -> button < 1 ? 0 : button - 1;
        };
    }

    /** Translate a whole keystrokes layout in place. */
    public static void translate(List<SimpleCPSConfig.KeyButtonData> layout, int from, int to) {
        if (layout == null || from == to) return;
        for (SimpleCPSConfig.KeyButtonData key : layout) {
            if (key == null) continue;
            key.keyCode = key.isMouse ? mouse(key.keyCode, from, to) : key(key.keyCode, from, to);
        }
    }
}
