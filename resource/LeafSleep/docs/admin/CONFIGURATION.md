# Configuration Reference (`LeafSleep` / `OnePlayerSleep`)

Configuration files are generated on first server startup under `plugins/LeafSleep/` on `Paper`, `Spigot`, `Purpur`, and `Folia` (or `config/leafsleep/` on `Fabric` and `NeoForge`).

---

## How to Update Configuration

1. **Edit YAML files directly (`config.yml`, `sleep.yml`, `player.yml`, `commands.yml`, `messages/*.yml`)**: Every key includes `# @type`, `# @range`, `# @options`, and `# @default` schema directives validated at load time.
2. **Hot-reload in-game or from console (`/sleep reload` or `/ops reload`)**: Validates all YAML files and `messages/*.yml` theme files against their directives (`OPS_001` / `OPS_CONFIG_SCHEMA`) before swapping the active configuration atomically, preserving committed day-epoch watermarks across reloads.
3. **Verify runtime and cluster health (`/sleep test` and `/sleep status`)**: Run `/sleep test` to execute all 5 `[OPS bit]` PBIT, CBIT, and IBIT checks, and `/sleep status` to inspect active curves, shard counts, and `TPS` / `MSPT` / `leafsleepAvg` tick telemetry.

---

## File Overview

| File | Purpose | Key Sections |
|---|---|---|
| `config.yml` | Core shard identity, default quorum/cancel policies, `sleepCurve` / `cancelCurve` parameters, warmup/cooldowns, weather/Phantom rules, and fallback `sharding`/`storage` defaults | `serverShardId`, `defaultQuorumPolicy`, `defaultCancelPolicy`, `sleepCurve`, `cancelCurve`, `sharding`, `storage` |
| `network.yml` | Additive cross-server network companion to `OnePlayerSleep`: shard identity (`serverShardId`), `sharding` transport (`redis pubsub`, `plugin messaging`, `direct socket`), and shared `storage` SQL pool settings | `serverShardId`, `sharding`, `storage` |
| `sleep.yml` | Per-world enablement, `shardGroupId` mappings, per-world policy/curve overrides, and `wakeupEffects` (`effects-api`) | `wakeupEffects`, `worlds` |
| `messages/*.yml` | Weighted random sleep and wakeup message themes (`default.yml`, `cozy.yml`, `starlight.yml`, `campfire.yml`, `phantom.yml`, `nightmare.yml`, `rooster.yml`, `lazy.yml`) loaded via `MultiConfigParser<SleepMessageKeys>` | `weight`, `sleepText`, `wakeupText`, `cancelPrompt`, `sleepPrompt` |
| `player.yml` | Player-facing sleep flow, cooldown, warmup ActionBar/BossBar, and interactive chat prompt strings (`S-007`) | `prefix`, `sleepBroadcast`, `wakeupBroadcast`, `cancelBroadcast`, `cancelVoteBroadcast`, `warmupActionBar`, `warmupBossBar`, `messagePrefCurrent`, `messagePrefUpdated` |
| `commands.yml` | Command feedback, permission/parameter errors, and `/sleep status` / `/sleep reload` strings (`S-007`) | `reloadSuccess`, `reloadFailed`, `noPermission`, `invalidCommand`, `badParameter`, `busy`, `statusHeader`, `statusWorldLine`, `statusTransportLine` |

---

## `config.yml` — Core, Curve, Sharding & Storage Settings

### Top-Level Keys

