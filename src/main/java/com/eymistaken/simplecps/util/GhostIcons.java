package com.eymistaken.simplecps.util;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gray stand-ins for the armor HUD's empty slots in the editor: the diamond piece that
 * belongs in each slot, desaturated and darkened so it reads as "this goes here" rather
 * than as gear the player has on.
 *
 * <p>They are built from the textures of whatever resource pack is active, not from art of
 * our own, so a pack that redraws diamond armor gets ghosts in its own shape. Each one is
 * made once, on first use, and thrown away on every resource reload so the next draw picks
 * up the new pack. A texture that cannot be read is remembered as missing until that reload,
 * and the caller falls back to dimming the real item instead.
 */
public final class GhostIcons {
    private static final Logger LOGGER = LoggerFactory.getLogger("Eymistaken's HUD");

    /** Built icons by source texture; a null value marks one that could not be read. */
    private static final Map<Identifier, Identifier> BUILT = new HashMap<>();
    /** How much of each texture's height one frame takes; under 1 for an animation strip. */
    private static final Map<Identifier, Float> FRAME_V = new HashMap<>();

    private GhostIcons() {}

    /**
     * Draws the gray icon for {@code source}, a texture path such as
     * {@code textures/item/diamond_helmet.png}, as a 16x16 square at {@code x, y}.
     *
     * @return false when the texture is unavailable and nothing was drawn
     */
    public static boolean draw(GuiGraphicsExtractor context, Identifier source, int x, int y) {
        Identifier icon = BUILT.containsKey(source) ? BUILT.get(source) : build(source);
        if (icon == null) return false;
        context.blit(icon, x, y, x + 16, y + 16, 0f, 1f, 0f, FRAME_V.getOrDefault(source, 1f));
        return true;
    }

    /** Releases every built icon. Registered as a client resource reload listener. */
    public static void invalidate() {
        Minecraft client = Minecraft.getInstance();
        for (Identifier icon : BUILT.values()) {
            if (icon != null) client.getTextureManager().release(icon);
        }
        BUILT.clear();
        FRAME_V.clear();
    }

    private static Identifier build(Identifier source) {
        Identifier icon = null;
        Minecraft client = Minecraft.getInstance();
        Optional<Resource> resource = client.getResourceManager().getResource(source);
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) {
                NativeImage image = NativeImage.read(in);
                desaturate(image);
                int w = image.getWidth();
                int h = image.getHeight();
                // A pack may animate the item as a vertical strip of square frames; the
                // ghost only needs the first one.
                FRAME_V.put(source, h > w ? (float) w / h : 1f);
                icon = Identifier.fromNamespaceAndPath("simplecps",
                    "ghost/" + source.getPath().replace(".png", ""));
                Identifier name = icon;
                client.getTextureManager().register(icon, new DynamicTexture(() -> "simplecps " + name, image));
            } catch (Exception e) {
                LOGGER.warn("Could not build the armor HUD ghost icon from {}", source, e);
                icon = null;
            }
        }
        BUILT.put(source, icon);
        return icon;
    }

    /**
     * Replaces each pixel with its luminance squeezed into a dark band, keeping its alpha.
     * The band starts below the empty slot's own fill and ends a little above it, so the
     * outline sinks into the slot and the highlights still pick the shape out.
     */
    private static void desaturate(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getPixel(x, y);
                int a = argb >>> 24;
                if (a == 0) continue;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
                int v = Math.round(28 + lum * 74);
                image.setPixel(x, y, (a << 24) | (v << 16) | (v << 8) | v);
            }
        }
    }
}
