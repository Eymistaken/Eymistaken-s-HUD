package com.eymistaken.simplecps.util;

/**
 * Pure, side-effect-free easing functions. All take a normalized progress
 * {@code t} in {@code [0,1]} and return an eased value (also usually in
 * {@code [0,1]}). Use together with {@link Anim} or a manual interpolation.
 */
public final class Easings {

    private Easings() {}

    public static float clamp01(float t) {
        if (t < 0f) return 0f;
        if (t > 1f) return 1f;
        return t;
    }

    /** Linear interpolation between {@code a} and {@code b} by {@code t}. */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Fast start, decelerating hard to a stop. Great for snapping into place. */
    public static float expoOut(float t) {
        t = clamp01(t);
        return (t >= 1f) ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
    }

    /** Symmetric ease, gentle in and out. Good for open/close transitions. */
    public static float sineInOut(float t) {
        t = clamp01(t);
        return -(float) (Math.cos(Math.PI * t) - 1.0) / 2f;
    }

    /** Decelerating cubic. Softer than {@link #expoOut}. */
    public static float cubicOut(float t) {
        t = clamp01(t);
        float f = t - 1f;
        return f * f * f + 1f;
    }

    /**
     * Decelerating with a small overshoot past the target before settling back.
     * The springy feel is only visible over longer distances — across a few pixels
     * the overshoot is sub-pixel and it reads as a plain fast ease.
     */
    public static float backOut(float t) {
        t = clamp01(t);
        float overshoot = 1.2f;
        float f = t - 1f;
        return 1f + (overshoot + 1f) * f * f * f + overshoot * f * f;
    }

    /** Accelerate then decelerate (quadratic). */
    public static float quadInOut(float t) {
        t = clamp01(t);
        return (t < 0.5f) ? 2f * t * t : 1f - (float) Math.pow(-2.0 * t + 2.0, 2.0) / 2f;
    }
}
