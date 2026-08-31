package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import org.lwjgl.glfw.GLFW;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class KeystrokesModule extends HudModule {

    public static final Set<Integer> pressedKeys = new HashSet<>();

    // Animations are stepped once per draw with no delta time, so the preview cannot
    // share their state: the settings screen renders over a live HUD, and both passes
    // stepping the same store would run the real keystrokes at double speed.
    private final KeystrokesAnim.Store animStore = new KeystrokesAnim.Store();
    private final KeystrokesAnim.Store previewAnimStore = new KeystrokesAnim.Store();
    private final KeystrokesTimeline.Store timelineStore = new KeystrokesTimeline.Store();
    private final KeystrokesTimeline.Store previewTimelineStore = new KeystrokesTimeline.Store();

    private KeystrokesAnim.Store anim() {
        return isPreviewing() ? previewAnimStore : animStore;
    }

    private KeystrokesTimeline.Store timeline() {
        return isPreviewing() ? previewTimelineStore : timelineStore;
    }

    /** Whether this button's physical key or mouse button is currently down. */
    public static boolean isDown(SimpleCPSConfig.KeyButtonData btn) {
        return pressedKeys.contains(btn.keyCode + (btn.isMouse ? 1000 : 0));
    }

    @Override
    public com.eymistaken.simplecps.api.HudPreview getPreview() {
        return com.eymistaken.simplecps.api.HudPreview.ofModule(this);
    }

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.showKeystrokes;
    }

    @Override
    public SimpleCPSConfig.Position getPositionType() {
        return SimpleCPSConfig.instance.keystrokesPosition;
    }

    @Override
    public int getXOffset() {
        return SimpleCPSConfig.instance.keystrokesXOffset;
    }

    @Override
    public int getYOffset() {
        return SimpleCPSConfig.instance.keystrokesYOffset;
    }

    @Override
    public String getName() {
        return "Keystrokes";
    }

    @Override public void setPositionType(SimpleCPSConfig.Position pos) { SimpleCPSConfig.instance.keystrokesPosition = pos; }
    @Override public void setXOffset(int x) { SimpleCPSConfig.instance.keystrokesXOffset = x; }
    @Override public void setYOffset(int y) { SimpleCPSConfig.instance.keystrokesYOffset = y; }
    @Override public void setScale(int scale) { SimpleCPSConfig.instance.keystrokesScale = scale; }
    @Override public int getScale() { return SimpleCPSConfig.instance.keystrokesScale; }
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.keystrokesPosition = SimpleCPSConfig.Position.TOP_LEFT;
        SimpleCPSConfig.instance.keystrokesXOffset = 0;
        SimpleCPSConfig.instance.keystrokesYOffset = 0;
        SimpleCPSConfig.instance.keystrokesScale = 80;
    }

    @Override
    public void resetVisualDefaults() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        config.keystrokesScale = 80;
        config.keystrokesRainbow = false;
        config.keystrokesRainbowTarget = SimpleCPSConfig.RainbowTarget.TEXT;
        config.keystrokesColor = 0xFFFFFF;
        config.keystrokesPressedColor = 0x00FF00;
        config.keystrokesBackgroundColor = 0x000000;
        config.keystrokesBackgroundOpacity = 128;
        config.keystrokesDesign = KeystrokesDesign.BASELINE;
        // Squish with no fill, which is what a fresh install has always looked like
        // (the old keystrokesEffectMode defaulted to 1). A visual reset that landed
        // somewhere the mod never shipped would be a surprise, not a reset.
        config.keystrokesMotions = KeystrokesAnim.cleanMotions(
            java.util.List.of(KeystrokesAnim.Motion.SQUISH));
        config.keystrokesFills = KeystrokesAnim.cleanFills(java.util.List.of());
        config.keystrokesFillDirection = KeystrokesAnim.Direction.RIGHT;
        config.keystrokesGhost = false;
        config.keystrokesBoard = false;
        // Per-key overrides are part of the look too, so a visual reset that left
        // them behind would still show a neon key in an otherwise plain layout.
        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
            btn.design = null;
            btn.motion = null;
            btn.fill = null;
            btn.direction = null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float kScale = config.keystrokesScale / 100f;
        
        // Stacking/Positioning logic handled by SimpleCPSClient using getWidth/getHeight and setRenderPosition
        // But getWidth/getHeight for Keystrokes is complex (dynamic layout).
        
        renderKeystrokes(context, client, config, x, y, kScale);
    }
    
    /** Size the module claims when nothing is visible, so it stays grabbable in the editor. */
    private static final int EMPTY_EXTENT = 64;

    /** The design in force module-wide, never null. */
    private static KeystrokesDesign design(SimpleCPSConfig config) {
        return config.keystrokesDesign == null
            ? KeystrokesDesign.BASELINE : config.keystrokesDesign;
    }

    /**
     * Room the module reserves around its keys.
     *
     * <p>Only the board counts. A neon halo, a release trail and a pixel bevel's
     * outline all paint a few pixels past their key, but reserving space for them
     * would change the module's size every time the design changed — so every other
     * module on that edge would jump about while you were browsing designs. A soft
     * halo overlapping a neighbour by four pixels is far less disruptive than the
     * whole HUD reflowing, so the decoration is simply allowed to bleed.
     *
     * <p>The board is different: it is a deliberate frame around the whole cluster,
     * it is large, and it is toggled once rather than browsed. It gets its space.
     */
    private static int reserved(SimpleCPSConfig config) {
        return config.keystrokesBoard ? KeystrokesDesign.BOARD_PADDING : 0;
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float kScale = config.keystrokesScale / 100f;
        if (design(config) == KeystrokesDesign.TIMELINE) {
            return (int)(KeystrokesTimeline.width() * kScale);
        }
        // Hidden keys are excluded here as well as from the render: counting them
        // would reserve invisible space and push the auto-stacked modules around it.
        int maxX = Integer.MIN_VALUE;
        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
            if (btn.hidden) continue;
            maxX = Math.max(maxX, btn.x + btn.w);
        }
        if (maxX == Integer.MIN_VALUE) maxX = EMPTY_EXTENT;
        return (int)((maxX + reserved(config) * 2) * kScale);
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        float kScale = config.keystrokesScale / 100f;
        if (design(config) == KeystrokesDesign.TIMELINE) {
            return (int)(KeystrokesTimeline.height(config.keystrokesLayout) * kScale);
        }
        int maxY = Integer.MIN_VALUE;
        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
            if (btn.hidden) continue;
            maxY = Math.max(maxY, btn.y + btn.h);
        }
        if (maxY == Integer.MIN_VALUE) maxY = EMPTY_EXTENT;
        return (int)((maxY + reserved(config) * 2) * kScale);
    }

    private void renderKeystrokes(GuiGraphicsExtractor drawContext, Minecraft client, SimpleCPSConfig config, int x, int y, float scale) {
        int normalColor = config.keystrokesColor;
        if ((normalColor & 0xFF000000) == 0) normalColor |= 0xFF000000;

        int pressedColor = config.keystrokesPressedColor;
        if ((pressedColor & 0xFF000000) == 0) pressedColor |= 0xFF000000;

        int baseBgColor = (config.keystrokesBackgroundOpacity << 24) | (config.keystrokesBackgroundColor & 0x00FFFFFF);

        if (config.keystrokesRainbow) {
            int rainbow = getRainbowColor();
            if (config.keystrokesRainbowTarget == SimpleCPSConfig.RainbowTarget.TEXT) {
                normalColor = rainbow;
                pressedColor = rainbow;
            } else {
                baseBgColor = (config.keystrokesBackgroundOpacity << 24) | (rainbow & 0x00FFFFFF);
            }
        }

        drawContext.pose().pushMatrix();
        drawContext.pose().translate(x, y);
        drawContext.pose().scale(scale, scale);

        // No key is faked as held. Forcing one showed the pressed color, but which key got
        // it was arbitrary — the layout is user-defined — and it left a single key wearing
        // a background none of its neighbours had, which read as a rendering fault rather
        // than a pressed state. Real presses still register while the menu is open.

        if (design(config) == KeystrokesDesign.TIMELINE) {
            KeystrokesTimeline.update(timeline(), config.keystrokesLayout, KeystrokesModule::isDown);
            KeystrokesTimeline.draw(drawContext, client.font, timeline(),
                config.keystrokesLayout, normalColor, pressedColor);
            drawContext.pose().popMatrix();
            return;
        }

        // Only the board's reserved room shifts the cluster. Decoration that spills
        // past a key is drawn where it falls; see reserved().
        int pad = reserved(config);
        if (pad > 0) drawContext.pose().translate(pad, pad);

        if (config.keystrokesBoard) drawBoard(drawContext, config);

        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
            if (btn.hidden) continue;

            KeystrokesRender.KeyStyle style = KeystrokesRender.KeyStyle.of(config, btn);
            KeystrokesAnim.State state = anim().get(btn);
            boolean isPressed = isDown(btn);

            KeystrokesAnim.step(state, style.motions(), style.fills(), isPressed,
                style.animated(), config.keystrokesGhost, btn);

            int idle = btn.btnColor != -1 && btn.btnColor != 0 ? btn.btnColor : normalColor;
            int accent = btn.btnPressedColor != -1 && btn.btnPressedColor != 0
                ? btn.btnPressedColor : pressedColor;

            String cps = null;
            if (btn.showCps && btn.isMouse) {
                if (btn.keyCode == 0) cps = String.valueOf(CpsModule.leftClicks.size());
                else if (btn.keyCode == 1) cps = String.valueOf(CpsModule.rightClicks.size());
            }

            KeystrokesRender.drawKey(drawContext, client.font, btn, style, state,
                baseBgColor, idle, accent, config.keystrokesGhost, cps, null);
        }

        drawContext.pose().popMatrix();
    }

    /** Frame the whole visible cluster with the ornamented board. */
    private static void drawBoard(GuiGraphicsExtractor ctx, SimpleCPSConfig config) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
            if (btn.hidden) continue;
            minX = Math.min(minX, btn.x);
            minY = Math.min(minY, btn.y);
            maxX = Math.max(maxX, btn.x + btn.w);
            maxY = Math.max(maxY, btn.y + btn.h);
        }
        if (maxX == Integer.MIN_VALUE) return;
        KeystrokesDesign.drawBoard(ctx, minX, minY, maxX - minX, maxY - minY);
    }

    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>();
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Keystrokes", () -> config.showKeystrokes, v -> config.showKeystrokes = v),
            // applyTo, not a bare assignment: a design carries its arrangement,
            // palette and animation, and setting only the field left the keys sitting
            // in the previous design's layout wearing the new one's texture.
            cycle("Design", KeystrokesDesign.values(),
                d -> d.display(),
                () -> config.keystrokesDesign,
                v -> v.applyTo(config)),
            // Animations are not here on purpose. Several can run at once now, and a
            // set is not something a cycle setting can express — the designer's
            // animation drawer owns them.
            new com.eymistaken.simplecps.api.BooleanSetting("Board",
                () -> config.keystrokesBoard,
                v -> { config.keystrokesBoard = v; SimpleCPSConfig.save(); }),
            new com.eymistaken.simplecps.api.ActionSetting("Open Designer", () -> {
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                client.execute(() -> client.gui.setScreen(new com.eymistaken.simplecps.gui.KeystrokesDesignerScreen(client.gui.screen())));
            })
        ));
        return settings;
    }

    /**
     * A {@code CycleSetting} over an enum's values. The editor's cycle setting works
     * in indices, so every one of these would otherwise repeat the same
     * index-to-constant conversion and the same "unknown value means the first one"
     * guard.
     */
    private static <E extends Enum<E>> com.eymistaken.simplecps.api.CycleSetting cycle(
            String name, E[] values, java.util.function.Function<E, String> label,
            java.util.function.Supplier<E> get, java.util.function.Consumer<E> set) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (E value : values) names.add(label.apply(value));
        return new com.eymistaken.simplecps.api.CycleSetting(name, names,
            () -> {
                E current = get.get();
                for (int i = 0; i < values.length; i++) {
                    if (values[i] == current) return i;
                }
                return 0;
            },
            index -> {
                set.accept(values[Math.floorMod(index, values.length)]);
                SimpleCPSConfig.save();
            });
    }
}
