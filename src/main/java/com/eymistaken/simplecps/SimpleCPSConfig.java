package com.eymistaken.simplecps;

import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class SimpleCPSConfig {
    
    // Konum Listesi
    public enum Anchor {
        TOP_LEFT, TOP_RIGHT, TOP_CENTER,
        BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM_CENTER
    }

    // Renk Modu Listesi (YENİ)
    public enum ColorMode {
        WHITE,      // Beyaz
        RED,        // Kırmızı
        GREEN,      // Yeşil
        BLUE,       // Mavi
        GOLD,       // Altın Sarısı
        RAINBOW,    // Gökkuşağı (Animasyonlu)
        CUSTOM      // Özel (Hex Kodu)
    }

    // Ayarlar
    public static int x = 4;
    public static int y = 4;
    public static int color = 0xFFFFFFFF; // Custom seçilirse kullanılacak hex
    public static boolean showRightClick = true;
    public static Anchor anchor = Anchor.TOP_LEFT;
    public static ColorMode colorMode = ColorMode.WHITE; // Varsayılan: Beyaz

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("simplecps.properties");

    public static void save() {
        Properties props = new Properties();
        props.setProperty("x", String.valueOf(x));
        props.setProperty("y", String.valueOf(y));
        props.setProperty("color", String.valueOf(color));
        props.setProperty("showRightClick", String.valueOf(showRightClick));
        props.setProperty("anchor", anchor.name());
        props.setProperty("colorMode", colorMode.name()); // Rengi kaydet

        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            props.store(out, "SimpleCPS Ayarlari");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
            x = Integer.parseInt(props.getProperty("x", "4"));
            y = Integer.parseInt(props.getProperty("y", "4"));
            try {
                color = Integer.parseInt(props.getProperty("color", String.valueOf(0xFFFFFFFF)));
            } catch (NumberFormatException e) { color = 0xFFFFFFFF; }
            
            showRightClick = Boolean.parseBoolean(props.getProperty("showRightClick", "true"));
            
            try {
                anchor = Anchor.valueOf(props.getProperty("anchor", "TOP_LEFT"));
            } catch (IllegalArgumentException e) { anchor = Anchor.TOP_LEFT; }

            // Renk modunu yükle
            try {
                colorMode = ColorMode.valueOf(props.getProperty("colorMode", "WHITE"));
            } catch (IllegalArgumentException e) { colorMode = ColorMode.WHITE; }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}