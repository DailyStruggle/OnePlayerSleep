package net.leaf.sleep.common;

import io.github.dailystruggle.bit.BitFinding;
import io.github.dailystruggle.commandsapi.common.CommandsAPI;
import io.github.dailystruggle.menu.api.MenuTokenService;
import io.github.dailystruggle.server.api.ServerAccessor;
import io.github.dailystruggle.server.api.WorldEventHook;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.leaf.sleep.common.command.LeafSleepCommand;

/**
 * Platform-neutral lifecycle coordinator for {@code LeafSleep} across Paper/Spigot/Folia, Fabric,
 * and NeoForge ({@code LeafSleep ADR-001}, {@code ADR-002}).
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 */
public final class LeafSleepRuntime implements AutoCloseable {

  private final ServerAccessor accessor;
  private final MenuTokenService tokenService = new MenuTokenService();
  private final List<AutoCloseable> subscriptions = new ArrayList<>();
  private volatile LeafSleepConfig config;
  private volatile SleepDatabaseStore databaseStore;
  private volatile SleepSyncTransport transport;
  private volatile ShardSleepCoordinator coordinator;
  private volatile LeafSleepBitSuite bitSuite;
  private volatile boolean started;

  public LeafSleepRuntime(ServerAccessor accessor) {
    this.accessor = Objects.requireNonNull(accessor, "accessor");
  }

  /** Loads configuration, initializes async SQL storage, and opens the sync transport. */
  public synchronized void load() {
    Path root = accessor.getRootFolder();
    this.config = LeafSleepConfig.load(root);

    SleepDatabaseStore newStore = new SleepDatabaseStore(accessor, root, config.storage());
    newStore.initializeAsync().exceptionally(ex -> false);
    this.databaseStore = newStore;

    SleepSyncTransport newTransport = SleepSyncTransport.create(accessor, config);
    this.transport = newTransport;

    ShardSleepCoordinator newCoordinator =
        new ShardSleepCoordinator(
            accessor, () -> this.config, newStore, newTransport, tokenService);
    this.coordinator = newCoordinator;
    newTransport.start(newCoordinator::onRemoteFrame);

    for (LeafSleepConfig.WorldSleepConfig w : config.worlds().values()) {
      newStore
          .loadEpochWatermarkAsync(w.shardGroupId())
          .thenAccept(
              opt ->
                  opt.ifPresent(
                      wm ->
                          newCoordinator.seedEpochWatermark(
                              wm.shardGroupId(), wm.lastCommittedEpoch())));
    }

    this.bitSuite =
        new LeafSleepBitSuite(
            accessor,
            () -> this.config,
            () -> this.databaseStore,
            () -> this.transport,
            () -> this.coordinator);
  }

  /** Starts event subscriptions, command registration ({@code /sleep}, {@code /ops}), and tick loop. */
  public synchronized void start() {
    if (this.config == null || this.coordinator == null) {
      load();
    }
    if (started) return;
    started = true;

    WorldEventHook worldEvents = accessor.getWorldEventHook();
    subscriptions.add(worldEvents.onPlayerBedEnter(ctx -> coordinator.onBedEnter(ctx)));
    subscriptions.add(worldEvents.onPlayerBedLeave(ctx -> coordinator.onBedLeave(ctx)));
    subscriptions.add(worldEvents.onWorldTimeSkip(ctx -> coordinator.onWorldTimeSkip(ctx)));
    subscriptions.add(
        accessor
            .getPlayerLifecycleHook()
            .onPlayerJoin(
                playerId ->
                    databaseStore
                        .loadPlayerProfileAsync(playerId)
                        .thenAccept(
                            prof -> {
                              ShardSleepCoordinator c = this.coordinator;
                              if (c != null) {
                                c.markDirty();
                              }
                            })));
    subscriptions.add(
        accessor
            .getPlayerLifecycleHook()
            .onPlayerQuit(
                playerId -> {
                  ShardSleepCoordinator c = this.coordinator;
                  if (c != null) {
                    c.onPlayerQuit(playerId);
                  }
                  SleepDatabaseStore store = this.databaseStore;
                  if (store != null) {
                    store.evictPlayerProfile(playerId);
                  }
                }));

    LeafSleepCommand sleepCmd =
        new LeafSleepCommand(
            "sleep",
            accessor,
            () -> this.config,
            () -> this.coordinator,
            () -> this.transport,
            () -> this.databaseStore,
            () -> this.bitSuite,
            this::reloadAndValidate);
    accessor.registerCommands(sleepCmd, "ops", "playersleep");

    // Also register /ops and /playersleep directly for runtimes that do not alias root commands automatically
    LeafSleepCommand opsCmd =
        new LeafSleepCommand(
            "ops",
            accessor,
            () -> this.config,
            () -> this.coordinator,
            () -> this.transport,
            () -> this.databaseStore,
            () -> this.bitSuite,
            this::reloadAndValidate);
    accessor.registerCommands(opsCmd);

    LeafSleepCommand playerSleepCmd =
        new LeafSleepCommand(
            "playersleep",
            accessor,
            () -> this.config,
            () -> this.coordinator,
            () -> this.transport,
            () -> this.databaseStore,
            () -> this.bitSuite,
            this::reloadAndValidate);
    accessor.registerCommands(playerSleepCmd);

    Runnable tickTask =
        () -> {
          if (!this.started) return;
          CommandsAPI.execute();
          ShardSleepCoordinator c = this.coordinator;
          if (c != null) {
            c.tick();
          }
        };
    accessor
        .scheduler()
        .ifPresentOrElse(
            s -> {
              var handle = s.runSyncTimer(tickTask, 1L, 1L);
              if (handle != null) {
                subscriptions.add(handle::cancel);
              }
            },
            () -> accessor.scheduleRepeatingTask(tickTask, 1L, 1L));
  }

