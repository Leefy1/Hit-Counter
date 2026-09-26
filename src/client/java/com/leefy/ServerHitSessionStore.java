package com.leefy;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** Keeps separate hit sessions in memory for the lifetime of the Minecraft process. */
public final class ServerHitSessionStore {
  private static final Map<String, HitBalanceTracker.ServerState> PROFILES = new HashMap<>();
  private static String activeProfile;
  private static boolean transferJoinPending;

  private ServerHitSessionStore() {}

  public static void onJoin() {
    if (transferJoinPending && activeProfile != null) {
      transferJoinPending = false;
      return;
    }
    transferJoinPending = false;
    storeActiveProfile();
    activeProfile = currentAddress();
    HitBalanceTracker.restoreServerState(
        activeProfile == null ? null : PROFILES.get(activeProfile));
  }

  public static void onDisconnect() {
    storeActiveProfile();
    if (!transferJoinPending) activeProfile = null;
  }

  /** Called by the vanilla transfer packet before its temporary disconnect. */
  public static void markTransfer() {
    transferJoinPending = activeProfile != null;
  }

  /** A transfer error opens a disconnect screen instead of joining its destination. */
  public static void cancelPendingTransfer() {
    transferJoinPending = false;
    activeProfile = null;
  }

  private static void storeActiveProfile() {
    if (activeProfile == null) return;
    if (HitBalanceTracker.hasServerState()) {
      PROFILES.put(activeProfile, HitBalanceTracker.captureServerState());
    } else {
      PROFILES.remove(activeProfile);
    }
  }

  private static String currentAddress() {
    var server = Minecraft.getInstance().getCurrentServer();
    return server == null ? null : server.ip.trim().toLowerCase(Locale.ROOT);
  }
}
