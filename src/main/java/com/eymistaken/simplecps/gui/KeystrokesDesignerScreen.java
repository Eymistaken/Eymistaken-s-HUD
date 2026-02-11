package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.SimpleCPSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class KeystrokesDesignerScreen extends Screen {

    private final SimpleCPSConfig config;
    private SimpleCPSConfig.KeyButtonData selectedButton = null;
    private boolean dragging = false;
    private boolean resizing = false;
    private boolean draggingLabel = false;
    
    // Drag offsets
    private double dragStartX, dragStartY;
    private int originalX, originalY, originalW, originalH;
    private int originalLabelX, originalLabelY;

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

    // Polling State
    private boolean wasLeftDown = false;
    private boolean wasRightDown = false;

    // Context Menu
    private boolean contextMenuOpen = false;
    private int contextMenuX, contextMenuY;
    private SimpleCPSConfig.KeyButtonData contextMenuTarget = null;
    
    // Editing
    private boolean waitingForKeybind = false;
    // Helper to debounce key presses
    private int lastPressedKey = -1;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Gradient Background (matching HudEditorScreen)
        context.fillGradient(0, 0, this.width, this.height, 0xC0000000, 0xD0000000); 
        
        handleInput(mouseX, mouseY);

        // Origin Crosshair
        
        // Origin Crosshair
        int centerX = width / 2;
        int centerY = height / 2;
        context.fill(centerX - 1, 0, centerX + 1, height, 0x44FFFFFF);
        context.fill(0, centerY - 1, width, centerY + 1, 0x44FFFFFF);
        context.drawText(textRenderer, "Module Center", centerX + 5, centerY + 5, 0xAAAAAA, false);

        // Snap Lines
        if (snapX != null) context.fill(snapX, 0, snapX + 1, height, 0xFFFF00FF);
        if (snapY != null) context.fill(0, snapY, width, snapY + 1, 0xFFFF00FF);

        // Draw Buttons
        for (SimpleCPSConfig.KeyButtonData btn : config.keystrokesLayout) {
            int x = centerX + btn.x;
            int y = centerY + btn.y;
            boolean isSelected = (btn == selectedButton);
            
            int bgColor = 0xAA000000;
            if (btn == contextMenuTarget) bgColor = 0xAA444400; // Highlight target
            int borderColor = isSelected ? 0xFF00FF00 : 0xFFFFFFFF;
            
            context.fill(x, y, x + btn.w, y + btn.h, bgColor);
            drawBorder(context, x, y, btn.w, btn.h, borderColor);
            
            // Label
            String label = btn.label;
            if (waitingForKeybind && btn == selectedButton) label = "PRESS KEY...";

            int labelW = textRenderer.getWidth(label);
            int labelH = textRenderer.fontHeight;
            
            int lx = (btn.labelX == -1) ? x + (btn.w - labelW) / 2 : x + btn.labelX;
            int ly = (btn.labelY == -1) ? y + (btn.h - labelH) / 2 : y + btn.labelY;
            
            context.drawText(textRenderer, label, lx, ly, 0xFFFFFFFF, btn.shadow);
            
            if (isSelected && !contextMenuOpen) {
                // Resize Handle (Bottom Right)
                context.fill(x + btn.w - 4, y + btn.h - 4, x + btn.w, y + btn.h, 0xFF00FFFF);
            }
        }
        
        context.drawText(textRenderer, "Drag to move | Drag Corner to resize | Right Click to Edit", 10, 10, 0xFFFFFF, true);

        // Context Menu
        if (contextMenuOpen) {
            renderContextMenu(context, mouseX, mouseY);
        }
    }

    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        int w = 100;
        int h = (contextMenuTarget != null) ? 80 : 20; // 4 items if target, 1 if not
        int x = contextMenuX;
        int y = contextMenuY;
        
        context.fill(x, y, x + w, y + h, 0xFF222222);
        drawBorder(context, x, y, w, h, 0xFFFFFFFF);
        
        if (contextMenuTarget != null) {
            drawContextMenuItem(context, "Set Keybind", x, y, mouseX, mouseY, 0);
            drawContextMenuItem(context, "Delete", x, y, mouseX, mouseY, 1);
            drawContextMenuItem(context, "Add Button", x, y, mouseX, mouseY, 2);
        } else {
            drawContextMenuItem(context, "Add Button", x, y, mouseX, mouseY, 0);
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

    private void handleInput(int mouseX, int mouseY) {
        long windowHandle = client.getWindow().getHandle();
        boolean isLeftDown = GLFW.glfwGetMouseButton(windowHandle, 0) == GLFW.GLFW_PRESS;
        boolean isRightDown = GLFW.glfwGetMouseButton(windowHandle, 1) == GLFW.GLFW_PRESS;

        // Keybind Setting
        if (waitingForKeybind && selectedButton != null) {
            for (int k = 32; k < GLFW.GLFW_KEY_LAST; k++) { // Scan common keys
                 if (GLFW.glfwGetKey(windowHandle, k) == GLFW.GLFW_PRESS) {
                     if (lastPressedKey != k) { // Debounce
                         if (k != GLFW.GLFW_KEY_ESCAPE) {
                             selectedButton.keyCode = k;
                             // Basic label mapping
                             String name = GLFW.glfwGetKeyName(k, 0);
                             if (name == null) {
                                 if (k == GLFW.GLFW_KEY_SPACE) name = "SPACE";
                                 else name = "K" + k;
                             }
                             selectedButton.label = name.toUpperCase();
                         }
                         waitingForKeybind = false;
                         lastPressedKey = k;
                     }
                     return; 
                 }
            }
            // Reset debounce if no relevant key pressed
            boolean anyPressed = false;
            if (GLFW.glfwGetKey(windowHandle, lastPressedKey) == GLFW.GLFW_PRESS) anyPressed = true;
            if (!anyPressed) lastPressedKey = -1;
        }
        
        // Mouse Click Handling
        if (contextMenuOpen) {
            if (isLeftDown && !wasLeftDown) {
                int w = 100;
                int h = (contextMenuTarget != null) ? 60 : 20; 
                if (mouseX >= contextMenuX && mouseX <= contextMenuX + w && mouseY >= contextMenuY && mouseY <= contextMenuY + h) {
                    int index = (int)((mouseY - contextMenuY) / 20);
                    handleMenuAction(index);
                }
                contextMenuOpen = false; 
            }
        } else {
             if (isLeftDown && !wasLeftDown) { // On Click
                 onMouseClick(mouseX, mouseY, 0);
             } else if (isRightDown && !wasRightDown) { // On Right Click
                 onMouseClick(mouseX, mouseY, 1);
             } else if (isLeftDown && wasLeftDown) { // On Drag
                 onMouseDrag(mouseX, mouseY);
             } else if (!isLeftDown && wasLeftDown) { // On Release
                 onMouseRelease(mouseX, mouseY);
             }
        }

        wasLeftDown = isLeftDown;
        wasRightDown = isRightDown;
    }

    private void onMouseClick(int mouseX, int mouseY, int button) {
        int centerX = width / 2;
        int centerY = height / 2;

        if (button == 0) { // Left Click
             // Check Resize Handle of Selected
            if (selectedButton != null) {
                int x = centerX + selectedButton.x;
                int y = centerY + selectedButton.y;
                if (mouseX >= x + selectedButton.w - 5 && mouseX <= x + selectedButton.w + 5 &&
                    mouseY >= y + selectedButton.h - 5 && mouseY <= y + selectedButton.h + 5) {
                    resizing = true;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    originalW = selectedButton.w;
                    originalH = selectedButton.h;
                    return;
                }
            }

            // Check Buttons (Reverse order for Z-index)
            List<SimpleCPSConfig.KeyButtonData> layout = config.keystrokesLayout;
            for (int i = layout.size() - 1; i >= 0; i--) {
                SimpleCPSConfig.KeyButtonData btn = layout.get(i);
                int x = centerX + btn.x;
                int y = centerY + btn.y;
                
                if (mouseX >= x && mouseX <= x + btn.w && mouseY >= y && mouseY <= y + btn.h) {
                    selectedButton = btn;
                    
                    // Check if clicked ON LABEL
                    String label = btn.label;
                    int labelW = textRenderer.getWidth(label);
                    int labelH = textRenderer.fontHeight;
                    int lx = (btn.labelX == -1) ? x + (btn.w - labelW) / 2 : x + btn.labelX;
                    int ly = (btn.labelY == -1) ? y + (btn.h - labelH) / 2 : y + btn.labelY;
                    
                    if (mouseX >= lx && mouseX <= lx + labelW && mouseY >= ly && mouseY <= ly + labelH) {
                        draggingLabel = true;
                        originalLabelX = (btn.labelX == -1) ? (btn.w - labelW) / 2 : btn.labelX;
                        originalLabelY = (btn.labelY == -1) ? (btn.h - labelH) / 2 : btn.labelY;
                    } else {
                        dragging = true;
                        originalX = btn.x;
                        originalY = btn.y;
                    }
                    
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    return;
                }
            }
            selectedButton = null; // Deselect
        } else if (button == 1) { // Right Click
             List<SimpleCPSConfig.KeyButtonData> layout = config.keystrokesLayout;
             SimpleCPSConfig.KeyButtonData clickedBtn = null;
            for (int i = layout.size() - 1; i >= 0; i--) {
                SimpleCPSConfig.KeyButtonData btn = layout.get(i);
                int x = centerX + btn.x;
                int y = centerY + btn.y;
                 if (mouseX >= x && mouseX <= x + btn.w && mouseY >= y && mouseY <= y + btn.h) {
                     clickedBtn = btn;
                     selectedButton = btn;
                     break;
                 }
            }
            contextMenuTarget = clickedBtn;
            contextMenuOpen = true;
            contextMenuX = mouseX;
            contextMenuY = mouseY;
        }
    }

    private void onMouseDrag(int mouseX, int mouseY) {
        if (selectedButton != null) {
            if (resizing) {
                selectedButton.w = Math.max(10, originalW + (int)(mouseX - dragStartX));
                selectedButton.h = Math.max(10, originalH + (int)(mouseY - dragStartY));
            } else if (draggingLabel) {
                selectedButton.labelX = originalLabelX + (int)(mouseX - dragStartX);
                selectedButton.labelY = originalLabelY + (int)(mouseY - dragStartY);
            } else if (dragging) {
                selectedButton.x = originalX + (int)(mouseX - dragStartX);
                selectedButton.y = originalY + (int)(mouseY - dragStartY);
                applySnapping(selectedButton);
            }
        }
    }

    private void onMouseRelease(int mouseX, int mouseY) {
        dragging = false;
        resizing = false;
        draggingLabel = false;
        snapX = null;
        snapY = null;
    }

    private void handleMenuAction(int index) {
        if (contextMenuTarget != null) {
            switch(index) {
                case 0: // Set Keybind
                    waitingForKeybind = true;
                    lastPressedKey = -1;
                    break;
                case 1: // Delete
                    config.keystrokesLayout.remove(contextMenuTarget);
                    selectedButton = null;
                    contextMenuTarget = null;
                    break;
                case 2: // Add Button
                    addNewButton();
                    break;
            }
        } else {
            if (index == 0) addNewButton();
        }
    }
    
    private void addNewButton() {
        SimpleCPSConfig.KeyButtonData newBtn = new SimpleCPSConfig.KeyButtonData("New", 0, 0, 30, 20, GLFW.GLFW_KEY_UNKNOWN);
        config.keystrokesLayout.add(newBtn);
        selectedButton = newBtn;
    }
    
    @Override
    public void close() {
        SimpleCPSConfig.save();
        if (parent != null) {
            client.setScreen(parent);
        } else {
            super.close();
        }
    }

    private void applySnapping(SimpleCPSConfig.KeyButtonData target) {
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
            
            int oLeft = centerX + other.x;
            int oRight = oLeft + other.w;
            int oTop = centerY + other.y;
            int oBottom = oTop + other.h;
            int oCX = oLeft + other.w / 2;
            int oCY = oTop + other.h / 2;
            
            // X Snapping
            if (Math.abs(tLeft - oLeft) < threshold) { target.x = other.x; snapX = oLeft; }
            else if (Math.abs(tRight - oRight) < threshold) { target.x = other.x + other.w - target.w; snapX = oRight; }
            else if (Math.abs(tLeft - oRight) < threshold) { target.x = other.x + other.w; snapX = oRight; }
            else if (Math.abs(tRight - oLeft) < threshold) { target.x = other.x - target.w; snapX = oLeft; }
            else if (Math.abs(tCX - oCX) < threshold) { target.x = other.x + (other.w - target.w)/2; snapX = oCX; }
            
            // Y Snapping
            if (Math.abs(tTop - oTop) < threshold) { target.y = other.y; snapY = oTop; }
            else if (Math.abs(tBottom - oBottom) < threshold) { target.y = other.y + other.h - target.h; snapY = oBottom; }
            else if (Math.abs(tTop - oBottom) < threshold) { target.y = other.y + other.h; snapY = oBottom; }
            else if (Math.abs(tBottom - oTop) < threshold) { target.y = other.y - target.h; snapY = oTop; }
            else if (Math.abs(tCY - oCY) < threshold) { target.y = other.y + (other.h - target.h)/2; snapY = oCY; }
        }
        
        // Equal Distribution Snapping (Midpoint)
        for (SimpleCPSConfig.KeyButtonData A : config.keystrokesLayout) {
            if (A == target) continue;
            for (SimpleCPSConfig.KeyButtonData B : config.keystrokesLayout) {
                if (B == target || A == B) continue;
                
                // Horizontal Gap (Target between A and B)
                int aRight = centerX + A.x + A.w;
                int bLeft = centerX + B.x;
                
                if (aRight < bLeft) {
                    // Check vertical overlap
                    int aTop = centerY + A.y; int aBot = aTop + A.h;
                    int bTop = centerY + B.y; int bBot = bTop + B.h;
                    if (tBottom > aTop && tTop < aBot && tBottom > bTop && tTop < bBot) {
                        int midX = (aRight + bLeft - target.w) / 2;
                         if (Math.abs(tLeft - midX) < threshold) {
                             target.x = midX - centerX;
                             snapX = midX;
                         }
                    }
                }
                
                // Vertical Gap (Target between A and B)
                int aBot = centerY + A.y + A.h;
                int bTop = centerY + B.y;
                
                if (aBot < bTop) {
                    // Check horizontal overlap
                    int aL = centerX + A.x; int aR = aL + A.w;
                    int bL = centerX + B.x; int bR = bL + B.w;
                    if (tRight > aL && tLeft < aR && tRight > bL && tLeft < bR) {
                        int midY = (aBot + bTop - target.h) / 2;
                        if (Math.abs(tTop - midY) < threshold) {
                            target.y = midY - centerY;
                            snapY = midY;
                        }
                    }
                }
            }
        }
    }
}
