package com.eymistaken.simplecps.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
//? if <26.3 {
/*import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
*///?}

/**
 * Opening screens, reading the open one, and F1's hidden-HUD state.
 *
 * <p>All three moved between versions: 26.2 and 26.3 keep them on {@code Minecraft.gui},
 * 26.1 and 1.21.x on {@code Minecraft} itself and its options. Nothing outside
 * {@code compat} touches them directly.
 *
 * <p>The jar for 26.1 to 26.2 is compiled against 26.2, so it calls {@code Gui} directly and
 * reaches 26.1's versions through {@link Legacy}, which looks them up at run time. 26.x is
 * not obfuscated, so the names it looks up are the real ones and do not change between
 * 26.1 patches.
 */
public final class Screens {

    private Screens() {}

    /** Show {@code screen}, or return to the game for {@code null}. */
    public static void open(Screen screen) {
        //? if <26.3 {
        /*if (Legacy.ACTIVE) {
            Legacy.open(screen);
            return;
        }
        *///?}
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /** The screen on display, or {@code null} while playing. */
    public static Screen current() {
        //? if <26.3 {
        /*if (Legacy.ACTIVE) return Legacy.current();
        *///?}
        return Minecraft.getInstance().gui.screen();
    }

    /** Whether F1 has the HUD hidden. */
    public static boolean hudHidden() {
        //? if <26.3 {
        /*if (Legacy.ACTIVE) return Legacy.hudHidden();
        *///?}
        return Minecraft.getInstance().gui.hud.isHidden();
    }

    /** Hide or show the HUD the way F1 does. Does nothing if it is already that way. */
    public static void setHudHidden(boolean hidden) {
        if (hudHidden() == hidden) return;
        //? if <26.3 {
        /*if (Legacy.ACTIVE) {
            Legacy.setHudHidden(hidden);
            return;
        }
        *///?}
        Minecraft.getInstance().gui.hud.toggle();
    }

    //? if <26.3 {
    /*/^*
     * 26.1's homes for the three, found once, the first time any of them is used.
     *
     * <p>26.2 moved them from {@code Minecraft} onto {@code Gui} ({@code setScreen},
     * {@code screen()} and {@code hud}, whose hidden flag replaced {@code Options.hideGui}). The
     * jar is compiled against 26.2, so a direct call to 26.1's versions would not compile, and
     * a direct call to 26.2's would throw {@code NoSuchMethodError} on 26.1. Whether
     * {@code Gui.screen()} exists decides which game this is; the handles below are only
     * looked up when it does not.
     ^/
    private static final class Legacy {

        /^* True on 26.1, where {@code Gui} has none of the three. ^/
        static final boolean ACTIVE;
        private static final MethodHandle SET_SCREEN;
        private static final MethodHandle SCREEN;
        private static final MethodHandle HIDE_GUI;
        private static final MethodHandle SET_HIDE_GUI;

        static {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            boolean legacy;
            try {
                lookup.findVirtual(Gui.class, "screen", MethodType.methodType(Screen.class));
                legacy = false;
            } catch (NoSuchMethodException | IllegalAccessException e) {
                legacy = true;
            }
            ACTIVE = legacy;
            if (legacy) {
                try {
                    SET_SCREEN = lookup.findVirtual(Minecraft.class, "setScreen",
                        MethodType.methodType(void.class, Screen.class));
                    SCREEN = lookup.findGetter(Minecraft.class, "screen", Screen.class);
                    HIDE_GUI = lookup.findGetter(Options.class, "hideGui", boolean.class);
                    SET_HIDE_GUI = lookup.findSetter(Options.class, "hideGui", boolean.class);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(
                        "Neither Gui nor Minecraft has the screen calls this jar expects", e);
                }
            } else {
                SET_SCREEN = SCREEN = HIDE_GUI = SET_HIDE_GUI = null;
            }
        }

        private Legacy() {}

        static void open(Screen screen) {
            try {
                SET_SCREEN.invokeExact(Minecraft.getInstance(), screen);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        static Screen current() {
            try {
                return (Screen) SCREEN.invokeExact(Minecraft.getInstance());
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        static boolean hudHidden() {
            try {
                return (boolean) HIDE_GUI.invokeExact(Minecraft.getInstance().options);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        static void setHudHidden(boolean hidden) {
            try {
                SET_HIDE_GUI.invokeExact(Minecraft.getInstance().options, hidden);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        /^* A screen's own exception passes through as it is; only checked ones are wrapped. ^/
        private static RuntimeException rethrow(Throwable t) {
            if (t instanceof RuntimeException r) throw r;
            if (t instanceof Error e) throw e;
            return new IllegalStateException(t);
        }
    }
    *///?}
}
