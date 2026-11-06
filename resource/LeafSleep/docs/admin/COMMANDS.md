# Commands & Permissions Reference (`LeafSleep` / `OnePlayerSleep`)

`LeafSleep` registers `/sleep` alongside legacy `OnePlayerSleep` aliases (`/ops`, `/playersleep`). All commands work identically on `Paper`, `Spigot`, `Purpur`, `Folia`, `Fabric`, and `NeoForge`.

---

## Subcommands

| Command | Aliases | Default Permission | Description |
|---|---|---|---|
| `/sleep status` | `/ops status` | `leafsleep.status` / `oneplayersleep.status` (`true`) | Displays per-world `shardGroupId`, active quorum and cancel policies (`policy=curve:<type>/<cancelPolicy>`), `sleeping/required`, `cancelling/cancelRequired`, `eligible/maxPlayers`, committed `epoch`, transport mode (`shards=<N>`), SQL storage health, and live `TPS` / `MSPT` / `leafsleepAvg` telemetry. |
| `/sleep sleep [messageId]` | `/ops sleep [messageId]` | `leafsleep.sleep` / `oneplayersleep.sleep` (`true`) | Votes to skip the night in the caller's current world without needing a physical bed (or triggers the clickable `[Sleep]` chat button). Optionally selects a specific message theme ID from `messages/*.yml` (`default`, `cozy`, `starlight`, `campfire`, `phantom`, `nightmare`, `rooster`, `lazy`). |
| `/sleep cancel` | `/ops cancel` | `leafsleep.cancel` / `oneplayersleep.cancel` (`true`) | Casts a counter-vote against an active sleep warmup (or triggers the clickable `[Cancel]` chat button). Under `tug of war`, each cancel vote adds `+1` to required sleepers and aborts the skip when `cancelRequired` is reached. |
| `/sleep ignore` | `/ops ignore` | `leafsleep.ignore` / `oneplayersleep.ignore` (`true`) | Toggles personal opt-out from `eligibleCount`. Persisted asynchronously to `storage` (`leafsleep_player_prefs`). |
| `/sleep message [id\|random]` | `/ops message [id\|random]` | `leafsleep.sleep` / `oneplayersleep.sleep` (`true`) | Views or selects a persistent personal sleep and wakeup message theme from `messages/*.yml` (`default`, `cozy`, `starlight`, `campfire`, `phantom`, `nightmare`, `rooster`, `lazy`, or `random`), displaying clickable theme chips in chat when invoked without arguments. |
| `/sleep reload` | `/ops reload` | `leafsleep.reload` / `oneplayersleep.reload` (`op`) | Validates `config.yml`, `network.yml`, `sleep.yml`, `player.yml`, `commands.yml`, and `messages/*.yml` against `@type`, `@range`, and `@options` directives and hot-reloads the runtime without resetting committed day epochs. |
| `/sleep test` | `/ops test` | `leafsleep.test` / `oneplayersleep.test` (`op`) | Runs all 5 `[OPS bit]` PBIT, CBIT, and IBIT self-test components (`ops-config-pbit`, `ops-messages-pbit`, `ops-transport-cbit`, `ops-coordinator-cbit`, `ops-storage-ibit`). |
| `/sleep version` | `/ops version` | `leafsleep.status` (`true`) | Displays the running `LeafSleep` version, platform adapter (`Paper`, `Folia`, `Fabric`, `NeoForge`), and active `serverShardId`. |
| `/sleep help` | `/ops help` | `leafsleep.status` (`true`) | Lists available `/sleep` subcommands for the caller's permission level. |

---

## Permission Nodes

Both `leafsleep.*` and legacy `oneplayersleep.*` permission namespaces are supported interchangeably:

| Permission Node | Legacy Alias | Default | Behavior |
|---|---|---|---|
| `leafsleep.status` | `oneplayersleep.status` | `true` | Allows `/sleep status`, `/sleep version`, and `/sleep help`. |
| `leafsleep.cancel` | `oneplayersleep.cancel` | `true` | Allows clicking `[Cancel]` or running `/sleep cancel` during an active sleep warmup. |
| `leafsleep.ignore` | `oneplayersleep.ignore` | `true` | Allows running `/sleep ignore` to opt out of `eligibleCount`. |
| `leafsleep.sleep` | `oneplayersleep.sleep` | `true` | Allows running `/sleep sleep` or clicking `[Sleep]` in chat to vote for dawn without entering a physical bed. Negate `leafsleep.sleep: false` and `oneplayersleep.sleep: false` in LuckPerms if you require every voter to physically enter a bed. |
| `leafsleep.reload` | `oneplayersleep.reload` | `op` | Allows `/sleep reload`. |
| `leafsleep.test` | `oneplayersleep.test` | `op` | Allows `/sleep test` (`[OPS bit]` diagnostics). |
| `leafsleep.bypass` | `oneplayersleep.bypass` | `false` | **Opt-out bypass node.** When explicitly granted (`player.isPermissionSet(node) && player.hasPermission(node)`), excludes the player from `eligibleCount`. If you grant wildcard `leafsleep.*` or `oneplayersleep.*` in LuckPerms, negate `leafsleep.bypass: false` and `oneplayersleep.bypass: false` so players are not inadvertently excluded. |
