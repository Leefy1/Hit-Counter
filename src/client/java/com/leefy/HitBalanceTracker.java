package com.leefy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/** Stores the local player's PvP balance against each opponent. */
public final class HitBalanceTracker {
  private static final long UNDO_WINDOW_NANOS = 5_000_000_000L;
  private static final int SEPARATOR_COLOR = 0xAAAAAA;
  private static final Map<UUID, Integer> BALANCES = new HashMap<>();
  private static final Map<UUID, String> PLAYER_NAMES = new HashMap<>();
  private static final Map<UUID, Long> LAST_INTERACTIONS = new HashMap<>();
  private static final java.util.Set<UUID> SESSION_OPPONENTS = new java.util.HashSet<>();
  private static final Map<Integer, Long> CRITICAL_TARGETS = new HashMap<>();
  private static final Map<Integer, Long> WEAK_ATTACKERS = new HashMap<>();
  private static final Map<String, String> DIRECT_ENTITY_HIT_SOURCES =
      Map.of(
          "wind_charge", "wind_charge",
          "arrow", "arrow",
          "trident", "trident",
          "fireball", "fireball",
          "firework_rocket", "fireworks",
          "potion", "instant_damage_potion",
          "area_effect_cloud", "instant_damage_potion");
  private static final List<PendingHit> PENDING_HITS = new ArrayList<>();
  private static long clientTick;
  private static ResetSnapshot undoSnapshot;
  private static final Map<UUID, InactivityResetSnapshot> INACTIVITY_UNDOS = new HashMap<>();
  private static int sessionDealtHalfPoints;
  private static int sessionReceivedHalfPoints;
  private static RegisteredBalance sessionHighestBalance;
  private static RegisteredBalance sessionLowestBalance;

  private HitBalanceTracker() {}

  /**
   * Receives vanilla damage events, which carry attacker attribution but not damage magnitude.
   * Waiting two ticks lets the matching critical-animation packet arrive.
   */
  public static void onDamageEvent(int victimId, DamageSource source) {
    if (FlashbackReplaySupport.isReplayPlayback()) {
      return;
    }
    if (!HitCounterConfig.get().isEnabled()) {
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.level == null || minecraft.player == null) {
      return;
    }

    Entity victim = minecraft.level.getEntity(victimId);
    Entity attacker = source.getEntity();
    if (!(victim instanceof Player victimPlayer) || !(attacker instanceof Player attackerPlayer)) {
      return;
    }

    Player localPlayer = minecraft.player;
    if (!isAllowedHitSource(source)) {
      return;
    }

    if (attackerPlayer.getUUID().equals(localPlayer.getUUID())) {
      if (victimPlayer.getUUID().equals(localPlayer.getUUID())) {
        return;
      }
      if (!isEligibleCombatPlayer(victimPlayer)) {
        return;
      }
      PENDING_HITS.add(
          new PendingHit(
              victimId,
              attackerPlayer.getId(),
              victimPlayer.getUUID(),
              victimPlayer.getName().getString(),
              true,
              isWeakMeleeSource(source),
              clientTick));
    } else if (victimPlayer.getUUID().equals(localPlayer.getUUID())
        && isEligibleCombatPlayer(attackerPlayer)) {
      PENDING_HITS.add(
          new PendingHit(
              victimId,
              attackerPlayer.getId(),
              attackerPlayer.getUUID(),
              attackerPlayer.getName().getString(),
              false,
              isWeakMeleeSource(source),
              clientTick));
    }
  }

  /** Marks the target of a vanilla melee critical animation packet. */
  public static void onCriticalAnimation(int targetEntityId) {
    if (FlashbackReplaySupport.isReplayPlayback()) {
      return;
    }
    if (!HitCounterConfig.get().isEnabled()) {
      return;
    }

    CRITICAL_TARGETS.put(targetEntityId, clientTick);
  }

  /** Marks a player whose next confirmed vanilla melee hit may use the weak-hit value. */
  public static void onWeakAttackSound(int attackerEntityId) {
    if (FlashbackReplaySupport.isReplayPlayback()) {
      return;
    }
    if (HitCounterConfig.get().isEnabled()) {
      WEAK_ATTACKERS.put(attackerEntityId, clientTick);
    }
  }

