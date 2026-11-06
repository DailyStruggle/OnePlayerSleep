<!--
Single source for every LeafSleep / OnePlayerSleep storefront page (BuiltByBit, Modrinth, Hangar, SpigotMC).
Follows docs/dev/RULES.md Rule D-006 (first-person plain voice, 3-Zone hierarchy,
ASCII punctuation only, concrete numbers and honest caveats).
  Paid BuiltByBit title:  "LeafSleep-Pro"  tagline: "Cross-server & multi-loader sleep voting and night skip (/sleep & /ops) for Paper, Spigot, Folia, Fabric, NeoForge & Velocity"
  Free title:             "LeafSleep"      tagline: "Cross-server & multi-loader sleep voting and night skip (/sleep & /ops) for Paper, Spigot, Folia, Fabric, NeoForge & Velocity"
-->

<div align="center">

# {{name}}: Multi-Loader & Sharded Sleep Voting (`OnePlayerSleep`)

[![Build](https://github.com/DailyStruggle/RTP/actions/workflows/gradle.yml/badge.svg)](https://github.com/DailyStruggle/RTP/actions/workflows/gradle.yml)
[![Java](https://img.shields.io/badge/Java-21%2B-blue)](https://adoptium.net/)
[![Platforms](https://img.shields.io/badge/Platforms-Paper%20%7C%20Spigot%20%7C%20Folia%20%7C%20Fabric%20%7C%20NeoForge%20%7C%20Velocity-orange)](../../README.md)

</div>

## Sleep

**{{name}} is a sleep voting and night-skip plugin and mod, and the cross-platform sequel to `OnePlayerSleep`.** Drop the jar in and one player entering a bed (or a configurable share of online players) starts a short warmup countdown with clickable `[Sleep]` and `[Cancel]` chat buttons before advancing the world to morning, clearing weather, and resetting Phantom rest timers. It runs on single survival servers (`Paper`, `Spigot`, `Folia`, `Fabric`, `NeoForge`) and across multi-server shards (`Velocity`, `Redis`, or UDP).

I built `OnePlayerSleep` so a single player AFK in a mob farm wouldn't keep the whole server in the dark, and rebuilt it as `LeafSleep` on the shared [`DailyStruggle/RTP`](https://github.com/DailyStruggle/RTP) SDK so one jar works across Paper, Folia, Fabric, NeoForge, and multi-server networks while keeping every `/ops` command and permission alias intact.

<!-- only: bbb-pro -->
<!-- kind: promo -->
This listing is how you pay for support. The code is the same as the [free download](https://modrinth.com/plugin/oneplayersleep) and all of it is MIT licensed. Paying doesn't unlock anything in the plugin; details are in [What paying gets you](#what-paying-gets-you) below.
<!-- /kind -->
<!-- /only -->
<!-- only: bbb-lite -->
<!-- kind: promo -->
There is also a paid LeafSleep-Pro listing on BuiltByBit. It is the same code, and nothing in the plugin is locked. Paying gets you priority support and a patch release when a bug you report needs one.
<!-- /kind -->
<!-- /only -->

### The short version

**One player or population-scaled sleep voting with anti-troll `/sleep cancel`, Phantom rest-timer reset, and cross-server shard sync in a single multi-loader jar.**

- [**Easy**](#install): drop the jar in for instant `OnePlayerSleep` behavior (`/sleep` and `/ops` aliases), or tune warmup delays, weighted messages, and wakeup effects in `config.yml` and `sleep.yml`.
- [**Safe**](../site/how-it-works.md): anti-troll `tug of war` cancel voting prevents a single player from locking a busy server in eternal night, and Phantom rest counters reset on each player's own entity thread.
- [**Adaptive**](../admin/SERVER_DESIGNS.md): scales required sleepers smoothly from `1` player during quiet hours up to your configured cap at peak population, while ignoring AFK, vanished, Creative, and Spectator players.
- [**Sharded**](../admin/CONFIGURATION.md): optional cross-server synchronization over Redis pub/sub, Velocity plugin messaging, or HMAC-signed UDP keeps multi-shard survival worlds on the same day cycle.
- [**One jar**](../../README.md): runs the same sleep state machine and `/sleep` (`/ops`) command tree on Paper, Spigot, Folia, Fabric, NeoForge, and Velocity.

<details>
<summary><b>More highlights</b></summary>

- [**Clickable chat prompts**](../admin/COMMANDS.md): broadcasts weighted random sleep messages with clickable `[Cancel]` and `[Sleep]` chat buttons.
- [**Wakeup buffs**](../admin/CONFIGURATION.md): dispatches configurable morning sounds, particles, and potion effects through `effects-api` when dawn commits.
- [**Self-checking**](../dev/BIT_CODES.md): `/sleep test` (`/ops test`) validates YAML schema directives, coordinator invariants, transport health, and SQL storage in game.

</details>

Quorum policy comparisons and network transport details are in [Performance](#performance) and [`SERVER_DESIGNS.md`](../admin/SERVER_DESIGNS.md).

## Common setups

These work out of the box from `config.yml` and `sleep.yml`:

- [**Classic OnePlayerSleep (`policy: "one player"`)**](../admin/SERVER_DESIGNS.md): Any single player entering a bed (or running `/sleep sleep`) starts a 3-second warmup countdown and skips to morning unless countered via `[Cancel]`.
- [**24/7 survival server with peak hours (population scaling, default)**](../admin/CONFIGURATION.md): Only `1` sleeper is needed when 1-3 players are online late at night, scaling smoothly up to `maxRequired: 8` when the server is full.
- [**Multi-shard survival cluster (`sharding.enabled: true`)**](../admin/SERVER_DESIGNS.md): Link multiple Paper or Folia survival shards under a shared `shardGroupId` (`overworld-main`) so sleep votes and dawn transitions happen once across the whole cluster.

---

## Install

**Requirements:** Paper or Spigot 1.20+, Folia 1.21+, Velocity 3.3+, or Fabric / NeoForge 26.1+. Java 21+ (Java 25 on 26.x).

1. Drop `{{jar}}` into `plugins/` (Paper, Spigot, Folia, Velocity) or `mods/` (Fabric, NeoForge) and start the server.
2. Enter a bed at night or during a thunderstorm (or run `/sleep status` / `/ops status`).
3. **Optional - customize quorum, messages, or wakeup effects:** Edit `plugins/LeafSleep/config.yml`, `sleep.yml`, and `messages.yml`, then run `/sleep reload` and `/sleep test` to verify your changes.

---

## Features

- **Quorum policies:** choose `one player`, `percentage`, `fixed count`, `absolute majority`, or population-adaptive scaling (`logarithmic`, `smootherstep`, `power`, `sigmoid`, `linear`) per world.
- **Anti-troll cancel policies:** choose `tug of war` (each `/sleep cancel` adds `+1` to required sleepers and aborts when cancel quorum is met), population-scaled threshold, or single-click `veto`.
- **Automatic eligibility filtering:** excludes `CREATIVE`, `SPECTATOR`, AFK, vanished (`vanished`, `ess.vanished`), `/sleep ignore`, and explicit `leafsleep.bypass` (`oneplayersleep.bypass`) players from the required count.
- **Phantom & weather control:** clears rain/thunder on skip (configurable for fishing/trident servers) and resets `TIME_SINCE_REST` so skipping the night prevents Phantom spawns.
- **Weighted messages & wakeup buffs:** define multiple themed sleep/wakeup message sets (`default`, `cozy`, `starlight`) and morning `SOUND`, `PARTICLE`, and `POTION` effects in `sleep.yml`.
- **Cross-server transport & SQL persistence:** supports `local only`, `redis pubsub`, `plugin messaging`, and `direct socket` (HMAC-SHA256 UDP), plus async `SQLite`, `MySQL`, `MariaDB`, and `PostgreSQL` persistence.

---

## Performance

`LeafSleep` evaluates player eligibility on bed/command events rather than scanning worlds every tick, and bounds outbound network sync frames with a per-tick token pacer.

**Coordinator, Transport, and Folia Threading Summary:**

| Subsystem / Path | Measured Behavior | Main-Thread / Region-Thread Impact | Verification Suite |
|---|---|---|---|
| **Event-Driven Quorum Coordinator (`ShardSleepCoordinator`)** | Recomputes world eligibility on bed/vote events with 1 Hz idle reconciliation | `0.04-0.12 ms` average coordinator work at 20 players across 4 shards | `LeafSleepCoordinatorAndSyncTest` |
| **Idempotent Dawn Commit (`targetDayEpoch` CAS)** | Commits `targetDayEpoch = (fullTime / 24000) + 1` once per day across all shards | `0` duplicate skips when two shards hit quorum on the same tick | `LeafSleepCoordinatorAndSyncTest` |
| **Folia Regional & Entity Dispatch** | Mutates world time/weather on the global region thread and resets `TIME_SINCE_REST` on each player's entity thread | `0` cross-region thread violations on Folia | `LeafSleepCoordinatorAndSyncTest` |
| **Async SQL State Store (`SleepDatabaseStore`)** | Persists `/sleep ignore` preferences and epoch watermarks off-thread via `:database-api` | `0` main-thread JDBC queries | `LeafSleepBitSuite` (`ops-storage-ibit`) |

---

## How it works

All eligibility checks, warmup countdowns, and cross-shard epoch commits run automatically in the background behind standard `CIRCLE` or world-wide shard groups and quorum settings in `config.yml`:

- **Eligibility snapshot:** When a player enters a bed or runs `/sleep sleep`, the coordinator counts eligible online players in that world (excluding AFK, vanished, Creative/Spectator, and `/sleep ignore` players) and computes required sleepers and cancel thresholds.
- **Warmup & tug-of-war:** Once sleeping players meet the required count, a warmup timer (`warmupDelayTicks`, default `60` ticks / `3s`) starts and players can click `[Sleep]` to join or `[Cancel]` to push back.
- **Idempotent dawn transition:** When the warmup completes, the coordinator atomically advances the monotonic `targetDayEpoch` watermark, sets the world clock to sunrise, clears weather, and dispatches Phantom rest resets and wakeup effects on each player's owning thread.
- **Built-in self-test (`[OPS bit]`):** Startup and `/sleep test` run 5 automated checks covering YAML schema directives (`ops-config-pbit`), message placeholders (`ops-messages-pbit`), transport health (`ops-transport-cbit`), quorum math (`ops-coordinator-cbit`), and SQL round-trips (`ops-storage-ibit`).

---

## Testing and quality gates

Every build runs unit, multi-shard synchronization, and built-in diagnostic checks before release:

- [**Built-in `[OPS bit]` self-tests**](../dev/BIT_CODES.md): `/sleep test` (`/ops test`) verifies `config.yml`, `sleep.yml`, and `messages.yml` directives, quorum curve monotonicity, transport frame budgets, and SQL persistence in game.
- [**Multi-shard & epoch CAS test suite**](../admin/SERVER_DESIGNS.md): verifies `ONE_PLAYER`, `PERCENTAGE`, `FIXED_COUNT`, `ABSOLUTE_MAJORITY`, and `CURVE` policies, `TUG_OF_WAR` cancel offsets, HMAC-SHA256 frame signing, and duplicate epoch rejection across simulated shards.
- [**Coordinator & transport architecture**](../site/how-it-works.md): verifies thread-safe eligibility snapshots, per-tick transport frame pacing, and idempotent epoch watermarks.

---

## Why I made it

Vanilla Minecraft requires every player in the Overworld to lie in a bed at the same time before night skips, which breaks down as soon as one player is AFK, underground, or on another survival shard. Basic one-player-sleep scripts have the opposite problem: one player skips the night immediately without giving anyone hunting mobs or phantoms a chance to cancel.

I built `OnePlayerSleep` and `LeafSleep` to scale sleep requirements with online population, give players a `[Cancel]` tug-of-war window, reset Phantom rest timers for everyone who stayed awake, and keep multi-server survival worlds on the same day cycle.

<!-- only: bbb-pro -->
<!-- kind: promo -->
---

## What paying gets you

Same code, config, and commands as the free download, under the same MIT license. Nothing in the plugin is locked:

| This listing | Free download |
|---|---|
| Priority on my support queue | Community support, best effort |
| A patch release when a bug you report needs one | Updates when I choose to publish them |

Switching between the free download and this one is a jar swap.
<!-- /kind -->
<!-- /only -->

---

## Commands, configuration, and FAQ

Everyday commands, permissions, configuration files, and common operator questions:

<details>
<summary><b>Commands, permissions, configuration files</b></summary>

**Everyday commands** (aliases: `/sleep`, `/ops`, `/playersleep`; full table in [`COMMANDS.md`](../admin/COMMANDS.md)):

| Command | Description | Permission |
| --- | --- | --- |
| `/sleep status` (`/ops status`) | Show world quorum policy, `sleeping/required`, `cancelling/cancelRequired`, `eligible/maxPlayers`, shard count, and `TPS`/`MSPT` | `leafsleep.status` (`true`) |
| `/sleep sleep [messageId]` | Vote to skip the night from anywhere (or click `[Sleep]` in chat) | `leafsleep.sleep` (`true`) |
| `/sleep cancel` (`/ops cancel`) | Vote to cancel an active sleep countdown (or click `[Cancel]` in chat) | `leafsleep.cancel` (`true`) |
| `/sleep ignore` (`/ops ignore`) | Toggle personal opt-out from the eligible player count | `leafsleep.ignore` (`true`) |
| `/sleep reload` (`/ops reload`) | Validate and reload `config.yml`, `sleep.yml`, and `messages.yml` | `leafsleep.reload` (`op`) |
| `/sleep test` (`/ops test`) | Run all 5 `[OPS bit]` PBIT/CBIT/IBIT self-tests | `leafsleep.test` (`op`) |

**Core permissions** (both `leafsleep.*` and `oneplayersleep.*` work interchangeably):

| Permission | Default | Grants |
| --- | --- | --- |
| `leafsleep.status` | `true` | `/sleep status`, `/sleep version`, `/sleep help` |
| `leafsleep.cancel` | `true` | `/sleep cancel` and clicking `[Cancel]` |
| `leafsleep.ignore` | `true` | `/sleep ignore` opt-out toggle |
| `leafsleep.sleep` | `true` | `/sleep sleep` and clicking `[Sleep]` without entering a bed |
| `leafsleep.reload` | `op` | `/sleep reload` |
| `leafsleep.test` | `op` | `/sleep test` diagnostics |
| `leafsleep.bypass` | `false` | Opt-out bypass node (excludes holder from `eligibleCount`; negate if granting `*`) |

**Configuration files** under `plugins/LeafSleep/` (or `config/leafsleep/` on Fabric/NeoForge; full reference in [`CONFIGURATION.md`](../admin/CONFIGURATION.md)):

| File | Controls | Example keys |
| --- | --- | --- |
| `config.yml` | Default quorum/cancel policies, warmup delay, Phantom/weather rules, sharding transport, SQL storage | `defaultQuorumPolicy`, `defaultCancelPolicy`, `warmupDelayTicks`, `sharding.transport` |
| `sleep.yml` | Per-world enablement, `shardGroupId`, per-world policy overrides, `wakeupEffects`, weighted `sleepMessages` | `worlds.world.policy`, `wakeupEffects`, `sleepMessages` |
| `messages.yml` | Player broadcasts, clickable `[Cancel]`/`[Sleep]` button labels, `/sleep status` formatting | `sleepBroadcast`, `wakeupBroadcast`, `cancelBroadcast` |

</details>

<details>
<summary><b>FAQ</b></summary>

**Q: Why does `/sleep status` show `eligible=0` when I test with LuckPerms `*` or `leafsleep.*`?**
A: Granting a wildcard permission in LuckPerms explicitly sets `leafsleep.bypass: true` (`oneplayersleep.bypass: true`), which excludes the player from `eligibleCount`. Negate `leafsleep.bypass: false` and `oneplayersleep.bypass: false` in LuckPerms. Plain vanilla `/op` without a wildcard does not trigger `.bypass`.

**Q: How do I require players to physically lie in a bed instead of clicking `[Sleep]` in chat?**
A: By default `leafsleep.sleep` (`oneplayersleep.sleep`) is `true` so players can click `[Sleep]` in chat or run `/sleep sleep` to join a sleep vote. Negate `leafsleep.sleep: false` and `oneplayersleep.sleep: false` on your `default` group in LuckPerms if you only want physical bed entry to count.

**Q: Why does clicking `[Cancel]` add `+1` to required sleepers instead of immediately aborting?**
A: The default cancel policy is `tug of war`, where each cancel vote offsets one sleeper and aborts the skip when `cancelRequired` is reached, preventing one troll on a busy server from blocking dawn forever. For a single-click abort on small cooperative servers, set `defaultCancelPolicy: "veto"` in `config.yml` and `worlds.world.cancelPolicy: "veto"` in `sleep.yml`.

**Q: Do my existing `OnePlayerSleep` commands and permissions still work?**
A: Yes. Every `/ops` subcommand (`/ops status`, `/ops sleep`, `/ops cancel`, `/ops ignore`, `/ops reload`, `/ops test`) and `oneplayersleep.*` permission node is supported out of the box alongside `/sleep` and `leafsleep.*`.

</details>

---

## Links

- [**LeafSleep README & overview**](../../README.md): architecture summary and documentation index
- [**Server & network design guide**](../admin/SERVER_DESIGNS.md): recipes for 1-player sleep, population curves, and sharded Velocity/Redis clusters
- [**Configuration reference**](../admin/CONFIGURATION.md): full key-by-key table for `config.yml`, `sleep.yml`, and `messages.yml`
- [**Commands & permissions**](../admin/COMMANDS.md): `/sleep` and `/ops` command reference
- [**BIT code catalog (`[OPS bit]`)**](../dev/BIT_CODES.md): startup and `/sleep test` diagnostic codes
- [**Changelog**](../../CHANGELOG.md)
- [**LeafRTP**](../../../LeafRTP/docs/publishing/FRONT_PAGE.md): zero-wait random teleportation (`/rtp` & `/wild`) with `.mca`/`.linear` region pre-filtering
- [**LeafTreeGen**](../../../LeafTreeGen/docs/publishing/FRONT_PAGE.md): procedural custom biome trees baked into native vanilla jigsaw worldgen datapacks
- [**Source on GitHub**](https://github.com/DailyStruggle/OnePlayerSleep): MIT source code, issues, and automated tests
<!-- only: bbb-pro -->
- [**Free download**](https://modrinth.com/plugin/oneplayersleep)
<!-- /only -->
