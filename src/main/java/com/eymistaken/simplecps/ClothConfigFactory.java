package com.eymistaken.simplecps;

import com.eymistaken.simplecps.gui.HudEditorScreen;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ClothConfigFactory {
    public static Screen create(Screen parent) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.of("Eymistaken's HUD Settings"))
                .setSavingRunnable(SimpleCPSConfig::save);

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // --- CPS ---
        ConfigCategory cps = builder.getOrCreateCategory(Text.of("CPS"));
        cps.addEntry(entryBuilder.startBooleanToggle(Text.of("Enabled"), config.enabled)
                .setDefaultValue(true)
                .setTooltip(Text.of("Toggle CPS counter visibility"))
                .setSaveConsumer(val -> config.enabled = val)
                .build());
        cps.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.position)
                .setDefaultValue(SimpleCPSConfig.Position.BOTTOM_RIGHT)
                .setTooltip(Text.of("Anchor position for CPS"))
                .setSaveConsumer(val -> config.position = val)
                .build());
        cps.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.xOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.xOffset = val)
                .build());
        cps.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.yOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.yOffset = val)
                .build());
        cps.addEntry(entryBuilder.startBooleanToggle(Text.of("Right Click CPS"), config.rightClickCps)
                .setDefaultValue(false)
                .setTooltip(Text.of("Show right click CPS alongside left click"))
                .setSaveConsumer(val -> config.rightClickCps = val)
                .build());
        cps.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow"), config.rainbow)
                .setDefaultValue(false)
                .setTooltip(Text.of("Enable rainbow text effect"))
                .setSaveConsumer(val -> config.rainbow = val)
                .build());
        cps.addEntry(entryBuilder.startIntSlider(Text.of("Scale %"), config.scale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Text.of("Size of the CPS display"))
                .setSaveConsumer(val -> config.scale = val)
                .build());
        cps.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.textColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Text.of("Color of the text"))
                .setSaveConsumer(val -> config.textColor = val)
                .build());
        cps.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.cpsShowBackground)
                .setDefaultValue(true)
                .setTooltip(Text.of("Show background box"))
                .setSaveConsumer(val -> config.cpsShowBackground = val)
                .build());
        cps.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.cpsBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Text.of("Color of the background"))
                .setSaveConsumer(val -> config.cpsBackgroundColor = val)
                .build());
        cps.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.cpsBackgroundOpacity, 0, 255)
                .setDefaultValue(64)
                .setTooltip(Text.of("Transparency of the background"))
                .setSaveConsumer(val -> config.cpsBackgroundOpacity = val)
                .build());

        // --- PING ---
        ConfigCategory ping = builder.getOrCreateCategory(Text.of("Ping"));
        ping.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Ping"), config.showPing)
                .setDefaultValue(true)
                .setTooltip(Text.of("Toggle Ping visibility"))
                .setSaveConsumer(val -> config.showPing = val)
                .build());
        ping.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.pingPosition)
                .setDefaultValue(SimpleCPSConfig.Position.BOTTOM_RIGHT)
                .setTooltip(Text.of("Anchor position for Ping"))
                .setSaveConsumer(val -> config.pingPosition = val)
                .build());
        ping.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.pingXOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.pingXOffset = val)
                .build());
        ping.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.pingYOffset)
                .setDefaultValue(-10)
                .setTooltip(Text.of("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.pingYOffset = val)
                .build());
        ping.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.pingColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Text.of("Color of the text"))
                .setSaveConsumer(val -> config.pingColor = val)
                .build());
        ping.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.pingShowBackground)
                .setDefaultValue(true)
                .setTooltip(Text.of("Show background box"))
                .setSaveConsumer(val -> config.pingShowBackground = val)
                .build());
        ping.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.pingBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Text.of("Color of the background"))
                .setSaveConsumer(val -> config.pingBackgroundColor = val)
                .build());
        ping.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.pingBackgroundOpacity, 0, 255)
                .setDefaultValue(64)
                .setTooltip(Text.of("Transparency of the background"))
                .setSaveConsumer(val -> config.pingBackgroundOpacity = val)
                .build());

        // --- FPS ---
        ConfigCategory fps = builder.getOrCreateCategory(Text.of("FPS"));
        fps.addEntry(entryBuilder.startBooleanToggle(Text.of("Show FPS"), config.showFps)
                .setDefaultValue(true)
                .setTooltip(Text.of("Toggle FPS visibility"))
                .setSaveConsumer(val -> config.showFps = val)
                .build());
        fps.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.fpsPosition)
                .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                .setTooltip(Text.of("Anchor position for FPS"))
                .setSaveConsumer(val -> config.fpsPosition = val)
                .build());
        fps.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.fpsXOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.fpsXOffset = val)
                .build());
        fps.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.fpsYOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.fpsYOffset = val)
                .build());
        fps.addEntry(entryBuilder.startIntSlider(Text.of("Scale %"), config.fpsScale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Text.of("Size of the FPS display"))
                .setSaveConsumer(val -> config.fpsScale = val)
                .build());
        fps.addEntry(entryBuilder.startStrField(Text.of("Suffix Text"), config.fpsText)
                .setDefaultValue("FPS")
                .setTooltip(Text.of("Text to display after fps value"))
                .setSaveConsumer(val -> config.fpsText = val)
                .build());
        fps.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.fpsColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Text.of("Color of the text"))
                .setSaveConsumer(val -> config.fpsColor = val)
                .build());
        fps.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow"), config.fpsRainbow)
                .setDefaultValue(false)
                .setTooltip(Text.of("Enable rainbow text effect"))
                .setSaveConsumer(val -> config.fpsRainbow = val)
                .build());
        fps.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.fpsShowBackground)
                .setDefaultValue(false)
                .setTooltip(Text.of("Show background box"))
                .setSaveConsumer(val -> config.fpsShowBackground = val)
                .build());
         fps.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.fpsBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Text.of("Color of the background"))
                .setSaveConsumer(val -> config.fpsBackgroundColor = val)
                .build());
        fps.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.fpsBackgroundOpacity, 0, 255)
                .setDefaultValue(64)
                .setTooltip(Text.of("Transparency of the background"))
                .setSaveConsumer(val -> config.fpsBackgroundOpacity = val)
                .build());
        
        // --- KEYSTROKES ---
        ConfigCategory keys = builder.getOrCreateCategory(Text.of("Keystrokes"));
        keys.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Keystrokes"), config.showKeystrokes)
                .setDefaultValue(true)
                .setTooltip(Text.of("Toggle Keystrokes visibility"))
                .setSaveConsumer(val -> config.showKeystrokes = val)
                .build());
        keys.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.keystrokesPosition)
                .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                .setTooltip(Text.of("Anchor position for Keystrokes"))
                .setSaveConsumer(val -> config.keystrokesPosition = val)
                .build());
        keys.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.keystrokesXOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.keystrokesXOffset = val)
                .build());
        keys.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.keystrokesYOffset)
                .setDefaultValue(40)
                .setTooltip(Text.of("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.keystrokesYOffset = val)
                .build());
        keys.addEntry(entryBuilder.startEnumSelector(Text.of("Mode"), SimpleCPSConfig.KeystrokesMode.class, config.keystrokesMode)
                .setDefaultValue(SimpleCPSConfig.KeystrokesMode.LETTERS)
                .setTooltip(Text.of("Display style (WASD, Arrows, Custom)"))
                .setSaveConsumer(val -> config.keystrokesMode = val)
                .build());
        keys.addEntry(entryBuilder.startStrField(Text.of("Custom W"), config.customW)
                .setDefaultValue("W")
                .setTooltip(Text.of("Text for Forward key (Custom Mode)"))
                .setSaveConsumer(val -> config.customW = val)
                .build());
        keys.addEntry(entryBuilder.startStrField(Text.of("Custom A"), config.customA)
                .setDefaultValue("A")
                .setTooltip(Text.of("Text for Left key (Custom Mode)"))
                .setSaveConsumer(val -> config.customA = val)
                .build());
        keys.addEntry(entryBuilder.startStrField(Text.of("Custom S"), config.customS)
                .setDefaultValue("S")
                .setTooltip(Text.of("Text for Back key (Custom Mode)"))
                .setSaveConsumer(val -> config.customS = val)
                .build());
        keys.addEntry(entryBuilder.startStrField(Text.of("Custom D"), config.customD)
                .setDefaultValue("D")
                .setTooltip(Text.of("Text for Right key (Custom Mode)"))
                .setSaveConsumer(val -> config.customD = val)
                .build());
        keys.addEntry(entryBuilder.startStrField(Text.of("Custom Space"), config.customSpace)
                .setDefaultValue("----")
                .setTooltip(Text.of("Text for Jump key (Custom Mode)"))
                .setSaveConsumer(val -> config.customSpace = val)
                .build());
         keys.addEntry(entryBuilder.startIntSlider(Text.of("Scale %"), config.keystrokesScale, 50, 300)
                .setDefaultValue(80)
                .setTooltip(Text.of("Size of the keystrokes"))
                .setSaveConsumer(val -> config.keystrokesScale = val)
                .build());
        keys.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.keystrokesColor)
                 .setDefaultValue(0xFFFFFF)
                 .setTooltip(Text.of("Color of the key text"))
                 .setSaveConsumer(val -> config.keystrokesColor = val)
                 .build());
        keys.addEntry(entryBuilder.startColorField(Text.of("Pressed Color"), config.keystrokesPressedColor)
                 .setDefaultValue(0x00FF00)
                 .setTooltip(Text.of("Color when key is pressed"))
                 .setSaveConsumer(val -> config.keystrokesPressedColor = val)
                 .build());
        keys.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow"), config.keystrokesRainbow)
                .setDefaultValue(false)
                .setTooltip(Text.of("Enable rainbow effect"))
                .setSaveConsumer(val -> config.keystrokesRainbow = val)
                .build());
        keys.addEntry(entryBuilder.startEnumSelector(Text.of("Rainbow Target"), SimpleCPSConfig.RainbowTarget.class, config.keystrokesRainbowTarget)
                .setDefaultValue(SimpleCPSConfig.RainbowTarget.TEXT)
                .setTooltip(Text.of("Apply rainbow to text or background"))
                .setSaveConsumer(val -> config.keystrokesRainbowTarget = val)
                .build());
        keys.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.keystrokesBackgroundColor)
                 .setDefaultValue(0x000000)
                 .setTooltip(Text.of("Color of key backgrounds"))
                 .setSaveConsumer(val -> config.keystrokesBackgroundColor = val)
                 .build());
        keys.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.keystrokesBackgroundOpacity, 0, 255)
                 .setDefaultValue(128)
                 .setTooltip(Text.of("Transparency of key backgrounds"))
                 .setSaveConsumer(val -> config.keystrokesBackgroundOpacity = val)
                 .build());
        
        // --- COMBO ---
        ConfigCategory combo = builder.getOrCreateCategory(Text.of("Combo (Beta)"));
        combo.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Combo"), config.showCombo)
                .setDefaultValue(false)
                .setTooltip(Text.of("Toggle Combo counter visibility"))
                .setSaveConsumer(val -> config.showCombo = val)
                .build());
        combo.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.comboPosition)
                .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                .setTooltip(Text.of("Anchor position for Combo"))
                .setSaveConsumer(val -> config.comboPosition = val)
                .build());
        combo.addEntry(entryBuilder.startEnumSelector(Text.of("Combat Mode"), SimpleCPSConfig.CombatMode.class, config.combatMode)
                .setDefaultValue(SimpleCPSConfig.CombatMode.MODERN)
                .setTooltip(Text.of("Combat mechanics for combo detection"))
                .setSaveConsumer(val -> config.combatMode = val)
                .build());
        combo.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.comboXOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.comboXOffset = val)
                .build());
        combo.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.comboYOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.comboYOffset = val)
                .build());
        combo.addEntry(entryBuilder.startIntSlider(Text.of("Scale %"), config.comboScale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Text.of("Size of the Combo display"))
                .setSaveConsumer(val -> config.comboScale = val)
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Text.of("Reset on Any Damage"), config.comboResetOnAnyDamage)
                .setDefaultValue(false)
                .setTooltip(Text.of("Reset combo when taking any damage"))
                .setSaveConsumer(val -> config.comboResetOnAnyDamage = val)
                .build());
        combo.addEntry(entryBuilder.startIntSlider(Text.of("Timeout (s)"), (int)config.comboTimeout, 1, 10)
                .setDefaultValue(2)
                .setTooltip(Text.of("Seconds before combo resets"))
                .setSaveConsumer(val -> config.comboTimeout = val)
                .build());
        combo.addEntry(entryBuilder.startStrField(Text.of("Suffix Text"), config.comboText)
                .setDefaultValue("Combo")
                .setTooltip(Text.of("Text displayed after combo count"))
                .setSaveConsumer(val -> config.comboText = val)
                .build());
        combo.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.comboColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Text.of("Color of the text"))
                .setSaveConsumer(val -> config.comboColor = val)
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow"), config.comboRainbow)
                .setDefaultValue(false)
                .setTooltip(Text.of("Enable rainbow text effect"))
                .setSaveConsumer(val -> config.comboRainbow = val)
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.comboShowBackground)
                .setDefaultValue(false)
                .setTooltip(Text.of("Show background box"))
                .setSaveConsumer(val -> config.comboShowBackground = val)
                .build());
         combo.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.comboBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Text.of("Color of the background"))
                .setSaveConsumer(val -> config.comboBackgroundColor = val)
                .build());
        combo.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.comboBackgroundOpacity, 0, 255)
                .setDefaultValue(128)
                .setTooltip(Text.of("Transparency of the background"))
                .setSaveConsumer(val -> config.comboBackgroundOpacity = val)
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Text.of("Hide When Inactive"), config.comboHideWhenInactive)
                .setDefaultValue(false)
                .setTooltip(Text.of("Hide combo display when combo is 0"))
                .setSaveConsumer(val -> config.comboHideWhenInactive = val)
                .build());

        // --- REACH DISPLAY ---
        ConfigCategory reach = builder.getOrCreateCategory(Text.of("Reach Display"));
        reach.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Reach Display"), config.showReach)
                .setDefaultValue(false)
                .setTooltip(Text.of("Toggle Reach Display visibility"))
                .setSaveConsumer(val -> config.showReach = val)
                .build());
        reach.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.reachPosition)
                .setDefaultValue(SimpleCPSConfig.Position.CENTER)
                .setTooltip(Text.of("Anchor position for Reach Display"))
                .setSaveConsumer(val -> config.reachPosition = val)
                .build());
        reach.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.reachXOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Horizontal offset from anchor"))
                .setSaveConsumer(val -> config.reachXOffset = val)
                .build());
        reach.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.reachYOffset)
                .setDefaultValue(0)
                .setTooltip(Text.of("Vertical offset from anchor"))
                .setSaveConsumer(val -> config.reachYOffset = val)
                .build());
        reach.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.reachColor)
                .setDefaultValue(0xFFFFFF)
                .setTooltip(Text.of("Color of the text"))
                .setSaveConsumer(val -> config.reachColor = val)
                .build());
        reach.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow"), config.reachRainbow)
                .setDefaultValue(false)
                .setTooltip(Text.of("Enable rainbow text effect"))
                .setSaveConsumer(val -> config.reachRainbow = val)
                .build());
        reach.addEntry(entryBuilder.startIntSlider(Text.of("Scale %"), config.reachScale, 50, 300)
                .setDefaultValue(100)
                .setTooltip(Text.of("Size of the Reach display"))
                .setSaveConsumer(val -> config.reachScale = val)
                .build());
        reach.addEntry(entryBuilder.startIntSlider(Text.of("Timeout (s)"), (int)config.reachTimeout, 1, 10)
                .setDefaultValue(3)
                .setTooltip(Text.of("Seconds before display disappears"))
                .setSaveConsumer(val -> config.reachTimeout = val)
                .build());
        reach.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.reachShowBackground)
                .setDefaultValue(true)
                .setTooltip(Text.of("Show background box"))
                .setSaveConsumer(val -> config.reachShowBackground = val)
                .build());
        reach.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.reachBackgroundColor)
                .setDefaultValue(0x000000)
                .setTooltip(Text.of("Color of the background"))
                .setSaveConsumer(val -> config.reachBackgroundColor = val)
                .build());
        reach.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.reachBackgroundOpacity, 0, 255)
                .setDefaultValue(128)
                .setTooltip(Text.of("Transparency of the background"))
                .setSaveConsumer(val -> config.reachBackgroundOpacity = val)
                .build());
        reach.addEntry(entryBuilder.startBooleanToggle(Text.of("Only Players"), config.reachOnlyPlayers)
                .setDefaultValue(true)
                .setTooltip(Text.of("Only show reach when hitting players"))
                .setSaveConsumer(val -> config.reachOnlyPlayers = val)
                .build());
        reach.addEntry(entryBuilder.startBooleanToggle(Text.of("Always Show"), config.reachAlwaysShow)
                .setDefaultValue(false)
                .setTooltip(Text.of("Show reach display even when inactive"))
                .setSaveConsumer(val -> config.reachAlwaysShow = val)
                .build());
        reach.addEntry(entryBuilder.startStrField(Text.of("No Hit Text"), config.reachNoHitText)
                .setDefaultValue("No Hit")
                .setTooltip(Text.of("Text to show when no hit has occurred"))
                .setSaveConsumer(val -> config.reachNoHitText = val)
                .build());

        // --- NEW LAYOUT TAB ---
        ConfigCategory layout = builder.getOrCreateCategory(Text.of("Layout"));
        layout.addEntry(entryBuilder.startTextDescription(Text.of("To open the Drag & Drop Editor: Enable the toggle below and click 'Save & Quit'."))
                .build());
        
        layout.addEntry(entryBuilder.startBooleanToggle(Text.of("Open Drag & Drop Editor"), false)
                .setDefaultValue(false)
                .setTooltip(Text.of("Enable this and Save to open the editor directly."))
                .setSaveConsumer(val -> {
                    if (val) {
                         // We need to schedule this because the screen is currently closing
                        MinecraftClient.getInstance().execute(() -> 
                            MinecraftClient.getInstance().setScreen(new HudEditorScreen(parent))
                        );
                    }
                })
                .build());

        return builder.build();
    }
}