  public static void endClientTick() {
    FlashbackReplaySupport.tick();
    if (FlashbackReplaySupport.isReplayPlayback()) {
      return;
    }
    if (!HitCounterConfig.get().isEnabled()) {
      PENDING_HITS.clear();
      CRITICAL_TARGETS.clear();
      WEAK_ATTACKERS.clear();
      return;
    }

    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.level == null || minecraft.player == null) {
      PENDING_HITS.clear();
      CRITICAL_TARGETS.clear();
      WEAK_ATTACKERS.clear();
      return;
    }

    clientTick++;
    flushReadyHits();
    resetInactiveBalances();
    CRITICAL_TARGETS.entrySet().removeIf(entry -> clientTick - entry.getValue() > 4);
    WEAK_ATTACKERS.entrySet().removeIf(entry -> clientTick - entry.getValue() > 4);
  }

  /** Clears all active balances. */
  public static void reset() {
    clearUndo();
    resetActiveBalances();
  }

  /** Resets or restores the all-player state for the reset key and global command. */
  public static ManualResetResult resetOrUndo() {
    if (restoreLatestInactivityUndo()) {
      return ManualResetResult.UNDONE;
    }
    if (undoSnapshot != null
        && System.nanoTime() - undoSnapshot.startedAtNanos() <= UNDO_WINDOW_NANOS) {
      restoreUndoSnapshot();
      return ManualResetResult.UNDONE;
    }

    clearUndo();
    if (BALANCES.isEmpty()) {
      return ManualResetResult.EMPTY;
    }
    undoSnapshot =
        new ResetSnapshot(new HashMap<>(BALANCES), new HashMap<>(PLAYER_NAMES), System.nanoTime());
    resetActiveBalances();
    return ManualResetResult.RESET;
  }

  private static void resetActiveBalances() {
    BALANCES.clear();
    PLAYER_NAMES.clear();
    LAST_INTERACTIONS.clear();
    PENDING_HITS.clear();
    CRITICAL_TARGETS.clear();
    WEAK_ATTACKERS.clear();
    recordReplaySnapshot();
  }

  private static void restoreUndoSnapshot() {
    ResetSnapshot snapshot = undoSnapshot;
    undoSnapshot = null;

    Map<UUID, Integer> hitsSinceReset = new HashMap<>(BALANCES);
    Map<UUID, String> namesSinceReset = new HashMap<>(PLAYER_NAMES);
    BALANCES.clear();
    PLAYER_NAMES.clear();
    BALANCES.putAll(snapshot.balances());
    PLAYER_NAMES.putAll(snapshot.playerNames());
    for (Map.Entry<UUID, Integer> entry : hitsSinceReset.entrySet()) {
      BALANCES.merge(entry.getKey(), entry.getValue(), Integer::sum);
    }
    PLAYER_NAMES.putAll(namesSinceReset);
    recordReplaySnapshot();
  }

  private static void clearUndo() {
    undoSnapshot = null;
    INACTIVITY_UNDOS.clear();
  }

  /** Restores the most recently timed-out player without affecting other balances. */
  private static boolean restoreLatestInactivityUndo() {
    long now = System.nanoTime();
    INACTIVITY_UNDOS
        .entrySet()
        .removeIf(entry -> now - entry.getValue().startedAtNanos() > UNDO_WINDOW_NANOS);
    Map.Entry<UUID, InactivityResetSnapshot> latest =
        INACTIVITY_UNDOS.entrySet().stream()
            .max(
                Map.Entry.comparingByValue(
                    java.util.Comparator.comparingLong(InactivityResetSnapshot::startedAtNanos)))
            .orElse(null);
    if (latest == null) {
      return false;
    }

    UUID opponentId = latest.getKey();
    InactivityResetSnapshot snapshot = latest.getValue();
    INACTIVITY_UNDOS.remove(opponentId);
    BALANCES.merge(opponentId, snapshot.halfPoints(), Integer::sum);
    PLAYER_NAMES.putIfAbsent(opponentId, snapshot.playerName());
    LAST_INTERACTIONS.putIfAbsent(opponentId, clientTick);
    recordReplaySnapshot();
    return true;
  }

  /** Returns a formatted nameplate balance, or {@code null} before the first tracked hit. */
  public static Component getNameplateBalance(UUID opponentId) {
    if (FlashbackReplaySupport.isReplayPlayback()) {
      ReplayBalance replayBalance = FlashbackReplaySupport.getBalance(opponentId);
      return replayBalance == null ? null : formatBalance(replayBalance.points());
    }
    if (!HitCounterConfig.get().isEnabled()) {
      return null;
    }
    Player localPlayer = Minecraft.getInstance().player;
    if (localPlayer != null && opponentId.equals(localPlayer.getUUID())) {
      return null;
    }

    Integer halfPoints = BALANCES.get(opponentId);
    if (halfPoints == null || (halfPoints == 0 && !HitCounterConfig.get().showsZeroBalances())) {
      return null;
    }
    return formatBalance(halfPoints);
  }

  public static Component getNameplateSuffix(UUID opponentId) {
    Component balance = getNameplateBalance(opponentId);
    return balance == null ? null : separator().copy().append(balance);
  }

  /** Captures the current visible balances for Flashback companion data. */
  public static Map<UUID, ReplayBalance> getReplayBalanceSnapshot() {
    Map<UUID, ReplayBalance> snapshot = new HashMap<>();
    for (Map.Entry<UUID, Integer> entry : BALANCES.entrySet()) {
      snapshot.put(
          entry.getKey(),
          new ReplayBalance(
              entry.getKey(),
              PLAYER_NAMES.getOrDefault(entry.getKey(), entry.getKey().toString()),
              entry.getValue()));
    }
    return snapshot;
  }

  /**
   * Removes a single opponent's recorded balance, returning their stored display name when found.
   */
  public static String resetPlayer(String playerName) {
    Iterator<Map.Entry<UUID, String>> iterator = PLAYER_NAMES.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<UUID, String> entry = iterator.next();
      if (entry.getValue().equalsIgnoreCase(playerName)) {
        clearUndo();
        UUID opponentId = entry.getKey();
        INACTIVITY_UNDOS.remove(opponentId);
        iterator.remove();
        BALANCES.remove(opponentId);
        LAST_INTERACTIONS.remove(opponentId);
        PENDING_HITS.removeIf(pending -> pending.opponentId().equals(opponentId));
        recordReplaySnapshot();
        return entry.getValue();
      }
    }
    return null;
  }

  /** Removes one opponent's balance without showing a reset notification. */
  public static void resetOpponent(UUID opponentId) {
    clearUndo();
    INACTIVITY_UNDOS.remove(opponentId);
    PLAYER_NAMES.remove(opponentId);
    BALANCES.remove(opponentId);
    LAST_INTERACTIONS.remove(opponentId);
    PENDING_HITS.removeIf(pending -> pending.opponentId().equals(opponentId));
    recordReplaySnapshot();
  }

  /** Returns all session balances, including a registered zero balance, ordered by player name. */
  public static List<RegisteredBalance> getRegisteredBalances() {
    return BALANCES.entrySet().stream()
        .map(
            entry ->
                new RegisteredBalance(
                    PLAYER_NAMES.getOrDefault(entry.getKey(), entry.getKey().toString()),
                    entry.getValue()))
        .sorted(
            (first, second) ->
                String.CASE_INSENSITIVE_ORDER.compare(first.playerName(), second.playerName()))
        .toList();
  }

  /** Returns the opponents that currently have a recorded balance. */
  public static Set<UUID> getTrackedOpponentIds() {
    return new java.util.HashSet<>(BALANCES.keySet());
  }

  public static SessionSummary getSessionSummary() {
    if (sessionDealtHalfPoints == 0 && sessionReceivedHalfPoints == 0) return null;
    return new SessionSummary(
        SESSION_OPPONENTS.size(),
        sessionDealtHalfPoints,
        sessionReceivedHalfPoints,
        sessionHighestBalance,
        sessionLowestBalance);
  }

  /** Captures this server's transient state so it can be restored later in this game session. */
  public static ServerState captureServerState() {
    return new ServerState(
        new HashMap<>(BALANCES),
        new HashMap<>(PLAYER_NAMES),
        new HashMap<>(LAST_INTERACTIONS),
        new java.util.HashSet<>(SESSION_OPPONENTS),
        clientTick,
        sessionDealtHalfPoints,
        sessionReceivedHalfPoints,
        sessionHighestBalance,
        sessionLowestBalance);
  }

  /** Replaces the active state with a previously captured server profile. */
  public static void restoreServerState(ServerState state) {
    clearUndo();
    resetActiveBalances();
    if (state == null) {
      sessionDealtHalfPoints = 0;
      sessionReceivedHalfPoints = 0;
      SESSION_OPPONENTS.clear();
      sessionHighestBalance = null;
      sessionLowestBalance = null;
      clientTick = 0;
      return;
    }
    BALANCES.putAll(state.balances());
    PLAYER_NAMES.putAll(state.playerNames());
    LAST_INTERACTIONS.putAll(state.lastInteractions());
    SESSION_OPPONENTS.addAll(state.sessionOpponents());
    clientTick = state.clientTick();
    sessionDealtHalfPoints = state.sessionDealtHalfPoints();
    sessionReceivedHalfPoints = state.sessionReceivedHalfPoints();
    sessionHighestBalance = state.sessionHighestBalance();
    sessionLowestBalance = state.sessionLowestBalance();
    recordReplaySnapshot();
  }

  /** Whether this profile has any PvP information worth retaining. */
  public static boolean hasServerState() {
    return !BALANCES.isEmpty() || sessionDealtHalfPoints != 0 || sessionReceivedHalfPoints != 0;
  }

  /** Formats a balance using the configured positive, negative, or zero color. */
  public static Component formatBalance(int halfPoints) {
    int color =
        halfPoints > 0
            ? HitCounterConfig.get().getPositiveColorRgb()
            : halfPoints < 0
                ? HitCounterConfig.get().getNegativeColorRgb()
                : HitCounterConfig.get().getZeroColorRgb();
    return Component.literal(formatHalfPoints(halfPoints)).withColor(color);
  }

  /** Applies the clear-and-stop behavior when the mod is turned off. */
  public static void onConfigurationSaved(boolean enabledBeforeOpening) {
    if (enabledBeforeOpening && !HitCounterConfig.get().isEnabled()) {
      reset();
    }
  }

  private static void flushReadyHits() {
    Iterator<PendingHit> iterator = PENDING_HITS.iterator();
    while (iterator.hasNext()) {
      PendingHit pending = iterator.next();
      if (clientTick - pending.receivedAtTick() < 2) {
        continue;
      }

      int hitValue =
          consumeCritical(pending.victimEntityId())
              ? HitCounterConfig.get().getCriticalTenths()
              : pending.weakMelee() && consumeWeakAttack(pending.attackerEntityId())
                  ? HitCounterConfig.get().getWeakTenths()
                  : HitCounterConfig.get().getNormalTenths();
      PLAYER_NAMES.put(pending.opponentId(), pending.opponentName());
      if (!BALANCES.containsKey(pending.opponentId())) {
        recordSessionExtreme(pending.opponentName(), 0);
      }
      int newBalance =
          BALANCES.merge(
              pending.opponentId(), pending.wasOutgoing() ? hitValue : -hitValue, Integer::sum);
      recordSessionExtreme(pending.opponentName(), newBalance);
      LAST_INTERACTIONS.put(pending.opponentId(), clientTick);
      SESSION_OPPONENTS.add(pending.opponentId());
      if (pending.wasOutgoing()) {
        sessionDealtHalfPoints += hitValue;
      } else {
        sessionReceivedHalfPoints += hitValue;
      }
      recordReplaySnapshot();
      iterator.remove();
    }
  }

  /**
   * Records the highest and lowest balances reached in this server profile, independent of active
   * resets.
   */
  private static void recordSessionExtreme(String playerName, int halfPoints) {
    RegisteredBalance balance = new RegisteredBalance(playerName, halfPoints);
    if (sessionHighestBalance == null || halfPoints > sessionHighestBalance.halfPoints()) {
      sessionHighestBalance = balance;
    }
    if (sessionLowestBalance == null || halfPoints < sessionLowestBalance.halfPoints()) {
      sessionLowestBalance = balance;
    }
  }

  private static void resetInactiveBalances() {
    HitCounterConfig config = HitCounterConfig.get();
    if (!config.autoResetsInactiveBalances()) return;
    long timeoutTicks = config.getInactivityTimeoutSeconds() * 20L;
    for (UUID opponentId : new ArrayList<>(LAST_INTERACTIONS.keySet())) {
      Long lastInteraction = LAST_INTERACTIONS.get(opponentId);
      if (lastInteraction == null
          || clientTick - lastInteraction < timeoutTicks
          || !BALANCES.containsKey(opponentId)) continue;
      String playerName = PLAYER_NAMES.getOrDefault(opponentId, opponentId.toString());
      INACTIVITY_UNDOS.put(
          opponentId,
          new InactivityResetSnapshot(BALANCES.get(opponentId), playerName, System.nanoTime()));
      PLAYER_NAMES.remove(opponentId);
      BALANCES.remove(opponentId);
      LAST_INTERACTIONS.remove(opponentId);
      PENDING_HITS.removeIf(pending -> pending.opponentId().equals(opponentId));
      HitCounterToasts.showInactiveReset(playerName);
      recordReplaySnapshot();
    }
  }

  private static boolean consumeCritical(int targetEntityId) {
    Long markedAt = CRITICAL_TARGETS.remove(targetEntityId);
    return markedAt != null && clientTick - markedAt <= 4;
  }

  private static boolean consumeWeakAttack(int attackerEntityId) {
    Long markedAt = WEAK_ATTACKERS.remove(attackerEntityId);
    return markedAt != null && clientTick - markedAt <= 4;
  }

  private static boolean isWeakMeleeSource(DamageSource source) {
    return "player_attack".equals(damageTypeId(source));
  }

  private static String damageTypeId(DamageSource source) {
    return source.typeHolder().unwrapKey().map(key -> key.identifier().getPath()).orElse("unknown");
  }

  /**
   * Classifies hits using the actual damage-type registry key rather than its death-message ID.
   * Some servers use generic damage types for projectiles, so a recognized direct projectile takes
   * precedence.
   */
  private static boolean isAllowedHitSource(DamageSource source) {
    HitCounterConfig config = HitCounterConfig.get();
    Entity directEntity = source.getDirectEntity();
    if (directEntity != null) {
      String directSource =
          DIRECT_ENTITY_HIT_SOURCES.get(
              BuiltInRegistries.ENTITY_TYPE.getKey(directEntity.getType()).getPath());
      if (directSource != null) {
        return config.allowsHitSource(directSource);
      }
    }

    String damageTypeId = damageTypeId(source);
    if ("magic".equals(damageTypeId) || "indirect_magic".equals(damageTypeId)) {
      return config.allowsHitSource("server_magic_and_abilities");
    }
    if (HitCounterConfig.HIT_SOURCES.contains(damageTypeId)) {
      return config.allowsHitSource(damageTypeId);
    }
    return config.countsUnknownHitSources();
  }

  private static boolean isEligibleCombatPlayer(Player player) {
    if (player.isSpectator()) {
      return false;
    }

    PlayerInfo playerInfo =
        Minecraft.getInstance().getConnection() == null
            ? null
            : Minecraft.getInstance().getConnection().getPlayerInfo(player.getUUID());
    return playerInfo == null || playerInfo.getGameMode() != GameType.CREATIVE;
  }

  private static String formatHalfPoints(int halfPoints) {
    if (halfPoints == 0) {
      return "0";
    }

    String sign = halfPoints > 0 ? "+" : "-";
    int absolute = Math.abs(halfPoints);
    return sign
        + (absolute % 10 == 0
            ? Integer.toString(absolute / 10)
            : String.format(java.util.Locale.ROOT, "%.1f", absolute / 10.0D));
  }

  public static Component separator() {
    return Component.literal(" | ").withColor(SEPARATOR_COLOR);
  }

  private static void recordReplaySnapshot() {
    FlashbackReplaySupport.recordSnapshot(getReplayBalanceSnapshot());
  }

  public record RegisteredBalance(String playerName, int halfPoints) {}

  public record ReplayBalance(UUID playerId, String playerName, int points) {}

  public record SessionSummary(
      int playersTracked,
      int dealtHalfPoints,
      int receivedHalfPoints,
      RegisteredBalance highestBalance,
      RegisteredBalance lowestBalance) {}

  public record ServerState(
      Map<UUID, Integer> balances,
      Map<UUID, String> playerNames,
      Map<UUID, Long> lastInteractions,
      java.util.Set<UUID> sessionOpponents,
      long clientTick,
      int sessionDealtHalfPoints,
      int sessionReceivedHalfPoints,
      RegisteredBalance sessionHighestBalance,
      RegisteredBalance sessionLowestBalance) {}

  public enum ManualResetResult {
    RESET,
    UNDONE,
    EMPTY
  }

  private record ResetSnapshot(
      Map<UUID, Integer> balances, Map<UUID, String> playerNames, long startedAtNanos) {}

  private record InactivityResetSnapshot(int halfPoints, String playerName, long startedAtNanos) {}

  private record PendingHit(
      int victimEntityId,
      int attackerEntityId,
      UUID opponentId,
      String opponentName,
      boolean wasOutgoing,
      boolean weakMelee,
      long receivedAtTick) {}
}
