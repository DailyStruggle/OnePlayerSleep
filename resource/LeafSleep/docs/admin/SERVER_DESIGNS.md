# Server & Network Design Guide (`LeafSleep` / `OnePlayerSleep`)

This guide walks server administrators through **obvious (standard)** and **non-obvious (advanced / edge-case)** server designs, showing the exact `config.yml`, `sleep.yml`, and permission configurations for each architecture.

---

## Topology Decision Matrix

| Server Design | `sharding.enabled` | `sharding.transport` | `storage.type` | Recommended `defaultQuorumPolicy` & `defaultCancelPolicy` |
|---|---|---|---|---|
| **1. Classic Single-Player-Sleep SMP** (`Paper` / `Spigot` / `Fabric` / `NeoForge`) | `false` | `"local only"` | `"SQLITE"` | `defaultQuorumPolicy: "one player"`, `defaultCancelPolicy: "tug of war"` (or `"veto"` for trusted friend servers) |
| **2. Folia Regionised Single Server** (`Folia 1.21.11+`) | `false` | `"local only"` | `"SQLITE"` | `defaultQuorumPolicy: "curve"`, `defaultCancelPolicy: "tug of war"` |
| **3. 24/7 Global Time-Zone SMP** (1 player at 4 AM, 100+ at peak) | `false` (or `true` if sharded) | `"local only"` (or `"redis pubsub"`) | `"SQLITE"` / `"MYSQL"` | `defaultQuorumPolicy: "curve"` (`smootherstep` or `logarithmic`, `minRequired: 1`, `maxRequired: 8`), `defaultCancelPolicy: "tug of war"` |
| **4. Multi-World Single JVM** (`Multiverse-Core` / Hub + Survival + Creative + Resource) | `false` | `"local only"` | `"SQLITE"` | Per-world `shardGroupId` isolation in `sleep.yml`; `enabled: false` on lobby, creative, Nether, End |
| **5. Velocity Proxy + Hub + Single Survival Backend** | `false` | `"local only"` | `"SQLITE"` | Install only on the survival backend (`sharding.enabled: false`); no Velocity plugin or Redis required |
| **6. Horizontally Sharded Survival Cluster (2–100+ Shards, Shared Day/Night)** | `true` | `"redis pubsub"` (recommended) | `"MYSQL"` / `"MARIADB"` / `"POSTGRESQL"` | Unique `serverShardId` per shard, shared `shardGroupId: "overworld-main"` in `sleep.yml`, `defaultQuorumPolicy: "curve"` |
| **7. Zero-Redis Velocity Cluster (Single Proxy, 2–10 Shards)** | `true` | `"plugin messaging"` | `"SQLITE"` or `"MYSQL"` | Install `LeafSleep` on backends + Velocity (`leafsleep:sync`); note empty (`0`-player) shards only receive packets when a player is connected |
| **8. Private VLAN / WireGuard Mesh Without Redis or Proxy Plugin** | `true` | `"direct socket"` | `"MYSQL"` / `"POSTGRESQL"` | Static `sharding.peers` UDP mesh with `LEAFSLEEP_HMAC_SECRET` and NTP clock sync (`±30s` replay window) |
| **9. Multi-Realm Shared Infrastructure (SMP-Alpha + SMP-Beta on Same Redis/Velocity)** | `true` | `"redis pubsub"` or `"plugin messaging"` | `"MYSQL"` / `"POSTGRESQL"` | Distinct `shardGroupId` per realm (`"alpha-overworld"` vs `"beta-overworld"`) in `sleep.yml` so realms never skip each other's night |
| **10. Asymmetric Main-Survival + Underground Resource Shards** | `true` | `"redis pubsub"` | `"MYSQL"` / `"POSTGRESQL"` | Either separate `shardGroupId: "resource-overworld"` (`policy: "one player"`) OR shared `shardGroupId` with `/sleep ignore` for cave miners |

