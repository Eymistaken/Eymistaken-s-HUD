package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.HudModuleManager;
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
    
    // For slider dragging
    private com.eymistaken.simplecps.api.SliderSetting draggingSlider = null;

    public HudEditorScreen(Screen parent) {
        super(Component.nullToEmpty("HUD Editor"));
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
                
                context.text(this.font, name, p.x, p.y - 10, 0xFFFFFFFF, true);
            }
        }
        
        context.centeredText(this.font, Component.nullToEmpty("Drag & Drop Editor - Scroll to Scale"), this.width / 2, 10, 0xFFFFFFFF);
        context.centeredText(this.font, Component.nullToEmpty("Right Click to Open Settings"), this.width / 2, 22, 0xFFAAAAAA);

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
            
            context.centeredText(this.font, textEditTarget.label + ": " + textEditBuffer + "_", cx, cy - 4, 0xFFFFFFFF);
        }
    }
    
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
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
    public boolean mouseReleased(MouseButtonEvent click) {
        int button = click.button();
        if (button == 0) {
            if (draggingModule != null) {
                draggingModule = null;
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
    public void onClose() {
        SimpleCPSConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private int getMenuHeight(com.eymistaken.simplecps.api.HudModule module) {
        int height = 20;
        for (com.eymistaken.simplecps.api.HudModuleSetting setting : module.getContextMenuSettings()) {
            if (setting instanceof com.eymistaken.simplecps.api.SliderSetting) height += 24;
            else height += 20;
        }
        return height;
    }

    private void handleMenuClick(int index, double mouseX, double mouseY) {
        if (index == -1) {
            contextMenuTarget.resetToDefaults();
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
                this.minecraft.setScreen(new ColorPickerScreen(
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

    @Override
    public boolean charTyped(CharacterEvent charInput) {
        char chr = (char) charInput.codepoint();
        if (textEditTarget != null) {
            textEditBuffer += chr;
            return true;
        }
        return super.charTyped(charInput);
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
        return super.keyPressed(keyInput);
    }
}
