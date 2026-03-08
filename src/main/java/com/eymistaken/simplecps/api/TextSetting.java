package com.eymistaken.simplecps.api;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class TextSetting extends HudModuleSetting {
    public final Supplier<String> getter;
    public final Consumer<String> setter;

    public TextSetting(String label, Supplier<String> getter, Consumer<String> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
    }
}
