package com.eymistaken.simplecps;

import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.api.IHudElement;
import com.eymistaken.simplecps.modules.ArmorModule;
import com.eymistaken.simplecps.modules.ReachModule;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HudPlacementResolver {
    public static final int EDGE_GAP = 5;
    public static final int ITEM_GAP = 4;

    private HudPlacementResolver() {}

    public static final class Rect {
        public final int x;
        public final int y;
        public final int w;
        public final int h;

        public Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = Math.max(0, w);
            this.h = Math.max(0, h);
        }

        public Rect moveTo(int newX, int newY) {
            return new Rect(newX, newY, w, h);
        }

        public Rect moveBy(int dx, int dy) {
            return new Rect(x + dx, y + dy, w, h);
        }

        public boolean intersects(Rect other) {
            return x < other.x + other.w && x + w > other.x && y < other.y + other.h && y + h > other.y;
        }

        public int overlapArea(Rect other) {
            int overlapW = Math.min(x + w, other.x + other.w) - Math.max(x, other.x);
            int overlapH = Math.min(y + h, other.y + other.h) - Math.max(y, other.y);
            if (overlapW <= 0 || overlapH <= 0) return 0;
            return overlapW * overlapH;
        }
    }

    public static final class ModulePlacement {
        public final HudModule module;
        public final String key;
        public final int x;
        public final int y;
        public final int w;
        public final int h;
        public final boolean shouldRender;
        public final boolean manual;

        private ModulePlacement(HudModule module, String key, Rect rect, boolean shouldRender, boolean manual) {
            this.module = module;
            this.key = key;
            this.x = rect.x;
            this.y = rect.y;
            this.w = rect.w;
            this.h = rect.h;
            this.shouldRender = shouldRender;
            this.manual = manual;
        }

        public Rect rect() {
            return new Rect(x, y, w, h);
        }
    }

    private static final class Entry {
        HudModule module;
        String key;
        boolean shouldRender;
        boolean manual;
        int renderW;
        int renderH;
        int layoutW;
        int layoutH;
        int preferredX;
        int preferredY;
        Rect actual;
    }

    /**
     * Never switch on a raw anchor: it is null whenever a config names a Position this
     * build lacks, and a third-party module may simply return null from
     * {@code getPositionType()}. A switch over null throws regardless of a default arm.
     */
    private static SimpleCPSConfig.Position anchorOf(SimpleCPSConfig.Position pos) {
        return pos == null ? SimpleCPSConfig.Position.TOP_LEFT : pos;
    }

    public static String getModuleKey(HudModule module) {
        return "module:" + module.getName();
    }

    public static String getElementKey(IHudElement element, List<HudModule> modules) {
        for (HudModule module : modules) {
            if (module == element) {
                return getModuleKey(module);
            }
            for (IHudElement subElement : module.getSubElements()) {
                if (subElement == element) {
                    return getModuleKey(module) + "/element:" + subElement.getName();
                }
            }
        }
        return "element:" + element.getName();
    }

    public static boolean isManualLayout(IHudElement element, List<HudModule> modules) {
        String key = getElementKey(element, modules);
        return SimpleCPSConfig.isManualLayout(key) || element.getXOffset() != 0 || element.getYOffset() != 0;
    }

    public static void setManualLayout(IHudElement element, List<HudModule> modules, boolean manual) {
        SimpleCPSConfig.setManualLayout(getElementKey(element, modules), manual);
    }

    public static List<ModulePlacement> resolveModules(List<HudModule> modules, int screenWidth, int screenHeight, boolean editorMode) {
        List<Entry> entries = buildEntries(modules, editorMode);
        assignPreferredPositions(entries, screenWidth, screenHeight);

        List<Entry> visibleEntries = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.shouldRender && entry.renderW > 0 && entry.renderH > 0) {
                visibleEntries.add(entry);
            }
        }

        if (SimpleCPSConfig.instance.preventOverlap) {
            resolveVisiblePositions(visibleEntries, screenWidth, screenHeight);
        } else {
            for (Entry entry : visibleEntries) {
                entry.actual = clampToScreen(new Rect(entry.preferredX, entry.preferredY, entry.renderW, entry.renderH), screenWidth, screenHeight);
            }
        }

        List<ModulePlacement> placements = new ArrayList<>();
        for (Entry entry : visibleEntries) {
            placements.add(new ModulePlacement(entry.module, entry.key, entry.actual, true, entry.manual));
        }
        return placements;
    }

    private static List<Entry> buildEntries(List<HudModule> modules, boolean editorMode) {
        List<Entry> entries = new ArrayList<>();
        for (HudModule module : modules) {
            Entry entry = new Entry();
            entry.module = module;
            entry.key = getModuleKey(module);
            entry.manual = SimpleCPSConfig.isManualLayout(entry.key) || module.getXOffset() != 0 || module.getYOffset() != 0;
            entry.layoutW = Math.max(0, module.getLayoutWidth());
            entry.layoutH = Math.max(0, module.getLayoutHeight());
            entry.renderW = Math.max(0, module.getWidth());
            entry.renderH = Math.max(0, module.getHeight());
            entry.shouldRender = editorMode || (module.isEnabled() && entry.renderW > 0 && entry.renderH > 0);
            if (entry.layoutW == 0 || entry.layoutH == 0) {
                entry.layoutW = entry.renderW;
                entry.layoutH = entry.renderH;
            }
            if (entry.layoutW > 0 && entry.layoutH > 0) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private static void assignPreferredPositions(List<Entry> entries, int screenWidth, int screenHeight) {
        int topLeftStack = EDGE_GAP;
        int topRightStack = EDGE_GAP;
        int bottomLeftStack = screenHeight - EDGE_GAP;
        int bottomRightStack = screenHeight - EDGE_GAP;

        for (Entry entry : entries) {
            int w = Math.max(1, entry.layoutW);
            int h = Math.max(1, entry.layoutH);
            SimpleCPSConfig.Position anchor = anchorOf(entry.module.getPositionType());

            if (entry.manual) {
                Point base = getBasePos(anchor, screenWidth, screenHeight, w, h);
                entry.preferredX = base.x + entry.module.getXOffset();
                entry.preferredY = base.y + entry.module.getYOffset();
                continue;
            }

            switch (anchor) {
                case TOP_LEFT -> {
                    entry.preferredX = EDGE_GAP;
                    entry.preferredY = topLeftStack;
                    topLeftStack += h + ITEM_GAP;
                }
                case TOP_RIGHT -> {
                    entry.preferredX = screenWidth - w - EDGE_GAP;
                    entry.preferredY = topRightStack;
                    topRightStack += h + ITEM_GAP;
                }
                case BOTTOM_LEFT -> {
                    entry.preferredX = EDGE_GAP;
                    int currentBottom = entry.module instanceof ArmorModule && bottomLeftStack == screenHeight - EDGE_GAP
                        ? screenHeight
                        : bottomLeftStack;
                    currentBottom -= h;
                    entry.preferredY = currentBottom;
                    bottomLeftStack = currentBottom - ITEM_GAP;
                }
                case BOTTOM_RIGHT -> {
                    entry.preferredX = screenWidth - w - EDGE_GAP;
                    int currentBottom = entry.module instanceof ArmorModule && bottomRightStack == screenHeight - EDGE_GAP
                        ? screenHeight
                        : bottomRightStack;
                    currentBottom -= h;
                    entry.preferredY = currentBottom;
                    bottomRightStack = currentBottom - ITEM_GAP;
                }
                case CENTER -> {
                    entry.preferredX = (screenWidth - w) / 2;
                    entry.preferredY = (screenHeight - h) / 2;
                    if (entry.module instanceof ReachModule) {
                        entry.preferredY += 15;
                    }
                }
            }
        }
    }

    private static void resolveVisiblePositions(List<Entry> visibleEntries, int screenWidth, int screenHeight) {
        List<Entry> ordered = new ArrayList<>();
        for (Entry entry : visibleEntries) {
            if (entry.manual) ordered.add(entry);
        }
        for (Entry entry : visibleEntries) {
            if (!entry.manual) ordered.add(entry);
        }

        List<Rect> occupied = new ArrayList<>();
        for (Entry entry : ordered) {
            Rect desired = new Rect(entry.preferredX, entry.preferredY, entry.renderW, entry.renderH);
            if (entry.manual) {
                entry.actual = clampToScreen(desired, screenWidth, screenHeight);
            } else {
                entry.actual = findNearestFree(desired, occupied, screenWidth, screenHeight);
            }
            occupied.add(entry.actual);
        }
    }

    public static Point getBasePos(SimpleCPSConfig.Position pos, int screenWidth, int screenHeight, int elementW, int elementH) {
        return switch (anchorOf(pos)) {
            case TOP_LEFT -> new Point(EDGE_GAP, EDGE_GAP);
            case TOP_RIGHT -> new Point(screenWidth - elementW - EDGE_GAP, EDGE_GAP);
            case BOTTOM_LEFT -> new Point(EDGE_GAP, screenHeight - elementH - EDGE_GAP);
            case BOTTOM_RIGHT -> new Point(screenWidth - elementW - EDGE_GAP, screenHeight - elementH - EDGE_GAP);
            case CENTER -> new Point((screenWidth - elementW) / 2, (screenHeight - elementH) / 2);
        };
    }

    public static Rect findNearestFree(Rect desired, List<Rect> blockers, int screenWidth, int screenHeight) {
        Rect clampedDesired = clampToScreen(desired, screenWidth, screenHeight);
        if (!intersectsAny(clampedDesired, blockers)) {
            return clampedDesired;
        }

        List<Rect> candidates = buildCandidates(desired, blockers, screenWidth, screenHeight);
        candidates.sort(Comparator
            .comparingInt((Rect rect) -> overlapArea(rect, blockers))
            .thenComparingLong(rect -> distanceSquared(rect.x, rect.y, clampedDesired.x, clampedDesired.y)));

        for (Rect candidate : candidates) {
            if (!intersectsAny(candidate, blockers)) {
                return candidate;
            }
        }
        return candidates.isEmpty() ? clampedDesired : candidates.get(0);
    }

    private static List<Rect> buildCandidates(Rect desired, List<Rect> blockers, int screenWidth, int screenHeight) {
        Set<Integer> xs = new HashSet<>();
        Set<Integer> ys = new HashSet<>();
        addCandidateAxisValues(xs, desired.x, desired.w, screenWidth);
        addCandidateAxisValues(ys, desired.y, desired.h, screenHeight);

        for (Rect blocker : blockers) {
            xs.add(blocker.x - desired.w - ITEM_GAP);
            xs.add(blocker.x + blocker.w + ITEM_GAP);
            xs.add(blocker.x);
            xs.add(blocker.x + blocker.w - desired.w);

            ys.add(blocker.y - desired.h - ITEM_GAP);
            ys.add(blocker.y + blocker.h + ITEM_GAP);
            ys.add(blocker.y);
            ys.add(blocker.y + blocker.h - desired.h);
        }

        List<Rect> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int x : xs) {
            for (int y : ys) {
                Rect candidate = clampToScreen(desired.moveTo(x, y), screenWidth, screenHeight);
                String key = candidate.x + ":" + candidate.y;
                if (seen.add(key)) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    private static void addCandidateAxisValues(Set<Integer> values, int desiredValue, int size, int screenSize) {
        values.add(desiredValue);
        values.add(0);
        values.add(EDGE_GAP);
        values.add(screenSize - size);
        values.add(screenSize - size - EDGE_GAP);
    }

    public static Rect clampToScreen(Rect rect, int screenWidth, int screenHeight) {
        int maxX = Math.max(0, screenWidth - rect.w);
        int maxY = Math.max(0, screenHeight - rect.h);
        int x = Math.max(0, Math.min(maxX, rect.x));
        int y = Math.max(0, Math.min(maxY, rect.y));
        return rect.moveTo(x, y);
    }

    private static boolean intersectsAny(Rect rect, List<Rect> blockers) {
        for (Rect blocker : blockers) {
            if (rect.intersects(blocker)) {
                return true;
            }
        }
        return false;
    }

    private static int overlapArea(Rect rect, List<Rect> blockers) {
        int area = 0;
        for (Rect blocker : blockers) {
            area += rect.overlapArea(blocker);
        }
        return area;
    }

    private static long distanceSquared(int x1, int y1, int x2, int y2) {
        long dx = x1 - x2;
        long dy = y1 - y2;
        return dx * dx + dy * dy;
    }
}
