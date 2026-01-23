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
                    .setTitle(Text.of("SimpleCPS Ayarları"));

            ConfigCategory general = builder.getOrCreateCategory(Text.of("Genel"));
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Konum Seçici
            general.addEntry(entryBuilder.startEnumSelector(Text.of("Konum"), SimpleCPSConfig.Anchor.class, SimpleCPSConfig.anchor)
                    .setDefaultValue(SimpleCPSConfig.Anchor.TOP_LEFT)
                    .setEnumNameProvider(value -> {
                        if (value == SimpleCPSConfig.Anchor.TOP_LEFT) return Text.of("Sol Üst");
                        if (value == SimpleCPSConfig.Anchor.TOP_RIGHT) return Text.of("Sağ Üst");
                        if (value == SimpleCPSConfig.Anchor.TOP_CENTER) return Text.of("Üst Orta");
                        if (value == SimpleCPSConfig.Anchor.BOTTOM_LEFT) return Text.of("Sol Alt");
                        if (value == SimpleCPSConfig.Anchor.BOTTOM_RIGHT) return Text.of("Sağ Alt");
                        if (value == SimpleCPSConfig.Anchor.BOTTOM_CENTER) return Text.of("Alt Orta");
                        return Text.of(value.toString());
                    })
                    .setSaveConsumer(newValue -> SimpleCPSConfig.anchor = newValue)
                    .build());

            // --- YENİ RENK SEÇİCİ ---
            general.addEntry(entryBuilder.startEnumSelector(Text.of("Renk Modu"), SimpleCPSConfig.ColorMode.class, SimpleCPSConfig.colorMode)
                    .setDefaultValue(SimpleCPSConfig.ColorMode.WHITE)
                    .setEnumNameProvider(value -> {
                        if (value == SimpleCPSConfig.ColorMode.WHITE) return Text.of("Beyaz");
                        if (value == SimpleCPSConfig.ColorMode.RED) return Text.of("Kırmızı");
                        if (value == SimpleCPSConfig.ColorMode.GREEN) return Text.of("Yeşil");
                        if (value == SimpleCPSConfig.ColorMode.BLUE) return Text.of("Mavi");
                        if (value == SimpleCPSConfig.ColorMode.GOLD) return Text.of("Altın");
                        if (value == SimpleCPSConfig.ColorMode.RAINBOW) return Text.of("§cG§6ö§ek§ak§bu§9ş§da§5ğ§dı"); // Renkli yazı
                        if (value == SimpleCPSConfig.ColorMode.CUSTOM) return Text.of("Özel (Aşağıdan Ayarla)");
                        return Text.of(value.toString());
                    })
                    .setSaveConsumer(newValue -> SimpleCPSConfig.colorMode = newValue)
                    .build());

            // Hex Renk Ayarı (Sadece 'Özel' seçiliyse işe yarar)
            general.addEntry(entryBuilder.startIntField(Text.of("Özel Renk (Hex)"), SimpleCPSConfig.color)
                    .setDefaultValue(0xFFFFFFFF)
                    .setTooltip(Text.of("Yukarıdan 'Özel' seçeneğini seçmelisin."))
                    .setSaveConsumer(newValue -> SimpleCPSConfig.color = newValue)
                    .build());

            // Diğer Ayarlar
            general.addEntry(entryBuilder.startIntField(Text.of("Yatay Kaydırma (X)"), SimpleCPSConfig.x)
                    .setDefaultValue(4)
                    .setSaveConsumer(newValue -> SimpleCPSConfig.x = newValue)
                    .build());

            general.addEntry(entryBuilder.startIntField(Text.of("Dikey Kaydırma (Y)"), SimpleCPSConfig.y)
                    .setDefaultValue(4)
                    .setSaveConsumer(newValue -> SimpleCPSConfig.y = newValue)
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Text.of("Sağ Tık Göster"), SimpleCPSConfig.showRightClick)
                    .setDefaultValue(true)
                    .setSaveConsumer(newValue -> SimpleCPSConfig.showRightClick = newValue)
                    .build());

            builder.setSavingRunnable(SimpleCPSConfig::save);

            return builder.build();
        };
    }
}