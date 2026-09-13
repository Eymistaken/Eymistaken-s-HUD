package com.eymistaken.simplecps;

import com.eymistaken.simplecps.api.ComboHeatmap;
import com.eymistaken.simplecps.api.EymistakenHudEventBus;
import com.eymistaken.simplecps.api.event.ComboStreakEvent;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Counts the local player's combo from what the server reports instead of from what the
 * player attempted, along with the combo someone else is landing on them.
 *
 * <p>An attack only records a pending swing. It turns into a hit when the server's damage
 * event for that target arrives, and the server sends one only for a fresh hit that was not
 * blocked, so clicks into invulnerability frames or a raised shield never count. Servers that
 * do not name the attacker (older protocols behind a translator) are handled by taking the
 * swing we just sent as the cause.
 *
 * <p>A combo is lost in one of two ways: it breaks, because the player took damage, or it runs
 * out of time. "Break on Any Damage" decides what breaks it; Decay decides whether losing it
 * ends it at once or starts draining it one hit per second.
 *
 * <p>The received combo counts hits taken in a row from one attacker. It ends when that
 * attacker is hit back, dies, or goes the timeout without landing another.
 *
 * <p>What happens to the combo is also queued as {@link Moment}s, for the display to react to.
 */
public final class ComboTracker {

    /** Something the combo display reacts to. */
    public enum Kind {
        /** A counted hit that stayed inside its heatmap tier. */
        HIT,
        /** A counted hit that reached a higher heatmap tier. */
        TIER,
        /** Our target died right after our hit. */
        FINISHER,
        /** Taking damage ended the combo. */
        BROKEN,
        /** Running out of time, losing the target or finishing it ended the combo. */
        ENDED
    }

    /** A moment and the combo it happened at; {@code time} is in {@code System.nanoTime()} milliseconds. */
    public record Moment(Kind kind, int value, long time) {}

    /** How long our swing may wait for the server's damage event, on top of latency. */
    private static final long CONFIRM_WINDOW_MS = 200;
    private static final long MAX_CONFIRM_WINDOW_MS = 1000;
    /** Reach within which a swing takes the blame for damage the server did not attribute. */
    private static final double SWING_BLAME_RANGE = 6.0;
    private static final double TARGET_LOST_DISTANCE = 20.0;
    /** A target dying this soon after our last hit is our kill. */
    private static final long KILL_WINDOW_MS = 1000;
    private static final long DRAIN_INTERVAL_MS = 1000;
    private static final float FULL_STRENGTH = 0.9f;
    private static final byte DEATH_EVENT = 3;
    private static final int NO_ENTITY = -1;
    /** Kept short: a moment nobody takes in time is stale anyway. */
    private static final int MAX_MOMENTS = 8;

    private record Swing(long time, boolean fullStrength) {}

    private static int combo = 0;
    private static int targetId = NO_ENTITY;
    private static UUID targetUuid = null;
    private static long lastHitTime = 0;
    private static boolean draining = false;
    private static long nextDrainTime = 0;

    private static int received = 0;
    private static int attackerId = NO_ENTITY;
    private static long lastReceivedTime = 0;
    /** Every hit ever taken from an attacker. It only grows, so a change means a new hit. */
    private static int receivedHits = 0;

    /** Entities we landed a counted hit on, by id, with the time of the latest one. */
    private static final Map<Integer, Long> opponents = new HashMap<>();
    /** Swings we sent that the server has not answered yet, by target id. */
    private static final Map<Integer, Swing> pendingSwings = new HashMap<>();
    /** When other entities last swung, by id. Only kept while attackers go unnamed. */
    private static final Map<Integer, Long> recentSwings = new HashMap<>();
    private static final ArrayDeque<Moment> moments = new ArrayDeque<>();
    /**
     * Set once a damage event names its cause. From then on, damage without a cause is the
     * environment's rather than a hit whose attacker was left out.
     */
    private static boolean serverNamesAttackers = false;
    private static ClientLevel trackedLevel = null;

    private ComboTracker() {}

    /** The local player attacked {@code target}; the packet has not gone out yet. */
    public static void onAttack(Entity target, Player attacker) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (config.comboOnlyPlayers && !(target instanceof Player)) return;

        long now = now();
        boolean fullStrength = config.combatMode != SimpleCPSConfig.CombatMode.MODERN
            || attacker.getAttackStrengthScale(0.5f) >= FULL_STRENGTH;

