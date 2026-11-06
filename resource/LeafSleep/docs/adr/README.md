# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for the `LeafSleep` project (`plugins/LeafSleep/`).

An ADR captures a significant architectural decision: the context that forced it, the decision made, the alternatives considered, and the consequences. They are numbered sequentially and never deleted - superseded records are marked as such.

## Index

| # | Title | Status |
|---|-------|--------|
| [leafsleep-ADR-001](leafsleep-ADR-001-sharded-sleep-state-machine-and-hybrid-sync.md) | Sharded Sleep State Machine, Idempotent Epoch CAS, and Hybrid Direct/Database Sync | Accepted |
| [leafsleep-ADR-002](leafsleep-ADR-002-multi-platform-carrier-and-config-architecture.md) | Multi-Platform Carrier Architecture via `ServerAccessor`, `OnePlayerSleep` Configuration Superset, and `[OPS bit]` | Accepted |

## Template

Use [ADR-TEMPLATE.md](ADR-TEMPLATE.md) when recording a new decision.
