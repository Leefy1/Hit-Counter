package com.leefy;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

/** Client bootstrap for hit tracking and the reset key binding. */
public final class HitCounterClient implements ClientModInitializer {
  private static final KeyMapping.Category CATEGORY =
      KeyMapping.Category.register(HitCounter.id("general"));

  /** The key beside 1: backtick on US layouts and backslash on Italian layouts. */
  private static final KeyMapping RESET_KEY =
      KeyBindingHelper.registerKeyBinding(
          new KeyMapping(
              "key.hit-counter.reset",
              InputConstants.Type.KEYSYM,
              GLFW.GLFW_KEY_GRAVE_ACCENT,
              CATEGORY));
  private static boolean resetKeyWasDown;

  @Override
  public void onInitializeClient() {
    HitCounterConfig.load();
    UpdateChecker.check();
    ServerOverrideStore.load();
    HitCounterCommands.register();
    SessionSummaryRenderer.register();
    ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, client) -> {
          resetDeathTracking(isLocalSpectator(client));
          ServerHitSessionStore.onJoin();
          ServerOverrideStore.onJoin();
        });
    ClientPlayConnectionEvents.DISCONNECT.register(
        (handler, client) -> {
          HitCounterSessionSummary.capture();
          ServerHitSessionStore.onDisconnect();
          ServerOverrideStore.onDisconnect();
          resetDeathTracking(false);
        });

    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          HitBalanceTracker.endClientTick();
          onClientTick(client);
          HitCounterConfigScreen.tickOverrideEditorReturn(client);

          boolean resetPressed = RESET_KEY.consumeClick();
          while (RESET_KEY.consumeClick()) {}
          if (resetPressed && !resetKeyWasDown && HitCounterConfig.get().isEnabled()) {
            switch (HitBalanceTracker.resetOrUndo()) {
              case UNDONE -> HitCounterToasts.showResetUndone();
              case RESET -> HitCounterToasts.showResetAllWithUndo();
              case EMPTY -> {}
            }
          }
          resetKeyWasDown = RESET_KEY.isDown();
        });
  }

  /**
   * Called when a server instructs the client to reconnect to another server in the same network.
   */
  public static void onServerTransfer() {
    ServerHitSessionStore.markTransfer();
  }

  /**
   * A normal death can emit both a zero-health update and a respawn update. This marks the health
   * update as handled so its following respawn does not reset the counts a second time.
   */
  private static boolean localDeathResetHandled;

  private static boolean wasLocalSpectator;
  private static final Map<UUID, OpponentDeathState> OPPONENT_DEATH_STATES = new HashMap<>();
  private static final Map<UUID, Boolean> OPPONENT_TAB_SPECTATOR_STATES = new HashMap<>();

  private static boolean isLocalSpectator(net.minecraft.client.Minecraft client) {
    if (client.player == null) return false;
    PlayerInfo playerInfo =
        client.getConnection() == null
            ? null
            : client.getConnection().getPlayerInfo(client.player.getUUID());
    return playerInfo == null
        ? client.player.isSpectator()
        : playerInfo.getGameMode() == GameType.SPECTATOR;
  }

  private static void resetDeathTracking(boolean isSpectator) {
    localDeathResetHandled = false;
    wasLocalSpectator = isSpectator;
    OPPONENT_DEATH_STATES.clear();
    OPPONENT_TAB_SPECTATOR_STATES.clear();
  }

  /** Detects servers that use a spectator-mode transition instead of a normal respawn. */
  private static void onClientTick(net.minecraft.client.Minecraft client) {
    boolean isSpectator = isLocalSpectator(client);
    if (client.player == null
        || client.getConnection() == null
        || !HitCounterConfig.get().isEnabled()) {
      resetDeathTracking(isSpectator);
      return;
    }

    if (wasLocalSpectator && !isSpectator) {
      localDeathResetHandled = false;
    }

    if (isSpectator
        && !wasLocalSpectator
        && !localDeathResetHandled
        && HitCounterConfig.get().autoResetsOnSpectatorMode()
        && HitCounterConfig.get().resetsWhenYouDie()) {
      HitBalanceTracker.reset();
      localDeathResetHandled = true;
    }
    wasLocalSpectator = isSpectator;
    trackOpponentDeathTransitions(client);
    trackOpponentSpectatorTransitions(client);
  }

  /** Clears an opponent's balance when normal death or spectator fallback detects their death. */
  private static void trackOpponentDeathTransitions(net.minecraft.client.Minecraft client) {
    if (client.level == null || client.player == null || client.getConnection() == null) {
      OPPONENT_DEATH_STATES.clear();
      return;
    }

    Set<UUID> presentOpponents = new HashSet<>();
    for (Player player : client.level.players()) {
      if (player.getUUID().equals(client.player.getUUID())) {
        continue;
      }

      UUID opponentId = player.getUUID();
      presentOpponents.add(opponentId);
      boolean isOpponentAlive = player.isAlive() && player.getHealth() > 0.0F;
      OpponentDeathState state = OPPONENT_DEATH_STATES.get(opponentId);
      if (state == null) {
        OPPONENT_DEATH_STATES.put(opponentId, new OpponentDeathState(isOpponentAlive));
        continue;
      }

      if (state.alive && !isOpponentAlive) {
        resetOpponentForDeath(opponentId, state, HitCounterConfig.get().autoResetsOnDeath());
      }
      if (isOpponentAlive) {
        state.resetHandled = false;
      }
      state.alive = isOpponentAlive;
    }
    OPPONENT_DEATH_STATES.keySet().retainAll(presentOpponents);
  }

  /**
   * Uses tab-list game modes so spectator transitions are detected beyond entity render distance.
   */
  private static void trackOpponentSpectatorTransitions(net.minecraft.client.Minecraft client) {
    if (client.getConnection() == null) {
      OPPONENT_TAB_SPECTATOR_STATES.clear();
      return;
    }

    Set<UUID> trackedOpponents = HitBalanceTracker.getTrackedOpponentIds();
    for (UUID opponentId : trackedOpponents) {
      PlayerInfo playerInfo = client.getConnection().getPlayerInfo(opponentId);
      if (playerInfo == null) continue;
      boolean isSpectator = playerInfo.getGameMode() == GameType.SPECTATOR;
      Boolean wasSpectator = OPPONENT_TAB_SPECTATOR_STATES.put(opponentId, isSpectator);
      if (wasSpectator != null
          && !wasSpectator
          && isSpectator
          && HitCounterConfig.get().autoResetsOnSpectatorMode()
          && HitCounterConfig.get().resetsWhenOpponentDies()) {
        HitBalanceTracker.resetOpponent(opponentId);
      }
    }
    OPPONENT_TAB_SPECTATOR_STATES.keySet().retainAll(trackedOpponents);
  }

  private static void resetOpponentForDeath(
      UUID opponentId, OpponentDeathState state, boolean detectionEnabled) {
    if (detectionEnabled
        && !state.resetHandled
        && HitCounterConfig.get().resetsWhenOpponentDies()) {
      HitBalanceTracker.resetOpponent(opponentId);
      state.resetHandled = true;
    }
  }

  /** Receives the local player's authoritative health value from the server. */
  public static void onHealthUpdated(float health) {
    if (!HitCounterConfig.get().isEnabled()) {
      localDeathResetHandled = false;
      return;
    }

    if (health <= 0.0F
        && !localDeathResetHandled
        && HitCounterConfig.get().autoResetsOnDeath()
        && HitCounterConfig.get().resetsWhenYouDie()) {
      resetForLocalDeath();
      localDeathResetHandled = true;
    }
  }

  /** Receives the client's respawn update, including immediate server respawns. */
  public static void onClientRespawn() {
    if (!HitCounterConfig.get().isEnabled()) {
      localDeathResetHandled = false;
      return;
    }

    if (localDeathResetHandled) {
      localDeathResetHandled = false;
      return;
    }

    if (HitCounterConfig.get().autoResetsOnDeath() && HitCounterConfig.get().resetsWhenYouDie()) {
      resetForLocalDeath();
    }
  }

  private static void resetForLocalDeath() {
    HitBalanceTracker.reset();
  }

  private static final class OpponentDeathState {
    private boolean alive;
    private boolean resetHandled;

    private OpponentDeathState(boolean alive) {
      this.alive = alive;
    }
  }
}
