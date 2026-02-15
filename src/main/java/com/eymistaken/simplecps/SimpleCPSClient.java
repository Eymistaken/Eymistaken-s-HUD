package com.eymistaken.simplecps;

import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.modules.ComboModule;
import com.eymistaken.simplecps.modules.CpsModule;
import com.eymistaken.simplecps.modules.FpsModule;
import com.eymistaken.simplecps.modules.KeystrokesModule;
import com.eymistaken.simplecps.modules.PingModule;
import com.eymistaken.simplecps.modules.ReachModule;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleCPSClient implements ClientModInitializer {

    // Modules List
    private static final List<HudModule> modules = new ArrayList<>();

    // Module Bounds for Editor
    public static class ModuleBounds {
        public int x, y, w, h;
        public ModuleBounds(int x, int y, int w, int h) {
            this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }
    public static final Map<String, ModuleBounds> MODULE_BOUNDS = new HashMap<>();

    @Override
    public void onInitializeClient() {
        // Load Config
        SimpleCPSConfig.load();
        
        // Initialize Modules
        modules.clear();
        modules.add(new CpsModule());
        modules.add(new FpsModule());
        modules.add(new PingModule());
        modules.add(new ComboModule());
        modules.add(new KeystrokesModule());
        modules.add(new ReachModule());
        
        System.out.println("Eymistaken's HUD (Standalone) Initialized with Modular Architecture!");
    }
    
    public static void onClientTick(MinecraftClient client) {
        for (HudModule module : modules) {
            module.tick(client);
        }
    }

    public static void onHudRender(DrawContext drawContext, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return; 
        
        boolean isEditor = client.currentScreen instanceof HudEditorScreen;
        if (client.options.hudHidden && !isEditor) return; 
        
        MODULE_BOUNDS.clear();

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
            
            MODULE_BOUNDS.put(module.getName(), new ModuleBounds(finalX, finalY, w, h));
            
            module.setRenderPosition(finalX, finalY);
            module.render(drawContext, tickDelta); 
        }
    }
}
