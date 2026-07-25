package com.eymistaken.simplecps.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets third-party mods react to things happening inside the HUD. Get the instance
 * with {@link #getInstance()} and subscribe from
 * {@link EymistakenHudPlugin#registerEvents(EymistakenHudEventBus)}:
 *
 * <pre>{@code
 * bus.subscribe(ComboStreakEvent.class, event -> {
 *     if (event.level() >= 2) playSound();
 * });
 * }</pre>
 *
 * <p>Handlers run on the client thread, inline with whatever caused the event, so
 * keep them short. A handler that throws is logged and skipped — it never takes the
 * rest of the mod down with it.
 *
 * <p>Dispatch is by exact class: subscribing to a supertype does not receive
 * subclasses.
 */
public final class EymistakenHudEventBus {

    private static final Logger LOGGER = LoggerFactory.getLogger("Eymistaken's HUD");
    private static final EymistakenHudEventBus INSTANCE = new EymistakenHudEventBus();

    // Copy-on-write so a handler that subscribes while an event is being dispatched
    // cannot break the loop that is running.
    private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

    private EymistakenHudEventBus() {}

    public static EymistakenHudEventBus getInstance() {
        return INSTANCE;
    }

    /** Listen for one event type. Safe to call at any point after client init. */
    public <T> void subscribe(Class<T> type, Consumer<? super T> handler) {
        if (type == null || handler == null) return;
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

    /** Deliver an event to every subscriber of its exact class. */
    @SuppressWarnings("unchecked")
    public <T> void emit(T event) {
        if (event == null) return;
        List<Consumer<?>> subscribers = handlers.get(event.getClass());
        if (subscribers == null) return; // nothing listening: the common case

        for (Consumer<?> subscriber : subscribers) {
            try {
                ((Consumer<T>) subscriber).accept(event);
            } catch (Exception e) {
                LOGGER.error("A listener for {} threw; skipping it.", event.getClass().getName(), e);
            }
        }
    }
}
