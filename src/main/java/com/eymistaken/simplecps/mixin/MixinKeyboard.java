package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.modules.KeystrokesModule;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboard {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKeyInject(long window, int action, KeyEvent keyInput, CallbackInfo ci) {
        if (action == 1) {
            KeystrokesModule.pressedKeys.add(keyInput.input());
        } else if (action == 0) {
            KeystrokesModule.pressedKeys.remove(keyInput.input());
        }
    }
}
