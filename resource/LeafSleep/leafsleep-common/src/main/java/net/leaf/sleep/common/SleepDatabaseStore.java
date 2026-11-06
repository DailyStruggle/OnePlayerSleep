package net.leaf.sleep.common;

import io.github.dailystruggle.database.api.CloseableDataSource;
import io.github.dailystruggle.database.api.SqlDataSourceFactory;
import io.github.dailystruggle.database.api.SqlDialect;
import io.github.dailystruggle.server.api.ServerAccessor;
import io.github.dailystruggle.server.api.TaskScheduler;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Asynchronous SQL persistence store ({@code SQLite}, {@code MySQL}, {@code MariaDB}, {@code
 * PostgreSQL}, {@code H2}) for player sleep preferences, cooldowns, counters, and shard-group epoch
 * watermarks ({@code LeafSleep ADR-001}).
 *
 * <p>Backed by {@link CloseableDataSource} and {@link SqlDialect} from {@code :database-api} so
 * network SQL engines reuse bounded connection pools ({@code MiniConnectionPool}) and local SQLite
 * uses a single-writer {@code WAL} connection without per-query socket or file-descriptor churn.
 * All database I/O executes strictly off the tick thread via {@link TaskScheduler#runAsync},
 * satisfying prohibition {@code S-005}.
 *
 * @governedBy ADR-001
 * @governedBy ADR-002
 * @governedBy leafrtp-ADR-079
 * @trace S-004
 * @trace S-005
 */
public final class SleepDatabaseStore implements AutoCloseable {

  /** Maximum cached player profiles held in memory before opportunistic eviction. */
  public static final int MAX_PROFILE_CACHE_SIZE = 4096;

  /** Immutable persistent player sleep profile row. */
  public record PlayerSleepProfile(
      UUID playerId,
      boolean sleepIgnored,
      String wakeupMsgPref,
      long cooldownUntilMs,
      long totalSleeps,
      long totalCancels) {

    public PlayerSleepProfile {
      Objects.requireNonNull(playerId, "playerId");
      wakeupMsgPref = wakeupMsgPref == null ? "default" : wakeupMsgPref;
    }

    public static PlayerSleepProfile defaults(UUID playerId) {
      return new PlayerSleepProfile(playerId, false, "default", 0L, 0L, 0L);
    }

    public PlayerSleepProfile withIgnore(boolean ignored) {
      return new PlayerSleepProfile(
          playerId, ignored, wakeupMsgPref, cooldownUntilMs, totalSleeps, totalCancels);
    }

    public PlayerSleepProfile withCooldown(long untilMs) {
      return new PlayerSleepProfile(
          playerId, sleepIgnored, wakeupMsgPref, untilMs, totalSleeps, totalCancels);
    }

    public PlayerSleepProfile incrementSleeps(long nextCooldownUntilMs) {
      return new PlayerSleepProfile(
          playerId,
          sleepIgnored,
          wakeupMsgPref,
          Math.max(cooldownUntilMs, nextCooldownUntilMs),
          totalSleeps + 1,
          totalCancels);
    }

    public PlayerSleepProfile incrementCancels(long nextCooldownUntilMs) {
      return new PlayerSleepProfile(
          playerId,
          sleepIgnored,
          wakeupMsgPref,
          Math.max(cooldownUntilMs, nextCooldownUntilMs),
          totalSleeps,
          totalCancels + 1);
    }
  }

  /** Immutable persistent shard-group epoch watermark row. */
  public record ShardEpochWatermark(
      String shardGroupId, long lastCommittedEpoch, long worldTimeTicks, long updatedAtMs) {}

  private final ServerAccessor accessor;
  private final LeafSleepConfig.StorageType storageType;
  private final SqlDialect dialect;
  private final CloseableDataSource dataSource;
  private final String playerTable;
  private final String epochTable;
  private final String playerUpsertSql;
  private final String epochUpsertSql;
  private final ExecutorService fallbackExecutor;
  private final Map<UUID, PlayerSleepProfile> profileCache = new ConcurrentHashMap<>();
  private final Map<String, ShardEpochWatermark> epochCache = new ConcurrentHashMap<>();
  private final AtomicBoolean healthy = new AtomicBoolean();
  private final AtomicBoolean closed = new AtomicBoolean();

  public SleepDatabaseStore(
      ServerAccessor accessor, Path rootDir, LeafSleepConfig.StorageConfig storageConfig) {
    this(
        accessor,
        Objects.requireNonNull(storageConfig, "storageConfig").type(),
        toDialect(storageConfig.type()),
        buildDataSource(rootDir, storageConfig),
        storageConfig.tablePrefix());
  }

  public SleepDatabaseStore(
      ServerAccessor accessor,
      CloseableDataSource dataSource,
      SqlDialect dialect,
      String tablePrefix) {
    this(
        accessor,
        fromDialect(dialect),
        dialect == null ? SqlDialect.SQLITE : dialect,
        Objects.requireNonNull(dataSource, "dataSource"),
        tablePrefix);
  }

  private SleepDatabaseStore(
      ServerAccessor accessor,
      LeafSleepConfig.StorageType storageType,
      SqlDialect dialect,
      CloseableDataSource dataSource,
      String tablePrefix) {
    this.accessor = Objects.requireNonNull(accessor, "accessor");
    this.storageType = Objects.requireNonNull(storageType, "storageType");
    this.dialect = Objects.requireNonNull(dialect, "dialect");
    this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    String prefix = sanitizeIdentifier(tablePrefix);
    this.playerTable = prefix + "player_prefs";
    this.epochTable = prefix + "shard_group_epochs";
    this.playerUpsertSql =
        this.dialect.buildUpsertSql(
            this.playerTable,
            List.of(
                "player_uuid",
                "sleep_ignored",
                "wakeup_msg_pref",
                "cooldown_until_ms",
                "total_sleeps",
                "total_cancels"),
            List.of("player_uuid"),
            List.of(
                "sleep_ignored",
                "wakeup_msg_pref",
                "cooldown_until_ms",
                "total_sleeps",
                "total_cancels"));
    this.epochUpsertSql =
        this.dialect.buildMonotonicUpsertSql(
            this.epochTable,
            List.of("shard_group_id", "last_committed_epoch", "world_time_ticks", "updated_at_ms"),
            "shard_group_id",
            "last_committed_epoch",
            List.of("world_time_ticks", "updated_at_ms"));
    this.fallbackExecutor =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "leafsleep-sql-async");
              t.setDaemon(true);
              return t;
            });
  }

  private static SqlDialect toDialect(LeafSleepConfig.StorageType type) {
    if (type == null) return SqlDialect.SQLITE;
    return switch (type) {
      case SQLITE -> SqlDialect.SQLITE;
      case MYSQL -> SqlDialect.MYSQL;
      case MARIADB -> SqlDialect.MARIADB;
      case POSTGRESQL -> SqlDialect.POSTGRESQL;
    };
  }

  private static LeafSleepConfig.StorageType fromDialect(SqlDialect dialect) {
    if (dialect == null) return LeafSleepConfig.StorageType.SQLITE;
    return switch (dialect) {
      case MYSQL -> LeafSleepConfig.StorageType.MYSQL;
      case MARIADB -> LeafSleepConfig.StorageType.MARIADB;
      case POSTGRESQL -> LeafSleepConfig.StorageType.POSTGRESQL;
      case SQLITE, H2, UNKNOWN -> LeafSleepConfig.StorageType.SQLITE;
    };
  }

  private static CloseableDataSource buildDataSource(
      Path rootDir, LeafSleepConfig.StorageConfig cfg) {
    SqlDialect dialect = toDialect(cfg.type());
    if (dialect == SqlDialect.SQLITE) {
      Path dbPath =
          (rootDir != null ? rootDir : Path.of(".")).resolve(cfg.sqliteFile()).toAbsolutePath();
      return SqlDataSourceFactory.createSqlite(dbPath);
    }
    String envPass = System.getenv(cfg.passwordEnv());
    String password = envPass != null ? envPass : "";
    return SqlDataSourceFactory.create(
        dialect, cfg.host(), cfg.port(), cfg.database(), cfg.username(), password);
  }

  private static String sanitizeIdentifier(String raw) {
    if (raw == null || raw.isBlank()) return "leafsleep_";
    return raw.replaceAll("[^A-Za-z0-9_]", "_");
  }

  private Executor asyncExecutor() {
    Optional<TaskScheduler> sched = accessor.scheduler();
    if (sched.isPresent()) {
      TaskScheduler ts = sched.get();
      return task -> ts.runAsync(task);
    }
    return fallbackExecutor;
  }

  private Connection openConnection() throws SQLException {
    return dataSource.getConnection();
  }

  /** Initializes schema tables asynchronously off the tick thread. */
  public CompletableFuture<Boolean> initializeAsync() {
    return CompletableFuture.supplyAsync(this::initializeSchemaSync, asyncExecutor());
  }

  boolean initializeSchemaSync() {
    String createPlayers =
        "CREATE TABLE IF NOT EXISTS "
            + playerTable
            + " ("
            + "player_uuid VARCHAR(36) PRIMARY KEY, "
            + "sleep_ignored INTEGER NOT NULL DEFAULT 0, "
            + "wakeup_msg_pref VARCHAR(64) NOT NULL DEFAULT 'default', "
            + "cooldown_until_ms BIGINT NOT NULL DEFAULT 0, "
            + "total_sleeps BIGINT NOT NULL DEFAULT 0, "
            + "total_cancels BIGINT NOT NULL DEFAULT 0"
            + ")";
    String createEpochs =
        "CREATE TABLE IF NOT EXISTS "
            + epochTable
            + " ("
            + "shard_group_id VARCHAR(128) PRIMARY KEY, "
            + "last_committed_epoch BIGINT NOT NULL DEFAULT 0, "
            + "world_time_ticks BIGINT NOT NULL DEFAULT 0, "
            + "updated_at_ms BIGINT NOT NULL DEFAULT 0"
            + ")";
    try (Connection conn = openConnection();
        Statement stmt = conn.createStatement()) {
      stmt.executeUpdate(createPlayers);
      stmt.executeUpdate(createEpochs);
      healthy.set(true);
      return true;
    } catch (SQLException ex) {
      healthy.set(false);
      accessor.log(
          Level.WARNING,
          "[LeafSleep] SQL schema initialization degraded ("
              + storageType
              + "): "
              + ex.getMessage());
      return false;
    }
  }

  /** Returns the cached player profile immediately without blocking the calling thread. */
  public PlayerSleepProfile getCachedOrDefault(UUID playerId) {
    if (playerId == null) {
      return PlayerSleepProfile.defaults(new UUID(0L, 0L));
    }
    PlayerSleepProfile existing = profileCache.get(playerId);
    if (existing != null) {
      return existing;
    }
    enforceProfileCacheCap();
    return profileCache.computeIfAbsent(playerId, PlayerSleepProfile::defaults);
  }

  /**
   * Evicts a player's cached profile on disconnect so offline players do not accumulate unboundedly
   * on high-churn networks.
   *
   * @param playerId player UUID to evict
   */
  public void evictPlayerProfile(UUID playerId) {
    if (playerId != null) {
      profileCache.remove(playerId);
    }
  }

  public int cachedProfileCount() {
    return profileCache.size();
  }

  private void enforceProfileCacheCap() {
    if (profileCache.size() < MAX_PROFILE_CACHE_SIZE) {
      return;
    }
    Iterator<UUID> it = profileCache.keySet().iterator();
    int toEvict = Math.max(1, MAX_PROFILE_CACHE_SIZE / 8);
    while (it.hasNext() && toEvict > 0) {
      it.next();
      it.remove();
      toEvict--;
    }
  }

  /** Warm-loads a player's profile from SQL asynchronously off the tick thread. */
  public CompletableFuture<PlayerSleepProfile> loadPlayerProfileAsync(UUID playerId) {
    Objects.requireNonNull(playerId, "playerId");
    return CompletableFuture.supplyAsync(
        () -> {
          String sql =
              "SELECT sleep_ignored, wakeup_msg_pref, cooldown_until_ms, total_sleeps, total_cancels "
                  + "FROM "
                  + playerTable
                  + " WHERE player_uuid = ?";
          try (Connection conn = openConnection();
              PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                PlayerSleepProfile loaded =
                    new PlayerSleepProfile(
                        playerId,
                        rs.getInt(1) != 0,
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getLong(5));
                enforceProfileCacheCap();
                profileCache.put(playerId, loaded);
                return loaded;
              }
            }
          } catch (SQLException ex) {
            accessor.log(
                Level.WARNING,
                "[LeafSleep] Failed to load sleep profile for "
                    + playerId
                    + ": "
                    + ex.getMessage());
          }
          return getCachedOrDefault(playerId);
        },
        asyncExecutor());
  }

  /** Updates the in-memory profile cache immediately and persists write-behind asynchronously. */
  public CompletableFuture<Boolean> savePlayerProfileAsync(PlayerSleepProfile profile) {
    Objects.requireNonNull(profile, "profile");
    enforceProfileCacheCap();
    profileCache.put(profile.playerId(), profile);
    return CompletableFuture.supplyAsync(
        () -> {
          try (Connection conn = openConnection();
              PreparedStatement ps = conn.prepareStatement(playerUpsertSql)) {
            ps.setString(1, profile.playerId().toString());
            ps.setInt(2, profile.sleepIgnored() ? 1 : 0);
            ps.setString(3, profile.wakeupMsgPref());
            ps.setLong(4, profile.cooldownUntilMs());
            ps.setLong(5, profile.totalSleeps());
            ps.setLong(6, profile.totalCancels());
            ps.executeUpdate();
            healthy.set(true);
            return true;
          } catch (SQLException ex) {
            accessor.log(
                Level.WARNING,
                "[LeafSleep] Failed to persist sleep profile for "
                    + profile.playerId()
                    + ": "
                    + ex.getMessage());
            return false;
          }
        },
        asyncExecutor());
  }

  /** Loads the latest committed epoch watermark for {@code shardGroupId} asynchronously. */
  public CompletableFuture<Optional<ShardEpochWatermark>> loadEpochWatermarkAsync(
      String shardGroupId) {
    Objects.requireNonNull(shardGroupId, "shardGroupId");
    return CompletableFuture.supplyAsync(
        () -> {
          String sql =
              "SELECT last_committed_epoch, world_time_ticks, updated_at_ms FROM "
                  + epochTable
                  + " WHERE shard_group_id = ?";
          try (Connection conn = openConnection();
              PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shardGroupId);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                ShardEpochWatermark wm =
                    new ShardEpochWatermark(
                        shardGroupId, rs.getLong(1), rs.getLong(2), rs.getLong(3));
                epochCache.put(shardGroupId, wm);
                return Optional.of(wm);
              }
            }
          } catch (SQLException ex) {
            accessor.log(
                Level.WARNING,
                "[LeafSleep] Failed to load epoch watermark for "
                    + shardGroupId
                    + ": "
                    + ex.getMessage());
          }
          return Optional.ofNullable(epochCache.get(shardGroupId));
        },
        asyncExecutor());
  }

  /** Persists a newly committed {@link ShardEpochWatermark} asynchronously off the tick thread. */
  public CompletableFuture<Boolean> saveEpochWatermarkAsync(ShardEpochWatermark watermark) {
    Objects.requireNonNull(watermark, "watermark");
    epochCache.put(watermark.shardGroupId(), watermark);
    return CompletableFuture.supplyAsync(
        () -> {
          try (Connection conn = openConnection();
              PreparedStatement ps = conn.prepareStatement(epochUpsertSql)) {
            ps.setString(1, watermark.shardGroupId());
            ps.setLong(2, watermark.lastCommittedEpoch());
            ps.setLong(3, watermark.worldTimeTicks());
            ps.setLong(4, watermark.updatedAtMs());
            ps.executeUpdate();
            healthy.set(true);
            return true;
          } catch (SQLException ex) {
            accessor.log(
                Level.WARNING,
                "[LeafSleep] Failed to persist epoch watermark for "
                    + watermark.shardGroupId()
                    + ": "
                    + ex.getMessage());
            return false;
          }
        },
        asyncExecutor());
  }

  /** Executes a lightweight health probe query for {@code [OPS bit]}. */
  public boolean checkHealthSync() {
    if (closed.get()) return false;
    try (Connection conn = openConnection();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT 1")) {
      boolean ok = rs.next();
      healthy.set(ok);
      return ok;
    } catch (SQLException ex) {
      return false;
    }
  }

  /** Executes a lightweight health probe query asynchronously off the tick thread. */
  public CompletableFuture<Boolean> checkHealthAsync() {
    return CompletableFuture.supplyAsync(this::checkHealthSync, asyncExecutor());
  }

  public boolean isHealthy() {
    return healthy.get() && !closed.get();
  }

  public LeafSleepConfig.StorageType storageType() {
    return storageType;
  }

  public SqlDialect dialect() {
    return dialect;
  }

  public CloseableDataSource dataSource() {
    return dataSource;
  }

  @Override
  public void close() {
    closed.set(true);
    fallbackExecutor.shutdown();
    dataSource.close();
  }
}
