package com.eymistaken.simplecps.api.event;

import net.minecraft.world.entity.Entity;

/**
 * Fired when a landed hit pushes the combo into a higher heatmap tier — so once per
 * tier, not once per hit. Levels come from
 * {@link com.eymistaken.simplecps.api.ComboHeatmap} and follow the user's chosen
 * heatmap mode.
 *
 * <p>Fired whether or not the heatmap coloring is switched on: that setting decides
 * how the counter looks, not whether the streak happened.
 *
 * @param combo         the combo count after this hit
 * @param level         the tier it just reached (1-3)
 * @param previousLevel the tier it was in before
 * @param target        the entity that was hit; never null
 */
public record ComboStreakEvent(int combo, int level, int previousLevel, Entity target) {}
