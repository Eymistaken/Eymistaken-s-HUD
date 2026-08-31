package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.util.EymHudFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Draws the contents of one keystroke button — its label (or bar) and CPS readout.
 *
 * <p>Both the live HUD module and the designer's canvas call this. They used to each
 * carry their own copy of the centering, styling and scaling math, which is exactly
 * the kind of duplication that lets a designer preview drift away from what the
 * player actually sees in game.
 *
 * <p>Everything here draws in the button's own coordinate space: the caller has
 * already placed the origin so that the button occupies
 * {@code (btn.x, btn.y, btn.w, btn.h)}.
 */
public final class KeystrokesRender {

    private KeystrokesRender() {}

    /** Vertical room made for the CPS readout under a mouse button's label. */
    private static final int CPS_LABEL_LIFT = -4;

    /** Label scale as a factor, guarded against a hand-edited config reaching us. */
    public static float scaleFactor(SimpleCPSConfig.KeyButtonData btn) {
        int percent = Math.max(SimpleCPSConfig.MIN_LABEL_SCALE,
            Math.min(SimpleCPSConfig.MAX_LABEL_SCALE, btn.labelScale));
        return percent / 100f;
    }

    /** The styled label component: the active HUD font plus this key's bold/italic/underline. */
    public static MutableComponent labelText(SimpleCPSConfig.KeyButtonData btn) {
        return labelText(btn, null);
    }

    /**
     * As {@link #labelText(SimpleCPSConfig.KeyButtonData)}, but drawing {@code override}
     * in place of the key's own label. The designer needs this to show "..." while a
     * key is waiting for a new binding, and writing that into the config for one frame
     * would be an edit the undo stack never saw.
     */
    public static MutableComponent labelText(SimpleCPSConfig.KeyButtonData btn, String override) {
        String value = override != null ? override : btn.label;
        MutableComponent text = Component.literal(value == null ? "" : value);
        Style style = EymHudFonts.activeStyle();
        if (btn.bold) style = style.withBold(true);
        if (btn.italic) style = style.withItalic(true);
        if (btn.underlined) style = style.withUnderlined(true);
        text.setStyle(style);
        return text;
    }

    /**
     * Bar length in line mode: the key's inner width (the mock's
     * {@code max(6, (w - 6) * 2)} in game pixels) scaled by the key's own percentage.
     */
    public static int lineWidth(SimpleCPSConfig.KeyButtonData btn) {
        int full = Math.max(3, btn.w - 6);
        int percent = Math.max(SimpleCPSConfig.MIN_LINE_WIDTH,
            Math.min(SimpleCPSConfig.MAX_LINE_WIDTH, btn.lineWidthPercent));
        return Math.max(1, Math.round(full * percent / 100f));
    }

    /** Bar thickness in line mode; the label scale doubles as the weight control. */
    public static int lineHeight(SimpleCPSConfig.KeyButtonData btn) {
        return Math.max(1, Math.round(2 * scaleFactor(btn)));
    }

    /**
     * Size the label occupies once scaled, as {@code {width, height}}. Line mode is
     * already sized in final pixels, so the scale is not applied to it twice.
     */
    public static float[] labelSize(Font font, SimpleCPSConfig.KeyButtonData btn) {
        return labelSize(font, btn, null);
    }

    public static float[] labelSize(Font font, SimpleCPSConfig.KeyButtonData btn, String override) {
        if (btn.labelLine) {
            return new float[] { lineWidth(btn), lineHeight(btn) };
        }
        float s = scaleFactor(btn);
        return new float[] { font.width(labelText(btn, override)) * s, font.lineHeight * s };
    }

    /**
     * Top-left corner the label is drawn at, in the button's coordinate space.
     * {@code labelX}/{@code labelY} of -1 mean "centered on that axis".
     */
    public static float[] labelOrigin(Font font, SimpleCPSConfig.KeyButtonData btn) {
        return labelOrigin(font, btn, null);
    }

