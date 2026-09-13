package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.api.ComboHeatmap;
import com.eymistaken.simplecps.util.Easings;

/**
 * Motion for {@link ComboModule}: how hits land on our row, how it shakes at a high combo and
 * flashes on a new best, the color and shake of the received combo, and the divider between the
 * two rows.
 *
 * <p>All of it reads one clock, {@link #now()}, and that clock stands still while a hitstop
 * holds. A big moment therefore freezes everything on the counter at once, shakes included, the
 * way a fighting game freezes both fighters on a heavy blow, and the motion resumes exactly where
 * it stopped.
 *
 * <p>State moves only through {@link #update}, {@link #impact}, {@link #best} and
 * {@link #forgetBest}. Each display of the counter keeps its own instance: the live HUD, and the
 * settings preview's demo, which is drawn alongside the live HUD and would otherwise step the
 * same state twice a frame. The HUD editor's still sample uses only the static helpers.
 */
final class ComboFeel {

    /** Longest a hitstop may hold; any longer and it reads as the game stalling. */
    static final long MAX_HOLD_MS = 160;
    /** Phase offset that keeps our row from shaking in step with the received row under it. */
    static final double OWN_PHASE = 2.4;

    // Stops the received combo darkens through, from its first hit to the top tier.
    private static final int YELLOW = 0xFFD23F;
    private static final int ORANGE = 0xFF8A1F;
    private static final int RED = 0xD91E1E;
    private static final int DARK_RED = 0x5C0000;
    private static final int NEAR_BLACK = 0x120000;
    /** The edge dark text warms to, so a near-black number still reads against a dark world. */
    private static final int GLOW = 0x8A1C1C;

    /** Time constant of the color's approach; it has all but arrived after three of these. */
    private static final float COLOR_EASE_MS = 150f;
    private static final float DIVIDER_MS = 150f;
    /** Steady shake of the received row at the top tier, in unscaled pixels. */
    private static final float MAX_SHAKE_PX = 2.5f;
    /** Jolt from a single hit taken, in unscaled pixels at full strength, gone after {@link #JOLT_MS}. */
    private static final float JOLT_PX = 1.5f;
    private static final float JOLT_MS = 300f;
    /** Our row starts shaking this hard on reaching the top tier, in unscaled pixels... */
    private static final float OWN_SHAKE_START_PX = 1.0f;
    /** ...and gains this much more as the combo runs on past it. */
    private static final float OWN_SHAKE_GROWTH_PX = 2.0f;
    /** Longest step one frame may take, so a stall does not skip the motion outright. */
    private static final float MAX_STEP_MS = 250f;
    /** A new best stays gold through its hitstop, then drains back to the row's own color over this. */
    private static final float GOLD_FADE_MS = 400f;
    /** How long the BEST tag stays up, spending the last stretch of it fading out. */
    private static final float BEST_TAG_MS = 1600f;
    private static final float BEST_TAG_FADE_MS = 300f;
    private static final int[] NO_SHAKE = {0, 0};

    // Hitstop: from holdStart until holdEnd, in real time, the clock reads frozenClock.
    private long holdStart = -1;
    private long holdEnd = 0;
    private long frozenClock = 0;
    private long heldTotal = 0;

    // The impact playing on our row.
    private long impactClock = Long.MIN_VALUE;
    private float impactBoost = 0f;
    private float impactSettleMs = 1f;
    private int impactRank = 0;

    // When the latest new best landed, on the animation clock; MIN_VALUE when there is none to show.
    private long bestClock = Long.MIN_VALUE;

    private long lastFrame = -1;
    private float divider = 0f;
    private float red;
    private float green;
    private float blue;
    private boolean colorTracking = false;
    private int seenReceivedHits = -1;
    private long joltStart = Long.MIN_VALUE;

    /** Real milliseconds, for anything that has to keep running through a hitstop. */
    static long realNow() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Milliseconds on the clock every animation in the module reads. It stops during a hitstop. */
    long now() {
        long real = realNow();
        if (holdStart >= 0) {
            if (real < holdEnd) return frozenClock;
            heldTotal += holdEnd - holdStart;
            holdStart = -1;
        }
        return real - heldTotal;
    }

