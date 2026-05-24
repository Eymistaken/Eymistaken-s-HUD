package com.eymistaken.simplecps.api;

import java.util.List;
import java.util.Collections;

public interface IHudElement {
    int getX();
    int getY();
    int getWidth();
    int getHeight();
    
    int getXOffset();
    int getYOffset();
    void setXOffset(int x);
    void setYOffset(int y);
    
    String getName();
    
    default int getScale() {
        return 100;
    }
    
    default void setScale(int scale) {}
    
    default void resetToDefaults() {}
    
    default List<HudModuleSetting> getContextMenuSettings() {
        return Collections.emptyList();
    }
    
    default List<IHudElement> getSubElements() {
        return Collections.emptyList();
    }
    
    default void onPositionUpdated() {}
}
