package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import com.eymistaken.simplecps.api.IHudElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public class ArmorModule extends HudModule {

    private final int[] lastDamages = new int[6];
    private final long[] flashEndTimes = new long[6];

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
            switch (slot) {
                case HELMET: return client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
                case CHESTPLATE: return client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
                case LEGGINGS: return client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
                case BOOTS: return client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
                case MAIN_HAND: return client.player.getMainHandItem();
                case OFF_HAND: return client.player.getOffhandItem();
            }
            return ItemStack.EMPTY;
        }

        public boolean isEnabled() {
            if (client.player == null && !ArmorModule.this.isPreviewing()) return false;
            if (slot == ArmorSlot.MAIN_HAND && !SimpleCPSConfig.instance.armorShowMainHand) return false;
            if (slot == ArmorSlot.OFF_HAND && !SimpleCPSConfig.instance.armorShowOffHand) return false;
            ItemStack stack = getItemStack();
            return stack != null && !stack.isEmpty();
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

        if (isRightHanded) {
            if (offHand.isEnabled()) ordered.add(offHand);
            if (helmet.isEnabled()) ordered.add(helmet);
            if (chest.isEnabled()) ordered.add(chest);
            if (legs.isEnabled()) ordered.add(legs);
            if (boots.isEnabled()) ordered.add(boots);
            if (mainHand.isEnabled()) ordered.add(mainHand);
        } else {
            if (mainHand.isEnabled()) ordered.add(mainHand);
            if (helmet.isEnabled()) ordered.add(helmet);
            if (chest.isEnabled()) ordered.add(chest);
            if (legs.isEnabled()) ordered.add(legs);
            if (boots.isEnabled()) ordered.add(boots);
            if (offHand.isEnabled()) ordered.add(offHand);
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
        boolean damageFlash = SimpleCPSConfig.instance.armorDamageFlash;
        
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

        // Pass 3: Draw Items and Durability text
        for (int i = 0; i < ordered.size(); i++) {
            ArmorElement el = ordered.get(i);
            ItemStack stack = el.getItemStack();
            int elX = el.localX;
            int elY = el.localY;

            // Damage Flash Logic. Skipped while previewing: the preview's stacks are not
            // the player's, so recording their wear here would overwrite the real
            // baseline and swallow the next genuine hit's flash.
            int damageIndex = el.slot.ordinal();
            if (isPreviewing()) {
                // nothing to track
            } else if (damageFlash && stack.isDamageableItem()) {
                int currentDamage = stack.getDamageValue();
                if (currentDamage > lastDamages[damageIndex]) {
                    if (lastDamages[damageIndex] != 0) {
                        flashEndTimes[damageIndex] = now + 400; // 400ms flash
                    }
                }
                lastDamages[damageIndex] = currentDamage;
            } else {
                lastDamages[damageIndex] = 0;
            }

            // Draw Item
            context.item(stack, elX + 1, elY + 1);
            context.itemDecorations(client.font, stack, elX + 1, elY + 1);

            // Damage Flash Overlay
            if (damageFlash && !isPreviewing() && flashEndTimes[damageIndex] > now) {
                context.fill(elX + 1, elY + 1, elX + 17, elY + 17, 0x66FF0000); // Red tint
            }
            
            // Durability Text
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

        context.pose().popMatrix();
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
            new com.eymistaken.simplecps.api.BooleanSetting("Damage Flash", () -> config.armorDamageFlash, v -> config.armorDamageFlash = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Show Main Hand", () -> config.armorShowMainHand, v -> config.armorShowMainHand = v),
            new com.eymistaken.simplecps.api.BooleanSetting("Show Off Hand", () -> config.armorShowOffHand, v -> config.armorShowOffHand = v)
        ));
        return settings;
    }
}
