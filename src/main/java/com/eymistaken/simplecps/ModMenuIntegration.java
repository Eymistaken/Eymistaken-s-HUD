package com.eymistaken.simplecps;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            SimpleCPSConfig config = AutoConfig.getConfigHolder(SimpleCPSConfig.class).getConfig();
            
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.of("Eymistaken's HUD Settings"))
                    .setSavingRunnable(() -> {
                        AutoConfig.getConfigHolder(SimpleCPSConfig.class).save();
                    });

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();
            
            // Renk İpucu Metni
            Text colorTooltip = Text.of("Black: 000000, White: FFFFFF, Red: FF0000, Blue: 0000FF");

            // ---------------- KATEGORİ 1: CPS (Ana Özellik) ----------------
            ConfigCategory general = builder.getOrCreateCategory(Text.of("CPS"));

            general.addEntry(entryBuilder.startBooleanToggle(Text.of("Enable CPS"), config.enabled)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> config.enabled = newValue)
                    .build());

            general.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.position)
                    .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                    .setSaveConsumer(newValue -> config.position = newValue)
                    .build());

            general.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.xOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.xOffset = newValue)
                    .build());

            general.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.yOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.yOffset = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Right Click"), config.rightClickCps)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> config.rightClickCps = newValue)
                    .build());
            
            general.addEntry(entryBuilder.startIntSlider(Text.of("Scale (%)"), config.scale, 50, 300)
                    .setDefaultValue(100)
                    .setSaveConsumer(newValue -> config.scale = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow Mode"), config.rainbow)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.rainbow = newValue)
                    .build());

            general.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.textColor)
                    .setDefaultValue(0xFFFFFF)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.textColor = newValue)
                    .build());

            // CPS Arkaplan
            general.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.cpsShowBackground)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.cpsShowBackground = newValue)
                    .build());
            general.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.cpsBackgroundColor)
                    .setDefaultValue(0x000000)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.cpsBackgroundColor = newValue)
                    .build());
            general.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.cpsBackgroundOpacity, 0, 255)
                    .setDefaultValue(128)
                    .setTooltip(Text.of("0 = Invisible, 255 = Solid"))
                    .setSaveConsumer(newValue -> config.cpsBackgroundOpacity = newValue)
                    .build());


            // ---------------- KATEGORİ 2: PING (Beta) ----------------
            // İSİM GÜNCELLENDİ: Ping -> Ping (Beta)
            ConfigCategory pingCategory = builder.getOrCreateCategory(Text.of("Ping (Beta)"));

            pingCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Ping"), config.showPing)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.showPing = newValue)
                    .build());

            pingCategory.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.pingPosition)
                    .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                    .setSaveConsumer(newValue -> config.pingPosition = newValue)
                    .build());

            pingCategory.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.pingXOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.pingXOffset = newValue)
                    .build());

            pingCategory.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.pingYOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.pingYOffset = newValue)
                    .build());

            pingCategory.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.pingColor)
                    .setDefaultValue(0xFFFFFF)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.pingColor = newValue)
                    .build());

            // Ping Arkaplan
            pingCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.pingShowBackground)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.pingShowBackground = newValue)
                    .build());
            pingCategory.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.pingBackgroundColor)
                    .setDefaultValue(0x000000)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.pingBackgroundColor = newValue)
                    .build());
            pingCategory.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.pingBackgroundOpacity, 0, 255)
                    .setDefaultValue(128)
                    .setSaveConsumer(newValue -> config.pingBackgroundOpacity = newValue)
                    .build());


            // ---------------- KATEGORİ 3: FPS (Beta) ----------------
            // İSİM GÜNCELLENDİ: FPS -> FPS (Beta)
            ConfigCategory fpsCategory = builder.getOrCreateCategory(Text.of("FPS (Beta)"));

            fpsCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Show FPS"), config.showFps)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.showFps = newValue)
                    .build());

            fpsCategory.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.fpsPosition)
                    .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                    .setSaveConsumer(newValue -> config.fpsPosition = newValue)
                    .build());

            fpsCategory.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.fpsXOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.fpsXOffset = newValue)
                    .build());

            fpsCategory.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.fpsYOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.fpsYOffset = newValue)
                    .build());
            
            fpsCategory.addEntry(entryBuilder.startIntSlider(Text.of("Scale (%)"), config.fpsScale, 50, 300)
                    .setDefaultValue(100)
                    .setSaveConsumer(newValue -> config.fpsScale = newValue)
                    .build());

            fpsCategory.addEntry(entryBuilder.startStrField(Text.of("FPS Label"), config.fpsText)
                    .setDefaultValue("FPS")
                    .setTooltip(Text.of("Text to show after the number (e.g. '144 FPS')"))
                    .setSaveConsumer(newValue -> config.fpsText = newValue)
                    .build());

            fpsCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow Mode"), config.fpsRainbow)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.fpsRainbow = newValue)
                    .build());

            fpsCategory.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.fpsColor)
                    .setDefaultValue(0xFFFFFF)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.fpsColor = newValue)
                    .build());

            // FPS Arkaplan
            fpsCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.fpsShowBackground)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.fpsShowBackground = newValue)
                    .build());
            fpsCategory.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.fpsBackgroundColor)
                    .setDefaultValue(0x000000)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.fpsBackgroundColor = newValue)
                    .build());
            fpsCategory.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.fpsBackgroundOpacity, 0, 255)
                    .setDefaultValue(128)
                    .setSaveConsumer(newValue -> config.fpsBackgroundOpacity = newValue)
                    .build());


            // ---------------- KATEGORİ 4: KEYSTROKES (Beta) ----------------
            // İSİM GÜNCELLENDİ: Keystrokes -> Keystrokes (Beta)
            ConfigCategory keysCategory = builder.getOrCreateCategory(Text.of("Keystrokes (Beta)"));

            keysCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Keystrokes"), config.showKeystrokes)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.showKeystrokes = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.keystrokesPosition)
                    .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                    .setSaveConsumer(newValue -> config.keystrokesPosition = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.keystrokesXOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.keystrokesXOffset = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.keystrokesYOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.keystrokesYOffset = newValue)
                    .build());
            
            keysCategory.addEntry(entryBuilder.startIntSlider(Text.of("Scale (%)"), config.keystrokesScale, 50, 200)
                    .setDefaultValue(80)
                    .setSaveConsumer(newValue -> config.keystrokesScale = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startEnumSelector(Text.of("Display Mode"), SimpleCPSConfig.KeystrokesMode.class, config.keystrokesMode)
                    .setDefaultValue(SimpleCPSConfig.KeystrokesMode.LETTERS)
                    .setSaveConsumer(newValue -> config.keystrokesMode = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow Effect"), config.keystrokesRainbow)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.keystrokesRainbow = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startEnumSelector(Text.of("Rainbow Target"), SimpleCPSConfig.RainbowTarget.class, config.keystrokesRainbowTarget)
                    .setDefaultValue(SimpleCPSConfig.RainbowTarget.TEXT)
                    .setSaveConsumer(newValue -> config.keystrokesRainbowTarget = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom W"), config.customW).setDefaultValue("W").setSaveConsumer(newValue -> config.customW = newValue).build());
            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom A"), config.customA).setDefaultValue("A").setSaveConsumer(newValue -> config.customA = newValue).build());
            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom S"), config.customS).setDefaultValue("S").setSaveConsumer(newValue -> config.customS = newValue).build());
            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom D"), config.customD).setDefaultValue("D").setSaveConsumer(newValue -> config.customD = newValue).build());
            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom Space"), config.customSpace).setDefaultValue("----").setSaveConsumer(newValue -> config.customSpace = newValue).build());

            keysCategory.addEntry(entryBuilder.startColorField(Text.of("Key Text Color"), config.keystrokesColor)
                    .setDefaultValue(0xFFFFFF)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.keystrokesColor = newValue)
                    .build());
            
            keysCategory.addEntry(entryBuilder.startColorField(Text.of("Pressed Color"), config.keystrokesPressedColor)
                    .setDefaultValue(0x00FF00)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.keystrokesPressedColor = newValue)
                    .build());

            // Keystrokes Arkaplan
            keysCategory.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.keystrokesBackgroundColor)
                    .setDefaultValue(0x000000)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.keystrokesBackgroundColor = newValue)
                    .build());
            keysCategory.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.keystrokesBackgroundOpacity, 0, 255)
                    .setDefaultValue(128)
                    .setSaveConsumer(newValue -> config.keystrokesBackgroundOpacity = newValue)
                    .build());

            // ---------------- KATEGORİ 5: COMBO (Beta) ----------------
            ConfigCategory comboCategory = builder.getOrCreateCategory(Text.of("Combo (Beta)"));

            comboCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Combo"), config.showCombo)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.showCombo = newValue)
                    .build());

            comboCategory.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Position.class, config.comboPosition)
                    .setDefaultValue(SimpleCPSConfig.Position.TOP_LEFT)
                    .setSaveConsumer(newValue -> config.comboPosition = newValue)
                    .build());

            comboCategory.addEntry(entryBuilder.startIntField(Text.of("X Offset"), config.comboXOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.comboXOffset = newValue)
                    .build());

            comboCategory.addEntry(entryBuilder.startIntField(Text.of("Y Offset"), config.comboYOffset)
                    .setDefaultValue(0)
                    .setSaveConsumer(newValue -> config.comboYOffset = newValue)
                    .build());
            
            comboCategory.addEntry(entryBuilder.startIntSlider(Text.of("Scale (%)"), config.comboScale, 50, 300)
                    .setDefaultValue(100)
                    .setSaveConsumer(newValue -> config.comboScale = newValue)
                    .build());

            comboCategory.addEntry(entryBuilder.startStrField(Text.of("Label Text"), config.comboText)
                    .setDefaultValue("Combo")
                    .setSaveConsumer(newValue -> config.comboText = newValue)
                    .build());

            comboCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow Mode"), config.comboRainbow)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.comboRainbow = newValue)
                    .build());

            comboCategory.addEntry(entryBuilder.startColorField(Text.of("Text Color"), config.comboColor)
                    .setDefaultValue(0xFFFFFF)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.comboColor = newValue)
                    .build());

            // Combo Background
            comboCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Background"), config.comboShowBackground)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.comboShowBackground = newValue)
                    .build());
            comboCategory.addEntry(entryBuilder.startColorField(Text.of("Background Color"), config.comboBackgroundColor)
                    .setDefaultValue(0x000000)
                    .setTooltip(colorTooltip)
                    .setSaveConsumer(newValue -> config.comboBackgroundColor = newValue)
                    .build());
            comboCategory.addEntry(entryBuilder.startIntSlider(Text.of("Background Opacity"), config.comboBackgroundOpacity, 0, 255)
                    .setDefaultValue(128)
                    .setSaveConsumer(newValue -> config.comboBackgroundOpacity = newValue)
                    .build());

            // Logic Settings
            comboCategory.addEntry(entryBuilder.startEnumSelector(Text.of("Combat Mode"), SimpleCPSConfig.CombatMode.class, config.combatMode)
                    .setDefaultValue(SimpleCPSConfig.CombatMode.MODERN)
                    .setTooltip(Text.of("Modern: Requires attack cooldown.\nClassic: Spam clicking allowed."))
                    .setSaveConsumer(newValue -> config.combatMode = newValue)
                    .build());

            comboCategory.addEntry(entryBuilder.startIntSlider(Text.of("Combo Timeout (s)"), (int)(config.comboTimeout * 10), 5, 100)
                    .setDefaultValue(30)
                    .setTextGetter(value -> Text.of(String.format("%.1fs", value / 10.0)))
                    .setSaveConsumer(newValue -> config.comboTimeout = newValue / 10.0)
                    .build());

            comboCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Reset on Any Damage"), config.comboResetOnAnyDamage)
                    .setDefaultValue(true)
                    .setTooltip(Text.of("If true, ANY damage resets combo. If false, only target damage resets it."))
                    .setSaveConsumer(newValue -> config.comboResetOnAnyDamage = newValue)
                    .build());

            comboCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Continue on Switch"), config.comboContinueOnSwitch)
                    .setDefaultValue(true)
                    .setTooltip(Text.of("If true, hitting a NEW player keeps the combo."))
                    .setSaveConsumer(newValue -> config.comboContinueOnSwitch = newValue)
                    .build());

            comboCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Only Show While Comboing"), config.comboHideWhenInactive)
                    .setDefaultValue(false)
                    .setTooltip(Text.of("If true, hides the counter when combo is 0."))
                    .setSaveConsumer(newValue -> config.comboHideWhenInactive = newValue)
                    .build());

            return builder.build();
        };
    }
}