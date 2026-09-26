package com.leefy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

/** Persistent client settings for Hit Counter. */
public final class HitCounterConfig {
  public static final List<String> HIT_SOURCES =
      List.of(
          "player_attack",
          "mace_smash",
          "spear",
          "arrow",
          "trident",
          "wind_charge",
          "fireball",
          "fireworks",
          "instant_damage_potion",
          "server_magic_and_abilities",
          "player_explosion",
          "explosion",
          "thorns");
  public static final int DEFAULT_POSITIVE_RGB = 0x55FF55;
  public static final int DEFAULT_NEGATIVE_RGB = 0xFF5555;
  public static final int DEFAULT_ZERO_RGB = 0xFFFFFF;

  private static final String DEFAULT_POSITIVE_COLOR = "#55FF55";
  private static final String DEFAULT_NEGATIVE_COLOR = "#FF5555";
  private static final String DEFAULT_ZERO_COLOR = "#FFFFFF";
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Gson COMPACT_GSON = new Gson();
  private static final List<List<String>> SAVE_GROUPS =
      List.of(
          List.of(
              "enabled",
              "balancePosition",
              "showZeroBalances",
              "criticalHitValue",
              "normalHitValue",
              "weakHitValue",
              "renderHitBalancesInFlashbackReplays"),
          List.of(
              "autoResetOnDeath",
              "autoResetOnSpectatorMode",
              "deathResetMode",
              "autoResetInactiveBalances",
              "inactivityTimeoutSeconds"),
          List.of("positiveColor", "negativeColor", "zeroColor"),
          List.of("hitSourceFilterMode", "countUnknownHitSources", "hitSources"),
          List.of("lastNotifiedUpdateVersion"));
  private static final Pattern HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");
  private static final Path PATH =
      FabricLoader.getInstance().getConfigDir().resolve("hit-counter.json");
  private static HitCounterConfig instance = new HitCounterConfig();
  private static HitCounterConfig runtimeInstance = instance;

  private boolean enabled = true;
  private boolean showZeroBalances = true;
  private BalancePosition balancePosition = BalancePosition.RIGHT;
  private boolean renderHitBalancesInFlashbackReplays = true;
  private String lastNotifiedUpdateVersion = "";
  private boolean autoResetOnDeath = true;
  private boolean autoResetOnSpectatorMode = true;
  private DeathResetMode deathResetMode = DeathResetMode.EITHER_PLAYER;
  private boolean autoResetInactiveBalances;
  private int inactivityTimeoutSeconds = 60;
  private HitSourceFilterMode hitSourceFilterMode = HitSourceFilterMode.BLACKLIST;
  private boolean countUnknownHitSources = true;
  private Set<String> hitSources = new HashSet<>(List.of("wind_charge", "thorns"));
  private double normalHitValue = 1.0D;
  private double weakHitValue = 0.5D;
  private double criticalHitValue = 1.5D;
  private String positiveColor = DEFAULT_POSITIVE_COLOR;
  private String negativeColor = DEFAULT_NEGATIVE_COLOR;
  private String zeroColor = DEFAULT_ZERO_COLOR;

  private HitCounterConfig() {}

  public static HitCounterConfig get() {
    return runtimeInstance;
  }

  /** The persistent default profile edited by the standard configuration tabs. */
  public static HitCounterConfig getDefault() {
    return instance;
  }

