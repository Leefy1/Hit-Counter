package com.leefy;

import com.google.gson.Gson;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

/** Records Hit Counter state beside a Flashback replay and restores it during playback. */
public final class FlashbackReplaySupport {
  private static final Gson GSON = new Gson();
  private static final String FLASHBACK_MOD_ID = "flashback";
  private static final String SIDECAR_SUFFIX = ".hitcounter.jsonl";
  private static BufferedWriter recordingWriter;
  private static boolean wasRecording;
  private static int recordingTick;
  private static Path loadedSidecar;
  private static List<Snapshot> playbackSnapshots = List.of();

  private FlashbackReplaySupport() {}

  public static void tick() {
    if (!HitCounterConfig.get().isEnabled()) {
      stopRecording();
      wasRecording = false;
      return;
    }
    if (!isFlashbackInstalled()) {
      return;
    }

    boolean recording = isRecording();
    if (recording && !wasRecording) {
      startRecording();
    } else if (!recording && wasRecording) {
      stopRecording();
    }
    wasRecording = recording;
    if (recordingWriter != null) {
      recordingTick++;
    }
  }

  public static boolean isReplayPlayback() {
    return isFlashbackInstalled() && invokeStaticBoolean("isInReplay");
  }

  public static void recordSnapshot(Map<UUID, HitBalanceTracker.ReplayBalance> balances) {
    if (recordingWriter == null) {
      return;
    }

    try {
      recordingWriter.write(
          GSON.toJson(new Snapshot(recordingTick, new ArrayList<>(balances.values()))));
      recordingWriter.newLine();
      recordingWriter.flush();
    } catch (IOException exception) {
      HitCounter.LOGGER.warn("Could not write Flashback Hit Counter data.", exception);
      stopRecording();
    }
  }

  public static HitBalanceTracker.ReplayBalance getBalance(UUID playerId) {
    if (!HitCounterConfig.getDefault().isEnabled()
        || !isReplayPlayback()
        || !HitCounterConfig.getDefault().rendersHitBalancesInFlashbackReplays()) {
      return null;
    }

    loadPlaybackSidecar();
    int replayTick = getReplayTick();
    Snapshot snapshot = null;
    for (Snapshot candidate : playbackSnapshots) {
      if (candidate.tick() > replayTick) {
        break;
      }
      snapshot = candidate;
    }
    if (snapshot == null) {
      return null;
    }
    for (HitBalanceTracker.ReplayBalance balance : snapshot.balances()) {
      if (playerId.equals(balance.playerId())) {
        return balance;
      }
    }
    return null;
  }

  private static void startRecording() {
    Path directory = getFlashbackDataDirectory();
    if (directory == null) {
      return;
    }

    try {
      Files.createDirectories(directory.resolve("hit-counter"));
      UUID replayId = getReplayIdentifier(getStaticField("RECORDER"));
      String fileName =
          replayId == null ? "recording-" + System.currentTimeMillis() : "recording-" + replayId;
      recordingWriter =
          Files.newBufferedWriter(
              directory.resolve("hit-counter").resolve(fileName + SIDECAR_SUFFIX),
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING);
      recordingTick = 0;
      recordSnapshot(HitBalanceTracker.getReplayBalanceSnapshot());
    } catch (IOException exception) {
      HitCounter.LOGGER.warn("Could not create Flashback Hit Counter data.", exception);
      recordingWriter = null;
    }
  }

  private static void stopRecording() {
    if (recordingWriter == null) {
      return;
    }
    try {
      recordingWriter.close();
    } catch (IOException exception) {
      HitCounter.LOGGER.warn("Could not close Flashback Hit Counter data.", exception);
    } finally {
      recordingWriter = null;
    }
  }

