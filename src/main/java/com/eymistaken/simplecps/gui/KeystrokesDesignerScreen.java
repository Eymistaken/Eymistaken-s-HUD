package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Stack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;


public class KeystrokesDesignerScreen extends Screen {

    /**
     * Always read the config through this, never into a captured field: applying a
     * preset or a share code replaces {@link SimpleCPSConfig#instance} wholesale, and
     * a held reference would go on writing into the discarded object.
     */
    private static SimpleCPSConfig config() {
        return SimpleCPSConfig.instance;
    }
    
    // State Machine Vars
    private enum InteractionState {
        IDLE,
        BOX_SELECTED, // One or more buttons selected
        TEXT_EDIT_MODE, // Specialized mode for label editing
        DRAGGING_BOX, // Moving buttons
        TEXT_DRAGGING, // Moving Label (Right Click Drag)
        RESIZING // Scaling via scroll (handled in state check)
    }
    private InteractionState currentState = InteractionState.IDLE;
    private final List<SimpleCPSConfig.KeyButtonData> selectedButtons = new ArrayList<>();
    
    // Undo/Redo
    private final Stack<String> undoStack = new Stack<>();
    private final Stack<String> redoStack = new Stack<>();
    private final Gson gson = new Gson();

    // Right Click Logic
    private double rightClickStartX, rightClickStartY;
    private boolean isRightClickDrag = false;
    private SimpleCPSConfig.KeyButtonData rightClickTarget = null;

    // Polling State restoration removed

    // Drag offsets & State
    private double dragStartX, dragStartY;
    // We need a map or relative offsets for multi-drag? 
    // Just store delta since drag start for simplicity, applied to original positions.
    // Actually, storing original pos for ALL selected buttons is safer.
    private static class ButtonSnapshot {
        int x, y, w, h, labelX, labelY;
        SimpleCPSConfig.KeyButtonData btn;
        
        ButtonSnapshot(SimpleCPSConfig.KeyButtonData btn) {
            this.btn = btn;
            this.x = btn.x; this.y = btn.y;
            this.w = btn.w; this.h = btn.h;
            this.labelX = btn.labelX; this.labelY = btn.labelY;
        }
    }
    private final List<ButtonSnapshot> dragSnapshots = new ArrayList<>();

    // Enum for Resize handles - REMOVED for Scroll Scaling, but keeping enum if needed for logic or remove entirely?
    // User said "Remove drag-to-resize handles".
    // We can remove the enum and logic.
    
    // Snapping lines
    private Integer snapX = null;
    private Integer snapY = null;
    
    private final Screen parent;

    public KeystrokesDesignerScreen(Screen parent) {
        super(Component.nullToEmpty("Keystrokes Designer"));
        this.parent = parent;
    }
    
    public KeystrokesDesignerScreen() {
        this(null);
    }

    // Polling State for double click detection?
    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_INTERVAL = 250;

    // Canvas Logic
    private static final int CANVAS_W = 400;
    private static final int CANVAS_H = 300;

    // Inline Color Picker State
    private boolean colorPickerOpen = false;
    private boolean colorPickerTargetPressed = false;
    private SimpleCPSConfig.KeyButtonData colorPickerTarget = null;
    private float cpHue = 0.0f;
    private float cpSat = 1.0f;
    private float cpVal = 1.0f;
    private enum CpDrag { NONE, SV, HUE }
    private CpDrag cpDragTarget = CpDrag.NONE;

    private boolean cpHexBoxActive = false;
    private String cpHexInput = "";
    private long cpCopiedTime = 0;

    // Context Menu
    private boolean contextMenuOpen = false;
    private int contextMenuX, contextMenuY;
    private SimpleCPSConfig.KeyButtonData contextMenuTarget = null;
    private static final int CONTEXT_MENU_WIDTH = 140;
    /** Gap kept between a menu and the screen edge. */
    private static final int MENU_MARGIN = 2;
    private final com.eymistaken.simplecps.util.Anim contextMenuAnim = new com.eymistaken.simplecps.util.Anim(0f);
    private static final float MENU_ANIM_DURATION = 0.16f;
    /** Below this alpha a closing menu is invisible anyway, so stop drawing it. */
    private static final float FADE_OUT_CUTOFF = 0.01f;
    /**
     * The target the menu was opened on, held past the moment it closes. Deleting a
     * button clears {@code contextMenuTarget}, which would otherwise switch the menu to
     * its shorter empty-space form halfway through its own fade-out.
     */
    private SimpleCPSConfig.KeyButtonData contextMenuRenderTarget = null;

    /** Scale an ARGB color's alpha by menu-open progress {@code p}. */
    private static int fade(int argb, float p) {
        return com.eymistaken.simplecps.util.RenderUtil.withAlpha(argb, p);
    }

    // --- Smooth movement ---
    //
    // Same idea as the HUD editor: the button's stored x/y jumps straight to where it
    // belongs, and only the leftover — the magnet pulling it onto a guide, an arrow-key
    // step — is drawn as an offset that eases back to zero. Cursor-following therefore
    // stays exactly 1:1.

    private static final float GLIDE_DURATION = 0.16f;

    private static final class Glide {
        final com.eymistaken.simplecps.util.Anim x = new com.eymistaken.simplecps.util.Anim(0f);
        final com.eymistaken.simplecps.util.Anim y = new com.eymistaken.simplecps.util.Anim(0f);

        Glide() {
            // snap() marks the Anim initialized; without it the first update() would
            // jump straight to zero and there would be no animation at all.
            x.snap(0f);
            y.snap(0f);
        }
    }

    private final java.util.Map<SimpleCPSConfig.KeyButtonData, Glide> glides = new java.util.HashMap<>();
    /** Magnet correction carried by the previous drag frame, to spot changes in it. */
    private int prevSnapCorrX = 0;
    private int prevSnapCorrY = 0;

