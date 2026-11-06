package net.leaf.sleep.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dailystruggle.commandsapi.common.CommandsAPI;
import io.github.dailystruggle.menu.api.MenuTokenService;
import io.github.dailystruggle.server.api.DispatchingPlayerLifecycleHook;
import io.github.dailystruggle.server.api.DispatchingWorldEventHook;
import io.github.dailystruggle.server.api.PlayerBedEnterContext;
import io.github.dailystruggle.server.api.PlayerLifecycleHook;
import io.github.dailystruggle.server.api.PlayerSleepSnapshot;
import io.github.dailystruggle.server.api.ServerAccessor;
import io.github.dailystruggle.server.api.TaskHandle;
import io.github.dailystruggle.server.api.TaskScheduler;
import io.github.dailystruggle.server.api.WeatherState;
import io.github.dailystruggle.server.api.WorldEventHook;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import net.leaf.sleep.common.command.LeafSleepCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit and multi-shard simulation tests for {@code LeafSleep} ({@code ADR-001}, {@code ADR-002}).
 */
class LeafSleepCoordinatorAndSyncTest {

  private static final class MockShardServerAccessor implements ServerAccessor {
    private final Path rootFolder;
    private final AtomicLong fullTime = new AtomicLong(13000L);
    private final Map<String, Long> worldFullTimes = new ConcurrentHashMap<>();
    private final AtomicInteger maxPlayers = new AtomicInteger(20);
    private final AtomicInteger worldPlayerSnapshotsQueried = new AtomicInteger();
    private final AtomicReference<WeatherState> weather =
        new AtomicReference<>(WeatherState.THUNDER);
    private final List<PlayerSleepSnapshot> players = new CopyOnWriteArrayList<>();
    private final Map<String, List<PlayerSleepSnapshot>> worldPlayers = new ConcurrentHashMap<>();
    private volatile List<String> worldNames = List.of("world");
    private final Set<UUID> wokenPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> phantomResetPlayers = ConcurrentHashMap.newKeySet();
    private final List<String> sentMessages = new CopyOnWriteArrayList<>();
    private final List<String> sentActionBars = new CopyOnWriteArrayList<>();
    private final Map<String, String> activeBossBars = new ConcurrentHashMap<>();
    private final List<Double> bossBarProgressHistory = new CopyOnWriteArrayList<>();
    private final List<io.github.dailystruggle.server.api.BookMenuSpec.Line> interactiveLines =
        new CopyOnWriteArrayList<>();
    private final DispatchingWorldEventHook worldEvents = new DispatchingWorldEventHook();
    private final DispatchingPlayerLifecycleHook lifecycleEvents =
        new DispatchingPlayerLifecycleHook();
    private final Map<String, List<BiConsumer<UUID, byte[]>>> channels = new ConcurrentHashMap<>();

    MockShardServerAccessor(Path rootFolder) {
      this.rootFolder = rootFolder;
    }

    void setPlayers(List<PlayerSleepSnapshot> snapshots) {
      players.clear();
      players.addAll(snapshots);
    }

    void setWorldPlayers(String worldName, List<PlayerSleepSnapshot> snapshots) {
      worldPlayers.put(worldName, List.copyOf(snapshots));
    }

    void setWorldNames(List<String> names) {
      this.worldNames = List.copyOf(names);
    }

    void setMaxPlayers(int max) {
      maxPlayers.set(max);
    }

    @Override
    public int getMaxPlayers() {
      return maxPlayers.get();
    }

    @Override
    public String getServerVersion() {
      return "1.21.4";
    }

    @Override
    public String getPlatform() {
      return "paper";
    }

    @Override
    public Integer getServerIntVersion() {
      return 21;
    }

    @Override
    public void log(Level level, String message) {}

    @Override
    public void log(Level level, String message, Throwable throwable) {}

    @Override
    public File getPluginDirectory() {
      return rootFolder.toFile();
    }

    @Override
    public Path getRootFolder() {
      return rootFolder;
    }

    @Override
    public List<String> getWorldNames() {
      return worldNames;
    }

    @Override
    public WorldEventHook getWorldEventHook() {
      return worldEvents;
    }

    @Override
    public PlayerLifecycleHook getPlayerLifecycleHook() {
      return lifecycleEvents;
    }

