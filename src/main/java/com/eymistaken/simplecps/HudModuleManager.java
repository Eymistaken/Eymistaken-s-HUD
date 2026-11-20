package com.eymistaken.simplecps;

import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.modules.*;
import com.eymistaken.simplecps.util.Anim;
import com.eymistaken.simplecps.util.Easings;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HudModuleManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("Eymistaken's HUD");

    private static HudModuleManager instance;
    private final List<HudModule> modules = new ArrayList<>();
    
    // Module Bounds for Editor
    public static class ModuleBounds {
        public int x, y, w, h;
        public ModuleBounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
    
    private final Map<String, ModuleBounds> moduleBounds = new HashMap<>();

    // Fade-in / fade-out state (only used by modules that supportsFade()).
    private final Map<String, Anim> moduleAlpha = new HashMap<>();
    private final Map<String, ModuleBounds> lastBounds = new HashMap<>();
    private boolean wasEditor = false;
    private static final float FADE_DURATION = 0.18f;

    private HudModuleManager() {
        // Initialize Default Modules
        registerModule(new CpsModule());
        registerModule(new FpsModule());
        registerModule(new PingModule());
        registerModule(new ComboModule());
        registerModule(new KeystrokesModule());
        registerModule(new ArmorModule());
        registerModule(new ReachModule());
    }

    public static HudModuleManager getInstance() {
        if (instance == null) {
            instance = new HudModuleManager();
        }
        return instance;
    }

    /**
     * Add a module to the HUD. Rejects duplicate names: {@link HudModule#getName()}
     * is the key for the layout flags <em>and</em> for saved state, so two modules
     * sharing one would silently corrupt each other's settings — and the key format
     * is baked into every saved config and share code, so it cannot be fixed later.
     * Third-party modules should prefix their name with their mod id.
     */
    public void registerModule(HudModule module) {
        if (module == null) return;

        String name = module.getName();
        if (name == null || name.isEmpty()) {
            LOGGER.error("Refusing to register {}: getName() returned null or empty.", module.getClass().getName());
            return;
        }

        HudModule existing = getModuleByName(name);
        if (existing != null) {
            LOGGER.error(
                "Refusing to register {}: the module name \"{}\" is already taken by {}. "
                + "getName() keys both the layout and the saved settings, so a duplicate would corrupt both. "
                + "Prefix it with your mod id, e.g. \"yourmod:{}\".",
                module.getClass().getName(), name, existing.getClass().getName(), name);
            return;
        }

        modules.add(module);

        // Plugins register from a Fabric entrypoint, long after the config was read,
        // so replay this module's stored state now rather than waiting for a reload.
        loadStateInto(module);
    }

    /** @return true if a module already uses this name (check before registering). */
    public boolean isNameTaken(String name) {
        return getModuleByName(name) != null;
    }

    public List<HudModule> getModules() {
        return modules;
    }

    public HudModule getModuleByName(String name) {
        for (HudModule module : modules) {
            if (module.getName().equals(name)) {
                return module;
            }
        }
        return null;
    }

    // --- Third-party module state ---

    /**
     * Point {@link SimpleCPSConfig}'s collect/distribute hooks at this manager. Call
     * once at startup, before plugins register, so any config save they trigger already
     * captures module state.
     */
    public void installConfigHooks() {
        SimpleCPSConfig.stateCollector = this::collectModuleState;
        SimpleCPSConfig.stateDistributor = this::distributeModuleState;
    }

    /**
     * Ask every module for its state and stash it on the live config, then refresh the
     * roster of module names this install knows about. Blobs belonging to modules that
     * are <em>not</em> registered here are left untouched — that is what lets a config
     * carry a friend's plugin settings through unharmed.
     */
    public void collectModuleState() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (config == null) return;
        config.ensureCollections();

        List<String> roster = new ArrayList<>();
        for (HudModule module : modules) {
            String name = module.getName();
            if (name == null || name.isEmpty()) continue;
            roster.add(name);
            try {
                JsonElement state = module.saveState();
                if (state == null || state.isJsonNull()) {
                    config.moduleData.remove(name);
                } else {
                    config.moduleData.put(name, state);
                }
            } catch (Exception e) {
                LOGGER.error("Module \"{}\" threw while saving its state; leaving the stored copy alone.", name, e);
            }
        }
        config.knownModules = roster;
    }

    /** Push stored state back into every module. */
    public void distributeModuleState() {
        for (HudModule module : modules) {
            loadStateInto(module);
        }
    }

    private void loadStateInto(HudModule module) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (config == null) return;
        config.ensureCollections();

        String name = module.getName();
        if (name == null || name.isEmpty()) return;

        JsonElement state = config.moduleData.get(name);
        if (state == null) return; // Nothing stored: leave the module exactly as it is.

        try {
            module.loadState(state);
        } catch (Exception e) {
            LOGGER.error("Module \"{}\" threw while loading its state; it keeps its current settings.", name, e);
        }
    }
    
    public Map<String, ModuleBounds> getModuleBounds() {
        return moduleBounds;
    }

    /**
     * Modules that threw while ticking, being measured or drawing. They are left out
     * from then on, until the game restarts.
     *
     * <p>Without this one broken module -- most likely a plugin built against a class
     * this build no longer has -- crashed the whole game on its first frame, and the
     * crash report named this mod. Losing one HUD element and logging why is a far
     * smaller failure than losing the session.
     */
    private final Set<HudModule> failed =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    /** Whether we already logged that layout failed; it would otherwise log every frame. */
    private boolean layoutFailureLogged = false;

    private void fail(HudModule module, String while_, Throwable error) {
        if (!failed.add(module)) return;
        String name;
        try {
            name = module.getName();
        } catch (RuntimeException | LinkageError e) {
            name = "?";
        }
        LOGGER.error("Module \"{}\" ({}) threw while {}; it is switched off until the game restarts.",
            name, module.getClass().getName(), while_, error);
    }

    /**
     * The modules safe to lay out this frame. Each is asked everything the layout
     * engine will ask it first, so a module that throws there is dropped here, by
     * name, rather than taking the whole layout down with it.
     */
    private List<HudModule> healthyModules() {
        List<HudModule> healthy = new ArrayList<>(modules.size());
        for (HudModule module : modules) {
            if (failed.contains(module)) continue;
            try {
                module.getName();
                module.isEnabled();
                module.getPositionType();
                module.getXOffset();
                module.getYOffset();
                module.getWidth();
                module.getHeight();
                module.getLayoutWidth();
                module.getLayoutHeight();
                healthy.add(module);
            } catch (RuntimeException | LinkageError e) {
                fail(module, "being measured", e);
            }
        }
        return healthy;
    }

    public void tickAll(Minecraft client) {
        for (HudModule module : modules) {
            if (failed.contains(module)) continue;
            try {
                module.tick(client);
            } catch (RuntimeException | LinkageError e) {
                fail(module, "ticking", e);
            }
        }
    }

    /** Draw one module, dropping it if it throws. */
    private void draw(HudModule module, GuiGraphicsExtractor drawContext, float tickDelta) {
        try {
            module.extractRenderState(drawContext, tickDelta);
        } catch (RuntimeException | LinkageError e) {
            fail(module, "drawing", e);
        }
    }

    public void renderAll(GuiGraphicsExtractor drawContext, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        // The editor needs the HUD on screen to edit it. It turns F1 off while it is
        // open (see HudEditorScreen), which is what actually gets us called: vanilla
        // skips this whole layer while the HUD is hidden.
        boolean isEditor = client.gui.screen() instanceof HudEditorScreen;
        if (client.gui.hud.isHidden() && !isEditor) return;

        moduleBounds.clear();

        int screenWidth = drawContext.guiWidth();
        int screenHeight = drawContext.guiHeight();

        Set<String> renderedNow = new HashSet<>();

        List<HudPlacementResolver.ModulePlacement> placements;
        try {
            placements = HudPlacementResolver.resolveModules(healthyModules(), screenWidth, screenHeight, isEditor);
        } catch (RuntimeException | LinkageError e) {
            // A module that passed healthyModules() and still threw. Skip the frame
            // rather than crash; the log says so once.
            if (!layoutFailureLogged) {
                layoutFailureLogged = true;
                LOGGER.error("Laying out the HUD failed; skipping frames until it succeeds.", e);
            }
            return;
        }

        for (HudPlacementResolver.ModulePlacement placement : placements) {
            if (!placement.shouldRender) continue;

            HudModule module = placement.module;
            String name = module.getName();
            renderedNow.add(name);

            ModuleBounds bounds = new ModuleBounds(placement.x, placement.y, placement.w, placement.h);
            moduleBounds.put(name, bounds);
            lastBounds.put(name, bounds);

            float alpha = 1f;
            if (module.supportsFade()) {
                Anim anim = moduleAlpha.computeIfAbsent(name, k -> {
                    Anim a = new Anim(0f);
                    a.snap(0f);
                    return a;
                });
                if (isEditor) {
                    anim.snap(1f);
                    alpha = 1f;
                } else {
                    alpha = anim.update(1f, FADE_DURATION, Easings::sineInOut);
                }
            }

            module.setRenderAlpha(alpha);
            module.setRenderPosition(placement.x, placement.y);
            draw(module, drawContext, tickDelta);
        }

        // Ghost fade-out: modules that were rendering but no longer are keep
        // drawing at their last position until their alpha decays to ~0. Only
        // applies to fade-capable modules during normal gameplay (not the editor).
        if (!isEditor) {
            for (HudModule module : modules) {
                if (failed.contains(module) || !module.supportsFade()) continue;
                String name = module.getName();
                if (renderedNow.contains(name)) continue;

                Anim anim = moduleAlpha.get(name);
                if (anim == null) continue;

                // Right after closing the editor every module was forced to full
                // alpha; snap the now-hidden ones off instead of flashing a fade.
                if (wasEditor) {
                    anim.snap(0f);
                    continue;
                }

                float alpha = anim.update(0f, FADE_DURATION, Easings::sineInOut);
                if (alpha <= 0.02f) {
                    anim.snap(0f);
                    continue;
                }
                ModuleBounds bounds = lastBounds.get(name);
                if (bounds == null) continue;

                module.setRenderAlpha(alpha);
                module.setRenderPosition(bounds.x, bounds.y);
                draw(module, drawContext, tickDelta);
            }
        }

        wasEditor = isEditor;
    }
}
