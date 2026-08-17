package com.eymistaken.simplecps.gui.settings;

import com.eymistaken.simplecps.ComboTracker;
import com.eymistaken.simplecps.HudModuleManager;
import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.SimpleCPSConfig.Position;
import com.eymistaken.simplecps.api.ActionSetting;
import com.eymistaken.simplecps.api.BooleanSetting;
import com.eymistaken.simplecps.api.ColorSetting;
import com.eymistaken.simplecps.api.CycleSetting;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.api.HudModuleSetting;
import com.eymistaken.simplecps.api.SettingsTab;
import com.eymistaken.simplecps.api.SliderSetting;
import com.eymistaken.simplecps.api.TextSetting;
import com.eymistaken.simplecps.modules.ArmorModule;
import com.eymistaken.simplecps.modules.ComboModule;
import com.eymistaken.simplecps.modules.CpsModule;
import com.eymistaken.simplecps.modules.FpsModule;
import com.eymistaken.simplecps.modules.KeystrokesModule;
import com.eymistaken.simplecps.modules.PingModule;
import com.eymistaken.simplecps.modules.ReachModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Declarative description of the settings screen: pages (one per module, plus
 * GENERAL) → tabs → rows.
 *
 * <p>Two rules make the whole thing hold together:
 *
 * <ol>
 * <li><b>Accessors read {@link SimpleCPSConfig#instance} at call time.</b> Applying
 *     a preset, a share code or a server config replaces that object wholesale, and
 *     the screen has to keep showing the truth without rebuilding anything.</li>
 * <li><b>Defaults come from the field initialisers.</b> Every built-in row is
 *     declared with a {@code Function<SimpleCPSConfig, T>}, so its default is simply
 *     that function applied to {@link #DEFAULTS} — a pristine {@code new
 *     SimpleCPSConfig()}. Nothing restates a default by hand, which is how the old
 *     Cloth screen ended up disagreeing with the config in half a dozen places.</li>
 * </ol>
 */
public final class SettingsSchema {

    /** A never-mutated config used purely as the source of every default value. */
    public static final SimpleCPSConfig DEFAULTS = new SimpleCPSConfig();

    private static final int OFFSET_LIMIT = 10_000;

    private SettingsSchema() {}

    /** Actions the schema needs from the screen that owns it. */
    public interface Host {
        void openHudEditor();
        void openKeystrokesDesigner();
        void resetHudLayout();
    }

    // --- Rows --------------------------------------------------------------

    public abstract static sealed class Row
        permits BoolRow, IntRow, TextRow, ChoiceRow, ColorRow, PositionRow, ActionRow {

        public final String id;
        public final String label;
        public final String desc;

        protected Row(String id, String label, String desc) {
            this.id = id;
            this.label = label;
            this.desc = desc == null ? "" : desc;
        }

        /** True when a default is known and the live value differs from it. */
        public abstract boolean isModified();

        /** Restore the default. No-op when there is no known default. */
        public abstract void reset();

        /** Rendered in the INFO panel, or "" when this row has no meaningful default. */
        public abstract String defaultText();
    }

    public static final class BoolRow extends Row {
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;
        private final Boolean fallback;

        private BoolRow(String id, String label, String desc,
                        Supplier<Boolean> getter, Consumer<Boolean> setter, Boolean fallback) {
            super(id, label, desc);
            this.getter = getter;
            this.setter = setter;
            this.fallback = fallback;
        }

        public boolean get() { return Boolean.TRUE.equals(getter.get()); }
        public void set(boolean value) { setter.accept(value); }
        public void toggle() { set(!get()); }

        @Override public boolean isModified() { return fallback != null && get() != fallback; }
        @Override public void reset() { if (fallback != null) set(fallback); }
        @Override public String defaultText() { return fallback == null ? "" : (fallback ? "ON" : "OFF"); }
    }

    public static final class IntRow extends Row {
        /** SLIDER drags across a range; STEPPER is a -/+ spinner with a typed field. */
        public enum Style { SLIDER, STEPPER }

        public final Style style;
        public final int min;
        public final int max;
        private final Supplier<Integer> getter;
        private final Consumer<Integer> setter;
        private final Integer fallback;

        private IntRow(String id, String label, String desc, Style style, int min, int max,
                       Supplier<Integer> getter, Consumer<Integer> setter, Integer fallback) {
            super(id, label, desc);
            this.style = style;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            this.fallback = fallback;
        }

        public int get() {
            Integer v = getter.get();
            return v == null ? min : v;
        }

        public void set(int value) {
            setter.accept(Math.max(min, Math.min(max, value)));
        }

        /** Where the value sits in its range, in {@code [0,1]}. */
        public float fraction() {
            return max == min ? 0f : (get() - min) / (float) (max - min);
        }

        public void setFromFraction(float fraction) {
            set(Math.round(min + fraction * (max - min)));
        }

        @Override public boolean isModified() { return fallback != null && get() != fallback; }
        @Override public void reset() { if (fallback != null) set(fallback); }
        @Override public String defaultText() { return fallback == null ? "" : String.valueOf(fallback); }
    }

    public static final class TextRow extends Row {
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private final String fallback;

        private TextRow(String id, String label, String desc,
                        Supplier<String> getter, Consumer<String> setter, String fallback) {
            super(id, label, desc);
            this.getter = getter;
            this.setter = setter;
            this.fallback = fallback;
        }

        public String get() {
            String v = getter.get();
            return v == null ? "" : v;
        }

        public void set(String value) { setter.accept(value == null ? "" : value); }

        @Override public boolean isModified() { return fallback != null && !get().equals(fallback); }
        @Override public void reset() { if (fallback != null) set(fallback); }
        @Override public String defaultText() {
            if (fallback == null) return "";
            return fallback.isEmpty() ? "(empty)" : "\"" + fallback + "\"";
        }
    }

    /** Cycles through a fixed list of labels; backs both Java enums and int modes. */
    public static final class ChoiceRow extends Row {
        public final List<String> options;
        private final Supplier<Integer> getter;
        private final Consumer<Integer> setter;
        private final Integer fallback;

        private ChoiceRow(String id, String label, String desc, List<String> options,
                          Supplier<Integer> getter, Consumer<Integer> setter, Integer fallback) {
            super(id, label, desc);
            this.options = options;
            this.getter = getter;
            this.setter = setter;
            this.fallback = fallback;
        }

        public int index() {
            Integer v = getter.get();
            if (v == null || options.isEmpty()) return 0;
            return Math.floorMod(v, options.size());
        }

        public String value() { return options.isEmpty() ? "" : options.get(index()); }

        public void cycle(int direction) {
            if (options.isEmpty()) return;
            setter.accept(Math.floorMod(index() + direction, options.size()));
        }

        @Override public boolean isModified() { return fallback != null && index() != fallback; }
        @Override public void reset() { if (fallback != null) setter.accept(fallback); }
        @Override public String defaultText() {
            if (fallback == null || fallback < 0 || fallback >= options.size()) return "";
            return options.get(fallback);
        }
    }

    /** An RGB color, stored without an alpha byte the way the config does. */
    public static final class ColorRow extends Row {
        private final Supplier<Integer> getter;
        private final Consumer<Integer> setter;
        private final Integer fallback;

        private ColorRow(String id, String label, String desc,
                         Supplier<Integer> getter, Consumer<Integer> setter, Integer fallback) {
            super(id, label, desc);
            this.getter = getter;
            this.setter = setter;
            this.fallback = fallback;
        }

        public int get() {
            Integer v = getter.get();
            return (v == null ? 0 : v) & 0xFFFFFF;
        }

        public void set(int rgb) { setter.accept(rgb & 0xFFFFFF); }

        public String hex() { return String.format("#%06X", get()); }

        @Override public boolean isModified() { return fallback != null && get() != (fallback & 0xFFFFFF); }
        @Override public void reset() { if (fallback != null) set(fallback); }
        @Override public String defaultText() {
            return fallback == null ? "" : String.format("#%06X", fallback & 0xFFFFFF);
        }
    }

    /** The five anchors, picked from a 3x3 grid. */
    public static final class PositionRow extends Row {
        private final Supplier<Position> getter;
        private final Consumer<Position> setter;
        private final Position fallback;

        private PositionRow(String id, String label, String desc,
                            Supplier<Position> getter, Consumer<Position> setter, Position fallback) {
            super(id, label, desc);
            this.getter = getter;
            this.setter = setter;
            this.fallback = fallback;
        }

        public Position get() {
            Position v = getter.get();
            return v == null ? Position.TOP_LEFT : v;
        }

        public void set(Position value) { if (value != null) setter.accept(value); }

        @Override public boolean isModified() { return fallback != null && get() != fallback; }
        @Override public void reset() { if (fallback != null) set(fallback); }
        @Override public String defaultText() { return fallback == null ? "" : fallback.name(); }
    }

    public static final class ActionRow extends Row {
        public final String button;
        public final boolean primary;
        private final Runnable action;

        private ActionRow(String id, String label, String desc, String button, boolean primary, Runnable action) {
            super(id, label, desc);
            this.button = button;
            this.primary = primary;
            this.action = action;
        }

        public void run() { if (action != null) action.run(); }

        @Override public boolean isModified() { return false; }
        @Override public void reset() {}
        @Override public String defaultText() { return ""; }
    }

    // --- Pages -------------------------------------------------------------

    public record Tab(String id, String name, List<Row> rows) {}

    public static final class Page {
        public final String id;
        public final String name;
        public final String desc;
        public final List<Tab> tabs;
        /** GENERAL: no on/off state, shows "GLOBAL" in the header badge. */
        public final boolean global;
        /** Registered by a third-party plugin rather than built in. */
        public final boolean plugin;

        private final Supplier<Boolean> enabledGetter;
        private final Consumer<Boolean> enabledSetter;

        private Page(String id, String name, String desc, List<Tab> tabs, boolean global, boolean plugin,
                     Supplier<Boolean> enabledGetter, Consumer<Boolean> enabledSetter) {
            this.id = id;
            this.name = name;
            this.desc = desc;
            this.tabs = tabs;
            this.global = global;
            this.plugin = plugin;
            this.enabledGetter = enabledGetter;
            this.enabledSetter = enabledSetter;
        }

        public boolean isEnabled() {
            return global || enabledGetter == null || Boolean.TRUE.equals(enabledGetter.get());
        }

        /** False for GENERAL and for plugin modules, which expose no enable setter. */
        public boolean canToggle() {
            return !global && enabledSetter != null;
        }

        public void toggle() {
            if (canToggle()) enabledSetter.accept(!isEnabled());
        }
    }

    // --- Row builders ------------------------------------------------------
    //
    // Each takes config-parameterised accessors, binds the live one to
    // SimpleCPSConfig.instance and derives the default from DEFAULTS.

    private static BoolRow bool(String id, String label, String desc,
                                Function<SimpleCPSConfig, Boolean> get,
                                BiConsumer<SimpleCPSConfig, Boolean> set) {
        return new BoolRow(id, label, desc,
            () -> get.apply(SimpleCPSConfig.instance),
            v -> set.accept(SimpleCPSConfig.instance, v),
            get.apply(DEFAULTS));
    }

    private static IntRow slider(String id, String label, String desc, int min, int max,
                                 Function<SimpleCPSConfig, Integer> get,
                                 BiConsumer<SimpleCPSConfig, Integer> set) {
        return new IntRow(id, label, desc, IntRow.Style.SLIDER, min, max,
            () -> get.apply(SimpleCPSConfig.instance),
            v -> set.accept(SimpleCPSConfig.instance, v),
            get.apply(DEFAULTS));
    }

    private static IntRow stepper(String id, String label, String desc, int min, int max,
                                  Function<SimpleCPSConfig, Integer> get,
                                  BiConsumer<SimpleCPSConfig, Integer> set) {
        return new IntRow(id, label, desc, IntRow.Style.STEPPER, min, max,
            () -> get.apply(SimpleCPSConfig.instance),
            v -> set.accept(SimpleCPSConfig.instance, v),
            get.apply(DEFAULTS));
    }

    private static TextRow text(String id, String label, String desc,
                                Function<SimpleCPSConfig, String> get,
                                BiConsumer<SimpleCPSConfig, String> set) {
        String fallback = get.apply(DEFAULTS);
        return new TextRow(id, label, desc,
            () -> get.apply(SimpleCPSConfig.instance),
            v -> set.accept(SimpleCPSConfig.instance, v),
            fallback == null ? "" : fallback);
    }

    private static ColorRow color(String id, String label, String desc,
                                  Function<SimpleCPSConfig, Integer> get,
                                  BiConsumer<SimpleCPSConfig, Integer> set) {
        return new ColorRow(id, label, desc,
            () -> get.apply(SimpleCPSConfig.instance),
            v -> set.accept(SimpleCPSConfig.instance, v),
            get.apply(DEFAULTS));
    }

    private static PositionRow position(String id, String desc,
                                        Function<SimpleCPSConfig, Position> get,
                                        BiConsumer<SimpleCPSConfig, Position> set) {
        return new PositionRow(id, "Position", desc,
            () -> get.apply(SimpleCPSConfig.instance),
            v -> set.accept(SimpleCPSConfig.instance, v),
            get.apply(DEFAULTS));
    }

    /** Cycle over a Java enum, labeled with the constant names. */
    private static <E extends Enum<E>> ChoiceRow enumRow(String id, String label, String desc, Class<E> type,
                                                         Function<SimpleCPSConfig, E> get,
                                                         BiConsumer<SimpleCPSConfig, E> set) {
        E[] values = type.getEnumConstants();
        List<String> names = Arrays.stream(values).map(Enum::name).toList();
        E fallbackValue = get.apply(DEFAULTS);
        return new ChoiceRow(id, label, desc, names,
            () -> {
                E v = get.apply(SimpleCPSConfig.instance);
                return v == null ? 0 : v.ordinal();
            },
            i -> set.accept(SimpleCPSConfig.instance, values[Math.floorMod(i, values.length)]),
            fallbackValue == null ? 0 : fallbackValue.ordinal());
    }

    /** Cycle over an int-backed mode with explicit labels. */
    private static ChoiceRow choice(String id, String label, String desc, List<String> options,
                                    Function<SimpleCPSConfig, Integer> get,
                                    BiConsumer<SimpleCPSConfig, Integer> set) {
        return new ChoiceRow(id, label, desc, options,
            () -> get.apply(SimpleCPSConfig.instance),
            i -> set.accept(SimpleCPSConfig.instance, i),
            get.apply(DEFAULTS));
    }

    private static ActionRow action(String id, String label, String desc, String button, boolean primary, Runnable run) {
        return new ActionRow(id, label, desc, button, primary, run);
    }

    /** The four rows every module's POSITION tab starts with. */
    private static List<Row> placement(String prefix, String moduleName,
                                       Function<SimpleCPSConfig, Position> getPos,
                                       BiConsumer<SimpleCPSConfig, Position> setPos,
                                       Function<SimpleCPSConfig, Integer> getX,
                                       BiConsumer<SimpleCPSConfig, Integer> setX,
                                       Function<SimpleCPSConfig, Integer> getY,
                                       BiConsumer<SimpleCPSConfig, Integer> setY,
                                       Function<SimpleCPSConfig, Integer> getScale,
                                       BiConsumer<SimpleCPSConfig, Integer> setScale) {
        return List.of(
            position(prefix + ".position", "Anchor position for " + moduleName + ".", getPos, setPos),
            stepper(prefix + ".x", "X Offset", "Horizontal offset from the anchor, in pixels.",
                -OFFSET_LIMIT, OFFSET_LIMIT, getX, setX),
            stepper(prefix + ".y", "Y Offset", "Vertical offset from the anchor, in pixels.",
                -OFFSET_LIMIT, OFFSET_LIMIT, getY, setY),
            slider(prefix + ".scale", "Scale %", "Size of the " + moduleName + " display.",
                50, 300, getScale, setScale)
        );
    }

    private static final String BG_SHOW_DESC = "Show the background box behind the text.";
    private static final String BG_COLOR_DESC = "Color of the background box.";
    private static final String BG_OPACITY_DESC = "Transparency of the background box.";
    private static final String RAINBOW_DESC = "Enable the rainbow text effect. Overrides Text Color.";
    private static final String TEXT_COLOR_DESC = "Color of the text.";

    // --- Built-in pages ----------------------------------------------------

    private static Page cps() {
        return new Page("cps", "CPS",
            "Clicks per second counter for left and right mouse buttons.",
            List.of(
                new Tab("pos", "POSITION", placement("cps", "CPS",
                    c -> c.position, (c, v) -> c.position = v,
                    c -> c.xOffset, (c, v) -> c.xOffset = v,
                    c -> c.yOffset, (c, v) -> c.yOffset = v,
                    c -> c.scale, (c, v) -> c.scale = v)),
                new Tab("style", "STYLE", List.of(
                    bool("cps.rightClick", "Right Click CPS", "Show right click CPS alongside left click.",
                        c -> c.rightClickCps, (c, v) -> c.rightClickCps = v),
                    color("cps.textColor", "Text Color", TEXT_COLOR_DESC,
                        c -> c.textColor, (c, v) -> c.textColor = v),
                    bool("cps.rainbow", "Rainbow", RAINBOW_DESC,
                        c -> c.rainbow, (c, v) -> c.rainbow = v))),
                new Tab("bg", "BACKGROUND", List.of(
                    bool("cps.showBg", "Show Background", BG_SHOW_DESC,
                        c -> c.cpsShowBackground, (c, v) -> c.cpsShowBackground = v),
                    color("cps.bgColor", "Background Color", BG_COLOR_DESC,
                        c -> c.cpsBackgroundColor, (c, v) -> c.cpsBackgroundColor = v),
                    slider("cps.bgOpacity", "Background Opacity", BG_OPACITY_DESC, 0, 255,
                        c -> c.cpsBackgroundOpacity, (c, v) -> c.cpsBackgroundOpacity = v))),
                new Tab("text", "TEXT", List.of(
                    text("cps.leftText", "Left Prefix", "Text placed before the left click count.",
                        c -> c.cpsLeftText, (c, v) -> c.cpsLeftText = v),
                    text("cps.separator", "Separator", "Text between the left and right click counts.",
                        c -> c.cpsSeparator, (c, v) -> c.cpsSeparator = v),
                    text("cps.rightText", "Right Prefix", "Text placed before the right click count.",
                        c -> c.cpsRightText, (c, v) -> c.cpsRightText = v)))
            ),
            false, false,
            () -> SimpleCPSConfig.instance.enabled,
            v -> SimpleCPSConfig.instance.enabled = v);
    }

    private static Page ping() {
        return new Page("ping", "PING",
            "Latency to the current server, in milliseconds.",
            List.of(
                new Tab("pos", "POSITION", placement("ping", "Ping",
                    c -> c.pingPosition, (c, v) -> c.pingPosition = v,
                    c -> c.pingXOffset, (c, v) -> c.pingXOffset = v,
                    c -> c.pingYOffset, (c, v) -> c.pingYOffset = v,
                    c -> c.pingScale, (c, v) -> c.pingScale = v)),
                new Tab("style", "STYLE", List.of(
                    color("ping.textColor", "Text Color", TEXT_COLOR_DESC,
                        c -> c.pingColor, (c, v) -> c.pingColor = v))),
                new Tab("bg", "BACKGROUND", List.of(
                    bool("ping.showBg", "Show Background", BG_SHOW_DESC,
                        c -> c.pingShowBackground, (c, v) -> c.pingShowBackground = v),
                    color("ping.bgColor", "Background Color", BG_COLOR_DESC,
                        c -> c.pingBackgroundColor, (c, v) -> c.pingBackgroundColor = v),
                    slider("ping.bgOpacity", "Background Opacity", BG_OPACITY_DESC, 0, 255,
                        c -> c.pingBackgroundOpacity, (c, v) -> c.pingBackgroundOpacity = v)))
            ),
            false, false,
            () -> SimpleCPSConfig.instance.showPing,
            v -> SimpleCPSConfig.instance.showPing = v);
    }

    private static Page fps() {
        return new Page("fps", "FPS",
            "Frames per second counter.",
            List.of(
                new Tab("pos", "POSITION", placement("fps", "FPS",
                    c -> c.fpsPosition, (c, v) -> c.fpsPosition = v,
                    c -> c.fpsXOffset, (c, v) -> c.fpsXOffset = v,
                    c -> c.fpsYOffset, (c, v) -> c.fpsYOffset = v,
                    c -> c.fpsScale, (c, v) -> c.fpsScale = v)),
                new Tab("style", "STYLE", List.of(
                    text("fps.suffix", "Suffix Text", "Text displayed after the fps value.",
                        c -> c.fpsText, (c, v) -> c.fpsText = v),
                    color("fps.textColor", "Text Color", TEXT_COLOR_DESC,
                        c -> c.fpsColor, (c, v) -> c.fpsColor = v),
                    bool("fps.rainbow", "Rainbow", RAINBOW_DESC,
                        c -> c.fpsRainbow, (c, v) -> c.fpsRainbow = v))),
                new Tab("bg", "BACKGROUND", List.of(
                    bool("fps.showBg", "Show Background", BG_SHOW_DESC,
                        c -> c.fpsShowBackground, (c, v) -> c.fpsShowBackground = v),
                    color("fps.bgColor", "Background Color", BG_COLOR_DESC,
                        c -> c.fpsBackgroundColor, (c, v) -> c.fpsBackgroundColor = v),
                    slider("fps.bgOpacity", "Background Opacity", BG_OPACITY_DESC, 0, 255,
                        c -> c.fpsBackgroundOpacity, (c, v) -> c.fpsBackgroundOpacity = v)))
            ),
            false, false,
            () -> SimpleCPSConfig.instance.showFps,
            v -> SimpleCPSConfig.instance.showFps = v);
    }

    private static Page keystrokes(Host host) {
        return new Page("keystrokes", "KEYSTROKES",
            "Key display with a fully custom layout designer.",
            List.of(
                new Tab("pos", "POSITION", placement("keys", "Keystrokes",
                    c -> c.keystrokesPosition, (c, v) -> c.keystrokesPosition = v,
                    c -> c.keystrokesXOffset, (c, v) -> c.keystrokesXOffset = v,
                    c -> c.keystrokesYOffset, (c, v) -> c.keystrokesYOffset = v,
                    c -> c.keystrokesScale, (c, v) -> c.keystrokesScale = v)),
                new Tab("style", "STYLE", List.of(
                    color("keys.textColor", "Text Color", "Color of the key text.",
                        c -> c.keystrokesColor, (c, v) -> c.keystrokesColor = v),
                    color("keys.pressedColor", "Pressed Color", "Color used while a key is held down.",
                        c -> c.keystrokesPressedColor, (c, v) -> c.keystrokesPressedColor = v),
                    bool("keys.rainbow", "Rainbow", "Enable the rainbow effect.",
                        c -> c.keystrokesRainbow, (c, v) -> c.keystrokesRainbow = v),
                    enumRow("keys.rainbowTarget", "Rainbow Target",
                        "Apply the rainbow effect to the text or the background.",
                        SimpleCPSConfig.RainbowTarget.class,
                        c -> c.keystrokesRainbowTarget, (c, v) -> c.keystrokesRainbowTarget = v),
                    choice("keys.effect", "Key Effect",
                        "Animation played when a key is pressed.",
                        List.of("NONE", "SQUISH", "RIPPLE", "BOTH"),
                        c -> c.keystrokesEffectMode, (c, v) -> c.keystrokesEffectMode = v))),
                new Tab("bg", "BACKGROUND", List.of(
                    color("keys.bgColor", "Background Color", "Color of the key backgrounds.",
                        c -> c.keystrokesBackgroundColor, (c, v) -> c.keystrokesBackgroundColor = v),
                    slider("keys.bgOpacity", "Background Opacity", "Transparency of the key backgrounds.", 0, 255,
                        c -> c.keystrokesBackgroundOpacity, (c, v) -> c.keystrokesBackgroundOpacity = v))),
                new Tab("layout", "LAYOUT", List.of(
                    action("keys.designer", "Keystroke Designer",
                        "Place, resize and rename every key on a free canvas.",
                        "OPEN DESIGNER", true, host::openKeystrokesDesigner)))
            ),
            false, false,
            () -> SimpleCPSConfig.instance.showKeystrokes,
            v -> SimpleCPSConfig.instance.showKeystrokes = v);
    }

    private static Page combo() {
        return new Page("combo", "COMBO",
            "Hit combo counter with optional heatmap coloring.",
            List.of(
                new Tab("pos", "POSITION", placement("combo", "Combo",
                    c -> c.comboPosition, (c, v) -> c.comboPosition = v,
                    c -> c.comboXOffset, (c, v) -> c.comboXOffset = v,
                    c -> c.comboYOffset, (c, v) -> c.comboYOffset = v,
                    c -> c.comboScale, (c, v) -> c.comboScale = v)),
                new Tab("behavior", "BEHAVIOR", List.of(
                    enumRow("combo.combatMode", "Combat Mode",
                        "Combat mechanics used for combo detection.",
                        SimpleCPSConfig.CombatMode.class,
                        c -> c.combatMode, (c, v) -> c.combatMode = v),
                    bool("combo.resetAny", "Reset on Any Damage",
                        "ON: classic mode, the combo resets on any hit taken. "
                            + "OFF: advanced mode with decay and a distance check.",
                        c -> c.comboResetOnAnyDamage, (c, v) -> c.comboResetOnAnyDamage = v),
                    bool("combo.onlyPlayers", "Only Players",
                        "Only hits landed on players count towards the combo.",
                        c -> c.comboOnlyPlayers, (c, v) -> c.comboOnlyPlayers = v),
                    bool("combo.continueOnSwitch", "Continue On Target Switch",
                        "Keep the combo going when you start hitting a different target.",
                        c -> c.comboContinueOnSwitch, (c, v) -> c.comboContinueOnSwitch = v),
                    // The field is a double; the UI has always exposed whole seconds.
                    slider("combo.timeout", "Timeout (s)",
                        "Seconds of inactivity before the combo resets.", 1, 10,
                        c -> (int) Math.round(c.comboTimeout), (c, v) -> c.comboTimeout = v),
                    bool("combo.hideInactive", "Hide When Inactive",
                        "Hide the display entirely while the combo is 0.",
                        c -> c.comboHideWhenInactive, (c, v) -> c.comboHideWhenInactive = v))),
                new Tab("style", "STYLE", List.of(
                    text("combo.suffix", "Suffix Text", "Text displayed after the combo count.",
                        c -> c.comboText, (c, v) -> c.comboText = v),
                    bool("combo.heatmap", "Heatmap Mode",
                        "Dynamic color (blue to red to black) plus shake. Overrides Rainbow.",
                        c -> c.comboHeatmap, (c, v) -> c.comboHeatmap = v),
                    enumRow("combo.heatmapMode", "Heatmap Difficulty",
                        "Difficulty scaling for the heatmap colors and shake. Resets the combo on change.",
                        SimpleCPSConfig.HeatmapMode.class,
                        c -> c.comboHeatmapMode,
                        (c, v) -> {
                            // Matches the old Cloth screen: the curves change under you,
                            // so an in-flight combo would be scored on two different scales.
                            if (c.comboHeatmapMode != v) ComboTracker.reset();
                            c.comboHeatmapMode = v;
                        }),
                    color("combo.textColor", "Text Color", TEXT_COLOR_DESC,
                        c -> c.comboColor, (c, v) -> c.comboColor = v),
                    bool("combo.rainbow", "Rainbow", "Enable the rainbow text effect.",
                        c -> c.comboRainbow, (c, v) -> c.comboRainbow = v))),
                new Tab("bg", "BACKGROUND", List.of(
                    bool("combo.showBg", "Show Background", BG_SHOW_DESC,
                        c -> c.comboShowBackground, (c, v) -> c.comboShowBackground = v),
                    color("combo.bgColor", "Background Color", BG_COLOR_DESC,
                        c -> c.comboBackgroundColor, (c, v) -> c.comboBackgroundColor = v),
                    slider("combo.bgOpacity", "Background Opacity", BG_OPACITY_DESC, 0, 255,
                        c -> c.comboBackgroundOpacity, (c, v) -> c.comboBackgroundOpacity = v)))
            ),
            false, false,
            () -> SimpleCPSConfig.instance.showCombo,
            v -> SimpleCPSConfig.instance.showCombo = v);
    }

    private static Page reach() {
        return new Page("reach", "REACH",
            "Distance of your last landed hit.",
            List.of(
                new Tab("pos", "POSITION", placement("reach", "Reach",
                    c -> c.reachPosition, (c, v) -> c.reachPosition = v,
                    c -> c.reachXOffset, (c, v) -> c.reachXOffset = v,
                    c -> c.reachYOffset, (c, v) -> c.reachYOffset = v,
                    c -> c.reachScale, (c, v) -> c.reachScale = v)),
                new Tab("behavior", "BEHAVIOR", List.of(
                    slider("reach.timeout", "Timeout (s)", "Seconds before the display disappears.", 1, 10,
                        c -> (int) Math.round(c.reachTimeout), (c, v) -> c.reachTimeout = v),
                    bool("reach.onlyPlayers", "Only Players", "Only show reach when hitting players.",
                        c -> c.reachOnlyPlayers, (c, v) -> c.reachOnlyPlayers = v),
                    bool("reach.alwaysShow", "Always Show", "Keep the display on screen even when inactive.",
                        c -> c.reachAlwaysShow, (c, v) -> c.reachAlwaysShow = v),
                    text("reach.noHitText", "No Hit Text", "Text shown when no hit has been landed yet.",
                        c -> c.reachNoHitText, (c, v) -> c.reachNoHitText = v))),
                new Tab("style", "STYLE", List.of(
                    color("reach.textColor", "Text Color", TEXT_COLOR_DESC,
                        c -> c.reachColor, (c, v) -> c.reachColor = v),
                    bool("reach.rainbow", "Rainbow", "Enable the rainbow text effect.",
                        c -> c.reachRainbow, (c, v) -> c.reachRainbow = v))),
                new Tab("bg", "BACKGROUND", List.of(
                    bool("reach.showBg", "Show Background", BG_SHOW_DESC,
                        c -> c.reachShowBackground, (c, v) -> c.reachShowBackground = v),
                    color("reach.bgColor", "Background Color", BG_COLOR_DESC,
                        c -> c.reachBackgroundColor, (c, v) -> c.reachBackgroundColor = v),
                    slider("reach.bgOpacity", "Background Opacity", BG_OPACITY_DESC, 0, 255,
                        c -> c.reachBackgroundOpacity, (c, v) -> c.reachBackgroundOpacity = v)))
            ),
            false, false,
            () -> SimpleCPSConfig.instance.showReach,
            v -> SimpleCPSConfig.instance.showReach = v);
    }

    private static Page armor() {
        return new Page("armor", "ARMOR HUD",
            "Armor pieces and held items with durability.",
            List.of(
                new Tab("pos", "POSITION", placement("armor", "the armor HUD",
                    c -> c.armorPosition, (c, v) -> c.armorPosition = v,
                    c -> c.armorXOffset, (c, v) -> c.armorXOffset = v,
                    c -> c.armorYOffset, (c, v) -> c.armorYOffset = v,
                    c -> c.armorScale, (c, v) -> c.armorScale = v)),
                new Tab("layout", "LAYOUT", List.of(
                    bool("armor.vertical", "Vertical Orientation",
                        "Stack the items vertically instead of horizontally.",
                        c -> c.armorVertical, (c, v) -> c.armorVertical = v),
                    bool("armor.mainHand", "Show Main Hand", "Display the item in your main hand.",
                        c -> c.armorShowMainHand, (c, v) -> c.armorShowMainHand = v),
                    bool("armor.offHand", "Show Off Hand", "Display the item in your off hand.",
                        c -> c.armorShowOffHand, (c, v) -> c.armorShowOffHand = v))),
                new Tab("style", "STYLE", List.of(
                    bool("armor.slots", "Show Background Slots",
                        "Show a semi-transparent slot behind each item.",
                        c -> c.armorShowBackgroundSlots, (c, v) -> c.armorShowBackgroundSlots = v),
                    bool("armor.durability", "Durability Text",
                        "Display precise durability numbers next to the items.",
                        c -> c.armorDurabilityText, (c, v) -> c.armorDurabilityText = v),
                    bool("armor.flash", "Damage Flash",
                        "Flash items red momentarily when durability is lost.",
                        c -> c.armorDamageFlash, (c, v) -> c.armorDamageFlash = v)))
            ),
            false, false,
            () -> SimpleCPSConfig.instance.showArmor,
            v -> SimpleCPSConfig.instance.showArmor = v);
    }

    private static Page general(Host host) {
        return new Page("general", "GENERAL",
            "Settings that apply to every module at once.",
            List.of(
                new Tab("display", "DISPLAY", List.of(
                    bool("gen.outline", "Text Outline",
                        "Draw HUD text with a thin black outline on every side instead of a drop shadow.",
                        c -> c.textOutline, (c, v) -> c.textOutline = v),
                    enumRow("gen.font", "HUD Font",
                        "Font used for HUD elements and the editor screens only, never chat or other menus.",
                        SimpleCPSConfig.HudFont.class,
                        c -> c.hudFont, (c, v) -> c.hudFont = v),
                    slider("gen.menuScale", "Menu Scale %",
                        "Size of this settings screen. It already holds one size across every "
                            + "Minecraft GUI Scale; this nudges that baseline.", 50, 200,
                        c -> c.settingsMenuScale, (c, v) -> c.settingsMenuScale = v))),
                new Tab("editor", "EDITOR", List.of(
                    bool("gen.preventOverlap", "Prevent Overlap",
                        "Push modules apart so two of them never cover the same pixels.",
                        c -> c.preventOverlap, (c, v) -> c.preventOverlap = v),
                    bool("gen.grid", "Editor Grid",
                        "Show a grid in the drag and drop editor and snap dragged elements to it.",
                        c -> c.editorGridEnabled, (c, v) -> c.editorGridEnabled = v),
                    slider("gen.gridSize", "Grid Size", "Spacing of the editor grid, in pixels.", 2, 64,
                        c -> c.editorGridSize, (c, v) -> c.editorGridSize = v),
                    action("gen.openEditor", "Drag & Drop Editor",
                        "Place every module directly on your screen with snapping and alignment tools.",
                        "OPEN EDITOR", true, host::openHudEditor),
                    action("gen.resetLayout", "Reset HUD Layout",
                        "Send every module back to its default anchor, offset and scale. "
                            + "Colors and behavior are left alone.",
                        "RESET LAYOUT", false, host::resetHudLayout))),
                new Tab("data", "DATA", List.of(
                    bool("gen.serverConfigs", "Server Configs",
                        "Remember the HUD per server: joining a configured server loads its config, "
                            + "and changes are saved back to it.",
                        c -> c.serverConfigsEnabled, (c, v) -> c.serverConfigsEnabled = v)))
            ),
            true, false, null, null);
    }

    // --- Plugin pages ------------------------------------------------------

    private static final Set<Class<?>> BUILT_IN_MODULES = Set.of(
        CpsModule.class, FpsModule.class, PingModule.class, ComboModule.class,
        KeystrokesModule.class, ArmorModule.class, ReachModule.class
    );

    /** Tab ids this schema hands out itself: placement first, screen-only extras last. */
    private static final String POSITION_TAB_ID = "pos";
    private static final String SETTINGS_TAB_ID = "settings";

    /**
     * Build a page for a module registered through {@code EymistakenHudPlugin}.
     *
     * <p>Placement comes from {@link HudModule}'s own accessors; everything else is
     * whatever the module already exposes to the editor's context menu. Those
     * settings carry plain suppliers with no notion of a default, so their reset
     * button stays inert — {@link SliderSetting} is the one exception, since it
     * declares one.
     *
     * <p>A module that declares {@link HudModule#getSettingsTabs() tabs} gets one tab
     * each instead of the single SETTINGS tab; see there for how the two flat setting
     * lists fold in.
     */
    private static Page pluginPage(HudModule module) {
        String name = module.getName();

        List<Row> placement = new ArrayList<>();
        placement.add(new PositionRow("plugin." + name + ".position", "Position",
            "Anchor position for " + name + ".",
            module::getPositionType, module::setPositionType, null));
        placement.add(new IntRow("plugin." + name + ".x", "X Offset",
            "Horizontal offset from the anchor, in pixels.",
            IntRow.Style.STEPPER, -OFFSET_LIMIT, OFFSET_LIMIT,
            module::getXOffset, module::setXOffset, 0));
        placement.add(new IntRow("plugin." + name + ".y", "Y Offset",
            "Vertical offset from the anchor, in pixels.",
            IntRow.Style.STEPPER, -OFFSET_LIMIT, OFFSET_LIMIT,
            module::getYOffset, module::setYOffset, 0));
        placement.add(new IntRow("plugin." + name + ".scale", "Scale %",
            "Size of the " + name + " display.",
            IntRow.Style.SLIDER, 50, 300,
            module::getScale, module::setScale, 100));

        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab(POSITION_TAB_ID, "POSITION", placement));

        // One counter for the whole page, not one per tab: the screen keys per-row UI
        // state (the open color picker, the focused field) off the row id, so two tabs
        // must never mint the same one.
        int[] counter = { 0 };
        Set<String> usedTabIds = new HashSet<>();
        usedTabIds.add(POSITION_TAB_ID);

        List<SettingsTab> declared = declaredTabs(module);
        if (declared.isEmpty()) {
            // The editor's right-click entries first, then anything the module keeps for
            // the settings screen alone. Concatenated as-is: a module that lists the same
            // setting twice gets it twice, which is the documented contract.
            List<Row> settings = adaptAll(name, exposedSettings(module, false), counter);
            settings.addAll(adaptAll(name, exposedSettings(module, true), counter));
            if (!settings.isEmpty()) {
                tabs.add(new Tab(SETTINGS_TAB_ID, "SETTINGS", settings));
            }
        } else {
            // The context menu keeps its contract — everything on it also shows up on the
            // page — so those rows lead the first tab the module declared.
            List<Row> lead = adaptAll(name, exposedSettings(module, false), counter);
            boolean first = true;
            for (SettingsTab tab : declared) {
                List<Row> rows = new ArrayList<>();
                if (first) {
                    // Spending the flag on a tab that may be dropped just below is safe:
                    // a non-empty lead is what keeps that tab from being empty.
                    rows.addAll(lead);
                    first = false;
                }
                rows.addAll(adaptAll(name, tab.settings(), counter));
                if (rows.isEmpty()) continue;
                tabs.add(new Tab(uniqueTabId(usedTabIds, tab.id(), tab.name()), tabLabel(tab), rows));
            }
            // Declaring tabs does not discard the settings-screen-only list; it lands at
            // the end, where it would have been anyway.
            List<Row> extra = adaptAll(name, exposedSettings(module, true), counter);
            if (!extra.isEmpty()) {
                tabs.add(new Tab(uniqueTabId(usedTabIds, SETTINGS_TAB_ID, null), "SETTINGS", extra));
            }
        }

        return new Page("plugin:" + name, SettingsTheme.up(name),
            "Module provided by a plugin.",
            tabs, false, true,
            module::isEnabled, null);
    }

    /**
     * Pull one of a module's two setting lists, tolerating a badly behaved plugin.
     * A module that throws here loses its settings, not the whole screen.
     */
    private static List<HudModuleSetting> exposedSettings(HudModule module, boolean settingsScreen) {
        List<HudModuleSetting> list;
        try {
            list = settingsScreen ? module.getSettingsScreenSettings() : module.getContextMenuSettings();
        } catch (Exception e) {
            return List.of();
        }
        return list == null ? List.of() : list;
    }

    /**
     * The tabs a module declared, with the same tolerance {@link #exposedSettings} gives
     * the flat lists: a module that throws loses its tabs, not the whole screen, and a
     * null list or a null entry is skipped rather than fatal. Coming back empty is the
     * signal to fall back to the single SETTINGS tab.
     */
    private static List<SettingsTab> declaredTabs(HudModule module) {
        List<SettingsTab> list;
        try {
            list = module.getSettingsTabs();
        } catch (Exception e) {
            return List.of();
        }
        if (list == null) return List.of();
        List<SettingsTab> tabs = new ArrayList<>(list.size());
        for (SettingsTab tab : list) {
            if (tab != null) tabs.add(tab);
        }
        return tabs;
    }

    /**
     * Adapt a whole setting list to rows, dropping nulls and unknown types.
     *
     * <p>{@code counter} is shared across every list on one page so row ids stay unique
     * however the settings are split up. For a null-free list it hands out the same ids
     * the flat single-tab layout always did.
     */
    private static List<Row> adaptAll(String moduleName, List<HudModuleSetting> settings, int[] counter) {
        List<Row> rows = new ArrayList<>();
        if (settings == null) return rows;
        for (HudModuleSetting setting : settings) {
            if (setting == null) continue;
            Row row = adapt(moduleName, setting, counter[0]++);
            if (row != null) rows.add(row);
        }
        return rows;
    }

    /** A declared tab's strip label: its name, or its id when the name is blank. */
    private static String tabLabel(SettingsTab tab) {
        if (tab.name() != null && !tab.name().isBlank()) return SettingsTheme.up(tab.name());
        if (tab.id() != null && !tab.id().isBlank()) return SettingsTheme.up(tab.id());
        return "SETTINGS";
    }

    /**
     * A tab id no other tab on this page has claimed.
     *
     * <p>Ids key the screen's memory of which tab you were on, and its lookup takes the
     * first match — so a plugin repeating an id, or claiming {@code "pos"}, would leave
     * the duplicate impossible to select. Numbering the clashes keeps every tab
     * reachable. Deterministic on purpose: the page is rebuilt on every {@code init()}
     * and the selected tab has to survive that.
     */
    private static String uniqueTabId(Set<String> used, String preferred, String fallbackName) {
        String base = preferred != null && !preferred.isBlank() ? preferred.trim() : null;
        if (base == null) {
            base = fallbackName != null && !fallbackName.isBlank()
                ? fallbackName.trim().toLowerCase(Locale.ROOT).replace(' ', '-')
                : "tab";
        }
        String id = base;
        int n = 2;
        while (!used.add(id)) {
            id = base + n++;
        }
        return id;
    }

    /**
     * Map one exposed setting onto a schema row, or null for unknown types.
     *
     * <p>{@code index} keeps the row id unique. Labels cannot: duplicates across the
     * two lists are allowed by design, and the screen keys per-row UI state (the open
     * color picker, the focused field) off the id.
     */
    private static Row adapt(String moduleName, HudModuleSetting setting, int index) {
        String id = "plugin." + moduleName + "." + index + "." + setting.label;
        if (setting instanceof BooleanSetting s) {
            return new BoolRow(id, s.label, "", s.getter, s.setter, null);
        }
        if (setting instanceof ColorSetting s) {
            return new ColorRow(id, s.label, "", s.getter, s.setter, null);
        }
        if (setting instanceof SliderSetting s) {
            return new IntRow(id, s.label, "", IntRow.Style.SLIDER, s.min, s.max,
                s.getter, s.setter, s.defaultValue);
        }
        if (setting instanceof CycleSetting s) {
            return new ChoiceRow(id, s.label, "", s.options, s.getter, s.setter, null);
        }
        if (setting instanceof TextSetting s) {
            return new TextRow(id, s.label, "", s.getter, s.setter, null);
        }
        if (setting instanceof ActionSetting s) {
            return new ActionRow(id, s.label, "", SettingsTheme.up(s.label), false, s.action);
        }
        return null;
    }

    /**
     * The settings-screen page a module belongs to, for jumping straight there from
     * the editor's right-click menu.
     *
     * @return a page id, or null when {@code module} is null
     */
    public static String pageIdFor(HudModule module) {
        if (module == null) return null;
        if (module instanceof CpsModule) return "cps";
        if (module instanceof PingModule) return "ping";
        if (module instanceof FpsModule) return "fps";
        if (module instanceof KeystrokesModule) return "keystrokes";
        if (module instanceof ComboModule) return "combo";
        if (module instanceof ReachModule) return "reach";
        if (module instanceof ArmorModule) return "armor";
        return "plugin:" + module.getName();
    }

    /**
     * The registered module a page belongs to — the inverse of {@link #pageIdFor}, used by
     * the preview card to find what it should draw.
     *
     * <p>Walks the registry and compares against {@code pageIdFor} rather than keeping a
     * second table, so the two can never disagree about a module.
     *
     * @return the module, or null for GENERAL, an unknown id, or a page whose module has
     *         since been unregistered
     */
    public static HudModule moduleForPage(String pageId) {
        if (pageId == null || pageId.isEmpty()) return null;
        for (HudModule module : HudModuleManager.getInstance().getModules()) {
            if (pageId.equals(pageIdFor(module))) return module;
        }
        return null;
    }

    // --- Assembly ----------------------------------------------------------

    /** Every built-in page, in the order the sidebar lists them. */
    public static List<Page> builtInPages(Host host) {
        return List.of(cps(), ping(), fps(), keystrokes(host), combo(), reach(), armor(), general(host));
    }

    /** Pages for third-party modules, in registration order. Empty when none are installed. */
    public static List<Page> pluginPages() {
        List<Page> pages = new ArrayList<>();
        for (HudModule module : HudModuleManager.getInstance().getModules()) {
            if (BUILT_IN_MODULES.contains(module.getClass())) continue;
            String name = module.getName();
            if (name == null || name.isEmpty()) continue;
            pages.add(pluginPage(module));
        }
        return pages;
    }
}
