package com.eymistaken.simplecps.gui;

import com.eymistaken.simplecps.SimpleCPSConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ModuleSettingsScreen extends Screen {
    private final Screen parent;
    private final String category;
    private final SimpleCPSConfig config;
    
    // Manual Scrolling
    private double scrollY = 0;
    private int contentHeight = 0;
    private final int topMargin = 40;
    private final int bottomMargin = 40;
    private final int itemHeight = 25;
    
    private final List<ClickableWidget> optionWidgets = new ArrayList<>();
    private final List<Text> optionLabels = new ArrayList<>(); // For TextField labels

    public ModuleSettingsScreen(Screen parent, String category) {
        super(Text.of(category + " Settings"));
        this.parent = parent;
        this.category = category;
        this.config = SimpleCPSConfig.instance;
    }

    @Override
    protected void init() {
        this.optionWidgets.clear();
        this.optionLabels.clear();
        this.scrollY = 0;

        // Footer Buttons (Fixed)
        this.addDrawableChild(ButtonWidget.builder(Text.of("Reset to Defaults"), button -> {
            resetToDefaults();
            this.clearChildren();
            this.init(); 
        }).dimensions(this.width / 2 - 155, this.height - 29, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.of("Done"), button -> this.close())
                .dimensions(this.width / 2 + 5, this.height - 29, 150, 20).build());

        // Populate widgets list (not adding to screen yet)
        switch (category) {
            case "CPS" -> initCps();
            case "PING" -> initPing();
            case "FPS" -> initFps();
            case "KEYSTROKES" -> initKeystrokes();
            case "COMBO" -> initCombo();
        }
        
        // Calculate content height
        this.contentHeight = this.optionWidgets.size() * itemHeight;
        
        // Add option widgets to screen so they receive events
        for (ClickableWidget w : optionWidgets) {
            this.addDrawableChild(w);
        }
        
        updateWidgetPositions();
    }
    
    // Override mouseScrolled to handle scrolling
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (canScroll()) {
            this.scrollY += verticalAmount * 20; // Scroll speed
            clampScroll();
            updateWidgetPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    private boolean canScroll() {
        return contentHeight > (this.height - topMargin - bottomMargin);
    }
    
    private void clampScroll() {
        if (!canScroll()) {
            scrollY = 0;
            return;
        }
        double minScroll = -(contentHeight - (this.height - topMargin - bottomMargin));
        double maxScroll = 0;
        
        // Warning: Usually scroll is positive (0 to max). 
        // Here I use negative offset (content moves up). 
        // 0 = top. -100 = content moved up 100px.
        if (scrollY > maxScroll) scrollY = maxScroll;
        if (scrollY < minScroll) scrollY = minScroll;
    }

    private void updateWidgetPositions() {
        int centerX = this.width / 2;
        int y = topMargin + (int)scrollY;
        
        for (ClickableWidget w : optionWidgets) {
            w.setX(centerX - 75); // Centered width 150
            w.setY(y);
            
            // Allow clicking only if visible in view area?
            // Vanilla handles clipping visually, but events might trigger outside?
            // We should disable visibility if outside.
            boolean visible = y >= topMargin - itemHeight && y <= this.height - bottomMargin;
            w.visible = visible; // Use visible flag to prevent drawing/clicking
            
            y += itemHeight;
        }
    }
    
    // --- Render ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background
        context.fill(0, 0, this.width, this.height, 0x85000000);
        
        // Draw Scrollbar?
        if (canScroll()) {
             int barHeight = this.height - topMargin - bottomMargin;
             int contentH = contentHeight;
             int viewH = barHeight;
             float ratio = (float)viewH / contentH;
             int thumbHeight = (int)(viewH * ratio);
             if (thumbHeight < 32) thumbHeight = 32;
             
             int barX = this.width / 2 + 85; 
             // Map scrollY (0 to -max) to ThumbY (top to bottom)
             double scrollRange = contentHeight - viewH;
             double scrollPercent = Math.abs(scrollY) / scrollRange;
             int thumbY = topMargin + (int)(scrollPercent * (viewH - thumbHeight));
             
             context.fill(barX, topMargin, barX + 6, topMargin + viewH, 0x80000000); // Track
             context.fill(barX, thumbY, barX + 6, thumbY + thumbHeight, 0xFFC0C0C0); // Thumb
        }
        
        // Draw Labels for TextFields (Manual rendering because they are not widgets)
        // We need to match the positions of their corresponding widgets.
        // Assuming label is stored in parallel? Or stored in the widget?
        // TextFieldWidget typically contains text.
        // But the "Label" (e.g. "Text Color") is external?
        // In my previous impl, I had LabelWidgetEntry.
        // Here, I will just draw labels relative to widgets if needed.
        // Or simpler: Use TextFields that HAVE labels?
        // I will iterate optionWidgets. If it's a TextField, render its label?
        // No, I need to know WHICH label.
        
        // Fix: Store label in a map or list parallel?
        // Simpler: optionLabels list.
        
        updateWidgetPositions(); // Ensure strictly sync before render
        super.render(context, mouseX, mouseY, delta);
        
        // Render Labels
        for (int i = 0; i < optionWidgets.size(); i++) {
             ClickableWidget w = optionWidgets.get(i);
             if (w.visible && i < optionLabels.size()) {
                 Text label = optionLabels.get(i);
                 if (label != null) {
                     // Draw label to the left of the widget
                     int x = w.getX() - 10 - textRenderer.getWidth(label);
                     int y = w.getY() + (w.getHeight() - 8) / 2;
                     context.drawTextWithShadow(textRenderer, label, x, y, 0xFFFFFF);
                 }
             }
        }
        
        // Header
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
    }
    
    // --- Initializers (Same as before but adding to optionWidgets) ---
    // Note: I will only implement one helper to show pattern, reusing logic
    
    private void addBool(String label, String tooltip, boolean current, Consumer<Boolean> saver) {
        optionWidgets.add(ButtonWidget.builder(Text.of(label + ": " + (current ? "ON" : "OFF")), button -> {
            boolean newVal = !button.getMessage().getString().endsWith("ON");
            saver.accept(newVal);
            button.setMessage(Text.of(label + ": " + (newVal ? "ON" : "OFF")));
        })
        .tooltip(Tooltip.of(Text.of(tooltip)))
        .dimensions(0, 0, 150, 20)
        .build());
        optionLabels.add(null); // No external label needed
    }
    
    private <E extends Enum<E>> void addEnum(String label, String tooltip, E current, Class<E> enumClass, Consumer<Enum<?>> saver) {
        optionWidgets.add(ButtonWidget.builder(Text.of(label + ": " + current), button -> {
            E[] values = enumClass.getEnumConstants();
            int index = 0;
            for (int i = 0; i < values.length; i++) {
                if (button.getMessage().getString().endsWith(values[i].toString())) {
                    index = i;
                    break;
                }
            }
            E next = values[(index + 1) % values.length];
            saver.accept(next);
            button.setMessage(Text.of(label + ": " + next));
        })
        .tooltip(Tooltip.of(Text.of(tooltip)))
        .dimensions(0, 0, 150, 20)
        .build());
        optionLabels.add(null);
    }

    private void addInt(String label, String tooltip, int min, int max, int current, Consumer<Integer> saver) {
        SliderWidget slider = new SliderWidget(0, 0, 150, 20, Text.of(label + ": " + current), (current - min) / (double)(max - min)) {
            @Override
            protected void updateMessage() {
                setMessage(Text.of(label + ": " + (min + (int)(this.value * (max - min)))));
            }
            @Override
            protected void applyValue() {
                saver.accept(min + (int)(this.value * (max - min)));
            }
        };
        slider.setTooltip(Tooltip.of(Text.of(tooltip))); 
        optionWidgets.add(slider);
        optionLabels.add(null);
    }

    private void addHex(String label, String tooltip, int current, Consumer<Integer> saver) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, 150, 20, Text.of(label));
        tf.setMaxLength(6);
        tf.setText(Integer.toHexString(current).toUpperCase());
        tf.setTooltip(Tooltip.of(Text.of(tooltip + "\n(Example: FFFFFF for White)")));
        tf.setChangedListener(s -> {
            try {
                saver.accept(Integer.parseInt(s, 16));
                tf.setEditableColor(0xFFFFFF);
            } catch (NumberFormatException e) {
                tf.setEditableColor(0xFF0000);
            }
        });
        optionWidgets.add(tf);
        optionLabels.add(Text.of(label)); // Store label to draw externally
    }
    
    // --- Config Init Methods ---
    // (Pasting the switch content from previous steps, no changes to logic)

    private void resetToDefaults() {
        SimpleCPSConfig defaults = new SimpleCPSConfig();
        switch (category) {
            case "CPS" -> {
                config.enabled = defaults.enabled;
                config.position = defaults.position;
                config.xOffset = defaults.xOffset;
                config.yOffset = defaults.yOffset;
                config.textColor = defaults.textColor;
                config.rightClickCps = defaults.rightClickCps;
                config.rainbow = defaults.rainbow;
                config.scale = defaults.scale;
                config.cpsShowBackground = defaults.cpsShowBackground;
                config.cpsBackgroundColor = defaults.cpsBackgroundColor;
                config.cpsBackgroundOpacity = defaults.cpsBackgroundOpacity;
            }
            case "PING" -> {
                config.showPing = defaults.showPing;
                config.pingPosition = defaults.pingPosition;
                config.pingXOffset = defaults.pingXOffset;
                config.pingYOffset = defaults.pingYOffset;
                config.pingColor = defaults.pingColor;
                config.pingShowBackground = defaults.pingShowBackground;
                config.pingBackgroundColor = defaults.pingBackgroundColor;
                config.pingBackgroundOpacity = defaults.pingBackgroundOpacity;
            }
            case "FPS" -> {
                config.showFps = defaults.showFps;
                config.fpsPosition = defaults.fpsPosition;
                config.fpsXOffset = defaults.fpsXOffset;
                config.fpsYOffset = defaults.fpsYOffset;
                config.fpsColor = defaults.fpsColor;
                config.fpsRainbow = defaults.fpsRainbow;
                config.fpsScale = defaults.fpsScale;
                config.fpsText = defaults.fpsText;
                config.fpsShowBackground = defaults.fpsShowBackground;
                config.fpsBackgroundColor = defaults.fpsBackgroundColor;
                config.fpsBackgroundOpacity = defaults.fpsBackgroundOpacity;
            }
            case "KEYSTROKES" -> {
                config.showKeystrokes = defaults.showKeystrokes;
                config.keystrokesPosition = defaults.keystrokesPosition;
                config.keystrokesXOffset = defaults.keystrokesXOffset;
                config.keystrokesYOffset = defaults.keystrokesYOffset;
                config.keystrokesScale = defaults.keystrokesScale;
                config.keystrokesMode = defaults.keystrokesMode;
                config.keystrokesRainbow = defaults.keystrokesRainbow;
                config.keystrokesRainbowTarget = defaults.keystrokesRainbowTarget;
                config.keystrokesColor = defaults.keystrokesColor;
                config.keystrokesPressedColor = defaults.keystrokesPressedColor;
                config.keystrokesBackgroundColor = defaults.keystrokesBackgroundColor;
                config.keystrokesBackgroundOpacity = defaults.keystrokesBackgroundOpacity;
                config.customW = defaults.customW;
                config.customA = defaults.customA;
                config.customS = defaults.customS;
                config.customD = defaults.customD;
                config.customSpace = defaults.customSpace;
            }
            case "COMBO" -> {
                config.showCombo = defaults.showCombo;
                config.comboPosition = defaults.comboPosition;
                config.comboXOffset = defaults.comboXOffset;
                config.comboYOffset = defaults.comboYOffset;
                config.comboScale = defaults.comboScale;
                config.comboColor = defaults.comboColor;
                config.comboRainbow = defaults.comboRainbow;
                config.comboText = defaults.comboText;
                config.comboShowBackground = defaults.comboShowBackground;
                config.comboBackgroundColor = defaults.comboBackgroundColor;
                config.comboBackgroundOpacity = defaults.comboBackgroundOpacity;
                config.comboTimeout = defaults.comboTimeout;
                config.comboResetOnAnyDamage = defaults.comboResetOnAnyDamage;
                config.comboContinueOnSwitch = defaults.comboContinueOnSwitch;
                config.comboHideWhenInactive = defaults.comboHideWhenInactive;
                config.combatMode = defaults.combatMode;
            }
        }
    }

    private void initCps() {
        addBool("Enabled", "Toggle CPS counter visibility.", config.enabled, v -> config.enabled = v);
        addEnum("Position", "Choose where the module appears on screen.", config.position, SimpleCPSConfig.Position.class, v -> config.position = (SimpleCPSConfig.Position) v);
        addInt("X Offset", "Fine-tune horizontal position.", -1000, 1000, config.xOffset, v -> config.xOffset = v);
        addInt("Y Offset", "Fine-tune vertical position.", -1000, 1000, config.yOffset, v -> config.yOffset = v);
        addBool("Right Click", "Show RMB clicks alongside LMB.", config.rightClickCps, v -> config.rightClickCps = v);
        addBool("Rainbow", "Cycle text color through the rainbow.", config.rainbow, v -> config.rainbow = v);
        addInt("Scale %", "Adjust the size of the text.", 50, 300, config.scale, v -> config.scale = v);
        addHex("Text Color", "Hex color code for the text.", config.textColor, v -> config.textColor = v);
        addBool("Background", "Show a background box.", config.cpsShowBackground, v -> config.cpsShowBackground = v);
        addHex("Bg Color", "Hex color code for the background.", config.cpsBackgroundColor, v -> config.cpsBackgroundColor = v);
        addInt("Bg Opacity", "0 = Transparent, 255 = Opaque.", 0, 255, config.cpsBackgroundOpacity, v -> config.cpsBackgroundOpacity = v);
    }

    private void initPing() {
        addBool("Show Ping", "Toggle Ping display.", config.showPing, v -> config.showPing = v);
        addEnum("Position", "Choose where the module appears.", config.pingPosition, SimpleCPSConfig.Position.class, v -> config.pingPosition = (SimpleCPSConfig.Position) v);
        addInt("X Offset", "Fine-tune horizontal position.", -1000, 1000, config.pingXOffset, v -> config.pingXOffset = v);
        addInt("Y Offset", "Fine-tune vertical position.", -1000, 1000, config.pingYOffset, v -> config.pingYOffset = v);
        addHex("Text Color", "Hex color code for the text.", config.pingColor, v -> config.pingColor = v);
        addBool("Background", "Show a background box.", config.pingShowBackground, v -> config.pingShowBackground = v);
        addHex("Bg Color", "Hex color code for the background.", config.pingBackgroundColor, v -> config.pingBackgroundColor = v);
        addInt("Bg Opacity", "0 = Transparent, 255 = Opaque.", 0, 255, config.pingBackgroundOpacity, v -> config.pingBackgroundOpacity = v);
    }

    private void initFps() {
        addBool("Show FPS", "Toggle FPS display.", config.showFps, v -> config.showFps = v);
        addEnum("Position", "Choose where the module appears.", config.fpsPosition, SimpleCPSConfig.Position.class, v -> config.fpsPosition = (SimpleCPSConfig.Position) v);
        addInt("X Offset", "Fine-tune horizontal position.", -1000, 1000, config.fpsXOffset, v -> config.fpsXOffset = v);
        addInt("Y Offset", "Fine-tune vertical position.", -1000, 1000, config.fpsYOffset, v -> config.fpsYOffset = v);
        addInt("Scale %", "Adjust size.", 50, 300, config.fpsScale, v -> config.fpsScale = v);
        addBool("Rainbow", "Cycle text color.", config.fpsRainbow, v -> config.fpsRainbow = v);
        addHex("Text Color", "Hex color code.", config.fpsColor, v -> config.fpsColor = v);
        addBool("Background", "Show background box.", config.fpsShowBackground, v -> config.fpsShowBackground = v);
        addHex("Bg Color", "Background hex color.", config.fpsBackgroundColor, v -> config.fpsBackgroundColor = v);
        addInt("Bg Opacity", "Background opacity.", 0, 255, config.fpsBackgroundOpacity, v -> config.fpsBackgroundOpacity = v);
    }

    private void initKeystrokes() {
        addBool("Show Keystrokes", "Toggle Keystrokes.", config.showKeystrokes, v -> config.showKeystrokes = v);
        addEnum("Position", "Choose module position.", config.keystrokesPosition, SimpleCPSConfig.Position.class, v -> config.keystrokesPosition = (SimpleCPSConfig.Position) v);
        addInt("X Offset", "Horizontal offset.", -1000, 1000, config.keystrokesXOffset, v -> config.keystrokesXOffset = v);
        addInt("Y Offset", "Vertical offset.", -1000, 1000, config.keystrokesYOffset, v -> config.keystrokesYOffset = v);
        addInt("Scale %", "Module size.", 50, 200, config.keystrokesScale, v -> config.keystrokesScale = v);
        addEnum("Mode", "Style: WASD, Arrows, or Custom.", config.keystrokesMode, SimpleCPSConfig.KeystrokesMode.class, v -> config.keystrokesMode = (SimpleCPSConfig.KeystrokesMode) v);
        addBool("Rainbow", "Enable rainbow effect.", config.keystrokesRainbow, v -> config.keystrokesRainbow = v);
        addEnum("Rainbow Target", "Apply rainbow to Text or Background?", config.keystrokesRainbowTarget, SimpleCPSConfig.RainbowTarget.class, v -> config.keystrokesRainbowTarget = (SimpleCPSConfig.RainbowTarget) v);
        addHex("Text Color", "Normal key text color.", config.keystrokesColor, v -> config.keystrokesColor = v);
        addHex("Pressed Color", "Key color when pressed.", config.keystrokesPressedColor, v -> config.keystrokesPressedColor = v);
        addHex("Bg Color", "Background color.", config.keystrokesBackgroundColor, v -> config.keystrokesBackgroundColor = v);
        addInt("Bg Opacity", "Background opacity.", 0, 255, config.keystrokesBackgroundOpacity, v -> config.keystrokesBackgroundOpacity = v);
    }

    private void initCombo() {
        addBool("Show Combo", "Toggle Combo Counter.", config.showCombo, v -> config.showCombo = v);
        addEnum("Position", "Choose position.", config.comboPosition, SimpleCPSConfig.Position.class, v -> config.comboPosition = (SimpleCPSConfig.Position) v);
        addEnum("Combat Mode", "Modern (1.9+ Cooldown) or Classic (1.8 Spam).", config.combatMode, SimpleCPSConfig.CombatMode.class, v -> config.combatMode = (SimpleCPSConfig.CombatMode) v);
        addInt("X Offset", "Horizontal offset.", -1000, 1000, config.comboXOffset, v -> config.comboXOffset = v);
        addInt("Y Offset", "Vertical offset.", -1000, 1000, config.comboYOffset, v -> config.comboYOffset = v);
        addInt("Scale %", "Text size.", 50, 300, config.comboScale, v -> config.comboScale = v);
        addBool("Rainbow", "Cycle colors.", config.comboRainbow, v -> config.comboRainbow = v);
        addHex("Text Color", "Text hex color.", config.comboColor, v -> config.comboColor = v);
        addBool("Background", "Show background.", config.comboShowBackground, v -> config.comboShowBackground = v);
        addHex("Bg Color", "Background hex color.", config.comboBackgroundColor, v -> config.comboBackgroundColor = v);
        addInt("Bg Opacity", "Background opacity.", 0, 255, config.comboBackgroundOpacity, v -> config.comboBackgroundOpacity = v);
        addInt("Timeout (s)", "Time before combo resets.", 1, 10, (int)config.comboTimeout, v -> config.comboTimeout = v);
        addBool("Reset Any Dmg", "Reset combo on ANY damage taken.", config.comboResetOnAnyDamage, v -> config.comboResetOnAnyDamage = v);
        addBool("Continue Switch", "Keep combo when switching targets.", config.comboContinueOnSwitch, v -> config.comboContinueOnSwitch = v);
        addBool("Only Active", "Hide counter when combo is 0.", config.comboHideWhenInactive, v -> config.comboHideWhenInactive = v);
    }

    @Override
    public void close() {
        SimpleCPSConfig.save();
        client.setScreen(parent);
    }
}
