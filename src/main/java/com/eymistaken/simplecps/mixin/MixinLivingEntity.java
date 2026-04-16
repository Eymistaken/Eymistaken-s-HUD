package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.ComboTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void onDamageTaken(ServerLevel world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            if ((Object) this instanceof LocalPlayer) {
                LocalPlayer player = (LocalPlayer) (Object) this;
                if (player == net.minecraft.client.Minecraft.getInstance().player) {
                    ComboTracker.onDamage(source.getEntity());
                }
            }
        }
    }
}
