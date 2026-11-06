# LeafSleep

Cross-server, cross-platform sharded sequel to `OnePlayerSleep` for Paper, Spigot, Folia, Fabric, NeoForge, and Velocity.

## Features

- **Sharded Sleep State Machine (`ADR-001`):** Aggregates `(eligibleCount, sleepingCount, cancellingCount, ignoredCount, maxPlayers)` across regional threads (Folia) and multi-server shard groups (`ONE_PLAYER`, `PERCENTAGE`, `FIXED_COUNT`, `ABSOLUTE_MAJORITY`, `CURVE`) with population-adaptive `UnitCurve` shapes (`logarithmic`, `smootherstep`, `power`, `sigmoid`, `linear`), a configurable warmup countdown, and cross-shard `/sleep cancel` (`TUG_OF_WAR`, `CURVE`, `VETO`).
- **Idempotent `targetDayEpoch` CAS:** Advances dawn (`targetDayEpoch = currentFullTime / 24000L + 1`) and clears weather once per day epoch across all shards, resetting `TIME_SINCE_REST` on each player's owning entity thread so Phantoms do not spawn after a group skip.
- **Hybrid Direct Bus + Pooled Async SQL Store:** Sub-50ms delta frames (`BED_ENTER`, `BED_LEAVE`, `SLEEP_VOTE`, `CANCEL_SLEEP`, `EPOCH_COMMIT`, `SHARD_HEARTBEAT`) over Redis RESP2 pub/sub (`REDIS_PUBSUB`), Velocity plugin messaging (`PLUGIN_MESSAGING`), HMAC-signed direct UDP sockets (`DIRECT_SOCKET` with `±30s` replay protection), or `LOCAL_ONLY` mode guarded by `BudgetPacer`, paired with `:database-api`'s `MiniConnectionPool` (`SQLITE`, `MYSQL`, `MARIADB`, `POSTGRESQL`).
- **`OnePlayerSleep` Configuration Superset (`ADR-002`):** Self-documenting `config.yml`, `player.yml`, `commands.yml`, `sleep.yml`, and `messages/*.yml` (loaded via `MultiConfigParser<SleepMessageKeys>`) with `@type`, `@range`, `@options`, and `@default` schema directives, weighted random sleep/wakeup messages, clickable `[Cancel]` / `[Sleep]` prompts, and `effects-api` wakeup buffs.
- **Built-In Test (`[OPS bit]`):** `/sleep test` (or `/ops test`) executes PBIT/CBIT/IBIT diagnostics (`OPS_001`..`OPS_005`) indexed by `discord-support-bot`.

## Documentation

- [Intended Usage](resource/LeafSleep/docs/site/intended-usage.md)
- [Server & Network Design Guide](resource/LeafSleep/docs/admin/SERVER_DESIGNS.md)
- [Configuration Reference](resource/LeafSleep/docs/admin/CONFIGURATION.md)
- [Commands & Permissions](resource/LeafSleep/docs/admin/COMMANDS.md)
- [What NOT to Do!](resource/LeafSleep/docs/site/what-not-to-do.md)
- [How It Works](resource/LeafSleep/docs/site/how-it-works.md)
- [FAQ](resource/LeafSleep/docs/admin/FAQ.md)
- [BIT Code Catalog (`[OPS bit]`)](resource/LeafSleep/docs/dev/BIT_CODES.md)
