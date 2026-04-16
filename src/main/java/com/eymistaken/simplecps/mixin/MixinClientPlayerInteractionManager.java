package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.ComboTracker;
import com.eymistaken.simplecps.ReachTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MixinClientPlayerInteractionManager {
    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttackEntity(Player player, Entity target, CallbackInfo ci) {
        ComboTracker.registerHit(target, player);
        ReachTracker.onAttack(target, player);
    }
}
