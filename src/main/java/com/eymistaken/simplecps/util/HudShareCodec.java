package com.eymistaken.simplecps.util;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Encodes a config, or the whole Keystrokes look, into a short shareable string and back.
 *
 * <p>Format: {@code EYMHUD1-<base64url(gzip(json))>}, where the JSON is an envelope
 * {@code {"v":<schema>,"mv":"<mod version>","t":"<type>","d":<payload>}}. The prefix
 * versions the container; {@code v} versions the payload shape, so a code written by
 * a newer build is rejected cleanly instead of being half-applied.
 *
 * <p>Everything decoded here is untrusted input pasted from a stranger, so
 * {@link #decode(String)} is bounded at every step and {@link #apply(Decoded)} never
 * touches the live config until the incoming one has been fully validated.
 */
public final class HudShareCodec {

    public static final String PREFIX = "EYMHUD1-";
    public static final String TYPE_CONFIG = "config";
    public static final String TYPE_KEYSTROKES = "keys";

    /**
     * Payload schema, stamped per type so one payload growing does not invalidate the
     * other. A config payload has not changed shape, so it stays at 1 and older builds
     * go on reading it; a keystrokes payload became an object at 2, and stamping that
     * is what makes an older build reject it with "not a valid share code" instead of
     * half-applying it.
     */
    private static final int SCHEMA_CONFIG = 1;
    private static final int SCHEMA_KEYSTROKES = 2;
    /** Highest schema this build understands; anything above it was written by a newer one. */
    private static final int MAX_SCHEMA = 2;

    /** A legitimate full-config code is a couple of KB; anything near this is an attack. */
    private static final int MAX_ENCODED_CHARS = 64 * 1024;
    /** Without this, a few hundred KB of base64 inflates to hundreds of MB and OOMs the client. */
    private static final int MAX_INFLATED_BYTES = 512 * 1024;

    private static final Gson GSON = new GsonBuilder().create();

    private HudShareCodec() {}

    /** Result of decoding: the envelope type and payload, plus who wrote it. */
    public record Decoded(String type, String payloadJson, int schemaVersion, String modVersion) {}

    // --- Encoding ---

    /** Share code for the entire current config. */
    public static String encodeConfig() {
        SimpleCPSConfig.collectModuleState(); // this path does not go through save()
        return encode(TYPE_CONFIG, GSON.toJsonTree(SimpleCPSConfig.instance), SCHEMA_CONFIG);
    }

    /**
     * Share code for the whole Keystrokes look — the key list and the design,
     * animation and palette it was drawn with.
     *
     * <p>It used to carry only {@code keystrokesLayout}, which meant the geometry
     * arrived wearing whatever design the receiver already had: a compass layout
     * repainted as a plain grid. See {@link SimpleCPSConfig.KeystrokesShare}.
     */
    public static String encodeKeystrokes() {
        return encode(TYPE_KEYSTROKES,
            GSON.toJsonTree(SimpleCPSConfig.KeystrokesShare.capture(SimpleCPSConfig.instance)),
            SCHEMA_KEYSTROKES);
    }

    /** Mod version of this install, or "" if it cannot be determined. */
    public static String modVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("simplecps")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("");
        } catch (Exception e) {
            return "";
        }
    }

    /** True when the code was written by a different build — worth telling the user. */
    public static boolean isFromOtherVersion(Decoded decoded) {
        if (decoded == null || decoded.modVersion().isEmpty()) return false;
        String here = modVersion();
        return !here.isEmpty() && !here.equals(decoded.modVersion());
    }

    private static String encode(String type, com.google.gson.JsonElement payload, int schema) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("v", schema);
        envelope.addProperty("mv", modVersion());
        envelope.addProperty("t", type);
        envelope.add("d", payload);

        byte[] raw = GSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(raw);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    // --- Decoding ---

    /** @return the decoded envelope, or null if {@code code} is not a valid share code. */
    public static Decoded decode(String code) {
        if (code == null) return null;
        String s = code.trim();
        if (!s.startsWith(PREFIX)) return null;
        if (s.length() > MAX_ENCODED_CHARS) return null;
        s = s.substring(PREFIX.length()).replaceAll("\\s", "");

        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gz.read(buf)) > 0) {
                // Stop before allocating, not after: the whole point is to never let a
                // crafted code decide how much heap we hand it.
                if (out.size() + n > MAX_INFLATED_BYTES) return null;
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            return null;
        }

        try {
            JsonObject envelope = JsonParser.parseString(out.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            // Codes written before "v" existed are schema 1 by definition.
            int schema = envelope.has("v") ? envelope.get("v").getAsInt() : 1;
            if (schema > MAX_SCHEMA) return null; // written by a newer build
            String type = envelope.get("t").getAsString();
            String writerVersion = envelope.has("mv") ? envelope.get("mv").getAsString() : "";
            return new Decoded(type, envelope.get("d").toString(), schema, writerVersion);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Apply a decoded share code to the live config and persist it. The caller
     * should back up the current config first (see
     * {@link com.eymistaken.simplecps.ConfigPresetManager#savePreset(String)}).
     *
     * <p>The incoming config is parsed, repaired and validated as a detached object;
     * {@link SimpleCPSConfig#instance} is only reassigned once that has all succeeded.
     * So a false return really does mean nothing changed.
     *
     * @return true if it applied cleanly
     */
    public static boolean apply(Decoded decoded) {
        if (decoded == null) return false;
        try {
            switch (decoded.type()) {
                case TYPE_CONFIG -> {
                    SimpleCPSConfig loaded = GSON.fromJson(decoded.payloadJson(), SimpleCPSConfig.class);
                    if (loaded == null) return false;
                    loaded.normalize();
                    // Keep settings belonging to modules the sender did not have, so a
                    // code from a differently-modded friend does not wipe them.
                    SimpleCPSConfig.mergeForeignState(SimpleCPSConfig.instance, loaded);
                    SimpleCPSConfig.instance = loaded;
                    SimpleCPSConfig.distributeModuleState();
                }
                case TYPE_KEYSTROKES -> {
                    SimpleCPSConfig.KeystrokesShare share = readKeystrokes(decoded.payloadJson());
                    if (share == null) return false;
                    if (!share.applyTo(SimpleCPSConfig.instance)) return false;
                    SimpleCPSConfig.instance.normalize();
                }
                default -> {
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        SimpleCPSConfig.save();
        return true;
    }

    /**
     * Read a keystrokes payload in either shape it has ever had.
     *
     * <p>The shape is what decides, not the stamped schema: schema 1 wrote a bare key
     * list, schema 2 writes the whole look as an object. A schema 1 code still imports
     * — it just brings no look with it, so the receiver keeps the design and palette it
     * already had, which is exactly what those codes have always done.
     *
     * @return the share to apply, or null if the payload is neither shape
     */
    private static SimpleCPSConfig.KeystrokesShare readKeystrokes(String payloadJson) {
        com.google.gson.JsonElement payload = JsonParser.parseString(payloadJson);
        if (payload.isJsonObject()) {
            return GSON.fromJson(payload, SimpleCPSConfig.KeystrokesShare.class);
        }
        if (payload.isJsonArray()) {
            List<SimpleCPSConfig.KeyButtonData> layout = GSON.fromJson(
                payload, new TypeToken<List<SimpleCPSConfig.KeyButtonData>>() {}.getType());
            SimpleCPSConfig.KeystrokesShare share =
                SimpleCPSConfig.KeystrokesShare.capture(SimpleCPSConfig.instance);
            share.layout = layout;
            return share;
        }
        return null;
    }
}
