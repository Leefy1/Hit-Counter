package com.leefy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Displays the mod's short-lived feedback messages in Minecraft's toast area. */
public final class HitCounterToasts {
  private HitCounterToasts() {}

  public static void showResetAllWithUndo() {
    show(
        Component.translatable("toast.hit-counter.reset.all"),
        Component.translatable("toast.hit-counter.reset.undo_prompt"));
  }

  public static void showResetUndone() {
    show(
        Component.translatable("toast.hit-counter.reset.title"),
        Component.translatable("toast.hit-counter.reset.restored"));
  }

  public static void showResetPlayer(String playerName) {
    show(
        Component.translatable("toast.hit-counter.reset.title"),
        Component.translatable("toast.hit-counter.reset.player", playerName));
  }

  public static void showInactiveReset(String playerName) {
    show(
        Component.translatable("toast.hit-counter.inactive_reset.title"),
        Component.translatable("toast.hit-counter.inactive_reset.description", playerName));
  }

  public static void showUpdate(String version, int newerVersions) {
    show(
        Component.literal("Update available"),
        Component.literal(
            "v"
                + version
                + " is available ("
                + newerVersions
                + " newer compatible release"
                + (newerVersions == 1 ? "" : "s")
                + ")."));
  }

  private static void show(Component title, Component description) {
    if (!HitCounterConfig.get().isEnabled()) {
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    minecraft
        .gui
        .toastManager()
        .addToast(
            new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description));
  }
}
