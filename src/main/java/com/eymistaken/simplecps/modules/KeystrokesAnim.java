package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * What a keystroke button does when it is pressed, independent of how it looks.
 *
 * <p>The three axes here are deliberately separate rather than one list of named
 * effects. A key can only move one way at a time and can only be flooded one way
 * at a time, but moving and flooding are unrelated — the old single
 * {@code keystrokesEffectMode} enum had to invent a "Both" entry just to express
 * squish+ripple, and every further combination would have doubled the list again.
 *
 * <p>{@link Direction} is what makes a fill work from any side: a sweep is one
 * piece of code that reads its origin from the direction, not six copies of it.
 */
public final class KeystrokesAnim {

    private KeystrokesAnim() {}

    /** The key moves. One at a time — they all drive the same transform. */
    public enum Motion {
        NONE("None"), SQUISH("Squish"), KICK("Kick"), SINK("Sink"), NUDGE("Nudge");

        private final String display;
        Motion(String d) { this.display = d; }
        public String display() { return display; }
    }

    /** The key is painted. One at a time — they all fill the same body. */
    public enum Fill {
        NONE("None"), RIPPLE("Ripple"), SWEEP("Sweep"), CASCADE("Cascade"),
        HOLD_RING("Hold Ring"), EDGE_RUN("Edge Run"), GLOW("Glow");

        private final String display;
        Fill(String d) { this.display = d; }
        public String display() { return display; }

        /** Whether {@link Direction} means anything for this fill. */
        public boolean directional() {
            return this == SWEEP || this == CASCADE || this == HOLD_RING || this == EDGE_RUN;
        }

        /** Whether this fill travels around the border rather than across the body. */
        public boolean perimeter() {
            return this == HOLD_RING || this == EDGE_RUN;
        }
    }

    /**
     * Where a fill starts. The linear six apply to {@link Fill#SWEEP} and
     * {@link Fill#CASCADE}; the two rotational ones to the perimeter fills.
     */
    public enum Direction {
        RIGHT("Right"), LEFT("Left"), DOWN("Down"), UP("Up"),
        CENTER_OUT("Center Out"), EDGES_IN("Edges In"),
        CW("Clockwise"), CCW("Counter-Clockwise");

        private final String display;
        Direction(String d) { this.display = d; }
        public String display() { return display; }

        public boolean rotational() { return this == CW || this == CCW; }
    }

    /** The directions worth offering for a given fill, in menu order. */
    public static Direction[] choicesFor(Fill fill) {
        if (fill.perimeter()) return new Direction[] { Direction.CW, Direction.CCW };
        return new Direction[] { Direction.RIGHT, Direction.LEFT, Direction.DOWN,
            Direction.UP, Direction.CENTER_OUT, Direction.EDGES_IN };
    }

    /** Drop nulls, duplicates and NONE from a stored list. */
    public static java.util.List<Motion> cleanMotions(java.util.List<Motion> in) {
        java.util.List<Motion> out = new java.util.ArrayList<>();
        if (in == null) return out;
        for (Motion m : in) {
            if (m != null && m != Motion.NONE && !out.contains(m)) out.add(m);
        }
        return out;
    }

    /** Drop nulls, duplicates and NONE from a stored list. */
    public static java.util.List<Fill> cleanFills(java.util.List<Fill> in) {
        java.util.List<Fill> out = new java.util.ArrayList<>();
        if (in == null) return out;
        for (Fill f : in) {
            if (f != null && f != Fill.NONE && !out.contains(f)) out.add(f);
        }
        return out;
    }

