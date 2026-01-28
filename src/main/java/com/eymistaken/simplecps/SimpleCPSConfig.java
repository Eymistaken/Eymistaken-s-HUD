package com.eymistaken.simplecps;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "simplecps")
public class SimpleCPSConfig implements ConfigData {

    public enum Position {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }

    public enum KeystrokesMode {
        LETTERS, ARROWS, CUSTOM
    }

    public enum RainbowTarget {
        TEXT, BACKGROUND
    }

    // --- 1. CPS AYARLARI ---
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
    public boolean rainbow = false;

    @ConfigEntry.Gui.Tooltip
    public int scale = 100;

    // CPS Arkaplan
    @ConfigEntry.Gui.Tooltip
    public boolean cpsShowBackground = false;
    @ConfigEntry.Gui.Tooltip
    public int cpsBackgroundColor = 0x000000;
    @ConfigEntry.Gui.Tooltip
    public int cpsBackgroundOpacity = 128; // 0-255

    // --- 2. PING AYARLARI ---
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

    // Ping Arkaplan
    @ConfigEntry.Gui.Tooltip
    public boolean pingShowBackground = false;
    @ConfigEntry.Gui.Tooltip
    public int pingBackgroundColor = 0x000000;
    @ConfigEntry.Gui.Tooltip
    public int pingBackgroundOpacity = 128;

    // --- 3. FPS AYARLARI (YENİ) ---
    @ConfigEntry.Gui.Tooltip
    public boolean showFps = false;

    @ConfigEntry.Gui.Tooltip
    public Position fpsPosition = Position.TOP_LEFT;

    @ConfigEntry.Gui.Tooltip
    public int fpsXOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int fpsYOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int fpsColor = 0xFFFFFF;

    @ConfigEntry.Gui.Tooltip
    public boolean fpsRainbow = false;

    @ConfigEntry.Gui.Tooltip
    public int fpsScale = 100;

    @ConfigEntry.Gui.Tooltip
    public String fpsText = "FPS"; // Örnek: "144 FPS"

    // FPS Arkaplan
    @ConfigEntry.Gui.Tooltip
    public boolean fpsShowBackground = false;
    @ConfigEntry.Gui.Tooltip
    public int fpsBackgroundColor = 0x000000;
    @ConfigEntry.Gui.Tooltip
    public int fpsBackgroundOpacity = 128;

    // --- 4. KEYSTROKES AYARLARI ---
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
    public KeystrokesMode keystrokesMode = KeystrokesMode.LETTERS;

    @ConfigEntry.Gui.Tooltip
    public boolean keystrokesRainbow = false;

    @ConfigEntry.Gui.Tooltip
    public RainbowTarget keystrokesRainbowTarget = RainbowTarget.TEXT;

    @ConfigEntry.Gui.Tooltip
    public int keystrokesColor = 0xFFFFFF; // Yazı Rengi

    @ConfigEntry.Gui.Tooltip
    public int keystrokesPressedColor = 0x00FF00; // Basılınca Yazı Rengi

    // Keystrokes Arkaplan (Kapatma tuşu YOK, sadece renk/opaklık)
    @ConfigEntry.Gui.Tooltip
    public int keystrokesBackgroundColor = 0x000000;
    @ConfigEntry.Gui.Tooltip
    public int keystrokesBackgroundOpacity = 128; // Varsayılan yarı saydam

    // Custom Text Fields
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
    // --- 5. COMBO AYARLARI (BETA) ---
    @ConfigEntry.Gui.Tooltip
    public boolean showCombo = false;

    @ConfigEntry.Gui.Tooltip
    public Position comboPosition = Position.TOP_LEFT;

    @ConfigEntry.Gui.Tooltip
    public int comboXOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int comboYOffset = 0;

    @ConfigEntry.Gui.Tooltip
    public int comboScale = 100;

    @ConfigEntry.Gui.Tooltip
    public int comboColor = 0xFFFFFF;

    @ConfigEntry.Gui.Tooltip
    public boolean comboRainbow = false;

    @ConfigEntry.Gui.Tooltip
    public String comboText = "Combo";

    // Combo Arkaplan
    @ConfigEntry.Gui.Tooltip
    public boolean comboShowBackground = false;
    @ConfigEntry.Gui.Tooltip
    public int comboBackgroundColor = 0x000000;
    @ConfigEntry.Gui.Tooltip
    public int comboBackgroundOpacity = 128;

    // Logic
    @ConfigEntry.Gui.Tooltip
    public double comboTimeout = 3.0; // Seconds

    @ConfigEntry.Gui.Tooltip
    public boolean comboResetOnAnyDamage = true;

    @ConfigEntry.Gui.Tooltip
    public boolean comboContinueOnSwitch = true;

    @ConfigEntry.Gui.Tooltip
    public boolean comboHideWhenInactive = false;
}