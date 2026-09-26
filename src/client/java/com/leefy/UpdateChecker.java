package com.leefy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.Minecraft;

final class UpdateChecker {
  private static final String PROJECT = "player-hit-counter";

  private UpdateChecker() {}

  static void check() {
    String current =
        FabricLoader.getInstance()
            .getModContainer(HitCounter.MOD_ID)
            .orElseThrow()
            .getMetadata()
            .getVersion()
            .getFriendlyString();
    Version currentVersion = parseVersion(current);
    if (currentVersion == null) return;
    String gameVersion = Minecraft.getInstance().getLaunchedVersion();
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "https://api.modrinth.com/v2/project/"
                        + PROJECT
                        + "/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22"
                        + gameVersion
                        + "%22%5D&include_changelog=false"))
            .header("User-Agent", "player-hit-counter/" + current)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    HttpClient.newHttpClient()
        .sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenAccept(
            response -> {
              if (response.statusCode() != 200) return;
              JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
              String latest = null;
              Version latestVersion = currentVersion;
              int newer = 0;
              for (var element : versions) {
                JsonObject version = element.getAsJsonObject();
                if (!"release".equals(version.get("version_type").getAsString())) continue;
                String number = version.get("version_number").getAsString();
                Version candidate = parseVersion(number);
                if (candidate == null || candidate.compareTo(currentVersion) <= 0) continue;
                newer++;
                if (candidate.compareTo(latestVersion) > 0) {
                  latest = number;
                  latestVersion = candidate;
                }
              }
              if (latest == null) return;
              String notifiedVersion = latest;
              int count = newer;
              Minecraft.getInstance()
                  .execute(
                      () -> {
                        HitCounterConfig config = HitCounterConfig.getDefault();
                        if (notifiedVersion.equals(config.getLastNotifiedUpdateVersion())) return;
                        config.setLastNotifiedUpdateVersion(notifiedVersion);
                        config.save();
                        HitCounterToasts.showUpdate(notifiedVersion, count);
                      });
            })
        .exceptionally(error -> null);
  }

  private static Version parseVersion(String number) {
    try {
      SemanticVersion version = SemanticVersion.parse(number);
      return version.hasWildcard() ? null : version;
    } catch (VersionParsingException exception) {
      return null;
    }
  }
}
