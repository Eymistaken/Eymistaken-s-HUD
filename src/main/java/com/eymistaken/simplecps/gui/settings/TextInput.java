package com.eymistaken.simplecps.gui.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/**
 * A single-line text field that draws itself inline, with a caret, a selection
 * and clipboard support.
 *
 * <p>Deliberately not {@link net.minecraft.client.gui.components.EditBox}: almost
 * every field on the settings screen lives inside a scrolling, scissor-clipped
 * list, and a registered widget would render outside our own draw order and keep
 * its own focus state alongside the screen's click routing. This owns nothing but
 * a string, so the caller decides exactly when and where it appears.
 *
 * <p>The caller supplies the frame; {@link #render} only paints text, selection
 * and caret inside the rect it is handed.
 */
public final class TextInput {

    /** Which characters the field accepts, applied to typing and pasting alike. */
    public enum Filter {
        ANY(null),
        /** Optional leading minus then digits. Empty and a lone "-" are valid while typing. */
        INTEGER("-?\\d*"),
        /** Optional leading hash then up to six hex digits. */
        HEX("#?[0-9a-fA-F]{0,6}");

        private final java.util.regex.Pattern pattern;

        Filter(String regex) {
            this.pattern = regex == null ? null : java.util.regex.Pattern.compile(regex);
        }

        boolean accepts(String candidate) {
            return pattern == null || pattern.matcher(candidate).matches();
        }
    }

    private String value = "";
    private int cursor;
    private int anchor;
    private int maxLength = 64;
    private Filter filter = Filter.ANY;

    /** Index of the first visible character; lets long values scroll horizontally. */
    private int displayOffset;

    /** Reset on every edit so the caret is solid right after you type. */
    private long lastEditMillis = System.currentTimeMillis();

    public String getValue() {
        return value;
    }

    /** Replace the contents and put the caret at the end. Does not run the filter. */
    public void setValue(String s) {
        this.value = s == null ? "" : s;
        if (this.value.length() > maxLength) {
            this.value = this.value.substring(0, maxLength);
        }
        moveCursorToEnd();
        displayOffset = 0;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
    }

    public void setFilter(Filter filter) {
        this.filter = filter == null ? Filter.ANY : filter;
    }

    public void selectAll() {
        anchor = 0;
        cursor = value.length();
        touch();
    }

    public void moveCursorToEnd() {
        cursor = value.length();
        anchor = cursor;
        touch();
    }

    public boolean hasSelection() {
        return cursor != anchor;
    }

    private void touch() {
        lastEditMillis = System.currentTimeMillis();
    }

    private int selStart() {
        return Math.min(cursor, anchor);
    }

    private int selEnd() {
        return Math.max(cursor, anchor);
    }

    // --- Editing -----------------------------------------------------------

