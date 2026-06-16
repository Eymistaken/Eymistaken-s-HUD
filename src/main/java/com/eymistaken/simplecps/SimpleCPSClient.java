package com.eymistaken.simplecps;

import com.eymistaken.simplecps.gui.HudEditorScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class SimpleCPSClient implements ClientModInitializer {
    private static final Identifier HUD_LAYER_ID = Identifier.fromNamespaceAndPath("simplecps", "main_hud");

    public static KeyMapping openEditorKey;

    @Override
    public void onInitializeClient() {
        SimpleCPSConfig.load();

        HudModuleManager manager = HudModuleManager.getInstance();

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

        openEditorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.simplecps.open_editor",
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            new KeyMapping.Category(Identifier.fromNamespaceAndPath("simplecps", "categories.eymistaken"))
        ));

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            HUD_LAYER_ID,
            (graphics, tickCounter) -> onHudRender(graphics, tickCounter.getGameTimeDeltaPartialTick(false))
        );
    }

    public static void onClientTick(Minecraft client) {
        HudModuleManager.getInstance().tickAll(client);

        while (openEditorKey.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new HudEditorScreen(null));
            }
        }
    }

    public static void onHudRender(GuiGraphicsExtractor drawContext, float tickDelta) {
        HudModuleManager.getInstance().renderAll(drawContext, tickDelta);
    }
}
