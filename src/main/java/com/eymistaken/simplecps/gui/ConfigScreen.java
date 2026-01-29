package com.eymistaken.simplecps.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;

    public ConfigScreen(Screen parent) {
        super(Text.of("Eymistaken's HUD Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = 40;
        int btnWidth = 200;
        int btnHeight = 20;
        int spacing = 24;

        addDrawableChild(ButtonWidget.builder(Text.of("CPS Settings"), button -> {
            this.client.setScreen(new ModuleSettingsScreen(this, "CPS"));
        }).dimensions(centerX - 100, startY, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Ping Settings"), button -> {
            this.client.setScreen(new ModuleSettingsScreen(this, "PING"));
        }).dimensions(centerX - 100, startY + spacing, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.of("FPS Settings"), button -> {
            this.client.setScreen(new ModuleSettingsScreen(this, "FPS"));
        }).dimensions(centerX - 100, startY + spacing * 2, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Keystrokes Settings"), button -> {
            this.client.setScreen(new ModuleSettingsScreen(this, "KEYSTROKES"));
        }).dimensions(centerX - 100, startY + spacing * 3, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Combo Settings"), button -> {
            this.client.setScreen(new ModuleSettingsScreen(this, "COMBO"));
        }).dimensions(centerX - 100, startY + spacing * 4, btnWidth, btnHeight).build());

        addDrawableChild(ButtonWidget.builder(Text.of("Done"), button -> {
            this.close();
        }).dimensions(centerX - 100, this.height - 30, btnWidth, btnHeight).build());
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fix: Use manual background fill instead of renderBackground to avoid "Can only blur once per frame" crash
        context.fill(0, 0, this.width, this.height, 0x85000000); // 85 hex alpha = approx 50% opacity black
        
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