    /**
     * A direction the whole set can live with.
     *
     * <p>One direction is shared by every active fill. A perimeter fill cannot honour
     * "up", but {@link #coerce} maps it onto a rotation at draw time, so the stored
     * value only has to make sense for the linear fills — and if there are none, for
     * the perimeter ones.
     */
    public static Direction coerceAll(java.util.List<Fill> fills, Direction dir) {
        if (fills == null || fills.isEmpty()) return dir == null ? Direction.RIGHT : dir;
        boolean anyLinear = false;
        for (Fill f : fills) {
            if (f.directional() && !f.perimeter()) anyLinear = true;
        }
        if (anyLinear) {
            return dir == null || dir.rotational() ? Direction.RIGHT : dir;
        }
        boolean anyPerimeter = false;
        for (Fill f : fills) {
            if (f.perimeter()) anyPerimeter = true;
        }
        if (anyPerimeter) return dir != null && dir.rotational() ? dir : Direction.CW;
        return dir == null ? Direction.RIGHT : dir;
    }

    /**
     * Force a direction that suits the fill. A share code can carry any pairing,
     * and a perimeter fill told to go "up" would otherwise stall at zero.
     */
    public static Direction coerce(Fill fill, Direction dir) {
        if (fill == null) return dir == null ? Direction.RIGHT : dir;
        if (dir == null) return fill.perimeter() ? Direction.CW : Direction.RIGHT;
        if (fill.perimeter() && !dir.rotational()) return Direction.CW;
        if (!fill.perimeter() && dir.rotational()) return Direction.RIGHT;
        return dir;
    }

    // ---------------------------------------------------------------- state

    /** Per-key animation progress. Stepped once per draw, as the old effects were. */
    public static final class State {
        /** Scale for squish and kick; 1 is at rest. */
        public float scale = 1f;
        /** How far into a press this key is, 0 to 1. Drives fills and offsets alike. */
        public float press = 0f;
        /** How long the key has been held, 0 to 1, for the hold ring. */
        public float hold = 0f;
        /** Looping phase for the edge runner. */
        public float run = 0f;
        /** Release trail, 1 at the moment of release and decaying. */
        public float ghost = 0f;
        /** Ripple front and back radius, kept in the old two-value form. */
        public float rippleOuter = 0f;
        public float rippleInner = 0f;
        boolean wasPressed = false;
    }

    /**
     * States keyed by button. The settings screen renders a preview over the live
     * HUD, so a single shared map would be stepped by both passes and run the real
     * keystrokes at double speed — hence separate stores, picked by the caller.
     */
    public static final class Store {
        private final Map<Integer, State> states = new HashMap<>();

        public State get(SimpleCPSConfig.KeyButtonData btn) {
            return get(key(btn));
        }

        /**
         * State under an explicit slot. The design gallery needs this: its cards all
         * preview the same three sample buttons, so keying by keybind alone would
         * give every card one shared state and make the whole grid blink in unison.
         */
        public State get(int slot) {
            return states.computeIfAbsent(slot, k -> new State());
        }

        public void clear() {
            states.clear();
        }
    }

    /** Mouse buttons share the keystroke keyspace by sitting 1000 above it. */
    private static int key(SimpleCPSConfig.KeyButtonData btn) {
        return btn.keyCode + (btn.isMouse ? 1000 : 0);
    }

