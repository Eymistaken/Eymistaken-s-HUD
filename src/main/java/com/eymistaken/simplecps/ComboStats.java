package com.eymistaken.simplecps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * The best combo the player has landed, kept between sessions in
 * {@code <configDir>/simplecps_stats.json}.
 *
 * <p>Deliberately not a field on {@link SimpleCPSConfig}. That object is the whole payload of a
 * config share code and is copied into the per-server configs on every save, so a record kept
 * there would travel to anyone who imports your HUD and would change with the server you joined.
 *
 * <p>The record rises in memory with each hit that beats it and reaches disk when the combo ends
 * or the game closes, so a long run past the old record costs one write rather than one per hit.
 */
public final class ComboStats {

    private static final File FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "simplecps_stats.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The file's contents. An object rather than a bare number, so it can hold more later. */
    private static final class Stats {
        int bestCombo;
    }

    private static int best = 0;
    private static boolean dirty = false;

    private ComboStats() {}

    public static void load() {
        if (!FILE.exists()) return;
        try (FileReader reader = new FileReader(FILE)) {
            // An empty file parses to null and a malformed one throws; either way the record
            // starts from zero rather than taking the client down during init.
            Stats stats = GSON.fromJson(reader, Stats.class);
            if (stats != null) best = Math.max(0, stats.bestCombo);
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    /** The best combo on record, or 0 when there is none yet. */
    public static int getBest() {
        return best;
    }

    /** The running combo reached {@code combo}; it becomes the record if it beats it. */
    static void offer(int combo) {
        if (combo <= best) return;
        best = combo;
        dirty = true;
    }

    /** Forgets the record and writes that at once, since no combo is on its way to write it. */
    public static void resetBest() {
        best = 0;
        dirty = true;
        flush();
    }

    /** Writes the record if it changed since it was last written. */
    public static void flush() {
        if (!dirty) return;
        dirty = false;

        Stats stats = new Stats();
        stats.bestCombo = best;
        File temp = new File(FILE.getParentFile(), FILE.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(temp)) {
            GSON.toJson(stats, writer);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try {
            try {
                Files.move(temp.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
