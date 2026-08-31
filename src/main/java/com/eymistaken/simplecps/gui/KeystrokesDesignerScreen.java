package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.gui.settings.HudSettingsScreen;
import com.eymistaken.simplecps.gui.settings.ScaledDesignScreen;
import com.eymistaken.simplecps.gui.settings.SettingsTheme;
import com.eymistaken.simplecps.gui.settings.TextInput;
import com.eymistaken.simplecps.modules.KeystrokesAnim;
import com.eymistaken.simplecps.modules.KeystrokesDesign;
import com.eymistaken.simplecps.modules.KeystrokesRender;
import com.eymistaken.simplecps.util.Anim;
import com.eymistaken.simplecps.util.Easings;
import com.eymistaken.simplecps.util.HudActions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/**
 * The keystrokes layout designer: an add/layers/presets rail on the left, a zoomed
 * canvas in the middle, and an inspector for the selected key on the right.
 *
 * <p>Laid out from the same 1600x900 mock as {@link HudSettingsScreen}, through the
 * shared {@link ScaledDesignScreen} viewport, and drawn entirely with
 * {@link SettingsTheme} so the two screens stay one design rather than two.
 *
 * <p><b>Canvas.</b> Everything on it is measured in <em>in-game</em> pixels, not in
 * design units — a 21x21 key has to look the size it will actually be. So the canvas
 * picks a whole-number {@code zoom} that fits the 400x300 safe area into whatever
 * room the panels leave, and the layout origin sits where the default 67x97 layout
 * lands centered, exactly as the old designer placed it. Keeping the zoom integral is
 * what stops the key labels going soft.
 *
 * <p><b>Saving.</b> Edits land on {@link SimpleCPSConfig#instance} as you make them;
 * DONE writes them to disk, CANCEL restores the layout snapshot taken when the screen
 * opened. ESC behaves like DONE, matching every other Minecraft screen.
 */
public class KeystrokesDesignerScreen extends ScaledDesignScreen {

    /**
     * Always read the config through this, never into a captured field: applying a
     * preset or a share code replaces {@link SimpleCPSConfig#instance} wholesale, and
     * a held reference would go on writing into the discarded object.
     */
    private static SimpleCPSConfig config() {
        return SimpleCPSConfig.instance;
    }

    private static List<SimpleCPSConfig.KeyButtonData> layout() {
        return config().keystrokesLayout;
    }

    // --- Design metrics (mock pixels) --------------------------------------

    private static final int HEADER_H = 58;
    private static final int TOOLBAR_H = 48;
    private static final int FOOTER_H = 50;
    private static final int CANVAS_HEAD_H = 62;
    private static final int LEFT_W = 296;
    private static final int RIGHT_W = 330;
    private static final int SIDE_HEAD_H = 41;
    private static final int SECTION_H = 30;
    private static final int ROW_H = 46;
    private static final int LAYER_ROW_H = 42;
    private static final int ADD_TILE_H = 50;
    private static final int PRESET_BTN_H = 34;
    private static final int MENU_W = 216;
    private static final int MENU_ROW_H = 34;
    private static final int COLOR_PANEL_W = 314;

    /** The safe area drawn on the canvas, in in-game pixels. */
    private static final int SAFE_W = 400;
    private static final int SAFE_H = 300;


    private static final int MIN_KEY_SIZE = 5;
    private static final int LABEL_SCALE_STEP = 10;
    private static final int LINE_WIDTH_STEP = 5;
    /**
     * Line thickness is {@code round(2 * labelScale / 100)}, so this is the step that
     * moves it by exactly one pixel. Anything smaller makes four clicks in five do
     * nothing visible.
     */
    private static final int LINE_WEIGHT_STEP = 50;

    /** How long EXPORT reads "COPIED!" before going back to its label. */
    private static final long COPIED_MILLIS = 1400;

    private static final float MENU_ANIM_DURATION = 0.16f;
    private static final float FADE_OUT_CUTOFF = 0.01f;

    /** Undo depth. Each entry is the whole layout as JSON, so this is not free. */
    private static final int MAX_HISTORY = 60;

    /** Cheat-sheet rows, as {@code {what, how}}. Kept short so both columns fit. */
    private static final String[][] SHORTCUTS = {
        { "MULTI SELECT", "CTRL+CLICK" },
        { "BOX SELECT", "DRAG CANVAS" },
        { "EDIT LABEL", "DBL CLICK" },
        { "MOVE LABEL", "RIGHT DRAG" },
        { "RESIZE BOX", "SCROLL" },
        { "TEXT SIZE", "CTRL+SCROLL" },
        { "NUDGE", "ARROWS" },
        { "NUDGE 5PX", "SHIFT+ARROWS" },
        { "DUPLICATE", "CTRL+D" },
        { "UNDO / REDO", "CTRL+Z / Y" },
        { "QUICK ACTIONS", "RIGHT CLICK" },
        { "DESIGNS", "TOOLBAR" },
        { "ANIMATION", "TOOLBAR" },
    };

    /**
     * A color per animation, so which one is active reads at a glance instead of
     * having to be word-read. Desaturated to the same recipe as the palette's own
     * green, so they sit inside the design rather than shouting over it; NONE stays
     * gray precisely because it is the "nothing happening" state.
     */
    private static int motionColor(KeystrokesAnim.Motion motion) {
        if (motion == null) return SettingsTheme.TEXT_OFF;
        return switch (motion) {
            case NONE -> SettingsTheme.TEXT_OFF;
            case SQUISH -> 0xFFA9E2A9;
            case KICK -> 0xFFE2D0A9;
            case SINK -> 0xFFA9C9E2;
            case NUDGE -> 0xFFD8A9E2;
        };
    }

    private static int fillColor(KeystrokesAnim.Fill fill) {
        if (fill == null) return SettingsTheme.TEXT_OFF;
        return switch (fill) {
            case NONE -> SettingsTheme.TEXT_OFF;
            case RIPPLE -> 0xFFA9C9E2;
            case SWEEP -> 0xFFA9E2A9;
            case CASCADE -> 0xFFE2D0A9;
            case HOLD_RING -> 0xFFD8A9E2;
            case EDGE_RUN -> 0xFFA9E2D8;
            case GLOW -> 0xFFE2A9A9;
        };
    }

    // --- State -------------------------------------------------------------

    private enum Mode { IDLE, LABEL, KEYBIND }

    private final Screen parent;

    private final List<SimpleCPSConfig.KeyButtonData> selection = new ArrayList<>();
    private Mode mode = Mode.IDLE;

    private final Gson gson = new Gson();
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();
    /** The layout as it was when the screen opened; CANCEL puts this back. */
    private String openSnapshot = "";
    /**
     * Taken once, not on every {@link #init()}. Minecraft re-runs init on every window
     * resize, so re-snapshotting there would quietly adopt the edits made so far as
     * the baseline and leave CANCEL with nothing to restore.
     */
    private boolean snapshotTaken = false;
    private boolean dirty = false;

    /** Session-only, like the mock: snapping is a working aid, not a saved preference. */
    private boolean snapEnabled = true;

    // Marquee, in virtual screen coordinates.
    private boolean marqueeActive = false;
    private boolean marqueeMoved = false;
    private boolean marqueeAdditive = false;
    private int marqueeStartX, marqueeStartY, marqueeX, marqueeY;
    private final List<SimpleCPSConfig.KeyButtonData> marqueeBase = new ArrayList<>();

    // Left-drag move.
    private static final class Snapshot {
        final SimpleCPSConfig.KeyButtonData btn;
        final int x, y;

        Snapshot(SimpleCPSConfig.KeyButtonData btn) {
            this.btn = btn;
            this.x = btn.x;
            this.y = btn.y;
        }
    }

    private final List<Snapshot> dragSnapshots = new ArrayList<>();
    private boolean dragging = false;
    private int dragStartX, dragStartY;
    /** Magnet correction carried by the previous drag frame, to spot changes in it. */
    private int prevSnapCorrX, prevSnapCorrY;

    // Right-drag moves the label rather than the key.
    private SimpleCPSConfig.KeyButtonData rightTarget = null;
    private int rightStartX, rightStartY;
    private boolean rightDragging = false;

    /** Snap guides, in layout coordinates. */
    private Integer snapX = null;
    private Integer snapY = null;

    /**
     * Guides for the label magnet, in layout coordinates, plus the key they belong to.
     * Kept separate from the key guides above: these are drawn only across their own
     * key, and the two magnets never run at the same time anyway.
     */
    private SimpleCPSConfig.KeyButtonData labelGuideKey = null;
    private Integer labelGuideX = null;
    private Integer labelGuideY = null;

    /** How close the label has to get to a target before the magnet takes it. */
    private static final int LABEL_SNAP = 3;

    private long lastClickMillis = 0;
    private SimpleCPSConfig.KeyButtonData lastClickTarget = null;
    private static final long DOUBLE_CLICK_MILLIS = 250;

    // Context menu.
    private boolean menuOpen = false;
    private int menuX, menuY;
    private final Anim menuAnim = new Anim(0f);

    // Inline color editor.
    private boolean colorOpen = false;
    private boolean colorPressedTarget = false;
    private float cpHue, cpSat = 1f, cpVal = 1f;
    private enum CpDrag { NONE, SV, HUE }
    private CpDrag cpDrag = CpDrag.NONE;

    // Design gallery.
    private boolean galleryOpen = false;
    private int galleryScroll = 0;
    /**
     * Card previews animate themselves so a design can be judged pressed as well as
     * at rest. Their state is kept here, away from the live HUD's, for the same
     * reason the module keeps a separate preview store.
     */
    private final KeystrokesAnim.Store galleryAnim = new KeystrokesAnim.Store();
    private long galleryOpenedAt = 0;

    // Animation panel.
    private boolean animOpen = false;

    // Import modal.
    private boolean importOpen = false;
    private final TextInput importInput = new TextInput();
    private String importMessage = "";
    private boolean importOk = false;

    private long copiedUntil = 0;

    // Label field in the inspector.
    private final TextInput labelInput = new TextInput();
    private boolean labelFocused = false;
    /** The key labelInput is currently mirroring, so it can be refilled on a change. */
    private SimpleCPSConfig.KeyButtonData labelInputFor = null;

    private int layersScroll = 0;
    private int inspectorScroll = 0;
    /** Folded away to hand the inspector its room back. Session state, like SNAP. */
    private boolean shortcutsCollapsed = false;

    // --- Smooth movement ---------------------------------------------------
    //
    // The key's stored x/y jumps straight to where it belongs; only the leftover —
    // the magnet pulling it onto a guide, an arrow-key step — is drawn as an offset
    // that eases back to zero, so cursor-following stays exactly 1:1.

    private static final float GLIDE_DURATION = 0.16f;

    private static final class Glide {
        final Anim x = new Anim(0f);
        final Anim y = new Anim(0f);

        Glide() {
            // snap() marks the Anim initialized; without it the first update() would
            // jump straight to zero and there would be no animation at all.
            x.snap(0f);
            y.snap(0f);
        }
    }

    private final Map<SimpleCPSConfig.KeyButtonData, Glide> glides = new HashMap<>();

    // --- Layout (recomputed every frame) -----------------------------------

    private int panelX, panelY, panelW, panelH;
    private int innerX, innerW, bodyY, bodyH, footerY;
    private int headerH, toolbarH, footerH, canvasHeadH;
    private int leftW, rightW, sideHeadH, sectionH, rowH, layerRowH, addTileH, presetBtnH;
    private int pad, gap, ctrlH, btnH, stepBtn, stepFieldW, scrollbarW, swatchH;
    private int menuW, menuRowH, shortcutRowH, shortcutsH;

    private int leftX, canvasX, canvasY, canvasW, canvasH, rightX;
    private int tilesY, layersHeadY, layersY, layersH, presetsHeadY, presetsY;
    private int zoom = 1, safeX, safeY, originX, originY;

    /**
     * The cluster size the canvas centers on, in game pixels.
     *
     * <p>The origin used to be a constant sized for the stock 67x96 layout, so an
     * arranged design — the compass is 120x134, the honeycomb 100x158 — sat down and
     * to the right of the safe area instead of in it.
     *
     * <p>Held as a field rather than measured each frame on purpose: recomputing it
     * live would slide every key sideways as soon as a drag pushed the extent past
     * its old maximum. It is refreshed when the layout is replaced wholesale, which
     * is the only time the cluster's size changes without the player watching a key
     * move under the cursor.
     */
    private int centerW = 67, centerH = 97;

    public KeystrokesDesignerScreen(Screen parent) {
        super(Component.nullToEmpty("Keystrokes Designer"));
        this.parent = parent;
    }

    public KeystrokesDesignerScreen() {
        this(null);
    }

    @Override
    protected void init() {
        importInput.setMaxLength(4096);
        labelInput.setMaxLength(32);
        if (!snapshotTaken) {
            openSnapshot = gson.toJson(snapshot());
            snapshotTaken = true;
        }
        recenter();
        layout0();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        layout0();
    }

    /** Named to stay out of the way of {@link #layout()}, which is the key list. */
    private void layout0() {
        computeRenderScale();

        int margin = vw >= 400 ? 3 : 0;
        panelX = margin;
        panelY = margin;
        panelW = vw - margin * 2;
        panelH = vh - margin * 2;

        uiScale = Math.min(MAX_UI_SCALE,
            Math.min(panelW / (float) DESIGN_W, panelH / (float) DESIGN_H));

        headerH = d(HEADER_H, 16);
        toolbarH = d(TOOLBAR_H, 14);
        footerH = d(FOOTER_H, 16);
        canvasHeadH = d(CANVAS_HEAD_H, 16);
        sideHeadH = d(SIDE_HEAD_H, 12);
        sectionH = d(SECTION_H, 10);
        rowH = d(ROW_H, 14);
        layerRowH = d(LAYER_ROW_H, 12);
        addTileH = d(ADD_TILE_H, 16);
        presetBtnH = d(PRESET_BTN_H, 12);
        pad = d(14, 3);
        gap = d(8, 2);
        ctrlH = d(26, 10);
        btnH = d(30, 12);
        stepBtn = d(22, 7);
        stepFieldW = d(38, 14);
        scrollbarW = d(10, 3);
        swatchH = d(24, 8);
        menuW = d(MENU_W, 70);
        menuRowH = d(MENU_ROW_H, 11);
        shortcutRowH = d(18, 12);

        innerX = panelX + 1;
        innerW = panelW - 2;
        bodyY = panelY + 1 + headerH + toolbarH;
        footerY = panelY + panelH - 1 - footerH;
        bodyH = Math.max(0, footerY - bodyY);

        // The rails keep their share of the width at every size, so no breakpoint is
        // needed: leftW and rightW are both fractions of panelW by construction.
        leftW = d(LEFT_W, 70);
        rightW = d(RIGHT_W, 80);
        leftX = innerX;
        canvasX = leftX + leftW;
        rightX = innerX + innerW - rightW;
        canvasW = Math.max(0, rightX - canvasX);
        canvasY = bodyY + canvasHeadH;
        canvasH = Math.max(0, footerY - canvasY);

        // Left rail: ADD tiles at the top, PRESETS pinned to the bottom, LAYERS
        // taking whatever is left between them.
        tilesY = bodyY + sideHeadH + gap;
        int tilesH = addTileH * 3 + gap * 2;
        layersHeadY = tilesY + tilesH + gap;
        layersY = layersHeadY + sideHeadH;
        presetsHeadY = footerY - sideHeadH - gap * 2 - presetBtnH;
        presetsY = presetsHeadY + sideHeadH + gap;
        layersH = Math.max(0, presetsHeadY - layersY);

        shortcutsH = shortcutsCollapsed
            ? sectionH
            : sectionH + shortcutRowH * SHORTCUTS.length;
        // The inspector comes first. Fold the sheet to its header rather than let it
        // push the settings out of reach, and drop it outright if even that is too much.
        if (bodyH - shortcutsH < rowH * 5) shortcutsH = Math.min(shortcutsH, sectionH);
        if (bodyH - shortcutsH < rowH * 3) shortcutsH = 0;

        computeZoom();
    }

    /**
     * Largest whole-number zoom whose safe area still fits the canvas pane, floored
     * at 1. A fractional zoom would put key edges and labels between pixels, which is
     * exactly the softness the virtual viewport exists to avoid; when even 1x does not
     * fit, the canvas is scissored instead of scaled down.
     */
    private void computeZoom() {
        int roomW = canvasW - gap * 2;
        int roomH = canvasH - gap * 2 - d(26, 9);
        int fit = Math.min(roomW / SAFE_W, roomH / SAFE_H);
        zoom = Math.max(1, Math.min(3, fit));

        safeX = canvasX + (canvasW - SAFE_W * zoom) / 2;
        safeY = canvasY + (canvasH - SAFE_H * zoom) / 2;
        originX = safeX + (SAFE_W - centerW) / 2 * zoom;
        originY = safeY + (SAFE_H - centerH) / 2 * zoom;
    }

    private int centerTextY(int y, int h) {
        return y + (h - this.font.lineHeight) / 2 + 1;
    }


    // --- Canvas palette ----------------------------------------------------
    //
    // The canvas is the one place that is not chrome: it stands in for the game
    // behind the HUD, so it gets its own near-black ground rather than the panel's
    // white glazes.

    private static final int CANVAS_BG = 0xFF0C0C0E;
    private static final int CHECKER = 0x09FFFFFF;
    private static final int SAFE_BORDER = 0x21FFFFFF;
    /** Shared resting animation state; the canvas never presses a key, and drawKey only reads it. */
    private static final KeystrokesAnim.State REST = new KeystrokesAnim.State();

    private static final int KEY_SEL_BORDER = 0xFFD2D2D8;
    private static final int KEY_SEL_OUTLINE = 0x59D2D2D8;
    private static final int MARQUEE_FILL = 0x1AD2D2D8;
    private static final int ACCENT = 0xFFD2D2D8;

    // --- Rendering ---------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout0();
        advanceGlides();
        syncLabelInput();

        int vmx = virtualX(mouseX);
        int vmy = virtualY(mouseY);
        // A modal owns the pointer; letting hover states light up behind it reads as
        // if the screen were still live.
        boolean modal = importOpen || galleryOpen;
        int mx = modal ? -1 : vmx;
        int my = modal ? -1 : vmy;

        ctx.pose().pushMatrix();
        ctx.pose().scale(renderScale, renderScale);

        SettingsTheme.rect(ctx, 0, 0, vw, vh, SettingsTheme.BACKDROP);
        SettingsTheme.raised(ctx, panelX, panelY, panelW, panelH,
            SettingsTheme.PANEL_BG, SettingsTheme.PANEL_LIGHT, SettingsTheme.PANEL_DARK);

        renderHeader(ctx, mx, my);
        renderToolbar(ctx, mx, my);
        renderLeftPanel(ctx, mx, my);
        renderCanvas(ctx, mx, my);
        renderInspector(ctx, mx, my);
        renderFooter(ctx, mx, my);

        float menuP = menuAnim.update(menuOpen ? 1f : 0f, MENU_ANIM_DURATION, Easings::expoOut);
        if (menuOpen || menuP > FADE_OUT_CUTOFF) {
            renderContextMenu(ctx, mx, my, menuP);
        }
        if (colorOpen) {
            renderColorPanel(ctx, mx, my);
        }
        if (animOpen) {
            renderAnimPanel(ctx, mx, my);
        }
        if (galleryOpen) {
            renderGallery(ctx, vmx, vmy);
        }
        if (importOpen) {
            renderImportModal(ctx, vmx, vmy);
        }

