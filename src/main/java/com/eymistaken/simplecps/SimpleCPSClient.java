package com.eymistaken.simplecps;

import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.modules.ComboModule;
import com.eymistaken.simplecps.modules.CpsModule;
import com.eymistaken.simplecps.modules.FpsModule;
import com.eymistaken.simplecps.modules.KeystrokesModule;
import com.eymistaken.simplecps.modules.PingModule;
import com.eymistaken.simplecps.modules.ReachModule;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.option.KeyBinding;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleCPSClient implements ClientModInitializer {

    // Removed Modules List and MODULE_BOUNDS - Migrated to HudModuleManager
    // Kept Class Definition clean

    public static KeyBinding openEditorKey;

    @Override
    public void onInitializeClient() {
        // Load Config
        SimpleCPSConfig.load();
        
        // Initialize Modules via Manager
        HudModuleManager manager = HudModuleManager.getInstance();

        // Load Plugins (Entrypoints)
        try {
            FabricLoader.getInstance().getEntrypointContainers("eymistaken_hud", com.eymistaken.simplecps.api.EymistakenHudPlugin.class)
                .forEach(entrypoint -> {
                    try {
                        entrypoint.getEntrypoint().registerHudModules(manager);
                    } catch (Exception e) {
                        System.err.println("Failed to load HUD plugin from " + entrypoint.getProvider().getMetadata().getId());
                        e.printStackTrace();
                    }
                });
        } catch (Exception e) {
            System.err.println("Error initializing HUD plugins");
            e.printStackTrace();
        }
        
        System.out.println("Eymistaken's HUD (Standalone) Initialized with Modular Architecture!");

        // Register Keybinding
        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.simplecps.open_editor",
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                new KeyBinding.Category(Identifier.of("simplecps", "categories.eymistaken"))
        ));
    }
    
    public static void onClientTick(MinecraftClient client) {
        HudModuleManager.getInstance().tickAll(client);

        while (openEditorKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new com.eymistaken.simplecps.gui.HudEditorScreen(null));
            }
        }
    }

    public static void onHudRender(DrawContext drawContext, float tickDelta) {
        HudModuleManager.getInstance().renderAll(drawContext, tickDelta);
    }
}
