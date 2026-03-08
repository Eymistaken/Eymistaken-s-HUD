package com.eymistaken.simplecps.api;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ColorSetting extends HudModuleSetting {
    public final Supplier<Integer> getter;
    public final Consumer<Integer> setter;

    public ColorSetting(String label, Supplier<Integer> getter, Consumer<Integer> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
    }
}
