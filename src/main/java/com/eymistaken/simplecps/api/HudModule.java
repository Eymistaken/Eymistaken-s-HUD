package com.eymistaken.simplecps.api;

import com.eymistaken.simplecps.SimpleCPSClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class HudModule implements IHudElement {

    protected final Minecraft client;

    public HudModule() {
        this.client = Minecraft.getInstance();
    }

    protected int x, y;

    /**
     * Rainbow phase, advanced half a degree per call to {@link #getRainbowColor()}.
     *
     * <p>Previews keep their own: the settings screen draws over a running HUD, so a single
     * shared phase would be stepped twice a frame and the real HUD's rainbow would visibly
     * speed up whenever the menu was open.
     */
    private static float hue = 0;
    private static float previewHue = 0;

    private boolean previewing;

    /**
     * Opacity this module should draw with, in {@code [0,1]}. Set by
     * {@link com.eymistaken.simplecps.HudModuleManager} each frame to drive
     * fade-in / fade-out. Modules that opt into fading (see {@link #supportsFade()})
     * should wrap their colors with {@link #col(int)}.
     */
    protected float renderAlpha = 1f;

    public void setRenderAlpha(float alpha) {
        this.renderAlpha = alpha;
    }

    /** Apply the current {@link #renderAlpha} to an ARGB color. */
    protected int col(int argb) {
        return renderAlpha >= 1f ? argb : com.eymistaken.simplecps.util.RenderUtil.withAlpha(argb, renderAlpha);
    }

    /**
     * Draw module text in the active HUD font, honouring both the current
     * {@link #renderAlpha} and the global {@code textOutline} setting (thin black
     * outline vs vanilla shadow). Modules should call this instead of
     * {@code context.text(...)} directly.
     */
    protected void drawText(GuiGraphicsExtractor context, String text, int x, int y, int color) {
        net.minecraft.network.chat.Component comp = com.eymistaken.simplecps.util.EymHudFonts.text(text);
        if (com.eymistaken.simplecps.SimpleCPSConfig.instance.textOutline) {
            com.eymistaken.simplecps.util.RenderUtil.outlinedText(context, client.font, comp, x, y, col(color), col(0xFF000000));
        } else {
            context.text(client.font, comp, x, y, col(color));
        }
    }

    /** Width of {@code text} measured in the active HUD font. */
    protected int textWidth(String text) {
        return client.font.width(com.eymistaken.simplecps.util.EymHudFonts.text(text));
    }

    /**
     * @return true if this module renders through {@link #col(int)} and can be
     *         smoothly faded in/out. Defaults to false (module pops in/out as
     *         before); the simple text modules override this to true.
     */
    public boolean supportsFade() {
        return false;
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    public void setRenderPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Called on client tick.
     */
    public void tick(Minecraft client) {}

    public abstract com.eymistaken.simplecps.SimpleCPSConfig.Position getPositionType();
    public abstract int getXOffset();
    public abstract int getYOffset();

    // Editable properties
    public void setPositionType(com.eymistaken.simplecps.SimpleCPSConfig.Position pos) {}
    public void setXOffset(int x) {}
    public void setYOffset(int y) {}
    public void setScale(int scale) {}
    public int getScale() { return 100; } // Default 100
    
    /**
     * Resets ONLY the layout-related properties of this module.
     * This includes Position, X Offset, Y Offset, and Scale.
     * Visual and behavioral settings are outside the scope of this method.
     */
    public void resetToDefaults() {}

    /**
     * Resets ALL visual and behavioral properties of this module
     * back to their default values as defined in SimpleCPSConfig.
     * This method strictly ignores layout properties (Position, Offsets, Scale).
     */
    public void resetVisualDefaults() {}

    /**
     * Settings offered on the right-click menu in the drag-and-drop editor.
     *
     * <p>This menu is meant to be short — the handful of things worth reaching for
     * without leaving the editor. Everything returned here <em>also</em> appears on
     * this module's page in the settings screen, so there is no need to repeat it in
     * {@link #getSettingsScreenSettings()}.
     *
     * @return the context menu settings for this module
     */
    public java.util.List<HudModuleSetting> getContextMenuSettings() {
        return java.util.List.of(
            new com.eymistaken.simplecps.api.ActionSetting("Reset Settings", () -> {
                this.resetVisualDefaults();
                com.eymistaken.simplecps.SimpleCPSConfig.save();
            })
        );
    }

    /**
     * Extra settings shown only on this module's page in the settings screen.
     *
     * <p>Use this for everything that belongs in the full settings screen but would
     * clutter the editor's right-click menu. The two lists are concatenated in
     * order: whatever {@link #getContextMenuSettings()} returns comes first, then
     * these.
     *
     * <p>Nothing is de-duplicated, so put each setting in one list or the other —
     * a setting named in both simply shows up twice.
     *
     * <p>Once {@link #getSettingsTabs()} returns tabs these are not concatenated onto
     * the end any more; they get a trailing SETTINGS tab of their own.
     *
     * @return additional settings-screen-only settings; empty by default
     */
    public java.util.List<HudModuleSetting> getSettingsScreenSettings() {
        return java.util.List.of();
    }

    /**
     * Tabs this module opens on its page in the settings screen.
     *
     * <p>Empty by default, which keeps the original layout exactly: one flat SETTINGS
     * tab holding everything from {@link #getContextMenuSettings()} followed by
     * everything from {@link #getSettingsScreenSettings()}. Return tabs once that list
     * has grown long enough to stop being readable.
     *
     * <p>Returning tabs does not throw the two flat lists away. The context menu
     * settings lead the first tab — the contract that everything on the editor's
     * right-click menu also appears on the page still holds — and the
     * settings-screen-only ones become a trailing SETTINGS tab.
     *
     * <p>The POSITION tab is always added for you, so do not declare placement rows
     * here. A tab whose settings are all empty or of an unrecognized type is dropped
     * rather than shown blank, and two tabs sharing an id are renumbered rather than
     * one going missing. Keep to a handful of short names: the tab strip does not
     * scroll, so tabs past the panel's width are not drawn.
     *
     * @return the tabs for this module's settings page; empty by default
     */
    public java.util.List<SettingsTab> getSettingsTabs() {
        return java.util.List.of();
    }

    /**
     * A live sample of this module, shown in the PREVIEW card at the top of the settings
     * screen's right column while this module's page is open.
     *
     * <p>Null by default, and a null preview means no card at all rather than an empty one:
     * a module written before this existed, or one with nothing worth showing, simply does
     * not get the panel and everything below it moves up. {@link HudPreview#ofModule} covers
     * the usual case, which is this module drawing itself with stand-in data:
     *
     * <pre>{@code
     * @Override
     * public HudPreview getPreview() {
     *     return HudPreview.ofModule(this);
     * }
     * }</pre>
     *
     * <p>Because the preview draws the module as it would really look, it usually needs data
     * the game is not currently producing — no clicks have been counted, no server is
     * connected, the player may not even exist. Branch on {@link #isPreviewing()} inside the
     * render path to substitute sample values. That flag is also set while the preview is
     * measured, so {@link #getWidth()} and {@link #getHeight()} see the same data they will
     * be asked to draw.
     *
     * <p>Called every frame the page is open. Keep it cheap, and prefer returning a cached
     * instance to allocating one per call. Throwing loses the preview for that frame and
     * nothing else; the screen carries on.
     *
     * @return the preview for this module, or null for none
     * @see HudPreview
     */
    public HudPreview getPreview() {
        return null;
    }

    /**
     * Whether this module is currently being drawn as a preview rather than onto the real
     * HUD.
     *
     * <p>True for the whole of a preview pass, measurement included. Two things follow from
     * that. Sample data substituted here must also be reflected in {@link #getWidth()} and
     * {@link #getHeight()}, or the box will be sized for something other than what it shows.
     * And a preview runs <em>in addition to</em> the live HUD render whenever the settings
     * screen is opened in-game, so any per-frame animation counter stepped inside the render
     * path needs a preview-local copy — otherwise the real HUD animates at double speed for
     * as long as the menu is open.
     */
    public final boolean isPreviewing() {
        return previewing;
    }

    /**
     * Marks a preview pass. Called by the settings screen around measurement and drawing;
     * modules read the flag through {@link #isPreviewing()} rather than setting it.
     */
    public final void setPreviewing(boolean previewing) {
        this.previewing = previewing;
    }

    /**
     * Serialize this module's own settings so they travel with the config: they are
     * written into {@code simplecps.json}, saved into presets, packed into share
     * codes and (later) into per-server configs, all for free.
     *
     * <p>Return {@code null} to store nothing — that is what every built-in module
     * does, because their settings already live as fields on
     * {@link com.eymistaken.simplecps.SimpleCPSConfig}. Third-party modules cannot
     * add fields there, so this is their way in. Include everything you own:
     * position, offsets, scale, enabled state, colors.
     *
     * <p>Do not put JSON nulls anywhere in the returned tree; the serializer drops
     * them at every depth, so they will not survive a round trip. Omit the key
     * instead. Called on every config save, so keep it cheap.
     */
    public com.google.gson.JsonElement saveState() {
        return null;
    }

    /**
     * Restore what {@link #saveState()} produced.
     *
     * <p>Never called with {@code null}: when no state was stored for this module the
     * call is skipped entirely. That is deliberate — it means a config written by
     * someone who does not have your module leaves your settings untouched instead of
     * resetting them.
     *
     * <p>Called once at registration and again every time a preset, share code or
     * server config is applied, so it can run at any point after startup.
     */
    public void loadState(com.google.gson.JsonElement state) {}

    /**
     * @return true if the module should be rendered
     */
    public abstract boolean isEnabled();

    /**
     * @return the width of the module content
     */
    public abstract int getWidth();

    /**
     * @return the height of the module content
     */
    public abstract int getHeight();

    /**
     * Render the module content.
     * The context is already translated to the correct position (x, y) if the implementation uses x/y, 
     * but usually SimpleCPSClient handles the translation or the module uses this.x/this.y.
     * Note: SimpleCPSClient logic was: translate to x,y then draw at 0,0.
     * 
     * @param context DrawContext for rendering
     * @param tickDelta Partial tick time
     */
    public abstract void extractRenderState(GuiGraphicsExtractor context, float tickDelta);
    
    /**
     * Returns the name of the module for identification in bounds map.
     */
    public abstract String getName();

    protected int getRainbowColor() {
        float phase;
        if (previewing) {
            previewHue += 0.5f;
            if (previewHue > 360) previewHue = 0;
            phase = previewHue;
        } else {
            hue += 0.5f;
            if (hue > 360) hue = 0;
            phase = hue;
        }
        int rgb = net.minecraft.util.Mth.hsvToRgb(phase / 360f, 1.0f, 1.0f);
        return rgb | 0xFF000000;
    }

    @Override
    public void onPositionUpdated() {
        com.eymistaken.simplecps.SimpleCPSConfig.save();
    }
}
