package com.eymistaken.simplecps.api;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CycleSetting extends HudModuleSetting {
    public final List<String> options;
    public final Supplier<Integer> getter;
    public final Consumer<Integer> setter;

    public CycleSetting(String label, List<String> options, Supplier<Integer> getter, Consumer<Integer> setter) {
        super(label);
        this.options = options;
        this.getter = getter;
        this.setter = setter;
    }
}
