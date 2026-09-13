package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.ComboStats;
import com.eymistaken.simplecps.ComboTracker;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.ComboHeatmap;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.util.Easings;
import com.eymistaken.simplecps.util.EymHudFonts;
import com.eymistaken.simplecps.util.RenderUtil;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class ComboModule extends HudModule {

    /** Background padding around the text, in unscaled pixels. */
    private static final int PADDING = 2;
    /** Extra space between our row and the received row under it; the divider sits in it. */
    private static final int ROW_GAP = 4;
    private static final int DIVIDER_COLOR = 0x59FFFFFF;
    /** Divider length as a share of the wider row. */
    private static final float DIVIDER_LENGTH = 0.6f;
    /** Room under a lone row for each of its lines; the first sits where the divider does. */
    private static final int LINE_SPACE = 2;
    /** The timeout line takes the count's color, a little see-through. */
    private static final float TIMEOUT_LINE_ALPHA = 0.75f;
    private static final int SEGMENT_GAP = 1;
    private static final int SEGMENT_NORMAL = 0xFF9A9A9A;
    private static final int SEGMENT_SPRINT = 0xFFFFFFFF;
    /** A new hit's segment starts white and settles into its color over this. */
    private static final long SEGMENT_FLASH_MS = 150;
    /** A draining combo keeps its hits on show, dimmed. */
    private static final float DRAINING_ALPHA = 0.4f;

    /** How long a finished combo stays on screen before it goes. */
    static final long LINGER_MS = 1200;
    /** A finished combo lands at full strength first, and only then dims. */
    private static final long LINGER_BRIGHT_MS = 300;
    private static final long LINGER_DIM_MS = 250;
    private static final float LINGER_ALPHA = 0.45f;
    private static final long NOT_LINGERING = -1;
    /** A moment older than this happened while the counter was not being drawn; replaying it would be wrong. */
    private static final long STALE_MOMENT_MS = 250;
    /**
     * Impact scales how far a hit punches the count up directly, and how long it freezes more
     * gently, so that 150% lands exactly where the old STRONG preset did: a 1.5x punch held 1.3x
     * as long.
     */
    private static final double HOLD_EXPONENT = 0.65;

    /** The gold a new best flashes the count with and writes its tag in. */
    private static final int BEST_GOLD = 0xFFFFB800;
    private static final String BEST_TAG = "BEST";
    /** The tag's size against the count's, and its distance from the count in unscaled pixels. */
    private static final float BEST_TAG_SCALE = 0.5f;
    private static final int BEST_TAG_GAP = 2;

    // The format's placeholders.
    private static final String COMBO_TOKEN = "{combo}";
    private static final String BEST_TOKEN = "{best}";
    /** How many times its usual size the BIG style draws the count. */
    private static final int BIG_SCALE = 2;
    /** The label under a BIG count rises into the empty bottom of its digits by this much. */
    private static final int BIG_LABEL_RISE = 2;
    private static final int[] STILL = {0, 0};

    // The HUD editor's still sample of the line, whichever kind the config asks for.
    private static final float SAMPLE_TIME_LEFT = 0.7f;
    private static final List<ComboTracker.HitMark> SAMPLE_MARKS = List.of(
        new ComboTracker.HitMark(ComboTracker.HitKind.SPRINT, 0),
        new ComboTracker.HitMark(ComboTracker.HitKind.NORMAL, 0),
        new ComboTracker.HitMark(ComboTracker.HitKind.CRIT, 0),
        new ComboTracker.HitMark(ComboTracker.HitKind.NORMAL, 0),
        new ComboTracker.HitMark(ComboTracker.HitKind.SPRINT, 0));

    /**
     * What to draw: our own combo, the combo being landed on us, or both. When ours is gone the
     * received combo takes over its row, so the two only stack while ours is still alive. A
     * lingering row is a combo that already ended, shown dimmed on its way out; {@code lingerMs}
     * is how long it has lingered so far, or {@link #NOT_LINGERING}.
     */
    private record Rows(int own, int received, boolean ownShown, long lingerMs) {
        boolean receivedShown() {
            return received > 0;
        }

        boolean stacked() {
            return ownShown && receivedShown();
        }

        boolean empty() {
            return !ownShown && !receivedShown();
        }

        boolean lingering() {
            return lingerMs != NOT_LINGERING;
        }
    }

    private static final Rows EMPTY = new Rows(0, 0, false, NOT_LINGERING);

    /**
     * What one display of the counter animates from. The live HUD has one and the settings
     * preview's demo another: the preview is drawn over the running HUD, and sharing one would
     * step the same motion twice a frame.
     */
    private static final class Stage {
        final ComboFeel feel = new ComboFeel();
        // A combo a hit took apart stays frozen on screen for the length of its hitstop.
        int brokenValue = 0;
        long brokenUntil = 0;
        // A combo that ended any other way lingers from lingerStart.
        int lingerValue = 0;
        long lingerStart = 0;

        boolean lingering(long real) {
            return lingerValue > 0 && real - lingerStart < LINGER_MS;
        }
    }

    private final Stage live = new Stage();
    private final Stage preview = new Stage();
    private final ComboDemo demo = new ComboDemo();

    // The last rows drawn with a count on them. A fade-out runs after the counters have
    // already dropped to zero, and should fade on "12 Combo", not "0 Combo".
    private Rows lastRows = new Rows(0, 0, true, NOT_LINGERING);

    @Override
    public boolean supportsFade() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (!config.showCombo) return false;
        int min = config.comboMinDisplay;
        long real = ComboFeel.realNow();
        return !config.comboHideWhenInactive || ComboTracker.getCombo() >= min
            || ComboTracker.getReceivedCombo() >= min || real < live.brokenUntil || live.lingering(real);
    }

    @Override
    public SimpleCPSConfig.Position getPositionType() {
        return SimpleCPSConfig.instance.comboPosition;
    }

    @Override
    public int getXOffset() {
        return SimpleCPSConfig.instance.comboXOffset;
    }

    @Override
    public int getYOffset() {
        return SimpleCPSConfig.instance.comboYOffset;
    }

    @Override
    public String getName() {
        return "Combo";
    }

    @Override public void setPositionType(SimpleCPSConfig.Position pos) { SimpleCPSConfig.instance.comboPosition = pos; }
    @Override public void setXOffset(int x) { SimpleCPSConfig.instance.comboXOffset = x; }
    @Override public void setYOffset(int y) { SimpleCPSConfig.instance.comboYOffset = y; }
    @Override public void setScale(int scale) { SimpleCPSConfig.instance.comboScale = scale; }
    @Override public int getScale() { return SimpleCPSConfig.instance.comboScale; }
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.comboPosition = SimpleCPSConfig.Position.TOP_LEFT;
        SimpleCPSConfig.instance.comboXOffset = 0;
        SimpleCPSConfig.instance.comboYOffset = 0;
        SimpleCPSConfig.instance.comboScale = 100;
    }

    @Override
    public void resetVisualDefaults() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        config.comboScale = 100;
        config.comboColor = 0xFFFFFF;
        config.comboColorMode = SimpleCPSConfig.ComboColorMode.STATIC;
        config.comboFormat = SimpleCPSConfig.DEFAULT_COMBO_FORMAT;
        config.comboStyle = SimpleCPSConfig.ComboStyle.TEXT;
        config.comboShowBackground = false;
        config.comboBackgroundColor = 0x000000;
        config.comboBackgroundOpacity = 128;
        config.comboTimeout = 3.0;
        config.comboResetOnAnyDamage = true;
        config.comboContinueOnSwitch = true;
        config.comboHideWhenInactive = false;
        config.comboHeatmapMode = SimpleCPSConfig.HeatmapMode.MEDIUM;
        config.comboHeatTier1 = 5;
        config.comboHeatTier2 = 8;
        config.comboHeatTier3 = 12;
        config.comboHeatColorMid = 0xFF0000;
        config.comboHeatColorTop = 0x550000;
        config.comboOnlyPlayers = true;
        config.combatMode = SimpleCPSConfig.CombatMode.MODERN;
        config.comboHitDetection = SimpleCPSConfig.HitDetection.SERVER;
        config.comboDecay = false;
        config.comboMinDisplay = 2;
        config.comboImpactStrength = 100;
        config.comboShakeStrength = 100;
        config.comboJoltStrength = 100;
        config.comboLine = SimpleCPSConfig.ComboLine.TIMEOUT;
    }

    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>(super.getContextMenuSettings());
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Combo", () -> config.showCombo, v -> config.showCombo = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Always Show", () -> !config.comboHideWhenInactive, v -> config.comboHideWhenInactive = !v),
            cycle("Color Mode", SimpleCPSConfig.ComboColorMode.values(),
                config::resolvedComboColorMode, v -> config.comboColorMode = v),
            cycle("Style", SimpleCPSConfig.ComboStyle.values(), () -> config.comboStyle, v -> config.comboStyle = v),
            new com.eymistaken.simplecps.api.ColorSetting("Text Color", () -> config.comboColor, v -> config.comboColor = v),
            new com.eymistaken.simplecps.api.TextSetting("Format", config::resolvedComboFormat, v -> config.comboFormat = v)
        ));
        return settings;
    }

    /** A menu cycle over an enum. The menu counts in indices; a missing value reads as the first. */
    private static <E extends Enum<E>> com.eymistaken.simplecps.api.CycleSetting cycle(String name, E[] values,
            java.util.function.Supplier<E> get, java.util.function.Consumer<E> set) {
        List<String> names = java.util.Arrays.stream(values).map(Enum::name).toList();
        return new com.eymistaken.simplecps.api.CycleSetting(name, names,
            () -> get.get() == null ? 0 : get.get().ordinal(),
            index -> set.accept(values[Math.floorMod(index, values.length)]));
    }

    @Override
    public void tick(Minecraft client) {
        ComboTracker.onTick(client);
    }

    @Override
    public com.eymistaken.simplecps.api.HudPreview getPreview() {
        return com.eymistaken.simplecps.api.HudPreview.ofModule(this);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        ComboHeatmap heatmap = ComboHeatmap.of(config.comboHeatmapMode);
        long real = ComboFeel.realNow();
        Stage stage = stage();
        if (stage == live) {
            takeMoments(config, heatmap, real);
        } else if (stage == preview) {
            demo.advance(real, heatmap, canStack(config), timeoutMs(config),
                moment -> play(moment, config, heatmap, real, preview));
            clearLinger(preview, demo.own(), demo.received(), config);
        }

        Rows rows = rows(stage, config, heatmap, real);
        if (rows.empty()) return;
        // Without a stage there is no motion: the HUD editor's sample holds still to be placed.
        ComboFeel feel = stage == null ? null : stage.feel;
        if (feel != null) {
            if (stage == live && (rows.own() > 0 || rows.received() > 0)) lastRows = rows;
            int receivedHits = stage == live ? ComboTracker.getReceivedHits() : demo.receivedHits();
            feel.update(rows.stacked(), rows.received(), receivedHits, heatmap);
        }

        float scale = config.comboScale / 100f;
        float shake = config.comboShakeStrength / 100f;
        float jolt = config.comboJoltStrength / 100f;
        int padding = config.comboShowBackground ? PADDING : 0;
        // The preview holds the width of its whole demo so the rows keep their place while it
        // plays; everywhere else the content is exactly what is shown.
        int shownW = contentWidth(rows, config);
        int contentW = stage == preview ? demoWidth(config, heatmap) : shownW;
        int row = rowHeight(config);
        long clock = feel == null ? 0L : feel.now();

        context.pose().pushMatrix();
        context.pose().translate((float) x, (float) y);
        context.pose().scale(scale, scale);

        if (config.comboShowBackground) {
            int background = (config.comboBackgroundOpacity << 24) | (config.comboBackgroundColor & 0x00FFFFFF);
            int left = align(contentW, shownW);
            context.fill(left, 0, left + shownW + padding * 2, contentHeight(rows.stacked(), config) + padding * 2, col(background));
        }

        // Our row's color and fade; the line under it follows both.
        int color = rows.ownShown() ? ownColor(rows.own(), config, heatmap) : 0;
        float gold = feel == null ? 0f : feel.gold();
        if (gold > 0f) color = interpolateColor(color, BEST_GOLD, gold);
        float alpha = rows.lingering() ? lingerAlpha(rows.lingerMs()) : 1f;

        if (rows.ownShown()) {
            // A combo past the top tier shakes, and every hitstop freezes that shake mid-motion
            // along with the rest of the counter. A finished combo lingers still.
            float amplitude = feel == null || rows.lingering() ? 0f
                : ComboFeel.ownShake(rows.own(), heatmap) * shake * scale;
            int[] offset = ComboFeel.shakeOffset(amplitude, clock, ComboFeel.OWN_PHASE);
            float pulse = feel == null ? 1f : feel.pulse();
            float tag = feel == null ? 0f : feel.bestTag();
            drawRow(context, config, rows.own(), contentW, padding, padding, color, alpha, offset, pulse, tag);
        }

        drawLine(context, stage, rows, config, real, shownW, contentW, padding, padding + row + 1, color, alpha);

        if (rows.receivedShown()) {
            int receivedColor = feel == null ? ComboFeel.colorFor(rows.received(), heatmap) : feel.receivedColor();
            int top = padding + (rows.stacked() ? row + ROW_GAP : 0);
            int[] offset = feel == null ? STILL : feel.shake(rows.received(), heatmap, scale, shake, jolt);
            drawRow(context, config, -rows.received(), contentW, padding, top, receivedColor, 1f, offset, 1f, 0f);
        }

        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        ComboHeatmap heatmap = ComboHeatmap.of(config.comboHeatmapMode);
        Stage stage = stage();
        if (stage == preview) return scaled(demoWidth(config, heatmap), config);
        return scaled(contentWidth(rows(stage, config, heatmap, ComboFeel.realNow()), config), config);
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        Stage stage = stage();
        // The demo stacks and unstacks as it plays; its card keeps the room the stack needs.
        if (stage == preview) return scaled(contentHeight(canStack(config), config), config);
        ComboHeatmap heatmap = ComboHeatmap.of(config.comboHeatmapMode);
        return scaled(contentHeight(rows(stage, config, heatmap, ComboFeel.realNow()).stacked(), config), config);
    }

    /**
     * Reserves the received row whenever it can appear under ours, and otherwise the line under a
     * lone row, so the modules stacked on this edge do not jump every time a hit is taken. That
     * space is structural and set once, the same trade KeystrokesModule makes for its board.
     */
    @Override
    public int getLayoutHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        return scaled(contentHeight(canStack(config), config), config);
    }

    /**
     * Which display is being drawn: the settings preview's demo, the live HUD, or null for the
     * HUD editor, which shows a still sample so the module can be placed.
     */
    private Stage stage() {
        if (isPreviewing()) return preview;
        return client.gui.screen() instanceof HudEditorScreen ? null : live;
    }

    /** Plays what the tracker reported since the last frame. */
    private void takeMoments(SimpleCPSConfig config, ComboHeatmap heatmap, long real) {
        ComboTracker.Moment moment;
        while ((moment = ComboTracker.pollMoment()) != null) {
            // Only a new combo is ever at one hit, and a BEST tag still up was earned by the last one.
            if (moment.value() == 1) live.feel.forgetBest();
            if (real - moment.time() <= STALE_MOMENT_MS) play(moment, config, heatmap, real, live);
        }
        clearLinger(live, ComboTracker.getCombo(), ComboTracker.getReceivedCombo(), config);
    }

    /** A new combo, ours or theirs, takes the screen from one that is lingering. */
    private static void clearLinger(Stage stage, int own, int received, SimpleCPSConfig config) {
        int min = config.comboMinDisplay;
        if (own >= min || received >= min) stage.lingerValue = 0;
    }

    private static void play(ComboTracker.Moment moment, SimpleCPSConfig config, ComboHeatmap heatmap,
                             long real, Stage stage) {
        int value = moment.value();
        ComboFeel feel = stage.feel;
        float boost = config.comboImpactStrength / 100f;
        float hold = (float) Math.pow(boost, HOLD_EXPONENT);
        boolean still = boost <= 0f;

        switch (moment.kind()) {
            case HIT -> {
                // Past the top tier every hit lands with a short freeze on the shaking count,
                // longer the further the combo runs.
                int over = value - heatmap.tier3();
                if (!still) {
                    long holdMs = over > 0 ? Math.round(Math.min(90, 30 + 3 * over) * hold) : 0;
                    float peak = over > 0 ? Math.min(0.16f, 0.10f + 0.003f * over) : 0.08f;
                    feel.impact(peak * boost, holdMs, 140, 1);
                }
            }
            case TIER -> {
                if (!still) feel.impact(0.18f * boost, Math.round(60 * hold), 200, 2);
            }
            case BEST -> {
                // Lands a little harder than a tier and turns the count gold through its hold. The
                // flash and the tag stay with Impact at 0: they carry news, not just motion.
                if (!still) feel.impact(0.22f * boost, Math.round(90 * hold), 220, 3);
                feel.best();
            }
            case FINISHER -> {
                // Bigger combos land harder and hold longer.
                if (!still) {
                    float peak = Math.min(0.40f, 0.20f + 0.01f * value) * boost;
                    feel.impact(peak, Math.round(Math.min(160, 70 + 4 * value) * hold), 260, 5);
                }
            }
            case BROKEN -> {
                // Only a combo that reached a tier gets the freeze; a short one just gives way.
                if (!still && value >= heatmap.tier1()) {
                    long holdMs = Math.round(Math.min(120, 50 + 3 * value) * hold);
                    feel.impact(0f, holdMs, 1, 4);
                    stage.brokenValue = value;
                    stage.brokenUntil = real + Math.min(holdMs, ComboFeel.MAX_HOLD_MS);
                }
            }
            case ENDED -> {
                if (value >= config.comboMinDisplay) {
                    stage.lingerValue = value;
                    stage.lingerStart = real;
                }
            }
        }
    }

    /**
     * The rows {@code stage} shows: the tracker's counts on the live HUD, the demo's in the
     * settings preview, and a still sample in the HUD editor, which has no stage.
     */
    private Rows rows(Stage stage, SimpleCPSConfig config, ComboHeatmap heatmap, long real) {
        if (stage == null) {
            // Taken from the active heatmap rather than fixed numbers, so the sample lands in the
            // same tiers whatever the difficulty is: ours far enough in for the heatmap coloring
            // to show, and a received combo under it whenever the layout can stack at all.
            return new Rows(heatmap.tier2(), canStack(config) ? heatmap.tier1() : 0, true, NOT_LINGERING);
        }
        int min = config.comboMinDisplay;
        int own = stage == live ? ComboTracker.getCombo() : demo.own();
        int received = stage == live ? ComboTracker.getReceivedCombo() : demo.received();
        boolean receivedShown = received >= min;

        // A broken combo holds the screen for its hitstop; the received one waits it out.
        if (real < stage.brokenUntil) return new Rows(stage.brokenValue, 0, true, NOT_LINGERING);
        if (own >= min) return new Rows(own, receivedShown ? received : 0, true, NOT_LINGERING);
        if (receivedShown) return new Rows(own, received, false, NOT_LINGERING);
        if (stage.lingering(real)) return new Rows(stage.lingerValue, 0, true, real - stage.lingerStart);
        if (!config.comboHideWhenInactive) return new Rows(own, 0, true, NOT_LINGERING);
        // Fading out after everything is gone: keep drawing what was last on screen.
        return stage == live && renderAlpha < 1f ? lastRows : EMPTY;
    }

    /**
     * One row: its count, with the rest of the format beside it or, in the BIG style, under it. The
     * count shakes by {@code offset} and punches up by {@code pulse} from its middle, carrying the
     * BEST tag with it; a BIG row's label holds still under it, so it stays readable.
     */
    private void drawRow(GuiGraphicsExtractor context, SimpleCPSConfig config, int count, int contentW,
                         int padding, int top, int color, float alpha, int[] offset, float pulse, float tag) {
        boolean big = big(config);
        String text = big ? String.valueOf(count) : lineText(count, config);
        int size = big ? BIG_SCALE : 1;
        int width = textWidth(text) * size;
        int left = padding + align(contentW, width);

        context.pose().pushMatrix();
        shift(context, offset, config.comboScale / 100f);
        if (pulse != 1f) {
            // Grow from the middle of the number, not from its corner.
            float centerX = left + width / 2f;
            float centerY = top + client.font.lineHeight * size / 2f;
            context.pose().translate(centerX, centerY);
            context.pose().scale(pulse, pulse);
            context.pose().translate(-centerX, -centerY);
        }
        context.pose().pushMatrix();
        context.pose().translate((float) left, (float) top);
        context.pose().scale(size, size);
        drawEdgedText(context, text, 0, 0, color, alpha);
        context.pose().popMatrix();
        // Inside the count's transform, so the tag punches, shakes and freezes along with it.
        if (tag > 0f) drawBestTag(context, left, width, top, size, alpha * tag);
        context.pose().popMatrix();

        String label = big ? bigLabel(config) : "";
        if (!label.isEmpty()) {
            int labelTop = top + client.font.lineHeight * BIG_SCALE - BIG_LABEL_RISE;
            drawEdgedText(context, label, padding + align(contentW, textWidth(label)), labelTop, color, alpha);
        }
    }

    /** Full strength while the ending lands, then easing down to dim. */
    private static float lingerAlpha(long lingerMs) {
        float dim = Easings.clamp01((lingerMs - LINGER_BRIGHT_MS) / (float) LINGER_DIM_MS);
        return Easings.lerp(1f, LINGER_ALPHA, Easings.sineInOut(dim));
    }

    /**
     * The lines under our count, and the divider they turn into. A lone row of ours carries the
     * lines the Line setting names, the timeout line on top when there are both; once a combo
     * landed on us stacks under it, they become the short divider between the two rows, and turn
     * back when the rows part.
     */
    private void drawLine(GuiGraphicsExtractor context, Stage stage, Rows rows, SimpleCPSConfig config,
                          long real, int shownW, int contentW, int padding, int top, int color, float alpha) {
        // 0 while our row stands alone, 1 once the rows have stacked, eased in between.
        float divider = stage == null ? (rows.stacked() ? 1f : 0f) : stage.feel.divider();
        int dividerW = Math.round(shownW * DIVIDER_LENGTH);
        boolean ours = rows.ownShown() && rows.own() > 0;
        SimpleCPSConfig.ComboLine mode = config.comboLine;
        boolean timeout = mode == SimpleCPSConfig.ComboLine.TIMEOUT || mode == SimpleCPSConfig.ComboLine.BOTH;
        boolean hits = mode == SimpleCPSConfig.ComboLine.HITS || mode == SimpleCPSConfig.ComboLine.BOTH;

        if (hits && ours && divider < 1f) {
            float fade = alpha * (1f - divider) * (draining(stage) ? DRAINING_ALPHA : 1f);
            // Under the timeout line when there are both, a pixel apart.
            drawHits(context, stage, rows, real, shownW, contentW, padding, timeout ? top + LINE_SPACE : top, fade);
        }
        if (timeout && ours) {
            // Only a running combo has time left; one that lingers, drains or broke shows none.
            float timeLeft = rows.lingering() ? 0f : timeLeft(stage, config, real);
            int width = Math.round(Easings.lerp(shownW * timeLeft, dividerW, divider));
            int lineColor = mixArgb(RenderUtil.withAlpha(color, TIMEOUT_LINE_ALPHA), DIVIDER_COLOR, divider);
            fillLine(context, padding + align(contentW, width), top, width, lineColor);
            return;
        }
        int width = Math.round(dividerW * divider);
        fillLine(context, padding + align(contentW, width), top, width, DIVIDER_COLOR);
    }

    /**
     * A segment for each remembered hit: gray, white for a sprint hit, gold for a critical one,
     * newest at the right end. While there are fewer than the strip holds, they gather toward the
     * anchor's side.
     */
    private void drawHits(GuiGraphicsExtractor context, Stage stage, Rows rows, long real, int shownW,
                          int contentW, int padding, int top, float alpha) {
        // A lingering count shows the hits of the combo it ended, never the first of the next one.
        if (rows.lingering() && stage == live && ComboTracker.getCombo() > 0) return;
        Collection<ComboTracker.HitMark> marks = hitMarks(stage);
        int slots = ComboTracker.MAX_HIT_MARKS;
        int shown = Math.min(marks.size(), slots);
        if (shown == 0) return;

        int segmentW = Math.max(1, (shownW - SEGMENT_GAP * (slots - 1)) / slots);
        int stripW = segmentW * slots + SEGMENT_GAP * (slots - 1);
        int usedW = segmentW * shown + SEGMENT_GAP * (shown - 1);
        int x = padding + align(contentW, stripW) + align(stripW, usedW);
        int skip = marks.size() - shown;
        for (ComboTracker.HitMark mark : marks) {
            if (skip-- > 0) continue;
            int kindColor = switch (mark.kind()) {
                case SPRINT -> SEGMENT_SPRINT;
                case CRIT -> BEST_GOLD;
                case NORMAL -> SEGMENT_NORMAL;
            };
            // A new hit lands white and settles into its own color.
            float flash = Easings.clamp01(1f - (real - mark.time()) / (float) SEGMENT_FLASH_MS);
            fillLine(context, x, top, segmentW, RenderUtil.withAlpha(mixArgb(kindColor, 0xFFFFFFFF, flash), alpha));
            x += segmentW + SEGMENT_GAP;
        }
    }

    private void fillLine(GuiGraphicsExtractor context, int left, int top, int width, int color) {
        if (width > 0) context.fill(left, top, left + width, top + 1, col(color));
    }

    /** Share of the timeout left on the combo {@code stage} shows. */
    private float timeLeft(Stage stage, SimpleCPSConfig config, long real) {
        if (stage == null) return SAMPLE_TIME_LEFT;
        return stage == live ? ComboTracker.getTimeLeft() : demo.timeLeft(real, timeoutMs(config));
    }

    private Collection<ComboTracker.HitMark> hitMarks(Stage stage) {
        if (stage == null) return SAMPLE_MARKS;
        return stage == live ? ComboTracker.getHitMarks() : demo.hitMarks();
    }

    private boolean draining(Stage stage) {
        return stage == live && ComboTracker.isDraining();
    }

    private static long timeoutMs(SimpleCPSConfig config) {
        return (long) (config.comboTimeout * 1000);
    }

    /** Blends every channel, alpha included, where {@link #interpolateColor} keeps the result opaque. */
    private static int mixArgb(int from, int to, float t) {
        int a = Math.round(Easings.lerp((from >>> 24) & 0xFF, (to >>> 24) & 0xFF, t));
        int r = Math.round(Easings.lerp((from >> 16) & 0xFF, (to >> 16) & 0xFF, t));
        int g = Math.round(Easings.lerp((from >> 8) & 0xFF, (to >> 8) & 0xFF, t));
        int b = Math.round(Easings.lerp(from & 0xFF, to & 0xFF, t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Shakes are counted in GUI pixels, so they are undone from the scale they are applied inside of. */
    private static void shift(GuiGraphicsExtractor context, int[] shake, float scale) {
        if (shake[0] != 0 || shake[1] != 0) context.pose().translate(shake[0] / scale, shake[1] / scale);
    }

    /**
     * Whether a received combo can sit under a live combo of ours. With any damage breaking the
     * combo and no Decay, the first hit taken ends ours, so the received one always replaces it.
     */
    private static boolean canStack(SimpleCPSConfig config) {
        return Boolean.TRUE.equals(config.comboDecay) || !config.comboResetOnAnyDamage;
    }

    private static boolean big(SimpleCPSConfig config) {
        return config.comboStyle == SimpleCPSConfig.ComboStyle.BIG;
    }

    /**
     * The format the rows are written from. One that names no count gets it in front, so an edit to
     * the format can never lose the number.
     */
    private static String template(SimpleCPSConfig config) {
        String format = config.resolvedComboFormat();
        if (format.contains(COMBO_TOKEN)) return format;
        return format.isBlank() ? COMBO_TOKEN : COMBO_TOKEN + " " + format;
    }

    /** A row on one line: the format with {@code count} in it, negative for a combo landed on us. */
    private static String lineText(int count, SimpleCPSConfig config) {
        return fill(template(config), count);
    }

    /** What a BIG count carries under it: the rest of the format, trimmed, or nothing. */
    private static String bigLabel(SimpleCPSConfig config) {
        return fill(template(config).replace(COMBO_TOKEN, ""), 0).strip();
    }

    private static String fill(String template, int count) {
        return template.replace(COMBO_TOKEN, String.valueOf(count))
            .replace(BEST_TOKEN, String.valueOf(ComboStats.getBest()));
    }

    private int rowWidth(int count, SimpleCPSConfig config) {
        if (!big(config)) return textWidth(lineText(count, config));
        return Math.max(textWidth(String.valueOf(count)) * BIG_SCALE, textWidth(bigLabel(config)));
    }

    /** A line of text, or a BIG count with its label, when it has one, tucked under it. */
    private int rowHeight(SimpleCPSConfig config) {
        int line = client.font.lineHeight;
        if (!big(config)) return line;
        int count = line * BIG_SCALE - BIG_LABEL_RISE;
        return bigLabel(config).isEmpty() ? count : count + line;
    }

    private int contentWidth(Rows rows, SimpleCPSConfig config) {
        // Nothing on screen still measures as a count of 0: the slot stays reserved, so the
        // modules stacked around this one do not jump when it appears.
        if (rows.empty()) return rowWidth(0, config);
        int width = rows.ownShown() ? rowWidth(rows.own(), config) : 0;
        if (rows.receivedShown()) width = Math.max(width, rowWidth(-rows.received(), config));
        return width;
    }

    /** The widest the demo gets, so the preview card keeps one size while it plays. */
    private int demoWidth(SimpleCPSConfig config, ComboHeatmap heatmap) {
        return Math.max(rowWidth(ComboDemo.ownPeak(heatmap), config),
            rowWidth(-ComboDemo.receivedPeak(heatmap), config));
    }

    /** Two rows with the divider between them, or one row with room under it for its lines. */
    private int contentHeight(boolean stacked, SimpleCPSConfig config) {
        int row = rowHeight(config);
        if (stacked) return row * 2 + ROW_GAP;
        return row + LINE_SPACE * lineCount(config);
    }

    /** How many lines sit under a lone row: none, one, or the timeout line with the hits under it. */
    private static int lineCount(SimpleCPSConfig config) {
        if (config.comboLine == SimpleCPSConfig.ComboLine.OFF) return 0;
        return config.comboLine == SimpleCPSConfig.ComboLine.BOTH ? 2 : 1;
    }

    private static int scaled(int content, SimpleCPSConfig config) {
        int padding = config.comboShowBackground ? PADDING * 2 : 0;
        return (int) ((content + padding) * (config.comboScale / 100f));
    }

    /** Offset of something {@code width} wide inside the content, pushed toward the anchor's side. */
    private int align(int contentW, int width) {
        if (anchoredRight()) return contentW - width;
        return getPositionType() == SimpleCPSConfig.Position.CENTER ? (contentW - width) / 2 : 0;
    }

    private boolean anchoredRight() {
        SimpleCPSConfig.Position position = getPositionType();
        return position == SimpleCPSConfig.Position.TOP_RIGHT || position == SimpleCPSConfig.Position.BOTTOM_RIGHT;
    }

    /**
     * The tag a new best wears: a word half the count's size, level with its top, on the side away
     * from the anchor. It overflows the module rather than growing it, and that side keeps it on
     * screen; toward the anchor it would run off the edge the module is pinned to.
     */
    private void drawBestTag(GuiGraphicsExtractor context, int left, int width, int top, int size, float alpha) {
        float tagScale = BEST_TAG_SCALE * size;
        float tagWidth = textWidth(BEST_TAG) * tagScale;
        float tagX = anchoredRight() ? left - BEST_TAG_GAP - tagWidth : left + width + BEST_TAG_GAP;
        context.pose().pushMatrix();
        context.pose().translate(tagX, (float) top);
        context.pose().scale(tagScale, tagScale);
        drawEdgedText(context, BEST_TAG, 0, 0, BEST_GOLD, alpha);
        context.pose().popMatrix();
    }

    private int ownColor(int combo, SimpleCPSConfig config, ComboHeatmap heatmap) {
        int color = switch (config.resolvedComboColorMode()) {
            case STATIC -> config.comboColor;
            case RAINBOW -> getRainbowColor();
            case HEATMAP -> heatColor(combo, config, heatmap);
        };
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;
        return color;
    }

    /** The text color up to tier 1, turning into the mid color by tier 2 and the top color by tier 3. */
    private int heatColor(int combo, SimpleCPSConfig config, ComboHeatmap heatmap) {
        int t1 = heatmap.tier1();
        int t2 = heatmap.tier2();
        int t3 = heatmap.tier3();
        if (combo >= t2) {
            float progress = Math.min((float) (combo - t2) / (float) (t3 - t2), 1.0f);
            return interpolateColor(config.comboHeatColorMid, config.comboHeatColorTop, progress);
        }
        if (combo >= t1) {
            float progress = (float) (combo - t1) / (float) (t2 - t1);
            return interpolateColor(config.comboColor, config.comboHeatColorMid, progress);
        }
        return config.comboColor;
    }

    /**
     * Text whose edge follows its color, so a count that has darkened to near black still reads
     * against a dark world, and fades as a whole. Honors the global outline setting the way
     * {@link #drawText} does.
     */
    private void drawEdgedText(GuiGraphicsExtractor context, String text, int textX, int textY,
                               int color, float alpha) {
        Component component = EymHudFonts.text(text);
        boolean outline = SimpleCPSConfig.instance.textOutline;
        int shown = col(RenderUtil.withAlpha(color, alpha));
        int edge = col(RenderUtil.withAlpha(ComboFeel.edgeColor(color, outline), alpha));
        if (outline) {
            RenderUtil.outlinedText(context, client.font, component, textX, textY, shown, edge);
        } else {
            context.text(client.font, component, textX + 1, textY + 1, edge, false);
            context.text(client.font, component, textX, textY, shown, false);
        }
    }

    private int interpolateColor(int c1, int c2, float factor) {
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int r = (int)(r1 + (r2 - r1) * factor);
        int g = (int)(g1 + (g2 - g1) * factor);
        int b = (int)(b1 + (b2 - b1) * factor);

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
