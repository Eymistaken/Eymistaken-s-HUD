package com.eymistaken.simplecps;

import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.gui.KeystrokesDesignerScreen;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClothConfigFactory {
    // Color Picker Flags
    private static boolean openColorPicker = false;
    private static Runnable openColorPickerRunnable = null;
    private static boolean pendingReset = false;

    public static Screen create(Screen parent) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        pendingReset = false;
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Eymistaken's HUD Settings"))
                .setSavingRunnable(() -> {
                    // Cloth runs this after every save consumer, which is the only safe
                    // point to swap SimpleCPSConfig.instance: all those consumers write
                    // into the object captured above, so resetting from inside one would
                    // silently discard whichever of them happened to run afterwards.
                    if (pendingReset) {
                        pendingReset = false;
                        SimpleCPSConfig.instance.resetToDefaults(); // saves on its own
                    } else {
                        SimpleCPSConfig.save();
                    }
                    if (openColorPicker && openColorPickerRunnable != null) {
                        openColorPicker = false;
                        Minecraft.getInstance().execute(openColorPickerRunnable);
                    }
                });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // --- CPS ---
        ConfigCategory cps = builder.getOrCreateCategory(Component.literal("CPS"));
        cps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Enabled"), config.enabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Toggle CPS counter visibility"))
                .setSaveConsumer(val -> config.enabled = val)
                .build());
        cps.addEntry(entryBuilder.startEnumSelector(Component.literal("Position"), SimpleCPSConfig.Position.class, config.position)
                .setDefaultValue(SimpleCPSConfig.Position.BOTTOM_RIGHT)
                .setTooltip(Component.literal("Anchor position for CPS"))
                .setSaveConsumer(val -> config.position = val)
                .build());
        cps.addEntry(entryBuilder.startIntField(Component.literal("X Offset"), config.xOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.xOffset = val)
                .build());
        cps.addEntry(entryBuilder.startIntField(Component.literal("Y Offset"), config.yOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.yOffset = val)
                .build());
        cps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Right Click CPS"), config.rightClickCps)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Show right click CPS alongside left click"))
                .setSaveConsumer(val -> config.rightClickCps = val)
                .build());
        cps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Rainbow"), config.rainbow)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Enable rainbow text effect"))
                .setSaveConsumer(val -> config.rainbow = val)
                .build());
        cps.addEntry(entryBuilder.startIntSlider(Component.literal("Scale %"), config.scale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Component.literal("Size of the CPS display"))
                .setSaveConsumer(val -> config.scale = val)
                .build());
        cps.addEntry(entryBuilder.startColorField(Component.literal("Text Color"), config.textColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Component.literal("Color of the text"))
                .setSaveConsumer(val -> config.textColor = val)
                .build());

        cps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.textColor, c -> config.textColor = c, parent));
                    }
                })
                .build());
        cps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Background"), config.cpsShowBackground)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Show background box"))
                .setSaveConsumer(val -> config.cpsShowBackground = val)
                .build());
        cps.addEntry(entryBuilder.startColorField(Component.literal("Background Color"), config.cpsBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Component.literal("Color of the background"))
                .setSaveConsumer(val -> config.cpsBackgroundColor = val)
                .build());

        cps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.cpsBackgroundColor, c -> config.cpsBackgroundColor = c, parent));
                    }
                })
                .build());
        cps.addEntry(entryBuilder.startIntSlider(Component.literal("Background Opacity"), config.cpsBackgroundOpacity, 0, 255)
                .setDefaultValue(64)
                .setTooltip(Component.literal("Transparency of the background"))
                .setSaveConsumer(val -> config.cpsBackgroundOpacity = val)
                .build());

        // New CPS Text Fields
        cps.addEntry(entryBuilder.startStrField(Component.literal("Left Prefix"), config.cpsLeftText)
                .setDefaultValue("")
                .setTooltip(Component.literal("Text before left click count"))
                .setSaveConsumer(val -> config.cpsLeftText = val)
                .build());
        cps.addEntry(entryBuilder.startStrField(Component.literal("Separator"), config.cpsSeparator)
                .setDefaultValue(" | ")
                .setTooltip(Component.literal("Separator between left and right count"))
                .setSaveConsumer(val -> config.cpsSeparator = val)
                .build());
        cps.addEntry(entryBuilder.startStrField(Component.literal("Right Prefix"), config.cpsRightText)
                .setDefaultValue("")
                .setTooltip(Component.literal("Text before right click count"))
                .setSaveConsumer(val -> config.cpsRightText = val)
                .build());

        // --- PING ---
        ConfigCategory ping = builder.getOrCreateCategory(Component.literal("Ping"));
        ping.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Ping"), config.showPing)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Toggle Ping visibility"))
                .setSaveConsumer(val -> config.showPing = val)
                .build());
        ping.addEntry(entryBuilder.startEnumSelector(Component.literal("Position"), SimpleCPSConfig.Position.class, config.pingPosition)
                .setDefaultValue(SimpleCPSConfig.Position.BOTTOM_RIGHT)
                .setTooltip(Component.literal("Anchor position for Ping"))
                .setSaveConsumer(val -> config.pingPosition = val)
                .build());
        ping.addEntry(entryBuilder.startIntField(Component.literal("X Offset"), config.pingXOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.pingXOffset = val)
                .build());
        ping.addEntry(entryBuilder.startIntField(Component.literal("Y Offset"), config.pingYOffset)
                .setDefaultValue(-10)
                .setTooltip(Component.literal("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.pingYOffset = val)
                .build());
        ping.addEntry(entryBuilder.startIntSlider(Component.literal("Scale %"), config.pingScale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Component.literal("Size of the ping display"))
                .setSaveConsumer(val -> config.pingScale = val)
                .build());
        ping.addEntry(entryBuilder.startColorField(Component.literal("Text Color"), config.pingColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Component.literal("Color of the text"))
                .setSaveConsumer(val -> config.pingColor = val)
                .build());

        ping.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.pingColor, c -> config.pingColor = c, parent));
                    }
                })
                .build());
        ping.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Background"), config.pingShowBackground)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Show background box"))
                .setSaveConsumer(val -> config.pingShowBackground = val)
                .build());
        ping.addEntry(entryBuilder.startColorField(Component.literal("Background Color"), config.pingBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Component.literal("Color of the background"))
                .setSaveConsumer(val -> config.pingBackgroundColor = val)
                .build());

        ping.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.pingBackgroundColor, c -> config.pingBackgroundColor = c, parent));
                    }
                })
                .build());
        ping.addEntry(entryBuilder.startIntSlider(Component.literal("Background Opacity"), config.pingBackgroundOpacity, 0, 255)
                .setDefaultValue(64)
                .setTooltip(Component.literal("Transparency of the background"))
                .setSaveConsumer(val -> config.pingBackgroundOpacity = val)
                .build());

        // --- FPS ---
        ConfigCategory fps = builder.getOrCreateCategory(Component.literal("FPS"));
        fps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show FPS"), config.showFps)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Toggle FPS visibility"))
                .setSaveConsumer(val -> config.showFps = val)
                .build());
        fps.addEntry(entryBuilder.startEnumSelector(Component.literal("Position"), SimpleCPSConfig.Position.class, config.fpsPosition)
                .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                .setTooltip(Component.literal("Anchor position for FPS"))
                .setSaveConsumer(val -> config.fpsPosition = val)
                .build());
        fps.addEntry(entryBuilder.startIntField(Component.literal("X Offset"), config.fpsXOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.fpsXOffset = val)
                .build());
        fps.addEntry(entryBuilder.startIntField(Component.literal("Y Offset"), config.fpsYOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.fpsYOffset = val)
                .build());
        fps.addEntry(entryBuilder.startIntSlider(Component.literal("Scale %"), config.fpsScale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Component.literal("Size of the FPS display"))
                .setSaveConsumer(val -> config.fpsScale = val)
                .build());
        fps.addEntry(entryBuilder.startStrField(Component.literal("Suffix Text"), config.fpsText)
                .setDefaultValue("FPS")
                .setTooltip(Component.literal("Text to display after fps value"))
                .setSaveConsumer(val -> config.fpsText = val)
                .build());
        fps.addEntry(entryBuilder.startColorField(Component.literal("Text Color"), config.fpsColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Component.literal("Color of the text"))
                .setSaveConsumer(val -> config.fpsColor = val)
                .build());

        fps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.fpsColor, c -> config.fpsColor = c, parent));
                    }
                })
                .build());
        fps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Rainbow"), config.fpsRainbow)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Enable rainbow text effect"))
                .setSaveConsumer(val -> config.fpsRainbow = val)
                .build());
        fps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Background"), config.fpsShowBackground)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Show background box"))
                .setSaveConsumer(val -> config.fpsShowBackground = val)
                .build());
         fps.addEntry(entryBuilder.startColorField(Component.literal("Background Color"), config.fpsBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Component.literal("Color of the background"))
                .setSaveConsumer(val -> config.fpsBackgroundColor = val)
                .build());

         fps.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                 .setDefaultValue(false)
                 .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                 .setSaveConsumer(val -> {
                     if (val) {
                         openColorPicker = true;
                         openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.fpsBackgroundColor, c -> config.fpsBackgroundColor = c, parent));
                     }
                 })
                 .build());
        fps.addEntry(entryBuilder.startIntSlider(Component.literal("Background Opacity"), config.fpsBackgroundOpacity, 0, 255)
                .setDefaultValue(64)
                .setTooltip(Component.literal("Transparency of the background"))
                .setSaveConsumer(val -> config.fpsBackgroundOpacity = val)
                .build());
        
        // --- KEYSTROKES ---
        ConfigCategory keys = builder.getOrCreateCategory(Component.literal("Keystrokes"));
        keys.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Keystrokes"), config.showKeystrokes)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Toggle Keystrokes visibility"))
                .setSaveConsumer(val -> config.showKeystrokes = val)
                .build());
        
        // Open Designer Button
        keys.addEntry(entryBuilder.startBooleanToggle(Component.literal("Designer"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("To open Designer, Enable the toggle and click \"Save & Quit\""))
                .setSaveConsumer(val -> {
                    if (val) {
                         // Schedule opening the designer
                        Minecraft.getInstance().execute(() -> 
                            Minecraft.getInstance().gui.setScreen(new KeystrokesDesignerScreen(parent))
                        );
                    }
                })
                .build());

        keys.addEntry(entryBuilder.startEnumSelector(Component.literal("Position"), SimpleCPSConfig.Position.class, config.keystrokesPosition)
                .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                .setTooltip(Component.literal("Anchor position for Keystrokes"))
                .setSaveConsumer(val -> config.keystrokesPosition = val)
                .build());
        keys.addEntry(entryBuilder.startIntField(Component.literal("X Offset"), config.keystrokesXOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.keystrokesXOffset = val)
                .build());
        keys.addEntry(entryBuilder.startIntField(Component.literal("Y Offset"), config.keystrokesYOffset)
                .setDefaultValue(40)
                .setTooltip(Component.literal("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.keystrokesYOffset = val)
                .build());

         keys.addEntry(entryBuilder.startIntSlider(Component.literal("Scale %"), config.keystrokesScale, 50, 300)
                .setDefaultValue(80)
                .setTooltip(Component.literal("Size of the keystrokes"))
                .setSaveConsumer(val -> config.keystrokesScale = val)
                .build());
        keys.addEntry(entryBuilder.startColorField(Component.literal("Text Color"), config.keystrokesColor)
                 .setDefaultValue(0xFFFFFF)
                 .setTooltip(Component.literal("Color of the key text"))
                 .setSaveConsumer(val -> config.keystrokesColor = val)
                 .build());

        keys.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.keystrokesColor, c -> config.keystrokesColor = c, parent));
                    }
                })
                .build());
        keys.addEntry(entryBuilder.startColorField(Component.literal("Pressed Color"), config.keystrokesPressedColor)
                 .setDefaultValue(0x00FF00)
                 .setTooltip(Component.literal("Color when key is pressed"))
                 .setSaveConsumer(val -> config.keystrokesPressedColor = val)
                 .build());

        keys.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.keystrokesPressedColor, c -> config.keystrokesPressedColor = c, parent));
                    }
                })
                .build());
        keys.addEntry(entryBuilder.startBooleanToggle(Component.literal("Rainbow"), config.keystrokesRainbow)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Enable rainbow effect"))
                .setSaveConsumer(val -> config.keystrokesRainbow = val)
                .build());
        keys.addEntry(entryBuilder.startEnumSelector(Component.literal("Rainbow Target"), SimpleCPSConfig.RainbowTarget.class, config.keystrokesRainbowTarget)
                .setDefaultValue(SimpleCPSConfig.RainbowTarget.TEXT)
                .setTooltip(Component.literal("Apply rainbow to text or background"))
                .setSaveConsumer(val -> config.keystrokesRainbowTarget = val)
                .build());
        keys.addEntry(entryBuilder.startColorField(Component.literal("Background Color"), config.keystrokesBackgroundColor)
                 .setDefaultValue(0x000000)
                 .setTooltip(Component.literal("Color of key backgrounds"))
                 .setSaveConsumer(val -> config.keystrokesBackgroundColor = val)
                 .build());

        keys.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.keystrokesBackgroundColor, c -> config.keystrokesBackgroundColor = c, parent));
                    }
                })
                .build());
        keys.addEntry(entryBuilder.startIntSlider(Component.literal("Background Opacity"), config.keystrokesBackgroundOpacity, 0, 255)
                 .setDefaultValue(128)
                 .setTooltip(Component.literal("Transparency of key backgrounds"))
                 .setSaveConsumer(val -> config.keystrokesBackgroundOpacity = val)
                 .build());
        
        // --- COMBO ---
        ConfigCategory combo = builder.getOrCreateCategory(Component.literal("Combo")); // Removed (Beta)
        combo.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Combo"), config.showCombo)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Toggle Combo counter visibility"))
                .setSaveConsumer(val -> config.showCombo = val)
                .build());
        combo.addEntry(entryBuilder.startEnumSelector(Component.literal("Position"), SimpleCPSConfig.Position.class, config.comboPosition)
                .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                .setTooltip(Component.literal("Anchor position for Combo"))
                .setSaveConsumer(val -> config.comboPosition = val)
                .build());
        combo.addEntry(entryBuilder.startEnumSelector(Component.literal("Combat Mode"), SimpleCPSConfig.CombatMode.class, config.combatMode)
                .setDefaultValue(SimpleCPSConfig.CombatMode.MODERN)
                .setTooltip(Component.literal("Combat mechanics for combo detection"))
                .setSaveConsumer(val -> config.combatMode = val)
                .build());
        combo.addEntry(entryBuilder.startIntField(Component.literal("X Offset"), config.comboXOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.comboXOffset = val)
                .build());
        combo.addEntry(entryBuilder.startIntField(Component.literal("Y Offset"), config.comboYOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.comboYOffset = val)
                .build());
        combo.addEntry(entryBuilder.startIntSlider(Component.literal("Scale %"), config.comboScale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Component.literal("Size of the Combo display"))
                .setSaveConsumer(val -> config.comboScale = val)
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Component.literal("Reset on Any Damage (Experimental)"), config.comboResetOnAnyDamage)
                .setDefaultValue(true) // Legacy Default
                .setTooltip(Component.literal("ON: Classic Mode (Reset on hit). OFF: Advanced Mode (Decay + Distance Check)"))
                .setSaveConsumer(val -> config.comboResetOnAnyDamage = val)
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Component.literal("Only Players"), config.comboOnlyPlayers) // New
                .setDefaultValue(true)
                .setTooltip(Component.literal("If enabled, only hits on players count for combo"))
                .setSaveConsumer(val -> config.comboOnlyPlayers = val)
                .build());
        combo.addEntry(entryBuilder.startIntSlider(Component.literal("Timeout (s)"), (int)config.comboTimeout, 1, 10)
                .setDefaultValue(2)
                .setTooltip(Component.literal("Seconds before combo resets"))
                .setSaveConsumer(val -> config.comboTimeout = val)
                .build());
        combo.addEntry(entryBuilder.startStrField(Component.literal("Suffix Text"), config.comboText)
                .setDefaultValue("Combo")
                .setTooltip(Component.literal("Text displayed after combo count"))
                .setSaveConsumer(val -> config.comboText = val)
                .build());
        // Decay & Distance Check are now tied to !comboResetOnAnyDamage internally.
        // UI Removed per user request.
        
        // LOS Check Removed per user request
        
        combo.addEntry(entryBuilder.startBooleanToggle(Component.literal("Heatmap Mode"), config.comboHeatmap)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Dynamic Color (Blue->Red->Black) + Shake. Overrides Rainbow."))
                .setSaveConsumer(val -> config.comboHeatmap = val)
                .build());
        combo.addEntry(entryBuilder.startEnumSelector(Component.literal("Heatmap Difficulty"), SimpleCPSConfig.HeatmapMode.class, config.comboHeatmapMode) // New
                .setDefaultValue(SimpleCPSConfig.HeatmapMode.MEDIUM)
                .setTooltip(Component.literal("Difficulty scaling for Heatmap colors and shake effect. Resets combo on change."))
                .setSaveConsumer(val -> {
                     if (config.comboHeatmapMode != val) {
                         ComboTracker.reset(); // Reset combo on mode change
                     }
                     config.comboHeatmapMode = val;
                })
                .build());
        combo.addEntry(entryBuilder.startColorField(Component.literal("Text Color"), config.comboColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Component.literal("Color of the text"))
                .setSaveConsumer(val -> config.comboColor = val)
                .build());

        combo.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.comboColor, c -> config.comboColor = c, parent));
                    }
                })
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Component.literal("Rainbow"), config.comboRainbow)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Enable rainbow text effect"))
                .setSaveConsumer(val -> config.comboRainbow = val)
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Background"), config.comboShowBackground)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Show background box"))
                .setSaveConsumer(val -> config.comboShowBackground = val)
                .build());
         combo.addEntry(entryBuilder.startColorField(Component.literal("Background Color"), config.comboBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Component.literal("Color of the background"))
                .setSaveConsumer(val -> config.comboBackgroundColor = val)
                .build());

         combo.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                 .setDefaultValue(false)
                 .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                 .setSaveConsumer(val -> {
                     if (val) {
                         openColorPicker = true;
                         openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.comboBackgroundColor, c -> config.comboBackgroundColor = c, parent));
                     }
                 })
                 .build());
        combo.addEntry(entryBuilder.startIntSlider(Component.literal("Background Opacity"), config.comboBackgroundOpacity, 0, 255)
                .setDefaultValue(128)
                .setTooltip(Component.literal("Transparency of the background"))
                .setSaveConsumer(val -> config.comboBackgroundOpacity = val)
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Component.literal("Hide When Inactive"), config.comboHideWhenInactive)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Hide combo display when combo is 0"))
                .setSaveConsumer(val -> config.comboHideWhenInactive = val)
                .build());

        // --- REACH DISPLAY ---
        ConfigCategory reach = builder.getOrCreateCategory(Component.literal("Reach Display"));
        reach.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Reach Display"), config.showReach)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Toggle Reach Display visibility"))
                .setSaveConsumer(val -> config.showReach = val)
                .build());
        reach.addEntry(entryBuilder.startEnumSelector(Component.literal("Position"), SimpleCPSConfig.Position.class, config.reachPosition)
                .setDefaultValue(SimpleCPSConfig.Position.CENTER)
                .setTooltip(Component.literal("Anchor position for Reach Display"))
                .setSaveConsumer(val -> config.reachPosition = val)
                .build());
        reach.addEntry(entryBuilder.startIntField(Component.literal("X Offset"), config.reachXOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.reachXOffset = val)
                .build());
        reach.addEntry(entryBuilder.startIntField(Component.literal("Y Offset"), config.reachYOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.reachYOffset = val)
                .build());
        reach.addEntry(entryBuilder.startColorField(Component.literal("Text Color"), config.reachColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Component.literal("Color of the text"))
                .setSaveConsumer(val -> config.reachColor = val)
                .build());

        reach.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.reachColor, c -> config.reachColor = c, parent));
                    }
                })
                .build());
        reach.addEntry(entryBuilder.startBooleanToggle(Component.literal("Rainbow"), config.reachRainbow)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Enable rainbow text effect"))
                .setSaveConsumer(val -> config.reachRainbow = val)
                .build());
        reach.addEntry(entryBuilder.startIntSlider(Component.literal("Scale %"), config.reachScale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Component.literal("Size of the Reach display"))
                .setSaveConsumer(val -> config.reachScale = val)
                .build());
        reach.addEntry(entryBuilder.startIntSlider(Component.literal("Timeout (s)"), (int)config.reachTimeout, 1, 10)
                .setDefaultValue(3)
                .setTooltip(Component.literal("Seconds before display disappears"))
                .setSaveConsumer(val -> config.reachTimeout = val)
                .build());
        reach.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Background"), config.reachShowBackground)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Show background box"))
                .setSaveConsumer(val -> config.reachShowBackground = val)
                .build());
        reach.addEntry(entryBuilder.startColorField(Component.literal("Background Color"), config.reachBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Component.literal("Color of the background"))
                .setSaveConsumer(val -> config.reachBackgroundColor = val)
                .build());

        reach.addEntry(entryBuilder.startBooleanToggle(Component.literal("Edit Color"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save & Quit to open precise color picker"))
                .setSaveConsumer(val -> {
                    if (val) {
                        openColorPicker = true;
                        openColorPickerRunnable = () -> Minecraft.getInstance().gui.setScreen(new com.eymistaken.simplecps.gui.ColorPickerScreen(config.reachBackgroundColor, c -> config.reachBackgroundColor = c, parent));
                    }
                })
                .build());
        reach.addEntry(entryBuilder.startIntSlider(Component.literal("Background Opacity"), config.reachBackgroundOpacity, 0, 255)
                .setDefaultValue(128)
                .setTooltip(Component.literal("Transparency of the background"))
                .setSaveConsumer(val -> config.reachBackgroundOpacity = val)
                .build());
        reach.addEntry(entryBuilder.startBooleanToggle(Component.literal("Only Players"), config.reachOnlyPlayers)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Only show reach when hitting players"))
                .setSaveConsumer(val -> config.reachOnlyPlayers = val)
                .build());
        reach.addEntry(entryBuilder.startBooleanToggle(Component.literal("Always Show"), config.reachAlwaysShow)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Show reach display even when inactive"))
                .setSaveConsumer(val -> config.reachAlwaysShow = val)
                .build());
        reach.addEntry(entryBuilder.startStrField(Component.literal("No Hit Text"), config.reachNoHitText)
                .setDefaultValue("No Hit")
                .setTooltip(Component.literal("Text to show when no hit has occurred"))
                .setSaveConsumer(val -> config.reachNoHitText = val)
                .build());

        // --- ARMOR HUD ---
        ConfigCategory armor = builder.getOrCreateCategory(Component.literal("Armor HUD"));
        armor.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Armor"), config.showArmor)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Toggle Armor HUD visibility"))
                .setSaveConsumer(val -> config.showArmor = val)
                .build());
        armor.addEntry(entryBuilder.startEnumSelector(Component.literal("Position"), SimpleCPSConfig.Position.class, config.armorPosition)
                .setDefaultValue(SimpleCPSConfig.Position.BOTTOM_LEFT)
                .setTooltip(Component.literal("Anchor position for Armor HUD"))
                .setSaveConsumer(val -> config.armorPosition = val)
                .build());
        armor.addEntry(entryBuilder.startIntField(Component.literal("X Offset"), config.armorXOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.armorXOffset = val)
                .build());
        armor.addEntry(entryBuilder.startIntField(Component.literal("Y Offset"), config.armorYOffset)
                .setDefaultValue(0)
                .setTooltip(Component.literal("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.armorYOffset = val)
                .build());
        armor.addEntry(entryBuilder.startBooleanToggle(Component.literal("Vertical Orientation"), config.armorVertical)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Stack items vertically instead of horizontally"))
                .setSaveConsumer(val -> config.armorVertical = val)
                .build());
        armor.addEntry(entryBuilder.startIntSlider(Component.literal("Scale %"), config.armorScale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Component.literal("Size of the armor slots"))
                .setSaveConsumer(val -> config.armorScale = val)
                .build());
        armor.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Background Slots"), config.armorShowBackgroundSlots)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Show semi-transparent background slot for each item"))
                .setSaveConsumer(val -> config.armorShowBackgroundSlots = val)
                .build());
        armor.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Main Hand"), config.armorShowMainHand)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Display the item in your main hand"))
                .setSaveConsumer(val -> config.armorShowMainHand = val)
                .build());
        armor.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show Off Hand"), config.armorShowOffHand)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Display the item in your off hand"))
                .setSaveConsumer(val -> config.armorShowOffHand = val)
                .build());
        armor.addEntry(entryBuilder.startBooleanToggle(Component.literal("Durability Text"), config.armorDurabilityText)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Display precise durability numbers alongside items"))
                .setSaveConsumer(val -> config.armorDurabilityText = val)
                .build());
        armor.addEntry(entryBuilder.startBooleanToggle(Component.literal("Damage Flash"), config.armorDamageFlash)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Flash items red momentarily when durability is lost"))
                .setSaveConsumer(val -> config.armorDamageFlash = val)
                .build());

        // --- NEW LAYOUT TAB ---
        ConfigCategory layout = builder.getOrCreateCategory(Component.literal("General"));
        layout.addEntry(entryBuilder.startTextDescription(Component.literal("To open the Drag & Drop Editor: Enable the toggle below and click 'Save & Quit'."))
                .build());

        layout.addEntry(entryBuilder.startBooleanToggle(Component.literal("Text Outline"), config.textOutline)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Draw HUD text with a thin black outline on every side instead of a drop shadow."))
                .setSaveConsumer(val -> config.textOutline = val)
                .build());

        layout.addEntry(entryBuilder.startEnumSelector(Component.literal("HUD Font"), SimpleCPSConfig.HudFont.class, config.hudFont)
                .setDefaultValue(SimpleCPSConfig.HudFont.VANILLA)
                .setTooltip(Component.literal("Font for HUD elements and the editor screens only (never chat or other menus)."))
                .setSaveConsumer(val -> config.hudFont = val)
                .build());

        layout.addEntry(entryBuilder.startBooleanToggle(Component.literal("Editor Grid"), config.editorGridEnabled)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Show a grid in the Drag & Drop Editor and snap dragged elements to it."))
                .setSaveConsumer(val -> config.editorGridEnabled = val)
                .build());

        layout.addEntry(entryBuilder.startIntSlider(Component.literal("Grid Size"), config.editorGridSize, 2, 64)
                .setDefaultValue(8)
                .setTooltip(Component.literal("Spacing of the editor grid, in pixels."))
                .setSaveConsumer(val -> config.editorGridSize = val)
                .build());

        layout.addEntry(entryBuilder.startBooleanToggle(Component.literal("Server Configs"), config.serverConfigsEnabled)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Remember the HUD per server: joining a server you have configured loads its config, and changes made there are saved back to it."))
                .setSaveConsumer(val -> config.serverConfigsEnabled = val)
                .build());

        layout.addEntry(entryBuilder.startBooleanToggle(Component.literal("Open Drag & Drop Editor"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Enable this and Save to open the editor directly."))
                .setSaveConsumer(val -> {
                    if (val) {
                         // We need to schedule this because the screen is currently closing
                        Minecraft.getInstance().execute(() -> 
                            Minecraft.getInstance().gui.setScreen(new HudEditorScreen(parent))
                        );
                    }
                })
                .build());

        // Reset Config Button
        layout.addEntry(entryBuilder.startBooleanToggle(Component.literal("RESET ALL SETTINGS"), false)
                .setDefaultValue(false)
                .setTooltip(Component.literal("WARNING: Resets all settings to default! (Click Save & Quit to apply)"))
                .setSaveConsumer(val -> {
                    if (val) {
                        pendingReset = true; // deferred; see setSavingRunnable above
                    }
                })
                .build());

        return builder.build();
    }
}
