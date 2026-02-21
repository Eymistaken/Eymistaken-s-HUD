package com.eymistaken.simplecps;

import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.modules.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    public Map<String, ModuleBounds> getModuleBounds() {
        return moduleBounds;
    }

    public void tickAll(MinecraftClient client) {
        for (HudModule module : modules) {
            module.tick(client);
        }
    }

    public void renderAll(DrawContext drawContext, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return; 
        
        boolean isEditor = client.currentScreen instanceof HudEditorScreen;
        if (client.options.hudHidden && !isEditor) return; 
        
        moduleBounds.clear();

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        int screenWidth = drawContext.getScaledWindowWidth();
        int screenHeight = drawContext.getScaledWindowHeight();

        // Stacking Variables
        int stackGap = 5;
        int topLeftStack = stackGap;
        int topRightStack = stackGap;
        int bottomLeftStack = screenHeight - stackGap;
        int bottomRightStack = screenHeight - stackGap;
        int gap = 4;

        for (HudModule module : modules) {
             if (!module.isEnabled() && !isEditor) continue;
             
             // Get Dimensions
             int w = module.getWidth();
             int h = module.getHeight();
             
             // If module has no size (e.g. Reach with no hit), skip rendering/stacking logic if logically hidden
             if (w == 0 || h == 0) continue;

             // Get Config/Position
             SimpleCPSConfig.Position pos = module.getPositionType();
             int xOff = module.getXOffset();
             int yOff = module.getYOffset();
             
             boolean detached = xOff != 0 || yOff != 0;
             
             int x = 0, y = 0;
             int usedHeight = h; 
             
             switch (pos) {
                case TOP_LEFT -> { 
                    x = stackGap; 
                    y = detached ? stackGap : topLeftStack; 
                    if(!detached) topLeftStack += usedHeight + gap; 
                }
                case TOP_RIGHT -> { 
                    x = screenWidth - w - stackGap; 
                    y = detached ? stackGap : topRightStack; 
                    if(!detached) topRightStack += usedHeight + gap; 
                }
                case BOTTOM_LEFT -> { 
                    x = stackGap; 
                    if (!detached) bottomLeftStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomLeftStack;
                    if (!detached) bottomLeftStack -= gap;
                }
                case BOTTOM_RIGHT -> { 
                    x = screenWidth - w - stackGap; 
                    if (!detached) bottomRightStack -= usedHeight;
                    y = detached ? (screenHeight - usedHeight - stackGap) : bottomRightStack;
                    if (!detached) bottomRightStack -= gap;
                }
                case CENTER -> { 
                    x = (screenWidth - w) / 2; 
                    y = (screenHeight - h) / 2; 
                    
                    if (module instanceof ReachModule && !detached) {
                         y += 15;
                    }
                }
            }
            
            int finalX = x + xOff;
            int finalY = y + yOff;
            
            moduleBounds.put(module.getName(), new ModuleBounds(finalX, finalY, w, h));
            
            module.setRenderPosition(finalX, finalY);
            module.render(drawContext, tickDelta); 
        }
    }
}
