package com.eymistaken.simplecps.util;

/**
 * Frame-rate-independent animated float. Tracks a current value and eases it
 * toward a target over a wall-clock duration using {@link System#nanoTime()},
 * so motion looks identical at 30 or 240 FPS (unlike the mod's older per-frame
 * decay animations).
 *
 * <p>Typical use: keep one {@code Anim} per element in a {@code Map} and call
 * {@link #update(float, float, Curve)} once per frame from {@code extractRenderState}.
 */
public final class Anim {

    /** Easing curve: maps normalized progress {@code t in [0,1]} to an eased value. */
    public interface Curve {
        float apply(float t);
    }

    private float value;
    private float start;
    private float target;
    private long startNanos;
    private long durationNanos;
    private boolean initialized;

    public Anim(float initial) {
        this.value = initial;
        this.start = initial;
        this.target = initial;
    }

    /** Current animated value. */
    public float get() {
        return value;
    }

    /** Jump instantly to {@code v}, cancelling any in-flight animation. */
    public void snap(float v) {
        this.value = v;
        this.start = v;
        this.target = v;
        this.durationNanos = 0;
        this.initialized = true;
    }

    /** True once the current tween has reached its target. */
    public boolean isDone() {
        return value == target;
    }

    /**
     * Advance the animation toward {@code target}. When {@code target} changes,
     * a fresh tween starts from the current value over {@code durationSeconds}.
     *
     * @param target          value to ease toward
     * @param durationSeconds tween length in seconds ({@code <= 0} snaps instantly)
     * @param curve           easing curve (e.g. {@code Easings::expoOut})
     * @return the updated current value
     */
    public float update(float target, float durationSeconds, Curve curve) {
        long now = System.nanoTime();

        if (!initialized) {
            this.value = target;
            this.start = target;
            this.target = target;
            this.initialized = true;
            return value;
        }

        if (target != this.target) {
            this.start = this.value;
            this.target = target;
            this.startNanos = now;
            this.durationNanos = (long) (durationSeconds * 1_000_000_000L);
        }

        if (durationNanos <= 0) {
            value = target;
            return value;
        }

        float t = (now - startNanos) / (float) durationNanos;
        if (t >= 1f) {
            value = target;
            return value;
        }
        value = start + (target - start) * curve.apply(Easings.clamp01(t));
        return value;
    }
}