    /** Draw a button {@code dx,dy} away from where it now is, then ease that away. */
    private void addGlideOffset(SimpleCPSConfig.KeyButtonData btn, int dx, int dy) {
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
            int rx = Math.round(glide.x.update(0f, GLIDE_DURATION, com.eymistaken.simplecps.util.Easings::backOut));
            int ry = Math.round(glide.y.update(0f, GLIDE_DURATION, com.eymistaken.simplecps.util.Easings::backOut));
            return rx == 0 && ry == 0;
        });
    }

    /** Open at the cursor, flipping to its other side when that would run off screen. */
    private int placeMenuX(int mouseX, int w) {
        int x = (mouseX + w + MENU_MARGIN > this.width) ? mouseX - w : mouseX;
        return Math.max(MENU_MARGIN, Math.min(x, this.width - w - MENU_MARGIN));
    }

    private int placeMenuY(int mouseY, int h) {
        int y = (mouseY + h + MENU_MARGIN > this.height) ? mouseY - h : mouseY;
        return Math.max(MENU_MARGIN, Math.min(y, this.height - h - MENU_MARGIN));
    }
    
    // Editing
    private boolean waitingForKeybind = false;
    // Helper to debounce key presses removed

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        advanceGlides();

        // Gradient Background (matching HudEditorScreen)
        context.fillGradient(0, 0, this.width, this.height, 0xC0000000, 0xD0000000);
        
        // Polling Input removed

        // Draw Canvas (Centered Safe Area)
        int centerX = width / 2;
        int centerY = height / 2;
        int originX = centerX - 33;
        int originY = centerY - 48;
        int cw = CANVAS_W / 2;
        int ch = CANVAS_H / 2;
        
        // Canvas Background
        context.fill(centerX - cw, centerY - ch, centerX + cw, centerY + ch, 0x22FFFFFF); 
        // Canvas Border
        drawBorder(context, centerX - cw, centerY - ch, CANVAS_W, CANVAS_H, 0x44FFFFFF);

        // Snap Lines
        if (snapX != null) context.fill(snapX, 0, snapX + 1, height, 0xFFFF00FF);
        if (snapY != null) context.fill(0, snapY, width, snapY + 1, 0xFFFF00FF);

        // Draw Buttons
        for (SimpleCPSConfig.KeyButtonData btn : config().keystrokesLayout) {
            // The glide offset is purely visual — btn.x/y are already final, so nothing
            // else in the designer has to know this animation exists.
            int x = originX + btn.x + glideX(btn);
            int y = originY + btn.y + glideY(btn);
            boolean isSelected = selectedButtons.contains(btn);
            
            int bgColor = 0xAA000000;
            if (btn == contextMenuTarget) bgColor = 0xAA444400; // Highlight target
            
            // Visual Styles for State
            int borderColor = 0xFFFFFFFF; // Default White
            if (isSelected) {
                if (currentState == InteractionState.TEXT_EDIT_MODE) {
                     // Blue border but focuses on text?
                     borderColor = 0xFF00FFFF; // Cyan for box context
                } else {
                     borderColor = 0xFF00FF00; // Green (or Blue per request) for Box Mode
                }
            }
            
            context.fill(x, y, x + btn.w, y + btn.h, bgColor);
            drawBorder(context, x, y, btn.w, btn.h, borderColor);
            
            // Label
            String label = btn.label;
            if (waitingForKeybind && isSelected) label = "...";

            net.minecraft.network.chat.MutableComponent text = net.minecraft.network.chat.Component.literal(label);
            net.minecraft.network.chat.Style style = com.eymistaken.simplecps.util.EymHudFonts.activeStyle();
            if (btn.bold) style = style.withBold(true);
            if (btn.italic) style = style.withItalic(true);
            if (btn.underlined) style = style.withUnderlined(true);
            text.setStyle(style);

            int labelW = font.width(text);
            int labelH = font.lineHeight;
            
            int lx = (btn.labelX == -1) ? x + (btn.w - labelW) / 2 : x + btn.labelX;
            int ly = (btn.labelY == -1) ? y + (btn.h - labelH) / 2 : y + btn.labelY;
            
            // Dynamic Label Centering for Mouse Buttons with CPS
            if (btn.isMouse && btn.showCps && btn.labelY == -1) {
                ly -= 4; // Shift up 4px to make room
            }
            
            int lblColor = (btn.btnColor != -1 && btn.btnColor != 0) ? (btn.btnColor | 0xFF000000) : 0xFFFFFFFF;
            context.text(font, text, lx, ly, lblColor, btn.shadow);
            
            // Render CPS Preview
            if (btn.isMouse && btn.showCps) {
                String cpsPreview = "0"; 
                int cpsW = font.width(com.eymistaken.simplecps.util.EymHudFonts.text(cpsPreview));
                // Center CPS horizontally
                int cpsX = x + (btn.w - cpsW) / 2;
                int cpsY = ly + labelH + 1; // Below label
                
                context.text(font, com.eymistaken.simplecps.util.EymHudFonts.text(cpsPreview), cpsX, cpsY, 0xFFAAAAAA, btn.shadow);
            }
            
            // TEXT EDIT MODE VISUAL
            if (isSelected && currentState == InteractionState.TEXT_EDIT_MODE) {
                // Dotted Yellow Border around text
                drawBorder(context, lx - 2, ly - 2, labelW + 4, labelH + 4, 0xFFFFFF00);
            }
            
            // Draw visual indicator for specific button types (Mouse)
            if (btn.isMouse) {
                  // e.g. CPS count preview if enabled? 
            }
        }
        
        context.text(font, com.eymistaken.simplecps.util.EymHudFonts.text("Click: Select | Ctrl+Click: Multi | Dbl Click: Text Mode | Scroll: resize"), 10, 10, 0xFFFFFF, true);
        context.text(font, com.eymistaken.simplecps.util.EymHudFonts.text("Right-Drag: Move Text | Left-Drag: Move Box"), 10, 22, 0xAAAAAA, true);

        // Visual Aid for Centering (Red Lines)
        if (currentState == InteractionState.TEXT_DRAGGING && rightClickTarget != null) {
            int cx = originX + rightClickTarget.x + rightClickTarget.w / 2;
            int cy = originY + rightClickTarget.y + rightClickTarget.h / 2;
            
            // If label is centered horizontally
            if (rightClickTarget.labelX == -1) {
                context.fill(cx, originY + rightClickTarget.y, cx + 1, originY + rightClickTarget.y + rightClickTarget.h, 0xFFFF0000);
            }
            // If label is centered vertically
            if (rightClickTarget.labelY == -1) {
                context.fill(originX + rightClickTarget.x, cy, originX + rightClickTarget.x + rightClickTarget.w, cy + 1, 0xFFFF0000);
            }
        }

        // Target 1 while open, 0 once closed, advanced every frame: the same tween
        // that fades the menu in also fades it back out.
        if (contextMenuOpen) contextMenuRenderTarget = contextMenuTarget;
        float menuP = contextMenuAnim.update(contextMenuOpen ? 1f : 0f, MENU_ANIM_DURATION,
            com.eymistaken.simplecps.util.Easings::expoOut);
        if (contextMenuOpen || menuP > FADE_OUT_CUTOFF) {
            renderContextMenu(context, mouseX, mouseY, menuP, contextMenuRenderTarget);
        }
        
        if (colorPickerOpen && colorPickerTarget != null) {
            renderColorPicker(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (colorPickerOpen && colorPickerTarget != null) {
            int[] bnds = getPickerBounds();
            if (mouseX >= bnds[0] && mouseX <= bnds[0] + 160 && mouseY >= bnds[1] && mouseY <= bnds[1] + 180) {
                if (button == 0) {
                    int cx = bnds[0] + 10;
                    
                    int hexBoxX = cx;
                    int hexBoxY = bnds[1] + 115;
                    int hexBoxW = 60;
                    int hexBoxH = 15;
                    int copyBtnX = hexBoxX + hexBoxW + 5;
                    int copyBtnY = hexBoxY;
                    int copyBtnW = 40;
                    int copyBtnH = 15;

                    if (mouseX >= hexBoxX && mouseX <= hexBoxX + hexBoxW && mouseY >= hexBoxY && mouseY <= hexBoxY + hexBoxH) {
                        cpHexBoxActive = true;
                        cpHexInput = String.format("#%06X", getCurrentPickerColor() & 0xFFFFFF);
                        return true;
                    } else {
                        cpHexBoxActive = false;
                    }
                    
                    if (mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + copyBtnH) {
                        if (this.minecraft != null) {
                            this.minecraft.keyboardHandler.setClipboard(String.format("#%06X", getCurrentPickerColor() & 0xFFFFFF));
                            cpCopiedTime = System.currentTimeMillis();
                        }
                        return true;
                    }

                    if (mouseX >= cx && mouseX <= cx + 140 && mouseY >= bnds[1] + 10 && mouseY <= bnds[1] + 90) {
                        cpDragTarget = CpDrag.SV;
                        cpSat = (float)((mouseX - cx) / 140.0);
                        cpVal = 1.0f - (float)((mouseY - (bnds[1] + 10)) / 80.0);
                        cpSat = Math.max(0, Math.min(1, cpSat));
                        cpVal = Math.max(0, Math.min(1, cpVal));
                    } else if (mouseX >= cx && mouseX <= cx + 140 && mouseY >= bnds[1] + 95 && mouseY <= bnds[1] + 105) {
                        cpDragTarget = CpDrag.HUE;
                        cpHue = (float)((mouseX - cx) / 140.0);
                        cpHue = Math.max(0, Math.min(1, cpHue));
                    } else if (mouseX >= cx + 50 && mouseX <= cx + 93 && mouseY >= bnds[1] + 145 && mouseY <= bnds[1] + 170) {
                        // Apply Clicked
                        if (colorPickerTargetPressed) {
                            colorPickerTarget.btnPressedColor = getCurrentPickerColor();
                        } else {
                            colorPickerTarget.btnColor = getCurrentPickerColor();
                        }
                        SimpleCPSConfig.save();
                        colorPickerOpen = false;
                    } else if (mouseX >= cx + 97 && mouseX <= cx + 140 && mouseY >= bnds[1] + 145 && mouseY <= bnds[1] + 170) {
                        // Reset Clicked
                        if (colorPickerTargetPressed) {
                            colorPickerTarget.btnPressedColor = -1;
                        } else {
                            colorPickerTarget.btnColor = -1;
                        }
                        SimpleCPSConfig.save();
                        colorPickerOpen = false;
                    }
                }
                return true;
            } else {
                colorPickerOpen = false;
                return true;
            }
        }
        
        if (contextMenuOpen) {
             // Handle Menu Clicks
             int w = CONTEXT_MENU_WIDTH;
             int menuY = contextMenuY;
             int fullH = getMenuHeight(contextMenuTarget != null);
             
             // No dynamic calculation here! Use stored constraints.
             if (mouseX >= contextMenuX && mouseX <= contextMenuX + w && mouseY >= menuY && mouseY <= menuY + fullH) {
                 int index = (int)((mouseY - menuY) / 20);
                 handleMenuAction(index);
                 contextMenuOpen = false;
                 return true;
             }
             contextMenuOpen = false;
             return true; 
        }

        int originX = width / 2 - 33;
        int originY = height / 2 - 48;
        int mX = (int)mouseX;
        int mY = (int)mouseY;
        
        // Find Hit
        SimpleCPSConfig.KeyButtonData hitBtn = null;
        List<SimpleCPSConfig.KeyButtonData> layout = config().keystrokesLayout;
        for (int i = layout.size() - 1; i >= 0; i--) {
            SimpleCPSConfig.KeyButtonData btn = layout.get(i);
            if (mX >= originX + btn.x && mX <= originX + btn.x + btn.w && 
                mY >= originY + btn.y && mY <= originY + btn.y + btn.h) {
                hitBtn = btn;
                break;
            }
        }

        if (button == 0) { // Left Click
            long now = System.currentTimeMillis();
            boolean doubleClick = (now - lastClickTime < DOUBLE_CLICK_INTERVAL);
            lastClickTime = now;

            if (hitBtn != null) {
                if (doubleClick) {
                    saveUndo();
                    currentState = InteractionState.TEXT_EDIT_MODE;
                    selectedButtons.clear();
                    selectedButtons.add(hitBtn);
                    return true;
                }

                boolean ctrl = isCtrlDown();
                
                if (currentState == InteractionState.TEXT_EDIT_MODE) {
                    if (!selectedButtons.contains(hitBtn)) {
                        currentState = InteractionState.BOX_SELECTED;
                        selectedButtons.clear();
                        selectedButtons.add(hitBtn);
                    }
                } else {
                    if (ctrl) {
                        if (selectedButtons.contains(hitBtn)) selectedButtons.remove(hitBtn);
                        else selectedButtons.add(hitBtn);
                    } else {
                        if (!selectedButtons.contains(hitBtn)) {
                             selectedButtons.clear();
                             selectedButtons.add(hitBtn);
                        }
                    }
                    currentState = InteractionState.BOX_SELECTED;
                    
                    // Bring to front
                    if (selectedButtons.contains(hitBtn)) {
                         config().keystrokesLayout.remove(hitBtn);
                         config().keystrokesLayout.add(hitBtn);
                    }
                }
                
                
                // Prepare Drag (Left = Box)
                if (currentState == InteractionState.BOX_SELECTED) {
                    saveUndo(); // Save state before starting drag
                    currentState = InteractionState.DRAGGING_BOX;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    dragSnapshots.clear();
                    prevSnapCorrX = 0;
                    prevSnapCorrY = 0;
                    for (SimpleCPSConfig.KeyButtonData b : selectedButtons) {
                        dragSnapshots.add(new ButtonSnapshot(b));
                    }
                }

            } else {
                // Clicked Empty Space
                if (currentState == InteractionState.TEXT_EDIT_MODE) {
                    currentState = InteractionState.IDLE;
                } else {
                    selectedButtons.clear();
                    currentState = InteractionState.IDLE;
                }
            }
            return true;
            
        } else if (button == 1) { // Right Click
            if (hitBtn != null) {
                rightClickTarget = hitBtn;
                rightClickStartX = mouseX;
                rightClickStartY = mouseY;
                isRightClickDrag = false; 
                // Don't open menu yet, wait for release
                return true;
            } else {
                // Right click on empty space -> Immediate Context Menu
                contextMenuTarget = null;
                contextMenuOpen = true;
                contextMenuAnim.snap(0f);
                // This path used to place the menu at the raw cursor with no clamping
                // at all, so it ran straight off the right and bottom edges.
                contextMenuX = placeMenuX(mX, CONTEXT_MENU_WIDTH);
                contextMenuY = placeMenuY(mY, getMenuHeight(false));
                return true;
            }
        }

        return false; // Handled manually
    }
    
    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (colorPickerOpen) {
            if (cpDragTarget != CpDrag.NONE) {
                int[] bnds = getPickerBounds();
                int cx = bnds[0] + 10;
                if (cpDragTarget == CpDrag.SV) {
                    cpSat = (float)((mouseX - cx) / 140.0);
                    cpVal = 1.0f - (float)((mouseY - (bnds[1] + 10)) / 80.0);
                    cpSat = Math.max(0, Math.min(1, cpSat));
                    cpVal = Math.max(0, Math.min(1, cpVal));
                } else if (cpDragTarget == CpDrag.HUE) {
                    cpHue = (float)((mouseX - cx) / 140.0);
                    cpHue = Math.max(0, Math.min(1, cpHue));
                }
                return true;
            }
            return true;
        }
        
        if (button == 0 && currentState == InteractionState.DRAGGING_BOX) { // Left Drag
            double rawDx = mouseX - dragStartX;
            double rawDy = mouseY - dragStartY;
            
            if (selectedButtons.isEmpty()) return false;
            
            for (ButtonSnapshot snap : dragSnapshots) {
                snap.btn.x = snap.x + (int)rawDx;
                snap.btn.y = snap.y + (int)rawDy;
            }
            
            SimpleCPSConfig.KeyButtonData leader = selectedButtons.get(0);
            int freeX = dragSnapshots.get(0).x + (int) rawDx;
            int freeY = dragSnapshots.get(0).y + (int) rawDy;
            applySnapping(leader, true); // Snap Leader

            int finalDx = leader.x - dragSnapshots.get(0).x;
            int finalDy = leader.y - dragSnapshots.get(0).y;

            for (int i = 1; i < dragSnapshots.size(); i++) {
                ButtonSnapshot snap = dragSnapshots.get(i);
                snap.btn.x = snap.x + finalDx;
                snap.btn.y = snap.y + finalDy;
            }

            // How far the magnet is holding the button away from the cursor. A change
            // in that — engaging, releasing, jumping to another guide — is the only
            // discontinuity in the drag, so it is the only part worth easing.
            int corrX = leader.x - freeX;
            int corrY = leader.y - freeY;
            int jumpX = corrX - prevSnapCorrX;
            int jumpY = corrY - prevSnapCorrY;
            prevSnapCorrX = corrX;
            prevSnapCorrY = corrY;
            for (ButtonSnapshot snap : dragSnapshots) {
                addGlideOffset(snap.btn, -jumpX, -jumpY);
            }
            return true;
        } 
        else if (button == 1) { // Right Drag (Text)
            if (rightClickTarget != null) {
                if (!isRightClickDrag) {
                    // Check threshold
                    if (Math.abs(mouseX - rightClickStartX) > 2 || Math.abs(mouseY - rightClickStartY) > 2) {
                        isRightClickDrag = true;
                        currentState = InteractionState.TEXT_DRAGGING;
                        saveUndo();
                        
                        // Select this button if not selected (visualization)
                        if (!selectedButtons.contains(rightClickTarget)) {
                            selectedButtons.clear();
                            selectedButtons.add(rightClickTarget);
                        }
                    }
                }
                
                if (isRightClickDrag) {
                    // Text Movement Logic
                    // Calculate relative mouse position within button
                    int originX = width / 2 - 33;
                    int originY = height / 2 - 48;
                    int btnX = originX + rightClickTarget.x;
                    int btnY = originY + rightClickTarget.y;
                    
                    // We want the text center to follow the mouse? Or just offset?
                    // Let's make it follow offset.
                    // Actually, simpler: Set label pos based on mouse pos relative to button
                    
                    int relX = (int)mouseX - btnX;
                    int relY = (int)mouseY - btnY;
                    
                    // Center check (Threshold 4px)
                    int midX = rightClickTarget.w / 2;
                    int midY = rightClickTarget.h / 2;
                    
                    // Update labelX
                    String label = rightClickTarget.label;
                    int labelW = font.width(com.eymistaken.simplecps.util.EymHudFonts.text(label));
                    int labelH = font.lineHeight;
                    
                    // Calculate Top-Left of label based on mouse being center of label?
                    // Let's assume user grabs "center" of text.
                    int targetLX = relX - labelW / 2;
                    int targetLY = relY - labelH / 2;
                    
                    // Check for centering
                    // Label Center X = targetLX + labelW/2 = relX
                    // Button Center X = w/2
                    if (Math.abs(relX - midX) < 4) {
                        rightClickTarget.labelX = -1; // Snap Center X
                    } else {
                        rightClickTarget.labelX = targetLX;
                    }
                    
                    if (Math.abs(relY - midY) < 4) {
                        rightClickTarget.labelY = -1; // Snap Center Y
                    } else {
                        rightClickTarget.labelY = targetLY;
                    }
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (button == 0) {
            if (currentState == InteractionState.DRAGGING_BOX) {
                currentState = InteractionState.BOX_SELECTED;
                dragSnapshots.clear();
                snapX = null;
                snapY = null;
            }
        } else if (button == 1) {
            if (isRightClickDrag) {
                isRightClickDrag = false;
                rightClickTarget = null;
                currentState = InteractionState.BOX_SELECTED; // Return to normal state
            } else if (rightClickTarget != null) {
                // A click on a button, not a drag. Opening is deferred to the release so
                // that holding and dragging moves the label instead.
                //
                // Only this case belongs here: a right-click on empty space never sets
                // rightClickTarget and already opened its menu on press. Reaching this
                // code for it too would re-snap the animation to zero part-way through,
                // which is visible as a flicker.
                contextMenuTarget = rightClickTarget;
                if (!selectedButtons.contains(rightClickTarget)) {
                     selectedButtons.clear();
                     selectedButtons.add(rightClickTarget);
                     currentState = InteractionState.BOX_SELECTED;
                }

                contextMenuOpen = true;
                contextMenuAnim.snap(0f);

                // Flip to whichever side of the cursor fits, then clamp — the old code
                // only handled the vertical case and let the menu run off the right edge.
                int h = getMenuHeight(true);
                contextMenuX = placeMenuX((int) mouseX, CONTEXT_MENU_WIDTH);
                contextMenuY = placeMenuY((int) mouseY, h);

                rightClickTarget = null;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.input();
        int scanCode = keyInput.scancode();
        int modifiers = keyInput.modifiers();
        
        if (colorPickerOpen && cpHexBoxActive) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!cpHexInput.isEmpty()) {
                    cpHexInput = cpHexInput.substring(0, cpHexInput.length() - 1);
                    tryApplyHexPicker();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                 if (this.minecraft != null) {
                     String clipboard = this.minecraft.keyboardHandler.getClipboard();
                     if (clipboard != null) {
                         clipboard = clipboard.trim();
                         if (clipboard.matches("^#?[0-9a-fA-F]{1,8}$")) {
                             cpHexInput = clipboard.startsWith("#") ? clipboard : "#" + clipboard;
                             tryApplyHexPicker();
                         }
                     }
                 }
                 return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cpHexBoxActive = false;
                return true;
            }
        }

        if (waitingForKeybind) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                 if (contextMenuTarget != null) {
                      contextMenuTarget.keyCode = keyCode;
                      contextMenuTarget.label = getKeyName(keyCode);
                 }
            }
            waitingForKeybind = false;
            return true;
        }

        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (keyCode == GLFW.GLFW_KEY_Z) {
                undo();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
        }

        if (currentState == InteractionState.TEXT_EDIT_MODE && !selectedButtons.isEmpty()) {
            SimpleCPSConfig.KeyButtonData target = selectedButtons.get(0);
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            int step = shift ? 5 : 1;
            
            // Dynamic Size Calculation for smooth transition from Center (-1)
            int textW = font.width(com.eymistaken.simplecps.util.EymHudFonts.text(target.label));
            int textH = font.lineHeight;
            
            if (keyCode == GLFW.GLFW_KEY_UP) target.labelY = (target.labelY == -1 ? (target.h - textH)/2 : target.labelY) - step;
            else if (keyCode == GLFW.GLFW_KEY_DOWN) target.labelY = (target.labelY == -1 ? (target.h - textH)/2 : target.labelY) + step;
            else if (keyCode == GLFW.GLFW_KEY_LEFT) target.labelX = (target.labelX == -1 ? (target.w - textW)/2 : target.labelX) - step;
            else if (keyCode == GLFW.GLFW_KEY_RIGHT) target.labelX = (target.labelX == -1 ? (target.w - textW)/2 : target.labelX) + step;
            
            else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                currentState = InteractionState.BOX_SELECTED; 
            } 
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                 if (!target.label.isEmpty()) target.label = target.label.substring(0, target.label.length() - 1);
            }
            else if (keyCode == GLFW.GLFW_KEY_SPACE) {
                 target.label += " ";
            }
            else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                 String clipboard = minecraft.keyboardHandler.getClipboard();
                 if (clipboard != null && !clipboard.isEmpty()) {
                     target.label += clipboard;
                 }
            }
            return true;
        }
        
        if (currentState == InteractionState.BOX_SELECTED && !selectedButtons.isEmpty()) {
             boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
             boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
             int step = shift ? 5 : 1;
             
             if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN || 
                 keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
                 saveUndo();
             }

             // The glide offset is the opposite of the step, so the button starts drawn
             // where it was and eases into its new spot.
             if (keyCode == GLFW.GLFW_KEY_UP) { for (SimpleCPSConfig.KeyButtonData b : selectedButtons) { b.y -= step; addGlideOffset(b, 0, step); } return true; }
             if (keyCode == GLFW.GLFW_KEY_DOWN) { for (SimpleCPSConfig.KeyButtonData b : selectedButtons) { b.y += step; addGlideOffset(b, 0, -step); } return true; }
             if (keyCode == GLFW.GLFW_KEY_LEFT) { for (SimpleCPSConfig.KeyButtonData b : selectedButtons) { b.x -= step; addGlideOffset(b, step, 0); } return true; }
             if (keyCode == GLFW.GLFW_KEY_RIGHT) { for (SimpleCPSConfig.KeyButtonData b : selectedButtons) { b.x += step; addGlideOffset(b, -step, 0); } return true; }
             
             if (keyCode == GLFW.GLFW_KEY_DELETE) {
                 saveUndo();
                 config().keystrokesLayout.removeAll(selectedButtons);
                 selectedButtons.clear();
                 currentState = InteractionState.IDLE;
                 return true;
             }
             if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                 selectedButtons.clear();
                 currentState = InteractionState.IDLE;
                 return true;
             }
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharacterEvent charInput) {
        char chr = (char) charInput.codepoint();
        int modifiers = 0;
        
        if (colorPickerOpen && cpHexBoxActive) {
            if (cpHexInput.length() < 9 && ((chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F') || chr == '#')) {
                if (chr == '#' && cpHexInput.contains("#")) return super.charTyped(charInput);
                if (cpHexInput.isEmpty() && chr != '#') cpHexInput = "#" + chr;
                else cpHexInput += chr;
                tryApplyHexPicker();
                return true;
            }
        }

        if (currentState == InteractionState.TEXT_EDIT_MODE && !selectedButtons.isEmpty()) {
            SimpleCPSConfig.KeyButtonData target = selectedButtons.get(0);
            target.label += chr;
            return true;
        }
        return super.charTyped(charInput);
    }


    private int getCurrentPickerColor() {
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(cpHue, cpSat, cpVal) & 0xFFFFFF);
    }

    private int[] getPickerBounds() {
        int centerX = width / 2;
        int centerY = height / 2;
        int originX = centerX - 33;
        int originY = centerY - 48;
        
        int bx = originX + colorPickerTarget.x;
        int by = originY + colorPickerTarget.y;
        
        int px = bx + colorPickerTarget.w + 5;
        if (px + 160 > this.width) {
            px = bx - 160 - 5;
        }
        
        int py = by;
        if (py + 180 > this.height) {
            py = this.height - 180 - 5;
        }
        if (py < 5) py = 5;
        
        return new int[]{px, py, 160, 180};
    }

    private void renderColorPicker(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int[] bnds = getPickerBounds();
        int px = bnds[0];
        int py = bnds[1];
        
        context.fill(px, py, px + 160, py + 180, 0xFF222222);
        drawBorder(context, px, py, 160, 180, 0xFFFFFFFF);
        
        int cx = px + 10;
        int cy = py + 10;
        
        // SV Box (140 x 80)
        for (int i = 0; i < 140; i++) {
            float s = i / 140f;
            int c = java.awt.Color.HSBtoRGB(cpHue, s, 1.0f);
            context.fill(cx + i, cy, cx + i + 1, cy + 80, c | 0xFF000000);
        }
        context.fillGradient(cx, cy, cx + 140, cy + 80, 0x00000000, 0xFF000000);
        
        int svCurX = cx + (int)(cpSat * 140);
        int svCurY = cy + (int)((1.0f - cpVal) * 80);
        context.fill(svCurX - 2, svCurY - 2, svCurX + 2, svCurY + 2, 0xFFFFFFFF);
        context.fill(svCurX - 1, svCurY - 1, svCurX + 1, svCurY + 1, 0xFF000000);
        
        // Hue Slider (140 x 10) at py + 95
        cy = py + 95;
        for (int i = 0; i < 140; i++) {
            float h = i / 140f;
            int c = java.awt.Color.HSBtoRGB(h, 1.0f, 1.0f);
            context.fill(cx + i, cy, cx + i + 1, cy + 10, c | 0xFF000000);
        }
        int hCurX = cx + (int)(cpHue * 140);
        context.fill(hCurX - 2, cy - 1, hCurX + 2, cy + 11, 0xFFFFFFFF);
        context.fill(hCurX - 1, cy, hCurX + 1, cy + 10, 0xFF000000);
        
        // Hex Box + Copy Button at Y=115
        cy = py + 115;
        int hexBoxX = cx;
        int hexBoxY = cy;
        int hexBoxW = 60;
        int hexBoxH = 15;
        
        String displayHex = cpHexBoxActive ? cpHexInput + (((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "") : String.format("#%06X", getCurrentPickerColor() & 0xFFFFFF);
        context.fill(hexBoxX, hexBoxY, hexBoxX + hexBoxW, hexBoxY + hexBoxH, 0xFF000000);
        drawBorder(context, hexBoxX, hexBoxY, hexBoxW, hexBoxH, cpHexBoxActive ? 0xFF00FF00 : 0xFFFFFFFF);
        context.text(this.font, com.eymistaken.simplecps.util.EymHudFonts.text(displayHex), hexBoxX + 4, hexBoxY + 4, 0xFFFFFFFF, true);
        
        int copyBtnX = hexBoxX + hexBoxW + 5;
        int copyBtnY = hexBoxY;
        int copyBtnW = 40;
        int copyBtnH = 15;
        boolean copyHovered = mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + copyBtnH;
        context.fill(copyBtnX, copyBtnY, copyBtnX + copyBtnW, copyBtnY + copyBtnH, copyHovered ? 0xFF444444 : 0xFF222222);
        drawBorder(context, copyBtnX, copyBtnY, copyBtnW, copyBtnH, 0xFFFFFFFF);
        boolean isCopied = System.currentTimeMillis() - cpCopiedTime < 2000;
        String copyText = isCopied ? "Copied!" : "Copy";
        context.text(this.font, com.eymistaken.simplecps.util.EymHudFonts.text(copyText), copyBtnX + 4, copyBtnY + 4, isCopied ? 0xFF55FF55 : 0xFFFFFFFF, true);

        // Preview + Apply (Y=145, H=25)
        cy = py + 145;
        int curColor = getCurrentPickerColor();
        context.fill(cx, cy, cx + 40, cy + 25, curColor);
        drawBorder(context, cx, cy, 40, 25, 0xFFFFFFFF);
        
        boolean applyHover = mouseX >= cx + 50 && mouseX <= cx + 93 && mouseY >= cy && mouseY <= cy + 25;
        context.fill(cx + 50, cy, cx + 93, cy + 25, applyHover ? 0xFF444444 : 0xFF333333);
        drawBorder(context, cx + 50, cy, 43, 25, 0xFFFFFFFF);
        context.text(this.font, com.eymistaken.simplecps.util.EymHudFonts.text("Apply"), cx + 59, cy + 8, 0xFFFFFFFF, true);

        boolean resetHover = mouseX >= cx + 97 && mouseX <= cx + 140 && mouseY >= cy && mouseY <= cy + 25;
        context.fill(cx + 97, cy, cx + 140, cy + 25, resetHover ? 0xFF444444 : 0xFF333333);
        drawBorder(context, cx + 97, cy, 43, 25, 0xFFFFFFFF);
        context.text(this.font, com.eymistaken.simplecps.util.EymHudFonts.text("Reset"), cx + 105, cy + 8, 0xFFFFFFFF, true);
    }

    private void tryApplyHexPicker() {
        String hex = cpHexInput.startsWith("#") ? cpHexInput.substring(1) : cpHexInput;
        if (hex.length() == 6) {
            try {
                int rgb = Integer.parseInt(hex, 16);
                java.awt.Color col = new java.awt.Color(rgb);
                float[] hsb = java.awt.Color.RGBtoHSB(col.getRed(), col.getGreen(), col.getBlue(), null);
                this.cpHue = hsb[0];
                this.cpSat = hsb[1];
                this.cpVal = hsb[2];
            } catch (NumberFormatException ignored) {}
        }
    }

    private void renderContextMenu(
        GuiGraphicsExtractor context, int mouseX, int mouseY, float p,
        SimpleCPSConfig.KeyButtonData target
    ) {
        int w = CONTEXT_MENU_WIDTH;
        int h = getMenuHeight(target != null);

        // Position is already clamped where the menu is opened.
        int x = contextMenuX;
        int y = contextMenuY;

        context.pose().pushMatrix();
        context.pose().translate(0f, (1f - p) * 8f);

        context.fill(x, y, x + w, y + h, fade(0xFF222222, p));
        drawBorder(context, x, y, w, h, fade(0xFFFFFFFF, p));

        if (target != null) {
            drawContextMenuItem(context, "Set Keybind", x, y, mouseX, mouseY, 0);
            drawContextMenuItem(context, "Edit Label", x, y, mouseX, mouseY, 1);
            drawContextMenuItem(context, "Center Label", x, y, mouseX, mouseY, 2);
            drawContextMenuItem(context, "Toggle Bold", x, y, mouseX, mouseY, 3);
            drawContextMenuItem(context, "Toggle Italic", x, y, mouseX, mouseY, 4);
            drawContextMenuItem(context, "Toggle Underline", x, y, mouseX, mouseY, 5);
            
            if (target.isMouse) {
                 drawContextMenuItem(context, "Toggle CPS", x, y, mouseX, mouseY, 6);
                 drawContextMenuItem(context, "Delete", x, y, mouseX, mouseY, 7);
                 drawContextMenuItem(context, "Change Color", x, y, mouseX, mouseY, 8);
                 drawContextMenuItem(context, "Change Pressed Color", x, y, mouseX, mouseY, 9);
            } else {
                 drawContextMenuItem(context, "Delete", x, y, mouseX, mouseY, 6);
                 drawContextMenuItem(context, "Duplicate", x, y, mouseX, mouseY, 7);
                 drawContextMenuItem(context, "Change Color", x, y, mouseX, mouseY, 8);
                 drawContextMenuItem(context, "Change Pressed Color", x, y, mouseX, mouseY, 9);
            }
            boolean animOn = target.animationEnabled == null || target.animationEnabled;
            String animLabel = "Animation [" + (animOn ? "ON" : "OFF") + "]";
            drawContextMenuItem(context, animLabel, x, y, mouseX, mouseY, 10);
        } else {
            drawContextMenuItem(context, "Add Key (1x1)", x, y, mouseX, mouseY, 0);
            drawContextMenuItem(context, "Add Space (Wide)", x, y, mouseX, mouseY, 1);
            drawContextMenuItem(context, "Add Mouse (Left)", x, y, mouseX, mouseY, 2);
            drawContextMenuItem(context, "Add Mouse (Right)", x, y, mouseX, mouseY, 3);
            // Removed Side Mouse
            drawContextMenuItem(context, "Add Modifier", x, y, mouseX, mouseY, 4);
            drawContextMenuItem(context, "Reset Layout", x, y, mouseX, mouseY, 5);
            
            String effectName = config().keystrokesEffectMode == 0 ? "None" : config().keystrokesEffectMode == 1 ? "Squish" : config().keystrokesEffectMode == 2 ? "Ripple" : "Both";
            String globalAnimLabel = "Effect: " + effectName;
            drawContextMenuItem(context, globalAnimLabel, x, y, mouseX, mouseY, 6);
            drawContextMenuItem(context, "Export Layout", x, y, mouseX, mouseY, 7);
            drawContextMenuItem(context, "Import Layout", x, y, mouseX, mouseY, 8);
        }

        context.pose().popMatrix();
    }

    private void drawContextMenuItem(GuiGraphicsExtractor context, String text, int menuX, int menuY, int mouseX, int mouseY, int index) {
        int itemH = 20;
        int y = menuY + (index * itemH);
        float p = contextMenuAnim.get();
        // Full menu width: the highlight used to stop at 100px while clicks were
        // accepted across all 140, so the right third looked dead but wasn't.
        boolean hovered = mouseX >= menuX && mouseX < menuX + CONTEXT_MENU_WIDTH && mouseY >= y && mouseY < y + itemH;
        if (hovered) {
             context.fill(menuX + 1, y, menuX + CONTEXT_MENU_WIDTH - 1, y + itemH, fade(0x44FFFFFF, p));
        }
        context.text(font, com.eymistaken.simplecps.util.EymHudFonts.text(text), menuX + 5, y + 6, fade(0xFFFFFFFF, p), false);
    }

    private void drawBorder(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }
    
    private int getMenuHeight(boolean hasTarget) {
        // Must match the rows drawn in renderContextMenu, or rows become unclickable.
        // 11 with a target (the last being Animation), 9 without.
        return (hasTarget ? 11 : 9) * 20;
    }
    
    private void handleMenuAction(int index) {
        if (contextMenuTarget != null) {
            saveUndo();
            switch(index) {
                case 0: // Set Keybind
                    waitingForKeybind = true;
                    // lastPressedKey = -1; // Reset?
                    break;
                case 1: // Edit Label
                    currentState = InteractionState.TEXT_EDIT_MODE;
                    selectedButtons.clear();
                    selectedButtons.add(contextMenuTarget);
                    waitingForKeybind = false;
                    break;
                case 2: // Center Label
                    contextMenuTarget.labelX = -1;
                    contextMenuTarget.labelY = -1;
                    break;
                case 3: contextMenuTarget.bold = !contextMenuTarget.bold; break;
                case 4: contextMenuTarget.italic = !contextMenuTarget.italic; break;
                case 5: contextMenuTarget.underlined = !contextMenuTarget.underlined; break;
                
                case 6: // Variable based on isMouse
                    if (contextMenuTarget.isMouse) {
                        contextMenuTarget.showCps = !contextMenuTarget.showCps;
                    } else {
                        // Delete
                        config().keystrokesLayout.remove(contextMenuTarget);
                        selectedButtons.remove(contextMenuTarget);
                        contextMenuTarget = null;
                        currentState = InteractionState.IDLE;
                    }
                    break;
                    
                case 7: // Variable
                if (contextMenuTarget.isMouse) {
                    // Delete for Mouse
                    config().keystrokesLayout.remove(contextMenuTarget);
                    selectedButtons.remove(contextMenuTarget);
                    contextMenuTarget = null;
                    currentState = InteractionState.IDLE;
                } else {
                    // Duplicate
                    addNewButton(contextMenuTarget.label, contextMenuTarget.x + 10, contextMenuTarget.y + 10, contextMenuTarget.w, contextMenuTarget.h, contextMenuTarget.keyCode);
                }
                break;
            case 8: // Change Color
                colorPickerTargetPressed = false;
                colorPickerTarget = contextMenuTarget;
                int initColor = colorPickerTarget.btnColor;
                if (initColor <= 0) initColor = config().keystrokesColor;
                float[] hsb = java.awt.Color.RGBtoHSB((initColor >> 16) & 0xFF, (initColor >> 8) & 0xFF, initColor & 0xFF, null);
                cpHue = hsb[0]; cpSat = hsb[1]; cpVal = hsb[2];
                contextMenuOpen = false;
                colorPickerOpen = true;
                break;
            case 9: // Change Pressed Color
                colorPickerTargetPressed = true;
                colorPickerTarget = contextMenuTarget;
                int initPColor = colorPickerTarget.btnPressedColor;
                if (initPColor <= 0) initPColor = config().keystrokesPressedColor;
                float[] phsb = java.awt.Color.RGBtoHSB((initPColor >> 16) & 0xFF, (initPColor >> 8) & 0xFF, initPColor & 0xFF, null);
                cpHue = phsb[0]; cpSat = phsb[1]; cpVal = phsb[2];
                contextMenuOpen = false;
                colorPickerOpen = true;
                break;
            case 10: // Toggle per-key animation
                boolean current = contextMenuTarget.animationEnabled == null || contextMenuTarget.animationEnabled;
                contextMenuTarget.animationEnabled = !current;
                SimpleCPSConfig.save();
                break;
        }
    } else {
            saveUndo();
            switch(index) {
                case 0: // Add 1x1 Key
                    addNewButton("K", 0, 0, 21, 21, GLFW.GLFW_KEY_UNKNOWN); // 21x21
                    break;
                case 1: // Add Space (Wide)
                    addNewButton("-----", 0, 40, 67, 13, GLFW.GLFW_KEY_SPACE); // 67x13
                    break;
                case 2: // Add LMB (Wide Style)
                    SimpleCPSConfig.KeyButtonData lmb = new SimpleCPSConfig.KeyButtonData("LMB", 0, 60, 33, 21, 0, true); // 33x21
                    lmb.showCps = true;
                    config().keystrokesLayout.add(lmb);
                    selectedButtons.clear();
                    selectedButtons.add(lmb);
                    currentState = InteractionState.BOX_SELECTED;
                    break;
                case 3: // Add RMB (Wide Style)
                    SimpleCPSConfig.KeyButtonData rmb = new SimpleCPSConfig.KeyButtonData("RMB", 33, 60, 33, 21, 1, true); // 33x21
                    rmb.showCps = true;
                    config().keystrokesLayout.add(rmb);
                    selectedButtons.clear();
                    selectedButtons.add(rmb);
                    currentState = InteractionState.BOX_SELECTED;
                    break;
                case 4: // Add Modifier
                    addNewButton("MOD", 0, 80, 33, 13, GLFW.GLFW_KEY_LEFT_CONTROL); // 33x13
                    break;
                case 5: // Reset
                    config().resetLayout();
                    SimpleCPSConfig.save();
                    selectedButtons.clear();
                    currentState = InteractionState.IDLE;
                    break;
                case 6: // Toggle global animations
                    config().keystrokesEffectMode = (config().keystrokesEffectMode + 1) % 4;
                    SimpleCPSConfig.save();
                    break;
                case 7: { // Export Layout -> clipboard share code
                    String code = com.eymistaken.simplecps.util.HudShareCodec.encodeKeystrokes();
                    if (code != null) this.minecraft.keyboardHandler.setClipboard(code);
                    break;
                }
                case 8: { // Import Layout from a clipboard share code (undo already snapshotted)
                    var decoded = com.eymistaken.simplecps.util.HudShareCodec.decode(
                        this.minecraft.keyboardHandler.getClipboard());
                    if (decoded != null
                        && com.eymistaken.simplecps.util.HudShareCodec.TYPE_KEYSTROKES.equals(decoded.type())
                        && com.eymistaken.simplecps.util.HudShareCodec.apply(decoded)) {
                        selectedButtons.clear();
                        currentState = InteractionState.IDLE;
                    }
                    break;
                }
            }
        }
    }
    
    private void addNewButton(String label, int x, int y, int w, int h, int key) {
        // Smart Add Logic: Relative to last selected if any
        if (!selectedButtons.isEmpty()) {
            SimpleCPSConfig.KeyButtonData last = selectedButtons.get(selectedButtons.size() - 1);
            x = last.x + 10;
            y = last.y + 10;
            // Do NOT copy w/h. Use the passed arguments.
        }
        
        SimpleCPSConfig.KeyButtonData newBtn = new SimpleCPSConfig.KeyButtonData(label, x, y, w, h, key);
        config().keystrokesLayout.add(newBtn);
        selectedButtons.clear();
        selectedButtons.add(newBtn);
        currentState = InteractionState.BOX_SELECTED;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Scroll to Scale
        double amount = verticalAmount;
        if (currentState == InteractionState.BOX_SELECTED || currentState == InteractionState.TEXT_EDIT_MODE) {
             for (SimpleCPSConfig.KeyButtonData btn : selectedButtons) {
                 // Center Scale
                 int scaler = (amount > 0) ? 2 : -2;
                 
                 // Enforce Min Size
                 if (btn.w + scaler < 5 || btn.h + scaler < 5) continue;
                 
                 btn.x -= scaler / 2;
                 btn.y -= scaler / 2;
                 btn.w += scaler;
                 btn.h += scaler;
             }
             return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }


    
    // ... applySnapping (modified for boolean moving) logic was in previous file?
    // Wait, the previous file had `applySnapping(target, ResizeHandle)`.
    // I called `applySnapping(leader, true)` in mouseDragged.
    // Need to update signature.
    
    private void applySnapping(SimpleCPSConfig.KeyButtonData target, boolean moving) {
        snapX = null;
        snapY = null;
        int threshold = 2; // Reduced threshold to 2px
        int monitorGap = 2; // Gap for smart snapping (Matched to default layout)
        int originX = width / 2 - 33;
        int originY = height / 2 - 48;
        
        int tLeft = originX + target.x;
        int tRight = tLeft + target.w;
        int tTop = originY + target.y;
        int tBottom = tTop + target.h;
        int tCX = tLeft + target.w / 2;
        int tCY = tTop + target.h / 2;
        
        for (SimpleCPSConfig.KeyButtonData other : config().keystrokesLayout) {
            if (other == target) continue;
            // Ignore other selected buttons to prevent self-snapping within group
            if (selectedButtons.contains(other)) continue;
            
            int oLeft = originX + other.x;
            int oRight = oLeft + other.w;
            int oTop = originY + other.y;
            int oBottom = oTop + other.h;
            int oCX = oLeft + other.w / 2;
            int oCY = oTop + other.h / 2;
            
            // X Snapping
            if (moving) {
                // Direct Edges
                if (Math.abs(tLeft - oLeft) <= threshold) { target.x = other.x; snapX = oLeft; }
                else if (Math.abs(tRight - oRight) <= threshold) { target.x = other.x + other.w - target.w; snapX = oRight; }
                else if (Math.abs(tLeft - oRight) <= threshold) { target.x = other.x + other.w; snapX = oRight; }
                else if (Math.abs(tRight - oLeft) <= threshold) { target.x = other.x - target.w; snapX = oLeft; }
                else if (Math.abs(tCX - oCX) <= threshold) { target.x = other.x + (other.w - target.w)/2; snapX = oCX; }
                
                // Gap Snapping (4px)
                // Target Left to Other Right + Gap
                else if (Math.abs(tLeft - (oRight + monitorGap)) <= threshold) { target.x = other.x + other.w + monitorGap; snapX = oRight + monitorGap; }
                // Target Right to Other Left - Gap
                else if (Math.abs(tRight - (oLeft - monitorGap)) <= threshold) { target.x = other.x - monitorGap - target.w; snapX = oLeft - monitorGap; }
            } 
            // Y Snapping
            if (moving) {
                // Direct Edges
                if (Math.abs(tTop - oTop) <= threshold) { target.y = other.y; snapY = oTop; }
                else if (Math.abs(tBottom - oBottom) <= threshold) { target.y = other.y + other.h - target.h; snapY = oBottom; }
                else if (Math.abs(tTop - oBottom) <= threshold) { target.y = other.y + other.h; snapY = oBottom; }
                else if (Math.abs(tBottom - oTop) <= threshold) { target.y = other.y - target.h; snapY = oTop; }
                else if (Math.abs(tCY - oCY) <= threshold) { target.y = other.y + (other.h - target.h)/2; snapY = oCY; }
                
                // Gap Snapping (4px)
                // Target Top to Other Bottom + Gap
                else if (Math.abs(tTop - (oBottom + monitorGap)) <= threshold) { target.y = other.y + other.h + monitorGap; snapY = oBottom + monitorGap; }
                // Target Bottom to Other Top - Gap
                else if (Math.abs(tBottom - (oTop - monitorGap)) <= threshold) { target.y = other.y - monitorGap - target.h; snapY = oTop - monitorGap; }
            }
        }
    }
    private void saveUndo() {
        String json = gson.toJson(config().keystrokesLayout);
        undoStack.push(json);
        redoStack.clear();
    }
    
    private void undo() {
        if (!undoStack.isEmpty()) {
            String current = gson.toJson(config().keystrokesLayout);
            redoStack.push(current);
            
            String json = undoStack.pop();
            List<SimpleCPSConfig.KeyButtonData> list = gson.fromJson(json, new TypeToken<List<SimpleCPSConfig.KeyButtonData>>(){}.getType());
            config().keystrokesLayout.clear();
            config().keystrokesLayout.addAll(list);
            
            // Clear selection to avoid ghost references
            selectedButtons.clear();
            currentState = InteractionState.IDLE;
        }
    }
    
    private void redo() {
        if (!redoStack.isEmpty()) {
            String current = gson.toJson(config().keystrokesLayout);
            undoStack.push(current);
            
            String json = redoStack.pop();
            List<SimpleCPSConfig.KeyButtonData> list = gson.fromJson(json, new TypeToken<List<SimpleCPSConfig.KeyButtonData>>(){}.getType());
            config().keystrokesLayout.clear();
            config().keystrokesLayout.addAll(list);
            
            selectedButtons.clear();
            currentState = InteractionState.IDLE;
        }
    }
            

    private boolean isCtrlDown() {
        long window = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private String getKeyName(int keyCode) {
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        if (name != null) return name.toUpperCase();
        
        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE: return "SPACE";
            case GLFW.GLFW_KEY_ESCAPE: return "ESC";
            case GLFW.GLFW_KEY_ENTER: return "ENTER";
            case GLFW.GLFW_KEY_TAB: return "TAB";
            case GLFW.GLFW_KEY_BACKSPACE: return "BACK";
            case GLFW.GLFW_KEY_INSERT: return "INS";
            case GLFW.GLFW_KEY_DELETE: return "DEL";
            case GLFW.GLFW_KEY_RIGHT: return "RIGHT";
            case GLFW.GLFW_KEY_LEFT: return "LEFT";
            case GLFW.GLFW_KEY_DOWN: return "DOWN";
            case GLFW.GLFW_KEY_UP: return "UP";
            case GLFW.GLFW_KEY_PAGE_UP: return "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "PGDN";
            case GLFW.GLFW_KEY_HOME: return "HOME";
            case GLFW.GLFW_KEY_END: return "END";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "CAPS";
            case GLFW.GLFW_KEY_SCROLL_LOCK: return "SCROLL";
            case GLFW.GLFW_KEY_NUM_LOCK: return "NUM";
            case GLFW.GLFW_KEY_PRINT_SCREEN: return "PRT";
            case GLFW.GLFW_KEY_PAUSE: return "PAUSE";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "LSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "LCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT: return "LALT";
            case GLFW.GLFW_KEY_LEFT_SUPER: return "LSUPER";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "RSHIFT";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "RCTRL";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "RALT";
            case GLFW.GLFW_KEY_RIGHT_SUPER: return "RSUPER";
            case GLFW.GLFW_KEY_MENU: return "MENU";
            default: 
                if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) return "F" + (keyCode - GLFW.GLFW_KEY_F1 + 1);
                if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) return "NUM" + (keyCode - GLFW.GLFW_KEY_KP_0);
                return "K" + keyCode;
        }
    }

    @Override
    public void onClose() {
        com.eymistaken.simplecps.SimpleCPSConfig.save();
        super.onClose();
    }
}
