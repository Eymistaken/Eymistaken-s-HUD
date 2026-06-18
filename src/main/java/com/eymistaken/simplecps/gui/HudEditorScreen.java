package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.HudModuleManager;
import com.eymistaken.simplecps.HudPlacementResolver;
import com.eymistaken.simplecps.SimpleCPSClient;
import com.eymistaken.simplecps.SimpleCPSConfig;
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
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    private int clamp(int val) {
        return Math.max(50, Math.min(300, val));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Gradient Background
        context.fillGradient(0, 0, this.width, this.height, 0xC0000000, 0xD0000000); 

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

                context.text(this.font, element.getName(), ex, ey - 10, 0xFF00FFFF, true);
            } else if (isHovered || element == draggingElement) {
                int borderColor = 0xFFFFFFFF;
                context.fill(ex - 1, ey - 1, ex + ew + 1, ey, borderColor); // Top
                context.fill(ex - 1, ey + eh, ex + ew + 1, ey + eh + 1, borderColor); // Bottom
                context.fill(ex - 1, ey, ex, ey + eh, borderColor); // Left
                context.fill(ex + ew, ey, ex + ew + 1, ey + eh, borderColor); // Right
                
                context.text(this.font, element.getName(), ex, ey - 10, 0xFFFFFFFF, true);
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
        
        context.centeredText(this.font, Component.nullToEmpty("Eymistaken's HUD"), this.width / 2, 10, 0xFFFFFFFF);

        if (contextMenuOpen && contextMenuTarget != null) {
            renderContextMenu(context, mouseX, mouseY);
        }
        if (globalMenuOpen) {
            renderGlobalMenu(context, mouseX, mouseY);
        }
        
        if (textEditTarget != null) {
            int cx = this.width / 2;
            int cy = this.height - 40;
            context.fill(cx - 100, cy - 10, cx + 100, cy + 10, 0xAA000000);
            context.fill(cx - 101, cy - 11, cx + 101, cy - 10, 0xFFFFFFFF);
            context.fill(cx - 101, cy + 10, cx + 101, cy + 11, 0xFFFFFFFF);
            context.fill(cx - 101, cy - 10, cx - 100, cy + 10, 0xFFFFFFFF);
            context.fill(cx + 100, cy - 10, cx + 101, cy + 10, 0xFFFFFFFF);
            
            context.centeredText(this.font, textEditTarget.label + ": " + textEditBuffer + "_", cx, cy - 4, 0xFFFFFFFF);
        }
    }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (globalMenuOpen) {
            int menuW = 180;
            int menuH = getGlobalMenuHeight();
            if (mouseX >= globalMenuX && mouseX <= globalMenuX + menuW &&
                mouseY >= globalMenuY && mouseY <= globalMenuY + menuH) {
                if (mouseY >= globalMenuY + 20 && mouseY < globalMenuY + 40) {
                    SimpleCPSConfig.instance.preventOverlap = !SimpleCPSConfig.instance.preventOverlap;
                    SimpleCPSConfig.save();
                    return true;
                } else if (mouseY >= globalMenuY + 40 && mouseY < globalMenuY + 60) {
                    resetHudLayout();
                    globalMenuOpen = false;
                    return true;
                }
                return true;
            } else {
                globalMenuOpen = false;
            }
        }
        
        if (contextMenuOpen && contextMenuTarget != null) {
            int menuW = 220;
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
                    contextMenuX = (int)mouseX;
                    
                    int h = getMenuHeight(element);
                    int y = (int)mouseY;
                    
                    if (y + h > this.height) y = y - h;
                    if (y < 0) y = 0;
                    if (y + h > this.height) y = this.height - h;
                    
                    contextMenuY = y;
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
                applyElementScreenPosition(el, targetX, targetY);
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
        return 60;
    }

    private void openGlobalMenu(int mouseX, int mouseY) {
        globalMenuOpen = true;
        contextMenuOpen = false;
        globalMenuX = mouseX;

        int h = getGlobalMenuHeight();
        int y = mouseY;
        if (y + h > this.height) y = y - h;
        if (y < 0) y = 0;
        if (y + h > this.height) y = this.height - h;
        globalMenuY = y;
    }

    private void resetHudLayout() {
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

    private void renderContextMenu(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int w = 220;
        int h = getMenuHeight(contextMenuTarget);
        int x = contextMenuX;
        int y = contextMenuY;

        context.fill(x, y, x + w, y + h, 0xFF222222);
        context.fill(x - 1, y - 1, x + w + 1, y, 0xFFFFFFFF); // Top
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFFFFFFFF); // Bottom
        context.fill(x - 1, y, x, y + h, 0xFFFFFFFF); // Left
        context.fill(x + w, y, x + w + 1, y + h, 0xFFFFFFFF); // Right
        
        // As defined in Phase 4 earlier requests, we don't have Reset Position here natively
        // Actually, this line is preserved for retro-compatibility / the HudEditor specific entry.
        // Wait, the user said "Reset Position zaten HudEditorScreen tarafindan menuye ayrica ekleniyor."
        // We preserved the 'y + 20' skip in hit testing, so the 0th item is the Reset Position.
        boolean hoverReset = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + 20;
        if (hoverReset) context.fill(x, y, x + w, y + 20, 0xFF444444);
        context.text(this.font, "Reset Position", x + 5, y + 6, 0xFFFFFFFF, true);
        
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = contextMenuTarget.getContextMenuSettings();
        int currentY = y + 20;
        
        for (int i = 0; i < settings.size(); i++) {
            com.eymistaken.simplecps.api.HudModuleSetting setting = settings.get(i);
            int itemHeight = (setting instanceof com.eymistaken.simplecps.api.SliderSetting) ? 24 : 20;
            
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= currentY && mouseY < currentY + itemHeight;
            if (hovered) context.fill(x, currentY, x + w, currentY + itemHeight, 0xFF444444);
            
            context.text(this.font, setting.label, x + 5, currentY + (itemHeight / 2) - 4, 0xFFFFFFFF, true);
            
            if (setting instanceof com.eymistaken.simplecps.api.BooleanSetting boolSetting) {
                String val = boolSetting.getter.get() ? "[ON]" : "[OFF]";
                int valW = this.font.width(val);
                int color = boolSetting.getter.get() ? 0xFF55FF55 : 0xFFFF5555;
                context.text(this.font, val, x + w - valW - 5, currentY + 6, color, true);
            } else if (setting instanceof com.eymistaken.simplecps.api.CycleSetting cycleSetting) {
                String val = cycleSetting.options.get(cycleSetting.getter.get());
                int valW = this.font.width(val);
                context.text(this.font, val, x + w - valW - 5, currentY + 6, 0xFFFFAA00, true);
            } else if (setting instanceof com.eymistaken.simplecps.api.ColorSetting colorSetting) {
                int c = colorSetting.getter.get() | 0xFF000000;
                context.fill(x + w - 15, currentY + 5, x + w - 5, currentY + 15, c);
                // border
                context.fill(x + w - 16, currentY + 4, x + w - 15, currentY + 16, 0xFFFFFFFF);
                context.fill(x + w - 4, currentY + 4, x + w - 5, currentY + 16, 0xFFFFFFFF);
                context.fill(x + w - 15, currentY + 4, x + w - 5, currentY + 5, 0xFFFFFFFF);
                context.fill(x + w - 15, currentY + 15, x + w - 5, currentY + 16, 0xFFFFFFFF);
            } else if (setting instanceof com.eymistaken.simplecps.api.TextSetting textSetting) {
                String val = textSetting.getter.get();
                if (val.length() > 5) val = val.substring(0, 5) + "...";
                int valW = this.font.width(val);
                context.text(this.font, val, x + w - valW - 5, currentY + 6, 0xFFAAAAAA, true);
            } else if (setting instanceof com.eymistaken.simplecps.api.SliderSetting slider) {
                int val = slider.getter.get();
                float ratio = (float)(val - slider.min) / (slider.max - slider.min);
                int fillWidth = (int)(94 * ratio);
                
                // Draw Bar Background
                context.fill(x + 78, currentY + 6, x + 78 + 94, currentY + 18, 0xFF000000);
                // Draw Bar Fill
                context.fill(x + 78, currentY + 6, x + 78 + fillWidth, currentY + 18, 0xFF36454F);
                // Draw Thumb
                context.fill(x + 78 + fillWidth - 2, currentY + 4, x + 78 + fillWidth + 2, currentY + 20, 0xFFAAAAAA);
                // Draw Value text centered in Bar
                String valStr = val + "%";
                int valW = this.font.width(valStr);
                context.text(this.font, valStr, x + 78 + (94 - valW) / 2, currentY + 8, 0xFFFFFFFF, true);
                
                // Draw Reset Button (width 40px starting at x+176)
                boolean hoverRButton = mouseX >= x + 176 && mouseX <= x + 176 + 40 && mouseY >= currentY && mouseY < currentY + itemHeight;
                int rColor = hoverRButton ? 0xFF990000 : 0xFF550000;
                context.fill(x + 176, currentY + 4, x + 176 + 40, currentY + 20, rColor);
                context.centeredText(this.font, Component.nullToEmpty("Reset"), x + 176 + 20, currentY + 8, 0xFFFFFFFF);
            }
            
            currentY += itemHeight;
        }
    }

    private void renderGlobalMenu(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        int w = 180;
        int h = getGlobalMenuHeight();
        int x = globalMenuX;
        int y = globalMenuY;

        context.fill(x, y, x + w, y + h, 0xFF222222);
        context.fill(x - 1, y - 1, x + w + 1, y, 0xFFFFFFFF);
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFFFFFFFF);
        context.fill(x - 1, y, x, y + h, 0xFFFFFFFF);
        context.fill(x + w, y, x + w + 1, y + h, 0xFFFFFFFF);

        context.text(this.font, "HUD Settings", x + 5, y + 6, 0xFFFFFFFF, true);

        int rowY = y + 20;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY < rowY + 20;
        if (hovered) {
            context.fill(x, rowY, x + w, rowY + 20, 0xFF444444);
        }

        context.text(this.font, "Prevent Overlap", x + 5, rowY + 6, 0xFFFFFFFF, true);
        String val = SimpleCPSConfig.instance.preventOverlap ? "[ON]" : "[OFF]";
        int valW = this.font.width(val);
        int color = SimpleCPSConfig.instance.preventOverlap ? 0xFF55FF55 : 0xFFFF5555;
        context.text(this.font, val, x + w - valW - 5, rowY + 6, color, true);

        int resetY = y + 40;
        boolean resetHovered = mouseX >= x && mouseX <= x + w && mouseY >= resetY && mouseY < resetY + 20;
        if (resetHovered) {
            context.fill(x, resetY, x + w, resetY + 20, 0xFF444444);
        }
        context.text(this.font, "Reset HUD", x + 5, resetY + 6, 0xFFFFAA55, true);
    }

    @Override
    public boolean charTyped(CharacterEvent charInput) {
        char chr = (char) charInput.codepoint();
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
                pos = new java.awt.Point(target.getX(), target.getY());
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
            blockers.add(new HudPlacementResolver.Rect(element.getX(), element.getY(), w, h));
        }
        return blockers;
    }

    private void applyElementScreenPosition(com.eymistaken.simplecps.api.IHudElement el, int targetX, int targetY) {
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
        java.util.Set<com.eymistaken.simplecps.api.IHudElement> targets = new java.util.HashSet<>(selectedElements);
        java.util.Map<com.eymistaken.simplecps.api.IHudElement, java.awt.Point> targetPositions = new java.util.HashMap<>();
        for (com.eymistaken.simplecps.api.IHudElement el : targets) {
            targetPositions.put(el, new java.awt.Point(el.getX() + dx, el.getY() + dy));
        }

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

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.input();
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