    public static float[] labelOrigin(Font font, SimpleCPSConfig.KeyButtonData btn, String override) {
        float[] size = labelSize(font, btn, override);
        // A mouse button showing CPS lifts its label to make room for the number.
        int lift = (btn.showCps && btn.isMouse) ? CPS_LABEL_LIFT : 0;

        float lx = btn.labelX == -1 ? btn.x + (btn.w - size[0]) / 2f : btn.x + btn.labelX;
        float ly = btn.labelY == -1 ? btn.y + (btn.h - size[1]) / 2f + lift : btn.y + btn.labelY;
        return new float[] { lx, ly };
    }

    /** Vanilla's text-shadow tone: the color at a quarter brightness, alpha kept. */
    private static int shadowColor(int color) {
        return ((color & 0xFCFCFC) >> 2) | (color & 0xFF000000);
    }

    /** Draw the label, or the bar when the key is in line mode. */
    public static void drawLabel(GuiGraphicsExtractor ctx, Font font,
                                 SimpleCPSConfig.KeyButtonData btn, int textColor) {
        drawLabel(ctx, font, btn, textColor, null);
    }

    public static void drawLabel(GuiGraphicsExtractor ctx, Font font,
                                 SimpleCPSConfig.KeyButtonData btn, int textColor, String override) {
        float[] origin = labelOrigin(font, btn, override);
        int lx = Math.round(origin[0]);
        int ly = Math.round(origin[1]);

        if (btn.labelLine) {
            int w = lineWidth(btn);
            int h = lineHeight(btn);
            if (btn.shadow) {
                ctx.fill(lx + 1, ly + 1, lx + w + 1, ly + h + 1, shadowColor(textColor));
            }
            ctx.fill(lx, ly, lx + w, ly + h, textColor);
            return;
        }

        MutableComponent text = labelText(btn, override);
        float s = scaleFactor(btn);
        if (s == 1f) {
            ctx.text(font, text, lx, ly, textColor, btn.shadow);
            return;
        }
        // Scaling around the label's own top-left keeps labelX/labelY meaning the same
        // thing at every size.
        ctx.pose().pushMatrix();
        ctx.pose().translate(lx, ly);
        ctx.pose().scale(s, s);
        ctx.text(font, text, 0, 0, textColor, btn.shadow);
        ctx.pose().popMatrix();
    }

    /**
     * The look and feel one button ends up with, after the per-key overrides have
     * been folded onto the module's settings.
     *
     * <p>Resolving this in one place matters: the HUD, the designer canvas and the
     * design gallery all need the same answer, and "null means use the global" is
     * exactly the kind of rule that rots when it is written three times.
     */
    public record KeyStyle(KeystrokesDesign design,
                        java.util.List<KeystrokesAnim.Motion> motions,
                        java.util.List<KeystrokesAnim.Fill> fills,
                        KeystrokesAnim.Direction direction,
                        boolean animated) {

        public static KeyStyle of(SimpleCPSConfig config, SimpleCPSConfig.KeyButtonData btn) {
            KeystrokesDesign design = btn.design != null ? btn.design : config.keystrokesDesign;
            if (design == null) design = KeystrokesDesign.BASELINE;

            // A per-key override names one animation and means only that one, rather
            // than adding to the module's set — otherwise there would be no way for a
            // key to opt out of an effect its neighbours have.
            java.util.List<KeystrokesAnim.Motion> motions = btn.motion != null
                ? java.util.List.of(btn.motion)
                : KeystrokesAnim.cleanMotions(config.keystrokesMotions);
            java.util.List<KeystrokesAnim.Fill> fills = btn.fill != null
                ? java.util.List.of(btn.fill)
                : KeystrokesAnim.cleanFills(config.keystrokesFills);

            KeystrokesAnim.Direction dir = btn.direction != null
                ? btn.direction : config.keystrokesFillDirection;
            // Coerce rather than trust: a per-key override and the module's fills can
            // be edited independently, so the pairing is only valid once combined.
            dir = KeystrokesAnim.coerceAll(fills, dir);

            boolean animated = btn.animationEnabled == null || btn.animationEnabled;
            return new KeyStyle(design, motions, fills, dir, animated);
        }

        /** A style built straight from a design's own presets, for previews. */
        public static KeyStyle preset(KeystrokesDesign design) {
            java.util.List<KeystrokesAnim.Motion> motions =
                KeystrokesAnim.cleanMotions(java.util.List.of(design.defaultMotion()));
            java.util.List<KeystrokesAnim.Fill> fills =
                KeystrokesAnim.cleanFills(java.util.List.of(design.defaultFill()));
            return new KeyStyle(design, motions, fills,
                KeystrokesAnim.coerceAll(fills, design.defaultDirection()), true);
        }
    }