        if (config.comboHitDetection == SimpleCPSConfig.HitDetection.CLIENT) {
            if (fullStrength) countHit(target, now);
            return;
        }
        // Spam-clicking after a full swing must not turn the hit the server is about to
        // confirm into a weak one.
        pendingSwings.merge(target.getId(), new Swing(now, fullStrength),
            (earlier, later) -> earlier.fullStrength() && !later.fullStrength() ? earlier : later);
    }

    public static void onDamageEvent(ClientboundDamageEventPacket packet) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) return;

        long now = now();
        if (packet.sourceCauseId() != NO_ENTITY) serverNamesAttackers = true;

        if (packet.entityId() == player.getId()) {
            onLocalPlayerHurt(packet, player, level, now);
        } else if (SimpleCPSConfig.instance.comboHitDetection == SimpleCPSConfig.HitDetection.SERVER) {
            confirmHit(packet, player, level, now);
        }
    }

    public static void onEntityEvent(ClientboundEntityEventPacket packet) {
        if (packet.getEventId() != DEATH_EVENT) return;
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : packet.getEntity(level);
        if (entity == null) return;

        opponents.remove(entity.getId());
        if (entity.getId() == attackerId) endReceived();

        long now = now();
        boolean ourKill = combo > 0 && entity.getUUID().equals(targetUuid)
            && now - lastHitTime <= KILL_WINDOW_MS;
        if (!ourKill) return;
        emit(Kind.FINISHER, combo, now);
        // A kill finishes the combo, unless it is meant to carry over to the next target.
        if (!SimpleCPSConfig.instance.comboContinueOnSwitch) {
            emit(Kind.ENDED, combo, now);
            endCombo();
        }
    }

    public static void onAnimate(ClientboundAnimatePacket packet) {
        if (serverNamesAttackers) return;
        int action = packet.getAction();
        if (action == ClientboundAnimatePacket.SWING_MAIN_HAND
                || action == ClientboundAnimatePacket.SWING_OFF_HAND) {
            recentSwings.put(packet.getId(), now());
        }
    }

    public static void onTick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            forgetSession();
            return;
        }
        // Entity ids only mean something within one level of one connection.
        if (level != trackedLevel) {
            forgetSession();
            trackedLevel = level;
        }

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        long now = now();
        long timeout = timeoutMs(config);
        pendingSwings.values().removeIf(swing -> now - swing.time() > MAX_CONFIRM_WINDOW_MS);
        recentSwings.values().removeIf(time -> now - time > MAX_CONFIRM_WINDOW_MS);
        opponents.values().removeIf(time -> now - time > timeout);
        if (received > 0 && now - lastReceivedTime > timeout) endReceived();

        if (combo == 0) return;

        Entity target = level.getEntity(targetId);
        boolean targetFled = target != null && target.getUUID().equals(targetUuid)
            && player.distanceTo(target) > TARGET_LOST_DISTANCE;
        if (targetFled || now - lastHitTime > timeout) loseCombo(now, false);

        if (draining && now >= nextDrainTime) {
            combo--;
            nextDrainTime = now + DRAIN_INTERVAL_MS;
            if (combo <= 0) endCombo();
        }
    }

    /** Ends the running combo. Changing the heatmap difficulty does this too. */
    public static void reset() {
        endCombo();
    }

    public static int getCombo() {
        return combo;
    }

    /** Hits taken in a row from one attacker who has not been hit back; 0 when there is none. */
    public static int getReceivedCombo() {
        return received;
    }

    /** Grows by one with every hit taken from an attacker, so effects can tell each new hit apart. */
    public static int getReceivedHits() {
        return receivedHits;
    }

    /** The oldest moment not taken yet, or null. The combo display takes them as it draws. */
    public static Moment pollMoment() {
        return moments.pollFirst();
    }

    private static void confirmHit(ClientboundDamageEventPacket packet, LocalPlayer player,
                                   ClientLevel level, long now) {
        Swing swing = pendingSwings.get(packet.entityId());
        if (swing == null || now - swing.time() > confirmWindow(player)) return;

        // Our melee names us as both cause and direct source; our arrow, or a teammate's hit
        // landing in the same moment, does not. Without a cause it is ours only on a server
        // that never names one.
        int cause = packet.sourceCauseId();
        boolean ours = cause == player.getId() && packet.sourceDirectId() == player.getId();
        if (!ours && (cause != NO_ENTITY || serverNamesAttackers)) return;

        pendingSwings.remove(packet.entityId());
        Entity target = level.getEntity(packet.entityId());
        if (swing.fullStrength() && target != null) countHit(target, now);
    }

    private static void onLocalPlayerHurt(ClientboundDamageEventPacket packet, LocalPlayer player,
                                          ClientLevel level, long now) {
        // Thorns is the echo of our own hit, not something done to us.
        if (packet.sourceType().is(DamageTypes.THORNS)) return;

        SimpleCPSConfig config = SimpleCPSConfig.instance;
        int attacker = attackerOf(packet, player, level, now);
        countReceived(attacker, level, now, config);

        if (combo == 0) return;
        if (config.comboResetOnAnyDamage) {
            loseCombo(now, true);
            return;
        }
        Long lastHitOnAttacker = opponents.get(attacker);
        if (lastHitOnAttacker != null && now - lastHitOnAttacker <= timeoutMs(config)) loseCombo(now, true);
    }

    /** Who damaged the local player, or {@link #NO_ENTITY} for the environment. */
    private static int attackerOf(ClientboundDamageEventPacket packet, LocalPlayer player,
                                  ClientLevel level, long now) {
        int cause = packet.sourceCauseId();
        if (cause != NO_ENTITY) return cause == player.getId() ? NO_ENTITY : cause;
        if (serverNamesAttackers) return NO_ENTITY;

        // The server did not say: blame whoever swung at arm's length most recently.
        long window = confirmWindow(player);
        int blamed = NO_ENTITY;
        long blamedTime = Long.MIN_VALUE;
        for (Map.Entry<Integer, Long> swing : recentSwings.entrySet()) {
            long time = swing.getValue();
            if (now - time > window || time <= blamedTime) continue;
            Entity entity = level.getEntity(swing.getKey());
            if (entity != null && entity != player && entity.distanceTo(player) <= SWING_BLAME_RANGE) {
                blamed = swing.getKey();
                blamedTime = time;
            }
        }
        return blamed;
    }

    private static void countReceived(int attacker, ClientLevel level, long now, SimpleCPSConfig config) {
        Entity entity = attacker == NO_ENTITY ? null : level.getEntity(attacker);
        boolean counts = entity instanceof Player
            || (!config.comboOnlyPlayers && entity instanceof LivingEntity);
        if (!counts) return;

        boolean sameStreak = attacker == attackerId && now - lastReceivedTime <= timeoutMs(config);
        received = sameStreak ? received + 1 : 1;
        attackerId = attacker;
        lastReceivedTime = now;
        receivedHits++;
    }

    private static void countHit(Entity target, long now) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        if (combo > 0) {
            boolean switched = !target.getUUID().equals(targetUuid);
            // Ran out of time since the last tick looked; with Decay the drain covers that.
            boolean expired = !decays(config) && now - lastHitTime > timeoutMs(config);
            if (expired || (switched && !config.comboContinueOnSwitch)) endCombo();
        }

        targetId = target.getId();
        targetUuid = target.getUUID();
        lastHitTime = now;
        draining = false;
        opponents.put(target.getId(), now);
        // Hitting back ends the combo that attacker was landing on us.
        if (target.getId() == attackerId) endReceived();

        // Compared around the increment so plugins hear about a tier once, on the hit
        // that reaches it. Independent of comboHeatmap: that only controls coloring.
        ComboHeatmap heatmap = ComboHeatmap.of(config.comboHeatmapMode);
        int previousLevel = heatmap.levelFor(combo);
        combo++;
        int level = heatmap.levelFor(combo);
        if (level > previousLevel) {
            EymistakenHudEventBus.getInstance().emit(new ComboStreakEvent(combo, level, previousLevel, target));
        }
        emit(level > previousLevel ? Kind.TIER : Kind.HIT, combo, now);
    }

    /** The combo broke or ran out of time: it ends, or with Decay starts draining. */
    private static void loseCombo(long now, boolean broken) {
        if (!decays(SimpleCPSConfig.instance)) {
            emit(broken ? Kind.BROKEN : Kind.ENDED, combo, now);
            endCombo();
        } else if (!draining) {
            draining = true;
            nextDrainTime = now + DRAIN_INTERVAL_MS;
        }
    }

    private static void emit(Kind kind, int value, long now) {
        if (moments.size() == MAX_MOMENTS) moments.pollFirst();
        moments.addLast(new Moment(kind, value, now));
    }

    private static void endCombo() {
        combo = 0;
        targetId = NO_ENTITY;
        targetUuid = null;
        draining = false;
    }

    private static void endReceived() {
        received = 0;
        attackerId = NO_ENTITY;
    }

    private static void forgetSession() {
        endCombo();
        endReceived();
        opponents.clear();
        pendingSwings.clear();
        recentSwings.clear();
        moments.clear();
        serverNamesAttackers = false;
        trackedLevel = null;
    }

    private static boolean decays(SimpleCPSConfig config) {
        return Boolean.TRUE.equals(config.comboDecay);
    }

    private static long timeoutMs(SimpleCPSConfig config) {
        return (long) (config.comboTimeout * 1000);
    }

    /** Our swing and the server's answer are a round trip apart. */
    private static long confirmWindow(LocalPlayer player) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
        int latency = info == null ? 0 : Math.max(0, info.getLatency());
        return Math.min(MAX_CONFIRM_WINDOW_MS, CONFIRM_WINDOW_MS + latency);
    }

    /** Monotonic, unlike the wall clock, so a clock change cannot end or stall a combo. */
    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }
}
