package com.eymistaken.simplecps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

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
    public boolean keystrokesRainbow = false;
    public RainbowTarget keystrokesRainbowTarget = RainbowTarget.TEXT;
    public int keystrokesColor = 0xFFFFFF;
    public int keystrokesPressedColor = 0x00FF00;
    public int keystrokesBackgroundColor = 0x000000;
    public int keystrokesBackgroundOpacity = 128;
    
    // Toggleable Defaults
    public boolean showLCTRL = true;
    public boolean showLSHIFT = true;
    public boolean showSpace = true; // Migrated logic if needed, but space is usually in list

    public static class KeyButtonData {
        public String label;
        public int x, y, w, h;
        public int keyCode;
        public boolean isMouse = false; // New: Mouse Button Support
        public boolean showCps = false; // New: CPS Counter
        public boolean shadow = true;
        public boolean bold = false;
        public boolean italic = false;
        public boolean underlined = false;
        public int labelX = -1, labelY = -1; // -1 means center

        public KeyButtonData(String label, int x, int y, int w, int h, int keyCode) {
            this(label, x, y, w, h, keyCode, false);
        }

        public KeyButtonData(String label, int x, int y, int w, int h, int keyCode, boolean isMouse) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.keyCode = keyCode;
            this.isMouse = isMouse;
        }
    }

    public List<KeyButtonData> keystrokesLayout = new ArrayList<>();

    public SimpleCPSConfig() {
        // Default Layout: WASD + Space + Mouse + Modifiers
        resetLayout();
    }
    
    public void resetLayout() {
        keystrokesLayout.clear();
        // WASD
        keystrokesLayout.add(new KeyButtonData("W", 22, 0, 20, 20, GLFW.GLFW_KEY_W));
        keystrokesLayout.add(new KeyButtonData("A", 0, 22, 20, 20, GLFW.GLFW_KEY_A));
        keystrokesLayout.add(new KeyButtonData("S", 22, 22, 20, 20, GLFW.GLFW_KEY_S));
        keystrokesLayout.add(new KeyButtonData("D", 44, 22, 20, 20, GLFW.GLFW_KEY_D));
        
        // Mouse Buttons (LMB, RMB) - Split Mouse Style
        // keystrokesLayout.add(new KeyButtonData("LMB", -22, 22, 20, 42, 0, true)); // OLD Vertical
        // keystrokesLayout.add(new KeyButtonData("RMB", 66, 22, 20, 42, 1, true));  // OLD Vertical
        
        // Space
        keystrokesLayout.add(new KeyButtonData("----", 0, 44, 64, 12, GLFW.GLFW_KEY_SPACE));
        
        // Modifiers (LCTRL, LSHIFT)
        keystrokesLayout.add(new KeyButtonData("LCTRL", 0, 58, 31, 12, GLFW.GLFW_KEY_LEFT_CONTROL));
        keystrokesLayout.add(new KeyButtonData("LSHIFT", 33, 58, 31, 12, GLFW.GLFW_KEY_LEFT_SHIFT));

        // Mouse Buttons (LMB, RMB) - Wide Style Below Modifiers
        // Align with LCTRL (0) and LSHIFT (33)
        // Default CPS enabled for mouse buttons? User asked for option to toggle. Let's default true for buttons? Or false. 
        // User: "bu seçenek ... kapatılabilsin" -> imply it might be on or off. Let's default false, user can enable. Or default true? "altında ufak bir sayı olsun... kapatılabilsin" sounds like feature request.
        KeyButtonData lmb = new KeyButtonData("LMB", 0, 72, 31, 12, 0, true);
        lmb.showCps = true;
        keystrokesLayout.add(lmb);
        
        KeyButtonData rmb = new KeyButtonData("RMB", 33, 72, 31, 12, 1, true);
        rmb.showCps = true;
        keystrokesLayout.add(rmb);
    }

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