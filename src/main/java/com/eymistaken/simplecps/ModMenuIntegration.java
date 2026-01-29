package com.eymistaken.simplecps;

import com.eymistaken.simplecps.gui.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cloth-config")) {
                return com.eymistaken.simplecps.ClothConfigFactory.create(parent);
            } else {
                return new com.eymistaken.simplecps.gui.ConfigScreen(parent);
            }
        };
    }
}