package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.ComboTracker;
import com.eymistaken.simplecps.ReachTracker;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        // Trigger Combo Logic
        ComboTracker.registerHit(target, player);
        // Trigger Reach Logic
        ReachTracker.onAttack(target, player);
    }
}
