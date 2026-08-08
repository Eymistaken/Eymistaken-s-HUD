package com.eymistaken.simplecps.api;

import java.util.List;

/**
 * One tab a plugin module opens on its page in the settings screen.
 *
 * <p>Top-level rather than nested inside {@link HudModule} to match the rest of this
 * package, where every API type — {@link HudModuleSetting} and each of its subtypes,
 * {@link IHudElement} — lives in its own file. A plugin already imports
 * {@code com.eymistaken.simplecps.api.BooleanSetting} and friends; this falls in line.
 *
 * <p>Deliberately a plain record with no validation: a compact constructor that rejected
 * nulls would throw inside the plugin's own {@code getSettingsTabs()} call, and the
 * screen would then lose every tab instead of the one bad entry. Tolerance lives in
 * {@code SettingsSchema} instead, where the rest of the plugin-page defences already are.
 *
 * @param id       stable key for the tab. The settings screen remembers which tab you
 *                 were on by it, so keep it constant across calls. Made unique per page,
 *                 so a repeat is renumbered rather than fatal.
 * @param name     shown on the tab strip. Upper-cased for you, to match the built-in
 *                 pages. Falls back to {@code id} when blank.
 * @param settings the rows on this tab, in order.
 *
 * @see HudModule#getSettingsTabs()
 */
public record SettingsTab(String id, String name, List<HudModuleSetting> settings) {}