    /** Insert {@code text} over the selection, rejecting the edit if it fails the filter. */
    public void insert(String text) {
        if (text == null || text.isEmpty()) return;
        // Strip control characters (a pasted newline would otherwise sit in the value).
        StringBuilder clean = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (cp >= 32 && cp != 127) clean.appendCodePoint(cp);
        });
        if (clean.isEmpty()) return;

        int start = selStart();
        int end = selEnd();
        int room = maxLength - (value.length() - (end - start));
        if (room <= 0) return;

        String insertion = clean.length() > room ? clean.substring(0, room) : clean.toString();
        String candidate = value.substring(0, start) + insertion + value.substring(end);
        if (!filter.accepts(candidate)) return;

        value = candidate;
        cursor = start + insertion.length();
        anchor = cursor;
        touch();
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int start = selStart();
        int end = selEnd();
        value = value.substring(0, start) + value.substring(end);
        cursor = start;
        anchor = start;
        touch();
    }

    private void backspace(boolean word) {
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        if (cursor == 0) return;
        int target = word ? wordBoundary(-1) : cursor - 1;
        value = value.substring(0, target) + value.substring(cursor);
        cursor = target;
        anchor = target;
        touch();
    }

    private void delete(boolean word) {
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        if (cursor >= value.length()) return;
        int target = word ? wordBoundary(1) : cursor + 1;
        value = value.substring(0, cursor) + value.substring(target);
        touch();
    }

    /** Nearest word edge in {@code direction} (-1 left, +1 right). */
    private int wordBoundary(int direction) {
        int i = cursor;
        if (direction < 0) {
            while (i > 0 && value.charAt(i - 1) == ' ') i--;
            while (i > 0 && value.charAt(i - 1) != ' ') i--;
        } else {
            int n = value.length();
            while (i < n && value.charAt(i) == ' ') i++;
            while (i < n && value.charAt(i) != ' ') i++;
        }
        return i;
    }

    private void setCursor(int position, boolean keepSelection) {
        cursor = Math.max(0, Math.min(value.length(), position));
        if (!keepSelection) anchor = cursor;
        touch();
    }

    // --- Input -------------------------------------------------------------

    public boolean charTyped(CharacterEvent event) {
        int cp = event.codepoint();
        if (cp < 32 || cp == 127) return false;
        insert(new String(Character.toChars(cp)));
        return true;
    }

    /**
     * @return true if the key was consumed. Enter and Escape are deliberately not
     *         consumed — committing or cancelling the edit is the screen's call.
     */
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() || event.isEscape()) return false;

        if (event.isSelectAll()) {
            selectAll();
            return true;
        }
        if (event.isCopy()) {
            copySelection();
            return true;
        }
        if (event.isPaste()) {
            insert(clipboard());
            return true;
        }
        if (event.isCut()) {
            copySelection();
            deleteSelection();
            return true;
        }

        boolean shift = event.hasShiftDown();
        boolean ctrl = event.hasControlDownWithQuirk();

        if (event.isLeft()) {
            setCursor(ctrl ? wordBoundary(-1) : cursor - 1, shift);
            return true;
        }
        if (event.isRight()) {
            setCursor(ctrl ? wordBoundary(1) : cursor + 1, shift);
            return true;
        }

        return switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> { backspace(ctrl); yield true; }
            case GLFW.GLFW_KEY_DELETE -> { delete(ctrl); yield true; }
            case GLFW.GLFW_KEY_HOME -> { setCursor(0, shift); yield true; }
            case GLFW.GLFW_KEY_END -> { setCursor(value.length(), shift); yield true; }
            default -> false;
        };
    }

    private void copySelection() {
        if (!hasSelection()) return;
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.keyboardHandler.setClipboard(value.substring(selStart(), selEnd()));
        }
    }

    private String clipboard() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? "" : client.keyboardHandler.getClipboard();
    }

    /** Place the caret at the click position. {@code textX} is where the text starts. */
    public void onClick(Font font, double mouseX, int textX, boolean keepSelection) {
        int relative = (int) Math.round(mouseX - textX);
        String visible = value.substring(Math.min(displayOffset, value.length()));
        String fitting = font.plainSubstrByWidth(visible, Math.max(0, relative));
        setCursor(displayOffset + fitting.length(), keepSelection);
    }

    // --- Rendering ---------------------------------------------------------

    /**
     * Draw the text, selection and caret inside {@code (x, y, w, h)}. The caller
     * has already drawn the frame; {@code w} is the usable inner width.
     */
    public void render(GuiGraphicsExtractor ctx, Font font, int x, int y, int w, int h, boolean focused, int color) {
        clampView(font, w);

        String visible = font.plainSubstrByWidth(value.substring(Math.min(displayOffset, value.length())), w);
        int textY = y + (h - font.lineHeight) / 2 + 1;

        if (focused && hasSelection()) {
            int from = Math.max(selStart(), displayOffset);
            int to = Math.min(selEnd(), displayOffset + visible.length());
            if (to > from) {
                int hx = x + SettingsTheme.textWidth(font, value.substring(displayOffset, from));
                int hw = SettingsTheme.textWidth(font, value.substring(from, to));
                SettingsTheme.rect(ctx, hx, textY - 1, hw, font.lineHeight, SettingsTheme.SELECTION_HL);
            }
        }

        SettingsTheme.text(ctx, font, visible, x, textY, color);

        if (focused && (System.currentTimeMillis() - lastEditMillis) / 500 % 2 == 0) {
            int caretIndex = Math.max(displayOffset, Math.min(cursor, displayOffset + visible.length()));
            int caretX = x + SettingsTheme.textWidth(font, value.substring(displayOffset, caretIndex));
            SettingsTheme.rect(ctx, caretX, textY - 1, 1, font.lineHeight, color);
        }
    }

    /** Scroll the view so the caret stays inside {@code innerW}. */
    private void clampView(Font font, int innerW) {
        if (innerW <= 0) {
            displayOffset = 0;
            return;
        }
        displayOffset = Math.max(0, Math.min(displayOffset, value.length()));
        if (displayOffset > cursor) displayOffset = cursor;
        while (displayOffset < cursor
            && SettingsTheme.textWidth(font, value.substring(displayOffset, cursor)) > innerW) {
            displayOffset++;
        }
        // Deleting from the end would otherwise leave the field scrolled past blank space.
        while (displayOffset > 0
            && SettingsTheme.textWidth(font, value.substring(displayOffset - 1)) <= innerW) {
            displayOffset--;
        }
    }
}
