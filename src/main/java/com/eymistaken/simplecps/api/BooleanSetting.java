package com.eymistaken.simplecps.api;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class BooleanSetting extends HudModuleSetting {
    public final Supplier<Boolean> getter;
    public final Consumer<Boolean> setter;

    public BooleanSetting(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
    }
}
