package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.ConfigPresetManager;
import com.eymistaken.simplecps.HudModuleManager;
import com.eymistaken.simplecps.HudPlacementResolver;
import com.eymistaken.simplecps.SimpleCPSClient;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.ServerConfigManager;
import com.eymistaken.simplecps.util.Anim;
import com.eymistaken.simplecps.util.Easings;
import com.eymistaken.simplecps.util.EymHudFonts;
import com.eymistaken.simplecps.util.RenderUtil;
import org.lwjgl.glfw.GLFW;

import java.awt.Point;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class HudEditorScreen extends Screen {

    private final Screen parent;
    private com.eymistaken.simplecps.api.IHudElement draggingElement = null;
    
    // Local click offset within the module (0,0 is top-left of module)
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private boolean contextMenuOpen = false;
    private int contextMenuX, contextMenuY;
    private com.eymistaken.simplecps.api.IHudElement contextMenuTarget = null;

    private boolean globalMenuOpen = false;
    private int globalMenuX, globalMenuY;

    // Global menu layout (title row + N action rows, each 20px tall)
    private static final int GLOBAL_MENU_WIDTH = 180;
    private static final int CONTEXT_MENU_WIDTH = 220;

    /**
     * Gap kept between a menu and the screen edge. Every menu paints a 1px border
     * one pixel <em>outside</em> its own rect, so clamping to 0 still clips it.
     */
    private static final int MENU_MARGIN = 2;
    private static final int GLOBAL_ROW_PREVENT_OVERLAP = 0;
    private static final int GLOBAL_ROW_GRID = 1;
    private static final int GLOBAL_ROW_SAVE_CONFIG = 2;
    private static final int GLOBAL_ROW_CONFIGS = 3;
    private static final int GLOBAL_ROW_SERVER_CONFIGS = 4;
    private static final int GLOBAL_ROW_EXPORT_CODE = 5;
    private static final int GLOBAL_ROW_IMPORT_CODE = 6;
    private static final int GLOBAL_ROW_RESET_HUD = 7;
    private static final int GLOBAL_ROW_COUNT = 8;

    /** Grid steps the editor's Grid row cycles through on right-click. */
    private static final int[] GRID_SIZES = { 4, 8, 16, 32 };

    // Transient feedback for share-code actions (shown in the global menu).
    private String shareStatus = "";
    private long shareStatusUntil = 0;

    // Font restore button (bottom-right, only shown while the enchant font is active)
    private static final int RESTORE_BTN_W = 96;
    private static final int RESTORE_BTN_H = 16;

    // Align / distribute toolbar, shown at the top while 2+ elements are selected.
    private static final int ALIGN_BTN_W = 26;
    private static final int ALIGN_BTN_H = 18;
    private static final int ALIGN_BTN_GAP = 2;
    private static final int ALIGN_TOOLBAR_Y = 24;
    private static final String[] ALIGN_LABELS = { "L", "CX", "R", "T", "CY", "B", "DX", "DY" };
    private static final String[] ALIGN_TOOLTIPS = {
        "Align Left", "Same Center X", "Align Right",
        "Align Top", "Same Center Y", "Align Bottom",
        "Distribute Horizontally", "Distribute Vertically"
    };
    // The last two need a third element to have anything to space out.
    private static final int FIRST_DISTRIBUTE_ACTION = 6;

    // Configs fly-out submenu (hover on the "Configs" row)
    private static final int SUBMENU_WIDTH = 170;
    private boolean configsSubmenuOpen = false;
    private int configsSubmenuX, configsSubmenuY;
    private java.util.List<String> cachedPresetNames = new java.util.ArrayList<>();

    // Server Configs fly-out (hover on the "Server Configs" row). Kept separate from
    // the Configs fly-out above so both keep their own position and animation.
    private boolean serverSubmenuOpen = false;
    private int serverSubmenuX, serverSubmenuY;
    private java.util.List<String> cachedServerKeys = new java.util.ArrayList<>();

    // Menu open animations (fade + slide). Each is snapped to 0 when its menu
    // opens and eased to 1 while it renders.
    private final Anim globalMenuAnim = new Anim(0f);
    private final Anim contextMenuAnim = new Anim(0f);
    private final Anim submenuAnim = new Anim(0f);
    private final Anim serverSubmenuAnim = new Anim(0f);
    private static final float MENU_ANIM_DURATION = 0.16f;
    /** Below this alpha a closing menu is invisible anyway, so stop drawing it. */
    private static final float FADE_OUT_CUTOFF = 0.01f;

    /**
     * The context menu's target, held past the moment it closes. Without it a menu
     * whose target is cleared on close (or a config swap) would change shape or vanish
     * halfway through its own fade-out.
     */
    private com.eymistaken.simplecps.api.IHudElement contextMenuRenderTarget = null;

    /** Scale an ARGB colour's alpha by menu-open progress {@code p}. */
    private static int fade(int argb, float p) {
        return RenderUtil.withAlpha(argb, p);
    }

    // Generic text prompt overlay (used for renaming a config)
    private boolean textPromptActive = false;
    private String textPromptBuffer = "";
    private String textPromptLabel = "";
    private java.util.function.Consumer<String> textPromptCallback = null;

    // For text editing (Stage 4 prep)
    private com.eymistaken.simplecps.api.TextSetting textEditTarget = null;
    private String textEditBuffer = "";
    
    // For slider dragging
    private com.eymistaken.simplecps.api.SliderSetting draggingSlider = null;

    // Marquee Selection and Multi-Selection Dragging
    private final java.util.Set<com.eymistaken.simplecps.api.IHudElement> selectedElements = new java.util.HashSet<>();
    private boolean isSelectingMarquee = false;
    private int marqueeStartX = -1;
    private int marqueeStartY = -1;
    private int marqueeCurrentX = -1;
    private int marqueeCurrentY = -1;
 
    // Snapping Guides
    private Integer snapLineX = null;
    private Integer snapLineY = null;
 
    // Snapshots to prevent feedback loops in dragging
    private double dragStartX = 0;
    private double dragStartY = 0;
    private int leaderDragStartX = 0;
    private int leaderDragStartY = 0;
    // Previous frame's un-snapped drag position, used to tell cursor travel apart
    // from a snap correction.
    private int lastRawTargetX = 0;
    private int lastRawTargetY = 0;
    private final java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> dragStartOffsets = new java.util.HashMap<>();
    private final java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> dragStartScreenPositions = new java.util.HashMap<>();
    private final java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> currentDragTargetPositions = new java.util.HashMap<>();
    private boolean hasDraggedElement = false;

    public HudEditorScreen(Screen parent) {
        super(Component.nullToEmpty("HUD Editor"));
        this.parent = parent;
    }
    
    private java.util.List<com.eymistaken.simplecps.api.IHudElement> getAllActiveElements() {
        java.util.List<com.eymistaken.simplecps.api.IHudElement> list = new java.util.ArrayList<>();
        for (com.eymistaken.simplecps.api.HudModule module : HudModuleManager.getInstance().getModules()) {
            java.util.List<com.eymistaken.simplecps.api.IHudElement> sub = module.getSubElements();
            list.addAll(sub);
            if (sub.isEmpty()) {
                list.add(module);
            }
        }
        return list;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (com.eymistaken.simplecps.api.IHudElement element : getAllActiveElements()) {
            int ex = element.getX();
            int ey = element.getY();
            int ew = element.getWidth();
            int eh = element.getHeight();
            
            if (mouseX >= ex && mouseX <= ex + ew &&
                mouseY >= ey && mouseY <= ey + eh) {
                
                // Scroll to scale (range 50-300)
                int scaleChange = (verticalAmount > 0) ? 5 : -5;
                element.setScale(clamp(element.getScale() + scaleChange));
                com.eymistaken.simplecps.SimpleCPSConfig.save(); // scrolling has no "release" to save on
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    private int clamp(int val) {
        return Math.max(50, Math.min(300, val));
    }

    /** Grid step, guarded here too because a hand-edited config reaches the render loop. */
    private static int gridSize() {
        return Math.max(2, SimpleCPSConfig.instance.editorGridSize);
    }

    /**
     * Step to the next preset grid size, turning the grid on if it was off — a
     * right-click asking for a different step clearly means "show me the grid".
     */
    private static void cycleGridSize() {
        int current = SimpleCPSConfig.instance.editorGridSize;
        int next = GRID_SIZES[0];
        for (int i = 0; i < GRID_SIZES.length; i++) {
            if (GRID_SIZES[i] == current) {
                next = GRID_SIZES[(i + 1) % GRID_SIZES.length];
                break;
            }
        }
        SimpleCPSConfig.instance.editorGridSize = next;
        SimpleCPSConfig.instance.editorGridEnabled = true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Before the live preview below, so the resolver sees this frame's positions.
        advanceGlides();

        // Gradient Background
        context.fillGradient(0, 0, this.width, this.height, 0xC0000000, 0xD0000000);

        // Grid, drawn first so the brighter centre lines stay on top of it.
        if (SimpleCPSConfig.instance.editorGridEnabled) {
            int grid = gridSize();
            int gridColor = 0x22FFFFFF;
            for (int gx = grid; gx < this.width; gx += grid) {
                context.fill(gx, 0, gx + 1, this.height, gridColor);
            }
            for (int gy = grid; gy < this.height; gy += grid) {
                context.fill(0, gy, this.width, gy + 1, gridColor);
            }
        }

        // Center Lines
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int lineColor = 0x55FFFFFF;
        context.fill(centerX, 0, centerX + 1, this.height, lineColor);
        context.fill(0, centerY, this.width, centerY + 1, lineColor);

        // Render Live Preview
        SimpleCPSClient.onHudRender(context, delta);

        // Draw Selection Borders
        for (com.eymistaken.simplecps.api.IHudElement element : getAllActiveElements()) {
            int ex = element.getX();
            int ey = element.getY();
            int ew = element.getWidth();
            int eh = element.getHeight();
            
            boolean isSelected = selectedElements.contains(element);
            boolean isHovered = mouseX >= ex && mouseX <= ex + ew && 
                                mouseY >= ey && mouseY <= ey + eh;
            
            if (isSelected) {
                // Bright cyan border for selection
                int selColor = 0xFF00FFFF;
                context.fill(ex - 2, ey - 2, ex + ew + 2, ey, selColor); // Top
                context.fill(ex - 2, ey + eh, ex + ew + 2, ey + eh + 2, selColor); // Bottom
                context.fill(ex - 2, ey, ex, ey + eh, selColor); // Left
                context.fill(ex + ew, ey, ex + ew + 2, ey + eh, selColor); // Right
                
                // White premium corner highlights
                context.fill(ex - 3, ey - 3, ex + 2, ey - 2, 0xFFFFFFFF);
                context.fill(ex - 3, ey - 3, ex - 2, ey + 2, 0xFFFFFFFF);
                context.fill(ex + ew - 2, ey - 3, ex + ew + 3, ey - 2, 0xFFFFFFFF);
                context.fill(ex + ew + 2, ey - 3, ex + ew + 3, ey + 2, 0xFFFFFFFF);
                context.fill(ex - 3, ey + eh + 1, ex + 2, ey + eh + 2, 0xFFFFFFFF);
                context.fill(ex - 3, ey + eh - 2, ex - 2, ey + eh + 2, 0xFFFFFFFF);
                context.fill(ex + ew - 2, ey + eh + 1, ex + ew + 3, ey + eh + 2, 0xFFFFFFFF);
                context.fill(ex + ew + 2, ey + eh - 2, ex + ew + 3, ey + eh + 2, 0xFFFFFFFF);

                context.text(this.font, EymHudFonts.text(element.getName()), ex, ey - 10, 0xFF00FFFF, true);
            } else if (isHovered || element == draggingElement) {
                int borderColor = 0xFFFFFFFF;
                context.fill(ex - 1, ey - 1, ex + ew + 1, ey, borderColor); // Top
                context.fill(ex - 1, ey + eh, ex + ew + 1, ey + eh + 1, borderColor); // Bottom
                context.fill(ex - 1, ey, ex, ey + eh, borderColor); // Left
                context.fill(ex + ew, ey, ex + ew + 1, ey + eh, borderColor); // Right
                
                context.text(this.font, EymHudFonts.text(element.getName()), ex, ey - 10, 0xFFFFFFFF, true);
            }
        }

        // Draw Marquee Selection Box
        if (isSelectingMarquee) {
            int x1 = Math.min(marqueeStartX, marqueeCurrentX);
            int y1 = Math.min(marqueeStartY, marqueeCurrentY);
            int x2 = Math.max(marqueeStartX, marqueeCurrentX);
            int y2 = Math.max(marqueeStartY, marqueeCurrentY);
            
            // Semi-transparent blue fill
            context.fill(x1, y1, x2, y2, 0x3300AAFF);
            // Solid blue borders
            int mColor = 0xFF00AAFF;
            context.fill(x1, y1, x2, y1 + 1, mColor); // Top
            context.fill(x1, y2 - 1, x2, y2, mColor); // Bottom
            context.fill(x1, y1, x1 + 1, y2, mColor); // Left
            context.fill(x2 - 1, y1, x2, y2, mColor); // Right
        }
 
        // Draw Snap Lines
        if (snapLineX != null) {
            context.fill(snapLineX, 0, snapLineX + 1, this.height, 0xFFFF00FF);
        }
        if (snapLineY != null) {
            context.fill(0, snapLineY, this.width, snapLineY + 1, 0xFFFF00FF);
        }
        
        context.centeredText(this.font, EymHudFonts.text("Eymistaken's HUD"), this.width / 2, 10, 0xFFFFFFFF);

        renderAlignToolbar(context, mouseX, mouseY);

        // Escape hatch: the enchanting-table font is unreadable, so offer a
        // one-click restore to the vanilla font in the bottom-right corner.
        if (EymHudFonts.isEnchantActive()) {
            int bx = this.width - RESTORE_BTN_W - 6;
            int by = this.height - RESTORE_BTN_H - 6;
            boolean hov = mouseX >= bx && mouseX <= bx + RESTORE_BTN_W && mouseY >= by && mouseY <= by + RESTORE_BTN_H;
            context.fill(bx, by, bx + RESTORE_BTN_W, by + RESTORE_BTN_H, hov ? 0xFF553311 : 0xFF332211);
            context.fill(bx - 1, by - 1, bx + RESTORE_BTN_W + 1, by, 0xFFFFAA55);
            context.fill(bx - 1, by + RESTORE_BTN_H, bx + RESTORE_BTN_W + 1, by + RESTORE_BTN_H + 1, 0xFFFFAA55);
            context.fill(bx - 1, by, bx, by + RESTORE_BTN_H, 0xFFFFAA55);
            context.fill(bx + RESTORE_BTN_W, by, bx + RESTORE_BTN_W + 1, by + RESTORE_BTN_H, 0xFFFFAA55);
            // Plain String → always vanilla font, so this label stays readable.
            context.text(this.font, "Restore Font", bx + 8, by + 4, 0xFFFFFFFF, false);
        }

        // Every menu animation is advanced every frame, open or not: the target is 1
        // while open and 0 once closed, so the same tween that fades a menu in also
        // fades it back out. Each menu keeps drawing until its alpha reaches zero.
        if (contextMenuOpen) contextMenuRenderTarget = contextMenuTarget;
        float contextP = contextMenuAnim.update(contextMenuOpen ? 1f : 0f, MENU_ANIM_DURATION, Easings::expoOut);
        if (contextMenuRenderTarget != null && (contextMenuOpen || contextP > FADE_OUT_CUTOFF)) {
            renderContextMenu(context, mouseX, mouseY, contextP, contextMenuRenderTarget);
        }

        float globalP = globalMenuAnim.update(globalMenuOpen ? 1f : 0f, MENU_ANIM_DURATION, Easings::expoOut);
        if (globalMenuOpen || globalP > FADE_OUT_CUTOFF) {
            renderGlobalMenu(context, mouseX, mouseY, globalP);
        }

        updateSubmenuHover(mouseX, mouseY);
        float configsP = submenuAnim.update(configsSubmenuOpen ? 1f : 0f, MENU_ANIM_DURATION, Easings::expoOut);
        if (configsSubmenuOpen || configsP > FADE_OUT_CUTOFF) {
            renderConfigsSubmenu(context, mouseX, mouseY, configsP);
        }
        float serverP = serverSubmenuAnim.update(serverSubmenuOpen ? 1f : 0f, MENU_ANIM_DURATION, Easings::expoOut);
        if (serverSubmenuOpen || serverP > FADE_OUT_CUTOFF) {
            renderServerSubmenu(context, mouseX, mouseY, serverP);
        }

        if (textEditTarget != null || textPromptActive) {
            String label = textPromptActive ? textPromptLabel : textEditTarget.label;
            String buffer = textPromptActive ? textPromptBuffer : textEditBuffer;
            int cx = this.width / 2;
            int cy = this.height - 40;
            context.fill(cx - 100, cy - 10, cx + 100, cy + 10, 0xAA000000);
            context.fill(cx - 101, cy - 11, cx + 101, cy - 10, 0xFFFFFFFF);
            context.fill(cx - 101, cy + 10, cx + 101, cy + 11, 0xFFFFFFFF);
            context.fill(cx - 101, cy - 10, cx - 100, cy + 10, 0xFFFFFFFF);
            context.fill(cx + 100, cy - 10, cx + 101, cy + 10, 0xFFFFFFFF);

            context.centeredText(this.font, EymHudFonts.text(label + ": " + buffer + "_"), cx, cy - 4, 0xFFFFFFFF);
        }
    }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        // While the rename prompt is up, swallow all clicks (Enter/Esc dismiss it).
        if (textPromptActive) {
            return true;
        }

        // Font restore button (bottom-right) takes priority so it works even when
        // the enchant font makes everything else unreadable.
        if (button == 0 && EymHudFonts.isEnchantActive()) {
            int bx = this.width - RESTORE_BTN_W - 6;
            int by = this.height - RESTORE_BTN_H - 6;
            if (mouseX >= bx && mouseX <= bx + RESTORE_BTN_W && mouseY >= by && mouseY <= by + RESTORE_BTN_H) {
                SimpleCPSConfig.instance.hudFont = SimpleCPSConfig.HudFont.VANILLA;
                SimpleCPSConfig.save();
                return true;
            }
        }

        // Configs submenu must be handled BEFORE the global menu: a submenu click
        // lands outside the global menu rect and would otherwise close everything.
        if (configsSubmenuOpen && insideSubmenuRect((int) mouseX, (int) mouseY)) {
            if (!cachedPresetNames.isEmpty()) {
                int rowsTop = configsSubmenuY + 20;
                if (mouseY >= rowsTop) {
                    int idx = (int) ((mouseY - rowsTop) / 20);
                    if (idx >= 0 && idx < cachedPresetNames.size()) {
                        String name = cachedPresetNames.get(idx);
                        boolean overTrash = mouseX >= configsSubmenuX + SUBMENU_WIDTH - 20
                                         && mouseX <= configsSubmenuX + SUBMENU_WIDTH;
                        if (button == 0) {
                            if (overTrash) {
                                ConfigPresetManager.deletePreset(name);
                                cachedPresetNames = ConfigPresetManager.listPresets();
                                if (cachedPresetNames.isEmpty()) configsSubmenuOpen = false;
                            } else {
                                if (ConfigPresetManager.applyPreset(name)) {
                                    onConfigReplaced();
                                }
                                configsSubmenuOpen = false;
                                globalMenuOpen = false;
                            }
                            return true;
                        } else if (button == 1) {
                            final String target = name;
                            startTextPrompt("Rename Config", name, newName -> {
                                if (newName != null && !newName.trim().isEmpty()) {
                                    ConfigPresetManager.renamePreset(target, newName.trim());
                                    cachedPresetNames = ConfigPresetManager.listPresets();
                                }
                            });
                            return true;
                        }
                    }
                }
            }
            return true; // consume any click inside the submenu box
        }

        if (serverSubmenuOpen && insideServerSubmenuRect((int) mouseX, (int) mouseY)) {
            int rowsTop = serverSubmenuY + 20;
            if (mouseY < rowsTop) {
                // Title row doubles as the on/off switch for the whole feature.
                if (button == 0) toggleServerConfigs();
                return true;
            }
            if (!cachedServerKeys.isEmpty()) {
                int idx = (int) ((mouseY - rowsTop) / 20);
                if (idx >= 0 && idx < cachedServerKeys.size()) {
                    String key = cachedServerKeys.get(idx);
                    boolean overTrash = mouseX >= serverSubmenuX + SUBMENU_WIDTH - 20
                                     && mouseX <= serverSubmenuX + SUBMENU_WIDTH;
                    if (button == 0) {
                        if (overTrash) {
                            ServerConfigManager.deleteFor(key);
                            cachedServerKeys = ServerConfigManager.listKeys();
                        } else {
                            // Applying binds the result to the server we are on, so this
                            // also works as "use my Hypixel HUD here".
                            if (ServerConfigManager.applyFor(key)) {
                                onConfigReplaced();
                            }
                            serverSubmenuOpen = false;
                            globalMenuOpen = false;
                        }
                        return true;
                    } else if (button == 1) {
                        final String target = key;
                        startTextPrompt("Rename Server", ServerConfigManager.labelFor(key), newName -> {
                            // Only the label changes; the file keeps the address so
                            // joining this server still finds it.
                            ServerConfigManager.setLabel(target, newName);
                            cachedServerKeys = ServerConfigManager.listKeys();
                        });
                        return true;
                    }
                }
            }
            return true; // consume any click inside the submenu box
        }

        if (globalMenuOpen) {
            int menuW = GLOBAL_MENU_WIDTH;
            int menuH = getGlobalMenuHeight();
            if (mouseX >= globalMenuX && mouseX <= globalMenuX + menuW &&
                mouseY >= globalMenuY && mouseY <= globalMenuY + menuH) {
                int rowsTop = globalMenuY + 20;
                if (mouseY >= rowsTop) {
                    int row = (int) ((mouseY - rowsTop) / 20);
                    switch (row) {
                        case GLOBAL_ROW_PREVENT_OVERLAP -> {
                            SimpleCPSConfig.instance.preventOverlap = !SimpleCPSConfig.instance.preventOverlap;
                            SimpleCPSConfig.save();
                        }
                        case GLOBAL_ROW_GRID -> {
                            if (button == 1) {
                                cycleGridSize();
                            } else {
                                SimpleCPSConfig.instance.editorGridEnabled = !SimpleCPSConfig.instance.editorGridEnabled;
                            }
                            SimpleCPSConfig.save();
                        }
                        case GLOBAL_ROW_SAVE_CONFIG -> {
                            ConfigPresetManager.savePreset(ConfigPresetManager.uniqueDefaultName());
                            cachedPresetNames = ConfigPresetManager.listPresets();
                        }
                        case GLOBAL_ROW_CONFIGS -> { /* hover opens the fly-out; click just consumes */ }
                        case GLOBAL_ROW_SERVER_CONFIGS -> { /* hover opens the fly-out; click just consumes */ }
                        case GLOBAL_ROW_EXPORT_CODE -> exportShareCode();
                        case GLOBAL_ROW_IMPORT_CODE -> importShareCode();
                        case GLOBAL_ROW_RESET_HUD -> {
                            resetHudLayout();
                            globalMenuOpen = false;
                            configsSubmenuOpen = false;
                            serverSubmenuOpen = false;
                        }
                        default -> { }
                    }
                }
                return true;
            } else {
                globalMenuOpen = false;
                configsSubmenuOpen = false;
                serverSubmenuOpen = false;
            }
        }

        if (contextMenuOpen && contextMenuTarget != null) {
            int menuW = CONTEXT_MENU_WIDTH;
            int menuH = getMenuHeight(contextMenuTarget);
            if (mouseX >= contextMenuX && mouseX <= contextMenuX + menuW &&
                mouseY >= contextMenuY && mouseY <= contextMenuY + menuH) {
                
                int currentY = contextMenuY + 20; // Skip context menu title (Reset Position)
                java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = contextMenuTarget.getContextMenuSettings();
                
                if (mouseY < currentY) {
                    handleMenuClick(-1, mouseX, mouseY); // Reset Position
                    return true;
                }
                
                for (int i = 0; i < settings.size(); i++) {
                    com.eymistaken.simplecps.api.HudModuleSetting setting = settings.get(i);
                    int itemHeight = (setting instanceof com.eymistaken.simplecps.api.SliderSetting) ? 24 : 20;
                    if (mouseY >= currentY && mouseY < currentY + itemHeight) {
                        handleMenuClick(i, mouseX, mouseY);
                        return true;
                    }
                    currentY += itemHeight;
                }
                return true;
            } else {
                contextMenuOpen = false;
            }
        }
        
        // After the menus (they render on top of the toolbar) but before the element
        // hit test, so pressing a button never starts a drag on whatever is beneath it.
        if (button == 0) {
            int alignIndex = alignButtonAt((int) mouseX, (int) mouseY);
            if (alignIndex >= 0) {
                runAlignAction(alignIndex);
                return true;
            }
        }

        if (button == 0) { // Left click
            boolean elementClicked = false;
            for (com.eymistaken.simplecps.api.IHudElement element : getAllActiveElements()) {
                int ex = element.getX();
                int ey = element.getY();
                int ew = element.getWidth();
                int eh = element.getHeight();
                
                if (mouseX >= ex && mouseX <= ex + ew &&
                    mouseY >= ey && mouseY <= ey + eh) {
                    
                    if (!selectedElements.contains(element)) {
                        selectedElements.clear();
                        selectedElements.add(element);
                    }
                    draggingElement = element;
                    dragOffsetX = (int)mouseX - ex;
                    dragOffsetY = (int)mouseY - ey;
                    elementClicked = true;
                    
                    // Snapshot starting coordinates to prevent feedback loops
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    leaderDragStartX = element.getX();
                    leaderDragStartY = element.getY();
                    lastRawTargetX = leaderDragStartX;
                    lastRawTargetY = leaderDragStartY;
                    dragStartOffsets.clear();
                    currentDragTargetPositions.clear();
                    hasDraggedElement = false;
                    
                    java.util.Set<com.eymistaken.simplecps.api.IHudElement> targets = new java.util.HashSet<>();
                    if (selectedElements.contains(element)) {
                        targets.addAll(selectedElements);
                    } else {
                        targets.add(element);
                    }
                    dragStartScreenPositions.clear();
                    for (com.eymistaken.simplecps.api.IHudElement el : targets) {
                        dragStartOffsets.put(el, new java.awt.Point(el.getXOffset(), el.getYOffset()));
                        dragStartScreenPositions.put(el, new java.awt.Point(el.getX(), el.getY()));
                    }
                    
                    return true;
                }
            }
            if (!elementClicked) {
                selectedElements.clear();
                isSelectingMarquee = true;
                marqueeStartX = (int)mouseX;
                marqueeStartY = (int)mouseY;
                marqueeCurrentX = (int)mouseX;
                marqueeCurrentY = (int)mouseY;
                return true;
            }
        } else if (button == 1) { // Right click
            for (com.eymistaken.simplecps.api.IHudElement element : getAllActiveElements()) {
                int ex = element.getX();
                int ey = element.getY();
                int ew = element.getWidth();
                int eh = element.getHeight();
                
                if (mouseX >= ex && mouseX <= ex + ew &&
                    mouseY >= ey && mouseY <= ey + eh) {
                    
                    contextMenuTarget = element;
                    contextMenuOpen = true;
                    globalMenuOpen = false;
                    contextMenuAnim.snap(0f);

                    int h = getMenuHeight(element);
                    contextMenuX = placeMenuX((int) mouseX, CONTEXT_MENU_WIDTH);
                    contextMenuY = placeMenuY((int) mouseY, h);
                    return true;
                }
            }
            contextMenuOpen = false;
            openGlobalMenu((int)mouseX, (int)mouseY);
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        int button = click.button();
        if (button == 0) {
            if (isSelectingMarquee) {
                int x1 = Math.min(marqueeStartX, marqueeCurrentX);
                int y1 = Math.min(marqueeStartY, marqueeCurrentY);
                int x2 = Math.max(marqueeStartX, marqueeCurrentX);
                int y2 = Math.max(marqueeStartY, marqueeCurrentY);
                
                if (x2 - x1 > 2 && y2 - y1 > 2) {
                    for (com.eymistaken.simplecps.api.IHudElement element : getAllActiveElements()) {
                        int ex = element.getX();
                        int ey = element.getY();
                        int ew = element.getWidth();
                        int eh = element.getHeight();
                        
                        boolean intersect = !(ex + ew < x1 || ex > x2 || ey + eh < y1 || ey > y2);
                        if (intersect) {
                            selectedElements.add(element);
                        }
                    }
                }
                isSelectingMarquee = false;
                return true;
            }
            snapLineX = null;
            snapLineY = null;
            if (draggingElement != null) {
                java.util.Set<com.eymistaken.simplecps.api.IHudElement> targets = getDragTargets();
                if (hasDraggedElement) {
                    resolveDraggedTargets(targets);
                    for (com.eymistaken.simplecps.api.IHudElement target : targets) {
                        HudPlacementResolver.setManualLayout(target, HudModuleManager.getInstance().getModules(), true);
                    }
                }
                for (com.eymistaken.simplecps.api.IHudElement target : targets) {
                    target.onPositionUpdated();
                }
                draggingElement = null;
                hasDraggedElement = false;
                currentDragTargetPositions.clear();
                return true;
            }
            if (draggingSlider != null) {
                draggingSlider = null;
                SimpleCPSConfig.save();
                return true;
            }
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (button == 0 && draggingSlider != null) {
            int barX = contextMenuX + 78;
            int barW = 94;
            int relX = (int)mouseX - barX;
            float ratio = (float)relX / barW;
            ratio = Math.max(0, Math.min(1, ratio));
            
            int range = draggingSlider.max - draggingSlider.min;
            int newVal = draggingSlider.min + (int)(ratio * range);
            draggingSlider.setter.accept(newVal);
            return true;
        }
        
        if (button == 0 && isSelectingMarquee) {
            marqueeCurrentX = (int)mouseX;
            marqueeCurrentY = (int)mouseY;
            return true;
        }
        
        if (button == 0 && draggingElement != null) {
            // Calculate absolute mouse displacement since drag start
            int dx = (int)(mouseX - dragStartX);
            int dy = (int)(mouseY - dragStartY);
            
            int rawTargetX = leaderDragStartX + dx;
            int rawTargetY = leaderDragStartY + dy;

            // Pure cursor travel since the last frame: the part of the move that must
            // land instantly, before any snapping is folded in below.
            int freeDX = rawTargetX - lastRawTargetX;
            int freeDY = rawTargetY - lastRawTargetY;
            lastRawTargetX = rawTargetX;
            lastRawTargetY = rawTargetY;

            // Apply Snapping to Leader (unless Shift is held)
            java.awt.Point snappedPos = new java.awt.Point();
            long windowHandle = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
            boolean isShiftDown = org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS ||
                                  org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            
            if (isShiftDown) {
                snappedPos.x = rawTargetX;
                snappedPos.y = rawTargetY;
                snapLineX = null;
                snapLineY = null;
            } else {
                applySnapping(draggingElement, rawTargetX, rawTargetY, snappedPos);
                // The grid is only a fallback: an edge snap is a deliberate alignment
                // to another module and rounding would immediately pull it back off.
                if (SimpleCPSConfig.instance.editorGridEnabled) {
                    int grid = gridSize();
                    if (snapLineX == null) snappedPos.x = Math.round(snappedPos.x / (float) grid) * grid;
                    if (snapLineY == null) snappedPos.y = Math.round(snappedPos.y / (float) grid) * grid;
                }
            }
            
            // Snapped displacement from drag start position
            int snapDispX = snappedPos.x - leaderDragStartX;
            int snapDispY = snappedPos.y - leaderDragStartY;
            
            hasDraggedElement = true;
            currentDragTargetPositions.clear();
            for (var entry : dragStartScreenPositions.entrySet()) {
                com.eymistaken.simplecps.api.IHudElement el = entry.getKey();
                java.awt.Point startScreenPos = entry.getValue();
                
                int targetX = startScreenPos.x + snapDispX;
                int targetY = startScreenPos.y + snapDispY;

                currentDragTargetPositions.put(el, new java.awt.Point(targetX, targetY));
                applyElementScreenPosition(el, targetX, targetY, freeDX, freeDY);
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }
    
    private void applySnapping(com.eymistaken.simplecps.api.IHudElement leader, int targetX, int targetY, java.awt.Point outSnappedPos) {
        snapLineX = null;
        snapLineY = null;
        int threshold = 4; // 3-4 pixels snap threshold
        int snapGap = 4; // Gap snapping (similar to Keystrokes designer)
        
        int tLeft = targetX;
        int tRight = tLeft + leader.getWidth();
        int tTop = targetY;
        int tBottom = tTop + leader.getHeight();
        int tCX = tLeft + leader.getWidth() / 2;
        int tCY = tTop + leader.getHeight() / 2;
        
        int snappedX = targetX;
        int snappedY = targetY;
        
        // Find X Snap
        for (com.eymistaken.simplecps.api.IHudElement other : getAllActiveElements()) {
            if (other == leader) continue;
            if (selectedElements.contains(other)) continue; // Don't snap to selected group
            
            int oLeft = other.getX();
            int oRight = oLeft + other.getWidth();
            int oCX = oLeft + other.getWidth() / 2;
            
            // 1. Direct Edge Snap
            if (Math.abs(tLeft - oLeft) <= threshold) {
                snappedX = oLeft;
                snapLineX = oLeft;
                break;
            } else if (Math.abs(tRight - oRight) <= threshold) {
                snappedX = oRight - leader.getWidth();
                snapLineX = oRight;
                break;
            } else if (Math.abs(tLeft - oRight) <= threshold) {
                snappedX = oRight;
                snapLineX = oRight;
                break;
            } else if (Math.abs(tRight - oLeft) <= threshold) {
                snappedX = oLeft - leader.getWidth();
                snapLineX = oLeft;
                break;
            }
            // 2. Center-to-Center Snap
            else if (Math.abs(tCX - oCX) <= threshold) {
                snappedX = oCX - leader.getWidth() / 2;
                snapLineX = oCX;
                break;
            }
            // 3. Center-to-Edge Snaps (e.g. alignment of center to borders)
            else if (Math.abs(tCX - oLeft) <= threshold) {
                snappedX = oLeft - leader.getWidth() / 2;
                snapLineX = oLeft;
                break;
            } else if (Math.abs(tCX - oRight) <= threshold) {
                snappedX = oRight - leader.getWidth() / 2;
                snapLineX = oRight;
                break;
            } else if (Math.abs(tLeft - oCX) <= threshold) {
                snappedX = oCX;
                snapLineX = oCX;
                break;
            } else if (Math.abs(tRight - oCX) <= threshold) {
                snappedX = oCX - leader.getWidth();
                snapLineX = oCX;
                break;
            }
            // 4. Gap Snapping (4px space locking)
            else if (Math.abs(tLeft - (oRight + snapGap)) <= threshold) {
                snappedX = oRight + snapGap;
                snapLineX = oRight + snapGap;
                break;
            } else if (Math.abs(tRight - (oLeft - snapGap)) <= threshold) {
                snappedX = oLeft - snapGap - leader.getWidth();
                snapLineX = oLeft - snapGap;
                break;
            }
        }
        
        // Find Y Snap
        for (com.eymistaken.simplecps.api.IHudElement other : getAllActiveElements()) {
            if (other == leader) continue;
            if (selectedElements.contains(other)) continue; // Don't snap to selected group
            
            int oTop = other.getY();
            int oBottom = oTop + other.getHeight();
            int oCY = oTop + other.getHeight() / 2;
            
            // 1. Direct Edge Snap
            if (Math.abs(tTop - oTop) <= threshold) {
                snappedY = oTop;
                snapLineY = oTop;
                break;
            } else if (Math.abs(tBottom - oBottom) <= threshold) {
                snappedY = oBottom - leader.getHeight();
                snapLineY = oBottom;
                break;
            } else if (Math.abs(tTop - oBottom) <= threshold) {
                snappedY = oBottom;
                snapLineY = oBottom;
                break;
            } else if (Math.abs(tBottom - oTop) <= threshold) {
                snappedY = oTop - leader.getHeight();
                snapLineY = oTop;
                break;
            }
            // 2. Center-to-Center Snap
            else if (Math.abs(tCY - oCY) <= threshold) {
                snappedY = oCY - leader.getHeight() / 2;
                snapLineY = oCY;
                break;
            }
            // 3. Center-to-Edge Snaps
            else if (Math.abs(tCY - oTop) <= threshold) {
                snappedY = oTop - leader.getHeight() / 2;
                snapLineY = oTop;
                break;
            } else if (Math.abs(tCY - oBottom) <= threshold) {
                snappedY = oBottom - leader.getHeight() / 2;
                snapLineY = oBottom;
                break;
            } else if (Math.abs(tTop - oCY) <= threshold) {
                snappedY = oCY;
                snapLineY = oCY;
                break;
            } else if (Math.abs(tBottom - oCY) <= threshold) {
                snappedY = oCY - leader.getHeight();
                snapLineY = oCY;
                break;
            }
            // 4. Gap Snapping (4px space locking)
            else if (Math.abs(tTop - (oBottom + snapGap)) <= threshold) {
                snappedY = oBottom + snapGap;
                snapLineY = oBottom + snapGap;
                break;
            } else if (Math.abs(tBottom - (oTop - snapGap)) <= threshold) {
                snappedY = oTop - snapGap - leader.getHeight();
                snapLineY = oTop - snapGap;
                break;
            }
        }
        
        outSnappedPos.x = snappedX;
        outSnappedPos.y = snappedY;
    }
 
    private SimpleCPSConfig.Position determineAnchor(int cx, int cy) {
        int w = this.width;
        int h = this.height;
        
        if (cx > w * 0.4 && cx < w * 0.6 && cy > h * 0.4 && cy < h * 0.6) {
            return SimpleCPSConfig.Position.CENTER;
        }
        
        boolean left = cx < w / 2;
        boolean top = cy < h / 2;
        
        if (left && top) return SimpleCPSConfig.Position.TOP_LEFT;
        if (!left && top) return SimpleCPSConfig.Position.TOP_RIGHT;
        if (left && !top) return SimpleCPSConfig.Position.BOTTOM_LEFT;
        return SimpleCPSConfig.Position.BOTTOM_RIGHT;
    }
    
    private Point getEstimatedBasePos(SimpleCPSConfig.Position pos, int modW, int modH) {
        int x = 0;
        int y = 0;
        int gap = 5;
        
        switch (pos) {
            case TOP_LEFT -> { x = gap; y = gap; }
            case TOP_RIGHT -> { x = this.width - modW - gap; y = gap; }
            case BOTTOM_LEFT -> { x = gap; y = this.height - modH - gap; } 
            case BOTTOM_RIGHT -> { x = this.width - modW - gap; y = this.height - modH - gap; }
            case CENTER -> { x = (this.width - modW) / 2; y = (this.height - modH) / 2; }
        }
        return new Point(x, y);
    }

    @Override
    public void onClose() {
        SimpleCPSConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    private int getMenuHeight(com.eymistaken.simplecps.api.IHudElement element) {
        int height = 20;
        for (com.eymistaken.simplecps.api.HudModuleSetting setting : element.getContextMenuSettings()) {
            if (setting instanceof com.eymistaken.simplecps.api.SliderSetting) height += 24;
            else height += 20;
        }
        return height;
    }

    private int getGlobalMenuHeight() {
        return 20 + GLOBAL_ROW_COUNT * 20;
    }

    /**
     * Keep a menu of the given size fully on screen. When it is taller or wider than
     * the screen the top-left corner wins, so the title stays readable.
     */
    private int clampMenuX(int desiredX, int w) {
        return Math.max(MENU_MARGIN, Math.min(desiredX, this.width - w - MENU_MARGIN));
    }

    private int clampMenuY(int desiredY, int h) {
        return Math.max(MENU_MARGIN, Math.min(desiredY, this.height - h - MENU_MARGIN));
    }

    /** Open at the cursor, flipping to its other side when that would run off screen. */
    private int placeMenuX(int mouseX, int w) {
        if (mouseX + w + MENU_MARGIN > this.width) {
            return clampMenuX(mouseX - w, w);
        }
        return clampMenuX(mouseX, w);
    }

    private int placeMenuY(int mouseY, int h) {
        if (mouseY + h + MENU_MARGIN > this.height) {
            return clampMenuY(mouseY - h, h);
        }
        return clampMenuY(mouseY, h);
    }

    /**
     * Where a fly-out goes: to the right of the parent menu, or to its left when the
     * right side would overflow. If neither side fits it is simply clamped on screen
     * and overlaps the parent, which beats being unreachable.
     */
    private int placeSubmenuX(int w) {
        int rightX = globalMenuX + GLOBAL_MENU_WIDTH;
        if (rightX + w + MENU_MARGIN <= this.width) return rightX;
        int leftX = globalMenuX - w;
        if (leftX >= MENU_MARGIN) return leftX;
        return clampMenuX(rightX, w);
    }

    private void openGlobalMenu(int mouseX, int mouseY) {
        globalMenuOpen = true;
        contextMenuOpen = false;
        configsSubmenuOpen = false;
        serverSubmenuOpen = false;
        globalMenuAnim.snap(0f);

        int h = getGlobalMenuHeight();
        globalMenuX = placeMenuX(mouseX, GLOBAL_MENU_WIDTH);
        globalMenuY = placeMenuY(mouseY, h);
    }

    /**
     * Drop every piece of state that points into the config we just replaced.
     * {@code textEditTarget} and {@code draggingSlider} hold setter lambdas closed over
     * the old object, so an edit left half-finished would otherwise land in the
     * discarded config and vanish on the next save.
     */
    private void onConfigReplaced() {
        // In-flight moves point at the config we are throwing away; letting them run
        // would write stale positions over the one we just adopted.
        glides.clear();
        glideMoved = false;
        selectedElements.clear();
        draggingElement = null;
        textEditTarget = null;
        draggingSlider = null;
        contextMenuOpen = false;
    }

    private void setShareStatus(String msg) {
        shareStatus = msg;
        shareStatusUntil = System.currentTimeMillis() + 2500;
    }

    /** Copy the whole current config to the clipboard as an EYMHUD1- share code. */
    private void exportShareCode() {
        String code = com.eymistaken.simplecps.util.HudShareCodec.encodeConfig();
        if (code == null) {
            setShareStatus("Export failed");
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(code);
        setShareStatus("Copied to clipboard!");
    }

    /**
     * Read a share code from the clipboard and apply it. The current config is
     * backed up to a preset first so an unwanted import can be undone.
     */
    private void importShareCode() {
        String clip = this.minecraft.keyboardHandler.getClipboard();
        var decoded = com.eymistaken.simplecps.util.HudShareCodec.decode(clip);
        if (decoded == null) {
            setShareStatus("No valid code in clipboard");
            return;
        }
        if (!com.eymistaken.simplecps.util.HudShareCodec.TYPE_CONFIG.equals(decoded.type())) {
            // A keystrokes code would apply only the layout yet still report success
            // here — and burn a backup slot doing it. Send them to the designer.
            setShareStatus("That's a keystrokes code - use the Keystrokes designer");
            return;
        }

        // Timestamped: a fixed name would overwrite the previous backup, so a second
        // import would destroy the only copy of the original config.
        String backupName = ConfigPresetManager.uniqueName(
            "Backup " + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")));
        ConfigPresetManager.savePreset(backupName);

        if (com.eymistaken.simplecps.util.HudShareCodec.apply(decoded)) {
            onConfigReplaced();
            cachedPresetNames = ConfigPresetManager.listPresets();
            if (com.eymistaken.simplecps.util.HudShareCodec.isFromOtherVersion(decoded)) {
                setShareStatus("Imported from v" + decoded.modVersion() + " (backup saved)");
            } else {
                setShareStatus("Imported! (backup saved)");
            }
        } else {
            setShareStatus("Import failed");
        }
    }

    private void resetHudLayout() {
        // resetToDefaults() writes offsets straight to the config, so any in-flight
        // move would overwrite the reset on the next frame.
        glides.clear();
        glideMoved = false;
        java.util.List<com.eymistaken.simplecps.api.HudModule> modules = HudModuleManager.getInstance().getModules();
        for (com.eymistaken.simplecps.api.HudModule module : modules) {
            clearManualLayout(module, modules);
            for (com.eymistaken.simplecps.api.IHudElement subElement : module.getSubElements()) {
                clearManualLayout(subElement, modules);
                subElement.resetToDefaults();
            }
            module.resetToDefaults();
            for (com.eymistaken.simplecps.api.IHudElement subElement : module.getSubElements()) {
                clearManualLayout(subElement, modules);
                subElement.resetToDefaults();
            }
        }
        SimpleCPSConfig.instance.preventOverlap = true;
        selectedElements.clear();
        draggingElement = null;
        snapLineX = null;
        snapLineY = null;
        SimpleCPSConfig.save();
    }

    private void clearManualLayout(
        com.eymistaken.simplecps.api.IHudElement element,
        java.util.List<com.eymistaken.simplecps.api.HudModule> modules
    ) {
        HudPlacementResolver.setManualLayout(element, modules, false);
    }

    private void handleMenuClick(int index, double mouseX, double mouseY) {
        if (index == -1) {
            // Same reason as resetHudLayout: this writes the config directly, so an
            // in-flight move for this element has to be dropped, not finished.
            glides.remove(contextMenuTarget);
            contextMenuTarget.resetToDefaults();
            HudPlacementResolver.setManualLayout(contextMenuTarget, HudModuleManager.getInstance().getModules(), false);
            SimpleCPSConfig.save();
            contextMenuOpen = false;
            return;
        }
        
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = contextMenuTarget.getContextMenuSettings();
        if (index < settings.size()) {
            com.eymistaken.simplecps.api.HudModuleSetting setting = settings.get(index);
            if (setting instanceof com.eymistaken.simplecps.api.ActionSetting actionSetting) {
                actionSetting.action.run();
                SimpleCPSConfig.save();
                contextMenuOpen = false;
            } else if (setting instanceof com.eymistaken.simplecps.api.BooleanSetting boolSetting) {
                boolSetting.setter.accept(!boolSetting.getter.get());
                SimpleCPSConfig.save();
            } else if (setting instanceof com.eymistaken.simplecps.api.CycleSetting cycleSetting) {
                int next = cycleSetting.getter.get() + 1;
                if (next >= cycleSetting.options.size()) next = 0;
                cycleSetting.setter.accept(next);
                SimpleCPSConfig.save();
            } else if (setting instanceof com.eymistaken.simplecps.api.ColorSetting colorSetting) {
                this.minecraft.gui.setScreen(new ColorPickerScreen(
                    colorSetting.getter.get(),
                    newColor -> {
                        colorSetting.setter.accept(newColor);
                        SimpleCPSConfig.save();
                    },
                    this
                ));
                contextMenuOpen = false;
            } else if (setting instanceof com.eymistaken.simplecps.api.TextSetting textSetting) {
                textEditTarget = textSetting;
                textEditBuffer = textSetting.getter.get();
                contextMenuOpen = false;
            } else if (setting instanceof com.eymistaken.simplecps.api.SliderSetting slider) {
                int relX = (int)mouseX - contextMenuX;
                if (relX >= 176 && relX <= 176 + 40) { // Reset button
                    slider.setter.accept(slider.defaultValue);
                    SimpleCPSConfig.save();
                } else if (relX >= 78 && relX <= 78 + 94) { // Bar
                    float ratio = (float)(relX - 78) / 94f;
                    ratio = Math.max(0, Math.min(1, ratio));
                    int range = slider.max - slider.min;
                    slider.setter.accept(slider.min + (int)(ratio * range));
                    draggingSlider = slider;
                }
            }
        }
    }

    private void renderContextMenu(
        GuiGraphicsExtractor context, int mouseX, int mouseY, float p,
        com.eymistaken.simplecps.api.IHudElement target
    ) {
        int w = CONTEXT_MENU_WIDTH;
        int h = getMenuHeight(target);
        int x = contextMenuX;
        int y = contextMenuY;

        context.pose().pushMatrix();
        context.pose().translate(0f, (1f - p) * 8f);

        context.fill(x, y, x + w, y + h, fade(0xFF222222, p));
        context.fill(x - 1, y - 1, x + w + 1, y, fade(0xFFFFFFFF, p)); // Top
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, fade(0xFFFFFFFF, p)); // Bottom
        context.fill(x - 1, y, x, y + h, fade(0xFFFFFFFF, p)); // Left
        context.fill(x + w, y, x + w + 1, y + h, fade(0xFFFFFFFF, p)); // Right

        // Row 0 is the "Reset Position" entry (hit-tested as index -1 elsewhere).
        boolean hoverReset = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + 20;
        if (hoverReset) context.fill(x, y, x + w, y + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Reset Position"), x + 5, y + 6, fade(0xFFFFFFFF, p), true);

        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = target.getContextMenuSettings();
        int currentY = y + 20;

        for (int i = 0; i < settings.size(); i++) {
            com.eymistaken.simplecps.api.HudModuleSetting setting = settings.get(i);
            int itemHeight = (setting instanceof com.eymistaken.simplecps.api.SliderSetting) ? 24 : 20;

            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= currentY && mouseY < currentY + itemHeight;
            if (hovered) context.fill(x, currentY, x + w, currentY + itemHeight, fade(0xFF444444, p));

            context.text(this.font, EymHudFonts.text(setting.label), x + 5, currentY + (itemHeight / 2) - 4, fade(0xFFFFFFFF, p), true);

            if (setting instanceof com.eymistaken.simplecps.api.BooleanSetting boolSetting) {
                String val = boolSetting.getter.get() ? "[ON]" : "[OFF]";
                int valW = this.font.width(EymHudFonts.text(val));
                int color = boolSetting.getter.get() ? 0xFF55FF55 : 0xFFFF5555;
                context.text(this.font, EymHudFonts.text(val), x + w - valW - 5, currentY + 6, fade(color, p), true);
            } else if (setting instanceof com.eymistaken.simplecps.api.CycleSetting cycleSetting) {
                String val = cycleSetting.options.get(cycleSetting.getter.get());
                int valW = this.font.width(EymHudFonts.text(val));
                context.text(this.font, EymHudFonts.text(val), x + w - valW - 5, currentY + 6, fade(0xFFFFAA00, p), true);
            } else if (setting instanceof com.eymistaken.simplecps.api.ColorSetting colorSetting) {
                int c = colorSetting.getter.get() | 0xFF000000;
                context.fill(x + w - 15, currentY + 5, x + w - 5, currentY + 15, fade(c, p));
                // border
                context.fill(x + w - 16, currentY + 4, x + w - 15, currentY + 16, fade(0xFFFFFFFF, p));
                context.fill(x + w - 4, currentY + 4, x + w - 5, currentY + 16, fade(0xFFFFFFFF, p));
                context.fill(x + w - 15, currentY + 4, x + w - 5, currentY + 5, fade(0xFFFFFFFF, p));
                context.fill(x + w - 15, currentY + 15, x + w - 5, currentY + 16, fade(0xFFFFFFFF, p));
            } else if (setting instanceof com.eymistaken.simplecps.api.TextSetting textSetting) {
                String val = textSetting.getter.get();
                if (val.length() > 5) val = val.substring(0, 5) + "...";
                int valW = this.font.width(EymHudFonts.text(val));
                context.text(this.font, EymHudFonts.text(val), x + w - valW - 5, currentY + 6, fade(0xFFAAAAAA, p), true);
            } else if (setting instanceof com.eymistaken.simplecps.api.SliderSetting slider) {
                int val = slider.getter.get();
                float ratio = (float)(val - slider.min) / (slider.max - slider.min);
                int fillWidth = (int)(94 * ratio);

                // Draw Bar Background
                context.fill(x + 78, currentY + 6, x + 78 + 94, currentY + 18, fade(0xFF000000, p));
                // Draw Bar Fill
                context.fill(x + 78, currentY + 6, x + 78 + fillWidth, currentY + 18, fade(0xFF36454F, p));
                // Draw Thumb
                context.fill(x + 78 + fillWidth - 2, currentY + 4, x + 78 + fillWidth + 2, currentY + 20, fade(0xFFAAAAAA, p));
                // Draw Value text centered in Bar
                String valStr = val + "%";
                int valW = this.font.width(EymHudFonts.text(valStr));
                context.text(this.font, EymHudFonts.text(valStr), x + 78 + (94 - valW) / 2, currentY + 8, fade(0xFFFFFFFF, p), true);

                // Draw Reset Button (width 40px starting at x+176)
                boolean hoverRButton = mouseX >= x + 176 && mouseX <= x + 176 + 40 && mouseY >= currentY && mouseY < currentY + itemHeight;
                int rColor = hoverRButton ? 0xFF990000 : 0xFF550000;
                context.fill(x + 176, currentY + 4, x + 176 + 40, currentY + 20, fade(rColor, p));
                context.centeredText(this.font, EymHudFonts.text("Reset"), x + 176 + 20, currentY + 8, fade(0xFFFFFFFF, p));
            }

            currentY += itemHeight;
        }

        context.pose().popMatrix();
    }

    private boolean hoverRow(int mouseX, int mouseY, int x, int rowY, int w) {
        return mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY < rowY + 20;
    }

    private void renderGlobalMenu(GuiGraphicsExtractor context, int mouseX, int mouseY, float p) {
        int w = GLOBAL_MENU_WIDTH;
        int h = getGlobalMenuHeight();
        int x = globalMenuX;
        int y = globalMenuY;

        context.pose().pushMatrix();
        context.pose().translate(0f, (1f - p) * 8f);

        context.fill(x, y, x + w, y + h, fade(0xFF222222, p));
        context.fill(x - 1, y - 1, x + w + 1, y, fade(0xFFFFFFFF, p));
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, fade(0xFFFFFFFF, p));
        context.fill(x - 1, y, x, y + h, fade(0xFFFFFFFF, p));
        context.fill(x + w, y, x + w + 1, y + h, fade(0xFFFFFFFF, p));

        context.text(this.font, EymHudFonts.text("HUD Settings"), x + 5, y + 6, fade(0xFFFFFFFF, p), true);

        // Row 0: Prevent Overlap toggle
        int r0 = y + 20 + GLOBAL_ROW_PREVENT_OVERLAP * 20;
        if (hoverRow(mouseX, mouseY, x, r0, w)) context.fill(x, r0, x + w, r0 + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Prevent Overlap"), x + 5, r0 + 6, fade(0xFFFFFFFF, p), true);
        String val = SimpleCPSConfig.instance.preventOverlap ? "[ON]" : "[OFF]";
        int valW = this.font.width(EymHudFonts.text(val));
        int color = SimpleCPSConfig.instance.preventOverlap ? 0xFF55FF55 : 0xFFFF5555;
        context.text(this.font, EymHudFonts.text(val), x + w - valW - 5, r0 + 6, fade(color, p), true);

        // Row 1: Grid toggle (left-click) / step (right-click)
        int rGrid = y + 20 + GLOBAL_ROW_GRID * 20;
        if (hoverRow(mouseX, mouseY, x, rGrid, w)) context.fill(x, rGrid, x + w, rGrid + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Grid"), x + 5, rGrid + 6, fade(0xFFFFFFFF, p), true);
        boolean gridOn = SimpleCPSConfig.instance.editorGridEnabled;
        String gridVal = gridOn ? ("[" + gridSize() + "px]") : "[OFF]";
        int gridValW = this.font.width(EymHudFonts.text(gridVal));
        context.text(this.font, EymHudFonts.text(gridVal), x + w - gridValW - 5, rGrid + 6,
            fade(gridOn ? 0xFF55FF55 : 0xFFFF5555, p), true);

        // Row 2: Save Config
        int r1 = y + 20 + GLOBAL_ROW_SAVE_CONFIG * 20;
        if (hoverRow(mouseX, mouseY, x, r1, w)) context.fill(x, r1, x + w, r1 + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Save Config"), x + 5, r1 + 6, fade(0xFF55FFFF, p), true);

        // Row 3: Configs (fly-out)
        int r2 = y + 20 + GLOBAL_ROW_CONFIGS * 20;
        if (hoverRow(mouseX, mouseY, x, r2, w) || configsSubmenuOpen) context.fill(x, r2, x + w, r2 + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Configs"), x + 5, r2 + 6, fade(0xFFFFFFFF, p), true);
        context.text(this.font, EymHudFonts.text(">"), x + w - this.font.width(EymHudFonts.text(">")) - 6, r2 + 6, fade(0xFFFFFFFF, p), true);

        // Row 4: Server Configs (fly-out)
        int rSrv = y + 20 + GLOBAL_ROW_SERVER_CONFIGS * 20;
        if (hoverRow(mouseX, mouseY, x, rSrv, w) || serverSubmenuOpen) context.fill(x, rSrv, x + w, rSrv + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Server Configs"), x + 5, rSrv + 6, fade(0xFFFFFFFF, p), true);
        context.text(this.font, EymHudFonts.text(">"), x + w - this.font.width(EymHudFonts.text(">")) - 6, rSrv + 6, fade(0xFFFFFFFF, p), true);

        // Row 5: Export Code
        int rExp = y + 20 + GLOBAL_ROW_EXPORT_CODE * 20;
        if (hoverRow(mouseX, mouseY, x, rExp, w)) context.fill(x, rExp, x + w, rExp + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Export Code"), x + 5, rExp + 6, fade(0xFF55FF55, p), true);

        // Row 6: Import Code
        int rImp = y + 20 + GLOBAL_ROW_IMPORT_CODE * 20;
        if (hoverRow(mouseX, mouseY, x, rImp, w)) context.fill(x, rImp, x + w, rImp + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Import Code"), x + 5, rImp + 6, fade(0xFF55FF55, p), true);

        // Row 7: Reset HUD
        int r3 = y + 20 + GLOBAL_ROW_RESET_HUD * 20;
        if (hoverRow(mouseX, mouseY, x, r3, w)) context.fill(x, r3, x + w, r3 + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Reset HUD"), x + 5, r3 + 6, fade(0xFFFFAA55, p), true);

        // Transient feedback for the export/import actions. Moves above the menu when
        // it opened near the bottom, where the line below would be off screen.
        if (System.currentTimeMillis() < shareStatusUntil && !shareStatus.isEmpty()) {
            int statusY = (y + h + 13 <= this.height) ? y + h + 4 : y - 13;
            context.text(this.font, EymHudFonts.text(shareStatus), x + 5, statusY, fade(0xFFFFFF55, p), true);
        }

        context.pose().popMatrix();
    }

    private int getConfigsSubmenuHeight() {
        int rows = cachedPresetNames.isEmpty() ? 1 : cachedPresetNames.size();
        return 20 + rows * 20;
    }

    private boolean insideSubmenuRect(int mouseX, int mouseY) {
        int w = SUBMENU_WIDTH;
        int h = getConfigsSubmenuHeight();
        return mouseX >= configsSubmenuX && mouseX <= configsSubmenuX + w &&
               mouseY >= configsSubmenuY && mouseY <= configsSubmenuY + h;
    }

    private void openConfigsSubmenu() {
        cachedPresetNames = ConfigPresetManager.listPresets();
        configsSubmenuOpen = true;
        // No snap to 0 here: hovering back onto the row mid-fade should pick the
        // animation up where it is, not restart it from invisible.
        int h = getConfigsSubmenuHeight();
        configsSubmenuX = placeSubmenuX(SUBMENU_WIDTH);
        configsSubmenuY = clampMenuY(globalMenuY + 20 + GLOBAL_ROW_CONFIGS * 20, h);
    }

    /**
     * Hover routing for both fly-outs. They share the same anchor column, so opening
     * one must close the other — otherwise sliding from "Configs" down to "Server
     * Configs" would leave two overlapping panels on screen.
     */
    private void updateSubmenuHover(int mouseX, int mouseY) {
        if (!globalMenuOpen) {
            configsSubmenuOpen = false;
            serverSubmenuOpen = false;
            return;
        }
        int configsRowY = globalMenuY + 20 + GLOBAL_ROW_CONFIGS * 20;
        int serverRowY = globalMenuY + 20 + GLOBAL_ROW_SERVER_CONFIGS * 20;
        boolean overConfigsRow = hoverRow(mouseX, mouseY, globalMenuX, configsRowY, GLOBAL_MENU_WIDTH);
        boolean overServerRow = hoverRow(mouseX, mouseY, globalMenuX, serverRowY, GLOBAL_MENU_WIDTH);

        if (overConfigsRow) {
            serverSubmenuOpen = false;
            if (!configsSubmenuOpen) openConfigsSubmenu();
            return;
        }
        if (overServerRow) {
            configsSubmenuOpen = false;
            if (!serverSubmenuOpen) openServerSubmenu();
            return;
        }
        if (configsSubmenuOpen && !insideSubmenuRect(mouseX, mouseY)) configsSubmenuOpen = false;
        if (serverSubmenuOpen && !insideServerSubmenuRect(mouseX, mouseY)) serverSubmenuOpen = false;
    }

    private int getServerSubmenuHeight() {
        int rows = cachedServerKeys.isEmpty() ? 1 : cachedServerKeys.size();
        return 20 + rows * 20;
    }

    private boolean insideServerSubmenuRect(int mouseX, int mouseY) {
        int w = SUBMENU_WIDTH;
        int h = getServerSubmenuHeight();
        return mouseX >= serverSubmenuX && mouseX <= serverSubmenuX + w &&
               mouseY >= serverSubmenuY && mouseY <= serverSubmenuY + h;
    }

    private void openServerSubmenu() {
        cachedServerKeys = ServerConfigManager.listKeys();
        serverSubmenuOpen = true;
        int h = getServerSubmenuHeight();
        serverSubmenuX = placeSubmenuX(SUBMENU_WIDTH);
        serverSubmenuY = clampMenuY(globalMenuY + 20 + GLOBAL_ROW_SERVER_CONFIGS * 20, h);
    }

    /**
     * Flip the feature on or off. Turning it on adopts this server's stored config if
     * there is one — overwriting it with whatever is on screen would throw away a
     * layout the user built here earlier and never asked to replace.
     */
    private void toggleServerConfigs() {
        boolean on = !SimpleCPSConfig.instance.serverConfigsEnabled;
        SimpleCPSConfig.instance.serverConfigsEnabled = on;

        String key = ServerConfigManager.currentKey();
        if (on && ServerConfigManager.hasConfig(key)) {
            if (ServerConfigManager.applyFor(key)) {
                onConfigReplaced();
            }
        } else {
            // With the switch on this save also creates the binding for this server.
            SimpleCPSConfig.save();
        }
        cachedServerKeys = ServerConfigManager.listKeys();
    }

    private void renderServerSubmenu(GuiGraphicsExtractor context, int mouseX, int mouseY, float p) {
        int x = serverSubmenuX;
        int y = serverSubmenuY;
        int w = SUBMENU_WIDTH;
        int h = getServerSubmenuHeight();

        context.pose().pushMatrix();
        context.pose().translate(0f, (1f - p) * 8f);

        context.fill(x, y, x + w, y + h, fade(0xFF222222, p));
        context.fill(x - 1, y - 1, x + w + 1, y, fade(0xFFFFFFFF, p));
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, fade(0xFFFFFFFF, p));
        context.fill(x - 1, y, x, y + h, fade(0xFFFFFFFF, p));
        context.fill(x + w, y, x + w + 1, y + h, fade(0xFFFFFFFF, p));

        // Title row is also the switch.
        boolean on = SimpleCPSConfig.instance.serverConfigsEnabled;
        if (hoverRow(mouseX, mouseY, x, y, w)) context.fill(x, y, x + w, y + 20, fade(0xFF444444, p));
        context.text(this.font, EymHudFonts.text("Per Server"), x + 5, y + 6, fade(0xFFFFAA55, p), true);
        String sw = on ? "[ON]" : "[OFF]";
        int swW = this.font.width(EymHudFonts.text(sw));
        context.text(this.font, EymHudFonts.text(sw), x + w - swW - 5, y + 6, fade(on ? 0xFF55FF55 : 0xFFFF5555, p), true);

        int rowY = y + 20;
        if (cachedServerKeys.isEmpty()) {
            context.text(this.font, EymHudFonts.text("No server configs"), x + 5, rowY + 6, fade(0xFF888888, p), true);
            context.pose().popMatrix();
            return;
        }

        String activeKey = ServerConfigManager.currentKey();
        for (String key : cachedServerKeys) {
            boolean trashHover = mouseX >= x + w - 20 && mouseX <= x + w && mouseY >= rowY && mouseY < rowY + 20;
            boolean rowHover = hoverRow(mouseX, mouseY, x, rowY, w) && !trashHover;
            if (rowHover) context.fill(x, rowY, x + w, rowY + 20, fade(0xFF444444, p));
            if (trashHover) context.fill(x + w - 20, rowY, x + w, rowY + 20, fade(0xFF552222, p));

            // Server addresses are long; truncate the same way the Configs list does.
            String disp = ServerConfigManager.labelFor(key);
            int maxTextW = w - 26;
            if (this.font.width(EymHudFonts.text(disp)) > maxTextW) {
                while (disp.length() > 1 && this.font.width(EymHudFonts.text(disp + "...")) > maxTextW) {
                    disp = disp.substring(0, disp.length() - 1);
                }
                disp = disp + "...";
            }
            // Green marks the server we are on, so it is obvious which one is live.
            int nameColor = key.equals(activeKey) ? 0xFF55FF55 : 0xFFFFFFFF;
            context.text(this.font, EymHudFonts.text(disp), x + 5, rowY + 6, fade(nameColor, p), true);

            int tx = x + w - 15;
            int ty = rowY + 5;
            int tColor = trashHover ? 0xFFFF7777 : 0xFFAAAAAA;
            context.fill(tx + 3, ty, tx + 7, ty + 1, fade(tColor, p));       // handle
            context.fill(tx, ty + 1, tx + 10, ty + 3, fade(tColor, p));      // lid
            context.fill(tx + 1, ty + 3, tx + 9, ty + 11, fade(tColor, p));  // body

            rowY += 20;
        }

        context.pose().popMatrix();
    }

    private void renderConfigsSubmenu(GuiGraphicsExtractor context, int mouseX, int mouseY, float p) {
        int x = configsSubmenuX;
        int y = configsSubmenuY;
        int w = SUBMENU_WIDTH;
        int h = getConfigsSubmenuHeight();

        context.pose().pushMatrix();
        context.pose().translate(0f, (1f - p) * 8f);

        context.fill(x, y, x + w, y + h, fade(0xFF222222, p));
        context.fill(x - 1, y - 1, x + w + 1, y, fade(0xFFFFFFFF, p));
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, fade(0xFFFFFFFF, p));
        context.fill(x - 1, y, x, y + h, fade(0xFFFFFFFF, p));
        context.fill(x + w, y, x + w + 1, y + h, fade(0xFFFFFFFF, p));

        context.text(this.font, EymHudFonts.text("Saved Configs"), x + 5, y + 6, fade(0xFFFFAA55, p), true);

        int rowY = y + 20;
        if (cachedPresetNames.isEmpty()) {
            context.text(this.font, EymHudFonts.text("No saved configs"), x + 5, rowY + 6, fade(0xFF888888, p), true);
            context.pose().popMatrix();
            return;
        }

        for (String name : cachedPresetNames) {
            boolean trashHover = mouseX >= x + w - 20 && mouseX <= x + w && mouseY >= rowY && mouseY < rowY + 20;
            boolean rowHover = hoverRow(mouseX, mouseY, x, rowY, w) && !trashHover;
            if (rowHover) context.fill(x, rowY, x + w, rowY + 20, fade(0xFF444444, p));
            if (trashHover) context.fill(x + w - 20, rowY, x + w, rowY + 20, fade(0xFF552222, p));

            // Name, truncated with ellipsis if too wide (leave room for the trash icon).
            String disp = name;
            int maxTextW = w - 26;
            if (this.font.width(EymHudFonts.text(disp)) > maxTextW) {
                while (disp.length() > 1 && this.font.width(EymHudFonts.text(disp + "...")) > maxTextW) {
                    disp = disp.substring(0, disp.length() - 1);
                }
                disp = disp + "...";
            }
            context.text(this.font, EymHudFonts.text(disp), x + 5, rowY + 6, fade(0xFFFFFFFF, p), true);

            // Trash icon (drawn with fills to match the rest of this UI).
            int tx = x + w - 15;
            int ty = rowY + 5;
            int tColor = trashHover ? 0xFFFF7777 : 0xFFAAAAAA;
            context.fill(tx + 3, ty, tx + 7, ty + 1, fade(tColor, p));       // handle
            context.fill(tx, ty + 1, tx + 10, ty + 3, fade(tColor, p));      // lid
            context.fill(tx + 1, ty + 3, tx + 9, ty + 11, fade(tColor, p));  // body

            rowY += 20;
        }

        context.pose().popMatrix();
    }

    private void startTextPrompt(String label, String initial, java.util.function.Consumer<String> callback) {
        textPromptActive = true;
        textPromptLabel = label;
        textPromptBuffer = initial == null ? "" : initial;
        textPromptCallback = callback;
        // Close menus so the prompt overlay is unobstructed.
        configsSubmenuOpen = false;
        serverSubmenuOpen = false;
        globalMenuOpen = false;
        contextMenuOpen = false;
    }

    private void closeTextPrompt() {
        textPromptActive = false;
        textPromptBuffer = "";
        textPromptLabel = "";
        textPromptCallback = null;
    }

    @Override
    public boolean charTyped(CharacterEvent charInput) {
        char chr = (char) charInput.codepoint();
        if (textPromptActive) {
            textPromptBuffer += chr;
            return true;
        }
        if (textEditTarget != null) {
            textEditBuffer += chr;
            return true;
        }
        return super.charTyped(charInput);
    }

    private java.util.Set<com.eymistaken.simplecps.api.IHudElement> getDragTargets() {
        java.util.Set<com.eymistaken.simplecps.api.IHudElement> targets = new java.util.HashSet<>();
        if (selectedElements.contains(draggingElement)) {
            targets.addAll(selectedElements);
        } else if (draggingElement != null) {
            targets.add(draggingElement);
        }
        return targets;
    }

    private void resolveDraggedTargets(java.util.Set<com.eymistaken.simplecps.api.IHudElement> targets) {
        if (!SimpleCPSConfig.instance.preventOverlap || targets.isEmpty()) return;

        java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> targetPositions = new java.util.HashMap<>();
        for (com.eymistaken.simplecps.api.IHudElement target : targets) {
            java.awt.Point pos = currentDragTargetPositions.get(target);
            if (pos == null) {
                pos = new java.awt.Point(logicalX(target), logicalY(target));
            }
            targetPositions.put(target, pos);
        }
        resolveAndApplyTargetPositions(targets, targetPositions);
    }

    private void resolveAndApplyTargetPositions(
        java.util.Set<com.eymistaken.simplecps.api.IHudElement> targets,
        java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> targetPositions
    ) {
        HudPlacementResolver.Rect groupRect = buildGroupRect(targetPositions);
        if (groupRect == null) return;

        HudPlacementResolver.Rect resolved = HudPlacementResolver.findNearestFree(
            groupRect,
            getBlockingRects(targets),
            this.width,
            this.height
        );
        int dx = resolved.x - groupRect.x;
        int dy = resolved.y - groupRect.y;

        for (var entry : targetPositions.entrySet()) {
            java.awt.Point pos = entry.getValue();
            applyElementScreenPosition(entry.getKey(), pos.x + dx, pos.y + dy);
        }
    }

    private HudPlacementResolver.Rect buildGroupRect(java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> targetPositions) {
        if (targetPositions.isEmpty()) return null;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (var entry : targetPositions.entrySet()) {
            com.eymistaken.simplecps.api.IHudElement element = entry.getKey();
            java.awt.Point pos = entry.getValue();
            int w = Math.max(1, element.getWidth());
            int h = Math.max(1, element.getHeight());
            minX = Math.min(minX, pos.x);
            minY = Math.min(minY, pos.y);
            maxX = Math.max(maxX, pos.x + w);
            maxY = Math.max(maxY, pos.y + h);
        }

        return new HudPlacementResolver.Rect(minX, minY, maxX - minX, maxY - minY);
    }

    private java.util.List<HudPlacementResolver.Rect> getBlockingRects(java.util.Set<com.eymistaken.simplecps.api.IHudElement> movingElements) {
        java.util.List<HudPlacementResolver.Rect> blockers = new java.util.ArrayList<>();
        for (com.eymistaken.simplecps.api.IHudElement element : getAllActiveElements()) {
            if (movingElements.contains(element)) continue;
            int w = element.getWidth();
            int h = element.getHeight();
            if (w <= 0 || h <= 0) continue;
            blockers.add(new HudPlacementResolver.Rect(logicalX(element), logicalY(element), w, h));
        }
        return blockers;
    }

    // --- Smooth movement ---
    //
    // Every position change in this screen funnels through applyElementScreenPosition,
    // so easing here covers dragging, magnet snapping, nudging, align/distribute,
    // overlap resolution and Reset Position in one place. The eased value is written
    // into the config, which is what the resolver, the modules and Armor's individual
    // slots all read from — so nothing else in the mod has to know about the animation.

    private static final float GLIDE_DURATION = 0.16f;

    /**
     * One element's move. The element is drawn at {@code target + residual}: the
     * target is written through instantly, and only the residual is animated. That
     * split is what keeps free dragging perfectly 1:1 — easing the position itself
     * makes the pixel steps uneven whenever the cursor changes speed, which reads as
     * stutter.
     */
    private static final class Glide {
        int targetX;
        int targetY;
        /** How far behind the target it is still drawing; always eased back to zero. */
        final Anim residualX = new Anim(0f);
        final Anim residualY = new Anim(0f);

        Glide(int startX, int startY) {
            targetX = startX;
            targetY = startY;
            // snap() marks the Anim initialized; without it the first update() would
            // jump straight to zero and there would be no animation at all.
            residualX.snap(0f);
            residualY.snap(0f);
        }
    }

    private final java.util.Map<com.eymistaken.simplecps.api.IHudElement, Glide> glides = new java.util.HashMap<>();
    /** True once a glide has actually drawn off-target, so settling is worth a save. */
    private boolean glideMoved = false;

    /**
     * Where an element is really going, as opposed to where it currently draws.
     * Anything that computes a new position <em>from</em> the old one must use this:
     * reading the mid-animation position instead would make held arrow keys crawl and
     * would align elements to wherever they happened to be that frame.
     */
    private int logicalX(com.eymistaken.simplecps.api.IHudElement el) {
        Glide glide = glides.get(el);
        return glide != null ? glide.targetX : el.getX();
    }

    private int logicalY(com.eymistaken.simplecps.api.IHudElement el) {
        Glide glide = glides.get(el);
        return glide != null ? glide.targetY : el.getY();
    }

    /** Move an element with no cursor behind it, so the whole move is eased. */
    private void applyElementScreenPosition(com.eymistaken.simplecps.api.IHudElement el, int targetX, int targetY) {
        applyElementScreenPosition(el, targetX, targetY, 0, 0);
    }

    /**
     * Send an element to a screen position.
     *
     * <p>{@code freeDX/freeDY} is how far the cursor moved this frame. Whatever part
     * of the move that explains is written straight through; only the remainder — the
     * magnet pulling onto a guide, a grid cell boundary, overlap resolution, an align
     * command — is handed to the residual and eased. Cursor-following therefore stays
     * exact, with no lag and no rounding stutter.
     *
     * <p>Over-reporting a jump is harmless: when the element is already sitting on its
     * target the recomputed residual is zero and nothing animates.
     */
    private void applyElementScreenPosition(
        com.eymistaken.simplecps.api.IHudElement el, int targetX, int targetY, int freeDX, int freeDY
    ) {
        Glide glide = glides.get(el);
        if (glide == null) {
            glide = new Glide(el.getX(), el.getY());
            glides.put(el, glide);
        }

        if (Math.abs((targetX - glide.targetX) - freeDX) > 1) {
            glide.residualX.snap(glide.targetX + glide.residualX.get() - targetX);
        }
        if (Math.abs((targetY - glide.targetY) - freeDY) > 1) {
            glide.residualY.snap(glide.targetY + glide.residualY.get() - targetY);
        }
        glide.targetX = targetX;
        glide.targetY = targetY;
    }

    /** Step every in-flight move one frame. Runs before the live preview is drawn. */
    private void advanceGlides() {
        if (glides.isEmpty()) return;

        boolean allSettled = true;
        for (var entry : glides.entrySet()) {
            Glide glide = entry.getValue();
            int rx = Math.round(glide.residualX.update(0f, GLIDE_DURATION, Easings::backOut));
            int ry = Math.round(glide.residualY.update(0f, GLIDE_DURATION, Easings::backOut));
            writeElementScreenPosition(entry.getKey(), glide.targetX + rx, glide.targetY + ry);
            if (rx != 0 || ry != 0) {
                allSettled = false;
                glideMoved = true;
            }
        }
        // Mid-drag the cursor pausing is not the end of the move, and the release path
        // saves on its own.
        if (!allSettled || draggingElement != null) return;

        glides.clear();
        if (glideMoved) {
            // The config now holds the final position, not the frame it stopped on.
            glideMoved = false;
            SimpleCPSConfig.save();
        }
    }

    /** Land everything where it was headed; the screen is going away. */
    private void finishGlides() {
        if (glides.isEmpty()) return;
        for (var entry : glides.entrySet()) {
            Glide glide = entry.getValue();
            writeElementScreenPosition(entry.getKey(), glide.targetX, glide.targetY);
        }
        glides.clear();
        glideMoved = false;
        SimpleCPSConfig.save();
    }

    @Override
    public void removed() {
        finishGlides();
        super.removed();
    }

    /** Turn a screen position into the anchor + offset pair the config stores. */
    private void writeElementScreenPosition(com.eymistaken.simplecps.api.IHudElement el, int targetX, int targetY) {
        if (el instanceof com.eymistaken.simplecps.api.HudModule module) {
            int w = Math.max(1, module.getWidth());
            int h = Math.max(1, module.getHeight());
            int centerX = targetX + w / 2;
            int centerY = targetY + h / 2;

            SimpleCPSConfig.Position newAnchor = determineAnchor(centerX, centerY);
            Point base = HudPlacementResolver.getBasePos(newAnchor, this.width, this.height, w, h);

            module.setPositionType(newAnchor);
            module.setXOffset(targetX - base.x);
            module.setYOffset(targetY - base.y);
        } else {
            com.eymistaken.simplecps.api.HudModule parent = findParentModule(el);
            if (parent != null) {
                el.setXOffset(targetX - parent.getX());
                el.setYOffset(targetY - parent.getY());
            } else {
                el.setXOffset(targetX);
                el.setYOffset(targetY);
            }
        }
    }

    private com.eymistaken.simplecps.api.HudModule findParentModule(com.eymistaken.simplecps.api.IHudElement el) {
        for (com.eymistaken.simplecps.api.HudModule module : HudModuleManager.getInstance().getModules()) {
            if (module.getSubElements().contains(el)) {
                return module;
            }
        }
        return null;
    }

    private void nudgeElements(int dx, int dy) {
        java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> targetPositions = new java.util.HashMap<>();
        for (com.eymistaken.simplecps.api.IHudElement el : selectedElements) {
            targetPositions.put(el, new java.awt.Point(logicalX(el) + dx, logicalY(el) + dy));
        }
        commitTargetPositions(targetPositions);
    }

    /**
     * Move elements to explicit screen positions and persist the result. Shared by
     * arrow-key nudging and the align/distribute toolbar so all three honour
     * {@code preventOverlap} and mark what they touch as manually placed.
     */
    private void commitTargetPositions(java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> targetPositions) {
        if (targetPositions.isEmpty()) return;
        java.util.Set<com.eymistaken.simplecps.api.IHudElement> targets = new java.util.HashSet<>(targetPositions.keySet());

        if (SimpleCPSConfig.instance.preventOverlap) {
            resolveAndApplyTargetPositions(targets, targetPositions);
        } else {
            for (var entry : targetPositions.entrySet()) {
                java.awt.Point pos = entry.getValue();
                applyElementScreenPosition(entry.getKey(), pos.x, pos.y);
            }
        }

        for (com.eymistaken.simplecps.api.IHudElement el : targets) {
            HudPlacementResolver.setManualLayout(el, HudModuleManager.getInstance().getModules(), true);
            el.onPositionUpdated();
        }
        SimpleCPSConfig.save();
    }

    // --- Align / distribute ---

    private boolean isAlignToolbarVisible() {
        return selectedElements.size() >= 2 && !textPromptActive && textEditTarget == null;
    }

    private int alignToolbarWidth() {
        return ALIGN_LABELS.length * ALIGN_BTN_W + (ALIGN_LABELS.length - 1) * ALIGN_BTN_GAP;
    }

    private int alignToolbarX() {
        return (this.width - alignToolbarWidth()) / 2;
    }

    /** Index of the toolbar button under the cursor, or -1. */
    private int alignButtonAt(int mouseX, int mouseY) {
        if (!isAlignToolbarVisible()) return -1;
        if (mouseY < ALIGN_TOOLBAR_Y || mouseY >= ALIGN_TOOLBAR_Y + ALIGN_BTN_H) return -1;
        int relX = mouseX - alignToolbarX();
        if (relX < 0) return -1;
        int step = ALIGN_BTN_W + ALIGN_BTN_GAP;
        int index = relX / step;
        if (index >= ALIGN_LABELS.length) return -1;
        if (relX % step >= ALIGN_BTN_W) return -1; // in the gap between two buttons
        return index;
    }

    private boolean isAlignActionEnabled(int index) {
        return index < FIRST_DISTRIBUTE_ACTION || selectedElements.size() >= 3;
    }

    private void renderAlignToolbar(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!isAlignToolbarVisible()) return;

        int x = alignToolbarX();
        int y = ALIGN_TOOLBAR_Y;
        int hovered = alignButtonAt(mouseX, mouseY);

        for (int i = 0; i < ALIGN_LABELS.length; i++) {
            int bx = x + i * (ALIGN_BTN_W + ALIGN_BTN_GAP);
            boolean enabled = isAlignActionEnabled(i);
            boolean hover = enabled && i == hovered;

            context.fill(bx, y, bx + ALIGN_BTN_W, y + ALIGN_BTN_H, hover ? 0xFF335566 : 0xE0222222);
            int border = enabled ? 0xFF00FFFF : 0xFF555555;
            context.fill(bx, y, bx + ALIGN_BTN_W, y + 1, border);
            context.fill(bx, y + ALIGN_BTN_H - 1, bx + ALIGN_BTN_W, y + ALIGN_BTN_H, border);
            context.fill(bx, y, bx + 1, y + ALIGN_BTN_H, border);
            context.fill(bx + ALIGN_BTN_W - 1, y, bx + ALIGN_BTN_W, y + ALIGN_BTN_H, border);

            context.centeredText(this.font, EymHudFonts.text(ALIGN_LABELS[i]),
                bx + ALIGN_BTN_W / 2, y + 5, enabled ? 0xFFFFFFFF : 0xFF777777);
        }

        // The two-letter labels mean nothing on their own, so name the hovered one.
        if (hovered >= 0) {
            String tip = ALIGN_TOOLTIPS[hovered];
            if (!isAlignActionEnabled(hovered)) tip = tip + " (needs 3+)";
            context.centeredText(this.font, EymHudFonts.text(tip),
                this.width / 2, y + ALIGN_BTN_H + 4, 0xFFAAAAAA);
        }
    }

    private void runAlignAction(int index) {
        if (!isAlignActionEnabled(index)) return;
        switch (index) {
            case 0, 1, 2, 3, 4, 5 -> alignSelection(index);
            case 6 -> distributeSelection(true);
            case 7 -> distributeSelection(false);
            default -> { }
        }
    }

    /** {@code mode}: 0 left, 1 same centre X, 2 right, 3 top, 4 same centre Y, 5 bottom. */
    private void alignSelection(int mode) {
        if (selectedElements.size() < 2) return;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (com.eymistaken.simplecps.api.IHudElement el : selectedElements) {
            minX = Math.min(minX, logicalX(el));
            minY = Math.min(minY, logicalY(el));
            maxX = Math.max(maxX, logicalX(el) + el.getWidth());
            maxY = Math.max(maxY, logicalY(el) + el.getHeight());
        }
        int groupCenterX = (minX + maxX) / 2;
        int groupCenterY = (minY + maxY) / 2;

        java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> targets = new java.util.HashMap<>();
        for (com.eymistaken.simplecps.api.IHudElement el : selectedElements) {
            int x = logicalX(el);
            int y = logicalY(el);
            switch (mode) {
                case 0 -> x = minX;
                case 1 -> x = groupCenterX - el.getWidth() / 2;
                case 2 -> x = maxX - el.getWidth();
                case 3 -> y = minY;
                case 4 -> y = groupCenterY - el.getHeight() / 2;
                case 5 -> y = maxY - el.getHeight();
                default -> { }
            }
            targets.put(el, new java.awt.Point(x, y));
        }
        commitTargetPositions(targets);
    }

    /**
     * Even out the gaps between the selected elements along one axis. The outermost
     * two stay put and everything between them is respaced, which is what makes this
     * different from aligning.
     */
    private void distributeSelection(boolean horizontal) {
        if (selectedElements.size() < 3) return;

        java.util.List<com.eymistaken.simplecps.api.IHudElement> ordered = new java.util.ArrayList<>(selectedElements);
        ordered.sort(java.util.Comparator.comparingInt(
            (com.eymistaken.simplecps.api.IHudElement el) ->
                horizontal ? logicalX(el) + el.getWidth() / 2 : logicalY(el) + el.getHeight() / 2));

        com.eymistaken.simplecps.api.IHudElement first = ordered.get(0);
        com.eymistaken.simplecps.api.IHudElement last = ordered.get(ordered.size() - 1);
        int start = horizontal ? logicalX(first) : logicalY(first);
        int end = horizontal
            ? logicalX(last) + last.getWidth()
            : logicalY(last) + last.getHeight();

        int occupied = 0;
        for (com.eymistaken.simplecps.api.IHudElement el : ordered) {
            occupied += horizontal ? el.getWidth() : el.getHeight();
        }

        // Float cursor, not an int gap: rounding each step would drift the last
        // element several pixels away from where it started.
        float gap = (end - start - occupied) / (float) (ordered.size() - 1);

        java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> targets = new java.util.HashMap<>();
        float cursor = start;
        for (com.eymistaken.simplecps.api.IHudElement el : ordered) {
            int pos = Math.round(cursor);
            targets.put(el, horizontal
                ? new java.awt.Point(pos, el.getY())
                : new java.awt.Point(el.getX(), pos));
            cursor += (horizontal ? el.getWidth() : el.getHeight()) + gap;
        }
        commitTargetPositions(targets);
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.input();
        if (textPromptActive) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                java.util.function.Consumer<String> cb = textPromptCallback;
                String val = textPromptBuffer;
                closeTextPrompt();
                if (cb != null) cb.accept(val);
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeTextPrompt();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!textPromptBuffer.isEmpty()) {
                    textPromptBuffer = textPromptBuffer.substring(0, textPromptBuffer.length() - 1);
                }
                return true;
            }
            return true; // swallow all other keys while typing (no nudging, etc.)
        }
        if (textEditTarget != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                textEditTarget.setter.accept(textEditBuffer);
                SimpleCPSConfig.save();
                textEditTarget = null;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                textEditTarget = null;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!textEditBuffer.isEmpty()) {
                    textEditBuffer = textEditBuffer.substring(0, textEditBuffer.length() - 1);
                }
                return true;
            }
        }
        
        if (textEditTarget == null && !selectedElements.isEmpty()) {
            int dx = 0;
            int dy = 0;
            if (keyCode == GLFW.GLFW_KEY_UP) {
                dy = -1;
            } else if (keyCode == GLFW.GLFW_KEY_DOWN) {
                dy = 1;
            } else if (keyCode == GLFW.GLFW_KEY_LEFT) {
                dx = -1;
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                dx = 1;
            }
            
            if (dx != 0 || dy != 0) {
                nudgeElements(dx, dy);
                return true;
            }
        }
        
        return super.keyPressed(keyInput);
    }
}