| Key | Type | Default | Description |
|---|---|---|---|
| `version` | String | `"1.0"` | Configuration schema version. |
| `serverShardId` | String | `"shard-1"` | Unique identifier for this backend shard inside a multi-server cluster. Every shard in a cluster **must** have a distinct `serverShardId`. |
| `defaultQuorumPolicy` | Enum | `"curve"` | Default quorum policy for worlds without a `policy` override in `sleep.yml`. Options: `"one player"`, `"percentage"`, `"fixed count"`, `"absolute majority"`, `"curve"`. |
| `defaultQuorumValue` | Integer | `25` | Default quorum value (`1..100`): percentage when `defaultQuorumPolicy: "percentage"`, or player count when `"fixed count"`. |
| `defaultCancelPolicy` | Enum | `"tug of war"` | How `/sleep cancel` interacts with an active sleep proposal. Options: `"tug of war"` (each cancel offsets 1 sleeper and reaching `cancelCurve` aborts), `"curve"` (reaching `cancelCurve` aborts without +1 sleeper offset), `"veto"` (1 cancel aborts immediately). |
| `warmupDelayTicks` | Integer | `60` | Warmup countdown in ticks (`0..1200`, `20` ticks = `1s`) after quorum is met before dawn is committed. Keep `>= 60` (`3s`) so players have time to click `[Cancel]`. |
| `warmupActionBar` | Boolean | `true` | Displays a live per-second actionbar countdown (`player.yml::warmupActionBar`) during `warmupDelayTicks`. |
| `warmupBossBar` | Boolean | `true` | Displays a live draining bossbar progress bar (`player.yml::warmupBossBar`) during `warmupDelayTicks` and clears it automatically on dawn or cancel. |
| `increment` | Integer | `150` | Amount of world time in ticks (`0..24000`) to advance per increment step after `warmupDelayTicks` completes (`0` = instant snap to dawn; `150` = smooth `OnePlayerSleep` sky transition). |
| `timeBetweenIncrements` | Integer | `2` | Number of server ticks (`1..100`) between smooth time-advance increments when `increment > 0`. |
| `sleepCooldownSeconds` | Integer | `10` | Per-player cooldown in seconds (`0..3600`) between `/sleep sleep` votes or bed-enter broadcasts. |
| `cancelCooldownSeconds` | Integer | `30` | Per-player cooldown in seconds (`0..3600`) between `/sleep cancel` actions. Note: a player cancelling their own active sleep vote is never blocked by their sleep cooldown. |
| `wakeOnCancel` | Boolean | `true` | Whether aborting a night skip via `/sleep cancel` wakes currently sleeping players across the shard group. |
| `clearWeatherOnSkip` | Boolean | `true` | Whether to clear rain when a night skip commits. Set to `false` if players rely on rain for fishing or trident farms. |
| `clearThunderOnSkip` | Boolean | `true` | Whether to clear thunderstorms when a night skip commits. |
| `resetPhantomStatistic` | Boolean | `true` | Whether to reset `Statistic.TIME_SINCE_REST` to `0` for all eligible online players in the world across all shards on dawn commit so Phantoms do not spawn. |
| `excludeAfkAndVanished` | Boolean | `true` | Excludes AFK and vanished (`vanished`, `ess.vanished`) players from `eligibleCount`. |
| `excludeCreativeAndSpectator` | Boolean | `true` | Excludes `CREATIVE` and `SPECTATOR` mode players from `eligibleCount`. |
| `targetDawnTimeTicks` | Integer | `0` | Target time-of-day in ticks (`0..23999`) when advancing the world clock to morning (`0` = 6:00 AM sunrise). |
| `nightStartTicks` | Integer | `12542` | Start of the sleepable night window in world ticks (`0..23999`). |
| `nightEndTicks` | Integer | `23459` | End of the sleepable night window in world ticks (`0..23999`). |
| `allowSleepDuringThunder` | Boolean | `true` | Allows players to sleep during thunderstorms even during daytime (`dayTime < nightStartTicks`). |
| `budgetMaxFramesPerTick` | Integer | `32` | Maximum outbound sync frames per tick (`1..1024`) admitted by `BudgetPacer`. Critical `EPOCH_COMMIT` and `CANCEL_SLEEP` frames are always admitted. |

### `sleepCurve` and `cancelCurve` Sections

Both `sleepCurve` and `cancelCurve` scale over normalized population load $t = (\text{eligible} - 1) / (\text{maxPlayers} - 1) \in [0, 1]$ using `:predict-api`'s `UnitCurve`:

