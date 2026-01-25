package com.eymistaken.simplecps;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "simplecps")
public class SimpleCPSConfig implements ConfigData {

    public enum Position {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }

    // Yeni: Keystrokes Modu (Harf, Ok veya Özel)
    public enum KeystrokesMode {
        LETTERS, // W A S D
        ARROWS,  // ^ < v >
        CUSTOM   // Kullanıcının yazdığı
    }

    // Yeni: Gökkuşağı Hedefi
    public enum RainbowTarget {
        TEXT,       // Sadece yazı
        BACKGROUND  // Sadece kutu
    }

    // --- CPS AYARLARI ---
    @ConfigEntry.Gui.Tooltip
    public boolean enabled = true;

    @ConfigEntry.Gui.Tooltip
    public Position position = Position.TOP_LEFT;

    @ConfigEntry.Gui.Tooltip
    public int xOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int yOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int textColor = 0xFFFFFF;

    @ConfigEntry.Gui.Tooltip
    public boolean rightClickCps = true;

    @ConfigEntry.Gui.Tooltip
    public boolean rainbow = false; // CPS için rainbow

    @ConfigEntry.Gui.Tooltip
    public int scale = 100;

    // --- PING AYARLARI ---
    @ConfigEntry.Gui.Tooltip
    public boolean showPing = false;

    @ConfigEntry.Gui.Tooltip
    public Position pingPosition = Position.TOP_LEFT;

    @ConfigEntry.Gui.Tooltip
    public int pingXOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int pingYOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int pingColor = 0xFFFFFF;

    // --- KEYSTROKES AYARLARI ---
    @ConfigEntry.Gui.Tooltip
    public boolean showKeystrokes = false;

    @ConfigEntry.Gui.Tooltip
    public Position keystrokesPosition = Position.TOP_LEFT;

    @ConfigEntry.Gui.Tooltip
    public int keystrokesXOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int keystrokesYOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int keystrokesScale = 80;

    @ConfigEntry.Gui.Tooltip
    public int keystrokesColor = 0xFFFFFF;

    @ConfigEntry.Gui.Tooltip
    public int keystrokesPressedColor = 0x00FF00;

    // --- YENİ EKLENEN KEYSTROKES ÖZELLİKLERİ ---
    
    @ConfigEntry.Gui.Tooltip
    public KeystrokesMode keystrokesMode = KeystrokesMode.LETTERS;

    @ConfigEntry.Gui.Tooltip
    public boolean keystrokesRainbow = false; // Keystrokes için ayrı rainbow

    @ConfigEntry.Gui.Tooltip
    public RainbowTarget keystrokesRainbowTarget = RainbowTarget.TEXT;

    // Custom Yazılar (Reset tuşu ile bunlara dönecek)
    @ConfigEntry.Gui.Tooltip
    public String customW = "W";
    @ConfigEntry.Gui.Tooltip
    public String customA = "A";
    @ConfigEntry.Gui.Tooltip
    public String customS = "S";
    @ConfigEntry.Gui.Tooltip
    public String customD = "D";
    @ConfigEntry.Gui.Tooltip
    public String customSpace = "----";
}