    /** Move {@code current} toward {@code target}, snapping once it is close. */
    private static float approach(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.01f) return target;
        return current + diff * speed;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * Advance one key's animation by a frame.
     *
     * <p>{@code enabled} is the key's own animation switch, and it gates every axis.
     * Before it did, a key with its animation turned off still rippled, so the
     * switch looked broken on every mode but squish.
     */
    public static void step(State s, java.util.List<Motion> motions, java.util.List<Fill> fills,
                            boolean pressed, boolean enabled, boolean ghostOn,
                            SimpleCPSConfig.KeyButtonData btn) {
        boolean down = pressed && enabled;

        // Squish and kick both drive the scale. When both are on, kick wins: it is
        // the same compression with a spring on the way back, so running them
        // together would just be a squish that cannot decide how fast to return.
        boolean kick = motions.contains(Motion.KICK);
        boolean squish = motions.contains(Motion.SQUISH);

        float target = 1f;
        if (down) {
            if (kick) target = 0.88f;
            else if (squish) target = 0.85f;
        }
        if (kick && !down && s.scale < 1f) {
            // The spring overshoots on release, then settles. Without the overshoot
            // this would just be a slower squish.
            s.scale = approach(s.scale, 1.06f, 0.35f);
            if (s.scale >= 1.055f) s.scale = 1f;
        } else {
            s.scale = approach(s.scale, target, kick ? 0.45f : 0.2f);
        }

        if (fills.contains(Fill.RIPPLE) && enabled) {
            float maxR = (float) Math.sqrt(btn.w * btn.w + btn.h * btn.h) / 2f * 1.05f;
            if (pressed) {
                s.rippleOuter = Math.min(s.rippleOuter + maxR * 0.18f, maxR);
                s.rippleInner = 0f;
            } else if (s.rippleOuter > 0f) {
                s.rippleOuter = maxR;
                s.rippleInner = Math.min(s.rippleInner + maxR * 0.15f, maxR);
                if (s.rippleInner >= maxR) {
                    s.rippleOuter = 0f;
                    s.rippleInner = 0f;
                }
            }
        } else {
            s.rippleOuter = 0f;
            s.rippleInner = 0f;
        }

        s.press = clamp01(approach(s.press, down ? 1f : 0f, down ? 0.22f : 0.18f));

        // The hold ring counts real held frames rather than easing toward a target,
        // because saying how long the key has been down is its whole job.
        if (down && fills.contains(Fill.HOLD_RING)) s.hold = clamp01(s.hold + 1f / 42f);
        else s.hold = clamp01(s.hold - 1f / 26f);

        if (down && fills.contains(Fill.EDGE_RUN)) s.run = (s.run + 1f / 54f) % 1f;
        else s.run = 0f;

        // The trail fires on the press-to-release edge, then fades on its own.
        if (s.wasPressed && !pressed && ghostOn && enabled) s.ghost = 1f;
        else if (s.ghost > 0f) s.ghost = Math.max(0f, s.ghost - 0.07f);
        s.wasPressed = pressed;
    }

    /**
     * Pixel offset the motions apply, as {@code {dx, dy}}.
     *
     * <p>Sink and nudge both move the key without resizing it, so they add rather
     * than compete: a nudged key that also sinks travels diagonally, which is what
     * both settings on at once ought to look like.
     */
    public static int[] offset(State s, java.util.List<Motion> motions,
                               SimpleCPSConfig.KeyButtonData btn) {
        int dx = 0, dy = 0;
        if (motions.contains(Motion.SINK)) {
            dy += Math.round(3f * s.press);
        }
        if (motions.contains(Motion.NUDGE)) {
            int amount = Math.round(5f * s.press);
            switch (wedgeDirOf(btn)) {
                case 0 -> dy -= amount;
                case 1 -> dx += amount;
                case 2 -> dy += amount;
                case 3 -> dx -= amount;
                default -> { }
            }
        }
        return new int[] { dx, dy };
    }

    /** Direction a NUDGE pushes, mirroring the wedge design's own mapping. */
    private static int wedgeDirOf(SimpleCPSConfig.KeyButtonData btn) {
        if (btn.isMouse) return -1;
        return switch (btn.keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_W, org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> 0;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_D, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> 1;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_S, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> 2;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> 3;
            default -> -1;
        };
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static void box(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + h, color);
    }