  /**
   * Validates YAML files via {@code config-bit-api} and hot-reloads when zero {@code ERROR}
   * findings exist ({@code ADR-002 Decision 4}).
   *
   * @return list of error messages (empty when reload succeeded)
   */
  public synchronized List<String> reloadAndValidate() {
    Path root = accessor.getRootFolder();
    List<BitFinding> findings = LeafSleepConfig.validateDirectory(root);
    List<String> errors = new ArrayList<>();
    for (BitFinding f : findings) {
      if (f.severity() == Level.ERROR) {
        errors.add(f.source() + ":" + f.path() + " (" + f.message() + ")");
      }
    }
    if (!errors.isEmpty()) {
      return errors;
    }

    LeafSleepConfig prevCfg = this.config;
    LeafSleepConfig nextCfg = LeafSleepConfig.load(root);
    this.config = nextCfg;
    SleepDatabaseStore activeStore = this.databaseStore;
    if (activeStore == null
        || prevCfg == null
        || !prevCfg.storage().equals(nextCfg.storage())) {
      SleepDatabaseStore nextStore = new SleepDatabaseStore(accessor, root, nextCfg.storage());
      nextStore.initializeAsync().exceptionally(ex -> false);
      this.databaseStore = nextStore;
      if (activeStore != null) {
        activeStore.close();
      }
      activeStore = nextStore;
    }
    SleepSyncTransport oldTransport = this.transport;
    if (oldTransport != null) {
      oldTransport.close();
    }
    SleepSyncTransport nextTransport = SleepSyncTransport.create(accessor, nextCfg);
    this.transport = nextTransport;
    ShardSleepCoordinator prevCoord = this.coordinator;
    ShardSleepCoordinator nextCoord =
        new ShardSleepCoordinator(
            accessor, () -> this.config, activeStore, nextTransport, tokenService);
    if (prevCoord != null) {
      prevCoord.clearAllWarmupFeedback();
      prevCoord.exportEpochWatermarks().forEach(nextCoord::seedEpochWatermark);
    }
    if (activeStore != null) {
      for (LeafSleepConfig.WorldSleepConfig w : nextCfg.worlds().values()) {
        activeStore
            .loadEpochWatermarkAsync(w.shardGroupId())
            .thenAccept(
                opt ->
                    opt.ifPresent(
                        wm ->
                            nextCoord.seedEpochWatermark(
                                wm.shardGroupId(), wm.lastCommittedEpoch())));
      }
    }
    this.coordinator = nextCoord;
    nextTransport.start(nextCoord::onRemoteFrame);
    return List.of();
  }

  public LeafSleepConfig config() {
    return config;
  }

  public SleepDatabaseStore databaseStore() {
    return databaseStore;
  }

  public SleepSyncTransport transport() {
    return transport;
  }

  public ShardSleepCoordinator coordinator() {
    return coordinator;
  }

  public LeafSleepBitSuite bitSuite() {
    return bitSuite;
  }

  @Override
  public synchronized void close() {
    for (AutoCloseable sub : subscriptions) {
      try {
        sub.close();
      } catch (Exception ignored) {
      }
    }
    subscriptions.clear();
    ShardSleepCoordinator c = this.coordinator;
    if (c != null) {
      c.clearAllWarmupFeedback();
    }
    tokenService.invalidateAll();
    if (transport != null) {
      transport.close();
    }
    if (databaseStore != null) {
      databaseStore.close();
    }
    started = false;
  }
}
