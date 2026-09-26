package com.leefy.mixin;

import com.leefy.HitBalanceTracker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends balances to server nametags implemented as text-display passengers mounted directly on a
 * player.
 */
@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public class TextDisplayRendererMixin {
  @Inject(
      method =
          "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",
      at = @At("RETURN"))
  private void hitcounter$appendBalanceToPassengerDisplay(
      Display.TextDisplay entity,
      TextDisplayEntityRenderState renderState,
      float tickProgress,
      CallbackInfo ci) {
    if (renderState.cachedInfo == null || !(entity.getVehicle() instanceof Player player)) {
      return;
    }

    Component suffix = HitBalanceTracker.getNameplateSuffix(player.getUUID());
    if (suffix == null) {
      return;
    }

    List<Display.TextDisplay.CachedLine> lines = renderState.cachedInfo.lines();
    for (int index = 0; index < lines.size(); index++) {
      Display.TextDisplay.CachedLine line = lines.get(index);
      MutableComponent lineText = toComponent(line.contents());
      if (!lineText.getString().contains(player.getScoreboardName())) {
        continue;
      }

      Component modified = lineText.append(suffix);
      FormattedCharSequence modifiedSequence = modified.getVisualOrderText();
      int modifiedWidth = Minecraft.getInstance().font.width(modified);

      List<Display.TextDisplay.CachedLine> modifiedLines = new ArrayList<>(lines);
      modifiedLines.set(index, new Display.TextDisplay.CachedLine(modifiedSequence, modifiedWidth));
      int maximumWidth =
          modifiedLines.stream()
              .mapToInt(Display.TextDisplay.CachedLine::width)
              .max()
              .orElse(renderState.cachedInfo.width());
      renderState.cachedInfo = new Display.TextDisplay.CachedInfo(modifiedLines, maximumWidth);
      return;
    }
  }

  private static MutableComponent toComponent(FormattedCharSequence sequence) {
    MutableComponent result = Component.empty();
    sequence.accept(
        (characterIndex, style, codePoint) -> {
          result.append(
              Component.literal(new String(Character.toChars(codePoint))).setStyle(style));
          return true;
        });
    return result;
  }
}