    /**
     * Paint the pressed state over the body, clipped to the design's shape.
     *
     * <p>Called after {@link KeystrokesDesign#drawBody} and before the label, so a
     * flooded key still reads its letter on top.
     */
    public static void drawFill(GuiGraphicsExtractor ctx, State s,
                                java.util.List<Fill> fills, Direction dir,
                                KeystrokesDesign design, SimpleCPSConfig.KeyButtonData btn,
                                int accent) {
        if (fills == null) return;
        // Drawn in enum order, so stacking two fills always layers them the same way
        // round however the player happened to tick them.
        for (Fill fill : Fill.values()) {
            if (fill == Fill.NONE || !fills.contains(fill)) continue;
            // Each fill reads the shared direction through coerce, which is what lets
            // a sweep going "up" and a ring going clockwise coexist under one setting.
            Direction own = coerce(fill, dir);
            switch (fill) {
                case RIPPLE -> drawRipple(ctx, s, design, btn);
                case SWEEP -> drawSweep(ctx, s, own, design, btn, accent);
                case CASCADE -> drawCascade(ctx, s, own, design, btn, accent);
                case HOLD_RING -> drawRing(ctx, s.hold, own, btn, accent);
                case EDGE_RUN -> drawRun(ctx, s, own, btn, accent);
                case GLOW -> drawGlow(ctx, s, btn, accent);
                default -> { }
            }
        }
    }

    /** The existing circular ripple, now clipped to the design's rows. */
    private static void drawRipple(GuiGraphicsExtractor ctx, State s,
                                   KeystrokesDesign design, SimpleCPSConfig.KeyButtonData btn) {
        if (s.rippleOuter <= 0f) return;
        int color = 0x40FFFFFF;
        int cx = btn.x + btn.w / 2;
        int cy = btn.y + btn.h / 2;
        int rOuter = (int) s.rippleOuter;
        int rInner = (int) s.rippleInner;
        for (int sy = Math.max(btn.y, cy - rOuter); sy < Math.min(btn.y + btn.h, cy + rOuter); sy++) {
            int[] span = design.rowSpan(btn, sy - btn.y);
            if (span == null) continue;
            int dy = sy - cy;
            int dxOuter = (int) Math.sqrt(Math.max(0.0, (double) rOuter * rOuter - (double) dy * dy));
            int x1 = Math.max(span[0], cx - dxOuter);
            int x2 = Math.min(span[1], cx + dxOuter);
            if (rInner > 0) {
                int dxInner = (int) Math.sqrt(Math.max(0.0, (double) rInner * rInner - (double) dy * dy));
                int lx2 = Math.min(x2, cx - dxInner);
                if (lx2 > x1) ctx.fill(x1, sy, lx2, sy + 1, color);
                int rx1 = Math.max(x1, cx + dxInner);
                if (x2 > rx1) ctx.fill(rx1, sy, x2, sy + 1, color);
            } else if (x2 > x1) {
                ctx.fill(x1, sy, x2, sy + 1, color);
            }
        }
    }

    /**
     * A linear flood. This single method is what "from any direction" means: the
     * covered band is derived from the direction and everything else is shared.
     */
    private static void drawSweep(GuiGraphicsExtractor ctx, State s, Direction dir,
                                  KeystrokesDesign design, SimpleCPSConfig.KeyButtonData btn,
                                  int accent) {
        float p = s.press;
        if (p <= 0f) return;

        for (int row = 0; row < btn.h; row++) {
            int[] span = design.rowSpan(btn, row);
            if (span == null) continue;
            int y = btn.y + row;
            int full = span[1] - span[0];

            switch (dir) {
                case RIGHT -> box(ctx, span[0], y, Math.round(full * p), 1, accent);
                case LEFT -> {
                    int w = Math.round(full * p);
                    box(ctx, span[1] - w, y, w, 1, accent);
                }
                case DOWN -> {
                    if (row < btn.h * p) box(ctx, span[0], y, full, 1, accent);
                }
                case UP -> {
                    if (row >= btn.h * (1f - p)) box(ctx, span[0], y, full, 1, accent);
                }
                case CENTER_OUT -> {
                    // Opens from the middle on both axes at once, so it reads as a
                    // box growing rather than a sideways wipe.
                    float half = btn.h / 2f;
                    if (Math.abs(row + 0.5f - half) > half * p) continue;
                    int w = Math.round(full * p);
                    box(ctx, span[0] + (full - w) / 2, y, w, 1, accent);
                }
                case EDGES_IN -> {
                    // The two bands are sized to meet exactly. Rounding each to half
                    // the width independently leaves a one-pixel seam down the middle
                    // of an odd-width key at full progress, or overlaps by one.
                    int covered = Math.round(full * p);
                    int leftW = Math.round(full * p / 2f);
                    int rightW = covered - leftW;
                    box(ctx, span[0], y, leftW, 1, accent);
                    box(ctx, span[1] - rightW, y, rightW, 1, accent);
                }
                default -> box(ctx, span[0], y, full, 1, accent);
            }
        }
    }

