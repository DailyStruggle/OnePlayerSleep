# Frequently Asked Questions (`LeafSleep` / `OnePlayerSleep`)

Answers to common and non-obvious configuration, permission, and multi-server questions.

---

## Why does `/sleep status` show `eligible=0` when I test with LuckPerms `*` or `leafsleep.*`?

If you grant a wildcard permission (`*`, `leafsleep.*`, or `oneplayersleep.*`) in LuckPerms, LuckPerms explicitly sets `leafsleep.bypass: true` (and `oneplayersleep.bypass: true`). Players with `.bypass` are excluded from `eligibleCount`.

**Fix:** Negate `leafsleep.bypass: false` and `oneplayersleep.bypass: false` in LuckPerms, or test with standard player permissions (`leafsleep.status`, `leafsleep.cancel`, `leafsleep.ignore`, `leafsleep.sleep`). Note that plain vanilla `/op` without a LuckPerms wildcard does **not** trigger `.bypass` because `LeafSleep` checks `isPermissionSet` before reading `.bypass`.

---

## How do I require players to physically lie in a bed instead of clicking `[Sleep]` in chat?

By default, `leafsleep.sleep` (`oneplayersleep.sleep`) is `default: true` so players can click `[Sleep]` in chat (or run `/sleep sleep`) to join a sleep vote while mining or boating, and `[Cancel]` (`leafsleep.cancel`) is `default: true`.

If you want to require players to physically lie down in a bed to start or join a sleep vote, negate `leafsleep.sleep: false` and `oneplayersleep.sleep: false` on your `default` group in LuckPerms.

---

## Why does clicking `[Cancel]` add `+1` to required sleepers instead of immediately cancelling the night skip?

By default, `defaultCancelPolicy: "tug of war"` is enabled in `config.yml` (and `worlds.world.cancelPolicy: "tug of war"` in `sleep.yml`). Under `tug of war`:
- Each `/sleep cancel` click offsets 1 sleeper (`effectiveRequired = sleepRequired + cancellingCount`).
- The sleep warmup is only aborted completely when `cancellingCount >= cancelRequired` (determined by `cancelCurve`).

This prevents a single troll on a 40-player server from locking the server in eternal night. If you run a small cooperative server and want a single `[Cancel]` click to immediately abort the skip, set `defaultCancelPolicy: "veto"` in `config.yml` and `worlds.world.cancelPolicy: "veto"` in `sleep.yml`, then run `/sleep reload`.

---

## Which `sleepCurve.type` should I use for my server?

All five curve types (`smootherstep`, `linear`, `power`, `logarithmic`, `sigmoid`) map normalized population load $t = (\text{eligible} - 1) / (\text{maxPlayers} - 1)$ between `minRequired` and `maxRequired`:

- **Want 1–2 players to skip night easily during quiet/moderate hours, and only require a group at peak?** Use `type: "smootherstep"`.
- **Want small groups (5–15 players) to immediately require 3–6 sleepers while capping peak hours at `maxRequired: 8`?** Use `type: "logarithmic"` (`parameter: 10.0`, default).
- **Want a gentle square-root ramp?** Use `type: "power"` (`parameter: 0.5`).
- **Want a sharp switch from 1-player sleep below 25% server capacity to strict group voting above 25% capacity?** Use `type: "sigmoid"` with `midpoint: 0.25` and `parameter: 12.0`.

See the full comparison table in [Server & Network Design Guide](SERVER_DESIGNS.md#5-247-global-time-zone-server-curve-selection--anti-troll-tug_of_war-design).

---

## Should I use `redis pubsub`, `plugin messaging`, or `direct socket` for a multi-server network?

- **`sharding.transport: "redis pubsub"` (Recommended for 2–100+ shards or multi-proxy networks):** Delivers `BED_ENTER`, `SLEEP_VOTE`, `CANCEL_SLEEP`, `EPOCH_COMMIT`, and `SHARD_HEARTBEAT` frames across all shards in sub-10ms with zero tick-thread blocking, and **works even when a shard has `0` online players** so empty shards still advance their clock to dawn.
- **`sharding.transport: "plugin messaging"` (Best when you have 1 Velocity proxy and no Redis):** Requires installing `LeafSleep` on Velocity as well. Uses channel `leafsleep:sync`. Keep in mind that Minecraft plugin messaging can only reach backend servers that currently have at least `1` online player connected.
- **`sharding.transport: "direct socket"` (Best for 2–8 shards on a private VLAN/WireGuard mesh without Redis):** Sends HMAC-SHA256 signed UDP datagrams directly between `sharding.peers` (`host:port`). Requires `LEAFSLEEP_HMAC_SECRET` and NTP-synchronized clocks (`±30s` replay window).
