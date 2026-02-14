package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Stack;
import java.util.ArrayList;

public class KeystrokesDesignerScreen extends Screen {

    private final SimpleCPSConfig config;
    
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

    // Polling State restoration
    private boolean wasLeftDown = false;
    private boolean wasRightDown = false;
    private double lastMouseX = 0, lastMouseY = 0;

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
        super(Text.of("Keystrokes Designer"));
        this.parent = parent;
        this.config = SimpleCPSConfig.instance;
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

    // Context Menu
    private boolean contextMenuOpen = false;
    private int contextMenuX, contextMenuY;
    private SimpleCPSConfig.KeyButtonData contextMenuTarget = null;
    
    // Editing
    private boolean waitingForKeybind = false;
    // Helper to debounce key presses
    private int lastPressedKey = -1;
    private long lastPressTime = 0; // For repeat keys if needed

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Gradient Background (matching HudEditorScreen)
        context.fillGradient(0, 0, this.width, this.height, 0xC0000000, 0xD0000000); 
        
        // Polling Input
        updateInput(mouseX, mouseY);

        // Draw Canvas (Centered Safe Area)
        int centerX = width / 2;
        int centerY = height / 2;
        int cw = CANVAS_W / 2;
        int ch = CANVAS_H / 2;
        
        // Canvas Background
        context.fill(centerX - cw, centerY - ch, centerX + cw, centerY + ch, 0x22FFFFFF); 
        // Canvas Border
        drawBorder(context, centerX - cw, centerY - ch, CANVAS_W, CANVAS_H, 0x44FFFFFF);

        // Origin Crosshair
        context.fill(centerX - 1, centerY - 5, centerX + 1, centerY + 5, 0x88FFFFFF);
        context.fill(centerX - 5, centerY - 1, centerX + 5, centerY + 1, 0x88FFFFFF);
        context.drawText(textRenderer, "Module Center", centerX + 5, centerY + 5, 0xAAAAAA, false);

        // Snap Lines
        if (snapX != null) context.fill(snapX, 0, snapX + 1, height, 0xFFFF00FF);
        if (snapY != null) context.fill(0, snapY, width, snapY + 1, 0xFFFF00FF);

        // Draw Buttons
        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
            int x = centerX + btn.x;
            int y = centerY + btn.y;
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

            net.minecraft.text.MutableText text = net.minecraft.text.Text.literal(label);
            net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY;
            if (btn.bold) style = style.withBold(true);
            if (btn.italic) style = style.withItalic(true);
            if (btn.underlined) style = style.withUnderline(true);
            text.setStyle(style);

            int labelW = textRenderer.getWidth(text);
            int labelH = textRenderer.fontHeight;
            
            int lx = (btn.labelX == -1) ? x + (btn.w - labelW) / 2 : x + btn.labelX;
            int ly = (btn.labelY == -1) ? y + (btn.h - labelH) / 2 : y + btn.labelY;
            
            // Dynamic Label Centering for Mouse Buttons with CPS
            if (btn.isMouse && btn.showCps && btn.labelY == -1) {
                ly -= 4; // Shift up 4px to make room
            }
            
            context.drawText(textRenderer, text, lx, ly, 0xFFFFFFFF, btn.shadow);
            