    /** Segments lighting in sequence, staggered along the chosen direction. */
    private static void drawCascade(GuiGraphicsExtractor ctx, State s, Direction dir,
                                    KeystrokesDesign design, SimpleCPSConfig.KeyButtonData btn,
                                    int accent) {
        float p = s.press;
        if (p <= 0f) return;

        boolean vertical = dir == Direction.UP || dir == Direction.DOWN
            || dir == Direction.CENTER_OUT || dir == Direction.EDGES_IN;
        int extent = vertical ? btn.h : btn.w;
        int count = Math.max(2, Math.min(5, extent / 4));
        int gap = extent >= 16 ? 2 : 1;
        int sliceSize = Math.max(1, (extent - gap * (count - 1)) / count);

        for (int i = 0; i < count; i++) {
            // Each slice owns a slot of the progress, which produces the source's
            // 30ms stagger without any timers.
            float lit = clamp01((p - (float) i / count) * count);
            if (lit <= 0f) continue;
            int color = withAlpha(accent, Math.round(255 * lit));

            int index = switch (dir) {
                case UP, LEFT -> count - 1 - i;
                case EDGES_IN -> (i % 2 == 0) ? i / 2 : count - 1 - i / 2;
                case CENTER_OUT -> (count / 2 + ((i % 2 == 0) ? i / 2 : -(i / 2 + 1)) + count) % count;
                default -> i;
            };
            int start = index * (sliceSize + gap);

            if (vertical) {
                for (int row = start; row < Math.min(btn.h, start + sliceSize); row++) {
                    int[] span = design.rowSpan(btn, row);
                    if (span == null) continue;
                    box(ctx, span[0], btn.y + row, span[1] - span[0], 1, color);
                }
            } else {
                for (int row = 0; row < btn.h; row++) {
                    int[] span = design.rowSpan(btn, row);
                    if (span == null) continue;
                    int x0 = Math.max(span[0], btn.x + start);
                    int x1 = Math.min(span[1], btn.x + start + sliceSize);
                    box(ctx, x0, btn.y + row, x1 - x0, 1, color);
                }
            }
        }
    }

    /** Border thickness a perimeter fill draws with. */
    private static int ringThickness(SimpleCPSConfig.KeyButtonData btn) {
        return Math.max(1, Math.min(3, Math.min(btn.w, btn.h) / 8));
    }

    /**
     * The perimeter walked as one line — top, right, bottom, left — as
     * {@code {perimeterStart, length, sideIndex}}. Both ring fills share it so a
     * clockwise run and a clockwise hold ring cannot disagree about which way that is.
     */
    private static int[][] perimeterLegs(SimpleCPSConfig.KeyButtonData btn) {
        return new int[][] {
            { 0, btn.w, 0 },
            { btn.w, btn.h, 1 },
            { btn.w + btn.h, btn.w, 2 },
            { btn.w * 2 + btn.h, btn.h, 3 },
        };
    }

    /** Draw {@code [from, to)} of the perimeter line, given in walk order. */
    private static void drawLeg(GuiGraphicsExtractor ctx, SimpleCPSConfig.KeyButtonData btn,
                                int side, int from, int len, int t, int color) {
        if (len <= 0) return;
        switch (side) {
            case 0 -> box(ctx, btn.x + from, btn.y, len, t, color);
            case 1 -> box(ctx, btn.x + btn.w - t, btn.y + from, t, len, color);
            case 2 -> box(ctx, btn.x + btn.w - from - len, btn.y + btn.h - t, len, t, color);
            case 3 -> box(ctx, btn.x, btn.y + btn.h - from - len, t, len, color);
            default -> { }
        }
    }

