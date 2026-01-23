package com.eymistaken.simplecps;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            SimpleCPSConfig.load();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.of("SimpleCPS Settings"));

            ConfigCategory general = builder.getOrCreateCategory(Text.of("General"));
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Position
            general.addEntry(entryBuilder.startEnumSelector(Text.of("Position"), SimpleCPSConfig.Anchor.class, SimpleCPSConfig.anchor)
                    .setDefaultValue(SimpleCPSConfig.Anchor.TOP_LEFT)
                    .setEnumNameProvider(value -> {
                        if (value == SimpleCPSConfig.Anchor.TOP_LEFT) return Text.of("Top Left");
                        if (value == SimpleCPSConfig.Anchor.TOP_RIGHT) return Text.of("Top Right");
                        if (value == SimpleCPSConfig.Anchor.TOP_CENTER) return Text.of("Top Center");
                        if (value == SimpleCPSConfig.Anchor.BOTTOM_LEFT) return Text.of("Bottom Left");
                        if (value == SimpleCPSConfig.Anchor.BOTTOM_RIGHT) return Text.of("Bottom Right");
                        if (value == SimpleCPSConfig.Anchor.BOTTOM_CENTER) return Text.of("Bottom Center");
                        return Text.of(value.toString());
                    })
                    .setSaveConsumer(newValue -> SimpleCPSConfig.anchor = newValue)
                    .build());
            
            // Scale
            general.addEntry(entryBuilder.startIntSlider(Text.of("Scale (%)"), (int)(SimpleCPSConfig.scale * 100), 50, 300)
                    .setDefaultValue(100)
                    .setSaveConsumer(newValue -> SimpleCPSConfig.scale = newValue / 100f)
                    .setTextGetter(value -> Text.of(value + "%"))
                    .build());

            // Color Mode
            general.addEntry(entryBuilder.startEnumSelector(Text.of("Color Mode"), SimpleCPSConfig.ColorMode.class, SimpleCPSConfig.colorMode)
                    .setDefaultValue(SimpleCPSConfig.ColorMode.WHITE)
                    .setEnumNameProvider(value -> {
                        if (value == SimpleCPSConfig.ColorMode.WHITE) return Text.of("White");
                        if (value == SimpleCPSConfig.ColorMode.RED) return Text.of("Red");
                        if (value == SimpleCPSConfig.ColorMode.GREEN) return Text.of("Green");
                        if (value == SimpleCPSConfig.ColorMode.BLUE) return Text.of("Blue");
                        if (value == SimpleCPSConfig.ColorMode.GOLD) return Text.of("Gold");
                        if (value == SimpleCPSConfig.ColorMode.RAINBOW) return Text.of("§cR§6a§ei§an§bb§9o§dw"); // Gökkuşağı Yazısı
                        if (value == SimpleCPSConfig.ColorMode.CUSTOM) return Text.of("Custom (Hex)");
                        return Text.of(value.toString());
                    })
                    .setSaveConsumer(newValue -> SimpleCPSConfig.colorMode = newValue)
                    .build());

            // Custom Color
            general.addEntry(entryBuilder.startIntField(Text.of("Custom Color (Hex)"), SimpleCPSConfig.color)
                    .setDefaultValue(0xFFFFFFFF)
                    .setTooltip(Text.of("Example: 0xFF0000 (Red), 0xFFFFFFFF (White)"))
                    .setSaveConsumer(newValue -> SimpleCPSConfig.color = newValue)
                    .build());

            // Offsets
            general.addEntry(entryBuilder.startIntField(Text.of("Horizontal Offset (X)"), SimpleCPSConfig.x)
                    .setDefaultValue(4)
                    .setSaveConsumer(newValue -> SimpleCPSConfig.x = newValue)
                    .build());

            general.addEntry(entryBuilder.startIntField(Text.of("Vertical Offset (Y)"), SimpleCPSConfig.y)
                    .setDefaultValue(4)
                    .setSaveConsumer(newValue -> SimpleCPSConfig.y = newValue)
                    .build());

            // Right Click Toggle
            general.addEntry(entryBuilder.startBooleanToggle(Text.of("Show Right Click"), SimpleCPSConfig.showRightClick)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> SimpleCPSConfig.showRightClick = newValue)
                    .build());

            builder.setSavingRunnable(SimpleCPSConfig::save);

            return builder.build();
        };
    }
}