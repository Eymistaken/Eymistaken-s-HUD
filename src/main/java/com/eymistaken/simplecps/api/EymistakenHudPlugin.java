package com.eymistaken.simplecps.api;

import com.eymistaken.simplecps.HudModuleManager;

public interface EymistakenHudPlugin {
    void registerHudModules(HudModuleManager manager);

    /**
     * Subscribe to HUD events. Called once at client init, right after
     * {@link #registerHudModules}. Default no-op, so plugins written before this
     * existed keep compiling and running unchanged.
     *
     * @see EymistakenHudEventBus
     */
    default void registerEvents(EymistakenHudEventBus bus) {}
}