        ctx.pose().popMatrix();
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphicsExtractor ctx, int mx, int my) {
        int y = panelY + 1;
        SettingsTheme.rect(ctx, innerX, y, innerW, headerH, SettingsTheme.BAR_BG);
        SettingsTheme.rect(ctx, innerX, y, innerW, 1, SettingsTheme.BAR_LIGHT);
        SettingsTheme.rect(ctx, innerX, y + headerH - 1, innerW, 1, SettingsTheme.BORDER);

        int textY = centerTextY(y, headerH);
        int tx = innerX + pad;
        SettingsTheme.text(ctx, this.font, "EYMISTAKEN'S HUD", tx, textY, SettingsTheme.TEXT);
        tx += SettingsTheme.textWidth(this.font, "EYMISTAKEN'S HUD") + gap + gap / 2;

        String badge = "KEYSTROKES DESIGNER";
        int badgeW = SettingsTheme.textWidth(this.font, badge) + pad * 2;
        int badgeY = y + (headerH - ctrlH) / 2;
        SettingsTheme.sunken(ctx, tx, badgeY, badgeW, ctrlH, SettingsTheme.FIELD_BG, SettingsTheme.FIELD_RIM);
        SettingsTheme.text(ctx, this.font, badge, tx + pad, centerTextY(badgeY, ctrlH), SettingsTheme.TEXT_MUTED);

        int[] close = headerCloseRect();
        int[] settings = headerSettingsRect();

        boolean sHov = SettingsTheme.inside(mx, my, settings[0], settings[1], settings[2], settings[3]);
        SettingsTheme.button(ctx, settings[0], settings[1], settings[2], settings[3], sHov);
        SettingsTheme.text(ctx, this.font, "SETTINGS", settings[0] + d(12, 3),
            centerTextY(settings[1], settings[3]), SettingsTheme.TEXT_LABEL);

        boolean cHov = SettingsTheme.inside(mx, my, close[0], close[1], close[2], close[3]);
        SettingsTheme.button(ctx, close[0], close[1], close[2], close[3], cHov);
        SettingsTheme.closeIcon(ctx, close[0] + (close[2] - 5) / 2, close[1] + (close[3] - 5) / 2,
            SettingsTheme.TEXT_LABEL);
    }

    private int[] headerCloseRect() {
        int w = d(34, 12);
        return new int[] { innerX + innerW - pad - w, panelY + 1 + (headerH - btnH) / 2, w, btnH };
    }

    private int[] headerSettingsRect() {
        int w = SettingsTheme.textWidth(this.font, "SETTINGS") + d(24, 6);
        int[] close = headerCloseRect();
        return new int[] { close[0] - gap - w, close[1], w, btnH };
    }

    /** One toolbar control: its id, label and rect. */
    private record ToolBtn(String id, String label, int x, int y, int w, int h) {}

    private List<ToolBtn> toolbarButtons() {
        List<ToolBtn> out = new ArrayList<>();
        int y = panelY + 1 + headerH + (toolbarH - btnH) / 2;
        int padX = d(24, 6);

        int x = innerX + pad;
        for (String id : new String[] { "undo", "redo" }) {
            String label = id.toUpperCase(java.util.Locale.ROOT);
            int w = SettingsTheme.textWidth(this.font, label) + padX;
            out.add(new ToolBtn(id, label, x, y, w, btnH));
            x += w + gap;
        }
        int snapW = SettingsTheme.textWidth(this.font, "SNAP") + padX + d(14, 5);
        out.add(new ToolBtn("snap", "SNAP", x, y, snapW, btnH));
        x += snapW + gap + d(10, 3);

        // Right group first, laid out from the right edge inwards, because the two
        // value buttons on the left size themselves against whatever it leaves.
        int right = innerX + innerW - pad;
        for (String[] item : new String[][] { { "reset", "RESET LAYOUT" }, { "import", "IMPORT" }, { "export", "EXPORT" } }) {
            boolean isExport = item[0].equals("export");
            String label = isExport && System.currentTimeMillis() < copiedUntil ? "COPIED!" : item[1];
            // EXPORT keeps the width of its widest state, so the flash does not shove
            // the buttons beside it sideways for a second and a half.
            int w = SettingsTheme.textWidth(this.font,
                isExport && SettingsTheme.textWidth(this.font, "COPIED!") > SettingsTheme.textWidth(this.font, item[1])
                    ? "COPIED!" : item[1]) + padX;
            right -= w;
            out.add(new ToolBtn(item[0], label, right, y, w, btnH));
            right -= gap;
        }

        // Widths cover the widest name so a change does not resize the button
        // underneath the cursor, plus room for the caret.
        int widestDesign = 0;
        for (KeystrokesDesign design : KeystrokesDesign.values()) {
            widestDesign = Math.max(widestDesign,
                SettingsTheme.textWidth(this.font, SettingsTheme.up(design.display())));
        }
        int widestAnim = 0;
        for (KeystrokesAnim.Fill fill : KeystrokesAnim.Fill.values()) {
            widestAnim = Math.max(widestAnim,
                SettingsTheme.textWidth(this.font, SettingsTheme.up(fill.display())));
        }
        for (KeystrokesAnim.Motion motion : KeystrokesAnim.Motion.values()) {
            widestAnim = Math.max(widestAnim,
                SettingsTheme.textWidth(this.font, SettingsTheme.up(motion.display())));
        }

        int caret = padX + d(10, 4);
        int designW = SettingsTheme.textWidth(this.font, "DESIGN") + gap + widestDesign + caret;
        int animW = SettingsTheme.textWidth(this.font, "ANIM") + gap + widestAnim + caret;
        // In a narrow window the two value buttons would run under the right group.
        // Drop their values rather than overlap: the panels they open still say
        // what is selected, and a toolbar that silently overflows says nothing.
        if (x + designW + gap + animW > right) {
            designW = SettingsTheme.textWidth(this.font, "DESIGN") + caret;
            animW = SettingsTheme.textWidth(this.font, "ANIM") + caret;
        }
        out.add(new ToolBtn("designs", "DESIGN", x, y, designW, btnH));
        x += designW + gap;
        out.add(new ToolBtn("anim", "ANIM", x, y, animW, btnH));
        return out;
    }

    /** Whether the toolbar has room to print the value beside DESIGN / ANIM. */
    private boolean toolbarShowsValues() {
        for (ToolBtn b : toolbarButtons()) {
            if (b.id().equals("anim")) {
                return b.w() > SettingsTheme.textWidth(this.font, "ANIM") + d(24, 6) + d(10, 4) + gap;
            }
        }
        return true;
    }

    /** Small filled triangle, the mock's "this cycles" marker. */
    private static void caretDown(GuiGraphicsExtractor ctx, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            int w = size * 2 - 1 - i * 2;
            if (w <= 0) break;
            SettingsTheme.rect(ctx, x + i, y + i, w, 1, color);
        }
    }

    private static void caretUp(GuiGraphicsExtractor ctx, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            int w = size * 2 - 1 - i * 2;
            if (w <= 0) break;
            SettingsTheme.rect(ctx, x + i, y + size - 1 - i, w, 1, color);
        }
    }

    private KeystrokesDesign globalDesign() {
        KeystrokesDesign design = config().keystrokesDesign;
        return design == null ? KeystrokesDesign.BASELINE : design;
    }

    private List<KeystrokesAnim.Motion> globalMotions() {
        return KeystrokesAnim.cleanMotions(config().keystrokesMotions);
    }

    private List<KeystrokesAnim.Fill> globalFills() {
        return KeystrokesAnim.cleanFills(config().keystrokesFills);
    }

    private KeystrokesAnim.Direction globalDirection() {
        return KeystrokesAnim.coerceAll(globalFills(), config().keystrokesFillDirection);
    }

    /** Whether any active fill actually reads the direction setting. */
    private boolean directionMatters() {
        for (KeystrokesAnim.Fill fill : globalFills()) {
            if (fill.directional()) return true;
        }
        return false;
    }

    private String designName() {
        return SettingsTheme.up(globalDesign().display());
    }

    /**
     * What the ANIM button shows: the first fill, or the first motion when there is
     * no fill — reporting "NONE" while the keys are visibly squishing would be a lie
     * — plus a count of whatever else is ticked.
     */
    private String animName() {
        List<KeystrokesAnim.Fill> fills = globalFills();
        List<KeystrokesAnim.Motion> motions = globalMotions();
        int total = fills.size() + motions.size();
        if (total == 0) return "NONE";

        String lead = fills.isEmpty()
            ? SettingsTheme.up(motions.get(0).display())
            : SettingsTheme.up(fills.get(0).display());
        return total == 1 ? lead : lead + " +" + (total - 1);
    }

    private int animColor() {
        List<KeystrokesAnim.Fill> fills = globalFills();
        if (!fills.isEmpty()) return fillColor(fills.get(0));
        List<KeystrokesAnim.Motion> motions = globalMotions();
        return motions.isEmpty() ? SettingsTheme.TEXT_OFF : motionColor(motions.get(0));
    }

    private void renderToolbar(GuiGraphicsExtractor ctx, int mx, int my) {
        int y = panelY + 1 + headerH;
        SettingsTheme.rect(ctx, innerX, y, innerW, toolbarH, SettingsTheme.CONTENT_HEAD);
        SettingsTheme.rect(ctx, innerX, y + toolbarH - 1, innerW, 1, SettingsTheme.BORDER);

        boolean values = toolbarShowsValues();
        for (ToolBtn b : toolbarButtons()) {
            boolean hov = SettingsTheme.inside(mx, my, b.x(), b.y(), b.w(), b.h());
            boolean enabled = switch (b.id()) {
                case "undo" -> !undoStack.isEmpty();
                case "redo" -> !redoStack.isEmpty();
                default -> true;
            };
            int textColor = enabled ? SettingsTheme.TEXT_LABEL : SettingsTheme.TEXT_OFF;

            if (b.id().equals("reset")) {
                SettingsTheme.raised(ctx, b.x(), b.y(), b.w(), b.h(),
                    hov ? SettingsTheme.DANGER_HOVER : SettingsTheme.DANGER_BG,
                    SettingsTheme.DANGER_LIGHT, SettingsTheme.DANGER_DARK);
                textColor = SettingsTheme.DANGER_TEXT;
            } else if (b.id().equals("snap") && snapEnabled) {
                SettingsTheme.raised(ctx, b.x(), b.y(), b.w(), b.h(),
                    hov ? SettingsTheme.BTN_HOVER : SettingsTheme.TAB_ACTIVE_BG,
                    SettingsTheme.TAB_ACTIVE_HI, SettingsTheme.BTN_DARK);
            } else {
                SettingsTheme.button(ctx, b.x(), b.y(), b.w(), b.h(), hov && enabled);
            }
            if (b.id().equals("export") && System.currentTimeMillis() < copiedUntil) {
                textColor = SettingsTheme.OK_TEXT;
            }

            int tx = b.x() + d(12, 3);
            if (b.id().equals("snap")) {
                int dotSize = d(8, 2);
                SettingsTheme.rect(ctx, tx, b.y() + (b.h() - dotSize) / 2, dotSize, dotSize,
                    snapEnabled ? SettingsTheme.DOT_ON : SettingsTheme.DOT_OFF);
                tx += dotSize + gap;
                textColor = snapEnabled ? SettingsTheme.TEXT : SettingsTheme.TEXT_FAINT;
            }
            int textY = centerTextY(b.y(), b.h());
            if (b.id().equals("designs") || b.id().equals("anim")) {
                // Two-tone: the left word is a label, the right one the live value.
                boolean designs = b.id().equals("designs");
                String head = designs ? "DESIGN" : "ANIM";
                SettingsTheme.text(ctx, this.font, head, tx, textY,
                    values ? SettingsTheme.TEXT_FAINT : SettingsTheme.TEXT_LABEL);
                if (values) {
                    SettingsTheme.text(ctx, this.font, designs ? designName() : animName(),
                        tx + SettingsTheme.textWidth(this.font, head) + gap, textY,
                        designs ? SettingsTheme.TEXT : animColor());
                }
                // Without the caret the button reads as a label, not as something you
                // click -- which is exactly how the animation type went unfound.
                int size = Math.max(2, d(4, 2));
                caretDown(ctx, b.x() + b.w() - d(10, 4) - size, b.y() + (b.h() - size) / 2, size,
                    SettingsTheme.TEXT_MUTED);
            } else {
                SettingsTheme.text(ctx, this.font, b.label(), tx, textY, textColor);
            }
        }
    }

    // --- Left rail ---------------------------------------------------------

    /** The add-element tiles, as {@code {label, size caption}} plus their rects. */
    private record AddTile(String id, String label, String size, int x, int y, int w, int h) {}

    private List<AddTile> addTiles() {
        List<AddTile> out = new ArrayList<>();
        int x = leftX + gap;
        int w = leftW - gap * 2;
        int colW = (w - gap) / 2;
        int y = tilesY;

        out.add(new AddTile("key", "KEY", "21 x 21", x, y, colW, addTileH));
        out.add(new AddTile("mod", "MODIFIER", "33 x 13", x + colW + gap, y, w - colW - gap, addTileH));
        y += addTileH + gap;
        out.add(new AddTile("lmb", "MOUSE L", "33 x 21", x, y, colW, addTileH));
        out.add(new AddTile("rmb", "MOUSE R", "33 x 21", x + colW + gap, y, w - colW - gap, addTileH));
        y += addTileH + gap;
        out.add(new AddTile("space", "SPACE BAR", "67 x 13", x, y, w, addTileH));
        return out;
    }

    /** The layout presets, in rail order. */
    private static final String[] PRESETS = { "WASD", "FULL", "MOUSE" };

    private int[] presetRect(int index) {
        int w = leftW - gap * 2;
        int colW = (w - gap * (PRESETS.length - 1)) / PRESETS.length;
        return new int[] { leftX + gap + index * (colW + gap), presetsY, colW, presetBtnH };
    }

    private void renderLeftPanel(GuiGraphicsExtractor ctx, int mx, int my) {
        SettingsTheme.rect(ctx, leftX, bodyY, leftW, bodyH, SettingsTheme.SIDE_BG);
        SettingsTheme.rect(ctx, leftX + leftW - 1, bodyY, 1, bodyH, SettingsTheme.BORDER);

        railHeader(ctx, bodyY, "ADD ELEMENT", "CLICK TO PLACE");

        for (AddTile tile : addTiles()) {
            boolean hov = SettingsTheme.inside(mx, my, tile.x(), tile.y(), tile.w(), tile.h());
            SettingsTheme.button(ctx, tile.x(), tile.y(), tile.w(), tile.h(), hov);
            int cx = tile.x() + tile.w() / 2;
            int textY = tile.y() + tile.h() / 2 - this.font.lineHeight;
            SettingsTheme.centeredText(ctx, this.font,
                SettingsTheme.truncate(this.font, tile.label(), tile.w() - gap), cx, textY,
                SettingsTheme.TEXT_LABEL);
            SettingsTheme.centeredText(ctx, this.font, tile.size(), cx, textY + this.font.lineHeight + 2,
                SettingsTheme.TEXT_FAINT);
        }

        railHeader(ctx, layersHeadY, "LAYERS", layout().size() + " ITEMS");
        renderLayers(ctx, mx, my);

        railHeader(ctx, presetsHeadY, "PRESETS", "");
        String[] presets = PRESETS;
        for (int i = 0; i < presets.length; i++) {
            int[] r = presetRect(i);
            boolean hov = SettingsTheme.inside(mx, my, r[0], r[1], r[2], r[3]);
            SettingsTheme.button(ctx, r[0], r[1], r[2], r[3], hov);
            SettingsTheme.centeredText(ctx, this.font,
                SettingsTheme.truncate(this.font, presets[i], r[2] - 2), r[0] + r[2] / 2,
                centerTextY(r[1], r[3]), SettingsTheme.TEXT_LABEL);
        }
    }

    private void railHeader(GuiGraphicsExtractor ctx, int y, String left, String right) {
        SettingsTheme.rect(ctx, leftX, y, leftW - 1, sideHeadH, SettingsTheme.SIDE_HEAD_BG);
        SettingsTheme.rect(ctx, leftX, y, leftW - 1, 1, SettingsTheme.BORDER);
        SettingsTheme.rect(ctx, leftX, y + sideHeadH - 1, leftW - 1, 1, SettingsTheme.BORDER);
        int textY = centerTextY(y, sideHeadH);
        SettingsTheme.text(ctx, this.font, left, leftX + pad, textY, SettingsTheme.TEXT_MUTED);
        if (!right.isEmpty()) {
            SettingsTheme.rightText(ctx, this.font, right, leftX + leftW - pad, textY, SettingsTheme.TEXT_FAINT);
        }
    }

    private int layersContentH() {
        return layout().size() * layerRowH;
    }

    private int maxLayersScroll() {
        return Math.max(0, layersContentH() - layersH);
    }

    private void renderLayers(GuiGraphicsExtractor ctx, int mx, int my) {
        if (layersH <= 0) return;
        layersScroll = Math.max(0, Math.min(layersScroll, maxLayersScroll()));

        ctx.enableScissor(leftX, layersY, leftX + leftW - 1, layersY + layersH);
        ctx.pose().pushMatrix();
        ctx.pose().translate(0f, -layersScroll);

        List<SimpleCPSConfig.KeyButtonData> keys = layout();
        for (int i = 0; i < keys.size(); i++) {
            SimpleCPSConfig.KeyButtonData btn = keys.get(i);
            int y = layersY + i * layerRowH;
            boolean selected = selection.contains(btn);
            boolean hov = SettingsTheme.inside(mx, my + layersScroll, leftX, y, leftW - 1, layerRowH);

            if (selected) {
                SettingsTheme.rect(ctx, leftX + gap / 2, y, leftW - 1 - gap, layerRowH, SettingsTheme.SEL_BG);
                SettingsTheme.frame(ctx, leftX + gap / 2, y, leftW - 1 - gap, layerRowH, SettingsTheme.SEL_BORDER);
            } else if (hov) {
                SettingsTheme.rect(ctx, leftX + gap / 2, y, leftW - 1 - gap, layerRowH, SettingsTheme.IDLE_BG);
            }

            int[] dot = layerDotRect(y);
            SettingsTheme.sunken(ctx, dot[0], dot[1], dot[2], dot[3], SettingsTheme.FIELD_BG, SettingsTheme.FIELD_RIM);
            int inner = d(8, 2);
            SettingsTheme.rect(ctx, dot[0] + (dot[2] - inner) / 2, dot[1] + (dot[3] - inner) / 2, inner, inner,
                btn.hidden ? SettingsTheme.DOT_OFF : SettingsTheme.DOT_ON);

            int textY = centerTextY(y, layerRowH);
            int nameX = dot[0] + dot[2] + gap;
            String name = SettingsTheme.up(displayLabel(btn));
            int nameColor = btn.hidden ? SettingsTheme.NAME_OFF
                : selected ? SettingsTheme.TEXT_STRONG : SettingsTheme.NAME_ON;

            String type = typeOf(btn);
            String size = btn.w + "x" + btn.h;
            int typeW = SettingsTheme.textWidth(this.font, type);
            int sizeW = SettingsTheme.textWidth(this.font, size);
            int nameRoom = Math.max(0, (leftX + leftW - pad - typeW - gap - sizeW - gap) - nameX);

            SettingsTheme.text(ctx, this.font, SettingsTheme.truncate(this.font, name, nameRoom),
                nameX, textY, nameColor);
            SettingsTheme.rightText(ctx, this.font, type, leftX + leftW - pad, textY, SettingsTheme.TEXT_FAINT);
            SettingsTheme.rightText(ctx, this.font, size, leftX + leftW - pad - typeW - gap, textY,
                SettingsTheme.TEXT_GHOST);
        }

        ctx.pose().popMatrix();
        ctx.disableScissor();

        renderScrollbar(ctx, leftX + leftW - 1 - scrollbarW, layersY, layersH, layersContentH(), layersScroll);
    }

    private int[] layerDotRect(int rowY) {
        int size = d(20, 7);
        return new int[] { leftX + gap, rowY + (layerRowH - size) / 2, size, size };
    }

    private void renderScrollbar(GuiGraphicsExtractor ctx, int x, int y, int viewH, int contentH, int scroll) {
        if (contentH <= viewH || viewH <= 0) return;
        SettingsTheme.rect(ctx, x, y, scrollbarW, viewH, SettingsTheme.SCROLL_TRACK);
        int thumbH = Math.max(d(20, 6), viewH * viewH / contentH);
        int travel = viewH - thumbH;
        int thumbY = y + (contentH == viewH ? 0 : Math.round(travel * (scroll / (float) (contentH - viewH))));
        SettingsTheme.rect(ctx, x, thumbY, scrollbarW, thumbH, SettingsTheme.SCROLL_THUMB);
    }


    // --- Canvas ------------------------------------------------------------

    /** A key's rect on the canvas, in virtual screen coordinates. */
    private int[] keyRect(SimpleCPSConfig.KeyButtonData btn) {
        return new int[] {
            originX + (btn.x + glideX(btn)) * zoom,
            originY + (btn.y + glideY(btn)) * zoom,
            btn.w * zoom,
            btn.h * zoom
        };
    }

    /** Topmost visible key under the cursor, or null. */
    private SimpleCPSConfig.KeyButtonData keyAt(int vx, int vy) {
        List<SimpleCPSConfig.KeyButtonData> keys = layout();
        for (int i = keys.size() - 1; i >= 0; i--) {
            SimpleCPSConfig.KeyButtonData btn = keys.get(i);
            if (btn.hidden) continue;
            int[] r = keyRect(btn);
            if (SettingsTheme.inside(vx, vy, r[0], r[1], r[2], r[3])) return btn;
        }
        return null;
    }

    private void renderCanvas(GuiGraphicsExtractor ctx, int mx, int my) {
        // Head
        SettingsTheme.rect(ctx, canvasX, bodyY, canvasW, canvasHeadH, SettingsTheme.PANEL_BG);
        SettingsTheme.rect(ctx, canvasX, bodyY + canvasHeadH - 1, canvasW, 1, SettingsTheme.BORDER);

        int titleY = bodyY + (canvasHeadH - this.font.lineHeight * 2 - 4) / 2;
        SettingsTheme.text(ctx, this.font, "LAYOUT CANVAS", canvasX + pad, titleY, SettingsTheme.TEXT);
        SettingsTheme.text(ctx, this.font,
            SAFE_W + " x " + SAFE_H + " SAFE AREA - SHOWN AT " + zoom + "x - IN-GAME PIXELS",
            canvasX + pad, titleY + this.font.lineHeight + 4, SettingsTheme.TEXT_DIM);

        String status = statusText();
        int statusW = SettingsTheme.textWidth(this.font, status) + pad * 2;
        int statusX = canvasX + canvasW - pad - statusW;
        int statusY = bodyY + (canvasHeadH - ctrlH) / 2;
        if (statusX > canvasX + pad) {
            SettingsTheme.sunken(ctx, statusX, statusY, statusW, ctrlH,
                SettingsTheme.FIELD_BG, SettingsTheme.FIELD_RIM);
            SettingsTheme.text(ctx, this.font, status, statusX + pad, centerTextY(statusY, ctrlH),
                SettingsTheme.TEXT_MUTED);
        }

        if (canvasW <= 0 || canvasH <= 0) return;

        // Body. Scissored so a safe area larger than the pane is cropped rather than
        // painted over the rails.
        ctx.enableScissor(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH);
        SettingsTheme.rect(ctx, canvasX, canvasY, canvasW, canvasH, CANVAS_BG);

        int cell = d(16, 2);
        for (int gx = 0; gx * cell < canvasW; gx++) {
            for (int gy = 0; gy * cell < canvasH; gy++) {
                if (((gx + gy) & 1) != 0) continue;
                SettingsTheme.rect(ctx, canvasX + gx * cell, canvasY + gy * cell, cell, cell, CHECKER);
            }
        }

        SettingsTheme.frame(ctx, safeX, safeY, SAFE_W * zoom, SAFE_H * zoom, SAFE_BORDER);
        int chipH = d(22, 8);
        int chipY = safeY - chipH - 2;
        if (chipY > canvasY) {
            String chip = "SAFE AREA - " + SAFE_W + " x " + SAFE_H;
            int chipW = SettingsTheme.textWidth(this.font, chip) + gap * 2;
            SettingsTheme.rect(ctx, safeX, chipY, chipW, chipH, SettingsTheme.FIELD_BG);
            SettingsTheme.frame(ctx, safeX, chipY, chipW, chipH, SAFE_BORDER);
            SettingsTheme.text(ctx, this.font, chip, safeX + gap, centerTextY(chipY, chipH),
                SettingsTheme.TEXT_GHOST);
        }

        renderGuides(ctx);

        // The keys are drawn exactly as the HUD draws them, through the same
        // KeystrokesRender path. Picking a design is pointless if the canvas answers
        // with a generic gray box instead of what the game will show.
        int edge = Math.max(1, zoom);
        SimpleCPSConfig config = config();
        int accent = config.keystrokesPressedColor | 0xFF000000;
        int bg = (config.keystrokesBackgroundOpacity << 24)
            | (config.keystrokesBackgroundColor & 0x00FFFFFF);

        if (globalDesign() == KeystrokesDesign.TIMELINE) {
            renderTimelineNotice(ctx);
        } else {
            ctx.pose().pushMatrix();
            ctx.pose().translate(originX, originY);
            ctx.pose().scale(zoom, zoom);
            if (config.keystrokesBoard) renderCanvasBoard(ctx);
            for (SimpleCPSConfig.KeyButtonData btn : layout()) {
                if (btn.hidden) continue;
                // KeystrokesRender reads btn.x/btn.y directly, so the glide offset has
                // to ride on the matrix rather than on the data.
                ctx.pose().pushMatrix();
                ctx.pose().translate(glideX(btn), glideY(btn));

                KeystrokesRender.KeyStyle style = KeystrokesRender.KeyStyle.of(config, btn);
                // At rest: the canvas is for arranging, and keys flickering through
                // their press animation while you drag them is noise, not preview.
                // The gallery is where the pressed state is shown.
                String override = (mode == Mode.KEYBIND && selection.contains(btn)) ? "..." : null;
                KeystrokesRender.drawKey(ctx, this.font, btn, style, REST, bg,
                    keyTextColor(btn), accent, false, btn.showCps && btn.isMouse ? "0" : null,
                    override);

                ctx.pose().popMatrix();
            }
            ctx.pose().popMatrix();

            // Selection chrome last and outside the key, so it cannot be mistaken for
            // part of the design or be painted over by one.
            for (SimpleCPSConfig.KeyButtonData btn : layout()) {
                if (btn.hidden || !selection.contains(btn)) continue;
                int[] r = keyRect(btn);
                thickFrame(ctx, r[0] - edge * 2, r[1] - edge * 2,
                    r[2] + edge * 4, r[3] + edge * 4, edge, KEY_SEL_BORDER);
                thickFrame(ctx, r[0] - edge * 4, r[1] - edge * 4,
                    r[2] + edge * 8, r[3] + edge * 8, edge, KEY_SEL_OUTLINE);
                if (mode == Mode.LABEL && selection.size() == 1) {
                    renderLabelEditMarker(ctx, btn, r);
                }
            }
        }

        if (dragging && selection.size() == 1) {
            renderDragTag(ctx, selection.get(0));
        }

        if (marqueeActive && marqueeMoved) {
            int x1 = Math.min(marqueeStartX, marqueeX);
            int y1 = Math.min(marqueeStartY, marqueeY);
            int x2 = Math.max(marqueeStartX, marqueeX);
            int y2 = Math.max(marqueeStartY, marqueeY);
            SettingsTheme.rect(ctx, x1, y1, x2 - x1, y2 - y1, MARQUEE_FILL);
            thickFrame(ctx, x1, y1, x2 - x1, y2 - y1, Math.max(1, d(2, 1)), ACCENT);
        }

        if (mode == Mode.LABEL || mode == Mode.KEYBIND) {
            renderHintBar(ctx);
        }

        ctx.disableScissor();
    }

    /** Dashed alignment guides, drawn a little past the safe area so they read as guides. */
    private void renderGuides(GuiGraphicsExtractor ctx) {
        int dash = d(6, 2);
        int over = d(8, 2);
        if (snapX != null) {
            int gx = originX + snapX * zoom;
            for (int y = safeY - over; y < safeY + SAFE_H * zoom + over; y += dash * 2) {
                SettingsTheme.rect(ctx, gx, y, Math.max(1, zoom), dash, ACCENT);
            }
        }
        if (snapY != null) {
            int gy = originY + snapY * zoom;
            for (int x = safeX - over; x < safeX + SAFE_W * zoom + over; x += dash * 2) {
                SettingsTheme.rect(ctx, x, gy, dash, Math.max(1, zoom), ACCENT);
            }
        }

        // Label guides run across their own key only, so it stays obvious which key
        // the magnet just caught.
        if (labelGuideKey == null) return;
        int[] key = keyRect(labelGuideKey);
        int lip = Math.max(2, zoom * 3);
        if (labelGuideX != null) {
            int gx = originX + labelGuideX * zoom;
            for (int y = key[1] - lip; y < key[1] + key[3] + lip; y += dash * 2) {
                SettingsTheme.rect(ctx, gx, y, Math.max(1, zoom), dash, ACCENT);
            }
        }
        if (labelGuideY != null) {
            int gy = originY + labelGuideY * zoom;
            for (int x = key[0] - lip; x < key[0] + key[2] + lip; x += dash * 2) {
                SettingsTheme.rect(ctx, x, gy, dash, Math.max(1, zoom), ACCENT);
            }
        }
    }

    /** Border of {@code t} pixels drawn just inside the rect. */
    private static void thickFrame(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int t, int color) {
        if (w <= 0 || h <= 0) return;
        SettingsTheme.rect(ctx, x, y, w, t, color);
        SettingsTheme.rect(ctx, x, y + h - t, w, t, color);
        SettingsTheme.rect(ctx, x, y, t, h, color);
        SettingsTheme.rect(ctx, x + w - t, y, t, h, color);
    }

    /** The board behind the cluster, in canvas space. */
    private void renderCanvasBoard(GuiGraphicsExtractor ctx) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (SimpleCPSConfig.KeyButtonData btn : layout()) {
            if (btn.hidden) continue;
            minX = Math.min(minX, btn.x);
            minY = Math.min(minY, btn.y);
            maxX = Math.max(maxX, btn.x + btn.w);
            maxY = Math.max(maxY, btn.y + btn.h);
        }
        if (maxX == Integer.MIN_VALUE) return;
        KeystrokesDesign.drawBoard(ctx, minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * What the canvas shows under the timeline design. It lays the keys out itself
     * and ignores their positions, so drawing them here would invite the player to
     * arrange something the HUD will throw away.
     */
    private void renderTimelineNotice(GuiGraphicsExtractor ctx) {
        int w = Math.min(canvasW - gap * 4, d(300, 110));
        int x = canvasX + (canvasW - w) / 2;
        List<String> lines = SettingsTheme.wrap(this.font,
            "THE TIMELINE ARRANGES THE KEYS ITSELF, ONE LANE EACH, SO POSITION AND SIZE "
                + "DO NOTHING HERE. LABELS, KEYBINDS AND COLORS STILL APPLY.",
            w - pad * 2);
        int h = pad * 2 + this.font.lineHeight + gap + lines.size() * (this.font.lineHeight + 3);
        int y = canvasY + (canvasH - h) / 2;
        SettingsTheme.card(ctx, x, y, w, h);
        SettingsTheme.text(ctx, this.font, "TIMELINE DESIGN", x + pad, y + pad, SettingsTheme.TEXT_MUTED);
        int ly = y + pad + this.font.lineHeight + gap;
        for (String line : lines) {
            SettingsTheme.text(ctx, this.font, line, x + pad, ly, SettingsTheme.TEXT_GHOST);
            ly += this.font.lineHeight + 3;
        }
    }

    private void renderLabelEditMarker(GuiGraphicsExtractor ctx, SimpleCPSConfig.KeyButtonData btn, int[] r) {
        float[] size = KeystrokesRender.labelSize(this.font, btn);
        float[] origin = KeystrokesRender.labelOrigin(this.font, btn);
        int lx = r[0] + Math.round((origin[0] - btn.x) * zoom) - 2;
        int ly = r[1] + Math.round((origin[1] - btn.y) * zoom) - 2;
        int lw = Math.round(size[0] * zoom) + 4;
        int lh = Math.round(size[1] * zoom) + 4;

        // Dashed rather than solid: a solid box around a few characters reads as a
        // second key rather than as a caret.
        int dash = Math.max(2, d(4, 2));
        for (int x = lx; x < lx + lw; x += dash * 2) {
            int seg = Math.min(dash, lx + lw - x);
            SettingsTheme.rect(ctx, x, ly, seg, 1, ACCENT);
            SettingsTheme.rect(ctx, x, ly + lh - 1, seg, 1, ACCENT);
        }
        for (int y = ly; y < ly + lh; y += dash * 2) {
            int seg = Math.min(dash, ly + lh - y);
            SettingsTheme.rect(ctx, lx, y, 1, seg, ACCENT);
            SettingsTheme.rect(ctx, lx + lw - 1, y, 1, seg, ACCENT);
        }
        if ((System.currentTimeMillis() / 500) % 2 == 0) {
            SettingsTheme.rect(ctx, lx + lw, ly + lh / 5, Math.max(1, zoom), lh * 3 / 5, SettingsTheme.CARET);
        }
    }

    private void renderDragTag(GuiGraphicsExtractor ctx, SimpleCPSConfig.KeyButtonData btn) {
        int[] r = keyRect(btn);
        String tag = btn.w + " x " + btn.h + "   X " + btn.x + " Y " + btn.y;
        int h = d(20, 8);
        int w = SettingsTheme.textWidth(this.font, tag) + gap * 2;
        int y = r[1] - h - 2;
        if (y < canvasY) y = r[1] + r[3] + 2;
        SettingsTheme.rect(ctx, r[0], y, w, h, ACCENT);
        SettingsTheme.text(ctx, this.font, tag, r[0] + gap, centerTextY(y, h), SettingsTheme.DONE_TEXT);
    }

    private void renderHintBar(GuiGraphicsExtractor ctx) {
        String hint = mode == Mode.KEYBIND
            ? "PRESS ANY KEY TO BIND - ESC TO CANCEL"
            : "TYPING EDITS THE LABEL - ARROWS MOVE IT - ENTER TO FINISH";
        int h = d(34, 12);
        int w = SettingsTheme.textWidth(this.font, hint) + pad * 2;
        int x = canvasX + (canvasW - w) / 2;
        int y = canvasY + canvasH - h - gap * 2;
        SettingsTheme.sunken(ctx, x, y, w, h, SettingsTheme.FOCUS_BG, SettingsTheme.FIELD_RIM);
        SettingsTheme.text(ctx, this.font, hint, x + pad, centerTextY(y, h), SettingsTheme.TEXT_LABEL);
    }

    private String statusText() {
        SimpleCPSConfig.KeyButtonData single = single();
        if (single != null) {
            return SettingsTheme.up(displayLabel(single)) + " - " + single.w + "x" + single.h
                + " - X " + single.x + " Y " + single.y;
        }
        if (selection.size() > 1) return selection.size() + " BUTTONS SELECTED";
        return layout().size() + " BUTTONS - NO SELECTION";
    }

    /** The key's text color as the HUD would draw it unpressed. */
    private int keyTextColor(SimpleCPSConfig.KeyButtonData btn) {
        int color = (btn.btnColor != -1 && btn.btnColor != 0) ? btn.btnColor : config().keystrokesColor;
        return color | 0xFF000000;
    }


    // --- Inspector ---------------------------------------------------------

    /** One inspector line: a section heading when the id starts with '#'. */
    private record InspRow(String id, int h) {}

    private List<InspRow> inspectorRows() {
        List<InspRow> rows = new ArrayList<>();
        if (selection.isEmpty()) return rows;
        rows.add(new InspRow("#IDENTITY", sectionH));
        rows.add(new InspRow("keybind", rowH));
        rows.add(new InspRow("label", rowH));
        rows.add(new InspRow("#GEOMETRY", sectionH));
        rows.add(new InspRow("position", rowH));
        rows.add(new InspRow("size", rowH));
        rows.add(new InspRow("#LABEL", sectionH));
        rows.add(new InspRow("placement", rowH));
        rows.add(new InspRow("content", rowH));
        rows.add(new InspRow("scale", rowH));
        // Only line mode has a length to set; in text mode the label's own width is it.
        if (lineMode()) rows.add(new InspRow("linewidth", rowH));
        rows.add(new InspRow("style", rowH));
        rows.add(new InspRow("#COLOR", sectionH));
        rows.add(new InspRow("color", rowH));
        rows.add(new InspRow("pressed", rowH));
        rows.add(new InspRow("#DESIGN", sectionH));
        rows.add(new InspRow("kdesign", rowH));
        rows.add(new InspRow("kmotion", rowH));
        rows.add(new InspRow("kfill", rowH));
        // The direction row only means something for a fill that has one, and an
        // always-present "N/A" row is just a row that never does anything.
        // Only worth a row when something the key actually runs reads a direction.
        KeystrokesAnim.Fill own = keyFill();
        if (own != null ? own.directional() : directionMatters()) {
            rows.add(new InspRow("kdirection", rowH));
        }
        rows.add(new InspRow("#BEHAVIOR", sectionH));
        rows.add(new InspRow("cps", rowH));
        rows.add(new InspRow("anim", rowH));
        return rows;
    }

    private int inspectorTop() {
        return bodyY + sideHeadH;
    }

    private int inspectorViewH() {
        return Math.max(0, footerY - shortcutsH - inspectorTop());
    }

    private int inspectorContentH() {
        int total = 0;
        for (InspRow row : inspectorRows()) total += row.h();
        return selection.isEmpty() ? d(120, 40) : total;
    }

    private int maxInspectorScroll() {
        return Math.max(0, inspectorContentH() - inspectorViewH());
    }

    private int inspRight() {
        return rightX + rightW - d(18, 4);
    }

    private int inspLabelX() {
        return rightX + d(12, 3);
    }

    private int ctrlY(int rowY) {
        return rowY + (rowH - ctrlH) / 2;
    }

    private int stepperW() {
        return stepBtn * 2 + stepFieldW;
    }

    /** The X/Y (or W/H) stepper pair on a geometry row. */
    private int[][] pairStepperRects(int rowY) {
        int w = stepperW();
        int sep = Math.max(2, gap * 3 / 4);
        int second = inspRight() - w;
        int first = second - sep - w;
        return new int[][] {
            { first, ctrlY(rowY), w, ctrlH },
            { second, ctrlY(rowY), w, ctrlH }
        };
    }

    /** Whether the selection is in line mode, judged by its leader. */
    private boolean lineMode() {
        return !selection.isEmpty() && selection.get(0).labelLine;
    }

    private int[] scaleStepperRect(int rowY) {
        int w = stepBtn * 2 + d(52, 18);
        return new int[] { inspRight() - w, ctrlY(rowY), w, ctrlH };
    }

    private int[] styleBtnRect(int rowY, int index) {
        int sb = d(27, 9);
        int sep = d(5, 1);
        int x0 = inspRight() - (sb * 4 + sep * 3);
        return new int[] { x0 + index * (sb + sep), ctrlY(rowY), sb, ctrlH };
    }

    private int[] segRect(int rowY, int index) {
        int w0 = SettingsTheme.textWidth(this.font, "TEXT") + d(16, 4);
        int w1 = SettingsTheme.textWidth(this.font, "LINE") + d(16, 4);
        int sep = d(5, 1);
        int x1 = inspRight() - w1;
        int x0 = x1 - sep - w0;
        return index == 0
            ? new int[] { x0, ctrlY(rowY), w0, ctrlH }
            : new int[] { x1, ctrlY(rowY), w1, ctrlH };
    }

    private int[] centerBtnRect(int rowY) {
        int w = SettingsTheme.textWidth(this.font, "CENTER") + d(16, 4);
        return new int[] { inspRight() - w, ctrlY(rowY), w, ctrlH };
    }

    private int[] toggleRect(int rowY) {
        // Wide enough that the knob and the ON/OFF label each get their own half.
        // At the mock's 68 the knob ran over the "OFF" text at small UI scales.
        int w = d(86, 44);
        return new int[] { inspRight() - w, ctrlY(rowY), w, ctrlH };
    }

    private int[] keybindRect(int rowY) {
        int w = Math.max(d(84, 28), SettingsTheme.textWidth(this.font, keybindLabel()) + d(16, 4));
        return new int[] { inspRight() - w, ctrlY(rowY), w, ctrlH };
    }

    private int[] labelFieldRect(int rowY) {
        int w = d(140, 40);
        return new int[] { inspRight() - w, ctrlY(rowY), w, ctrlH };
    }

    private int[] colorBtnRect(int rowY, boolean pressed) {
        String hex = colorHexLabel(pressed);
        int w = d(16, 6) + gap + SettingsTheme.textWidth(this.font, hex) + d(16, 4);
        return new int[] { inspRight() - w, ctrlY(rowY), w, ctrlH };
    }

    private String keybindLabel() {
        SimpleCPSConfig.KeyButtonData k = single();
        if (k == null) return "MIXED";
        if (mode == Mode.KEYBIND) return "PRESS A KEY";
        return k.isMouse ? (k.keyCode == 0 ? "MOUSE L" : k.keyCode == 1 ? "MOUSE R" : "MOUSE " + k.keyCode)
            : keyName(k.keyCode);
    }

    private String colorHexLabel(boolean pressed) {
        SimpleCPSConfig.KeyButtonData k = single();
        if (k == null) return "MIXED";
        int value = pressed ? k.btnPressedColor : k.btnColor;
        return (value == -1 || value == 0) ? "GLOBAL" : String.format("#%06X", value & 0xFFFFFF);
    }

    private int colorSwatchValue(boolean pressed) {
        SimpleCPSConfig.KeyButtonData k = single();
        int fallback = pressed ? config().keystrokesPressedColor : config().keystrokesColor;
        if (k == null) return fallback | 0xFF000000;
        int value = pressed ? k.btnPressedColor : k.btnColor;
        return ((value == -1 || value == 0) ? fallback : value) | 0xFF000000;
    }

    private void renderInspector(GuiGraphicsExtractor ctx, int mx, int my) {
        SettingsTheme.rect(ctx, rightX, bodyY, rightW, bodyH, SettingsTheme.SIDE_BG);
        SettingsTheme.rect(ctx, rightX, bodyY, 1, bodyH, SettingsTheme.BORDER);

        SettingsTheme.rect(ctx, rightX + 1, bodyY, rightW - 1, sideHeadH, SettingsTheme.SIDE_HEAD_BG);
        SettingsTheme.rect(ctx, rightX + 1, bodyY + sideHeadH - 1, rightW - 1, 1, SettingsTheme.BORDER);
        int headY = centerTextY(bodyY, sideHeadH);
        SettingsTheme.text(ctx, this.font, "INSPECTOR", rightX + pad, headY, SettingsTheme.TEXT_MUTED);
        SettingsTheme.rightText(ctx, this.font,
            selection.isEmpty() ? "-" : selection.size() + " SELECTED",
            rightX + rightW - pad, headY, SettingsTheme.TEXT_FAINT);

        int viewH = inspectorViewH();
        if (viewH > 0) {
            inspectorScroll = Math.max(0, Math.min(inspectorScroll, maxInspectorScroll()));
            ctx.enableScissor(rightX + 1, inspectorTop(), rightX + rightW, inspectorTop() + viewH);
            ctx.pose().pushMatrix();
            ctx.pose().translate(0f, -inspectorScroll);

            if (selection.isEmpty()) {
                renderEmptyInspector(ctx);
            } else {
                int y = inspectorTop();
                for (InspRow row : inspectorRows()) {
                    renderInspectorRow(ctx, row, y, mx, my + inspectorScroll);
                    y += row.h();
                }
            }

            ctx.pose().popMatrix();
            ctx.disableScissor();
            renderScrollbar(ctx, rightX + rightW - scrollbarW, inspectorTop(), viewH,
                inspectorContentH(), inspectorScroll);
        }

        if (shortcutsH > 0) renderShortcuts(ctx, mx, my);
    }

    private void renderEmptyInspector(GuiGraphicsExtractor ctx) {
        int x = rightX + pad;
        int w = rightW - pad * 2;
        int y = inspectorTop() + pad;
        List<String> lines = SettingsTheme.wrap(this.font,
            "PICK A BUTTON ON THE CANVAS OR A ROW IN LAYERS. ITS KEYBIND, LABEL, GEOMETRY AND COLORS ALL LIVE HERE.",
            w - pad * 2);
        int h = pad * 2 + this.font.lineHeight + gap + lines.size() * (this.font.lineHeight + 3);
        SettingsTheme.card(ctx, x, y, w, h);
        SettingsTheme.text(ctx, this.font, "NOTHING SELECTED", x + pad, y + pad, SettingsTheme.TEXT_MUTED);
        int ly = y + pad + this.font.lineHeight + gap;
        for (String line : lines) {
            SettingsTheme.text(ctx, this.font, line, x + pad, ly, SettingsTheme.TEXT_GHOST);
            ly += this.font.lineHeight + 3;
        }
    }

    /** The fold-away header bar above the cheat sheet. */
    private int[] shortcutsHeaderRect() {
        return new int[] { rightX + 1, footerY - shortcutsH, rightW - 1, sectionH };
    }

    /**
     * A two-column table rather than a right-aligned list: both columns start at a
     * fixed x, which is what stops eight ragged rows reading as a noticeboard.
     */
    private void renderShortcuts(GuiGraphicsExtractor ctx, int mx, int my) {
        int[] head = shortcutsHeaderRect();
        boolean folded = shortcutsH <= sectionH;
        boolean hov = SettingsTheme.inside(mx, my, head[0], head[1], head[2], head[3]);

        SettingsTheme.rect(ctx, head[0], head[1], head[2], head[3],
            hov ? SettingsTheme.BTN_HOVER : SettingsTheme.CONTENT_HEAD);
        SettingsTheme.rect(ctx, head[0], head[1], head[2], 1, SettingsTheme.BORDER);
        SettingsTheme.rect(ctx, head[0], head[1] + head[3] - 1, head[2], 1, SettingsTheme.BORDER);
        SettingsTheme.text(ctx, this.font, "SHORTCUTS", rightX + d(12, 3),
            centerTextY(head[1], head[3]), SettingsTheme.TEXT_FAINT);

        int caret = Math.max(2, d(4, 2));
        int caretX = rightX + rightW - pad - caret * 2;
        int caretY = head[1] + (head[3] - caret) / 2;
        if (folded) caretUp(ctx, caretX, caretY, caret, SettingsTheme.TEXT_MUTED);
        else caretDown(ctx, caretX, caretY, caret, SettingsTheme.TEXT_MUTED);

        if (folded) return;

        SettingsTheme.rect(ctx, rightX + 1, head[1] + head[3], rightW - 1,
            shortcutsH - head[3], SettingsTheme.CARD_BG);

        int leftX0 = rightX + pad;
        int colX = leftX0 + (rightW - pad * 2) / 2;
        int rowRight = rightX + rightW - pad;
        int y = head[1] + head[3];
        for (int i = 0; i < SHORTCUTS.length; i++) {
            if (i > 0) {
                SettingsTheme.rect(ctx, rightX + pad, y, rightW - pad * 2, 1, SettingsTheme.ROW_SEPARATOR);
            }
            int textY = y + (shortcutRowH - this.font.lineHeight) / 2 + 1;
            SettingsTheme.text(ctx, this.font,
                SettingsTheme.truncate(this.font, SHORTCUTS[i][0], colX - leftX0 - gap),
                leftX0, textY, SettingsTheme.TEXT_MUTED);
            SettingsTheme.text(ctx, this.font,
                SettingsTheme.truncate(this.font, SHORTCUTS[i][1], rowRight - colX),
                colX, textY, SettingsTheme.TEXT_GHOST);
            y += shortcutRowH;
        }
    }

    /**
     * The design the selection wears. A null per-key override means the global one,
     * so the row can show what the key actually looks like and still say GLOBAL.
     */
    private KeystrokesDesign keyDesign() {
        SimpleCPSConfig.KeyButtonData k = single();
        if (k == null || k.design == null) return globalDesign();
        return k.design;
    }

    /**
     * The selection's own animation override, or {@code null} when it follows the
     * module.
     *
     * <p>A per-key override names exactly one animation and means only that one — it
     * replaces the module's set rather than adding to it, so a key can opt out of an
     * effect its neighbors have.
     */
    private KeystrokesAnim.Motion keyMotion() {
        SimpleCPSConfig.KeyButtonData k = single();
        return k == null ? null : k.motion;
    }

    private KeystrokesAnim.Fill keyFill() {
        SimpleCPSConfig.KeyButtonData k = single();
        return k == null ? null : k.fill;
    }

    /** What a row shows when the key defers: the module's set, summarized. */
    private String globalSummary(String id) {
        List<?> values = id.equals("kmotion") ? globalMotions() : globalFills();
        if (values.isEmpty()) return "NONE";
        String lead = SettingsTheme.up(id.equals("kmotion")
            ? ((KeystrokesAnim.Motion) values.get(0)).display()
            : ((KeystrokesAnim.Fill) values.get(0)).display());
        return values.size() == 1 ? lead : lead + " +" + (values.size() - 1);
    }

    private KeystrokesAnim.Direction keyDirection() {
        SimpleCPSConfig.KeyButtonData k = single();
        KeystrokesAnim.Direction dir = k == null || k.direction == null
            ? config().keystrokesFillDirection : k.direction;
        return KeystrokesAnim.coerce(keyFill(), dir);
    }

    /** True when every selected key defers to the module setting for this row. */
    private boolean usesGlobal(String id) {
        if (selection.isEmpty()) return true;
        for (SimpleCPSConfig.KeyButtonData b : selection) {
            Object value = switch (id) {
                case "kdesign" -> b.design;
                case "kmotion" -> b.motion;
                case "kfill" -> b.fill;
                default -> b.direction;
            };
            if (value != null) return false;
        }
        return true;
    }

    /** The value control on a design row: same geometry as the color button. */
    private int[] designBtnRect(int rowY) {
        int w = d(140, 52);
        return new int[] { inspRight() - w, ctrlY(rowY), w, ctrlH };
    }

    private void renderInspectorRow(GuiGraphicsExtractor ctx, InspRow row, int y, int mx, int my) {
        if (row.id().startsWith("#")) {
            SettingsTheme.rect(ctx, rightX + 1, y, rightW - 1, row.h(), SettingsTheme.CONTENT_HEAD);
            SettingsTheme.rect(ctx, rightX + 1, y, rightW - 1, 1, SettingsTheme.BORDER);
            SettingsTheme.rect(ctx, rightX + 1, y + row.h() - 1, rightW - 1, 1, SettingsTheme.BORDER);
            SettingsTheme.text(ctx, this.font, row.id().substring(1), rightX + d(12, 3),
                centerTextY(y, row.h()), SettingsTheme.TEXT_FAINT);
            return;
        }

        SettingsTheme.rect(ctx, rightX + 1, y + row.h() - 1, rightW - 1, 1, SettingsTheme.ROW_SEPARATOR);
        SimpleCPSConfig.KeyButtonData k = single();
        int textY = centerTextY(y, row.h());
        int labelColor = SettingsTheme.TEXT_LABEL;

        switch (row.id()) {
            case "keybind" -> {
                SettingsTheme.text(ctx, this.font, "KEYBIND", inspLabelX(), textY, labelColor);
                int[] r = keybindRect(y);
                boolean active = mode == Mode.KEYBIND;
                boolean hov = SettingsTheme.inside(mx, my, r[0], r[1], r[2], r[3]);
                if (active) {
                    SettingsTheme.raised(ctx, r[0], r[1], r[2], r[3], SettingsTheme.DONE_BG,
                        SettingsTheme.DONE_LIGHT, SettingsTheme.DONE_DARK);
                } else {
                    SettingsTheme.button(ctx, r[0], r[1], r[2], r[3], hov);
                }
                SettingsTheme.centeredText(ctx, this.font, keybindLabel(), r[0] + r[2] / 2,
                    centerTextY(r[1], r[3]), active ? SettingsTheme.DONE_TEXT : SettingsTheme.TEXT_LABEL);
            }
            case "label" -> {
                SettingsTheme.text(ctx, this.font, "LABEL", inspLabelX(), textY, labelColor);
                int[] r = labelFieldRect(y);
                SettingsTheme.sunken(ctx, r[0], r[1], r[2], r[3], SettingsTheme.FIELD_BG,
                    labelFocused ? SettingsTheme.SEL_BORDER : SettingsTheme.FIELD_RIM);
                if (k != null) {
                    labelInput.render(ctx, this.font, r[0] + gap, r[1], r[2] - gap * 2, r[3],
                        labelFocused, SettingsTheme.TEXT);
                } else {
                    SettingsTheme.text(ctx, this.font, "MIXED", r[0] + gap, centerTextY(r[1], r[3]),
                        SettingsTheme.TEXT_PLACEHOLD);
                }
            }
            case "position", "size" -> {
                boolean isSize = row.id().equals("size");
                SettingsTheme.text(ctx, this.font, isSize ? "SIZE" : "POSITION", inspLabelX(), textY, labelColor);
                int[][] rects = pairStepperRects(y);
                String[] tags = isSize ? new String[] { "W", "H" } : new String[] { "X", "Y" };
                String[] values = new String[2];
                if (k == null) {
                    values[0] = "-";
                    values[1] = "-";
                } else if (isSize) {
                    values[0] = String.valueOf(k.w);
                    values[1] = String.valueOf(k.h);
                } else {
                    values[0] = String.valueOf(k.x);
                    values[1] = String.valueOf(k.y);
                }
                for (int i = 0; i < 2; i++) {
                    renderStepper(ctx, rects[i], tags[i], values[i], mx, my);
                }
            }
            case "placement" -> {
                SettingsTheme.text(ctx, this.font, "PLACEMENT", inspLabelX(), textY, labelColor);
                int[] btn = centerBtnRect(y);
                boolean hov = SettingsTheme.inside(mx, my, btn[0], btn[1], btn[2], btn[3]);
                SettingsTheme.button(ctx, btn[0], btn[1], btn[2], btn[3], hov);
                SettingsTheme.centeredText(ctx, this.font, "CENTER", btn[0] + btn[2] / 2,
                    centerTextY(btn[1], btn[3]), SettingsTheme.TEXT_LABEL);

                String read = k == null ? "MIXED"
                    : (k.labelX == -1 && k.labelY == -1) ? "CENTERED"
                    : "OFFSET " + k.labelX + " / " + k.labelY;
                int rw = SettingsTheme.textWidth(this.font, read) + gap * 2;
                int rx = btn[0] - gap - rw;
                if (rx > inspLabelX() + SettingsTheme.textWidth(this.font, "PLACEMENT") + gap) {
                    SettingsTheme.sunken(ctx, rx, btn[1], rw, ctrlH, SettingsTheme.FIELD_BG, SettingsTheme.FIELD_RIM);
                    SettingsTheme.text(ctx, this.font, read, rx + gap, centerTextY(btn[1], ctrlH),
                        SettingsTheme.TEXT_MUTED);
                }
            }
            case "content" -> {
                SettingsTheme.text(ctx, this.font, "CONTENT", inspLabelX(), textY, labelColor);
                boolean line = k != null && k.labelLine;
                renderSegment(ctx, segRect(y, 0), "TEXT", k != null && !line, mx, my);
                renderSegment(ctx, segRect(y, 1), "LINE", line, mx, my);
            }
            case "scale" -> {
                boolean line = lineMode();
                SettingsTheme.text(ctx, this.font, line ? "LINE WEIGHT" : "TEXT SIZE",
                    inspLabelX(), textY, labelColor);
                String value = k == null ? "-"
                    : line ? KeystrokesRender.lineHeight(k) + " PX"
                    : k.labelScale + " %";
                renderStepper(ctx, scaleStepperRect(y), "", value, mx, my);
            }
            case "linewidth" -> {
                SettingsTheme.text(ctx, this.font, "LINE WIDTH", inspLabelX(), textY, labelColor);
                renderStepper(ctx, scaleStepperRect(y), "",
                    k == null ? "-" : k.lineWidthPercent + " %", mx, my);
            }
            case "style" -> {
                SettingsTheme.text(ctx, this.font, "STYLE", inspLabelX(), textY, labelColor);
                String[] labels = { "B", "I", "U", "S" };
                boolean[] on = {
                    k != null && k.bold, k != null && k.italic,
                    k != null && k.underlined, k != null && k.shadow
                };
                // A bar has no weight, slant or underline -- only its shadow applies.
                // Showing those three live in line mode invited clicks that did nothing.
                boolean line = lineMode();
                for (int i = 0; i < 4; i++) {
                    renderSegment(ctx, styleBtnRect(y, i), labels[i], on[i], mx, my, i == 3 || !line);
                }
            }
            case "kdesign", "kmotion", "kfill", "kdirection" -> {
                String label = switch (row.id()) {
                    case "kdesign" -> "DESIGN";
                    case "kmotion" -> "MOTION";
                    case "kfill" -> "FILL";
                    default -> "DIRECTION";
                };
                SettingsTheme.text(ctx, this.font, label, inspLabelX(), textY, labelColor);

                int[] r = designBtnRect(y);
                boolean hov = SettingsTheme.inside(mx, my, r[0], r[1], r[2], r[3]);
                SettingsTheme.button(ctx, r[0], r[1], r[2], r[3], hov);

                boolean global = usesGlobal(row.id());
                String value;
                int color;
                switch (row.id()) {
                    case "kdesign" -> {
                        value = SettingsTheme.up(keyDesign().display());
                        color = SettingsTheme.TEXT;
                    }
                    case "kmotion" -> {
                        KeystrokesAnim.Motion own = keyMotion();
                        value = own != null ? SettingsTheme.up(own.display())
                            : globalSummary("kmotion");
                        color = own != null ? motionColor(own) : SettingsTheme.TEXT_GHOST;
                    }
                    case "kfill" -> {
                        KeystrokesAnim.Fill own = keyFill();
                        value = own != null ? SettingsTheme.up(own.display())
                            : globalSummary("kfill");
                        color = own != null ? fillColor(own) : SettingsTheme.TEXT_GHOST;
                    }
                    default -> {
                        value = SettingsTheme.up(keyDirection().display());
                        color = SettingsTheme.TEXT;
                    }
                }
                // Saying GLOBAL as well as the value, rather than instead of it,
                // keeps the row honest: the key does look like that, it just is not
                // the key's own choice.
                if (global) {
                    SettingsTheme.text(ctx, this.font, "GLOBAL", r[0] + d(8, 2),
                        centerTextY(r[1], r[3]), SettingsTheme.TEXT_FAINT);
                    int used = SettingsTheme.textWidth(this.font, "GLOBAL") + gap;
                    SettingsTheme.text(ctx, this.font,
                        SettingsTheme.truncate(this.font, value, r[2] - used - d(20, 7)),
                        r[0] + d(8, 2) + used, centerTextY(r[1], r[3]), SettingsTheme.TEXT_GHOST);
                } else {
                    SettingsTheme.text(ctx, this.font,
                        SettingsTheme.truncate(this.font, value, r[2] - d(26, 9)),
                        r[0] + d(8, 2), centerTextY(r[1], r[3]), color);
                }
                int size = Math.max(2, d(4, 2));
                caretDown(ctx, r[0] + r[2] - d(8, 3) - size, r[1] + (r[3] - size) / 2, size,
                    SettingsTheme.TEXT_MUTED);
            }
            case "color", "pressed" -> {
                boolean isPressed = row.id().equals("pressed");
                SettingsTheme.text(ctx, this.font, isPressed ? "PRESSED" : "TEXT",
                    inspLabelX(), textY, labelColor);
                int[] r = colorBtnRect(y, isPressed);
                boolean hov = SettingsTheme.inside(mx, my, r[0], r[1], r[2], r[3]);
                SettingsTheme.button(ctx, r[0], r[1], r[2], r[3], hov);
                int sw = d(16, 6);
                SettingsTheme.rect(ctx, r[0] + d(8, 2), r[1] + (r[3] - sw) / 2, sw, sw,
                    colorSwatchValue(isPressed));
                SettingsTheme.frame(ctx, r[0] + d(8, 2), r[1] + (r[3] - sw) / 2, sw, sw, SettingsTheme.BORDER);
                SettingsTheme.text(ctx, this.font, colorHexLabel(isPressed), r[0] + d(8, 2) + sw + gap,
                    centerTextY(r[1], r[3]), SettingsTheme.TEXT_LABEL);
            }
            case "cps", "anim" -> {
                boolean isCps = row.id().equals("cps");
                boolean enabled = !isCps || (k != null && k.isMouse);
                boolean on = isCps ? (k != null && k.showCps)
                    : (k != null && (k.animationEnabled == null || k.animationEnabled));
                SettingsTheme.text(ctx, this.font, isCps ? "SHOW CPS" : "ANIMATION", inspLabelX(), textY,
                    enabled ? labelColor : SettingsTheme.TEXT_GHOST);
                renderToggle(ctx, toggleRect(y), on, enabled);
            }
            default -> { }
        }
    }

    private void renderStepper(GuiGraphicsExtractor ctx, int[] r, String tag, String value, int mx, int my) {
        int fieldW = r[2] - stepBtn * 2;
        boolean minusHov = SettingsTheme.inside(mx, my, r[0], r[1], stepBtn, r[3]);
        boolean plusHov = SettingsTheme.inside(mx, my, r[0] + stepBtn + fieldW, r[1], stepBtn, r[3]);

        SettingsTheme.button(ctx, r[0], r[1], stepBtn, r[3], minusHov);
        SettingsTheme.centeredText(ctx, this.font, "-", r[0] + stepBtn / 2, centerTextY(r[1], r[3]),
            SettingsTheme.TEXT_LABEL);

        SettingsTheme.rect(ctx, r[0] + stepBtn, r[1], fieldW, r[3], SettingsTheme.FIELD_BG);
        SettingsTheme.rect(ctx, r[0] + stepBtn, r[1], fieldW, 1, SettingsTheme.BORDER);
        SettingsTheme.rect(ctx, r[0] + stepBtn, r[1] + r[3] - 1, fieldW, 1, SettingsTheme.BORDER);

        String shown = tag.isEmpty() ? value : tag + " " + value;
        SettingsTheme.centeredText(ctx, this.font, SettingsTheme.truncate(this.font, shown, fieldW - 2),
            r[0] + stepBtn + fieldW / 2, centerTextY(r[1], r[3]), SettingsTheme.TEXT);

        SettingsTheme.button(ctx, r[0] + stepBtn + fieldW, r[1], stepBtn, r[3], plusHov);
        SettingsTheme.centeredText(ctx, this.font, "+", r[0] + stepBtn + fieldW + stepBtn / 2,
            centerTextY(r[1], r[3]), SettingsTheme.TEXT_LABEL);
    }

    private void renderSegment(GuiGraphicsExtractor ctx, int[] r, String label, boolean on, int mx, int my) {
        renderSegment(ctx, r, label, on, mx, my, true);
    }

    private void renderSegment(GuiGraphicsExtractor ctx, int[] r, String label, boolean on,
                               int mx, int my, boolean enabled) {
        boolean hov = enabled && SettingsTheme.inside(mx, my, r[0], r[1], r[2], r[3]);
        if (on && enabled) {
            SettingsTheme.raised(ctx, r[0], r[1], r[2], r[3], SettingsTheme.DONE_BG,
                SettingsTheme.DONE_LIGHT, SettingsTheme.DONE_DARK);
        } else {
            SettingsTheme.button(ctx, r[0], r[1], r[2], r[3], hov);
        }
        int color = !enabled ? SettingsTheme.TEXT_OFF
            : on ? SettingsTheme.DONE_TEXT : SettingsTheme.TEXT_MUTED;
        SettingsTheme.centeredText(ctx, this.font, label, r[0] + r[2] / 2, centerTextY(r[1], r[3]), color);
    }

    private void renderToggle(GuiGraphicsExtractor ctx, int[] r, boolean on, boolean enabled) {
        SettingsTheme.sunken(ctx, r[0], r[1], r[2], r[3], SettingsTheme.FIELD_BG, SettingsTheme.FIELD_RIM);

        // The track is split into a knob half and a label half. Deriving both from the
        // same split is what guarantees they cannot overlap, whatever the UI scale --
        // the knob used to have a minimum width of its own and ran over the label.
        int inset = 2;
        int half = (r[2] - inset * 2) / 2;
        int knobX = on ? r[0] + r[2] - inset - half : r[0] + inset;
        int labelX = on ? r[0] + inset : r[0] + r[2] - inset - half;

        int knobColor = enabled && on ? SettingsTheme.KNOB_ON : SettingsTheme.KNOB_OFF;
        SettingsTheme.raised(ctx, knobX, r[1] + inset, half, r[3] - inset * 2, knobColor,
            SettingsTheme.BTN_LIGHT, SettingsTheme.BTN_DARK);

        String state = on ? "ON" : "OFF";
        int stateColor = enabled && on ? SettingsTheme.KNOB_LABEL_ON : SettingsTheme.TEXT_OFF;
        SettingsTheme.centeredText(ctx, this.font, SettingsTheme.truncate(this.font, state, half),
            labelX + half / 2, centerTextY(r[1], r[3]), stateColor);
    }

    // --- Footer ------------------------------------------------------------

    private int[] doneRect() {
        int h = d(34, 12);
        int w = SettingsTheme.textWidth(this.font, "DONE") + d(44, 10);
        return new int[] { innerX + innerW - pad - w, footerY + (footerH - h) / 2, w, h };
    }

    private int[] cancelRect() {
        int[] done = doneRect();
        int w = SettingsTheme.textWidth(this.font, "CANCEL") + d(36, 8);
        return new int[] { done[0] - gap - w, done[1], w, done[3] };
    }

    private void renderFooter(GuiGraphicsExtractor ctx, int mx, int my) {
        SettingsTheme.rect(ctx, innerX, footerY, innerW, footerH, SettingsTheme.BAR_BG);
        SettingsTheme.rect(ctx, innerX, footerY, innerW, 1, SettingsTheme.BORDER);
        SettingsTheme.rect(ctx, innerX, footerY + 1, innerW, 1, SettingsTheme.BAR_LIGHT);

        int textY = centerTextY(footerY, footerH);
        int x = innerX + pad;
        SettingsTheme.text(ctx, this.font, "config/simplecps.json", x, textY, SettingsTheme.TEXT_FAINT);
        x += SettingsTheme.textWidth(this.font, "config/simplecps.json") + gap;
        SettingsTheme.text(ctx, this.font, "|", x, textY, SettingsTheme.DIVIDER);
        x += SettingsTheme.textWidth(this.font, "|") + gap;
        SettingsTheme.text(ctx, this.font,
            dirty ? "UNSAVED EDITS - AUTOSAVE ON CLOSE" : "ALL CHANGES SAVED",
            x, textY, dirty ? SettingsTheme.TEXT_MUTED : SettingsTheme.TEXT_FAINT);

        int[] cancel = cancelRect();
        boolean cancelHov = SettingsTheme.inside(mx, my, cancel[0], cancel[1], cancel[2], cancel[3]);
        SettingsTheme.raised(ctx, cancel[0], cancel[1], cancel[2], cancel[3],
            cancelHov ? SettingsTheme.DANGER_HOVER : SettingsTheme.DANGER_BG,
            SettingsTheme.DANGER_LIGHT, SettingsTheme.DANGER_DARK);
        SettingsTheme.centeredText(ctx, this.font, "CANCEL", cancel[0] + cancel[2] / 2,
            centerTextY(cancel[1], cancel[3]), SettingsTheme.DANGER_TEXT);

        int[] done = doneRect();
        boolean doneHov = SettingsTheme.inside(mx, my, done[0], done[1], done[2], done[3]);
        SettingsTheme.raised(ctx, done[0], done[1], done[2], done[3],
            doneHov ? SettingsTheme.DONE_HOVER : SettingsTheme.DONE_BG,
            SettingsTheme.DONE_LIGHT, SettingsTheme.DONE_DARK);
        SettingsTheme.centeredText(ctx, this.font, "DONE", done[0] + done[2] / 2,
            centerTextY(done[1], done[3]), SettingsTheme.DONE_TEXT);
    }


    // --- Overlays ----------------------------------------------------------

    private static final String[][] MENU_ITEMS = {
        { "label", "EDIT LABEL", "F2" },
        { "bind", "SET KEYBIND", "" },
        { "center", "CENTER LABEL", "" },
        { "duplicate", "DUPLICATE", "CTRL+D" },
        { "delete", "DELETE", "DEL" },
    };

    private int menuHeight() {
        return MENU_ITEMS.length * menuRowH + d(8, 2);
    }

    private void renderContextMenu(GuiGraphicsExtractor ctx, int mx, int my, float p) {
        int h = menuHeight();
        ctx.nextStratum();
        ctx.pose().pushMatrix();
        ctx.pose().translate(0f, (1f - p) * 6f);

        SettingsTheme.rect(ctx, menuX, menuY, menuW, h, fade(SettingsTheme.FOCUS_BG, p));
        SettingsTheme.frame(ctx, menuX, menuY, menuW, h, fade(SettingsTheme.BORDER, p));
        SettingsTheme.rect(ctx, menuX + 1, menuY + 1, menuW - 2, 1, fade(SettingsTheme.PANEL_LIGHT, p));

        int y = menuY + d(4, 1);
        for (String[] item : MENU_ITEMS) {
            boolean hov = menuOpen && SettingsTheme.inside(mx, my, menuX, y, menuW, menuRowH);
            if (hov) SettingsTheme.rect(ctx, menuX + 1, y, menuW - 2, menuRowH, fade(SettingsTheme.BTN_HOVER, p));
            int textY = centerTextY(y, menuRowH);
            SettingsTheme.text(ctx, this.font, item[1], menuX + d(10, 3), textY,
                fade(SettingsTheme.TEXT_LABEL, p));
            if (!item[2].isEmpty()) {
                SettingsTheme.rightText(ctx, this.font, item[2], menuX + menuW - d(10, 3), textY,
                    fade(SettingsTheme.TEXT_GHOST, p));
            }
            y += menuRowH;
        }
        ctx.pose().popMatrix();
    }

    private static int fade(int argb, float p) {
        return com.eymistaken.simplecps.util.RenderUtil.withAlpha(argb, p);
    }

    // Color editor geometry. Every sub-rect is derived from the panel rect so the
    // click handler and the renderer cannot disagree about where anything is.

    private int cpHeadH() { return d(30, 10); }
    private int cpSvH() { return d(150, 34); }
    private int cpHueH() { return d(18, 6); }
    private int cpHexH() { return d(28, 10); }
    private int cpBtnH() { return d(32, 12); }
    private int cpSwGap() { return Math.max(1, gap / 2); }

    private int[] colorPanelRect() {
        int w = Math.max(d(120, 90), Math.min(d(COLOR_PANEL_W, 110), rightW - gap * 2));
        int h = cpHeadH() + pad + cpSvH() + gap + cpHueH() + gap + cpHexH() + gap
            + swatchH * 2 + cpSwGap() + gap + cpBtnH() + pad;
        int x = rightX + rightW - w - gap;
        int y = Math.max(panelY + 1 + headerH + gap, footerY - h - gap);
        return new int[] { x, y, w, h };
    }

    private int[] cpSvRect() {
        int[] r = colorPanelRect();
        return new int[] { r[0] + pad, r[1] + cpHeadH() + pad, r[2] - pad * 2, cpSvH() };
    }

    private int[] cpHueRect() {
        int[] sv = cpSvRect();
        return new int[] { sv[0], sv[1] + sv[3] + gap, sv[2], cpHueH() };
    }

    private int[] cpSwatchRect(int index) {
        int[] hue = cpHueRect();
        int top = hue[1] + hue[3] + gap + cpHexH() + gap;
        int cols = 5;
        int cellW = (hue[2] - cpSwGap() * (cols - 1)) / cols;
        int col = index % cols;
        int row = index / cols;
        return new int[] { hue[0] + col * (cellW + cpSwGap()), top + row * (swatchH + cpSwGap()), cellW, swatchH };
    }

    private int[] cpApplyRect() {
        int[] last = cpSwatchRect(SettingsTheme.SWATCHES.length - 1);
        int[] hue = cpHueRect();
        int w = (hue[2] - gap) / 2;
        return new int[] { hue[0], last[1] + last[3] + gap, w, cpBtnH() };
    }

    private int[] cpResetRect() {
        int[] apply = cpApplyRect();
        int[] hue = cpHueRect();
        return new int[] { hue[0] + hue[2] - apply[2], apply[1], apply[2], apply[3] };
    }

    private int[] cpCloseRect() {
        int[] r = colorPanelRect();
        int s = d(20, 8);
        return new int[] { r[0] + r[2] - s - gap, r[1] + (cpHeadH() - s) / 2, s, s };
    }

    private void renderColorPanel(GuiGraphicsExtractor ctx, int mx, int my) {
        int[] r = colorPanelRect();
        ctx.nextStratum();
        SettingsTheme.rect(ctx, r[0], r[1], r[2], r[3], SettingsTheme.FOCUS_BG);
        SettingsTheme.raised(ctx, r[0], r[1], r[2], r[3], SettingsTheme.COLOR_PANEL,
            SettingsTheme.PANEL_LIGHT, SettingsTheme.PANEL_DARK);

        SettingsTheme.rect(ctx, r[0] + 1, r[1] + 1, r[2] - 2, cpHeadH(), SettingsTheme.CONTENT_HEAD);
        SettingsTheme.text(ctx, this.font, colorPressedTarget ? "PRESSED COLOR" : "TEXT COLOR",
            r[0] + d(12, 3), centerTextY(r[1], cpHeadH()), SettingsTheme.TEXT_MUTED);
        int[] close = cpCloseRect();
        SettingsTheme.closeIcon(ctx, close[0] + (close[2] - 5) / 2, close[1] + (close[3] - 5) / 2,
            SettingsTheme.inside(mx, my, close[0], close[1], close[2], close[3])
                ? SettingsTheme.TEXT : SettingsTheme.TEXT_MUTED);

        int[] sv = cpSvRect();
        for (int i = 0; i < sv[2]; i++) {
            float s = sv[2] <= 1 ? 0f : i / (float) (sv[2] - 1);
            SettingsTheme.rect(ctx, sv[0] + i, sv[1], 1, sv[3],
                java.awt.Color.HSBtoRGB(cpHue, s, 1f) | 0xFF000000);
        }
        ctx.fillGradient(sv[0], sv[1], sv[0] + sv[2], sv[1] + sv[3], 0x00000000, 0xFF000000);
        SettingsTheme.frame(ctx, sv[0], sv[1], sv[2], sv[3], SettingsTheme.BORDER);
        int curX = sv[0] + Math.round(cpSat * (sv[2] - 1));
        int curY = sv[1] + Math.round((1f - cpVal) * (sv[3] - 1));
        SettingsTheme.rect(ctx, curX - 2, curY - 2, 5, 5, 0xFFFFFFFF);
        SettingsTheme.rect(ctx, curX - 1, curY - 1, 3, 3, SettingsTheme.BORDER);

        int[] hue = cpHueRect();
        for (int i = 0; i < hue[2]; i++) {
            float h = hue[2] <= 1 ? 0f : i / (float) (hue[2] - 1);
            SettingsTheme.rect(ctx, hue[0] + i, hue[1], 1, hue[3],
                java.awt.Color.HSBtoRGB(h, 1f, 1f) | 0xFF000000);
        }
        SettingsTheme.frame(ctx, hue[0], hue[1], hue[2], hue[3], SettingsTheme.BORDER);
        int hueX = hue[0] + Math.round(cpHue * (hue[2] - 1));
        SettingsTheme.rect(ctx, hueX - 1, hue[1] - 2, 3, hue[3] + 4, 0xFFFFFFFF);
        SettingsTheme.rect(ctx, hueX, hue[1] - 1, 1, hue[3] + 2, SettingsTheme.BORDER);

        int hexY = hue[1] + hue[3] + gap;
        int previewW = d(44, 16);
        int hexW = hue[2] - previewW - gap;
        SettingsTheme.sunken(ctx, hue[0], hexY, hexW, cpHexH(), SettingsTheme.FIELD_BG, SettingsTheme.FIELD_RIM);
        SettingsTheme.text(ctx, this.font, String.format("#%06X", pickerColor() & 0xFFFFFF),
            hue[0] + gap, centerTextY(hexY, cpHexH()), SettingsTheme.TEXT);
        SettingsTheme.rect(ctx, hue[0] + hexW + gap, hexY, previewW, cpHexH(), pickerColor());
        SettingsTheme.frame(ctx, hue[0] + hexW + gap, hexY, previewW, cpHexH(), SettingsTheme.BORDER);

        for (int i = 0; i < SettingsTheme.SWATCHES.length; i++) {
            int[] sw = cpSwatchRect(i);
            SettingsTheme.rect(ctx, sw[0], sw[1], sw[2], sw[3], SettingsTheme.SWATCHES[i] | 0xFF000000);
            SettingsTheme.frame(ctx, sw[0], sw[1], sw[2], sw[3], SettingsTheme.BORDER);
        }

        int[] apply = cpApplyRect();
        boolean applyHov = SettingsTheme.inside(mx, my, apply[0], apply[1], apply[2], apply[3]);
        SettingsTheme.raised(ctx, apply[0], apply[1], apply[2], apply[3],
            applyHov ? SettingsTheme.DONE_HOVER : SettingsTheme.DONE_BG,
            SettingsTheme.DONE_LIGHT, SettingsTheme.DONE_DARK);
        SettingsTheme.centeredText(ctx, this.font, "APPLY", apply[0] + apply[2] / 2,
            centerTextY(apply[1], apply[3]), SettingsTheme.DONE_TEXT);

        int[] reset = cpResetRect();
        boolean resetHov = SettingsTheme.inside(mx, my, reset[0], reset[1], reset[2], reset[3]);
        SettingsTheme.button(ctx, reset[0], reset[1], reset[2], reset[3], resetHov);
        SettingsTheme.centeredText(ctx, this.font,
            SettingsTheme.truncate(this.font, "USE GLOBAL", reset[2] - 4),
            reset[0] + reset[2] / 2, centerTextY(reset[1], reset[3]), SettingsTheme.TEXT_LABEL);
    }

    private int pickerColor() {
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(cpHue, cpSat, cpVal) & 0xFFFFFF);
    }

    // --- Design gallery ----------------------------------------------------

    /** Cards per row. Two keeps each preview big enough to actually judge. */
    private static final int GALLERY_COLS = 2;

    /** Nominal size of a card's preview stage, in game pixels. */
    private static final int PREVIEW_W = 46;
    private static final int PREVIEW_H = 30;

    private void openGallery() {
        galleryOpen = true;
        galleryScroll = 0;
        galleryOpenedAt = System.currentTimeMillis();
        galleryAnim.clear();
    }

    private int[] galleryPanelRect() {
        // Nearly the whole screen. The cards carry live previews, and a preview too
        // small to read is the same as no preview at all.
        int w = panelW - pad * 2;
        int h = panelH - pad * 2;
        return new int[] { panelX + (panelW - w) / 2, panelY + (panelH - h) / 2, w, h };
    }

    private int galleryHeadH() {
        return cpHeadH();
    }

    /** The scrolling area, inside the panel's head and footer. */
    private int[] galleryBodyRect() {
        int[] r = galleryPanelRect();
        int top = r[1] + galleryHeadH() + gap;
        int bottom = r[1] + r[3] - pad - cpBtnH() - gap;
        return new int[] { r[0] + pad, top, r[2] - pad * 2, Math.max(0, bottom - top) };
    }

    private int galleryCardW() {
        int[] body = galleryBodyRect();
        return (body[2] - gap * (GALLERY_COLS - 1)) / GALLERY_COLS;
    }

    private int galleryCardH() {
        // Room for the title bar plus a stage the arranged designs fit in: the
        // compass and the honeycomb are far taller than a row of keys.
        return d(26, 10) + d(150, 62);
    }

    private int[] galleryCardRect(int index) {
        int[] body = galleryBodyRect();
        int col = index % GALLERY_COLS;
        int row = index / GALLERY_COLS;
        int cw = galleryCardW();
        int ch = galleryCardH();
        return new int[] { body[0] + col * (cw + gap),
            body[1] + row * (ch + gap) - galleryScroll, cw, ch };
    }

    private int galleryContentH() {
        int rows = (KeystrokesDesign.values().length + GALLERY_COLS - 1) / GALLERY_COLS;
        return rows * galleryCardH() + Math.max(0, rows - 1) * gap;
    }

    private int maxGalleryScroll() {
        return Math.max(0, galleryContentH() - galleryBodyRect()[3]);
    }

    private int[] galleryCloseRect() {
        int[] r = galleryPanelRect();
        int w = SettingsTheme.textWidth(this.font, "CLOSE") + d(28, 8);
        return new int[] { r[0] + r[2] - pad - w, r[1] + r[3] - pad - cpBtnH(), w, cpBtnH() };
    }

    /**
     * The sample buttons a card draws: a plain key, a mouse button and a wide one,
     * which between them show a design's corners, its CPS slot and how it handles a
     * bar. Throwaway instances rather than the player's real keys, so a preview can
     * never write back into the layout.
     */
    private static final List<SimpleCPSConfig.KeyButtonData> PREVIEW_KEYS = List.of(
        new SimpleCPSConfig.KeyButtonData("W", 15, 0, 13, 13, org.lwjgl.glfw.GLFW.GLFW_KEY_W),
        new SimpleCPSConfig.KeyButtonData("L", 0, 15, 13, 13, 0, true),
        new SimpleCPSConfig.KeyButtonData("R", 15, 15, 13, 13, 1, true),
        new SimpleCPSConfig.KeyButtonData("-----", 0, 30, 28, 8, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE));

    /**
     * The sample buttons a card draws for a design.
     *
     * <p>A design that arranges its own keys is previewed in that arrangement, built
     * from the very same {@code arrangement()} the click applies — so the card is not
     * an impression of the design, it is the design. The rest get the generic cluster
     * above, because in game they keep whatever layout the player already has.
     */
    private static List<SimpleCPSConfig.KeyButtonData> previewKeys(KeystrokesDesign design) {
        List<KeystrokesDesign.Slot> slots = design.arrangement();
        if (slots == null) return PREVIEW_KEYS;

        List<SimpleCPSConfig.KeyButtonData> keys = new ArrayList<>();
        for (KeystrokesDesign.Slot slot : slots) {
            SimpleCPSConfig.KeyButtonData btn = new SimpleCPSConfig.KeyButtonData(
                roleLabel(slot.role()), slot.x(), slot.y(), slot.w(), slot.h(),
                roleKeyCode(slot.role()), isMouseRole(slot.role()));
            if (slot.role() == KeystrokesDesign.ROLE_SPACE) btn.labelLine = true;
            keys.add(btn);
        }
        return keys;
    }

    private static String roleLabel(int role) {
        return switch (role) {
            case KeystrokesDesign.ROLE_W -> "W";
            case KeystrokesDesign.ROLE_A -> "A";
            case KeystrokesDesign.ROLE_S -> "S";
            case KeystrokesDesign.ROLE_D -> "D";
            case KeystrokesDesign.ROLE_LMB -> "L";
            case KeystrokesDesign.ROLE_RMB -> "R";
            case KeystrokesDesign.ROLE_CTRL -> "CTRL";
            case KeystrokesDesign.ROLE_SHIFT -> "SHIFT";
            default -> "";
        };
    }

    private static int roleKeyCode(int role) {
        return switch (role) {
            case KeystrokesDesign.ROLE_W -> GLFW.GLFW_KEY_W;
            case KeystrokesDesign.ROLE_A -> GLFW.GLFW_KEY_A;
            case KeystrokesDesign.ROLE_S -> GLFW.GLFW_KEY_S;
            case KeystrokesDesign.ROLE_D -> GLFW.GLFW_KEY_D;
            case KeystrokesDesign.ROLE_LMB -> 0;
            case KeystrokesDesign.ROLE_RMB -> 1;
            case KeystrokesDesign.ROLE_CTRL -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case KeystrokesDesign.ROLE_SHIFT -> GLFW.GLFW_KEY_LEFT_SHIFT;
            default -> GLFW.GLFW_KEY_SPACE;
        };
    }

    private static boolean isMouseRole(int role) {
        return role == KeystrokesDesign.ROLE_LMB || role == KeystrokesDesign.ROLE_RMB;
    }

    /** Bounds the sample cluster occupies, as {@code {w, h}}. */
    private static int[] previewExtent(KeystrokesDesign design) {
        int w = 0, h = 0;
        for (SimpleCPSConfig.KeyButtonData btn : previewKeys(design)) {
            w = Math.max(w, btn.x + btn.w);
            h = Math.max(h, btn.y + btn.h);
        }
        return new int[] { w, h };
    }

    /**
     * Which sample key is held this instant. The cards press themselves on a loop —
     * seeing only the resting state would hide half of what separates these designs,
     * and the source document demos them the same way.
     */
    private boolean previewPressed(int cardIndex, int keyIndex, int keyCount) {
        long elapsed = System.currentTimeMillis() - galleryOpenedAt;
        long step = 700L;
        long cycle = step * Math.max(1, keyCount);
        // Staggered per card so the grid ripples instead of blinking in unison.
        long phase = Math.floorMod(elapsed + cardIndex * 230L, cycle);
        return phase / step == keyIndex && phase % step < 420L;
    }

    private void renderGallery(GuiGraphicsExtractor ctx, int mx, int my) {
        ctx.nextStratum();
        SettingsTheme.rect(ctx, 0, 0, vw, vh, SettingsTheme.MODAL_SCRIM);

        int[] r = galleryPanelRect();
        SettingsTheme.rect(ctx, r[0], r[1], r[2], r[3], SettingsTheme.MODAL_BG);
        SettingsTheme.raised(ctx, r[0], r[1], r[2], r[3], SettingsTheme.PANEL_BG,
            SettingsTheme.PANEL_LIGHT, SettingsTheme.PANEL_DARK);
        SettingsTheme.rect(ctx, r[0] + 1, r[1] + 1, r[2] - 2, galleryHeadH(), SettingsTheme.CONTENT_HEAD);
        SettingsTheme.text(ctx, this.font, "KEYSTROKE DESIGNS", r[0] + d(12, 3),
            centerTextY(r[1], galleryHeadH()), SettingsTheme.MODAL_HEAD_FG);
        SettingsTheme.rightText(ctx, this.font, "EACH BRINGS ITS OWN ANIMATION AND COLORS",
            r[0] + r[2] - d(12, 3), centerTextY(r[1], galleryHeadH()), SettingsTheme.TEXT_FAINT);

        int[] body = galleryBodyRect();
        galleryScroll = Math.max(0, Math.min(galleryScroll, maxGalleryScroll()));

        KeystrokesDesign[] designs = KeystrokesDesign.values();
        for (int i = 0; i < designs.length; i++) {
            int[] card = galleryCardRect(i);
            // Clip each card to itself, intersected with the scrolling body. One
            // region per card handles both a half-scrolled card and a preview that
            // would otherwise spill across its neighbor, without nesting regions.
            int x0 = Math.max(card[0], body[0]);
            int y0 = Math.max(card[1], body[1]);
            int x1 = Math.min(card[0] + card[2], body[0] + body[2]);
            int y1 = Math.min(card[1] + card[3], body[1] + body[3]);
            if (x1 <= x0 || y1 <= y0) continue;

            ctx.enableScissor(x0, y0, x1, y1);
            renderGalleryCard(ctx, designs[i], i, card, mx, my);
            ctx.disableScissor();
        }
        renderScrollbar(ctx, body[0] + body[2] - d(4, 2), body[1], body[3],
            galleryContentH(), galleryScroll);

        int[] close = galleryCloseRect();
        boolean hov = SettingsTheme.inside(mx, my, close[0], close[1], close[2], close[3]);
        SettingsTheme.button(ctx, close[0], close[1], close[2], close[3], hov);
        SettingsTheme.centeredText(ctx, this.font, "CLOSE", close[0] + close[2] / 2,
            centerTextY(close[1], close[3]), SettingsTheme.TEXT_LABEL);
    }

    private void renderGalleryCard(GuiGraphicsExtractor ctx, KeystrokesDesign design, int index,
                                   int[] card, int mx, int my) {
        boolean active = globalDesign() == design;
        boolean hov = SettingsTheme.inside(mx, my, card[0], card[1], card[2], card[3]);

        SettingsTheme.rect(ctx, card[0], card[1], card[2], card[3],
            active ? SettingsTheme.SEL_BG : (hov ? SettingsTheme.BTN_BG : SettingsTheme.CARD_BG));
        SettingsTheme.frame(ctx, card[0], card[1], card[2], card[3],
            active ? SettingsTheme.SEL_BORDER : SettingsTheme.DIVIDER);

        int headH = d(26, 10);
        SettingsTheme.rect(ctx, card[0] + 1, card[1] + 1, card[2] - 2, headH - 1,
            active ? SettingsTheme.TAB_ACTIVE_BG : SettingsTheme.CONTENT_HEAD);
        SettingsTheme.text(ctx, this.font,
            SettingsTheme.truncate(this.font, SettingsTheme.up(design.display()), card[2] - d(90, 32)),
            card[0] + gap * 2, centerTextY(card[1], headH),
            active ? SettingsTheme.TEXT_STRONG : SettingsTheme.TEXT_LABEL);

        // Name the animation the design ships with, in its own color, so the card
        // says what picking it will do and not only what it looks like.
        boolean rearranges = design.isModuleWide() || design.arrangement() != null;
        String note = rearranges
            ? "ARRANGES KEYS"
            : SettingsTheme.up(design.defaultFill() == KeystrokesAnim.Fill.NONE
                ? design.defaultMotion().display() : design.defaultFill().display());
        SettingsTheme.rightText(ctx, this.font, note, card[0] + card[2] - gap * 2,
            centerTextY(card[1], headH),
            rearranges ? SettingsTheme.TEXT_FAINT
                : (design.defaultFill() == KeystrokesAnim.Fill.NONE
                    ? motionColor(design.defaultMotion()) : fillColor(design.defaultFill())));

        // The preview stage. A design carries its own background, so the plate behind
        // it is neutral rather than the panel's glaze, and translucent bodies read
        // against something instead of dissolving into the card.
        int[] stage = { card[0] + 1, card[1] + headH, card[2] - 2, card[3] - headH - 1 };
        SettingsTheme.rect(ctx, stage[0], stage[1], stage[2], stage[3], 0xFF6E90B8);

        renderPreviewCluster(ctx, design, index, stage);
    }

    /**
     * Draw the sample keys for one design, filling the given stage.
     *
     * <p>The design's own palette is used rather than the player's current one: the
     * card is showing what the design will look like once picked, and picking it
     * applies exactly these colors.
     */
    private void renderPreviewCluster(GuiGraphicsExtractor ctx, KeystrokesDesign design,
                                      int index, int[] stage) {
        int cx = stage[0] + stage[2] / 2;
        int cy = stage[1] + stage[3] / 2;

        if (design == KeystrokesDesign.TIMELINE) {
            // The timeline has no per-key body to show, so the card says what it is
            // rather than drawing an empty stage. On the light preview plate the
            // muted gray was all but invisible, so it takes the design's own label
            // color like every other card's keys do.
            SettingsTheme.centeredText(ctx, this.font, "PRESS HISTORY", cx,
                cy - this.font.lineHeight / 2, design.defaultColors()[0] | 0xFF000000);
            return;
        }

        List<SimpleCPSConfig.KeyButtonData> keys = previewKeys(design);
        int[] extent = previewExtent(design);
        int margin = d(8, 3);
        // Fit rather than clip. A whole-number scale keeps the edges crisp, but the
        // arranged designs are much taller than the others, and forcing a minimum of
        // 1 made them spill out of the card and across their neighbors.
        float fit = Math.min(
            (stage[2] - margin * 2) / (float) Math.max(1, extent[0]),
            (stage[3] - margin * 2) / (float) Math.max(1, extent[1]));
        float scale = fit >= 1f ? (float) Math.floor(fit) : Math.max(0.15f, fit);

        int[] palette = design.defaultColors();
        int idle = palette[0] | 0xFF000000;
        int accent = palette[1] | 0xFF000000;
        int bg = (palette[3] << 24) | (palette[2] & 0x00FFFFFF);

        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - extent[0] * scale / 2f, cy - extent[1] * scale / 2f);
        ctx.pose().scale(scale, scale);

        for (int k = 0; k < keys.size(); k++) {
            SimpleCPSConfig.KeyButtonData btn = keys.get(k);
            KeystrokesRender.KeyStyle style = KeystrokesRender.KeyStyle.preset(design);
            // Slotted per card: every card previews the same sample buttons, so keying
            // the state by keybind alone would make the whole grid share one animation.
            KeystrokesAnim.State state = galleryAnim.get(index * 16 + k);
            boolean pressed = previewPressed(index, k, keys.size());
            KeystrokesAnim.step(state, style.motions(), style.fills(), pressed, true, false, btn);
            KeystrokesRender.drawKey(ctx, this.font, btn, style, state,
                bg, idle, accent, false, null, null);
        }
        ctx.pose().popMatrix();
    }

    /**
     * RESET LAYOUT: back to a stock Keystrokes module, not just a stock key list.
     *
     * <p>It used to restore only the positions, which left the previous design's
     * texture, palette and animation painted over a baseline grid — a "reset" that
     * visibly did not reset. Everything the designer can reach goes back, in one
     * undo step.
     */
    private void resetEverything() {
        saveUndo();
        SimpleCPSConfig config = config();
        config.resetLayout();
        // resetLayout rebuilds the key list, so the per-key overrides go with it;
        // this puts the module-wide look back to the shipping default. The module
        // owns that list, so it is asked rather than copied here.
        new com.eymistaken.simplecps.modules.KeystrokesModule().resetVisualDefaults();
        recenter();

        selection.clear();
        glides.clear();
        clearLabelGuides();
        mode = Mode.IDLE;
        dirty = true;
    }

    /**
     * Adopt a design, with the arrangement, animation and palette it was drawn with.
     *
     * <p>The work itself lives on {@link KeystrokesDesign#applyTo} so the HUD editor
     * and the settings screen get exactly the same behavior; this only wraps it in
     * one undo step and drops the editing state the new layout invalidates.
     */
    private void applyDesign(KeystrokesDesign design) {
        saveUndo();
        design.applyTo(config());
        recenter();
        selection.clear();
        glides.clear();
        clearLabelGuides();
        mode = Mode.IDLE;
        dirty = true;
    }

    private boolean handleGalleryClick(int vx, int vy) {
        int[] close = galleryCloseRect();
        if (SettingsTheme.inside(vx, vy, close[0], close[1], close[2], close[3])) {
            galleryOpen = false;
            return true;
        }
        int[] body = galleryBodyRect();
        if (SettingsTheme.inside(vx, vy, body[0], body[1], body[2], body[3])) {
            KeystrokesDesign[] designs = KeystrokesDesign.values();
            for (int i = 0; i < designs.length; i++) {
                int[] card = galleryCardRect(i);
                if (!SettingsTheme.inside(vx, vy, card[0], card[1], card[2], card[3])) continue;
                applyDesign(designs[i]);
                return true;
            }
            return true;
        }
        int[] r = galleryPanelRect();
        // A click outside the panel closes it, matching the context menu.
        if (!SettingsTheme.inside(vx, vy, r[0], r[1], r[2], r[3])) galleryOpen = false;
        return true;
    }

    // --- Animation panel ---------------------------------------------------

    /**
     * One line of the animation drawer.
     *
     * <p>A drawer of tickboxes rather than a set of cycles because animations are no
     * longer mutually exclusive: a key can squish and sweep and trail a ghost at the
     * same time, and a control that only ever shows one value cannot say that.
     */
    private record AnimRow(String kind, String label, Object value) {}

    private List<AnimRow> animRows() {
        List<AnimRow> rows = new ArrayList<>();
        rows.add(new AnimRow("head", "MOTION", null));
        for (KeystrokesAnim.Motion motion : KeystrokesAnim.Motion.values()) {
            if (motion == KeystrokesAnim.Motion.NONE) continue;   // "none" is nothing ticked
            rows.add(new AnimRow("motion", SettingsTheme.up(motion.display()), motion));
        }
        rows.add(new AnimRow("head", "FILL", null));
        for (KeystrokesAnim.Fill fill : KeystrokesAnim.Fill.values()) {
            if (fill == KeystrokesAnim.Fill.NONE) continue;
            rows.add(new AnimRow("fill", SettingsTheme.up(fill.display()), fill));
        }
        rows.add(new AnimRow("head", "OPTIONS", null));
        rows.add(new AnimRow("direction", "DIRECTION", null));
        rows.add(new AnimRow("ghost", "RELEASE TRAIL", null));
        rows.add(new AnimRow("board", "BOARD", null));
        return rows;
    }

    /**
     * Row height for the drawer's tickboxes, shrunk to whatever the screen leaves.
     *
     * <p>Sixteen rows at the inspector's usual control height overflow the panel at
     * small window sizes, and the rows past the bottom edge would be invisible and
     * unclickable. Better a tighter list than a truncated one.
     */
    private int animRowUnit() {
        List<AnimRow> rows = animRows();
        int heads = 0, items = 0;
        for (AnimRow row : rows) {
            if (row.kind().equals("head")) heads++;
            else items++;
        }
        if (items == 0) return ctrlH;
        int available = panelH - pad * 4 - cpHeadH()
            - heads * d(20, 8) - (rows.size() - 1) * gap;
        return Math.max(d(13, 7), Math.min(ctrlH, available / items));
    }

    private int animRowH(AnimRow row) {
        return row.kind().equals("head") ? d(20, 8) : animRowUnit();
    }

    private int[] animPanelRect() {
        int w = Math.min(panelW - pad * 4, d(240, 100));
        int h = cpHeadH() + pad;
        for (AnimRow row : animRows()) h += animRowH(row) + gap;
        h += pad - gap;

        // Hung under the ANIM button rather than centerd, so it reads as that
        // button's drawer instead of as another modal. Pulled back up when the list
        // is taller than the room below the toolbar.
        int x = innerX + pad;
        for (ToolBtn b : toolbarButtons()) {
            if (b.id().equals("anim")) x = b.x();
        }
        x = Math.min(x, panelX + panelW - pad - w);
        int y = panelY + 1 + headerH + toolbarH + gap;
        y = Math.min(y, Math.max(panelY + pad, panelY + panelH - pad - h));
        return new int[] { x, y, w, h };
    }

    private int[] animRowRect(int index) {
        int[] r = animPanelRect();
        List<AnimRow> rows = animRows();
        int y = r[1] + cpHeadH() + pad;
        for (int i = 0; i < index && i < rows.size(); i++) {
            y += animRowH(rows.get(i)) + gap;
        }
        int h = index < rows.size() ? animRowH(rows.get(index)) : ctrlH;
        return new int[] { r[0] + pad, y, r[2] - pad * 2, h };
    }

    /** A tickbox: an empty well, with a check drawn into it when set. */
    private void renderCheck(GuiGraphicsExtractor ctx, int x, int y, int size, boolean on) {
        SettingsTheme.sunken(ctx, x, y, size, size, SettingsTheme.FIELD_BG, SettingsTheme.FIELD_RIM);
        if (!on) return;
        // Two strokes, drawn as blocks so the tick stays crisp at every UI scale.
        int unit = Math.max(1, size / 6);
        int cx = x + size / 2;
        int cy = y + size / 2;
        SettingsTheme.rect(ctx, cx - unit * 2, cy, unit, unit * 2, SettingsTheme.KNOB_ON);
        SettingsTheme.rect(ctx, cx - unit, cy + unit, unit, unit, SettingsTheme.KNOB_ON);
        SettingsTheme.rect(ctx, cx, cy - unit, unit, unit * 2, SettingsTheme.KNOB_ON);
        SettingsTheme.rect(ctx, cx + unit, cy - unit * 2, unit, unit * 2, SettingsTheme.KNOB_ON);
    }

    private void renderAnimPanel(GuiGraphicsExtractor ctx, int mx, int my) {
        ctx.nextStratum();
        int[] r = animPanelRect();
        // Opaque, not the palette's translucent panel color: this floats over the
        // canvas, and the checkerboard showing through it made the rows hard to read.
        SettingsTheme.rect(ctx, r[0], r[1], r[2], r[3], SettingsTheme.FOCUS_BG);
        SettingsTheme.raised(ctx, r[0], r[1], r[2], r[3], SettingsTheme.PANEL_BG,
            SettingsTheme.PANEL_LIGHT, SettingsTheme.PANEL_DARK);
        SettingsTheme.frame(ctx, r[0], r[1], r[2], r[3], SettingsTheme.BORDER);
        SettingsTheme.rect(ctx, r[0] + 1, r[1] + 1, r[2] - 2, cpHeadH(), SettingsTheme.CONTENT_HEAD);
        SettingsTheme.text(ctx, this.font, "ANIMATION", r[0] + d(10, 3),
            centerTextY(r[1], cpHeadH()), SettingsTheme.TEXT_LABEL);
        SettingsTheme.rightText(ctx, this.font, "COMBINE FREELY", r[0] + r[2] - d(10, 3),
            centerTextY(r[1], cpHeadH()), SettingsTheme.TEXT_FAINT);

        List<AnimRow> rows = animRows();
        List<KeystrokesAnim.Motion> motions = globalMotions();
        List<KeystrokesAnim.Fill> fills = globalFills();

        for (int i = 0; i < rows.size(); i++) {
            AnimRow row = rows.get(i);
            int[] rect = animRowRect(i);
            boolean hov = SettingsTheme.inside(mx, my, rect[0], rect[1], rect[2], rect[3]);

            if (row.kind().equals("head")) {
                SettingsTheme.text(ctx, this.font, row.label(), rect[0],
                    rect[1] + rect[3] - this.font.lineHeight, SettingsTheme.TEXT_FAINT);
                continue;
            }

            int textY = centerTextY(rect[1], rect[3]);
            switch (row.kind()) {
                case "motion", "fill" -> {
                    boolean on = row.kind().equals("motion")
                        ? motions.contains((KeystrokesAnim.Motion) row.value())
                        : fills.contains((KeystrokesAnim.Fill) row.value());
                    if (hov) SettingsTheme.rect(ctx, rect[0], rect[1], rect[2], rect[3],
                        SettingsTheme.BTN_BG);
                    int box = Math.min(rect[3] - 2, d(14, 6));
                    renderCheck(ctx, rect[0] + 2, rect[1] + (rect[3] - box) / 2, box, on);
                    int color = on
                        ? (row.kind().equals("motion")
                            ? motionColor((KeystrokesAnim.Motion) row.value())
                            : fillColor((KeystrokesAnim.Fill) row.value()))
                        : SettingsTheme.TEXT_DIM;
                    SettingsTheme.text(ctx, this.font, row.label(),
                        rect[0] + 2 + box + gap, textY, color);
                }
                case "direction" -> {
                    boolean usable = directionMatters();
                    SettingsTheme.text(ctx, this.font, row.label(), rect[0], textY,
                        usable ? SettingsTheme.TEXT_FAINT : SettingsTheme.TEXT_OFF);
                    int labelW = d(84, 30);
                    int vx = rect[0] + labelW;
                    int vw2 = rect[2] - labelW;
                    SettingsTheme.button(ctx, vx, rect[1], vw2, rect[3], hov && usable);
                    SettingsTheme.text(ctx, this.font,
                        SettingsTheme.truncate(this.font,
                            usable ? SettingsTheme.up(globalDirection().display()) : "N/A",
                            vw2 - d(18, 6)),
                        vx + gap, textY, usable ? SettingsTheme.TEXT : SettingsTheme.TEXT_OFF);
                    int size = Math.max(2, d(4, 2));
                    caretDown(ctx, vx + vw2 - d(8, 3) - size, rect[1] + (rect[3] - size) / 2,
                        size, SettingsTheme.TEXT_MUTED);
                }
                default -> {
                    boolean on = row.kind().equals("ghost")
                        ? config().keystrokesGhost : config().keystrokesBoard;
                    SettingsTheme.text(ctx, this.font, row.label(), rect[0], textY,
                        SettingsTheme.TEXT_FAINT);
                    int tw = Math.min(rect[2] - d(84, 30), d(86, 44));
                    renderToggle(ctx, new int[] { rect[0] + rect[2] - tw, rect[1], tw, rect[3] },
                        on, true);
                }
            }
        }
    }

    private boolean handleAnimPanelClick(int vx, int vy, int button) {
        int[] r = animPanelRect();
        if (!SettingsTheme.inside(vx, vy, r[0], r[1], r[2], r[3])) {
            // Dismiss on any click outside, and swallow it. Letting it fall through
            // would hand the click straight back to the ANIM button that opened the
            // drawer, which would close and immediately reopen it.
            animOpen = false;
            return true;
        }
        List<AnimRow> rows = animRows();
        for (int i = 0; i < rows.size(); i++) {
            AnimRow row = rows.get(i);
            if (row.kind().equals("head")) continue;
            int[] rect = animRowRect(i);
            if (!SettingsTheme.inside(vx, vy, rect[0], rect[1], rect[2], rect[3])) continue;

            SimpleCPSConfig config = config();
            switch (row.kind()) {
                case "motion" -> {
                    List<KeystrokesAnim.Motion> next = new ArrayList<>(globalMotions());
                    KeystrokesAnim.Motion value = (KeystrokesAnim.Motion) row.value();
                    if (!next.remove(value)) next.add(value);
                    config.keystrokesMotions = next;
                }
                case "fill" -> {
                    List<KeystrokesAnim.Fill> next = new ArrayList<>(globalFills());
                    KeystrokesAnim.Fill value = (KeystrokesAnim.Fill) row.value();
                    if (!next.remove(value)) next.add(value);
                    config.keystrokesFills = next;
                    // The shared direction has to stay meaningful for what is left
                    // ticked, or a lone perimeter fill inherits "up" and never moves.
                    config.keystrokesFillDirection =
                        KeystrokesAnim.coerceAll(next, config.keystrokesFillDirection);
                }
                case "direction" -> {
                    if (!directionMatters()) break;
                    // Offer the directions the linear fills understand when there are
                    // any, and the rotations otherwise.
                    boolean linear = false;
                    for (KeystrokesAnim.Fill f : globalFills()) {
                        if (f.directional() && !f.perimeter()) linear = true;
                    }
                    KeystrokesAnim.Direction[] choices = KeystrokesAnim.choicesFor(
                        linear ? KeystrokesAnim.Fill.SWEEP : KeystrokesAnim.Fill.EDGE_RUN);
                    config.keystrokesFillDirection = cycleEnum(choices, globalDirection(),
                        button == 0 ? 1 : -1);
                }
                case "ghost" -> config.keystrokesGhost = !config.keystrokesGhost;
                case "board" -> config.keystrokesBoard = !config.keystrokesBoard;
                default -> { }
            }
            dirty = true;
            return true;
        }
        return true;
    }

    /** Step through an enum's values, wrapping. Shared by every cycling control. */
    private static <E> E cycleEnum(E[] values, E current, int step) {
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) index = i;
        }
        return values[Math.floorMod(index + step, values.length)];
    }

    // Import modal.

    private int[] importPanelRect() {
        int w = Math.max(d(200, 140), Math.min(d(460, 160), panelW - pad * 4));
        int h = cpHeadH() + pad + ctrlH + gap + this.font.lineHeight + gap * 2 + cpBtnH() + pad;
        return new int[] { panelX + (panelW - w) / 2, panelY + (panelH - h) / 2, w, h };
    }

    private int[] importFieldRect() {
        int[] r = importPanelRect();
        return new int[] { r[0] + pad, r[1] + cpHeadH() + pad, r[2] - pad * 2, ctrlH };
    }

    private int[] importConfirmRect() {
        int[] r = importPanelRect();
        int w = (r[2] - pad * 2 - gap) / 2;
        return new int[] { r[0] + r[2] - pad - w, r[1] + r[3] - pad - cpBtnH(), w, cpBtnH() };
    }

    private int[] importCancelRect() {
        int[] confirm = importConfirmRect();
        int[] r = importPanelRect();
        return new int[] { r[0] + pad, confirm[1], confirm[2], confirm[3] };
    }

    private void renderImportModal(GuiGraphicsExtractor ctx, int mx, int my) {
        ctx.nextStratum();
        SettingsTheme.rect(ctx, 0, 0, vw, vh, SettingsTheme.MODAL_SCRIM);

        int[] r = importPanelRect();
        SettingsTheme.rect(ctx, r[0], r[1], r[2], r[3], SettingsTheme.MODAL_BG);
        SettingsTheme.raised(ctx, r[0], r[1], r[2], r[3], SettingsTheme.PANEL_BG,
            SettingsTheme.PANEL_LIGHT, SettingsTheme.PANEL_DARK);

        SettingsTheme.rect(ctx, r[0] + 1, r[1] + 1, r[2] - 2, cpHeadH(), SettingsTheme.CONTENT_HEAD);
        SettingsTheme.text(ctx, this.font, "IMPORT LAYOUT CODE", r[0] + d(12, 3),
            centerTextY(r[1], cpHeadH()), SettingsTheme.MODAL_HEAD_FG);

        int[] field = importFieldRect();
        SettingsTheme.sunken(ctx, field[0], field[1], field[2], field[3],
            SettingsTheme.FIELD_BG, SettingsTheme.SEL_BORDER);
        if (importInput.getValue().isEmpty()) {
            SettingsTheme.text(ctx, this.font, "PASTE AN EYMHUD1- CODE (CTRL+V)", field[0] + gap,
                centerTextY(field[1], field[3]), SettingsTheme.TEXT_PLACEHOLD);
        }
        importInput.render(ctx, this.font, field[0] + gap, field[1], field[2] - gap * 2, field[3],
            true, SettingsTheme.TEXT);

        String message = importMessage.isEmpty()
            ? "THE LAYOUT IS REPLACED. CTRL+Z PUTS THE OLD ONE BACK."
            : SettingsTheme.up(importMessage);
        SettingsTheme.text(ctx, this.font,
            SettingsTheme.truncate(this.font, message, r[2] - pad * 2),
            r[0] + pad, field[1] + field[3] + gap,
            importMessage.isEmpty() ? SettingsTheme.MODAL_BODY_FG
                : importOk ? SettingsTheme.OK_TEXT : SettingsTheme.DANGER_TEXT);

        int[] cancel = importCancelRect();
        boolean cancelHov = SettingsTheme.inside(mx, my, cancel[0], cancel[1], cancel[2], cancel[3]);
        SettingsTheme.button(ctx, cancel[0], cancel[1], cancel[2], cancel[3], cancelHov);
        SettingsTheme.centeredText(ctx, this.font, "CANCEL", cancel[0] + cancel[2] / 2,
            centerTextY(cancel[1], cancel[3]), SettingsTheme.TEXT);

        int[] confirm = importConfirmRect();
        boolean confirmHov = SettingsTheme.inside(mx, my, confirm[0], confirm[1], confirm[2], confirm[3]);
        SettingsTheme.raised(ctx, confirm[0], confirm[1], confirm[2], confirm[3],
            confirmHov ? SettingsTheme.DONE_HOVER : SettingsTheme.DONE_BG,
            SettingsTheme.DONE_LIGHT, SettingsTheme.DONE_DARK);
        SettingsTheme.centeredText(ctx, this.font, "IMPORT", confirm[0] + confirm[2] / 2,
            centerTextY(confirm[1], confirm[3]), SettingsTheme.DONE_TEXT);
    }


    // --- Input -------------------------------------------------------------

    private boolean dragArmed = false;
    private long lastScrollUndo = 0;

    private boolean canvasContains(int vx, int vy) {
        return SettingsTheme.inside(vx, vy, canvasX, canvasY, canvasW, canvasH);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        int vmx = virtualX(click.x());
        int vmy = virtualY(click.y());
        int button = click.button();

        if (importOpen) {
            handleImportClick(vmx, vmy, button);
            return true;
        }
        if (galleryOpen) {
            if (button == 0) handleGalleryClick(vmx, vmy);
            return true;
        }
        if (animOpen && handleAnimPanelClick(vmx, vmy, button)) {
            return true;
        }
        if (colorOpen) {
            handleColorClick(vmx, vmy, button);
            return true;
        }
        if (menuOpen) {
            handleMenuClick(vmx, vmy);
            return true;
        }

        if (button == 0) {
            if (handleHeaderClick(vmx, vmy)) return true;
            if (handleToolbarClick(vmx, vmy, button)) return true;
            if (handleFooterClick(vmx, vmy)) return true;
            if (handleLeftRailClick(vmx, vmy)) return true;
            if (handleShortcutsClick(vmx, vmy)) return true;
            if (handleInspectorClick(vmx, vmy)) return true;
        }

        if (canvasContains(vmx, vmy)) return handleCanvasClick(vmx, vmy, button);
        if (button == 0) setLabelFocus(false);
        return false;
    }

    private boolean handleHeaderClick(int vx, int vy) {
        int[] close = headerCloseRect();
        if (SettingsTheme.inside(vx, vy, close[0], close[1], close[2], close[3])) {
            onClose();
            return true;
        }
        int[] settings = headerSettingsRect();
        if (SettingsTheme.inside(vx, vy, settings[0], settings[1], settings[2], settings[3])) {
            openSettings();
            return true;
        }
        return false;
    }

    private boolean handleToolbarClick(int vx, int vy, int button) {
        for (ToolBtn b : toolbarButtons()) {
            if (!SettingsTheme.inside(vx, vy, b.x(), b.y(), b.w(), b.h())) continue;
            // These two open panels, so a right-click on them is meaningless; the
            // rest ignore it rather than firing their left-click action.
            if (button != 0) return false;
            setLabelFocus(false);
            switch (b.id()) {
                case "undo" -> undo();
                case "redo" -> redo();
                case "snap" -> snapEnabled = !snapEnabled;
                case "designs" -> openGallery();
                case "anim" -> animOpen = !animOpen;
                case "export" -> exportLayout();
                case "import" -> openImport();
                case "reset" -> resetEverything();
                default -> { }
            }
            return true;
        }
        return false;
    }

    private boolean handleFooterClick(int vx, int vy) {
        int[] done = doneRect();
        if (SettingsTheme.inside(vx, vy, done[0], done[1], done[2], done[3])) {
            onClose();
            return true;
        }
        int[] cancel = cancelRect();
        if (SettingsTheme.inside(vx, vy, cancel[0], cancel[1], cancel[2], cancel[3])) {
            cancel();
            return true;
        }
        return false;
    }

    private boolean handleLeftRailClick(int vx, int vy) {
        if (vx < leftX || vx >= leftX + leftW) return false;

        for (AddTile tile : addTiles()) {
            if (!SettingsTheme.inside(vx, vy, tile.x(), tile.y(), tile.w(), tile.h())) continue;
            setLabelFocus(false);
            switch (tile.id()) {
                case "key" -> addButton("K", 0, 0, 21, 21, GLFW.GLFW_KEY_UNKNOWN, false, false);
                case "mod" -> addButton("MOD", 0, 80, 33, 13, GLFW.GLFW_KEY_LEFT_CONTROL, false, false);
                case "lmb" -> addButton("LMB", 0, 60, 33, 21, 0, true, true);
                case "rmb" -> addButton("RMB", 33, 60, 33, 21, 1, true, true);
                case "space" -> addButton("-----", 0, 40, 67, 13, GLFW.GLFW_KEY_SPACE, false, false);
                default -> { }
            }
            return true;
        }

        if (SettingsTheme.inside(vx, vy, leftX, layersY, leftW, layersH)) {
            int index = (vy + layersScroll - layersY) / Math.max(1, layerRowH);
            List<SimpleCPSConfig.KeyButtonData> keys = layout();
            if (index >= 0 && index < keys.size()) {
                SimpleCPSConfig.KeyButtonData btn = keys.get(index);
                setLabelFocus(false);
                int rowY = layersY + index * layerRowH - layersScroll;
                int[] dot = layerDotRect(rowY);
                if (SettingsTheme.inside(vx, vy, dot[0], dot[1], dot[2], dot[3])) {
                    saveUndo();
                    btn.hidden = !btn.hidden;
                    // A hidden key cannot be dragged or seen, so leaving it selected
                    // would make the inspector edit something invisible.
                    if (btn.hidden) selection.remove(btn);
                    dirty = true;
                    return true;
                }
                if (isCtrlDown()) {
                    if (!selection.remove(btn)) selection.add(btn);
                } else {
                    selectOnly(btn);
                }
                mode = Mode.IDLE;
            }
            return true;
        }

        for (int i = 0; i < PRESETS.length; i++) {
            int[] r = presetRect(i);
            if (!SettingsTheme.inside(vx, vy, r[0], r[1], r[2], r[3])) continue;
            setLabelFocus(false);
            saveUndo();
            switch (i) {
                case 0 -> config().resetLayout();
                case 1 -> replaceLayout(fullPreset());
                default -> replaceLayout(mousePreset());
            }
            selection.clear();
            glides.clear();
            mode = Mode.IDLE;
            dirty = true;
            return true;
        }
        return false;
    }

    private boolean handleShortcutsClick(int vx, int vy) {
        if (shortcutsH <= 0) return false;
        int[] head = shortcutsHeaderRect();
        if (!SettingsTheme.inside(vx, vy, head[0], head[1], head[2], head[3])) return false;
        setLabelFocus(false);
        shortcutsCollapsed = !shortcutsCollapsed;
        return true;
    }

    private boolean handleInspectorClick(int vx, int vy) {
        if (vx < rightX || selection.isEmpty()) return false;
        int viewH = inspectorViewH();
        if (!SettingsTheme.inside(vx, vy, rightX, inspectorTop(), rightW, viewH)) return false;

        int scrolled = vy + inspectorScroll;
        int y = inspectorTop();
        for (InspRow row : inspectorRows()) {
            if (scrolled >= y && scrolled < y + row.h()) {
                if (!row.id().startsWith("#")) handleInspectorRowClick(row.id(), y, vx, scrolled);
                return true;
            }
            y += row.h();
        }
        return true;
    }

    private void handleInspectorRowClick(String id, int rowY, int vx, int vy) {
        if (!id.equals("label")) setLabelFocus(false);

        switch (id) {
            case "keybind" -> {
                int[] r = keybindRect(rowY);
                if (SettingsTheme.inside(vx, vy, r[0], r[1], r[2], r[3])) mode = Mode.KEYBIND;
            }
            case "label" -> {
                int[] r = labelFieldRect(rowY);
                if (SettingsTheme.inside(vx, vy, r[0], r[1], r[2], r[3]) && single() != null) {
                    if (!labelFocused) {
                        labelFocused = true;
                        mode = Mode.IDLE;
                    }
                    labelInput.onClick(this.font, vx, r[0] + gap, false);
                } else {
                    setLabelFocus(false);
                }
            }
            case "position", "size" -> {
                boolean isSize = id.equals("size");
                int[][] rects = pairStepperRects(rowY);
                for (int i = 0; i < 2; i++) {
                    int step = stepperHit(rects[i], vx, vy);
                    if (step == 0) continue;
                    final int delta = step;
                    final boolean first = i == 0;
                    if (isSize) {
                        resizeLabel(b -> {
                            if (first) b.w = Math.max(MIN_KEY_SIZE, b.w + delta);
                            else b.h = Math.max(MIN_KEY_SIZE, b.h + delta);
                        }, true);
                    } else {
                        patchSel(b -> {
                            if (first) b.x += delta;
                            else b.y += delta;
                        });
                    }
                    return;
                }
            }
            case "placement" -> {
                int[] r = centerBtnRect(rowY);
                if (SettingsTheme.inside(vx, vy, r[0], r[1], r[2], r[3])) {
                    patchSel(b -> {
                        b.labelX = -1;
                        b.labelY = -1;
                    });
                }
            }
            case "content" -> {
                if (insideRect(segRect(rowY, 0), vx, vy)) patchSel(b -> b.labelLine = false);
                else if (insideRect(segRect(rowY, 1), vx, vy)) patchSel(b -> b.labelLine = true);
            }
            case "scale" -> {
                int step = stepperHit(scaleStepperRect(rowY), vx, vy);
                if (step != 0) {
                    final int delta = step * (lineMode() ? LINE_WEIGHT_STEP : LABEL_SCALE_STEP);
                    resizeLabel(b -> b.labelScale = clampInt(b.labelScale + delta,
                        SimpleCPSConfig.MIN_LABEL_SCALE, SimpleCPSConfig.MAX_LABEL_SCALE), true);
                }
            }
            case "linewidth" -> {
                int step = stepperHit(scaleStepperRect(rowY), vx, vy);
                if (step != 0) {
                    final int delta = step * LINE_WIDTH_STEP;
                    resizeLabel(b -> b.lineWidthPercent = clampInt(b.lineWidthPercent + delta,
                        SimpleCPSConfig.MIN_LINE_WIDTH, SimpleCPSConfig.MAX_LINE_WIDTH), true);
                }
            }
            case "style" -> {
                boolean line = lineMode();
                if (!line && insideRect(styleBtnRect(rowY, 0), vx, vy)) patchSel(b -> b.bold = !b.bold);
                else if (!line && insideRect(styleBtnRect(rowY, 1), vx, vy)) patchSel(b -> b.italic = !b.italic);
                else if (!line && insideRect(styleBtnRect(rowY, 2), vx, vy)) patchSel(b -> b.underlined = !b.underlined);
                else if (insideRect(styleBtnRect(rowY, 3), vx, vy)) patchSel(b -> b.shadow = !b.shadow);
            }
            case "kdesign", "kmotion", "kfill", "kdirection" -> {
                if (insideRect(designBtnRect(rowY), vx, vy)) cycleDesignRow(id);
            }
            case "color", "pressed" -> {
                boolean isPressed = id.equals("pressed");
                if (insideRect(colorBtnRect(rowY, isPressed), vx, vy)) openColorEditor(isPressed);
            }
            case "cps" -> {
                SimpleCPSConfig.KeyButtonData k = single();
                if (k != null && k.isMouse && insideRect(toggleRect(rowY), vx, vy)) {
                    patchSel(b -> b.showCps = !b.showCps);
                }
            }
            case "anim" -> {
                if (insideRect(toggleRect(rowY), vx, vy)) {
                    patchSel(b -> b.animationEnabled = !(b.animationEnabled == null || b.animationEnabled));
                }
            }
            default -> { }
        }
    }

    private static boolean insideRect(int[] r, int vx, int vy) {
        return SettingsTheme.inside(vx, vy, r[0], r[1], r[2], r[3]);
    }

    /** -1 for the minus button, +1 for plus, 0 for neither. */
    private int stepperHit(int[] r, int vx, int vy) {
        if (vy < r[1] || vy >= r[1] + r[3]) return 0;
        if (vx >= r[0] && vx < r[0] + stepBtn) return -1;
        if (vx >= r[0] + r[2] - stepBtn && vx < r[0] + r[2]) return 1;
        return 0;
    }

    private boolean handleCanvasClick(int vmx, int vmy, int button) {
        setLabelFocus(false);
        SimpleCPSConfig.KeyButtonData hit = keyAt(vmx, vmy);

        if (button == 0) {
            long now = System.currentTimeMillis();
            boolean doubleClick = hit != null && hit == lastClickTarget
                && now - lastClickMillis < DOUBLE_CLICK_MILLIS;
            lastClickMillis = now;
            lastClickTarget = hit;

            if (hit != null) {
                if (doubleClick) {
                    selectOnly(hit);
                    mode = Mode.LABEL;
                    return true;
                }
                if (isCtrlDown()) {
                    if (!selection.remove(hit)) selection.add(hit);
                } else if (!selection.contains(hit)) {
                    selectOnly(hit);
                }
                if (mode != Mode.LABEL || !selection.contains(hit) || selection.size() != 1) {
                    mode = Mode.IDLE;
                }
                armDrag(vmx, vmy);
                return true;
            }

            mode = Mode.IDLE;
            marqueeActive = true;
            marqueeMoved = false;
            marqueeAdditive = isCtrlDown();
            marqueeBase.clear();
            if (marqueeAdditive) {
                marqueeBase.addAll(selection);
            } else {
                selection.clear();
            }
            marqueeStartX = vmx;
            marqueeStartY = vmy;
            marqueeX = vmx;
            marqueeY = vmy;
            return true;
        }

        if (button == 1) {
            if (hit != null) {
                // Opening is deferred to the release so that holding and dragging
                // moves the label instead.
                rightTarget = hit;
                rightStartX = vmx;
                rightStartY = vmy;
                rightDragging = false;
            } else {
                menuOpen = false;
            }
            return true;
        }
        return false;
    }

    private void armDrag(int vmx, int vmy) {
        dragArmed = true;
        dragging = false;
        dragStartX = vmx;
        dragStartY = vmy;
        prevSnapCorrX = 0;
        prevSnapCorrY = 0;
        dragSnapshots.clear();
        for (SimpleCPSConfig.KeyButtonData b : selection) dragSnapshots.add(new Snapshot(b));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        int vmx = virtualX(click.x());
        int vmy = virtualY(click.y());
        int button = click.button();

        if (importOpen) return true;
        if (colorOpen && cpDrag != CpDrag.NONE) {
            updateColorDrag(vmx, vmy);
            return true;
        }
        if (button == 0 && marqueeActive) {
            marqueeX = vmx;
            marqueeY = vmy;
            int slop = d(3, 1);
            if (Math.abs(vmx - marqueeStartX) > slop || Math.abs(vmy - marqueeStartY) > slop) {
                marqueeMoved = true;
            }
            updateMarqueeSelection();
            return true;
        }
        if (button == 0 && dragArmed) {
            updateDrag(vmx, vmy);
            return true;
        }
        if (button == 1 && rightTarget != null) {
            updateRightDrag(vmx, vmy);
            return true;
        }
        return false;
    }

    private void updateMarqueeSelection() {
        selection.clear();
        if (marqueeAdditive) selection.addAll(marqueeBase);
        if (!marqueeMoved) return;

        int x1 = Math.min(marqueeStartX, marqueeX);
        int y1 = Math.min(marqueeStartY, marqueeY);
        int x2 = Math.max(marqueeStartX, marqueeX);
        int y2 = Math.max(marqueeStartY, marqueeY);
        for (SimpleCPSConfig.KeyButtonData btn : layout()) {
            if (btn.hidden || selection.contains(btn)) continue;
            int[] r = keyRect(btn);
            boolean intersects = !(r[0] + r[2] < x1 || r[0] > x2 || r[1] + r[3] < y1 || r[1] > y2);
            if (intersects) selection.add(btn);
        }
    }

    private void updateDrag(int vmx, int vmy) {
        if (dragSnapshots.isEmpty()) return;
        int dx = Math.round((vmx - dragStartX) / (float) zoom);
        int dy = Math.round((vmy - dragStartY) / (float) zoom);
        if (!dragging) {
            // No undo entry for a click that never became a drag.
            if (dx == 0 && dy == 0) return;
            saveUndo();
            dragging = true;
        }

        for (Snapshot s : dragSnapshots) {
            s.btn.x = s.x + dx;
            s.btn.y = s.y + dy;
        }

        Snapshot lead = dragSnapshots.get(0);
        int freeX = lead.x + dx;
        int freeY = lead.y + dy;
        snapX = null;
        snapY = null;
        if (snapEnabled) applySnapping(lead.btn);

        int finalDx = lead.btn.x - lead.x;
        int finalDy = lead.btn.y - lead.y;
        for (int i = 1; i < dragSnapshots.size(); i++) {
            Snapshot s = dragSnapshots.get(i);
            s.btn.x = s.x + finalDx;
            s.btn.y = s.y + finalDy;
        }

        // How far the magnet is holding the key away from the cursor. A change in
        // that — engaging, releasing, jumping to another guide — is the only
        // discontinuity in the drag, so it is the only part worth easing.
        int corrX = lead.btn.x - freeX;
        int corrY = lead.btn.y - freeY;
        int jumpX = corrX - prevSnapCorrX;
        int jumpY = corrY - prevSnapCorrY;
        prevSnapCorrX = corrX;
        prevSnapCorrY = corrY;
        for (Snapshot s : dragSnapshots) addGlide(s.btn, -jumpX, -jumpY);
        dirty = true;
    }

    private void updateRightDrag(int vmx, int vmy) {
        if (!rightDragging) {
            if (Math.abs(vmx - rightStartX) <= 2 && Math.abs(vmy - rightStartY) <= 2) return;
            rightDragging = true;
            saveUndo();
            if (!selection.contains(rightTarget)) selectOnly(rightTarget);
        }

        int[] r = keyRect(rightTarget);
        int relX = Math.round((vmx - r[0]) / (float) zoom);
        int relY = Math.round((vmy - r[1]) / (float) zoom);
        float[] size = KeystrokesRender.labelSize(this.font, rightTarget);
        int labelW = Math.round(size[0]);
        int labelH = Math.round(size[1]);

        // The cursor holds the middle of the label; these are where its top-left would
        // land with no magnet at all.
        int wantX = relX - labelW / 2;
        int wantY = relY - labelH / 2;

        // Same idea as the key magnet, scoped to the one key: centered, or flush
        // against an inside edge. Centered is the one that matters -- by hand it is a
        // single exact pixel, and it is the position almost every key wants.
        labelGuideKey = rightTarget;
        labelGuideX = null;
        labelGuideY = null;

        int centerX = Math.round((rightTarget.w - size[0]) / 2f);
        if (Math.abs(wantX - centerX) <= LABEL_SNAP) {
            // -1 keeps it centered as the label's own width changes later on.
            rightTarget.labelX = -1;
            labelGuideX = rightTarget.x + rightTarget.w / 2;
        } else if (Math.abs(wantX) <= LABEL_SNAP) {
            rightTarget.labelX = 0;
            labelGuideX = rightTarget.x;
        } else if (Math.abs(wantX - (rightTarget.w - labelW)) <= LABEL_SNAP) {
            rightTarget.labelX = rightTarget.w - labelW;
            labelGuideX = rightTarget.x + rightTarget.w;
        } else {
            rightTarget.labelX = wantX;
        }

        int centerY = Math.round((rightTarget.h - size[1]) / 2f);
        if (Math.abs(wantY - centerY) <= LABEL_SNAP) {
            rightTarget.labelY = -1;
            labelGuideY = rightTarget.y + rightTarget.h / 2;
        } else if (Math.abs(wantY) <= LABEL_SNAP) {
            rightTarget.labelY = 0;
            labelGuideY = rightTarget.y;
        } else if (Math.abs(wantY - (rightTarget.h - labelH)) <= LABEL_SNAP) {
            rightTarget.labelY = rightTarget.h - labelH;
            labelGuideY = rightTarget.y + rightTarget.h;
        } else {
            rightTarget.labelY = wantY;
        }
        dirty = true;
    }

    private void clearLabelGuides() {
        labelGuideKey = null;
        labelGuideX = null;
        labelGuideY = null;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        int vmx = virtualX(click.x());
        int vmy = virtualY(click.y());
        int button = click.button();

        if (cpDrag != CpDrag.NONE) {
            cpDrag = CpDrag.NONE;
            return true;
        }
        if (button == 0) {
            if (marqueeActive) {
                marqueeActive = false;
                marqueeMoved = false;
                marqueeBase.clear();
                return true;
            }
            if (dragArmed) {
                dragArmed = false;
                dragging = false;
                dragSnapshots.clear();
                snapX = null;
                snapY = null;
                return true;
            }
        } else if (button == 1 && rightTarget != null) {
            if (!rightDragging) openMenuAt(vmx, vmy, rightTarget);
            rightTarget = null;
            rightDragging = false;
            clearLabelGuides();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int vmx = virtualX(mouseX);
        int vmy = virtualY(mouseY);
        if (importOpen || colorOpen || animOpen) return true;

        int step = (int) Math.signum(verticalAmount);
        if (step == 0) return false;

        if (galleryOpen) {
            galleryScroll = clampInt(galleryScroll - step * galleryCardH() / 2,
                0, maxGalleryScroll());
            return true;
        }
        if (SettingsTheme.inside(vmx, vmy, leftX, layersY, leftW, layersH)) {
            layersScroll = clampInt(layersScroll - step * layerRowH, 0, maxLayersScroll());
            return true;
        }
        if (SettingsTheme.inside(vmx, vmy, rightX, inspectorTop(), rightW, inspectorViewH())) {
            inspectorScroll = clampInt(inspectorScroll - step * rowH, 0, maxInspectorScroll());
            return true;
        }
        if (canvasContains(vmx, vmy) && !selection.isEmpty()) {
            // One undo entry per gesture, not per notch: a wheel emits a burst of
            // events and a snapshot each would bury the rest of the history.
            long now = System.currentTimeMillis();
            if (now - lastScrollUndo > 500) saveUndo();
            lastScrollUndo = now;

            if (isCtrlDown()) {
                final int delta = step * LABEL_SCALE_STEP;
                resizeLabel(b -> b.labelScale = clampInt(b.labelScale + delta,
                    SimpleCPSConfig.MIN_LABEL_SCALE, SimpleCPSConfig.MAX_LABEL_SCALE), false);
            } else {
                final int delta = step * 2;
                resizeLabel(b -> {
                    if (b.w + delta < MIN_KEY_SIZE || b.h + delta < MIN_KEY_SIZE) return;
                    b.x -= delta / 2;
                    b.y -= delta / 2;
                    b.w += delta;
                    b.h += delta;
                }, false);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.input();

        // The gallery and the animation panel swallow every key while open, so a
        // stray Delete cannot reach the canvas behind them.
        if (galleryOpen) {
            if (keyInput.isEscape() || keyInput.isConfirmation()) galleryOpen = false;
            return true;
        }
        if (animOpen && keyInput.isEscape()) {
            animOpen = false;
            return true;
        }

        if (importOpen) {
            if (keyInput.isEscape()) {
                closeImport();
                return true;
            }
            if (keyInput.isConfirmation()) {
                runImport();
                return true;
            }
            importInput.keyPressed(keyInput);
            return true;
        }

        if (mode == Mode.KEYBIND) {
            if (!keyInput.isEscape()) {
                saveUndo();
                final int bound = keyCode;
                final String name = keyName(keyCode);
                patchNoUndo(b -> {
                    b.keyCode = bound;
                    b.isMouse = false;
                    b.label = name;
                });
            }
            mode = Mode.IDLE;
            return true;
        }

        if (labelFocused) {
            if (keyInput.isConfirmation() || keyInput.isEscape()) {
                setLabelFocus(false);
                return true;
            }
            labelInput.keyPressed(keyInput);
            commitLabelInput();
            return true;
        }

        boolean ctrl = keyInput.hasControlDownWithQuirk();
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_D) {
            duplicateSelection();
            return true;
        }

        SimpleCPSConfig.KeyButtonData k = single();
        if (mode == Mode.LABEL && k != null) {
            int step = keyInput.hasShiftDown() ? 5 : 1;
            float[] size = KeystrokesRender.labelSize(this.font, k);
            int anchorX = k.labelX == -1 ? Math.round((k.w - size[0]) / 2f) : k.labelX;
            int anchorY = k.labelY == -1 ? Math.round((k.h - size[1]) / 2f) : k.labelY;
            switch (keyCode) {
                case GLFW.GLFW_KEY_UP -> { k.labelY = anchorY - step; dirty = true; return true; }
                case GLFW.GLFW_KEY_DOWN -> { k.labelY = anchorY + step; dirty = true; return true; }
                case GLFW.GLFW_KEY_LEFT -> { k.labelX = anchorX - step; dirty = true; return true; }
                case GLFW.GLFW_KEY_RIGHT -> { k.labelX = anchorX + step; dirty = true; return true; }
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (k.label != null && !k.label.isEmpty()) {
                        k.label = k.label.substring(0, k.label.length() - 1);
                        dirty = true;
                    }
                    return true;
                }
                default -> { }
            }
            if (keyInput.isConfirmation() || keyInput.isEscape()) {
                mode = Mode.IDLE;
                return true;
            }
            return true;
        }

        if (!selection.isEmpty()) {
            int step = keyInput.hasShiftDown() ? 5 : 1;
            int dx = 0;
            int dy = 0;
            switch (keyCode) {
                case GLFW.GLFW_KEY_UP -> dy = -step;
                case GLFW.GLFW_KEY_DOWN -> dy = step;
                case GLFW.GLFW_KEY_LEFT -> dx = -step;
                case GLFW.GLFW_KEY_RIGHT -> dx = step;
                default -> { }
            }
            if (dx != 0 || dy != 0) {
                saveUndo();
                final int mx = dx;
                final int my = dy;
                for (SimpleCPSConfig.KeyButtonData b : selection) {
                    b.x += mx;
                    b.y += my;
                    // The glide is the opposite of the step, so the key starts drawn
                    // where it was and eases into its new spot.
                    addGlide(b, -mx, -my);
                }
                dirty = true;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                deleteSelection();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_F2) {
                if (single() != null) mode = Mode.LABEL;
                return true;
            }
            if (keyInput.isEscape()) {
                selection.clear();
                mode = Mode.IDLE;
                menuOpen = false;
                return true;
            }
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharacterEvent charInput) {
        if (importOpen) {
            importInput.charTyped(charInput);
            return true;
        }
        if (labelFocused) {
            labelInput.charTyped(charInput);
            commitLabelInput();
            return true;
        }
        SimpleCPSConfig.KeyButtonData k = single();
        if (mode == Mode.LABEL && k != null) {
            k.label = (k.label == null ? "" : k.label) + new String(Character.toChars(charInput.codepoint()));
            dirty = true;
            return true;
        }
        return super.charTyped(charInput);
    }


    // --- Menu, color editor and import -------------------------------------

    private void openMenuAt(int vx, int vy, SimpleCPSConfig.KeyButtonData target) {
        if (!selection.contains(target)) selectOnly(target);
        menuOpen = true;
        menuAnim.snap(0f);
        int h = menuHeight();
        menuX = Math.max(MENU_MARGIN, Math.min(vx + menuW > vw ? vx - menuW : vx, vw - menuW - MENU_MARGIN));
        menuY = Math.max(MENU_MARGIN, Math.min(vy + h > vh ? vy - h : vy, vh - h - MENU_MARGIN));
    }

    private static final int MENU_MARGIN = 2;

    private void handleMenuClick(int vx, int vy) {
        int h = menuHeight();
        if (SettingsTheme.inside(vx, vy, menuX, menuY, menuW, h)) {
            int index = (vy - menuY - d(4, 1)) / Math.max(1, menuRowH);
            if (index >= 0 && index < MENU_ITEMS.length) {
                switch (MENU_ITEMS[index][0]) {
                    case "label" -> { if (single() != null) mode = Mode.LABEL; }
                    case "bind" -> mode = Mode.KEYBIND;
                    case "center" -> patchSel(b -> {
                        b.labelX = -1;
                        b.labelY = -1;
                    });
                    case "duplicate" -> duplicateSelection();
                    case "delete" -> deleteSelection();
                    default -> { }
                }
            }
        }
        menuOpen = false;
    }

    private void openColorEditor(boolean pressed) {
        SimpleCPSConfig.KeyButtonData k = single();
        if (k == null) return;
        colorPressedTarget = pressed;
        int start = pressed ? k.btnPressedColor : k.btnColor;
        if (start == -1 || start == 0) {
            start = pressed ? config().keystrokesPressedColor : config().keystrokesColor;
        }
        float[] hsb = java.awt.Color.RGBtoHSB((start >> 16) & 0xFF, (start >> 8) & 0xFF, start & 0xFF, null);
        cpHue = hsb[0];
        cpSat = hsb[1];
        cpVal = hsb[2];
        colorOpen = true;
        menuOpen = false;
    }

    private void handleColorClick(int vx, int vy, int button) {
        int[] panel = colorPanelRect();
        if (!SettingsTheme.inside(vx, vy, panel[0], panel[1], panel[2], panel[3])) {
            colorOpen = false;
            return;
        }
        if (button != 0) return;

        if (insideRect(cpCloseRect(), vx, vy)) {
            colorOpen = false;
            return;
        }
        if (insideRect(cpSvRect(), vx, vy)) {
            cpDrag = CpDrag.SV;
            updateColorDrag(vx, vy);
            return;
        }
        if (insideRect(cpHueRect(), vx, vy)) {
            cpDrag = CpDrag.HUE;
            updateColorDrag(vx, vy);
            return;
        }
        for (int i = 0; i < SettingsTheme.SWATCHES.length; i++) {
            if (!insideRect(cpSwatchRect(i), vx, vy)) continue;
            int rgb = SettingsTheme.SWATCHES[i];
            float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            cpHue = hsb[0];
            cpSat = hsb[1];
            cpVal = hsb[2];
            return;
        }
        if (insideRect(cpApplyRect(), vx, vy)) {
            final int color = pickerColor();
            final boolean pressed = colorPressedTarget;
            patchSel(b -> {
                if (pressed) b.btnPressedColor = color;
                else b.btnColor = color;
            });
            colorOpen = false;
            return;
        }
        if (insideRect(cpResetRect(), vx, vy)) {
            final boolean pressed = colorPressedTarget;
            patchSel(b -> {
                if (pressed) b.btnPressedColor = -1;
                else b.btnColor = -1;
            });
            colorOpen = false;
        }
    }

    private void updateColorDrag(int vx, int vy) {
        if (cpDrag == CpDrag.SV) {
            int[] sv = cpSvRect();
            cpSat = clampUnit((vx - sv[0]) / (float) Math.max(1, sv[2] - 1));
            cpVal = 1f - clampUnit((vy - sv[1]) / (float) Math.max(1, sv[3] - 1));
        } else if (cpDrag == CpDrag.HUE) {
            int[] hue = cpHueRect();
            cpHue = clampUnit((vx - hue[0]) / (float) Math.max(1, hue[2] - 1));
        }
    }

    private static float clampUnit(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void exportLayout() {
        HudActions.Result result = HudActions.exportKeystrokes();
        if (result.ok()) copiedUntil = System.currentTimeMillis() + COPIED_MILLIS;
    }

    private void openImport() {
        importOpen = true;
        importMessage = "";
        importOk = false;
        importInput.setValue("");
        setLabelFocus(false);
        menuOpen = false;
        colorOpen = false;
    }

    private void closeImport() {
        importOpen = false;
        importInput.setValue("");
        importMessage = "";
    }

    private void handleImportClick(int vx, int vy, int button) {
        if (button != 0) return;
        int[] panel = importPanelRect();
        if (!SettingsTheme.inside(vx, vy, panel[0], panel[1], panel[2], panel[3])) return;

        int[] field = importFieldRect();
        if (insideRect(field, vx, vy)) {
            importInput.onClick(this.font, vx, field[0] + gap, false);
            return;
        }
        if (insideRect(importCancelRect(), vx, vy)) {
            closeImport();
            return;
        }
        if (insideRect(importConfirmRect(), vx, vy)) runImport();
    }

    private void runImport() {
        // Snapshot before the codec swaps the list out, so Ctrl+Z still has the
        // layout the import replaced.
        String before = gson.toJson(layout());
        HudActions.Result result = HudActions.importKeystrokes(importInput.getValue().trim());
        if (!result.ok()) {
            importMessage = result.message();
            importOk = false;
            return;
        }
        pushHistory(before);
        selection.clear();
        glides.clear();
        labelInputFor = null;
        mode = Mode.IDLE;
        dirty = true;
        closeImport();
    }

    // --- Layout actions ----------------------------------------------------

    private void addButton(String label, int x, int y, int w, int h, int keyCode, boolean mouse, boolean cps) {
        saveUndo();
        if (!selection.isEmpty()) {
            SimpleCPSConfig.KeyButtonData last = selection.get(selection.size() - 1);
            x = last.x + 10;
            y = last.y + 10;
        }
        SimpleCPSConfig.KeyButtonData btn =
            new SimpleCPSConfig.KeyButtonData(label, x, y, w, h, keyCode, mouse);
        btn.showCps = cps;
        layout().add(btn);
        selectOnly(btn);
        mode = Mode.IDLE;
        dirty = true;
    }

    private void replaceLayout(List<SimpleCPSConfig.KeyButtonData> keys) {
        layout().clear();
        layout().addAll(keys);
        recenter();
    }

    /** Re-measure the cluster the canvas centers on. See {@link #centerW}. */
    private void recenter() {
        int w = 0, h = 0;
        for (SimpleCPSConfig.KeyButtonData btn : layout()) {
            if (btn.hidden) continue;
            w = Math.max(w, btn.x + btn.w);
            h = Math.max(h, btn.y + btn.h);
        }
        centerW = Math.max(1, Math.min(SAFE_W, w));
        centerH = Math.max(1, Math.min(SAFE_H, h));
    }

    private List<SimpleCPSConfig.KeyButtonData> fullPreset() {
        List<SimpleCPSConfig.KeyButtonData> keys = new ArrayList<>();
        keys.add(new SimpleCPSConfig.KeyButtonData("Q", 0, 0, 21, 21, GLFW.GLFW_KEY_Q));
        keys.add(new SimpleCPSConfig.KeyButtonData("W", 23, 0, 21, 21, GLFW.GLFW_KEY_W));
        keys.add(new SimpleCPSConfig.KeyButtonData("E", 46, 0, 21, 21, GLFW.GLFW_KEY_E));
        keys.add(new SimpleCPSConfig.KeyButtonData("A", 0, 23, 21, 21, GLFW.GLFW_KEY_A));
        keys.add(new SimpleCPSConfig.KeyButtonData("S", 23, 23, 21, 21, GLFW.GLFW_KEY_S));
        keys.add(new SimpleCPSConfig.KeyButtonData("D", 46, 23, 21, 21, GLFW.GLFW_KEY_D));

        SimpleCPSConfig.KeyButtonData lmb = new SimpleCPSConfig.KeyButtonData("LMB", 0, 46, 33, 21, 0, true);
        lmb.showCps = true;
        keys.add(lmb);
        SimpleCPSConfig.KeyButtonData rmb = new SimpleCPSConfig.KeyButtonData("RMB", 34, 46, 33, 21, 1, true);
        rmb.showCps = true;
        keys.add(rmb);

        SimpleCPSConfig.KeyButtonData space =
            new SimpleCPSConfig.KeyButtonData("-----", 0, 69, 67, 13, GLFW.GLFW_KEY_SPACE);
        // Line mode is what the row of dashes was always imitating. The label is kept
        // so switching back to TEXT gives the familiar look rather than an empty key.
        space.labelLine = true;
        keys.add(space);

        keys.add(new SimpleCPSConfig.KeyButtonData("CTRL", 0, 84, 33, 13, GLFW.GLFW_KEY_LEFT_CONTROL));
        keys.add(new SimpleCPSConfig.KeyButtonData("SHIFT", 34, 84, 33, 13, GLFW.GLFW_KEY_LEFT_SHIFT));
        return keys;
    }

    private List<SimpleCPSConfig.KeyButtonData> mousePreset() {
        List<SimpleCPSConfig.KeyButtonData> keys = new ArrayList<>();
        SimpleCPSConfig.KeyButtonData lmb = new SimpleCPSConfig.KeyButtonData("LMB", 0, 0, 33, 27, 0, true);
        lmb.showCps = true;
        SimpleCPSConfig.KeyButtonData rmb = new SimpleCPSConfig.KeyButtonData("RMB", 34, 0, 33, 27, 1, true);
        rmb.showCps = true;
        keys.add(lmb);
        keys.add(rmb);
        return keys;
    }

    private void duplicateSelection() {
        if (selection.isEmpty()) return;
        saveUndo();
        List<SimpleCPSConfig.KeyButtonData> clones = new ArrayList<>();
        for (SimpleCPSConfig.KeyButtonData b : selection) {
            // Through JSON so every field comes along, including any added later.
            SimpleCPSConfig.KeyButtonData copy =
                gson.fromJson(gson.toJson(b), SimpleCPSConfig.KeyButtonData.class);
            copy.x += 10;
            copy.y += 10;
            clones.add(copy);
        }
        layout().addAll(clones);
        selection.clear();
        selection.addAll(clones);
        menuOpen = false;
        dirty = true;
    }

    private void deleteSelection() {
        if (selection.isEmpty()) return;
        saveUndo();
        layout().removeAll(selection);
        selection.clear();
        glides.clear();
        clearLabelGuides();
        labelInputFor = null;
        mode = Mode.IDLE;
        menuOpen = false;
        dirty = true;
    }

    // --- History -----------------------------------------------------------

    private void pushHistory(String json) {
        undoStack.push(json);
        while (undoStack.size() > MAX_HISTORY) undoStack.removeLast();
        redoStack.clear();
    }

    /**
     * Everything this screen can change, in one object.
     *
     * <p>History used to hold only the key list. That was enough while the designer
     * only moved keys about, but selecting a design now rewrites the module's
     * animation and palette too — and an undo that put the keys back while leaving
     * the colors changed would be worse than no undo at all.
     */
    private record EditState(List<SimpleCPSConfig.KeyButtonData> layout,
                            KeystrokesDesign design,
                            List<KeystrokesAnim.Motion> motions,
                            List<KeystrokesAnim.Fill> fills,
                            KeystrokesAnim.Direction direction,
                            boolean ghost, boolean board,
                            int color, int pressedColor, int bgColor, int bgOpacity) {}

    private EditState snapshot() {
        SimpleCPSConfig c = config();
        return new EditState(layout(), c.keystrokesDesign, globalMotions(), globalFills(),
            c.keystrokesFillDirection, c.keystrokesGhost, c.keystrokesBoard,
            c.keystrokesColor, c.keystrokesPressedColor,
            c.keystrokesBackgroundColor, c.keystrokesBackgroundOpacity);
    }

    private void saveUndo() {
        pushHistory(gson.toJson(snapshot()));
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(gson.toJson(snapshot()));
        restoreLayout(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(gson.toJson(snapshot()));
        restoreLayout(redoStack.pop());
    }

    /**
     * Swap the layout for a stored one. The selection holds references into the list
     * being replaced, so it has to go with it — the same for the glides and the label
     * field, which would otherwise animate and edit objects nothing draws any more.
     */
    private void restoreLayout(String json) {
        EditState snap = gson.fromJson(json, EditState.class);
        if (snap == null) return;

        SimpleCPSConfig c = config();
        c.keystrokesDesign = snap.design();
        c.keystrokesMotions = KeystrokesAnim.cleanMotions(snap.motions());
        c.keystrokesFills = KeystrokesAnim.cleanFills(snap.fills());
        c.keystrokesFillDirection =
            KeystrokesAnim.coerceAll(c.keystrokesFills, snap.direction());
        c.keystrokesGhost = snap.ghost();
        c.keystrokesBoard = snap.board();
        c.keystrokesColor = snap.color();
        c.keystrokesPressedColor = snap.pressedColor();
        c.keystrokesBackgroundColor = snap.bgColor();
        c.keystrokesBackgroundOpacity = snap.bgOpacity();

        replaceLayout(SimpleCPSConfig.sanitizeKeystrokesLayout(snap.layout()));
        selection.clear();
        glides.clear();
        clearLabelGuides();
        labelInputFor = null;
        labelFocused = false;
        mode = Mode.IDLE;
        menuOpen = false;
        colorOpen = false;
        dirty = true;
    }

    // --- Snapping ----------------------------------------------------------

    /**
     * Pull {@code target} onto the nearest edge, center or 2px gap of another key.
     * Works in layout coordinates, and stops at the first match on each axis so a
     * later key cannot quietly override an earlier snap.
     */
    private void applySnapping(SimpleCPSConfig.KeyButtonData target) {
        int threshold = 3;
        int keyGap = 2;
        int tLeft = target.x;
        int tRight = target.x + target.w;
        int tTop = target.y;
        int tBottom = target.y + target.h;
        int tCX = tLeft + target.w / 2;
        int tCY = tTop + target.h / 2;

        for (SimpleCPSConfig.KeyButtonData other : layout()) {
            if (other == target || other.hidden || selection.contains(other)) continue;
            int oLeft = other.x;
            int oRight = other.x + other.w;
            int oTop = other.y;
            int oBottom = other.y + other.h;
            int oCX = oLeft + other.w / 2;
            int oCY = oTop + other.h / 2;

            if (snapX == null) {
                if (Math.abs(tLeft - oLeft) <= threshold) { target.x = oLeft; snapX = oLeft; }
                else if (Math.abs(tRight - oRight) <= threshold) { target.x = oRight - target.w; snapX = oRight; }
                else if (Math.abs(tLeft - oRight) <= threshold) { target.x = oRight; snapX = oRight; }
                else if (Math.abs(tRight - oLeft) <= threshold) { target.x = oLeft - target.w; snapX = oLeft; }
                else if (Math.abs(tCX - oCX) <= threshold) { target.x = oCX - target.w / 2; snapX = oCX; }
                else if (Math.abs(tLeft - (oRight + keyGap)) <= threshold) {
                    target.x = oRight + keyGap;
                    snapX = oRight + keyGap;
                } else if (Math.abs(tRight - (oLeft - keyGap)) <= threshold) {
                    target.x = oLeft - keyGap - target.w;
                    snapX = oLeft - keyGap;
                }
            }
            if (snapY == null) {
                if (Math.abs(tTop - oTop) <= threshold) { target.y = oTop; snapY = oTop; }
                else if (Math.abs(tBottom - oBottom) <= threshold) { target.y = oBottom - target.h; snapY = oBottom; }
                else if (Math.abs(tTop - oBottom) <= threshold) { target.y = oBottom; snapY = oBottom; }
                else if (Math.abs(tBottom - oTop) <= threshold) { target.y = oTop - target.h; snapY = oTop; }
                else if (Math.abs(tCY - oCY) <= threshold) { target.y = oCY - target.h / 2; snapY = oCY; }
                else if (Math.abs(tTop - (oBottom + keyGap)) <= threshold) {
                    target.y = oBottom + keyGap;
                    snapY = oBottom + keyGap;
                } else if (Math.abs(tBottom - (oTop - keyGap)) <= threshold) {
                    target.y = oTop - keyGap - target.h;
                    snapY = oTop - keyGap;
                }
            }
            if (snapX != null && snapY != null) return;
        }
    }

    // --- Glides ------------------------------------------------------------

    private void addGlide(SimpleCPSConfig.KeyButtonData btn, int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        Glide glide = glides.computeIfAbsent(btn, k -> new Glide());
        glide.x.snap(glide.x.get() + dx);
        glide.y.snap(glide.y.get() + dy);
    }

    private int glideX(SimpleCPSConfig.KeyButtonData btn) {
        Glide glide = glides.get(btn);
        return glide == null ? 0 : Math.round(glide.x.get());
    }

    private int glideY(SimpleCPSConfig.KeyButtonData btn) {
        Glide glide = glides.get(btn);
        return glide == null ? 0 : Math.round(glide.y.get());
    }

    /** Step every offset one frame toward zero, dropping the ones that got there. */
    private void advanceGlides() {
        if (glides.isEmpty()) return;
        glides.values().removeIf(glide -> {
            int rx = Math.round(glide.x.update(0f, GLIDE_DURATION, Easings::backOut));
            int ry = Math.round(glide.y.update(0f, GLIDE_DURATION, Easings::backOut));
            return rx == 0 && ry == 0;
        });
    }

    // --- Selection helpers -------------------------------------------------

    private SimpleCPSConfig.KeyButtonData single() {
        return selection.size() == 1 ? selection.get(0) : null;
    }

    private void selectOnly(SimpleCPSConfig.KeyButtonData btn) {
        selection.clear();
        selection.add(btn);
    }

    /**
     * Step one of the inspector's design rows. The cycle runs through {@code null}
     * first, so "use the module setting" is a stop on the wheel rather than a
     * separate reset button the way the color rows need one.
     */
    private void cycleDesignRow(String id) {
        if (selection.isEmpty()) return;

        switch (id) {
            case "kdesign" -> {
                // A module-wide design describes the whole layout, so it is never
                // offered as one key's override.
                List<KeystrokesDesign> choices = new ArrayList<>();
                choices.add(null);
                for (KeystrokesDesign d : KeystrokesDesign.values()) {
                    if (!d.isModuleWide()) choices.add(d);
                }
                KeystrokesDesign next = stepList(choices, single() == null ? null : single().design);
                patchSel(b -> b.design = next);
            }
            case "kmotion" -> {
                List<KeystrokesAnim.Motion> choices = new ArrayList<>();
                choices.add(null);
                choices.addAll(java.util.Arrays.asList(KeystrokesAnim.Motion.values()));
                KeystrokesAnim.Motion next = stepList(choices, single() == null ? null : single().motion);
                patchSel(b -> b.motion = next);
            }
            case "kfill" -> {
                List<KeystrokesAnim.Fill> choices = new ArrayList<>();
                choices.add(null);
                choices.addAll(java.util.Arrays.asList(KeystrokesAnim.Fill.values()));
                KeystrokesAnim.Fill next = stepList(choices, single() == null ? null : single().fill);
                patchSel(b -> {
                    b.fill = next;
                    // The direction has to follow the fill: a perimeter fill left
                    // pointing "up" would sit at zero and read as broken. With no
                    // override the key follows the module's set, which polices its
                    // own direction already.
                    if (b.direction != null && next != null) {
                        b.direction = KeystrokesAnim.coerce(next, b.direction);
                    }
                });
            }
            default -> {
                KeystrokesAnim.Fill own = keyFill();
                List<KeystrokesAnim.Direction> choices = new ArrayList<>();
                choices.add(null);
                choices.addAll(java.util.Arrays.asList(KeystrokesAnim.choicesFor(
                    own != null ? own : KeystrokesAnim.Fill.SWEEP)));
                KeystrokesAnim.Direction next =
                    stepList(choices, single() == null ? null : single().direction);
                patchSel(b -> b.direction = next);
            }
        }
    }

    /** The entry after {@code current} in {@code choices}, wrapping; nulls allowed. */
    private static <T> T stepList(List<T> choices, T current) {
        int index = choices.indexOf(current);
        return choices.get(Math.floorMod(index + 1, choices.size()));
    }

    private void patchSel(java.util.function.Consumer<SimpleCPSConfig.KeyButtonData> fn) {
        if (selection.isEmpty()) return;
        saveUndo();
        patchNoUndo(fn);
    }

    private void patchNoUndo(java.util.function.Consumer<SimpleCPSConfig.KeyButtonData> fn) {
        for (SimpleCPSConfig.KeyButtonData b : selection) fn.accept(b);
        dirty = true;
    }

    /**
     * Apply a change that resizes the label, holding it on the spot it was centerd on.
     *
     * <p>{@code labelX}/{@code labelY} are the label's top-left corner, so a label that
     * has been placed by hand grows and shrinks from that corner — shortening a space
     * bar's line made it creep to the left instead of staying put. A label still at -1
     * re-centers itself and is left alone.
     */
    private void resizeLabel(java.util.function.Consumer<SimpleCPSConfig.KeyButtonData> change,
                             boolean snapshot) {
        if (selection.isEmpty()) return;
        if (snapshot) saveUndo();
        for (SimpleCPSConfig.KeyButtonData b : selection) {
            float[] before = KeystrokesRender.labelSize(this.font, b);
            change.accept(b);
            float[] after = KeystrokesRender.labelSize(this.font, b);
            if (b.labelX != -1) b.labelX += Math.round((before[0] - after[0]) / 2f);
            if (b.labelY != -1) b.labelY += Math.round((before[1] - after[1]) / 2f);
        }
        dirty = true;
    }

    private void setLabelFocus(boolean focused) {
        if (labelFocused && !focused) commitLabelInput();
        labelFocused = focused;
    }

    private void commitLabelInput() {
        SimpleCPSConfig.KeyButtonData k = single();
        if (k == null) return;
        String value = labelInput.getValue();
        if (!value.equals(k.label)) {
            k.label = value;
            dirty = true;
        }
    }

    /** Keep the inspector's label field showing the selected key's label. */
    private void syncLabelInput() {
        SimpleCPSConfig.KeyButtonData k = single();
        String want = k == null || k.label == null ? "" : k.label;
        if (k != labelInputFor) {
            labelInputFor = k;
            labelFocused = false;
            labelInput.setValue(want);
        } else if (!labelFocused && !labelInput.getValue().equals(want)) {
            labelInput.setValue(want);
        }
    }

    // --- Screen lifecycle --------------------------------------------------

    private void saveNow() {
        SimpleCPSConfig.save();
        dirty = false;
    }

    private void cancel() {
        restoreLayout(openSnapshot);
        // Write the restore through: an import along the way already touched disk, so
        // leaving it in memory only would make CANCEL a half-undo.
        saveNow();
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }

    private void openSettings() {
        saveNow();
        if (this.minecraft == null) return;
        // Coming from the settings screen, go back to it rather than stacking a second.
        this.minecraft.gui.setScreen(
            parent instanceof HudSettingsScreen ? parent : new HudSettingsScreen(parent));
    }

    @Override
    public void onClose() {
        setLabelFocus(false);
        saveNow();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    // --- Small helpers -----------------------------------------------------

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isCtrlDown() {
        if (this.minecraft == null) return false;
        long window = this.minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static boolean isModifierKey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT,
                 GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL,
                 GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
                 GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> true;
            default -> false;
        };
    }

    /** The LAYERS column's type tag. Derived, so no extra field has to be stored. */
    private static String typeOf(SimpleCPSConfig.KeyButtonData btn) {
        if (btn.isMouse) return "MOUSE";
        if (btn.keyCode == GLFW.GLFW_KEY_SPACE) return "SPACE";
        if (isModifierKey(btn.keyCode)) return "MOD";
        return "KEY";
    }

    /** What to call a key in the layers list and the status line. */
    private static String displayLabel(SimpleCPSConfig.KeyButtonData btn) {
        if (btn.labelLine || btn.label == null || btn.label.isBlank() || btn.label.equals("-----")) {
            return typeOf(btn);
        }
        return btn.label;
    }

    private static String keyName(int keyCode) {
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        if (name != null) return name.toUpperCase(java.util.Locale.ROOT);

        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_ESCAPE -> "ESC";
            case GLFW.GLFW_KEY_ENTER -> "ENTER";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_BACKSPACE -> "BACK";
            case GLFW.GLFW_KEY_INSERT -> "INS";
            case GLFW.GLFW_KEY_DELETE -> "DEL";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "SCROLL";
            case GLFW.GLFW_KEY_NUM_LOCK -> "NUM";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "PRT";
            case GLFW.GLFW_KEY_PAUSE -> "PAUSE";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_LEFT_SUPER -> "LSUPER";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_RIGHT_SUPER -> "RSUPER";
            case GLFW.GLFW_KEY_MENU -> "MENU";
            default -> {
                if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
                }
                if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
                    yield "NUM" + (keyCode - GLFW.GLFW_KEY_KP_0);
                }
                yield "K" + keyCode;
            }
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