| Key | Type | Default (`sleepCurve` / `cancelCurve`) | Description |
|---|---|---|---|
| `type` | Enum | `"logarithmic"` | Shaping function: `"logarithmic"`, `"power"`, `"smootherstep"`, `"sigmoid"`, or `"linear"`. |
| `minRequired` | Integer | `1` / `1` | Minimum sleepers or cancel votes required at low population ($t = 0.0$, range `1..100`). |
| `maxRequired` | Integer | `8` / `5` | Maximum sleepers or cancel votes required when eligible players reach `maxPlayers` ($t = 1.0$, range `1..500`). |
| `parameter` | Number | `10.0` (or `0.5` when `type: "power"`) | Curve parameter (`0.05..100.0`): logarithmic base ($> 1.0$), power exponent ($0.5 = \sqrt{t}$), or sigmoid steepness. |
| `midpoint` | Number | `0.35` | Inflection midpoint in `[0.0, 1.0]` used when `type: "sigmoid"`. |

### `sharding` Section (`config.yml`)

| Key | Type | Default | Description |
|---|---|---|---|
| `sharding.enabled` | Boolean | `false` | Master toggle for cross-server shard synchronization. |
| `sharding.transport` | Enum | `"local only"` | Fast transient transport mode: `"local only"`, `"redis pubsub"`, `"plugin messaging"`, or `"direct socket"`. |
| `sharding.channel` | String | `"leafsleep:sync"` | Redis pub/sub channel or Velocity plugin-messaging channel identifier. |
| `sharding.bindHost` | String | `"127.0.0.1"` | Local bind host for `DIRECT_SOCKET`, or Redis hostname when `transport: "redis pubsub"` (overridden by `LEAFSLEEP_REDIS_HOST`). |
| `sharding.bindPort` | Integer | `25620` | Local UDP port for `DIRECT_SOCKET` (`1024..65535`), or Redis port when `transport: "redis pubsub"` (defaults to `6379` if left at `25620`, or overridden by `LEAFSLEEP_REDIS_PORT`). |
| `sharding.peers` | List\<String\> | `[]` | Peer `host:port` endpoints for `DIRECT_SOCKET` UDP gossip. |
| `sharding.hmacSecretEnv` | String | `"LEAFSLEEP_HMAC_SECRET"` | Environment variable supplying the shared HMAC-SHA256 signing key for `DIRECT_SOCKET` and `REDIS_PUBSUB` frames (`±30s` replay window). |
| `sharding.heartbeatIntervalTicks` | Integer | `100` | Heartbeat broadcast period in ticks (`20..6000`, `100` ticks = `5s`) across shards. Peer counts expire after `3 * heartbeatIntervalTicks`. |

### `storage` Section (`config.yml`)

Backed by `:database-api`'s `MiniConnectionPool` (`CloseableDataSource`) and `SqlDialect`:

| Key | Type | Default | Description |
|---|---|---|---|
| `storage.type` | Enum | `"SQLITE"` | SQL dialect: `"SQLITE"`, `"MYSQL"`, `"MARIADB"`, or `"POSTGRESQL"`. |
| `storage.sqliteFile` | String | `"leafsleep.db"` | Relative SQLite file path inside the plugin folder (`WAL` mode + `busy_timeout = 5000ms` enabled automatically). |
| `storage.host` | String | `"127.0.0.1"` | Remote SQL database hostname (`MYSQL`, `MARIADB`, `POSTGRESQL`). |
| `storage.port` | Integer | `3306` | Remote SQL database port (`1..65535`, `3306` for MySQL/MariaDB, `5432` for PostgreSQL). |
| `storage.database` | String | `"leafsleep"` | Remote SQL database name. |
| `storage.username` | String | `"root"` | Remote SQL username. |
| `storage.passwordEnv` | String | `"LEAFSLEEP_DB_PASSWORD"` | Environment variable supplying the remote SQL password. |
| `storage.tablePrefix` | String | `"leafsleep_"` | Table prefix for `leafsleep_player_prefs` and `leafsleep_shard_group_epochs`. |