    /**
     * Lands an impact on our row: it jumps to {@code 1 + boost} at once, stays there for
     * {@code holdMs} with the whole counter frozen, then settles back with a slight overshoot.
     * A moment ranked below the one still playing does not cut it short.
     */
    void impact(float boost, long holdMs, float settleMs, int rank) {
        long clock = now();
        boolean playing = impactClock != Long.MIN_VALUE && clock - impactClock < impactSettleMs;
        if (playing && rank < impactRank) return;

        impactClock = clock;
        impactBoost = boost;
        impactSettleMs = Math.max(1f, settleMs);
        impactRank = rank;
        if (holdMs > 0) hold(holdMs);
    }

    /** Scale for our row this frame: 1 at rest, the impact's peak while it holds, then settling. */
    float pulse() {
        if (impactClock == Long.MIN_VALUE) return 1f;
        float t = (now() - impactClock) / impactSettleMs;
        if (t >= 1f) return 1f;
        return 1f + impactBoost * (1f - Easings.backOut(t));
    }

    /**
     * A new best landed: our row flashes gold and wears the BEST tag for a moment. Call it after
     * the best's impact, so both start inside that hitstop and hold with everything else.
     */
    void best() {
        bestClock = now();
    }

    /** A new combo began; the tag belonged to the one before it. */
    void forgetBest() {
        bestClock = Long.MIN_VALUE;
    }

    /** How far our row's color goes toward gold: all the way while the best holds, then draining. */
    float gold() {
        if (bestClock == Long.MIN_VALUE) return 0f;
        float t = (now() - bestClock) / GOLD_FADE_MS;
        return t >= 1f ? 0f : 1f - Easings.sineInOut(t);
    }

    /** Opacity of the BEST tag: up at once, fading over the end of its time. */
    float bestTag() {
        if (bestClock == Long.MIN_VALUE) return 0f;
        return Easings.clamp01((BEST_TAG_MS - (now() - bestClock)) / BEST_TAG_FADE_MS);
    }

    /** Advances the motion by one live frame. */
    void update(boolean stacked, int received, int receivedHits, ComboHeatmap heatmap) {
        long now = now();
        float step = lastFrame < 0 ? 0f : Math.min(MAX_STEP_MS, now - lastFrame);
        lastFrame = now;

        float dividerStep = step / DIVIDER_MS;
        divider = stacked ? Math.min(1f, divider + dividerStep) : Math.max(0f, divider - dividerStep);

        if (receivedHits != seenReceivedHits) {
            if (seenReceivedHits >= 0) joltStart = now;
            seenReceivedHits = receivedHits;
        }

        if (received <= 0) {
            // The next combo taken starts from its own color instead of easing out of this one.
            colorTracking = false;
            return;
        }
        int target = colorFor(received, heatmap);
        float follow = colorTracking ? 1f - (float) Math.exp(-step / COLOR_EASE_MS) : 1f;
        red += (((target >> 16) & 0xFF) - red) * follow;
        green += (((target >> 8) & 0xFF) - green) * follow;
        blue += ((target & 0xFF) - blue) * follow;
        colorTracking = true;
    }

    /** Eased length of the divider, from 0 to 1. */
    float divider() {
        return Easings.cubicOut(divider);
    }

    int receivedColor() {
        return 0xFF000000 | (Math.round(red) << 16) | (Math.round(green) << 8) | Math.round(blue);
    }

    /**
     * This frame's shake for the received row, in whole GUI pixels: its steady shake and the jolt
     * of the latest hit taken, each scaled by its own strength.
     */
    int[] shake(int received, ComboHeatmap heatmap, float scale, float shakeStrength, float joltStrength) {
        long now = now();
        float jolt = joltStart == Long.MIN_VALUE ? 0f : Easings.clamp01(1f - (now - joltStart) / JOLT_MS);
        float amplitude = growthShake(received, heatmap) * shakeStrength
            + JOLT_PX * jolt * jolt * joltStrength;
        return shakeOffset(amplitude * scale, now, 0.0);
    }

