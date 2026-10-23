package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.api.IHudElement;
import com.eymistaken.simplecps.gui.HudEditorScreen;
import com.eymistaken.simplecps.util.GhostIcons;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ArmorModule extends HudModule {

    // Durability tracking per slot, by ArmorSlot ordinal. Fed from tick() with the player's
    // own gear only, so the settings preview can never disturb it. A hit is a rise in damage
    // on the same item; anything else, from a swap to a new piece, just moves the baseline.
    private final Item[] lastItems = new Item[6];
    private final int[] lastDamages = new int[6];
    private final int[] lastBarWidths = new int[6];
    private int lastSelectedSlot = -1;
    /** When each slot last lost durability, and how long its bar was just before. */
    private final long[] hitTimes = new long[6];
    private final int[] chipFromWidths = new int[6];

    /** How often the settings preview pretends to take a hit, so the hit effects can be judged. */
    private static final int PREVIEW_HIT_PERIOD = 2000;
    /** Past this, every hit effect has finished playing. */
    private static final long HIT_DONE = 2000;

    /**
     * Gear the preview wears. This module is the one built-in that draws nothing at all
     * without a player — no equipment, no slots, no box — so the settings preview has to
     * bring its own, and it is the module whose look is hardest to picture from the
     * settings list alone.
     *
     * <p>Wear is set as a fraction of each item's own max damage rather than a raw number,
     * so the spread across the durability colors survives any rebalance, and it is spread
     * on purpose: green, yellow and red all on show at once.
     */
    private static ItemStack[] previewStacks;

    private static ItemStack previewStack(net.minecraft.world.item.Item item, float worn) {
        ItemStack stack = new ItemStack(item);
        if (stack.isDamageableItem()) {
            stack.setDamageValue(Math.round(stack.getMaxDamage() * worn));
        }
        return stack;
    }

    /** Built on first use, not in a static initializer, so item registries are up. */
    private static ItemStack previewStackFor(ArmorSlot slot) {
        if (previewStacks == null) {
            previewStacks = new ItemStack[] {
                previewStack(net.minecraft.world.item.Items.DIAMOND_HELMET, 0.20f),
                previewStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE, 0.55f),
                previewStack(net.minecraft.world.item.Items.DIAMOND_LEGGINGS, 0.45f),
                previewStack(net.minecraft.world.item.Items.DIAMOND_BOOTS, 0.85f),
                previewStack(net.minecraft.world.item.Items.DIAMOND_SWORD, 0.30f),
                previewStack(net.minecraft.world.item.Items.SHIELD, 0.10f),
            };
        }
        return previewStacks[slot.ordinal()];
    }

    private static final net.minecraft.resources.Identifier SLOT_TEXTURE = net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "hud/hotbar");

    /** One slot's edge length before scaling; the whole layout is built on this grid. */
    private static final int SLOT = 20;

    private static float scale() {
        return SimpleCPSConfig.instance.armorScale / 100f;
    }

    /** Unscaled local length to on-screen pixels. */
    private static int scaled(int local) {
        return Math.round(local * scale());
    }

    public enum ArmorSlot {
        HELMET("Helmet", () -> SimpleCPSConfig.instance.armorHelmetXOffset, v -> SimpleCPSConfig.instance.armorHelmetXOffset = v, () -> SimpleCPSConfig.instance.armorHelmetYOffset, v -> SimpleCPSConfig.instance.armorHelmetYOffset = v),
        CHESTPLATE("Chestplate", () -> SimpleCPSConfig.instance.armorChestXOffset, v -> SimpleCPSConfig.instance.armorChestXOffset = v, () -> SimpleCPSConfig.instance.armorChestYOffset, v -> SimpleCPSConfig.instance.armorChestYOffset = v),
        LEGGINGS("Leggings", () -> SimpleCPSConfig.instance.armorLeggingsXOffset, v -> SimpleCPSConfig.instance.armorLeggingsXOffset = v, () -> SimpleCPSConfig.instance.armorLeggingsYOffset, v -> SimpleCPSConfig.instance.armorLeggingsYOffset = v),
        BOOTS("Boots", () -> SimpleCPSConfig.instance.armorBootsXOffset, v -> SimpleCPSConfig.instance.armorBootsXOffset = v, () -> SimpleCPSConfig.instance.armorBootsYOffset, v -> SimpleCPSConfig.instance.armorBootsYOffset = v),
        MAIN_HAND("Main Hand", () -> SimpleCPSConfig.instance.armorMainHandXOffset, v -> SimpleCPSConfig.instance.armorMainHandXOffset = v, () -> SimpleCPSConfig.instance.armorMainHandYOffset, v -> SimpleCPSConfig.instance.armorMainHandYOffset = v),
        OFF_HAND("Off Hand", () -> SimpleCPSConfig.instance.armorOffHandXOffset, v -> SimpleCPSConfig.instance.armorOffHandXOffset = v, () -> SimpleCPSConfig.instance.armorOffHandYOffset, v -> SimpleCPSConfig.instance.armorOffHandYOffset = v);

        public final String name;
        public final java.util.function.Supplier<Integer> xGet;
        public final java.util.function.Consumer<Integer> xSet;
        public final java.util.function.Supplier<Integer> yGet;
        public final java.util.function.Consumer<Integer> ySet;

        ArmorSlot(String name, java.util.function.Supplier<Integer> xGet, java.util.function.Consumer<Integer> xSet, java.util.function.Supplier<Integer> yGet, java.util.function.Consumer<Integer> ySet) {
            this.name = name;
            this.xGet = xGet;
            this.xSet = xSet;
            this.yGet = yGet;
            this.ySet = ySet;
        }
    }

    public class ArmorElement implements IHudElement {
        private final ArmorSlot slot;
        /** On-screen position, for the editor's hit testing and selection borders. */
        private int cachedX, cachedY;
        /** The same spot in the module's own unscaled grid, which is what we draw in. */
        private int localX, localY;

        public ArmorElement(ArmorSlot slot) {
            this.slot = slot;
        }

        @Override
        public int getX() {
            return cachedX;
        }

        @Override
        public int getY() {
            return cachedY;
        }

        @Override
        public int getWidth() {
            return Math.max(1, scaled(SLOT));
        }

        @Override
        public int getHeight() {
            boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
            ItemStack stack = getItemStack();
            if (stack != null && !stack.isEmpty() && showText && stack.isDamageableItem()) {
                boolean vertical = SimpleCPSConfig.instance.armorVertical;
                if (!vertical) return Math.max(1, scaled(30));
            }
            return Math.max(1, scaled(SLOT));
        }

        @Override
        public int getXOffset() {
            return slot.xGet.get();
        }

        @Override
        public int getYOffset() {
            return slot.yGet.get();
        }

        @Override
        public void setXOffset(int x) {
            slot.xSet.accept(x);
        }

        @Override
        public void setYOffset(int y) {
            slot.ySet.accept(y);
        }

        @Override
        public String getName() {
            return slot.name;
        }

        @Override
        public void resetToDefaults() {
            slot.xSet.accept(0);
            slot.ySet.accept(0);
        }

        public ItemStack getItemStack() {
            if (ArmorModule.this.isPreviewing()) return previewStackFor(slot);
            if (client.player == null) return ItemStack.EMPTY;
            return liveStack(slot);
        }

        /** Whether this slot is drawn at all; {@link #getOrderedElements()} decides. */
        public boolean isEnabled() {
            return getOrderedElements().contains(this);
        }

        /** Whether the settings let this slot appear, whatever it holds. */
        private boolean slotShown() {
            if (slot == ArmorSlot.MAIN_HAND && !SimpleCPSConfig.instance.armorShowMainHand) return false;
            if (slot == ArmorSlot.OFF_HAND && !SimpleCPSConfig.instance.armorShowOffHand) return false;
            return true;
        }

        private boolean hasItem() {
            ItemStack stack = getItemStack();
            return stack != null && !stack.isEmpty();
        }

        private boolean isDetached() {
            return getXOffset() != 0 || getYOffset() != 0;
        }

        /** An empty slot the editor shows anyway, so it can still be placed. */
        public boolean isGhost() {
            return isEditing() && !hasItem();
        }

        @Override
        public void onPositionUpdated() {
            com.eymistaken.simplecps.SimpleCPSConfig.save();
        }

        @Override
        public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
            java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>();
            boolean anyDetached = false;
            for (ArmorElement el : subElements) {
                if (el.getXOffset() != 0 || el.getYOffset() != 0) {
                    anyDetached = true;
                    break;
                }
            }
            
            if (anyDetached) {
                settings.add(new com.eymistaken.simplecps.api.ActionSetting("Group", () -> {
                    List<ArmorElement> ordered = getOrderedElements();
                    int index = ordered.indexOf(this);
                    if (index != -1) {
                        boolean vertical = SimpleCPSConfig.instance.armorVertical;
                        int step = scaled(index * SLOT);
                        int newParentX, newParentY;
                        if (vertical) {
                            newParentX = this.getX();
                            newParentY = this.getY() - step;
                        } else {
                            newParentX = this.getX() - step;
                            newParentY = this.getY();
                        }

                        SimpleCPSConfig.Position anchor = getPositionType();
                        net.minecraft.client.gui.screens.Screen screen = client.gui.screen();
                        int screenW = screen != null ? screen.width : 400;
                        int screenH = screen != null ? screen.height : 300;

                        int baseX = 0;
                        int baseY = 0;
                        int gap = 5;

                        // Grouped size, in screen pixels. Computed here rather than through
                        // getWidth()/getHeight() because those still see the detached layout.
                        int finalW = 0;
                        int finalH = 0;
                        if (!ordered.isEmpty()) {
                            finalW = scaled(localWidth());
                            finalH = scaled(localHeight());
                        }

                        switch (anchor) {
                            case TOP_LEFT -> { baseX = gap; baseY = gap; }
                            case TOP_RIGHT -> { baseX = screenW - finalW - gap; baseY = gap; }
                            case BOTTOM_LEFT -> { baseX = gap; baseY = screenH - finalH - gap; }
                            case BOTTOM_RIGHT -> { baseX = screenW - finalW - gap; baseY = screenH - finalH - gap; }
                            case CENTER -> { baseX = (screenW - finalW) / 2; baseY = (screenH - finalH) / 2; }
                        }
                        
                        ArmorModule.this.setXOffset(newParentX - baseX);
                        ArmorModule.this.setYOffset(newParentY - baseY);
                    }
                    
                    // Reset all sub-elements' offsets to 0
                    for (ArmorElement el : subElements) {
                        el.resetToDefaults();
                    }
                    com.eymistaken.simplecps.SimpleCPSConfig.save();
                }));
            }
            return settings;
        }
    }

    private final List<ArmorElement> subElements = List.of(
        new ArmorElement(ArmorSlot.HELMET),
        new ArmorElement(ArmorSlot.CHESTPLATE),
        new ArmorElement(ArmorSlot.LEGGINGS),
        new ArmorElement(ArmorSlot.BOOTS),
        new ArmorElement(ArmorSlot.MAIN_HAND),
        new ArmorElement(ArmorSlot.OFF_HAND)
    );

    /**
     * Whether the in-world editor is open over the live HUD. The settings preview is not the
     * editor even when opened from it: it brings its own gear and never shows ghosts.
     */
    private boolean isEditing() {
        return !isPreviewing() && client.gui.screen() instanceof HudEditorScreen;
    }

    /**
     * The slots to draw, in the order they stack. In play that is whatever is worn or held.
     *
     * <p>The editor has to show something to place even when nothing is, so it adds ghosts
     * by two rules. When none of the grouped slots holds anything, every grouped slot is
     * shown as a ghost: otherwise the whole group would vanish and could not be dragged. As
     * soon as one does, only the real items show, because that is what the HUD will look
     * like. A detached slot is its own little module, so it always shows, as a ghost when
     * empty, whatever the group is doing.
     */
    private List<ArmorElement> getOrderedElements() {
        List<ArmorElement> ordered = new ArrayList<>();
        if (client.player == null && !isPreviewing()) return ordered;
        // The preview can run from the main menu, where there is no player to ask which
        // hand is which; right is the vanilla default.
        boolean isRightHanded = client.player == null
            || client.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;

        ArmorElement helmet = subElements.get(0);
        ArmorElement chest = subElements.get(1);
        ArmorElement legs = subElements.get(2);
        ArmorElement boots = subElements.get(3);
        ArmorElement mainHand = subElements.get(4);
        ArmorElement offHand = subElements.get(5);

        List<ArmorElement> slots = isRightHanded
            ? List.of(offHand, helmet, chest, legs, boots, mainHand)
            : List.of(mainHand, helmet, chest, legs, boots, offHand);

        boolean editing = isEditing();
        boolean groupHasItem = false;
        if (editing) {
            for (ArmorElement el : slots) {
                if (el.slotShown() && !el.isDetached() && el.hasItem()) {
                    groupHasItem = true;
                    break;
                }
            }
        }

        for (ArmorElement el : slots) {
            if (!el.slotShown()) continue;
            if (el.hasItem() || (editing && (el.isDetached() || !groupHasItem))) {
                ordered.add(el);
            }
        }
        return ordered;
    }

    private List<ArmorElement> getNonDraggedElements() {
        List<ArmorElement> list = new ArrayList<>();
        for (ArmorElement el : getOrderedElements()) {
            if (el.getXOffset() == 0 && el.getYOffset() == 0) {
                list.add(el);
            }
        }
        return list;
    }

    @Override
    public List<com.eymistaken.simplecps.api.IHudElement> getSubElements() {
        boolean anyDetached = false;
        for (ArmorElement el : subElements) {
            if (el.getXOffset() != 0 || el.getYOffset() != 0) {
                anyDetached = true;
                break;
            }
        }
        if (!anyDetached) {
            return Collections.emptyList();
        }
        return new ArrayList<>(getOrderedElements());
    }

    @Override
    public SimpleCPSConfig.Position getPositionType() {
        return SimpleCPSConfig.instance.armorPosition;
    }

    @Override
    public int getXOffset() {
        return SimpleCPSConfig.instance.armorXOffset;
    }

    @Override
    public int getYOffset() {
        return SimpleCPSConfig.instance.armorYOffset;
    }

    @Override
    public boolean isEnabled() {
        return SimpleCPSConfig.instance.showArmor;
    }

    @Override
    public int getWidth() {
        return Math.max(1, scaled(localWidth()));
    }

    @Override
    public int getHeight() {
        return Math.max(1, scaled(localHeight()));
    }

    /** Width on the unscaled grid. Kept separate so Group can reuse it. */
    private int localWidth() {
        List<ArmorElement> items = getOrderedElements();
        if (items.isEmpty()) return SLOT;

        boolean vertical = SimpleCPSConfig.instance.armorVertical;
        boolean showText = SimpleCPSConfig.instance.armorDurabilityText;

        if (vertical) {
            int w = SLOT;
            if (showText) {
                int maxText = 0;
                for (ArmorElement sub : items) {
                    ItemStack stack = sub.getItemStack();
                    if (stack.isDamageableItem()) {
                        int durability = stack.getMaxDamage() - stack.getDamageValue();
                        maxText = Math.max(maxText, client.font.width(com.eymistaken.simplecps.util.EymHudFonts.text(String.valueOf(durability))));
                    }
                }
                if (maxText > 0) {
                    w += maxText + 2;
                }
            }
            return w;
        } else {
            return items.size() * SLOT;
        }
    }

    /** Height on the unscaled grid. */
    private int localHeight() {
        List<ArmorElement> items = getOrderedElements();
        if (items.isEmpty()) return SLOT;

        boolean vertical = SimpleCPSConfig.instance.armorVertical;
        boolean showText = SimpleCPSConfig.instance.armorDurabilityText;

        if (vertical) {
            return items.size() * SLOT;
        } else {
            int h = SLOT;
            if (showText) {
                boolean anyDmg = items.stream().anyMatch(sub -> sub.getItemStack().isDamageableItem());
                if (anyDmg) {
                    h += 10;
                }
            }
            return h;
        }
    }

    /** {@code slotX/slotY} are local grid coordinates; the caller has already scaled. */
    private void drawSlotBackground(GuiGraphicsExtractor context, List<ArmorElement> ordered, ArmorElement el, int slotX, int slotY) {
        boolean hasLeft = ordered.stream().anyMatch(other -> other != el && Math.abs(other.localX - (slotX - SLOT)) <= 1 && Math.abs(other.localY - slotY) <= 1);
        boolean hasRight = ordered.stream().anyMatch(other -> other != el && Math.abs(other.localX - (slotX + SLOT)) <= 1 && Math.abs(other.localY - slotY) <= 1);
        
        if (!hasLeft && !hasRight) {
            // Isolated Slot
            context.blitSprite(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                SLOT_TEXTURE,
                182, 22,
                0, 0,
                slotX - 2, slotY - 2,
                21, 22
            );
            context.blitSprite(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                SLOT_TEXTURE,
                182, 22,
                181, 0,
                slotX + 19, slotY - 2,
                1, 22
            );
        } else if (!hasLeft && hasRight) {
            // Leftmost Slot of a horizontal row
            context.blitSprite(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                SLOT_TEXTURE,
                182, 22,
                0, 0,
                slotX - 2, slotY - 2,
                20, 22
            );
        } else if (hasLeft && hasRight) {
            // Middle Slot of a horizontal row
            context.blitSprite(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                SLOT_TEXTURE,
                182, 22,
                20, 0,
                slotX - 2, slotY - 2,
                20, 22
            );
        } else {
            // Rightmost Slot of a horizontal row
            context.blitSprite(
                net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                SLOT_TEXTURE,
                182, 22,
                160, 0,
                slotX - 2, slotY - 2,
                22, 22
            );
        }
    }

    @Override
    public com.eymistaken.simplecps.api.HudPreview getPreview() {
        return com.eymistaken.simplecps.api.HudPreview.ofModule(this);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        if (client.player == null && !isPreviewing()) return;

        List<ArmorElement> ordered = getOrderedElements();
        if (ordered.isEmpty()) return;
        
        boolean vertical = SimpleCPSConfig.instance.armorVertical;
        boolean showBg = SimpleCPSConfig.instance.armorShowBackgroundSlots;
        boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
        long now = System.currentTimeMillis();
        float s = scale();

        // Everything below is drawn in the module's own unscaled grid and scaled as a
        // whole, so the slot textures, dividers and durability text keep lining up at
        // any size. Only cachedX/cachedY are converted to screen pixels, because that
        // is what the editor hit-tests and draws selection borders against.
        int stackX = 0;
        int stackY = 0;
        for (int i = 0; i < ordered.size(); i++) {
            ArmorElement el = ordered.get(i);
            int elX, elY;
            if (el.getXOffset() != 0 || el.getYOffset() != 0) {
                // Offsets are stored in screen pixels (that is what the editor drags),
                // so undo the scale to get back onto the local grid.
                elX = Math.round(el.getXOffset() / s);
                elY = Math.round(el.getYOffset() / s);
            } else {
                elX = stackX;
                elY = stackY;
                if (vertical) {
                    stackY += SLOT;
                } else {
                    stackX += SLOT;
                }
            }
            el.localX = elX;
            el.localY = elY;
            el.cachedX = this.x + Math.round(elX * s);
            el.cachedY = this.y + Math.round(elY * s);
        }

        context.pose().pushMatrix();
        context.pose().translate((float) this.x, (float) this.y);
        context.pose().scale(s, s);

        // Pass 1: Background drawing
        if (showBg) {
            for (ArmorElement el : ordered) {
                drawSlotBackground(context, ordered, el, el.localX, el.localY);
            }
        }

        // Pass 2: Draw Horizontal Dividers between vertically stacked touching slots
        if (showBg) {
            for (ArmorElement el : ordered) {
                int slotX = el.localX;
                int slotY = el.localY;

                boolean hasBottom = ordered.stream().anyMatch(other -> other != el && Math.abs(other.localX - slotX) <= 1 && Math.abs(other.localY - (slotY + SLOT)) <= 1);

                if (hasBottom) {
                    // Draw horizontal divider to cover the adjacent black borders
                    // Dark shadow on the top side of divider, light highlight on the bottom side
                    context.fill(slotX - 1, slotY + 18, slotX + 19, slotY + 19, 0xFF3C3C3C);
                    context.fill(slotX - 1, slotY + 19, slotX + 19, slotY + 20, 0xFF8F8F8F);
                }
            }
        }

        // Pass 3: items, with whatever motion their effects give them
        for (ArmorElement el : ordered) {
            if (el.isGhost()) {
                drawGhost(context, el, showBg);
            } else {
                drawItem(context, el, now, showText, vertical);
            }
        }

        // Pass 4: effects that paint over the items. Last so a badge or a spark that reaches
        // into a neighboring slot is not covered by that slot's item.
        for (ArmorElement el : ordered) {
            if (!el.isGhost()) drawOverlays(context, el, now);
        }

        context.pose().popMatrix();
    }

    /** The texture each slot's ghost is made from. */
    private static Identifier ghostSource(ArmorSlot slot) {
        String path = switch (slot) {
            case HELMET -> "textures/item/diamond_helmet.png";
            case CHESTPLATE -> "textures/item/diamond_chestplate.png";
            case LEGGINGS -> "textures/item/diamond_leggings.png";
            case BOOTS -> "textures/item/diamond_boots.png";
            case MAIN_HAND -> "textures/item/diamond_sword.png";
            // The shield is a 3D model with no flat texture to gray; the empty off-hand
            // slot's own silhouette is the one shape that means "a shield goes here".
            case OFF_HAND -> "textures/gui/sprites/container/slot/shield.png";
        };
        return Identifier.withDefaultNamespace(path);
    }

    /** An empty slot in the editor: a sunken dark square with the piece it takes, in gray. */
    private void drawGhost(GuiGraphicsExtractor context, ArmorElement el, boolean showBg) {
        int x = el.localX;
        int y = el.localY;
        if (showBg) {
            // The hotbar slot is already behind it; only its inside is darkened.
            context.fill(x + 1, y + 1, x + 17, y + 17, 0xE0181818);
        } else {
            context.fill(x, y, x + 18, y + 18, 0xFF5C5C5C);
            context.fill(x, y, x + 17, y + 17, 0xFF151515);
            context.fill(x + 1, y + 1, x + 17, y + 17, 0xFF2E2E2E);
        }
        if (!GhostIcons.draw(context, ghostSource(el.slot), x + 1, y + 1)) {
            // No texture to gray (a broken pack): dim the real piece instead, so the slot
            // still says what belongs in it.
            context.item(previewStackFor(el.slot), x + 1, y + 1);
            context.fill(x + 1, y + 1, x + 17, y + 17, 0xB0101010);
        }
    }

    /**
     * Milliseconds since this slot last lost durability, or {@link #HIT_DONE} when no effect
     * is still playing. The preview takes a pretend hit every {@link #PREVIEW_HIT_PERIOD},
     * on every damageable piece at once, which is what a real hit does to armor.
     */
    private long sinceHit(ArmorElement el, ItemStack stack, long now) {
        if (!SimpleCPSConfig.instance.armorDamageFlash || !stack.isDamageableItem()) return HIT_DONE;
        if (isPreviewing()) return now % PREVIEW_HIT_PERIOD;
        long t = now - hitTimes[el.slot.ordinal()];
        return t >= 0 && t < HIT_DONE ? t : HIT_DONE;
    }

    /** How long the bar was before the hit now playing, for the chip effect. */
    private int chipFromWidth(ArmorElement el, ItemStack stack) {
        if (isPreviewing()) return Math.min(13, stack.getBarWidth() + 3);
        return chipFromWidths[el.slot.ordinal()];
    }

    /**
     * How far under the low-durability threshold the item is: 0 at the threshold, 1 when
     * it is about to break, and negative above the threshold, where the warnings stay off.
     */
    private static float lowness(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return -1f;
        float left = 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
        float threshold = SimpleCPSConfig.instance.armorLowThreshold / 100f;
        if (left >= threshold) return -1f;
        return Math.min(1f, 1f - left / threshold);
    }

    private static float strength(int percent) {
        return percent / 100f;
    }

    /**
     * Draws one item, moved and scaled by its effects, with its bar and durability text.
     *
     * <p>Every motion here is a function of time alone, never of a random number or a
     * counter stepped per frame: the same hit always plays the same way, and the preview,
     * drawn alongside the live HUD, cannot speed it up.
     */
    private void drawItem(GuiGraphicsExtractor context, ArmorElement el, long now, boolean showText, boolean vertical) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        ItemStack stack = el.getItemStack();
        int elX = el.localX;
        int elY = el.localY;
        long t = sinceHit(el, stack, now);
        float k = lowness(stack);

        int ox = 0;
        int oy = 0;
        float scale = 1f;

        // Shake: a short hold knocked up and over, then a ring that dies away.
        float shake = strength(config.armorHitShake);
        if (shake > 0 && t < 380) {
            if (t < 70) {
                ox += Math.round(shake);
                oy -= Math.round(shake);
            } else {
                ox += Math.round(2 * shake * Math.exp(-(t - 70) / 100.0) * Math.sin((t - 70) / 22.0));
            }
        }

        if (k >= 0) {
            // Pulse: a red ground behind the item, breathing faster as it nears breaking.
            float pulse = strength(config.armorLowPulse);
            if (pulse > 0) {
                double breath = 0.5 + 0.5 * Math.sin(now / (380.0 - 180.0 * k));
                float a = (float) Math.min(0.9, ((0.2 + 0.35 * k) * breath + 0.08) * pulse);
                context.fill(elX + 1, elY + 1, elX + 17, elY + 17, alpha(0xFF2828, a));
                int ring = alpha(0xFF6E6E, Math.min(1f, a + 0.2f));
                context.fill(elX + 1, elY + 1, elX + 17, elY + 2, ring);
                context.fill(elX + 1, elY + 16, elX + 17, elY + 17, ring);
                context.fill(elX + 1, elY + 2, elX + 2, elY + 16, ring);
                context.fill(elX + 16, elY + 2, elX + 17, elY + 16, ring);
            }

            // Tremble: grown a little for as long as it is low, and shivering in bursts
            // that come closer together the lower it gets.
            float tremble = strength(config.armorLowTremble);
            if (tremble > 0) {
                scale *= 1f + 0.12f * k * tremble;
                long period = Math.round(1500 - 800 * k);
                long phase = now % period;
                if (phase < 300) ox += Math.round(Math.sin(phase / 28.0) * (1 + 0.6 * k) * tremble);
            }

            // Heartbeat: a double beat whose tempo climbs from 60 to 150 a minute.
            float heart = strength(config.armorLowHeartbeat);
            if (heart > 0) {
                double beat = 60000.0 / (60 + 90 * k);
                double phase = (now % (long) beat) / beat;
                double kick = bump(phase, 0, 0.14) + 0.6 * bump(phase, 0.22, 0.14);
                scale *= (float) (1 + (0.06 + 0.1 * k) * heart * kick);
            }
        }

        int ix = elX + 1;
        int iy = elY + 1;
        context.pose().pushMatrix();
        context.pose().translate((float) ox, (float) oy);
        if (scale != 1f) {
            context.pose().translate(ix + 8f, iy + 8f);
            context.pose().scale(scale, scale);
            context.pose().translate(-(ix + 8f), -(iy + 8f));
        }
        context.item(stack, ix, iy);
        context.itemDecorations(client.font, stack, ix, iy);

        // Chip: what the hit took stays on the bar in white for a moment, then drains away,
        // so a small loss is visible at all.
        float chip = strength(config.armorHitChip);
        if (chip > 0 && t < HIT_DONE && stack.isBarVisible()) {
            int to = stack.getBarWidth();
            int from = chipFromWidth(el, stack);
            float hold = 250 * chip;
            float drain = 300 * chip;
            float shown = t < hold ? from : t < hold + drain ? from - (from - to) * ((t - hold) / drain) : to;
            int end = Math.round(shown);
            if (end > to) context.fill(ix + 2 + to, iy + 13, ix + 2 + end, iy + 14, 0xFFFFFFFF);
        }
        context.pose().popMatrix();

        if (showText && stack.isDamageableItem()) {
            int durability = stack.getMaxDamage() - stack.getDamageValue();
            String text = String.valueOf(durability);
            int tw = client.font.width(com.eymistaken.simplecps.util.EymHudFonts.text(text));
            int color = getDurabilityColor(durability, stack.getMaxDamage());

            if (vertical) {
                context.text(client.font, com.eymistaken.simplecps.util.EymHudFonts.text(text), elX + 20, elY + 5, color);
            } else {
                context.text(client.font, com.eymistaken.simplecps.util.EymHudFonts.text(text), elX + 9 - tw / 2, elY + 20, color);
            }
        }
    }

    /** Sparks, warning corners and the low-durability badge, drawn over every item. */
    private void drawOverlays(GuiGraphicsExtractor context, ArmorElement el, long now) {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        ItemStack stack = el.getItemStack();
        int elX = el.localX;
        int elY = el.localY;
        long t = sinceHit(el, stack, now);

        // Sparks: eight pixels thrown out from the item, slowing as they fade.
        float sparks = strength(config.armorHitSparks);
        if (sparks > 0 && t < 340) {
            float p = t / 340f;
            float travel = 1f - (1f - p) * (1f - p) * (1f - p);
            float reach = 3 + 8 * travel * sparks;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4 + 0.39;
                int px = (int) Math.round(elX + 9 + Math.cos(angle) * reach);
                int py = (int) Math.round(elY + 9 + Math.sin(angle) * reach);
                context.fill(px, py, px + 1, py + 1, alpha(i % 2 == 0 ? 0xFF4A3A : 0xFFB03A, 1f - p));
            }
        }

        // Corners: red brackets around the slot that blink twice, closing in on the second.
        float corners = strength(config.armorHitCorners);
        if (corners > 0 && (t < 90 || (t >= 170 && t < 260))) {
            int in = t < 90 ? 0 : 1;
            int len = Math.max(2, Math.min(7, Math.round(4 * corners)));
            drawBrackets(context, elX + in, elY + in, elX + 17 - in, elY + 17 - in, len, 0xFFFF3B30);
        }

        // Badge: a little red "!" on the corner that hops and wiggles now and then, more
        // often the closer the item is to breaking.
        float badge = strength(config.armorLowBadge);
        float k = lowness(stack);
        if (badge > 0 && k >= 0) {
            long period = Math.round(1600 - 800 * k);
            long phase = now % period;
            int dx = 0;
            int dy = 0;
            if (phase < 320) {
                int[] wiggle = {-1, 1, -1, 1, 0};
                dx = Math.round(wiggle[(int) (phase / 64)] * badge);
                dy = phase < 160 ? -Math.max(1, Math.round(badge)) : 0;
            }
            int bx = elX + 13 + dx;
            int by = elY - 3 + dy;
            context.fill(bx, by, bx + 5, by + 8, 0xFF5A0C0C);
            context.fill(bx + 1, by + 1, bx + 4, by + 7, 0xFFFF3B30);
            context.fill(bx + 2, by + 2, bx + 3, by + 4, 0xFFFFFFFF);
            context.fill(bx + 2, by + 5, bx + 3, by + 6, 0xFFFFFFFF);
        }
    }

    /** Four L-shaped corners on the box from {@code (a, b)} to {@code (c, d)}, inclusive. */
    private static void drawBrackets(GuiGraphicsExtractor context, int a, int b, int c, int d, int len, int color) {
        context.fill(a, b, a + len, b + 1, color);
        context.fill(a, b, a + 1, b + len, color);
        context.fill(c - len + 1, b, c + 1, b + 1, color);
        context.fill(c, b, c + 1, b + len, color);
        context.fill(a, d, a + len, d + 1, color);
        context.fill(a, d - len + 1, a + 1, d + 1, color);
        context.fill(c - len + 1, d, c + 1, d + 1, color);
        context.fill(c, d - len + 1, c + 1, d + 1, color);
    }

    /** A half-sine bump of width {@code w} starting at {@code start}, 0 outside it. */
    private static double bump(double phase, double start, double w) {
        return phase >= start && phase < start + w ? Math.sin(Math.PI * (phase - start) / w) : 0;
    }

    private static int alpha(int rgb, float a) {
        int alpha = Math.max(0, Math.min(255, Math.round(a * 255)));
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * Watches the player's own gear for lost durability. Here rather than in the render
     * path so the settings preview, which draws stand-in gear, can never move the baseline.
     */
    @Override
    public void tick(Minecraft client) {
        if (client.player == null) {
            java.util.Arrays.fill(lastItems, null);
            lastSelectedSlot = -1;
            return;
        }
        int selected = client.player.getInventory().getSelectedSlot();
        long now = System.currentTimeMillis();
        for (ArmorSlot slot : ArmorSlot.values()) {
            int i = slot.ordinal();
            ItemStack stack = liveStack(slot);
            Item item = stack.getItem();
            int damage = stack.isDamageableItem() ? stack.getDamageValue() : -1;
            // Scrolling to another sword of the same kind is a different item, not a hit.
            boolean same = item == lastItems[i] && (slot != ArmorSlot.MAIN_HAND || selected == lastSelectedSlot);
            if (same && lastDamages[i] >= 0 && damage > lastDamages[i]) {
                hitTimes[i] = now;
                chipFromWidths[i] = lastBarWidths[i];
            }
            lastItems[i] = item;
            lastDamages[i] = damage;
            lastBarWidths[i] = stack.isBarVisible() ? stack.getBarWidth() : 13;
        }
        lastSelectedSlot = selected;
    }

    private ItemStack liveStack(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
            case CHESTPLATE -> client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            case LEGGINGS -> client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
            case BOOTS -> client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
            case MAIN_HAND -> client.player.getMainHandItem();
            case OFF_HAND -> client.player.getOffhandItem();
        };
    }

    private int getDurabilityColor(int durability, int max) {
        float ratio = (float) durability / max;
        if (ratio > 0.5f) return 0xFF55FF55; // Green
        if (ratio > 0.2f) return 0xFFFFFF55; // Yellow
        return 0xFFFF5555; // Red
    }

    @Override
    public String getName() {
        return "ArmorHud";
    }

    @Override public void setPositionType(SimpleCPSConfig.Position pos) { SimpleCPSConfig.instance.armorPosition = pos; }
    @Override public void setXOffset(int x) { SimpleCPSConfig.instance.armorXOffset = x; }
    @Override public void setYOffset(int y) { SimpleCPSConfig.instance.armorYOffset = y; }
    
    @Override public void setScale(int scale) { SimpleCPSConfig.instance.armorScale = scale; }
    @Override public int getScale() { return SimpleCPSConfig.instance.armorScale; }

    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.armorPosition = SimpleCPSConfig.Position.BOTTOM_LEFT;
        SimpleCPSConfig.instance.armorXOffset = 0;
        SimpleCPSConfig.instance.armorYOffset = 0;
        SimpleCPSConfig.instance.armorScale = 100;
        for (ArmorElement el : subElements) {
            el.resetToDefaults();
        }
    }

    @Override
    public void resetVisualDefaults() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        config.armorVertical = true;
        config.armorShowBackgroundSlots = false;
        config.armorShowMainHand = true;
        config.armorShowOffHand = false;
        config.armorDurabilityText = false;
        config.armorDamageFlash = true;
        config.armorHitShake = 100;
        config.armorHitSparks = 0;
        config.armorHitCorners = 0;
        config.armorHitChip = 0;
        config.armorLowThreshold = 25;
        config.armorLowTremble = 0;
        config.armorLowBadge = 100;
        config.armorLowHeartbeat = 0;
        config.armorLowPulse = 0;
    }

    @Override
    public java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> getContextMenuSettings() {
        SimpleCPSConfig config = SimpleCPSConfig.instance;
        java.util.List<com.eymistaken.simplecps.api.HudModuleSetting> settings = new java.util.ArrayList<>(super.getContextMenuSettings());
        
        boolean anyDetached = false;
        for (ArmorElement el : subElements) {
            if (el.getXOffset() != 0 || el.getYOffset() != 0) {
                anyDetached = true;
                break;
            }
        }
        if (!anyDetached) {
            settings.add(0, new com.eymistaken.simplecps.api.ActionSetting("Ungroup", () -> {
                int parentX = ArmorModule.this.getX();
                int parentY = ArmorModule.this.getY();
                List<ArmorElement> ordered = getOrderedElements();
                boolean vertical = SimpleCPSConfig.instance.armorVertical;
                
                for (int i = 0; i < ordered.size(); i++) {
                    ArmorElement el = ordered.get(i);
                    // Offsets are screen pixels, so the stack has to be laid out scaled
                    // or ungrouping would visibly bunch the slots together.
                    int step = scaled(i * SLOT);
                    if (vertical) {
                        el.setXOffset(0);
                        el.setYOffset(step);
                    } else {
                        el.setXOffset(step);
                        el.setYOffset(0);
                    }
                }
                com.eymistaken.simplecps.SimpleCPSConfig.save();
            }));
        }

        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Armor", () -> config.showArmor, v -> config.showArmor = v),
            new com.eymistaken.simplecps.api.SliderSetting("Scale %", 50, 300, 100, () -> config.armorScale, v -> config.armorScale = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Vertical", () -> config.armorVertical, v -> config.armorVertical = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Show Background", () -> config.armorShowBackgroundSlots, v -> config.armorShowBackgroundSlots = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Durability Text", () -> config.armorDurabilityText, v -> config.armorDurabilityText = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Damage Effects", () -> config.armorDamageFlash, v -> config.armorDamageFlash = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Show Main Hand", () -> config.armorShowMainHand, v -> config.armorShowMainHand = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Show Off Hand", () -> config.armorShowOffHand, v -> config.armorShowOffHand = v)
        ));
        return settings;
    }
}
