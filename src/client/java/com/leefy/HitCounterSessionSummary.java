package com.leefy;

import java.util.List;
import net.minecraft.network.chat.Component;

/** Formats current and captured multiplayer session summaries for menu display. */
public final class HitCounterSessionSummary {
  private static List<Component> lines;

  private HitCounterSessionSummary() {}

  public static void capture() {
    HitBalanceTracker.SessionSummary summary = HitBalanceTracker.getSessionSummary();
    if (summary == null) {
      lines = null;
      return;
    }
    lines = format(summary);
  }

  public static List<Component> lines() {
    return lines;
  }

  public static List<Component> currentLines() {
    HitBalanceTracker.SessionSummary summary = HitBalanceTracker.getSessionSummary();
    return summary == null ? null : format(summary);
  }

  private static List<Component> format(HitBalanceTracker.SessionSummary summary) {
    return List.of(
        Component.translatable("disconnect.hit-counter.summary.title"),
        Component.translatable(
            "disconnect.hit-counter.summary.totals",
            summary.playersTracked(),
            formatPoints(summary.dealtHalfPoints()),
            formatPoints(summary.receivedHalfPoints())),
        Component.translatable(
            "disconnect.hit-counter.summary.extremes",
            formatBalance(summary.highestBalance()),
            formatBalance(summary.lowestBalance())));
  }

  private static String formatPoints(int halfPoints) {
    return halfPoints % 10 == 0
        ? Integer.toString(halfPoints / 10)
        : String.format(java.util.Locale.ROOT, "%.1f", halfPoints / 10.0D);
  }

  private static Component formatBalance(HitBalanceTracker.RegisteredBalance balance) {
    if (balance == null) return Component.literal("-");
    return Component.literal(balance.playerName() + " (")
        .append(HitBalanceTracker.formatBalance(balance.halfPoints()))
        .append(Component.literal(")"));
  }
}
