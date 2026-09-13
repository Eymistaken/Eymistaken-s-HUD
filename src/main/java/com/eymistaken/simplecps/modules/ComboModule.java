package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.ComboTracker;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.ComboHeatmap;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.util.Easings;
import com.eymistaken.simplecps.util.EymHudFonts;
import com.eymistaken.simplecps.util.RenderUtil;
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

    /** How long a finished combo stays on screen before it goes. */
    private static final long LINGER_MS = 1200;
    /** A finished combo lands at full strength first, and only then dims. */
    private static final long LINGER_BRIGHT_MS = 300;
    private static final long LINGER_DIM_MS = 250;
    private static final float LINGER_ALPHA = 0.45f;
    /** A moment older than this happened while the counter was not being drawn; replaying it would be wrong. */
    private static final long STALE_MOMENT_MS = 250;

    /**
     * What to draw: our own combo, the combo being landed on us, or both. When ours is gone the
     * received combo takes over its row, so the two only stack while ours is still alive. A
     * lingering row is a combo that already ended, shown dimmed on its way out.
     */
    private record Rows(int own, int received, boolean ownShown, boolean lingering) {
        boolean receivedShown() {
            return received > 0;
        }

        boolean stacked() {
            return ownShown && receivedShown();
        }

        boolean empty() {
            return !ownShown && !receivedShown();
        }
    }

    private static final Rows EMPTY = new Rows(0, 0, false, false);

    private final ComboFeel feel = new ComboFeel();

    // The last rows drawn with a count on them. A fade-out runs after the counters have
    // already dropped to zero, and should fade on "12 Combo", not "0 Combo".
    private Rows lastRows = new Rows(0, 0, true, false);

    // A combo a hit took apart stays frozen on screen for the length of its hitstop.
    private int brokenValue = 0;
    private long brokenUntil = 0;
    // A combo that ended any other way lingers from lingerStart.
    private int lingerValue = 0;
    private long lingerStart = 0;

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
            || ComboTracker.getReceivedCombo() >= min || real < brokenUntil || lingering(real);
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
        config.comboRainbow = false;
        config.comboText = "Combo";
        config.comboShowBackground = false;
        config.comboBackgroundColor = 0x000000;
        config.comboBackgroundOpacity = 128;
        config.comboTimeout = 3.0;
        config.comboResetOnAnyDamage = true;
        config.comboContinueOnSwitch = true;
        config.comboHideWhenInactive = false;
        config.comboHeatmap = false;
        config.comboHeatmapMode = SimpleCPSConfig.HeatmapMode.MEDIUM;
        config.comboOnlyPlayers = true;
        config.combatMode = SimpleCPSConfig.CombatMode.MODERN;
        config.comboHitDetection = SimpleCPSConfig.HitDetection.SERVER;
        config.comboDecay = false;
        config.comboImpact = SimpleCPSConfig.Impact.SUBTLE;
        config.comboMinDisplay = 2;
    }

    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>(super.getContextMenuSettings());
        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Combo", () -> config.showCombo, v -> config.showCombo = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Always Show", () -> !config.comboHideWhenInactive, v -> config.comboHideWhenInactive = !v),
            new com.eymistaken.simplecps.api.ColorSetting("Text Color", () -> config.comboColor, v -> config.comboColor = v),
            new com.eymistaken.simplecps.api.TextSetting("Suffix Text", () -> config.comboText, v -> config.comboText = v)
        ));
        return settings;
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
        boolean live = !showsSample();
        if (live) takeMoments(config, heatmap);

        Rows rows = rows();
        if (rows.empty()) return;
        if (live) {
            if (rows.own() > 0 || rows.received() > 0) lastRows = rows;
            feel.update(rows.stacked(), rows.received(), ComboTracker.getReceivedHits(), heatmap);
        }

        float scale = config.comboScale / 100f;
        int padding = config.comboShowBackground ? PADDING : 0;
        int contentW = contentWidth(rows, config);
        int line = client.font.lineHeight;

        context.pose().pushMatrix();
        context.pose().translate((float) x, (float) y);
        context.pose().scale(scale, scale);

        if (config.comboShowBackground) {
            int background = (config.comboBackgroundOpacity << 24) | (config.comboBackgroundColor & 0x00FFFFFF);
            context.fill(0, 0, contentW + padding * 2, contentHeight(rows.stacked()) + padding * 2, col(background));
        }

        if (rows.ownShown()) {
            String text = ownText(rows.own(), config);
            int width = textWidth(text);
            int left = padding + align(contentW, width);
            float alpha = rows.lingering() ? lingerAlpha(ComboFeel.realNow()) : 1f;
            float pulse = live ? feel.pulse() : 1f;
            // A live combo past the top tier shakes, and every hitstop freezes that shake mid-motion
            // along with the rest of the counter. A finished combo lingers still.
            boolean shakes = live && !rows.lingering() && config.comboImpact != SimpleCPSConfig.Impact.OFF;
            float amplitude = shakes ? ComboFeel.ownShake(rows.own(), heatmap) * strength(config) * scale : 0f;

            context.pose().pushMatrix();
            shift(context, ComboFeel.shakeOffset(amplitude, feel.now(), ComboFeel.OWN_PHASE), scale);
            if (pulse != 1f) {
                // Grow from the middle of the number, not from its corner.
                float centerX = left + width / 2f;
                float centerY = padding + line / 2f;
                context.pose().translate(centerX, centerY);
                context.pose().scale(pulse, pulse);
                context.pose().translate(-centerX, -centerY);
            }
            drawEdgedText(context, text, left, padding, ownColor(rows.own(), config, heatmap), alpha);
            context.pose().popMatrix();
        }

        // Grows in as the rows stack and shrinks back once they no longer do.
        float divider = live ? feel.divider() : (rows.stacked() ? 1f : 0f);
        if (divider > 0f) {
            int width = Math.round(contentW * DIVIDER_LENGTH * divider);
            int left = padding + align(contentW, width);
            int top = padding + line + 1;
            context.fill(left, top, left + width, top + 1, col(DIVIDER_COLOR));
        }

        if (rows.receivedShown()) {
            String text = receivedText(rows.received(), config);
            int color;
            int[] shake;
            if (live) {
                color = feel.receivedColor();
                shake = feel.shake(rows.received(), heatmap, scale);
            } else {
                color = ComboFeel.colorFor(rows.received(), heatmap);
                // The editor holds still so the module can be placed; the preview shows the shake.
                float amplitude = isPreviewing() ? ComboFeel.growthShake(rows.received(), heatmap) * scale : 0f;
                shake = ComboFeel.shakeOffset(amplitude, feel.now(), 0.0);
            }
            int top = padding + (rows.stacked() ? line + ROW_GAP : 0);

            context.pose().pushMatrix();
            shift(context, shake, scale);
            drawEdgedText(context, text, padding + align(contentW, textWidth(text)), top, color, 1f);
            context.pose().popMatrix();
        }

        context.pose().popMatrix();
    }

    @Override
    public int getWidth() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        return scaled(contentWidth(rows(), config), config);
    }

    @Override
    public int getHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        return scaled(contentHeight(rows().stacked()), config);
    }

    /**
     * Reserves the received row whenever it can appear under ours, so the modules stacked on
     * this edge do not jump every time a hit is taken. That space is structural and set once,
     * the same trade KeystrokesModule makes for its board.
     */
    @Override
    public int getLayoutHeight() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        return scaled(contentHeight(canStack(config)), config);
    }

    /** Plays what the tracker reported since the last frame. */
    private void takeMoments(SimpleCPSConfig config, ComboHeatmap heatmap) {
        long real = ComboFeel.realNow();
        ComboTracker.Moment moment;
        while ((moment = ComboTracker.pollMoment()) != null) {
            if (real - moment.time() <= STALE_MOMENT_MS) play(moment, config, heatmap, real);
        }
        // A new combo, ours or theirs, takes the screen from one that is lingering.
        int min = config.comboMinDisplay;
        if (ComboTracker.getCombo() >= min || ComboTracker.getReceivedCombo() >= min) lingerValue = 0;
    }

    private void play(ComboTracker.Moment moment, SimpleCPSConfig config, ComboHeatmap heatmap, long real) {
        int value = moment.value();
        boolean still = config.comboImpact == SimpleCPSConfig.Impact.OFF;
        float boost = strength(config);
        float hold = config.comboImpact == SimpleCPSConfig.Impact.STRONG ? 1.3f : 1f;

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
            case FINISHER -> {
                // Bigger combos land harder and hold longer.
                if (!still) {
                    float peak = Math.min(0.40f, 0.20f + 0.01f * value) * boost;
                    feel.impact(peak, Math.round(Math.min(160, 70 + 4 * value) * hold), 260, 4);
                }
            }
            case BROKEN -> {
                // Only a combo that reached a tier gets the freeze; a short one just gives way.
                if (!still && value >= heatmap.tier1()) {
                    long holdMs = Math.round(Math.min(120, 50 + 3 * value) * hold);
                    feel.impact(0f, holdMs, 1, 3);
                    brokenValue = value;
                    brokenUntil = real + Math.min(holdMs, ComboFeel.MAX_HOLD_MS);
                }
            }
            case ENDED -> {
                if (value >= config.comboMinDisplay) {
                    lingerValue = value;
                    lingerStart = real;
                }
            }
        }
    }

    private Rows rows() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (showsSample()) {
            // Taken from the active heatmap rather than fixed numbers, so the sample lands in the
            // same tiers whatever the difficulty is: ours far enough in for the heatmap coloring
            // to show, and a received combo under it whenever the layout can stack at all.
            ComboHeatmap heatmap = ComboHeatmap.of(config.comboHeatmapMode);
            return new Rows(heatmap.tier2(), canStack(config) ? heatmap.tier1() : 0, true, false);
        }
        long real = ComboFeel.realNow();
        int min = config.comboMinDisplay;
        int own = ComboTracker.getCombo();
        int received = ComboTracker.getReceivedCombo();
        boolean receivedShown = received >= min;

        // A broken combo holds the screen for its hitstop; the received one waits it out.
        if (real < brokenUntil) return new Rows(brokenValue, 0, true, false);
        if (own >= min) return new Rows(own, receivedShown ? received : 0, true, false);
        if (receivedShown) return new Rows(own, received, false, false);
        if (lingering(real)) return new Rows(lingerValue, 0, true, true);
        if (!config.comboHideWhenInactive) return new Rows(own, 0, true, false);
        // Fading out after everything is gone: keep drawing what was last on screen.
        return renderAlpha < 1f ? lastRows : EMPTY;
    }

    private boolean lingering(long real) {
        return lingerValue > 0 && real - lingerStart < LINGER_MS;
    }

    /** Full strength while the ending lands, then easing down to dim. */
    private float lingerAlpha(long real) {
        float dim = Easings.clamp01((real - lingerStart - LINGER_BRIGHT_MS) / (float) LINGER_DIM_MS);
        return Easings.lerp(1f, LINGER_ALPHA, Easings.sineInOut(dim));
    }

    /** The settings preview and the HUD editor show sample numbers instead of the live ones. */
    private boolean showsSample() {
        return isPreviewing() || client.gui.screen() instanceof HudEditorScreen;
    }

    /** How much STRONG adds to the size of a punch and the reach of a shake. */
    private static float strength(SimpleCPSConfig config) {
        return config.comboImpact == SimpleCPSConfig.Impact.STRONG ? 1.5f : 1f;
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

    private static String ownText(int combo, SimpleCPSConfig config) {
        return combo + " " + config.comboText;
    }

    private static String receivedText(int received, SimpleCPSConfig config) {
        return "-" + received + " " + config.comboText;
    }

    private int contentWidth(Rows rows, SimpleCPSConfig config) {
        // Nothing on screen still measures as "0 Combo": the slot stays reserved, so the
        // modules stacked around this one do not jump when it appears.
        if (rows.empty()) return textWidth(ownText(0, config));
        int width = rows.ownShown() ? textWidth(ownText(rows.own(), config)) : 0;
        if (rows.receivedShown()) width = Math.max(width, textWidth(receivedText(rows.received(), config)));
        return width;
    }

    private int contentHeight(boolean stacked) {
        int line = client.font.lineHeight;
        return stacked ? line * 2 + ROW_GAP : line;
    }

    private static int scaled(int content, SimpleCPSConfig config) {
        int padding = config.comboShowBackground ? PADDING * 2 : 0;
        return (int) ((content + padding) * (config.comboScale / 100f));
    }

    /** Offset of something {@code width} wide inside the content, pushed toward the anchor's side. */
    private int align(int contentW, int width) {
        SimpleCPSConfig.Position position = getPositionType();
        if (position == SimpleCPSConfig.Position.TOP_RIGHT || position == SimpleCPSConfig.Position.BOTTOM_RIGHT) {
            return contentW - width;
        }
        return position == SimpleCPSConfig.Position.CENTER ? (contentW - width) / 2 : 0;
    }

    private int ownColor(int combo, SimpleCPSConfig config, ComboHeatmap heatmap) {
        int color = config.comboColor;
        if (config.comboHeatmap) {
            int t1 = heatmap.tier1();
            int t2 = heatmap.tier2();
            int t3 = heatmap.tier3();
            if (combo >= t2) {
                float progress = Math.min((float) (combo - t2) / (float) (t3 - t2), 1.0f);
                color = interpolateColor(0xFFFF0000, 0xFF550000, progress);
            } else if (combo >= t1) {
                float progress = (float) (combo - t1) / (float) (t2 - t1);
                color = interpolateColor(config.comboColor, 0xFFFF0000, progress);
            }
        } else if (config.comboRainbow) {
            color = getRainbowColor();
        }
        if ((color & 0xFF000000) == 0) color |= 0xFF000000;
        return color;
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
