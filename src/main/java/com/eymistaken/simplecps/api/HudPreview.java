package com.eymistaken.simplecps.api;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A live sample of what a module looks like, drawn in the settings screen's PREVIEW card
 * while its page is open.
 *
 * <p>Top-level rather than nested inside {@link HudModule} to match the rest of this
 * package, where every API type — {@link HudModuleSetting} and each of its subtypes,
 * {@link SettingsTab}, {@link IHudElement} — lives in its own file.
 *
 * <p><b>The host owns the geometry.</b> It measures you with {@link #width()} and
 * {@link #height()}, sizes the card, centres you in it, applies the transform that makes
 * one of your pixels one real screen pixel, and clips you to the card. So
 * {@link #render} draws from {@code (0,0)} in plain screen pixels and does not translate
 * or scale itself. Anything wider than the card is clipped rather than shrunk — that is
 * deliberate, because the whole point is seeing the module at the size it will really be;
 * the card's FOCUS button is the escape hatch for a module too big to fit.
 *
 * <p>Everything runs under {@link HudModule#isPreviewing()}, measurement included, so a
 * module can substitute sample data from inside its normal render path and have its
 * reported size agree with what it draws.
 *
 * <p>A preview must not disturb state the real HUD animates from. It is drawn on top of a
 * running HUD whenever the settings screen is opened in-game, so a per-frame counter
 * stepped by both runs at double speed. Keep preview animation state separate, or leave it
 * alone while {@code isPreviewing()}.
 *
 * @see HudModule#getPreview()
 */
public interface HudPreview {

    /**
     * Width of the preview in screen pixels — the same units as
     * {@link HudModule#getWidth()}, which is to say scale already applied.
     */
    int width();

    /** Height of the preview in screen pixels. @see #width() */
    int height();

    /**
     * Draw the preview with its top-left corner at {@code (0,0)}.
     *
     * <p>Called once per frame while the module's page is open, so keep it as cheap as the
     * real render path. Throwing loses the preview for that frame, not the screen.
     *
     * @param ctx       the frame being built
     * @param tickDelta partial tick, passed straight through from the screen
     */
    void render(GuiGraphicsExtractor ctx, float tickDelta);

    /**
     * A preview that is simply the module drawing itself, which is what every built-in
     * module uses.
     *
     * <p>Size comes from {@link HudModule#getWidth()}/{@link HudModule#getHeight()} and the
     * drawing from {@link HudModule#extractRenderState}, both measured and called under
     * {@link HudModule#isPreviewing()}. Use this when your preview is the module with
     * stand-in data rather than something drawn differently; return your own
     * implementation when it is not.
     *
     * <p>The module's render position is set to the origin for the call and put back
     * afterwards, because {@code x}/{@code y} are what the drag-and-drop editor hit-tests
     * against.
     */
    static HudPreview ofModule(HudModule module) {
        return new HudPreview() {
            @Override
            public int width() {
                return module.getWidth();
            }

            @Override
            public int height() {
                return module.getHeight();
            }

            @Override
            public void render(GuiGraphicsExtractor ctx, float tickDelta) {
                int prevX = module.getX();
                int prevY = module.getY();
                module.setRenderPosition(0, 0);
                try {
                    module.extractRenderState(ctx, tickDelta);
                } finally {
                    module.setRenderPosition(prevX, prevY);
                }
            }
        };
    }
}
