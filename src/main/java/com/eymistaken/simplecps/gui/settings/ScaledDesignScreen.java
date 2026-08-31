package com.eymistaken.simplecps.gui.settings;

import com.eymistaken.simplecps.SimpleCPSConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base for the screens laid out from the mod's 1600x900 design mock — the settings
 * screen and the keystrokes designer.
 *
 * <p>Both are laid out in a virtual viewport of their own, around 960x540, which is
 * then scaled onto the real window. That is what makes them the same physical size
 * whatever the player has GUI Scale set to. Laying out in Minecraft's GUI space
 * directly does not work: the chrome would track the GUI space while the fixed 9px
 * font could not, so at GUI Scale 4 the text comes out twice the size the design
 * wants.
 *
 * <p>This lives in one place on purpose. {@link #computeRenderScale()} keeps
 * {@code renderScale * guiScale} a whole number, and that single detail is the only
 * reason the text stays sharp when the menu is scaled down — two slightly different
 * copies of it is exactly the failure worth designing out.
 */
public abstract class ScaledDesignScreen extends Screen {

    /** Width and height of the design mock every proportion here comes from. */
    protected static final int DESIGN_W = 1600;
    protected static final int DESIGN_H = 900;

    /**
     * Upper bound on the design-to-panel scale, set by the font: the mock's body
     * text is 11px, Minecraft's is 9px, so past 9/11 the chrome would keep growing
     * around text that cannot. Beyond that point the UI just looks empty.
     */
    protected static final float MAX_UI_SCALE = 9f / 11f;

    /** Layout size the screen aims for, in virtual units, at 100% menu scale. */
    protected static final int TARGET_VIRTUAL_W = 960;
    protected static final int TARGET_VIRTUAL_H = 540;

    /** Virtual-to-screen factor, and the virtual viewport it produces. */
    protected float renderScale = 1f;
    protected int vw, vh;

    /** Design-to-viewport factor, set by each subclass from the panel it laid out. */
    protected float uiScale = 1f;

    protected ScaledDesignScreen(Component title) {
        super(title);
    }

    /**
     * Pick the virtual viewport and the factor that maps it onto the screen.
     *
     * <p>The factor is chosen so that {@code renderScale * guiScale} lands on a
     * whole number. That product is how many screen pixels one font pixel covers,
     * and keeping it integral is what stops the text going soft when the menu is
     * scaled down — at 1080p it works out to exactly 2 whether the player has GUI
     * Scale on 1, 2 or 4.
     */
    protected void computeRenderScale() {
        int guiScale = this.minecraft != null ? Math.max(1, this.minecraft.getWindow().getGuiScale()) : 1;

        // Larger menu scale means fewer virtual units across the same screen.
        int percent = Math.max(50, Math.min(200, SimpleCPSConfig.instance.settingsMenuScale));
        float targetW = TARGET_VIRTUAL_W * 100f / percent;
        float targetH = TARGET_VIRTUAL_H * 100f / percent;

        float physicalW = this.width * (float) guiScale;
        float physicalH = this.height * (float) guiScale;

        // Round up, not to nearest: rounding down would hand the layout more
        // virtual units than it was designed for and shrink the menu on small
        // displays. The epsilon keeps an exact 2.0 from creeping up to 3.
        int total = Math.max(1, (int) Math.ceil(Math.max(physicalW / targetW, physicalH / targetH) - 0.01f));
        renderScale = total / (float) guiScale;

        vw = Math.max(160, Math.round(this.width / renderScale));
        vh = Math.max(120, Math.round(this.height / renderScale));
    }

    /** Map a screen coordinate into the virtual space everything is laid out in. */
    protected int virtualX(double screenX) {
        return (int) Math.floor(screenX / renderScale);
    }

    protected int virtualY(double screenY) {
        return (int) Math.floor(screenY / renderScale);
    }

    /** Scale a length from the design mock, never going below {@code min}. */
    protected int d(int designPx, int min) {
        return Math.max(min, Math.round(designPx * uiScale));
    }
}
