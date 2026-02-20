package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.HudModuleManager;
import com.eymistaken.simplecps.SimpleCPSClient;
import com.eymistaken.simplecps.SimpleCPSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.awt.Point;

public class HudEditorScreen extends Screen {

    private final Screen parent;
    private String draggingModule = null;
    
    // Local click offset within the module (0,0 is top-left of module)
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    // Polling State
    private boolean wasLeftDown = false;
    private boolean wasRightDown = false;

    public HudEditorScreen(Screen parent) {
        super(Text.of("HUD Editor"));
        this.parent = parent;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (var entry : HudModuleManager.getInstance().getModuleBounds().entrySet()) {
            String name = entry.getKey();
            HudModuleManager.ModuleBounds p = entry.getValue();
            
            if (mouseX >= p.x && mouseX <= p.x + p.w &&
                mouseY >= p.y && mouseY <= p.y + p.h) {
                
                // Scroll to scale (range 50-300)
                int scaleChange = (verticalAmount > 0) ? 5 : -5;
                SimpleCPSConfig config = SimpleCPSConfig.instance;
                
                switch (name) {
                    case "CPS" -> config.scale = clamp(config.scale + scaleChange);
                    // Ping has no scale
                    case "FPS" -> config.fpsScale = clamp(config.fpsScale + scaleChange);
                    case "Combo" -> config.comboScale = clamp(config.comboScale + scaleChange);
                    case "Keystrokes" -> config.keystrokesScale = clamp(config.keystrokesScale + scaleChange);
                    case "Reach" -> config.reachScale = clamp(config.reachScale + scaleChange);
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    private int clamp(int val) {
        return Math.max(50, Math.min(300, val));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Gradient Background
        context.fillGradient(0, 0, this.width, this.height, 0xC0000000, 0xD0000000); 

        // ============================================
        // Input Handling (GLFW Polling)
        // ============================================
        long windowHandle = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
        
        boolean isLeftDown = GLFW.glfwGetMouseButton(windowHandle, 0) == GLFW.GLFW_PRESS;
        boolean isRightDown = GLFW.glfwGetMouseButton(windowHandle, 1) == GLFW.GLFW_PRESS;

        // LEFT CLICK (Start Drag)
        if (isLeftDown && !wasLeftDown) {
            for (var entry : HudModuleManager.getInstance().getModuleBounds().entrySet()) {
                String name = entry.getKey();
                HudModuleManager.ModuleBounds p = entry.getValue();
                
                if (mouseX >= p.x && mouseX <= p.x + p.w &&
                    mouseY >= p.y && mouseY <= p.y + p.h) {
                    
                    draggingModule = name;
                    dragOffsetX = mouseX - p.x;
                    dragOffsetY = mouseY - p.y;
                    break; 
                }
            }
        } 
        
        // RIGHT CLICK (Reset to Defaults)
        if (isRightDown && !wasRightDown) {
             for (var entry : HudModuleManager.getInstance().getModuleBounds().entrySet()) {
                String name = entry.getKey();
                HudModuleManager.ModuleBounds p = entry.getValue();
                
                if (mouseX >= p.x && mouseX <= p.x + p.w &&
                    mouseY >= p.y && mouseY <= p.y + p.h) {
                    
                    SimpleCPSConfig config = SimpleCPSConfig.instance;
                    switch (name) {
                        case "CPS" -> { 
                             config.position = SimpleCPSConfig.Position.TOP_LEFT; 
                             config.xOffset = 0; config.yOffset = 0; 
                        }
                        case "Ping" -> { 
                             config.pingPosition = SimpleCPSConfig.Position.TOP_LEFT; 
                             config.pingXOffset = 0; config.pingYOffset = 0; 
                        }
                        case "FPS" -> { 
                             config.fpsPosition = SimpleCPSConfig.Position.TOP_LEFT; 
                             config.fpsXOffset = 0; config.fpsYOffset = 0; 
                        }
                        case "Combo" -> { 
                             config.comboPosition = SimpleCPSConfig.Position.TOP_LEFT; 
                             config.comboXOffset = 0; config.comboYOffset = 0; 
                        }
                        case "Keystrokes" -> { 
                             config.keystrokesPosition = SimpleCPSConfig.Position.TOP_LEFT; 
                             config.keystrokesXOffset = 0; config.keystrokesYOffset = 0; 
                        }
                        case "Reach" -> { 
                             config.reachPosition = SimpleCPSConfig.Position.TOP_LEFT; 
                             config.reachXOffset = 0; config.reachYOffset = 0; 
                        }
                        case "ArmorHud" -> {
                             config.armorPosition = SimpleCPSConfig.Position.BOTTOM_LEFT;
                             config.armorXOffset = 0; config.armorYOffset = 0;
                        }
                    }
                    break;
                }
             }
        }

        // LEFT RELEASE (Stop Drag)
        if (!isLeftDown && wasLeftDown) {
            draggingModule = null;
        } 
        
        // DRAGGING (Update Position)
        if (isLeftDown && draggingModule != null) {
            // 1. Calculate TARGET Screen Position
            int targetX = mouseX - dragOffsetX;
            int targetY = mouseY - dragOffsetY;
            
            // 2. Determine Smart Anchor based on Center of module
            HudModuleManager.ModuleBounds p = HudModuleManager.getInstance().getModuleBounds().get(draggingModule);
            int w = (p != null) ? p.w : 50;
            int h = (p != null) ? p.h : 20;
            
            int centerX = targetX + w / 2;
            int centerY = targetY + h / 2;
            
            SimpleCPSConfig.Position newAnchor = determineAnchor(centerX, centerY);
            
            // 3. Estimate Base Position for that Anchor (to calc pure offset)
            Point base = getEstimatedBasePos(newAnchor, w, h);
            
            // 4. Calculate Relative Offset
            int newOffsetX = targetX - base.x;
            int newOffsetY = targetY - base.y;
            
            // 5. Apply to Config
            SimpleCPSConfig config = SimpleCPSConfig.instance;
            
            switch (draggingModule) {
                case "CPS" -> { 
                    config.position = newAnchor; 
                    config.xOffset = newOffsetX; config.yOffset = newOffsetY; 
                }
                case "Ping" -> { 
                    config.pingPosition = newAnchor; 
                    config.pingXOffset = newOffsetX; config.pingYOffset = newOffsetY; 
                }
                case "FPS" -> { 
                    config.fpsPosition = newAnchor; 
                    config.fpsXOffset = newOffsetX; config.fpsYOffset = newOffsetY; 
                }
                case "Combo" -> { 
                    config.comboPosition = newAnchor; 
                    config.comboXOffset = newOffsetX; config.comboYOffset = newOffsetY; 
                }
                case "Keystrokes" -> { 
                    config.keystrokesPosition = newAnchor; 
                    config.keystrokesXOffset = newOffsetX; config.keystrokesYOffset = newOffsetY; 
                }
                case "Reach" -> { 
                    config.reachPosition = newAnchor; 
                    config.reachXOffset = newOffsetX; config.reachYOffset = newOffsetY; 
                }
                case "ArmorHud" -> {
                    config.armorPosition = newAnchor;
                    config.armorXOffset = newOffsetX; config.armorYOffset = newOffsetY;
                }
            }
        }
        
        // Update State
        wasLeftDown = isLeftDown;
        wasRightDown = isRightDown;
        // ============================================

        // Center Lines
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int lineColor = 0x55FFFFFF;
        context.fill(centerX, 0, centerX + 1, this.height, lineColor);
        context.fill(0, centerY, this.width, centerY + 1, lineColor);

        // Render Live Preview
        SimpleCPSClient.onHudRender(context, delta);

        // Draw Selection Borders
        for (var entry : HudModuleManager.getInstance().getModuleBounds().entrySet()) {
            String name = entry.getKey();
            HudModuleManager.ModuleBounds p = entry.getValue();
            
            boolean isHovered = mouseX >= p.x && mouseX <= p.x + p.w && 
                                mouseY >= p.y && mouseY <= p.y + p.h;
            
            if (isHovered || name.equals(draggingModule)) {
                int borderColor = 0xFFFFFFFF;
                context.fill(p.x - 1, p.y - 1, p.x + p.w + 1, p.y, borderColor); // Top
                context.fill(p.x - 1, p.y + p.h, p.x + p.w + 1, p.y + p.h + 1, borderColor); // Bottom
                context.fill(p.x - 1, p.y, p.x, p.y + p.h, borderColor); // Left
                context.fill(p.x + p.w, p.y, p.x + p.w + 1, p.y + p.h, borderColor); // Right
                
                context.drawText(this.textRenderer, name, p.x, p.y - 10, 0xFFFFFFFF, true);
            }
        }
        
        context.drawCenteredTextWithShadow(this.textRenderer, Text.of("Drag & Drop Editor - Scroll to Scale"), this.width / 2, 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.of("Right Click to Reset"), this.width / 2, 22, 0xFFAAAAAA);
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
    public void close() {
        SimpleCPSConfig.save();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