---

## Part 1: Obvious Server Designs

### 1. Classic Small-Group or Friends SMP (`ONE_PLAYER` vs `PERCENTAGE`)

On a small private SMP (2–15 friends) where any single player entering a bed should skip the night after a 3-second countdown:

```yaml
# config.yml
serverShardId: "shard-1"
defaultQuorumPolicy: "one player"
defaultCancelPolicy: "veto"
warmupDelayTicks: 60
clearWeatherOnSkip: true
clearThunderOnSkip: true
resetPhantomStatistic: true
sharding:
  enabled: false
  transport: "local only"
storage:
  type: "SQLITE"
```

- **Why `warmupDelayTicks: 60` matters:** Even on a `one player` server, keeping a 3-second (`60`-tick) warmup gives a friend fighting a Phantom or curing a zombie villager time to click `[Cancel]` in chat before dawn commits.
- **When to use `PERCENTAGE` instead:** If you want a fixed fraction (e.g. `25%` of eligible players) on a server with a steady player count, set `defaultQuorumPolicy: "percentage"` and `defaultQuorumValue: 25`.

---

### 2. Folia Regionised Multithreaded Survival Server

On `Folia`, the world is partitioned into independent region tick threads that execute in parallel.

- **How `LeafSleep` stays Folia-safe automatically:**
  - `ShardSleepCoordinator` runs its 1 Hz idle reconciliation and warmup countdown on Folia's `GlobalRegionScheduler`.
  - `BukkitPluginServerAccessor` avoids cross-region `Statistic.TIME_SINCE_REST` and cross-world `RegionizedWorldData` reads during player snapshot collection.
  - When dawn commits (`applyEpochCommit`), world time (`setFullTime`) and weather clear execute on the global region thread, while `resetPhantomStatistic: true` (`TIME_SINCE_REST = 0`) and `wakeupEffects` (`SOUND`, `PARTICLE`, `POTION` in `sleep.yml`) are dispatched onto each player's owning `EntityScheduler`.
- **Configuration:** Identical to a Paper server (`sharding.enabled: false` for a single Folia server, or `sharding.enabled: true` when clustering multiple Folia/Paper shards). Verify Folia scheduler health anytime with `/sleep test` and `/sleep status`.

---

### 3. Multi-World Single Server (`Multiverse-Core` / Creative + Survival + Minigames)

Many single-JVM servers host multiple worlds at once: `world` (main survival), `world_nether`, `world_the_end`, `resource_world`, `creative_plots`, and `lobby`.

**Non-obvious trap:** If you add multiple Overworld-environment worlds to `sleep.yml` and give them the same `shardGroupId: "overworld-main"`, players in `creative_plots` or `resource_world` will be merged into the same quorum pool and skip `world`'s night!

**Solution (`sleep.yml`):**
- Set `enabled: false` on dimensions where beds explode or time is locked (`world_nether`, `world_the_end`, `lobby`, `creative_plots`, `arena`).
- Give each active survival world its own `shardGroupId` if their clocks are independent, **or** the same `shardGroupId` if you intentionally want synchronized dawn across both worlds:

```yaml
# sleep.yml
worlds:
  world:
    enabled: true
    shardGroupId: "overworld-main"
    policy: "curve"
    cancelPolicy: "tug of war"
    warmupDelayTicks: 60
  resource_world:
    enabled: true
    shardGroupId: "resource-independent"
    policy: "one player"
    cancelPolicy: "tug of war"
    warmupDelayTicks: 40
  creative_plots:
    enabled: false
    shardGroupId: "creative-disabled"
  lobby:
    enabled: false
    shardGroupId: "lobby-disabled"
  world_nether:
    enabled: false
    shardGroupId: "nether-main"
  world_the_end:
    enabled: false
    shardGroupId: "end-main"
```

---

### 4. Velocity / BungeeCord Proxy + Hub + Single Survival Backend

