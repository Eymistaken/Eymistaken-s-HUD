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
 */
public final class TextInputCompat {

    private TextInputCompat() {}

    /**
     * Start or stop asking for text for {@code owner}. Passing the owner means a screen can
     * only ever turn off its own request.
     */
    public static void setTyping(Object owner, boolean typing) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        if (typing) {
            client.textInputManager().startTextInput(owner);
        } else {
            client.textInputManager().stopTextInput(owner);
        }
    }
}
