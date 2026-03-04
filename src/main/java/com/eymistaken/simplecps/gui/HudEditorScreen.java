package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.HudModuleManager;
import com.eymistaken.simplecps.SimpleCPSClient;
import com.eymistaken.simplecps.SimpleCPSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.awt.Point;
import net.minecraft.client.gui.Click;

public class HudEditorScreen extends Screen {

    private final Screen parent;
    private String draggingModule = null;
    
    // Local click offset within the module (0,0 is top-left of module)
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

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
                com.eymistaken.simplecps.api.HudModule module = HudModuleManager.getInstance().getModuleByName(name);
                if (module != null) {
                    module.setScale(clamp(module.getScale() + scaleChange));
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
    
    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        double mouseX = click.comp_4798();
        double mouseY = click.comp_4799();
        int button = click.button();
        
        if (button == 0) { // Left click
            for (var entry : HudModuleManager.getInstance().getModuleBounds().entrySet()) {
                String name = entry.getKey();
                HudModuleManager.ModuleBounds p = entry.getValue();
                
                if (mouseX >= p.x && mouseX <= p.x + p.w &&
                    mouseY >= p.y && mouseY <= p.y + p.h) {
                    
                    draggingModule = name;
                    dragOffsetX = (int)mouseX - p.x;
                    dragOffsetY = (int)mouseY - p.y;
                    return true;
                }
            }
        } else if (button == 1) { // Right click
             for (var entry : HudModuleManager.getInstance().getModuleBounds().entrySet()) {
                String name = entry.getKey();
                HudModuleManager.ModuleBounds p = entry.getValue();
                
                if (mouseX >= p.x && mouseX <= p.x + p.w &&
                    mouseY >= p.y && mouseY <= p.y + p.h) {
                    
                    com.eymistaken.simplecps.api.HudModule module = HudModuleManager.getInstance().getModuleByName(name);
                    if (module != null) {
                        module.resetToDefaults();
                    }
                    return true;
                }
             }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        int button = click.button();
        if (button == 0 && draggingModule != null) {
            draggingModule = null;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        double mouseX = click.comp_4798();
        double mouseY = click.comp_4799();
        int button = click.button();
        
        if (button == 0 && draggingModule != null) {
            // 1. Calculate TARGET Screen Position
            int targetX = (int)mouseX - dragOffsetX;
            int targetY = (int)mouseY - dragOffsetY;
            
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
            com.eymistaken.simplecps.api.HudModule module = HudModuleManager.getInstance().getModuleByName(draggingModule);
            if (module != null) {
                module.setPositionType(newAnchor);
                module.setXOffset(newOffsetX);
                module.setYOffset(newOffsetY);
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
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
