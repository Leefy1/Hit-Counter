package com.leefy.mixin;

import com.leefy.HitBalanceTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the local player's exact cooldown before an attack resets it. */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
  @Inject(method = "attack", at = @At("HEAD"))
  private void hitcounter$trackLocalWeakAttack(Player player, Entity target, CallbackInfo ci) {
    if (player.getAttackStrengthScale(0.5F) < 0.9F) {
      HitBalanceTracker.onWeakAttackSound(player.getId());
    }
  }
}