    private void hold(long ms) {
        long real = realNow();
        if (holdStart < 0) {
            frozenClock = real - heldTotal;
            holdStart = real;
            holdEnd = real + Math.min(ms, MAX_HOLD_MS);
        } else {
            // Already frozen: a bigger moment may stretch the hold, never past the cap from its start.
            holdEnd = Math.max(holdEnd, Math.min(real + ms, holdStart + MAX_HOLD_MS));
        }
    }

    /** The color the received combo settles on at {@code received} hits. */
    static int colorFor(int received, ComboHeatmap heatmap) {
        int tier1 = heatmap.tier1();
        int tier2 = heatmap.tier2();
        int tier3 = heatmap.tier3();
        int darkRedAt = (tier2 + tier3) / 2;
        int rgb;
        if (received < tier1) {
            rgb = mix(YELLOW, ORANGE, progress(received, 1, tier1));
        } else if (received < tier2) {
            rgb = mix(ORANGE, RED, progress(received, tier1, tier2));
        } else if (received < darkRedAt) {
            rgb = mix(RED, DARK_RED, progress(received, tier2, darkRedAt));
        } else {
            rgb = mix(DARK_RED, NEAR_BLACK, progress(received, darkRedAt, tier3));
        }
        return 0xFF000000 | rgb;
    }

    /** Steady shake of the received row in unscaled pixels: none at one hit, full at the top tier. */
    static float growthShake(int received, ComboHeatmap heatmap) {
        float growth = progress(received, 1, heatmap.tier3());
        return MAX_SHAKE_PX * growth * growth;
    }

    /**
     * How hard our row shakes at {@code combo}, in unscaled pixels at full strength. Nothing below
     * the top tier; from there it grows over as many hits again as the step between the last two
     * tiers.
     */
    static float ownShake(int combo, ComboHeatmap heatmap) {
        int top = heatmap.tier3();
        if (combo < top) return 0f;
        float growth = progress(combo, top, top + Math.max(1, top - heatmap.tier2()));
        return OWN_SHAKE_START_PX + OWN_SHAKE_GROWTH_PX * growth * growth;
    }

    /**
     * A smooth wander of up to {@code amplitude} GUI pixels, rounded to whole pixels so the text
     * stays on the pixel grid. Each axis sums its own detuned sines, so the path does not repeat
     * visibly and plays the same at any frame rate; {@code phase} gives each row its own path.
     */
    static int[] shakeOffset(float amplitude, long now, double phase) {
        if (amplitude <= 0f) return NO_SHAKE;
        double t = now / 1000.0;
        double x = 0.55 * Math.sin(t * 71.0 + 1.3 + phase) + 0.30 * Math.sin(t * 113.0 + 4.1 + phase)
            + 0.15 * Math.sin(t * 173.0 + 2.7 + phase);
        double y = 0.55 * Math.sin(t * 83.0 + 5.2 + phase) + 0.30 * Math.sin(t * 127.0 + 0.6 + phase)
            + 0.15 * Math.sin(t * 157.0 + 3.9 + phase);
        return new int[] {(int) Math.round(x * amplitude), (int) Math.round(y * amplitude)};
    }

    /**
     * Edge color for text drawn in {@code argb}: black for an outline, or the text darkened for a
     * shadow, warming to a dim red as the text itself gets too dark to stand out.
     */
    static int edgeColor(int argb, boolean outline) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float luminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
        float glow = Easings.clamp01((0.35f - luminance) / 0.30f);
        int base = outline ? 0x000000 : ((r / 4) << 16) | ((g / 4) << 8) | (b / 4);
        return 0xFF000000 | mix(base, GLOW, glow);
    }

    private static float progress(int value, int from, int to) {
        return to <= from ? 1f : Easings.clamp01((value - from) / (float) (to - from));
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round(Easings.lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t));
        int g = Math.round(Easings.lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t));
        int b = Math.round(Easings.lerp(from & 0xFF, to & 0xFF, t));
        return (r << 16) | (g << 8) | b;
    }
}
