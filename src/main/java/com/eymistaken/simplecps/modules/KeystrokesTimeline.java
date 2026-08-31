package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.util.EymHudFonts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The timeline design: one lane per key, showing the last few seconds of presses
 * scrolling away from a "now" line on the right.
 *
 * <p>This is the one design that is not a button style. It ignores every button's
 * x/y/w/h and lays the keys out as rows itself, which is why it is global-only and
 * why it computes its own size instead of deriving one from the layout.
 *
 * <p>It reads a hold as a bar whose length is its duration, so holding a key draws
 * one long block and spamming it draws a comb — the rhythm of a combo becomes
 * something you can see rather than count.
 */
public final class KeystrokesTimeline {

    private KeystrokesTimeline() {}

    /** How much history a lane shows. */
    private static final long WINDOW_MS = 4000L;

    /** Lane geometry in game pixels, scaled down from the mock's desktop sizes. */
    private static final int ROW_H = 9;
    private static final int ROW_GAP = 2;
    private static final int LABEL_W = 22;
    private static final int LABEL_GAP = 3;
    private static final int LANE_W = 66;
    private static final int BAR_H = 5;

    /** One press: closed once the key comes back up. */
    private static final class Bar {
        final long start;
        long end = -1L;
        Bar(long start) { this.start = start; }
    }

    /**
     * Press history per key. Two stores for the same reason the animations have
     * two: the settings screen draws a preview over the live HUD, and a shared
     * buffer would record every press twice.
     */
    public static final class Store {
        private final Map<Integer, Deque<Bar>> lanes = new HashMap<>();

        private Deque<Bar> lane(int key) {
            return lanes.computeIfAbsent(key, k -> new ArrayDeque<>());
        }

        public void clear() {
            lanes.clear();
        }
    }

    private static int keyOf(SimpleCPSConfig.KeyButtonData btn) {
        return btn.keyCode + (btn.isMouse ? 1000 : 0);
    }

    /** Record the current press state and drop anything that has scrolled off. */
    public static void update(Store store, List<SimpleCPSConfig.KeyButtonData> keys,
                              java.util.function.Predicate<SimpleCPSConfig.KeyButtonData> isDown) {
        long now = System.currentTimeMillis();
        for (SimpleCPSConfig.KeyButtonData btn : keys) {
            if (btn.hidden) continue;
            Deque<Bar> lane = store.lane(keyOf(btn));
            boolean down = isDown.test(btn);
            Bar last = lane.peekLast();

            if (down && (last == null || last.end != -1L)) {
                lane.addLast(new Bar(now));
            } else if (!down && last != null && last.end == -1L) {
                last.end = now;
            }

            // A bar leaves once even its trailing edge has crossed the lane.
            while (!lane.isEmpty()) {
                Bar first = lane.peekFirst();
                long end = first.end == -1L ? now : first.end;
                if (now - end > WINDOW_MS) lane.removeFirst();
                else break;
            }
        }
    }

    public static int width() {
        return LABEL_W + LABEL_GAP + LANE_W;
    }

    public static int height(List<SimpleCPSConfig.KeyButtonData> keys) {
        int rows = 0;
        for (SimpleCPSConfig.KeyButtonData btn : keys) {
            if (!btn.hidden) rows++;
        }
        if (rows == 0) return ROW_H;
        return rows * ROW_H + (rows - 1) * ROW_GAP;
    }

    /** A short lane label — the full one rarely fits, and "SHIFT" is not "SHFT" by accident. */
    private static String shortLabel(SimpleCPSConfig.KeyButtonData btn) {
        String label = btn.label == null ? "" : btn.label.trim();
        if (label.isEmpty()) return "?";
        if (label.length() <= 4) return label;
        return label.substring(0, 4);
    }

    public static void draw(GuiGraphicsExtractor ctx, Font font, Store store,
                            List<SimpleCPSConfig.KeyButtonData> keys,
                            int idle, int accent) {
        long now = System.currentTimeMillis();
        // Pixels per millisecond: the lane holds exactly WINDOW_MS of history.
        float speed = (float) LANE_W / WINDOW_MS;

        List<SimpleCPSConfig.KeyButtonData> visible = new ArrayList<>();
        for (SimpleCPSConfig.KeyButtonData btn : keys) {
            if (!btn.hidden) visible.add(btn);
        }

        int laneX = LABEL_W + LABEL_GAP;
        for (int i = 0; i < visible.size(); i++) {
            SimpleCPSConfig.KeyButtonData btn = visible.get(i);
            int y = i * (ROW_H + ROW_GAP);
            Deque<Bar> lane = store.lane(keyOf(btn));
            boolean active = !lane.isEmpty() && lane.peekLast().end == -1L;

            String label = shortLabel(btn);
            int textW = font.width(EymHudFonts.text(label));
            ctx.text(font, EymHudFonts.text(label),
                laneX - LABEL_GAP - textW, y + (ROW_H - font.lineHeight) / 2 + 1,
                active ? accent : idle, true);

            ctx.fill(laneX, y, laneX + LANE_W, y + ROW_H, 0x14FFFFFF);
            // The "now" line: presses are born here and travel left.
            ctx.fill(laneX + LANE_W - 1, y, laneX + LANE_W, y + ROW_H, 0x47FFFFFF);

            int barY = y + (ROW_H - BAR_H) / 2;
            for (Bar bar : lane) {
                long end = bar.end == -1L ? now : bar.end;
                int right = laneX + LANE_W - Math.round((now - end) * speed);
                int len = Math.max(1, Math.round((end - bar.start) * speed));
                int left = right - len;
                if (right <= laneX) continue;
                left = Math.max(left, laneX);
                if (right > left) ctx.fill(left, barY, right, barY + BAR_H, accent);
            }
        }
    }
}
