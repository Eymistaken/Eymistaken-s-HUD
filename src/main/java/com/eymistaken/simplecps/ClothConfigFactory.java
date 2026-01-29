package com.eymistaken.simplecps;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
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
                .setSaveConsumer(val -> config.enabled = val)
                .build());
        cps.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.position)
                .setDefaultValue(SimpleCPSConfig.Position.BOTTOM_RIGHT)
                .setSaveConsumer(val -> config.position = val)
                .build());
        cps.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.xOffset)
                .setDefaultValue(0)
                .setSaveConsumer(val -> config.xOffset = val)
                .build());
        cps.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.yOffset)
                .setDefaultValue(0)
                .setSaveConsumer(val -> config.yOffset = val)
                .build());
        cps.addEntry(entryBuilder.startBooleanToggle(Text.of("Right Click CPS"), config.rightClickCps)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.rightClickCps = val)
                .build());
        cps.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow"), config.rainbow)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.rainbow = val)
                .build());
        cps.addEntry(entryBuilder.startIntSlider(Text.of("Scale %"), config.scale, 50, 300)
                .setDefaultValue(100)
                .setSaveConsumer(val -> config.scale = val)
                .build());
        cps.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.textColor)
                .setDefaultValue(0xFFFFFF)
                .setSaveConsumer(val -> config.textColor = val)
                .build());
        cps.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.cpsShowBackground)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.cpsShowBackground = val)
                .build());
        cps.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.cpsBackgroundColor)
                .setDefaultValue(0x000000)
                .setSaveConsumer(val -> config.cpsBackgroundColor = val)
                .build());
        cps.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.cpsBackgroundOpacity, 0, 255)
                .setDefaultValue(64)
                .setSaveConsumer(val -> config.cpsBackgroundOpacity = val)
                .build());

        // --- PING ---
        ConfigCategory ping = builder.getOrCreateCategory(Text.of("Ping"));
        ping.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Ping"), config.showPing)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.showPing = val)
                .build());
        ping.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.pingPosition)
                .setDefaultValue(SimpleCPSConfig.Position.BOTTOM_RIGHT)
                .setSaveConsumer(val -> config.pingPosition = val)
                .build());
        ping.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.pingXOffset)
                .setDefaultValue(0)
                .setSaveConsumer(val -> config.pingXOffset = val)
                .build());
        ping.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.pingYOffset)
                .setDefaultValue(-10)
                .setSaveConsumer(val -> config.pingYOffset = val)
                .build());
        ping.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.pingColor)
                .setDefaultValue(0xFFFFFF)
                .setSaveConsumer(val -> config.pingColor = val)
                .build());
        ping.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.pingShowBackground)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.pingShowBackground = val)
                .build());
        ping.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.pingBackgroundColor)
                .setDefaultValue(0x000000)
                .setSaveConsumer(val -> config.pingBackgroundColor = val)
                .build());
        ping.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.pingBackgroundOpacity, 0, 255)
                .setDefaultValue(64)
                .setSaveConsumer(val -> config.pingBackgroundOpacity = val)
                .build());

        // --- FPS ---
        ConfigCategory fps = builder.getOrCreateCategory(Text.of("FPS"));
        fps.addEntry(entryBuilder.startBooleanToggle(Text.of("Show FPS"), config.showFps)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.showFps = val)
                .build());
        fps.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.fpsPosition)
                .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                .setSaveConsumer(val -> config.fpsPosition = val)
                .build());
        fps.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.fpsXOffset)
                .setDefaultValue(0)
                .setSaveConsumer(val -> config.fpsXOffset = val)
                .build());
        fps.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.fpsYOffset)
                .setDefaultValue(0)
                .setSaveConsumer(val -> config.fpsYOffset = val)
                .build());
        
        // --- KEYSTROKES ---
        ConfigCategory keys = builder.getOrCreateCategory(Text.of("Keystrokes"));
        keys.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Keystrokes"), config.showKeystrokes)
                .setDefaultValue(true)
                .setSaveConsumer(val -> config.showKeystrokes = val)
                .build());
        keys.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.keystrokesPosition)
                .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                .setSaveConsumer(val -> config.keystrokesPosition = val)
                .build());
        keys.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.keystrokesXOffset)
                .setDefaultValue(0)
                .setSaveConsumer(val -> config.keystrokesXOffset = val)
                .build());
        keys.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.keystrokesYOffset)
                .setDefaultValue(40)
                .setSaveConsumer(val -> config.keystrokesYOffset = val)
                .build());
        keys.addEntry(entryBuilder.startEnumSelector(Text.of("Mode"), SimpleCPSConfig.KeystrokesMode.class, config.keystrokesMode)
                .setDefaultValue(SimpleCPSConfig.KeystrokesMode.LETTERS)
                .setSaveConsumer(val -> config.keystrokesMode = val)
                .build());
        keys.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.keystrokesColor)
                 .setDefaultValue(0xFFFFFF)
                 .setSaveConsumer(val -> config.keystrokesColor = val)
                 .build());
        keys.addEntry(entryBuilder.startColorField(Text.of("Pressed Color"), config.keystrokesPressedColor)
                 .setDefaultValue(0x000000)
                 .setSaveConsumer(val -> config.keystrokesPressedColor = val)
                 .build());
        keys.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.keystrokesBackgroundColor)
                 .setDefaultValue(0x000000)
                 .setSaveConsumer(val -> config.keystrokesBackgroundColor = val)
                 .build());
        
        // --- COMBO ---
        ConfigCategory combo = builder.getOrCreateCategory(Text.of("Combo (Beta)"));
        combo.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Combo"), config.showCombo)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.showCombo = val)
                .build());
        combo.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.comboPosition)
                .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                .setSaveConsumer(val -> config.comboPosition = val)
                .build());
        combo.addEntry(entryBuilder.startEnumSelector(Text.of("Combat Mode"), SimpleCPSConfig.CombatMode.class, config.combatMode)
                .setDefaultValue(SimpleCPSConfig.CombatMode.MODERN)
                .setSaveConsumer(val -> config.combatMode = val)
                .build());
        combo.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.comboXOffset)
                .setDefaultValue(0)
                .setSaveConsumer(val -> config.comboXOffset = val)
                .build());
        combo.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.comboYOffset)
                .setDefaultValue(0)
                .setSaveConsumer(val -> config.comboYOffset = val)
                .build());
        combo.addEntry(entryBuilder.startBooleanToggle(Text.of("Reset on Any Damage"), config.comboResetOnAnyDamage)
                .setDefaultValue(false)
                .setSaveConsumer(val -> config.comboResetOnAnyDamage = val)
                .build());
        combo.addEntry(entryBuilder.startIntSlider(Text.of("Timeout (s)"), (int)config.comboTimeout, 1, 10)
                .setDefaultValue(2)
                .setSaveConsumer(val -> config.comboTimeout = val)
                .build());

        return builder.build();
    }
}
