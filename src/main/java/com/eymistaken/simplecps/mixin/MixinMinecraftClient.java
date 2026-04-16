package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.SimpleCPSClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftClient {
    @Inject(method = "tick", at = @At("HEAD"))
    private void tickSimpleCPS(CallbackInfo ci) {
        SimpleCPSClient.onClientTick((Minecraft) (Object) this);
    }
}
