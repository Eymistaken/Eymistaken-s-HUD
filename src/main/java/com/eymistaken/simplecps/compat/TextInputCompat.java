package com.eymistaken.simplecps.compat;

import net.minecraft.client.Minecraft;

/**
 * Asks the game for typed text on behalf of a screen.
 *
 * <p>Minecraft 26.3 only asks the system for text while something has text focus; with it
 * off, no character ever reaches {@link net.minecraft.client.gui.screens.Screen#charTyped}
 * and a field looks alive — caret and all — while swallowing everything typed into it.
 * Vanilla turns it on from {@code EditBox}'s focus. How that request is made differs between
 * versions, which is why it lives here.
 *
 * <p>Before 26.3, under GLFW, characters arrive whether or not anything asked for them; the
 * request only switches the input method (IME) on for languages that compose text. Those
 * versions also take no owner, so the last screen to stop typing turns it off for everyone,
 * which is how vanilla's own fields behave there. 1.21.x has no such request at all, so
 * there this does nothing.
 */
public final class TextInputCompat {

    private TextInputCompat() {}

    /**
     * Start or stop asking for text for {@code owner}. Passing the owner means a screen can
     * only ever turn off its own request.
     */
    public static void setTyping(Object owner, boolean typing) {
        //? if >=26.1 {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        //?}
        //? if >=26.3 {
        if (typing) {
            client.textInputManager().startTextInput(owner);
        } else {
            client.textInputManager().stopTextInput(owner);
        }
        //?} elif >=26.1 {
        /*if (typing) {
            client.textInputManager().startTextInput();
        } else {
            client.textInputManager().stopTextInput();
        }
        *///?}
    }
}
