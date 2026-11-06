# leafsleep-ADR-002 - Multi-Platform Carrier Architecture via `ServerAccessor`, `OnePlayerSleep` Configuration Superset, and `[OPS bit]`

**Status:** Accepted
**Date:** 2026-10-27

## Context

`LeafSleep` targets all modern Minecraft server and proxy platforms (`Spigot`, `Paper`, `Folia`, `Fabric`, `NeoForge`, and `Velocity`) from a single shaded artifact (`LeafSleep-<version>.jar`) while preserving the configuration ergonomics of `OnePlayerSleep`.

Writing bespoke platform adapters inside `plugins/LeafSleep/` would duplicate the platform lifecycle, scheduler, and event plumbing already maintained in `api/server-api` and `platforms/*` (`BukkitPluginServerAccessor`, `FabricModServerAccessor`, `NeoForgeModServerAccessor`), which `LeafTreeGen` (`plugins/LeafTreeGen/docs/adr/ADR-003-java-plugin-architecture.md`) already consumes via thin ~30-line carriers.

At the same time, server operators migrating from `OnePlayerSleep` expect the same YAML configuration capabilities (customizable weighted sleep/wakeup messages, clickable `[Cancel]` / `[Sleep]` chat prompts, wakeup buffs/effects, per-world settings, AFK/vanish/gamemode exclusions, cooldowns) extended with sharding and storage settings—without needing a legacy config-importer command.

## Decision

Build `LeafSleep` as a pure-Java common module (`leafsleep-common`) plus thin platform carriers (`leafsleep-paper`, `leafsleep-fabric`, `leafsleep-neoforge`, `leafsleep-velocity`, assembled in `leafsleep-plugin`), delegating all platform hooks to `api/server-api` and the pre-existing `platforms/*` adapters.

### 1. Module Layout (`plugins/LeafSleep/`)

```text
plugins/LeafSleep/
├── build.gradle
├── README.md
├── CHANGELOG.md
├── docs/adr/
│   ├── README.md
│   ├── ADR-TEMPLATE.md
│   ├── ADR-001-sharded-sleep-state-machine-and-hybrid-sync.md
│   └── ADR-002-multi-platform-carrier-and-config-architecture.md
├── leafsleep-common/      # Pure Java 17: Config, ShardSleepCoordinator, Direct Bus + SQL Store, Commands, [OPS bit]
├── leafsleep-paper/       # Paper/Spigot/Folia carrier binding BukkitPluginServerAccessor
├── leafsleep-fabric/      # Fabric carrier binding FabricModServerAccessor
├── leafsleep-neoforge/    # NeoForge carrier binding NeoForgeModServerAccessor
├── leafsleep-velocity/    # Optional lightweight Velocity plugin-message fanout relay
└── leafsleep-plugin/      # Unified shaded multi-loader JAR -> LeafSleep-<version>.jar
```

### 2. Platform-Neutral `SleepWorldAccessor` & Sleep Event Hooks in `api/server-api`
- Add `io.github.dailystruggle.server.api.SleepWorldAccessor` (extended by `ServerAccessor` with safe default no-op methods so `LeafRTP`, `LeafTreeGen`, and existing mocks remain 100% compatible):
  - `getWorldTime(String worldName)`, `getWorldFullTime(String worldName)`, `setWorldTime(String worldName, long timeTicks)`
  - `getWeather(String worldName)`, `setWeather(String worldName, WeatherState state, int durationTicks)` (`WeatherState`: `CLEAR`, `RAIN`, `THUNDER`)
  - `getWorldPlayers(String worldName)` returning immutable `PlayerSleepSnapshot` records `(UUID playerId, String playerName, boolean sleeping, int sleepTicks, boolean sleepIgnored, String gameMode, boolean vanishedOrAfk, int timeSinceRestTicks, Set<String> permissions)`
  - `wakePlayer(UUID playerId, boolean setSpawn)` and `resetTimeSinceRest(UUID playerId)`
  - `sendActionBar(UUID playerId, String message)`, `showBossBar(UUID playerId, String barId, String title, double progress)`, and `clearBossBar(UUID playerId, String barId)`
  - `registerMessagingChannel(String channel, BiConsumer<UUID, byte[]> receiver)` and `sendPluginMessage(String channel, byte[] payload)`
- Extend `WorldEventHook` and `DispatchingWorldEventHook` with default-capable `onPlayerBedEnter(Consumer<PlayerBedEnterContext>)`, `onPlayerBedLeave(Consumer<PlayerBedLeaveContext>)`, and `onWorldTimeSkip(Consumer<WorldTimeSkipContext>)`.
- Implement these methods in the pre-existing `BukkitPluginServerAccessor` / `BukkitWorldEventHook` (using `Bukkit.getGlobalRegionScheduler()` for world clock/weather and `player.getScheduler()` for wake and `Statistic.TIME_SINCE_REST` on Folia), `FabricModServerAccessor`, and `NeoForgeModServerAccessor`.

