package com.eymistaken.simplecps.util;

import com.eymistaken.simplecps.ConfigPresetManager;
import com.eymistaken.simplecps.HudModuleManager;
import com.eymistaken.simplecps.HudPlacementResolver;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.api.IHudElement;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Config-level actions shared by the HUD editor and the settings screen: reset the
 * layout, and move a whole config in or out through the clipboard.
 *
 * <p>These used to live as private methods on {@code HudEditorScreen}. The backup
 * and version-mismatch handling in {@link #importShareCode()} in particular is the
 * kind of thing that must not exist in two slightly different copies.
 */
public final class HudActions {

    private HudActions() {}

    /** Outcome of a clipboard action, with a message meant for a status line. */
    public record Result(boolean ok, String message) {}

    /**
     * Send every module and sub-element back to its default anchor, offset and
     * scale, and clear the manual-placement flags so auto-stacking takes over again.
     * Visual and behavioral settings are untouched.
     *
     * <p>Callers with in-flight animation state must clear it first: this writes
     * offsets straight to the config, so a running move would overwrite the reset
     * on the next frame.
     */
    public static void resetHudLayout() {
        List<HudModule> modules = HudModuleManager.getInstance().getModules();
        for (HudModule module : modules) {
            HudPlacementResolver.setManualLayout(module, modules, false);
            for (IHudElement subElement : module.getSubElements()) {
                HudPlacementResolver.setManualLayout(subElement, modules, false);
                subElement.resetToDefaults();
            }
            module.resetToDefaults();
            // Resetting the parent re-derives sub-element keys, so clear them again.
            for (IHudElement subElement : module.getSubElements()) {
                HudPlacementResolver.setManualLayout(subElement, modules, false);
                subElement.resetToDefaults();
            }
        }
        SimpleCPSConfig.instance.preventOverlap = true;
        SimpleCPSConfig.save();
    }

    /** Copy the whole current config to the clipboard as an {@code EYMHUD1-} code. */
    public static Result exportShareCode() {
        String code = HudShareCodec.encodeConfig();
        if (code == null) return new Result(false, "Export failed");

        Minecraft client = Minecraft.getInstance();
        if (client == null) return new Result(false, "Export failed");
        client.keyboardHandler.setClipboard(code);
        return new Result(true, "Copied to clipboard!");
    }

    /**
     * Read a share code from the clipboard and apply it. The current config is
     * backed up to a preset first so an unwanted import can be undone.
     */
    public static Result importShareCode() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return new Result(false, "Import failed");

        HudShareCodec.Decoded decoded = HudShareCodec.decode(client.keyboardHandler.getClipboard());
        if (decoded == null) return new Result(false, "No valid code in clipboard");
        if (!HudShareCodec.TYPE_CONFIG.equals(decoded.type())) {
            // A keystrokes code would apply only the layout yet still report success
            // here - and burn a backup slot doing it. Send them to the designer.
            return new Result(false, "That's a keystrokes code - use the Keystrokes designer");
        }

        // Timestamped: a fixed name would overwrite the previous backup, so a second
        // import would destroy the only copy of the original config.
        String backupName = ConfigPresetManager.uniqueName(
            "Backup " + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")));
        ConfigPresetManager.savePreset(backupName);

        if (!HudShareCodec.apply(decoded)) {
            return new Result(false, "Import failed");
        }
        if (HudShareCodec.isFromOtherVersion(decoded)) {
            return new Result(true, "Imported from v" + decoded.modVersion() + " (backup saved)");
        }
        return new Result(true, "Imported! (backup saved)");
    }

    /**
     * Apply a share code the user typed or pasted into a field, rather than one
     * sitting on the clipboard. Same backup guarantees as {@link #importShareCode()}.
     */
    public static Result importShareCode(String code) {
        if (code == null || code.trim().isEmpty()) return new Result(false, "Enter a code first");

        HudShareCodec.Decoded decoded = HudShareCodec.decode(code);
        if (decoded == null) return new Result(false, "Not a valid share code");
        if (!HudShareCodec.TYPE_CONFIG.equals(decoded.type())) {
            return new Result(false, "That's a keystrokes code - use the designer");
        }

        String backupName = ConfigPresetManager.uniqueName(
            "Backup " + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")));
        ConfigPresetManager.savePreset(backupName);

        if (!HudShareCodec.apply(decoded)) {
            return new Result(false, "Import failed");
        }
        if (HudShareCodec.isFromOtherVersion(decoded)) {
            return new Result(true, "Imported from v" + decoded.modVersion() + " (backup saved)");
        }
        return new Result(true, "Imported! (backup saved)");
    }

    /** Copy just the keystrokes layout to the clipboard as an {@code EYMHUD1-} code. */
    public static Result exportKeystrokes() {
        String code = HudShareCodec.encodeKeystrokes();
        if (code == null) return new Result(false, "Export failed");

        Minecraft client = Minecraft.getInstance();
        if (client == null) return new Result(false, "Export failed");
        client.keyboardHandler.setClipboard(code);
        return new Result(true, "Copied to clipboard!");
    }

    /**
     * Apply a keystrokes share code the user typed or pasted into a field.
     *
     * <p>Unlike {@link #importShareCode(String)} this takes no preset backup. It only
     * ever replaces the layout, and the designer that calls it has already pushed the
     * old layout onto its own undo stack — burning a preset slot per import as well
     * would fill the list with backups nobody asked for.
     */
    public static Result importKeystrokes(String code) {
        if (code == null || code.trim().isEmpty()) return new Result(false, "Enter a code first");

        HudShareCodec.Decoded decoded = HudShareCodec.decode(code);
        if (decoded == null) return new Result(false, "Not a valid share code");
        if (!HudShareCodec.TYPE_KEYSTROKES.equals(decoded.type())) {
            // The mirror of the message importShareCode gives for a keystrokes code.
            return new Result(false, "That's a config code - use the HUD editor");
        }

        if (!HudShareCodec.apply(decoded)) {
            return new Result(false, "Import failed");
        }
        if (HudShareCodec.isFromOtherVersion(decoded)) {
            return new Result(true, "Imported from v" + decoded.modVersion());
        }
        return new Result(true, "Imported!");
    }
}
