package com.eymistaken.simplecps.gui.settings;

import com.eymistaken.simplecps.ConfigPresetManager;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.api.HudPreview;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.gui.KeystrokesDesignerScreen;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.ActionRow;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.BoolRow;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.ChoiceRow;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.ColorRow;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.IntRow;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.Page;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.PositionRow;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.Row;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.Tab;
import com.eymistaken.simplecps.gui.settings.SettingsSchema.TextRow;
import com.eymistaken.simplecps.util.Anim;
import com.eymistaken.simplecps.util.Easings;
import com.eymistaken.simplecps.util.HudActions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The mod's settings screen: a module list on the left, tabbed settings in the
 * middle, and info / presets / share code on the right.
 *
 * <p>The right column leads with a live PREVIEW of the selected module, drawn at true
 * screen scale so color and size edits can be judged without leaving the menu. See
 * {@link HudPreview}.
 *
 * <p>Replaces the old Cloth Config screen. Everything is drawn with
 * {@code fill} and {@code text} the same way {@link HudEditorScreen} and
 * {@link KeystrokesDesignerScreen} already do, so the mod carries no optional GUI
 * dependency at all.
 *
 * <p><b>Layout.</b> The design mock is a 1600x900 desktop canvas. The screen is
 * laid out in a virtual viewport of its own — around 960x540 — and then scaled
 * onto the real window, which makes it the same physical size whatever the player
 * has GUI Scale set to. Laying out in Minecraft's GUI space directly does not
 * work here: the chrome would track the GUI space while the fixed 9px font could
 * not, so at GUI Scale 4 the text comes out twice the size the design wants.
 * {@link #computeRenderScale()} keeps the virtual-to-physical factor a whole
 * number so nothing goes soft, and two breakpoints fold the side panels away when
 * the viewport is genuinely narrow.
 *
 * <p><b>Saving.</b> Edits land on {@link SimpleCPSConfig#instance} immediately but
 * are not written to disk until DONE — {@code save()} does file I/O and mirrors to
 * the current server's config, so it cannot run per keystroke. CANCEL restores the
 * snapshot taken when the screen opened. ESC behaves like DONE, matching every
 * other Minecraft screen; leaving unsaved edits in memory would be worse, because
 * the next unrelated save would silently commit them.
 */
public class HudSettingsScreen extends Screen implements SettingsSchema.Host {

    private static final Gson GSON = new GsonBuilder().create();

    /** Below this panel width the right column becomes an overlay drawer. */
    private static final int RIGHT_BREAKPOINT = 380;

    private static final long FLASH_MILLIS = 1400;
    /** Fraction of the flash spent ramping up; the rest eases back out. */
    private static final float FLASH_ATTACK = 0.22f;

    /** How long after the last edit the autosave fires. */
    private static final long AUTOSAVE_DELAY_MILLIS = 350;

    /** How long "COPIED!" stays beside a hex field. */
    private static final long HEX_TOAST_MILLIS = 1200;

    // --- Metrics -----------------------------------------------------------
    //
    // Every size is the design mock's own value, scaled to the panel we actually
    // got, and floored at whatever still fits Minecraft's fixed 9px font. Fixed
    // constants were the original mistake: they were chosen for a ~480px GUI
    // space, so at the far more common guiScale 2 (960px) every control came out
    // at roughly half its intended share of the width and the rows looked starved.

    /** Width of the design mock these proportions come from. */
    private static final int DESIGN_W = 1600;
    private static final int DESIGN_H = 900;

    /**
     * Upper bound on the design-to-panel scale, set by the font: the mock's body
     * text is 11px, Minecraft's is 9px, so past 9/11 the chrome would keep growing
     * around text that cannot. Beyond that point the UI just looks empty.
     */
    private static final float MAX_UI_SCALE = 9f / 11f;

    /**
     * Layout width the screen aims for, in virtual units, at 100% menu scale.
     *
     * <p>Everything is laid out in this space and then scaled onto the real
     * screen, which is what makes the menu the same physical size at GUI Scale 1
     * and at GUI Scale 4. Without it the chrome shrinks with the GUI space while
     * Minecraft's 9px font cannot, so at high GUI Scale the text ends up twice
     * the size the design calls for and the whole thing reads as clunky.
     */
    private static final int TARGET_VIRTUAL_W = 960;
    private static final int TARGET_VIRTUAL_H = 540;

    /** Virtual-to-screen factor, and the virtual viewport it produces. */
    private float renderScale = 1f;
    private int vw, vh;

    private float uiScale;

    private int headerH, footerH, contentHeadH, tabH, moduleRowH, rowH;
    private int pad, gap, resetW, ctrlH;
    private int toggleW, knobW, knobH;
    private int sliderW, sliderH, sliderValueW, sliderHandleW;
    private int stepBtn, stepFieldW;
    private int choiceBtn, choiceValueW;
    private int textW, colorW;
    private int posCellW, posCellH, posGap, posGridW, posGridH, posBlockH;
    private int colorPanelH, colorBlockH, swatch;
    private int scrollbarW;
    private int sideHeadH, sideFootH;
    private int cardHeadH, presetRowH;
    private int badgeH, indicatorSize;

    /** Scale a length from the design mock, never going below {@code min}. */
    private int d(int designPx, int min) {
        return Math.max(min, Math.round(designPx * uiScale));
    }

    // --- Layout (recomputed every frame) -----------------------------------

    private int panelX, panelY, panelW, panelH;
    private int innerX, innerW;
    private int bodyY, bodyH, footerY;
    private int sidebarX, sidebarW;
    private int contentX, contentW;
    private int rightX, rightW;
    private boolean rightCollapsed;

    // --- State -------------------------------------------------------------

    private final Screen parent;

    private List<Page> pages = List.of();
    private List<Page> builtInPages = List.of();
    private List<Page> pluginPages = List.of();
    private String selectedPageId = "cps";
    private final Map<String, String> selectedTab = new HashMap<>();

    private int sidebarScroll;
    private int contentScroll;
    private int rightScroll;
    /** Scroll inside the preset list, which has its own viewport in the right panel. */
    private int presetScroll;

    /** Rows currently laid out in the content column, in draw order. */
    private final List<PlacedRow> placedRows = new ArrayList<>();
    private int rowsContentH;

    /** Last row the cursor was over; drives the INFO panel and stays put on exit. */
    private Row infoRow;

    /**
     * Module the PREVIEW card draws, resolved when the page changes rather than every
     * frame. Null on GENERAL and on any page whose module is gone, which hides the card.
     */
    private HudModule previewModule;

    /** Whether the preview is blown up over the rest of the screen. */
    private boolean previewFocused;

    private String openColorRowId;

    /**
     * HSV of the open color picker. Held here rather than derived from the row
     * every frame because hue and saturation are undefined at black and at white
     * — reading them back would make the cursor jump the moment you drag into a
     * corner of the square.
     */
    private float pickerHue, pickerSat, pickerVal;

    /** Which hex field was last copied, and when, for the confirmation flash. */
    private String hexCopiedRowId;
    private long hexCopiedAt;

    private enum Focus { NONE, SEARCH, SHARE, ROW_TEXT, ROW_NUMBER, ROW_HEX }
    private Focus focus = Focus.NONE;
    private Row focusedRow;

    private final TextInput searchInput = new TextInput();
    private final TextInput rowInput = new TextInput();
    private final TextInput shareInput = new TextInput();

    /** A control claiming the cursor between press and release (sliders, the color field). */
    @FunctionalInterface
    private interface DragHandler {
        void apply(double mouseX, double mouseY);
    }

    private DragHandler dragHandler;

    /** Per-page toggle flash: what it flipped to, and when. */
    private final Map<String, Boolean> flashState = new HashMap<>();
    private final Map<String, Long> flashStart = new HashMap<>();

    private List<String> presetNames = List.of();
    private String shareMessage = "PASTE A CODE TO IMPORT SOMEONE ELSE'S HUD LAYOUT.";
    private boolean shareOk;

    /** Which confirmation is on screen, if any. */
    private enum Dialog { NONE, RESET_ALL, DISCARD }

    private Dialog dialog = Dialog.NONE;
    private final Anim dialogAnim = new Anim(0f);

    private boolean drawerOpen;

    private JsonElement openSnapshot;
    /** Whether anything has changed since the screen opened — what CANCEL undoes. */
    private boolean changedSinceOpen;
    /** When the queued autosave should fire, or 0 when nothing is pending. */
    private long pendingSaveAt;

    private record PlacedRow(Row row, String tag, int y, int h) {}

    public HudSettingsScreen(Screen parent) {
        this(parent, null);
    }

    /**
     * Open straight onto a module's page — what the editor's "Open Settings" entry
     * uses. An unknown id falls back to the first page via {@link #init()}.
     */
    public HudSettingsScreen(Screen parent, String initialPageId) {
        super(Component.literal("Eymistaken's HUD Settings"));
        this.parent = parent;
        if (initialPageId != null && !initialPageId.isEmpty()) {
            this.selectedPageId = initialPageId;
            this.jumpToSettingsTab = true;
        }
    }

    /** Set when opened at a specific page: prefer that page's settings tab. */
    private boolean jumpToSettingsTab;

    // --- Setup -------------------------------------------------------------

    @Override
    protected void init() {
        builtInPages = SettingsSchema.builtInPages(this);
        pluginPages = SettingsSchema.pluginPages();
        List<Page> all = new ArrayList<>(builtInPages);
        all.addAll(pluginPages);
        pages = List.copyOf(all);

        if (page(selectedPageId) == null) {
            selectedPageId = pages.isEmpty() ? "" : pages.get(0).id;
        }

        // Plugin pages put their own settings on a "settings" tab; opening from the
        // editor should land on those rather than on placement.
        if (jumpToSettingsTab) {
            jumpToSettingsTab = false;
            Page opened = page(selectedPageId);
            if (opened != null) {
                String target = null;
                for (Tab tab : opened.tabs) {
                    if (tab.id().equals("settings")) {
                        target = tab.id();
                        break;
                    }
                    // A plugin that declares its own tabs may have no "settings" tab at
                    // all; its first non-placement tab is the equivalent landing spot.
                    if (target == null && opened.plugin && !tab.id().equals("pos")) {
                        target = tab.id();
                    }
                }
                if (target != null) selectedTab.put(opened.id, target);
            }
        }

        searchInput.setMaxLength(48);
        shareInput.setMaxLength(4096);

        resolvePreviewModule();
        presetNames = ConfigPresetManager.listPresets();
        takeSnapshot();
        layout();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        layout();
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
    private void computeRenderScale() {
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

    private void layout() {
        computeRenderScale();

        int margin = vw >= 400 ? 3 : 0;
        panelX = margin;
        panelY = margin;
        panelW = vw - margin * 2;
        panelH = vh - margin * 2;

        computeMetrics();

        innerX = panelX + 1;
        innerW = panelW - 2;

        bodyY = panelY + 1 + headerH;
        footerY = panelY + panelH - 1 - footerH;
        bodyH = Math.max(0, footerY - bodyY);

        rightCollapsed = panelW < RIGHT_BREAKPOINT;
        if (!rightCollapsed) drawerOpen = false;

        sidebarX = innerX;
        rightX = innerX + innerW - rightW;

        int contentRight = rightCollapsed ? innerX + innerW : rightX - 1;
        contentX = sidebarX + sidebarW + 1;
        contentW = Math.max(40, contentRight - contentX);

        refreshPreview();
    }

    /** Derive every control size from the panel we were given. */
    private void computeMetrics() {
        // Take the tighter of the two axes so the design fits however the window
        // is shaped, then cap it where the font stops keeping up.
        uiScale = Math.min(MAX_UI_SCALE,
            Math.min(panelW / (float) DESIGN_W, panelH / (float) DESIGN_H));

        headerH       = d(58, 18);
        footerH       = d(50, 16);
        contentHeadH  = d(62, 24);
        tabH          = d(40, 12);
        moduleRowH    = d(42, 13);
        rowH          = d(46, 16);

        sidebarW      = d(296, 70);
        rightW        = d(330, 80);

        pad           = d(14, 3);
        gap           = d(8, 3);
        ctrlH         = d(26, 11);
        resetW        = d(26, 11);

        knobW         = d(30, 15);
        knobH         = d(18, 7);
        // Guarantee the ON/OFF label still fits beside the knob, whatever the scale.
        toggleW       = Math.max(d(68, 34),
            knobW + SettingsTheme.textWidth(this.font, "OFF") + gap * 2);

        sliderW       = d(240, 60);
        sliderH       = d(22, 9);
        sliderValueW  = d(54, 22);
        sliderHandleW = d(12, 3);

        stepBtn       = d(26, 11);
        stepFieldW    = d(84, 34);

        choiceBtn     = d(26, 9);
        choiceValueW  = d(190, 56);

        textW         = d(246, 60);
        colorW        = d(150, 62);

        posCellW      = d(66, 19);
        posCellH      = d(36, 11);
        posGap        = d(4, 2);
        posGridW      = posCellW * 3 + posGap * 2 + 4;
        posGridH      = posCellH * 3 + posGap * 2 + 4;
        posBlockH     = posGridH + gap;

        // Square + hue bar + hex row, each with a gap above it.
        colorPanelH   = gap + svBoxH() + gap + hueBarH() + gap + ctrlH + gap;
        colorBlockH   = colorPanelH + gap;
        swatch        = d(24, 8);

        scrollbarW    = d(10, 3);
        sideHeadH     = d(41, 12);
        sideFootH     = d(60, 16);
        cardHeadH     = d(30, 11);
        presetRowH    = d(30, 11);

        badgeH        = d(24, 11);
        indicatorSize = d(20, 9);
    }

    /** Vertically center one line of text in a box of height {@code h}. */
    private int centerTextY(int y, int h) {
        return y + (h - this.font.lineHeight) / 2 + 1;
    }

    private Page page(String id) {
        for (Page p : pages) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    private Page activePage() {
        Page p = page(selectedPageId);
        return p != null ? p : (pages.isEmpty() ? null : pages.get(0));
    }

    private Tab activeTab(Page page) {
        if (page == null || page.tabs.isEmpty()) return null;
        String id = selectedTab.get(page.id);
        for (Tab t : page.tabs) {
            if (t.id().equals(id)) return t;
        }
        return page.tabs.get(0);
    }

    private boolean searching() {
        return !searchInput.getValue().trim().isEmpty();
    }

    // --- Save / revert -----------------------------------------------------

    private void takeSnapshot() {
        SimpleCPSConfig.collectModuleState();
        openSnapshot = GSON.toJsonTree(SimpleCPSConfig.instance);
        changedSinceOpen = false;
        pendingSaveAt = 0;
    }

    /**
     * Record an edit and queue the autosave.
     *
     * <p>Edits land on the live config immediately, but {@code save()} does file
     * I/O and mirrors to the current server's config, so it cannot run per
     * keystroke or per frame of a slider drag. Coalescing into one write a short
     * moment after you stop is what makes "it just saves" affordable.
     */
    private void markDirty() {
        changedSinceOpen = true;
        pendingSaveAt = System.currentTimeMillis() + AUTOSAVE_DELAY_MILLIS;
    }

    /** Write now if an autosave is queued. */
    private void flushPendingSave() {
        if (pendingSaveAt == 0) return;
        pendingSaveAt = 0;
        SimpleCPSConfig.save();
    }

    private void tickAutoSave() {
        if (pendingSaveAt != 0 && System.currentTimeMillis() >= pendingSaveAt) {
            flushPendingSave();
        }
    }

    /** Persist and close. The DONE, ESC and X path. */
    private void saveAndClose() {
        commitFocus();
        flushPendingSave();
        close();
    }

    /** True when CANCEL would actually undo something. */
    private boolean hasChangesToDiscard() {
        return changedSinceOpen && openSnapshot != null;
    }

    /**
     * Put the config back exactly as it was when the screen opened, undoing the
     * autosaves along the way, then close.
     */
    private void discardAndClose() {
        commitFocus();
        pendingSaveAt = 0;
        if (openSnapshot != null) {
            try {
                SimpleCPSConfig restored = GSON.fromJson(openSnapshot, SimpleCPSConfig.class);
                if (restored != null) {
                    // Validate while detached, exactly like a preset or share import,
                    // so a bad round trip cannot leave the live config half-replaced.
                    restored.normalize();
                    SimpleCPSConfig.instance = restored;
                    SimpleCPSConfig.distributeModuleState();
                    SimpleCPSConfig.save();
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
        changedSinceOpen = false;
        close();
    }

    private void close() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    /**
     * The config was swapped out from under us by a preset, a share code or a
     * reset. Re-baseline so CANCEL does not undo something the user asked for.
     */
    private void onConfigReplaced() {
        dialog = Dialog.NONE;
        openColorRowId = null;
        clearFocusState();
        presetNames = ConfigPresetManager.listPresets();
        takeSnapshot();
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    /**
     * Safety net for the paths that tear the screen down without going through
     * {@link #saveAndClose()} — leaving the world, for instance. The queued edit
     * is already on the live config, so it must reach disk.
     */
    @Override
    public void removed() {
        flushPendingSave();
        super.removed();
    }

    // --- SettingsSchema.Host ------------------------------------------------

    @Override
    public void openHudEditor() {
        openSubScreen(new HudEditorScreen(this));
    }

    @Override
    public void openKeystrokesDesigner() {
        openSubScreen(new KeystrokesDesignerScreen(this));
    }

    @Override
    public void resetHudLayout() {
        HudActions.resetHudLayout();
        onConfigReplaced();
    }

    /**
     * Hand off to the editor or designer. Flush first: those screens write to the
     * same config, and returning here re-runs {@link #init()} and re-snapshots, so
     * anything unsaved would be silently dropped.
     */
    private void openSubScreen(Screen screen) {
        commitFocus();
        flushPendingSave();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(screen);
        }
    }

    // --- Rendering ----------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        layout();
        tickAutoSave();

        int vmx = virtualX(mouseX);
        int vmy = virtualY(mouseY);

        ctx.pose().pushMatrix();
        ctx.pose().scale(renderScale, renderScale);

        // Cover everything first, margin included. Leaving the game visible in
        // the gap around the panel put a bright strip right against the chrome.
        SettingsTheme.rect(ctx, 0, 0, vw, vh, SettingsTheme.BACKDROP);
        SettingsTheme.raised(ctx, panelX, panelY, panelW, panelH,
            SettingsTheme.PANEL_BG, SettingsTheme.PANEL_LIGHT, SettingsTheme.PANEL_DARK);

        boolean blocked = dialog != Dialog.NONE;
        int mx = blocked ? -1 : vmx;
        int my = blocked ? -1 : vmy;

        renderHeader(ctx, mx, my);
        renderSidebar(ctx, mx, my);
        renderContent(ctx, mx, my);
        if (!rightCollapsed || drawerOpen) {
            renderRightPanel(ctx, mx, my, delta);
        }
        // Outside the right panel's scissor, so the focus overlay can cover the settings
        // rows. The normal card is still drawn underneath it: nothing below reflows when
        // focus opens, which is what stops the layout jumping when it closes again.
        if (previewFocused) {
            renderPreviewFocus(ctx, mx, my, delta);
        }
        renderFooter(ctx, mx, my);

        float modalProgress = dialogAnim.update(dialog != Dialog.NONE ? 1f : 0f, 0.14f, Easings::sineInOut);
        if (modalProgress > 0.01f) {
            renderDialog(ctx, vmx, vmy, modalProgress);
        }

        ctx.pose().popMatrix();

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    /** Map a screen coordinate into the virtual space everything is laid out in. */
    private int virtualX(double screenX) {
        return (int) Math.floor(screenX / renderScale);
    }

    private int virtualY(double screenY) {
        return (int) Math.floor(screenY / renderScale);
    }

    // --- Header -------------------------------------------------------------

    /** Vertical position of every control in the header bar. */
    private int headerControlY() {
        return panelY + 1 + (headerH - ctrlH) / 2;
    }

    private int headerCloseW() {
        return d(32, 12);
    }

    private int headerSearchX() {
        int right = innerX + innerW - pad;
        int drawerW = rightCollapsed ? headerCloseW() + gap : 0;
        return right - headerCloseW() - gap - drawerW - headerSearchW();
    }

    private int headerSearchW() {
        // Whatever is left once the title block and the buttons have taken theirs.
        int taken = pad * 2 + headerCloseW() + gap
            + (rightCollapsed ? headerCloseW() + gap : 0)
            + SettingsTheme.textWidth(this.font, TITLE) + gap * 2;
        return Math.max(d(60, 40), Math.min(d(300, 110), innerW - taken));
    }

    /** Where the search field's text starts, past the magnifier glyph. */
    private int headerSearchTextX() {
        return headerSearchX() + gap + SEARCH_ICON_W + gap;
    }

    private static final int SEARCH_ICON_W = 5;

    private int headerCloseX() {
        return innerX + innerW - pad - headerCloseW();
    }

    private int headerDrawerX() {
        return headerCloseX() - gap - headerCloseW();
    }

    private static final String TITLE = "EYMISTAKEN'S HUD";

    private void renderHeader(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int y = panelY + 1;
        SettingsTheme.rect(ctx, innerX, y, innerW, headerH, SettingsTheme.BAR_BG);
        SettingsTheme.rect(ctx, innerX, y, innerW, 1, SettingsTheme.BAR_LIGHT);
        SettingsTheme.rect(ctx, innerX, y + headerH - 1, innerW, 1, SettingsTheme.BORDER);

        int cy = headerControlY();
        int textY = centerTextY(y, headerH);
        int x = innerX + pad;

        SettingsTheme.text(ctx, this.font, TITLE, x, textY, SettingsTheme.TEXT);
        x += SettingsTheme.textWidth(this.font, TITLE) + gap + 2;

        int badgeW = SettingsTheme.textWidth(this.font, "SETTINGS") + gap * 2;
        if (x + badgeW < headerSearchX() - gap) {
            int badgeY = y + (headerH - badgeH) / 2;
            SettingsTheme.sunken(ctx, x, badgeY, badgeW, badgeH, 0xFF16161A, 0xFF3A3A42);
            SettingsTheme.centeredText(ctx, this.font, "SETTINGS", x + badgeW / 2,
                centerTextY(badgeY, badgeH), SettingsTheme.TEXT_MUTED);
        }

        // Search
        int sx = headerSearchX();
        int sw = headerSearchW();
        SettingsTheme.field(ctx, sx, cy, sw, ctrlH);
        SettingsTheme.searchIcon(ctx, sx + gap, cy + (ctrlH - SEARCH_ICON_W) / 2,
            focus == Focus.SEARCH ? SettingsTheme.TEXT : SettingsTheme.TEXT_FAINT);
        int searchTextX = headerSearchTextX();
        int searchTextW = sw - (searchTextX - sx) - gap;
        if (searchInput.getValue().isEmpty() && focus != Focus.SEARCH) {
            SettingsTheme.text(ctx, this.font, "SEARCH SETTINGS", searchTextX,
                centerTextY(cy, ctrlH), SettingsTheme.TEXT_PLACEHOLD);
        } else {
            searchInput.render(ctx, this.font, searchTextX, cy, searchTextW, ctrlH,
                focus == Focus.SEARCH, SettingsTheme.TEXT);
        }

        if (rightCollapsed) {
            int dx = headerDrawerX();
            int dw = headerCloseW();
            boolean hov = SettingsTheme.inside(mouseX, mouseY, dx, cy, dw, ctrlH);
            SettingsTheme.button(ctx, dx, cy, dw, ctrlH, hov || drawerOpen);
            int barW = Math.max(5, dw - gap * 2);
            for (int i = 0; i < 3; i++) {
                SettingsTheme.rect(ctx, dx + (dw - barW) / 2, cy + ctrlH / 2 - 3 + i * 3, barW, 1,
                    SettingsTheme.TEXT);
            }
        }

        int cx = headerCloseX();
        int cw = headerCloseW();
        boolean closeHovered = SettingsTheme.inside(mouseX, mouseY, cx, cy, cw, ctrlH);
        SettingsTheme.button(ctx, cx, cy, cw, ctrlH, closeHovered);
        SettingsTheme.closeIcon(ctx, cx + (cw - 5) / 2, cy + (ctrlH - 5) / 2, 0xFFCFCFD4);
    }

    // --- Sidebar -------------------------------------------------------------


    private int sidebarListY() {
        return bodyY + sideHeadH;
    }

    private int sidebarListH() {
        return Math.max(0, bodyH - sideHeadH - sideFootH);
    }

    private void renderSidebar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        SettingsTheme.rect(ctx, sidebarX, bodyY, sidebarW, bodyH, SettingsTheme.SIDE_BG);
        SettingsTheme.rect(ctx, sidebarX + sidebarW, bodyY, 1, bodyH, SettingsTheme.BORDER);

        // Header strip
        SettingsTheme.rect(ctx, sidebarX, bodyY, sidebarW, sideHeadH, SettingsTheme.SIDE_HEAD_BG);
        SettingsTheme.rect(ctx, sidebarX, bodyY + sideHeadH - 1, sidebarW, 1, SettingsTheme.BORDER);
        int headTextY = centerTextY(bodyY, sideHeadH);
        SettingsTheme.text(ctx, this.font, "MODULES", sidebarX + pad, headTextY, SettingsTheme.TEXT_MUTED);
        SettingsTheme.rightText(ctx, this.font, moduleCount(), sidebarX + sidebarW - pad, headTextY,
            SettingsTheme.TEXT_FAINT);

        int listY = sidebarListY();
        int listH = sidebarListH();
        int contentH = sidebarContentHeight();
        sidebarScroll = clampScroll(sidebarScroll, contentH, listH);

        ctx.enableScissor(sidebarX, listY, sidebarX + sidebarW, listY + listH);
        ctx.pose().pushMatrix();
        ctx.pose().translate(0f, -sidebarScroll);

        int y = listY + gap;
        int rowW = sidebarW - gap * 2;
        int virtualMouseY = mouseY + sidebarScroll;
        boolean mouseInList = mouseY >= listY && mouseY < listY + listH && mouseX >= sidebarX && mouseX < sidebarX + sidebarW;

        for (Page p : builtInPages) {
            renderModuleRow(ctx, p, sidebarX + gap, y, rowW, mouseInList ? mouseX : -1, mouseInList ? virtualMouseY : -1);
            y += moduleRowH + 1;
        }

        // PLUGINS divider
        y += gap * 2;
        SettingsTheme.text(ctx, this.font, "PLUGINS", sidebarX + gap, y, SettingsTheme.TEXT_FAINT);
        int dividerX = sidebarX + gap + SettingsTheme.textWidth(this.font, "PLUGINS") + gap;
        SettingsTheme.rect(ctx, dividerX, y + 4, Math.max(0, sidebarX + sidebarW - gap - dividerX), 1, SettingsTheme.DIVIDER);
        y += this.font.lineHeight + gap;

        if (pluginPages.isEmpty()) {
            List<String> lines = SettingsTheme.wrap(this.font, NO_PLUGINS, rowW - gap * 2);
            int boxH = lines.size() * (this.font.lineHeight + 1) + gap * 2;
            SettingsTheme.frame(ctx, sidebarX + gap, y, rowW, boxH, SettingsTheme.DIVIDER);
            int ly = y + gap;
            for (String line : lines) {
                SettingsTheme.text(ctx, this.font, line, sidebarX + gap * 2, ly, SettingsTheme.TEXT_GHOST);
                ly += this.font.lineHeight + 1;
            }
        } else {
            for (Page p : pluginPages) {
                renderModuleRow(ctx, p, sidebarX + gap, y, rowW, mouseInList ? mouseX : -1, mouseInList ? virtualMouseY : -1);
                y += moduleRowH + 1;
            }
        }

        ctx.pose().popMatrix();
        ctx.disableScissor();
        renderScrollbar(ctx, sidebarX + sidebarW - scrollbarW - 1, listY, listH, sidebarScroll, contentH);

        // Footer button
        int fy = bodyY + bodyH - sideFootH;
        SettingsTheme.rect(ctx, sidebarX, fy, sidebarW, sideFootH, SettingsTheme.SIDE_HEAD_BG);
        SettingsTheme.rect(ctx, sidebarX, fy, sidebarW, 1, SettingsTheme.BORDER);
        int bx = sidebarX + gap;
        int bw = sidebarW - gap * 2;
        int bh = Math.min(sideFootH - gap * 2, d(40, 11));
        int by = fy + (sideFootH - bh) / 2;
        boolean hov = SettingsTheme.inside(mouseX, mouseY, bx, by, bw, bh);
        SettingsTheme.raised(ctx, bx, by, bw, bh,
            hov ? SettingsTheme.BTN_ACCENT_HI : SettingsTheme.BTN_ACCENT,
            SettingsTheme.BTN_ACCENT_L, SettingsTheme.BTN_ACCENT_D);
        SettingsTheme.centeredText(ctx, this.font,
            SettingsTheme.truncate(this.font, "OPEN HUD EDITOR", bw - gap * 2),
            bx + bw / 2, centerTextY(by, bh), SettingsTheme.TEXT);
    }

    private static final String NO_PLUGINS =
        "NO PLUGIN MODULES LOADED. MODULES REGISTERED THROUGH EymistakenHudPlugin APPEAR HERE.";

    private int sidebarContentHeight() {
        int h = gap + builtInPages.size() * (moduleRowH + 1);
        h += gap * 2 + this.font.lineHeight + gap;
        if (pluginPages.isEmpty()) {
            h += SettingsTheme.wrap(this.font, NO_PLUGINS, sidebarW - gap * 4).size()
                * (this.font.lineHeight + 1) + gap * 2;
        } else {
            h += pluginPages.size() * (moduleRowH + 1);
        }
        return h + gap;
    }

    /** "enabled / total", counting real modules only — GENERAL is always on. */
    private String moduleCount() {
        int total = 0;
        int on = 0;
        for (Page p : pages) {
            if (p.global) continue;
            total++;
            if (p.isEnabled()) on++;
        }
        return on + " / " + total;
    }

    /** The sidebar button that toggles a module, as opposed to selecting it. */
    private int moduleIndicatorX() {
        return sidebarX + gap * 2;
    }

    private void renderModuleRow(GuiGraphicsExtractor ctx, Page p, int x, int y, int w, int mouseX, int mouseY) {
        boolean selected = p.id.equals(selectedPageId);
        boolean on = p.isEnabled();
        boolean hovered = SettingsTheme.inside(mouseX, mouseY, x, y, w, moduleRowH);

        // GENERAL is not a module you switch on and off, so it drops the indicator
        // and centers its label. It still sits in the list rather than standing
        // apart as a button — a thin outline is enough to mark it as different.
        if (p.global) {
            int bg = selected ? SettingsTheme.SEL_BG : (hovered ? SettingsTheme.IDLE_BG : 0);
            if (bg != 0) SettingsTheme.rect(ctx, x, y, w, moduleRowH, bg);
            SettingsTheme.frame(ctx, x, y, w, moduleRowH,
                selected ? SettingsTheme.SEL_BORDER : SettingsTheme.DIVIDER);
            SettingsTheme.centeredText(ctx, this.font,
                SettingsTheme.truncate(this.font, p.name, w - gap * 2),
                x + w / 2, centerTextY(y, moduleRowH),
                selected ? SettingsTheme.TEXT_STRONG : SettingsTheme.NAME_ON);
            return;
        }

        float flash = flashIntensity(p.id);
        Boolean flashOn = flashState.get(p.id);

        int bg = selected ? SettingsTheme.SEL_BG : (hovered ? SettingsTheme.IDLE_BG : 0);
        if (flash > 0f) {
            int target = Boolean.TRUE.equals(flashOn) ? SettingsTheme.FLASH_ON_BG : SettingsTheme.FLASH_OFF_BG;
            bg = bg == 0 ? SettingsTheme.lerpColor(target & 0x00FFFFFF, target, flash)
                         : SettingsTheme.lerpColor(bg, target, flash);
        }
        if (bg != 0) SettingsTheme.rect(ctx, x, y, w, moduleRowH, bg);
        if (selected) SettingsTheme.frame(ctx, x, y, w, moduleRowH, SettingsTheme.SEL_BORDER);

        // Enable indicator
        int boxY = y + (moduleRowH - indicatorSize) / 2;
        int boxX = x + gap;
        SettingsTheme.sunken(ctx, boxX, boxY, indicatorSize, indicatorSize, SettingsTheme.FIELD_BG, 0xFF303038);
        int dot = on ? SettingsTheme.DOT_ON : SettingsTheme.DOT_OFF;
        if (flash > 0f && Boolean.TRUE.equals(flashOn)) {
            dot = SettingsTheme.lerpColor(dot, SettingsTheme.FLASH_ON_DOT, flash);
        }
        int dotSize = Math.max(3, indicatorSize - gap * 2);
        SettingsTheme.rect(ctx, boxX + (indicatorSize - dotSize) / 2, boxY + (indicatorSize - dotSize) / 2,
            dotSize, dotSize, dot);

        int nameColor = on ? (selected ? SettingsTheme.TEXT_STRONG : SettingsTheme.NAME_ON)
                           : SettingsTheme.NAME_OFF;
        if (flash > 0f && Boolean.TRUE.equals(flashOn)) {
            nameColor = SettingsTheme.lerpColor(nameColor, SettingsTheme.FLASH_ON_TEXT, flash);
        }

        String state = on ? "ON" : "OFF";
        int stateW = SettingsTheme.textWidth(this.font, state) + gap;
        int nameX = boxX + indicatorSize + gap;
        int nameRoom = x + w - gap - stateW - nameX;
        int textY = centerTextY(y, moduleRowH);
        SettingsTheme.text(ctx, this.font, SettingsTheme.truncate(this.font, p.name, nameRoom), nameX, textY, nameColor);

        int stateColor = on ? SettingsTheme.TEXT_DIM : SettingsTheme.KNOB_OFF;
        if (flash > 0f && Boolean.TRUE.equals(flashOn)) {
            stateColor = SettingsTheme.lerpColor(stateColor, SettingsTheme.FLASH_ON_DOT, flash);
        }
        SettingsTheme.rightText(ctx, this.font, state, x + w - gap, textY, stateColor);
    }

    /**
     * Highlight strength for a page that was just toggled, in {@code [0,1]}.
     *
     * <p>Ramps up over the first quarter of the window and eases back down over
     * the rest, so the row glows and settles instead of blinking on and popping
     * off the way a plain deadline did.
     */
    private float flashIntensity(String pageId) {
        Long start = flashStart.get(pageId);
        if (start == null) return 0f;

        float t = (System.currentTimeMillis() - start) / (float) FLASH_MILLIS;
        if (t >= 1f) {
            flashStart.remove(pageId);
            flashState.remove(pageId);
            return 0f;
        }
        if (t < 0f) return 0f;

        float envelope = t < FLASH_ATTACK
            ? t / FLASH_ATTACK
            : 1f - (t - FLASH_ATTACK) / (1f - FLASH_ATTACK);
        return Easings.sineInOut(Easings.clamp01(envelope));
    }

    // --- Content -------------------------------------------------------------

    private int contentListY() {
        return bodyY + contentHeadH + (searching() ? 0 : tabH);
    }

    private int contentListH() {
        return Math.max(0, bodyY + bodyH - contentListY());
    }

    private void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        Page page = activePage();
        boolean search = searching();

        // Header
        SettingsTheme.rect(ctx, contentX, bodyY, contentW, contentHeadH, SettingsTheme.CONTENT_HEAD);
        SettingsTheme.rect(ctx, contentX, bodyY + contentHeadH - 1, contentW, 1, SettingsTheme.BORDER);

        rebuildPlacedRows(page, search);

        String title = search ? "SEARCH RESULTS" : (page == null ? "" : page.name);
        String desc = search
            ? placedRows.size() + " SETTING(S) MATCHING YOUR SEARCH"
            : (page == null ? "" : SettingsTheme.up(page.desc));

        String badge = search ? "" : enableButtonLabel(page);
        int badgeColor = SettingsTheme.TEXT_FAINT;
        int badgeW = badge.isEmpty() ? 0 : enableButtonW(page);
        int titleRoom = contentW - pad * 2 - (badgeW > 0 ? badgeW + gap : 0);

        // Title and description share the header, stacked and vertically centered.
        int blockH = this.font.lineHeight * 2 + gap;
        int titleY = bodyY + (contentHeadH - blockH) / 2;
        SettingsTheme.text(ctx, this.font, SettingsTheme.truncate(this.font, title, titleRoom),
            contentX + pad, titleY, SettingsTheme.TEXT);
        SettingsTheme.text(ctx, this.font, SettingsTheme.truncate(this.font, desc, titleRoom),
            contentX + pad, titleY + this.font.lineHeight + gap, SettingsTheme.TEXT_DIM);

        if (badgeW > 0) {
            int bx = enableButtonX(page);
            int by = enableButtonY();
            if (page.canToggle()) {
                // A live switch, not a label: this is the fastest way to flip a
                // module while you are already looking at its settings.
                boolean hovered = SettingsTheme.inside(mouseX, mouseY, bx, by, badgeW, badgeH);
                boolean on = page.isEnabled();
                SettingsTheme.raised(ctx, bx, by, badgeW, badgeH,
                    on ? (hovered ? SettingsTheme.BTN_ACCENT_HI : SettingsTheme.BTN_PRIMARY)
                       : (hovered ? SettingsTheme.BTN_HOVER : SettingsTheme.BTN_BG),
                    SettingsTheme.BTN_ACCENT_L, SettingsTheme.BTN_ACCENT_D);
                int dotSize = Math.max(3, badgeH - gap * 2);
                SettingsTheme.rect(ctx, bx + gap, by + (badgeH - dotSize) / 2, dotSize, dotSize,
                    on ? SettingsTheme.DOT_ON : SettingsTheme.DOT_OFF);
                SettingsTheme.text(ctx, this.font, badge, bx + gap + dotSize + gap,
                    centerTextY(by, badgeH), on ? SettingsTheme.TEXT_STRONG : SettingsTheme.TEXT_FAINT);
            } else {
                SettingsTheme.sunken(ctx, bx, by, badgeW, badgeH, SettingsTheme.FIELD_BG, 0xFF2E2E35);
                SettingsTheme.centeredText(ctx, this.font, badge, bx + badgeW / 2,
                    centerTextY(by, badgeH), badgeColor);
            }
        }

        // Tabs
        if (!search && page != null) {
            renderTabs(ctx, page, mouseX, mouseY);
        }

        // Rows
        int listY = contentListY();
        int listH = contentListH();
        contentScroll = clampScroll(contentScroll, rowsContentH, listH);

        if (placedRows.isEmpty()) {
            String message = search
                ? "NO SETTINGS MATCH \"" + SettingsTheme.up(searchInput.getValue().trim()) + "\""
                : "NOTHING TO CONFIGURE HERE";
            SettingsTheme.centeredText(ctx, this.font,
                SettingsTheme.truncate(this.font, message, contentW - pad * 2),
                contentX + contentW / 2, listY + Math.max(10, listH / 2 - 4), SettingsTheme.TEXT_GHOST);
            return;
        }

        boolean mouseInList = mouseX >= contentX && mouseX < contentX + contentW
            && mouseY >= listY && mouseY < listY + listH;
        int virtualMouseY = mouseY + contentScroll;

        ctx.enableScissor(contentX, listY, contentX + contentW, listY + listH);
        ctx.pose().pushMatrix();
        ctx.pose().translate(0f, -contentScroll);

        for (PlacedRow placed : placedRows) {
            boolean hovered = mouseInList
                && virtualMouseY >= placed.y() && virtualMouseY < placed.y() + placed.h();
            if (hovered) infoRow = placed.row();
            renderRow(ctx, placed, hovered, mouseInList ? mouseX : -1, mouseInList ? virtualMouseY : -1);
        }

        ctx.pose().popMatrix();
        ctx.disableScissor();
        renderScrollbar(ctx, contentX + contentW - scrollbarW - 1, listY, listH, contentScroll, rowsContentH);
    }

    /** Label shown on the enable button at the top of the content column. */
    private String enableButtonLabel(Page page) {
        if (page == null) return "";
        if (page.global) return "GLOBAL";
        return page.isEnabled() ? "ENABLED" : "DISABLED";
    }

    private int enableButtonW(Page page) {
        String label = enableButtonLabel(page);
        if (label.isEmpty()) return 0;
        int w = SettingsTheme.textWidth(this.font, label) + gap * 2;
        if (page != null && page.canToggle()) {
            w += Math.max(3, badgeH - gap * 2) + gap;
        }
        // Steady width so flipping ENABLED/DISABLED does not make it jump.
        int widest = SettingsTheme.textWidth(this.font, "DISABLED") + gap * 2
            + (page != null && page.canToggle() ? Math.max(3, badgeH - gap * 2) + gap : 0);
        return Math.max(w, widest);
    }

    private int enableButtonX(Page page) {
        return contentX + contentW - pad - enableButtonW(page);
    }

    private int enableButtonY() {
        return bodyY + (contentHeadH - badgeH) / 2;
    }

    private void renderTabs(GuiGraphicsExtractor ctx, Page page, int mouseX, int mouseY) {
        int y = bodyY + contentHeadH;
        SettingsTheme.rect(ctx, contentX, y, contentW, tabH, SettingsTheme.PANEL_BG);
        SettingsTheme.rect(ctx, contentX, y + tabH - 1, contentW, 1, SettingsTheme.BORDER);

        Tab active = activeTab(page);
        int x = contentX + pad;
        for (Tab tab : page.tabs) {
            int w = tabWidth(tab);
            if (x + w > contentX + contentW - pad) break;
            boolean isActive = active != null && active.id().equals(tab.id());
            SettingsTheme.rect(ctx, x, y + 1, w, tabH - 1,
                isActive ? SettingsTheme.TAB_ACTIVE_BG : SettingsTheme.TAB_IDLE_BG);
            SettingsTheme.rect(ctx, x, y + 1, w, 1, SettingsTheme.BORDER);
            SettingsTheme.rect(ctx, x, y + 1, 1, tabH - 1, SettingsTheme.BORDER);
            SettingsTheme.rect(ctx, x + w - 1, y + 1, 1, tabH - 1, SettingsTheme.BORDER);
            SettingsTheme.rect(ctx, x + 1, y + 2, w - 2, 1,
                isActive ? SettingsTheme.TAB_ACTIVE_HI : SettingsTheme.TAB_IDLE_HI);
            if (isActive) {
                // Erase the strip's bottom border so the tab merges into the list.
                SettingsTheme.rect(ctx, x + 1, y + tabH - 1, w - 2, 1, SettingsTheme.TAB_ACTIVE_BG);
            }
            SettingsTheme.centeredText(ctx, this.font, tab.name(), x + w / 2, centerTextY(y + 1, tabH - 1),
                isActive ? SettingsTheme.TEXT_STRONG : SettingsTheme.TEXT_DIM);
            x += w + 1;
        }
    }

    private int tabWidth(Tab tab) {
        return SettingsTheme.textWidth(this.font, tab.name()) + gap * 3;
    }

    private void rebuildPlacedRows(Page page, boolean search) {
        placedRows.clear();
        int y = contentListY() + gap;

        if (search) {
            String query = searchInput.getValue().trim().toLowerCase(Locale.ROOT);
            for (Page p : pages) {
                boolean pageMatches = p.name.toLowerCase(Locale.ROOT).contains(query);
                for (Tab tab : p.tabs) {
                    for (Row row : tab.rows()) {
                        if (!pageMatches && !row.label.toLowerCase(Locale.ROOT).contains(query)) continue;
                        int h = rowHeight(row);
                        placedRows.add(new PlacedRow(row, p.name, y, h));
                        y += h;
                    }
                }
            }
        } else {
            Tab tab = activeTab(page);
            if (tab != null) {
                for (Row row : tab.rows()) {
                    int h = rowHeight(row);
                    placedRows.add(new PlacedRow(row, null, y, h));
                    y += h;
                }
            }
        }

        rowsContentH = y - contentListY() + gap * 2;
    }

    private int rowHeight(Row row) {
        int h = rowH;
        if (row instanceof PositionRow) h += posBlockH;
        if (row instanceof ColorRow && row.id.equals(openColorRowId)) h += colorBlockH;
        return h;
    }

    // --- Row rendering ---------------------------------------------------------

    private int rowX() {
        return contentX + pad;
    }

    private int rowW() {
        return contentW - pad * 2 - scrollbarW - 1;
    }

    private int resetX() {
        return rowX() + rowW() - resetW;
    }

    /** Right edge available to a row's control, just left of the reset button. */
    private int controlRight() {
        return resetX() - gap;
    }

    private void renderRow(GuiGraphicsExtractor ctx, PlacedRow placed, boolean hovered, int mouseX, int mouseY) {
        Row row = placed.row();
        int x = rowX();
        int w = rowW();
        int y = placed.y();

        if (hovered) {
            SettingsTheme.rect(ctx, x, y, w, placed.h(), 0x14FFFFFF);
        }
        SettingsTheme.rect(ctx, x, y + placed.h() - 1, w, 1, SettingsTheme.ROW_SEPARATOR);

        int textY = centerTextY(y, rowH);
        int labelX = x + gap;

        if (placed.tag() != null) {
            String tag = SettingsTheme.up(placed.tag());
            int tw = SettingsTheme.textWidth(this.font, tag) + gap;
            int th = this.font.lineHeight + 2;
            SettingsTheme.frame(ctx, labelX, y + (rowH - th) / 2, tw, th, SettingsTheme.TAG_BORDER);
            SettingsTheme.text(ctx, this.font, tag, labelX + gap / 2, textY, SettingsTheme.TAG_TEXT);
            labelX += tw + gap;
        }

        int controlW = controlWidth(row);
        int labelRoom = Math.max(10, controlRight() - controlW - gap - labelX);
        SettingsTheme.text(ctx, this.font,
            SettingsTheme.truncate(this.font, SettingsTheme.up(row.label), labelRoom),
            labelX, textY, SettingsTheme.TEXT_LABEL);

        int cx = controlRight() - controlW;
        int cy = y + (rowH - ctrlH) / 2;

        switch (row) {
            case BoolRow r -> renderToggle(ctx, r, cx, cy);
            case IntRow r -> {
                if (r.style == IntRow.Style.SLIDER) renderSlider(ctx, r, cx, cy);
                else renderStepper(ctx, r, cx, cy, mouseX, mouseY);
            }
            case ChoiceRow r -> renderChoice(ctx, r, cx, cy, mouseX, mouseY);
            case TextRow r -> renderTextField(ctx, r, cx, cy);
            case ColorRow r -> renderColorField(ctx, r, cx, cy);
            case ActionRow r -> renderAction(ctx, r, cx, cy, mouseX, mouseY);
            case PositionRow r -> { /* drawn in the expansion below */ }
        }

        // Reset button
        boolean modified = row.isModified();
        int rx = resetX();
        if (modified) {
            SettingsTheme.raised(ctx, rx, cy, resetW, ctrlH, SettingsTheme.BTN_BG,
                SettingsTheme.BTN_LIGHT, SettingsTheme.BTN_DARK);
        } else if (!(row instanceof ActionRow)) {
            SettingsTheme.frame(ctx, rx, cy, resetW, ctrlH, SettingsTheme.DIVIDER);
        }
        if (!(row instanceof ActionRow)) {
            SettingsTheme.resetIcon(ctx, rx + (resetW - 7) / 2, cy + (ctrlH - 7) / 2,
                modified ? SettingsTheme.RESET_FG_ON : SettingsTheme.RESET_FG_OFF);
        }

        // Expansions
        if (row instanceof PositionRow r) {
            renderPositionGrid(ctx, r, x + gap, y + rowH, mouseX, mouseY);
        } else if (row instanceof ColorRow r && r.id.equals(openColorRowId)) {
            renderColorEditor(ctx, r, x + gap, y + rowH, w - gap * 2);
        }
    }

    private int controlWidth(Row row) {
        return switch (row) {
            case BoolRow ignored -> toggleW;
            case IntRow r -> r.style == IntRow.Style.SLIDER
                ? sliderTrackWidth() + gap + sliderValueW
                : stepBtn * 2 + gap * 2 + stepFieldWidth();
            case ChoiceRow ignored -> choiceBtn * 2 + gap * 2 + choiceValueWidth();
            case TextRow ignored -> textFieldWidth();
            case ColorRow ignored -> colorW;
            case ActionRow r -> actionWidth(r);
            case PositionRow ignored -> 0;
        };
    }

    /**
     * Width a control may take. Controls shrink before the label does, so a narrow
     * window degrades gracefully instead of pushing the label out of the row.
     */
    private int available() {
        return Math.max(30, controlRight() - rowX() - d(160, 40));
    }

    private int sliderTrackWidth() {
        return Math.max(30, Math.min(sliderW, available() - sliderValueW - gap));
    }

    private int stepFieldWidth() {
        return Math.max(20, Math.min(stepFieldW, available() - stepBtn * 2 - gap * 2));
    }

    private int choiceValueWidth() {
        return Math.max(30, Math.min(choiceValueW, available() - choiceBtn * 2 - gap * 2));
    }

    private int textFieldWidth() {
        return Math.max(30, Math.min(textW, available()));
    }

    private int actionWidth(ActionRow row) {
        return Math.max(d(110, 40),
            Math.min(available(), SettingsTheme.textWidth(this.font, row.button) + pad * 2));
    }

    private void renderToggle(GuiGraphicsExtractor ctx, BoolRow row, int x, int y) {
        boolean on = row.get();
        SettingsTheme.field(ctx, x, y, toggleW, ctrlH);

        int knobY = y + (ctrlH - knobH) / 2;
        int knobX = on ? x + toggleW - knobW - 2 : x + 2;
        SettingsTheme.rect(ctx, knobX, knobY, knobW, knobH, on ? SettingsTheme.KNOB_ON : SettingsTheme.KNOB_OFF);
        SettingsTheme.frame(ctx, knobX, knobY, knobW, knobH, SettingsTheme.BORDER);

        // The label sits in whichever half the knob is not occupying.
        String text = on ? "ON" : "OFF";
        int labelCenter = on ? x + 2 + (toggleW - knobW - 2) / 2 : x + toggleW - 2 - (toggleW - knobW - 2) / 2;
        SettingsTheme.centeredText(ctx, this.font, text, labelCenter, centerTextY(y, ctrlH),
            on ? SettingsTheme.KNOB_LABEL_ON : SettingsTheme.TEXT_FAINT);
    }

    private void renderSlider(GuiGraphicsExtractor ctx, IntRow row, int x, int y) {
        int trackW = sliderTrackWidth();
        int ty = y + (ctrlH - sliderH) / 2;
        SettingsTheme.field(ctx, x, ty, trackW, sliderH);
        int fill = Math.round(row.fraction() * (trackW - 2));
        SettingsTheme.rect(ctx, x + 1, ty + 1, fill, sliderH - 2, SettingsTheme.SLIDER_FILL);

        int hw = sliderHandleW;
        int handleX = x + 1 + Math.max(0, Math.min(trackW - 2 - hw, fill - hw / 2));
        SettingsTheme.rect(ctx, handleX, ty - 1, hw, sliderH + 2, SettingsTheme.SLIDER_KNOB);
        SettingsTheme.frame(ctx, handleX, ty - 1, hw, sliderH + 2, SettingsTheme.BORDER);

        SettingsTheme.rightText(ctx, this.font, String.valueOf(row.get()),
            x + trackW + gap + sliderValueW, centerTextY(y, ctrlH), SettingsTheme.TEXT_STRONG);
    }

    private void renderStepper(GuiGraphicsExtractor ctx, IntRow row, int x, int y, int mouseX, int mouseY) {
        int fieldW = stepFieldWidth();
        int signW = Math.max(5, stepBtn / 2);

        boolean minusHover = SettingsTheme.inside(mouseX, mouseY, x, y, stepBtn, ctrlH);
        SettingsTheme.button(ctx, x, y, stepBtn, ctrlH, minusHover);
        SettingsTheme.rect(ctx, x + (stepBtn - signW) / 2, y + ctrlH / 2, signW, 1, SettingsTheme.TEXT);

        int fx = x + stepBtn + gap;
        SettingsTheme.field(ctx, fx, y, fieldW, ctrlH);
        boolean editing = focus == Focus.ROW_NUMBER && focusedRow == row;
        if (editing) {
            rowInput.render(ctx, this.font, fx + gap, y, fieldW - gap * 2, ctrlH, true, SettingsTheme.TEXT_STRONG);
        } else {
            SettingsTheme.centeredText(ctx, this.font, String.valueOf(row.get()),
                fx + fieldW / 2, centerTextY(y, ctrlH), SettingsTheme.TEXT_STRONG);
        }

        int px = fx + fieldW + gap;
        boolean plusHover = SettingsTheme.inside(mouseX, mouseY, px, y, stepBtn, ctrlH);
        SettingsTheme.button(ctx, px, y, stepBtn, ctrlH, plusHover);
        SettingsTheme.rect(ctx, px + (stepBtn - signW) / 2, y + ctrlH / 2, signW, 1, SettingsTheme.TEXT);
        SettingsTheme.rect(ctx, px + stepBtn / 2, y + ctrlH / 2 - signW / 2, 1, signW, SettingsTheme.TEXT);
    }

    private void renderChoice(GuiGraphicsExtractor ctx, ChoiceRow row, int x, int y, int mouseX, int mouseY) {
        int valueW = choiceValueWidth();
        int textY = centerTextY(y, ctrlH);

        boolean prevHover = SettingsTheme.inside(mouseX, mouseY, x, y, choiceBtn, ctrlH);
        SettingsTheme.button(ctx, x, y, choiceBtn, ctrlH, prevHover);
        SettingsTheme.centeredText(ctx, this.font, "<", x + choiceBtn / 2, textY, SettingsTheme.TEXT);

        int vx = x + choiceBtn + gap;
        SettingsTheme.field(ctx, vx, y, valueW, ctrlH);
        SettingsTheme.centeredText(ctx, this.font,
            SettingsTheme.truncate(this.font, row.value(), valueW - gap * 2),
            vx + valueW / 2, textY, SettingsTheme.TEXT_STRONG);

        int nx = vx + valueW + gap;
        boolean nextHover = SettingsTheme.inside(mouseX, mouseY, nx, y, choiceBtn, ctrlH);
        SettingsTheme.button(ctx, nx, y, choiceBtn, ctrlH, nextHover);
        SettingsTheme.centeredText(ctx, this.font, ">", nx + choiceBtn / 2, textY, SettingsTheme.TEXT);
    }

    private void renderTextField(GuiGraphicsExtractor ctx, TextRow row, int x, int y) {
        int w = textFieldWidth();
        SettingsTheme.field(ctx, x, y, w, ctrlH);
        boolean editing = focus == Focus.ROW_TEXT && focusedRow == row;
        if (editing) {
            rowInput.render(ctx, this.font, x + gap, y, w - gap * 2, ctrlH, true, SettingsTheme.TEXT_STRONG);
        } else {
            SettingsTheme.text(ctx, this.font,
                SettingsTheme.truncate(this.font, row.get(), w - gap * 2),
                x + gap, centerTextY(y, ctrlH), SettingsTheme.TEXT_STRONG);
        }
    }

    private void renderColorField(GuiGraphicsExtractor ctx, ColorRow row, int x, int y) {
        SettingsTheme.field(ctx, x, y, colorW, ctrlH);
        int swatchW = d(34, 12);
        SettingsTheme.rect(ctx, x + 2, y + 2, swatchW, ctrlH - 4, 0xFF000000 | row.get());
        SettingsTheme.rect(ctx, x + 2 + swatchW, y + 1, 1, ctrlH - 2, SettingsTheme.BORDER);
        SettingsTheme.text(ctx, this.font, row.hex(), x + swatchW + gap + 2,
            centerTextY(y, ctrlH), SettingsTheme.TEXT_STRONG);

        // Disclosure caret
        boolean open = row.id.equals(openColorRowId);
        int ax = x + colorW - 8;
        int ay = y + ctrlH / 2 + (open ? 1 : -2);
        for (int i = 0; i < 3; i++) {
            int dy = open ? -i : i;
            SettingsTheme.rect(ctx, ax + i, ay + dy, 1, 1, SettingsTheme.CARET);
            SettingsTheme.rect(ctx, ax + 4 - i, ay + dy, 1, 1, SettingsTheme.CARET);
        }
    }

    private void renderAction(GuiGraphicsExtractor ctx, ActionRow row, int x, int y, int mouseX, int mouseY) {
        int w = actionWidth(row);
        boolean hovered = SettingsTheme.inside(mouseX, mouseY, x, y, w, ctrlH);
        int bg = row.primary
            ? (hovered ? SettingsTheme.BTN_ACCENT_HI : SettingsTheme.BTN_PRIMARY)
            : (hovered ? SettingsTheme.BTN_HOVER : SettingsTheme.BTN_BG);
        SettingsTheme.raised(ctx, x, y, w, ctrlH, bg, SettingsTheme.BTN_ACCENT_L, SettingsTheme.BTN_ACCENT_D);
        SettingsTheme.centeredText(ctx, this.font,
            SettingsTheme.truncate(this.font, row.button, w - gap * 2), x + w / 2,
            centerTextY(y, ctrlH), SettingsTheme.TEXT);
    }

    private static final SimpleCPSConfig.Position[] POS_CELLS = {
        SimpleCPSConfig.Position.TOP_LEFT, null, SimpleCPSConfig.Position.TOP_RIGHT,
        null, SimpleCPSConfig.Position.CENTER, null,
        SimpleCPSConfig.Position.BOTTOM_LEFT, null, SimpleCPSConfig.Position.BOTTOM_RIGHT
    };

    private void renderPositionGrid(GuiGraphicsExtractor ctx, PositionRow row, int x, int y, int mouseX, int mouseY) {
        SettingsTheme.sunken(ctx, x, y, posGridW, posGridH, SettingsTheme.POS_PANEL, SettingsTheme.FIELD_RIM);
        SimpleCPSConfig.Position current = row.get();

        for (int i = 0; i < POS_CELLS.length; i++) {
            SimpleCPSConfig.Position cell = POS_CELLS[i];
            int cxi = x + 2 + (i % 3) * (posCellW + posGap);
            int cyi = y + 2 + (i / 3) * (posCellH + posGap);

            if (cell == null) {
                SettingsTheme.rect(ctx, cxi, cyi, posCellW, posCellH, SettingsTheme.POS_DEAD_BG);
                SettingsTheme.frame(ctx, cxi, cyi, posCellW, posCellH, SettingsTheme.POS_DEAD_BRD);
                continue;
            }

            boolean selected = cell == current;
            boolean hovered = SettingsTheme.inside(mouseX, mouseY, cxi, cyi, posCellW, posCellH);
            SettingsTheme.rect(ctx, cxi, cyi, posCellW, posCellH,
                selected ? SettingsTheme.POS_SEL_BG : (hovered ? SettingsTheme.SEL_BG : SettingsTheme.POS_CELL_BG));
            SettingsTheme.frame(ctx, cxi, cyi, posCellW, posCellH,
                selected ? SettingsTheme.POS_SEL_BRD : SettingsTheme.POS_CELL_BRD);

            // A little bar showing where content would sit inside that anchor.
            int markW = Math.max(5, posCellW / 4);
            int markH = Math.max(2, posCellH / 6);
            int inset = Math.max(2, posCellW / 12);
            int markX = switch (cell) {
                case TOP_RIGHT, BOTTOM_RIGHT -> cxi + posCellW - markW - inset;
                case CENTER -> cxi + (posCellW - markW) / 2;
                default -> cxi + inset;
            };
            int markY = switch (cell) {
                case BOTTOM_LEFT, BOTTOM_RIGHT -> cyi + posCellH - markH - inset;
                case CENTER -> cyi + (posCellH - markH) / 2;
                default -> cyi + inset;
            };
            SettingsTheme.rect(ctx, markX, markY, markW, markH,
                selected ? SettingsTheme.TEXT_STRONG : SettingsTheme.POS_CELL_MARK);
        }

        int infoX = x + posGridW + pad;
        int infoRoom = rowX() + rowW() - gap - infoX;
        if (infoRoom > 30) {
            SettingsTheme.text(ctx, this.font, current.name(), infoX, y + gap, SettingsTheme.TEXT_STRONG);
            List<String> lines = SettingsTheme.wrap(this.font,
                "CLICK A CELL TO ANCHOR THIS MODULE. FINE-TUNE WITH X / Y OFFSET BELOW.", infoRoom);
            int ly = y + gap + this.font.lineHeight + gap;
            for (String line : lines) {
                if (ly + this.font.lineHeight > y + posGridH) break;
                SettingsTheme.text(ctx, this.font, line, infoX, ly, SettingsTheme.TEXT_DIM);
                ly += this.font.lineHeight + 1;
            }
        }
    }

    // --- Inline color picker --------------------------------------------------
    //
    // A saturation/value square over a hue bar, the same model the standalone
    // ColorPickerScreen uses, restyled to this menu and folded into the row.
    // Geometry lives in these helpers so the renderer and the click handler
    // cannot disagree about where anything is.

    private int svBoxX(int x) { return x + gap; }
    private int svBoxY(int y) { return y + gap; }
    private int svBoxW(int w) { return Math.max(24, Math.min(d(240, 90), w - previewColumnW() - gap * 3)); }
    private int svBoxH() { return Math.max(16, d(110, 44)); }

    private int hueBarY(int y) { return svBoxY(y) + svBoxH() + gap; }
    private int hueBarH() { return Math.max(5, d(20, 8)); }

    private int colorHexY(int y) { return hueBarY(y) + hueBarH() + gap; }
    private int colorHexX(int x) { return x + gap + SettingsTheme.textWidth(this.font, "HEX") + gap; }
    private int colorHexW() { return SettingsTheme.textWidth(this.font, "#FFFFFF") + gap * 3; }

    /** Width of the preview swatch + R/G/B readout column beside the square. */
    private int previewColumnW() {
        return Math.max(SettingsTheme.textWidth(this.font, "B 255"), d(48, 18)) + gap;
    }

    private int swatchStripWidth() {
        return SettingsTheme.SWATCHES.length * (swatch + 1) - 1;
    }

    /** Adopt a row's color into the picker's HSV state. */
    private void syncPickerFrom(ColorRow row) {
        int rgb = row.get();
        float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        pickerHue = hsb[0];
        pickerSat = hsb[1];
        pickerVal = hsb[2];
    }

    private void applyPicker(ColorRow row) {
        row.set(java.awt.Color.HSBtoRGB(pickerHue, pickerSat, pickerVal) & 0xFFFFFF);
        markDirty();
    }

    private void renderColorEditor(GuiGraphicsExtractor ctx, ColorRow row, int x, int y, int w) {
        SettingsTheme.sunken(ctx, x, y, w, colorPanelH, SettingsTheme.COLOR_PANEL, SettingsTheme.FIELD_RIM);

        int bx = svBoxX(x);
        int by = svBoxY(y);
        int bw = svBoxW(w);
        int bh = svBoxH();

        // Saturation left-to-right at full value, then value darkened downwards.
        // One fill per column is what the standalone picker does too; at these
        // widths it is a few dozen quads.
        for (int i = 0; i < bw; i++) {
            int c = java.awt.Color.HSBtoRGB(pickerHue, i / (float) bw, 1.0f);
            SettingsTheme.rect(ctx, bx + i, by, 1, bh, 0xFF000000 | c);
        }
        ctx.fillGradient(bx, by, bx + bw, by + bh, 0x00000000, 0xFF000000);
        SettingsTheme.frame(ctx, bx, by, bw, bh, SettingsTheme.BORDER);

        int curX = bx + Math.round(pickerSat * (bw - 1));
        int curY = by + Math.round((1f - pickerVal) * (bh - 1));
        crosshair(ctx, curX, curY);

        // Hue bar
        int hy = hueBarY(y);
        int hh = hueBarH();
        for (int i = 0; i < bw; i++) {
            int c = java.awt.Color.HSBtoRGB(i / (float) bw, 1.0f, 1.0f);
            SettingsTheme.rect(ctx, bx + i, hy, 1, hh, 0xFF000000 | c);
        }
        SettingsTheme.frame(ctx, bx, hy, bw, hh, SettingsTheme.BORDER);
        int hueX = bx + Math.round(pickerHue * (bw - 1));
        SettingsTheme.rect(ctx, hueX - 1, hy - 1, 3, hh + 2, SettingsTheme.SLIDER_KNOB);
        SettingsTheme.frame(ctx, hueX - 1, hy - 1, 3, hh + 2, SettingsTheme.BORDER);

        // Preview + channel readout beside the square
        int px = bx + bw + gap;
        int pw = previewColumnW() - gap;
        int previewH = Math.max(8, bh / 3);
        if (px + pw <= x + w - gap) {
            SettingsTheme.rect(ctx, px, by, pw, previewH, 0xFF000000 | row.get());
            SettingsTheme.frame(ctx, px, by, pw, previewH, SettingsTheme.BORDER);

            int rgb = row.get();
            String[] readout = {
                "R " + ((rgb >> 16) & 0xFF),
                "G " + ((rgb >> 8) & 0xFF),
                "B " + (rgb & 0xFF)
            };
            int ry = by + previewH + gap;
            for (String line : readout) {
                if (ry + this.font.lineHeight > by + bh) break;
                SettingsTheme.text(ctx, this.font, line, px, ry, SettingsTheme.TEXT_MUTED);
                ry += this.font.lineHeight + 1;
            }
        }

        // Hex field + the quick-pick swatches
        int hexY = colorHexY(y);
        SettingsTheme.text(ctx, this.font, "HEX", x + gap, centerTextY(hexY, ctrlH), SettingsTheme.CARET);
        int hexX = colorHexX(x);
        int hexW = colorHexW();
        SettingsTheme.field(ctx, hexX, hexY, hexW, ctrlH);
        boolean editingHex = focus == Focus.ROW_HEX && focusedRow == row;
        if (editingHex) {
            rowInput.render(ctx, this.font, hexX + gap, hexY, hexW - gap * 2, ctrlH, true, SettingsTheme.TEXT_STRONG);
        } else {
            SettingsTheme.text(ctx, this.font, row.hex(), hexX + gap, centerTextY(hexY, ctrlH),
                SettingsTheme.TEXT_STRONG);
        }

        // Copy confirmation, fading out where the swatches have not reached.
        float toast = hexToastProgress(row);
        if (toast > 0f) {
            SettingsTheme.text(ctx, this.font, "COPIED!", hexX + hexW + gap, centerTextY(hexY, ctrlH),
                com.eymistaken.simplecps.util.RenderUtil.withAlpha(SettingsTheme.OK_TEXT, toast));
        }

        int swatchesW = swatchStripWidth();
        int sx = x + w - gap - swatchesW;
        if (sx > hexX + hexW + gap) {
            int sy = hexY + (ctrlH - swatch) / 2;
            for (int i = 0; i < SettingsTheme.SWATCHES.length; i++) {
                int cxi = sx + i * (swatch + 1);
                SettingsTheme.rect(ctx, cxi, sy, swatch, swatch, 0xFF000000 | SettingsTheme.SWATCHES[i]);
                SettingsTheme.frame(ctx, cxi, sy, swatch, swatch,
                    SettingsTheme.SWATCHES[i] == row.get() ? SettingsTheme.TEXT_STRONG : SettingsTheme.BORDER);
            }
        }
    }

    /** Two-tone cursor so it stays visible on both light and dark areas. */
    private void crosshair(GuiGraphicsExtractor ctx, int cx, int cy) {
        SettingsTheme.frame(ctx, cx - 2, cy - 2, 5, 5, 0xFF000000);
        SettingsTheme.frame(ctx, cx - 3, cy - 3, 7, 7, 0xFFFFFFFF);
    }

    // --- Right panel -----------------------------------------------------------

    private int rightPanelX() {
        return rightCollapsed ? innerX + innerW - rightW : rightX;
    }

    private void renderRightPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int x = rightPanelX();
        int w = rightW;
        SettingsTheme.rect(ctx, x, bodyY, w, bodyH, SettingsTheme.SIDE_BG);
        SettingsTheme.rect(ctx, x - 1, bodyY, 1, bodyH, SettingsTheme.BORDER);

        int contentH = rightContentHeight(w);
        rightScroll = clampScroll(rightScroll, contentH, bodyH);

        ctx.enableScissor(x, bodyY, x + w, bodyY + bodyH);
        ctx.pose().pushMatrix();
        ctx.pose().translate(0f, -rightScroll);

        boolean mouseInPanel = mouseX >= x && mouseX < x + w && mouseY >= bodyY && mouseY < bodyY + bodyH;
        int vmy = mouseY + rightScroll;
        int mx = mouseInPanel ? mouseX : -1;
        int my = mouseInPanel ? vmy : -1;

        int y = bodyY + gap;
        int cw = w - gap * 2;
        int cx = x + gap;

        int previewH = previewCardHeight(cw);
        if (previewH > 0) {
            renderPreviewCard(ctx, cx, y, cw, previewH, mx, my, delta);
            y += previewH + gap;
        }
        y = renderInfoCard(ctx, cx, y, cw) + gap;
        y = renderPresetCard(ctx, cx, y, cw, mx, my) + gap;
        y = renderShareCard(ctx, cx, y, cw, mx, my) + gap;
        renderResetAllButton(ctx, cx, y, cw, mx, my);

        ctx.pose().popMatrix();
        ctx.disableScissor();
        renderScrollbar(ctx, x + w - scrollbarW - 1, bodyY, bodyH, rightScroll, contentH);
    }

    private int rightContentHeight(int w) {
        int cw = w - gap * 2;
        int preview = previewCardHeight(cw);
        return gap + (preview > 0 ? preview + gap : 0) + infoCardHeight(cw) + gap + presetCardHeight(cw) + gap
            + shareCardHeight(cw) + gap + dangerButtonH() + gap;
    }

    // --- Preview ---------------------------------------------------------------
    //
    // The card draws the selected module at true screen scale: the whole screen sits
    // under pose().scale(renderScale, renderScale), so scaling by its reciprocal puts
    // one module pixel back on one screen pixel. That is the point of the panel —
    // anything scaled to fit would answer the wrong question about how big a module is.
    //
    // Consequences: the card grows downward to fit, but never sideways. A module wider
    // than the card is clipped, so it can never bleed left into the settings rows, and
    // FOCUS is the way to see the rest of it.

    /**
     * This frame's preview. Fetched once in {@link #layout()} rather than at each of the
     * half-dozen places that need it, because a module is free to build one per call —
     * {@link HudPreview#ofModule} does — and the size feeds card heights, the scroll
     * extent, the preset list's top and hit testing alike.
     */
    private HudPreview previewCache;

    private void refreshPreview() {
        if (previewModule == null) {
            previewCache = null;
            return;
        }
        try {
            previewCache = previewModule.getPreview();
        } catch (Exception e) {
            previewCache = null;
        }
    }

    /** Live preview of the selected module, or null when the page has none. */
    private HudPreview activePreview() {
        return previewModule == null ? null : previewCache;
    }

    /** Floor on the card, sized so the body is always worth looking at. */
    private int previewMinH() {
        return cardHeadH + gap * 2 + d(56, 20);
    }

    /**
     * How tall the preview may grow before the cards under it would be pushed off the
     * bottom of the panel.
     *
     * <p>INFO is reserved at a fixed three lines rather than measured with
     * {@link #infoCardHeight}: that height changes with whichever row the cursor happens
     * to be over, and feeding it in here would make the preview box resize every time the
     * mouse moved between rows.
     */
    private int previewMaxH() {
        int cw = rightW - gap * 2;
        int infoReserve = cardHeadH + gap + this.font.lineHeight + gap
            + 3 * (this.font.lineHeight + 1) + this.font.lineHeight + gap + gap;
        int below = infoReserve + gap + presetCardHeight(cw) + gap + shareCardHeight(cw)
            + gap + dangerButtonH() + gap;
        return Math.max(previewMinH(), bodyH - gap * 2 - below);
    }

    /** Height of the whole card including its header, or 0 when there is no preview. */
    private int previewCardHeight(int w) {
        HudPreview preview = activePreview();
        if (preview == null) return 0;
        int natural = cardHeadH + gap * 2 + previewVirtual(previewHeightOf(preview));
        return Math.max(previewMinH(), Math.min(natural, previewMaxH()));
    }

    /** Screen pixels to the virtual units everything in this screen is laid out in. */
    private int previewVirtual(int screenPx) {
        return Math.max(0, Math.round(screenPx / renderScale));
    }

    private int previewWidthOf(HudPreview preview) {
        try {
            previewModule.setPreviewing(true);
            return Math.max(0, preview.width());
        } catch (Exception e) {
            return 0;
        } finally {
            previewModule.setPreviewing(false);
        }
    }

    private int previewHeightOf(HudPreview preview) {
        try {
            previewModule.setPreviewing(true);
            return Math.max(0, preview.height());
        } catch (Exception e) {
            return 0;
        } finally {
            previewModule.setPreviewing(false);
        }
    }

    private int previewButtonSize() {
        return Math.min(resetW, cardHeadH - 2);
    }

    /** The FOCUS button's rect, given the card's own top-left corner. */
    private int previewButtonX(int cardX, int cardW) {
        return cardX + cardW - gap - previewButtonSize();
    }

    private int previewButtonY(int cardY) {
        return cardY + (cardHeadH - previewButtonSize()) / 2;
    }

    private void renderPreviewCard(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                   int mouseX, int mouseY, float delta) {
        SettingsTheme.card(ctx, x, y, w, h);
        SettingsTheme.rect(ctx, x + 1, y + cardHeadH, w - 2, 1, SettingsTheme.BORDER);
        SettingsTheme.text(ctx, this.font, "PREVIEW", x + gap, centerTextY(y, cardHeadH),
            SettingsTheme.TEXT_MUTED);
        renderPreviewButton(ctx, x, y, w, mouseX, mouseY);
        // While focus mode is up the overlay shows the module instead. Drawing it here as
        // well rendered it twice a frame, which double-stepped its own animations and left
        // a clipped copy underneath for text to punch through.
        renderPreviewBody(ctx, x + gap, y + cardHeadH + gap,
            w - gap * 2, h - cardHeadH - gap * 2, delta, !previewFocused);
    }

    private void renderPreviewButton(GuiGraphicsExtractor ctx, int cardX, int cardY, int cardW,
                                     int mouseX, int mouseY) {
        int size = previewButtonSize();
        int bx = previewButtonX(cardX, cardW);
        int by = previewButtonY(cardY);
        boolean hovered = SettingsTheme.inside(mouseX, mouseY, bx, by, size, size);
        SettingsTheme.button(ctx, bx, by, size, size, hovered || previewFocused);
        int ix = bx + (size - 7) / 2;
        int iy = by + (size - 7) / 2;
        int color = hovered || previewFocused ? SettingsTheme.TEXT_STRONG : SettingsTheme.TEXT_MUTED;
        if (previewFocused) {
            SettingsTheme.collapseIcon(ctx, ix, iy, color);
        } else {
            SettingsTheme.expandIcon(ctx, ix, iy, color);
        }
    }

    /**
     * Checkerboard cell size: big enough to read as a pattern at any UI scale, and coarse
     * enough that a wide box does not cost thousands of fills a frame. The cap only bites
     * in focus mode, where the box can be most of the screen.
     */
    private int previewCheckerCell(int boxW) {
        return Math.max(d(9, 3), (boxW + CHECKER_MAX_COLUMNS - 1) / CHECKER_MAX_COLUMNS);
    }

    private static final int CHECKER_MAX_COLUMNS = 48;

    /**
     * The checkerboard the module is drawn on. HUD modules live over gameplay, so a flat
     * dark card would flatter every light-colored setting and hide a low background
     * opacity entirely; alternating tones show transparency and both contrast cases at once.
     */
    private void renderPreviewBody(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                   float delta, boolean drawModule) {
        if (w <= 0 || h <= 0) return;
        SettingsTheme.rect(ctx, x, y, w, h, SettingsTheme.CHECKER_DARK);

        ctx.enableScissor(x, y, x + w, y + h);
        int cell = previewCheckerCell(w);
        for (int row = 0; row * cell < h; row++) {
            for (int col = row % 2; col * cell < w; col += 2) {
                SettingsTheme.rect(ctx, x + col * cell, y + row * cell,
                    Math.min(cell, w - col * cell), Math.min(cell, h - row * cell),
                    SettingsTheme.CHECKER_LIGHT);
            }
        }
        if (drawModule) drawPreviewModule(ctx, x, y, w, h, delta);
        // After the module, so a preview that fills the box cannot draw over its own edge.
        SettingsTheme.frame(ctx, x, y, w, h, SettingsTheme.BORDER);
        ctx.disableScissor();
    }

    /** Center the module in the given box and draw it one-for-one with screen pixels. */
    private void drawPreviewModule(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float delta) {
        HudPreview preview = activePreview();
        if (preview == null) return;

        int vmw = previewVirtual(previewWidthOf(preview));
        int vmh = previewVirtual(previewHeightOf(preview));
        if (vmw <= 0 || vmh <= 0) return;

        ctx.pose().pushMatrix();
        ctx.pose().translate(x + (w - vmw) / 2f, y + (h - vmh) / 2f);
        ctx.pose().scale(1f / renderScale, 1f / renderScale);
        previewModule.setPreviewing(true);
        previewModule.setRenderAlpha(1f);
        try {
            preview.render(ctx, delta);
        } catch (Exception e) {
            // A third-party preview that throws loses its drawing for this frame, not
            // the screen. Silent because this runs every frame; a log line would flood.
        } finally {
            previewModule.setPreviewing(false);
            ctx.pose().popMatrix();
        }
    }

    // --- Preview focus mode ----------------------------------------------------
    //
    // An overlay, not a bigger card. The right column keeps rendering the normal-size
    // card underneath at its usual place, so the cards below it never move — closing
    // focus cannot leave the layout somewhere it was not before.
    //
    // The rect is anchored to the card's top-right corner and grows left and down until
    // the module fits, then clamps to the panel. Anchoring ignores rightScroll on purpose:
    // scrolling the column while focused would otherwise drag the overlay off the screen.

    /** Breathing room left around the module in focus mode. */
    private int previewFocusPad() {
        return d(20, 6);
    }

    /** {x, y, w, h} of the focus overlay, or null when there is nothing to show. */
    private int[] previewFocusRect() {
        HudPreview preview = activePreview();
        if (preview == null) return null;

        // The card's own inset plus the extra breathing room focus mode adds around the
        // module, so it is not pressed against its own border at full size.
        int inset = gap * 2 + previewFocusPad() * 2;
        int maxW = innerW - gap * 2;
        int maxH = bodyH - gap * 2;

        int w = Math.min(maxW, Math.max(rightW - gap * 2,
            previewVirtual(previewWidthOf(preview)) + inset));
        int h = Math.min(maxH, Math.max(previewMinH(),
            cardHeadH + previewVirtual(previewHeightOf(preview)) + inset));

        // Top-right of the card, unscrolled.
        int anchorRight = rightPanelX() + rightW - gap;
        int y = bodyY + gap;
        int x = Math.max(innerX + gap, anchorRight - w);
        if (y + h > bodyY + bodyH - gap) y = Math.max(bodyY + gap, bodyY + bodyH - gap - h);
        return new int[] {x, y, w, h};
    }

    private void renderPreviewFocus(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int[] r = previewFocusRect();
        if (r == null) {
            previewFocused = false;
            return;
        }
        int x = r[0], y = r[1], w = r[2], h = r[3];

        // Text is batched separately from fills and drawn after all of them, so an opaque
        // rectangle alone does not hide what is behind this overlay — preset names and the
        // settings rows came through it. nextStratum is the hard barrier: everything from
        // here on draws above everything already submitted this frame.
        ctx.nextStratum();

        // Opaque ground first. PANEL_BG and CARD_BG are thin white glazes designed to sit
        // on the panel, so on their own they left the overlay see-through.
        SettingsTheme.rect(ctx, x, y, w, h, SettingsTheme.FOCUS_BG);
        SettingsTheme.card(ctx, x, y, w, h);
        SettingsTheme.rect(ctx, x + 1, y + cardHeadH, w - 2, 1, SettingsTheme.BORDER);
        SettingsTheme.text(ctx, this.font, "PREVIEW", x + gap, centerTextY(y, cardHeadH),
            SettingsTheme.TEXT_STRONG);

        // Only when it fits whole: truncating this would leave a bare ellipsis, which
        // reads as a broken label rather than a shortened hint.
        String hint = "ESC TO EXIT";
        int hintX = x + gap + SettingsTheme.textWidth(this.font, "PREVIEW") + gap * 2;
        if (previewButtonX(x, w) - gap - hintX >= SettingsTheme.textWidth(this.font, hint)) {
            SettingsTheme.text(ctx, this.font, hint, hintX, centerTextY(y, cardHeadH),
                SettingsTheme.TEXT_GHOST);
        }
        renderPreviewButton(ctx, x, y, w, mouseX, mouseY);
        renderPreviewBody(ctx, x + gap, y + cardHeadH + gap,
            w - gap * 2, h - cardHeadH - gap * 2, delta, true);
    }

    /** Route a click while focus mode is up; it covers the columns beneath it. */
    private boolean handlePreviewFocusClick(double mouseX, double mouseY) {
        int[] r = previewFocusRect();
        if (r == null) return false;
        int size = previewButtonSize();
        if (SettingsTheme.inside(mouseX, mouseY,
            previewButtonX(r[0], r[2]), previewButtonY(r[1]), size, size)) {
            previewFocused = false;
            return true;
        }
        // Swallow the rest of the overlay so a press does not land on a settings row
        // that happens to sit behind it.
        return SettingsTheme.inside(mouseX, mouseY, r[0], r[1], r[2], r[3]);
    }


    private String infoTitle() {
        if (infoRow != null) return SettingsTheme.up(infoRow.label);
        if (searching()) return "SEARCH";
        Page p = activePage();
        return p == null ? "" : p.name;
    }

    private String infoDesc() {
        if (infoRow != null) return SettingsTheme.up(infoRow.desc);
        if (searching()) return "TYPE A SETTING OR MODULE NAME. RESULTS FROM EVERY MODULE ARE LISTED TOGETHER.";
        Page p = activePage();
        return p == null ? "" : SettingsTheme.up(p.desc);
    }

    private String infoDefault() {
        if (infoRow == null) return "";
        String text = infoRow.defaultText();
        return text.isEmpty() ? "" : "DEFAULT: " + SettingsTheme.up(text);
    }

    private int infoCardHeight(int w) {
        int inner = w - gap * 2;
        int lines = SettingsTheme.wrap(this.font, infoDesc(), inner).size();
        int h = cardHeadH + gap + this.font.lineHeight + gap + lines * (this.font.lineHeight + 1);
        if (!infoDefault().isEmpty()) h += this.font.lineHeight + gap;
        return h + gap;
    }

    private int renderInfoCard(GuiGraphicsExtractor ctx, int x, int y, int w) {
        int h = infoCardHeight(w);
        SettingsTheme.card(ctx, x, y, w, h);
        SettingsTheme.rect(ctx, x + 1, y + cardHeadH, w - 2, 1, SettingsTheme.BORDER);
        SettingsTheme.text(ctx, this.font, "INFO", x + gap, centerTextY(y, cardHeadH), SettingsTheme.TEXT_MUTED);

        int ty = y + cardHeadH + gap;
        SettingsTheme.text(ctx, this.font, SettingsTheme.truncate(this.font, infoTitle(), w - gap * 2),
            x + gap, ty, SettingsTheme.TEXT_STRONG);
        ty += this.font.lineHeight + gap;

        for (String line : SettingsTheme.wrap(this.font, infoDesc(), w - gap * 2)) {
            SettingsTheme.text(ctx, this.font, line, x + gap, ty, SettingsTheme.TEXT_MUTED);
            ty += this.font.lineHeight + 1;
        }

        String def = infoDefault();
        if (!def.isEmpty()) {
            SettingsTheme.text(ctx, this.font, SettingsTheme.truncate(this.font, def, w - gap * 2),
                x + gap, ty, SettingsTheme.TEXT_OFF);
        }
        return y + h;
    }

    /** Rows the preset list shows at once before it starts scrolling. */
    private static final int PRESET_VISIBLE_MAX = 6;

    private int presetRowPitch() {
        return presetRowH + 1;
    }

    /** Height of the scrolling viewport; always at least one row so the card is stable. */
    private int presetListH() {
        return Math.max(1, Math.min(PRESET_VISIBLE_MAX, presetNames.size())) * presetRowPitch();
    }

    private int presetContentH() {
        return presetNames.size() * presetRowPitch();
    }

    /** Top of the preset list, in right-panel content space. */
    private int presetListTop() {
        int cw = rightW - gap * 2;
        int preview = previewCardHeight(cw);
        return bodyY + gap + (preview > 0 ? preview + gap : 0)
            + infoCardHeight(cw) + gap + cardHeadH + gap;
    }

    private int presetCardHeight(int w) {
        return cardHeadH + gap + presetListH() + gap + presetRowH + gap;
    }

    private int renderPresetCard(GuiGraphicsExtractor ctx, int x, int y, int w, int mouseX, int mouseY) {
        int h = presetCardHeight(w);
        SettingsTheme.card(ctx, x, y, w, h);
        SettingsTheme.rect(ctx, x + 1, y + cardHeadH, w - 2, 1, SettingsTheme.BORDER);
        SettingsTheme.text(ctx, this.font, "PRESETS", x + gap, centerTextY(y, cardHeadH), SettingsTheme.TEXT_MUTED);
        SettingsTheme.rightText(ctx, this.font, String.valueOf(presetNames.size()),
            x + w - gap, centerTextY(y, cardHeadH), SettingsTheme.TEXT_OFF);

        int listY = y + cardHeadH + gap;
        int listH = presetListH();

        if (presetNames.isEmpty()) {
            SettingsTheme.text(ctx, this.font, "NO SAVED PRESETS", x + gap * 2,
                centerTextY(listY, presetRowH), SettingsTheme.TEXT_GHOST);
        } else {
            int contentH = presetContentH();
            boolean overflows = contentH > listH;
            presetScroll = clampScroll(presetScroll, contentH, listH);

            // Once the list outgrows its viewport the rows scroll inside it, so
            // older presets stay reachable instead of falling off the bottom.
            ctx.enableScissor(x, listY, x + w, listY + listH);
            ctx.pose().pushMatrix();
            ctx.pose().translate(0f, -presetScroll);

            int trashW = presetTrashW();
            int listW = w - gap * 2 - (overflows ? scrollbarW + 1 : 0);
            int virtualMouseY = mouseY < 0 ? -1 : mouseY + presetScroll;
            boolean mouseInList = mouseY >= listY && mouseY < listY + listH;

            for (int i = 0; i < presetNames.size(); i++) {
                int ry = listY + i * presetRowPitch();
                String name = presetNames.get(i);
                boolean hovered = mouseInList
                    && SettingsTheme.inside(mouseX, virtualMouseY, x + gap, ry, listW, presetRowH);
                boolean trashHovered = mouseInList && SettingsTheme.inside(mouseX, virtualMouseY,
                    x + gap + listW - trashW, ry, trashW, presetRowH);
                SettingsTheme.rect(ctx, x + gap, ry, listW, presetRowH,
                    hovered ? SettingsTheme.SEL_BG : SettingsTheme.IDLE_BG);
                SettingsTheme.frame(ctx, x + gap, ry, listW, presetRowH,
                    hovered ? SettingsTheme.SEL_BORDER : SettingsTheme.BORDER);
                if (trashHovered) {
                    SettingsTheme.rect(ctx, x + gap + listW - trashW, ry + 1, trashW - 1, presetRowH - 2, 0xFF552222);
                }
                SettingsTheme.rect(ctx, x + gap * 2, ry + presetRowH / 2 - 1, 3, 3,
                    hovered ? SettingsTheme.DOT_ON : SettingsTheme.RESET_FG_OFF);
                int nameX = x + gap * 2 + 3 + gap;
                SettingsTheme.text(ctx, this.font,
                    SettingsTheme.truncate(this.font, name, x + gap + listW - trashW - nameX), nameX,
                    centerTextY(ry, presetRowH),
                    hovered ? SettingsTheme.TEXT_STRONG : SettingsTheme.TEXT_MUTED);
                SettingsTheme.trashIcon(ctx, x + gap + listW - trashW + (trashW - 7) / 2,
                    ry + (presetRowH - 7) / 2,
                    trashHovered ? 0xFFFF9999 : SettingsTheme.RESET_FG_OFF);
            }

            ctx.pose().popMatrix();
            ctx.disableScissor();
            renderScrollbar(ctx, x + w - gap - scrollbarW, listY, listH, presetScroll, contentH);
        }

        int by = listY + listH + gap;
        boolean saveHovered = SettingsTheme.inside(mouseX, mouseY, x + gap, by, w - gap * 2, presetRowH);
        SettingsTheme.button(ctx, x + gap, by, w - gap * 2, presetRowH, saveHovered);
        SettingsTheme.centeredText(ctx, this.font, "SAVE CURRENT", x + w / 2,
            centerTextY(by, presetRowH), SettingsTheme.TEXT);
        return y + h;
    }

    private int shareCardHeight(int w) {
        int lines = SettingsTheme.wrap(this.font, SettingsTheme.up(shareMessage), w - gap * 2).size();
        return cardHeadH + gap + ctrlH + gap + ctrlH + gap + lines * (this.font.lineHeight + 1) + gap;
    }

    private int renderShareCard(GuiGraphicsExtractor ctx, int x, int y, int w, int mouseX, int mouseY) {
        int h = shareCardHeight(w);
        SettingsTheme.card(ctx, x, y, w, h);
        SettingsTheme.rect(ctx, x + 1, y + cardHeadH, w - 2, 1, SettingsTheme.BORDER);
        SettingsTheme.text(ctx, this.font, "SHARE CODE", x + gap, centerTextY(y, cardHeadH), SettingsTheme.TEXT_MUTED);

        int fy = y + cardHeadH + gap;
        SettingsTheme.field(ctx, x + gap, fy, w - gap * 2, ctrlH);
        if (shareInput.getValue().isEmpty() && focus != Focus.SHARE) {
            SettingsTheme.text(ctx, this.font, "EYMHUD1-...", x + gap * 2, centerTextY(fy, ctrlH),
                SettingsTheme.TEXT_PLACEHOLD);
        } else {
            shareInput.render(ctx, this.font, x + gap * 2, fy, w - gap * 4, ctrlH,
                focus == Focus.SHARE, SettingsTheme.TEXT_STRONG);
        }

        int by = fy + ctrlH + gap;
        int halfW = (w - gap * 3) / 2;
        boolean exportHovered = SettingsTheme.inside(mouseX, mouseY, x + gap, by, halfW, ctrlH);
        SettingsTheme.button(ctx, x + gap, by, halfW, ctrlH, exportHovered);
        SettingsTheme.centeredText(ctx, this.font, "EXPORT", x + gap + halfW / 2,
            centerTextY(by, ctrlH), SettingsTheme.TEXT);

        int ix = x + gap + halfW + gap;
        boolean importHovered = SettingsTheme.inside(mouseX, mouseY, ix, by, halfW, ctrlH);
        SettingsTheme.button(ctx, ix, by, halfW, ctrlH, importHovered);
        SettingsTheme.centeredText(ctx, this.font, "IMPORT", ix + halfW / 2,
            centerTextY(by, ctrlH), SettingsTheme.TEXT);

        int my = by + ctrlH + gap;
        for (String line : SettingsTheme.wrap(this.font, SettingsTheme.up(shareMessage), w - gap * 2)) {
            SettingsTheme.text(ctx, this.font, line, x + gap, my,
                shareOk ? SettingsTheme.OK_TEXT : SettingsTheme.TEXT_FAINT);
            my += this.font.lineHeight + 1;
        }
        return y + h;
    }

    /** Height of the destructive button at the foot of the right panel. */
    private int dangerButtonH() {
        return d(40, 13);
    }

    /** Width of a preset row's delete hit area. */
    private int presetTrashW() {
        return Math.max(9, presetRowH);
    }

    private void renderResetAllButton(GuiGraphicsExtractor ctx, int x, int y, int w, int mouseX, int mouseY) {
        int h = dangerButtonH();
        boolean hovered = SettingsTheme.inside(mouseX, mouseY, x, y, w, h);
        SettingsTheme.raised(ctx, x, y, w, h,
            hovered ? SettingsTheme.DANGER_HOVER : SettingsTheme.DANGER_BG,
            SettingsTheme.DANGER_LIGHT, SettingsTheme.DANGER_DARK);
        SettingsTheme.centeredText(ctx, this.font,
            SettingsTheme.truncate(this.font, "RESET ALL SETTINGS", w - gap * 2),
            x + w / 2, centerTextY(y, h), SettingsTheme.DANGER_TEXT);
    }

    // --- Footer -----------------------------------------------------------------

    private int cancelButtonX() {
        return doneButtonX() - gap - buttonWidth("CANCEL");
    }

    private int doneButtonX() {
        return innerX + innerW - pad - buttonWidth("DONE");
    }

    private int buttonWidth(String text) {
        return SettingsTheme.textWidth(this.font, text) + pad * 3;
    }

    /** Height of the CANCEL / DONE buttons. */
    private int footerButtonH() {
        return Math.min(footerH - 2, d(32, 12));
    }

    private void renderFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        SettingsTheme.rect(ctx, innerX, footerY, innerW, footerH, SettingsTheme.BAR_BG);
        SettingsTheme.rect(ctx, innerX, footerY, innerW, 1, SettingsTheme.BORDER);
        SettingsTheme.rect(ctx, innerX, footerY + 1, innerW, 1, SettingsTheme.BAR_LIGHT);

        int textY = centerTextY(footerY, footerH);
        int statusRoom = cancelButtonX() - innerX - pad - gap;

        String path = "config/simplecps.json";
        int pathW = SettingsTheme.textWidth(this.font, path);
        SettingsTheme.text(ctx, this.font, SettingsTheme.truncate(this.font, path, statusRoom),
            innerX + pad, textY, SettingsTheme.TEXT_FAINT);

        // Only worth saying once there is something to reassure you about;
        // on a screen you have not touched yet it is just noise.
        if (changedSinceOpen) {
            String status = "ALL CHANGES SAVED";
            if (pathW + gap * 4 + SettingsTheme.textWidth(this.font, status) < statusRoom) {
                SettingsTheme.text(ctx, this.font, "|", innerX + pad + pathW + gap, textY, 0xFF3F3F47);
                SettingsTheme.text(ctx, this.font, status, innerX + pad + pathW + gap * 3, textY,
                    SettingsTheme.TEXT_FAINT);
            }
        }

        int bh = footerButtonH();
        int by = footerY + (footerH - bh) / 2;
        int cancelX = cancelButtonX();
        int cancelW = buttonWidth("CANCEL");
        boolean cancelHovered = SettingsTheme.inside(mouseX, mouseY, cancelX, by, cancelW, bh);
        // Destructive: it throws away everything done since the screen opened.
        SettingsTheme.raised(ctx, cancelX, by, cancelW, bh,
            cancelHovered ? SettingsTheme.DANGER_HOVER : SettingsTheme.DANGER_BG,
            SettingsTheme.DANGER_LIGHT, SettingsTheme.DANGER_DARK);
        SettingsTheme.centeredText(ctx, this.font, "CANCEL", cancelX + cancelW / 2,
            centerTextY(by, bh), SettingsTheme.DANGER_TEXT);

        int doneX = doneButtonX();
        int doneW = buttonWidth("DONE");
        boolean doneHovered = SettingsTheme.inside(mouseX, mouseY, doneX, by, doneW, bh);
        SettingsTheme.raised(ctx, doneX, by, doneW, bh,
            doneHovered ? SettingsTheme.DONE_HOVER : SettingsTheme.DONE_BG,
            SettingsTheme.DONE_LIGHT, SettingsTheme.DONE_DARK);
        SettingsTheme.centeredText(ctx, this.font, "DONE", doneX + doneW / 2,
            centerTextY(by, bh), SettingsTheme.DONE_TEXT);
    }

    // --- Confirmations --------------------------------------------------------------

    private static final String RESET_BODY =
        "EVERY MODULE RETURNS TO ITS DEFAULT POSITION, COLOR AND SCALE. "
            + "SAVED PRESETS AND PLUGIN MODULE DATA ARE KEPT.";

    private static final String DISCARD_BODY =
        "EVERYTHING CHANGED SINCE THIS SCREEN OPENED WILL BE PUT BACK, "
            + "INCLUDING THE EDITS ALREADY WRITTEN TO DISK.";

    private String dialogTitle() {
        return dialog == Dialog.DISCARD ? "DISCARD CHANGES" : "RESET ALL SETTINGS";
    }

    private String dialogBody() {
        return dialog == Dialog.DISCARD ? DISCARD_BODY : RESET_BODY;
    }

    private String dialogConfirmLabel() {
        return dialog == Dialog.DISCARD ? "DISCARD" : "RESET";
    }

    private void openDialog(Dialog which) {
        commitFocus();
        dialog = which;
        dialogAnim.snap(0f);
    }

    private void confirmDialog() {
        Dialog which = dialog;
        dialog = Dialog.NONE;
        switch (which) {
            case RESET_ALL -> {
                SimpleCPSConfig.instance.resetToDefaults();
                onConfigReplaced();
            }
            case DISCARD -> discardAndClose();
            case NONE -> { }
        }
    }

    private int modalW() {
        return Math.min(d(460, 220), panelW - pad * 4);
    }

    private int modalHeadH() {
        return d(36, 13);
    }

    private int modalButtonH() {
        return d(34, 14);
    }

    private int modalH() {
        int inner = modalW() - pad * 2;
        int lines = SettingsTheme.wrap(this.font, dialogBody(), inner).size();
        return modalHeadH() + gap * 2 + lines * (this.font.lineHeight + 1)
            + gap + this.font.lineHeight + gap * 2 + modalButtonH() + gap;
    }

    private int modalX() {
        return (vw - modalW()) / 2;
    }

    private int modalY() {
        return (vh - modalH()) / 2;
    }

    private int modalCancelX() {
        return modalX() + gap;
    }

    private int modalConfirmX() {
        return modalX() + modalW() / 2 + gap / 2;
    }

    private int modalButtonW() {
        return modalW() / 2 - gap - gap / 2;
    }

    private int modalButtonY() {
        return modalY() + modalH() - gap - modalButtonH();
    }

    private void renderDialog(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float progress) {
        SettingsTheme.rect(ctx, 0, 0, vw, vh,
            com.eymistaken.simplecps.util.RenderUtil.withAlpha(SettingsTheme.MODAL_SCRIM, progress));

        ctx.pose().pushMatrix();
        ctx.pose().translate(0f, (1f - progress) * 6f);

        int x = modalX();
        int y = modalY();
        int w = modalW();
        int h = modalH();
        SettingsTheme.raised(ctx, x, y, w, h, SettingsTheme.MODAL_BG,
            SettingsTheme.PANEL_LIGHT, SettingsTheme.PANEL_DARK);

        int headH = modalHeadH();
        SettingsTheme.rect(ctx, x + 1, y + 1, w - 2, headH - 1, SettingsTheme.DANGER_BG);
        SettingsTheme.rect(ctx, x + 1, y + headH, w - 2, 1, SettingsTheme.BORDER);
        SettingsTheme.text(ctx, this.font, dialogTitle(), x + pad,
            centerTextY(y, headH), SettingsTheme.MODAL_HEAD_FG);

        int ty = y + headH + gap * 2;
        for (String line : SettingsTheme.wrap(this.font, dialogBody(), w - pad * 2)) {
            SettingsTheme.text(ctx, this.font, line, x + pad, ty, SettingsTheme.MODAL_BODY_FG);
            ty += this.font.lineHeight + 1;
        }
        ty += gap;
        SettingsTheme.text(ctx, this.font, "THIS CANNOT BE UNDONE.", x + pad, ty, SettingsTheme.TEXT_DIM);

        int by = modalButtonY();
        int bw = modalButtonW();
        int bh = modalButtonH();
        boolean keepHovered = SettingsTheme.inside(mouseX, mouseY, modalCancelX(), by, bw, bh);
        SettingsTheme.button(ctx, modalCancelX(), by, bw, bh, keepHovered);
        SettingsTheme.centeredText(ctx, this.font,
            dialog == Dialog.DISCARD ? "KEEP EDITING" : "CANCEL",
            modalCancelX() + bw / 2, centerTextY(by, bh), SettingsTheme.TEXT);

        boolean confirmHovered = SettingsTheme.inside(mouseX, mouseY, modalConfirmX(), by, bw, bh);
        SettingsTheme.raised(ctx, modalConfirmX(), by, bw, bh,
            confirmHovered ? SettingsTheme.CONFIRM_HOVER : SettingsTheme.CONFIRM_BG,
            SettingsTheme.CONFIRM_LIGHT, SettingsTheme.CONFIRM_DARK);
        SettingsTheme.centeredText(ctx, this.font, dialogConfirmLabel(), modalConfirmX() + bw / 2,
            centerTextY(by, bh), SettingsTheme.CONFIRM_TEXT);

        ctx.pose().popMatrix();
    }

    // --- Scroll helpers ------------------------------------------------------------

    private static int clampScroll(int scroll, int contentH, int viewH) {
        return Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
    }

    /** Thumb length for a bar. Shared so drawing and dragging cannot disagree. */
    private int scrollThumbH(int viewH, int contentH) {
        return Math.max(6, viewH * viewH / contentH);
    }

    /**
     * Scroll offset that puts the thumb's center under {@code mouseY}.
     *
     * @param barY top of the bar, in the same space as {@code mouseY}
     */
    private int scrollFromThumbY(double mouseY, int barY, int viewH, int contentH) {
        int thumbH = scrollThumbH(viewH, contentH);
        int travel = viewH - thumbH;
        if (travel <= 0) return 0;
        float fraction = (float) ((mouseY - barY - thumbH / 2.0) / travel);
        return Math.round(Easings.clamp01(fraction) * (contentH - viewH));
    }

    private void renderScrollbar(GuiGraphicsExtractor ctx, int x, int y, int viewH, int scroll, int contentH) {
        if (contentH <= viewH || viewH <= 0) return;
        SettingsTheme.rect(ctx, x, y, scrollbarW, viewH, SettingsTheme.SCROLL_TRACK);
        int thumbH = scrollThumbH(viewH, contentH);
        int travel = viewH - thumbH;
        int thumbY = y + Math.round(travel * (scroll / (float) (contentH - viewH)));
        SettingsTheme.rect(ctx, x, thumbY, scrollbarW, thumbH, SettingsTheme.SCROLL_THUMB);
    }

    /**
     * Claim the cursor for a scrollbar if the press landed on one.
     *
     * <p>{@code barScreenY} is a supplier rather than a value because the preset
     * bar lives inside the right panel's own scrolled region — its on-screen
     * position moves if that panel scrolls mid-drag.
     *
     * @return true if the bar took the press
     */
    private boolean tryScrollbarDrag(double mouseX, double mouseY, int barX,
                                     java.util.function.IntSupplier barScreenY,
                                     int viewH, int contentH,
                                     java.util.function.IntConsumer setScroll) {
        if (contentH <= viewH || viewH <= 0) return false;
        int barY = barScreenY.getAsInt();
        if (!SettingsTheme.inside(mouseX, mouseY, barX, barY, scrollbarW, viewH)) return false;

        commitFocus();
        dragHandler = (mx, my) ->
            setScroll.accept(scrollFromThumbY(my, barScreenY.getAsInt(), viewH, contentH));
        dragHandler.apply(mouseX, mouseY);
        return true;
    }

    /** Every scrollbar currently on screen, innermost first. */
    private boolean handleScrollbarPress(double mouseX, double mouseY) {
        // Preset list: nested inside the right panel, so it is tested first.
        if ((!rightCollapsed || drawerOpen) && !presetNames.isEmpty()) {
            int listH = presetListH();
            int panelX = rightPanelX();
            if (tryScrollbarDrag(mouseX, mouseY,
                panelX + rightW - gap * 2 - scrollbarW,
                () -> presetListTop() - rightScroll,
                listH, presetContentH(), v -> presetScroll = v)) {
                return true;
            }
        }

        if (!rightCollapsed || drawerOpen) {
            int panelX = rightPanelX();
            if (tryScrollbarDrag(mouseX, mouseY, panelX + rightW - scrollbarW - 1,
                () -> bodyY, bodyH, rightContentHeight(rightW), v -> rightScroll = v)) {
                return true;
            }
        }

        if (tryScrollbarDrag(mouseX, mouseY, sidebarX + sidebarW - scrollbarW - 1,
            this::sidebarListY, sidebarListH(), sidebarContentHeight(), v -> sidebarScroll = v)) {
            return true;
        }

        return tryScrollbarDrag(mouseX, mouseY, contentX + contentW - scrollbarW - 1,
            this::contentListY, contentListH(), rowsContentH, v -> contentScroll = v);
    }

    @Override
    public boolean mouseScrolled(double screenX, double screenY, double horizontalAmount, double verticalAmount) {
        if (dialog != Dialog.NONE) return true;
        int step = (int) Math.round(verticalAmount * 12);
        if (step == 0) return false;

        double mouseX = screenX / renderScale;
        double mouseY = screenY / renderScale;

        // Nothing inside the focus overlay scrolls, but the lists behind it must not
        // either — the wheel would move a column the cursor is not really over.
        if (previewFocused) {
            int[] r = previewFocusRect();
            if (r != null && SettingsTheme.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) return true;
        }

        if (mouseX >= sidebarX && mouseX < sidebarX + sidebarW
            && mouseY >= sidebarListY() && mouseY < sidebarListY() + sidebarListH()) {
            sidebarScroll = clampScroll(sidebarScroll - step, sidebarContentHeight(), sidebarListH());
            return true;
        }
        int rp = rightPanelX();
        if ((!rightCollapsed || drawerOpen)
            && mouseX >= rp && mouseX < rp + rightW && mouseY >= bodyY && mouseY < bodyY + bodyH) {
            // The preset list is a viewport inside a viewport; when the cursor is
            // over it, the wheel belongs to the inner one.
            int listTop = presetListTop() - rightScroll;
            int listH = presetListH();
            if (!presetNames.isEmpty() && presetContentH() > listH
                && mouseY >= listTop && mouseY < listTop + listH) {
                presetScroll = clampScroll(presetScroll - step, presetContentH(), listH);
                return true;
            }
            rightScroll = clampScroll(rightScroll - step, rightContentHeight(rightW), bodyH);
            return true;
        }
        if (mouseX >= contentX && mouseX < contentX + contentW
            && mouseY >= contentListY() && mouseY < contentListY() + contentListH()) {
            contentScroll = clampScroll(contentScroll - step, rowsContentH, contentListH());
            return true;
        }
        return false;
    }

    // --- Input ------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x() / renderScale;
        double mouseY = click.y() / renderScale;
        int button = click.button();

        if (dialog != Dialog.NONE) {
            handleDialogClick(mouseX, mouseY, button);
            return true;
        }
        if (button != 0 && button != 1) return super.mouseClicked(click, doubled);

        Focus previousFocus = focus;
        boolean consumed = routeClick(mouseX, mouseY, button);

        // Clicking anywhere that is not a text field commits whatever was being edited.
        if (focus == previousFocus && focus != Focus.NONE && !consumedByFocusedField(mouseX, mouseY)) {
            commitFocus();
        }
        return consumed || super.mouseClicked(click, doubled);
    }

    /** True when the click landed on the field that currently has focus. */
    private boolean consumedByFocusedField(double mouseX, double mouseY) {
        return switch (focus) {
            case SEARCH -> SettingsTheme.inside(mouseX, mouseY,
                headerSearchX(), headerControlY(), headerSearchW(), ctrlH);
            case SHARE, ROW_TEXT, ROW_NUMBER, ROW_HEX -> true; // handled where the click was routed
            case NONE -> false;
        };
    }

    private boolean routeClick(double mouseX, double mouseY, int button) {
        int headerY = headerControlY();

        // Focus mode is drawn over both columns, so it gets the click before anything
        // it happens to be covering.
        if (previewFocused && handlePreviewFocusClick(mouseX, mouseY)) return true;

        // Header
        if (SettingsTheme.inside(mouseX, mouseY, headerSearchX(), headerY, headerSearchW(), ctrlH)) {
            focusSearch(mouseX);
            return true;
        }
        if (SettingsTheme.inside(mouseX, mouseY, headerCloseX(), headerY, headerCloseW(), ctrlH)) {
            saveAndClose();
            return true;
        }
        if (rightCollapsed
            && SettingsTheme.inside(mouseX, mouseY, headerDrawerX(), headerY, headerCloseW(), ctrlH)) {
            drawerOpen = !drawerOpen;
            return true;
        }

        // Footer
        int bh = footerButtonH();
        int by = footerY + (footerH - bh) / 2;
        if (SettingsTheme.inside(mouseX, mouseY, cancelButtonX(), by, buttonWidth("CANCEL"), bh)) {
            commitFocus();
            if (hasChangesToDiscard()) {
                openDialog(Dialog.DISCARD);
            } else {
                close(); // nothing to undo, so nothing to confirm
            }
            return true;
        }
        if (SettingsTheme.inside(mouseX, mouseY, doneButtonX(), by, buttonWidth("DONE"), bh)) {
            saveAndClose();
            return true;
        }

        // Scrollbars sit on top of their lists, so they get first refusal.
        if (button == 0 && handleScrollbarPress(mouseX, mouseY)) return true;

        // Right panel first: when collapsed it is drawn over the content column.
        if ((!rightCollapsed || drawerOpen) && handleRightPanelClick(mouseX, mouseY)) return true;

        if (handleSidebarClick(mouseX, mouseY)) return true;

        // Enable switch in the content header
        Page page = activePage();
        if (!searching() && page != null && page.canToggle()
            && SettingsTheme.inside(mouseX, mouseY, enableButtonX(page), enableButtonY(),
                enableButtonW(page), badgeH)) {
            commitFocus();
            togglePage(page);
            return true;
        }

        if (handleTabClick(mouseX, mouseY)) return true;
        return handleContentClick(mouseX, mouseY, button);
    }

    private void focusSearch(double mouseX) {
        commitFocus();
        focus = Focus.SEARCH;
        focusedRow = null;
        searchInput.onClick(this.font, mouseX, headerSearchTextX(), false);
    }

    private boolean handleSidebarClick(double mouseX, double mouseY) {
        int listY = sidebarListY();
        int listH = sidebarListH();

        // Editor button
        int fy = bodyY + bodyH - sideFootH;
        int ebH = Math.min(sideFootH - gap * 2, d(40, 11));
        if (SettingsTheme.inside(mouseX, mouseY, sidebarX + gap, fy + (sideFootH - ebH) / 2,
            sidebarW - gap * 2, ebH)) {
            openHudEditor();
            return true;
        }

        if (!SettingsTheme.inside(mouseX, mouseY, sidebarX, listY, sidebarW, listH)) return false;

        int virtualY = (int) mouseY + sidebarScroll;
        int y = listY + gap;
        for (Page p : builtInPages) {
            if (virtualY >= y && virtualY < y + moduleRowH) {
                selectPage(p, mouseX, y);
                return true;
            }
            y += moduleRowH + 1;
        }

        y += gap * 2 + this.font.lineHeight + gap;
        if (pluginPages.isEmpty()) {
            y += SettingsTheme.wrap(this.font, NO_PLUGINS, sidebarW - gap * 4).size()
                * (this.font.lineHeight + 1) + gap * 2;
        }
        for (Page p : pluginPages) {
            if (virtualY >= y && virtualY < y + moduleRowH) {
                selectPage(p, mouseX, y);
                return true;
            }
            y += moduleRowH + 1;
        }
        return true; // swallow clicks on the list background
    }

    /** Flip a module on or off and start its highlight. */
    private void togglePage(Page p) {
        if (!p.canToggle()) return;
        p.toggle();
        markDirty();
        flashState.put(p.id, p.isEnabled());
        flashStart.put(p.id, System.currentTimeMillis());
    }

    private void selectPage(Page p, double mouseX, int rowY) {
        // The indicator box on the left toggles; the rest of the row selects.
        if (mouseX >= moduleIndicatorX() && mouseX < moduleIndicatorX() + indicatorSize && p.canToggle()) {
            togglePage(p);
            return;
        }
        commitFocus();
        selectedPageId = p.id;
        contentScroll = 0;
        openColorRowId = null;
        infoRow = null;
        searchInput.setValue("");
        resolvePreviewModule();
    }

    /**
     * Bind the preview to the selected page. Done here rather than per frame because the
     * lookup walks the module registry, and because leaving focus mode on across a page
     * change would blow up a module the player did not ask to see.
     */
    private void resolvePreviewModule() {
        previewModule = SettingsSchema.moduleForPage(selectedPageId);
        previewFocused = false;
        refreshPreview();
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        if (searching()) return false;
        Page page = activePage();
        if (page == null) return false;
        int y = bodyY + contentHeadH;
        if (!SettingsTheme.inside(mouseX, mouseY, contentX, y, contentW, tabH)) return false;

        int x = contentX + pad;
        for (Tab tab : page.tabs) {
            int w = tabWidth(tab);
            if (x + w > contentX + contentW - pad) break;
            if (mouseX >= x && mouseX < x + w) {
                commitFocus();
                selectedTab.put(page.id, tab.id());
                contentScroll = 0;
                openColorRowId = null;
                return true;
            }
            x += w + 1;
        }
        return true;
    }

    private boolean handleContentClick(double mouseX, double mouseY, int button) {
        int listY = contentListY();
        int listH = contentListH();
        if (!SettingsTheme.inside(mouseX, mouseY, contentX, listY, contentW, listH)) return false;

        int virtualY = (int) mouseY + contentScroll;
        for (PlacedRow placed : placedRows) {
            if (virtualY < placed.y() || virtualY >= placed.y() + placed.h()) continue;
            handleRowClick(placed, mouseX, virtualY, button);
            return true;
        }
        return true;
    }

    private void handleRowClick(PlacedRow placed, double mouseX, int virtualY, int button) {
        Row row = placed.row();
        int y = placed.y();
        int cy = y + (rowH - ctrlH) / 2;
        int controlW = controlWidth(row);
        int cx = controlRight() - controlW;

        // Reset button
        if (!(row instanceof ActionRow)
            && SettingsTheme.inside(mouseX, virtualY, resetX(), cy, resetW, ctrlH)) {
            if (row.isModified()) {
                row.reset();
                markDirty();
                syncFocusedInput(row);
                if (row instanceof ColorRow c && c.id.equals(openColorRowId)) syncPickerFrom(c);
            }
            return;
        }

        boolean inMainRow = virtualY >= y && virtualY < y + rowH;

        if (inMainRow) {
            switch (row) {
                case BoolRow r -> {
                    if (SettingsTheme.inside(mouseX, virtualY, cx, cy, toggleW, ctrlH)) {
                        commitFocus();
                        r.toggle();
                        markDirty();
                    }
                    return;
                }
                case IntRow r -> {
                    if (r.style == IntRow.Style.SLIDER) {
                        int trackW = sliderTrackWidth();
                        if (SettingsTheme.inside(mouseX, virtualY, cx, cy, trackW, ctrlH)) {
                            commitFocus();
                            startSliderDrag(r, cx, trackW);
                            dragHandler.apply(mouseX, virtualY);
                        }
                    } else {
                        handleStepperClick(r, cx, cy, mouseX, virtualY);
                    }
                    return;
                }
                case ChoiceRow r -> {
                    int valueW = choiceValueWidth();
                    if (SettingsTheme.inside(mouseX, virtualY, cx, cy, choiceBtn, ctrlH)) {
                        commitFocus();
                        r.cycle(-1);
                        markDirty();
                    } else if (SettingsTheme.inside(mouseX, virtualY,
                        cx + choiceBtn + gap + valueW + gap, cy, choiceBtn, ctrlH)) {
                        commitFocus();
                        r.cycle(1);
                        markDirty();
                    }
                    return;
                }
                case TextRow r -> {
                    int w = textFieldWidth();
                    if (SettingsTheme.inside(mouseX, virtualY, cx, cy, w, ctrlH)) {
                        if (focus != Focus.ROW_TEXT || focusedRow != r) {
                            commitFocus();
                            focus = Focus.ROW_TEXT;
                            focusedRow = r;
                            rowInput.setFilter(TextInput.Filter.ANY);
                            rowInput.setMaxLength(64);
                            rowInput.setValue(r.get());
                        }
                        rowInput.onClick(this.font, mouseX, cx + gap, false);
                    }
                    return;
                }
                case ColorRow r -> {
                    if (SettingsTheme.inside(mouseX, virtualY, cx, cy, colorW, ctrlH)) {
                        commitFocus();
                        if (r.id.equals(openColorRowId)) {
                            openColorRowId = null;
                        } else {
                            openColorRowId = r.id;
                            syncPickerFrom(r);
                        }
                    }
                    return;
                }
                case ActionRow r -> {
                    if (SettingsTheme.inside(mouseX, virtualY, cx, cy, actionWidth(r), ctrlH)) {
                        commitFocus();
                        r.run();
                        markDirty();
                    }
                    return;
                }
                case PositionRow ignored -> { return; }
            }
        }

        // Expanded areas
        if (row instanceof PositionRow r) {
            handlePositionClick(r, rowX() + gap, y + rowH, mouseX, virtualY);
        } else if (row instanceof ColorRow r && r.id.equals(openColorRowId)) {
            handleColorEditorClick(r, rowX() + gap, y + rowH, rowW() - gap * 2, button, mouseX, virtualY);
        }
    }

    private void handleStepperClick(IntRow row, int cx, int cy, double mouseX, int virtualY) {
        int fieldW = stepFieldWidth();
        if (SettingsTheme.inside(mouseX, virtualY, cx, cy, stepBtn, ctrlH)) {
            commitFocus();
            row.set(row.get() - 1);
            markDirty();
            return;
        }
        int px = cx + stepBtn + gap + fieldW + gap;
        if (SettingsTheme.inside(mouseX, virtualY, px, cy, stepBtn, ctrlH)) {
            commitFocus();
            row.set(row.get() + 1);
            markDirty();
            return;
        }
        int fx = cx + stepBtn + gap;
        if (SettingsTheme.inside(mouseX, virtualY, fx, cy, fieldW, ctrlH)) {
            if (focus != Focus.ROW_NUMBER || focusedRow != row) {
                commitFocus();
                focus = Focus.ROW_NUMBER;
                focusedRow = row;
                rowInput.setFilter(TextInput.Filter.INTEGER);
                rowInput.setMaxLength(6);
                rowInput.setValue(String.valueOf(row.get()));
                rowInput.selectAll();
            }
            rowInput.onClick(this.font, mouseX, fx + gap, false);
        }
    }

    private void startSliderDrag(IntRow row, int trackX, int trackW) {
        dragHandler = (mouseX, mouseY) -> {
            float fraction = (float) ((mouseX - trackX - 1) / Math.max(1, trackW - 2));
            row.setFromFraction(Easings.clamp01(fraction));
            markDirty();
        };
    }

    private void handlePositionClick(PositionRow row, int x, int y, double mouseX, int virtualY) {
        for (int i = 0; i < POS_CELLS.length; i++) {
            SimpleCPSConfig.Position cell = POS_CELLS[i];
            if (cell == null) continue;
            int cxi = x + 2 + (i % 3) * (posCellW + posGap);
            int cyi = y + 2 + (i / 3) * (posCellH + posGap);
            if (SettingsTheme.inside(mouseX, virtualY, cxi, cyi, posCellW, posCellH)) {
                commitFocus();
                row.set(cell);
                markDirty();
                return;
            }
        }
    }

    /** Fade of the copy confirmation for {@code row}, or 0 when it is not showing. */
    private float hexToastProgress(ColorRow row) {
        if (!row.id.equals(hexCopiedRowId)) return 0f;
        long age = System.currentTimeMillis() - hexCopiedAt;
        if (age >= HEX_TOAST_MILLIS) {
            hexCopiedRowId = null;
            return 0f;
        }
        // Hold, then fade over the last third.
        float t = age / (float) HEX_TOAST_MILLIS;
        return t < 0.66f ? 1f : Easings.clamp01((1f - t) / 0.34f);
    }

    private void copyHex(ColorRow row) {
        if (this.minecraft == null) return;
        this.minecraft.keyboardHandler.setClipboard(row.hex());
        hexCopiedRowId = row.id;
        hexCopiedAt = System.currentTimeMillis();
    }

    private void handleColorEditorClick(ColorRow row, int x, int y, int w, int button, double mouseX, int virtualY) {
        int bx = svBoxX(x);
        int by = svBoxY(y);
        int bw = svBoxW(w);
        int bh = svBoxH();

        // Saturation / value square
        if (SettingsTheme.inside(mouseX, virtualY, bx, by, bw, bh)) {
            commitFocus();
            dragHandler = (mx, my) -> {
                // my arrives in screen space; the square lives in the scrolled list.
                double vy = my + contentScroll;
                pickerSat = Easings.clamp01((float) ((mx - bx) / (double) Math.max(1, bw - 1)));
                pickerVal = 1f - Easings.clamp01((float) ((vy - by) / (double) Math.max(1, bh - 1)));
                applyPicker(row);
            };
            dragHandler.apply(mouseX, virtualY - contentScroll);
            return;
        }

        // Hue bar
        int hby = hueBarY(y);
        if (SettingsTheme.inside(mouseX, virtualY, bx, hby - 1, bw, hueBarH() + 2)) {
            commitFocus();
            dragHandler = (mx, my) -> {
                pickerHue = Easings.clamp01((float) ((mx - bx) / (double) Math.max(1, bw - 1)));
                applyPicker(row);
            };
            dragHandler.apply(mouseX, virtualY - contentScroll);
            return;
        }

        int hy = colorHexY(y);
        int hexX = colorHexX(x);
        int hexW = colorHexW();
        if (SettingsTheme.inside(mouseX, virtualY, hexX, hy, hexW, ctrlH)) {
            // Left click copies — that is what you nearly always want a hex code
            // for. Right click drops into the field to type or paste one.
            if (button == 0) {
                commitFocus();
                copyHex(row);
                return;
            }
            if (focus != Focus.ROW_HEX || focusedRow != row) {
                commitFocus();
                focus = Focus.ROW_HEX;
                focusedRow = row;
                rowInput.setFilter(TextInput.Filter.HEX);
                rowInput.setMaxLength(7);
                rowInput.setValue(row.hex());
                rowInput.selectAll();
            }
            rowInput.onClick(this.font, mouseX, hexX + gap, false);
            return;
        }

        int swatchesW = swatchStripWidth();
        int sx = x + w - gap - swatchesW;
        if (sx > hexX + hexW + gap) {
            int sy = hy + (ctrlH - swatch) / 2;
            for (int i = 0; i < SettingsTheme.SWATCHES.length; i++) {
                int cxi = sx + i * (swatch + 1);
                if (SettingsTheme.inside(mouseX, virtualY, cxi, sy, swatch, swatch)) {
                    commitFocus();
                    row.set(SettingsTheme.SWATCHES[i]);
                    syncPickerFrom(row);
                    markDirty();
                    return;
                }
            }
        }
    }

    private boolean handleRightPanelClick(double mouseX, double mouseY) {
        int x = rightPanelX();
        int w = rightW;
        if (!SettingsTheme.inside(mouseX, mouseY, x, bodyY, w, bodyH)) return false;

        int virtualY = (int) mouseY + rightScroll;
        // Must match renderRightPanel exactly: every card height below is derived
        // from cw, so a different padding here would shift all of them.
        int cw = w - gap * 2;
        int cx = x + gap;

        int y = bodyY + gap;

        // Preview: only the FOCUS button is live, so bound X as well as Y — the rest of
        // the card is inert and must not swallow the press as a button hit.
        int previewH = previewCardHeight(cw);
        if (previewH > 0) {
            int size = previewButtonSize();
            if (SettingsTheme.inside(mouseX, virtualY,
                previewButtonX(cx, cw), previewButtonY(y), size, size)) {
                previewFocused = !previewFocused;
                return true;
            }
            y += previewH + gap;
        }

        y += infoCardHeight(cw) + gap;

        // Presets
        int presetTop = y;
        int listY = presetTop + cardHeadH + gap;
        int listH = presetListH();
        boolean overflows = presetContentH() > listH;
        int listW = cw - gap * 2 - (overflows ? scrollbarW + 1 : 0);
        // The X bound matters: without it a press anywhere in this band counted as a
        // row, and since the scrollbar sits right of the trash threshold, dragging
        // the bar deleted presets.
        if (!presetNames.isEmpty()
            && virtualY >= listY && virtualY < listY + listH
            && mouseX >= cx + gap && mouseX < cx + gap + listW) {
            // Rows scroll inside the viewport, so undo that offset to find the index.
            int index = (virtualY + presetScroll - listY) / presetRowPitch();
            if (index >= 0 && index < presetNames.size()) {
                String name = presetNames.get(index);
                if (mouseX >= cx + gap + listW - presetTrashW()) {
                    deletePreset(name);
                } else {
                    applyPreset(name);
                }
            }
            return true;
        }

        int saveY = listY + listH + gap;
        if (virtualY >= saveY && virtualY < saveY + presetRowH) {
            saveCurrentPreset();
            return true;
        }

        // Share
        y = presetTop + presetCardHeight(cw) + gap;
        int fy = y + cardHeadH + gap;
        if (virtualY >= fy && virtualY < fy + ctrlH) {
            if (focus != Focus.SHARE) {
                commitFocus();
                focus = Focus.SHARE;
                focusedRow = null;
            }
            shareInput.onClick(this.font, mouseX, cx + gap * 2, false);
            return true;
        }
        int sby = fy + ctrlH + gap;
        int halfW = (cw - gap * 3) / 2;
        if (virtualY >= sby && virtualY < sby + ctrlH) {
            if (mouseX < cx + gap + halfW + gap / 2) {
                exportShareCode();
            } else {
                importShareCode();
            }
            return true;
        }

        // Reset all
        y = y + shareCardHeight(cw) + gap;
        if (virtualY >= y && virtualY < y + dangerButtonH()) {
            openDialog(Dialog.RESET_ALL);
            return true;
        }
        return true;
    }

    private void handleDialogClick(double mouseX, double mouseY, int button) {
        if (button != 0) return;
        int by = modalButtonY();
        int bw = modalButtonW();
        int bh = modalButtonH();
        if (SettingsTheme.inside(mouseX, mouseY, modalCancelX(), by, bw, bh)) {
            dialog = Dialog.NONE;
            return;
        }
        if (SettingsTheme.inside(mouseX, mouseY, modalConfirmX(), by, bw, bh)) {
            confirmDialog();
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (dragHandler != null) {
            dragHandler.apply(click.x() / renderScale, click.y() / renderScale);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (dragHandler != null) {
            dragHandler = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    // --- Keyboard ---------------------------------------------------------------------

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (dialog != Dialog.NONE) return true;
        if (focus == Focus.NONE) return super.charTyped(event);

        TextInput input = activeInput();
        if (input == null) return super.charTyped(event);
        if (input.charTyped(event)) {
            applyLiveEdit();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (dialog != Dialog.NONE) {
            if (event.isEscape()) {
                dialog = Dialog.NONE;
            } else if (event.isConfirmation()) {
                confirmDialog();
            }
            return true;
        }

        if (focus != Focus.NONE) {
            TextInput input = activeInput();
            if (input != null && input.keyPressed(event)) {
                applyLiveEdit();
                return true;
            }
            if (event.isConfirmation() || event.isEscape()) {
                commitFocus();
                return true;
            }
        }

        // After the field handling above, so ESC finishes an edit first, and before
        // super, which would take it as a request to close the screen.
        if (previewFocused && event.isEscape()) {
            previewFocused = false;
            return true;
        }

        return super.keyPressed(event);
    }

    private TextInput activeInput() {
        return switch (focus) {
            case SEARCH -> searchInput;
            case SHARE -> shareInput;
            case ROW_TEXT, ROW_NUMBER, ROW_HEX -> rowInput;
            case NONE -> null;
        };
    }

    /** Push the in-progress text into the config as it is typed, like the mock does. */
    private void applyLiveEdit() {
        switch (focus) {
            case SEARCH -> {
                contentScroll = 0;
                openColorRowId = null;
            }
            case ROW_TEXT -> {
                if (focusedRow instanceof TextRow r) {
                    r.set(rowInput.getValue());
                    markDirty();
                }
            }
            case ROW_NUMBER -> {
                if (focusedRow instanceof IntRow r) {
                    String raw = rowInput.getValue();
                    // "" and "-" are valid mid-edit states; leave the value alone until
                    // there is a number to apply.
                    if (!raw.isEmpty() && !raw.equals("-")) {
                        try {
                            r.set(Integer.parseInt(raw));
                            markDirty();
                        } catch (NumberFormatException ignored) {
                            // Out of int range; the filter already caps the length.
                        }
                    }
                }
            }
            case ROW_HEX -> {
                if (focusedRow instanceof ColorRow r) {
                    String raw = rowInput.getValue().replace("#", "");
                    if (raw.length() == 6) {
                        try {
                            r.set(Integer.parseInt(raw, 16));
                            if (r.id.equals(openColorRowId)) syncPickerFrom(r);
                            markDirty();
                        } catch (NumberFormatException ignored) {
                            // Unreachable: the HEX filter only admits hex digits.
                        }
                    }
                }
            }
            case SHARE, NONE -> { }
        }
    }

    /** Finish editing: normalize whatever half-typed value is in the field. */
    private void commitFocus() {
        switch (focus) {
            case ROW_NUMBER -> {
                if (focusedRow instanceof IntRow r) {
                    String raw = rowInput.getValue();
                    if (raw.isEmpty() || raw.equals("-")) {
                        r.set(r.get()); // re-clamp; the field goes back to showing it
                    }
                }
            }
            case ROW_HEX -> {
                if (focusedRow instanceof ColorRow r) {
                    String raw = rowInput.getValue().replace("#", "");
                    if (raw.length() != 6) {
                        r.set(r.get());
                    }
                }
            }
            default -> { }
        }
        clearFocusState();
    }

    private void clearFocusState() {
        focus = Focus.NONE;
        focusedRow = null;
        rowInput.setValue("");
    }

    /** Keep an open field in step with a value that changed underneath it. */
    private void syncFocusedInput(Row row) {
        if (focusedRow != row) return;
        switch (focus) {
            case ROW_TEXT -> { if (row instanceof TextRow r) rowInput.setValue(r.get()); }
            case ROW_NUMBER -> { if (row instanceof IntRow r) rowInput.setValue(String.valueOf(r.get())); }
            case ROW_HEX -> { if (row instanceof ColorRow r) rowInput.setValue(r.hex()); }
            default -> { }
        }
    }

    // --- Presets and share codes -------------------------------------------------------

    private void applyPreset(String name) {
        commitFocus();
        // The live config is about to be replaced; land any queued write first.
        flushPendingSave();
        if (ConfigPresetManager.applyPreset(name)) {
            onConfigReplaced();
            setShareMessage("LOADED PRESET \"" + SettingsTheme.up(name) + "\".", true);
        } else {
            setShareMessage("COULD NOT LOAD THAT PRESET.", false);
        }
    }

    private void deletePreset(String name) {
        commitFocus();
        if (ConfigPresetManager.deletePreset(name)) {
            presetNames = ConfigPresetManager.listPresets();
            presetScroll = clampScroll(presetScroll, presetContentH(), presetListH());
            setShareMessage("DELETED PRESET \"" + SettingsTheme.up(name) + "\".", true);
        }
    }

    private void saveCurrentPreset() {
        commitFocus();
        flushPendingSave();
        String name = ConfigPresetManager.uniqueDefaultName();
        if (ConfigPresetManager.savePreset(name)) {
            presetNames = ConfigPresetManager.listPresets();
            setShareMessage("SAVED AS \"" + SettingsTheme.up(name) + "\".", true);
        } else {
            setShareMessage("COULD NOT SAVE THE PRESET.", false);
        }
    }

    private void exportShareCode() {
        commitFocus();
        flushPendingSave();
        HudActions.Result result = HudActions.exportShareCode();
        if (result.ok()) {
            shareInput.setValue(com.eymistaken.simplecps.util.HudShareCodec.encodeConfig());
        }
        setShareMessage(SettingsTheme.up(result.message()), result.ok());
    }

    private void importShareCode() {
        commitFocus();
        String typed = shareInput.getValue().trim();
        // Fall back to the clipboard when the field is empty, so the button works
        // the same way the editor's "Import Code" always has.
        HudActions.Result result = typed.isEmpty()
            ? HudActions.importShareCode()
            : HudActions.importShareCode(typed);
        if (result.ok()) {
            onConfigReplaced();
            shareInput.setValue("");
        }
        setShareMessage(SettingsTheme.up(result.message()), result.ok());
    }

    private void setShareMessage(String message, boolean ok) {
        shareMessage = message;
        shareOk = ok;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
