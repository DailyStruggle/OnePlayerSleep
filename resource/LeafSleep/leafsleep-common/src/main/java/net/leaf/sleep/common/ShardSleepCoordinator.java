package net.leaf.sleep.common;

import io.github.dailystruggle.effectsapi.common.Effect;
import io.github.dailystruggle.effectsapi.common.EffectFactory;
import io.github.dailystruggle.menu.api.MenuAction;
import io.github.dailystruggle.menu.api.MenuTokenService;
import io.github.dailystruggle.server.api.BookMenuSpec;
import io.github.dailystruggle.server.api.PlayerBedEnterContext;
import io.github.dailystruggle.server.api.PlayerBedLeaveContext;
import io.github.dailystruggle.server.api.PlayerSleepSnapshot;
import io.github.dailystruggle.server.api.ServerAccessor;
import io.github.dailystruggle.server.api.WeatherState;
import io.github.dailystruggle.server.api.WorldTimeSkipContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Deterministic sharded quorum and idempotent {@code targetDayEpoch} state machine ({@code
 * LeafSleep ADR-001}).
 *
 * <p>Aggregates {@code (eligibleCount, sleepingCount, ignoredCount)} across all live shards in a
 * {@code shardGroupId}, drives the {@code warmupDelayTicks} countdown, processes cross-shard
 * {@code /sleep cancel} aborts, and commits dawn transitions idempotently via atomic high-water
 * mark CAS ({@code targetDayEpoch = currentFullTime / 24000L + 1}).
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 * @trace S-004
 * @trace S-005
 * @trace S-007
 */
public final class ShardSleepCoordinator {

  /** Snapshot of one shard's player counts inside a {@code shardGroupId}. */
  public record ShardCounts(
      String shardId,
      String shardGroupId,
      String worldName,
      int eligibleCount,
      int sleepingCount,
      int cancellingCount,
      int ignoredCount,
      int maxPlayers,
      List<String> sleepingPlayers,
      List<String> cancellingPlayers,
      long updatedAtMs) {

    public ShardCounts {
      maxPlayers = Math.max(1, maxPlayers);
      sleepingPlayers = sleepingPlayers == null ? List.of() : List.copyOf(sleepingPlayers);
      cancellingPlayers = cancellingPlayers == null ? List.of() : List.copyOf(cancellingPlayers);
    }

    public ShardCounts(
        String shardId,
        String shardGroupId,
        String worldName,
        int eligibleCount,
        int sleepingCount,
        int ignoredCount,
        long updatedAtMs) {
      this(
          shardId,
          shardGroupId,
          worldName,
          eligibleCount,
          sleepingCount,
          0,
          ignoredCount,
          20,
          List.of(),
          List.of(),
          updatedAtMs);
    }
  }

  /** Aggregated cluster-wide quorum status for a {@code shardGroupId}. */
  public record GroupQuorumStatus(
      String shardGroupId,
      String primaryWorldName,
      QuorumPolicy policy,
      int quorumValue,
      QuorumPolicy.CancelPolicy cancelPolicy,
      int eligibleCount,
      int maxPlayers,
      int sleepingCount,
      int cancellingCount,
      int ignoredCount,
      int requiredCount,
      int cancelRequiredCount,
      int needToSkip,
      int needToCancel,
      List<String> sleepingPlayers,
      List<String> cancellingPlayers,
      boolean quorumMet,
      boolean cancelQuorumMet,
      int warmupRemainingTicks,
      long lastCommittedEpoch,
      int activeShardCount) {

    public GroupQuorumStatus {
      sleepingPlayers = sleepingPlayers == null ? List.of() : List.copyOf(sleepingPlayers);
      cancellingPlayers = cancellingPlayers == null ? List.of() : List.copyOf(cancellingPlayers);
    }

    /** Formats the ultra-short requirement status string for chat broadcasts. */
    public String requirementsSummary() {
      return QuorumPolicy.formatShortRequirements(needToSkip, needToCancel);
    }
  }

  private static final long PEER_STALE_MS = 30_000L;
  private static final long IDLE_RECOMPUTE_INTERVAL_TICKS = 20L;

  /** Active smooth time-advance state for a {@code shardGroupId} (OnePlayerSleep {@code PassTime}). */
  private static final class PassTimeState {
    final long targetDayEpoch;
    final long targetFullTime;
    final int increment;
    final int timeBetweenIncrements;
    int ticksUntilNextStep;

    PassTimeState(
        long targetDayEpoch,
        long targetFullTime,
        int increment,
        int timeBetweenIncrements) {
      this.targetDayEpoch = targetDayEpoch;
      this.targetFullTime = targetFullTime;
      this.increment = Math.max(1, increment);
      this.timeBetweenIncrements = Math.max(1, timeBetweenIncrements);
      this.ticksUntilNextStep = 0;
    }
  }

  private final ServerAccessor accessor;
  private final Supplier<LeafSleepConfig> configSupplier;
  private final SleepDatabaseStore databaseStore;
  private final SleepSyncTransport transport;
  private final MenuTokenService tokenService;
  private final Random random = new Random();

  private final Map<String, Map<String, ShardCounts>> groupShardCounts = new ConcurrentHashMap<>();
  private final Map<String, List<PlayerSleepSnapshot>> cachedWorldPlayers =
      new ConcurrentHashMap<>();
  private final Map<String, Set<UUID>> localSleepVoters = new ConcurrentHashMap<>();
  private final Map<String, Map<UUID, String>> localSleepVoterNames = new ConcurrentHashMap<>();
  private final Map<String, Set<UUID>> localCancelVoters = new ConcurrentHashMap<>();
  private final Map<String, Map<UUID, String>> localCancelVoterNames = new ConcurrentHashMap<>();
  private final Map<String, Set<UUID>> recentlyLeftBedPlayers = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> groupWarmupRemaining = new ConcurrentHashMap<>();
  private final Map<String, Set<UUID>> activeBossBarViewers = new ConcurrentHashMap<>();
  private final Map<String, PassTimeState> groupPassTimeStates = new ConcurrentHashMap<>();
  private final Map<String, String> groupLastInitiator = new ConcurrentHashMap<>();
  private final Map<String, AtomicLong> lastCommittedEpochs = new ConcurrentHashMap<>();
  private final Map<String, Long> groupLastResetAtMs = new ConcurrentHashMap<>();
  private final AtomicLong totalCommittedEpochs = new AtomicLong();
  private final AtomicLong duplicateEpochsRejected = new AtomicLong();
  private final java.util.concurrent.atomic.AtomicBoolean localCountsDirty =
      new java.util.concurrent.atomic.AtomicBoolean(true);
  private final AtomicLong maxTickDurationNanos = new AtomicLong(0L);
  private volatile double avgTickDurationNanos = 10_000.0;
  private long tickCounter = 0L;

  public ShardSleepCoordinator(
      ServerAccessor accessor,
      Supplier<LeafSleepConfig> configSupplier,
      SleepDatabaseStore databaseStore,
      SleepSyncTransport transport,
      MenuTokenService tokenService) {
    this.accessor = Objects.requireNonNull(accessor, "accessor");
    this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    this.databaseStore = Objects.requireNonNull(databaseStore, "databaseStore");
    this.transport = Objects.requireNonNull(transport, "transport");
    this.tokenService =
        tokenService != null ? tokenService : new MenuTokenService();
    EffectFactory.registerBuiltInEffects();
  }

  /** Seeds a shard group's committed epoch watermark from durable storage on startup. */
  public void seedEpochWatermark(String shardGroupId, long epoch) {
    if (shardGroupId == null || epoch <= 0L) return;
    AtomicLong watermark =
        lastCommittedEpochs.computeIfAbsent(shardGroupId, k -> new AtomicLong(0L));
    watermark.updateAndGet(prev -> Math.max(prev, epoch));
  }

  /** Returns an immutable snapshot of all known shard-group committed epoch watermarks. */
  public Map<String, Long> exportEpochWatermarks() {
    Map<String, Long> out = new LinkedHashMap<>();
    for (Map.Entry<String, AtomicLong> e : lastCommittedEpochs.entrySet()) {
      out.put(e.getKey(), e.getValue().get());
    }
    return Collections.unmodifiableMap(out);
  }

  /** Handles a platform bed-enter event. */
  public void onBedEnter(PlayerBedEnterContext ctx) {
    if (ctx == null || ctx.isCancelled() || !ctx.isOk()) return;
    LeafSleepConfig cfg = configSupplier.get();
    String worldName = ctx.worldName();
    LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
    if (!worldCfg.enabled()) return;
    long worldTime = accessor.getWorldTime(worldName);
    WeatherState weather = accessor.getWeather(worldName);
    if (!cfg.isSleepableWindow(worldTime, weather == WeatherState.THUNDER)) return;

    UUID playerId = ctx.playerId();
    if (!isCallerEligibleInWorld(worldName, playerId, cfg)) {
      return;
    }
    long nowMs = System.currentTimeMillis();
    SleepDatabaseStore.PlayerSleepProfile profile = databaseStore.getCachedOrDefault(playerId);
    if (profile.cooldownUntilMs() > nowMs) {
      // Cancel the platform bed-enter event so the player is denied bed entry during cooldown
      // rather than lying in bed uncounted while excluded by recentlyLeftBedPlayers.
      ctx.cancel();
      long remSec = Math.max(1L, (profile.cooldownUntilMs() - nowMs + 999L) / 1000L);
      sendPrefixed(
          playerId,
          cfg.messages().onCooldown().replace("[seconds]", Long.toString(remSec)),
          true);
      return;
    }

    String safeName =
        (ctx.playerName() == null || ctx.playerName().isBlank()) ? "Player" : ctx.playerName();
    clearCancelVote(worldName, playerId);
    Set<UUID> leftBed = recentlyLeftBedPlayers.get(worldName);
    if (leftBed != null) {
      leftBed.remove(playerId);
    }
    localSleepVoters
        .computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet())
        .add(playerId);
    localSleepVoterNames
        .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
        .put(playerId, safeName);
    groupLastInitiator.put(worldCfg.shardGroupId(), safeName);

    long nextCooldown = nowMs + (cfg.sleepCooldownSeconds() * 1000L);
    databaseStore.savePlayerProfileAsync(profile.incrementSleeps(nextCooldown));

    ShardCounts local = recomputeLocalCounts(worldName, worldCfg, cfg);
    broadcastFrame(
        SleepSyncFrame.FrameType.BED_ENTER,
        worldCfg.shardGroupId(),
        worldName,
        playerId,
        safeName,
        local);

