package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.ComboTracker;
import com.eymistaken.simplecps.api.ComboHeatmap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scripted fight for the settings preview, so the Impact, Shake and Hit Jolt sliders and the
 * line under the count can be judged without landing a real hit.
 *
 * <p>Our combo counts up through the active tiers to a little past the top one, with a breather
 * halfway that lets the timeout line run down, and ends on a kill; sprint and critical hits are
 * mixed in for the hit strip. A combo landed on us climbs to the top tier too: under ours, trading
 * blows over our last hits, when the layout stacks the two, or on its own once ours has ended when
 * it does not. After a short pause the fight starts over.
 *
 * <p>The script is a list of timed events, each handed out once, when its time has passed. Drawing
 * the preview twice in one frame, as the card and FOCUS do, therefore never plays a moment twice.
 */
final class ComboDemo {

    private static final long HIT_MS = 500;
    /** How far past the top tier our combo runs before the kill. */
    private static final int PAST_TOP = 3;
    /** A high top tier is reached by starting partway in rather than by a longer fight. */
    private static final int MAX_OWN_HITS = 12;
    private static final int MAX_TAKEN_HITS = 6;
    /** The pause halfway through our combo, cut short where the timeout would not outlast it. */
    private static final long BREATHER_MS = 1300;
    private static final long BREATHER_MARGIN_MS = 400;
    /** Our finished combo lingers, then the stage stays clear for a moment. */
    private static final long AFTER_KILL_MS = ComboModule.LINGER_MS + 300;
    /** How long a combo landed on us stays up after its last hit, when it plays on its own. */
    private static final long TAKEN_HOLD_MS = 700;
    private static final long PAUSE_MS = 600;
    /** No frame takes this long: a gap like it means the preview was away, and it starts over. */
    private static final long RESTART_AFTER_MS = 250;

    private enum Type { OWN_HIT, KILL, TAKEN_HIT, TAKEN_END }

    private record Event(long time, Type type, int value) {}

    private record Script(ComboHeatmap heatmap, boolean stacked, long timeout, List<Event> events, long length) {}

    private final ArrayDeque<ComboTracker.HitMark> marks = new ArrayDeque<>();
    private final Collection<ComboTracker.HitMark> marksView = Collections.unmodifiableCollection(marks);
    private Script script;
    private long loopStart;
    private long lastAdvance;
    private int next;
    private int own;
    private long lastHit;
    private int received;
    private int receivedHits;

    /** The highest our combo gets. */
    static int ownPeak(ComboHeatmap heatmap) {
        return heatmap.tier3() + PAST_TOP;
    }

    /** The highest a combo landed on us gets. */
    static int receivedPeak(ComboHeatmap heatmap) {
        return heatmap.tier3();
    }

    int own() {
        return own;
    }

    int received() {
        return received;
    }

    /** Grows by one with every hit taken and never goes back, across loops too. */
    int receivedHits() {
        return receivedHits;
    }

    /** Our latest hits, oldest first; they stay through the kill until the fight starts over. */
    Collection<ComboTracker.HitMark> hitMarks() {
        return marksView;
    }

    /** Share of {@code timeoutMs} left since our last hit, or 0 once our combo is over. */
    float timeLeft(long real, long timeoutMs) {
        if (own == 0 || timeoutMs <= 0) return 0f;
        return Math.max(0f, 1f - (real - lastHit) / (float) timeoutMs);
    }

    /**
     * Plays the fight up to {@code real} milliseconds, handing every moment it passes to
     * {@code play}. The script is built from the heatmap, the layout and the timeout, so a change
     * to any of them starts the fight over.
     */
    void advance(long real, ComboHeatmap heatmap, boolean stacked, long timeoutMs,
                 Consumer<ComboTracker.Moment> play) {
        if (script == null || real - lastAdvance > RESTART_AFTER_MS || !script.heatmap().equals(heatmap)
                || script.stacked() != stacked || script.timeout() != timeoutMs) {
            script = build(heatmap, stacked, timeoutMs);
            startLoop(real);
        }
        lastAdvance = real;

        while (true) {
            long time = real - loopStart;
            List<Event> events = script.events();
            while (next < events.size() && events.get(next).time() <= time) {
                apply(events.get(next++), heatmap, real, play);
            }
            if (time < script.length()) return;
            startLoop(loopStart + script.length());
        }
    }

