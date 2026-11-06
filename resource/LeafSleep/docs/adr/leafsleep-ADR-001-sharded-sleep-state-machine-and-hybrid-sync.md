# leafsleep-ADR-001 - Sharded Sleep State Machine, Idempotent Epoch CAS, and Hybrid Direct/Database Sync

**Status:** Accepted
**Date:** 2026-10-27

## Context

`LeafSleep` is the sharded sequel to `OnePlayerSleep`. Classic `OnePlayerSleep` assumes a single-JVM, single-threaded `World` where player counts, bed-enter/leave events, and world-clock transitions are local and synchronous.

On modern survival networks, a single logical world is frequently sharded across:
1. **Intra-JVM regional tick threads (`Folia`):** Players in the same dimension tick on independent regional threads (`Rule F-001..F-003`).
2. **Cross-JVM server shards (`Paper`, `Folia`, `Fabric`, `NeoForge` behind `Velocity`):** Multiple backend servers host regions or instances of the same logical Overworld (`shardGroupId`, e.g., `overworld-main`).

Two distinct data-plane velocities are required for cross-shard sleep:
- **Interactive sleep/cancel coordination** (`BED_ENTER`, `BED_LEAVE`, `SLEEP_VOTE`, `CANCEL_SLEEP`, `EPOCH_COMMIT`) must propagate in `< 50 ms` so countdowns, quorum tallies, and `[Cancel]` clicks feel instantaneous.
- **Persistent player state and epoch watermarks** (`sleepIgnored` opt-out flags, wakeup message preferences, personal sleep/cancel counters, cooldowns, and last-committed `targetDayEpoch`) belong in a shared SQL database (`SQLite`, `MySQL`, `MariaDB`, `PostgreSQL`) so state survives server switches and restarts without blocking tick threads.

## Decision

Implement a deterministic sharded quorum and epoch state machine (`ShardSleepCoordinator`) in `leafsleep-common`, backed by a hybrid **Direct Messaging Bus** (fast transient state) and an **Asynchronous SQL Store** (durable player and epoch state).

### 1. `ShardGroup` Quorum Aggregation & Warmup Window
- Each configured world maps to a logical `shardGroupId` (defaulting to the local world name when unsharded, or a shared cluster identifier such as `overworld-main`).
- Each shard tracks its local `(eligibleCount, sleepingCount, ignoredCount)` from platform-neutral `PlayerSleepSnapshot` records (excluding AFK, vanished, Creative/Spectator, or `sleepIgnored` players per config) and broadcasts delta frames to peers in the same `shardGroupId`.
- `ShardSleepCoordinator` evaluates the aggregated counts across all live shards in `shardGroupId` against the configured policy:
  - `ONE_PLAYER` (classic 1-player sleep across the entire shard group)
  - `PERCENTAGE` (e.g., `25%` of eligible players across all shards in the group)
  - `FIXED_COUNT` (e.g., `N` sleeping players across the group)
  - `ABSOLUTE_MAJORITY` (`> 50%` of eligible players)
- When quorum is met, a configurable `warmupDelayTicks` countdown begins. During the warmup window, any player with `leafsleep.cancel` (via `/sleep cancel` or clickable chat `[Cancel]`) aborts the pending skip across all shards in the group, optionally waking sleeping players and applying a cancel cooldown.

### 2. Idempotent `targetDayEpoch` Transition (Split-Brain Prevention)
- When the warmup completes with quorum intact, the coordinating shard emits `EPOCH_COMMIT(shardGroupId, targetDayEpoch, targetDayTimeTicks, clearWeather)` where `targetDayEpoch = currentFullTime / 24000L + 1`.
- Every shard in `shardGroupId` applies `EPOCH_COMMIT` idempotently using an atomic high-water mark (`if (targetDayEpoch <= lastCommittedDayEpoch) return;` CAS):
  1. Advances the world clock to `targetDayTimeTicks` (dawn) and clears weather on the global region thread.
  2. Resets `TIME_SINCE_REST` for eligible online players on their owning entity threads so Phantoms do not spawn on any shard after a group night skip.
  3. Dispatches weighted random wakeup messages and `effects-api` wakeup buffs/sounds/particles locally.
- Periodic `SHARD_HEARTBEAT` frames and SQL epoch watermarks reconcile world clocks for newly booted or reconnecting shards.

### 3. Hybrid Transport & Storage Plane
- **Fast Path — Direct Messaging Bus (`SleepSyncTransport`):**
  - Supports `DIRECT_SOCKET` (HMAC-signed TCP/UDP peer gossip for fastest direct server-to-server delivery), `PLUGIN_MESSAGING` (`leafsleep:sync` with optional `leafsleep-velocity` proxy fanout), `REDIS_PUBSUB`, and `LOCAL_ONLY`.
  - Protected per player and per shard by `BudgetPacer` (`api/predict-api`) to prevent bed-click spam from amplifying across the network.
- **Durable Path — Async SQL Database (`SleepDatabaseStore`):**
  - Supports `SQLITE` (default single-server file DB) and `MYSQL` / `MARIADB` / `POSTGRESQL` via lightweight connection pooling.
  - All queries and writes execute strictly off the tick thread (`TaskScheduler#runAsync`): player profiles warm-load on `PlayerLifecycleHook#onPlayerJoin` and flush write-behind on mutation; `shard_group_epochs` persists the latest committed `targetDayEpoch` and world clock watermark.

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|--------------|
| Database-polling only for sleep synchronization | Polling SQL every tick adds `50-500 ms` latency and unnecessary DB load during interactive bed-enter and cancel windows. |
| Direct messaging only (no SQL persistence) | Loses player ignore/opt-out preferences, cooldowns, and `targetDayEpoch` watermarks when players switch servers or a shard restarts. |
| Unbounded or unversioned time-skip broadcasts | Without a monotonic `targetDayEpoch` CAS guard, two shards reaching quorum on the same tick would double-advance the world clock. |

## Consequences

- **Positive:**
  - Sub-50ms cross-shard bed-enter, vote, and cancel responsiveness via direct messaging.
  - Durable cross-server player preferences, cooldowns, and epoch recovery via async SQL.
  - Zero double-skips or clock oscillation under concurrent multi-shard quorum commits.
- **Negative / Trade-offs:**
  - Direct peer UDP/TCP or plugin-messaging deployments require a shared HMAC secret or proxy channel configuration when running across multiple hosts.

## References

- [`ADR-002`](ADR-002-multi-platform-carrier-and-config-architecture.md) (Multi-Platform Carrier Architecture via `ServerAccessor`, `OnePlayerSleep` Configuration Superset, and `[OPS bit]`)
- `api/predict-api` (`BudgetPacer`)