    /**
     * Draw one whole button: trail, body, press fill, label and CPS.
     *
     * <p>This is the single path the live HUD and the designer's canvas share. They
     * used to each carry their own copy of the drawing, which is what lets a "what
     * you see is what you get" screen quietly stop being that.
     *
     * @param accent  the pressed colour, which a design may put on the body or the text
     * @param idle    the resting label colour
     * @param cps     the click counter to draw, or null to skip it
     */
    public static void drawKey(GuiGraphicsExtractor ctx, Font font,
                               SimpleCPSConfig.KeyButtonData btn, KeyStyle style,
                               KeystrokesAnim.State state, int bgColor,
                               int idle, int accent, boolean ghostOn,
                               String cps, String labelOverride) {
        boolean pressed = state.press > 0.5f;

        // The trail is a leftover of the previous press, so it sits behind the key
        // and outside the press transform - it must not squish with it.
        if (ghostOn) KeystrokesAnim.drawGhost(ctx, state, btn, accent);

        int[] shift = KeystrokesAnim.offset(state, style.motions(), btn);
        boolean scales = style.motions().contains(KeystrokesAnim.Motion.SQUISH)
            || style.motions().contains(KeystrokesAnim.Motion.KICK);
        float scale = scales ? state.scale : 1f;

        ctx.pose().pushMatrix();
        if (shift[0] != 0 || shift[1] != 0) ctx.pose().translate(shift[0], shift[1]);
        if (scale != 1f) {
            float cx = btn.x + btn.w / 2f;
            float cy = btn.y + btn.h / 2f;
            ctx.pose().translate(cx, cy);
            ctx.pose().scale(scale, scale);
            ctx.pose().translate(-cx, -cy);
        }

        style.design().drawBody(ctx, btn, bgColor, accent, pressed);
        KeystrokesAnim.drawFill(ctx, state, style.fills(), style.direction(),
            style.design(), btn, accent);

        // A design that floods its body has to move the label off the accent, or the
        // letter vanishes into the colour that was meant to highlight it.
        int labelColor = pressed ? style.design().labelOn(accent) : idle;
        drawLabel(ctx, font, btn, labelColor, labelOverride);
        if (cps != null) drawCps(ctx, font, btn, labelColor, cps);

        ctx.pose().popMatrix();
    }

    /** Draw the small CPS number at the bottom of a mouse button. */
    public static void drawCps(GuiGraphicsExtractor ctx, Font font,
                               SimpleCPSConfig.KeyButtonData btn, int textColor, String cps) {
        if (!btn.showCps || !btn.isMouse) return;

        float small = 0.6f;
        int width = font.width(EymHudFonts.text(cps));
        float cx = btn.x + (btn.w - width * small) / 2f;
        float cy = btn.y + btn.h - font.lineHeight * small - 1;

        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(small, small);
        // Always shadowed, as it was before this drawing moved out of the module:
        // the number is small enough that dropping the shadow makes it hard to read.
        ctx.text(font, EymHudFonts.text(cps), 0, 0, textColor);
        ctx.pose().popMatrix();
    }
}