    /** Fill the border progressively from a corner: how long the key has been held. */
    private static void drawRing(GuiGraphicsExtractor ctx, float progress, Direction dir,
                                 SimpleCPSConfig.KeyButtonData btn, int accent) {
        if (progress <= 0f) return;
        int t = ringThickness(btn);
        int perimeter = 2 * (btn.w + btn.h);
        int filled = Math.round(progress * perimeter);
        boolean ccw = dir == Direction.CCW;

        for (int[] leg : perimeterLegs(btn)) {
            int start = leg[0], length = leg[1], side = leg[2];
            // Counter-clockwise is the same walk measured from the other end.
            int legStart = ccw ? perimeter - start - length : start;
            int amount = Math.max(0, Math.min(length, filled - legStart));
            if (amount <= 0) continue;
            int from = ccw ? length - amount : 0;
            drawLeg(ctx, btn, side, from, amount, t, accent);
        }
    }

    /** The looping edge runner: a quarter-perimeter band chasing itself. */
    private static void drawRun(GuiGraphicsExtractor ctx, State s, Direction dir,
                                SimpleCPSConfig.KeyButtonData btn, int accent) {
        if (s.press <= 0.01f) return;
        int t = ringThickness(btn);
        int perimeter = 2 * (btn.w + btn.h);
        int color = withAlpha(accent, Math.round(255 * s.press));
        int tail = Math.max(1, perimeter / 4);

        int head = Math.round(s.run * perimeter);
        if (dir == Direction.CCW) head = perimeter - head;
        int start = Math.floorMod(head - tail, perimeter);

        // Express the band as one or two non-wrapping ranges, then intersect each
        // side with them. Walking it pixel by pixel would cost a fill() per pixel.
        int[][] bands = (start + tail <= perimeter)
            ? new int[][] { { start, start + tail } }
            : new int[][] { { start, perimeter }, { 0, start + tail - perimeter } };

        for (int[] band : bands) {
            for (int[] leg : perimeterLegs(btn)) {
                int a = Math.max(band[0], leg[0]);
                int b = Math.min(band[1], leg[0] + leg[1]);
                if (b <= a) continue;
                drawLeg(ctx, btn, leg[2], a - leg[0], b - a, t, color);
            }
        }
    }

    /** Three nested haloes with falling alpha — no blur, just rectangles. */
    private static void drawGlow(GuiGraphicsExtractor ctx, State s,
                                 SimpleCPSConfig.KeyButtonData btn, int accent) {
        if (s.press <= 0f) return;
        int[] alphas = { 0x28, 0x16, 0x08 };
        for (int i = 0; i < 3; i++) {
            int grow = Math.round((i + 1) * 1.6f * s.press);
            if (grow <= 0) continue;
            box(ctx, btn.x - grow, btn.y - grow, btn.w + grow * 2, btn.h + grow * 2,
                withAlpha(accent, Math.round(alphas[i] * s.press)));
        }
    }

    /** The release trail: an outline growing away from the key as it fades. */
    public static void drawGhost(GuiGraphicsExtractor ctx, State s,
                                 SimpleCPSConfig.KeyButtonData btn, int accent) {
        if (s.ghost <= 0f) return;
        float grow = (1f - s.ghost) * 0.45f;
        int gx = Math.round(btn.w * grow / 2f);
        int gy = Math.round(btn.h * grow / 2f);
        int color = withAlpha(accent, Math.round(0xA6 * s.ghost));
        int x = btn.x - gx, y = btn.y - gy;
        int w = btn.w + gx * 2, h = btn.h + gy * 2;
        box(ctx, x, y, w, 1, color);
        box(ctx, x, y + h - 1, w, 1, color);
        box(ctx, x, y, 1, h, color);
        box(ctx, x + w - 1, y, 1, h, color);
    }

}
