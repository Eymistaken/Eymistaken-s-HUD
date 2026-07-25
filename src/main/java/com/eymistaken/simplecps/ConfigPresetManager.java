package com.eymistaken.simplecps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Named snapshots of the whole {@link SimpleCPSConfig}. Each preset is a full
 * GSON dump of the current config stored under {@code <configDir>/simplecps_presets/<name>.json}.
 * The file name (without {@code .json}) is the display name.
 */
public final class ConfigPresetManager {

    private static final File PRESET_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "simplecps_presets");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigPresetManager() {}

    private static void ensureDir() {
        if (!PRESET_DIR.exists()) {
            PRESET_DIR.mkdirs();
        }
    }

    /** Strip characters that are illegal in file names and cap the length. */
    public static String sanitize(String name) {
        if (name == null) return "config";
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (s.isEmpty()) s = "config";
        if (s.length() > 40) s = s.substring(0, 40);
        return s;
    }

    public static List<String> listPresets() {
        ensureDir();
        List<String> names = new ArrayList<>();
        File[] files = PRESET_DIR.listFiles((dir, n) -> n.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                names.add(n.substring(0, n.length() - 5)); // drop ".json"
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static boolean presetExists(String name) {
        return new File(PRESET_DIR, sanitize(name) + ".json").exists();
    }

    /** First free "Config N" name, used by the one-click Save Config button. */
    public static String uniqueDefaultName() {
        int i = 1;
        while (presetExists("Config " + i)) i++;
        return "Config " + i;
    }

    /** {@code base}, or {@code base (2)}, {@code base (3)}… if it is already taken. */
    public static String uniqueName(String base) {
        if (!presetExists(base)) return sanitize(base);
        int i = 2;
        while (presetExists(base + " (" + i + ")")) i++;
        return sanitize(base + " (" + i + ")");
    }

    /** Write the current {@link SimpleCPSConfig#instance} to a preset file (atomic where possible). */
    public static boolean savePreset(String name) {
        SimpleCPSConfig.collectModuleState(); // this path does not go through save()
        ensureDir();
        String safe = sanitize(name);
        File file = new File(PRESET_DIR, safe + ".json");
        File temp = new File(PRESET_DIR, safe + ".json.tmp");
        try (FileWriter writer = new FileWriter(temp)) {
            GSON.toJson(SimpleCPSConfig.instance, writer);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        try {
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /** Load a preset into {@link SimpleCPSConfig#instance} and persist it as the live config. */
    public static boolean applyPreset(String name) {
        File file = new File(PRESET_DIR, sanitize(name) + ".json");
        if (!file.exists()) return false;
        try (FileReader reader = new FileReader(file)) {
            SimpleCPSConfig loaded = GSON.fromJson(reader, SimpleCPSConfig.class);
            if (loaded == null) return false;
            // Repair and validate while it is still detached, so a corrupt or
            // hand-edited preset file cannot leave the live config half-replaced.
            loaded.normalize();
            SimpleCPSConfig.mergeForeignState(SimpleCPSConfig.instance, loaded);
            SimpleCPSConfig.instance = loaded;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
        SimpleCPSConfig.distributeModuleState();
        SimpleCPSConfig.save();
        return true;
    }

    /** Rename a preset file. Fails (returns false) on collision with an existing different name. */
    public static boolean renamePreset(String oldName, String newName) {
        String safeOld = sanitize(oldName);
        String safeNew = sanitize(newName);
        File oldFile = new File(PRESET_DIR, safeOld + ".json");
        if (!oldFile.exists()) return false;
        if (safeOld.equals(safeNew)) return true;
        File newFile = new File(PRESET_DIR, safeNew + ".json");
        if (newFile.exists()) return false;
        try {
            Files.move(oldFile.toPath(), newFile.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean deletePreset(String name) {
        File file = new File(PRESET_DIR, sanitize(name) + ".json");
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
