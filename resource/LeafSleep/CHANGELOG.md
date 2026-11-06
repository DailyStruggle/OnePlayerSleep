# Changelog

## [1.0.0] - 2026-10-27

- Initial multi-loader release of `LeafSleep` (`Paper`, `Spigot`, `Folia`, `Fabric`, `NeoForge`, `Velocity`).
- Implemented `ShardSleepCoordinator` with `ONE_PLAYER`, `PERCENTAGE`, `FIXED_COUNT`, and `ABSOLUTE_MAJORITY` quorum policies, warmup countdown, cross-shard `/sleep cancel`, and idempotent `targetDayEpoch` CAS dawn transitions (`ADR-001`).
- Implemented `SleepSyncTransport` (Direct TCP/UDP HMAC-signed peer gossip, `leafsleep:sync` plugin messaging, Redis pub/sub, and local-only modes) guarded by `BudgetPacer`.
- Implemented `SleepDatabaseStore` asynchronous SQL persistence (`SQLite`, `MySQL`, `MariaDB`, `PostgreSQL`) for player sleep preferences, cooldowns, counters, and shard group epoch watermarks.
- Added `/sleep` (`/ops`) command tree (`status`, `sleep`, `cancel`, `ignore`, `reload`, `test`) and `[OPS bit]` PBIT/CBIT/IBIT self-tests (`ADR-002`).
