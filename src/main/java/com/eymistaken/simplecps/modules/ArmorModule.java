package com.eymistaken.simplecps.modules;

import com.eymistaken.simplecps.SimpleCPSConfig;
import com.eymistaken.simplecps.api.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArmorModule extends HudModule {

    private final int[] lastDamages = new int[6];
    private final long[] flashEndTimes = new long[6];

    private static final net.minecraft.util.Identifier SLOT_TEXTURE = net.minecraft.util.Identifier.of("simplecps", "textures/gui/hotbar_slot.png");

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
        
        ItemStack offHand = client.player.getOffHandStack();
        ItemStack mainHand = client.player.getMainHandStack();
        
        boolean isRightHanded = client.player.getMainArm() == net.minecraft.util.Arm.RIGHT;

        // If Right Handed: Offhand is on the Left, Main Hand is on the Right.
        // If Left Handed: Mainhand is on the Left, Offhand is on the Right.
        if (isRightHanded) {
            if (showOff) items.add(offHand);
            items.add(client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
            items.add(client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
            items.add(client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
            items.add(client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
            if (showMain) items.add(mainHand);
        } else {
            if (showMain) items.add(mainHand);
            items.add(client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD));
            items.add(client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST));
            items.add(client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS));
            items.add(client.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET));
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
                    if (stack.isDamageable()) {
                        int durability = stack.getMaxDamage() - stack.getDamage();
                        maxText = Math.max(maxText, client.textRenderer.getWidth(String.valueOf(durability)));
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
                boolean anyDmg = items.stream().anyMatch(s -> s.isDamageable());
                if (anyDmg) {
                    h += 10;
                }
            }
            return h;
        }
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
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
            if (damageFlash && stack.isDamageable()) {
                int currentDamage = stack.getDamage();
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
                context.drawTexturedQuad(SLOT_TEXTURE, currentX - 2, currentY - 2, currentX + 20, currentY + 20, 0f, 1f, 0f, 1f);
            }
            
            // Draw Item
            context.drawItem(stack, currentX + 1, currentY + 1);
            context.drawStackOverlay(client.textRenderer, stack, currentX + 1, currentY + 1); 
            
            // Damage Flash Overlay
            if (damageFlash && flashEndTimes[i] > now) {
                context.fill(currentX + 1, currentY + 1, currentX + 17, currentY + 17, 0x66FF0000); // Red tint
            }
            
            // Durability Text
            if (showText && stack.isDamageable()) {
                int durability = stack.getMaxDamage() - stack.getDamage();
                String text = String.valueOf(durability);
                int tw = client.textRenderer.getWidth(text);
                int color = getDurabilityColor(durability, stack.getMaxDamage());
                
                if (vertical) {
                    context.drawTextWithShadow(client.textRenderer, text, currentX + 20, currentY + 5, color);
                } else {
                    context.drawTextWithShadow(client.textRenderer, text, currentX + 9 - tw / 2, currentY + 20, color);
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