            // Render CPS Preview
            if (btn.isMouse && btn.showCps) {
                String cpsPreview = "0"; 
                int cpsW = textRenderer.getWidth(cpsPreview);
                // Center CPS horizontally
                int cpsX = x + (btn.w - cpsW) / 2;
                int cpsY = ly + labelH + 1; // Below label
                
                context.drawText(textRenderer, cpsPreview, cpsX, cpsY, 0xFFAAAAAA, btn.shadow);
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
        
        context.drawText(textRenderer, "Click: Select | Ctrl+Click: Multi | Dbl Click: Text Mode | Scroll: resize", 10, 10, 0xFFFFFF, true);
        context.drawText(textRenderer, "Right-Drag: Move Text | Left-Drag: Move Box", 10, 22, 0xAAAAAA, true);

        // Visual Aid for Centering (Red Lines)
        if (currentState == InteractionState.TEXT_DRAGGING && rightClickTarget != null) {
            int cx = centerX + rightClickTarget.x + rightClickTarget.w / 2;
            int cy = centerY + rightClickTarget.y + rightClickTarget.h / 2;
            
            // If label is centered horizontally
            if (rightClickTarget.labelX == -1) {
                context.fill(cx, centerY + rightClickTarget.y, cx + 1, centerY + rightClickTarget.y + rightClickTarget.h, 0xFFFF0000);
            }
            // If label is centered vertically
            if (rightClickTarget.labelY == -1) {
                context.fill(centerX + rightClickTarget.x, cy, centerX + rightClickTarget.x + rightClickTarget.w, cy + 1, 0xFFFF0000);
            }
        }

         if (contextMenuOpen) {
            renderContextMenu(context, mouseX, mouseY);
        }
    }

    private void updateInput(int mouseX, int mouseY) {
         long windowHandle = client.getWindow().getHandle();
         boolean isLeftDown = GLFW.glfwGetMouseButton(windowHandle, 0) == GLFW.GLFW_PRESS;
         boolean isRightDown = GLFW.glfwGetMouseButton(windowHandle, 1) == GLFW.GLFW_PRESS;
         
         // Click / Release
         if (isLeftDown && !wasLeftDown) onMouseClicked(mouseX, mouseY, 0);
         else if (!isLeftDown && wasLeftDown) onMouseReleased(mouseX, mouseY, 0);
         
         if (isRightDown && !wasRightDown) onMouseClicked(mouseX, mouseY, 1);
         else if (!isRightDown && wasRightDown) onMouseReleased(mouseX, mouseY, 1);
         
         // Drag
         if (isLeftDown || isRightDown) {
             double deltaX = mouseX - lastMouseX;
             double deltaY = mouseY - lastMouseY;
             if (deltaX != 0 || deltaY != 0) {
                 int btn = isLeftDown ? 0 : 1;
                 onMouseDragged(mouseX, mouseY, btn, deltaX, deltaY);
             }
         }
         
         lastMouseX = mouseX;
         lastMouseY = mouseY;
         wasLeftDown = isLeftDown;
         wasRightDown = isRightDown;
         
         // Keys
         updateKeys(windowHandle);
    }
    
