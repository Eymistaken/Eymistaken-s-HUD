package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.modules.CpsModule;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.input.MouseInput;

@Mixin(Mouse.class)
public class MixinMouse {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onMouseButtonInject(long window, MouseInput mouseInput, int action, CallbackInfo ci) {
        int buttonId = mouseInput.button();
        int keyId = buttonId + 1000;
        
        if (action == 1) { // GLFW_PRESS
            com.eymistaken.simplecps.modules.KeystrokesModule.pressedKeys.add(keyId);
            if (buttonId == 0) {
                CpsModule.addLeftClick();
            } else if (buttonId == 1) {
                CpsModule.addRightClick();
            }
        } else if (action == 0) { // GLFW_RELEASE
            com.eymistaken.simplecps.modules.KeystrokesModule.pressedKeys.remove(keyId);
        }
    }
}
