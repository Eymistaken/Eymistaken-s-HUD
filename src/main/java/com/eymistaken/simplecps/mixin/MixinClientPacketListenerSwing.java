package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.ComboTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the combo tracker other entities' arm swings, from the packet 26.3 sends them in.
 *
 * <p>Kept apart from {@link MixinClientPacketListener} because this packet and its handler
 * exist only from 26.3 on: before that a swing arrived as an action of
 * {@code ClientboundAnimatePacket}. A build for an older version leaves this mixin out and
 * reads the swing from there instead.
 *
 * <p>Like the other handlers it hops to the client thread first, so RETURN only ever runs there.
 */
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListenerSwing {
    @Inject(method = "handleSwingAnimation", at = @At("RETURN"))
    private void onSwingAnimation(ClientboundSwingAnimationPacket packet, CallbackInfo ci) {
        ComboTracker.onSwingAnimation(packet.entityId());
    }
}
