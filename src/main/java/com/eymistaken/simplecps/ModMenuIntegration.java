package com.eymistaken.simplecps;

import com.eymistaken.simplecps.gui.settings.HudSettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HudSettingsScreen::new;
    }
}