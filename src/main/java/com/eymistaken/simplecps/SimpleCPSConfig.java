package com.eymistaken.simplecps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.glfw.GLFW;

public class SimpleCPSConfig {

    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "simplecps.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static SimpleCPSConfig instance = new SimpleCPSConfig();

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                // An empty file parses to null and a malformed one throws a
                // RuntimeException; in either case keep the defaults rather than
                // taking the whole client down during init.
                SimpleCPSConfig loaded = GSON.fromJson(reader, SimpleCPSConfig.class);
                if (loaded != null) {
                    instance = loaded;
                }
            } catch (IOException | RuntimeException e) {
                e.printStackTrace();
            }
            instance.normalize();
        } else {
            instance.resetLayout();
            instance.ensureDefaults();
            save(); // Create default
        }
    }

    public static void save() {
        collectModuleState();
        File tempFile = new File(CONFIG_FILE.getParentFile(), CONFIG_FILE.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tempFile)) {
            GSON.toJson(instance, writer);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        
        try {
            try {
                java.nio.file.Files.move(tempFile.toPath(), CONFIG_FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tempFile.toPath(), CONFIG_FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Implicit per-server binding: no-op unless the feature is on and we are in a
        // world. Kept last so a failure here can never cost us the main config write.
        ServerConfigManager.mirrorToCurrentServer();
    }
    
    /**
     * Reset every built-in setting. Third-party module state in {@link #moduleData}
     * is deliberately left alone — those modules own their settings and expose their
     * own "Reset Settings" action; the following {@link #save()} collects them back
     * into the fresh config.
     */
    public void resetToDefaults() {
        instance = new SimpleCPSConfig();
        instance.resetLayout();
        instance.ensureDefaults();
        save();
    }

    /**
     * Replace null collections with empty ones. Idempotent, cheap and free of side
     * effects — use this, not {@link #ensureDefaults()}, on hot paths like saving or
     * rendering, which must not resurrect a keystrokes layout the user emptied on
     * purpose.
     */
    public void ensureCollections() {
        if (manualLayoutElements == null) {
            manualLayoutElements = new HashMap<>();
        }
        if (moduleData == null) {
            moduleData = new HashMap<>();
        }
        if (knownModules == null) {
            knownModules = new ArrayList<>();
        }
    }

    /** {@link #ensureCollections()} plus restoring the stock keystrokes layout when
     *  there is none. Only for adopting a config (load, preset, share code). */
    public void ensureDefaults() {
        ensureCollections();
        if (keystrokesLayout == null || keystrokesLayout.isEmpty()) {
            resetLayout();
        }
    }

    /**
     * Repair a config that came from disk, a preset or a share code so the rest of
     * the mod can trust it. Safe to call on an object that is not {@link #instance}
     * yet — that is how an import validates before adopting anything.
     *
     * <p>Beyond filling in missing collections this defends against two things GSON
     * does silently: an enum name this build does not know becomes {@code null}
     * (which would NPE in the render path on every frame), and any number in the
     * JSON is accepted verbatim, including negatives and {@code Integer.MAX_VALUE}.
     */
    public void normalize() {
        ensureDefaults();
        coalesceEnums();
        coalesceStrings();
        clampRanges();
        keystrokesLayout = sanitizeKeystrokesLayout(keystrokesLayout);
        if (keystrokesLayout.isEmpty()) {
            resetLayout();
        }
    }

    /** A missing/unknown enum name deserializes to null; fall back to the default. */
    private void coalesceEnums() {
        if (position == null) position = Position.TOP_LEFT;
        if (pingPosition == null) pingPosition = Position.TOP_LEFT;
        if (fpsPosition == null) fpsPosition = Position.TOP_LEFT;
        if (keystrokesPosition == null) keystrokesPosition = Position.TOP_LEFT;
        if (comboPosition == null) comboPosition = Position.TOP_LEFT;
        if (reachPosition == null) reachPosition = Position.TOP_LEFT;
        if (armorPosition == null) armorPosition = Position.BOTTOM_LEFT;
        if (keystrokesRainbowTarget == null) keystrokesRainbowTarget = RainbowTarget.TEXT;
        if (comboHeatmapMode == null) comboHeatmapMode = HeatmapMode.MEDIUM;
        if (combatMode == null) combatMode = CombatMode.MODERN;
        if (hudFont == null) hudFont = HudFont.VANILLA;
    }

    private void coalesceStrings() {
        cpsLeftText = text(cpsLeftText, "");
        cpsRightText = text(cpsRightText, "");
        cpsSeparator = text(cpsSeparator, " | ");
        fpsText = text(fpsText, "FPS");
        comboText = text(comboText, "Combo");
        reachNoHitText = text(reachNoHitText, "No Hit");
    }

    private void clampRanges() {
        // Scales and opacities match the sliders in the settings screen and the editor.
        scale = clamp(scale, 50, 300);
        fpsScale = clamp(fpsScale, 50, 300);
        pingScale = clamp(pingScale, 50, 300);
        armorScale = clamp(armorScale, 50, 300);
        keystrokesScale = clamp(keystrokesScale, 50, 300);
        comboScale = clamp(comboScale, 50, 300);
        reachScale = clamp(reachScale, 50, 300);

        cpsBackgroundOpacity = clamp(cpsBackgroundOpacity, 0, 255);
        pingBackgroundOpacity = clamp(pingBackgroundOpacity, 0, 255);
        fpsBackgroundOpacity = clamp(fpsBackgroundOpacity, 0, 255);
        keystrokesBackgroundOpacity = clamp(keystrokesBackgroundOpacity, 0, 255);
        comboBackgroundOpacity = clamp(comboBackgroundOpacity, 0, 255);
        reachBackgroundOpacity = clamp(reachBackgroundOpacity, 0, 255);

        keystrokesEffectMode = clamp(keystrokesEffectMode, 0, 3);
        settingsMenuScale = clamp(settingsMenuScale, 50, 200);
        // A grid of 0 or 1 would draw a line per pixel across the whole screen.
        editorGridSize = clamp(editorGridSize, 2, 64);
        comboTimeout = clamp(comboTimeout, 0.1, 60.0);
        reachTimeout = clamp(reachTimeout, 0.1, 60.0);

        // Offsets are added to an anchor position; an unbounded value overflows int
        // in HudPlacementResolver before anything gets clamped to the screen.
        xOffset = offset(xOffset);
        yOffset = offset(yOffset);
        pingXOffset = offset(pingXOffset);
        pingYOffset = offset(pingYOffset);
        fpsXOffset = offset(fpsXOffset);
        fpsYOffset = offset(fpsYOffset);
        keystrokesXOffset = offset(keystrokesXOffset);
        keystrokesYOffset = offset(keystrokesYOffset);
        comboXOffset = offset(comboXOffset);
        comboYOffset = offset(comboYOffset);
        reachXOffset = offset(reachXOffset);
        reachYOffset = offset(reachYOffset);
        armorXOffset = offset(armorXOffset);
        armorYOffset = offset(armorYOffset);
        armorHelmetXOffset = offset(armorHelmetXOffset);
        armorHelmetYOffset = offset(armorHelmetYOffset);
        armorChestXOffset = offset(armorChestXOffset);
        armorChestYOffset = offset(armorChestYOffset);
        armorLeggingsXOffset = offset(armorLeggingsXOffset);
        armorLeggingsYOffset = offset(armorLeggingsYOffset);
        armorBootsXOffset = offset(armorBootsXOffset);
        armorBootsYOffset = offset(armorBootsYOffset);
        armorMainHandXOffset = offset(armorMainHandXOffset);
        armorMainHandYOffset = offset(armorMainHandYOffset);
        armorOffHandXOffset = offset(armorOffHandXOffset);
        armorOffHandYOffset = offset(armorOffHandYOffset);
    }

    /** Drop null entries and clamp every button to something renderable. */
    public static List<KeyButtonData> sanitizeKeystrokesLayout(List<KeyButtonData> layout) {
        List<KeyButtonData> out = new ArrayList<>();
        if (layout == null) return out;
        for (KeyButtonData key : layout) {
            if (key == null) continue;
            if (out.size() >= MAX_KEYSTROKE_BUTTONS) break;
            key.label = text(key.label, "");
            key.x = offset(key.x);
            key.y = offset(key.y);
            key.w = clamp(key.w, 1, 1024);
            key.h = clamp(key.h, 1, 1024);
            key.labelX = key.labelX < 0 ? -1 : offset(key.labelX);
            key.labelY = key.labelY < 0 ? -1 : offset(key.labelY);
            out.add(key);
        }
        return out;
    }

    private static final int MAX_KEYSTROKE_BUTTONS = 256;
    private static final int MAX_OFFSET = 10_000;
    private static final int MAX_TEXT_LENGTH = 64;

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static int offset(int value) {
        return clamp(value, -MAX_OFFSET, MAX_OFFSET);
    }

    private static String text(String value, String fallback) {
        if (value == null) return fallback;
        return value.length() > MAX_TEXT_LENGTH ? value.substring(0, MAX_TEXT_LENGTH) : value;
    }

    public static boolean isManualLayout(String key) {
        if (key == null || key.isEmpty()) return false;
        instance.ensureCollections();
        return Boolean.TRUE.equals(instance.manualLayoutElements.get(key));
    }

    public static void setManualLayout(String key, boolean manual) {
        if (key == null || key.isEmpty()) return;
        instance.ensureCollections();
        if (manual) {
            instance.manualLayoutElements.put(key, true);
        } else {
            instance.manualLayoutElements.remove(key);
        }
    }

    // --- THIRD-PARTY MODULE STATE ---
    //
    // Built-in modules keep their settings as fields on this class. Third-party
    // modules can't add fields here, so instead they get an opaque blob keyed by
    // HudModule.getName(). The mod never looks inside the value; it just carries it
    // along, which means plugin settings ride in simplecps.json, presets, share
    // codes and (later) server configs for free.

    /** Per-module opaque state. @see com.eymistaken.simplecps.api.HudModule#saveState() */
    public Map<String, JsonElement> moduleData = new HashMap<>();

    /**
     * The modules that were registered when this config was written.
     *
     * <p>Needed because <em>absence</em> is ambiguous: a config with no entry for
     * "Speedometer" could mean "the writer had Speedometer and it was off/unpinned"
     * or "the writer never had Speedometer at all". Only the first should overwrite
     * a local value. This roster is what tells them apart.
     */
    public List<String> knownModules = new ArrayList<>();

    /** Set by {@link HudModuleManager}; kept as a plain hook so this class never
     *  imports the manager (constructing it pulls in Minecraft-dependent modules,
     *  and {@link #save()} runs before the manager exists on a fresh install). */
    public static Runnable stateCollector;
    public static Runnable stateDistributor;

    /** Ask every registered module to write its state into {@link #moduleData}. */
    public static void collectModuleState() {
        if (stateCollector != null) stateCollector.run();
    }

    /** Push {@link #moduleData} back into every registered module. */
    public static void distributeModuleState() {
        if (stateDistributor != null) stateDistributor.run();
    }

    /**
     * Carry over state that {@code incoming} could not possibly have described,
     * because whoever wrote it did not have those modules installed. Call this
     * <em>before</em> {@code instance = incoming} on every path that adopts a config
     * from elsewhere (preset, share code, later: server config).
     *
     * <p>The rule is <b>preserve-if-absent</b>: anything the writer knew about wins
     * outright, anything they never knew about is kept from the old config. Blobs for
     * modules nobody here has installed are kept verbatim too, so they survive the
     * round trip and light up the day that module is installed.
     *
     * <p>A config written before {@code knownModules} existed has an empty roster, so
     * it preserves everything local — the conservative direction.
     */
    public static void mergeForeignState(SimpleCPSConfig previous, SimpleCPSConfig incoming) {
        if (previous == null || incoming == null || previous == incoming) return;
        previous.ensureCollections();
        incoming.ensureCollections();

        Set<String> writerKnew = new HashSet<>(incoming.knownModules);

        for (Map.Entry<String, JsonElement> entry : previous.moduleData.entrySet()) {
            if (writerKnew.contains(entry.getKey())) continue;
            incoming.moduleData.putIfAbsent(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Boolean> entry : previous.manualLayoutElements.entrySet()) {
            if (writerKnew.contains(owningModuleOf(entry.getKey()))) continue;
            incoming.manualLayoutElements.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    /** Both {@code module:Foo} and {@code module:Foo/element:Bar} belong to "Foo". */
    private static String owningModuleOf(String layoutKey) {
        if (layoutKey == null) return "";
        String s = layoutKey;
        int slash = s.indexOf('/');
        if (slash >= 0) s = s.substring(0, slash);
        return s.startsWith("module:") ? s.substring("module:".length()) : s;
    }

    // Enums
    public enum Position { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER }
    public enum RainbowTarget { TEXT, BACKGROUND }
    public enum HeatmapMode { EASY, MEDIUM, HARD } // New Enum
    public enum CombatMode { CLASSIC, MODERN }
    // Custom HUD font selection (affects HUD elements + editor screens only, never vanilla text).
    public enum HudFont { VANILLA, INTER, RUBIK, JETBRAINS_MONO, BEBAS_NEUE, ENCHANT }

    // --- HUD EDITOR ---
    public boolean preventOverlap = true;
    public Map<String, Boolean> manualLayoutElements = new HashMap<>();
    // Editor-only grid: drawn behind the live preview and used as a drag fallback
    // when nothing snaps to another module's edge.
    public boolean editorGridEnabled = false;
    public int editorGridSize = 8;

    // --- GENERAL ---
    // Draw HUD text with a thin black outline on every side instead of the vanilla drop shadow.
    public boolean textOutline = false;
    // Font used for HUD elements and the editor/designer screens (not the rest of Minecraft).
    public HudFont hudFont = HudFont.VANILLA;
    // Remember the HUD per server: joining a server you have configured swaps to its
    // config silently, and edits made there are bound back to it. See ServerConfigManager.
    public boolean serverConfigsEnabled = true;
    // Size of the settings screen, as a percentage. The screen already cancels out
    // Minecraft's GUI Scale so it stays the same size at any of them; this nudges
    // that baseline for people who want it bigger or smaller.
    public int settingsMenuScale = 100;

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
    // New Fields
    public String cpsLeftText = "";
    public String cpsRightText = "";
    public String cpsSeparator = " | ";

    // --- PING ---
    public boolean showPing = false;
    public Position pingPosition = Position.TOP_LEFT;
    public int pingXOffset = 0;
    public int pingYOffset = 0;
    public int pingScale = 100;
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
    public int keystrokesEffectMode = 1; // 0=None, 1=Squish, 2=Ripple, 3=Both
    
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
        @com.google.gson.annotations.SerializedName("btnColor")
        public int btnColor = -1; // -1 means global color
        @com.google.gson.annotations.SerializedName("btnPressedColor")
        public int btnPressedColor = -1; // -1 means global pressed color
        public Boolean animationEnabled = null; // null means unset (treat as true)

        /**
         * Required by GSON. Without a no-arg constructor it allocates through Unsafe
         * and the field initializers above never run — silently turning {@code shadow}
         * off and the {@code -1} sentinels ("use the global color", "center the label")
         * into 0 for any key whose JSON omits them.
         */
        public KeyButtonData() {}

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
    }
    
    public void resetLayout() {
        // Assign rather than clear(): this runs from ensureDefaults() precisely when
        // the list may be null, e.g. a hand-edited config with "keystrokesLayout": null.
        keystrokesLayout = new ArrayList<>();
        // WASD (Row 1 & 2)
        // W: centered in 3-key width (67px). x=23.
        keystrokesLayout.add(new KeyButtonData("W", 23, 0, 21, 21, GLFW.GLFW_KEY_W));
        keystrokesLayout.add(new KeyButtonData("A", 0, 23, 21, 21, GLFW.GLFW_KEY_A));
        keystrokesLayout.add(new KeyButtonData("S", 23, 23, 21, 21, GLFW.GLFW_KEY_S));
        keystrokesLayout.add(new KeyButtonData("D", 46, 23, 21, 21, GLFW.GLFW_KEY_D));
        
        // LMB / RMB (Row 3, Taller but slightly reduced per request)
        // Previous Height: 27. Requested 3/4 size -> ~21.
        // y = 23 + 21 + 2 = 46. Height 21.
        KeyButtonData lmb = new KeyButtonData("LMB", 0, 46, 33, 21, 0, true);
        lmb.showCps = false; // Default off
        keystrokesLayout.add(lmb);
        
        KeyButtonData rmb = new KeyButtonData("RMB", 34, 46, 33, 21, 1, true);
        rmb.showCps = false; // Default off
        keystrokesLayout.add(rmb);
        
        // Space (Row 4)
        // y = 46 + 21 + 2 = 69. Width 67. Height 13.
        keystrokesLayout.add(new KeyButtonData("-----", 0, 69, 67, 13, GLFW.GLFW_KEY_SPACE));
        
        // Modifiers (Row 5 - Below Space)
        // y = 69 + 13 + 2 = 84. Height 13.
        keystrokesLayout.add(new KeyButtonData("CTRL", 0, 84, 33, 13, GLFW.GLFW_KEY_LEFT_CONTROL));
        keystrokesLayout.add(new KeyButtonData("SHIFT", 34, 84, 33, 13, GLFW.GLFW_KEY_LEFT_SHIFT));
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
    public boolean comboResetOnAnyDamage = true; // Default: True (Legacy Mode). If False -> Advanced Mode (Decay/Distance)
    public boolean comboContinueOnSwitch = true;
    public boolean comboHideWhenInactive = false;
    public boolean comboHeatmap = false;
    public HeatmapMode comboHeatmapMode = HeatmapMode.MEDIUM; // Default: Medium
    public boolean comboOnlyPlayers = true; // Default: True
    // public boolean comboDecay = true; // Removed - Tied to !comboResetOnAnyDamage
    // public boolean comboDistanceCheck = true; // Removed - Tied to !comboResetOnAnyDamage
    // public boolean comboLOSCheck = true; // Removed
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

    // --- ARMOR HUD ---
    public boolean showArmor = false;
    public Position armorPosition = Position.BOTTOM_LEFT;
    public int armorXOffset = 0;
    public int armorYOffset = 0;
    public int armorScale = 100;
    public boolean armorVertical = true; // Orientation
    public boolean armorShowBackgroundSlots = false; // Render Style
    public boolean armorShowMainHand = true; // Main Hand toggle
    public boolean armorShowOffHand = false; // Off Hand toggle
    public boolean armorDurabilityText = false; // Durability Text
    public boolean armorDamageFlash = true; // Damage Flash

    // Armor Individual Slots Offsets
    public int armorHelmetXOffset = 0;
    public int armorHelmetYOffset = 0;
    public int armorChestXOffset = 0;
    public int armorChestYOffset = 0;
    public int armorLeggingsXOffset = 0;
    public int armorLeggingsYOffset = 0;
    public int armorBootsXOffset = 0;
    public int armorBootsYOffset = 0;
    public int armorMainHandXOffset = 0;
    public int armorMainHandYOffset = 0;
    public int armorOffHandXOffset = 0;
    public int armorOffHandYOffset = 0;
}
