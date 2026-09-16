package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.ComboTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the combo tracker what the server reported, rather than what the player attempted.
 *
 * <p>Each handler starts by hopping to the client thread: on the network thread
 * {@code PacketUtils.ensureRunningOnSameThread} reschedules the packet and throws before any
 * return is reached, so injecting at RETURN only ever runs on the client thread.
 */
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {
    @Inject(method = "handleDamageEvent", at = @At("RETURN"))
    private void onDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        ComboTracker.onDamageEvent(packet);
    }

    @Inject(method = "handleEntityEvent", at = @At("RETURN"))
    private void onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        ComboTracker.onEntityEvent(packet);
    }

    @Inject(method = "handleAnimate", at = @At("RETURN"))
    private void onAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
        ComboTracker.onAnimate(packet);
    }

    @Inject(method = "handleSwingAnimation", at = @At("RETURN"))
    private void onSwingAnimation(ClientboundSwingAnimationPacket packet, CallbackInfo ci) {
        ComboTracker.onSwingAnimation(packet);
    }
}
