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

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        int screenWidth = drawContext.guiWidth();
        int screenHeight = drawContext.guiHeight();

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
                    if (!detached) {
                        int currentBottom = (module instanceof ArmorModule && bottomLeftStack == screenHeight - stackGap) ? screenHeight : bottomLeftStack;
                        currentBottom -= usedHeight;
                        y = currentBottom;
                        bottomLeftStack = currentBottom - gap;
                    } else {
                        y = screenHeight - usedHeight - stackGap;
                    }
                }
                case BOTTOM_RIGHT -> { 
                    x = screenWidth - w - stackGap; 
                    if (!detached) {
                        int currentBottom = (module instanceof ArmorModule && bottomRightStack == screenHeight - stackGap) ? screenHeight : bottomRightStack;
                        currentBottom -= usedHeight;
                        y = currentBottom;
                        bottomRightStack = currentBottom - gap;
                    } else {
                        y = screenHeight - usedHeight - stackGap;
                    }
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
            module.extractRenderState(drawContext, tickDelta); 
        }
    }
}
