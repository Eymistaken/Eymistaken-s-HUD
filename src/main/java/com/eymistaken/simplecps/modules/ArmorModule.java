package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

public class ArmorModule extends HudModule {

    private final int[] lastDamages = new int[6];
    private final long[] flashEndTimes = new long[6];

    private static final net.minecraft.resources.Identifier SLOT_TEXTURE = net.minecraft.resources.Identifier.fromNamespaceAndPath("simplecps", "textures/gui/hotbar_slot.png");

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

    private List<ItemStack> getItems() {
        if (client.player == null) return new ArrayList<>();
        List<ItemStack> items = new ArrayList<>();
        
        boolean showMain = SimpleCPSConfig.instance.armorShowMainHand;
        boolean showOff = SimpleCPSConfig.instance.armorShowOffHand;
        
        ItemStack offHand = client.player.getOffhandItem();
        ItemStack mainHand = client.player.getMainHandItem();
        
        boolean isRightHanded = client.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;

        // If Right Handed: Offhand is on the Left, Main Hand is on the Right.
        // If Left Handed: Mainhand is on the Left, Offhand is on the Right.
        if (isRightHanded) {
            if (showOff) items.add(offHand);
            items.add(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
            items.add(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
            items.add(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
            items.add(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            if (showMain) items.add(mainHand);
        } else {
            if (showMain) items.add(mainHand);
            items.add(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
            items.add(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
            items.add(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
            items.add(client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
            if (showOff) items.add(offHand);
        }
        
        return items;
    }

    private List<ItemStack> getDisplayItems() {
        List<ItemStack> all = getItems();
        List<ItemStack> display = new ArrayList<>();
        for (ItemStack stack : all) {
            if (stack != null && !stack.isEmpty()) {
                display.add(stack);
            }
        }
        return display;
    }

    @Override
    public int getWidth() {
        List<ItemStack> items = getDisplayItems();
        if (items.isEmpty()) return 0;
        
        boolean vertical = SimpleCPSConfig.instance.armorVertical;
        boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
        
        if (vertical) {
            int w = 20;
            if (showText) {
                int maxText = 0;
                for (ItemStack stack : items) {
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
        List<ItemStack> items = getDisplayItems();
        if (items.isEmpty()) return 0;
        
        boolean vertical = SimpleCPSConfig.instance.armorVertical;
        boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
        
        if (vertical) {
            return items.size() * 20;
        } else {
            int h = 20;
            if (showText) {
                boolean anyDmg = items.stream().anyMatch(s -> s.isDamageableItem());
                if (anyDmg) {
                    h += 10;
                }
            }
            return h;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, float tickDelta) {
        if (client.player == null) return;
        
        List<ItemStack> displayItems = getDisplayItems();
        if (displayItems.isEmpty()) return; // Don't render anything if completely empty
        
        boolean vertical = SimpleCPSConfig.instance.armorVertical;
        boolean showBg = SimpleCPSConfig.instance.armorShowBackgroundSlots;
        boolean showText = SimpleCPSConfig.instance.armorDurabilityText;
        boolean damageFlash = SimpleCPSConfig.instance.armorDamageFlash;
        
        int currentX = this.x;
        int currentY = this.y;
        long now = System.currentTimeMillis();
        
        for (int i = 0; i < displayItems.size(); i++) {
            ItemStack stack = displayItems.get(i);
            
            // Damage Flash Logic
            if (damageFlash && stack.isDamageableItem()) {
                int currentDamage = stack.getDamageValue();
                if (currentDamage > lastDamages[i]) {
                    if (lastDamages[i] != 0) {
                        flashEndTimes[i] = now + 400; // 400ms flash
                    }
                }
                lastDamages[i] = currentDamage;
            } else {
                lastDamages[i] = 0;
            }

            // Draw Background Slot
            if (showBg) {
                // The correct parameter sequence for drawTexturedQuad is: x1, y1, x2, y2
                context.blit(SLOT_TEXTURE, currentX - 2, currentY - 2, currentX + 20, currentY + 20, 0f, 1f, 0f, 1f);
            }
            
            // Draw Item
            context.item(stack, currentX + 1, currentY + 1);
            context.itemDecorations(client.font, stack, currentX + 1, currentY + 1); 
            
            // Damage Flash Overlay
            if (damageFlash && flashEndTimes[i] > now) {
                context.fill(currentX + 1, currentY + 1, currentX + 17, currentY + 17, 0x66FF0000); // Red tint
            }
            
            // Durability Text
            if (showText && stack.isDamageableItem()) {
                int durability = stack.getMaxDamage() - stack.getDamageValue();
                String text = String.valueOf(durability);
                int tw = client.font.width(text);
                int color = getDurabilityColor(durability, stack.getMaxDamage());
                
                if (vertical) {
                    context.text(client.font, text, currentX + 20, currentY + 5, color);
                } else {
                    context.text(client.font, text, currentX + 9 - tw / 2, currentY + 20, color);
                }
            }
            
            if (vertical) {
                currentY += 20;
            } else {
                currentX += 20;
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
    // Armor has no scale config
    @Override public void resetToDefaults() {
        SimpleCPSConfig.instance.armorPosition = SimpleCPSConfig.Position.BOTTOM_LEFT;
        SimpleCPSConfig.instance.armorXOffset = 0;
        SimpleCPSConfig.instance.armorYOffset = 0;
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
