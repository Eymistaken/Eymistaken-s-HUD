package com.eymistaken.simplecps.mixin;

import com.eymistaken.simplecps.ComboTracker;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @Inject(method = "damage", at = @At("RETURN"))
    private void onDamageTaken(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            if ((Object) this instanceof ClientPlayerEntity) {
                ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
                if (player == net.minecraft.client.MinecraftClient.getInstance().player) {
                    ComboTracker.onDamage(source.getAttacker());
                }
            }
        }
    }
}
