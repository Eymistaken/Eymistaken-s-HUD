package com.eymistaken.simplecps.gui.settings;

import com.eymistaken.simplecps.util.EymHudFonts;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Palette and pixel-art drawing primitives for {@link HudSettingsScreen}.
 *
 * <p>Colours come straight from the design mock, which is a 1600x900 desktop
 * canvas. The screen itself is laid out in Minecraft GUI pixels (roughly a third
 * of that), so the mock's 2px borders and bevels become 1px here — the look is
 * preserved because it was pixel art to begin with.
 *
 * <p>Every helper takes {@code (x, y, w, h)} rather than the two-corner form
 * {@link GuiGraphicsExtractor#fill} wants; boxes in this UI are always described
 * by their size.
 */
public final class SettingsTheme {

    private SettingsTheme() {}

    // --- Palette -----------------------------------------------------------
    //
    // Transparency here is deliberately a *single* layer. Alpha compounds when
    // you stack it — a 0.85 panel under a 0.82 sidebar composites to 0.97, which
    // looks solid however translucent each piece claims to be. So the panel is
    // the one see-through surface, and everything inside it is a low-alpha tint
    // painted over that base rather than another fill of its own.
    //
    // Borders and text stay fully opaque: that is what keeps the pixel-art edges
    // crisp instead of muddy over a bright scene.

    /**
     * How solid the whole panel is over the game. This is the one number to turn
     * if the menu should be airier or more opaque.
     */
    public static final int PANEL_ALPHA = 0xFA;

    private static int surface(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /** Darkening overlay, {@code a} out of 255. */
    private static int shade(int a) {
        return a << 24;
    }

    /** Lightening overlay, {@code a} out of 255. */
    private static int glow(int a) {
        return (a << 24) | 0xFFFFFF;
    }

    public static final int BORDER        = 0xFF000000;

    /**
     * The single translucent layer, covering the whole screen. Everything —
     * including the margin around the panel — sits on this, so no strip of raw
     * game is left glaring at the edges.
     */
    public static final int BACKDROP      = surface(0x0B0B0C, PANEL_ALPHA);

    /** The panel is a lift off the backdrop, not another translucent fill. */
    public static final int PANEL_BG      = glow(0x10);
    public static final int PANEL_LIGHT   = glow(0x40);
    public static final int PANEL_DARK    = shade(0x70);

    public static final int BAR_BG        = glow(0x14);
    public static final int BAR_LIGHT     = glow(0x38);

    public static final int SIDE_BG       = shade(0x2E);
    public static final int SIDE_HEAD_BG  = glow(0x10);

    public static final int CONTENT_HEAD  = glow(0x10);
    public static final int ROW_SEPARATOR = shade(0x50);

    /** Recessed field (search box, sliders, inputs). */
    public static final int FIELD_BG      = shade(0x66);
    public static final int FIELD_RIM     = glow(0x1C);

    /** Raised button. */
    public static final int BTN_BG        = glow(0x24);
    public static final int BTN_HOVER     = glow(0x3A);
    public static final int BTN_LIGHT     = glow(0x30);
    public static final int BTN_DARK      = shade(0x50);

    public static final int BTN_PRIMARY   = glow(0x36);
    public static final int BTN_ACCENT    = glow(0x2E);
    public static final int BTN_ACCENT_HI = glow(0x44);
    public static final int BTN_ACCENT_L  = glow(0x38);
    public static final int BTN_ACCENT_D  = shade(0x54);

    public static final int TEXT          = 0xFFE2E2E6;
    public static final int TEXT_STRONG   = 0xFFFFFFFF;
    public static final int TEXT_LABEL    = 0xFFDCDCE1;
    public static final int TEXT_MUTED    = 0xFF8C8C95;
    public static final int TEXT_DIM      = 0xFF7E7E88;
    public static final int TEXT_FAINT    = 0xFF6D6D76;
    public static final int TEXT_GHOST    = 0xFF55555E;
    public static final int TEXT_OFF      = 0xFF5F5F68;
    public static final int TEXT_PLACEHOLD= 0xFF4E4E57;

    public static final int SEL_BG        = glow(0x28);
    public static final int SEL_BORDER    = 0xFF4C4C56;
    public static final int IDLE_BG       = shade(0x28);

    public static final int DOT_ON        = 0xFFD2D2D8;
    public static final int DOT_OFF       = 0xFF2F2F36;
    public static final int DOT_GLOBAL    = 0xFF7E7E88;
    public static final int FLASH_ON_DOT  = 0xFFA9E2A9;
    public static final int FLASH_ON_BG   = 0x4C7EC47E;
    public static final int FLASH_OFF_BG  = 0x24787882;
    public static final int FLASH_ON_TEXT = 0xFFD6F0D6;

    public static final int NAME_ON       = 0xFFC8C8CE;
    public static final int NAME_OFF      = 0xFF5F5F68;

    public static final int DIVIDER       = 0xFF232329;
    public static final int TAG_BORDER    = 0xFF2B2B32;
    public static final int TAG_TEXT      = 0xFF82828C;

    public static final int TAB_ACTIVE_BG = glow(0x22);
    public static final int TAB_ACTIVE_HI = glow(0x36);
    public static final int TAB_IDLE_BG   = shade(0x34);
    public static final int TAB_IDLE_HI   = glow(0x12);

    public static final int KNOB_ON       = 0xFFD2D2D8;
    public static final int KNOB_OFF      = 0xFF4A4A53;
    public static final int KNOB_LABEL_ON = 0xFF9A9AA2;

    public static final int SLIDER_FILL   = 0xFF4A4A54;
    public static final int SLIDER_KNOB   = 0xFFD2D2D8;
    public static final int SLIDER_KNOB_D = 0xFF7E7E88;

    public static final int CARET         = 0xFF9A9AA2;
    public static final int COLOR_PANEL   = shade(0x60);

    public static final int POS_PANEL     = shade(0x66);
    public static final int POS_CELL_BG   = shade(0x3C);
    public static final int POS_CELL_BRD  = 0xFF2A2A31;
    public static final int POS_CELL_MARK = 0xFF4A4A53;
    public static final int POS_SEL_BG    = glow(0x2E);
    public static final int POS_SEL_BRD   = 0xFFD2D2D8;
    public static final int POS_DEAD_BG   = shade(0x50);
    public static final int POS_DEAD_BRD  = 0xFF1A1A1E;

    public static final int RESET_FG_ON   = 0xFFDCDCE1;
    public static final int RESET_FG_OFF  = 0xFF3D3D45;

    public static final int CARD_BG       = glow(0x0E);
    public static final int CARD_LIGHT    = glow(0x22);

    /**
     * Checkerboard behind the module preview. Fully opaque and deliberately far apart:
     * HUD modules are drawn over gameplay, so a preview on one flat tone would flatter
     * light text and hide a low background opacity completely. The two tones show
     * transparency and both contrast cases at the same time.
     */
    public static final int CHECKER_DARK  = 0xFF1B1B20;
    public static final int CHECKER_LIGHT = 0xFF4E4E58;

    /**
     * Ground for the preview's focus overlay. Fully opaque, unlike {@link #PANEL_BG} and
     * {@link #CARD_BG}, which are thin white glazes meant to sit <em>on</em> the panel —
     * painting a floating overlay with those leaves the settings rows showing through it.
     */
    public static final int FOCUS_BG      = surface(0x1B1B1F, 0xFF);

    public static final int OK_TEXT       = 0xFFA9E2A9;

    public static final int DANGER_BG     = 0xE6401E24;
    public static final int DANGER_HOVER  = 0xF255262E;
    public static final int DANGER_LIGHT  = 0xE64A2C30;
    public static final int DANGER_DARK   = 0xE6140D0E;
    public static final int DANGER_TEXT   = 0xFFD9A1A1;

    public static final int CONFIRM_BG    = 0xFF6B2F34;
    public static final int CONFIRM_HOVER = 0xFF7D383E;
    public static final int CONFIRM_LIGHT = 0xFF97484F;
    public static final int CONFIRM_DARK  = 0xFF3A181B;
    public static final int CONFIRM_TEXT  = 0xFFFFE6E6;

    // The modal is a decision point, so it stays essentially solid.
    public static final int MODAL_BG      = surface(0x1B1B1F, 0xF6);
    public static final int MODAL_SCRIM   = 0xB8000000;
    public static final int MODAL_HEAD_FG = 0xFFE0B3B3;
    public static final int MODAL_BODY_FG = 0xFFB6B6BE;

    public static final int DONE_BG       = 0xFFD2D2D8;
    public static final int DONE_HOVER    = 0xFFE6E6EA;
    public static final int DONE_LIGHT    = 0xFFFFFFFF;
    public static final int DONE_DARK     = 0xFF82828B;
    public static final int DONE_TEXT     = 0xFF111114;

    public static final int SCROLL_TRACK  = shade(0x4C);
    public static final int SCROLL_THUMB  = glow(0x3C);

    public static final int SELECTION_HL  = 0x804444FF;

    /** The ten quick-pick colours offered by the inline colour editor. */
    public static final int[] SWATCHES = {
        0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000, 0xFF5555,
        0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF
    };

    // --- Text --------------------------------------------------------------

    /**
     * Wrap text for drawing. Normally this honours the user's HUD font, but the
     * ENCHANT font is deliberately unreadable and this screen is where you would
     * go to turn it off again — so fall back to vanilla in that one case.
     */
    public static Component label(String s) {
        return EymHudFonts.isEnchantActive() ? Component.literal(s) : EymHudFonts.text(s);
    }

    public static int textWidth(Font font, String s) {
        return font.width(label(s));
    }

    /** Draw shadowless text; the mock has no drop shadows anywhere. */
    public static void text(GuiGraphicsExtractor ctx, Font font, String s, int x, int y, int color) {
        ctx.text(font, label(s), x, y, color, false);
    }

    public static void centeredText(GuiGraphicsExtractor ctx, Font font, String s, int cx, int y, int color) {
        text(ctx, font, s, cx - textWidth(font, s) / 2, y, color);
    }

    public static void rightText(GuiGraphicsExtractor ctx, Font font, String s, int right, int y, int color) {
        text(ctx, font, s, right - textWidth(font, s), y, color);
    }

    /** Uppercase for the mock's all-caps chrome. Locale.ROOT: a Turkish locale turns "i" into "İ". */
    public static String up(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    /** Shorten {@code s} with a trailing ellipsis until it fits {@code maxWidth}. */
    public static String truncate(Font font, String s, int maxWidth) {
        if (s == null || s.isEmpty() || textWidth(font, s) <= maxWidth) return s == null ? "" : s;
        String ellipsis = "..";
        int room = maxWidth - textWidth(font, ellipsis);
        if (room <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (textWidth(font, sb.toString() + s.charAt(i)) > room) break;
            sb.append(s.charAt(i));
        }
        return sb + ellipsis;
    }

    /**
     * Split {@code s} into lines that each fit {@code maxWidth}, breaking on spaces.
     * The mock's INFO panel and modal body both wrap; MC's own word-wrap draws in one
     * call and we need the line count to lay out what follows.
     */
    public static java.util.List<String> wrap(Font font, String s, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (s == null || s.isEmpty() || maxWidth <= 0) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            if (word.isEmpty()) continue;
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(font, candidate) <= maxWidth) {
                line = new StringBuilder(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder();
            }
            // A single word wider than the line still has to go somewhere.
            if (textWidth(font, word) <= maxWidth) {
                line.append(word);
            } else {
                lines.add(truncate(font, word, maxWidth));
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    // --- Boxes -------------------------------------------------------------

    /** {@link GuiGraphicsExtractor#fill} in (x, y, w, h) form. */
    public static void rect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + h, color);
    }

    /** 1px border drawn just inside the rect. */
    public static void frame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        rect(ctx, x, y, w, 1, color);
        rect(ctx, x, y + h - 1, w, 1, color);
        rect(ctx, x, y, 1, h, color);
        rect(ctx, x + w - 1, y, 1, h, color);
    }

    /**
     * Raised control: black edge, then a light rim on the top/left and a dark one
     * on the bottom/right. The mock's {@code inset 2px 2px 0 light, -2px -2px 0 dark}.
     */
    public static void raised(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int bg, int light, int dark) {
        if (w <= 0 || h <= 0) return;
        rect(ctx, x, y, w, h, bg);
        frame(ctx, x, y, w, h, BORDER);
        if (w <= 2 || h <= 2) return;
        rect(ctx, x + 1, y + 1, w - 2, 1, light);
        rect(ctx, x + 1, y + 1, 1, h - 2, light);
        rect(ctx, x + 1, y + h - 2, w - 2, 1, dark);
        rect(ctx, x + w - 2, y + 1, 1, h - 2, dark);
    }

    public static void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean hovered) {
        raised(ctx, x, y, w, h, hovered ? BTN_HOVER : BTN_BG, BTN_LIGHT, BTN_DARK);
    }

    /**
     * Recessed field: black edge with a single light rim on the top/left, so it
     * reads as a well rather than a button. The mock's {@code inset 2px 2px 0 rim}.
     */
    public static void sunken(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int bg, int rim) {
        if (w <= 0 || h <= 0) return;
        rect(ctx, x, y, w, h, bg);
        frame(ctx, x, y, w, h, BORDER);
        if (w <= 2 || h <= 2) return;
        rect(ctx, x + 1, y + 1, w - 2, 1, rim);
        rect(ctx, x + 1, y + 1, 1, h - 2, rim);
    }

    public static void field(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        sunken(ctx, x, y, w, h, FIELD_BG, FIELD_RIM);
    }

    /** Bordered card with a top-left highlight, used by the right-hand panels. */
    public static void card(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        sunken(ctx, x, y, w, h, CARD_BG, CARD_LIGHT);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Blend two ARGB colours, {@code t} in {@code [0,1]} moving from a to b. */
    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            out |= (ca + Math.round((cb - ca) * t)) << shift;
        }
        return out;
    }

    // --- Icons -------------------------------------------------------------

    /**
     * 7x7 "revert" arrow. Drawn from a bitmap rather than a glyph because U+21BA
     * is not in the default font's own pages — it would depend on the unicode
     * fallback being present, and a missing-glyph box in every row would be ugly.
     */
    private static final String[] RESET_GLYPH = {
        " ##### ",
        "##   ##",
        "#     #",
        "#      ",
        "#      ",
        "##   ##",
        " ##### ",
    };

    /** Arrowhead closing the gap on the right of {@link #RESET_GLYPH}. */
    private static final String[] RESET_ARROW = {
        "     # ",
        "     ##",
        "  #####",
        "     ##",
        "     # ",
    };

    public static void resetIcon(GuiGraphicsExtractor ctx, int x, int y, int color) {
        drawGlyph(ctx, RESET_GLYPH, x, y, color);
        drawGlyph(ctx, RESET_ARROW, x, y + 1, color);
    }

    /** 5x5 magnifier for the search field. */
    private static final String[] SEARCH_GLYPH = {
        " ### ",
        "#   #",
        "#   #",
        " ### ",
        "    #",
    };

    public static void searchIcon(GuiGraphicsExtractor ctx, int x, int y, int color) {
        drawGlyph(ctx, SEARCH_GLYPH, x, y, color);
    }

    /** 5x5 X for the close button. */
    private static final String[] CLOSE_GLYPH = {
        "#   #",
        " # # ",
        "  #  ",
        " # # ",
        "#   #",
    };

    public static void closeIcon(GuiGraphicsExtractor ctx, int x, int y, int color) {
        drawGlyph(ctx, CLOSE_GLYPH, x, y, color);
    }

    /** 7x7 trash can, matching the one the HUD editor draws for preset rows. */
    private static final String[] TRASH_GLYPH = {
        " ##### ",
        "#######",
        " ##### ",
        " # # # ",
        " # # # ",
        " # # # ",
        " ##### ",
    };

    public static void trashIcon(GuiGraphicsExtractor ctx, int x, int y, int color) {
        drawGlyph(ctx, TRASH_GLYPH, x, y, color);
    }

    /**
     * 7x7 corner brackets for the preview's FOCUS button. The pair reads as one box
     * growing and shrinking: the corners sit on the outside to expand, and pull a pixel
     * in to collapse.
     */
    private static final String[] EXPAND_GLYPH = {
        "##   ##",
        "#     #",
        "       ",
        "       ",
        "       ",
        "#     #",
        "##   ##",
    };

    private static final String[] COLLAPSE_GLYPH = {
        "       ",
        " ## ## ",
        " #   # ",
        "       ",
        " #   # ",
        " ## ## ",
        "       ",
    };

    public static void expandIcon(GuiGraphicsExtractor ctx, int x, int y, int color) {
        drawGlyph(ctx, EXPAND_GLYPH, x, y, color);
    }

    public static void collapseIcon(GuiGraphicsExtractor ctx, int x, int y, int color) {
        drawGlyph(ctx, COLLAPSE_GLYPH, x, y, color);
    }

    private static void drawGlyph(GuiGraphicsExtractor ctx, String[] glyph, int x, int y, int color) {
        for (int row = 0; row < glyph.length; row++) {
            String line = glyph[row];
            int runStart = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean on = col < line.length() && line.charAt(col) == '#';
                if (on && runStart < 0) {
                    runStart = col;
                } else if (!on && runStart >= 0) {
                    // Coalesce horizontal runs into one fill instead of one per pixel.
                    rect(ctx, x + runStart, y + row, col - runStart, 1, color);
                    runStart = -1;
                }
            }
        }
    }
}
