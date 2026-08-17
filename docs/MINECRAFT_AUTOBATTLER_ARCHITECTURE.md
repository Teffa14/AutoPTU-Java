# Minecraft Autobattler Architecture

AutoPTU-Java is the authoritative PTU battle runtime. Minecraft, Cobblemon, and Craftics are adapters and presentation/runtime layers around it. They must not become a second implementation of PTU rules.

## Target runtime

```text
Minecraft / Fabric server
        |
        | world snapshot, entities, controller input
        v
Minecraft Battle Adapter
  - BlockPos <-> GridCoord
  - blocks <-> MovementGrid / terrain DTOs
  - Cobblemon entity <-> stable combatant id
  - Craftics movement/render hooks
        |
        | typed core DTOs
        v
AutoPTU-Java (headless, Java 21)
  BattleState
  -> legalActions(state, actor)
  -> AIPolicy chooses BattleChoice
  -> applyAction(state, choice, rng)
  -> ordered BattleEvent list + new state
        |
        | semantic events only
        v
Minecraft Event Renderer
  - move models
  - play animations/particles/sounds
  - update terrain visuals
  - show damage/status/KO
```

The Python AutoPTU repository is a development/CI oracle during migration. It is not required by the final Minecraft server.

## Autobattler decision loop

Every controller uses the same rule boundary:

```text
snapshot battle state
      |
      v
legalActions(actor)
      |
      +--> AI/autoplay scores legal choices
      +--> optional human UI displays legal choices
      |
      v
selected BattleChoice
      |
      v
applyAction
      |
      +--> validates again server-side
      +--> consumes action economy
      +--> resolves RNG/rules/hooks
      +--> mutates authoritative battle state
      +--> emits ordered semantic events
      |
      v
Minecraft plays those events
```

AI must never teleport a unit, apply damage, or mutate statuses directly. It selects from legal `BattleChoice` values only.

## Grid ownership

The battle engine uses arena-relative integer coordinates (`GridCoord`). It does not import Minecraft `BlockPos`.

The Minecraft adapter owns a transform such as:

```text
arena origin BlockPos(100, 64, -40)
GridCoord(0, 0) <-> BlockPos(100, 64, -40)
GridCoord(1, 0) <-> BlockPos(101, 64, -40)
...
```

Minecraft blocks are sampled into deterministic battle DTOs before rule evaluation. `MovementGrid` already follows this rule. Future terrain/elevation/cover DTOs must follow the same pattern.

The core must not query the live Minecraft world halfway through damage or movement resolution. A resolution step operates against one authoritative snapshot and then emits changes.

## Movement and Craftics

AutoPTU owns:

- legal destination tiles;
- PTU movement speeds/capabilities;
- rough/difficult terrain cost;
- Swim/Sky/Burrow/Phase/Liquefied behavior;
- footprints and collision legality;
- action-economy cost;
- forced movement and knockback rules.

Craftics may own:

- A* path used to visually traverse an already legal destination;
- entity interpolation/animation;
- grid rendering and selection UX;
- Minecraft networking helpers.

Craftics AP/Speed or A* must not silently override PTU legality. If a Craftics path cannot visually reach a PTU-legal destination, that is an adapter/rendering problem, not permission to change the battle result.

## Move targeting

The legal-action API distinguishes four target modes:

- `SELF`: the actor itself.
- `FIELD`: whole-field effects without a target entity.
- `COMBATANT`: a specific eligible combatant.
- `TILE`: an aim point used by Line/Cone/Blast/other grid effects.

`TILE` is required for an autobattler because an AI may correctly aim an AoE at an empty square to hit several units. Minecraft should render the selected aim point; it should not recompute the affected tiles.

## Stable combatant identity

Minecraft entities are runtime objects and can unload/reload. Core battle state must use stable string IDs for combatants. The adapter maintains the mapping:

```text
core id: battle-42:p1:charizard
    <-> Cobblemon/Minecraft entity reference
```

Events and choices refer to the core ID. Rendering resolves that ID back to the current entity/model.

## Semantic events

The renderer should consume a small event vocabulary rather than one Minecraft implementation per PTU move. Planned events include:

- `MOVE_START`, `MOVE_STEP`, `MOVE_END`
- `CAST_START`
- `HIT`, `MISS`, `CRIT`
- `DAMAGE`, `HEAL`
- `STATUS_APPLIED`, `STATUS_REMOVED`
- `PUSH`, `PULL`, `TELEPORT`
- `TERRAIN_CHANGED`
- `HAZARD_CREATED`, `HAZARD_REMOVED`
- `AURA_CREATED`, `AURA_REMOVED`
- `KO`

Example: Flamethrower is resolved by AutoPTU. Minecraft receives aim/cast/hit/damage/status/terrain events and chooses visuals for them. Minecraft does not calculate Burn chance, damage, range, or affected tiles.

## Server authority and synchronization

For multiplayer and autobattler execution:

1. server snapshots battle state;
2. server asks core for legal choices;
3. server AI chooses a choice;
4. server applies it through core;
5. server stores resulting state/event sequence;
6. clients receive playback events;
7. clients acknowledge/finish animation;
8. server advances to the next decision window.

Client animation timing must never change the deterministic battle result.

## Module boundary

Keep this repository's battle core free of Fabric/Cobblemon dependencies.

Recommended later modules:

```text
autoptu-core                 pure Java rules/runtime
autoptu-minecraft-adapter    Fabric/Cobblemon/Craftics integration
autoptu-sim                  headless tournament/debug runner
```

This lets CI run thousands of battles without launching Minecraft and lets the same AI run in tests, tournaments, dungeons, PvP, and the live server.

## First Minecraft vertical slice

Do not wait for the full PTU port. The first integration milestone should prove the boundary with:

- one reserved 12x12 arena;
- 3v3 combatants;
- 1x1 and 2x2 footprints;
- AI-vs-AI only;
- Shift movement;
- one melee move;
- one ranged move;
- one tile-aimed AoE;
- one status effect;
- one terrain/hazard effect;
- ordered event playback using Cobblemon models.

The acceptance condition is not visual polish. The same seed and battle input must produce the same core choices/events outside Minecraft and inside the Minecraft adapter.

## Migration rule

Every new Java battle slice must still pass the Python-oracle parity gates. Minecraft integration can begin once a narrow vertical slice is parity-safe, but Minecraft-specific code must never be used to compensate for missing or incorrect PTU rules in the Java core.
