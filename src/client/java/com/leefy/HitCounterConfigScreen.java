package com.leefy;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Builds the YACL screen exposed by Mod Menu. */
public final class HitCounterConfigScreen {
  private static Screen parentToCloseAfterOverrideEditor;

  private HitCounterConfigScreen() {}

  static void closeParentWhenOverrideEditorReturns(Screen parent) {
    parentToCloseAfterOverrideEditor = parent;
  }

  static void tickOverrideEditorReturn(net.minecraft.client.Minecraft client) {
    if (parentToCloseAfterOverrideEditor != null
        && client.screen == parentToCloseAfterOverrideEditor) {
      Screen parent = parentToCloseAfterOverrideEditor;
      parentToCloseAfterOverrideEditor = null;
      parent.onClose();
    }
  }

  public static Screen create(Screen parent) {
    return create(parent, null);
  }

  private static Screen create(Screen parent, String preferredServer) {
    HitCounterConfig config = HitCounterConfig.getDefault();
    boolean enabledBeforeOpening = config.isEnabled();
    Option<Boolean> autoResetOnDeathOption =
        Option.<Boolean>createBuilder()
            .name(Component.translatable("config.hit-counter.auto_reset_on_death"))
            .description(description("config.hit-counter.auto_reset_on_death.tooltip"))
            .binding(true, config::autoResetsOnDeath, config::setAutoResetOnDeath)
            .controller(TickBoxControllerBuilder::create)
            .build();
    Option<Boolean> spectatorFallbackOption =
        Option.<Boolean>createBuilder()
            .name(Component.translatable("config.hit-counter.auto_reset_on_spectator_mode"))
            .description(description("config.hit-counter.auto_reset_on_spectator_mode.tooltip"))
            .binding(true, config::autoResetsOnSpectatorMode, config::setAutoResetOnSpectatorMode)
            .controller(TickBoxControllerBuilder::create)
            .build();
    Option<HitCounterConfig.DeathResetMode> deathResetModeOption =
        Option.<HitCounterConfig.DeathResetMode>createBuilder()
            .name(Component.translatable("config.hit-counter.death_reset_mode"))
            .description(description("config.hit-counter.death_reset_mode.tooltip"))
            .binding(
                HitCounterConfig.DeathResetMode.EITHER_PLAYER,
                config::getDeathResetMode,
                config::setDeathResetMode)
            .available(config.autoResetsOnDeath() || config.autoResetsOnSpectatorMode())
            .controller(
                option ->
                    EnumControllerBuilder.create(option)
                        .enumClass(HitCounterConfig.DeathResetMode.class)
                        .formatValue(
                            value ->
                                Component.translatable(
                                    "config.hit-counter.death_reset_mode."
                                        + value.name().toLowerCase())))
            .build();
    Option<Integer> inactivityTimeoutOption =
        Option.<Integer>createBuilder()
            .name(Component.translatable("config.hit-counter.inactivity_timeout"))
            .description(description("config.hit-counter.inactivity_timeout.tooltip"))
            .binding(60, config::getInactivityTimeoutSeconds, config::setInactivityTimeoutSeconds)
            .available(config.autoResetsInactiveBalances())
            .controller(
                option ->
                    IntegerSliderControllerBuilder.create(option)
                        .range(10, 600)
                        .step(10)
                        .formatValue(HitCounterConfigScreen::formatInactivityTimeout))
            .build();
    Option<Boolean> autoResetInactiveOption =
        Option.<Boolean>createBuilder()
            .name(Component.translatable("config.hit-counter.auto_reset_inactive"))
            .description(description("config.hit-counter.auto_reset_inactive.tooltip"))
            .binding(
                false, config::autoResetsInactiveBalances, config::setAutoResetInactiveBalances)
            .controller(TickBoxControllerBuilder::create)
            .build();
    autoResetInactiveOption.addEventListener(
        (option, event) -> {
          if (event == OptionEventListener.Event.STATE_CHANGE) {
            inactivityTimeoutOption.setAvailable(option.pendingValue());
          }
        });
    autoResetOnDeathOption.addEventListener(
        (option, event) -> {
          if (event == OptionEventListener.Event.STATE_CHANGE) {
            deathResetModeOption.setAvailable(
                option.pendingValue() || spectatorFallbackOption.pendingValue());
          }
        });
    spectatorFallbackOption.addEventListener(
        (option, event) -> {
          if (event == OptionEventListener.Event.STATE_CHANGE) {
            deathResetModeOption.setAvailable(
                option.pendingValue() || autoResetOnDeathOption.pendingValue());
          }
        });
    List<ServerOverrideStore.SavedServer> savedServers = ServerOverrideStore.savedServers();
    Map<String, String> addressesByServerLabel = new LinkedHashMap<>();
    for (ServerOverrideStore.SavedServer server : savedServers) {
      addressesByServerLabel.put(server.displayName(), server.address());
    }
    List<String> serverLabels = new ArrayList<>(addressesByServerLabel.keySet());
    if (serverLabels.isEmpty()) serverLabels.add("No saved servers");
    String preferredServerLabel = "";
    if (preferredServer != null) {
      for (Map.Entry<String, String> entry : addressesByServerLabel.entrySet()) {
        if (preferredServer.equals(entry.getValue())) {
          preferredServerLabel = entry.getKey();
          break;
        }
      }
    }
    String[] selectedServer = {
      !preferredServerLabel.isEmpty()
          ? preferredServerLabel
          : (serverLabels.isEmpty() ? "" : serverLabels.getFirst())
    };
    Option<String> rawSavedServerOption =
        Option.<String>createBuilder()
            .name(Component.translatable("config.hit-counter.server_override.server"))
            .description(description("config.hit-counter.server_override.server.tooltip"))
            .binding(selectedServer[0], () -> selectedServer[0], value -> selectedServer[0] = value)
            .available(!savedServers.isEmpty())
            .controller(
                option ->
                    CyclingListControllerBuilder.create(option)
                        .values(serverLabels)
                        .formatValue(Component::literal))
                .build();
    Option<String> savedServerOption =
        FabricLoader.getInstance().isModLoaded("oneconfig")
            ? rawSavedServerOption
            : withoutDefaultReset(rawSavedServerOption);
    var addOverrideOption =
        ButtonOption.createBuilder()
            .name(Component.translatable("config.hit-counter.server_override.add"))
            .text(Component.translatable("config.hit-counter.server_override.add.button"))
            .available(!savedServers.isEmpty())
            .action(
                (screen, option) -> {
                  String address = addressesByServerLabel.get(savedServerOption.pendingValue());
                  if (address == null) return;
                  if (!ServerOverrideStore.hasOverride(address))
                    ServerOverrideStore.create(address, config);
                  closeParentWhenOverrideEditorReturns(screen);
                  Minecraft.getInstance().setScreen(createOverrideEditor(screen, address));
                })
            .build();
    var deleteOverrideOption =
        ButtonOption.createBuilder()
            .name(Component.literal("Delete selected override"))
            .text(Component.literal("Delete"))
            .available(!savedServers.isEmpty())
            .action(
                (screen, option) -> {
                  String address = addressesByServerLabel.get(savedServerOption.pendingValue());
                  if (address == null) return;
                  if (ServerOverrideStore.hasOverride(address)) ServerOverrideStore.remove(address);
                })
            .build();
    return YetAnotherConfigLib.createBuilder()
        .title(Component.translatable("config.hit-counter.title"))
        .category(
            ConfigCategory.createBuilder()
                .name(Component.translatable("config.hit-counter.category.general"))
                .group(OptionGroup.createBuilder()
                .name(Component.translatable("config.hit-counter.group.main"))
                .option(
                    Option.<Boolean>createBuilder()
                        .name(Component.translatable("config.hit-counter.enabled"))
                        .description(description("config.hit-counter.enabled.tooltip"))
                        .binding(true, config::isEnabled, config::setEnabled)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(balancePositionOption(config))
                .option(
                    Option.<Boolean>createBuilder()
                        .name(Component.translatable("config.hit-counter.show_zero_balances"))
                        .description(description("config.hit-counter.show_zero_balances.tooltip"))
                        .binding(true, config::showsZeroBalances, config::setShowZeroBalances)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .option(
                    hitValueOption(
                        "config.hit-counter.critical_hit_value",
                        "config.hit-counter.critical_hit_value.tooltip",
                        config::getCriticalHitValue,
                        config::setCriticalHitValue,
                        1.5D))
                .option(
                    hitValueOption(
                        "config.hit-counter.normal_hit_value",
                        "config.hit-counter.normal_hit_value.tooltip",
                        config::getNormalHitValue,
                        config::setNormalHitValue,
                        1.0D))
                .option(
                    hitValueOption(
                        "config.hit-counter.weak_hit_value",
                        "config.hit-counter.weak_hit_value.tooltip",
                        config::getWeakHitValue,
                        config::setWeakHitValue,
                        0.5D,
                        0.5D))
                .option(
                    Option.<Boolean>createBuilder()
                        .name(Component.translatable("config.hit-counter.flashback_replays"))
                        .description(description("config.hit-counter.flashback_replays.tooltip"))
                        .binding(
                            true,
                            config::rendersHitBalancesInFlashbackReplays,
                            config::setRenderHitBalancesInFlashbackReplays)
                        .controller(TickBoxControllerBuilder::create)
                        .available(FabricLoader.getInstance().isModLoaded("flashback"))
                        .build())
                .build())
                .group(OptionGroup.createBuilder()
                .name(Component.translatable("config.hit-counter.group.auto_resets"))
                .option(autoResetOnDeathOption)
                .option(spectatorFallbackOption)
                .option(deathResetModeOption)
                .option(autoResetInactiveOption)
                .option(inactivityTimeoutOption)
                .build())
                .build())
        .category(
            ConfigCategory.createBuilder()
                .name(Component.translatable("config.hit-counter.category.colors"))
                .option(
                    colorOption(
                        "config.hit-counter.positive_color",
                        config::getPositiveColor,
                        config::setPositiveColor,
                        HitCounterConfig.DEFAULT_POSITIVE_RGB))
                .option(
                    colorOption(
                        "config.hit-counter.negative_color",
                        config::getNegativeColor,
                        config::setNegativeColor,
                        HitCounterConfig.DEFAULT_NEGATIVE_RGB))
                .option(
                    colorOption(
                        "config.hit-counter.zero_color",
                        config::getZeroColor,
                        config::setZeroColor,
                        HitCounterConfig.DEFAULT_ZERO_RGB))
                .build())
        .category(
            ConfigCategory.createBuilder()
                .name(Component.translatable("config.hit-counter.category.hit_sources"))
                .option(
                    Option.<HitCounterConfig.HitSourceFilterMode>createBuilder()
                        .name(Component.translatable("config.hit-counter.hit_source_filter_mode"))
                        .description(
                            description("config.hit-counter.hit_source_filter_mode.tooltip"))
                        .binding(
                            HitCounterConfig.HitSourceFilterMode.BLACKLIST,
                            config::getHitSourceFilterMode,
                            config::setHitSourceFilterMode)
                        .controller(
                            option ->
                                EnumControllerBuilder.create(option)
                                    .enumClass(HitCounterConfig.HitSourceFilterMode.class)
                                    .formatValue(
                                        value ->
                                            Component.translatable(
                                                "config.hit-counter.hit_source_filter_mode."
                                                    + value.name().toLowerCase())))
                        .build())
                .option(
                    Option.<Boolean>createBuilder()
                        .name(
                            Component.translatable("config.hit-counter.count_unknown_hit_sources"))
                        .description(
                            description("config.hit-counter.count_unknown_hit_sources.tooltip"))
                        .binding(
                            true,
                            config::countsUnknownHitSources,
                            config::setCountUnknownHitSources)
                        .controller(TickBoxControllerBuilder::create)
                        .build())
                .group(
                    hitSourceGroup(
                        config, "melee", List.of("player_attack", "mace_smash", "spear")))
                .group(
                    hitSourceGroup(
                        config,
                        "projectiles",
                        List.of("arrow", "trident", "wind_charge", "fireball", "fireworks")))
                .group(
                    hitSourceGroup(
                        config,
                        "special_effects",
                        List.of("instant_damage_potion", "server_magic_and_abilities", "thorns")))
                .group(
                    hitSourceGroup(config, "explosions", List.of("player_explosion", "explosion")))
                .build())
        .category(
            ConfigCategory.createBuilder()
                .name(Component.translatable("config.hit-counter.category.server_overrides"))
                .option(savedServerOption)
                .option(addOverrideOption)
                .option(deleteOverrideOption)
                .build())
        .save(
            () -> {
              config.save();
              ServerOverrideStore.refreshActiveDefaultProfile();
              HitBalanceTracker.onConfigurationSaved(enabledBeforeOpening);
            })
        .build()
        .generateScreen(parent);
  }

  /**
   * A separate editor makes it explicit that these settings belong to one saved server, never
   * General.
   */
  private static Screen createOverrideEditor(Screen parent, String address) {
    HitCounterConfig config = ServerOverrideStore.profileCopy(address);
    if (config == null) return create(parent);
    return YetAnotherConfigLib.createBuilder()
        .title(Component.literal("Server override: " + address))
        .category(
            ConfigCategory.createBuilder()
                .name(Component.literal("Override settings — " + address))
                .group(OptionGroup.createBuilder()
                .name(Component.translatable("config.hit-counter.group.main"))
                .option(
                    toggle(
                        "config.hit-counter.enabled",
                        "config.hit-counter.enabled.tooltip",
                        config::isEnabled,
                        config::setEnabled,
                        true))
                .option(balancePositionOption(config))
                .option(
                    toggle(
                        "config.hit-counter.show_zero_balances",
                        "config.hit-counter.show_zero_balances.tooltip",
                        config::showsZeroBalances,
                        config::setShowZeroBalances,
                        true))
                .option(
                    hitValueOption(
                        "config.hit-counter.critical_hit_value",
                        "config.hit-counter.critical_hit_value.tooltip",
                        config::getCriticalHitValue,
                        config::setCriticalHitValue,
                        1.5D))
                .option(
                    hitValueOption(
                        "config.hit-counter.normal_hit_value",
                        "config.hit-counter.normal_hit_value.tooltip",
                        config::getNormalHitValue,
                        config::setNormalHitValue,
                        1.0D))
                .option(
                    hitValueOption(
                        "config.hit-counter.weak_hit_value",
                        "config.hit-counter.weak_hit_value.tooltip",
                        config::getWeakHitValue,
                        config::setWeakHitValue,
                        0.5D,
                        0.5D))
                .build())
                .group(OptionGroup.createBuilder()
                .name(Component.translatable("config.hit-counter.group.auto_resets"))
                .option(
                    toggle(
                        "config.hit-counter.auto_reset_on_death",
                        "config.hit-counter.auto_reset_on_death.tooltip",
                        config::autoResetsOnDeath,
                        config::setAutoResetOnDeath,
                        true))
                .option(
                    toggle(
                        "config.hit-counter.auto_reset_on_spectator_mode",
                        "config.hit-counter.auto_reset_on_spectator_mode.tooltip",
                        config::autoResetsOnSpectatorMode,
                        config::setAutoResetOnSpectatorMode,
                        true))
                .option(
                    Option.<HitCounterConfig.DeathResetMode>createBuilder()
                        .name(Component.translatable("config.hit-counter.death_reset_mode"))
                        .description(description("config.hit-counter.death_reset_mode.tooltip"))
                        .binding(
                            HitCounterConfig.DeathResetMode.EITHER_PLAYER,
                            config::getDeathResetMode,
                            config::setDeathResetMode)
                        .controller(
                            option ->
                                EnumControllerBuilder.create(option)
                                    .enumClass(HitCounterConfig.DeathResetMode.class)
                                    .formatValue(
                                        value ->
                                            Component.translatable(
                                                "config.hit-counter.death_reset_mode."
                                                    + value.name().toLowerCase())))
                        .build())
                .option(
                    toggle(
                        "config.hit-counter.auto_reset_inactive",
                        "config.hit-counter.auto_reset_inactive.tooltip",
                        config::autoResetsInactiveBalances,
                        config::setAutoResetInactiveBalances,
                        false))
                .option(
                    Option.<Integer>createBuilder()
                        .name(Component.translatable("config.hit-counter.inactivity_timeout"))
                        .description(description("config.hit-counter.inactivity_timeout.tooltip"))
                        .binding(
                            60,
                            config::getInactivityTimeoutSeconds,
                            config::setInactivityTimeoutSeconds)
                        .controller(
                            option ->
                                IntegerSliderControllerBuilder.create(option)
                                    .range(10, 600)
                                    .step(10)
                                    .formatValue(HitCounterConfigScreen::formatInactivityTimeout))
                        .build())
                .build())
                .build())
        .category(
            ConfigCategory.createBuilder()
                .name(Component.literal("Override colors — " + address))
                .option(
                    colorOption(
                        "config.hit-counter.positive_color",
                        config::getPositiveColor,
                        config::setPositiveColor,
                        HitCounterConfig.DEFAULT_POSITIVE_RGB))
                .option(
                    colorOption(
                        "config.hit-counter.negative_color",
                        config::getNegativeColor,
                        config::setNegativeColor,
                        HitCounterConfig.DEFAULT_NEGATIVE_RGB))
                .option(
                    colorOption(
                        "config.hit-counter.zero_color",
                        config::getZeroColor,
                        config::setZeroColor,
                        HitCounterConfig.DEFAULT_ZERO_RGB))
                .build())
        .category(
            ConfigCategory.createBuilder()
                .name(Component.literal("Override hit sources — " + address))
                .option(
                    Option.<HitCounterConfig.HitSourceFilterMode>createBuilder()
                        .name(Component.translatable("config.hit-counter.hit_source_filter_mode"))
                        .description(
                            description("config.hit-counter.hit_source_filter_mode.tooltip"))
                        .binding(
                            HitCounterConfig.HitSourceFilterMode.BLACKLIST,
                            config::getHitSourceFilterMode,
                            config::setHitSourceFilterMode)
                        .controller(
                            option ->
                                EnumControllerBuilder.create(option)
                                    .enumClass(HitCounterConfig.HitSourceFilterMode.class)
                                    .formatValue(
                                        value ->
                                            Component.translatable(
                                                "config.hit-counter.hit_source_filter_mode."
                                                    + value.name().toLowerCase())))
                        .build())
                .option(
                    toggle(
                        "config.hit-counter.count_unknown_hit_sources",
                        "config.hit-counter.count_unknown_hit_sources.tooltip",
                        config::countsUnknownHitSources,
                        config::setCountUnknownHitSources,
                        true))
                .group(
                    hitSourceGroup(
                        config, "melee", List.of("player_attack", "mace_smash", "spear")))
                .group(
                    hitSourceGroup(
                        config,
                        "projectiles",
                        List.of("arrow", "trident", "wind_charge", "fireball", "fireworks")))
                .group(
                    hitSourceGroup(
                        config,
                        "special_effects",
                        List.of("instant_damage_potion", "server_magic_and_abilities", "thorns")))
                .group(
                    hitSourceGroup(config, "explosions", List.of("player_explosion", "explosion")))
                .build())
        .save(
            () -> {
              ServerOverrideStore.saveProfile(address, config);
              parentToCloseAfterOverrideEditor = null;
              Minecraft.getInstance()
                  .execute(
                      () -> {
                        Minecraft.getInstance().setScreen(parent);
                        parent.onClose();
                      });
            })
        .build()
        .generateScreen(parent);
  }

  private static <T> Option<T> withoutDefaultReset(Option<T> option) {
    return new Option<>() {
      @Override
      public Component name() {
        return option.name();
      }

      @Override
      public OptionDescription description() {
        return option.description();
      }

      @Override
      @Deprecated
      public Component tooltip() {
        return option.tooltip();
      }

      @Override
      public dev.isxander.yacl3.api.Controller<T> controller() {
        return option.controller();
      }

      @Override
      public dev.isxander.yacl3.api.StateManager<T> stateManager() {
        return option.stateManager();
      }

      @Override
      @Deprecated
      public dev.isxander.yacl3.api.Binding<T> binding() {
        return option.binding();
      }

      @Override
      public boolean available() {
        return option.available();
      }

      @Override
      public void setAvailable(boolean available) {
        option.setAvailable(available);
      }

      @Override
      public com.google.common.collect.ImmutableSet<dev.isxander.yacl3.api.OptionFlag> flags() {
        return option.flags();
      }

      @Override
      public boolean changed() {
        return option.changed();
      }

      @Override
      public T pendingValue() {
        return option.pendingValue();
      }

      @Override
      public void requestSet(T value) {
        option.requestSet(value);
      }

      @Override
      public boolean applyValue() {
        return option.applyValue();
      }

      @Override
      public void forgetPendingValue() {
        option.forgetPendingValue();
      }

      @Override
      public void requestSetDefault() {
        option.requestSetDefault();
      }

      @Override
      public boolean isPendingValueDefault() {
        return option.isPendingValueDefault();
      }

      @Override
      public boolean canResetToDefault() {
        return false;
      }

      @Override
      public void addEventListener(OptionEventListener<T> listener) {
        option.addEventListener(listener);
      }

      @Override
      @Deprecated
      public void addListener(java.util.function.BiConsumer<Option<T>, T> listener) {
        option.addListener(listener);
      }
    };
  }

  private static Option<HitCounterConfig.BalancePosition> balancePositionOption(
      HitCounterConfig config) {
    return Option.<HitCounterConfig.BalancePosition>createBuilder()
        .name(Component.translatable("config.hit-counter.balance_position"))
        .description(description("config.hit-counter.balance_position.tooltip"))
        .binding(
            HitCounterConfig.BalancePosition.RIGHT,
            config::getBalancePosition,
            config::setBalancePosition)
        .controller(
            option ->
                EnumControllerBuilder.create(option)
                    .enumClass(HitCounterConfig.BalancePosition.class)
                    .formatValue(
                        value ->
                            Component.translatable(
                                "config.hit-counter.balance_position."
                                    + value.name().toLowerCase())))
        .build();
  }

  private static Option<Boolean> toggle(
      String name,
      String tooltip,
      java.util.function.Supplier<Boolean> getter,
      java.util.function.Consumer<Boolean> setter,
      boolean defaultValue) {
    return Option.<Boolean>createBuilder()
        .name(Component.translatable(name))
        .description(description(tooltip))
        .binding(defaultValue, getter, setter)
        .controller(TickBoxControllerBuilder::create)
        .build();
  }

  private static Option<Color> colorOption(
      String translationKey,
      java.util.function.Supplier<Color> getter,
      java.util.function.Consumer<Color> setter,
      int defaultRgb) {
    return Option.<Color>createBuilder()
        .name(Component.translatable(translationKey))
        .description(description("config.hit-counter.color.tooltip"))
        .binding(new Color(defaultRgb), getter, setter)
        .controller(ColorControllerBuilder::create)
        .build();
  }

  private static Option<Boolean> hitSourceOption(HitCounterConfig config, String sourceId) {
    return Option.<Boolean>createBuilder()
        .name(Component.translatable("config.hit-counter.hit_source." + sourceId))
        .description(description("config.hit-counter.hit_source." + sourceId + ".tooltip"))
        .binding(
            defaultHitSourceSelection(sourceId),
            () -> config.isHitSourceSelected(sourceId),
            selected -> config.setHitSourceSelected(sourceId, selected))
        .controller(TickBoxControllerBuilder::create)
        .build();
  }

  private static OptionGroup hitSourceGroup(
      HitCounterConfig config, String groupId, List<String> sourceIds) {
    List<Option<Boolean>> sourceOptions =
        sourceIds.stream().map(sourceId -> hitSourceOption(config, sourceId)).toList();
    boolean[] synchronizing = {false};
    Option<Boolean> allSourcesOption =
        Option.<Boolean>createBuilder()
            .name(Component.translatable("config.hit-counter.hit_source_group." + groupId + ".all"))
            .description(
                description("config.hit-counter.hit_source_group." + groupId + ".all.tooltip"))
            .binding(
                false,
                () -> config.areAllHitSourcesSelected(sourceIds),
                selected -> config.setHitSourcesSelected(sourceIds, selected))
            .controller(TickBoxControllerBuilder::create)
            .build();
    allSourcesOption.addEventListener(
        (option, event) -> {
          if (event != OptionEventListener.Event.STATE_CHANGE) {
            return;
          }
          if (synchronizing[0]) {
            return;
          }
          synchronizing[0] = true;
          try {
            for (Option<Boolean> sourceOption : sourceOptions) {
              sourceOption.requestSet(option.pendingValue());
            }
          } finally {
            synchronizing[0] = false;
          }
        });
    for (Option<Boolean> sourceOption : sourceOptions) {
      sourceOption.addEventListener(
          (option, event) -> {
            if (event != OptionEventListener.Event.STATE_CHANGE) {
              return;
            }
            if (synchronizing[0]) {
              return;
            }
            synchronizing[0] = true;
            try {
              allSourcesOption.requestSet(sourceOptions.stream().allMatch(Option::pendingValue));
            } finally {
              synchronizing[0] = false;
            }
          });
    }
    var builder =
        OptionGroup.createBuilder()
            .name(Component.translatable("config.hit-counter.hit_source_group." + groupId))
            .collapsed(true)
            .option(allSourcesOption);
    for (Option<Boolean> sourceOption : sourceOptions) {
      builder.option(sourceOption);
    }
    return builder.build();
  }

  private static boolean defaultHitSourceSelection(String sourceId) {
    return sourceId.equals("wind_charge") || sourceId.equals("thorns");
  }

  private static Option<Double> hitValueOption(
      String translationKey,
      String tooltipKey,
      java.util.function.Supplier<Double> getter,
      java.util.function.Consumer<Double> setter,
      double defaultValue) {
    return hitValueOption(translationKey, tooltipKey, getter, setter, defaultValue, 1.0D);
  }

  private static Component formatInactivityTimeout(int seconds) {
    if (seconds < 60) {
      return Component.translatable("config.hit-counter.inactivity_timeout.seconds", seconds);
    }
    int minutes = seconds / 60;
    int remainingSeconds = seconds % 60;
    if (remainingSeconds != 0) {
      return Component.translatable(
          "config.hit-counter.inactivity_timeout.minutes_seconds", minutes, remainingSeconds);
    }
    return Component.translatable(
        minutes == 1
            ? "config.hit-counter.inactivity_timeout.minute"
            : "config.hit-counter.inactivity_timeout.minutes",
        minutes);
  }

  private static Option<Double> hitValueOption(
      String translationKey,
      String tooltipKey,
      java.util.function.Supplier<Double> getter,
      java.util.function.Consumer<Double> setter,
      double defaultValue,
      double minimum) {
    return Option.<Double>createBuilder()
        .name(Component.translatable(translationKey))
        .description(description(tooltipKey))
        .binding(defaultValue, getter, setter)
        .controller(
            option ->
                DoubleSliderControllerBuilder.create(option)
                    .range(0.0D, 5.0D)
                    .step(0.1D)
                    .formatValue(value -> Component.literal(String.format("%.1f", value))))
        .build();
  }

  private static OptionDescription description(String translationKey) {
    return OptionDescription.of(Component.translatable(translationKey));
  }
}
