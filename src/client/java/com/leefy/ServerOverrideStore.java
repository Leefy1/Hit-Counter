package com.leefy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.Color;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Stores complete optional setting profiles keyed by saved server address. */
public final class ServerOverrideStore {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final Path PATH =
      FabricLoader.getInstance().getConfigDir().resolve("hit-counter-server-overrides.json");
  private static Map<String, Profile> profiles = new HashMap<>();
  private static String activeAddress;

  private ServerOverrideStore() {}

  public static void load() {
    try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
      StoreData data = GSON.fromJson(reader, StoreData.class);
      profiles =
          data == null || data.profiles == null ? new HashMap<>() : new HashMap<>(data.profiles);
    } catch (Exception ignored) {
      profiles = new HashMap<>();
    }
  }

  public static void save() {
    try {
      Files.createDirectories(PATH.getParent());
      try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
        GSON.toJson(new StoreData(profiles), writer);
      }
    } catch (Exception exception) {
      HitCounter.LOGGER.warn("Could not save Hit Counter server overrides.", exception);
    }
  }

  public static void onJoin() {
    String address = currentAddress();
    activeAddress = address;
    Profile profile = address == null ? null : profiles.get(address);
    HitCounterConfig runtime = HitCounterConfig.copyOfDefault();
    if (profile != null) profile.apply(runtime);
    HitCounterConfig.setRuntimeProfile(runtime);
  }

  public static void onDisconnect() {
    activeAddress = null;
    HitCounterConfig.setRuntimeProfile(null);
  }

  /** Applies newly saved General settings immediately when no server override is active. */
  public static void refreshActiveDefaultProfile() {
    if (activeAddress == null || !profiles.containsKey(activeAddress)) {
      HitCounterConfig.setRuntimeProfile(null);
    }
  }

  public static boolean hasOverride(String address) {
    return profiles.containsKey(normalize(address));
  }

  public static void create(String address, HitCounterConfig config) {
    profiles.putIfAbsent(normalize(address), new Profile(config));
    save();
  }

  /** Returns a detached editable copy; it never changes the default profile. */
  public static HitCounterConfig profileCopy(String address) {
    Profile profile = profiles.get(normalize(address));
    if (profile == null) return null;
    HitCounterConfig copy = HitCounterConfig.copyOfDefault();
    profile.apply(copy);
    return copy;
  }

  public static void saveProfile(String address, HitCounterConfig config) {
    String normalized = normalize(address);
    if (normalized == null) return;
    profiles.put(normalized, new Profile(config));
    save();
    if (normalized.equals(activeAddress)) onJoin();
  }

  public static void remove(String address) {
    String normalized = normalize(address);
    profiles.remove(normalized);
    save();
    if (normalized != null && normalized.equals(activeAddress)) onJoin();
  }

  public static List<SavedServer> savedServers() {
    var list = new net.minecraft.client.multiplayer.ServerList(Minecraft.getInstance());
    list.load();
    Map<String, String> namesByAddress = new LinkedHashMap<>();
    for (int index = 0; index < list.size(); index++) {
      var server = list.get(index);
      String address = normalize(server.ip);
      if (address != null) namesByAddress.putIfAbsent(address, server.name);
    }
    return namesByAddress.entrySet().stream()
        .map(entry -> new SavedServer(entry.getValue(), entry.getKey()))
        .toList();
  }

  public record SavedServer(String name, String address) {
    public String displayName() {
      return name == null || name.isBlank() || name.equals(address) ? address : name + " (" + address + ")";
    }
  }

  private static String currentAddress() {
    var server = Minecraft.getInstance().getCurrentServer();
    return server == null || server.ip == null ? null : normalize(server.ip);
  }

  public static String normalize(String address) {
    return address == null ? null : address.trim().toLowerCase(java.util.Locale.ROOT);
  }

  private record StoreData(Map<String, Profile> profiles) {}

  private static final class Profile {
    boolean enabled,
        showZeroBalances,
        autoResetOnDeath,
        autoResetOnSpectatorMode,
        autoResetInactiveBalances,
        countUnknownHitSources;
    HitCounterConfig.BalancePosition balancePosition;
    HitCounterConfig.DeathResetMode deathResetMode;
    HitCounterConfig.HitSourceFilterMode hitSourceFilterMode;
    java.util.Set<String> hitSources;
    int inactivityTimeoutSeconds;
    double normalHitValue, criticalHitValue;
    double weakHitValue = 0.5D;
    int positiveColor, negativeColor, zeroColor;

    Profile(HitCounterConfig c) {
      enabled = c.isEnabled();
      showZeroBalances = c.showsZeroBalances();
      balancePosition = c.getBalancePosition();
      autoResetOnDeath = c.autoResetsOnDeath();
      autoResetOnSpectatorMode = c.autoResetsOnSpectatorMode();
      autoResetInactiveBalances = c.autoResetsInactiveBalances();
      countUnknownHitSources = c.countsUnknownHitSources();
      deathResetMode = c.getDeathResetMode();
      hitSourceFilterMode = c.getHitSourceFilterMode();
      hitSources = new java.util.HashSet<>();
      for (String source : HitCounterConfig.HIT_SOURCES)
        if (c.isHitSourceSelected(source)) hitSources.add(source);
      inactivityTimeoutSeconds = c.getInactivityTimeoutSeconds();
      criticalHitValue = c.getCriticalHitValue();
      normalHitValue = c.getNormalHitValue();
      weakHitValue = c.getWeakHitValue();
      positiveColor = c.getPositiveColorRgb();
      negativeColor = c.getNegativeColorRgb();
      zeroColor = c.getZeroColorRgb();
    }

    void apply(HitCounterConfig c) {
      c.setEnabled(enabled);
      c.setShowZeroBalances(showZeroBalances);
      c.setBalancePosition(balancePosition);
      c.setAutoResetOnDeath(autoResetOnDeath);
      c.setAutoResetOnSpectatorMode(autoResetOnSpectatorMode);
      c.setAutoResetInactiveBalances(autoResetInactiveBalances);
      c.setDeathResetMode(deathResetMode);
      c.setHitSourceFilterMode(hitSourceFilterMode);
      c.setCountUnknownHitSources(countUnknownHitSources);
      for (String source : HitCounterConfig.HIT_SOURCES)
        c.setHitSourceSelected(source, hitSources != null && hitSources.contains(source));
      c.setInactivityTimeoutSeconds(inactivityTimeoutSeconds);
      c.setCriticalHitValue(criticalHitValue);
      c.setNormalHitValue(normalHitValue);
      c.setWeakHitValue(weakHitValue);
      c.setPositiveColor(new Color(positiveColor));
      c.setNegativeColor(new Color(negativeColor));
      c.setZeroColor(new Color(zeroColor));
    }
  }
}
