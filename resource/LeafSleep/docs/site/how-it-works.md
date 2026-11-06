# How LeafSleep Works

`LeafSleep` (`OnePlayerSleep`) is architected to synchronize night skipping across single-threaded servers (`Paper`, `Spigot`, `Fabric`, `NeoForge`), regionised multithreaded servers (`Folia`), and horizontally sharded multi-server clusters (`Velocity` + `Redis` / `SQL` / `UDP`) with sub-millisecond tick impact (`~0.04–0.12 ms` average coordinator tick duration at 20 concurrent players across 4 shards).

---

## 1. Per-World & Cross-Shard Quorum Aggregation (`ShardSleepCoordinator`)

Every enabled world in `sleep.yml` resolves to a `shardGroupId` (defaulting to `"overworld-main"` for `world`).

On each shard:
1. **Local Eligibility Snapshot (`recomputeLocalCounts`):**
   - Queries `ServerAccessor#getWorldPlayers(worldName)` and caches the snapshot across the current event turn so compound actions never scan players twice in one tick.
   - Filters out players who:
     - Are in `CREATIVE` or `SPECTATOR` mode when `excludeCreativeAndSpectator: true` (`config.yml`).
     - Are AFK or vanished (`vanished`, `ess.vanished`) when `excludeAfkAndVanished: true` (`config.yml`).
     - Explicitly hold `leafsleep.bypass` or `oneplayersleep.bypass` (`isPermissionSet(node) && hasPermission(node)`, so bare `/op` does not accidentally exclude operators).
     - Have opted out via `/sleep ignore` (or hold `leafsleep.ignore` when configured as an opt-out profile).
   - Counts a player as **sleeping** if they are physically in a bed (`PlayerSleepSnapshot#sleeping()` and not in `recentlyLeftBedPlayers`) **or** have voted via `/sleep sleep` (`localSleepVoters`), and not in `localCancelVoters`.
   - Counts a player as **cancelling** if they have voted via `/sleep cancel` (`localCancelVoters`).
2. **Event-Driven Dirty Flag + 1 Hz Idle Reconciliation:**
   - Rather than polling `getWorldPlayers` 20 times per second during daytime, `ShardSleepCoordinator` marks `localCountsDirty = true` on bed enter/leave, `/sleep sleep`, `/sleep cancel`, `/sleep ignore`, player join/quit, and incoming remote frames, and runs a background reconciliation once every `20` ticks (`1 Hz`).
3. **Cross-Shard Aggregation (`evaluateGroupQuorum`):**
   - Sums `(eligibleCount, sleepingCount, cancellingCount, ignoredCount, maxPlayers)` across the local shard and all non-stale peer shards (`updatedAtMs` within `3 * heartbeatIntervalTicks`) in the same `shardGroupId`.

---

## 2. Population-Adaptive `UnitCurve` & `TUG_OF_WAR` Math (`QuorumPolicy`)

When `policy: "curve"` is active (the default in `config.yml` and `sleep.yml`):

1. **Normalized Population Load ($t$):**
   $$t = \operatorname{clamp}_{[0,1]}\!\left(\frac{\text{eligibleCount} - 1}{\max(\text{maxPlayers}, \text{eligibleCount}) - 1}\right)$$
   Across a 4-shard cluster where each shard has `max-players=20`, aggregated `maxPlayers` is `80`. With `20` eligible players online across the 4 shards, $t = (20 - 1)/(80 - 1) = 19/79 \approx 0.2405$.
2. **Curve Shaping ($f(t) \in [0, 1]$):**
   - `logarithmic` (`parameter` $k > 1$, default `10.0`): $f(t) = \frac{\ln(1 + (k - 1)t)}{\ln k}$. Rises quickly at small group sizes and flattens early.
   - `smootherstep`: $f(t) = 6t^5 - 15t^4 + 10t^3$. Flat derivatives at $t=0$ and $t=1$; stays low through quiet hours and ramps smoothly in the midrange.
   - `power` (`parameter` $p$, default `0.5` when `type: "power"`): $f(t) = t^p$. Concave root curve for $p < 1$, convex delayed curve for $p > 1$.
   - `sigmoid` (`parameter` steepness $s$, `midpoint` $m \in [0,1]$): Logistic S-curve normalized to $[0, 1]$ with inflection at $t = m$.
   - `linear`: $f(t) = t$.
3. **Interpolation & Low-Population Guard:**
   $$\text{raw} = \operatorname{round}\!\big(\text{minRequired} + f(t)\cdot(\text{maxRequired} - \text{minRequired})\big)$$
   When `minRequired == 1` and `eligibleCount < maxPlayers`, a low-population guard caps the requirement at $\max(1, \lfloor\text{eligibleCount} / 2\rfloor)$ so 1–3 players online at night only ever need `1` sleeper.
4. **`TUG_OF_WAR` Cancel Offset:**
   - Under `cancelPolicy: "tug of war"`, each active `/sleep cancel` vote adds `+1` to the required sleepers:
     $$\text{effectiveRequired} = \min\!\big(\text{eligibleCount},\; \text{baseSleepRequired} + \text{cancellingCount}\big)$$
   - Simultaneously, `cancelCurve` computes `cancelRequired`. If `cancellingCount >= cancelRequired`, the sleep countdown is aborted outright, all local/remote sleep and cancel votes are cleared, `groupLastResetAtMs` is stamped to clamp any in-flight pre-reset heartbeats, and `cancelBroadcast` is sent listing all `cancellingPlayers`.

---

## 3. Idempotent `targetDayEpoch` Commit & Folia Regional Safety

Once `sleepingCount >= effectiveRequired`, `groupWarmupRemaining` counts down from `warmupDelayTicks` (default `60` ticks). If quorum holds until the counter reaches `0`:

1. **Monotonic Epoch High-Water Mark:**
   - Computes `targetDayEpoch = (currentFullTime / 24000L) + 1L` and `targetDayTimeTicks = targetDayEpoch * 24000L + targetDawnTimeTicks`.
   - Atomically updates `lastCommittedEpochs` (`watermark.compareAndSet(prev, targetDayEpoch)`) and broadcasts an `EPOCH_COMMIT` frame to peer shards.
   - If two shards reach quorum on the exact same tick, the second `EPOCH_COMMIT` for the same `targetDayEpoch` is rejected as a duplicate (`duplicateEpochsRejected`), guaranteeing **zero double-skips**.
2. **Folia-Safe World & Entity Execution:**
   - World time (`setFullTime`) and weather (`clearWeatherOnSkip`, `clearThunderOnSkip`) are mutated on the global region thread.
   - Player phantom rest statistics (`resetPhantomStatistic: true` -> `TIME_SINCE_REST = 0`) and `wakeupEffects` (`SOUND`, `PARTICLE`, `POTION` via `effects-api`) are scheduled onto each player's owning entity scheduler thread.
   - The committed epoch watermark is persisted asynchronously to SQL (`leafsleep_shard_group_epochs`) via `:database-api`.
