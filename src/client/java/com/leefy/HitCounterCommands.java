package com.leefy;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/** Client-side commands for inspecting and clearing the current session's balances. */
public final class HitCounterCommands {
  private HitCounterCommands() {}

  public static void register() {
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess) ->
            dispatcher.register(
                ClientCommandManager.literal("hitcounter")
                    .then(
                        ClientCommandManager.literal("reset")
                            .executes(context -> resetAll())
                            .then(
                                ClientCommandManager.argument("player", StringArgumentType.word())
                                    .executes(HitCounterCommands::resetPlayer)))
                    .then(
                        ClientCommandManager.literal("stats")
                            .executes(HitCounterCommands::showStats))));
  }

  private static int resetAll() {
    if (!HitCounterConfig.get().isEnabled()) {
      return 0;
    }

    return switch (HitBalanceTracker.resetOrUndo()) {
      case UNDONE -> {
        HitCounterToasts.showResetUndone();
        yield 1;
      }
      case RESET -> {
        HitCounterToasts.showResetAllWithUndo();
        yield 1;
      }
      case EMPTY -> 0;
    };
  }

  private static int resetPlayer(CommandContext<FabricClientCommandSource> context) {
    if (!HitCounterConfig.get().isEnabled()) {
      return 0;
    }

    String playerName = StringArgumentType.getString(context, "player");
    String matchedName = HitBalanceTracker.resetPlayer(playerName);
    if (matchedName == null) {
      context
          .getSource()
          .sendFeedback(
              Component.translatable("command.hit-counter.player_not_found", playerName)
                  .withStyle(ChatFormatting.RED));
      return 0;
    }

    HitCounterToasts.showResetPlayer(matchedName);
    return 1;
  }

  private static int showStats(CommandContext<FabricClientCommandSource> context) {
    if (!HitCounterConfig.get().isEnabled()) {
      return 0;
    }

    List<HitBalanceTracker.RegisteredBalance> balances = HitBalanceTracker.getRegisteredBalances();
    if (balances.isEmpty()) {
      context.getSource().sendFeedback(Component.translatable("command.hit-counter.stats.empty"));
      return 0;
    }

    context.getSource().sendFeedback(Component.translatable("command.hit-counter.stats.header"));
    for (HitBalanceTracker.RegisteredBalance balance : balances) {
      context
          .getSource()
          .sendFeedback(
              Component.literal(balance.playerName())
                  .withStyle(
                      style ->
                          style
                              .withClickEvent(
                                  new ClickEvent.RunCommand(
                                      "/hitcounter reset " + balance.playerName()))
                              .withHoverEvent(
                                  new net.minecraft.network.chat.HoverEvent.ShowText(
                                      Component.translatable(
                                          "command.hit-counter.stats.remove_hint"))))
                  .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
                  .append(HitBalanceTracker.formatBalance(balance.halfPoints())));
    }
    return balances.size();
  }
}
