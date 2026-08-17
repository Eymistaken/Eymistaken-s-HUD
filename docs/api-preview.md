# Live previews for plugin modules

Ready-to-adapt copy for the `api-guide.html` page in the `Eymistaken-s-HUD-Website` repo.
Added in **1.1.1-9**.

---

## A live sample on your settings page

The settings screen's right column leads with a **PREVIEW** card showing the selected
module as it will actually look, drawn at true screen size on a checkerboard. Change a
colour, a scale or a background opacity and the box updates the same frame, so the player
never has to close the menu to see what a setting did.

Your module does not get one by default. Override `getPreview()` to opt in; return `null`
— which is the default — and no card is drawn at all, with the INFO, PRESETS and SHARE
cards simply moving up to fill the space. Nothing appears empty or broken for a module
that has not opted in.

### The API

```java
package com.eymistaken.simplecps.api;

public interface HudPreview {
    /** Size in screen pixels — the same units as HudModule.getWidth(). */
    int width();
    int height();

    /** Draw with the top-left corner at (0,0). */
    void render(GuiGraphicsExtractor ctx, float tickDelta);

    /** A preview that is just the module drawing itself. */
    static HudPreview ofModule(HudModule module) { ... }
}
```

```java
// on HudModule
public HudPreview getPreview() {
    return null;
}

/** True for the whole of a preview pass, measurement included. */
public final boolean isPreviewing() { ... }
```

**The host owns the geometry.** It measures you, sizes the card, centres you in it, applies
the transform that puts one of your pixels on one real screen pixel, and clips you to the
box. So `render` draws from the origin in plain screen pixels and never translates or
scales itself.

### The usual case: the module drawing itself

Most previews are the module rendering as normal, with stand-in values for data the game is
not currently producing. `HudPreview.ofModule` wires that up for you — size from
`getWidth()`/`getHeight()`, drawing from `extractRenderState` — and `isPreviewing()` is how
you substitute the data:

```java
public class PotionHudModule extends HudModule {

    @Override
    public HudPreview getPreview() {
        return HudPreview.ofModule(this);
    }

    /** Sample effects, so the preview has something to show out of combat. */
    private static final List<MobEffectInstance> PREVIEW_EFFECTS = List.of(
        new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 87),
        new MobEffectInstance(MobEffects.STRENGTH, 20 * 214, 1));

    private List<MobEffectInstance> activeEffects() {
        if (isPreviewing()) return PREVIEW_EFFECTS;
        return client.player == null ? List.of() : List.copyOf(client.player.getActiveEffects());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, float tickDelta) {
        for (MobEffectInstance effect : activeEffects()) { ... }
    }

    @Override
    public int getWidth() {
        // Same helper, so the box is sized for what it actually draws.
        return measure(activeEffects());
    }
}
```

Note that `activeEffects()` feeds the render path *and* the measurement. That is the whole
trick, and the reason `isPreviewing()` is true during measurement too.

### When your preview is something else

If the preview should not be the module — a legend, a labelled before/after, a fixed
illustration — implement the interface directly and draw whatever you like:

```java
@Override
public HudPreview getPreview() {
    return new HudPreview() {
        @Override public int width()  { return 96; }
        @Override public int height() { return 40; }

        @Override
        public void render(GuiGraphicsExtractor ctx, float tickDelta) {
            ctx.fill(0, 0, 96, 40, 0x40FFFFFF);
            ctx.text(client.font, Component.literal("Water Breathing 1:27"), 2, 2, 0xFFFFFFFF);
            ctx.text(client.font, Component.literal("Strength II 3:34"), 2, 14, 0xFFFFFFFF);
        }
    };
}
```

### Rules worth knowing

- **Do not translate or scale.** The host has already positioned and scaled you. Drawing at
  anything other than the origin puts your module off-centre in the box, and scaling
  yourself defeats the point of the panel, which is showing true size.
- **Sizes are screen pixels**, exactly like `getWidth()` — scale already applied. Do not
  multiply by `getScale() / 100` a second time.
- **Measure what you draw.** `width()` and `height()` are called with `isPreviewing()` set,
  so sample data must be reflected there too. Report one size and draw another and the box
  will be wrong.
- **Wider than the box is clipped, not shrunk.** The card never widens; it grows downward
  only, and stops once the cards below it would be pushed off the panel. That is deliberate
  — the FOCUS button in the card's header is the way to see a module too big to fit, and it
  expands over the rest of the screen without moving anything.
- **Never touch state the live HUD animates from.** In-game the preview is drawn *in
  addition to* the real HUD, so any per-frame counter stepped inside your render path runs
  at double speed while the menu is open. Keep a preview-local copy, or leave the counter
  alone when `isPreviewing()`. The built-in keystrokes module keeps separate squish and
  ripple maps for exactly this reason.
- **Returning null means no card.** There is no empty state to design around; the panel is
  simply not there and the rest of the column moves up.
- **Throwing is survivable, not free.** A `getPreview()`, `width()`, `height()` or
  `render()` that throws costs you the preview for that frame and nothing else. The screen
  carries on.
- **`getPreview()` is called once per frame.** Keep it cheap, and prefer a cached instance
  to building one per call.
- **The preview runs with no world.** The settings screen opens from the main menu as well
  as in-game, so `client.player` and `client.level` may both be null. That is precisely
  what the sample data is for.

### Compatibility

Additive and `null` by default, so a module compiled against an older HUD build still runs:
the method is simply never called and no card appears. There is nothing to guard.

Going the other way — your plugin built against 1.1.1-9 running on an older HUD — the
override is dead code and costs nothing. If you want to know either way, probe for it the
same way the settings-tabs support is probed:

```java
static final boolean PREVIEW = hasMethod("getPreview");

private static boolean hasMethod(String name) {
    try {
        HudModule.class.getMethod(name);
        return true;
    } catch (NoSuchMethodException | RuntimeException e) {
        return false;
    }
}
```

Look it up on `HudModule` rather than on your own class: your module overrides the method
whatever the host supports, so only the superclass answers the question.