If your network has a `Velocity` proxy, a `lobby` server, and **only one** `survival` server:
- **Do not overcomplicate your setup:** You do **not** need `LeafSleep` on Velocity or `lobby`, and you do **not** need Redis or MySQL.
- Install `LeafSleep` **only** on the `survival` backend with `sharding.enabled: false`, `sharding.transport: "local only"`, and `storage.type: "SQLITE"`.

---

## Part 2: Non-Obvious & Advanced Server Designs

### 5. 24/7 Global Time-Zone Server (Curve Selection & Anti-Troll `TUG_OF_WAR` Design)

**The problem:** On a 24/7 public survival server (or multi-shard network) with `max-players: 80`:
- At **4:00 AM UTC**, only `2` players are online (`1` building on the surface, `1` AFK or mining at `Y = -54`). A `FIXED_COUNT: 5` policy makes skipping night impossible; a `PERCENTAGE: 50` policy forces the underground miner to carry a bed.
- At **8:00 PM UTC peak**, `60` players are online. A `ONE_PLAYER` policy lets `1` player skip the night every 20 minutes while 59 players are hunting Phantoms or fighting bosses; a `PERCENTAGE: 50` policy requires `30` players in beds simultaneously (which never happens in survival).

**The solution (`defaultQuorumPolicy: "curve"` + `defaultCancelPolicy: "tug of war"`):**
`QuorumPolicy.CURVE` computes normalized population load $t = (\text{eligible} - 1) / (\text{maxPlayers} - 1) \in [0, 1]$, applies a low-population guard (`max(1, eligibleCount / 2)` when `minRequired: 1`), and caps peak requirements at `maxRequired`.

#### Choosing the Right Curve Shape (`sleepCurve.type` & `cancelCurve.type`)

| Curve `type` | Mathematical Behavior | Empirical Requirement (`20/80` eligible across 4 shards, `1..14` range) | Best Server Archetype |
|---|---|---|---|
| `smootherstep` | Zero-derivative flat ends ($6t^5 - 15t^4 + 10t^3$). Stays near `minRequired` through low-to-mid pop ($t < 0.30$), then ramps smoothly in midrange. | `sleepReq = 3`, `cancelReq = 2` (for `1..20` / `1..12` bounds) | **Casual / Builder SMPs:** Feels like 1–3 player sleep during quiet and moderate hours, only asking for a larger group at peak. |
| `linear` | Straight line $f(t) = t$ from `minRequired` to `maxRequired`. | `sleepReq = 4`, `cancelReq = 2` | **Predictable Proportional SMPs:** Simple linear scaling with a hard ceiling (`maxRequired`) so peak hours never exceed `maxRequired`. |
| `power` | Polynomial $f(t) = t^p$ (`parameter: 0.5` $\sqrt{t}$ default when `type: "power"`). Concave for $p < 1$, convex for $p > 1$. | `sleepReq = 7`, `cancelReq = 4` (at `p = 0.55`) | **Balanced Mid-Size Servers:** $p \in [0.4, 0.6]$ ramps smoothly like square-root; $p \in [2.0, 3.0]$ stays near `1` sleeper until the server is over 50% full. |
| `logarithmic` | $f(t) = \ln(1 + (k - 1)t) / \ln k$ (`parameter: 10.0` default). Rises early as soon as a small group (`5–15` players) is online and plateaus near `maxRequired`. | `sleepReq = 8`, `cancelReq = 4` (at `k = 10.0`) | **Community / Semi-Hardcore SMPs (Default):** Ensures a single player cannot skip night once 6+ players are online, while capping peak requirements cleanly. |
| `sigmoid` | Logistic S-curve with steepness `parameter` (e.g. `10.0`–`14.0`) and inflection `midpoint` $\in [0, 1]$ (e.g. `0.15`–`0.50`). | `sleepReq = 10`, `cancelReq = 6` (at `parameter: 14.0, midpoint: 0.15`) | **Two-Regime Servers:** Switches sharply from low-pop casual mode ($t < \text{midpoint}$) to full peak-hour group voting ($t > \text{midpoint}$). |

