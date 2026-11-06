# Intended usage

`LeafSleep` (`OnePlayerSleep`) is built around one core model: **every world belongs to a `shardGroupId` that aggregates eligible, sleeping, and cancelling players across local regions (Folia) or networked backend servers, and commits dawn once per day epoch using a population-adaptive `sleepCurve` and `cancelCurve`.**

## Intended usage

1. **Install** the jar on your server (`Paper`, `Spigot`, `Purpur`, `Folia`, `Fabric`, or `NeoForge`, plus `Velocity` if using `sharding.transport: "plugin messaging"`) and boot once to generate `config.yml`, `sleep.yml`, and `messages.yml`.
2. **Choose your server design** (see the complete [Server & Network Design Guide](../admin/SERVER_DESIGNS.md)):
   - **Single survival server (Paper / Folia / Fabric / NeoForge):** Keep `sharding.enabled: false`, `sharding.transport: "local only"`, and `storage.type: "SQLITE"`.
   - **24/7 global server with variable population:** Use `defaultQuorumPolicy: "curve"` and `defaultCancelPolicy: "tug of war"` in `config.yml`. Pick a `sleepCurve.type` (`logarithmic`, `smootherstep`, `power`, `sigmoid`, or `linear`) so 1–3 late-night players only need `1` sleeper (`minRequired: 1`) while peak hours smoothly scale up to `maxRequired: 8` without requiring half the server to sleep.
   - **Multi-world single server (Multiverse / creative + survival + resource):** Configure each world under `worlds:` in `sleep.yml`. Set `enabled: false` on lobbies, creative worlds, arenas, Nether, and End; assign distinct `shardGroupId` values to worlds that should have independent day/night cycles.
   - **Horizontally sharded survival cluster (2 to 100+ Paper/Folia backends):** Give each backend a unique `serverShardId` (`shard-1`, `shard-2`, ...) in `config.yml`, map their shared overworlds to the same `shardGroupId: "overworld-main"` in `sleep.yml`, and enable `sharding.enabled: true` with `sharding.transport: "redis pubsub"` (recommended), `"plugin messaging"`, or `"direct socket"`.
3. **Verify health** with `/sleep test` (`[OPS bit] Health=HEALTHY | Components=5 | Findings=0`) and `/sleep status`.

---

## How locations are prepared and verified

Unlike teleportation plugins that load chunks, `LeafSleep` never performs chunk I/O on the tick thread (`S-005`). Instead, `ShardSleepCoordinator` tracks player sleep eligibility in memory:

1. **Event-Driven Dirty Invalidations + 1 Hz Idle Reconciliation:** Entering or leaving a bed (`BED_ENTER` / `BED_LEAVE`), running `/sleep sleep`, `/sleep cancel`, or `/sleep ignore`, or joining/leaving the server immediately marks local counts dirty. When idle during daytime, `ShardSleepCoordinator` reconciles world time and eligibility at a lightweight 1 Hz (`20-tick`) cadence rather than scanning player lists 20 times per second.
2. **Population-Normalized `UnitCurve` Quorum:** For any `shardGroupId`, normalized population load is $t = \frac{\text{eligible} - 1}{\max(\text{maxPlayers}, \text{eligible}) - 1} \in [0, 1]$. `sleepCurve` and `cancelCurve` map $t$ onto `[minRequired, maxRequired]` with a small-population guard (`max(1, eligibleCount / 2)` when `minRequired: 1`), so a nearly empty server never gets stuck at night.
3. **Idempotent `targetDayEpoch` CAS & Phantom Reset:** When active sleepers stay at or above `effectiveRequired` for `warmupDelayTicks` (default `60` ticks = `3s`), `ShardSleepCoordinator` atomically claims `targetDayEpoch = currentFullTime / 24000L + 1`, advances world time to `targetDawnTimeTicks` (`0`), clears weather if `clearWeatherOnSkip: true`, resets `TIME_SINCE_REST` on each eligible player's owning entity thread (`resetPhantomStatistic: true`), and dispatches `wakeupEffects` from `sleep.yml`.

---

## Teleport parameters are for testing

In `LeafSleep`, interactive chat buttons (`[Sleep]` -> `/sleep sleep` and `[Cancel]` -> `/sleep cancel`) and `/sleep status` are the everyday player workflow:

| Command | Purpose | Default Permission |
|---|---|---|
| `/sleep status` (or `/ops status`) | Shows per-world `shardGroupId`, active curve policy (`curve:<type>`), `sleeping/required`, `cancelling/cancelRequired`, `eligible/maxPlayers`, `epoch`, and live `TPS` / `MSPT` / `leafsleepAvg` telemetry | `leafsleep.status` (`true`) |
| `/sleep sleep [messageId]` | Votes to skip the night (or selects a specific weighted message entry from `sleep.yml`) | `leafsleep.sleep` (`true`) |
| `/sleep cancel` | Casts a counter-vote against an active sleep warmup (`tug of war`, `curve`, or `veto`) | `leafsleep.cancel` (`true`) |
| `/sleep ignore` | Toggles personal opt-out from `eligibleCount` (persisted asynchronously in `storage`) | `leafsleep.ignore` (`true`) |
| `/sleep reload` | Validates `config.yml`, `sleep.yml`, and `messages.yml` directives and hot-reloads | `leafsleep.reload` (`op`) |
| `/sleep test` | Runs all 5 `[OPS bit]` PBIT, CBIT, and IBIT self-tests | `leafsleep.test` (`op`) |

---

## Where to go next

- [Server & Network Design Guide](../admin/SERVER_DESIGNS.md) - blueprints for every obvious and non-obvious server topology.
- [Configuration Reference](../admin/CONFIGURATION.md) - every key in `config.yml`, `sleep.yml`, and `messages.yml`.
- [Commands & Permissions](../admin/COMMANDS.md) - subcommands, permissions, and `[OPS bit]` diagnostics.
- [What NOT to do!](what-not-to-do.md) - common misconfigurations that break night skipping or cause network desync.
- [How it works](how-it-works.md) - sharded state machine, curve math, and hybrid transport internals.
