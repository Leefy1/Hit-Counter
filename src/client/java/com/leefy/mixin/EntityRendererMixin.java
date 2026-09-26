package com.leefy.mixin;

import com.leefy.HitBalanceTracker;
import com.leefy.HitCounterConfig;
import com.leefy.HitCounterNameTagState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
  private static final double LINE_OFFSET = 9.0D * 1.15D * 0.025D;

  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void hitcounter$appendBalance(
      Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
    HitCounterNameTagState nameTagState = (HitCounterNameTagState) state;
    nameTagState.hitcounter$setBalance(null);
    if (!(entity instanceof Player player) || state.nameTag == null) return;

    Component balance = HitBalanceTracker.getNameplateBalance(player.getUUID());
    if (balance == null) return;
    switch (HitCounterConfig.get().getBalancePosition()) {
      case LEFT -> state.nameTag = Component.empty().append(balance).append(HitBalanceTracker.separator()).append(state.nameTag);
      case RIGHT -> state.nameTag = state.nameTag.copy().append(HitBalanceTracker.separator()).append(balance);
      case ABOVE -> nameTagState.hitcounter$setBalance(balance);
      case BELOW -> {
        nameTagState.hitcounter$setBalance(balance);
        if (state.nameTagAttachment != null) {
          state.nameTagAttachment = state.nameTagAttachment.add(0.0D, LINE_OFFSET, 0.0D);
        }
      }
    }
  }

  @Inject(method = "submitNameTag", at = @At("HEAD"))
  private void hitcounter$submitVerticalBalance(
      EntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      CameraRenderState camera,
      CallbackInfo ci) {
    Component balance = ((HitCounterNameTagState) state).hitcounter$getBalance();
    if (balance == null || state.nameTagAttachment == null) return;

    HitCounterConfig.BalancePosition position = HitCounterConfig.get().getBalancePosition();
    double offset =
        position == HitCounterConfig.BalancePosition.ABOVE
            ? LINE_OFFSET * (state instanceof AvatarRenderState avatar && avatar.scoreText != null ? 2 : 1)
            : -LINE_OFFSET;
    submitBalance(state, poseStack, submitNodeCollector, camera, balance, offset);
  }

  private static void submitBalance(
      EntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      CameraRenderState camera,
      Component balance,
      double offset) {
    Vec3 attachment = state.nameTagAttachment.add(0.0D, offset, 0.0D);
    submitNodeCollector.submitNameTag(
        poseStack,
        attachment,
        0,
        balance,
        !state.isDiscrete,
        state.lightCoords,
        state.distanceToCameraSq,
        camera);
  }
}