#### Choosing the Right Cancel Policy (`defaultCancelPolicy` / `worlds.<world>.cancelPolicy`)

- **`"tug of war"` (Recommended Default):**
  - Every player who clicks `[Cancel]` (`/sleep cancel`) **offsets 1 sleeping player**:
    $$\text{effectiveRequired} = \min(\text{eligibleCount},\; \text{sleepRequired} + \text{cancellingCount})$$
  - Example: If `sleepRequired = 3` and `cancelRequired = 2`, and `3` players sleep (`3/3`), warmup starts (`60` ticks). If `1` player clicks `[Cancel]`, `effectiveRequired` rises to `3 + 1 = 4` (`3/4` sleeping, `1/2` cancelling), pausing the warmup so a 4th sleeper can overcome the cancel **or** a 2nd canceller (`2/2`) can abort the skip completely and wake everyone (`wakeOnCancel: true`).
  - Prevents a single troll from vetoing 30 players while still giving night-hunters real counter-vote leverage.
- **`"curve"` (`CANCEL_THRESHOLD`):**
  - Cancel votes do not raise `effectiveRequired` one-for-one, but if `cancellingCount >= cancelRequired` (from `cancelCurve`), the countdown aborts immediately.
- **`"veto"` (`ONE_VETO`):**
  - Any `1` cancel vote immediately aborts the countdown. Use **only** on small cooperative whitelist servers where if anyone needs night for a farm or boss fight, the whole server agrees to wait.

---

### 6. Horizontally Sharded Multi-Server Survival Cluster (2 to 100+ Paper / Folia Shards)

When a large survival world is split across multiple backend servers (`backend-a`, `backend-b`, `backend-c`, `backend-d`—mixing Paper and Folia freely behind Velocity and Redis):

#### Architecture & Invariants
1. **Unique `serverShardId` per Backend (`config.yml`):**
   - Set `serverShardId: "backend-a"` on Shard A, `"backend-b"` on Shard B, `"backend-c"` on Shard C, `"backend-d"` on Shard D.
   - `/sleep status` aggregates `(eligibleCount, sleepingCount, cancellingCount, maxPlayers)` across all live shards in the `shardGroupId` (e.g., 4 shards with `max-players=20` each report `shards=4, eligible=20/80`).
2. **Shared `shardGroupId` (`sleep.yml`):**
   - Set `worlds.world.shardGroupId: "overworld-main"` on all 4 shards.
3. **Why `REDIS_PUBSUB` Is Preferred Over `PLUGIN_MESSAGING` for 3+ Shards or Multi-Proxy Networks:**
   - `sharding.transport: "redis pubsub"` uses `:database-api`'s zero-dependency `RespPool` / `RespPubSub` with an asynchronous outbound writer thread (`zero` main-thread TCP blocking) and marshals incoming frames onto the server scheduler.
   - Crucially, `REDIS_PUBSUB` delivers `EPOCH_COMMIT` and `SHARD_HEARTBEAT` frames to a shard **even when that shard has `0` online players**, keeping world clocks synchronized across empty and populated shards alike.
   - Configure Redis host/port/password via `sharding.bindHost` and `sharding.bindPort` in `config.yml` (or environment variables `LEAFSLEEP_REDIS_HOST`, `LEAFSLEEP_REDIS_PORT`, `LEAFSLEEP_REDIS_PASSWORD`) and sign frames with `LEAFSLEEP_HMAC_SECRET`.
4. **N-Shard Cancel & Stale-Heartbeat Protection:**
   - When cancel quorum is reached on a 3rd or 4th shard, `ShardSleepCoordinator` broadcasts the complete cross-shard `cancellingPlayers` list inside the committed `CANCEL_SLEEP` abort payload and stamps `groupLastResetAtMs` so any in-flight `SHARD_HEARTBEAT` emitted by a peer shard prior to the reset has its `sleepingCount`/`cancellingCount` clamped to `0` rather than resurrecting cleared votes.

