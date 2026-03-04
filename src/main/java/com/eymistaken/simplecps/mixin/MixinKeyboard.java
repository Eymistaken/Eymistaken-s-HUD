package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.modules.KeystrokesModule;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKeyInject(long window, int action, KeyInput keyInput, CallbackInfo ci) {
        if (action == 1) { // GLFW_PRESS
            KeystrokesModule.pressedKeys.add(keyInput.getKeycode());
        } else if (action == 0) { // GLFW_RELEASE
            KeystrokesModule.pressedKeys.remove(keyInput.getKeycode());
        }
    }
}
