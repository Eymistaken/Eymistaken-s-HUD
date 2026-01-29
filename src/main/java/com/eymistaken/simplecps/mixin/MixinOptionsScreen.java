package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.gui.ConfigScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public class MixinOptionsScreen extends Screen {

    protected MixinOptionsScreen(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cloth-config")) return;
        
        int x = 5; 
        int y = 5; 
        
        this.addDrawableChild(ButtonWidget.builder(Text.of("Eymistaken's HUD"), button -> {
            this.client.setScreen(new ConfigScreen(this));
        }).dimensions(x, y, 100, 20).build());
    }
}