    @Override
    public Optional<TaskScheduler> scheduler() {
      return Optional.of(
          new TaskScheduler() {
            @Override
            public TaskHandle runAsync(Runnable task) {
              task.run();
              return new TaskHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                  return false;
                }
              };
            }

            @Override
            public TaskHandle runSync(Runnable task) {
              task.run();
              return new TaskHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                  return false;
                }
              };
            }
          });
    }

    @Override
    public long getWorldTime(String worldName) {
      return Math.floorMod(fullTime.get(), 24000L);
    }

    @Override
    public long getWorldFullTime(String worldName) {
      return fullTime.get();
    }

    @Override
    public void setWorldTime(String worldName, long timeTicks) {
      fullTime.set(timeTicks);
    }

    @Override
    public WeatherState getWeather(String worldName) {
      return weather.get();
    }

    @Override
    public void setWeather(String worldName, WeatherState state, int durationTicks) {
      weather.set(state);
    }

    @Override
    public List<PlayerSleepSnapshot> getWorldPlayers(String worldName) {
      worldPlayerSnapshotsQueried.incrementAndGet();
      List<PlayerSleepSnapshot> perWorld = worldPlayers.get(worldName);
      if (perWorld != null) {
        return perWorld;
      }
      return List.copyOf(players);
    }

    @Override
    public void wakePlayer(UUID playerId, boolean setSpawn) {
      wokenPlayers.add(playerId);
    }

    @Override
    public void resetTimeSinceRest(UUID playerId) {
      phantomResetPlayers.add(playerId);
    }

    @Override
    public void sendMessage(UUID playerId, String message, boolean error) {
      sentMessages.add(message);
    }

    @Override
    public void sendActionBar(UUID playerId, String message) {
      if (message != null) {
        sentActionBars.add(message);
      }
    }

    @Override
    public void showBossBar(UUID playerId, String barId, String title, double progress) {
      if (playerId != null && barId != null) {
        activeBossBars.put(playerId + ":" + barId, title + "@" + progress);
        bossBarProgressHistory.add(progress);
      }
    }

    @Override
    public void clearBossBar(UUID playerId, String barId) {
      if (playerId != null && barId != null) {
        activeBossBars.remove(playerId + ":" + barId);
      }
    }

    @Override
    public void sendInteractiveLine(
        UUID playerId, io.github.dailystruggle.server.api.BookMenuSpec.Line line) {
      if (line != null) {
        interactiveLines.add(line);
        sentMessages.add(line.plainText());
      }
    }

    @Override
    public AutoCloseable registerMessagingChannel(
        String channel, BiConsumer<UUID, byte[]> receiver) {
      List<BiConsumer<UUID, byte[]>> list =
          channels.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>());
      list.add(receiver);
      return () -> list.remove(receiver);
    }

    @Override
    public boolean sendPluginMessage(String channel, byte[] payload) {
      List<BiConsumer<UUID, byte[]>> list = channels.get(channel);
      if (list == null || list.isEmpty()) return false;
      for (BiConsumer<UUID, byte[]> r : list) {
        r.accept(null, payload.clone());
      }
      return true;
    }
  }

  @Test
  @DisplayName("ADR-001: Single-shard ONE_PLAYER sleep advances dawn, clears weather, and resets phantom stat")
  void singleShardOnePlayerSleepAdvancesToDawn(@TempDir Path tempDir) throws Exception {
    MockShardServerAccessor accessor = new MockShardServerAccessor(tempDir);
    LeafSleepConfig baseCfg = LeafSleepConfig.load(tempDir);
    assertTrue(LeafSleepConfig.validateDirectory(tempDir).isEmpty());

    SleepDatabaseStore store = new SleepDatabaseStore(accessor, tempDir, baseCfg.storage());
    assertTrue(store.initializeAsync().get(5, TimeUnit.SECONDS));

    SleepSyncTransport.LocalOnlyTransport transport =
        new SleepSyncTransport.LocalOnlyTransport(32);
    ShardSleepCoordinator coordinator =
        new ShardSleepCoordinator(
            accessor,
            () -> baseCfg,
            store,
            transport,
            new MenuTokenService());
    transport.start(coordinator::onRemoteFrame);

    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    accessor.setPlayers(
        List.of(
            new PlayerSleepSnapshot(
                p1, "Alice", true, 10, false, "SURVIVAL", false, 80000, Set.of("leafsleep.sleep")),
            new PlayerSleepSnapshot(
                p2, "Bob", false, 0, false, "SURVIVAL", false, 90000, Set.of("leafsleep.sleep"))));

    coordinator.onBedEnter(
        new PlayerBedEnterContext(p1, "Alice", "world", 0, 64, 0, "OK", perm -> true));

    ShardSleepCoordinator.GroupQuorumStatus status =
        coordinator.evaluateGroupQuorum("overworld-main", "world", baseCfg);
    assertTrue(status.quorumMet());
    assertEquals(2, status.eligibleCount());
    assertEquals(1, status.sleepingCount());
    assertEquals(1, status.requiredCount());

    String expectedBarId = ShardSleepCoordinator.warmupBossBarId("overworld-main");
    assertTrue(
        accessor.activeBossBars.containsKey(p1 + ":" + expectedBarId)
            && accessor.activeBossBars.containsKey(p2 + ":" + expectedBarId),
        "Warmup bossbar must be shown immediately to all players in the world when quorum is met");
    assertTrue(
        accessor.sentActionBars.stream().anyMatch(a -> a.contains("3s") && a.contains("1/1")),
        "Warmup actionbar must display initial 3s countdown when quorum is met");

    for (int i = 0; i < 60; i++) {
      coordinator.tick();
    }

    assertTrue(
        accessor.activeBossBars.isEmpty(),
        "Warmup bossbar must be cleared automatically once warmupDelayTicks completes");
    assertTrue(
        accessor.sentActionBars.stream().anyMatch(a -> a.contains("2s")),
        "Warmup actionbar must emit 2s countdown update during warmup");
    assertTrue(
        accessor.sentActionBars.stream().anyMatch(a -> a.contains("1s")),
        "Warmup actionbar must emit 1s countdown update during warmup");
    assertTrue(
        accessor.bossBarProgressHistory.size() >= 60
            && accessor.bossBarProgressHistory.get(0) == 1.0
            && accessor.bossBarProgressHistory.get(accessor.bossBarProgressHistory.size() - 1)
                < 0.05,
        "Warmup bossbar progress must drain smoothly from 1.0 toward 0.0 over warmupDelayTicks");

    // After 60 warmup ticks, OnePlayerSleep smooth PassTime begins (increment=150, timeBetweenIncrements=2)
    assertTrue(
        coordinator.isPassTimeActive("overworld-main"),
        "Smooth PassTime animation must be active immediately after warmupDelayTicks");
    assertEquals(
        13150L,
        accessor.getWorldFullTime("world"),
        "First PassTime step on tick 60 must advance world time by increment (150)");
    assertEquals(
        0L,
        coordinator.getLastCommittedEpoch("overworld-main"),
        "Epoch must not commit until smooth PassTime reaches targetDawnTimeTicks");

    // Tick 61 waits (timeBetweenIncrements=2); tick 62 advances by another 150 ticks to 13300
    coordinator.tick();
    assertEquals(13150L, accessor.getWorldFullTime("world"));
    coordinator.tick();
    assertEquals(13300L, accessor.getWorldFullTime("world"));

    // Simulate vanilla sunrise bed ejection at >= 22000 ticks during PassTime and verify it does not abort
    for (int i = 0; i < 120; i++) {
      coordinator.tick();
    }
    assertTrue(accessor.getWorldTime("world") >= 22000L);
    coordinator.onBedLeave(
        new io.github.dailystruggle.server.api.PlayerBedLeaveContext(
            p1, "Alice", "world", 0, 64, 0, accessor.getWorldTime("world")));
    assertTrue(
        coordinator.isPassTimeActive("overworld-main"),
        "Vanilla sunrise bed leave at >= 22000 ticks must not abort active PassTime");

    // Complete remaining PassTime steps to dawn (24000L)
    for (int i = 0; i < 30 && coordinator.isPassTimeActive("overworld-main"); i++) {
      coordinator.tick();
    }

    assertFalse(coordinator.isPassTimeActive("overworld-main"));
    assertEquals(1L, coordinator.getLastCommittedEpoch("overworld-main"));
    assertEquals(24000L, accessor.getWorldFullTime("world"));
    assertEquals(0L, accessor.getWorldTime("world"));
    assertEquals(WeatherState.CLEAR, accessor.getWeather("world"));
    assertTrue(accessor.phantomResetPlayers.contains(p1));
    assertTrue(accessor.phantomResetPlayers.contains(p2));

    // Verify mid-animation /sleep cancel halts PassTime at intermediate world clock without committing epoch
    accessor.fullTime.set(37000L); // Day 1 night (tod = 13000)
    accessor.wokenPlayers.clear();
    store.savePlayerProfileAsync(SleepDatabaseStore.PlayerSleepProfile.defaults(p1)).get(5, TimeUnit.SECONDS);
    store.savePlayerProfileAsync(SleepDatabaseStore.PlayerSleepProfile.defaults(p2)).get(5, TimeUnit.SECONDS);
    assertTrue(coordinator.voteSleep(p1, "Alice", "world"));
    for (int i = 0; i < 64; i++) {
      coordinator.tick();
    }
    assertTrue(coordinator.isPassTimeActive("overworld-main"));
    long midAnimationFullTime = accessor.getWorldFullTime("world");
    assertTrue(midAnimationFullTime > 37000L && midAnimationFullTime < 48000L);
    assertTrue(coordinator.cancelSleep(p2, "Bob", "world"));
    assertFalse(
        coordinator.isPassTimeActive("overworld-main"),
        "/sleep cancel during PassTime must immediately halt smooth time-advance");
    assertEquals(
        1L,
        coordinator.getLastCommittedEpoch("overworld-main"),
        "Cancelled PassTime must not commit next day epoch");
    assertEquals(
        midAnimationFullTime,
        accessor.getWorldFullTime("world"),
        "World clock must remain at intermediate time when PassTime is cancelled");

    store.close();
    transport.close();
  }

  @Test
  @DisplayName("ADR-001: Multi-shard quorum aggregation, cross-shard cancel, and idempotent EPOCH_COMMIT CAS")
  void multiShardQuorumCancelAndIdempotentEpochCas(@TempDir Path tempDir) throws Exception {
    Path dirA = tempDir.resolve("shardA");
    Path dirB = tempDir.resolve("shardB");
    Files.createDirectories(dirA);
    Files.createDirectories(dirB);

    MockShardServerAccessor accessorA = new MockShardServerAccessor(dirA);
    MockShardServerAccessor accessorB = new MockShardServerAccessor(dirB);

    LeafSleepConfig defaults = LeafSleepConfig.load(dirA);
    Map<String, LeafSleepConfig.WorldSleepConfig> majorityWorld =
        Map.of(
            "world",
            new LeafSleepConfig.WorldSleepConfig(
                "world", true, "overworld-main", QuorumPolicy.ABSOLUTE_MAJORITY, 50, 5));

    LeafSleepConfig cfgA =
        new LeafSleepConfig(
            "shard-a",
            QuorumPolicy.ABSOLUTE_MAJORITY,
            50,
            5,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            new LeafSleepConfig.ShardingConfig(
                true,
                LeafSleepConfig.TransportType.LOCAL_ONLY,
                "leafsleep:sync",
                "127.0.0.1",
                25620,
                List.of(),
                "LEAFSLEEP_HMAC_SECRET",
                100),
            defaults.storage(),
            defaults.messages(),
            defaults.wakeupEffects(),
            defaults.sleepMessages(),
            majorityWorld);

    LeafSleepConfig cfgB =
        new LeafSleepConfig(
            "shard-b",
            QuorumPolicy.ABSOLUTE_MAJORITY,
            50,
            5,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            cfgA.sharding(),
            defaults.storage(),
            defaults.messages(),
            defaults.wakeupEffects(),
            defaults.sleepMessages(),
            majorityWorld);

    SleepDatabaseStore storeA = new SleepDatabaseStore(accessorA, dirA, cfgA.storage());
    SleepDatabaseStore storeB = new SleepDatabaseStore(accessorB, dirB, cfgB.storage());
    storeA.initializeAsync().get(5, TimeUnit.SECONDS);
    storeB.initializeAsync().get(5, TimeUnit.SECONDS);

    SleepSyncTransport.LocalOnlyTransport busA = new SleepSyncTransport.LocalOnlyTransport(64);
    SleepSyncTransport.LocalOnlyTransport busB = new SleepSyncTransport.LocalOnlyTransport(64);
    busA.connectPeer(busB);

    ShardSleepCoordinator coordA =
        new ShardSleepCoordinator(accessorA, () -> cfgA, storeA, busA, null);
    ShardSleepCoordinator coordB =
        new ShardSleepCoordinator(accessorB, () -> cfgB, storeB, busB, null);
    busA.start(coordA::onRemoteFrame);
    busB.start(coordB::onRemoteFrame);

    UUID a1 = UUID.randomUUID();
    UUID a2 = UUID.randomUUID();
    UUID b1 = UUID.randomUUID();
    UUID b2 = UUID.randomUUID();

    // 2 eligible players on Shard A (1 sleeping), 2 eligible players on Shard B (0 sleeping)
    // Total eligible = 4, ABSOLUTE_MAJORITY requires 3 sleepers.
    accessorA.setPlayers(
        List.of(
            new PlayerSleepSnapshot(a1, "A1", true, 5, false, "SURVIVAL", false, 1000, Set.of("*")),
            new PlayerSleepSnapshot(
                a2, "A2", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));
    accessorB.setPlayers(
        List.of(
            new PlayerSleepSnapshot(
                b1, "B1", false, 0, false, "SURVIVAL", false, 1000, Set.of("*")),
            new PlayerSleepSnapshot(
                b2, "B2", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));

    coordA.onBedEnter(new PlayerBedEnterContext(a1, "A1", "world", 0, 64, 0, "OK", p -> true));
    coordB.voteSleep(b1, "B1", "world"); // Now 2/4 sleeping -> still < 3 required!

    ShardSleepCoordinator.GroupQuorumStatus statusBeforeThird =
        coordA.evaluateGroupQuorum("overworld-main", "world", cfgA);
    assertEquals(4, statusBeforeThird.eligibleCount());
    assertEquals(2, statusBeforeThird.sleepingCount());
    assertEquals(3, statusBeforeThird.requiredCount());
    assertFalse(statusBeforeThird.quorumMet());

    // 3rd player votes on Shard A -> 3/4 meets ABSOLUTE_MAJORITY!
    coordA.voteSleep(a2, "A2", "world");
    assertTrue(coordA.evaluateGroupQuorum("overworld-main", "world", cfgA).quorumMet());
    assertTrue(coordB.evaluateGroupQuorum("overworld-main", "world", cfgB).quorumMet());

    // Cross-shard /sleep cancel on Shard B aborts warmup on both Shard A and Shard B and wakes a1!
    assertTrue(coordB.cancelSleep(b2, "B2", "world"));
    assertTrue(accessorA.wokenPlayers.contains(a1));
    assertEquals(0L, coordA.getLastCommittedEpoch("overworld-main"));

    // Now re-enter sleep and let both shards tick to completion concurrently
    coordA.voteSleep(a1, "A1", "world");
    coordA.voteSleep(a2, "A2", "world");
    coordB.voteSleep(b1, "B1", "world");
    for (int i = 0; i < 6; i++) {
      coordA.tick();
      coordB.tick();
    }

    // Both shards must have committed epoch 1 exactly once (no double advance to 48000)
    assertEquals(1L, coordA.getLastCommittedEpoch("overworld-main"));
    assertEquals(1L, coordB.getLastCommittedEpoch("overworld-main"));
    assertEquals(1L, coordA.totalCommittedEpochs());
    assertEquals(1L, coordB.totalCommittedEpochs());
    assertEquals(24000L, accessorA.getWorldFullTime("world"));
    assertEquals(24000L, accessorB.getWorldFullTime("world"));

    // Verify duplicate or stale epoch commit is rejected by CAS
    assertFalse(coordA.applyEpochCommit("overworld-main", "world", 1L, 0L, true));
    assertTrue(coordA.duplicateEpochsRejected() >= 1L);

    busA.close();
    busB.close();
    storeA.close();
    storeB.close();
  }

  @Test
  @DisplayName("ADR-001: Async SQLite round-trip persistence for player profiles and epoch watermarks")
  void sqliteAsyncRoundTripPersistence(@TempDir Path tempDir) throws Exception {
    MockShardServerAccessor accessor = new MockShardServerAccessor(tempDir);
    LeafSleepConfig cfg = LeafSleepConfig.load(tempDir);
    SleepDatabaseStore store = new SleepDatabaseStore(accessor, tempDir, cfg.storage());
    assertTrue(store.initializeAsync().get(5, TimeUnit.SECONDS));

    UUID playerId = UUID.randomUUID();
    SleepDatabaseStore.PlayerSleepProfile updated =
        new SleepDatabaseStore.PlayerSleepProfile(playerId, true, "cozy", 99999L, 7L, 2L);
    assertTrue(store.savePlayerProfileAsync(updated).get(5, TimeUnit.SECONDS));

    SleepDatabaseStore.ShardEpochWatermark wm =
        new SleepDatabaseStore.ShardEpochWatermark("overworld-main", 42L, 1008000L, 123456L);
    assertTrue(store.saveEpochWatermarkAsync(wm).get(5, TimeUnit.SECONDS));
    store.close();

    // Reopen fresh store against the same SQLite file to verify durable disk persistence
    SleepDatabaseStore reopened = new SleepDatabaseStore(accessor, tempDir, cfg.storage());
    assertTrue(reopened.initializeAsync().get(5, TimeUnit.SECONDS));
    SleepDatabaseStore.PlayerSleepProfile loadedProf =
        reopened.loadPlayerProfileAsync(playerId).get(5, TimeUnit.SECONDS);
    assertTrue(loadedProf.sleepIgnored());
    assertEquals("cozy", loadedProf.wakeupMsgPref());
    assertEquals(99999L, loadedProf.cooldownUntilMs());
    assertEquals(7L, loadedProf.totalSleeps());
    assertEquals(2L, loadedProf.totalCancels());

    Optional<SleepDatabaseStore.ShardEpochWatermark> loadedWm =
        reopened.loadEpochWatermarkAsync("overworld-main").get(5, TimeUnit.SECONDS);
    assertTrue(loadedWm.isPresent());
    assertEquals(42L, loadedWm.get().lastCommittedEpoch());
    assertEquals(1008000L, loadedWm.get().worldTimeTicks());
    reopened.close();
  }

  @Test
  @DisplayName("ADR-001: Direct UDP socket HMAC gossip and PluginMessaging transport round-trip")
  void directSocketAndPluginMessagingTransports(@TempDir Path tempDir) throws Exception {
    MockShardServerAccessor accessor = new MockShardServerAccessor(tempDir);
    byte[] secret = "test-cluster-hmac-secret-32bytes!".getBytes(StandardCharsets.UTF_8);

    // 1. Verify HMAC tamper rejection on SleepSyncFrame
    SleepSyncFrame frame =
        new SleepSyncFrame(
            SleepSyncFrame.FrameType.EPOCH_COMMIT,
            "shard-1",
            "overworld-main",
            "world",
            UUID.randomUUID(),
            "Alice",
            5,
            3,
            1,
            9L,
            0L,
            true,
            System.currentTimeMillis());
    byte[] signed = frame.encodeSigned(secret);
    SleepSyncFrame decoded = SleepSyncFrame.decodeSigned(signed, secret);
    assertEquals(frame.targetDayEpoch(), decoded.targetDayEpoch());

    byte[] tampered = signed.clone();
    tampered[tampered.length - 1] ^= 0x55;
    assertThrows(IOException.class, () -> SleepSyncFrame.decodeSigned(tampered, secret));

    // 2. Verify DirectSocketTransport UDP loopback between two ephemeral ports
    AtomicReference<SleepSyncFrame> receivedUdp = new AtomicReference<>();
    SleepSyncTransport.DirectSocketTransport receiverSocket =
        new SleepSyncTransport.DirectSocketTransport(
            accessor, "127.0.0.1", 0, List.of(), secret, 32);
    receiverSocket.start(receivedUdp::set);
    int receiverPort = receiverSocket.boundPort();

    SleepSyncTransport.DirectSocketTransport senderSocket =
        new SleepSyncTransport.DirectSocketTransport(
            accessor,
            "127.0.0.1",
            0,
            List.of("127.0.0.1:" + receiverPort),
            secret,
            32);
    senderSocket.start(f -> {});
    assertTrue(senderSocket.publish(frame));

    long deadline = System.currentTimeMillis() + 2000L;
    while (receivedUdp.get() == null && System.currentTimeMillis() < deadline) {
      Thread.sleep(15L);
    }
    assertNotNull(receivedUdp.get());
    assertEquals(9L, receivedUdp.get().targetDayEpoch());
    senderSocket.close();
    receiverSocket.close();

    // 3. Verify PluginMessagingTransport over ServerAccessor messaging channel
    AtomicReference<SleepSyncFrame> receivedPm = new AtomicReference<>();
    SleepSyncTransport.PluginMessagingTransport pmTransport =
        new SleepSyncTransport.PluginMessagingTransport(
            accessor, LeafSleepConfig.TransportType.PLUGIN_MESSAGING, "leafsleep:sync", 32);
    pmTransport.start(receivedPm::set);
    assertTrue(pmTransport.publish(frame));
    assertNotNull(receivedPm.get());
    assertEquals("overworld-main", receivedPm.get().shardGroupId());
    pmTransport.close();
  }

  @Test
  @DisplayName("ADR-002: /sleep (/ops) command tree and [OPS bit] PBIT/CBIT/IBIT execution")
  void sleepCommandAndOpsBitSuiteExecution(@TempDir Path tempDir) {
    MockShardServerAccessor accessor = new MockShardServerAccessor(tempDir);
    UUID adminId = UUID.randomUUID();
    accessor.setPlayers(
        List.of(
            new PlayerSleepSnapshot(
                adminId, "Admin", false, 0, false, "SURVIVAL", false, 2000, Set.of("*"))));

    try (LeafSleepRuntime runtime = new LeafSleepRuntime(accessor)) {
      runtime.load();
      runtime.start();

      // Run [OPS bit] suite directly and verify Discord Support Bot regex compatibility
      List<String> bitLines = runtime.bitSuite().executeAllAndFormat();
      assertFalse(bitLines.isEmpty());
      assertTrue(bitLines.get(0).contains("[OPS bit] Health=HEALTHY"));

      LeafSleepCommand cmd =
          new LeafSleepCommand(
              accessor,
              runtime::config,
              runtime::coordinator,
              runtime::transport,
              runtime::databaseStore,
              runtime::bitSuite,
              runtime::reloadAndValidate);

      List<String> replies = new ArrayList<>();
      cmd.onCommand(adminId, p -> true, replies::add, new String[] {"status"});
      cmd.onCommand(adminId, p -> true, replies::add, new String[] {"ignore"});
      cmd.onCommand(adminId, p -> true, replies::add, new String[] {"reload"});
      cmd.onCommand(adminId, p -> true, replies::add, new String[] {"test"});
      cmd.onCommand(adminId, p -> true, replies::add, new String[] {"version"});
      CommandsAPI.execute(Long.MAX_VALUE);

      assertTrue(
          accessor.sentMessages.stream().anyMatch(m -> m.contains("--- LeafSleep Status ---")));
      assertTrue(
          accessor.sentMessages.stream().anyMatch(m -> m.contains("[OPS bit] Health=HEALTHY")));
      assertTrue(
          accessor.sentMessages.stream().anyMatch(m -> m.contains("reloaded successfully")));
    }
  }

  @Test
  @DisplayName("ADR-001: Spaced policy names, sleep/cancel UnitCurve scaling with server max-players, tug-of-war cancel, and short player-set prompts")
  void sleepAndCancelCurvesWithTugOfWarAndPlayerSets(@TempDir Path tempDir) throws Exception {
    // 1. Spaced policy, cancelPolicy, and CurveType parsing
    assertEquals(QuorumPolicy.ONE_PLAYER, QuorumPolicy.fromString("one player"));
    assertEquals(QuorumPolicy.FIXED_COUNT, QuorumPolicy.fromString("fixed count"));
    assertEquals(QuorumPolicy.ABSOLUTE_MAJORITY, QuorumPolicy.fromString("absolute majority"));
    assertEquals(QuorumPolicy.CURVE, QuorumPolicy.fromString("curve"));
    assertEquals(
        QuorumPolicy.CancelPolicy.TUG_OF_WAR, QuorumPolicy.CancelPolicy.fromString("tug of war"));
    assertEquals(QuorumPolicy.CancelPolicy.VETO, QuorumPolicy.CancelPolicy.fromString("veto"));
    assertEquals(
        QuorumPolicy.CurveType.LOGARITHMIC, QuorumPolicy.CurveType.fromString("logarithmic"));
    assertEquals(QuorumPolicy.CurveType.POWER, QuorumPolicy.CurveType.fromString("power"));
    assertEquals(
        QuorumPolicy.CurveType.SMOOTHERSTEP, QuorumPolicy.CurveType.fromString("smootherstep"));
    assertEquals(QuorumPolicy.CurveType.SIGMOID, QuorumPolicy.CurveType.fromString("sigmoid"));

    // 2. Verify sleepCurve and cancelCurve monotonicity across server max-players = 100
    QuorumPolicy.CurveConfig sleepCurve =
        new QuorumPolicy.CurveConfig(QuorumPolicy.CurveType.LOGARITHMIC, 1, 8, 10.0, 0.35);
    QuorumPolicy.CurveConfig cancelCurve =
        new QuorumPolicy.CurveConfig(QuorumPolicy.CurveType.LOGARITHMIC, 1, 5, 10.0, 0.35);
    int prevSleep = 1;
    int prevCancel = 1;
    assertEquals(1, sleepCurve.evaluateRequired(1, 100));
    assertEquals(1, cancelCurve.evaluateRequired(1, 100));
    for (int eligible = 1; eligible <= 100; eligible++) {
      int reqSleep = sleepCurve.evaluateRequired(eligible, 100);
      int reqCancel = cancelCurve.evaluateRequired(eligible, 100);
      assertTrue(reqSleep >= prevSleep && reqSleep <= eligible);
      assertTrue(reqCancel >= prevCancel && reqCancel <= eligible);
      prevSleep = reqSleep;
      prevCancel = reqCancel;
    }
    assertEquals(8, sleepCurve.evaluateRequired(100, 100));
    assertEquals(5, cancelCurve.evaluateRequired(100, 100));

    // 3. Verify Tug-of-War ("both") cancel offset + short requirement messages + player sets
    MockShardServerAccessor accessor = new MockShardServerAccessor(tempDir);
    accessor.setMaxPlayers(20);
    LeafSleepConfig baseCfg = LeafSleepConfig.load(tempDir);
    Map<String, LeafSleepConfig.WorldSleepConfig> curveWorlds =
        Map.of(
            "world",
            new LeafSleepConfig.WorldSleepConfig(
                "world",
                true,
                "overworld-main",
                QuorumPolicy.CURVE,
                1,
                QuorumPolicy.CancelPolicy.TUG_OF_WAR,
                new QuorumPolicy.CurveConfig(QuorumPolicy.CurveType.LINEAR, 1, 5, 10.0, 0.5),
                new QuorumPolicy.CurveConfig(QuorumPolicy.CurveType.LINEAR, 1, 4, 10.0, 0.5),
                60));
    LeafSleepConfig cfg =
        new LeafSleepConfig(
            "shard-1",
            QuorumPolicy.CURVE,
            1,
            QuorumPolicy.CancelPolicy.TUG_OF_WAR,
            sleepCurve,
            cancelCurve,
            60,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            baseCfg.sharding(),
            baseCfg.storage(),
            baseCfg.messages(),
            baseCfg.wakeupEffects(),
            baseCfg.sleepMessages(),
            curveWorlds);

    SleepDatabaseStore store = new SleepDatabaseStore(accessor, tempDir, cfg.storage());
    assertTrue(store.initializeAsync().get(5, TimeUnit.SECONDS));
    SleepSyncTransport.LocalOnlyTransport transport = new SleepSyncTransport.LocalOnlyTransport(32);
    ShardSleepCoordinator coord =
        new ShardSleepCoordinator(accessor, () -> cfg, store, transport, new MenuTokenService());
    transport.start(coord::onRemoteFrame);

    // Populate 10 eligible players on a 20 max-player server:
    // t = 9/19 (~0.4737) -> base sleep required = round(1 + 4 * 9/19) = 3, cancel required = round(1 + 3 * 9/19) = 2
    List<PlayerSleepSnapshot> tenPlayers = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      tenPlayers.add(
          new PlayerSleepSnapshot(
              UUID.randomUUID(),
              "Player" + i,
              false,
              0,
              false,
              "SURVIVAL",
              false,
              2000,
              Set.of("*")));
    }
    accessor.setPlayers(tenPlayers);

    // First player votes to sleep -> 1/3 sleeping -> "Need 2 more to skip (2 to cancel)"
    PlayerSleepSnapshot p1 = tenPlayers.get(0);
    assertTrue(coord.voteSleep(p1.playerId(), p1.playerName(), "world"));
    ShardSleepCoordinator.GroupQuorumStatus st1 =
        coord.evaluateGroupQuorum("overworld-main", "world", cfg);
    assertEquals(3, st1.requiredCount());
    assertEquals(2, st1.cancelRequiredCount());
    assertEquals(2, st1.needToSkip());
    assertEquals(2, st1.needToCancel());
    assertEquals(List.of("Player1"), st1.sleepingPlayers());
    assertTrue(
        accessor.sentMessages.stream()
            .anyMatch(
                m ->
                    m.contains("Sleeping: Player1")
                        && m.contains("Need 2 more to skip (2 to cancel)")));

    // Player2 and Player3 also vote to sleep -> 3/3 sleeping -> "Skipping! (2 to cancel)"
    coord.voteSleep(tenPlayers.get(1).playerId(), tenPlayers.get(1).playerName(), "world");
    coord.voteSleep(tenPlayers.get(2).playerId(), tenPlayers.get(2).playerName(), "world");
    ShardSleepCoordinator.GroupQuorumStatus st3 =
        coord.evaluateGroupQuorum("overworld-main", "world", cfg);
    assertTrue(st3.quorumMet());
    assertEquals(0, st3.needToSkip());
    assertEquals("Skipping! (2 to cancel)", st3.requirementsSummary());

    // Player4 clicks [Cancel] -> 1/2 cancels: in TUG_OF_WAR, effective sleep required rises from 3 to 4!
    // Countdown pauses and broadcast says "Cancelling: Player4 - Need 1 more to skip (1 to cancel)"
    accessor.sentMessages.clear();
    assertTrue(coord.cancelSleep(tenPlayers.get(3).playerId(), tenPlayers.get(3).playerName(), "world"));
    ShardSleepCoordinator.GroupQuorumStatus stAfterOneCancel =
        coord.evaluateGroupQuorum("overworld-main", "world", cfg);
    assertFalse(stAfterOneCancel.quorumMet());
    assertFalse(stAfterOneCancel.cancelQuorumMet());
    assertEquals(4, stAfterOneCancel.requiredCount());
    assertEquals(1, stAfterOneCancel.needToSkip());
    assertEquals(1, stAfterOneCancel.needToCancel());
    assertEquals(List.of("Player4"), stAfterOneCancel.cancellingPlayers());
    assertTrue(
        accessor.sentMessages.stream()
            .anyMatch(
                m ->
                    m.contains("Cancelling: Player4")
                        && m.contains("Need 1 more to skip (1 to cancel)")));

    // Player5 also clicks [Cancel] -> 2/2 cancel quorum met -> night skip is aborted!
    accessor.sentMessages.clear();
    coord.cancelSleep(tenPlayers.get(4).playerId(), tenPlayers.get(4).playerName(), "world");
    assertTrue(
        accessor.sentMessages.stream()
            .anyMatch(m -> m.contains("Night skip cancelled by Player4, Player5")));

    store.close();
    transport.close();
  }

  @Test
  @DisplayName("ADR-001: Large-network hardening — idle tick snapshot throttling, remote progress broadcast, Redis pub/sub, replay guard, and profile eviction")
  void largeNetworkHardeningVerification(@TempDir Path tempDir) throws Exception {
    MockShardServerAccessor accessorA = new MockShardServerAccessor(tempDir.resolve("a"));
    MockShardServerAccessor accessorB = new MockShardServerAccessor(tempDir.resolve("b"));
    Files.createDirectories(tempDir.resolve("a"));
    Files.createDirectories(tempDir.resolve("b"));

    LeafSleepConfig cfgA = LeafSleepConfig.load(tempDir.resolve("a"));
    SleepDatabaseStore storeA = new SleepDatabaseStore(accessorA, tempDir.resolve("a"), cfgA.storage());
    SleepDatabaseStore storeB = new SleepDatabaseStore(accessorB, tempDir.resolve("b"), cfgA.storage());
    assertTrue(storeA.initializeAsync().get(5, TimeUnit.SECONDS));
    assertTrue(storeB.initializeAsync().get(5, TimeUnit.SECONDS));

    // 1. Verify profile cache eviction on player quit
    UUID quittingPlayer = UUID.randomUUID();
    storeA.getCachedOrDefault(quittingPlayer);
    assertEquals(1, storeA.cachedProfileCount());
    storeA.evictPlayerProfile(quittingPlayer);
    assertEquals(0, storeA.cachedProfileCount());

    // 2. Verify idle tick throttling: 19 idle ticks must only query getWorldPlayers once (on the initial dirty tick)
    SleepSyncTransport.LocalOnlyTransport busA = new SleepSyncTransport.LocalOnlyTransport(64);
    ShardSleepCoordinator coordA =
        new ShardSleepCoordinator(accessorA, () -> cfgA, storeA, busA, new MenuTokenService());
    busA.start(coordA::onRemoteFrame);

    UUID pA = UUID.randomUUID();
    UUID pB = UUID.randomUUID();
    accessorA.setPlayers(
        List.of(
            new PlayerSleepSnapshot(pA, "Alice", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));
    accessorB.setPlayers(
        List.of(
            new PlayerSleepSnapshot(pB, "Bob", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));

    accessorA.worldPlayerSnapshotsQueried.set(0);
    for (int i = 0; i < 19; i++) {
      coordA.tick();
    }
    assertEquals(
        1,
        accessorA.worldPlayerSnapshotsQueried.get(),
        "Idle ticks 1..19 should only query getWorldPlayers once on the initial dirty tick");
    coordA.tick(); // Tick 20 triggers the 1 Hz periodic idle refresh
    assertEquals(2, accessorA.worldPlayerSnapshotsQueried.get());

    // 3. Verify remote BED_ENTER / SLEEP_VOTE broadcasts chat progress to players on peer shards
    accessorA.sentMessages.clear();
    SleepSyncFrame remoteVote =
        new SleepSyncFrame(
            SleepSyncFrame.FrameType.SLEEP_VOTE,
            "shard-2",
            "world",
            "world",
            pB,
            "Bob",
            1,
            1,
            0,
            1,
            20,
            List.of("Bob"),
            List.of(),
            0L,
            13000L,
            true,
            System.currentTimeMillis());
    coordA.onRemoteFrame(remoteVote);
    assertTrue(
        accessorA.sentMessages.stream().anyMatch(m -> m.contains("Bob")),
        "Players on shard-1 must receive chat progress when Bob votes to sleep on shard-2");

    // 4. Verify SleepSyncFrame replay window rejection and ReplayWindowCache deduplication
    byte[] secret = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    SleepSyncFrame expiredFrame =
        new SleepSyncFrame(
            SleepSyncFrame.FrameType.SLEEP_VOTE,
            "shard-2",
            "world",
            "world",
            pB,
            "Bob",
            1,
            1,
            0,
            0L,
            13000L,
            true,
            System.currentTimeMillis() - 120_000L);
    byte[] expiredSigned = expiredFrame.encodeSigned(secret);
    assertThrows(
        IOException.class,
        () -> SleepSyncFrame.decodeSigned(expiredSigned, secret),
        "Expired signed frame outside 30s replay window must be rejected");

    byte[] validSigned = remoteVote.encodeSigned(secret);
    long fp = SleepSyncFrame.extractSignatureFingerprint(validSigned);
    SleepSyncTransport.ReplayWindowCache replayCache =
        new SleepSyncTransport.ReplayWindowCache(30_000L);
    assertTrue(replayCache.checkAndRecord(fp));
    assertFalse(replayCache.checkAndRecord(fp), "Duplicate frame fingerprint must be rejected");

    // 5. Verify RedisPubSubTransport over an embedded RESP2 server
    try (java.net.ServerSocket redisServer = new java.net.ServerSocket(0)) {
      int redisPort = redisServer.getLocalPort();
      List<java.io.OutputStream> subStreams = new CopyOnWriteArrayList<>();
      java.util.concurrent.ExecutorService redisExec =
          java.util.concurrent.Executors.newCachedThreadPool();
      AtomicReference<Boolean> runningRedis = new AtomicReference<>(true);
      redisExec.submit(
          () -> {
            while (runningRedis.get() && !redisServer.isClosed()) {
              try {
                java.net.Socket client = redisServer.accept();
                redisExec.submit(
                    () -> {
                      try (java.io.InputStream in = client.getInputStream();
                          java.io.OutputStream out = client.getOutputStream()) {
                        while (runningRedis.get() && !client.isClosed()) {
                          Object cmd =
                              io.github.dailystruggle.rtp.proxy.common.transport.redis.resp
                                  .RespProtocol.readReply(in);
                          if (cmd instanceof List<?> list && !list.isEmpty()) {
                            String op =
                                io.github.dailystruggle.rtp.proxy.common.transport.redis.resp
                                    .RespProtocol.toUtf8((byte[]) list.get(0))
                                    .toUpperCase();
                            if ("SUBSCRIBE".equals(op)) {
                              String ch =
                                  io.github.dailystruggle.rtp.proxy.common.transport.redis.resp
                                      .RespProtocol.toUtf8((byte[]) list.get(1));
                              subStreams.add(out);
                              String ack =
                                  "*3\r\n$9\r\nsubscribe\r\n$"
                                      + ch.length()
                                      + "\r\n"
                                      + ch
                                      + "\r\n:1\r\n";
                              out.write(ack.getBytes(StandardCharsets.UTF_8));
                              out.flush();
                            } else if ("PUBLISH".equals(op)) {
                              String ch =
                                  io.github.dailystruggle.rtp.proxy.common.transport.redis.resp
                                      .RespProtocol.toUtf8((byte[]) list.get(1));
                              String msg =
                                  io.github.dailystruggle.rtp.proxy.common.transport.redis.resp
                                      .RespProtocol.toUtf8((byte[]) list.get(2));
                              out.write(":1\r\n".getBytes(StandardCharsets.UTF_8));
                              out.flush();
                              String push =
                                  "*3\r\n$7\r\nmessage\r\n$"
                                      + ch.length()
                                      + "\r\n"
                                      + ch
                                      + "\r\n$"
                                      + msg.length()
                                      + "\r\n"
                                      + msg
                                      + "\r\n";
                              for (java.io.OutputStream subOut : subStreams) {
                                try {
                                  subOut.write(push.getBytes(StandardCharsets.UTF_8));
                                  subOut.flush();
                                } catch (IOException ignored) {
                                }
                              }
                            } else if ("UNSUBSCRIBE".equals(op)) {
                              out.write(
                                  "*3\r\n$11\r\nunsubscribe\r\n$14\r\nleafsleep:sync\r\n:0\r\n"
                                      .getBytes(StandardCharsets.UTF_8));
                              out.flush();
                              break;
                            } else {
                              out.write("+OK\r\n".getBytes(StandardCharsets.UTF_8));
                              out.flush();
                            }
                          }
                        }
                      } catch (Exception ignored) {
                      }
                    });
              } catch (IOException ignored) {
              }
            }
          });

      AtomicReference<SleepSyncFrame> redisReceived = new AtomicReference<>();
      SleepSyncTransport.RedisPubSubTransport redisTransport =
          new SleepSyncTransport.RedisPubSubTransport(
              accessorA, "127.0.0.1", redisPort, null, "leafsleep:sync", secret, 32);
      redisTransport.start(redisReceived::set);

      long subDeadline = System.currentTimeMillis() + 2000L;
      while (!redisTransport.isHealthy() && System.currentTimeMillis() < subDeadline) {
        Thread.sleep(15L);
      }
      assertTrue(redisTransport.publish(remoteVote));

      long msgDeadline = System.currentTimeMillis() + 2000L;
      while (redisReceived.get() == null && System.currentTimeMillis() < msgDeadline) {
        Thread.sleep(15L);
      }
      assertNotNull(redisReceived.get());
      assertEquals("Bob", redisReceived.get().playerName());
      redisTransport.close();
      runningRedis.set(false);
      redisExec.shutdownNow();
    }

    // 6. Verify bed-leave snapshot exclusion, self-cancel cooldown bypass, and daytime vote cleanup
    accessorA.fullTime.set(13000L);
    accessorA.setPlayers(
        List.of(
            new PlayerSleepSnapshot(
                pA, "Alice", true, 5, false, "SURVIVAL", false, 1000, Set.of("*"))));
    coordA.onBedEnter(
        new PlayerBedEnterContext(pA, "Alice", "world", 0, 64, 0, "OK", perm -> true));
    // Simulate Bukkit firing PlayerBedLeaveEvent while snap.sleeping() is still true
    coordA.onBedLeave(
        new io.github.dailystruggle.server.api.PlayerBedLeaveContext(
            pA, "Alice", "world", 0, 64, 0, 13005L));
    ShardSleepCoordinator.GroupQuorumStatus afterLeave =
        coordA.allGroupStatuses().get("overworld-main");
    assertNotNull(afterLeave);
    assertEquals(0, afterLeave.sleepingCount(), "Bed leave must immediately exclude the leaving player even if snapshot.sleeping() is still true");

    PlayerBedEnterContext cooldownReenter =
        new PlayerBedEnterContext(pA, "Alice", "world", 0, 64, 0, "OK", perm -> true);
    coordA.onBedEnter(cooldownReenter);
    assertTrue(
        cooldownReenter.isCancelled(),
        "Re-entering a bed while sleepCooldownSeconds is active must cancel PlayerBedEnterContext to deny bed entry");

    // Player votes /sleep sleep (incurring sleepCooldown) and immediately cancels their own vote
    accessorA.setPlayers(
        List.of(
            new PlayerSleepSnapshot(
                pA, "Alice", false, 0, false, "SURVIVAL", false, 1000, Set.of("*")),
            new PlayerSleepSnapshot(
                pB, "Bob", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));
    storeA.savePlayerProfileAsync(SleepDatabaseStore.PlayerSleepProfile.defaults(pA)).get(5, TimeUnit.SECONDS);
    assertTrue(coordA.voteSleep(pA, "Alice", "world"));
    assertTrue(
        coordA.cancelSleep(pA, "Alice", "world"),
        "A player who just voted to sleep must be able to cancel their own vote without being blocked by the sleep cooldown");

    // Advance world to daytime (1000L) and clear weather, then verify tick() clears leftover votes
    storeA.savePlayerProfileAsync(SleepDatabaseStore.PlayerSleepProfile.defaults(pA)).get(5, TimeUnit.SECONDS);
    assertTrue(coordA.voteSleep(pA, "Alice", "world"));
    accessorA.fullTime.set(1000L);
    accessorA.weather.set(WeatherState.CLEAR);
    coordA.tick();
    assertEquals(
        0,
        coordA.evaluateGroupQuorum("overworld-main", "world", cfgA).sleepingCount(),
        "Daytime tick must clear stale night sleep/cancel votes");

    // 7. Verify all 5 CurveTypes (LOGARITHMIC, POWER, SMOOTHERSTEP, SIGMOID, LINEAR) at scale (1..100 players)
    for (QuorumPolicy.CurveType curveType : QuorumPolicy.CurveType.values()) {
      double param = curveType == QuorumPolicy.CurveType.POWER ? 0.5 : 10.0;
      QuorumPolicy.CurveConfig sc =
          new QuorumPolicy.CurveConfig(curveType, 1, 10, param, 0.35);
      int prev = 1;
      assertEquals(1, sc.evaluateRequired(1, 100), "Curve " + curveType + " must start at 1 for 1 player");
      for (int eligible = 1; eligible <= 100; eligible++) {
        int req = sc.evaluateRequired(eligible, 100);
        assertTrue(
            req >= prev && req <= eligible,
            "Curve " + curveType + " must be monotonically non-decreasing at eligible=" + eligible);
        prev = req;
      }
      assertEquals(10, sc.evaluateRequired(100, 100), "Curve " + curveType + " must reach maxRequired at full capacity");
    }

    // Verify 100-player multi-shard voting + 60-tick warmup does not poll getWorldPlayers at 20 Hz
    List<PlayerSleepSnapshot> fiftyPlayersA = new ArrayList<>();
    for (int i = 1; i <= 50; i++) {
      fiftyPlayersA.add(
          new PlayerSleepSnapshot(
              UUID.randomUUID(), "ScaleA" + i, false, 0, false, "SURVIVAL", false, 1000, Set.of("*")));
    }
    accessorA.setMaxPlayers(50);
    accessorA.setPlayers(fiftyPlayersA);
    accessorA.fullTime.set(13000L);
    coordA.markDirty();
    coordA.tick();
    accessorA.worldPlayerSnapshotsQueried.set(0);
    // Vote with 1 player -> single getWorldPlayers call (not 3)
    PlayerSleepSnapshot voter1 = fiftyPlayersA.get(0);
    storeA.savePlayerProfileAsync(SleepDatabaseStore.PlayerSleepProfile.defaults(voter1.playerId())).get(5, TimeUnit.SECONDS);
    assertTrue(coordA.voteSleep(voter1.playerId(), voter1.playerName(), "world"));
    assertEquals(
        1,
        accessorA.worldPlayerSnapshotsQueried.get(),
        "voteSleep must perform at most 1 getWorldPlayers snapshot scan");
    // Run 15 active-voting ticks -> 0 extra getWorldPlayers scans between 20-tick periodic refreshes
    accessorA.worldPlayerSnapshotsQueried.set(0);
    for (int i = 0; i < 15; i++) {
      coordA.tick();
    }
    assertEquals(
        0,
        accessorA.worldPlayerSnapshotsQueried.get(),
        "Active voting/warmup ticks must not re-scan getWorldPlayers on non-dirty ticks");
    assertTrue(
        coordA.avgTickDurationMs() < 2.0,
        "Coordinator average tick duration must remain well below 2ms at 50 players/shard");

    // 8. Verify 4-shard mesh (shard-a, shard-b, shard-c, shard-d) cancel-quorum canceller propagation
    //    and stale pre-reset heartbeat vote clamping
    Path dirB = tempDir.resolve("b");
    Path dirC = tempDir.resolve("c");
    Path dirD = tempDir.resolve("d");
    Files.createDirectories(dirC);
    Files.createDirectories(dirD);
    MockShardServerAccessor accessorC = new MockShardServerAccessor(dirC);
    MockShardServerAccessor accessorD = new MockShardServerAccessor(dirD);
    LeafSleepConfig.ShardingConfig meshSharding =
        new LeafSleepConfig.ShardingConfig(
            true,
            LeafSleepConfig.TransportType.LOCAL_ONLY,
            "leafsleep:sync",
            "127.0.0.1",
            25620,
            List.of(),
            "LEAFSLEEP_HMAC_SECRET",
            20);
    LeafSleepConfig cfgMeshA =
        new LeafSleepConfig(
            "shard-a",
            QuorumPolicy.CURVE,
            25,
            3,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            meshSharding,
            cfgA.storage(),
            cfgA.messages(),
            cfgA.wakeupEffects(),
            cfgA.sleepMessages(),
            cfgA.worlds());
    LeafSleepConfig cfgB =
        new LeafSleepConfig(
            "shard-b",
            QuorumPolicy.CURVE,
            25,
            3,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            meshSharding,
            cfgA.storage(),
            cfgA.messages(),
            cfgA.wakeupEffects(),
            cfgA.sleepMessages(),
            cfgA.worlds());
    LeafSleepConfig cfgC =
        new LeafSleepConfig(
            "shard-c",
            QuorumPolicy.CURVE,
            25,
            3,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            meshSharding,
            cfgA.storage(),
            cfgA.messages(),
            cfgA.wakeupEffects(),
            cfgA.sleepMessages(),
            cfgA.worlds());
    LeafSleepConfig cfgD =
        new LeafSleepConfig(
            "shard-d",
            QuorumPolicy.CURVE,
            25,
            3,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            meshSharding,
            cfgA.storage(),
            cfgA.messages(),
            cfgA.wakeupEffects(),
            cfgA.sleepMessages(),
            cfgA.worlds());
    SleepDatabaseStore storeC = new SleepDatabaseStore(accessorC, dirC, cfgC.storage());
    SleepDatabaseStore storeD = new SleepDatabaseStore(accessorD, dirD, cfgD.storage());
    assertTrue(storeC.initializeAsync().get(5, TimeUnit.SECONDS));
    assertTrue(storeD.initializeAsync().get(5, TimeUnit.SECONDS));
    SleepSyncTransport.LocalOnlyTransport busMeshA = new SleepSyncTransport.LocalOnlyTransport(64);
    SleepSyncTransport.LocalOnlyTransport busB = new SleepSyncTransport.LocalOnlyTransport(64);
    SleepSyncTransport.LocalOnlyTransport busC = new SleepSyncTransport.LocalOnlyTransport(64);
    SleepSyncTransport.LocalOnlyTransport busD = new SleepSyncTransport.LocalOnlyTransport(64);
    for (SleepSyncTransport.LocalOnlyTransport peer1 : List.of(busMeshA, busB, busC, busD)) {
      for (SleepSyncTransport.LocalOnlyTransport peer2 : List.of(busMeshA, busB, busC, busD)) {
        peer1.connectPeer(peer2);
      }
    }
    ShardSleepCoordinator coordMeshA =
        new ShardSleepCoordinator(accessorA, () -> cfgMeshA, storeA, busMeshA, new MenuTokenService());
    ShardSleepCoordinator coordB =
        new ShardSleepCoordinator(accessorB, () -> cfgB, storeB, busB, new MenuTokenService());
    ShardSleepCoordinator coordC =
        new ShardSleepCoordinator(accessorC, () -> cfgC, storeC, busC, new MenuTokenService());
    ShardSleepCoordinator coordD =
        new ShardSleepCoordinator(accessorD, () -> cfgD, storeD, busD, new MenuTokenService());
    busMeshA.start(coordMeshA::onRemoteFrame);
    busB.start(coordB::onRemoteFrame);
    busC.start(coordC::onRemoteFrame);
    busD.start(coordD::onRemoteFrame);

    UUID pC = UUID.randomUUID();
    UUID pD = UUID.randomUUID();
    accessorA.setMaxPlayers(20);
    accessorB.setMaxPlayers(20);
    accessorC.setMaxPlayers(20);
    accessorD.setMaxPlayers(20);
    accessorA.setPlayers(
        List.of(new PlayerSleepSnapshot(pA, "Alice", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));
    accessorB.setPlayers(
        List.of(new PlayerSleepSnapshot(pB, "Bob", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));
    accessorC.setPlayers(
        List.of(new PlayerSleepSnapshot(pC, "Charlie", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));
    accessorD.setPlayers(
        List.of(new PlayerSleepSnapshot(pD, "Dana", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));
    for (MockShardServerAccessor acc : List.of(accessorA, accessorB, accessorC, accessorD)) {
      acc.fullTime.set(13000L);
    }
    for (ShardSleepCoordinator c : List.of(coordMeshA, coordB, coordC, coordD)) {
      c.markDirty();
      for (int t = 0; t < 20; t++) {
        c.tick();
      }
    }
    ShardSleepCoordinator.GroupQuorumStatus status4 =
        coordD.evaluateGroupQuorum("overworld-main", "world", cfgD);
    assertEquals(4, status4.activeShardCount(), "All 4 shards must be discovered in the group");
    assertEquals(4, status4.eligibleCount(), "All 4 players across 4 shards must be eligible");
    assertEquals(80, status4.maxPlayers(), "Aggregated maxPlayers across 4 shards of 20 must be 80");

    // At eligible=4/80 under default logarithmic curves, sleepRequired=2 and cancelRequired=2:
    //   1. Alice (shard-a) + Dana (shard-d) vote sleep -> 2/2 quorum met across the 4 shards
    //   2. Bob (shard-b) votes cancel -> 1/2 tug-of-war raises required sleepers to 3
    //   3. Charlie (shard-c) votes cancel -> 2/2 cancel quorum met -> aborts across all 4 shards
    //      with both Bob and Charlie listed in shard-d's cancel broadcast!
    storeA.savePlayerProfileAsync(SleepDatabaseStore.PlayerSleepProfile.defaults(pA)).get(5, TimeUnit.SECONDS);
    storeB.savePlayerProfileAsync(SleepDatabaseStore.PlayerSleepProfile.defaults(pB)).get(5, TimeUnit.SECONDS);
    storeC.savePlayerProfileAsync(SleepDatabaseStore.PlayerSleepProfile.defaults(pC)).get(5, TimeUnit.SECONDS);
    storeD.savePlayerProfileAsync(SleepDatabaseStore.PlayerSleepProfile.defaults(pD)).get(5, TimeUnit.SECONDS);
    assertTrue(coordMeshA.voteSleep(pA, "Alice", "world"));
    assertTrue(coordD.voteSleep(pD, "Dana", "world"));
    assertTrue(
        coordB.evaluateGroupQuorum("overworld-main", "world", cfgB).quorumMet(),
        "2/4 sleepers across shard-a and shard-d must satisfy quorum (2 required)");
    assertTrue(coordB.cancelSleep(pB, "Bob", "world"));
    assertFalse(
        coordD.evaluateGroupQuorum("overworld-main", "world", cfgD).quorumMet(),
        "1st cancel vote on shard-b must offset quorum via TUG_OF_WAR (now 3 sleepers required)");
    accessorD.sentMessages.clear();
    assertTrue(coordC.cancelSleep(pC, "Charlie", "world"));
    assertTrue(
        accessorD.sentMessages.stream()
            .anyMatch(m -> m.contains("Bob") && m.contains("Charlie")),
        "4th shard (shard-d) must receive the full cross-shard canceller list (Bob, Charlie) when shard-c completes cancel quorum");
    // Inject a stale pre-cancel heartbeat from shard-c carrying sleepingCount=1 from 500ms before the cancel
    SleepSyncFrame staleHb =
        new SleepSyncFrame(
            SleepSyncFrame.FrameType.SHARD_HEARTBEAT,
            "shard-c",
            "overworld-main",
            "world",
            null,
            "",
            1,
            1,
            0,
            0,
            20,
            List.of("Charlie"),
            List.of(),
            0L,
            13000L,
            true,
            System.currentTimeMillis() - 500L);
    coordD.onRemoteFrame(staleHb);
    assertEquals(
        0,
        coordD.evaluateGroupQuorum("overworld-main", "world", cfgD).sleepingCount(),
        "Stale pre-reset heartbeat from a 3rd/4th shard must not resurrect cleared sleep votes");

    busA.close();
    busMeshA.close();
    busB.close();
    busC.close();
    busD.close();
    storeA.close();
    storeB.close();
    storeC.close();
    storeD.close();
  }

  @Test
  @DisplayName("ADR-001: Multi-world per-shard group aggregation, MenuTokenService redemption, and checkHealthAsync")
  void multiWorldSameShardGroupAndTokenRedemption(@TempDir Path tempDir) throws Exception {
    MockShardServerAccessor accessor = new MockShardServerAccessor(tempDir);
    accessor.setWorldNames(List.of("world", "world_survival_2"));
    accessor.setMaxPlayers(20);

    LeafSleepConfig baseCfg = LeafSleepConfig.load(tempDir);
    Map<String, LeafSleepConfig.WorldSleepConfig> twoWorldsSameGroup =
        Map.of(
            "world",
            new LeafSleepConfig.WorldSleepConfig(
                "world",
                true,
                "overworld-main",
                QuorumPolicy.FIXED_COUNT,
                2,
                QuorumPolicy.CancelPolicy.VETO,
                baseCfg.sleepCurve(),
                baseCfg.cancelCurve(),
                20),
            "world_survival_2",
            new LeafSleepConfig.WorldSleepConfig(
                "world_survival_2",
                true,
                "overworld-main",
                QuorumPolicy.FIXED_COUNT,
                2,
                QuorumPolicy.CancelPolicy.VETO,
                baseCfg.sleepCurve(),
                baseCfg.cancelCurve(),
                20));
    LeafSleepConfig cfg =
        new LeafSleepConfig(
            "shard-1",
            QuorumPolicy.FIXED_COUNT,
            2,
            QuorumPolicy.CancelPolicy.VETO,
            baseCfg.sleepCurve(),
            baseCfg.cancelCurve(),
            20,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            baseCfg.sharding(),
            baseCfg.storage(),
            baseCfg.messages(),
            baseCfg.wakeupEffects(),
            baseCfg.sleepMessages(),
            twoWorldsSameGroup);

    SleepDatabaseStore store = new SleepDatabaseStore(accessor, tempDir, cfg.storage());
    assertTrue(store.initializeAsync().get(5, TimeUnit.SECONDS));
    assertTrue(store.checkHealthAsync().get(5, TimeUnit.SECONDS));

    SleepSyncTransport.LocalOnlyTransport transport = new SleepSyncTransport.LocalOnlyTransport(32);
    MenuTokenService tokenService = new MenuTokenService();
    ShardSleepCoordinator coord =
        new ShardSleepCoordinator(accessor, () -> cfg, store, transport, tokenService);
    transport.start(coord::onRemoteFrame);

    UUID p1 = UUID.randomUUID();
    UUID p2 = UUID.randomUUID();
    accessor.setWorldPlayers(
        "world",
        List.of(
            new PlayerSleepSnapshot(
                p1, "Alice", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));
    accessor.setWorldPlayers(
        "world_survival_2",
        List.of(
            new PlayerSleepSnapshot(
                p2, "Bob", false, 0, false, "SURVIVAL", false, 1000, Set.of("*"))));

    coord.tick();
    ShardSleepCoordinator.GroupQuorumStatus statusInitial =
        coord.evaluateGroupQuorum("overworld-main", "world", cfg);
    assertEquals(
        2,
        statusInitial.eligibleCount(),
        "Both local worlds mapped to overworld-main must contribute to eligibleCount without overwriting each other");
    assertEquals(
        1,
        statusInitial.activeShardCount(),
        "Two worlds on the same server shard must still count as 1 activeShardCount");
    assertEquals(
        20,
        statusInitial.maxPlayers(),
        "Two worlds on the same server shard must not double-count server maxPlayers");

    // Alice votes to sleep in "world"; Bob votes to sleep in "world_survival_2" -> 2/2 quorum met!
    assertTrue(coord.voteSleep(p1, "Alice", "world"));
    assertTrue(tokenService.activeTokenCount() > 0, "Sleep broadcast must issue action tokens");
    assertTrue(coord.voteSleep(p2, "Bob", "world_survival_2"));
    ShardSleepCoordinator.GroupQuorumStatus statusBoth =
        coord.evaluateGroupQuorum("overworld-main", "world", cfg);
    assertEquals(2, statusBoth.sleepingCount());
    assertTrue(statusBoth.quorumMet());
    assertFalse(
        accessor.activeBossBars.isEmpty(),
        "Warmup bossbar must be active across both worlds in the shard group once quorum is met");

    // Issue a one-time cancel token for Bob and redeem it via /sleep token <id>
    String cancelToken =
        tokenService.issue(
            p2, new io.github.dailystruggle.menu.api.MenuAction.RunCommand("sleep", "cancel"));
    LeafSleepCommand cmd =
        new LeafSleepCommand(
            accessor,
            () -> cfg,
            () -> coord,
            () -> transport,
            () -> store,
            () ->
                new LeafSleepBitSuite(
                    accessor, () -> cfg, () -> store, () -> transport, () -> coord),
            List::of);
    List<String> replies = new ArrayList<>();
    cmd.onCommand(p2, perm -> true, replies::add, new String[] {"token", "id=" + cancelToken});
    CommandsAPI.execute(Long.MAX_VALUE);

    assertEquals(
        0,
        coord.evaluateGroupQuorum("overworld-main", "world", cfg).sleepingCount(),
        "Redeeming a cancel token must abort the sleep vote across all local worlds in the group");
    assertTrue(
        accessor.activeBossBars.isEmpty(),
        "Redeeming a cancel token must immediately clear the warmup bossbar across all worlds in the group");
    assertFalse(
        coord.redeemActionToken(p2, "Bob", "world_survival_2", cancelToken),
        "One-time token must not be redeemable a second time");

    // Issue a new token for Alice and verify onPlayerQuit invalidates it
    String aliceSleepToken =
        tokenService.issue(
            p1, new io.github.dailystruggle.menu.api.MenuAction.RunCommand("sleep", "sleep"));
    coord.onPlayerQuit(p1);
    assertFalse(
        coord.redeemActionToken(p1, "Alice", "world", aliceSleepToken),
        "onPlayerQuit must invalidate all tokens issued to the disconnecting player");

    // Verify interactive chat buttons carry RUN_COMMAND click actions and hover tooltips
    assertFalse(
        accessor.interactiveLines.isEmpty(),
        "Sleep broadcasts must dispatch interactive BookMenuSpec.Line components");
    assertTrue(
        accessor.interactiveLines.stream()
            .flatMap(l -> l.fragments().stream())
            .anyMatch(
                f ->
                    f.clickAction()
                            == io.github.dailystruggle.server.api.BookMenuSpec.ClickAction
                                .RUN_COMMAND
                        && f.clickValue().startsWith("/sleep token id=")
                        && f.hover().contains("/sleep cancel")),
        "Interactive line must include clickable [Cancel] RUN_COMMAND fragment with hover text");

    // Verify SHARD_HEARTBEAT with higher epoch seeds watermark without waking players or faking dawn
    accessor.wokenPlayers.clear();
    accessor.sentMessages.clear();
    accessor.setWorldPlayers(
        "world",
        List.of(
            new PlayerSleepSnapshot(
                p1, "Alice", true, 10, false, "SURVIVAL", false, 1000, Set.of("*"))));
    SleepSyncFrame heartbeatHigherEpoch =
        new SleepSyncFrame(
            SleepSyncFrame.FrameType.SHARD_HEARTBEAT,
            "shard-2",
            "overworld-main",
            "world",
            null,
            "",
            1,
            0,
            0,
            0,
            20,
            List.of(),
            List.of(),
            5L,
            18000L,
            true,
            System.currentTimeMillis());
    coord.onRemoteFrame(heartbeatHigherEpoch);
    assertEquals(
        5L,
        coord.getLastCommittedEpoch("overworld-main"),
        "SHARD_HEARTBEAT with higher epoch must advance local epoch watermark");
    assertTrue(
        accessor.wokenPlayers.isEmpty(),
        "SHARD_HEARTBEAT watermark sync must not wake sleeping players mid-night");
    assertFalse(
        accessor.sentMessages.stream().anyMatch(m -> m.contains("Rise and shine")),
        "SHARD_HEARTBEAT watermark sync must not broadcast a fake dawn message");

    // Verify clearWeatherOnSkip=false + clearThunderOnSkip=true downgrades THUNDER to RAIN (preserving rain)
    LeafSleepConfig preserveRainCfg =
        new LeafSleepConfig(
            "shard-1",
            QuorumPolicy.ONE_PLAYER,
            1,
            QuorumPolicy.CancelPolicy.VETO,
            baseCfg.sleepCurve(),
            baseCfg.cancelCurve(),
            20,
            0,
            0,
            true,
            false,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            baseCfg.sharding(),
            baseCfg.storage(),
            baseCfg.messages(),
            baseCfg.wakeupEffects(),
            baseCfg.sleepMessages(),
            twoWorldsSameGroup);
    ShardSleepCoordinator rainCoord =
        new ShardSleepCoordinator(
            accessor, () -> preserveRainCfg, store, transport, tokenService);
    accessor.weather.set(WeatherState.THUNDER);
    assertTrue(rainCoord.applyEpochCommit("overworld-main", "world", 10L, 0L, false));
    assertEquals(
        WeatherState.RAIN,
        accessor.getWeather("world"),
        "When clearWeatherOnSkip=false and clearThunderOnSkip=true, THUNDER must downgrade to RAIN rather than CLEAR");

    // Verify non-OP player with leafsleep.status + leafsleep.cancel (and no leafsleep.sleep) can run /sleep cancel and /sleep help
    UUID regularPlayer = UUID.randomUUID();
    Set<String> nonOpPerms = Set.of("leafsleep.status", "leafsleep.cancel", "oneplayersleep.ignore");
    accessor.sentMessages.clear();
    cmd.onCommand(regularPlayer, nonOpPerms::contains, replies::add, new String[] {"help"});
    cmd.onCommand(regularPlayer, nonOpPerms::contains, replies::add, new String[] {"ignore"});
    CommandsAPI.execute(Long.MAX_VALUE);
    assertTrue(
        accessor.sentMessages.stream().anyMatch(m -> m.contains("Subcommands: /sleep")),
        "Non-OP player with leafsleep.status must be able to execute /sleep help");
    assertTrue(
        accessor.sentMessages.stream().anyMatch(m -> m.contains("Sleep ignore enabled")),
        "Legacy oneplayersleep.ignore permission must grant /sleep ignore");

    transport.close();
    store.close();
  }

  @Test
  @DisplayName("ADR-001/ADR-002: Ineligible voter exclusion, toggleIgnore vote cleanup, PassTime sunrise lock-in, and premature WorldTimeSkip guard")
  void ineligibleVoterExclusionToggleIgnoreAndPrematureTimeSkipGuard(@TempDir Path tempDir)
      throws Exception {
    MockShardServerAccessor accessor = new MockShardServerAccessor(tempDir);
    accessor.setWorldNames(List.of("world"));
    LeafSleepConfig cfg = LeafSleepConfig.load(tempDir);

    SleepDatabaseStore store = new SleepDatabaseStore(accessor, tempDir, cfg.storage());
    assertTrue(store.initializeAsync().get(5, TimeUnit.SECONDS));
    SleepSyncTransport.LocalOnlyTransport transport = new SleepSyncTransport.LocalOnlyTransport(32);
    ShardSleepCoordinator coord =
        new ShardSleepCoordinator(accessor, () -> cfg, store, transport, new MenuTokenService());
    transport.start(coord::onRemoteFrame);

    UUID vanishedPlayer = UUID.randomUUID();
    UUID activePlayer = UUID.randomUUID();
    accessor.setPlayers(
        List.of(
            new PlayerSleepSnapshot(
                vanishedPlayer, "Ghost", false, 0, false, "SURVIVAL", true, 5000, Set.of("*")),
            new PlayerSleepSnapshot(
                activePlayer, "Builder", false, 0, false, "SURVIVAL", false, 5000, Set.of("*"))));

    // 1. Vanished/ineligible player votes or enters bed: must NOT be counted in sleepingCount or leak presence in chat
    accessor.sentMessages.clear();
    assertFalse(coord.voteSleep(vanishedPlayer, "Ghost", "world"));
    coord.onBedEnter(
        new PlayerBedEnterContext(vanishedPlayer, "Ghost", "world", 0, 64, 0, "OK", perm -> true));
    assertTrue(
        accessor.sentMessages.isEmpty(),
        "Vanished/ineligible player entering bed or voting must not broadcast presence or '(Sleeping: none)' to chat");
    ShardSleepCoordinator.GroupQuorumStatus afterVanishedVote =
        coord.evaluateGroupQuorum("overworld-main", "world", cfg);
    assertEquals(1, afterVanishedVote.eligibleCount());
    assertEquals(1, afterVanishedVote.ignoredCount());
    assertEquals(
        0,
        afterVanishedVote.sleepingCount(),
        "Ineligible (vanished/AFK/creative/ignored) player must not be added to sleepingCount by voter fallback loop");
    assertFalse(afterVanishedVote.quorumMet());

    // 2. Eligible player votes to sleep, then toggles /sleep ignore ON: active sleep vote must be cleared
    assertTrue(coord.voteSleep(activePlayer, "Builder", "world"));
    assertEquals(1, coord.evaluateGroupQuorum("overworld-main", "world", cfg).sleepingCount());
    assertTrue(coord.toggleIgnore(activePlayer));
    ShardSleepCoordinator.GroupQuorumStatus afterIgnoreToggle =
        coord.evaluateGroupQuorum("overworld-main", "world", cfg);
    assertEquals(
        0,
        afterIgnoreToggle.sleepingCount(),
        "Toggling /sleep ignore ON must clear the player's active sleep vote");
    assertFalse(coord.toggleIgnore(activePlayer)); // re-enable eligibility
    store.savePlayerProfileAsync(store.getCachedOrDefault(activePlayer).withCooldown(0L)).get(5, TimeUnit.SECONDS);

    // 3. Bed enter starts warmup; premature vanilla WorldTimeSkipContext while warmup is counting down must be cancelled
    coord.onBedEnter(
        new PlayerBedEnterContext(
            activePlayer, "Builder", "world", 0, 64, 0, "OK", perm -> true));
    ShardSleepCoordinator.GroupQuorumStatus afterBedEnter =
        coord.evaluateGroupQuorum("overworld-main", "world", cfg);
    assertEquals(1, afterBedEnter.eligibleCount());
    assertEquals(1, afterBedEnter.sleepingCount());

    io.github.dailystruggle.server.api.WorldTimeSkipContext prematureSkip =
        new io.github.dailystruggle.server.api.WorldTimeSkipContext(
            "world", "NIGHT_SKIP", 11000L, 0L);
    coord.onWorldTimeSkip(prematureSkip);
    assertTrue(
        prematureSkip.isCancelled(),
        "Premature vanilla NIGHT_SKIP during active warmup must be cancelled so warmup/cancel window is respected");
    assertEquals(0L, coord.getLastCommittedEpoch("overworld-main"));

    // 3b. Console /sleep cancel (CommandsAPI.serverId) immediately aborts even when physical bed sleeper snapshot still reports sleeping=true
    accessor.setPlayers(
        List.of(
            new PlayerSleepSnapshot(
                activePlayer, "Builder", true, 10, false, "SURVIVAL", false, 5000, Set.of("*"))));
    assertTrue(coord.cancelSleep(CommandsAPI.serverId, "Console", "world"));
    assertEquals(
        0,
        coord.evaluateGroupQuorum("overworld-main", "world", cfg).sleepingCount(),
        "Aborting sleep via /sleep cancel must exclude physical bed sleepers until they re-enter bed or wake up");

    // 3c. Verify PassTime after /time set night (rewound fullTime=13000L when lastCommittedEpoch=50) advances to 24000L, not 51*24000L
    coord.seedEpochWatermark("overworld-main", 50L);
    accessor.fullTime.set(13000L);
    accessor.setPlayers(
        List.of(
            new PlayerSleepSnapshot(
                activePlayer, "Builder", false, 0, false, "SURVIVAL", false, 5000, Set.of("*"))));
    store.savePlayerProfileAsync(store.getCachedOrDefault(activePlayer).withCooldown(0L)).get(5, TimeUnit.SECONDS);
    assertTrue(coord.voteSleep(activePlayer, "Builder", "world"));
    for (int i = 0; i < 220 && coord.getLastCommittedEpoch("overworld-main") == 50L; i++) {
      coord.tick();
    }
    assertEquals(51L, coord.getLastCommittedEpoch("overworld-main"));
    assertEquals(
        24000L,
        accessor.getWorldFullTime("world"),
        "PassTime after /time set night must stop at next sunrise (24000L) instead of spinning through 50 days");

    // 3d. Verify unconfigured *_nether and *_the_end worlds default to enabled=false
    assertFalse(cfg.resolveWorldConfig("survival_nether").enabled());
    assertFalse(cfg.resolveWorldConfig("survival_the_end").enabled());
    assertTrue(cfg.resolveWorldConfig("survival").enabled());

    // 4. Verify AbstractModServerAccessor OP vs non-OP permission evaluation via ops.json
    File opsFile = tempDir.resolve("ops.json").toFile();
    Files.writeString(
        opsFile.toPath(),
        "[{\"uuid\":\"" + activePlayer + "\",\"name\":\"Builder\",\"level\":4}]",
        StandardCharsets.UTF_8);
    io.github.dailystruggle.server.api.AbstractModServerAccessor modAccessor =
        new io.github.dailystruggle.server.api.AbstractModServerAccessor("TestMod", tempDir) {
          @Override
          public String getServerVersion() {
            return "26.1.2";
          }

          @Override
          public String getPlatform() {
            return "fabric";
          }
        };
    assertTrue(modAccessor.hasModPermission(activePlayer, "leafsleep.reload", opsFile));
    assertFalse(modAccessor.hasModPermission(vanishedPlayer, "leafsleep.reload", opsFile));
    assertFalse(modAccessor.hasModPermission(activePlayer, "leafsleep.bypass", opsFile));
    assertTrue(modAccessor.hasModPermission(vanishedPlayer, "leafsleep.cancel", opsFile));

    // 5. Verify additive network.yml, player.yml, commands.yml, and MultiConfigParser messages/*.yml seeding & validation
    assertTrue(
        Files.isRegularFile(tempDir.resolve("network.yml")),
        "LeafSleepConfig.load must seed additive network.yml alongside OnePlayerSleep configs");
    assertTrue(
        Files.isRegularFile(tempDir.resolve("player.yml")),
        "LeafSleepConfig.load must seed player.yml for player-facing sleep and prompt strings");
    assertTrue(
        Files.isRegularFile(tempDir.resolve("commands.yml")),
        "LeafSleepConfig.load must seed commands.yml for command, status, and error strings");
    assertTrue(
        Files.isDirectory(tempDir.resolve("messages"))
            && Files.isRegularFile(tempDir.resolve("messages/default.yml"))
            && Files.isRegularFile(tempDir.resolve("messages/cozy.yml"))
            && Files.isRegularFile(tempDir.resolve("messages/starlight.yml"))
            && Files.isRegularFile(tempDir.resolve("messages/campfire.yml"))
            && Files.isRegularFile(tempDir.resolve("messages/phantom.yml"))
            && Files.isRegularFile(tempDir.resolve("messages/nightmare.yml"))
            && Files.isRegularFile(tempDir.resolve("messages/rooster.yml"))
            && Files.isRegularFile(tempDir.resolve("messages/lazy.yml")),
        "LeafSleepConfig.load must extract bundled messages/*.yml themes via MultiConfigParser");
    assertTrue(
        cfg.sleepMessages().size() >= 8,
        "Expected at least 8 bundled sleep message themes from messages/*.yml, got: "
            + cfg.sleepMessages().size());
    Files.writeString(
        tempDir.resolve("messages/pirate.yml"),
        "version: \"1.0\"\n"
            + "weight: 25\n"
            + "sleepText: \"[player] hit the hammock ([sleepingPlayers]) - [requirements] [cancelPrompt] [sleepPrompt]\"\n"
            + "wakeupText: \"Land ho, [world]!\"\n"
            + "cancelPrompt: \"[Stand Watch]\"\n"
            + "sleepPrompt: \"[Hammock]\"\n",
        StandardCharsets.UTF_8);
    LeafSleepConfig reloadedWithCustomTheme = LeafSleepConfig.load(tempDir);
    assertEquals(
        "Land ho, [world]!",
        reloadedWithCustomTheme.selectMessage("pirate", null).wakeupText(),
        "MultiConfigParser must discover custom messages/*.yml files added by operators");
    assertTrue(
        LeafSleepConfig.validateDirectory(tempDir).isEmpty(),
        "Bundled config.yml, player.yml, commands.yml, sleep.yml, network.yml, and messages/*.yml must validate cleanly with zero DirectiveValidator findings");
    io.github.dailystruggle.rtp.common.configuration.yaml.RtpYamlConfig customNet =
        io.github.dailystruggle.rtp.common.configuration.yaml.RtpYamlConfig.parse(
            "version: \"1.0\"\n"
                + "serverShardId: \"survival-eu-2\"\n"
                + "sharding:\n"
                + "  enabled: true\n"
                + "  transport: \"plugin messaging\"\n"
                + "  channel: \"leafsleep:custom\"\n"
                + "storage:\n"
                + "  type: \"POSTGRESQL\"\n"
                + "  database: \"sleep_cluster\"\n");
    LeafSleepConfig netMerged =
        LeafSleepConfig.fromYaml(
            io.github.dailystruggle.rtp.common.configuration.yaml.RtpYamlConfig.load(
                tempDir.resolve("config.yml").toFile()),
            io.github.dailystruggle.rtp.common.configuration.yaml.RtpYamlConfig.load(
                tempDir.resolve("player.yml").toFile()),
            io.github.dailystruggle.rtp.common.configuration.yaml.RtpYamlConfig.load(
                tempDir.resolve("commands.yml").toFile()),
            io.github.dailystruggle.rtp.common.configuration.yaml.RtpYamlConfig.load(
                tempDir.resolve("sleep.yml").toFile()),
            customNet);
    assertEquals("survival-eu-2", netMerged.serverShardId());
    assertTrue(netMerged.sharding().enabled());
    assertEquals(
        LeafSleepConfig.TransportType.PLUGIN_MESSAGING, netMerged.sharding().transport());
    assertEquals("leafsleep:custom", netMerged.sharding().channel());
    assertEquals(LeafSleepConfig.StorageType.POSTGRESQL, netMerged.storage().type());
    assertEquals("sleep_cluster", netMerged.storage().database());

    // 6. Verify /sleep message preference command & interactive status/message UX
    LeafSleepBitSuite bitSuite =
        new LeafSleepBitSuite(accessor, () -> cfg, () -> store, () -> transport, () -> coord);
    net.leaf.sleep.common.command.LeafSleepCommand sleepCmd =
        new net.leaf.sleep.common.command.LeafSleepCommand(
            accessor, () -> cfg, () -> coord, () -> transport, () -> store, () -> bitSuite, List::of);
    List<String> cmdReplies = new ArrayList<>();
    accessor.sentMessages.clear();
    accessor.interactiveLines.clear();

    // Bare /sleep message shows current preference ('default') + clickable theme chips
    sleepCmd.onCommand(activePlayer, p -> true, cmdReplies::add, new String[] {"message"});
    CommandsAPI.execute(Long.MAX_VALUE);
    assertTrue(
        accessor.sentMessages.stream()
            .anyMatch(m -> m.contains("Current sleep message theme: default")),
        "Bare /sleep message must display the player's current theme and available options; actual="
            + accessor.sentMessages);
    assertFalse(
        accessor.interactiveLines.isEmpty(),
        "Bare /sleep message must send interactive clickable theme chips to in-game players");

    // Positional /sleep message cozy updates persistent wakeupMsgPref
    accessor.sentMessages.clear();
    sleepCmd.onCommand(activePlayer, p -> true, cmdReplies::add, new String[] {"message", "cozy"});
    CommandsAPI.execute(Long.MAX_VALUE);
    assertEquals("cozy", coord.getWakeupMessagePreference(activePlayer));
    assertTrue(
        accessor.sentMessages.stream().anyMatch(m -> m.contains("'cozy'")),
        "/sleep message cozy must confirm the updated theme");

    // Reset via /sleep message random clears preference back to weighted random
    sleepCmd.onCommand(activePlayer, p -> true, cmdReplies::add, new String[] {"message", "random"});
    CommandsAPI.execute(Long.MAX_VALUE);
    assertEquals("random", coord.getWakeupMessagePreference(activePlayer));

    // Invalid theme ID triggers S-007 badParameter
    accessor.sentMessages.clear();
    sleepCmd.onCommand(
        activePlayer, p -> true, cmdReplies::add, new String[] {"message", "nonexistent_theme"});
    CommandsAPI.execute(Long.MAX_VALUE);
    assertTrue(
        accessor.sentMessages.stream().anyMatch(m -> m.contains("nonexistent_theme")),
        "Invalid /sleep message theme ID must report configurable badParameter message (S-007)");

    // 7. Verify ignored player running /sleep sleep or /sleep cancel receives explicit feedback (S-004)
    accessor.fullTime.set(13000L);
    assertTrue(coord.toggleIgnore(activePlayer));
    accessor.sentMessages.clear();
    assertFalse(coord.voteSleep(activePlayer, "Builder", "world"));
    assertTrue(
        accessor.sentMessages.stream().anyMatch(m -> m.contains("Sleep ignore enabled")),
        "Player with /sleep ignore enabled must receive feedback when attempting /sleep sleep");
    assertFalse(coord.toggleIgnore(activePlayer));
    store.savePlayerProfileAsync(store.getCachedOrDefault(activePlayer).withCooldown(0L)).get(5, TimeUnit.SECONDS);

    // 8. Verify multi-world PassTime with divergent fullTime (e.g. world at day 0 vs world_2 at day 10)
    //    advances each world to its own next dawn without clamping the higher-day world backward
    accessor.setWorldNames(List.of("world", "world_survival_2"));
    accessor.fullTime.set(13000L);
    Map<String, Long> perWorldFullTime = new ConcurrentHashMap<>();
    perWorldFullTime.put("world", 13000L);
    perWorldFullTime.put("world_survival_2", 240_000L + 13000L);
    ServerAccessor divergentClockAccessor =
        new ServerAccessor() {
          @Override
          public String getServerVersion() {
            return "1.21.4";
          }

          @Override
          public String getPlatform() {
            return "paper";
          }

          @Override
          public Integer getServerIntVersion() {
            return 21;
          }

          @Override
          public void log(Level level, String message) {}

          @Override
          public void log(Level level, String message, Throwable throwable) {}

          @Override
          public Path getRootFolder() {
            return tempDir;
          }

          @Override
          public List<String> getWorldNames() {
            return List.of("world", "world_survival_2");
          }

          @Override
          public long getWorldTime(String worldName) {
            return Math.floorMod(getWorldFullTime(worldName), 24000L);
          }

          @Override
          public long getWorldFullTime(String worldName) {
            return perWorldFullTime.getOrDefault(worldName, 13000L);
          }

          @Override
          public void setWorldTime(String worldName, long timeTicks) {
            perWorldFullTime.put(worldName, timeTicks);
          }

          @Override
          public List<PlayerSleepSnapshot> getWorldPlayers(String worldName) {
            if ("world".equals(worldName)) {
              return List.of(
                  new PlayerSleepSnapshot(
                      activePlayer, "Builder", true, 10, false, "SURVIVAL", false, 1000, Set.of("*")));
            }
            return List.of();
          }
        };
    LeafSleepConfig divergentCfg =
        new LeafSleepConfig(
            "shard-1",
            QuorumPolicy.ONE_PLAYER,
            1,
            QuorumPolicy.CancelPolicy.VETO,
            cfg.sleepCurve(),
            cfg.cancelCurve(),
            0,
            true,
            true,
            5000,
            1,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            0L,
            12542L,
            23459L,
            true,
            32,
            cfg.sharding(),
            cfg.storage(),
            cfg.messages(),
            cfg.wakeupEffects(),
            cfg.sleepMessages(),
            Map.of(
                "world",
                new LeafSleepConfig.WorldSleepConfig(
                    "world",
                    true,
                    "overworld-main",
                    QuorumPolicy.ONE_PLAYER,
                    1,
                    QuorumPolicy.CancelPolicy.VETO,
                    cfg.sleepCurve(),
                    cfg.cancelCurve(),
                    0,
                    true,
                    true,
                    5000,
                    1),
                "world_survival_2",
                new LeafSleepConfig.WorldSleepConfig(
                    "world_survival_2",
                    true,
                    "overworld-main",
                    QuorumPolicy.ONE_PLAYER,
                    1,
                    QuorumPolicy.CancelPolicy.VETO,
                    cfg.sleepCurve(),
                    cfg.cancelCurve(),
                    0,
                    true,
                    true,
                    5000,
                    1)));
    ShardSleepCoordinator divCoord =
        new ShardSleepCoordinator(
            divergentClockAccessor, () -> divergentCfg, store, transport, new MenuTokenService());
    assertTrue(divCoord.voteSleep(activePlayer, "Builder", "world"));
    assertEquals(
        18000L,
        perWorldFullTime.get("world"),
        "Primary world must advance from 13000L to 18000L on first PassTime step");
    assertEquals(
        240_000L + 18000L,
        perWorldFullTime.get("world_survival_2"),
        "Secondary world with higher day count must advance from 253000L to 258000L instead of clamping backward to 24000L");

    transport.close();
    store.close();
  }
}
