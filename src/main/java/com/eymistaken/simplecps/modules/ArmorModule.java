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

    private static final net.minecraft.resources.Identifier SLOT_TEXTURE = net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "hud/hotbar");

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
        private int cachedX, cachedY;

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
            return 20;
        }

        @Override
        public int getHeight() {
            boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
            ItemStack stack = getItemStack();
            if (stack != null && !stack.isEmpty() && showText && stack.isDamageableItem()) {
                boolean vertical = SimpleCPSConfig.instance.armorVertical;
                if (!vertical) return 30;
            }
            return 20;
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
            if (client.player == null) return false;
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
                        int newParentX, newParentY;
                        if (vertical) {
                            newParentX = this.getX();
                            newParentY = this.getY() - index * 20;
                        } else {
                            newParentX = this.getX() - index * 20;
                            newParentY = this.getY();
                        }
                        
                        SimpleCPSConfig.Position anchor = getPositionType();
                        net.minecraft.client.gui.screens.Screen screen = client.screen;
                        int screenW = screen != null ? screen.width : 400;
                        int screenH = screen != null ? screen.height : 300;
                        
                        int baseX = 0;
                        int baseY = 0;
                        int gap = 5;
                        
                        // Calculate grouped dimensions directly to avoid 0-width/height issues
                        int activeCount = ordered.size();
                        int finalW = 0;
                        int finalH = 0;
                        if (activeCount > 0) {
                            if (vertical) {
                                finalW = 20;
                                boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
                                if (showText) {
                                    int maxText = 0;
                                    for (ArmorElement sub : ordered) {
                                        ItemStack stack = sub.getItemStack();
                                        if (stack.isDamageableItem()) {
                                            int durability = stack.getMaxDamage() - stack.getDamageValue();
                                            maxText = Math.max(maxText, client.font.width(String.valueOf(durability)));
                                        }
                                    }
                                    if (maxText > 0) {
                                        finalW += maxText + 2;
                                    }
                                }
                                finalH = activeCount * 20;
                            } else {
                                finalW = activeCount * 20;
                                finalH = 20;
                                boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
                                if (showText) {
                                    boolean anyDmg = ordered.stream().anyMatch(sub -> sub.getItemStack().isDamageableItem());
                                    if (anyDmg) {
                                        finalH += 10;
                                    }
                                }
                            }
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
        if (client.player == null) return ordered;
        boolean isRightHanded = client.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;

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
        List<ArmorElement> items = getOrderedElements();
        if (items.isEmpty()) return 20;
        
        boolean vertical = SimpleCPSConfig.instance.armorVertical;
        boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
        
        if (vertical) {
            int w = 20;
            if (showText) {
                int maxText = 0;
                for (ArmorElement sub : items) {
                    ItemStack stack = sub.getItemStack();
                    if (stack.isDamageableItem()) {
                        int durability = stack.getMaxDamage() - stack.getDamageValue();
                        maxText = Math.max(maxText, client.font.width(String.valueOf(durability)));
                    }
                }
                if (maxText > 0) {
                    w += maxText + 2;
                }
            }
            return w; 
        } else {
            return items.size() * 20;
        }
    }

    @Override
    public int getHeight() {
        List<ArmorElement> items = getOrderedElements();
        if (items.isEmpty()) return 20;
        
        boolean vertical = SimpleCPSConfig.instance.armorVertical;
        boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
        
        if (vertical) {
            return items.size() * 20;
        } else {
            int h = 20;
            if (showText) {
                boolean anyDmg = items.stream().anyMatch(sub -> sub.getItemStack().isDamageableItem());
                if (anyDmg) {
                    h += 10;
                }
            }
            return h;
        }
    }

    private void drawSlotBackground(GuiGraphicsExtractor context, int slotX, int slotY) {
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
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        if (client.player == null) return;
        
        List<ArmorElement> ordered = getOrderedElements();
        if (ordered.isEmpty()) return;
        
        boolean vertical = SimpleCPSConfig.instance.armorVertical;
        boolean showBg = SimpleCPSConfig.instance.armorShowBackgroundSlots;
        boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
        boolean damageFlash = SimpleCPSConfig.instance.armorDamageFlash;
        
        int stackX = this.x;
        int stackY = this.y;
        long now = System.currentTimeMillis();
        
        for (int i = 0; i < ordered.size(); i++) {
            ArmorElement el = ordered.get(i);
            ItemStack stack = el.getItemStack();
            
            // Determine position
            int elX, elY;
            if (el.getXOffset() != 0 || el.getYOffset() != 0) {
                elX = this.x + el.getXOffset();
                elY = this.y + el.getYOffset();
            } else {
                elX = stackX;
                elY = stackY;
                // Advance stack cursor
                if (vertical) {
                    stackY += 20;
                } else {
                    stackX += 20;
                }
            }
            
            // Cache computed position for IHudElement bounds in editor
            el.cachedX = elX;
            el.cachedY = elY;
            
            // Draw Background Slot
            if (showBg) {
                drawSlotBackground(context, elX, elY);
            }
            
            // Damage Flash Logic
            int damageIndex = el.slot.ordinal();
            if (damageFlash && stack.isDamageableItem()) {
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
            if (damageFlash && flashEndTimes[damageIndex] > now) {
                context.fill(elX + 1, elY + 1, elX + 17, elY + 17, 0x66FF0000); // Red tint
            }
            
            // Durability Text
            if (showText && stack.isDamageableItem()) {
                int durability = stack.getMaxDamage() - stack.getDamageValue();
                String text = String.valueOf(durability);
                int tw = client.font.width(text);
                int color = getDurabilityColor(durability, stack.getMaxDamage());
                
                if (vertical) {
                    context.text(client.font, text, elX + 20, elY + 5, color);
                } else {
                    context.text(client.font, text, elX + 9 - tw / 2, elY + 20, color);
                }
            }
        }
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
    
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.armorPosition = SimpleCPSConfig.Position.BOTTOM_LEFT;
        SimpleCPSConfig.instance.armorXOffset = 0;
        SimpleCPSConfig.instance.armorYOffset = 0;
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
        config.armorShowOffHand = true;
        config.armorDurabilityText = true;
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
                    if (vertical) {
                        el.setXOffset(0);
                        el.setYOffset(i * 20);
                    } else {
                        el.setXOffset(i * 20);
                        el.setYOffset(0);
                    }
                }
                com.eymistaken.simplecps.SimpleCPSConfig.save();
            }));
        }

        settings.addAll(java.util.List.of(
            new com.eymistaken.simplecps.api.BooleanSetting("Enable Armor", () -> config.showArmor, v -> config.showArmor = v),
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
