package net.leaf.sleep.common;

import io.github.dailystruggle.bit.BitCode;
import io.github.dailystruggle.bit.BitComponent;
import io.github.dailystruggle.bit.BitFinding;
import io.github.dailystruggle.bit.BitReport;
import io.github.dailystruggle.bit.BitResult;
import io.github.dailystruggle.bit.BitRunner;
import io.github.dailystruggle.bit.BitSelector;
import io.github.dailystruggle.bit.BitSideEffect;
import io.github.dailystruggle.bit.BitTier;
import io.github.dailystruggle.server.api.ServerAccessor;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Built-In Test ({@code [OPS bit]}) suite registering PBIT, CBIT, and IBIT diagnostics for {@code
 * LeafSleep} / {@code OnePlayerSleep} ({@code LeafSleep ADR-002}).
 *
 * <p>Emits diagnostic codes {@code OPS_001}..{@code OPS_005} and the {@code [OPS bit]} banner
 * recognized by {@code discord-support-bot/support-bot.yml}.
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 */
public final class LeafSleepBitSuite {

  public static final BitCode OPS_001_CONFIG_SCHEMA =
      new BitCode(
          1,
          "OPS_CONFIG_SCHEMA",
          Level.ERROR,
          BitTier.PBIT,
          "config",
          "messages.reloadFailed",
          "Fix the YAML value in config.yml, messages.yml, or sleep.yml to match its @type/@range/@options directive.");

  public static final BitCode OPS_002_MESSAGE_TEMPLATES =
      new BitCode(
          2,
          "OPS_MESSAGE_TEMPLATES",
          Level.WARNING,
          BitTier.PBIT,
          "messages",
          "messages.reloadFailed",
          "Ensure all message templates in messages.yml are non-blank.");

  public static final BitCode OPS_003_TRANSPORT_HEALTH =
      new BitCode(
          3,
          "OPS_TRANSPORT_HEALTH",
          Level.ERROR,
          BitTier.CBIT,
          "transport",
          "messages.statusTransportLine",
          "Verify sharding.transport and bindPort / channel settings in config.yml.");

  public static final BitCode OPS_004_COORDINATOR_EPOCH =
      new BitCode(
          4,
          "OPS_COORDINATOR_EPOCH",
          Level.WARNING,
          BitTier.CBIT,
          "coordinator",
          "messages.statusWorldLine",
          "Verify world quorum policies and shardGroupId mappings in sleep.yml.");

  public static final BitCode OPS_005_STORAGE_ROUNDTRIP =
      new BitCode(
          5,
          "OPS_STORAGE_ROUNDTRIP",
          Level.ERROR,
          BitTier.IBIT,
          "storage",
          "messages.statusTransportLine",
          "Verify SQLite file permissions or remote SQL credentials in config.yml::storage.");

  private final ServerAccessor accessor;
  private final Supplier<LeafSleepConfig> configSupplier;
  private final Supplier<SleepDatabaseStore> storeSupplier;
  private final Supplier<SleepSyncTransport> transportSupplier;
  private final Supplier<ShardSleepCoordinator> coordinatorSupplier;
  private final BitRunner runner;

  public LeafSleepBitSuite(
      ServerAccessor accessor,
      Supplier<LeafSleepConfig> configSupplier,
      Supplier<SleepDatabaseStore> storeSupplier,
      Supplier<SleepSyncTransport> transportSupplier,
      Supplier<ShardSleepCoordinator> coordinatorSupplier) {
    this.accessor = Objects.requireNonNull(accessor, "accessor");
    this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
    this.storeSupplier = Objects.requireNonNull(storeSupplier, "storeSupplier");
    this.transportSupplier = Objects.requireNonNull(transportSupplier, "transportSupplier");
    this.coordinatorSupplier = Objects.requireNonNull(coordinatorSupplier, "coordinatorSupplier");
    this.runner = new BitRunner();
    registerComponents();
  }

  private void registerComponents() {
    runner.register(
        new BitComponent(
            "ops-config-pbit",
            "config",
            BitTier.PBIT,
            BitSideEffect.SAFE,
            2000L,
            this::runConfigPbit));
    runner.register(
        new BitComponent(
            "ops-messages-pbit",
            "messages",
            BitTier.PBIT,
            BitSideEffect.SAFE,
            2000L,
            this::runMessagesPbit));
    runner.register(
        new BitComponent(
            "ops-transport-cbit",
            "transport",
            BitTier.CBIT,
            BitSideEffect.SAFE,
            2000L,
            this::runTransportCbit));
    runner.register(
        new BitComponent(
            "ops-coordinator-cbit",
            "coordinator",
            BitTier.CBIT,
            BitSideEffect.SAFE,
            2000L,
            this::runCoordinatorCbit));
    runner.register(
        new BitComponent(
            "ops-storage-ibit",
            "storage",
            BitTier.IBIT,
            BitSideEffect.SAFE,
            3000L,
            this::runStorageAndWireIbit));
  }

