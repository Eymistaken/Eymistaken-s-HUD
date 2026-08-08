# Settings tabs for plugin modules

Ready-to-adapt copy for the `api-guide.html` page in the `Eymistaken-s-HUD-Website` repo.
Added in **1.1.1-8**.

---

## Own tabs on your settings page

By default a plugin module gets two tabs on its settings page: **POSITION**, built for you
from the module's anchor, offsets and scale, and **SETTINGS**, holding everything from
`getContextMenuSettings()` followed by everything from `getSettingsScreenSettings()`.

One flat list is fine for a handful of settings. Past that it stops being readable — a
module with forty settings gives the player one long scroll with no structure. Override
`getSettingsTabs()` to split them into tabs of your own, the same way the built-in modules
do it (CPS ships POSITION / STYLE / BACKGROUND / TEXT).

### The API

```java
package com.eymistaken.simplecps.api;

/**
 * @param id       stable key for the tab; the screen remembers the selected tab by it
 * @param name     shown on the tab strip, upper-cased for you
 * @param settings the rows on this tab, in order
 */
public record SettingsTab(String id, String name, List<HudModuleSetting> settings) {}
```

```java
// on HudModule
public java.util.List<SettingsTab> getSettingsTabs() {
    return java.util.List.of();
}
```

The default is empty, so this changes nothing until you override it. Existing modules keep
the layout they have.

### Example

```java
public class PotionHudModule extends HudModule {

    @Override
    public List<SettingsTab> getSettingsTabs() {
        return List.of(
            new SettingsTab("style", "Style", List.of(
                new ColorSetting("Text Color", () -> config.textColor, v -> config.textColor = v),
                new SliderSetting("Icon Size", 8, 32, 16, () -> config.iconSize, v -> config.iconSize = v),
                new BooleanSetting("Show Icons", () -> config.showIcons, v -> config.showIcons = v))),

            new SettingsTab("filter", "Filter", List.of(
                new BooleanSetting("Hide Ambient", () -> config.hideAmbient, v -> config.hideAmbient = v),
                new CycleSetting("Sort By", List.of("DURATION", "NAME", "AMPLIFIER"),
                    () -> config.sortMode, v -> config.sortMode = v))),

            new SettingsTab("bg", "Background", List.of(
                new BooleanSetting("Show Background", () -> config.showBg, v -> config.showBg = v),
                new SliderSetting("Opacity", 0, 255, 128, () -> config.bgOpacity, v -> config.bgOpacity = v)))
        );
    }
}
```

Result: **POSITION | STYLE | FILTER | BACKGROUND**.

### How it fits with the two flat lists

`getSettingsTabs()` does not replace `getContextMenuSettings()` or
`getSettingsScreenSettings()`; it sits alongside them.

| You return | What the page shows |
| --- | --- |
| Nothing (default) | POSITION, then one SETTINGS tab: context menu settings, then settings-screen settings. Unchanged from earlier versions. |
| Tabs | POSITION, then your tabs in order. The context menu settings lead your **first** tab, so the "everything on the right-click menu also appears on the page" rule still holds. Anything from `getSettingsScreenSettings()` becomes a trailing SETTINGS tab. |

So there is no need to restate context menu entries inside a tab — they arrive on their
own. Use `getSettingsScreenSettings()` for leftovers that do not belong in any of your
tabs, or drop it entirely once your tabs cover everything.

### Rules worth knowing

- **Do not declare placement rows.** POSITION is always built for you from
  `getPositionType()`, `getXOffset()`, `getYOffset()` and `getScale()`.
- **Empty tabs are not shown.** A tab with no settings, or one whose settings are all of a
  type the screen does not recognise, is dropped rather than rendered blank.
- **Duplicate ids are renumbered, not dropped.** Two tabs with id `style` become `style`
  and `style2`. Claiming a reserved id (`pos`, `settings`) is handled the same way. Nothing
  goes missing, but pick distinct ids anyway — the id is how the screen remembers which tab
  the player was on.
- **Keep ids stable across calls.** The method is called every time the screen is built. A
  freshly generated id each time loses the player's selected tab.
- **A blank name falls back to the id**, and a blank id is derived from the name, so a tab
  is always reachable and always labelled.
- **Keep to a handful of short names.** The tab strip does not scroll; tabs past the
  panel's width are not drawn. Four to six short names is comfortable.
- **Throwing is survivable, not free.** If `getSettingsTabs()` throws or returns `null`,
  the page falls back to the single flat SETTINGS tab instead of taking the screen down
  with it. Null entries in the list, and null settings inside a tab, are skipped.
- **Opening from the editor** (right-click a module → Open Settings) lands on your first
  non-POSITION tab.

### Compatibility

Compiled against an older HUD build, a module that overrides `getSettingsTabs()` still
runs — the method simply is not called, and the module shows the flat SETTINGS tab. There
is no need to guard the override.