  private static void loadPlaybackSidecar() {
    Path sidecar = findPlaybackSidecar();
    if (java.util.Objects.equals(sidecar, loadedSidecar)) {
      return;
    }
    loadedSidecar = sidecar;
    if (sidecar == null) {
      playbackSnapshots = List.of();
      return;
    }

    try {
      List<Snapshot> snapshots = new ArrayList<>();
      for (String line : Files.readAllLines(sidecar, StandardCharsets.UTF_8)) {
        if (!line.isBlank()) {
          snapshots.add(GSON.fromJson(line, Snapshot.class));
        }
      }
      snapshots.sort(Comparator.comparingInt(Snapshot::tick));
      playbackSnapshots = snapshots;
    } catch (IOException | RuntimeException exception) {
      HitCounter.LOGGER.warn("Could not load Flashback Hit Counter data.", exception);
      playbackSnapshots = List.of();
    }
  }

  private static Path findPlaybackSidecar() {
    Path directory = getFlashbackDataDirectory();
    if (directory == null) {
      return null;
    }
    Path sidecarDirectory = directory.resolve("hit-counter");
    if (!Files.isDirectory(sidecarDirectory)) {
      return null;
    }
    UUID replayId = getReplayIdentifier(invokeStatic("getReplayServer"));
    if (replayId != null) {
      Path exactMatch = sidecarDirectory.resolve("recording-" + replayId + SIDECAR_SUFFIX);
      if (Files.isRegularFile(exactMatch)) {
        return exactMatch;
      }
    }
    try (var files = Files.list(sidecarDirectory)) {
      return files
          .filter(path -> path.getFileName().toString().endsWith(SIDECAR_SUFFIX))
          .max(Comparator.comparingLong(FlashbackReplaySupport::lastModified))
          .orElse(null);
    } catch (IOException exception) {
      return null;
    }
  }

  private static long lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (IOException exception) {
      return 0L;
    }
  }

  private static boolean isRecording() {
    Object recorder = getStaticField("RECORDER");
    if (recorder == null) {
      return false;
    }
    try {
      return (boolean) recorder.getClass().getMethod("readyToWrite").invoke(recorder);
    } catch (ReflectiveOperationException | RuntimeException exception) {
      return false;
    }
  }

  private static int getReplayTick() {
    Object replayServer = invokeStatic("getReplayServer");
    if (replayServer == null) {
      return -1;
    }
    try {
      return (int) replayServer.getClass().getMethod("getReplayTick").invoke(replayServer);
    } catch (ReflectiveOperationException | RuntimeException exception) {
      return -1;
    }
  }

  private static UUID getReplayIdentifier(Object holder) {
    if (holder == null) {
      return null;
    }
    try {
      Object metadata;
      try {
        metadata = holder.getClass().getMethod("getMetadata").invoke(holder);
      } catch (NoSuchMethodException exception) {
        Field field = holder.getClass().getDeclaredField("metadata");
        field.setAccessible(true);
        metadata = field.get(holder);
      }
      Field identifier = metadata.getClass().getField("replayIdentifier");
      Object value = identifier.get(metadata);
      return value instanceof UUID uuid ? uuid : null;
    } catch (ReflectiveOperationException | RuntimeException exception) {
      return null;
    }
  }

  private static Path getFlashbackDataDirectory() {
    Object result = invokeStatic("getDataDirectory");
    return result instanceof Path path ? path : null;
  }

  private static boolean invokeStaticBoolean(String methodName) {
    Object result = invokeStatic(methodName);
    return result instanceof Boolean value && value;
  }

  private static Object invokeStatic(String methodName) {
    try {
      return flashbackClass().getMethod(methodName).invoke(null);
    } catch (ReflectiveOperationException | RuntimeException exception) {
      return null;
    }
  }

  private static Object getStaticField(String fieldName) {
    try {
      return flashbackClass().getField(fieldName).get(null);
    } catch (ReflectiveOperationException | RuntimeException exception) {
      return null;
    }
  }

  private static Class<?> flashbackClass() throws ClassNotFoundException {
    return Class.forName("com.moulberry.flashback.Flashback");
  }

  private static boolean isFlashbackInstalled() {
    return FabricLoader.getInstance().isModLoaded(FLASHBACK_MOD_ID);
  }

  private record Snapshot(int tick, List<HitBalanceTracker.ReplayBalance> balances) {}
}
