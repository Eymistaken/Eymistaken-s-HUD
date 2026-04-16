package com.eymistaken.simplecps.api;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SliderSetting extends HudModuleSetting {
    public final int min;
    public final int max;
    public final int defaultValue;
    public final Supplier<Integer> getter;
    public final Consumer<Integer> setter;

    public SliderSetting(String label, int min, int max, int defaultValue, Supplier<Integer> getter, Consumer<Integer> setter) {
        super(label);
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.getter = getter;
        this.setter = setter;
    }
}
