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
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;

public class HudEditorScreen extends Screen {

    private final Screen parent;
    private String draggingModule = null;
    
    // Local click offset within the module (0,0 is top-left of module)
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private boolean contextMenuOpen = false;
    private int contextMenuX, contextMenuY;
    private com.eymistaken.simplecps.api.HudModule contextMenuTarget = null;
    
    // For text editing (Stage 4 prep)
    private com.eymistaken.simplecps.api.TextSetting textEditTarget = null;
    private String textEditBuffer = "";

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
        context.drawCenteredTextWithShadow(this.textRenderer, Text.of("Right Click to Open Settings"), this.width / 2, 22, 0xFFAAAAAA);

        if (contextMenuOpen && contextMenuTarget != null) {
            renderContextMenu(context, mouseX, mouseY);
        }
        
        if (textEditTarget != null) {
            int cx = this.width / 2;
            int cy = this.height - 40;
            context.fill(cx - 100, cy - 10, cx + 100, cy + 10, 0xAA000000);
            context.fill(cx - 101, cy - 11, cx + 101, cy - 10, 0xFFFFFFFF);
            context.fill(cx - 101, cy + 10, cx + 101, cy + 11, 0xFFFFFFFF);
            context.fill(cx - 101, cy - 10, cx - 100, cy + 10, 0xFFFFFFFF);
            context.fill(cx + 100, cy - 10, cx + 101, cy + 10, 0xFFFFFFFF);
            
            context.drawCenteredTextWithShadow(this.textRenderer, textEditTarget.label + ": " + textEditBuffer + "_", cx, cy - 4, 0xFFFFFFFF);
        }
    }
    
    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        double mouseX = click.comp_4798();
        double mouseY = click.comp_4799();
        int button = click.button();
        
        if (contextMenuOpen && contextMenuTarget != null) {
            int menuW = 160;
            int menuH = getMenuHeight(contextMenuTarget);
            if (mouseX >= contextMenuX && mouseX <= contextMenuX + menuW &&
                mouseY >= contextMenuY && mouseY <= contextMenuY + menuH) {
                int index = (int)((mouseY - contextMenuY) / 20);
                handleMenuClick(index);
                return true;
            } else {
                contextMenuOpen = false;
            }
        }
        
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
                        contextMenuTarget = module;
                        contextMenuOpen = true;
                        contextMenuX = (int)mouseX;
                        
                        int h = getMenuHeight(module);
                        int y = (int)mouseY;
                        
                        if (y + h > this.height) y = y - h;
                        if (y < 0) y = 0;
                        if (y + h > this.height) y = this.height - h;
                        
                        contextMenuY = y;
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

    private int getMenuHeight(com.eymistaken.simplecps.api.HudModule module) {
        return 20 + module.getContextMenuSettings().size() * 20;
    }

    private void handleMenuClick(int index) {
        if (index == 0) {
            contextMenuTarget.resetToDefaults();
            SimpleCPSConfig.save();
            contextMenuOpen = false;
            return;
        }
        
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = contextMenuTarget.getContextMenuSettings();
        if (index - 1 < settings.size()) {
            com.eymistaken.simplecps.api.HudModuleSetting setting = settings.get(index - 1);
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
                this.client.setScreen(new ColorPickerScreen(
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
            }
        }
    }

    private void renderContextMenu(DrawContext context, int mouseX, int mouseY) {
        int w = 160;
        int h = getMenuHeight(contextMenuTarget);
        int x = contextMenuX;
        int y = contextMenuY;

        context.fill(x, y, x + w, y + h, 0xFF222222);
        context.fill(x - 1, y - 1, x + w + 1, y, 0xFFFFFFFF); // Top
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFFFFFFFF); // Bottom
        context.fill(x - 1, y, x, y + h, 0xFFFFFFFF); // Left
        context.fill(x + w, y, x + w + 1, y + h, 0xFFFFFFFF); // Right
        
        boolean hoverReset = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + 20;
        if (hoverReset) context.fill(x, y, x + w, y + 20, 0xFF444444);
        context.drawText(this.textRenderer, "Reset Position", x + 5, y + 6, 0xFFFFFFFF, true);
        
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = contextMenuTarget.getContextMenuSettings();
        for (int i = 0; i < settings.size(); i++) {
            int itemY = y + 20 + i * 20;
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= itemY && mouseY < itemY + 20;
            if (hovered) context.fill(x, itemY, x + w, itemY + 20, 0xFF444444);
            
            com.eymistaken.simplecps.api.HudModuleSetting setting = settings.get(i);
            context.drawText(this.textRenderer, setting.label, x + 5, itemY + 6, 0xFFFFFFFF, true);
            
            if (setting instanceof com.eymistaken.simplecps.api.BooleanSetting boolSetting) {
                String val = boolSetting.getter.get() ? "[ON]" : "[OFF]";
                int valW = this.textRenderer.getWidth(val);
                int color = boolSetting.getter.get() ? 0xFF55FF55 : 0xFFFF5555;
                context.drawText(this.textRenderer, val, x + w - valW - 5, itemY + 6, color, true);
            } else if (setting instanceof com.eymistaken.simplecps.api.CycleSetting cycleSetting) {
                String val = cycleSetting.options.get(cycleSetting.getter.get());
                int valW = this.textRenderer.getWidth(val);
                context.drawText(this.textRenderer, val, x + w - valW - 5, itemY + 6, 0xFFFFAA00, true);
            } else if (setting instanceof com.eymistaken.simplecps.api.ColorSetting colorSetting) {
                int c = colorSetting.getter.get() | 0xFF000000;
                context.fill(x + w - 15, itemY + 5, x + w - 5, itemY + 15, c);
                // border
                context.fill(x + w - 16, itemY + 4, x + w - 15, itemY + 16, 0xFFFFFFFF);
                context.fill(x + w - 4, itemY + 4, x + w - 5, itemY + 16, 0xFFFFFFFF);
                context.fill(x + w - 15, itemY + 4, x + w - 5, itemY + 5, 0xFFFFFFFF);
                context.fill(x + w - 15, itemY + 15, x + w - 5, itemY + 16, 0xFFFFFFFF);
            } else if (setting instanceof com.eymistaken.simplecps.api.TextSetting textSetting) {
                String val = textSetting.getter.get();
                if (val.length() > 5) val = val.substring(0, 5) + "...";
                int valW = this.textRenderer.getWidth(val);
                context.drawText(this.textRenderer, val, x + w - valW - 5, itemY + 6, 0xFFAAAAAA, true);
            }
        }
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        char chr = (char) charInput.comp_4793();
        if (textEditTarget != null) {
            textEditBuffer += chr;
            return true;
        }
        return super.charTyped(charInput);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.getKeycode();
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
        return super.keyPressed(keyInput);
    }
}
