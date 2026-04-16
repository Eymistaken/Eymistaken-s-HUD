package com.eymistaken.simplecps.api;

public class ActionSetting extends HudModuleSetting {
    public final Runnable action;

    public ActionSetting(String label, Runnable action) {
        super(label);
        this.action = action;
    }
}
