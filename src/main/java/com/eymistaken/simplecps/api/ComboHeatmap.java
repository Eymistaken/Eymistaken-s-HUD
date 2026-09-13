package com.eymistaken.simplecps.api;

import com.eymistaken.simplecps.SimpleCPSConfig;

/**
 * The combo thresholds behind the heatmap coloring, shared so the display and the
 * {@link com.eymistaken.simplecps.api.event.ComboStreakEvent} can never disagree
 * about what "level 2" means.
 *
 * <p>A combo sits at level 0 below {@code tier1}, level 1 from there to {@code tier2},
 * level 2 up to {@code tier3}, and level 3 at or above it — which is also the point
 * where the counter starts shaking.
 *
 * <p>EASY, MEDIUM and HARD are fixed; CUSTOM is the player's own three tiers, read
 * from the config on every call.
 */
public record ComboHeatmap(int tier1, int tier2, int tier3) {

    private static final ComboHeatmap EASY = new ComboHeatmap(3, 5, 7);
    private static final ComboHeatmap MEDIUM = new ComboHeatmap(5, 8, 12);
    private static final ComboHeatmap HARD = new ComboHeatmap(10, 40, 60);

    public static ComboHeatmap of(SimpleCPSConfig.HeatmapMode mode) {
        // Null when a config names a mode this build does not have; a switch over it
        // would throw even with a default arm.
        if (mode == null) return MEDIUM;
        return switch (mode) {
            case EASY -> EASY;
            case HARD -> HARD;
            case CUSTOM -> custom(SimpleCPSConfig.instance);
            default -> MEDIUM;
        };
    }

    /** Put in order here too, so a config caught mid-edit still gives usable tiers. */
    private static ComboHeatmap custom(SimpleCPSConfig config) {
        int tier1 = Math.max(1, config.comboHeatTier1);
        int tier2 = Math.max(tier1 + 1, config.comboHeatTier2);
        int tier3 = Math.max(tier2 + 1, config.comboHeatTier3);
        return new ComboHeatmap(tier1, tier2, tier3);
    }

    /** Heatmap level for a combo count: 0 (cold) to 3 (top tier). */
    public int levelFor(int combo) {
        if (combo < tier1) return 0;
        if (combo < tier2) return 1;
        if (combo < tier3) return 2;
        return 3;
    }
}
