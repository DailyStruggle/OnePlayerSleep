# BIT Code Catalog (`[OPS bit]`)

Generated from the registered `LeafSleepBitSuite` (`[OPS bit]`) catalog. IDs and keys are stable; retired codes are not reused.

Run `/sleep test` (or `/ops test`) in-game or from the console to execute all 5 PBIT, CBIT, and IBIT self-test components. A healthy shard or cluster reports:

```text
[OPS bit] Health=HEALTHY | Components=5 | Findings=0
```

## Namespaces

| Prefix |
|---|
| `OPS` |

## `OPS`

| ID | Code | Severity | Tier | Subsystem | Message key | Remediation |
|---:|---|---|---|---|---|---|
| 1 | `OPS_CONFIG_SCHEMA` | ERROR | PBIT | config | `messages.reloadFailed` | Fix the YAML value in `config.yml`, `messages.yml`, or `sleep.yml` to match its `# @type`, `# @range`, or `# @options` directive, then run `/sleep reload`. |
| 2 | `OPS_MESSAGE_TEMPLATES` | WARNING | PBIT | messages | `messages.reloadFailed` | Ensure `sleepBroadcast` and `wakeupBroadcast` message templates in `messages.yml` are non-blank strings. |
| 3 | `OPS_TRANSPORT_HEALTH` | ERROR | CBIT | transport | `messages.statusTransportLine` | Verify `sharding.transport` (`LOCAL_ONLY`, `PLUGIN_MESSAGING`, `DIRECT_SOCKET`, `REDIS_PUBSUB`), `sharding.channel`, `sharding.bindHost`/`bindPort`, `LEAFSLEEP_REDIS_HOST`/`LEAFSLEEP_REDIS_PORT`, and `LEAFSLEEP_HMAC_SECRET` in `config.yml`. |
| 4 | `OPS_COORDINATOR_EPOCH` | WARNING | CBIT | coordinator | `messages.statusWorldLine` | Verify world quorum policies (`quorumPolicy`, `sleepCurve`, `cancelCurve`) and `shardGroupId` mappings under `worlds:` in `sleep.yml`. |
| 5 | `OPS_STORAGE_ROUNDTRIP` | ERROR | IBIT | storage | `messages.statusTransportLine` | Verify SQLite file write permissions or remote `MYSQL` / `MARIADB` / `POSTGRESQL` host, port, database, and credentials under `storage:` in `config.yml`. |