  private BitReport runConfigPbit() {
    Path root = accessor.getRootFolder();
    List<BitFinding> findings = LeafSleepConfig.validateDirectory(root);
    return BitReport.of(findings);
  }

  private BitReport runMessagesPbit() {
    List<BitFinding> findings = new ArrayList<>();
    LeafSleepConfig cfg = configSupplier.get();
    LeafSleepConfig.MessagesConfig m = cfg.messages();
    if (m.sleepBroadcast() == null || m.sleepBroadcast().isBlank()) {
      findings.add(
          BitFinding.of(
              OPS_002_MESSAGE_TEMPLATES,
              "player.yml",
              "sleepBroadcast",
              "sleepBroadcast template is blank",
              "non-blank string",
              OPS_002_MESSAGE_TEMPLATES.remediation()));
    }
    if (m.wakeupBroadcast() == null || m.wakeupBroadcast().isBlank()) {
      findings.add(
          BitFinding.of(
              OPS_002_MESSAGE_TEMPLATES,
              "player.yml",
              "wakeupBroadcast",
              "wakeupBroadcast template is blank",
              "non-blank string",
              OPS_002_MESSAGE_TEMPLATES.remediation()));
    }
    for (LeafSleepConfig.SleepMessageEntry entry : cfg.sleepMessages()) {
      if (entry.sleepText() == null || entry.sleepText().isBlank()) {
        findings.add(
            BitFinding.of(
                OPS_002_MESSAGE_TEMPLATES,
                "messages/" + entry.id() + ".yml",
                "sleepText",
                "sleepText template is blank",
                "non-blank string",
                OPS_002_MESSAGE_TEMPLATES.remediation()));
      }
      if (entry.wakeupText() == null || entry.wakeupText().isBlank()) {
        findings.add(
            BitFinding.of(
                OPS_002_MESSAGE_TEMPLATES,
                "messages/" + entry.id() + ".yml",
                "wakeupText",
                "wakeupText template is blank",
                "non-blank string",
                OPS_002_MESSAGE_TEMPLATES.remediation()));
      }
    }
    return BitReport.of(findings);
  }

  private BitReport runTransportCbit() {
    List<BitFinding> findings = new ArrayList<>();
    SleepSyncTransport transport = transportSupplier.get();
    if (transport == null || !transport.isHealthy()) {
      findings.add(
          BitFinding.of(
              OPS_003_TRANSPORT_HEALTH,
              "config.yml",
              "sharding.transport",
              "SleepSyncTransport is not healthy",
              "healthy transport",
              OPS_003_TRANSPORT_HEALTH.remediation()));
    }
    LeafSleepConfig cfg = configSupplier.get();
    if (cfg != null
        && cfg.sharding().enabled()
        && cfg.sharding().transport() == LeafSleepConfig.TransportType.DIRECT_SOCKET
        && cfg.sharding().peers().isEmpty()) {
      findings.add(
          BitFinding.of(
              OPS_003_TRANSPORT_HEALTH,
              "config.yml",
              "sharding.peers",
              "sharding.enabled is true with DIRECT_SOCKET transport, but sharding.peers is empty",
              "at least one host:port peer",
              OPS_003_TRANSPORT_HEALTH.remediation()));
    }
    return BitReport.of(findings);
  }