```yaml
# config.yml (on backend-a; change serverShardId on backend-b, backend-c, backend-d)
serverShardId: "backend-a"
defaultQuorumPolicy: "curve"
defaultCancelPolicy: "tug of war"
warmupDelayTicks: 60
sharding:
  enabled: true
  transport: "redis pubsub"
  channel: "leafsleep:sync"
  bindHost: "redis.internal"
  bindPort: 6379
  hmacSecretEnv: "LEAFSLEEP_HMAC_SECRET"
  heartbeatIntervalTicks: 100
storage:
  type: "MYSQL"
  host: "db.internal"
  port: 3306
  database: "leafsleep"
  username: "leafsleep"
  passwordEnv: "LEAFSLEEP_DB_PASSWORD"
  tablePrefix: "leafsleep_"
```

---

### 7. Zero-Redis Velocity Cluster (`PLUGIN_MESSAGING` Mode)

If you run 2–10 backend shards behind a **single** `Velocity` proxy and do not want to run Redis:

1. Install the same `LeafSleep` universal jar on **Velocity** (`plugins/LeafSleep.jar`) and on every backend shard.
2. Set `sharding.enabled: true`, `sharding.transport: "plugin messaging"`, and `sharding.channel: "leafsleep:sync"` in each backend's `config.yml`.
3. `VelocityLeafSleepPlugin` learns which backend servers belong to which `shardGroupId` and fans frames out to all connected peer servers with at least 1 online player without echoing back to the origin server.
4. **Non-obvious caveat:** Because Minecraft plugin messaging travels over player connections, a backend shard with `0` online players cannot receive plugin messages until a player connects to it. If you need empty shards to advance their clock while empty, use `sharding.transport: "redis pubsub"`.

---

### 8. Multi-Realm Networks Sharing One Redis or Velocity Instance

**Scenario:** You host **SMP-Alpha** (`alpha-1`, `alpha-2`) and **Hardcore-Beta** (`beta-1`, `beta-2`) behind the same Velocity proxy and Redis server.

**How to isolate them cleanly:**
- In `sleep.yml` on `alpha-1` and `alpha-2`, set:
  ```yaml
  worlds:
    world:
      enabled: true
      shardGroupId: "alpha-overworld"
      policy: "curve"
      cancelPolicy: "tug of war"
  ```
- In `sleep.yml` on `beta-1` and `beta-2`, set:
  ```yaml
  worlds:
    world:
      enabled: true
      shardGroupId: "beta-overworld"
      policy: "curve"
      cancelPolicy: "veto"
  ```
- `ShardSleepCoordinator` keys all quorum math, warmup timers, and `EPOCH_COMMIT` watermarks by `shardGroupId`, so `alpha-overworld` and `beta-overworld` never interfere with each other even on the same `leafsleep:sync` channel.

---

### 9. Asymmetric Survival + Resource / Mining Shards

**Scenario:** Players build homes with beds on `survival-1` and `survival-2`, while `resource-1` is a resetting mining world where players spend all night in deep caves (`Y < 0`) without beds.

You have two clean architectural choices:
1. **Independent Resource Clock (Most Common):**
   - Set `shardGroupId: "resource-overworld"` and `policy: "one player"` in `resource-1`'s `sleep.yml`. Miners on `resource-1` do not inflate `eligibleCount` for `survival-1`/`survival-2`, and any 1 player on the surface of `resource-1` can skip its night.
