# AutoPTU Integration Coverage

AutoPTU-Java is not considered integration-complete when a mod, dependency, adapter, repository, API, data source, or external runtime is merely referenced. Every relevant integration must have an explicit, verifiable connection to the AutoPTU system.

## Integration mandate

Every external element tracked by this project must be classified as one of:

- `WIRED`: production code has an explicit typed boundary to AutoPTU and automated coverage proves the contract.
- `ADAPTER_ONLY`: the external system participates only through an adapter/presentation boundary and cannot own or recalculate PTU rules.
- `BLOCKED`: the intended connection is documented, but a concrete API/version/licensing/technical blocker prevents completion.
- `REFERENCE_ONLY`: the project is used for architecture, testing methodology, migration technique, or design research and is intentionally not linked as a runtime dependency.
- `UNUSED`: no current AutoPTU capability consumes it; this state is temporary and must have a documented reason or the reference should be removed.

No integration may be reported as complete without evidence in code and tests.

## Required proof for WIRED and ADAPTER_ONLY integrations

A completed integration must identify:

1. the exact external mod/library/API/version or repository;
2. the AutoPTU module or adapter that owns the connection;
3. the typed inputs accepted from the external system;
4. the authoritative AutoPTU APIs called;
5. the semantic events/state returned to the external system;
6. ownership of RNG, legality, action economy, damage, statuses, movement, targeting, lifecycle and other PTU rules;
7. automated contract/integration tests;
8. failure and version-mismatch behavior;
9. whether the integration is required in production, optional, development-only, or reference-only.

## Authority rule

AutoPTU-Java remains server-authoritative for PTU legality and battle state. Minecraft, Fabric, Cobblemon, Craftics, rendering systems, AI clients and other mods may provide world snapshots, entity identity, controller input, presentation, animation, interpolation, networking and other adapter services. They must not silently recalculate or override PTU outcomes.

Any external system that offers its own combat, pathfinding, AP, speed, damage, status, targeting or initiative logic must be adapted so that AutoPTU remains authoritative. If an external capability cannot be used without taking authority away from AutoPTU, mark it `BLOCKED` until an adapter-safe design exists.

## Current top-level integration inventory

| Integration | Intended role | Current classification | Completion requirement |
| --- | --- | --- | --- |
| Python AutoPTU pinned oracle | Differential behavior specification during migration | WIRED | Keep exact pinned SHA, deterministic fixtures, ordered-event and final-state parity gates. |
| AutoPTU Java rule registries | Production PTU rule composition | PARTIAL / must become WIRED | All rule registries must enter through the authoritative composition root instead of hidden static defaults or duplicate runtime maps. |
| Minecraft / Fabric server | Production host, world snapshot, networking and playback | ADAPTER_ONLY / not yet complete | Dedicated module with world<->core DTO translation and end-to-end deterministic adapter tests. |
| Cobblemon | Pokemon persistence/entities/models/visuals | ADAPTER_ONLY / not yet complete | Stable combatant identity binding, controlled PokemonEntity ownership, visual/action-effect bridge, no Showdown authority. |
| Craftics | Movement/render/network helper capability | ADAPTER_ONLY / not yet complete | Consume authoritative AutoPTU movement outcomes; A*/interpolation may render paths but may not redefine PTU legality. |
| Headless simulation / tournament runner | Core consumer for testing, AI and batch battles | WIRED/PARTIAL | Must consume the same legal-action and apply-action APIs as production adapters. |
| AI controllers | Choice policy only | WIRED/PARTIAL | AI consumes legal choices and observations and cannot mutate battle state directly. |
| Reference repositories in `REFERENCE_REPOS.md` | Architecture/migration/testing research | REFERENCE_ONLY unless explicitly promoted | Extract documented patterns/contracts; do not add runtime coupling merely because a repository is listed. Respect licenses. |
| Build/test dependencies | Development/runtime support | WIRED only when actually consumed | Every declared dependency must have a concrete consumer; stale declarations should be removed. |

`PARTIAL` is an audit state, not a final classification. Each partial item must eventually resolve to `WIRED`, `ADAPTER_ONLY`, `BLOCKED`, `REFERENCE_ONLY`, or be removed.

## Reference repository rule

`docs/REFERENCE_REPOS.md` deliberately includes projects that must not become production dependencies. Some are GPL-licensed, archived, written in another language, or useful only as methodology references. Their value must still be connected to AutoPTU through a concrete artifact when applicable: an architecture decision, protocol contract, parity harness technique, type-tracing tool, test pattern, or documented rejected approach. This is the correct form of integration for `REFERENCE_ONLY` projects.

Do not copy incompatible implementation code or introduce license obligations accidentally.

## Continuous audit

Every bounded AutoPTU work slice must inspect this matrix when the change touches external systems or an integration boundary. When a new mod, dependency, API, repository or reference is added, add it here in the same change and define its intended connection.

The project is ecosystem-complete only when every retained external element has a final classification and every `WIRED`/`ADAPTER_ONLY` row has executable evidence.