  private BitReport runCoordinatorCbit() {
    List<BitFinding> findings = new ArrayList<>();
    ShardSleepCoordinator coord = coordinatorSupplier.get();
    if (coord == null) {
      findings.add(
          BitFinding.of(
              OPS_004_COORDINATOR_EPOCH,
              "sleep.yml",
              "worlds",
              "ShardSleepCoordinator is uninitialized",
              "active coordinator",
              OPS_004_COORDINATOR_EPOCH.remediation()));
      return BitReport.of(findings);
    }
    LeafSleepConfig cfg = configSupplier.get();
    if (cfg != null) {
      if (cfg.nightStartTicks() > cfg.nightEndTicks()) {
        findings.add(
            BitFinding.of(
                OPS_004_COORDINATOR_EPOCH,
                "config.yml",
                "nightStartTicks",
                "nightStartTicks ("
                    + cfg.nightStartTicks()
                    + ") exceeds nightEndTicks ("
                    + cfg.nightEndTicks()
                    + ")",
                "nightStartTicks <= nightEndTicks",
                OPS_004_COORDINATOR_EPOCH.remediation()));
      }
      int maxPlayers = Math.max(2, accessor.getMaxPlayers());
      int prevSleep = 1;
      int prevCancel = 1;
      for (int eligible = 1; eligible <= maxPlayers; eligible++) {
        int reqSleep = cfg.sleepCurve().evaluateRequired(eligible, maxPlayers);
        int reqCancel = cfg.cancelCurve().evaluateRequired(eligible, maxPlayers);
        if (reqSleep < prevSleep || reqSleep > eligible || reqCancel < prevCancel || reqCancel > eligible) {
          findings.add(
              BitFinding.of(
                  OPS_004_COORDINATOR_EPOCH,
                  "config.yml",
                  "sleepCurve",
                  "Quorum curve violated monotonicity or bounds at eligible=" + eligible,
                  "monotonically non-decreasing in [1, eligible]",
                  OPS_004_COORDINATOR_EPOCH.remediation()));
          break;
        }
        prevSleep = reqSleep;
        prevCancel = reqCancel;
      }
    }
    return BitReport.of(findings);
  }

  private BitReport runStorageAndWireIbit() {
    List<BitFinding> findings = new ArrayList<>();
    try {
      byte[] key = "ops-ibit-canary-secret-key".getBytes(StandardCharsets.UTF_8);
      SleepSyncFrame canary =
          new SleepSyncFrame(
              SleepSyncFrame.FrameType.SHARD_HEARTBEAT,
              "canary-shard",
              "overworld-main",
              "world",
              UUID.randomUUID(),
              "Canary",
              4,
              2,
              0,
              10L,
              0L,
              true,
              System.currentTimeMillis());
      SleepSyncFrame decoded = SleepSyncFrame.decodeSigned(canary.encodeSigned(key), key);
      if (decoded.targetDayEpoch() != 10L || decoded.sleepingCount() != 2) {
        findings.add(
            BitFinding.of(
                OPS_003_TRANSPORT_HEALTH,
                "SleepSyncFrame",
                "encodeSigned",
                "Canary frame round-trip mismatch",
                "identical decoded fields",
                OPS_003_TRANSPORT_HEALTH.remediation()));
      }
    } catch (Exception ex) {
      findings.add(
          BitFinding.of(
              OPS_003_TRANSPORT_HEALTH,
              "SleepSyncFrame",
              "encodeSigned",
              "Canary frame failed: " + ex.getMessage(),
              "valid HMAC frame",
              OPS_003_TRANSPORT_HEALTH.remediation()));
    }

    SleepDatabaseStore store = storeSupplier.get();
    if (store == null || !store.checkHealthSync()) {
      findings.add(
          BitFinding.of(
              OPS_005_STORAGE_ROUNDTRIP,
              "config.yml",
              "storage.type",
              "SQL database store health probe failed",
              "SELECT 1 == true",
              OPS_005_STORAGE_ROUNDTRIP.remediation()));
    }
    return BitReport.of(findings);
  }

  /** Executes all registered {@code [OPS bit]} components and returns formatted report lines. */
  public List<String> executeAllAndFormat() {
    List<BitResult> results = runner.run(BitSelector.parse("all"), Runnable::run);
    BitReport agg = runner.aggregate(results);
    List<String> lines = new ArrayList<>();
    lines.add(
        "[OPS bit] Health="
            + agg.health()
            + " | Components="
            + results.size()
            + " | Findings="
            + agg.findings().size());
    for (BitResult res : results) {
      lines.add(
          "[OPS bit] "
              + res.component().tier()
              + " "
              + res.component().id()
              + ": "
              + res.report().health()
              + " ("
              + res.durationMillis()
              + "ms)");
      for (BitFinding f : res.report().findings()) {
        lines.add(
            "[OPS bit]   "
                + f.severity()
                + " OPS_"
                + String.format("%03d", f.codeId())
                + " ("
                + f.code()
                + ") "
                + f.source()
                + ":"
                + f.path()
                + " - "
                + f.message());
      }
    }
    return lines;
  }

  public BitRunner runner() {
    return runner;
  }
}
