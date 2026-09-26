package com.leefy.mixin;

import com.leefy.HitCounterNameTagState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements HitCounterNameTagState {
  @Unique private Component hitcounter$balance;

  @Override
  public Component hitcounter$getBalance() {
    return hitcounter$balance;
  }

  @Override
  public void hitcounter$setBalance(Component balance) {
    hitcounter$balance = balance;
  }
}