    private void apply(Event event, ComboHeatmap heatmap, long real, Consumer<ComboTracker.Moment> play) {
        switch (event.type()) {
            case OWN_HIT -> {
                own = event.value();
                lastHit = loopStart + event.time();
                if (marks.size() == ComboTracker.MAX_HIT_MARKS) marks.pollFirst();
                marks.addLast(new ComboTracker.HitMark(kindOf(own), lastHit));
                // The script counts up a hit at a time, so the hit before this one was one lower.
                boolean tier = heatmap.levelFor(own) > heatmap.levelFor(own - 1);
                ComboTracker.Kind kind = tier ? ComboTracker.Kind.TIER : ComboTracker.Kind.HIT;
                play.accept(new ComboTracker.Moment(kind, own, real));
            }
            case KILL -> {
                play.accept(new ComboTracker.Moment(ComboTracker.Kind.FINISHER, own, real));
                play.accept(new ComboTracker.Moment(ComboTracker.Kind.ENDED, own, real));
                own = 0;
            }
            case TAKEN_HIT -> {
                received = event.value();
                receivedHits++;
            }
            case TAKEN_END -> received = 0;
        }
    }

    private void startLoop(long start) {
        loopStart = start;
        next = 0;
        own = 0;
        received = 0;
        marks.clear();
    }

    /** Sprint hits open the combo and come back every few, with a critical one here and there. */
    private static ComboTracker.HitKind kindOf(int value) {
        if (value % 5 == 3) return ComboTracker.HitKind.CRIT;
        return value % 4 == 1 ? ComboTracker.HitKind.SPRINT : ComboTracker.HitKind.NORMAL;
    }

    private static Script build(ComboHeatmap heatmap, boolean stacked, long timeoutMs) {
        List<Event> events = new ArrayList<>();
        int ownPeak = ownPeak(heatmap);
        int ownHits = Math.min(ownPeak, MAX_OWN_HITS);
        long breather = Math.max(0, Math.min(BREATHER_MS, timeoutMs - BREATHER_MARGIN_MS));
        long time = 0;
        for (int i = 0; i < ownHits; i++) {
            if (i > 0) time += i == ownHits / 2 + 1 ? HIT_MS + breather : HIT_MS;
            events.add(new Event(time, Type.OWN_HIT, ownPeak - ownHits + 1 + i));
        }
        // The last hit is the kill.
        long kill = time;
        events.add(new Event(kill, Type.KILL, 0));

        int takenPeak = receivedPeak(heatmap);
        int takenHits = Math.min(takenPeak, MAX_TAKEN_HITS);
        // Stacked, the blows are traded: each hit taken lands between two of our last ones, and
        // the kill ends both combos. Otherwise ours has to be over before theirs can show.
        long first = stacked ? kill - takenHits * HIT_MS + HIT_MS / 2 : kill + AFTER_KILL_MS;
        for (int i = 0; i < takenHits; i++) {
            events.add(new Event(first + i * HIT_MS, Type.TAKEN_HIT, takenPeak - takenHits + 1 + i));
        }
        long takenEnd = stacked ? kill : first + (takenHits - 1) * HIT_MS + TAKEN_HOLD_MS;
        events.add(new Event(takenEnd, Type.TAKEN_END, 0));

        // A stable sort keeps events that share a time in the order they were added: the last
        // hit before the kill it makes, and the kill before the combo it ends.
        events.sort(Comparator.comparingLong(Event::time));
        long length = (stacked ? kill + AFTER_KILL_MS : takenEnd) + PAUSE_MS;
        return new Script(heatmap, stacked, timeoutMs, List.copyOf(events), length);
    }
}