2. **Synchronized Dawn + Chat-Button Voting (`/sleep sleep`):**
   - Put `resource-1` in `shardGroupId: "overworld-main"` so all shards share the same day/night cycle.
   - Grant `leafsleep.sleep: true` to default players in LuckPerms so cave miners on `resource-1` can click `[Sleep]` in chat when a surface player on `survival-1` gets into bed, **or** let cave miners toggle `/sleep ignore` (`leafsleep.ignore: true`) so they are excluded from `eligibleCount` (`ignoredCount`).
   - Because `resetPhantomStatistic: true` runs across all shards in `overworld-main` on `EPOCH_COMMIT`, miners on `resource-1` still have their `TIME_SINCE_REST` reset to `0` so Phantoms do not spawn when they return to the surface!

---

### 10. Vanished Moderators, AFK Mob Farms, and Alt Accounts

- **Vanished Staff (`excludeAfkAndVanished: true`):**
  - Keep `excludeAfkAndVanished: true` in `config.yml`. Players with `vanished` or `ess.vanished` metadata/permissions are automatically excluded from `eligibleCount`. This prevents players from deducing that a vanished moderator is online in their world by watching `eligible=[eligible]/[maxPlayers]` in `/sleep status`.
- **AFK Mob-Grinder Accounts (`/sleep ignore` + `excludeAfkAndVanished: true`):**
  - Overnight AFK accounts standing at mob farms are excluded automatically once flagged AFK (`excludeAfkAndVanished: true`), or can run `/sleep ignore` once to persist their opt-out in SQL (`leafsleep_player_prefs`) across server restarts.
- **Operator & LuckPerms Wildcard Trap (`leafsleep.bypass`):**
  - `leafsleep.bypass` and `oneplayersleep.bypass` default to `false` in `plugin.yml` / `paper-plugin.yml`, and `BukkitPluginServerAccessor` checks `player.isPermissionSet(node) && player.hasPermission(node)`.
  - Therefore, a normal `/op` player **is** included in `eligibleCount` and can test sleep normally. However, if you grant wildcard `leafsleep.*` or `*` in LuckPerms, LuckPerms explicitly sets `leafsleep.bypass: true`! Always negate `leafsleep.bypass: false` and `oneplayersleep.bypass: false` on any group using wildcards.

---

### 11. Weather-Sensitive Farms (Rain / Thunder / Daytime Storms)

- **Daytime Thunderstorm Sleeping (`allowSleepDuringThunder: true`):**
  - Matches vanilla mechanics: players can enter a bed or run `/sleep sleep` during a thunderstorm even at noon (`dayTime < 12542`). Set `allowSleepDuringThunder: false` if your server uses custom storm events that should not be skipped during daytime.
- **Preserving Rain for Fishing / Riptide / Cauldron Farms (`clearWeatherOnSkip: false`):**
  - If players complain that night skips ruin rain-dependent farms, set `clearWeatherOnSkip: false` while keeping `clearThunderOnSkip: true` in `config.yml`. Night skips will clear lightning storms without stopping normal rain.

---

### 12. Co-Deploying `LeafRTP` and `LeafSleep` on the Same Network Stack

`LeafRTP` and `LeafSleep` are designed to share the same `Redis` instance and the same `MySQL` / `MariaDB` / `PostgreSQL` database with zero collisions:

- **Redis isolation:** `LeafRTP` uses `rtp:*` keys and channels, while `LeafSleep` uses `sharding.channel: "leafsleep:sync"`.
- **SQL isolation:** Both plugins share `:database-api`'s `MiniConnectionPool` (`CloseableDataSource` + `SqlDialect`). `LeafRTP` uses `rtp_*` tables while `LeafSleep` uses `storage.tablePrefix: "leafsleep_"` (`leafsleep_player_prefs` and `leafsleep_shard_group_epochs`).
- **Hub / Lobby server pairing:** On a pure dispatch lobby server, set `routing.lobbyMode: true` in `LeafRTP`'s `advanced/network.yml` and set `worlds.world.enabled: false` in `LeafSleep`'s `sleep.yml` (or omit `LeafSleep` on the lobby altogether).
