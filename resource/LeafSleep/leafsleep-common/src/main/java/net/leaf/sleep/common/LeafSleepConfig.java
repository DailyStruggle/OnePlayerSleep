package net.leaf.sleep.common;

import io.github.dailystruggle.bit.BitFinding;
import io.github.dailystruggle.rtp.common.bit.config.ConfigSnapshot;
import io.github.dailystruggle.rtp.common.bit.config.DirectiveValidator;
import io.github.dailystruggle.rtp.common.configuration.ConfigParser;
import io.github.dailystruggle.rtp.common.configuration.MultiConfigParser;
import io.github.dailystruggle.rtp.common.configuration.yaml.RtpYamlConfig;
import io.github.dailystruggle.rtp.common.configuration.yaml.RtpYamlSection;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Immutable parsed configuration model for {@code LeafSleep} covering {@code config.yml}, {@code
 * player.yml}, {@code commands.yml}, {@code sleep.yml}, {@code network.yml}, and {@code
 * messages/*.yml} loaded via {@link MultiConfigParser} ({@code LeafSleep ADR-001}, {@code
 * ADR-002}).
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 */
public final class LeafSleepConfig {

  public static final List<String> CONFIG_FILES =
      List.of("config.yml", "player.yml", "commands.yml", "sleep.yml", "network.yml");

  public static final List<String> BUNDLED_MESSAGE_THEMES =
      List.of(
          "default",
          "cozy",
          "starlight",
          "campfire",
          "phantom",
          "nightmare",
          "rooster",
          "lazy");

  /** Fast transient sync transport mode ({@code ADR-001}). */
  public enum TransportType {
    LOCAL_ONLY,
    DIRECT_SOCKET,
    PLUGIN_MESSAGING,
    REDIS_PUBSUB;

    public static TransportType fromString(String raw) {
      if (raw == null || raw.isBlank()) return LOCAL_ONLY;
      String norm = raw.trim().replace('-', '_').replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
      try {
        return TransportType.valueOf(norm);
      } catch (IllegalArgumentException ex) {
        return LOCAL_ONLY;
      }
    }
  }

  /** Durable SQL dialect ({@code ADR-001}). */
  public enum StorageType {
    SQLITE,
    MYSQL,
    MARIADB,
    POSTGRESQL;

    public static StorageType fromString(String raw) {
      if (raw == null || raw.isBlank()) return SQLITE;
      String norm = raw.trim().replace('-', '_').replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
      try {
        return StorageType.valueOf(norm);
      } catch (IllegalArgumentException ex) {
        return SQLITE;
      }
    }
  }

  /** Sharding and direct sync bus configuration block. */
  public record ShardingConfig(
      boolean enabled,
      TransportType transport,
      String channel,
      String bindHost,
      int bindPort,
      List<String> peers,
      String hmacSecretEnv,
      int heartbeatIntervalTicks) {

    public ShardingConfig {
      transport = transport == null ? TransportType.LOCAL_ONLY : transport;
      channel = (channel == null || channel.isBlank()) ? "leafsleep:sync" : channel;
      bindHost = (bindHost == null || bindHost.isBlank()) ? "127.0.0.1" : bindHost;
      bindPort = bindPort <= 0 ? 25620 : bindPort;
      peers = peers == null ? List.of() : List.copyOf(peers);
      hmacSecretEnv =
          (hmacSecretEnv == null || hmacSecretEnv.isBlank())
              ? "LEAFSLEEP_HMAC_SECRET"
              : hmacSecretEnv;
      heartbeatIntervalTicks = Math.max(20, heartbeatIntervalTicks);
    }
  }

  /** Durable async SQL storage configuration block. */
  public record StorageConfig(
      StorageType type,
      String sqliteFile,
      String host,
      int port,
      String database,
      String username,
      String passwordEnv,
      String tablePrefix) {

    public StorageConfig {
      type = type == null ? StorageType.SQLITE : type;
      sqliteFile = (sqliteFile == null || sqliteFile.isBlank()) ? "leafsleep.db" : sqliteFile;
      host = (host == null || host.isBlank()) ? "127.0.0.1" : host;
      port = port <= 0 ? 3306 : port;
      database = (database == null || database.isBlank()) ? "leafsleep" : database;
      username = username == null ? "root" : username;
      passwordEnv =
          (passwordEnv == null || passwordEnv.isBlank()) ? "LEAFSLEEP_DB_PASSWORD" : passwordEnv;
      tablePrefix = (tablePrefix == null || tablePrefix.isBlank()) ? "leafsleep_" : tablePrefix;
    }
  }

  /** Weighted random sleep/wakeup message definition from {@code sleep.yml}. */
  public record SleepMessageEntry(
      String id,
      int weight,
      String sleepText,
      String wakeupText,
      String cancelPrompt,
      String sleepPrompt) {

    public SleepMessageEntry {
      id = id == null ? "default" : id;
      weight = Math.max(1, weight);
      sleepText =
          sleepText == null
              ? "[player] is sleeping ([sleeping]/[required] in [shardGroup])."
              : sleepText;
      wakeupText = wakeupText == null ? "Good morning, [world]!" : wakeupText;
      cancelPrompt = cancelPrompt == null ? "[Cancel]" : cancelPrompt;
      sleepPrompt = sleepPrompt == null ? "[Sleep]" : sleepPrompt;
    }
  }

  /** Per-world shard group and quorum configuration from {@code sleep.yml}. */
  public record WorldSleepConfig(
      String worldName,
      boolean enabled,
      String shardGroupId,
      QuorumPolicy policy,
      int quorumValue,
      QuorumPolicy.CancelPolicy cancelPolicy,
      QuorumPolicy.CurveConfig sleepCurve,
      QuorumPolicy.CurveConfig cancelCurve,
      int warmupDelayTicks,
      boolean warmupActionBar,
      boolean warmupBossBar,
      int increment,
      int timeBetweenIncrements) {

    public WorldSleepConfig {
      worldName = worldName == null ? "world" : worldName;
      shardGroupId = (shardGroupId == null || shardGroupId.isBlank()) ? worldName : shardGroupId;
      policy = policy == null ? QuorumPolicy.ONE_PLAYER : policy;
      quorumValue = Math.max(1, quorumValue);
      cancelPolicy =
          cancelPolicy == null ? QuorumPolicy.CancelPolicy.TUG_OF_WAR : cancelPolicy;
      sleepCurve =
          sleepCurve == null ? QuorumPolicy.CurveConfig.DEFAULT_SLEEP : sleepCurve;
      cancelCurve =
          cancelCurve == null ? QuorumPolicy.CurveConfig.DEFAULT_CANCEL : cancelCurve;
      warmupDelayTicks = Math.max(0, warmupDelayTicks);
      increment = Math.max(0, Math.min(24000, increment));
      timeBetweenIncrements = Math.max(1, timeBetweenIncrements);
    }

    /** Convenience constructor using default warmupActionBar/warmupBossBar = true. */
    public WorldSleepConfig(
        String worldName,
        boolean enabled,
        String shardGroupId,
        QuorumPolicy policy,
        int quorumValue,
        QuorumPolicy.CancelPolicy cancelPolicy,
        QuorumPolicy.CurveConfig sleepCurve,
        QuorumPolicy.CurveConfig cancelCurve,
        int warmupDelayTicks,
        int increment,
        int timeBetweenIncrements) {
      this(
          worldName,
          enabled,
          shardGroupId,
          policy,
          quorumValue,
          cancelPolicy,
          sleepCurve,
          cancelCurve,
          warmupDelayTicks,
          true,
          true,
          increment,
          timeBetweenIncrements);
    }

    /** Convenience constructor using instant time-skip (increment = 0). */
    public WorldSleepConfig(
        String worldName,
        boolean enabled,
        String shardGroupId,
        QuorumPolicy policy,
        int quorumValue,
        QuorumPolicy.CancelPolicy cancelPolicy,
        QuorumPolicy.CurveConfig sleepCurve,
        QuorumPolicy.CurveConfig cancelCurve,
        int warmupDelayTicks) {
      this(
          worldName,
          enabled,
          shardGroupId,
          policy,
          quorumValue,
          cancelPolicy,
          sleepCurve,
          cancelCurve,
          warmupDelayTicks,
          true,
          true,
          0,
          2);
    }

    /** Convenience constructor using default cancel policy and curves. */
    public WorldSleepConfig(
        String worldName,
        boolean enabled,
        String shardGroupId,
        QuorumPolicy policy,
        int quorumValue,
        int warmupDelayTicks) {
      this(
          worldName,
          enabled,
          shardGroupId,
          policy,
          quorumValue,
          policy == QuorumPolicy.CURVE
              ? QuorumPolicy.CancelPolicy.TUG_OF_WAR
              : QuorumPolicy.CancelPolicy.VETO,
          QuorumPolicy.CurveConfig.DEFAULT_SLEEP,
          QuorumPolicy.CurveConfig.DEFAULT_CANCEL,
          warmupDelayTicks,
          true,
          true,
          0,
          2);
    }
  }

  /** User-facing localized message templates from {@code messages.yml} (S-007). */
  public record MessagesConfig(
      String prefix,
      String sleepBroadcast,
      String wakeupBroadcast,
      String cancelBroadcast,
      String cancelVoteBroadcast,
      String cannotCancel,
      String alreadySleeping,
      String notNight,
      String onCooldown,
      String ignoreEnabled,
      String ignoreDisabled,
      String reloadSuccess,
      String reloadFailed,
      String noPermission,
      String invalidCommand,
      String badParameter,
      String busy,
      String statusHeader,
      String statusWorldLine,
      String statusTransportLine,
      String clickCancelLabel,
      String clickCancelHover,
      String clickSleepLabel,
      String clickSleepHover,
      String warmupActionBar,
      String warmupBossBar,
      String messagePrefCurrent,
      String messagePrefUpdated) {

    public MessagesConfig {
      cancelVoteBroadcast =
          (cancelVoteBroadcast == null || cancelVoteBroadcast.isBlank())
              ? "[player] wants night (Cancelling: [cancellingPlayers]) - [requirements] [cancelPrompt] [sleepPrompt]"
              : cancelVoteBroadcast;
      warmupActionBar =
          warmupActionBar == null
              ? "&eSkipping night in &f[seconds]s &7([sleeping]/[required] sleeping) - &c/sleep cancel"
              : warmupActionBar;
      warmupBossBar =
          warmupBossBar == null
              ? "&aSkipping night in [seconds]s ([sleeping]/[required] sleeping)"
              : warmupBossBar;
      messagePrefCurrent =
          (messagePrefCurrent == null || messagePrefCurrent.isBlank())
              ? "Current sleep message theme: [message] (Available: [available])."
              : messagePrefCurrent;
      messagePrefUpdated =
          (messagePrefUpdated == null || messagePrefUpdated.isBlank())
              ? "Preferred sleep message theme set to '[message]'."
              : messagePrefUpdated;
    }

    public MessagesConfig(
        String prefix,
        String sleepBroadcast,
        String wakeupBroadcast,
        String cancelBroadcast,
        String cancelVoteBroadcast,
        String cannotCancel,
        String alreadySleeping,
        String notNight,
        String onCooldown,
        String ignoreEnabled,
        String ignoreDisabled,
        String reloadSuccess,
        String reloadFailed,
        String noPermission,
        String invalidCommand,
        String badParameter,
        String busy,
        String statusHeader,
        String statusWorldLine,
        String statusTransportLine,
        String clickCancelLabel,
        String clickCancelHover,
        String clickSleepLabel,
        String clickSleepHover,
        String warmupActionBar,
        String warmupBossBar) {
      this(
          prefix,
          sleepBroadcast,
          wakeupBroadcast,
          cancelBroadcast,
          cancelVoteBroadcast,
          cannotCancel,
          alreadySleeping,
          notNight,
          onCooldown,
          ignoreEnabled,
          ignoreDisabled,
          reloadSuccess,
          reloadFailed,
          noPermission,
          invalidCommand,
          badParameter,
          busy,
          statusHeader,
          statusWorldLine,
          statusTransportLine,
          clickCancelLabel,
          clickCancelHover,
          clickSleepLabel,
          clickSleepHover,
          warmupActionBar,
          warmupBossBar,
          "Current sleep message theme: [message] (Available: [available]).",
          "Preferred sleep message theme set to '[message]'.");
    }

    public MessagesConfig(
        String prefix,
        String sleepBroadcast,
        String wakeupBroadcast,
        String cancelBroadcast,
        String cancelVoteBroadcast,
        String cannotCancel,
        String alreadySleeping,
        String notNight,
        String onCooldown,
        String ignoreEnabled,
        String ignoreDisabled,
        String reloadSuccess,
        String reloadFailed,
        String noPermission,
        String invalidCommand,
        String badParameter,
        String busy,
        String statusHeader,
        String statusWorldLine,
        String statusTransportLine,
        String clickCancelLabel,
        String clickCancelHover,
        String clickSleepLabel,
        String clickSleepHover) {
      this(
          prefix,
          sleepBroadcast,
          wakeupBroadcast,
          cancelBroadcast,
          cancelVoteBroadcast,
          cannotCancel,
          alreadySleeping,
          notNight,
          onCooldown,
          ignoreEnabled,
          ignoreDisabled,
          reloadSuccess,
          reloadFailed,
          noPermission,
          invalidCommand,
          badParameter,
          busy,
          statusHeader,
          statusWorldLine,
          statusTransportLine,
          clickCancelLabel,
          clickCancelHover,
          clickSleepLabel,
          clickSleepHover,
          "&eSkipping night in &f[seconds]s &7([sleeping]/[required] sleeping) - &c/sleep cancel",
          "&aSkipping night in [seconds]s ([sleeping]/[required] sleeping)",
          "Current sleep message theme: [message] (Available: [available]).",
          "Preferred sleep message theme set to '[message]'.");
    }

    public MessagesConfig(
        String prefix,
        String sleepBroadcast,
        String wakeupBroadcast,
        String cancelBroadcast,
        String cannotCancel,
        String alreadySleeping,
        String notNight,
        String onCooldown,
        String ignoreEnabled,
        String ignoreDisabled,
        String reloadSuccess,
        String reloadFailed,
        String noPermission,
        String invalidCommand,
        String badParameter,
        String busy,
        String statusHeader,
        String statusWorldLine,
        String statusTransportLine,
        String clickCancelLabel,
        String clickCancelHover,
        String clickSleepLabel,
        String clickSleepHover) {
      this(
          prefix,
          sleepBroadcast,
          wakeupBroadcast,
          cancelBroadcast,
          "[player] wants night (Cancelling: [cancellingPlayers]) - [requirements] [cancelPrompt] [sleepPrompt]",
          cannotCancel,
          alreadySleeping,
          notNight,
          onCooldown,
          ignoreEnabled,
          ignoreDisabled,
          reloadSuccess,
          reloadFailed,
          noPermission,
          invalidCommand,
          badParameter,
          busy,
          statusHeader,
          statusWorldLine,
          statusTransportLine,
          clickCancelLabel,
          clickCancelHover,
          clickSleepLabel,
          clickSleepHover,
          "&eSkipping night in &f[seconds]s &7([sleeping]/[required] sleeping) - &c/sleep cancel",
          "&aSkipping night in [seconds]s ([sleeping]/[required] sleeping)",
          "Current sleep message theme: [message] (Available: [available]).",
          "Preferred sleep message theme set to '[message]'.");
    }
  }

  private final String serverShardId;
  private final QuorumPolicy defaultQuorumPolicy;
  private final int defaultQuorumValue;
  private final QuorumPolicy.CancelPolicy defaultCancelPolicy;
  private final QuorumPolicy.CurveConfig sleepCurve;
  private final QuorumPolicy.CurveConfig cancelCurve;
  private final int warmupDelayTicks;
  private final boolean warmupActionBar;
  private final boolean warmupBossBar;
  private final int increment;
  private final int timeBetweenIncrements;
  private final int sleepCooldownSeconds;
  private final int cancelCooldownSeconds;
  private final boolean wakeOnCancel;
  private final boolean clearWeatherOnSkip;
  private final boolean clearThunderOnSkip;
  private final boolean resetPhantomStatistic;
  private final boolean excludeAfkAndVanished;
  private final boolean excludeCreativeAndSpectator;
  private final long targetDawnTimeTicks;
  private final long nightStartTicks;
  private final long nightEndTicks;
  private final boolean allowSleepDuringThunder;
  private final int budgetMaxFramesPerTick;
  private final ShardingConfig sharding;
  private final StorageConfig storage;
  private final MessagesConfig messages;
  private final List<String> wakeupEffects;
  private final List<SleepMessageEntry> sleepMessages;
  private final Map<String, WorldSleepConfig> worlds;

  public LeafSleepConfig(
      String serverShardId,
      QuorumPolicy defaultQuorumPolicy,
      int defaultQuorumValue,
      int warmupDelayTicks,
      int sleepCooldownSeconds,
      int cancelCooldownSeconds,
      boolean wakeOnCancel,
      boolean clearWeatherOnSkip,
      boolean clearThunderOnSkip,
      boolean resetPhantomStatistic,
      boolean excludeAfkAndVanished,
      boolean excludeCreativeAndSpectator,
      long targetDawnTimeTicks,
      long nightStartTicks,
      long nightEndTicks,
      boolean allowSleepDuringThunder,
      int budgetMaxFramesPerTick,
      ShardingConfig sharding,
      StorageConfig storage,
      MessagesConfig messages,
      List<String> wakeupEffects,
      List<SleepMessageEntry> sleepMessages,
      Map<String, WorldSleepConfig> worlds) {
    this(
        serverShardId,
        defaultQuorumPolicy,
        defaultQuorumValue,
        QuorumPolicy.CancelPolicy.TUG_OF_WAR,
        QuorumPolicy.CurveConfig.DEFAULT_SLEEP,
        QuorumPolicy.CurveConfig.DEFAULT_CANCEL,
        warmupDelayTicks,
        0,
        2,
        sleepCooldownSeconds,
        cancelCooldownSeconds,
        wakeOnCancel,
        clearWeatherOnSkip,
        clearThunderOnSkip,
        resetPhantomStatistic,
        excludeAfkAndVanished,
        excludeCreativeAndSpectator,
        targetDawnTimeTicks,
        nightStartTicks,
        nightEndTicks,
        allowSleepDuringThunder,
        budgetMaxFramesPerTick,
        sharding,
        storage,
        messages,
        wakeupEffects,
        sleepMessages,
        worlds);
  }

  public LeafSleepConfig(
      String serverShardId,
      QuorumPolicy defaultQuorumPolicy,
      int defaultQuorumValue,
      QuorumPolicy.CancelPolicy defaultCancelPolicy,
      QuorumPolicy.CurveConfig sleepCurve,
      QuorumPolicy.CurveConfig cancelCurve,
      int warmupDelayTicks,
      int sleepCooldownSeconds,
      int cancelCooldownSeconds,
      boolean wakeOnCancel,
      boolean clearWeatherOnSkip,
      boolean clearThunderOnSkip,
      boolean resetPhantomStatistic,
      boolean excludeAfkAndVanished,
      boolean excludeCreativeAndSpectator,
      long targetDawnTimeTicks,
      long nightStartTicks,
      long nightEndTicks,
      boolean allowSleepDuringThunder,
      int budgetMaxFramesPerTick,
      ShardingConfig sharding,
      StorageConfig storage,
      MessagesConfig messages,
      List<String> wakeupEffects,
      List<SleepMessageEntry> sleepMessages,
      Map<String, WorldSleepConfig> worlds) {
    this(
        serverShardId,
        defaultQuorumPolicy,
        defaultQuorumValue,
        defaultCancelPolicy,
        sleepCurve,
        cancelCurve,
        warmupDelayTicks,
        0,
        2,
        sleepCooldownSeconds,
        cancelCooldownSeconds,
        wakeOnCancel,
        clearWeatherOnSkip,
        clearThunderOnSkip,
        resetPhantomStatistic,
        excludeAfkAndVanished,
        excludeCreativeAndSpectator,
        targetDawnTimeTicks,
        nightStartTicks,
        nightEndTicks,
        allowSleepDuringThunder,
        budgetMaxFramesPerTick,
        sharding,
        storage,
        messages,
        wakeupEffects,
        sleepMessages,
        worlds);
  }

  public LeafSleepConfig(
      String serverShardId,
      QuorumPolicy defaultQuorumPolicy,
      int defaultQuorumValue,
      QuorumPolicy.CancelPolicy defaultCancelPolicy,
      QuorumPolicy.CurveConfig sleepCurve,
      QuorumPolicy.CurveConfig cancelCurve,
      int warmupDelayTicks,
      int increment,
      int timeBetweenIncrements,
      int sleepCooldownSeconds,
      int cancelCooldownSeconds,
      boolean wakeOnCancel,
      boolean clearWeatherOnSkip,
      boolean clearThunderOnSkip,
      boolean resetPhantomStatistic,
      boolean excludeAfkAndVanished,
      boolean excludeCreativeAndSpectator,
      long targetDawnTimeTicks,
      long nightStartTicks,
      long nightEndTicks,
      boolean allowSleepDuringThunder,
      int budgetMaxFramesPerTick,
      ShardingConfig sharding,
      StorageConfig storage,
      MessagesConfig messages,
      List<String> wakeupEffects,
      List<SleepMessageEntry> sleepMessages,
      Map<String, WorldSleepConfig> worlds) {
    this(
        serverShardId,
        defaultQuorumPolicy,
        defaultQuorumValue,
        defaultCancelPolicy,
        sleepCurve,
        cancelCurve,
        warmupDelayTicks,
        true,
        true,
        increment,
        timeBetweenIncrements,
        sleepCooldownSeconds,
        cancelCooldownSeconds,
        wakeOnCancel,
        clearWeatherOnSkip,
        clearThunderOnSkip,
        resetPhantomStatistic,
        excludeAfkAndVanished,
        excludeCreativeAndSpectator,
        targetDawnTimeTicks,
        nightStartTicks,
        nightEndTicks,
        allowSleepDuringThunder,
        budgetMaxFramesPerTick,
        sharding,
        storage,
        messages,
        wakeupEffects,
        sleepMessages,
        worlds);
  }

  public LeafSleepConfig(
      String serverShardId,
      QuorumPolicy defaultQuorumPolicy,
      int defaultQuorumValue,
      QuorumPolicy.CancelPolicy defaultCancelPolicy,
      QuorumPolicy.CurveConfig sleepCurve,
      QuorumPolicy.CurveConfig cancelCurve,
      int warmupDelayTicks,
      boolean warmupActionBar,
      boolean warmupBossBar,
      int increment,
      int timeBetweenIncrements,
      int sleepCooldownSeconds,
      int cancelCooldownSeconds,
      boolean wakeOnCancel,
      boolean clearWeatherOnSkip,
      boolean clearThunderOnSkip,
      boolean resetPhantomStatistic,
      boolean excludeAfkAndVanished,
      boolean excludeCreativeAndSpectator,
      long targetDawnTimeTicks,
      long nightStartTicks,
      long nightEndTicks,
      boolean allowSleepDuringThunder,
      int budgetMaxFramesPerTick,
      ShardingConfig sharding,
      StorageConfig storage,
      MessagesConfig messages,
      List<String> wakeupEffects,
      List<SleepMessageEntry> sleepMessages,
      Map<String, WorldSleepConfig> worlds) {
    this.serverShardId =
        (serverShardId == null || serverShardId.isBlank()) ? "shard-1" : serverShardId;
    this.defaultQuorumPolicy =
        defaultQuorumPolicy == null ? QuorumPolicy.ONE_PLAYER : defaultQuorumPolicy;
    this.defaultQuorumValue = Math.max(1, defaultQuorumValue);
    this.defaultCancelPolicy =
        defaultCancelPolicy == null
            ? QuorumPolicy.CancelPolicy.TUG_OF_WAR
            : defaultCancelPolicy;
    this.sleepCurve =
        sleepCurve == null ? QuorumPolicy.CurveConfig.DEFAULT_SLEEP : sleepCurve;
    this.cancelCurve =
        cancelCurve == null ? QuorumPolicy.CurveConfig.DEFAULT_CANCEL : cancelCurve;
    this.warmupDelayTicks = Math.max(0, warmupDelayTicks);
    this.warmupActionBar = warmupActionBar;
    this.warmupBossBar = warmupBossBar;
    this.increment = Math.max(0, Math.min(24000, increment));
    this.timeBetweenIncrements = Math.max(1, timeBetweenIncrements);
    this.sleepCooldownSeconds = Math.max(0, sleepCooldownSeconds);
    this.cancelCooldownSeconds = Math.max(0, cancelCooldownSeconds);
    this.wakeOnCancel = wakeOnCancel;
    this.clearWeatherOnSkip = clearWeatherOnSkip;
    this.clearThunderOnSkip = clearThunderOnSkip;
    this.resetPhantomStatistic = resetPhantomStatistic;
    this.excludeAfkAndVanished = excludeAfkAndVanished;
    this.excludeCreativeAndSpectator = excludeCreativeAndSpectator;
    this.targetDawnTimeTicks = Math.max(0L, targetDawnTimeTicks % 24000L);
    this.nightStartTicks = Math.max(0L, nightStartTicks);
    this.nightEndTicks = Math.max(0L, nightEndTicks);
    this.allowSleepDuringThunder = allowSleepDuringThunder;
    this.budgetMaxFramesPerTick = Math.max(1, budgetMaxFramesPerTick);
    this.sharding = Objects.requireNonNull(sharding, "sharding");
    this.storage = Objects.requireNonNull(storage, "storage");
    this.messages = Objects.requireNonNull(messages, "messages");
    this.wakeupEffects = wakeupEffects == null ? List.of() : List.copyOf(wakeupEffects);
    this.sleepMessages = sleepMessages == null ? List.of() : List.copyOf(sleepMessages);
    this.worlds =
        worlds == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(worlds));
  }

  /**
   * Ensures default {@code config.yml}, {@code player.yml}, {@code commands.yml}, {@code
   * sleep.yml}, {@code network.yml}, and {@code messages/*.yml} exist on disk.
   */
  public static void saveDefaultsIfAbsent(Path rootDir) throws IOException {
    if (rootDir == null) return;
    Files.createDirectories(rootDir);
    ClassLoader loader = LeafSleepConfig.class.getClassLoader();
    for (String fileName : CONFIG_FILES) {
      Path target = rootDir.resolve(fileName);
      if (!Files.exists(target)) {
        try (InputStream in = loader.getResourceAsStream(fileName)) {
          if (in != null) {
            Files.copy(in, target);
          }
        }
      }
    }
    new MultiConfigParser<>(
        SleepMessageKeys.class,
        "messages",
        "1.0",
        rootDir.toFile(),
        loader,
        "messages");
  }

  /**
   * Validates {@code config.yml}, {@code player.yml}, {@code commands.yml}, {@code sleep.yml},
   * {@code network.yml}, and {@code messages/*.yml} in {@code rootDir} against their bundled
   * ADR-064 directives.
   *
   * @param rootDir plugin configuration directory
   * @return list of validation findings; empty when valid
   */
  public static List<BitFinding> validateDirectory(Path rootDir) {
    List<BitFinding> findings = new ArrayList<>();
    ClassLoader loader = LeafSleepConfig.class.getClassLoader();
    ConfigSnapshot snapshot = ConfigSnapshot.read(rootDir);
    for (String fileName : CONFIG_FILES) {
      findings.addAll(
          DirectiveValidator.validate(
              snapshot,
              fileName,
              loader,
              io.github.dailystruggle.rtp.common.bit.config.DirectiveCodes.DEFAULT));
    }
    String bundledDefaultTheme = DirectiveValidator.readResource(loader, "messages/default.yml");
    if (bundledDefaultTheme != null) {
      for (ConfigSnapshot.Document doc : snapshot.documentsIn("messages").values()) {
        findings.addAll(
            DirectiveValidator.validate(
                doc,
                bundledDefaultTheme,
                io.github.dailystruggle.rtp.common.bit.config.DirectiveCodes.DEFAULT));
      }
    }
    return findings;
  }

  /** Loads {@link LeafSleepConfig} from {@code rootDir} (seeding bundled defaults if missing). */
  public static LeafSleepConfig load(Path rootDir) {
    try {
      saveDefaultsIfAbsent(rootDir);
    } catch (IOException ignored) {
    }
    RtpYamlConfig mainYaml = loadOrBundled(rootDir, "config.yml");
    RtpYamlConfig playerYaml = loadOrBundledWithFallback(rootDir, "player.yml", "messages.yml");
    RtpYamlConfig commandsYaml = loadOrBundledWithFallback(rootDir, "commands.yml", "messages.yml");
    RtpYamlConfig sleepYaml = loadOrBundled(rootDir, "sleep.yml");
    RtpYamlConfig networkYaml = loadOrBundled(rootDir, "network.yml");
    MultiConfigParser<SleepMessageKeys> sleepMessagesParser =
        rootDir != null
            ? new MultiConfigParser<>(
                SleepMessageKeys.class,
                "messages",
                "1.0",
                rootDir.toFile(),
                LeafSleepConfig.class.getClassLoader(),
                "messages")
            : null;
    return fromYaml(
        mainYaml, playerYaml, commandsYaml, sleepYaml, networkYaml, sleepMessagesParser);
  }

  private static RtpYamlConfig loadOrBundled(Path rootDir, String fileName) {
    File f = rootDir != null ? rootDir.resolve(fileName).toFile() : null;
    if (f != null && f.isFile()) {
      try {
        return RtpYamlConfig.load(f);
      } catch (Exception ignored) {
      }
    }
    String bundled = DirectiveValidator.readResource(LeafSleepConfig.class.getClassLoader(), fileName);
    return bundled != null ? RtpYamlConfig.parse(bundled) : RtpYamlConfig.parse("");
  }

  private static RtpYamlConfig loadOrBundledWithFallback(
      Path rootDir, String primaryFileName, String legacyFileName) {
    RtpYamlConfig primary = loadOrBundled(rootDir, primaryFileName);
    File legacyFile = rootDir != null ? rootDir.resolve(legacyFileName).toFile() : null;
    if (legacyFile != null && legacyFile.isFile()) {
      try {
        RtpYamlConfig legacy = RtpYamlConfig.load(legacyFile);
        for (String key : legacy.getKeys(false)) {
          if (!"version".equalsIgnoreCase(key) && primary.contains(key)) {
            primary.set(key, legacy.get(key));
          }
        }
      } catch (Exception ignored) {
      }
    }
    return primary;
  }

  /** Parses {@link LeafSleepConfig} from already-loaded YAML configurations. */
  public static LeafSleepConfig fromYaml(
      RtpYamlConfig mainYaml, RtpYamlConfig msgYaml, RtpYamlConfig sleepYaml) {
    return fromYaml(mainYaml, msgYaml, msgYaml, sleepYaml, null, null);
  }

  /**
   * Parses {@link LeafSleepConfig} from {@code config.yml}, a single combined messages YAML, {@code
   * sleep.yml}, and optional additive {@code network.yml}.
   */
  public static LeafSleepConfig fromYaml(
      RtpYamlConfig mainYaml,
      RtpYamlConfig msgYaml,
      RtpYamlConfig sleepYaml,
      RtpYamlConfig networkYaml) {
    return fromYaml(mainYaml, msgYaml, msgYaml, sleepYaml, networkYaml, null);
  }

  /**
   * Parses {@link LeafSleepConfig} from {@code config.yml}, {@code player.yml}, {@code
   * commands.yml}, {@code sleep.yml}, and optional additive {@code network.yml}.
   */
  public static LeafSleepConfig fromYaml(
      RtpYamlConfig mainYaml,
      RtpYamlConfig playerYaml,
      RtpYamlConfig commandsYaml,
      RtpYamlConfig sleepYaml,
      RtpYamlConfig networkYaml) {
    return fromYaml(mainYaml, playerYaml, commandsYaml, sleepYaml, networkYaml, null);
  }

  /**
   * Parses {@link LeafSleepConfig} from {@code config.yml}, {@code player.yml}, {@code
   * commands.yml}, {@code sleep.yml}, optional additive {@code network.yml}, and optional {@link
   * MultiConfigParser} over {@code messages/*.yml}.
   */
  public static LeafSleepConfig fromYaml(
      RtpYamlConfig mainYaml,
      RtpYamlConfig playerYaml,
      RtpYamlConfig commandsYaml,
      RtpYamlConfig sleepYaml,
      RtpYamlConfig networkYaml,
      MultiConfigParser<SleepMessageKeys> sleepMessagesParser) {
    RtpYamlConfig pYaml = playerYaml != null ? playerYaml : RtpYamlConfig.parse("");
    RtpYamlConfig cYaml = commandsYaml != null ? commandsYaml : pYaml;
    String shardId = mainYaml.getString("serverShardId", "shard-1");
    if (networkYaml != null) {
      String netShardId = networkYaml.getString("serverShardId", "shard-1");
      RtpYamlSection netBlock = networkYaml.getConfigurationSection("network");
      if ("shard-1".equals(netShardId) && netBlock != null) {
        String aliasId = netBlock.getString("serverId", "");
        if (aliasId != null && !aliasId.isBlank()) {
          netShardId = aliasId.trim();
        }
      }
      if (netShardId != null && !netShardId.isBlank() && !"shard-1".equals(netShardId)) {
        shardId = netShardId.trim();
      }
    }
    QuorumPolicy defaultPolicy =
        QuorumPolicy.fromString(mainYaml.getString("defaultQuorumPolicy", "curve"));
    int defaultQuorumVal = mainYaml.getInt("defaultQuorumValue", 25);
    QuorumPolicy.CancelPolicy defaultCancelPol =
        QuorumPolicy.CancelPolicy.fromString(
            mainYaml.getString("defaultCancelPolicy", "tug of war"));
    QuorumPolicy.CurveConfig sleepCurve =
        parseCurveSection(
            mainYaml.getConfigurationSection("sleepCurve"),
            QuorumPolicy.CurveConfig.DEFAULT_SLEEP);
    QuorumPolicy.CurveConfig cancelCurve =
        parseCurveSection(
            mainYaml.getConfigurationSection("cancelCurve"),
            QuorumPolicy.CurveConfig.DEFAULT_CANCEL);
    int warmupTicks = mainYaml.getInt("warmupDelayTicks", 60);
    boolean warmupActionBar = mainYaml.getBoolean("warmupActionBar", true);
    boolean warmupBossBar = mainYaml.getBoolean("warmupBossBar", true);
    int increment = mainYaml.getInt("increment", 150);
    int timeBetweenIncrements = mainYaml.getInt("timeBetweenIncrements", 2);
    int sleepCooldown = mainYaml.getInt("sleepCooldownSeconds", 10);
    int cancelCooldown = mainYaml.getInt("cancelCooldownSeconds", 30);
    boolean wakeOnCancel = mainYaml.getBoolean("wakeOnCancel", true);
    boolean clearWeather = mainYaml.getBoolean("clearWeatherOnSkip", true);
    boolean clearThunder = mainYaml.getBoolean("clearThunderOnSkip", true);
    boolean resetPhantom = mainYaml.getBoolean("resetPhantomStatistic", true);
    boolean excludeAfk = mainYaml.getBoolean("excludeAfkAndVanished", true);
    boolean excludeCreative = mainYaml.getBoolean("excludeCreativeAndSpectator", true);
    long dawnTicks = mainYaml.getLong("targetDawnTimeTicks", 0L);
    long nightStart = mainYaml.getLong("nightStartTicks", 12542L);
    long nightEnd = mainYaml.getLong("nightEndTicks", 23459L);
    boolean allowThunder = mainYaml.getBoolean("allowSleepDuringThunder", true);
    int maxFrames = mainYaml.getInt("budgetMaxFramesPerTick", 32);

    RtpYamlSection shardingSec = mainYaml.getConfigurationSection("sharding");
    ShardingConfig sharding = parseShardingSection(shardingSec, null);
    if (networkYaml != null) {
      RtpYamlSection netShardingSec = networkYaml.getConfigurationSection("sharding");
      if (netShardingSec != null) {
        sharding = parseShardingSection(netShardingSec, sharding);
      }
    }

    RtpYamlSection storageSec = mainYaml.getConfigurationSection("storage");
    StorageConfig storage = parseStorageSection(storageSec, null);
    if (networkYaml != null) {
      RtpYamlSection netStorageSec = networkYaml.getConfigurationSection("storage");
      if (netStorageSec != null) {
        storage = parseStorageSection(netStorageSec, storage);
      }
    }

    MessagesConfig messages =
        new MessagesConfig(
            readString(pYaml, cYaml, "prefix", "[LeafSleep] "),
            readString(
                pYaml,
                cYaml,
                "sleepBroadcast",
                "[player] is sleeping (Sleeping: [sleepingPlayers]) - [requirements] [cancelPrompt] [sleepPrompt]"),
            readString(
                pYaml,
                cYaml,
                "wakeupBroadcast",
                "Good morning, [world]! Night skipped (epoch [epoch])."),
            readString(
                pYaml,
                cYaml,
                "cancelBroadcast",
                "Night skip cancelled by [cancellingPlayers] in [shardGroup]!"),
            readString(
                pYaml,
                cYaml,
                "cancelVoteBroadcast",
                "[player] wants night (Cancelling: [cancellingPlayers]) - [requirements] [cancelPrompt] [sleepPrompt]"),
            readString(pYaml, cYaml, "cannotCancel", "No active sleep countdown to cancel."),
            readString(
                pYaml, cYaml, "alreadySleeping", "You are already sleeping or voting to sleep."),
            readString(
                pYaml,
                cYaml,
                "notNight",
                "You can only sleep at night or during a thunderstorm."),
            readString(
                pYaml,
                cYaml,
                "onCooldown",
                "Please wait [seconds]s before using that sleep action again."),
            readString(
                pYaml,
                cYaml,
                "ignoreEnabled",
                "Sleep ignore enabled: you are now excluded from sleep quorum counts."),
            readString(
                pYaml,
                cYaml,
                "ignoreDisabled",
                "Sleep ignore disabled: you are now included in sleep quorum counts."),
            readString(
                cYaml, pYaml, "reloadSuccess", "LeafSleep configuration reloaded successfully."),
            readString(
                cYaml,
                pYaml,
                "reloadFailed",
                "LeafSleep reload aborted due to configuration errors: [errors]"),
            readString(
                cYaml,
                pYaml,
                "noPermission",
                "You do not have permission to execute that command."),
            readString(
                cYaml,
                pYaml,
                "invalidCommand",
                "Unknown subcommand '[arg]'. Use /sleep <status|sleep|cancel|ignore|message|reload|test|version|help>."),
            readString(
                cYaml,
                pYaml,
                "badParameter",
                "Invalid value '[value]' for parameter '[param]'."),
            readString(
                cYaml,
                pYaml,
                "busy",
                "Sleep coordinator is busy processing another transition; please try again shortly."),
            readString(cYaml, pYaml, "statusHeader", "--- LeafSleep Status ---"),
            readString(
                cYaml,
                pYaml,
                "statusWorldLine",
                "World [world] ([shardGroup], policy=[policy]/[cancelPolicy]): [requirements] | sleeping=[sleeping]/[required] ([sleepingPlayers]), cancelling=[cancelling]/[cancelRequired] ([cancellingPlayers]), eligible=[eligible]/[maxPlayers], epoch=[epoch]"),
            readString(
                cYaml,
                pYaml,
                "statusTransportLine",
                "Transport=[transport] (shards=[shards]) | Storage=[storage]"),
            readString(pYaml, cYaml, "clickCancelLabel", "[Cancel]"),
            readString(pYaml, cYaml, "clickCancelHover", "Click to run /sleep cancel"),
            readString(pYaml, cYaml, "clickSleepLabel", "[Sleep]"),
            readString(pYaml, cYaml, "clickSleepHover", "Click to run /sleep sleep"),
            readString(
                pYaml,
                cYaml,
                "warmupActionBar",
                "&eSkipping night in &f[seconds]s &7([sleeping]/[required] sleeping) - &c/sleep cancel"),
            readString(
                pYaml,
                cYaml,
                "warmupBossBar",
                "&aSkipping night in [seconds]s ([sleeping]/[required] sleeping)"),
            readString(
                pYaml,
                cYaml,
                "messagePrefCurrent",
                "Current sleep message theme: [message] (Available: [available])."),
            readString(
                pYaml,
                cYaml,
                "messagePrefUpdated",
                "Preferred sleep message theme set to '[message]'."));

    List<String> wakeupEffects = sleepYaml.getStringList("wakeupEffects");
    List<SleepMessageEntry> sleepMsgList =
        parseSleepMessages(sleepMessagesParser, sleepYaml, messages);

    Map<String, WorldSleepConfig> worldConfigs = new LinkedHashMap<>();
    RtpYamlSection worldsSec = sleepYaml.getConfigurationSection("worlds");
    if (worldsSec != null) {
      for (String wName : worldsSec.getKeys(false)) {
        RtpYamlSection wSec = worldsSec.getConfigurationSection(wName);
        if (wSec != null) {
          QuorumPolicy parsedPolicy =
              wSec.isSet("policy")
                  ? QuorumPolicy.fromString(
                      wSec.getString("policy", defaultPolicy.displayName()))
                  : defaultPolicy;
          QuorumPolicy.CancelPolicy parsedCancelPol =
              wSec.isSet("cancelPolicy")
                  ? QuorumPolicy.CancelPolicy.fromString(
                      wSec.getString("cancelPolicy", defaultCancelPol.displayName()))
                  : defaultCancelPol;
          int parsedQuorumVal =
              wSec.isSet("quorumValue")
                  ? wSec.getInt("quorumValue", defaultQuorumVal)
                  : defaultQuorumVal;
          int parsedWarmup =
              wSec.isSet("warmupDelayTicks")
                  ? wSec.getInt("warmupDelayTicks", warmupTicks)
                  : warmupTicks;
          boolean parsedActionBar =
              wSec.isSet("warmupActionBar")
                  ? wSec.getBoolean("warmupActionBar", warmupActionBar)
                  : warmupActionBar;
          boolean parsedBossBar =
              wSec.isSet("warmupBossBar")
                  ? wSec.getBoolean("warmupBossBar", warmupBossBar)
                  : warmupBossBar;
          int parsedIncrement =
              wSec.isSet("increment")
                  ? wSec.getInt("increment", increment)
                  : increment;
          int parsedStepTicks =
              wSec.isSet("timeBetweenIncrements")
                  ? wSec.getInt("timeBetweenIncrements", timeBetweenIncrements)
                  : timeBetweenIncrements;
          worldConfigs.put(
              wName,
              new WorldSleepConfig(
                  wName,
                  wSec.getBoolean("enabled", true),
                  wSec.getString("shardGroupId", wName),
                  parsedPolicy,
                  parsedQuorumVal,
                  parsedCancelPol,
                  parseCurveSection(
                      wSec.getConfigurationSection("sleepCurve"), sleepCurve),
                  parseCurveSection(
                      wSec.getConfigurationSection("cancelCurve"), cancelCurve),
                  parsedWarmup,
                  parsedActionBar,
                  parsedBossBar,
                  parsedIncrement,
                  parsedStepTicks));
        }
      }
    }

    return new LeafSleepConfig(
        shardId,
        defaultPolicy,
        defaultQuorumVal,
        defaultCancelPol,
        sleepCurve,
        cancelCurve,
        warmupTicks,
        warmupActionBar,
        warmupBossBar,
        increment,
        timeBetweenIncrements,
        sleepCooldown,
        cancelCooldown,
        wakeOnCancel,
        clearWeather,
        clearThunder,
        resetPhantom,
        excludeAfk,
        excludeCreative,
        dawnTicks,
        nightStart,
        nightEnd,
        allowThunder,
        maxFrames,
        sharding,
        storage,
        messages,
        wakeupEffects,
        sleepMsgList,
        worldConfigs);
  }

  private static String readString(
      RtpYamlConfig primary, RtpYamlConfig fallback, String key, String defaultValue) {
    if (primary != null && primary.contains(key)) {
      return primary.getString(key, defaultValue);
    }
    if (fallback != null && fallback.contains(key)) {
      return fallback.getString(key, defaultValue);
    }
    return defaultValue;
  }

  private static List<SleepMessageEntry> parseSleepMessages(
      MultiConfigParser<SleepMessageKeys> sleepMessagesParser,
      RtpYamlConfig sleepYaml,
      MessagesConfig messages) {
    List<SleepMessageEntry> sleepMsgList = new ArrayList<>();
    if (sleepMessagesParser != null && sleepMessagesParser.configParserFactory != null) {
      for (ConfigParser<SleepMessageKeys> parser :
          sleepMessagesParser.configParserFactory.map.values()) {
        if (parser == null || parser.name == null) continue;
        String id =
            parser.name.toLowerCase(Locale.ROOT).endsWith(".yml")
                ? parser.name.substring(0, parser.name.length() - 4)
                : parser.name;
        int weight = parser.getNumber(SleepMessageKeys.weight, 10).intValue();
        Object rawSleep = parser.getConfigValue(SleepMessageKeys.sleepText, messages.sleepBroadcast());
        Object rawWakeup =
            parser.getConfigValue(SleepMessageKeys.wakeupText, messages.wakeupBroadcast());
        Object rawCancel =
            parser.getConfigValue(SleepMessageKeys.cancelPrompt, messages.clickCancelLabel());
        Object rawSleepPrompt =
            parser.getConfigValue(SleepMessageKeys.sleepPrompt, messages.clickSleepLabel());
        sleepMsgList.add(
            new SleepMessageEntry(
                id,
                weight,
                rawSleep != null ? String.valueOf(rawSleep) : messages.sleepBroadcast(),
                rawWakeup != null ? String.valueOf(rawWakeup) : messages.wakeupBroadcast(),
                rawCancel != null ? String.valueOf(rawCancel) : messages.clickCancelLabel(),
                rawSleepPrompt != null
                    ? String.valueOf(rawSleepPrompt)
                    : messages.clickSleepLabel()));
      }
    }
    if (sleepMsgList.isEmpty() && sleepYaml != null) {
      RtpYamlSection msgSetSec = sleepYaml.getConfigurationSection("sleepMessages");
      if (msgSetSec != null) {
        for (String id : msgSetSec.getKeys(false)) {
          RtpYamlSection sub = msgSetSec.getConfigurationSection(id);
          if (sub != null) {
            sleepMsgList.add(
                new SleepMessageEntry(
                    id,
                    sub.getInt("weight", 10),
                    sub.getString("sleepText", messages.sleepBroadcast()),
                    sub.getString("wakeupText", messages.wakeupBroadcast()),
                    sub.getString("cancelPrompt", messages.clickCancelLabel()),
                    sub.getString("sleepPrompt", messages.clickSleepLabel())));
          }
        }
      }
    }
    if (sleepMsgList.isEmpty()) {
      ClassLoader loader = LeafSleepConfig.class.getClassLoader();
      for (String themeId : BUNDLED_MESSAGE_THEMES) {
        String bundled = DirectiveValidator.readResource(loader, "messages/" + themeId + ".yml");
        if (bundled != null) {
          RtpYamlConfig themeYaml = RtpYamlConfig.parse(bundled);
          sleepMsgList.add(
              new SleepMessageEntry(
                  themeId,
                  themeYaml.getInt("weight", 10),
                  themeYaml.getString("sleepText", messages.sleepBroadcast()),
                  themeYaml.getString("wakeupText", messages.wakeupBroadcast()),
                  themeYaml.getString("cancelPrompt", messages.clickCancelLabel()),
                  themeYaml.getString("sleepPrompt", messages.clickSleepLabel())));
        }
      }
    }
    if (sleepMsgList.isEmpty()) {
      sleepMsgList.add(
          new SleepMessageEntry(
              "default",
              100,
              messages.sleepBroadcast(),
              messages.wakeupBroadcast(),
              messages.clickCancelLabel(),
              messages.clickSleepLabel()));
    }
    sleepMsgList.sort(
        Comparator.comparing((SleepMessageEntry e) -> !"default".equalsIgnoreCase(e.id()))
            .thenComparing(Comparator.comparingInt(SleepMessageEntry::weight).reversed())
            .thenComparing(e -> e.id().toLowerCase(Locale.ROOT)));
    return sleepMsgList;
  }

  private static ShardingConfig parseShardingSection(
      RtpYamlSection sec, ShardingConfig base) {
    ShardingConfig defaults =
        new ShardingConfig(
            false,
            TransportType.LOCAL_ONLY,
            "leafsleep:sync",
            "127.0.0.1",
            25620,
            List.of(),
            "LEAFSLEEP_HMAC_SECRET",
            100);
    if (sec == null) {
      return base != null ? base : defaults;
    }
    ShardingConfig fallback = base != null ? base : defaults;
    boolean enabled = sec.getBoolean("enabled", false) || fallback.enabled();
    TransportType parsedTransport =
        TransportType.fromString(sec.getString("transport", "local only"));
    TransportType transport =
        parsedTransport != TransportType.LOCAL_ONLY ? parsedTransport : fallback.transport();
    String parsedChannel = sec.getString("channel", "leafsleep:sync");
    String channel =
        !"leafsleep:sync".equals(parsedChannel) ? parsedChannel : fallback.channel();
    String parsedHost = sec.getString("bindHost", "127.0.0.1");
    String bindHost = !"127.0.0.1".equals(parsedHost) ? parsedHost : fallback.bindHost();
    int parsedPort = sec.getInt("bindPort", 25620);
    int bindPort = parsedPort != 25620 ? parsedPort : fallback.bindPort();
    List<String> parsedPeers = sec.getStringList("peers");
    List<String> peers =
        (parsedPeers != null && !parsedPeers.isEmpty()) ? parsedPeers : fallback.peers();
    String parsedHmac = sec.getString("hmacSecretEnv", "LEAFSLEEP_HMAC_SECRET");
    String hmacSecretEnv =
        !"LEAFSLEEP_HMAC_SECRET".equals(parsedHmac) ? parsedHmac : fallback.hmacSecretEnv();
    int parsedHb = sec.getInt("heartbeatIntervalTicks", 100);
    int heartbeatTicks = parsedHb != 100 ? parsedHb : fallback.heartbeatIntervalTicks();
    return new ShardingConfig(
        enabled,
        transport,
        channel,
        bindHost,
        bindPort,
        peers,
        hmacSecretEnv,
        heartbeatTicks);
  }

  private static StorageConfig parseStorageSection(
      RtpYamlSection sec, StorageConfig base) {
    StorageConfig defaults =
        new StorageConfig(
            StorageType.SQLITE,
            "leafsleep.db",
            "127.0.0.1",
            3306,
            "leafsleep",
            "root",
            "LEAFSLEEP_DB_PASSWORD",
            "leafsleep_");
    if (sec == null) {
      return base != null ? base : defaults;
    }
    StorageConfig fallback = base != null ? base : defaults;
    StorageType parsedType = StorageType.fromString(sec.getString("type", "sqlite"));
    StorageType type = parsedType != StorageType.SQLITE ? parsedType : fallback.type();
    String parsedFile = sec.getString("sqliteFile", "leafsleep.db");
    String sqliteFile = !"leafsleep.db".equals(parsedFile) ? parsedFile : fallback.sqliteFile();
    String parsedHost = sec.getString("host", "127.0.0.1");
    String host = !"127.0.0.1".equals(parsedHost) ? parsedHost : fallback.host();
    int parsedPort = sec.getInt("port", 3306);
    int port = parsedPort != 3306 ? parsedPort : fallback.port();
    String parsedDb = sec.getString("database", "leafsleep");
    String database = !"leafsleep".equals(parsedDb) ? parsedDb : fallback.database();
    String parsedUser = sec.getString("username", "root");
    String username = !"root".equals(parsedUser) ? parsedUser : fallback.username();
    String parsedPassEnv = sec.getString("passwordEnv", "LEAFSLEEP_DB_PASSWORD");
    String passwordEnv =
        !"LEAFSLEEP_DB_PASSWORD".equals(parsedPassEnv) ? parsedPassEnv : fallback.passwordEnv();
    String parsedPrefix = sec.getString("tablePrefix", "leafsleep_");
    String tablePrefix =
        !"leafsleep_".equals(parsedPrefix) ? parsedPrefix : fallback.tablePrefix();
    return new StorageConfig(
        type, sqliteFile, host, port, database, username, passwordEnv, tablePrefix);
  }

  private static QuorumPolicy.CurveConfig parseCurveSection(
      RtpYamlSection sec, QuorumPolicy.CurveConfig fallback) {
    if (sec == null) {
      return fallback != null ? fallback : QuorumPolicy.CurveConfig.DEFAULT_SLEEP;
    }
    QuorumPolicy.CurveConfig base =
        fallback != null ? fallback : QuorumPolicy.CurveConfig.DEFAULT_SLEEP;
    QuorumPolicy.CurveType type =
        QuorumPolicy.CurveType.fromString(sec.getString("type", base.type().displayName()));
    double defaultParam =
        (type == QuorumPolicy.CurveType.POWER && base.type() != QuorumPolicy.CurveType.POWER)
            ? 0.5
            : base.parameter();
    return new QuorumPolicy.CurveConfig(
        type,
        sec.getInt("minRequired", base.minRequired()),
        sec.getInt("maxRequired", base.maxRequired()),
        sec.getDouble("parameter", defaultParam),
        sec.getDouble("midpoint", base.midpoint()));
  }

  /** Resolves the effective {@link WorldSleepConfig} for {@code worldName}. */
  public WorldSleepConfig resolveWorldConfig(String worldName) {
    String key = (worldName == null || worldName.isBlank()) ? "world" : worldName.trim();
    WorldSleepConfig configured = worlds.get(key);
    if (configured != null) {
      return configured;
    }
    String lower = key.toLowerCase(Locale.ROOT);
    boolean isNetherOrEnd =
        lower.endsWith("_nether")
            || lower.endsWith("_the_end")
            || lower.equals("the_nether")
            || lower.equals("the_end")
            || lower.endsWith(":the_nether")
            || lower.endsWith(":the_end");
    return new WorldSleepConfig(
        key,
        !isNetherOrEnd,
        key,
        defaultQuorumPolicy,
        defaultQuorumValue,
        defaultCancelPolicy,
        sleepCurve,
        cancelCurve,
        warmupDelayTicks,
        warmupActionBar,
        warmupBossBar,
        increment,
        timeBetweenIncrements);
  }

  /** Selects a weighted random {@link SleepMessageEntry} (or a specific preferred ID). */
  public SleepMessageEntry selectMessage(String preferredId, Random random) {
    if (preferredId != null
        && !preferredId.isBlank()
        && !preferredId.equalsIgnoreCase("random")) {
      for (SleepMessageEntry entry : sleepMessages) {
        if (entry.id().equalsIgnoreCase(preferredId)) {
          return entry;
        }
      }
    }
    if (sleepMessages.isEmpty()) {
      return new SleepMessageEntry(
          "default",
          100,
          messages.sleepBroadcast(),
          messages.wakeupBroadcast(),
          messages.clickCancelLabel(),
          messages.clickSleepLabel());
    }
    int totalWeight = 0;
    for (SleepMessageEntry entry : sleepMessages) {
      totalWeight += entry.weight();
    }
    int roll = (random != null ? random.nextInt(Math.max(1, totalWeight)) : 0);
    int cumulative = 0;
    for (SleepMessageEntry entry : sleepMessages) {
      cumulative += entry.weight();
      if (roll < cumulative) {
        return entry;
      }
    }
    return sleepMessages.get(0);
  }

  /** Returns whether the given world time-of-day or weather state permits sleeping. */
  public boolean isSleepableWindow(long worldTimeTicks, boolean thundering) {
    if (allowSleepDuringThunder && thundering) {
      return true;
    }
    long tod = Math.floorMod(worldTimeTicks, 24000L);
    return tod >= nightStartTicks && tod <= nightEndTicks;
  }

  public String serverShardId() {
    return serverShardId;
  }

  public QuorumPolicy defaultQuorumPolicy() {
    return defaultQuorumPolicy;
  }

  public int defaultQuorumValue() {
    return defaultQuorumValue;
  }

  public QuorumPolicy.CancelPolicy defaultCancelPolicy() {
    return defaultCancelPolicy;
  }

  public QuorumPolicy.CurveConfig sleepCurve() {
    return sleepCurve;
  }

  public QuorumPolicy.CurveConfig cancelCurve() {
    return cancelCurve;
  }

  public int warmupDelayTicks() {
    return warmupDelayTicks;
  }

  public boolean warmupActionBar() {
    return warmupActionBar;
  }

  public boolean warmupBossBar() {
    return warmupBossBar;
  }

  public int increment() {
    return increment;
  }

  public int timeBetweenIncrements() {
    return timeBetweenIncrements;
  }

  public int sleepCooldownSeconds() {
    return sleepCooldownSeconds;
  }

  public int cancelCooldownSeconds() {
    return cancelCooldownSeconds;
  }

  public boolean wakeOnCancel() {
    return wakeOnCancel;
  }

  public boolean clearWeatherOnSkip() {
    return clearWeatherOnSkip;
  }

  public boolean clearThunderOnSkip() {
    return clearThunderOnSkip;
  }

  public boolean resetPhantomStatistic() {
    return resetPhantomStatistic;
  }

  public boolean excludeAfkAndVanished() {
    return excludeAfkAndVanished;
  }

  public boolean excludeCreativeAndSpectator() {
    return excludeCreativeAndSpectator;
  }

  public long targetDawnTimeTicks() {
    return targetDawnTimeTicks;
  }

  public long nightStartTicks() {
    return nightStartTicks;
  }

  public long nightEndTicks() {
    return nightEndTicks;
  }

  public boolean allowSleepDuringThunder() {
    return allowSleepDuringThunder;
  }

  public int budgetMaxFramesPerTick() {
    return budgetMaxFramesPerTick;
  }

  public ShardingConfig sharding() {
    return sharding;
  }

  public StorageConfig storage() {
    return storage;
  }

  public MessagesConfig messages() {
    return messages;
  }

  public List<String> wakeupEffects() {
    return wakeupEffects;
  }

  public List<SleepMessageEntry> sleepMessages() {
    return sleepMessages;
  }

  public Map<String, WorldSleepConfig> worlds() {
    return worlds;
  }
}