    GroupQuorumStatus status =
        evaluateGroupQuorumInternal(worldCfg.shardGroupId(), worldName, worldCfg, cfg);
    broadcastSleepProgress(worldName, ctx.playerId(), safeName, status, cfg);
    updateWarmupState(worldCfg, status, cfg);
  }

  /** Handles a platform bed-leave event. */
  public void onBedLeave(PlayerBedLeaveContext ctx) {
    if (ctx == null || ctx.isCancelled()) return;
    LeafSleepConfig cfg = configSupplier.get();
    String worldName = ctx.worldName();
    LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
    if (!worldCfg.enabled()) return;
    String groupId = worldCfg.shardGroupId();

    // During smooth time-advance near dawn (>= 22000 or past nightEndTicks), vanilla ejects
    // sleeping players from bed automatically; do not treat late sunrise wakeup as a cancel.
    if (groupPassTimeStates.containsKey(groupId)) {
      long tod = Math.floorMod(accessor.getWorldTime(worldName), 24000L);
      if (tod >= 22000L || tod >= cfg.nightEndTicks() || tod < cfg.nightStartTicks()) {
        return;
      }
    }

    clearSleepVote(worldName, ctx.playerId());
    if (ctx.playerId() != null) {
      recentlyLeftBedPlayers
          .computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet())
          .add(ctx.playerId());
    }
    ShardCounts local = recomputeLocalCounts(worldName, worldCfg, cfg, ctx.playerId());
    broadcastFrame(
        SleepSyncFrame.FrameType.BED_LEAVE,
        groupId,
        worldName,
        ctx.playerId(),
        ctx.playerName(),
        local);
    GroupQuorumStatus status =
        evaluateGroupQuorumInternal(groupId, worldName, worldCfg, cfg);
    if (!status.quorumMet()) {
      clearWarmupFeedback(groupId);
      groupPassTimeStates.remove(groupId);
    }
  }

  private void clearSleepVote(String worldName, UUID playerId) {
    if (worldName == null || playerId == null) return;
    Set<UUID> voters = localSleepVoters.get(worldName);
    if (voters != null) {
      voters.remove(playerId);
    }
    Map<UUID, String> names = localSleepVoterNames.get(worldName);
    if (names != null) {
      names.remove(playerId);
    }
  }

  private void clearCancelVote(String worldName, UUID playerId) {
    if (worldName == null || playerId == null) return;
    Set<UUID> cancels = localCancelVoters.get(worldName);
    if (cancels != null) {
      cancels.remove(playerId);
    }
    Map<UUID, String> names = localCancelVoterNames.get(worldName);
    if (names != null) {
      names.remove(playerId);
    }
  }

  /** Handles a platform world time-skip event. */
  public void onWorldTimeSkip(WorldTimeSkipContext ctx) {
    if (ctx == null || ctx.isCancelled() || !ctx.isNightSkip()) return;
    LeafSleepConfig cfg = configSupplier.get();
    String worldName = ctx.worldName();
    LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
    if (!worldCfg.enabled()) return;
    String groupId = worldCfg.shardGroupId();
    ensureLocalCountsInitialized(groupId, worldName, worldCfg, cfg);
    GroupQuorumStatus status = evaluateGroupQuorumInternal(groupId, worldName, worldCfg, cfg);
    AtomicInteger warmup = groupWarmupRemaining.get(groupId);
    if ((warmup != null && warmup.get() > 0)
        || groupPassTimeStates.containsKey(groupId)
        || status.cancelQuorumMet()
        || (status.eligibleCount() > 0 && !status.quorumMet())) {
      ctx.cancel();
      return;
    }
    long fullTime = accessor.getWorldFullTime(worldName);
    long currentCommitted = getLastCommittedEpoch(groupId);
    long targetEpoch = Math.max(currentCommitted + 1L, (fullTime / 24000L) + 1L);
    commitEpochAcrossGroup(
        groupId,
        worldName,
        targetEpoch,
        cfg.targetDawnTimeTicks(),
        cfg.clearWeatherOnSkip());
  }

  /** Initiates or votes for sleep via {@code /sleep sleep}. */
  public boolean voteSleep(UUID playerId, String playerName, String worldName) {
    return voteSleep(playerId, playerName, worldName, null);
  }

  /** Initiates or votes for sleep via {@code /sleep sleep [messageId]}. */
  public boolean voteSleep(
      UUID playerId, String playerName, String worldName, String preferredMessageId) {
    LeafSleepConfig cfg = configSupplier.get();
    LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
    if (!worldCfg.enabled()) {
      sendPrefixed(playerId, cfg.messages().notNight(), true);
      return false;
    }
    long worldTime = accessor.getWorldTime(worldName);
    WeatherState weather = accessor.getWeather(worldName);
    if (!cfg.isSleepableWindow(worldTime, weather == WeatherState.THUNDER)) {
      sendPrefixed(playerId, cfg.messages().notNight(), true);
      return false;
    }
    if (!isCallerEligibleInWorld(worldName, playerId, cfg)) {
      if (databaseStore.getCachedOrDefault(playerId).sleepIgnored()) {
        sendPrefixed(playerId, cfg.messages().ignoreEnabled(), true);
      }
      return false;
    }

    long nowMs = System.currentTimeMillis();
    SleepDatabaseStore.PlayerSleepProfile profile = databaseStore.getCachedOrDefault(playerId);
    if (profile.cooldownUntilMs() > nowMs) {
      long remSec = Math.max(1L, (profile.cooldownUntilMs() - nowMs + 999L) / 1000L);
      sendPrefixed(
          playerId,
          cfg.messages().onCooldown().replace("[seconds]", Long.toString(remSec)),
          true);
      return false;
    }

    Set<UUID> voters =
        localSleepVoters.computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet());
    if (!voters.add(playerId)) {
      sendPrefixed(playerId, cfg.messages().alreadySleeping(), true);
      return false;
    }
    String safeName = (playerName == null || playerName.isBlank()) ? "Player" : playerName;
    clearCancelVote(worldName, playerId);
    Set<UUID> leftBed = recentlyLeftBedPlayers.get(worldName);
    if (leftBed != null) {
      leftBed.remove(playerId);
    }
    localSleepVoterNames
        .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
        .put(playerId, safeName);
    groupLastInitiator.put(worldCfg.shardGroupId(), safeName);

    long nextCooldown = nowMs + (cfg.sleepCooldownSeconds() * 1000L);
    SleepDatabaseStore.PlayerSleepProfile updatedProfile = profile.incrementSleeps(nextCooldown);
    if (preferredMessageId != null && !preferredMessageId.isBlank()) {
      updatedProfile =
          new SleepDatabaseStore.PlayerSleepProfile(
              updatedProfile.playerId(),
              updatedProfile.sleepIgnored(),
              preferredMessageId.trim(),
              updatedProfile.cooldownUntilMs(),
              updatedProfile.totalSleeps(),
              updatedProfile.totalCancels());
    }
    databaseStore.savePlayerProfileAsync(updatedProfile);

    ShardCounts local = recomputeLocalCounts(worldName, worldCfg, cfg);
    broadcastFrame(
        SleepSyncFrame.FrameType.SLEEP_VOTE,
        worldCfg.shardGroupId(),
        worldName,
        playerId,
        safeName,
        local);

    GroupQuorumStatus status =
        evaluateGroupQuorumInternal(worldCfg.shardGroupId(), worldName, worldCfg, cfg);
    broadcastSleepProgress(worldName, playerId, safeName, status, cfg);
    updateWarmupState(worldCfg, status, cfg);
    return true;
  }

  /** Cancels or votes against an active sleep countdown across the shard group via {@code /sleep cancel}. */
  public boolean cancelSleep(UUID playerId, String playerName, String worldName) {
    LeafSleepConfig cfg = configSupplier.get();
    LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
    String groupId = worldCfg.shardGroupId();

    ensureLocalCountsInitialized(groupId, worldName, worldCfg, cfg);
    GroupQuorumStatus currentStatus =
        evaluateGroupQuorumInternal(groupId, worldName, worldCfg, cfg);
    boolean hasWarmup =
        groupWarmupRemaining.containsKey(groupId)
            || groupPassTimeStates.containsKey(groupId)
            || currentStatus.sleepingCount() > 0;
    if (!hasWarmup) {
      sendPrefixed(playerId, cfg.messages().cannotCancel(), true);
      return false;
    }

    long nowMs = System.currentTimeMillis();
    boolean consoleCaller = isConsoleCaller(playerId);
    if (!consoleCaller && !isCallerEligibleInWorld(worldName, playerId, cfg)) {
      if (databaseStore.getCachedOrDefault(playerId).sleepIgnored()) {
        sendPrefixed(playerId, cfg.messages().ignoreEnabled(), true);
      }
      return false;
    }
    if (!consoleCaller) {
      Set<UUID> currentSleepVoters =
          localSleepVoters.getOrDefault(worldName, Collections.emptySet());
      boolean cancellingOwnSleepVote = currentSleepVoters.contains(playerId);
      SleepDatabaseStore.PlayerSleepProfile profile = databaseStore.getCachedOrDefault(playerId);
      if (!cancellingOwnSleepVote && profile.cooldownUntilMs() > nowMs) {
        long remSec = Math.max(1L, (profile.cooldownUntilMs() - nowMs + 999L) / 1000L);
        sendPrefixed(
            playerId,
            cfg.messages().onCooldown().replace("[seconds]", Long.toString(remSec)),
            true);
        return false;
      }
      long nextCooldown = nowMs + (cfg.cancelCooldownSeconds() * 1000L);
      databaseStore.savePlayerProfileAsync(profile.incrementCancels(nextCooldown));
    }

    String safeName =
        (playerName == null || playerName.isBlank())
            ? (consoleCaller ? "Console" : "Player")
            : playerName;
    if (!consoleCaller) {
      clearSleepVote(worldName, playerId);
      localCancelVoters
          .computeIfAbsent(worldName, k -> ConcurrentHashMap.newKeySet())
          .add(playerId);
      localCancelVoterNames
          .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
          .put(playerId, safeName);
    }

    ShardCounts localBefore = recomputeLocalCounts(worldName, worldCfg, cfg);
    GroupQuorumStatus statusAfterVote =
        evaluateGroupQuorumInternal(groupId, worldName, worldCfg, cfg);
    if (statusAfterVote.cancelQuorumMet() || consoleCaller) {
      List<String> cancellers =
          statusAfterVote.cancellingPlayers().isEmpty()
              ? List.of(safeName)
              : statusAfterVote.cancellingPlayers();
      applyCancelLocally(groupId, worldName, safeName, cancellers, cfg);
      ShardCounts localAfter = recomputeLocalCounts(worldName, worldCfg, cfg);
      ShardCounts abortPayload =
          new ShardCounts(
              localAfter.shardId(),
              localAfter.shardGroupId(),
              localAfter.worldName(),
              localAfter.eligibleCount(),
              0,
              0,
              localAfter.ignoredCount(),
              localAfter.maxPlayers(),
              List.of(),
              cancellers,
              localAfter.updatedAtMs());
      broadcastFrame(
          SleepSyncFrame.FrameType.CANCEL_SLEEP,
          groupId,
          worldName,
          playerId,
          safeName,
          abortPayload);
    } else {
      if (!statusAfterVote.quorumMet()) {
        clearWarmupFeedback(groupId);
        groupPassTimeStates.remove(groupId);
      }
      broadcastCancelVoteProgress(worldName, safeName, statusAfterVote, cfg);
      broadcastFrame(
          SleepSyncFrame.FrameType.CANCEL_SLEEP,
          groupId,
          worldName,
          playerId,
          safeName,
          localBefore);
    }
    return true;
  }

  private void applyCancelLocally(
      String shardGroupId,
      String worldName,
      String cancellerName,
      List<String> cancellingPlayers,
      LeafSleepConfig cfg) {
    String safeCanceller =
        (cancellerName == null || cancellerName.isBlank()) ? "Player" : cancellerName;
    String cancellerListStr =
        (cancellingPlayers == null || cancellingPlayers.isEmpty())
            ? safeCanceller
            : formatPlayerSet(cancellingPlayers);
    groupLastResetAtMs.put(shardGroupId, System.currentTimeMillis());
    clearWarmupFeedback(shardGroupId);
    groupPassTimeStates.remove(shardGroupId);
    clearWorldVotes(worldName);
    Map<String, ShardCounts> peers = groupShardCounts.get(shardGroupId);
    if (peers != null) {
      for (Map.Entry<String, ShardCounts> e : peers.entrySet()) {
        ShardCounts sc = e.getValue();
        if (sc.shardId().equals(cfg.serverShardId()) && !sc.worldName().equals(worldName)) {
          clearWorldVotes(sc.worldName());
        }
        peers.put(
            e.getKey(),
            new ShardCounts(
                sc.shardId(),
                sc.shardGroupId(),
                sc.worldName(),
                sc.eligibleCount(),
                0,
                0,
                sc.ignoredCount(),
                sc.maxPlayers(),
                List.of(),
                List.of(),
                System.currentTimeMillis()));
      }
    }
    for ( String targetWorld : resolveLocalWorldsForGroup(shardGroupId, worldName, cfg)) {
      clearWorldVotes(targetWorld);
      List<PlayerSleepSnapshot> players = currentWorldPlayers(targetWorld);
      String msg =
          cfg.messages()
              .cancelBroadcast()
              .replace("[player]", safeCanceller)
              .replace("[cancellingPlayers]", cancellerListStr)
              .replace("[world]", targetWorld)
              .replace("[shardGroup]", shardGroupId);
      for (PlayerSleepSnapshot p : players) {
        if (p.sleeping()) {
          recentlyLeftBedPlayers
              .computeIfAbsent(targetWorld, k -> ConcurrentHashMap.newKeySet())
              .add(p.playerId());
          if (cfg.wakeOnCancel()) {
            accessor.wakePlayer(p.playerId(), false);
          }
        }
        sendPrefixed(p.playerId(), msg, false);
      }
    }
  }

  /** Toggles persistent {@code sleepIgnored} state for {@code playerId}. */
  public boolean toggleIgnore(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    LeafSleepConfig cfg = configSupplier.get();
    SleepDatabaseStore.PlayerSleepProfile profile = databaseStore.getCachedOrDefault(playerId);
    boolean nextIgnored = !profile.sleepIgnored();
    databaseStore.savePlayerProfileAsync(profile.withIgnore(nextIgnored));
    Set<String> touchedWorlds = new LinkedHashSet<>();
    for (Map.Entry<String, Set<UUID>> e : localSleepVoters.entrySet()) {
      if (e.getValue().contains(playerId)) {
        touchedWorlds.add(e.getKey());
      }
    }
    for (Map.Entry<String, Set<UUID>> e : localCancelVoters.entrySet()) {
      if (e.getValue().contains(playerId)) {
        touchedWorlds.add(e.getKey());
      }
    }
    if (nextIgnored) {
      for (String worldName : localSleepVoters.keySet()) {
        clearSleepVote(worldName, playerId);
      }
      for (String worldName : localCancelVoters.keySet()) {
        clearCancelVote(worldName, playerId);
      }
    }
    for (String w : accessor.getWorldNames()) {
      for (PlayerSleepSnapshot snap : currentWorldPlayers(w)) {
        if (playerId.equals(snap.playerId())) {
          touchedWorlds.add(w);
        }
      }
    }
    cachedWorldPlayers.clear();
    localCountsDirty.set(true);
    for (String worldName : touchedWorlds) {
      LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
      if (!worldCfg.enabled()) continue;
      ShardCounts local = recomputeLocalCounts(worldName, worldCfg, cfg);
      if (cfg.sharding().enabled()) {
        broadcastFrame(
            SleepSyncFrame.FrameType.SHARD_HEARTBEAT,
            worldCfg.shardGroupId(),
            worldName,
            playerId,
            "",
            local);
      }
      GroupQuorumStatus status =
          evaluateGroupQuorumInternal(worldCfg.shardGroupId(), worldName, worldCfg, cfg);
      updateWarmupState(worldCfg, status, cfg);
    }
    sendPrefixed(
        playerId,
        nextIgnored ? cfg.messages().ignoreEnabled() : cfg.messages().ignoreDisabled(),
        false);
    return nextIgnored;
  }

  /** Returns the current persistent wakeup/sleep message preference ID for {@code playerId} (or {@code "random"}). */
  public String getWakeupMessagePreference(UUID playerId) {
    if (playerId == null) return "random";
    String pref = databaseStore.getCachedOrDefault(playerId).wakeupMsgPref();
    return (pref == null || pref.isBlank()) ? "random" : pref;
  }

  /**
   * Updates or resets the persistent wakeup/sleep message preference for {@code playerId}.
   *
   * @param playerId target player UUID
   * @param messageId configured message ID from {@code sleep.yml}, or {@code "random"}/{@code "reset"}/{@code "clear"}
   * @return {@code true} if the message ID is valid and was saved
   */
  public boolean setWakeupMessagePreference(UUID playerId, String messageId) {
    Objects.requireNonNull(playerId, "playerId");
    LeafSleepConfig cfg = configSupplier.get();
    String raw = messageId == null ? "" : messageId.trim();
    if (raw.isEmpty()
        || raw.equalsIgnoreCase("random")
        || raw.equalsIgnoreCase("reset")
        || raw.equalsIgnoreCase("clear")
        || raw.equalsIgnoreCase("none")) {
      SleepDatabaseStore.PlayerSleepProfile profile = databaseStore.getCachedOrDefault(playerId);
      databaseStore.savePlayerProfileAsync(
          new SleepDatabaseStore.PlayerSleepProfile(
              profile.playerId(),
              profile.sleepIgnored(),
              "random",
              profile.cooldownUntilMs(),
              profile.totalSleeps(),
              profile.totalCancels()));
      sendPrefixed(
          playerId,
          cfg.messages().messagePrefUpdated().replace("[message]", "random"),
          false);
      return true;
    }
    for (LeafSleepConfig.SleepMessageEntry entry : cfg.sleepMessages()) {
      if (entry.id().equalsIgnoreCase(raw)) {
        SleepDatabaseStore.PlayerSleepProfile profile = databaseStore.getCachedOrDefault(playerId);
        databaseStore.savePlayerProfileAsync(
            new SleepDatabaseStore.PlayerSleepProfile(
                profile.playerId(),
                profile.sleepIgnored(),
                entry.id(),
                profile.cooldownUntilMs(),
                profile.totalSleeps(),
                profile.totalCancels()));
        sendPrefixed(
            playerId,
            cfg.messages().messagePrefUpdated().replace("[message]", entry.id()),
            false);
        return true;
      }
    }
    return false;
  }

  /** Sends an interactive clickable {@code [Sleep]} / {@code [Cancel]} action line to an in-game caller if their world is sleepable or in warmup. */
  public void sendInteractiveStatusActionsIfApplicable(UUID callerId, String worldName) {
    if (isConsoleCaller(callerId) || worldName == null || worldName.isBlank()) {
      return;
    }
    LeafSleepConfig cfg = configSupplier.get();
    LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
    if (!worldCfg.enabled()) {
      return;
    }
    String groupId = worldCfg.shardGroupId();
    long worldTime = accessor.getWorldTime(worldName);
    WeatherState weather = accessor.getWeather(worldName);
    boolean sleepable = cfg.isSleepableWindow(worldTime, weather == WeatherState.THUNDER);
    boolean activeWarmup =
        groupWarmupRemaining.containsKey(groupId) || groupPassTimeStates.containsKey(groupId);
    if (!sleepable && !activeWarmup) {
      return;
    }
    String sleepToken =
        tokenService.issue(callerId, new MenuAction.RunCommand("sleep", "sleep"));
    String cancelToken =
        tokenService.issue(callerId, new MenuAction.RunCommand("sleep", "cancel"));
    List<BookMenuSpec.Fragment> fragments = new ArrayList<>();
    fragments.add(new BookMenuSpec.Fragment(cfg.messages().prefix(), "", BookMenuSpec.ClickAction.NONE, ""));
    fragments.add(
        new BookMenuSpec.Fragment(
            formatActionPrompt(cfg.messages().clickSleepLabel(), "/sleep sleep"),
            cfg.messages().clickSleepHover(),
            BookMenuSpec.ClickAction.RUN_COMMAND,
            "/sleep token id=" + sleepToken));
    fragments.add(new BookMenuSpec.Fragment(" ", "", BookMenuSpec.ClickAction.NONE, ""));
    fragments.add(
        new BookMenuSpec.Fragment(
            formatActionPrompt(cfg.messages().clickCancelLabel(), "/sleep cancel"),
            cfg.messages().clickCancelHover(),
            BookMenuSpec.ClickAction.RUN_COMMAND,
            "/sleep token id=" + cancelToken));
    accessor.sendInteractiveLine(callerId, new BookMenuSpec.Line(fragments));
  }

  /** Marks local player snapshots dirty so the next tick refreshes counts immediately. */
  public void markDirty() {
    cachedWorldPlayers.clear();
    localCountsDirty.set(true);
  }

  /** Cleans up any active local sleep or cancel votes and menu tokens when a player disconnects. */
  public void onPlayerQuit(UUID playerId) {
    if (playerId == null) return;
    LeafSleepConfig cfg = configSupplier.get();
    Set<String> touchedWorlds = new LinkedHashSet<>();
    for (Map.Entry<String, Set<UUID>> e : localSleepVoters.entrySet()) {
      if (e.getValue().contains(playerId)) {
        touchedWorlds.add(e.getKey());
      }
    }
    for (Map.Entry<String, Set<UUID>> e : localCancelVoters.entrySet()) {
      if (e.getValue().contains(playerId)) {
        touchedWorlds.add(e.getKey());
      }
    }
    for (String w : accessor.getWorldNames()) {
      for (PlayerSleepSnapshot snap : currentWorldPlayers(w)) {
        if (playerId.equals(snap.playerId())) {
          touchedWorlds.add(w);
        }
      }
    }
    for (String worldName : localSleepVoters.keySet()) {
      clearSleepVote(worldName, playerId);
    }
    for (String worldName : localCancelVoters.keySet()) {
      clearCancelVote(worldName, playerId);
    }
    for (Map.Entry<String, Set<UUID>> entry : activeBossBarViewers.entrySet()) {
      if (entry.getValue().remove(playerId)) {
        accessor.clearBossBar(playerId, warmupBossBarId(entry.getKey()));
      }
    }
    tokenService.invalidateViewer(playerId);
    cachedWorldPlayers.clear();
    localCountsDirty.set(true);
    for (String worldName : touchedWorlds) {
      LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
      if (!worldCfg.enabled()) continue;
      ShardCounts local = recomputeLocalCounts(worldName, worldCfg, cfg, playerId);
      if (cfg.sharding().enabled()) {
        broadcastFrame(
            SleepSyncFrame.FrameType.SHARD_HEARTBEAT,
            worldCfg.shardGroupId(),
            worldName,
            playerId,
            "",
            local);
      }
      GroupQuorumStatus status =
          evaluateGroupQuorumInternal(worldCfg.shardGroupId(), worldName, worldCfg, cfg);
      updateWarmupState(worldCfg, status, cfg);
    }
  }

  /**
   * Redeems a one-time {@link MenuTokenService} token issued in a sleep or cancel progress
   * broadcast and executes the bound {@code /sleep sleep} or {@code /sleep cancel} action.
   */
  public boolean redeemActionToken(
      UUID playerId, String playerName, String worldName, String token) {
    if (playerId == null || token == null || token.isBlank()) {
      return false;
    }
    Optional<MenuAction> actionOpt = tokenService.redeem(playerId, token.trim());
    if (actionOpt.isEmpty()
        || !(actionOpt.get() instanceof MenuAction.RunCommand cmd)
        || cmd.args().isEmpty()) {
      return false;
    }
    String sub = cmd.args().get(cmd.args().size() - 1).trim().toLowerCase(Locale.ROOT);
    if ("cancel".equals(sub)) {
      if (!hasActionPermission(worldName, playerId, "leafsleep.cancel", "oneplayersleep.cancel")) {
        sendPrefixed(playerId, configSupplier.get().messages().noPermission(), true);
        return false;
      }
      return cancelSleep(playerId, playerName, worldName);
    } else if ("sleep".equals(sub)) {
      if (!hasActionPermission(worldName, playerId, "leafsleep.sleep", "oneplayersleep.sleep")) {
        sendPrefixed(playerId, configSupplier.get().messages().noPermission(), true);
        return false;
      }
      return voteSleep(playerId, playerName, worldName);
    }
    return false;
  }

  private boolean hasActionPermission(
      String worldName, UUID playerId, String primaryPerm, String legacyPerm) {
    for (PlayerSleepSnapshot snap : currentWorldPlayers(worldName)) {
      if (playerId.equals(snap.playerId())) {
        return snap.hasPermission(primaryPerm) || snap.hasPermission(legacyPerm);
      }
    }
    return true;
  }

  public MenuTokenService tokenService() {
    return tokenService;
  }

  private static String shardWorldKey(String shardId, String worldName) {
    String safeShard = (shardId == null || shardId.isBlank()) ? "shard-1" : shardId;
    String safeWorld = (worldName == null || worldName.isBlank()) ? "world" : worldName;
    return safeShard + ":" + safeWorld;
  }

  /** Processes an incoming delta frame from a remote shard on {@link SleepSyncTransport}. */
  public void onRemoteFrame(SleepSyncFrame frame) {
    if (frame == null) return;
    LeafSleepConfig cfg = configSupplier.get();
    if (frame.shardId().equals(cfg.serverShardId())) {
      return; // Ignore self-echoes
    }
    String groupId = frame.shardGroupId();
    String worldName = resolveLocalWorldForGroup(groupId, frame.worldName(), cfg);

    Map<String, ShardCounts> map =
        groupShardCounts.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>());
    long committedEpoch = getLastCommittedEpoch(groupId);
    long lastResetAt = groupLastResetAtMs.getOrDefault(groupId, 0L);
    boolean staleHeartbeatVotes =
        frame.type() == SleepSyncFrame.FrameType.SHARD_HEARTBEAT
            && ((frame.targetDayEpoch() > 0 && frame.targetDayEpoch() < committedEpoch)
                || (frame.timestampMs() > 0 && frame.timestampMs() < lastResetAt));
    boolean committedCancelAbort =
        frame.type() == SleepSyncFrame.FrameType.CANCEL_SLEEP && frame.cancellingCount() == 0;
    String remoteWorld =
        (frame.worldName() == null || frame.worldName().isBlank()) ? worldName : frame.worldName();
    map.put(
        shardWorldKey(frame.shardId(), remoteWorld),
        new ShardCounts(
            frame.shardId(),
            groupId,
            remoteWorld,
            frame.eligibleCount(),
            (staleHeartbeatVotes || committedCancelAbort) ? 0 : frame.sleepingCount(),
            (staleHeartbeatVotes || committedCancelAbort) ? 0 : frame.cancellingCount(),
            frame.ignoredCount(),
            frame.maxPlayers(),
            (staleHeartbeatVotes || committedCancelAbort) ? List.of() : frame.sleepingPlayers(),
            (staleHeartbeatVotes || committedCancelAbort) ? List.of() : frame.cancellingPlayers(),
            frame.timestampMs() > 0 ? frame.timestampMs() : System.currentTimeMillis()));

    LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
    ensureLocalCountsInitialized(groupId, worldName, worldCfg, cfg);

    switch (frame.type()) {
      case BED_ENTER, SLEEP_VOTE -> {
        String initiator = frame.playerName().isBlank() ? "Player" : frame.playerName();
        groupLastInitiator.put(groupId, initiator);
        GroupQuorumStatus status =
            evaluateGroupQuorumInternal(groupId, worldName, worldCfg, cfg);
        broadcastSleepProgress(worldName, frame.playerId(), initiator, status, cfg);
        updateWarmupState(worldCfg, status, cfg);
      }
      case BED_LEAVE, SHARD_HEARTBEAT -> {
        GroupQuorumStatus status =
            evaluateGroupQuorumInternal(groupId, worldName, worldCfg, cfg);
        if (!status.quorumMet()) {
          clearWarmupFeedback(groupId);
          groupPassTimeStates.remove(groupId);
        }
        if (frame.targetDayEpoch() > getLastCommittedEpoch(groupId)) {
          seedEpochWatermark(groupId, frame.targetDayEpoch());
          databaseStore.saveEpochWatermarkAsync(
              new SleepDatabaseStore.ShardEpochWatermark(
                  groupId,
                  frame.targetDayEpoch(),
                  accessor.getWorldFullTime(worldName),
                  System.currentTimeMillis()));
        }
      }
      case CANCEL_SLEEP -> {
        GroupQuorumStatus status =
            evaluateGroupQuorumInternal(groupId, worldName, worldCfg, cfg);
        if (committedCancelAbort || status.cancelQuorumMet()) {
          List<String> cancellers =
              !frame.cancellingPlayers().isEmpty()
                  ? frame.cancellingPlayers()
                  : status.cancellingPlayers();
          applyCancelLocally(
              groupId, worldName, frame.playerName(), cancellers, cfg);
        } else {
          if (!status.quorumMet()) {
            clearWarmupFeedback(groupId);
            groupPassTimeStates.remove(groupId);
          }
          broadcastCancelVoteProgress(
              worldName,
              frame.playerName().isBlank() ? "Player" : frame.playerName(),
              status,
              cfg);
        }
      }
      case EPOCH_COMMIT ->
          applyEpochCommit(
              groupId,
              worldName,
              frame.targetDayEpoch(),
              frame.targetDayTimeTicks(),
              frame.clearWeather());
    }
  }

  private void ensureLocalCountsInitialized(
      String groupId,
      String worldName,
      LeafSleepConfig.WorldSleepConfig worldCfg,
      LeafSleepConfig cfg) {
    Map<String, ShardCounts> map = groupShardCounts.get(groupId);
    String localKey = shardWorldKey(cfg.serverShardId(), worldName);
    boolean missingLocal = map == null || !map.containsKey(localKey);
    if (missingLocal || !cachedWorldPlayers.containsKey(worldName) || localCountsDirty.get()) {
      recomputeLocalCounts(worldName, worldCfg, cfg);
    }
  }

  private List<PlayerSleepSnapshot> currentWorldPlayers(String worldName) {
    List<PlayerSleepSnapshot> cached = cachedWorldPlayers.get(worldName);
    if (cached != null && !localCountsDirty.get()) {
      return cached;
    }
    List<PlayerSleepSnapshot> fresh = accessor.getWorldPlayers(worldName);
    cachedWorldPlayers.put(worldName, fresh);
    return fresh;
  }

  /**
   * Per-tick coordinator pulse: advances warmup timers, reconciles local player counts (when
   * dirtied by an event or throttled to {@code 1 Hz} when idle), and emits periodic heartbeats.
   */
  public void tick() {
    long startNanos = System.nanoTime();
    try {
      tickInternal();
    } finally {
      long elapsed = Math.max(0L, System.nanoTime() - startNanos);
      avgTickDurationNanos = avgTickDurationNanos + (elapsed - avgTickDurationNanos) / 40.0;
      maxTickDurationNanos.updateAndGet(prev -> Math.max(prev, elapsed));
    }
  }

  private void tickInternal() {
    tickCounter++;
    LeafSleepConfig cfg = configSupplier.get();
    List<String> worlds = new ArrayList<>(accessor.getWorldNames());
    if (worlds.isEmpty()) {
      worlds.addAll(cfg.worlds().keySet());
    }
    boolean emitHeartbeat =
        cfg.sharding().enabled()
            && (tickCounter % Math.max(20, cfg.sharding().heartbeatIntervalTicks()) == 0);
    boolean dirty = localCountsDirty.getAndSet(false);
    boolean periodicIdleRefresh = (tickCounter % IDLE_RECOMPUTE_INTERVAL_TICKS == 0);
    Set<String> steppedGroups = new HashSet<>();

    for (String worldName : worlds) {
      LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
      if (!worldCfg.enabled()) continue;
      String groupId = worldCfg.shardGroupId();
      long worldTime = accessor.getWorldTime(worldName);
      WeatherState weather = accessor.getWeather(worldName);
      boolean sleepable = cfg.isSleepableWindow(worldTime, weather == WeatherState.THUNDER);
      boolean hasWarmup = groupWarmupRemaining.containsKey(groupId);
      boolean passTimeActive = groupPassTimeStates.containsKey(groupId);
      boolean hasVoters =
          !localSleepVoters.getOrDefault(worldName, Collections.emptySet()).isEmpty()
              || !localCancelVoters.getOrDefault(worldName, Collections.emptySet()).isEmpty();
      if (!sleepable && !passTimeActive && (hasWarmup || hasVoters)) {
        clearWarmupFeedback(groupId);
        clearWorldVotes(worldName);
        hasWarmup = false;
        hasVoters = false;
        dirty = true;
      }
      Map<String, ShardCounts> shardMap = groupShardCounts.get(groupId);
      String localKey = shardWorldKey(cfg.serverShardId(), worldName);
      ShardCounts local = shardMap != null ? shardMap.get(localKey) : null;
      if (local == null || dirty || periodicIdleRefresh || emitHeartbeat) {
        local = recomputeLocalCounts(worldName, worldCfg, cfg);
      }
      GroupQuorumStatus status = evaluateGroupQuorumInternal(groupId, worldName, worldCfg, cfg);

      if (steppedGroups.add(groupId)) {
        long tod = Math.floorMod(worldTime, 24000L);
        boolean sunriseLockIn =
            passTimeActive
                && !status.cancelQuorumMet()
                && (tod >= 22000L || tod >= cfg.nightEndTicks() || tod < cfg.nightStartTicks());
        if ((sleepable || passTimeActive) && (status.quorumMet() || sunriseLockIn)) {
          PassTimeState activePassTime = groupPassTimeStates.get(groupId);
          if (activePassTime != null) {
            stepPassTime(groupId, worldName, activePassTime, cfg);
          } else {
            boolean newlyStarted = !groupWarmupRemaining.containsKey(groupId);
            AtomicInteger rem =
                groupWarmupRemaining.computeIfAbsent(
                    groupId, k -> new AtomicInteger(worldCfg.warmupDelayTicks()));
            int left = rem.decrementAndGet();
            if (left <= 0) {
              clearWarmupFeedback(groupId);
              triggerOrAdvanceToDawn(groupId, worldName, worldCfg, cfg);
            } else {
              boolean sendActionBarTick =
                  newlyStarted
                      || (left % 20 == 0)
                      || (left == worldCfg.warmupDelayTicks() - 1);
              dispatchWarmupFeedback(
                  groupId, worldName, worldCfg, status, cfg, left, sendActionBarTick);
            }
          }
        } else {
          clearWarmupFeedback(groupId);
          groupPassTimeStates.remove(groupId);
        }
      }

      if (emitHeartbeat) {
        broadcastFrame(
            SleepSyncFrame.FrameType.SHARD_HEARTBEAT,
            groupId,
            worldName,
            null,
            "",
            local);
      }
    }
  }

  /** Returns whether a smooth {@code PassTime} animation is currently advancing {@code shardGroupId}. */
  public boolean isPassTimeActive(String shardGroupId) {
    return shardGroupId != null && groupPassTimeStates.containsKey(shardGroupId);
  }

  private void triggerOrAdvanceToDawn(
      String groupId,
      String worldName,
      LeafSleepConfig.WorldSleepConfig worldCfg,
      LeafSleepConfig cfg) {
    long fullTime = accessor.getWorldFullTime(worldName);
    long currentCommitted = getLastCommittedEpoch(groupId);
    long targetEpoch = Math.max(currentCommitted + 1L, (fullTime / 24000L) + 1L);
    long tod = Math.floorMod(fullTime, 24000L);
    boolean inNightWindow = tod >= cfg.nightStartTicks() && tod <= cfg.nightEndTicks();
    int inc = worldCfg.increment();
    if (inc <= 0 || inc >= 24000 || !inNightWindow) {
      commitEpochAcrossGroup(
          groupId,
          worldName,
          targetEpoch,
          cfg.targetDawnTimeTicks(),
          cfg.clearWeatherOnSkip());
      return;
    }
    long targetFullTime = computeNextDawnFullTime(fullTime, cfg.targetDawnTimeTicks());
    PassTimeState state =
        groupPassTimeStates.computeIfAbsent(
            groupId,
            k ->
                new PassTimeState(
                    targetEpoch, targetFullTime, inc, worldCfg.timeBetweenIncrements()));
    stepPassTime(groupId, worldName, state, cfg);
  }

  private static long computeNextDawnFullTime(long currentFull, long targetDawnTimeTicks) {
    long safeFull = Math.max(0L, currentFull);
    long targetTod = Math.max(0L, targetDawnTimeTicks % 24000L);
    long currentDay = safeFull / 24000L;
    long candidate = (currentDay * 24000L) + targetTod;
    return candidate > safeFull ? candidate : ((currentDay + 1L) * 24000L) + targetTod;
  }

  private void stepPassTime(
      String groupId, String worldName, PassTimeState state, LeafSleepConfig cfg) {
    if (state.ticksUntilNextStep > 0) {
      state.ticksUntilNextStep--;
      return;
    }
    long currentFull = accessor.getWorldFullTime(worldName);
    long nextFull = currentFull + state.increment;
    if (nextFull >= state.targetFullTime) {
      groupPassTimeStates.remove(groupId);
      commitEpochAcrossGroup(
          groupId,
          worldName,
          state.targetDayEpoch,
          cfg.targetDawnTimeTicks(),
          cfg.clearWeatherOnSkip());
      return;
    }
    long newTod = Math.floorMod(nextFull, 24000L);
    for (String targetWorld : resolveLocalWorldsForGroup(groupId, worldName, cfg)) {
      long wFull = accessor.getWorldFullTime(targetWorld);
      long wTod = Math.floorMod(wFull, 24000L);
      long syncedFull = (wFull - wTod) + newTod;
      if (syncedFull < wFull) {
        syncedFull += 24000L;
      }
      long targetTod = Math.max(0L, cfg.targetDawnTimeTicks() % 24000L);
      long worldTargetFull = computeNextDawnFullTime(wFull, targetTod);
      accessor.setWorldTime(targetWorld, Math.min(syncedFull, worldTargetFull));
    }
    state.ticksUntilNextStep = state.timeBetweenIncrements - 1;
  }

  private void updateWarmupState(
      LeafSleepConfig.WorldSleepConfig worldCfg,
      GroupQuorumStatus status,
      LeafSleepConfig cfg) {
    String groupId = worldCfg.shardGroupId();
    if (!status.quorumMet()) {
      clearWarmupFeedback(groupId);
      groupPassTimeStates.remove(groupId);
      return;
    }
    if (groupPassTimeStates.containsKey(groupId)) {
      return;
    }
    if (worldCfg.warmupDelayTicks() <= 0) {
      clearWarmupFeedback(groupId);
      triggerOrAdvanceToDawn(groupId, worldCfg.worldName(), worldCfg, cfg);
    } else {
      boolean newlyStarted = !groupWarmupRemaining.containsKey(groupId);
      AtomicInteger rem =
          groupWarmupRemaining.computeIfAbsent(
              groupId, k -> new AtomicInteger(worldCfg.warmupDelayTicks()));
      if (newlyStarted) {
        dispatchWarmupFeedback(
            groupId, worldCfg.worldName(), worldCfg, status, cfg, rem.get(), true);
      }
    }
  }

  /** Returns the bossbar identifier used for a shard group's active warmup countdown. */
  public static String warmupBossBarId(String shardGroupId) {
    return "leafsleep-warmup-"
        + ((shardGroupId == null || shardGroupId.isBlank()) ? "overworld" : shardGroupId);
  }

  private void dispatchWarmupFeedback(
      String groupId,
      String worldName,
      LeafSleepConfig.WorldSleepConfig worldCfg,
      GroupQuorumStatus status,
      LeafSleepConfig cfg,
      int remainingTicks,
      boolean sendActionBar) {
    int totalTicks = Math.max(1, worldCfg.warmupDelayTicks());
    double progress =
        Math.max(0.0, Math.min(1.0, (double) remainingTicks / (double) totalTicks));
    int wholeSeconds = Math.max(1, (int) Math.ceil(remainingTicks / 20.0));
    String secondsDecimal = String.format(Locale.ROOT, "%.1f", remainingTicks / 20.0);
    String barId = warmupBossBarId(groupId);
    boolean bossBarEnabled =
        worldCfg.warmupBossBar()
            && cfg.messages().warmupBossBar() != null
            && !cfg.messages().warmupBossBar().isBlank();
    boolean actionBarEnabled =
        sendActionBar
            && worldCfg.warmupActionBar()
            && cfg.messages().warmupActionBar() != null
            && !cfg.messages().warmupActionBar().isBlank();

    Set<UUID> currentViewers = new HashSet<>();
    for (String targetWorld : resolveLocalWorldsForGroup(groupId, worldName, cfg)) {
      String bossTitle =
          bossBarEnabled
              ? formatWarmupTemplate(
                  cfg.messages().warmupBossBar(),
                  targetWorld,
                  groupId,
                  status,
                  wholeSeconds,
                  secondsDecimal,
                  remainingTicks)
              : null;
      String actionText =
          actionBarEnabled
              ? formatWarmupTemplate(
                  cfg.messages().warmupActionBar(),
                  targetWorld,
                  groupId,
                  status,
                  wholeSeconds,
                  secondsDecimal,
                  remainingTicks)
              : null;
      for (PlayerSleepSnapshot recipient : currentWorldPlayers(targetWorld)) {
        UUID pid = recipient.playerId();
        if (pid == null) continue;
        if (bossBarEnabled) {
          currentViewers.add(pid);
          accessor.showBossBar(pid, barId, bossTitle, progress);
        }
        if (actionBarEnabled) {
          accessor.sendActionBar(pid, actionText);
        }
      }
    }

    if (bossBarEnabled) {
      Set<UUID> tracked =
          activeBossBarViewers.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet());
      for (UUID staleId : new ArrayList<>(tracked)) {
        if (!currentViewers.contains(staleId)) {
          tracked.remove(staleId);
          accessor.clearBossBar(staleId, barId);
        }
      }
      tracked.addAll(currentViewers);
    } else {
      Set<UUID> prev = activeBossBarViewers.remove(groupId);
      if (prev != null) {
        for (UUID pid : prev) {
          accessor.clearBossBar(pid, barId);
        }
      }
    }
  }

  private String formatWarmupTemplate(
      String template,
      String worldName,
      String groupId,
      GroupQuorumStatus status,
      int wholeSeconds,
      String secondsDecimal,
      int remainingTicks) {
    String initiator = groupLastInitiator.getOrDefault(groupId, "Player");
    return template
        .replace("[seconds]", Integer.toString(wholeSeconds))
        .replace("[secondsDecimal]", secondsDecimal)
        .replace("[ticks]", Integer.toString(Math.max(0, remainingTicks)))
        .replace("[player]", initiator)
        .replace("[world]", worldName)
        .replace("[shardGroup]", groupId)
        .replace("[sleeping]", Integer.toString(status.sleepingCount()))
        .replace("[required]", Integer.toString(status.requiredCount()))
        .replace("[cancelling]", Integer.toString(status.cancellingCount()))
        .replace("[cancelRequired]", Integer.toString(status.cancelRequiredCount()))
        .replace("[needToSkip]", Integer.toString(status.needToSkip()))
        .replace("[needToCancel]", Integer.toString(status.needToCancel()))
        .replace("[eligible]", Integer.toString(status.eligibleCount()))
        .replace("[maxPlayers]", Integer.toString(status.maxPlayers()))
        .replace("[requirements]", status.requirementsSummary())
        .replace("[sleepingPlayers]", formatPlayerSet(status.sleepingPlayers()))
        .replace("[cancellingPlayers]", formatPlayerSet(status.cancellingPlayers()));
  }

  private void clearWarmupFeedback(String shardGroupId) {
    if (shardGroupId == null) return;
    groupWarmupRemaining.remove(shardGroupId);
    Set<UUID> viewers = activeBossBarViewers.remove(shardGroupId);
    if (viewers != null && !viewers.isEmpty()) {
      String barId = warmupBossBarId(shardGroupId);
      for (UUID viewerId : viewers) {
        accessor.clearBossBar(viewerId, barId);
      }
    }
  }

  /** Clears all active warmup bossbars and timers across all shard groups. */
  public void clearAllWarmupFeedback() {
    for (String groupId : new ArrayList<>(activeBossBarViewers.keySet())) {
      clearWarmupFeedback(groupId);
    }
    groupWarmupRemaining.clear();
  }

  private void commitEpochAcrossGroup(
      String shardGroupId,
      String worldName,
      long targetDayEpoch,
      long targetDayTimeTicks,
      boolean clearWeather) {
    boolean committed =
        applyEpochCommit(
            shardGroupId, worldName, targetDayEpoch, targetDayTimeTicks, clearWeather);
    if (committed) {
      LeafSleepConfig cfg = configSupplier.get();
      ShardCounts local = recomputeLocalCounts(worldName, cfg.resolveWorldConfig(worldName), cfg);
      long committedEpoch = Math.max(targetDayEpoch, getLastCommittedEpoch(shardGroupId));
      SleepSyncFrame commitFrame =
          new SleepSyncFrame(
              SleepSyncFrame.FrameType.EPOCH_COMMIT,
              cfg.serverShardId(),
              shardGroupId,
              worldName,
              null,
              groupLastInitiator.getOrDefault(shardGroupId, "Player"),
              local.eligibleCount(),
              0,
              local.ignoredCount(),
              committedEpoch,
              targetDayTimeTicks,
              clearWeather,
              System.currentTimeMillis());
      transport.publish(commitFrame);
    }
  }

  /**
   * Applies an {@code EPOCH_COMMIT} transition idempotently using an atomic high-water mark CAS
   * ({@code ADR-001 Decision 2}).
   *
   * @return {@code true} if this epoch was newly committed on this shard, {@code false} if already
   *     applied
   */
  public boolean applyEpochCommit(
      String shardGroupId,
      String worldName,
      long targetDayEpoch,
      long targetDayTimeTicks,
      boolean clearWeather) {
    AtomicLong watermark =
        lastCommittedEpochs.computeIfAbsent(shardGroupId, k -> new AtomicLong(0L));
    while (true) {
      long prev = watermark.get();
      if (targetDayEpoch <= prev) {
        duplicateEpochsRejected.incrementAndGet();
        return false;
      }
      if (watermark.compareAndSet(prev, targetDayEpoch)) {
        break;
      }
    }

    totalCommittedEpochs.incrementAndGet();
    LeafSleepConfig cfg = configSupplier.get();
    groupLastResetAtMs.put(shardGroupId, System.currentTimeMillis());
    clearWarmupFeedback(shardGroupId);
    groupPassTimeStates.remove(shardGroupId);
    clearWorldVotes(worldName);
    Map<String, ShardCounts> peers = groupShardCounts.get(shardGroupId);
    if (peers != null) {
      for (Map.Entry<String, ShardCounts> e : peers.entrySet()) {
        ShardCounts sc = e.getValue();
        if (sc.shardId().equals(cfg.serverShardId()) && !sc.worldName().equals(worldName)) {
          clearWorldVotes(sc.worldName());
        }
        peers.put(
            e.getKey(),
            new ShardCounts(
                sc.shardId(),
                sc.shardGroupId(),
                sc.worldName(),
                sc.eligibleCount(),
                0,
                0,
                sc.ignoredCount(),
                sc.maxPlayers(),
                List.of(),
                List.of(),
                System.currentTimeMillis()));
      }
    }

    String initiator = groupLastInitiator.getOrDefault(shardGroupId, "Player");
    List<Effect<?>> effects =
        EffectFactory.buildEffects("leafsleep.effect.", qualifyEffectNodes(cfg.wakeupEffects()));
    long primaryFullTime = 0L;

    for (String targetWorld : resolveLocalWorldsForGroup(shardGroupId, worldName, cfg)) {
      clearWorldVotes(targetWorld);
      long currentFull = accessor.getWorldFullTime(targetWorld);
      long newFullTime = computeNextDawnFullTime(currentFull, targetDayTimeTicks);
      if (primaryFullTime == 0L || targetWorld.equals(worldName)) {
        primaryFullTime = newFullTime;
      }
      accessor.setWorldTime(targetWorld, newFullTime);
      if (clearWeather || cfg.clearWeatherOnSkip()) {
        accessor.setWeather(targetWorld, WeatherState.CLEAR, 6000);
      } else if (cfg.clearThunderOnSkip()
          && accessor.getWeather(targetWorld) == WeatherState.THUNDER) {
        accessor.setWeather(targetWorld, WeatherState.RAIN, 6000);
      }

      List<PlayerSleepSnapshot> players = accessor.getWorldPlayers(targetWorld);
      for (PlayerSleepSnapshot p : players) {
        if (cfg.resetPhantomStatistic() && isPlayerEligible(p, cfg)) {
          accessor.resetTimeSinceRest(p.playerId());
        }
        if (p.sleeping()) {
          recentlyLeftBedPlayers
              .computeIfAbsent(targetWorld, k -> ConcurrentHashMap.newKeySet())
              .add(p.playerId());
          accessor.wakePlayer(p.playerId(), true);
        }
        if (!effects.isEmpty()) {
          Object effectTarget = accessor.wrapEffectPlayer(p.playerId());
          if (effectTarget != null) {
            for (Effect<?> effect : effects) {
              try {
                Effect<?> instance = effect.clone();
                instance.setTarget(effectTarget);
                instance.run();
              } catch (Exception ignored) {
              }
            }
          }
        }
        SleepDatabaseStore.PlayerSleepProfile prof = databaseStore.getCachedOrDefault(p.playerId());
        LeafSleepConfig.SleepMessageEntry msgEntry =
            cfg.selectMessage(prof.wakeupMsgPref(), random);
        String wakeupText =
            msgEntry
                .wakeupText()
                .replace("[world]", targetWorld)
                .replace("[shardGroup]", shardGroupId)
                .replace("[player]", initiator)
                .replace("[epoch]", Long.toString(targetDayEpoch));
        sendPrefixed(p.playerId(), wakeupText, false);
      }
    }
    if (!effects.isEmpty()) {
      accessor.log(
          java.util.logging.Level.FINE,
          "[LeafSleep] Executed " + effects.size() + " wakeup effects for " + worldName);
    }

    databaseStore.saveEpochWatermarkAsync(
        new SleepDatabaseStore.ShardEpochWatermark(
            shardGroupId, targetDayEpoch, primaryFullTime, System.currentTimeMillis()));
    return true;
  }

  private static Collection<String> qualifyEffectNodes(List<String> rawEffects) {
    if (rawEffects == null || rawEffects.isEmpty()) return List.of();
    List<String> out = new ArrayList<>(rawEffects.size());
    for (String r : rawEffects) {
      if (r != null && !r.isBlank()) {
        out.add("leafsleep.effect." + r.trim());
      }
    }
    return out;
  }

  private void clearWorldVotes(String worldName) {
    if (worldName == null) return;
    Set<UUID> voters = localSleepVoters.get(worldName);
    if (voters != null) {
      voters.clear();
    }
    Map<UUID, String> voterNames = localSleepVoterNames.get(worldName);
    if (voterNames != null) {
      voterNames.clear();
    }
    Set<UUID> cancels = localCancelVoters.get(worldName);
    if (cancels != null) {
      cancels.clear();
    }
    Map<UUID, String> cancelNames = localCancelVoterNames.get(worldName);
    if (cancelNames != null) {
      cancelNames.clear();
    }
  }

  /** Recomputes this shard's local {@link ShardCounts} for {@code worldName}. */
  public ShardCounts recomputeLocalCounts(
      String worldName,
      LeafSleepConfig.WorldSleepConfig worldCfg,
      LeafSleepConfig cfg) {
    return recomputeLocalCounts(worldName, worldCfg, cfg, null);
  }

  private ShardCounts recomputeLocalCounts(
      String worldName,
      LeafSleepConfig.WorldSleepConfig worldCfg,
      LeafSleepConfig cfg,
      UUID excludedSleepingPlayerId) {
    List<PlayerSleepSnapshot> snapshots = accessor.getWorldPlayers(worldName);
    cachedWorldPlayers.put(worldName, snapshots);
    Set<UUID> voters = localSleepVoters.getOrDefault(worldName, Collections.emptySet());
    Map<UUID, String> voterNames =
        localSleepVoterNames.getOrDefault(worldName, Collections.emptyMap());
    Set<UUID> cancels = localCancelVoters.getOrDefault(worldName, Collections.emptySet());
    Map<UUID, String> cancelNames =
        localCancelVoterNames.getOrDefault(worldName, Collections.emptyMap());
    Set<UUID> leftBed =
        recentlyLeftBedPlayers.getOrDefault(worldName, Collections.emptySet());
    int eligible = 0;
    int sleeping = 0;
    int ignored = 0;
    List<String> sleepingList = new ArrayList<>();
    List<String> cancellingList = new ArrayList<>();
    Set<UUID> sleepingIds = new HashSet<>();
    Set<UUID> cancellingIds = new HashSet<>();
    Set<UUID> snapshotPlayerIds = new HashSet<>(snapshots.size());

    for (PlayerSleepSnapshot snap : snapshots) {
      if (excludedSleepingPlayerId != null && excludedSleepingPlayerId.equals(snap.playerId())) {
        // When called from onPlayerQuit, player may still be in world.getPlayers() during the quit event
        if (!voters.contains(snap.playerId()) && !snap.sleeping()) {
          continue;
        }
      }
      snapshotPlayerIds.add(snap.playerId());
      if (!snap.sleeping()) {
        leftBed.remove(snap.playerId());
      }
      if (!isPlayerEligible(snap, cfg)) {
        ignored++;
        continue;
      }
      eligible++;
      String pName = snap.playerName().isBlank() ? "Player" : snap.playerName();
      boolean isExcluded =
          (excludedSleepingPlayerId != null && excludedSleepingPlayerId.equals(snap.playerId()))
              || leftBed.contains(snap.playerId());
      if (cancels.contains(snap.playerId())) {
        if (cancellingIds.add(snap.playerId())) {
          cancellingList.add(pName);
        }
      } else if (voters.contains(snap.playerId()) || (!isExcluded && snap.sleeping())) {
        if (sleepingIds.add(snap.playerId())) {
          sleeping++;
          sleepingList.add(pName);
        }
      }
    }
    // Include any explicit voters not yet reflected in snapshot list
    for (UUID vId : voters) {
      if (snapshotPlayerIds.contains(vId)
          || (excludedSleepingPlayerId != null && excludedSleepingPlayerId.equals(vId))
          || databaseStore.getCachedOrDefault(vId).sleepIgnored()) {
        continue;
      }
      String name = voterNames.getOrDefault(vId, "Player");
      if (!cancels.contains(vId) && sleepingIds.add(vId)) {
        sleeping++;
        sleepingList.add(name);
      }
    }
    for (UUID cId : cancels) {
      if (snapshotPlayerIds.contains(cId)
          || (excludedSleepingPlayerId != null && excludedSleepingPlayerId.equals(cId))
          || databaseStore.getCachedOrDefault(cId).sleepIgnored()) {
        continue;
      }
      String name = cancelNames.getOrDefault(cId, "Player");
      if (cancellingIds.add(cId)) {
        cancellingList.add(name);
      }
    }
    int maxPlayers = Math.max(1, accessor.getMaxPlayers());
    ShardCounts counts =
        new ShardCounts(
            cfg.serverShardId(),
            worldCfg.shardGroupId(),
            worldName,
            eligible,
            sleeping,
            cancellingList.size(),
            ignored,
            maxPlayers,
            sleepingList,
            cancellingList,
            System.currentTimeMillis());
    groupShardCounts
        .computeIfAbsent(worldCfg.shardGroupId(), k -> new ConcurrentHashMap<>())
        .put(shardWorldKey(cfg.serverShardId(), worldName), counts);
    return counts;
  }

  private boolean isPlayerEligible(PlayerSleepSnapshot snap, LeafSleepConfig cfg) {
    if (snap == null) return false;
    if (snap.sleepIgnored()) return false;
    if (snap.hasPermission("leafsleep.bypass") || snap.hasPermission("oneplayersleep.bypass")) {
      // Only treat explicit bypass when not wildcard "*"
      if (!snap.permissions().contains("*")) {
        return false;
      }
    }
    if (cfg.excludeAfkAndVanished() && snap.vanishedOrAfk()) {
      return false;
    }
    if (cfg.excludeCreativeAndSpectator()) {
      String gm = snap.gameMode();
      if ("CREATIVE".equalsIgnoreCase(gm) || "SPECTATOR".equalsIgnoreCase(gm)) {
        return false;
      }
    }
    SleepDatabaseStore.PlayerSleepProfile prof = databaseStore.getCachedOrDefault(snap.playerId());
    return !prof.sleepIgnored();
  }

  private static boolean isConsoleCaller(UUID playerId) {
    return playerId == null
        || (playerId.getMostSignificantBits() == 0L && playerId.getLeastSignificantBits() == 0L);
  }

  private boolean isCallerEligibleInWorld(String worldName, UUID playerId, LeafSleepConfig cfg) {
    if (playerId == null) return false;
    if (databaseStore.getCachedOrDefault(playerId).sleepIgnored()) {
      return false;
    }
    for (PlayerSleepSnapshot snap : currentWorldPlayers(worldName)) {
      if (playerId.equals(snap.playerId())) {
        return isPlayerEligible(snap, cfg);
      }
    }
    return true;
  }

  /** Evaluates the aggregated {@link GroupQuorumStatus} across all live shards in {@code groupId}. */
  public GroupQuorumStatus evaluateGroupQuorum(
      String shardGroupId, String worldName, LeafSleepConfig cfg) {
    LeafSleepConfig.WorldSleepConfig worldCfg = cfg.resolveWorldConfig(worldName);
    String effectiveGroupId =
        (shardGroupId == null || shardGroupId.isBlank() || shardGroupId.equals(worldName))
            ? worldCfg.shardGroupId()
            : shardGroupId;
    recomputeLocalCounts(worldName, worldCfg, cfg);
    return evaluateGroupQuorumInternal(effectiveGroupId, worldName, worldCfg, cfg);
  }

  private GroupQuorumStatus evaluateGroupQuorumInternal(
      String shardGroupId,
      String worldName,
      LeafSleepConfig.WorldSleepConfig worldCfg,
      LeafSleepConfig cfg) {
    Map<String, ShardCounts> shards =
        groupShardCounts.getOrDefault(shardGroupId, Collections.emptyMap());
    long nowMs = System.currentTimeMillis();
    long peerStaleWindowMs =
        Math.max(PEER_STALE_MS, cfg.sharding().heartbeatIntervalTicks() * 50L * 3L);
    int totalEligible = 0;
    int totalSleeping = 0;
    int totalCancelling = 0;
    int totalIgnored = 0;
    int totalMaxPlayers = 0;
    Set<String> activeShardIds = new HashSet<>();
    List<String> allSleepingPlayers = new ArrayList<>();
    List<String> allCancellingPlayers = new ArrayList<>();

    for (ShardCounts sc : shards.values()) {
      if (sc.shardId().equals(cfg.serverShardId())
          || (nowMs - sc.updatedAtMs()) <= peerStaleWindowMs) {
        totalEligible += sc.eligibleCount();
        totalSleeping += sc.sleepingCount();
        totalCancelling += sc.cancellingCount();
        totalIgnored += sc.ignoredCount();
        if (activeShardIds.add(sc.shardId())) {
          totalMaxPlayers += Math.max(1, sc.maxPlayers());
        }
        for (String sName : sc.sleepingPlayers()) {
          if (!allSleepingPlayers.contains(sName)) {
            allSleepingPlayers.add(sName);
          }
        }
        for (String cName : sc.cancellingPlayers()) {
          if (!allCancellingPlayers.contains(cName)) {
            allCancellingPlayers.add(cName);
          }
        }
      }
    }
    int activeShards = activeShardIds.size();
    if (totalMaxPlayers <= 0) {
      totalMaxPlayers = Math.max(1, accessor.getMaxPlayers());
    }

    int required =
        worldCfg
            .policy()
            .effectiveRequiredSleepers(
                totalEligible,
                worldCfg.quorumValue(),
                totalMaxPlayers,
                worldCfg.sleepCurve(),
                worldCfg.cancelPolicy(),
                totalCancelling);
    int cancelRequired =
        QuorumPolicy.requiredCancels(
            totalEligible, totalMaxPlayers, worldCfg.cancelPolicy(), worldCfg.cancelCurve());
    boolean cancelMet = totalCancelling >= cancelRequired && totalCancelling > 0;
    boolean met =
        totalEligible > 0
            && totalSleeping > 0
            && totalSleeping >= required
            && !cancelMet;
    int needToSkip = met ? 0 : Math.max(1, required - totalSleeping);
    int needToCancel = Math.max(1, cancelRequired - totalCancelling);
    AtomicInteger warmup = groupWarmupRemaining.get(shardGroupId);
    int warmupTicks = warmup != null ? Math.max(0, warmup.get()) : worldCfg.warmupDelayTicks();
    return new GroupQuorumStatus(
        shardGroupId,
        worldName,
        worldCfg.policy(),
        worldCfg.quorumValue(),
        worldCfg.cancelPolicy(),
        totalEligible,
        totalMaxPlayers,
        totalSleeping,
        totalCancelling,
        totalIgnored,
        required,
        cancelRequired,
        needToSkip,
        needToCancel,
        allSleepingPlayers,
        allCancellingPlayers,
        met,
        cancelMet,
        warmupTicks,
        getLastCommittedEpoch(shardGroupId),
        Math.max(1, activeShards));
  }

  private void broadcastFrame(
      SleepSyncFrame.FrameType type,
      String shardGroupId,
      String worldName,
      UUID playerId,
      String playerName,
      ShardCounts local) {
    LeafSleepConfig cfg = configSupplier.get();
    SleepSyncFrame frame =
        new SleepSyncFrame(
            type,
            cfg.serverShardId(),
            shardGroupId,
            worldName,
            playerId,
            playerName,
            local.eligibleCount(),
            local.sleepingCount(),
            local.cancellingCount(),
            local.ignoredCount(),
            local.maxPlayers(),
            local.sleepingPlayers(),
            local.cancellingPlayers(),
            getLastCommittedEpoch(shardGroupId),
            accessor.getWorldTime(worldName),
            cfg.clearWeatherOnSkip(),
            System.currentTimeMillis());
    transport.publish(frame);
  }

  private void broadcastSleepProgress(
      String worldName,
      UUID sleeperId,
      String sleeperName,
      GroupQuorumStatus status,
      LeafSleepConfig cfg) {
    SleepDatabaseStore.PlayerSleepProfile prof = databaseStore.getCachedOrDefault(sleeperId);
    LeafSleepConfig.SleepMessageEntry entry = cfg.selectMessage(prof.wakeupMsgPref(), random);
    String sleepingSet = formatPlayerSet(status.sleepingPlayers());
    String cancellingSet = formatPlayerSet(status.cancellingPlayers());
    String reqSummary = status.requirementsSummary();
    for (String targetWorld :
        resolveLocalWorldsForGroup(status.shardGroupId(), worldName, cfg)) {
      for (PlayerSleepSnapshot recipient : currentWorldPlayers(targetWorld)) {
        String cancelToken =
            tokenService.issue(recipient.playerId(), new MenuAction.RunCommand("sleep", "cancel"));
        String sleepToken =
            tokenService.issue(recipient.playerId(), new MenuAction.RunCommand("sleep", "sleep"));
        String template =
            entry
                .sleepText()
                .replace("[player]", sleeperName == null ? "Player" : sleeperName)
                .replace("[world]", targetWorld)
                .replace("[shardGroup]", status.shardGroupId())
                .replace("[sleeping]", Integer.toString(status.sleepingCount()))
                .replace("[required]", Integer.toString(status.requiredCount()))
                .replace("[cancelling]", Integer.toString(status.cancellingCount()))
                .replace("[cancelRequired]", Integer.toString(status.cancelRequiredCount()))
                .replace("[needToSkip]", Integer.toString(status.needToSkip()))
                .replace("[needToCancel]", Integer.toString(status.needToCancel()))
                .replace("[requirements]", reqSummary)
                .replace("[sleepingPlayers]", sleepingSet)
                .replace("[cancellingPlayers]", cancellingSet)
                .replace("[eligible]", Integer.toString(status.eligibleCount()))
                .replace("[maxPlayers]", Integer.toString(status.maxPlayers()))
                .replace("[cancelToken]", cancelToken)
                .replace("[sleepToken]", sleepToken);
        sendInteractivePromptLine(
            recipient.playerId(),
            cfg.messages().prefix() + template,
            formatActionPrompt(entry.cancelPrompt(), "/sleep cancel"),
            cfg.messages().clickCancelHover(),
            "/sleep token id=" + cancelToken,
            formatActionPrompt(entry.sleepPrompt(), "/sleep sleep"),
            cfg.messages().clickSleepHover(),
            "/sleep token id=" + sleepToken);
      }
    }
  }

  private void broadcastCancelVoteProgress(
      String worldName,
      String cancellerName,
      GroupQuorumStatus status,
      LeafSleepConfig cfg) {
    String sleepingSet = formatPlayerSet(status.sleepingPlayers());
    String cancellingSet = formatPlayerSet(status.cancellingPlayers());
    String reqSummary = status.requirementsSummary();
    LeafSleepConfig.SleepMessageEntry entry = cfg.selectMessage(null, random);
    for (String targetWorld :
        resolveLocalWorldsForGroup(status.shardGroupId(), worldName, cfg)) {
      for (PlayerSleepSnapshot recipient : currentWorldPlayers(targetWorld)) {
        String cancelToken =
            tokenService.issue(recipient.playerId(), new MenuAction.RunCommand("sleep", "cancel"));
        String sleepToken =
            tokenService.issue(recipient.playerId(), new MenuAction.RunCommand("sleep", "sleep"));
        String template =
            cfg.messages()
                .cancelVoteBroadcast()
                .replace("[player]", cancellerName == null ? "Player" : cancellerName)
                .replace("[world]", targetWorld)
                .replace("[shardGroup]", status.shardGroupId())
                .replace("[sleeping]", Integer.toString(status.sleepingCount()))
                .replace("[required]", Integer.toString(status.requiredCount()))
                .replace("[cancelling]", Integer.toString(status.cancellingCount()))
                .replace("[cancelRequired]", Integer.toString(status.cancelRequiredCount()))
                .replace("[needToSkip]", Integer.toString(status.needToSkip()))
                .replace("[needToCancel]", Integer.toString(status.needToCancel()))
                .replace("[requirements]", reqSummary)
                .replace("[sleepingPlayers]", sleepingSet)
                .replace("[cancellingPlayers]", cancellingSet)
                .replace("[cancelToken]", cancelToken)
                .replace("[sleepToken]", sleepToken);
        sendInteractivePromptLine(
            recipient.playerId(),
            cfg.messages().prefix() + template,
            formatActionPrompt(entry.cancelPrompt(), "/sleep cancel"),
            cfg.messages().clickCancelHover(),
            "/sleep token id=" + cancelToken,
            formatActionPrompt(entry.sleepPrompt(), "/sleep sleep"),
            cfg.messages().clickSleepHover(),
            "/sleep token id=" + sleepToken);
      }
    }
  }

  private void sendInteractivePromptLine(
      UUID playerId,
      String templateWithPlaceholders,
      String cancelDisplay,
      String cancelHover,
      String cancelCommand,
      String sleepDisplay,
      String sleepHover,
      String sleepCommand) {
    List<BookMenuSpec.Fragment> fragments = new ArrayList<>();
    int idx = 0;
    while (idx < templateWithPlaceholders.length()) {
      int nextCancel = templateWithPlaceholders.indexOf("[cancelPrompt]", idx);
      int nextSleep = templateWithPlaceholders.indexOf("[sleepPrompt]", idx);
      int nextToken;
      boolean isCancel;
      if (nextCancel >= 0 && (nextSleep < 0 || nextCancel < nextSleep)) {
        nextToken = nextCancel;
        isCancel = true;
      } else if (nextSleep >= 0) {
        nextToken = nextSleep;
        isCancel = false;
      } else {
        String tail = templateWithPlaceholders.substring(idx);
        if (!tail.isEmpty()) {
          fragments.add(BookMenuSpec.Fragment.plain(tail));
        }
        break;
      }
      if (nextToken > idx) {
        fragments.add(
            BookMenuSpec.Fragment.plain(templateWithPlaceholders.substring(idx, nextToken)));
      }
      if (isCancel) {
        fragments.add(
            BookMenuSpec.Fragment.runCommand(cancelDisplay, cancelHover, cancelCommand));
        idx = nextToken + "[cancelPrompt]".length();
      } else {
        fragments.add(
            BookMenuSpec.Fragment.runCommand(sleepDisplay, sleepHover, sleepCommand));
        idx = nextToken + "[sleepPrompt]".length();
      }
    }
    if (fragments.isEmpty()) {
      fragments.add(BookMenuSpec.Fragment.plain(templateWithPlaceholders));
    }
    accessor.sendInteractiveLine(playerId, new BookMenuSpec.Line(fragments));
  }

  private static String formatActionPrompt(String label, String command) {
    String safeLabel = (label == null || label.isBlank()) ? command : label;
    if (safeLabel.contains(command)) {
      return safeLabel;
    }
    return safeLabel + " (" + command + ")";
  }

  /** Formats a compact comma-separated player name list (capping at 5 names + overflow count). */
  public static String formatPlayerSet(List<String> players) {
    if (players == null || players.isEmpty()) {
      return "none";
    }
    int maxShown = 5;
    if (players.size() <= maxShown) {
      return String.join(", ", players);
    }
    List<String> head = players.subList(0, maxShown);
    return String.join(", ", head) + " +" + (players.size() - maxShown) + " more";
  }

  private String resolveLocalWorldForGroup(
      String shardGroupId, String fallbackWorld, LeafSleepConfig cfg) {
    List<String> matches = resolveLocalWorldsForGroup(shardGroupId, fallbackWorld, cfg);
    return matches.get(0);
  }

  private List<String> resolveLocalWorldsForGroup(
      String shardGroupId, String fallbackWorld, LeafSleepConfig cfg) {
    Set<String> out = new LinkedHashSet<>();
    if (fallbackWorld != null && !fallbackWorld.isBlank()) {
      LeafSleepConfig.WorldSleepConfig fbCfg = cfg.resolveWorldConfig(fallbackWorld);
      if (fbCfg.enabled() && fbCfg.shardGroupId().equalsIgnoreCase(shardGroupId)) {
        out.add(fallbackWorld);
      }
    }
    for (String loaded : accessor.getWorldNames()) {
      LeafSleepConfig.WorldSleepConfig wCfg = cfg.resolveWorldConfig(loaded);
      if (wCfg.enabled() && wCfg.shardGroupId().equalsIgnoreCase(shardGroupId)) {
        out.add(loaded);
      }
    }
    for (LeafSleepConfig.WorldSleepConfig w : cfg.worlds().values()) {
      if (w.enabled() && w.shardGroupId().equalsIgnoreCase(shardGroupId)) {
        out.add(w.worldName());
      }
    }
    if (out.isEmpty()) {
      out.add((fallbackWorld == null || fallbackWorld.isBlank()) ? "world" : fallbackWorld);
    }
    return List.copyOf(out);
  }

  private void sendPrefixed(UUID playerId, String message, boolean error) {
    LeafSleepConfig cfg = configSupplier.get();
    accessor.sendMessage(playerId, cfg.messages().prefix() + message, error);
  }

  public long getLastCommittedEpoch(String shardGroupId) {
    AtomicLong watermark = lastCommittedEpochs.get(shardGroupId);
    return watermark != null ? watermark.get() : 0L;
  }

  public long totalCommittedEpochs() {
    return totalCommittedEpochs.get();
  }

  public long duplicateEpochsRejected() {
    return duplicateEpochsRejected.get();
  }

  /** Returns the rolling average coordinator {@link #tick()} duration in milliseconds. */
  public double avgTickDurationMs() {
    return Math.max(0.0, avgTickDurationNanos / 1_000_000.0);
  }

  /** Returns the peak observed coordinator {@link #tick()} duration in milliseconds. */
  public double maxTickDurationMs() {
    return Math.max(0.0, maxTickDurationNanos.get() / 1_000_000.0);
  }

  public Map<String, GroupQuorumStatus> allGroupStatuses() {
    LeafSleepConfig cfg = configSupplier.get();
    Map<String, GroupQuorumStatus> out = new LinkedHashMap<>();
    List<String> worlds = new ArrayList<>(accessor.getWorldNames());
    if (worlds.isEmpty()) {
      worlds.addAll(cfg.worlds().keySet());
    }
    if (worlds.isEmpty()) {
      worlds.add("world");
    }
    for (String w : worlds) {
      LeafSleepConfig.WorldSleepConfig wCfg = cfg.resolveWorldConfig(w);
      if (!wCfg.enabled()) continue;
      ensureLocalCountsInitialized(wCfg.shardGroupId(), w, wCfg, cfg);
      out.put(
          wCfg.shardGroupId(),
          evaluateGroupQuorumInternal(wCfg.shardGroupId(), w, wCfg, cfg));
    }
    return out;
  }
}
