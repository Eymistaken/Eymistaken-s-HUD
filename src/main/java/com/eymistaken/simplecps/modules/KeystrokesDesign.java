package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * How a keystroke button's body looks — its resting shape, before any press
 * animation paints over it.
 *
 * <p>A design is deliberately only the <em>stance</em>: the box, its border, its
 * corners. What happens when the key goes down lives in {@link KeystrokesAnim},
 * and the two are independent — every design works with every animation. Each
 * design does carry a {@link #defaultMotion()}/{@link #defaultFill()} pair, but
 * that is a starting preset the player is free to change, not a fixed pairing.
 *
 * <p>Everything is drawn with axis-aligned {@code fill()} rectangles, in the
 * button's own coordinate space. Rounded corners and hexagons are filled row by
 * row, the same technique the ripple effect already used.
 */
public enum KeystrokesDesign {

    /** Today's look: flat translucent fill, sharp corners. */
    BASELINE("Baseline"),
    /** Empty body, 2px border. The most readable in a fight — it hides nothing. */
    FRAME("Frame"),
    /** No box at all: just the label and a thin rail under it. */
    BAR("Bar"),
    /** 3px rounded corners with a 2px light line along the top. */
    SOFT_BLOCK("Soft Block"),
    /** The body is built from horizontal slices, like an equalizer. */
    SEGMENT("Segment"),
    /** Near-black core; the halo is painted by the animation, not by the body. */
    NEON("Neon"),
    /** A wedge pointing away from center. The direction comes from the keybind. */
    WEDGE("Wedge"),
    /** A whole-module mode: press history scrolls right to left. Global only. */
    TIMELINE("Timeline"),
    /** Hexagonal keycaps that tile into a honeycomb. */
    HEX("Hex"),
    /** Double frame with a gold pixel at each corner. 3px is one "pixel". */
    PIXEL_BEVEL("Pixel Bevel"),
    /** A recess with no frame of its own — meant to sit inside the board. */
    SLOT("Slot");

    private final String display;

    KeystrokesDesign(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /**
     * Designs that reinterpret the whole module instead of styling one button.
     * These cannot be used as a per-key override: a single key cannot be a
     * timeline while its neighbor stays a box.
     */
    public boolean isModuleWide() {
        return this == TIMELINE;
    }

    /** The preset this design ships with, per the source document. */
    public KeystrokesAnim.Motion defaultMotion() {
        return switch (this) {
            case BASELINE, FRAME, HEX -> KeystrokesAnim.Motion.SQUISH;
            case SOFT_BLOCK, PIXEL_BEVEL, SLOT -> KeystrokesAnim.Motion.SINK;
            case WEDGE -> KeystrokesAnim.Motion.NUDGE;
            case BAR, SEGMENT, NEON, TIMELINE -> KeystrokesAnim.Motion.NONE;
        };
    }

    /** The preset this design ships with, per the source document. */
    public KeystrokesAnim.Fill defaultFill() {
        return switch (this) {
            case BASELINE -> KeystrokesAnim.Fill.RIPPLE;
            case FRAME, BAR, HEX -> KeystrokesAnim.Fill.SWEEP;
            case SEGMENT -> KeystrokesAnim.Fill.CASCADE;
            case NEON -> KeystrokesAnim.Fill.GLOW;
            case SOFT_BLOCK, WEDGE, PIXEL_BEVEL, SLOT, TIMELINE -> KeystrokesAnim.Fill.NONE;
        };
    }

    // ---------------------------------------------------------- arrangement

    /** A key's part in an arrangement, worked out from its binding. */
    public static final int ROLE_OTHER = -1;
    public static final int ROLE_W = 0, ROLE_A = 1, ROLE_S = 2, ROLE_D = 3;
    public static final int ROLE_LMB = 4, ROLE_RMB = 5;
    public static final int ROLE_SPACE = 6, ROLE_CTRL = 7, ROLE_SHIFT = 8;

    /** One position in an arrangement. */
    public record Slot(int role, int x, int y, int w, int h) {}

    public static int roleOf(SimpleCPSConfig.KeyButtonData btn) {
        if (btn.isMouse) {
            if (btn.keyCode == 0) return ROLE_LMB;
            if (btn.keyCode == 1) return ROLE_RMB;
            return ROLE_OTHER;
        }
        return switch (btn.keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_W, org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> ROLE_W;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> ROLE_A;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_S, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> ROLE_S;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_D, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> ROLE_D;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE -> ROLE_SPACE;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,
                 org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL -> ROLE_CTRL;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT,
                 org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT -> ROLE_SHIFT;
            default -> ROLE_OTHER;
        };
    }

    /**
     * Where this design wants its keys, or {@code null} to leave the layout alone.
     *
     * <p>Some of these designs <em>are</em> an arrangement, not just a body shape. A
     * compass whose four directions sit in a straight row is not a compass, and
     * hexagons that do not interlock are not a honeycomb — drawing the right shape in
     * the wrong places gives something that is neither the design nor the old layout.
     *
     * <p>The sizes are the source document's own, halved from its 3x mock into game
     * pixels. Larger than the stock 21x21 keys on purpose: these shapes are mostly
     * diagonal, and a diagonal needs room before it reads as a line rather than as a
     * staircase.
     */
    public java.util.List<Slot> arrangement() {
        return switch (this) {
            case WEDGE -> java.util.List.of(
                new Slot(ROLE_W, 38, 3, 44, 29),
                new Slot(ROLE_A, 3, 38, 29, 44),
                new Slot(ROLE_D, 88, 38, 29, 44),
                new Slot(ROLE_S, 38, 88, 44, 29),
                // The mouse buttons fill the middle the four wedges open around.
                // Wide enough for a three-letter label: the mock writes "L" and "R",
                // but the mod's own keys say LMB and RMB and a design must not
                // silently rename somebody's keys to make its geometry work.
                // 34..86 holds both with a 2px gap on either side and between them.
                // At 26 wide the right one ended flush against the D wedge.
                new Slot(ROLE_LMB, 34, 43, 25, 34),
                new Slot(ROLE_RMB, 61, 43, 25, 34),
                new Slot(ROLE_CTRL, 0, 123, 31, 11),
                new Slot(ROLE_SPACE, 33, 123, 54, 11),
                new Slot(ROLE_SHIFT, 89, 123, 31, 11));

            // Interlocking comb: alternate rows are offset by half a cell, which is
            // the only way hexagons tile without gaps between them.
            case HEX -> java.util.List.of(
                new Slot(ROLE_W, 31, 0, 39, 34),
                new Slot(ROLE_S, 31, 36, 39, 34),
                new Slot(ROLE_A, 0, 53, 39, 34),
                new Slot(ROLE_D, 61, 53, 39, 34),
                new Slot(ROLE_SPACE, 31, 71, 39, 34),
                new Slot(ROLE_LMB, 0, 89, 39, 34),
                new Slot(ROLE_RMB, 61, 89, 39, 34),
                new Slot(ROLE_CTRL, 0, 124, 39, 34),
                new Slot(ROLE_SHIFT, 61, 124, 39, 34));

            default -> null;
        };
    }

    /**
     * The stock arrangement, restored when leaving a design that imposed its own.
     *
     * <p>Without this, switching from the honeycomb to a plain design left the keys
     * sitting in the comb with only their texture changed — the layout would be stuck
     * in whichever arranging design was picked last.
     */
    private static final java.util.List<Slot> CLASSIC = java.util.List.of(
        new Slot(ROLE_W, 23, 0, 21, 21),
        new Slot(ROLE_A, 0, 23, 21, 21),
        new Slot(ROLE_S, 23, 23, 21, 21),
        new Slot(ROLE_D, 46, 23, 21, 21),
        new Slot(ROLE_LMB, 0, 46, 33, 21),
        new Slot(ROLE_RMB, 34, 46, 33, 21),
        new Slot(ROLE_SPACE, 0, 69, 67, 13),
        new Slot(ROLE_CTRL, 0, 84, 33, 13),
        new Slot(ROLE_SHIFT, 34, 84, 33, 13));

    /**
     * Adopt this design: its arrangement, animation and palette in one step.
     *
     * <p>Shared rather than living in the designer, because the HUD editor and the
     * settings screen can change the design too — and when only the designer applied
     * the preset, picking a design anywhere else changed the texture and left the
     * layout, colors and animation belonging to the previous one.
     *
     * <p>The layout is only rearranged when it needs to be: a design with its own
     * arrangement imposes it, and leaving such a design puts the keys back into the
     * stock grid. Switching between two designs that carry no arrangement leaves the
     * player's own layout untouched, which is the whole point of having one.
     */
    public void applyTo(SimpleCPSConfig config) {
        KeystrokesDesign previous = config.keystrokesDesign;
        config.keystrokesDesign = this;
        config.keystrokesMotions = KeystrokesAnim.cleanMotions(
            java.util.List.of(defaultMotion()));
        config.keystrokesFills = KeystrokesAnim.cleanFills(
            java.util.List.of(defaultFill()));
        config.keystrokesFillDirection =
            KeystrokesAnim.coerceAll(config.keystrokesFills, defaultDirection());

        int[] palette = defaultColors();
        config.keystrokesColor = palette[0];
        config.keystrokesPressedColor = palette[1];
        config.keystrokesBackgroundColor = palette[2];
        config.keystrokesBackgroundOpacity = palette[3];

        // Per-key color overrides would sit on top of the new palette and break the
        // look the design was drawn for, so they go back to following the module.
        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
            btn.btnColor = -1;
            btn.btnPressedColor = -1;
        }

        java.util.List<Slot> slots = arrangement();
        if (slots != null) {
            place(config.keystrokesLayout, slots);
        } else if (previous != null && previous.arrangement() != null) {
            place(config.keystrokesLayout, CLASSIC);
        }
    }

    /**
     * Move the keys into this design's arrangement, in place.
     *
     * <p>Only positions and sizes change: keybinds, labels, styling and per-key
     * settings stay with the key they belong to. Anything the arrangement has no slot
     * for — an extra key somebody added — is stacked underneath rather than dropped,
     * because losing a key the player made is worse than an untidy row.
     */
    public void arrange(java.util.List<SimpleCPSConfig.KeyButtonData> keys) {
        place(keys, arrangement());
    }

    private static void place(java.util.List<SimpleCPSConfig.KeyButtonData> keys,
                              java.util.List<Slot> slots) {
        if (slots == null || keys == null || keys.isEmpty()) return;

        java.util.Set<SimpleCPSConfig.KeyButtonData> placed =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        int bottom = 0;
        for (Slot slot : slots) {
            for (SimpleCPSConfig.KeyButtonData key : keys) {
                if (placed.contains(key) || roleOf(key) != slot.role()) continue;
                key.x = slot.x();
                key.y = slot.y();
                key.w = slot.w();
                key.h = slot.h();
                placed.add(key);
                bottom = Math.max(bottom, slot.y() + slot.h());
                break;
            }
        }

        int x = 0;
        int y = bottom + 4;
        for (SimpleCPSConfig.KeyButtonData key : keys) {
            if (placed.contains(key)) continue;
            key.w = Math.max(key.w, 21);
            key.h = Math.max(key.h, 13);
            key.x = x;
            key.y = y;
            x += key.w + 2;
        }
    }

    /**
     * The palette this design was drawn with, as
     * {@code {textColor, pressedColor, backgroundColor, backgroundOpacity}}.
     *
     * <p>These are not decoration: a design's legibility depends on them. The pixel
     * styles need their slate-and-gold to read as pixel art, the neon core has to
     * stay nearly black for the halo to show, and the bar style needs a dimmer label
     * because nothing sits behind it. Selecting a design applies them, and the
     * designer's undo covers it.
     */
    public int[] defaultColors() {
        return switch (this) {
            // The mod's original look, kept exactly.
            case BASELINE -> new int[] { 0xFFFFFF, 0x00FF00, 0x000000, 128 };
            // No body at all, so the border and label carry it; opacity 0 keeps the
            // scene visible through the key, which is this design's whole point.
            case FRAME -> new int[] { 0xF2F6FA, 0x6EE7A8, 0x000000, 0 };
            // Dimmer label: it sits directly on the game with no plate behind it.
            case BAR -> new int[] { 0xB8BFC7, 0x6EE7A8, 0x000000, 0 };
            case SOFT_BLOCK -> new int[] { 0xE9EEF4, 0x6EE7A8, 0x0E1116, 189 };
            // The slices are drawn by the body, so the background stays out of it.
            case SEGMENT -> new int[] { 0xE6E6E6, 0x6EE7A8, 0x000000, 0 };
            // Near-black core: the halo only reads against something this dark.
            case NEON -> new int[] { 0xD1D1D1, 0x6EE7A8, 0x080A0D, 158 };
            case WEDGE -> new int[] { 0xF2F6FA, 0x6EE7A8, 0x000000, 128 };
            case TIMELINE -> new int[] { 0x999999, 0x6EE7A8, 0x000000, 20 };
            case HEX -> new int[] { 0xF2F6FA, 0x6EE7A8, 0x000000, 128 };
            // Slate and gold, opaque: a pixel-art frame cannot be translucent and
            // still look carved.
            case PIXEL_BEVEL -> new int[] { 0xE6ECF5, 0xC9A24A, 0x1B2030, 255 };
            case SLOT -> new int[] { 0xF2E6CF, 0xC9A24A, 0x0D1116, 255 };
        };
    }

    /** The fill direction this design ships with. */
    public KeystrokesAnim.Direction defaultDirection() {
        return switch (this) {
            case FRAME, SEGMENT -> KeystrokesAnim.Direction.UP;
            case BAR -> KeystrokesAnim.Direction.RIGHT;
            case HEX, BASELINE, NEON -> KeystrokesAnim.Direction.CENTER_OUT;
            default -> KeystrokesAnim.Direction.RIGHT;
        };
    }

    /**
     * Whether the accent color paints the body rather than the label.
     *
     * <p>The old model only ever recolored the text. Most of these designs flood
     * the body instead and flip the label to a readable tone against it, which is
     * why {@link #labelOn(int)} exists rather than a second config field.
     */
    public boolean accentPaintsBody() {
        return switch (this) {
            case BASELINE, NEON, BAR -> false;
            default -> true;
        };
    }

    /** Perceived brightness, 0-255. Used to pick a label tone against a flooded body. */
    private static int luminance(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    /**
     * The label color to use while the key is held. When the body is flooded the
     * label has to leave the accent and go dark (or light, against a dark accent)
     * or it disappears into its own background.
     */
    public int labelOn(int accent) {
        if (!accentPaintsBody()) return accent;
        return luminance(accent) > 140 ? 0xFF0D1114 : 0xFFF2F6FA;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** Fill a rect, guarding against the zero-size rects the row loops can produce. */
    private static void box(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + h, color);
    }

    /** A hollow rectangle of the given thickness, drawn as four fills. */
    private static void frame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int t, int color) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        box(ctx, x, y, w, t, color);
        box(ctx, x, y + h - t, w, t, color);
        box(ctx, x, y + t, t, h - t * 2, color);
        box(ctx, x + w - t, y + t, t, h - t * 2, color);
    }

    /**
     * Rounded rect, filled row by row. The corner radius is small enough that a
     * per-row inset reads as a curve without any anti-aliasing.
     */
    private static float roundInset(int row, int h, int r) {
        if (r <= 0) return 0f;
        float center = row + 0.5f;
        float dy = center < r ? r - center : (center > h - r ? center - (h - r) : 0f);
        if (dy <= 0f) return 0f;
        return r - (float) Math.sqrt(Math.max(0.0, (double) r * r - (double) dy * dy));
    }

    private static void rounded(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            float inset = roundInset(row, h, r);
            rowAA(ctx, x + inset, x + w - inset, y + row, color);
        }
    }

    /**
     * Horizontal inset of a hexagon at the given row, as an exact fraction of its
     * width. Kept fractional on purpose: rounding it per row is what turns a slope
     * into a staircase, and {@link #rowAA} needs the remainder to soften the edge.
     */
    private static float hexInset(int row, int h, int w) {
        // Matches the source document's polygon: flat top and bottom spanning the
        // middle half, with the points reaching full width at mid-height.
        float t = h <= 1 ? 0f : (row + 0.5f) / h;
        float d = Math.abs(t - 0.5f) * 2f;   // 1 at the ends, 0 at the waist
        return w * 0.25f * d;
    }

    /** Scale a color's alpha by {@code coverage}. */
    private static int fade(int color, float coverage) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, coverage)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Draw one row of a shape between two fractional edges, fading the partly
     * covered pixel at each end.
     *
     * <p>This is the whole anti-aliasing story. Everything is still an axis-aligned
     * {@code fill()}; the only difference is that the pixel a slope passes through
     * gets the alpha its coverage deserves instead of being all-or-nothing. Without
     * it a 21-pixel triangle shows 21 visible stair steps.
     */
    private static void rowAA(GuiGraphicsExtractor ctx, float x0, float x1, int y, int color) {
        if (x1 <= x0) return;
        int solidStart = (int) Math.ceil(x0);
        int solidEnd = (int) Math.floor(x1);

        if (solidEnd <= solidStart) {
            // Thinner than a pixel: one sliver carrying the whole coverage.
            box(ctx, (int) Math.floor(x0), y, 1, 1, fade(color, x1 - x0));
            return;
        }
        float left = solidStart - x0;
        if (left > 0.02f) box(ctx, solidStart - 1, y, 1, 1, fade(color, left));
        box(ctx, solidStart, y, solidEnd - solidStart, 1, color);
        float right = x1 - solidEnd;
        if (right > 0.02f) box(ctx, solidEnd, y, 1, 1, fade(color, right));
    }

    private static void hexagon(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        for (int row = 0; row < h; row++) {
            float inset = hexInset(row, h, w);
            rowAA(ctx, x + inset, x + w - inset, y + row, color);
        }
    }

    /**
     * Which way a wedge points. Taken from the keybind rather than a new field, so
     * a WASD cluster becomes a compass with no extra configuration; anything that
     * is not a direction key stays a plain box.
     */
    private static int wedgeDir(SimpleCPSConfig.KeyButtonData btn) {
        if (btn.isMouse) return -1;
        return switch (btn.keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_W, org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> 0;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_D, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> 1;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_S, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> 2;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> 3;
            default -> -1;
        };
    }

    /**
     * The horizontal span a wedge covers on one row, as {@code {start, endExclusive}}
     * relative to the key's own left edge.
     *
     * <p>Both the drawing and {@link #rowSpan} go through this. They used to derive
     * the shape separately — the body column by column, the span row by row — and the
     * two disagreed by a few pixels along the slopes, which left a fill with a ragged
     * edge that did not follow the triangle it was supposed to be inside.
     */
    private static float[] wedgeSpan(int w, int h, int dir, int row) {
        // Sampled at the row's center so the slope is measured where the pixel is,
        // not at its top edge.
        float t = h <= 1 ? 1f : (row + 0.5f) / h;
        if (dir == 0 || dir == 2) {
            float f = dir == 2 ? 1f - t : t;
            float width = Math.max(1f, w * f);
            float off = (w - width) / 2f;
            return new float[] { off, off + width };
        }
        // Widest at the middle row, narrowing to a point: a vertical edge on one side
        // and the apex on the other.
        float d = Math.abs(t - 0.5f) * 2f;
        float reach = Math.max(1f, w * (1f - d));
        return dir == 1 ? new float[] { 0f, reach } : new float[] { w - reach, w };
    }

    /** A triangle pointing in {@code dir} (0=up 1=right 2=down 3=left), row by row. */
    private static void wedge(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int dir, int color) {
        for (int row = 0; row < h; row++) {
            float[] span = wedgeSpan(w, h, dir, row);
            rowAA(ctx, x + span[0], x + span[1], y + row, color);
        }
    }

    /** The rail a {@link #BAR} key draws, as {@code {x, y, w, h}}. */
    public static int[] railRect(SimpleCPSConfig.KeyButtonData btn) {
        int railH = Math.max(1, btn.h / 12 + 1);
        int inset = Math.max(1, btn.w / 8);
        return new int[] { btn.x + inset, btn.y + btn.h - railH - 1, btn.w - inset * 2, railH };
    }

    /**
     * The horizontal span this design's shape covers on one row, as absolute
     * {@code {startX, endXExclusive}}, or {@code null} where the row is empty.
     *
     * <p>Animation fills are painted row by row and clipped to this, which is what
     * keeps a sweep inside a hexagon or on a bar's rail. Asking per row rather than
     * per pixel is deliberate: a per-pixel test would need a {@code fill()} call per
     * pixel, and a single 21x21 key would cost more draw calls than the whole HUD.
     */
    public int[] rowSpan(SimpleCPSConfig.KeyButtonData btn, int row) {
        if (row < 0 || row >= btn.h) return null;
        switch (this) {
            case TIMELINE:
                return null;

            case BAR: {
                int[] rail = railRect(btn);
                int top = rail[1] - btn.y;
                if (row < top || row >= top + rail[3]) return null;
                return new int[] { rail[0], rail[0] + rail[2] };
            }

            case HEX: {
                int inset = Math.round(hexInset(row, btn.h, btn.w));
                return new int[] { btn.x + inset, btn.x + btn.w - inset };
            }

            case SOFT_BLOCK: {
                int r = Math.max(0, Math.min(3, Math.min(btn.w, btn.h) / 2));
                int inset = Math.round(roundInset(row, btn.h, r));
                return new int[] { btn.x + inset, btn.x + btn.w - inset };
            }

            case WEDGE: {
                int dir = wedgeDir(btn);
                if (dir < 0) break;
                float[] span = wedgeSpan(btn.w, btn.h, dir, row);
                return new int[] { btn.x + Math.round(span[0]), btn.x + Math.round(span[1]) };
            }

            default:
                break;
        }
        return new int[] { btn.x, btn.x + btn.w };
    }

    /**
     * Draw the resting body. {@code bg} is the module's configured background
     * color and {@code accent} the pressed color; a design uses whichever the
     * source document called for.
     */
    public void drawBody(GuiGraphicsExtractor ctx, SimpleCPSConfig.KeyButtonData btn,
                         int bg, int accent, boolean pressed) {
        int x = btn.x, y = btn.y, w = btn.w, h = btn.h;
        int body = pressed && accentPaintsBody() ? accent : bg;

        switch (this) {
            case BASELINE -> box(ctx, x, y, w, h, body);

            case FRAME -> frame(ctx, x, y, w, h, 2, pressed ? accent : withAlpha(0xFFFFFF, 0x99));

            case BAR -> {
                // No box at all: a rail under the label, dark when idle. The fill
                // animation rides the rail, so nothing is drawn behind the letter.
                int[] rail = railRect(btn);
                box(ctx, rail[0], rail[1], rail[2], rail[3], withAlpha(0x000000, 0x59));
            }

            case SOFT_BLOCK -> {
                rounded(ctx, x, y, w, h, 3, body);
                // The top light line reads as a bevel; it goes out under a press,
                // which is what sells the key as physically sinking.
                if (!pressed) {
                    int inset = Math.max(2, w / 9);
                    box(ctx, x + inset, y, w - inset * 2, 2, withAlpha(0xFFFFFF, 0x38));
                }
            }

            case SEGMENT -> {
                // Idle slices are faint; the cascade animation lights them.
                int count = Math.max(2, Math.min(5, h / 4));
                int gap = h >= 16 ? 2 : 1;
                int sliceH = Math.max(1, (h - gap * (count - 1)) / count);
                for (int i = 0; i < count; i++) {
                    box(ctx, x, y + i * (sliceH + gap), w, sliceH, withAlpha(0xFFFFFF, 0x1A));
                }
            }

            case NEON -> {
                // The core deepens under a press so the halo has something to sit on.
                int core = bg & 0x00FFFFFF;
                box(ctx, x, y, w, h, withAlpha(core, pressed ? 0xE6 : (bg >>> 24)));
                frame(ctx, x, y, w, h, 1, pressed ? accent : withAlpha(0xFFFFFF, 0x2E));
            }

            case WEDGE -> {
                int dir = wedgeDir(btn);
                if (dir < 0) box(ctx, x, y, w, h, body);
                else wedge(ctx, x, y, w, h, dir, body);
            }

            case HEX -> hexagon(ctx, x, y, w, h, body);

            case PIXEL_BEVEL -> {
                // 3px is one "pixel" here, so every measurement is a multiple of it.
                int p = 3;
                box(ctx, x - p, y - p, w + p * 2, h + p * 2, withAlpha(0x0B0E13, 0xFF));
                box(ctx, x, y, w, h, body);
                int light = pressed ? withAlpha(0x000000, 0x66) : withAlpha(0x626F8A, 0xFF);
                int dark = pressed ? withAlpha(0xFFFFFF, 0x73) : withAlpha(0x10141C, 0xFF);
                // The bevel inverts under a press: the light moves to the bottom.
                box(ctx, x, y, w, p, light);
                box(ctx, x, y, p, h, light);
                box(ctx, x, y + h - p, w, p, dark);
                box(ctx, x + w - p, y, p, h, dark);
                int corner = pressed ? 0xFFF6E3AB : 0xFFC9A24A;
                box(ctx, x - p, y - p, p, p, corner);
                box(ctx, x + w, y - p, p, p, corner);
                box(ctx, x - p, y + h, p, p, corner);
                box(ctx, x + w, y + h, p, p, corner);
            }

            case SLOT -> {
                box(ctx, x, y, w, h, pressed ? accent : bg);
                int p = Math.max(1, Math.min(3, w / 12));
                int hi = pressed ? withAlpha(0xFFFFFF, 0x66) : withAlpha(0x000000, 0x99);
                int lo = pressed ? withAlpha(0x000000, 0x73) : withAlpha(0xC9A24A, 0x38);
                box(ctx, x, y, w, p, hi);
                box(ctx, x, y, p, h, hi);
                box(ctx, x, y + h - p, w, p, lo);
                box(ctx, x + w - p, y, p, h, lo);
            }

            // The timeline draws itself at module level; a single button has no body.
            case TIMELINE -> { }
        }
    }

    /**
     * The gold board behind every key. It is a separate switch from the design so
     * it can back any style, not just {@link #SLOT} — the same independence the
     * animations get.
     */
    public static void drawBoard(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        int p = 3;
        box(ctx, x - p * 7, y - p * 7, w + p * 14, h + p * 14, withAlpha(0x12161D, 0xFF));
        frame(ctx, x - p * 7, y - p * 7, w + p * 14, h + p * 14, p, 0xFFC9A24A);
        frame(ctx, x - p * 5, y - p * 5, w + p * 10, h + p * 10, p, 0xFF7D6128);

        int bx = x - p * 7, by = y - p * 7, bw = w + p * 14, bh = h + p * 14;
        // Corner jewels with a dark pip, and a pip at the middle of each edge.
        for (int i = 0; i < 4; i++) {
            int cx = (i % 2 == 0) ? bx - p : bx + bw - p * 3;
            int cy = (i < 2) ? by - p : by + bh - p * 3;
            box(ctx, cx, cy, p * 4, p * 4, 0xFFC9A24A);
            box(ctx, cx + p, cy + p, p * 2, p * 2, 0xFF0B0E13);
        }
        box(ctx, bx + bw / 2 - p * 3, by - p, p * 6, p * 2, 0xFFC9A24A);
        box(ctx, bx + bw / 2 - p * 3, by + bh - p, p * 6, p * 2, 0xFFC9A24A);
        box(ctx, bx - p, by + bh / 2 - p * 3, p * 2, p * 6, 0xFFC9A24A);
        box(ctx, bx + bw - p, by + bh / 2 - p * 3, p * 2, p * 6, 0xFFC9A24A);
    }

    /** Extra room the board needs around the key cluster, in game pixels. */
    public static final int BOARD_PADDING = 24;
}
