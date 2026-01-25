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
                    .setTitle(Text.of("SimpleCPS Settings"))
                    .setSavingRunnable(() -> {
                        AutoConfig.getConfigHolder(SimpleCPSConfig.class).save();
                    });

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // ---------------- KATEGORİ 1: CPS AYARLARI ----------------
            ConfigCategory general = builder.getOrCreateCategory(Text.of("General"));

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
                    .setSaveConsumer(newValue -> config.textColor = newValue)
                    .build());

            // ---------------- KATEGORİ 2: PING AYARLARI ----------------
            ConfigCategory pingCategory = builder.getOrCreateCategory(Text.of("Ping (Beta)"));

            pingCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Ping"), config.showPing)
                    .setDefaultValue(false)
                    .setTooltip(Text.of("Displays your latency in ms."))
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
                    .setSaveConsumer(newValue -> config.pingColor = newValue)
                    .build());

            // ---------------- KATEGORİ 3: KEYSTROKES ----------------
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

            // --- YENİ KEYSTROKES AYARLARI ---
            keysCategory.addEntry(entryBuilder.startEnumSelector(Text.of("Display Mode"), SimpleCPSConfig.KeystrokesMode.class, config.keystrokesMode)
                    .setDefaultValue(SimpleCPSConfig.KeystrokesMode.LETTERS)
                    .setTooltip(Text.of("Letters (WASD), Arrows, or Custom Text."))
                    .setSaveConsumer(newValue -> config.keystrokesMode = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startBooleanToggle(Text.of("Rainbow Effect"), config.keystrokesRainbow)
                    .setDefaultValue(false)
                    .setSaveConsumer(newValue -> config.keystrokesRainbow = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startEnumSelector(Text.of("Rainbow Target"), SimpleCPSConfig.RainbowTarget.class, config.keystrokesRainbowTarget)
                    .setDefaultValue(SimpleCPSConfig.RainbowTarget.TEXT)
                    .setTooltip(Text.of("Apply rainbow to Text or Background."))
                    .setSaveConsumer(newValue -> config.keystrokesRainbowTarget = newValue)
                    .build());

            // Custom Text Fields
            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom W / Up"), config.customW)
                    .setDefaultValue("W")
                    .setSaveConsumer(newValue -> config.customW = newValue)
                    .build());
            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom A / Left"), config.customA)
                    .setDefaultValue("A")
                    .setSaveConsumer(newValue -> config.customA = newValue)
                    .build());
            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom S / Down"), config.customS)
                    .setDefaultValue("S")
                    .setSaveConsumer(newValue -> config.customS = newValue)
                    .build());
            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom D / Right"), config.customD)
                    .setDefaultValue("D")
                    .setSaveConsumer(newValue -> config.customD = newValue)
                    .build());
            keysCategory.addEntry(entryBuilder.startStrField(Text.of("Custom Space"), config.customSpace)
                    .setDefaultValue("----")
                    .setSaveConsumer(newValue -> config.customSpace = newValue)
                    .build());

            keysCategory.addEntry(entryBuilder.startColorField(Text.of("Key Color"), config.keystrokesColor)
                    .setDefaultValue(0xFFFFFF)
                    .setSaveConsumer(newValue -> config.keystrokesColor = newValue)
                    .build());
            
            keysCategory.addEntry(entryBuilder.startColorField(Text.of("Pressed Color"), config.keystrokesPressedColor)
                    .setDefaultValue(0x00FF00)
                    .setSaveConsumer(newValue -> config.keystrokesPressedColor = newValue)
                    .build());

            return builder.build();
        };
    }
}