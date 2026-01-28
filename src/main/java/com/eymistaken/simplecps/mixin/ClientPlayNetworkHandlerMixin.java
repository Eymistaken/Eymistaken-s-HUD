package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.SimpleCPSClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow private ClientWorld world;

    @Inject(method = "onEntityDamage", at = @At("HEAD"))
    private void onEntityDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        if (this.client.player != null && this.world != null) {
            if (packet.entityId() == this.client.player.getId()) {
                DamageSource source = packet.createDamageSource(this.world);
                SimpleCPSClient.onDamageEvent(source);
            }
        }
    }
}