  public static void load() {
    if (!Files.exists(PATH)) {
      instance = new HitCounterConfig();
      runtimeInstance = instance;
      return;
    }

    try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
      HitCounterConfig loaded = GSON.fromJson(reader, HitCounterConfig.class);
      instance = loaded == null ? new HitCounterConfig() : loaded;
      instance.normalize();
      runtimeInstance = instance;
    } catch (IOException | RuntimeException exception) {
      HitCounter.LOGGER.warn(
          "Could not load Hit Counter configuration; using defaults.", exception);
      instance = new HitCounterConfig();
      runtimeInstance = instance;
    }
  }

  public void save() {
    saveDefault();
  }

  static HitCounterConfig copyOfDefault() {
    HitCounterConfig copy = GSON.fromJson(GSON.toJson(instance), HitCounterConfig.class);
    copy.normalize();
    return copy;
  }

  static void setRuntimeProfile(HitCounterConfig profile) {
    runtimeInstance = profile == null ? instance : profile;
  }

  void saveDefault() {
    normalize();
    try {
      Files.createDirectories(PATH.getParent());
      try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
        writer.write(serializeForFile());
      }
    } catch (IOException exception) {
      HitCounter.LOGGER.warn("Could not save Hit Counter configuration.", exception);
    }
  }

  /** Writes settings in the same grouped order as the config screen. */
  private String serializeForFile() {
    JsonObject values = GSON.toJsonTree(this).getAsJsonObject();
    StringBuilder output = new StringBuilder("{\n");
    boolean firstValue = true;
    for (List<String> group : SAVE_GROUPS) {
      boolean firstInGroup = true;
      for (String key : group) {
        if (!firstValue) {
          output.append(",\n");
          if (firstInGroup) {
            output.append('\n');
          }
        }
        output
            .append("  \"")
            .append(key)
            .append("\": ")
            .append(COMPACT_GSON.toJson(values.get(key)));
        firstValue = false;
        firstInGroup = false;
      }
    }
    return output.append("\n}\n").toString();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean showsZeroBalances() {
    return showZeroBalances;
  }

  public void setShowZeroBalances(boolean showZeroBalances) {
    this.showZeroBalances = showZeroBalances;
  }

  public BalancePosition getBalancePosition() {
    return balancePosition;
  }

  public void setBalancePosition(BalancePosition balancePosition) {
    this.balancePosition = balancePosition == null ? BalancePosition.RIGHT : balancePosition;
  }

  public boolean rendersHitBalancesInFlashbackReplays() {
    return renderHitBalancesInFlashbackReplays;
  }

  public void setRenderHitBalancesInFlashbackReplays(boolean value) {
    renderHitBalancesInFlashbackReplays = value;
  }

  public String getLastNotifiedUpdateVersion() {
    return lastNotifiedUpdateVersion == null ? "" : lastNotifiedUpdateVersion;
  }

  public void setLastNotifiedUpdateVersion(String value) {
    lastNotifiedUpdateVersion = value == null ? "" : value;
  }

  public boolean autoResetsOnDeath() {
    return autoResetOnDeath;
  }

  public void setAutoResetOnDeath(boolean autoResetOnDeath) {
    this.autoResetOnDeath = autoResetOnDeath;
  }

  public boolean autoResetsOnSpectatorMode() {
    return autoResetOnSpectatorMode;
  }

  public void setAutoResetOnSpectatorMode(boolean autoResetOnSpectatorMode) {
    this.autoResetOnSpectatorMode = autoResetOnSpectatorMode;
  }

  public DeathResetMode getDeathResetMode() {
    return deathResetMode;
  }

  public void setDeathResetMode(DeathResetMode deathResetMode) {
    this.deathResetMode = deathResetMode == null ? DeathResetMode.EITHER_PLAYER : deathResetMode;
  }

  public boolean resetsWhenYouDie() {
    return deathResetMode != DeathResetMode.OPPONENT_ONLY;
  }

  public boolean resetsWhenOpponentDies() {
    return deathResetMode != DeathResetMode.YOU_ONLY;
  }

  public boolean autoResetsInactiveBalances() {
    return autoResetInactiveBalances;
  }

  public void setAutoResetInactiveBalances(boolean autoResetInactiveBalances) {
    this.autoResetInactiveBalances = autoResetInactiveBalances;
  }

  public int getInactivityTimeoutSeconds() {
    return inactivityTimeoutSeconds;
  }

  public void setInactivityTimeoutSeconds(int inactivityTimeoutSeconds) {
    this.inactivityTimeoutSeconds = Math.clamp(inactivityTimeoutSeconds, 10, 600);
  }

  public HitSourceFilterMode getHitSourceFilterMode() {
    return hitSourceFilterMode;
  }

  public void setHitSourceFilterMode(HitSourceFilterMode hitSourceFilterMode) {
    this.hitSourceFilterMode =
        hitSourceFilterMode == null ? HitSourceFilterMode.BLACKLIST : hitSourceFilterMode;
  }

  public boolean countsUnknownHitSources() {
    return countUnknownHitSources;
  }

  public void setCountUnknownHitSources(boolean countUnknownHitSources) {
    this.countUnknownHitSources = countUnknownHitSources;
  }

  public boolean isHitSourceSelected(String sourceId) {
    return hitSources.contains(sourceId);
  }

  public void setHitSourceSelected(String sourceId, boolean selected) {
    if (selected) {
      hitSources.add(sourceId);
    } else {
      hitSources.remove(sourceId);
    }
  }

  /** Returns whether every source in a configuration group is selected. */
  public boolean areAllHitSourcesSelected(List<String> sourceIds) {
    return sourceIds.stream().allMatch(hitSources::contains);
  }

  /** Selects or clears every source in a configuration group. */
  public void setHitSourcesSelected(List<String> sourceIds, boolean selected) {
    for (String sourceId : sourceIds) {
      setHitSourceSelected(sourceId, selected);
    }
  }

  /** Returns whether a player-attributed damage source is eligible for hit counting. */
  public boolean allowsHitSource(String sourceId) {
    boolean selected = isHitSourceSelected(sourceId);
    return hitSourceFilterMode == HitSourceFilterMode.WHITELIST ? selected : !selected;
  }

  public int getCriticalTenths() {
    return (int) Math.round(criticalHitValue * 10.0D);
  }

  public double getCriticalHitValue() {
    return criticalHitValue;
  }

  public void setCriticalHitValue(double criticalHitValue) {
    this.criticalHitValue = criticalHitValue;
  }

  public int getNormalTenths() {
    return (int) Math.round(normalHitValue * 10.0D);
  }

  public double getNormalHitValue() {
    return normalHitValue;
  }

  public void setNormalHitValue(double normalHitValue) {
    this.normalHitValue = normalHitValue;
  }

  public int getWeakTenths() {
    return (int) Math.round(weakHitValue * 10.0D);
  }

  public double getWeakHitValue() {
    return weakHitValue;
  }

  public void setWeakHitValue(double weakHitValue) {
    this.weakHitValue = weakHitValue;
  }

  public int getPositiveColorRgb() {
    return parseColor(positiveColor, DEFAULT_POSITIVE_RGB);
  }

  public void setPositiveColorRgb(int color) {
    positiveColor = formatColor(color);
  }

  public int getNegativeColorRgb() {
    return parseColor(negativeColor, DEFAULT_NEGATIVE_RGB);
  }

  public void setNegativeColorRgb(int color) {
    negativeColor = formatColor(color);
  }

  public int getZeroColorRgb() {
    return parseColor(zeroColor, DEFAULT_ZERO_RGB);
  }

  public void setZeroColorRgb(int color) {
    zeroColor = formatColor(color);
  }

  public Color getPositiveColor() {
    return new Color(getPositiveColorRgb());
  }

  public void setPositiveColor(Color color) {
    setPositiveColorRgb(color.getRGB());
  }

  public Color getNegativeColor() {
    return new Color(getNegativeColorRgb());
  }

  public void setNegativeColor(Color color) {
    setNegativeColorRgb(color.getRGB());
  }

  public Color getZeroColor() {
    return new Color(getZeroColorRgb());
  }

  public void setZeroColor(Color color) {
    setZeroColorRgb(color.getRGB());
  }

  private void normalize() {
    setBalancePosition(balancePosition);
    normalHitValue = Math.clamp((int) Math.round(normalHitValue * 10.0D), 0, 50) / 10.0D;
    weakHitValue = Math.clamp((int) Math.round(weakHitValue * 10.0D), 0, 50) / 10.0D;
    criticalHitValue = Math.clamp((int) Math.round(criticalHitValue * 10.0D), 0, 50) / 10.0D;
    positiveColor = normalizeColor(positiveColor, DEFAULT_POSITIVE_COLOR);
    negativeColor = normalizeColor(negativeColor, DEFAULT_NEGATIVE_COLOR);
    zeroColor = normalizeColor(zeroColor, DEFAULT_ZERO_COLOR);
    hitSourceFilterMode =
        hitSourceFilterMode == null ? HitSourceFilterMode.BLACKLIST : hitSourceFilterMode;
    deathResetMode = deathResetMode == null ? DeathResetMode.EITHER_PLAYER : deathResetMode;
    inactivityTimeoutSeconds = Math.clamp(inactivityTimeoutSeconds, 10, 600);
    if (hitSources == null) {
      hitSources = new HashSet<>();
    } else {
      hitSources.removeIf(source -> source == null || source.isBlank());
    }
  }

  private static int parseColor(String value, int fallback) {
    String normalized = normalizeColor(value, null);
    return normalized == null ? fallback : Integer.parseInt(normalized.substring(1), 16);
  }

  private static String normalizeColor(String value, String fallback) {
    if (value == null || !HEX_COLOR.matcher(value).matches()) {
      return fallback;
    }
    return value.toUpperCase(Locale.ROOT);
  }

  private static String formatColor(int color) {
    return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
  }

  public enum HitSourceFilterMode {
    BLACKLIST,
    WHITELIST
  }

  public enum DeathResetMode {
    EITHER_PLAYER,
    YOU_ONLY,
    OPPONENT_ONLY
  }

  public enum BalancePosition {
    LEFT,
    RIGHT,
    ABOVE,
    BELOW
  }
}
