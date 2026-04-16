package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.modules.CpsModule;
import com.eymistaken.simplecps.modules.KeystrokesModule;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouse {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void onMouseButtonInject(long window, MouseButtonInfo mouseInput, int action, CallbackInfo ci) {
        int buttonId = mouseInput.button();
        int keyId = buttonId + 1000;

        if (action == 1) {
            KeystrokesModule.pressedKeys.add(keyId);
            if (buttonId == 0) {
                CpsModule.addLeftClick();
            } else if (buttonId == 1) {
                CpsModule.addRightClick();
            }
        } else if (action == 0) {
            KeystrokesModule.pressedKeys.remove(keyId);
        }
    }
}
