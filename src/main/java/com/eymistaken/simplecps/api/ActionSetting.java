package com.eymistaken.simplecps.api;

public class ActionSetting extends HudModuleSetting {
    public final Runnable action;
    /**
     * ARGB color for the row's label in the editor's context menu, or 0 for the
     * default. An action opens something rather than changing a value, and coloring
     * it is how a menu row says so at a glance.
     */
    public final int color;

    public ActionSetting(String label, Runnable action) {
        this(label, 0, action);
    }

    public ActionSetting(String label, int color, Runnable action) {
        super(label);
        this.action = action;
        this.color = color;
    }
}