    private void updateKeys(long windowHandle) {
        if (waitingForKeybind || currentState == InteractionState.TEXT_EDIT_MODE || !selectedButtons.isEmpty()) {
             for (int k = 32; k < GLFW.GLFW_KEY_LAST; k++) {
                  if (GLFW.glfwGetKey(windowHandle, k) == GLFW.GLFW_PRESS) {
                      // Skip modifiers from repeat logic unless binding a key
                      boolean isModifier = (k >= 340 && k <= 348);
                      if (isModifier && !waitingForKeybind) continue;
                      
                      if (lastPressedKey != k || (System.currentTimeMillis() - lastPressTime > 200)) { 
                          lastPressTime = System.currentTimeMillis();
                          lastPressedKey = k;
                          
                          int modifiers = 0;
                          if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) modifiers |= GLFW.GLFW_MOD_CONTROL;
                          if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) modifiers |= GLFW.GLFW_MOD_SHIFT;
                          
                          onKeyPressed(k, 0, modifiers);
                      }
                  }
             }
             if (lastPressedKey != -1 && GLFW.glfwGetKey(windowHandle, lastPressedKey) == GLFW.GLFW_RELEASE) {
                  lastPressedKey = -1;
             }
        }
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenuOpen) {
             // Handle Menu Clicks
             int w = 140; 
             int menuY = contextMenuY;
             int fullH = (contextMenuTarget != null) ? 160 : 140; // Sync with renderContextMenu (8 items vs 7)
             if (menuY + fullH > this.height) menuY = menuY - fullH;
             if (mouseX >= contextMenuX && mouseX <= contextMenuX + w && mouseY >= menuY && mouseY <= menuY + fullH) {
                 int index = (int)((mouseY - menuY) / 20);
                 handleMenuAction(index);
                 contextMenuOpen = false;
                 return true;
             }
             contextMenuOpen = false;
             return true; 
        }

        int centerX = width / 2;
        int centerY = height / 2;
        int mX = (int)mouseX;
        int mY = (int)mouseY;
        
        // Find Hit
        SimpleCPSConfig.KeyButtonData hitBtn = null;
        List<SimpleCPSConfig.KeyButtonData> layout = config.keystrokesLayout;
        for (int i = layout.size() - 1; i >= 0; i--) {
            SimpleCPSConfig.KeyButtonData btn = layout.get(i);
            if (mX >= centerX + btn.x && mX <= centerX + btn.x + btn.w && 
                mY >= centerY + btn.y && mY <= centerY + btn.y + btn.h) {
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
                         config.keystrokesLayout.remove(hitBtn);
                         config.keystrokesLayout.add(hitBtn);
                    }
                }
                
                // Prepare Drag (Left = Box)
                if (currentState == InteractionState.BOX_SELECTED) {
                    currentState = InteractionState.DRAGGING_BOX;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    dragSnapshots.clear();
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
                contextMenuX = mX;
                contextMenuY = mY;
                return true;
            }
        }

        return false; // Handled manually
    }
    
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && currentState == InteractionState.DRAGGING_BOX) { // Left Drag
            double rawDx = mouseX - dragStartX;
            double rawDy = mouseY - dragStartY;
            
            if (selectedButtons.isEmpty()) return false;
            
            for (ButtonSnapshot snap : dragSnapshots) {
                snap.btn.x = snap.x + (int)rawDx;
                snap.btn.y = snap.y + (int)rawDy;
            }
            
            SimpleCPSConfig.KeyButtonData leader = selectedButtons.get(0);
            applySnapping(leader, true); // Snap Leader
            
            int finalDx = leader.x - dragSnapshots.get(0).x;
            int finalDy = leader.y - dragSnapshots.get(0).y;
            
            for (int i = 1; i < dragSnapshots.size(); i++) {
                ButtonSnapshot snap = dragSnapshots.get(i);
                snap.btn.x = snap.x + finalDx;
                snap.btn.y = snap.y + finalDy;
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
                    int centerX = width / 2;
                    int centerY = height / 2;
                    int btnX = centerX + rightClickTarget.x;
                    int btnY = centerY + rightClickTarget.y;
                    
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
                    int labelW = textRenderer.getWidth(label);
                    int labelH = textRenderer.fontHeight;
                    
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
    
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
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
            } else {
                // Was a click! Open Menu
                if (rightClickTarget != null) {
                    contextMenuTarget = rightClickTarget;
                    if (!selectedButtons.contains(rightClickTarget)) {
                         selectedButtons.clear();
                         selectedButtons.add(rightClickTarget);
                         currentState = InteractionState.BOX_SELECTED;
                    }
                    contextMenuOpen = true;
                    contextMenuX = (int)mouseX;
                    contextMenuY = (int)mouseY;
                }
                rightClickTarget = null;
            }
        }
        return false;
    }

    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
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

        if (currentState == InteractionState.TEXT_EDIT_MODE && !selectedButtons.isEmpty()) {
            SimpleCPSConfig.KeyButtonData target = selectedButtons.get(0);
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            int step = shift ? 5 : 1;
            
            if (keyCode == GLFW.GLFW_KEY_UP) target.labelY = (target.labelY == -1 ? (target.h - 8)/2 : target.labelY) - step;
            else if (keyCode == GLFW.GLFW_KEY_DOWN) target.labelY = (target.labelY == -1 ? (target.h - 8)/2 : target.labelY) + step;
            else if (keyCode == GLFW.GLFW_KEY_LEFT) target.labelX = (target.labelX == -1 ? (target.w - 10)/2 : target.labelX) - step;
            else if (keyCode == GLFW.GLFW_KEY_RIGHT) target.labelX = (target.labelX == -1 ? (target.w - 10)/2 : target.labelX) + step;
            
            else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                currentState = InteractionState.BOX_SELECTED; 
            } 
            else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                 if (!target.label.isEmpty()) target.label = target.label.substring(0, target.label.length() - 1);
            }
            else if (keyCode == GLFW.GLFW_KEY_SPACE) {
                 target.label += " ";
            }
            else {
                 // Manual Text Input Logic
                 String keyName = GLFW.glfwGetKeyName(keyCode, scanCode);
                 if (keyName != null && !keyName.isEmpty()) {
                     // Check if valid char (not F1, etc which return null usually but just in case)
                     // Actually glfwGetKeyName returns "f1" sometimes? No, usually null for non-printable.
                     // But for letters it returns "a", "b".
                     
                     // Filter out non-printable if needed, or valid chars.
                     // Length 1 usually means char.
                     if (keyName.length() == 1) {
                         char c = keyName.charAt(0);
                         // shift already defined in scope? Let's check. 
                         // Actually, look at line 534 in view file.
                         // "boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;"
                         // Yes. So remove declaration here.
                         
                         // Re-use shift
                         boolean caps = false; 
                         
                         if (shift) {
                             c = Character.toUpperCase(c);
                         }
                         
                         target.label += c;
                     }
                 }
            }
            return true;
        }
        
        if (currentState == InteractionState.BOX_SELECTED && !selectedButtons.isEmpty()) {
             boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
             boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
             int step = shift ? 5 : 1;
             
             if (keyCode == GLFW.GLFW_KEY_UP) { for (SimpleCPSConfig.KeyButtonData b : selectedButtons) b.y -= step; return true; }
             if (keyCode == GLFW.GLFW_KEY_DOWN) { for (SimpleCPSConfig.KeyButtonData b : selectedButtons) b.y += step; return true; }
             if (keyCode == GLFW.GLFW_KEY_LEFT) { for (SimpleCPSConfig.KeyButtonData b : selectedButtons) b.x -= step; return true; }
             if (keyCode == GLFW.GLFW_KEY_RIGHT) { for (SimpleCPSConfig.KeyButtonData b : selectedButtons) b.x += step; return true; }
             
             if (keyCode == GLFW.GLFW_KEY_DELETE) {
                 saveUndo();
                 config.keystrokesLayout.removeAll(selectedButtons);
                 selectedButtons.clear();
                 currentState = InteractionState.IDLE;
                 return true;
             }
             if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                 selectedButtons.clear();
                 currentState = InteractionState.IDLE;
                 return true;
             }
             // Undo/Redo
             if (keyCode == GLFW.GLFW_KEY_Z && ctrl) { if (shift) redo(); else undo(); return true; }
             if (keyCode == GLFW.GLFW_KEY_Y && ctrl) { redo(); return true; }
        }
        
        return false;
    }




    
    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        int w = 140; // Slightly wider for new options
        int h = 0;
        
        // Calculate height based on items
        if (contextMenuTarget != null) {
            h = 160; // 8 items * 20
        } else {
            h = 140; // 7 items (Add x5 + Reset + Close?)
        }
        
        // Smart Positioning (Open Upwards if overflow)
        int x = contextMenuX;
        int y = contextMenuY;
        
        if (y + h > this.height) {
            y = y - h;
        }
        
        context.fill(x, y, x + w, y + h, 0xFF222222);
        drawBorder(context, x, y, w, h, 0xFFFFFFFF);
        
        if (contextMenuTarget != null) {
            drawContextMenuItem(context, "Set Keybind", x, y, mouseX, mouseY, 0);
            drawContextMenuItem(context, "Edit Label", x, y, mouseX, mouseY, 1);
            drawContextMenuItem(context, "Center Label", x, y, mouseX, mouseY, 2);
            drawContextMenuItem(context, "Toggle Bold", x, y, mouseX, mouseY, 3);
            drawContextMenuItem(context, "Toggle Italic", x, y, mouseX, mouseY, 4);
            drawContextMenuItem(context, "Toggle Underline", x, y, mouseX, mouseY, 5);
            
            if (contextMenuTarget.isMouse) {
                 drawContextMenuItem(context, "Toggle CPS", x, y, mouseX, mouseY, 6);
                 drawContextMenuItem(context, "Delete", x, y, mouseX, mouseY, 7);
                 // 8 items
            } else {
                 drawContextMenuItem(context, "Delete", x, y, mouseX, mouseY, 6);
                 drawContextMenuItem(context, "Duplicate", x, y, mouseX, mouseY, 7);
            }
        } else {
            drawContextMenuItem(context, "Add Key (1x1)", x, y, mouseX, mouseY, 0);
            drawContextMenuItem(context, "Add Space (Wide)", x, y, mouseX, mouseY, 1);
            drawContextMenuItem(context, "Add Mouse (Left)", x, y, mouseX, mouseY, 2);
            drawContextMenuItem(context, "Add Mouse (Right)", x, y, mouseX, mouseY, 3);
            // Removed Side Mouse
            drawContextMenuItem(context, "Add Modifier (Ctrl)", x, y, mouseX, mouseY, 4);
            drawContextMenuItem(context, "Reset Layout", x, y, mouseX, mouseY, 5);
        }
    }
    
    private void drawContextMenuItem(DrawContext context, String text, int menuX, int menuY, int mouseX, int mouseY, int index) {
        int itemH = 20;
        int y = menuY + (index * itemH);
        boolean hovered = mouseX >= menuX && mouseX < menuX + 100 && mouseY >= y && mouseY < y + itemH;
        if (hovered) {
             context.fill(menuX + 1, y, menuX + 99, y + itemH, 0x44FFFFFF);
        }
        context.drawText(textRenderer, text, menuX + 5, y + 6, 0xFFFFFFFF, false);
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
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
                        config.keystrokesLayout.remove(contextMenuTarget);
                        selectedButtons.remove(contextMenuTarget);
                        contextMenuTarget = null;
                        currentState = InteractionState.IDLE;
                    }
                    break;
                    
                case 7: // Variable
                    if (contextMenuTarget.isMouse) {
                        // Delete for Mouse
                        config.keystrokesLayout.remove(contextMenuTarget);
                        selectedButtons.remove(contextMenuTarget);
                        contextMenuTarget = null;
                        currentState = InteractionState.IDLE;
                    } else {
                        // Duplicate
                        addNewButton(contextMenuTarget.label, contextMenuTarget.x + 10, contextMenuTarget.y + 10, contextMenuTarget.w, contextMenuTarget.h, contextMenuTarget.keyCode);
                    }
                    break;
            }
        } else {
            saveUndo();
            switch(index) {
                case 0: // Add 1x1 Key
                    addNewButton("K", 0, 0, 20, 20, GLFW.GLFW_KEY_UNKNOWN); 
                    break;
                case 1: // Add Space (Wide)
                    addNewButton("SPACE", 0, 40, 64, 12, GLFW.GLFW_KEY_SPACE);
                    break;
                case 2: // Add LMB (Wide Style)
                    SimpleCPSConfig.KeyButtonData lmb = new SimpleCPSConfig.KeyButtonData("LMB", 0, 60, 31, 12, 0, true);
                    lmb.showCps = true;
                    config.keystrokesLayout.add(lmb);
                    selectedButtons.clear();
                    selectedButtons.add(lmb);
                    currentState = InteractionState.BOX_SELECTED;
                    break;
                case 3: // Add RMB (Wide Style)
                    SimpleCPSConfig.KeyButtonData rmb = new SimpleCPSConfig.KeyButtonData("RMB", 33, 60, 31, 12, 1, true);
                    rmb.showCps = true;
                    config.keystrokesLayout.add(rmb);
                    selectedButtons.clear();
                    selectedButtons.add(rmb);
                    currentState = InteractionState.BOX_SELECTED;
                    break;
                case 4: // Add Modifier
                    addNewButton("CTRL", 0, 80, 31, 12, GLFW.GLFW_KEY_LEFT_CONTROL);
                    break;
                case 5: // Reset
                    config.resetLayout();
                    selectedButtons.clear();
                    currentState = InteractionState.IDLE;
                    break;
            }
        }
    }
    
    private void addNewButton(String label, int x, int y, int w, int h, int key) {
        // Smart Add Logic: Relative to last selected if any
        if (!selectedButtons.isEmpty()) {
            SimpleCPSConfig.KeyButtonData last = selectedButtons.get(selectedButtons.size() - 1);
            x = last.x + 10;
            y = last.y + 10;
            // Bound check? Na.
        }
        
        SimpleCPSConfig.KeyButtonData newBtn = new SimpleCPSConfig.KeyButtonData(label, x, y, w, h, key);
        config.keystrokesLayout.add(newBtn);
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
        int threshold = 5;
        int centerX = width / 2;
        int centerY = height / 2;
        
        int tLeft = centerX + target.x;
        int tRight = tLeft + target.w;
        int tTop = centerY + target.y;
        int tBottom = tTop + target.h;
        int tCX = tLeft + target.w / 2;
        int tCY = tTop + target.h / 2;
        
        for (SimpleCPSConfig.KeyButtonData other : config.keystrokesLayout) {
            if (other == target) continue;
            // Ignore other selected buttons to prevent self-snapping within group?
            if (selectedButtons.contains(other)) continue;
            
            int oLeft = centerX + other.x;
            int oRight = oLeft + other.w;
            int oTop = centerY + other.y;
            int oBottom = oTop + other.h;
            int oCX = oLeft + other.w / 2;
            int oCY = oTop + other.h / 2;
            
            // X Snapping
            if (moving) {
                if (Math.abs(tLeft - oLeft) < threshold) { target.x = other.x; snapX = oLeft; }
                else if (Math.abs(tRight - oRight) < threshold) { target.x = other.x + other.w - target.w; snapX = oRight; }
                else if (Math.abs(tLeft - oRight) < threshold) { target.x = other.x + other.w; snapX = oRight; }
                else if (Math.abs(tRight - oLeft) < threshold) { target.x = other.x - target.w; snapX = oLeft; }
                else if (Math.abs(tCX - oCX) < threshold) { target.x = other.x + (other.w - target.w)/2; snapX = oCX; }
            } 
            // Y Snapping
            if (moving) {
                 if (Math.abs(tTop - oTop) < threshold) { target.y = other.y; snapY = oTop; }
                else if (Math.abs(tBottom - oBottom) < threshold) { target.y = other.y + other.h - target.h; snapY = oBottom; }
                else if (Math.abs(tTop - oBottom) < threshold) { target.y = other.y + other.h; snapY = oBottom; }
                else if (Math.abs(tBottom - oTop) < threshold) { target.y = other.y - target.h; snapY = oTop; }
                else if (Math.abs(tCY - oCY) < threshold) { target.y = other.y + (other.h - target.h)/2; snapY = oCY; }
            }
        }
    }
    private void saveUndo() {
        String json = gson.toJson(config.keystrokesLayout);
        undoStack.push(json);
        redoStack.clear();
    }
    
    private void undo() {
        if (!undoStack.isEmpty()) {
            String current = gson.toJson(config.keystrokesLayout);
            redoStack.push(current);
            
            String json = undoStack.pop();
            List<SimpleCPSConfig.KeyButtonData> list = gson.fromJson(json, new TypeToken<List<SimpleCPSConfig.KeyButtonData>>(){}.getType());
            config.keystrokesLayout.clear();
            config.keystrokesLayout.addAll(list);
            
            // Clear selection to avoid ghost references
            selectedButtons.clear();
            currentState = InteractionState.IDLE;
        }
    }
    
    private void redo() {
        if (!redoStack.isEmpty()) {
            String current = gson.toJson(config.keystrokesLayout);
            undoStack.push(current);
            
            String json = redoStack.pop();
            List<SimpleCPSConfig.KeyButtonData> list = gson.fromJson(json, new TypeToken<List<SimpleCPSConfig.KeyButtonData>>(){}.getType());
            config.keystrokesLayout.clear();
            config.keystrokesLayout.addAll(list);
            
            selectedButtons.clear();
            currentState = InteractionState.IDLE;
        }
    }
            

    private boolean isCtrlDown() {
        long window = client.getWindow().getHandle();
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
}
