package com.eymistaken.simplecps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Per-server configs. Same idea as {@link ConfigPresetManager}, but the "name" is
 * the server you are on rather than something you type, and switching happens by
 * itself: join a server that has a config and it is applied silently.
 *
 * <p>Files live in {@code <configDir>/simplecps_server_presets/<serverKey>.json},
 * separate from the hand-named presets so the two lists never mix.
 *
 * <p>Binding is implicit. While {@link SimpleCPSConfig#serverConfigsEnabled} is on
 * and you are in a world, every config save is mirrored into that server's file —
 * so "how my HUD looks here" is simply remembered, with nothing to press.
 */
public final class ServerConfigManager {

    private static final File DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "simplecps_server_presets");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Key used for every single-player world (they share one config). */
    public static final String SINGLEPLAYER_KEY = "singleplayer";

    /**
     * Rolling safety net written just before a silent switch. Deliberately a single
     * fixed slot, not a timestamp like the import backup: this fires on every join,
     * so timestamps would bury the Configs list within a session.
     */
    private static final String SAFETY_PRESET = "Auto Backup";

    /** The server context as of the last {@link #pollContext}; null when not in a world. */
    private static String currentKey = null;

    private ServerConfigManager() {}

    private static void ensureDir() {
        if (!DIR.exists()) {
            DIR.mkdirs();
        }
    }

    private static File fileFor(String key) {
        return new File(DIR, key + ".json");
    }

    /** The server context we are in right now, or null if we are not in a world. */
    public static String currentKey() {
        return currentKey;
    }

    /**
     * Which config file the current game context maps to.
     * Multiplayer → the server address; single-player → {@link #SINGLEPLAYER_KEY};
     * not in a world → null.
     */
    public static String keyFor(Minecraft client) {
        if (client == null || client.level == null) return null;

        ServerData data = client.getCurrentServer();
        if (data == null) return SINGLEPLAYER_KEY;

        // Realms have no usable address, so fall back to the display name.
        String raw = (data.ip != null && !data.ip.isBlank()) ? data.ip : data.name;
        if (raw == null || raw.isBlank()) return SINGLEPLAYER_KEY;

        String s = raw.trim().toLowerCase(Locale.ROOT);
        // "mc.example.net" and "mc.example.net:25565" are the same server; without
        // this they would end up with two unrelated configs.
        if (s.endsWith(":25565")) {
            s = s.substring(0, s.length() - ":25565".length());
        }
        return ConfigPresetManager.sanitize(s);
    }

    public static boolean hasConfig(String key) {
        return key != null && fileFor(key).exists();
    }

    /** Server keys that have a stored config, sorted for display. */
    public static List<String> listKeys() {
        ensureDir();
        List<String> keys = new ArrayList<>();
        File[] files = DIR.listFiles((dir, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.equals(LABELS_FILE_NAME)) continue; // sidecar, not a server
                keys.add(n.substring(0, n.length() - 5)); // drop ".json"
            }
        }
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        return keys;
    }

    // --- Display names ---
    //
    // Renaming cannot touch the file name: it *is* the server address and it is how
    // pollContext finds the config on join. So a rename only stores a friendlier
    // label alongside it and the binding keeps working.

    private static final String LABELS_FILE_NAME = "_labels.json";
    private static final File LABELS_FILE = new File(DIR, LABELS_FILE_NAME);
    private static Map<String, String> labels = null;

    private static Map<String, String> labels() {
        if (labels == null) {
            labels = new HashMap<>();
            if (LABELS_FILE.exists()) {
                try (FileReader reader = new FileReader(LABELS_FILE)) {
                    Map<String, String> loaded = GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
                    if (loaded != null) labels.putAll(loaded);
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace(); // cosmetic data; fall back to raw addresses
                }
            }
        }
        return labels;
    }

    /** What to show for a key: the user's own name for it, or the address itself. */
    public static String labelFor(String key) {
        if (key == null) return "";
        String label = labels().get(key);
        return (label == null || label.isBlank()) ? key : label;
    }

    /** Set a display name, or clear it (blank/null) to fall back to the address. */
    public static void setLabel(String key, String label) {
        if (key == null) return;
        if (label == null || label.isBlank() || label.equals(key)) {
            labels().remove(key);
        } else {
            labels().put(key, label.trim());
        }
        writeLabels();
    }

    private static void writeLabels() {
        ensureDir();
        try (FileWriter writer = new FileWriter(LABELS_FILE)) {
            GSON.toJson(labels(), writer);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Watch for a change of server context and switch config when one happens.
     * Called every client tick; everything past the equality check only runs on an
     * actual transition.
     */
    public static void pollContext(Minecraft client) {
        String key = keyFor(client);
        if (Objects.equals(key, currentKey)) return;
        currentKey = key;

        if (key == null) return;                                    // left the world
        if (!SimpleCPSConfig.instance.serverConfigsEnabled) return;
        if (!hasConfig(key)) return;                                // nothing bound here yet

        // The config we are about to drop was mirrored into its own server file on
        // every save, so this is only really needed for edits made outside any world.
        ConfigPresetManager.savePreset(SAFETY_PRESET);
        applyFor(key);
    }

    /**
     * Mirror the live config into the current server's file. Called at the end of
     * {@link SimpleCPSConfig#save()} — this is what makes binding implicit.
     */
    public static void mirrorToCurrentServer() {
        if (currentKey == null) return;
        if (!SimpleCPSConfig.instance.serverConfigsEnabled) return;
        write(currentKey);
    }

    /** Explicitly store the live config as this server's config. */
    public static boolean saveCurrentFor(String key) {
        if (key == null) return false;
        SimpleCPSConfig.collectModuleState(); // this path does not go through save()
        return write(key);
    }

    private static boolean write(String key) {
        ensureDir();
        File file = fileFor(key);
        File temp = new File(DIR, key + ".json.tmp");
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

    /**
     * Adopt a server's stored config. Validated while still detached, exactly like
     * {@link ConfigPresetManager#applyPreset}, so a corrupt file cannot leave the
     * live config half-replaced.
     */
    public static boolean applyFor(String key) {
        if (key == null) return false;
        File file = fileFor(key);
        if (!file.exists()) return false;

        SimpleCPSConfig loaded;
        try (FileReader reader = new FileReader(file)) {
            loaded = GSON.fromJson(reader, SimpleCPSConfig.class);
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
        if (loaded == null) return false;

        try {
            loaded.normalize();
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }

        // The master switch is a client-wide preference, not a per-server one. A file
        // written while it was off would otherwise turn the feature off the instant it
        // is applied — and then nothing would ever switch again.
        loaded.serverConfigsEnabled = SimpleCPSConfig.instance.serverConfigsEnabled;

        SimpleCPSConfig.mergeForeignState(SimpleCPSConfig.instance, loaded);
        SimpleCPSConfig.instance = loaded;
        SimpleCPSConfig.distributeModuleState();
        // Persists as the live config and — via the mirror — binds it to whatever
        // server we are actually on, which is what a manual pick from the editor means.
        SimpleCPSConfig.save();
        return true;
    }

    public static boolean deleteFor(String key) {
        if (key == null) return false;
        try {
            boolean deleted = Files.deleteIfExists(fileFor(key).toPath());
            if (deleted && labels().remove(key) != null) {
                writeLabels(); // otherwise the name would reappear on the next config here
            }
            return deleted;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
