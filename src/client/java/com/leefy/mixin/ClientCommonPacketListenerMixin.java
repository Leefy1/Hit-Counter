package com.leefy.mixin;

import com.leefy.HitCounterClient;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks vanilla server transfers so the hit session survives the reconnect. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerMixin {
  @Inject(method = "handleTransfer", at = @At("HEAD"))
  private void hitcounter$markServerTransfer(ClientboundTransferPacket packet, CallbackInfo ci) {
    HitCounterClient.onServerTransfer();
  }
}
