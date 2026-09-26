package com.leefy;

import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/**
 * Shows the current session before a manual leave and the final session after an error disconnect.
 */
public final class SessionSummaryRenderer {
  private static final int LINE_HEIGHT = 10;

  private SessionSummaryRenderer() {}

  public static void register() {
    ScreenEvents.AFTER_INIT.register(
        (client, screen, width, height) -> {
          if (screen instanceof PauseScreen
              && client.getConnection() != null
              && !client.isLocalServer()) {
            List<Component> lines = HitCounterSessionSummary.currentLines();
            if (lines != null && !lines.isEmpty()) {
              addSummaryWidgets(
                  screen, width, disconnectButtonBottom(screen, height), client.font, () -> lines);
            }
          } else if (screen instanceof DisconnectedScreen) {
            ServerHitSessionStore.cancelPendingTransfer();
            addSummaryWidgets(
                screen,
                width,
                lowestWidgetBottom(screen, height),
                client.font,
                HitCounterSessionSummary::lines);
          }
        });
  }

  private static int disconnectButtonBottom(
      net.minecraft.client.gui.screens.Screen screen, int fallback) {
    String disconnectLabel = Component.translatable("menu.disconnect").getString();
    return Screens.getButtons(screen).stream()
        .filter(widget -> widget.getMessage().getString().equals(disconnectLabel))
        .mapToInt(widget -> widget.getY() + widget.getHeight())
        .findFirst()
        .orElseGet(() -> lowestWidgetBottom(screen, fallback));
  }

  private static int lowestWidgetBottom(
      net.minecraft.client.gui.screens.Screen screen, int fallback) {
    return Screens.getButtons(screen).stream()
        .mapToInt(widget -> widget.getY() + widget.getHeight())
        .max()
        .orElse(fallback / 2);
  }

  private static void addSummaryWidgets(
      net.minecraft.client.gui.screens.Screen screen,
      int width,
      int bottom,
      net.minecraft.client.gui.Font font,
      Supplier<List<Component>> linesSupplier) {
    StringWidget[] widgets = new StringWidget[3];
    for (int index = 0; index < widgets.length; index++) {
      StringWidget widget =
          new StringWidget(
              width / 2,
              bottom + 8 + index * LINE_HEIGHT,
              width,
              LINE_HEIGHT,
              Component.empty(),
              font);
      Screens.getButtons(screen).add(widget);
      widgets[index] = widget;
    }
    ScreenEvents.afterTick(screen)
        .register(rendered -> updateWidgets(widgets, width, font, linesSupplier.get()));
    updateWidgets(widgets, width, font, linesSupplier.get());
  }

  private static void updateWidgets(
      StringWidget[] widgets,
      int width,
      net.minecraft.client.gui.Font font,
      List<Component> lines) {
    for (int index = 0; index < widgets.length; index++) {
      Component line = lines != null && index < lines.size() ? lines.get(index) : Component.empty();
      widgets[index].setMessage(line);
      widgets[index].setX(width / 2 - font.width(line) / 2);
    }
  }
}
