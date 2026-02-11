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
    private SimpleCPSConfig.KeyButtonData selectedButton = null;
    private boolean dragging = false;
    private boolean resizing = false;
    private boolean draggingLabel = false;
    private boolean editingLabel = false;
    
    // Undo/Redo
    private final Stack<String> undoStack = new Stack<>();
    private final Stack<String> redoStack = new Stack<>();
    private final Gson gson = new Gson();

    // Drag offsets & State
    private double dragStartX, dragStartY;
    private int originalX, originalY, originalW, originalH;
    private int originalLabelX, originalLabelY;

    private enum ResizeHandle {
        TOP_LEFT, TOP, TOP_RIGHT, LEFT, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT, NONE, MOVE
    }
    private ResizeHandle draggingHandle = ResizeHandle.NONE;
    private ResizeHandle hoveredHandle = ResizeHandle.NONE;

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
    private long lastPressTime = 0; // For repeat keys if needed

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
            
            context.drawText(textRenderer, text, lx, ly, 0xFFFFFFFF, btn.shadow);
            
            if (isSelected && !contextMenuOpen) {
                drawResizeHandles(context, x, y, btn.w, btn.h);
            }
        }
        
        context.drawText(textRenderer, "Drag to move | Drag Corner to resize | Right Click to Edit", 10, 10, 0xFFFFFF, true);

        // Context Menu
        if (contextMenuOpen) {
            renderContextMenu(context, mouseX, mouseY);
        }
    }

    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        int w = 120;
        int h = (contextMenuTarget != null) ? 140 : 40; 
        int x = contextMenuX;
        int y = contextMenuY;
        
        context.fill(x, y, x + w, y + h, 0xFF222222);
        drawBorder(context, x, y, w, h, 0xFFFFFFFF);
        
        if (contextMenuTarget != null) {
            drawContextMenuItem(context, "Set Keybind", x, y, mouseX, mouseY, 0);
            drawContextMenuItem(context, "Edit Label", x, y, mouseX, mouseY, 1);
            drawContextMenuItem(context, "Toggle Bold", x, y, mouseX, mouseY, 2);
            drawContextMenuItem(context, "Toggle Italic", x, y, mouseX, mouseY, 3);
            drawContextMenuItem(context, "Toggle Underline", x, y, mouseX, mouseY, 4);
            drawContextMenuItem(context, "Delete", x, y, mouseX, mouseY, 5);
            drawContextMenuItem(context, "Add Button", x, y, mouseX, mouseY, 6);
        } else {
            drawContextMenuItem(context, "Add Button", x, y, mouseX, mouseY, 0);
            drawContextMenuItem(context, "Reset Layout", x, y, mouseX, mouseY, 1);
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

        // Keybind Setting & Label Editing & Hotkeys
        if ((waitingForKeybind || editingLabel || selectedButton != null || true) && !contextMenuOpen) { // Scan keys
             for (int k = 32; k < GLFW.GLFW_KEY_LAST; k++) {
                  if (GLFW.glfwGetKey(windowHandle, k) == GLFW.GLFW_PRESS) {
                      if (lastPressedKey != k || (System.currentTimeMillis() - lastPressTime > 200)) { // Debounce + Repeat
                          lastPressTime = System.currentTimeMillis();
                          lastPressedKey = k;
                          
                          boolean ctrl = (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);
                          boolean shift = (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);

                          if (waitingForKeybind) {
                              if (k != GLFW.GLFW_KEY_ESCAPE) {
                                  if (selectedButton != null) {
                                      selectedButton.keyCode = k;
                                      selectedButton.label = getKeyName(k);
                                  }
                              }
                              waitingForKeybind = false;
                              return;
                          } 
                          
                          if (editingLabel && selectedButton != null) {
                              if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_ESCAPE) {
                                  editingLabel = false;
                                  saveUndo();
                              } else if (k == GLFW.GLFW_KEY_BACKSPACE) {
                                  String lbl = selectedButton.label;
                                  if (!lbl.isEmpty()) selectedButton.label = lbl.substring(0, lbl.length() - 1);
                              } else {
                                  if (k == GLFW.GLFW_KEY_SPACE) selectedButton.label += " ";
                                  else {
                                      String n = GLFW.glfwGetKeyName(k, 0);
                                      if (n != null) selectedButton.label += shift ? n.toUpperCase() : n;
                                      else {
                                          if (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9) selectedButton.label += (k - GLFW.GLFW_KEY_0);
                                      }
                                  }
                              }
                              return;
                          }
                          
                          // Undo/Redo
                          if (k == GLFW.GLFW_KEY_Z && ctrl) {
                              if (shift) redo(); else undo();
                              return;
                          }
                          if (k == GLFW.GLFW_KEY_Y && ctrl) {
                              redo();
                              return;
                          }
                          
                          // Arrows
                          if (selectedButton != null && !editingLabel) {
                              int step = shift ? 10 : 1;
                              boolean moved = false;
                              if (k == GLFW.GLFW_KEY_UP) { saveUndo(); selectedButton.y -= step; moved = true; }
                              if (k == GLFW.GLFW_KEY_DOWN) { saveUndo(); selectedButton.y += step; moved = true; }
                              if (k == GLFW.GLFW_KEY_LEFT) { saveUndo(); selectedButton.x -= step; moved = true; }
                              if (k == GLFW.GLFW_KEY_RIGHT) { saveUndo(); selectedButton.x += step; moved = true; }
                              if (moved) return;
                          }
                      }
                      return; // Handle one key per frame
                  }
             }
             // Reset debounce if key released
             if (lastPressedKey != -1 && GLFW.glfwGetKey(windowHandle, lastPressedKey) == GLFW.GLFW_RELEASE) {
                 lastPressedKey = -1;
             }
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
             // Check if we are hovering a handle on the ALREADY selected button
             if (selectedButton != null && !contextMenuOpen) {
                 int x = centerX + selectedButton.x;
                 int y = centerY + selectedButton.y;
                 ResizeHandle handle = getHandle(mouseX, mouseY, x, y, selectedButton.w, selectedButton.h);
                 
                 if (handle != ResizeHandle.NONE && handle != ResizeHandle.MOVE) {
                     saveUndo();
                     draggingHandle = handle;
                     dragStartX = mouseX;
                     dragStartY = mouseY;
                     originalX = selectedButton.x;
                     originalY = selectedButton.y;
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
                    net.minecraft.text.MutableText text = net.minecraft.text.Text.literal(btn.label);
                    net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY;
                    if (btn.bold) style = style.withBold(true);
                    text.setStyle(style);
                    int labelW = textRenderer.getWidth(text);
                    int labelH = textRenderer.fontHeight;
                    int lx = (btn.labelX == -1) ? x + (btn.w - labelW) / 2 : x + btn.labelX;
                    int ly = (btn.labelY == -1) ? y + (btn.h - labelH) / 2 : y + btn.labelY;
                    
                    if (mouseX >= lx && mouseX <= lx + labelW && mouseY >= ly && mouseY <= ly + labelH) {
                        saveUndo();
                        draggingLabel = true;
                        originalLabelX = (btn.labelX == -1) ? (btn.w - labelW) / 2 : btn.labelX;
                        originalLabelY = (btn.labelY == -1) ? (btn.h - labelH) / 2 : btn.labelY;
                    } else {
                        saveUndo();
                        draggingHandle = ResizeHandle.MOVE;
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
            double dx = mouseX - dragStartX;
            double dy = mouseY - dragStartY;

            if (draggingLabel) {
                int newLabelX = originalLabelX + (int)dx;
                int newLabelY = originalLabelY + (int)dy;
                
                net.minecraft.text.MutableText text = net.minecraft.text.Text.literal(selectedButton.label);
                net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY;
                if (selectedButton.bold) style = style.withBold(true);
                text.setStyle(style);
                int labelW = textRenderer.getWidth(text);
                int labelH = textRenderer.fontHeight;
                
                int padding = 4;
                int maxX = selectedButton.w - labelW - padding;
                int maxY = selectedButton.h - labelH - padding;
                
                if (maxX < padding) maxX = padding;
                if (maxY < padding) maxY = padding;
                
                int finalX = Math.max(padding, Math.min(newLabelX, maxX));
                int finalY = Math.max(padding, Math.min(newLabelY, maxY));
                
                // Snap to Center
                int centerX = (selectedButton.w - labelW) / 2;
                int centerY = (selectedButton.h - labelH) / 2;
                if (Math.abs(finalX - centerX) <= 5) finalX = -1;
                if (Math.abs(finalY - centerY) <= 5) finalY = -1;
                
                selectedButton.labelX = finalX;
                selectedButton.labelY = finalY;
            
            } else if (draggingHandle == ResizeHandle.MOVE) {
                selectedButton.x = originalX + (int)dx;
                selectedButton.y = originalY + (int)dy;
                applySnapping(selectedButton, ResizeHandle.MOVE);
                
            } else if (draggingHandle != ResizeHandle.NONE) {
                int newX = originalX;
                int newY = originalY;
                int newW = originalW;
                int newH = originalH;

                if (draggingHandle == ResizeHandle.RIGHT || draggingHandle == ResizeHandle.TOP_RIGHT || draggingHandle == ResizeHandle.BOTTOM_RIGHT) {
                    newW = Math.max(10, originalW + (int)dx);
                }
                if (draggingHandle == ResizeHandle.LEFT || draggingHandle == ResizeHandle.TOP_LEFT || draggingHandle == ResizeHandle.BOTTOM_LEFT) {
                    int delta = (int)Math.min(dx, originalW - 10);
                    newX = originalX + delta;
                    newW = originalW - delta;
                }
                
                if (draggingHandle == ResizeHandle.BOTTOM || draggingHandle == ResizeHandle.BOTTOM_LEFT || draggingHandle == ResizeHandle.BOTTOM_RIGHT) {
                    newH = Math.max(10, originalH + (int)dy);
                }
                if (draggingHandle == ResizeHandle.TOP || draggingHandle == ResizeHandle.TOP_LEFT || draggingHandle == ResizeHandle.TOP_RIGHT) {
                     int delta = (int)Math.min(dy, originalH - 10);
                     newY = originalY + delta;
                     newH = originalH - delta;
                }
                
                selectedButton.x = newX;
                selectedButton.y = newY;
                selectedButton.w = newW;
                selectedButton.h = newH;
                
                applySnapping(selectedButton, draggingHandle);
            }
        }
    }

    private void onMouseRelease(int mouseX, int mouseY) {
        draggingHandle = ResizeHandle.NONE;
        draggingLabel = false;
        snapX = null;
        snapY = null;
    }

    private void handleMenuAction(int index) {
        if (contextMenuTarget != null) {
            saveUndo();
            switch(index) {
                case 0: // Set Keybind
                    waitingForKeybind = true;
                    lastPressedKey = -1;
                    break;
                case 1: // Edit Label
                    editingLabel = true;
                    waitingForKeybind = false;
                    break;
                case 2: contextMenuTarget.bold = !contextMenuTarget.bold; break;
                case 3: contextMenuTarget.italic = !contextMenuTarget.italic; break;
                case 4: contextMenuTarget.underlined = !contextMenuTarget.underlined; break;
                case 5: // Delete
                    config.keystrokesLayout.remove(contextMenuTarget);
                    selectedButton = null;
                    contextMenuTarget = null;
                    break;
                case 6: // Add Button
                    addNewButton();
                    break;
            }
        } else {
            if (index == 0) { saveUndo(); addNewButton(); }
            if (index == 1) { 
                saveUndo(); 
                config.keystrokesLayout.clear(); 
                
                // Factory Defaults: WASD + Space
                addNewButton("W", 22, 0, 20, 20, GLFW.GLFW_KEY_W);
                addNewButton("A", 0, 22, 20, 20, GLFW.GLFW_KEY_A);
                addNewButton("S", 22, 22, 20, 20, GLFW.GLFW_KEY_S);
                addNewButton("D", 44, 22, 20, 20, GLFW.GLFW_KEY_D);
                addNewButton("SPACE", 0, 44, 64, 12, GLFW.GLFW_KEY_SPACE);
                
                selectedButton = null;
            }
        }
    }
    
    private void addNewButton() {
        addNewButton("New", 0, 0, 30, 20, GLFW.GLFW_KEY_UNKNOWN);
    }
    
    private void addNewButton(String label, int x, int y, int w, int h, int key) {
        SimpleCPSConfig.KeyButtonData newBtn = new SimpleCPSConfig.KeyButtonData(label, x, y, w, h, key);
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

    private void applySnapping(SimpleCPSConfig.KeyButtonData target, ResizeHandle handle) {
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
        
        boolean moving = (handle == ResizeHandle.MOVE);
        boolean resizingLeft = (handle == ResizeHandle.LEFT || handle == ResizeHandle.TOP_LEFT || handle == ResizeHandle.BOTTOM_LEFT);
        boolean resizingRight = (handle == ResizeHandle.RIGHT || handle == ResizeHandle.TOP_RIGHT || handle == ResizeHandle.BOTTOM_RIGHT);
        boolean resizingTop = (handle == ResizeHandle.TOP || handle == ResizeHandle.TOP_LEFT || handle == ResizeHandle.TOP_RIGHT);
        boolean resizingBottom = (handle == ResizeHandle.BOTTOM || handle == ResizeHandle.BOTTOM_LEFT || handle == ResizeHandle.BOTTOM_RIGHT);
        
        for (SimpleCPSConfig.KeyButtonData other : config.keystrokesLayout) {
            if (other == target) continue;
            
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
            } else {
                if (resizingLeft) {
                    if (Math.abs(tLeft - oLeft) < threshold) { int diff = target.x - other.x; target.x = other.x; target.w += diff; snapX = oLeft; }
                    else if (Math.abs(tLeft - oRight) < threshold) { int diff = target.x - (other.x + other.w); target.x = other.x + other.w; target.w += diff; snapX = oRight; }
                }
                if (resizingRight) {
                    if (Math.abs(tRight - oRight) < threshold) { target.w = (other.x + other.w) - target.x; snapX = oRight; }
                    else if (Math.abs(tRight - oLeft) < threshold) { target.w = other.x - target.x; snapX = oLeft; }
                }
            }

            // Y Snapping
            if (moving) {
                 if (Math.abs(tTop - oTop) < threshold) { target.y = other.y; snapY = oTop; }
                else if (Math.abs(tBottom - oBottom) < threshold) { target.y = other.y + other.h - target.h; snapY = oBottom; }
                else if (Math.abs(tTop - oBottom) < threshold) { target.y = other.y + other.h; snapY = oBottom; }
                else if (Math.abs(tBottom - oTop) < threshold) { target.y = other.y - target.h; snapY = oTop; }
                else if (Math.abs(tCY - oCY) < threshold) { target.y = other.y + (other.h - target.h)/2; snapY = oCY; }
            } else {
                if (resizingTop) {
                    if (Math.abs(tTop - oTop) < threshold) { int diff = target.y - other.y; target.y = other.y; target.h += diff; snapY = oTop; }
                    else if (Math.abs(tTop - oBottom) < threshold) { int diff = target.y - (other.y + other.h); target.y = other.y + other.h; target.h += diff; snapY = oBottom; }
                }
                if (resizingBottom) {
                     if (Math.abs(tBottom - oBottom) < threshold) { target.h = (other.y + other.h) - target.y; snapY = oBottom; }
                     else if (Math.abs(tBottom - oTop) < threshold) { target.h = other.y - target.y; snapY = oTop; }
                }
            }
            }
        
        // Equal Distribution Snapping (Midpoint)
        if (moving) {
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

    private void drawResizeHandles(DrawContext context, int x, int y, int w, int h) {
        int handleSize = 6;
        int hs2 = handleSize / 2;
        int color = 0x88FFFFFF;
        
        // Corners
        context.fill(x - hs2, y - hs2, x + hs2, y + hs2, color); // TL
        context.fill(x + w - hs2, y - hs2, x + w + hs2, y + hs2, color); // TR
        context.fill(x - hs2, y + h - hs2, x + hs2, y + h + hs2, color); // BL
        context.fill(x + w - hs2, y + h - hs2, x + w + hs2, y + h + hs2, color); // BR
        
        // Sides (Small indicators)
        int mx = x + w / 2;
        int my = y + h / 2;
        context.fill(mx - hs2, y - hs2, mx + hs2, y + hs2, color); // Top
        context.fill(mx - hs2, y + h - hs2, mx + hs2, y + h + hs2, color); // Bottom
        context.fill(x - hs2, my - hs2, x + hs2, my + hs2, color); // Left
        context.fill(x + w - hs2, my - hs2, x + w + hs2, my + hs2, color); // Right
    }
    
    private ResizeHandle getHandle(int mx, int my, int x, int y, int w, int h) {
        int margin = 6;
        
        boolean nearLeft = Math.abs(mx - x) <= margin;
        boolean nearRight = Math.abs(mx - (x + w)) <= margin;
        boolean nearTop = Math.abs(my - y) <= margin;
        boolean nearBottom = Math.abs(my - (y + h)) <= margin;
        
        boolean insideX = mx > x + margin && mx < x + w - margin;
        boolean insideY = my > y + margin && my < y + h - margin;
        
        if (nearTop && nearLeft) return ResizeHandle.TOP_LEFT;
        if (nearTop && nearRight) return ResizeHandle.TOP_RIGHT;
        if (nearBottom && nearLeft) return ResizeHandle.BOTTOM_LEFT;
        if (nearBottom && nearRight) return ResizeHandle.BOTTOM_RIGHT;
        
        if (nearTop && insideX) return ResizeHandle.TOP;
        if (nearBottom && insideX) return ResizeHandle.BOTTOM;
        if (nearLeft && insideY) return ResizeHandle.LEFT;
        if (nearRight && insideY) return ResizeHandle.RIGHT;
        
        return ResizeHandle.NONE;
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
            selectedButton = null;
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
            selectedButton = null;
        }
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
