# What NOT to do!

This page catalogs the misconfigurations that either break `LeafSleep` (`OnePlayerSleep`) outright or degrade multi-server synchronization and player experience.

- **[This will break the mod entirely](#this-will-break-the-mod-entirely)** - mistakes that stop night skipping, break `/sleep cancel`, or corrupt cross-shard state.
- **[Why is this running slowly?](#why-is-this-running-slowly)** - mistakes that create database bottlenecks, network packet drops, or tick spikes.

---

## This will break the mod entirely

!!! warning "Don't leave identical `serverShardId: \"shard-1\"` on multiple servers in a sharded cluster"
    When `sharding.enabled: true`, `ShardSleepCoordinator` indexes per-shard player counts by `serverShardId` (`groupShardCounts`). If two backend servers both use `serverShardId: "shard-1"` in `config.yml`, they overwrite each other's `eligibleCount`, `sleepingCount`, and `maxPlayers` on every heartbeat and vote frame. Give every backend server a unique `serverShardId` (`"survival-a"`, `"survival-b"`, `"survival-c"`, `"survival-d"`).

!!! warning "Don't share the same `shardGroupId` across unrelated worlds that should have separate day/night cycles"
    In `sleep.yml`, every world mapped to the same `shardGroupId` (such as `"overworld-main"`) shares one combined voter pool and skips to dawn simultaneously when `EPOCH_COMMIT` fires. If you host a main survival world, a creative plot world, and a hardcore event world on the same server (or on the same Redis / Velocity network), give them distinct `shardGroupId` values (`"survival-main"`, `"hardcore-main"`) and set `enabled: false` on creative/lobby/minigame worlds.

!!! warning "Don't grant wildcard `leafsleep.*` or `oneplayersleep.*` to regular players without negating `.bypass`"
    Granting `leafsleep.bypass: true` (or `oneplayersleep.bypass: true`) via a wildcard permission in LuckPerms marks those players as bypassing sleep eligibility, excluding them from `eligibleCount`. Grant regular players only `leafsleep.status`, `leafsleep.cancel`, `leafsleep.ignore` (all `default: true`), and optionally `leafsleep.sleep` if you want chat-button voting without a physical bed. If you use a wildcard on staff/VIPs, explicitly set `leafsleep.bypass: false` and `oneplayersleep.bypass: false`.

!!! warning "Don't set `warmupDelayTicks: 0` if you expect `/sleep cancel` (`tug of war` / `curve` / `veto`) to work"
    Setting `warmupDelayTicks: 0` in `config.yml` or `sleep.yml` commits dawn on the exact tick quorum is reached. Players who need night for Phantom membranes, villager curing, or mob farms will have `0` milliseconds to click `[Cancel]` in chat. Keep `warmupDelayTicks: 60` to `100` (`3`–`5` seconds) whenever `/sleep cancel` is enabled.

!!! warning "Don't use `defaultQuorumPolicy: \"fixed count\"` with a high `defaultQuorumValue` on a 24/7 server"
    Setting `defaultQuorumPolicy: "fixed count"` with `defaultQuorumValue: 5` means that when only 2 or 3 players are online at 4 AM, it is mathematically impossible to reach 5 sleepers—or even if clamped to `eligibleCount`, 100% of online players (including cave miners without beds) must sleep simultaneously. Use `defaultQuorumPolicy: "curve"` with `sleepCurve.minRequired: 1` so off-peak hours require only `1` sleeper while peak hours scale up smoothly to `sleepCurve.maxRequired`.

!!! warning "Don't use `defaultCancelPolicy: \"veto\"` on a large public survival server"
    Under `defaultCancelPolicy: "veto"`, a single player clicking `[Cancel]` immediately aborts the sleep warmup and wakes every sleeping player across all shards. On a 50+ player server, one troll can lock the server in permanent night. Use `defaultCancelPolicy: "tug of war"` (default), where each cancel click offsets 1 sleeper (`effectiveRequired = sleepRequired + cancellingCount`) and only aborts when `cancelCurve` quorum is reached.

!!! warning "Don't rely on `sharding.transport: \"plugin messaging\"` to advance time on completely empty (`0`-player) shards"
    Minecraft proxy plugin messaging (`leafsleep:sync` over Velocity) requires at least 1 connected player on a backend server for the proxy to transmit plugin message packets to that backend. If a shard has `0` online players when peer shards commit dawn over `plugin messaging`, the empty shard cannot receive the packet until a player joins (or use `sharding.transport: "redis pubsub"`, which delivers `EPOCH_COMMIT` to all shards even when `0` players are online).

!!! warning "Don't run `DIRECT_SOCKET` or signed `REDIS_PUBSUB` across hosts with clocks skewed by more than 30 seconds"
    `SleepSyncFrame.decodeSigned` enforces a strict `±30s` timestamp replay window (`DEFAULT_REPLAY_WINDOW_MS = 30000`) and a bounded `ReplayWindowCache` to block packet replay attacks. If two host machines have system clocks skewed by more than 30 seconds, signed frames are rejected. Keep `chronyd` or `systemd-timesyncd` (NTP) enabled on all nodes.

---

## Why is this running slowly?

!!! warning "Don't point multiple shards at the same `SQLITE` file over a network filesystem (NFS / SMB)"
    `storage.type: "SQLITE"` is designed for single-server local disk (`WAL` mode + `busy_timeout`). Sharing a single `.db` file across multiple containers or VMs over NFS/SMB causes file-lock contention and `SQLITE_BUSY` stalls. For multi-server clusters, either keep `storage.type: "SQLITE"` local to each container's own disk or use `storage.type: "MYSQL"`, `"MARIADB"`, or `"POSTGRESQL"` backed by `:database-api`'s `MiniConnectionPool`.

!!! warning "Don't set `sleepCooldownSeconds: 0` and `cancelCooldownSeconds: 0` on large public servers"
    While `BudgetPacer` caps outbound network frames (`budgetMaxFramesPerTick: 32`) and applies a 25 ms per-player bed-event debounce, setting `sleepCooldownSeconds: 0` and `cancelCooldownSeconds: 0` lets players spam chat broadcasts on every click. Keep `sleepCooldownSeconds: 5`–`10` and `cancelCooldownSeconds: 15`–`30`.

---

## See also

- [Intended usage](intended-usage.md) - the core mental model.
- [Server & Network Design Guide](../admin/SERVER_DESIGNS.md) - complete designs for single-server, Folia, multi-world, and sharded networks.
- [Configuration Reference](../admin/CONFIGURATION.md) - full `config.yml`, `sleep.yml`, and `messages.yml` reference.