### 3. `OnePlayerSleep` Configuration Superset & SDK Reuse
- `leafsleep-common` consumes `:server-api`, `:yaml-api`, `:units-api`, `:bit-api`, `:config-bit-api`, `:commands-api`, `:menu-api`, `:effects-api`, and `:predict-api`:
  - Ships self-documenting `config.yml`, `player.yml`, `commands.yml`, `sleep.yml`, additive `network.yml`, and weighted `messages/*.yml` theme files (managed via `MultiConfigParser<SleepMessageKeys>` from `:yaml-api`) with `@type`, `@range`, `@options`, and `@default` comment annotations.
  - Preserves all familiar `OnePlayerSleep` settings (weighted random sleep/wakeup messages with placeholders under `messages/*.yml`, clickable chat `[Cancel]` / `[Sleep]` actions, cant-sleep/cancel messages in `player.yml`, command/status strings in `commands.yml`, wakeup effects/buffs via `effects-api`, AFK/vanish/gamemode exclusions, phantom reset, cooldowns) and adds `network.yml` (with backward-compatible `sharding:` and `storage:` fallback in `config.yml`).
  - Omits legacy file-migration commands in favor of clean default configs.

### 4. Commands & `[OPS bit]` Built-In Test
- Exposes `/sleep` (with `/ops` alias) via `commands-api` and `menu-api`:
  - `/sleep status` — displays local and cross-shard sleeper counts, quorum progress, world time, and sync transport status (plus interactive `[Sleep]` / `[Cancel]` buttons when in a sleepable window).
  - `/sleep sleep` — votes for or initiates sleep when permitted (`leafsleep.sleep`).
  - `/sleep cancel` — cancels an active sleep countdown across the shard group (`leafsleep.cancel`).
  - `/sleep ignore` — toggles persistent sleep-ignore status (`leafsleep.ignore`).
  - `/sleep message [id|random]` — views or selects a persistent personal sleep/wakeup message theme (`leafsleep.sleep`).
  - `/sleep reload` — validates YAML via `config-bit-api` and hot-reloads when zero `ERROR` findings exist.
  - `/sleep test` — runs PBIT/CBIT/IBIT self-tests (`[OPS bit]`) covering config schema, locale key parity, SQL store health, and direct transport connectivity, matching the `OnePlayerSleep` / `LeafSleep` diagnostic profile in `discord-support-bot/support-bot.yml`.

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|--------------|
| Bespoke Bukkit/Fabric/NeoForge adapters inside `plugins/LeafSleep/` | Duplicates `platforms/bukkit`, `platforms/fabric`, and `platforms/neoforge`; increases maintenance across Minecraft version updates. |
| Separate platform JARs (`LeafSleep-Paper.jar`, `LeafSleep-Fabric.jar`, etc.) | Single shaded multi-loader `LeafSleep-<version>.jar` (matching `LeafTreeGen` and `LeafRTP`) simplifies distribution and support. |
| Legacy `OnePlayerSleep` config importer | Unnecessary complexity when `LeafSleep` ships a direct configuration superset with self-validating `config-bit-api` comments. |

## Consequences

- **Positive:**
  - Zero platform imports in `leafsleep-common`; thin ~30-line carrier classes per loader.
  - Folia regional threading (`GlobalRegionScheduler` vs `EntityScheduler`) and zero main-thread I/O (`S-005`) are enforced once in `BukkitPluginServerAccessor`.
  - Out-of-the-box `[OPS bit]` support triage compatibility with `discord-support-bot`.
- **Negative / Trade-offs:**
  - Requires adding `SleepWorldAccessor` and 3 sleep event hooks to `api/server-api` (mitigated via default no-op implementations).

## References

- [`ADR-001`](ADR-001-sharded-sleep-state-machine-and-hybrid-sync.md) (Sharded Sleep State Machine, Idempotent Epoch CAS, and Hybrid Direct/Database Sync)
- [`plugins/LeafTreeGen/docs/adr/ADR-003-java-plugin-architecture.md`](../../../LeafTreeGen/docs/adr/ADR-003-java-plugin-architecture.md)
- `api/server-api/src/main/java/io/github/dailystruggle/server/api/ServerAccessor.java`
- `discord-support-bot/support-bot.yml` (`OnePlayerSleep` / `LeafSleep` profile)