---

## `sleep.yml` — Per-World Sharding & Wakeup Effects

| Key | Type | Default | Description |
|---|---|---|---|
| `wakeupEffects` | List\<String\> | `["SOUND ENTITY_PLAYER_LEVELUP 1.0 1.0", "PARTICLE VILLAGER_HAPPY 10", "POTION REGENERATION 100 1"]` | `effects-api` wakeup buffs, sounds, and particles dispatched to eligible waking players when dawn commits. |
| `worlds.<world>.enabled` | Boolean | `true` (`false` on Nether/End) | Enables or disables `LeafSleep` management in `<world>`. Set to `false` for Nether, End, lobbies, and creative worlds. |
| `worlds.<world>.shardGroupId` | String | `"overworld-main"` | Logical shard group identifier. All worlds/shards sharing a `shardGroupId` aggregate quorum and skip to dawn together. |
| `worlds.<world>.policy` | Enum | `"curve"` | Per-world quorum policy override (`"one player"`, `"percentage"`, `"fixed count"`, `"absolute majority"`, `"curve"`). |
| `worlds.<world>.cancelPolicy` | Enum | `"tug of war"` | Per-world cancel policy override (`"tug of war"`, `"curve"`, `"veto"`). |
| `worlds.<world>.quorumValue` | Integer | `1` | Per-world percentage or fixed count override. |
| `worlds.<world>.warmupDelayTicks` | Integer | `60` | Per-world warmup countdown in ticks before dawn commits. |
| `worlds.<world>.warmupActionBar` | Boolean | `true` | Per-world actionbar countdown toggle during `warmupDelayTicks` (inherits `config.yml` `warmupActionBar` when omitted). |
| `worlds.<world>.warmupBossBar` | Boolean | `true` | Per-world bossbar progress countdown toggle during `warmupDelayTicks` (inherits `config.yml` `warmupBossBar` when omitted). |
| `worlds.<world>.increment` | Integer | `150` | Per-world smooth time-advance increment in ticks per step (`0` = instant skip; inherits `config.yml` `increment` when omitted). |
| `worlds.<world>.timeBetweenIncrements` | Integer | `2` | Per-world server ticks between smooth time-advance increments (inherits `config.yml` `timeBetweenIncrements` when omitted). |
| `worlds.<world>.sleepCurve` | Map | (inherits `config.yml`) | Optional per-world `sleepCurve` override (`type`, `minRequired`, `maxRequired`, `parameter`, `midpoint`). |
| `worlds.<world>.cancelCurve` | Map | (inherits `config.yml`) | Optional per-world `cancelCurve` override (`type`, `minRequired`, `maxRequired`, `parameter`, `midpoint`). |

---

## `messages/*.yml` — Weighted Sleep Message Themes (`MultiConfigParser`)

Each `.yml` file inside `messages/` (seeded with `default.yml`, `cozy.yml`, `starlight.yml`, `campfire.yml`, `phantom.yml`, `nightmare.yml`, `rooster.yml`, and `lazy.yml`) defines a selectable sleep/wakeup message theme loaded via `MultiConfigParser<SleepMessageKeys>`:

| Key | Type | Default (`default.yml`) | Description |
|---|---|---|---|
| `weight` | Integer | `40` | Relative selection weight (`1..1000`) for weighted random sleep/wakeup message sets. |
| `sleepText` | String | `"[player] is sleeping ..."` | Chat broadcast template when a player sleeps or votes using this message entry. |
| `wakeupText` | String | `"Rise and shine, [world]! ..."` | Dawn broadcast template when the night skip commits. |
| `cancelPrompt` | String | `"[Cancel]"` | Clickable `/sleep cancel` button label for this message entry. |
| `sleepPrompt` | String | `"[Sleep]"` | Clickable `/sleep sleep` button label for this message entry. |
