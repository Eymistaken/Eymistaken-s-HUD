package com.eymistaken.simplecps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SimpleCPSConfig {

    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "simplecps.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static SimpleCPSConfig instance = new SimpleCPSConfig();

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, SimpleCPSConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save(); // Create default
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Enums
    public enum Position { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }
    public enum KeystrokesMode { LETTERS, ARROWS, CUSTOM }
    public enum RainbowTarget { TEXT, BACKGROUND }
    public enum CombatMode { CLASSIC, MODERN }

    // --- CPS ---
    public boolean enabled = true;
    public Position position = Position.TOP_LEFT;
    public int xOffset = 0;
    public int yOffset = 0;
    public int textColor = 0xFFFFFF;
    public boolean rightClickCps = true;
    public boolean rainbow = false;
    public int scale = 100;
    public boolean cpsShowBackground = false;
    public int cpsBackgroundColor = 0x000000;
    public int cpsBackgroundOpacity = 128;

    // --- PING ---
    public boolean showPing = false;
    public Position pingPosition = Position.TOP_LEFT;
    public int pingXOffset = 0;
    public int pingYOffset = 0;
    public int pingColor = 0xFFFFFF;
    public boolean pingShowBackground = false;
    public int pingBackgroundColor = 0x000000;
    public int pingBackgroundOpacity = 128;

    // --- FPS ---
    public boolean showFps = false;
    public Position fpsPosition = Position.TOP_LEFT;
    public int fpsXOffset = 0;
    public int fpsYOffset = 0;
    public int fpsColor = 0xFFFFFF;
    public boolean fpsRainbow = false;
    public int fpsScale = 100;
    public String fpsText = "FPS";
    public boolean fpsShowBackground = false;
    public int fpsBackgroundColor = 0x000000;
    public int fpsBackgroundOpacity = 128;

    // --- KEYSTROKES ---
    public boolean showKeystrokes = false;
    public Position keystrokesPosition = Position.TOP_LEFT;
    public int keystrokesXOffset = 0;
    public int keystrokesYOffset = 0;
    public int keystrokesScale = 80;
    public KeystrokesMode keystrokesMode = KeystrokesMode.LETTERS;
    public boolean keystrokesRainbow = false;
    public RainbowTarget keystrokesRainbowTarget = RainbowTarget.TEXT;
    public int keystrokesColor = 0xFFFFFF;
    public int keystrokesPressedColor = 0x00FF00;
    public int keystrokesBackgroundColor = 0x000000;
    public int keystrokesBackgroundOpacity = 128;
    public String customW = "W";
    public String customA = "A";
    public String customS = "S";
    public String customD = "D";
    public String customSpace = "----";

    // --- COMBO ---
    public boolean showCombo = false;
    public Position comboPosition = Position.TOP_LEFT;
    public int comboXOffset = 0;
    public int comboYOffset = 0;
    public int comboScale = 100;
    public int comboColor = 0xFFFFFF;
    public boolean comboRainbow = false;
    public String comboText = "Combo";
    public boolean comboShowBackground = false;
    public int comboBackgroundColor = 0x000000;
    public int comboBackgroundOpacity = 128;
    public double comboTimeout = 3.0;
    public boolean comboResetOnAnyDamage = true;
    public boolean comboContinueOnSwitch = true;
    public boolean comboHideWhenInactive = false;
    public CombatMode combatMode = CombatMode.MODERN;

    // --- REACH DISPLAY ---
    public boolean showReach = false;
    public Position reachPosition = Position.TOP_LEFT;
    public int reachXOffset = 0;
    public int reachYOffset = 0;
    public int reachColor = 0xFFFFFF;
    public boolean reachRainbow = false;
    public double reachTimeout = 3.0;
    public int reachScale = 100;
    public boolean reachShowBackground = false;
    public int reachBackgroundColor = 0x000000;
    public int reachBackgroundOpacity = 128;
    public boolean reachOnlyPlayers = true;
    public boolean reachAlwaysShow = false;
    public String reachNoHitText = "No Hit";
}