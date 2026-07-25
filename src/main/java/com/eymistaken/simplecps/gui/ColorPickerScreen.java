package com.eymistaken.simplecps.gui;

import org.lwjgl.glfw.GLFW;
import java.awt.Color;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ColorPickerScreen extends Screen {

    private final Screen parent;
    private final IntConsumer onSave;

    private float hue = 0.0f;
    private float saturation = 1.0f;
    private float value = 1.0f;

    private enum DragTarget { NONE, SV, HUE }
    private DragTarget dragTarget = DragTarget.NONE;

    private boolean hexBoxActive = false;
    private String hexInput = "";
    private long copiedTime = 0;
    private boolean isAllSelected = false;

    public ColorPickerScreen(int initialColor, IntConsumer onSave, Screen parent) {
        super(Component.nullToEmpty("Color Picker"));
        this.onSave = onSave;
        this.parent = parent;

        int rgb = initialColor & 0xFFFFFF;
        Color col = new Color(rgb);
        float[] hsb = Color.RGBtoHSB(col.getRed(), col.getGreen(), col.getBlue(), null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.value = hsb[2];
    }

    private int getCurrentColor() {
        return 0xFF000000 | (Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xC0000000, 0xD0000000); 

        // Total Height Calculation
        // SV Box: 100
        // Gap: 10
        // Hue Slider: 10
        // Gap: 10
        // Preview Box: 20
        // Gap: 10
        // Save Button: 20
        // Total = 100+10+10+10+20+10+20 = 180
        int totalHeight = 180;

        int cx = this.width / 2 - 100;
        int cy = (this.height - totalHeight) / 2;

        // Draw SV Box
        for (int i = 0; i < 200; i++) {
            float s = i / 200f;
            int c = Color.HSBtoRGB(hue, s, 1.0f);
            context.fill(cx + i, cy, cx + i + 1, cy + 100, c | 0xFF000000);
        }
        context.fillGradient(cx, cy, cx + 200, cy + 100, 0x00000000, 0xFF000000);

        // SV Cursor
        int cursorX = cx + (int)(saturation * 200);
        int cursorY = cy + (int)((1.0f - value) * 100);
        context.fill(cursorX - 2, cursorY - 2, cursorX + 2, cursorY + 2, 0xFFFFFFFF);
        context.fill(cursorX - 1, cursorY - 1, cursorX + 1, cursorY + 1, 0xFF000000);

        // Draw Hue Slider
        for (int i = 0; i < 200; i++) {
            float h = i / 200f;
            int c = Color.HSBtoRGB(h, 1.0f, 1.0f);
            context.fill(cx + i, cy + 110, cx + i + 1, cy + 120, c | 0xFF000000);
        }
        
        // Hue Cursor
        int hCursorX = cx + (int)(hue * 200);
        context.fill(hCursorX - 2, cy + 109, hCursorX + 2, cy + 121, 0xFFFFFFFF);
        context.fill(hCursorX - 1, cy + 110, hCursorX + 1, cy + 120, 0xFF000000);

        // Preview box
        for (int i = 0; i < 200; i += 10) {
            for (int j = 0; j < 20; j += 10) {
                int bg = ((i / 10 + j / 10) % 2 == 0) ? 0xFFFFFFFF : 0xFFCCCCCC;
                context.fill(cx + i, cy + 130 + j, Math.min(cx + i + 10, cx + 200), cy + 130 + j + 10, bg);
            }
        }
        context.fill(cx, cy + 130, cx + 200, cy + 150, getCurrentColor());
        drawBorder(context, cx, cy + 130, 200, 20, 0xFFFFFFFF);

        // Save Button
        boolean saveHovered = mouseX >= cx && mouseX <= cx + 200 && mouseY >= cy + 160 && mouseY <= cy + 180;
        context.fill(cx, cy + 160, cx + 200, cy + 180, saveHovered ? 0xFF444444 : 0xFF222222);
        drawBorder(context, cx, cy + 160, 200, 20, 0xFFFFFFFF);
        
        context.text(this.font, com.eymistaken.simplecps.util.EymHudFonts.text("Save & Close"), cx + 65, cy + 166, 0xFFFFFFFF, true);

        // Phase 1 - Hex Box
        int hexBoxX = cx;
        int hexBoxY = cy - 20;
        int hexBoxW = 60;
        int hexBoxH = 15;
        
        String displayHex = hexBoxActive ? hexInput + (((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "") : String.format("#%06X", getCurrentColor() & 0xFFFFFF);
        if (isAllSelected && hexBoxActive) {
            displayHex = hexInput;
        }
        
        context.fill(hexBoxX, hexBoxY, hexBoxX + hexBoxW, hexBoxY + hexBoxH, 0xFF000000);
        drawBorder(context, hexBoxX, hexBoxY, hexBoxW, hexBoxH, hexBoxActive ? 0xFF00FF00 : 0xFFFFFFFF);
        
        if (isAllSelected && hexBoxActive) {
            int textW = this.font.width(com.eymistaken.simplecps.util.EymHudFonts.text(hexInput));
            context.fill(hexBoxX + 4, hexBoxY + 3, hexBoxX + 4 + textW, hexBoxY + 13, 0x804444FF);
        }
        
        context.text(this.font, com.eymistaken.simplecps.util.EymHudFonts.text(displayHex), hexBoxX + 4, hexBoxY + 4, 0xFFFFFFFF, true);

        // Phase 2 - Copy Button
        int copyBtnX = hexBoxX + hexBoxW + 5;
        int copyBtnY = hexBoxY;
        int copyBtnW = 40;
        int copyBtnH = 15;
        
        boolean copyHovered = mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + copyBtnH;
        context.fill(copyBtnX, copyBtnY, copyBtnX + copyBtnW, copyBtnY + copyBtnH, copyHovered ? 0xFF444444 : 0xFF222222);
        drawBorder(context, copyBtnX, copyBtnY, copyBtnW, copyBtnH, 0xFFFFFFFF);
        
        boolean isCopied = System.currentTimeMillis() - copiedTime < 2000;
        String copyText = isCopied ? "Copied!" : "Copy";
        context.text(this.font, com.eymistaken.simplecps.util.EymHudFonts.text(copyText), copyBtnX + 4, copyBtnY + 4, isCopied ? 0xFF55FF55 : 0xFFFFFFFF, true);

        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void drawBorder(GuiGraphicsExtractor context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (button == 0) {
            int totalHeight = 180;
            int cx = this.width / 2 - 100;
            int cy = (this.height - totalHeight) / 2;

            int hexBoxX = cx;
            int hexBoxY = cy - 20;
            int hexBoxW = 60;
            int hexBoxH = 15;

            int copyBtnX = hexBoxX + hexBoxW + 5;
            int copyBtnY = hexBoxY;
            int copyBtnW = 40;
            int copyBtnH = 15;

            if (mouseX >= hexBoxX && mouseX <= hexBoxX + hexBoxW && mouseY >= hexBoxY && mouseY <= hexBoxY + hexBoxH) {
                hexBoxActive = true;
                isAllSelected = false;
                hexInput = String.format("#%06X", getCurrentColor() & 0xFFFFFF);
                return true;
            } else {
                hexBoxActive = false;
                isAllSelected = false;
            }

            if (mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW && mouseY >= copyBtnY && mouseY <= copyBtnY + copyBtnH) {
                if (this.minecraft != null) {
                    this.minecraft.keyboardHandler.setClipboard(String.format("#%06X", getCurrentColor() & 0xFFFFFF));
                    copiedTime = System.currentTimeMillis();
                }
                return true;
            }

            if (mouseX >= cx && mouseX <= cx + 200 && mouseY >= cy && mouseY <= cy + 100) {
                dragTarget = DragTarget.SV;
                updateSV(mouseX, mouseY, cx, cy);
                return true;
            }
            if (mouseX >= cx && mouseX <= cx + 200 && mouseY >= cy + 110 && mouseY <= cy + 120) {
                dragTarget = DragTarget.HUE;
                updateHue(mouseX, cx);
                return true;
            }
            if (mouseX >= cx && mouseX <= cx + 200 && mouseY >= cy + 160 && mouseY <= cy + 180) {
                saveAndClose();
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        
        if (button == 0 && dragTarget != DragTarget.NONE) {
            int totalHeight = 180;
            int cx = this.width / 2 - 100;
            int cy = (this.height - totalHeight) / 2;
            
            if (dragTarget == DragTarget.SV) {
                updateSV(mouseX, mouseY, cx, cy);
            } else if (dragTarget == DragTarget.HUE) {
                updateHue(mouseX, cx);
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        if (dragTarget != DragTarget.NONE) {
            dragTarget = DragTarget.NONE;
            return true;
        }
        return super.mouseReleased(click);
    }

    private void updateSV(double mouseX, double mouseY, int cx, int cy) {
        float s = (float) ((mouseX - cx) / 200.0);
        float v = 1.0f - (float) ((mouseY - cy) / 100.0);
        this.saturation = Math.max(0.0f, Math.min(1.0f, s));
        this.value = Math.max(0.0f, Math.min(1.0f, v));
    }

    private void updateHue(double mouseX, int cx) {
        float h = (float) ((mouseX - cx) / 200.0);
        this.hue = Math.max(0.0f, Math.min(1.0f, h));
    }

    @Override
    public boolean charTyped(CharacterEvent charInput) {
        char chr = (char) charInput.codepoint();
        if (hexBoxActive) {
            if (isAllSelected || hexInput.length() < 9) {
                if ((chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F') || chr == '#') {
                    if (chr == '#' && hexInput.contains("#") && !isAllSelected) return super.charTyped(charInput);
                    
                    if (isAllSelected) {
                        hexInput = "";
                        isAllSelected = false;
                    }
                    
                    if (hexInput.isEmpty() && chr != '#') hexInput = "#" + chr;
                    else hexInput += chr;
                    tryApplyHex();
                    return true;
                }
            }
        }
        return super.charTyped(charInput);
    }

    private void tryApplyHex() {
        String hex = hexInput.startsWith("#") ? hexInput.substring(1) : hexInput;
        if (hex.length() == 6) {
            try {
                int rgb = Integer.parseInt(hex, 16);
                Color col = new Color(rgb);
                float[] hsb = Color.RGBtoHSB(col.getRed(), col.getGreen(), col.getBlue(), null);
                this.hue = hsb[0];
                this.saturation = hsb[1];
                this.value = hsb[2];
            } catch (NumberFormatException ignored) {}
        }
    }
    private boolean isCtrlDown() {
        return GLFW.glfwGetKey(this.minecraft.getWindow().handle(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
               GLFW.glfwGetKey(this.minecraft.getWindow().handle(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        int keyCode = keyInput.input();
        
        if (hexBoxActive) {
            if (isCtrlDown()) {
                if (keyCode == GLFW.GLFW_KEY_A) {
                    isAllSelected = true;
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_V) {
                    if (this.minecraft != null) {
                        String clipboard = this.minecraft.keyboardHandler.getClipboard();
                        if (clipboard != null) {
                            clipboard = clipboard.trim();
                            if (clipboard.matches("^#?[0-9a-fA-F]{1,8}$")) {
                                hexInput = clipboard.startsWith("#") ? clipboard : "#" + clipboard;
                                isAllSelected = false;
                                tryApplyHex();
                            }
                        }
                    }
                    return true;
                }
            }

            if (isAllSelected && (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_ESCAPE)) {
                isAllSelected = false;
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!hexInput.isEmpty()) {
                    hexInput = hexInput.substring(0, hexInput.length() - 1);
                    tryApplyHex();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                hexBoxActive = false;
                isAllSelected = false;
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(parent);
            }
            return true;
        }
        return super.keyPressed(keyInput);
    }
    
    private void saveAndClose() {
        onSave.accept(getCurrentColor() & 0x00FFFFFF);
        com.eymistaken.simplecps.SimpleCPSConfig.save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
