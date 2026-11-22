<div align="center">

# Eymistaken's HUD

A client-side PvP HUD for Fabric. Seven modules, a live drag-and-drop editor, a keystrokes designer, layouts you can share as a line of text, and a plugin API for adding modules of your own.

[![Modrinth downloads](https://img.shields.io/modrinth/dt/eymistakenshud?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/mod/eymistakenshud)
[![CurseForge downloads](https://img.shields.io/curseforge/dt/1445158?logo=curseforge&label=CurseForge&color=F16436)](https://www.curseforge.com/minecraft/mc-mods/eymistakenshud)
[![Latest release](https://img.shields.io/github/v/release/Eymistaken/Eymistaken-s-HUD?label=release)](https://github.com/Eymistaken/Eymistaken-s-HUD/releases/latest)
[![Minecraft version](https://img.shields.io/modrinth/game-versions/eymistakenshud?label=Minecraft&color=62B47A)](https://modrinth.com/mod/eymistakenshud/versions)
[![Java version](https://img.shields.io/badge/Java-21%20%7C%2025-E76F00)](#installation)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENCE)

**[Website](https://eymistaken.github.io/Eymistaken-s-HUD-Website/)** ·
**[User Guide](https://eymistaken.github.io/Eymistaken-s-HUD-Website/user-guide.html)** ·
**[API Guide](https://eymistaken.github.io/Eymistaken-s-HUD-Website/api-guide.html)** ·
**[Modrinth](https://modrinth.com/mod/eymistakenshud)** ·
**[CurseForge](https://www.curseforge.com/minecraft/mc-mods/eymistakenshud)**

</div>

![The HUD in game: keystrokes, CPS, FPS, combo, reach, ping and armor](.github/media/overview.png)

## Modules

| Module | What it shows |
|---|---|
| **CPS** | Left and right clicks per second, with a configurable separator and free-text prefix and suffix, so it can read `12 \| 4`, `CPS: 12`, or anything else. |
| **Keystrokes** | Any keys and mouse buttons you choose, in a layout you draw yourself. Eleven designs, stackable press animations, per-key colors, and an optional CPS counter on any key. |
| **Combo** | Your current hit streak. It waits for the server to confirm each hit, colors the count through three heatmap tiers, shows a combo landed on you as a negative count, and records your best. |
| **Reach** | The distance of your last hit, to two decimal places. Can filter to players only and keep a placeholder label on screen before the first hit. |
| **Armor** | Armor, main hand and off hand as item icons with durability bars. Items react when they take a hit and warn you before they break. Every slot can be placed on its own. |
| **FPS** | Frames per second, with custom suffix, color, scale, rainbow mode and background. |
| **Ping** | Your latency to the server, read from your own player-list entry. |

Modules carry their own color, scale, background and opacity settings, and text can take an optional outline in place of the vanilla shadow.

## Keystrokes designer

The keystrokes layout is drawn in its own editor rather than picked from a list. Add keys, modifiers, mouse buttons and a space bar; resize them; move each label independently; and undo or redo any of it.

<p align="center">
  <img src=".github/media/design-gallery.gif" height="280" alt="The design gallery, with each card animating its own design">
  <img src=".github/media/keystrokes.gif" height="280" alt="The keystrokes module in game">
</p>

How a key looks at rest and what it does when pressed are separate choices, and any combination works:

- **Designs** set the resting shape: Baseline, Frame, Bar, Soft Block, Segment, Neon, Wedge, Timeline, Hex, Pixel Bevel and Slot. Wedge and Hex also rearrange the keys into a compass and a honeycomb; Timeline lays them out as lanes of press history. Each design arrives with its own palette and animation, and all of it stays editable.
- **Motions** move the key: squish, kick, sink and nudge.
- **Fills** paint the key: ripple, sweep, cascade, hold ring, edge run and glow, each running from a chosen direction.
- **Ghost trail** sends an outline outward from a released key as it fades.

Motions and fills are sets, so several run at once. Any single key can override the design, motion, fill or direction set for the rest.

![The keystrokes designer with a Hex layout and per-key overrides in the inspector](.github/media/designer.png)

## In-game editor

Press **Right Shift** in game (rebindable under Controls). Everything in the editor is the live HUD.

- Drag any module, or any single armor slot, to place it exactly. Scroll over it to scale from 50% to 300%, or nudge it with the arrow keys.
- Right-click a module for a menu of its own settings without leaving the screen.
- Select two or more elements to align them left, center, right, top, middle or bottom, or distribute them evenly.
- Elements snap to each other's edges, with an optional 4, 8, 16 or 32 pixel grid.
- **Prevent Overlap** keeps two modules from covering the same pixels by moving one to the nearest free space.
- Right-click empty space for settings, presets, server configs, share codes and a full reset.

![The in-game editor with the alignment toolbar, grid and a plugin module](.github/media/hud-editor.png)

## Settings screen

One page per module, split into tabs, with a search box that finds any setting by name, a description and default for every row, a one-click reset on anything you have changed, and a live preview of the module you are editing. The screen keeps the same physical size at every GUI Scale, and that size can be adjusted from 50% to 200%.

Open it from Mod Menu, or from the editor's right-click menu.

![The settings screen on the CPS page, with the live preview, presets and share code panels](.github/media/settings.png)

## Layouts that travel

- **Presets** save the whole HUD under a name, stored as plain JSON in `config/simplecps_presets/`.
- **Per-server configs** remember a layout for each server you configure. Joining that server loads it silently, and changes made there are saved back to it.
- **Share codes** export the whole HUD, or just the keystrokes look, as one `EYMHUD1-…` string. Imports are validated before anything is applied, and a timestamped backup is taken first.

## Installation

Each release comes as three jars, one for each range of Minecraft versions. Download the one that covers yours:

| Minecraft | Jar | Java | Fabric API |
|---|---|---|---|
| 26.3 | `eymistakens-hud-<version>+26.3.jar` | 25 | 0.160.6+26.3 or newer |
| 26.1, 26.1.1, 26.1.2, 26.2 | `eymistakens-hud-<version>+26.1-26.2.jar` | 25 | 0.140.3 or newer |
| 1.21.9, 1.21.10, 1.21.11 | `eymistakens-hud-<version>+1.21.9-1.21.11.jar` | 21 | 0.133.9 or newer |

Every jar needs Fabric Loader 0.19.5 or newer. Mod Menu is optional and adds a button for the settings screen.

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and the matching Eymistaken's HUD jar in `.minecraft/mods/`.
3. Launch the game and press Right Shift.

Download the jar from [Modrinth](https://modrinth.com/mod/eymistakenshud), [CurseForge](https://www.curseforge.com/minecraft/mc-mods/eymistakenshud) or [GitHub Releases](https://github.com/Eymistaken/Eymistaken-s-HUD/releases/latest). The mod is client-side only and needs nothing on the server.

## Building from source

You need JDK 25. Launching the 1.21.x development client also needs JDK 21.

```bash
git clone https://github.com/Eymistaken/Eymistaken-s-HUD.git
cd Eymistaken-s-HUD
./gradlew build
```

One source tree builds all three jars with [Stonecutter](https://stonecutter.kikugie.dev/). Each Minecraft range is a node under `versions/`, named after the version it is compiled against (`26.3`, `26.2` and `1.21.9`), and every node is built from the same `src/`. A node's jar lands in `versions/<node>/build/libs/`; `./gradlew build collectJars` also gathers all three into `build/libs/`.

To launch a development client with the mod loaded, name the node:

```bash
./gradlew :26.3:runClient
```

Mod Menu is a compile-only dependency and is not present in the development client, so reach the settings screen through the in-game editor there: Right Shift, then right-click empty space.

## Writing a plugin

Other mods can add modules to the HUD through the `eymistaken_hud` entrypoint. A registered module is ticked and drawn with the built-in ones, can be dragged and scaled in the editor, and gets a page in the settings screen with no extra work. If it opts in, its state also travels inside presets, share codes and per-server configs.

### Add the dependency

Builds are published through [JitPack](https://jitpack.io/#Eymistaken/Eymistaken-s-HUD):

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.Eymistaken:Eymistaken-s-HUD:1.2.6'
}
```

That line is the 26.3 jar. Minecraft 26.x runs unobfuscated and Loom no longer remaps mod dependencies, so this is a plain `implementation` rather than `modImplementation`. Use a release version without the leading `v`, or `main-SNAPSHOT` to track the main branch.

From 1.2.6 on, the other two jars are published beside it, each under a classifier named after its range. The `@jar` keeps the 26.3 jar's dependencies out of your build:

```gradle
// Minecraft 26.1 to 26.2
implementation 'com.github.Eymistaken:Eymistaken-s-HUD:1.2.6:26.1-26.2@jar'

// Minecraft 1.21.9 to 1.21.11, which is obfuscated, so Loom remaps the jar
modImplementation 'com.github.Eymistaken:Eymistaken-s-HUD:1.2.6:1.21.9-1.21.11@jar'
```

On 1.21.x the drawing class is called `GuiGraphics`, so the examples below take a `GuiGraphics` there instead of a `GuiGraphicsExtractor`.

The mod id is `simplecps`; declare it under `depends` or `suggests` in your `fabric.mod.json` as appropriate.

### Register the entrypoint

```json
"entrypoints": {
    "eymistaken_hud": [
        "com.example.myaddon.MyPlugin"
    ]
}
```

```java
import com.eymistaken.simplecps.HudModuleManager;
import com.eymistaken.simplecps.api.EymistakenHudEventBus;
import com.eymistaken.simplecps.api.EymistakenHudPlugin;
import com.eymistaken.simplecps.api.event.ComboStreakEvent;

public class MyPlugin implements EymistakenHudPlugin {
    @Override
    public void registerHudModules(HudModuleManager manager) {
        manager.registerModule(new MyModule());
    }

    @Override
    public void registerEvents(EymistakenHudEventBus bus) {
        bus.subscribe(ComboStreakEvent.class, event -> {
            // Fired once per heatmap tier, not once per hit.
            if (event.level() >= 2) onHotStreak(event.target());
        });
    }
}
```

### Write a module

A module extends `HudModule`, reports its size in screen pixels with its scale applied, and draws itself from its own origin:

```java
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MyModule extends HudModule {
    private static final String TEXT = "Hello";

    @Override public String getName() { return "myaddon:greeting"; }  // unique; prefix it with your mod id

    @Override public SimpleCPSConfig.Position getPositionType() { return SimpleCPSConfig.Position.TOP_LEFT; }
    @Override public int getXOffset() { return 0; }
    @Override public int getYOffset() { return 0; }
    @Override public boolean isEnabled() { return true; }

    private float scale() { return getScale() / 100f; }

    @Override public int getWidth()  { return Math.round(textWidth(TEXT) * scale()); }
    @Override public int getHeight() { return Math.round(client.font.lineHeight * scale()); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        context.pose().pushMatrix();
        context.pose().translate((float) x, (float) y);
        context.pose().scale(scale(), scale());
        drawText(context, TEXT, 0, 0, 0xFFFFFFFF);  // honors the player's outline setting
        context.pose().popMatrix();
    }
}
```

From there, a module can override the position and scale setters so the editor can move it, return settings from `getContextMenuSettings()` for its right-click menu, split its settings page into tabs with `getSettingsTabs()`, provide a live preview through `getPreview()`, expose independently placeable parts with `getSubElements()`, and persist its own state through `saveState()` and `loadState()`. The six setting types (`BooleanSetting`, `ColorSetting`, `SliderSetting`, `CycleSetting`, `TextSetting` and `ActionSetting`) live in `com.eymistaken.simplecps.api`.

Every one of those hooks has a default, so a module compiled against an older build keeps working. The [API Guide](https://eymistaken.github.io/Eymistaken-s-HUD-Website/api-guide.html) covers each of them with full examples.

![A plugin module's generated settings page, listed under Plugins](.github/media/plugin-page.png)

## How it fits together

```
src/main/java/com/eymistaken/simplecps/
├── api/                    Public plugin API: HudModule, setting types, events, HudPreview
├── compat/                 The calls that differ between Minecraft versions, kept in one place
├── gui/                    In-game editor, keystrokes designer, color picker
│   └── settings/           Settings screen and the shared screen toolkit
├── mixin/                  Client-only hooks: input, client tick, attacks, combat packets
├── modules/                The seven built-in modules and the keystrokes renderer
├── util/                   Share codes, fonts, input-code translation, easing
├── SimpleCPSClient         Client initializer
├── HudModuleManager        Module registry; ticks and renders every module
├── HudPlacementResolver    Layout engine
└── SimpleCPSConfig         The single persisted config
```

- **`SimpleCPSClient`** reads the config, builds the module registry, discovers plugins through the `eymistaken_hud` entrypoint, registers the editor key, and attaches the HUD layer beneath chat.
- **`HudPlacementResolver`** is a pure function from modules and screen size to rectangles. It anchors each module to a corner or the center, stacks modules that share an edge, and, with Prevent Overlap on, moves colliding elements to the nearest free space. A module with an explicit offset is placed exactly and never stacked.
- **`SimpleCPSConfig`** is one flat object serialized with Gson to `config/simplecps.json` through an atomic write. Its public field names are the keys on disk, so renaming one silently drops that setting from every existing config.
- **Share codes** are `EYMHUD1-` followed by base64url-encoded, gzipped JSON in a versioned envelope. A code carries either the whole config or the whole keystrokes look, and applying the wrong kind is rejected rather than half-applied. Decoding is size-bounded, because the input comes from someone else.
- **Mixins** are client-only and limited to reading input, ticking modules, recording attacks, and listening to the packets that confirm hits and swings. Nothing in gameplay logic is changed.

The internal id is `simplecps` throughout (mod id, package, config file) while the published name is Eymistaken's HUD. The two differ on purpose; the id is kept because existing configs and plugins depend on it.

## Contributing

Bug reports and feature requests go to [Issues](https://github.com/Eymistaken/Eymistaken-s-HUD/issues). For a bug, include the mod version, the Minecraft and Fabric Loader versions, and the log if the game crashed.

There is no automated test suite; changes are verified by running the development client and exercising the HUD in game. Code, comments and commit messages use American English.

## License

Released under the [MIT License](LICENCE).
