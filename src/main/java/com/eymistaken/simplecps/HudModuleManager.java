package com.eymistaken.simplecps;

import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.modules.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class HudModuleManager {

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

    public void registerModule(HudModule module) {
        modules.add(module);
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
    
    public Map<String, ModuleBounds> getModuleBounds() {
        return moduleBounds;
    }

    public void tickAll(Minecraft client) {
        for (HudModule module : modules) {
            module.tick(client);
        }
    }

    public void renderAll(GuiGraphicsExtractor drawContext, float tickDelta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gui.hud.isHidden()) return; 
        
        boolean isEditor = client.gui.screen() instanceof HudEditorScreen;
        if (client.gui.hud.isHidden() && !isEditor) return; 
        
        moduleBounds.clear();

        int screenWidth = drawContext.guiWidth();
        int screenHeight = drawContext.guiHeight();

        for (HudPlacementResolver.ModulePlacement placement : HudPlacementResolver.resolveModules(modules, screenWidth, screenHeight, isEditor)) {
            if (!placement.shouldRender) continue;

            moduleBounds.put(placement.module.getName(), new ModuleBounds(placement.x, placement.y, placement.w, placement.h));

            placement.module.setRenderPosition(placement.x, placement.y);
            placement.module.extractRenderState(drawContext, tickDelta);
        }
    }
}
