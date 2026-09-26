package com.leefy.mixin;

import com.leefy.HitBalanceTracker;
import com.leefy.HitCounterClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
  @Inject(method = "handleDamageEvent", at = @At("TAIL"))
  private void hitcounter$trackDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
    ClientPacketListener listener = (ClientPacketListener) (Object) this;
    HitBalanceTracker.onDamageEvent(packet.entityId(), packet.getSource(listener.getLevel()));
  }

  @Inject(method = "handleSetHealth", at = @At("TAIL"))
  private void hitcounter$trackDeathHealth(ClientboundSetHealthPacket packet, CallbackInfo ci) {
    HitCounterClient.onHealthUpdated(packet.getHealth());
  }

  @Inject(method = "handleRespawn", at = @At("TAIL"))
  private void hitcounter$trackRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
    HitCounterClient.onClientRespawn();
  }

  @Inject(method = "handleAnimate", at = @At("HEAD"))
  private void hitcounter$trackCritical(ClientboundAnimatePacket packet, CallbackInfo ci) {
    if (packet.getAction() == ClientboundAnimatePacket.CRITICAL_HIT) {
      HitBalanceTracker.onCriticalAnimation(packet.getId());
    }
  }

  @Inject(method = "handleSoundEntityEvent", at = @At("HEAD"))
  private void hitcounter$trackWeakAttack(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
    if (packet.getSound().value() == SoundEvents.PLAYER_ATTACK_WEAK) {
      HitBalanceTracker.onWeakAttackSound(packet.getId());
    }
  }
}
