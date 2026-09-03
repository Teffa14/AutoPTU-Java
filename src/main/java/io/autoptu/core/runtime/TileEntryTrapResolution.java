package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure authoritative resolver for tile-local terrain traps on entry.
 * Movement producers supply the landing context; adapters only render the result.
 */
public final class TileEntryTrapResolution {
    private TileEntryTrapResolution() {}

    public record TrapLayer(
            String trapKey,
            int layers,
            String sourceId,
            String sourceTeamId,
            Set<String> terrains,
            String trapName
    ) {
        public TrapLayer {
            trapKey = normalize(trapKey);
            sourceId = safe(sourceId);
            sourceTeamId = safe(sourceTeamId);
            trapName = safe(trapName);
            terrains = normalizeSet(terrains);
            if (trapKey.isBlank()) throw new IllegalArgumentException("trapKey is required");
        }
    }

    public record EntryContext(
            String actorId,
            String actorName,
            String actorTeamId,
            int actorHp,
            GridCoord coordinate,
            Set<String> naturewalkTerrains
    ) {
        public EntryContext {
            actorId = safe(actorId);
            actorName = safe(actorName);
            actorTeamId = safe(actorTeamId);
            naturewalkTerrains = normalizeSet(naturewalkTerrains);
            if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
            if (actorName.isBlank()) actorName = actorId;
            if (actorHp < 0) throw new IllegalArgumentException("actorHp cannot be negative");
            if (coordinate == null) throw new IllegalArgumentException("coordinate is required");
        }

        public EntryContext(
                String actorId,
                String actorTeamId,
                int actorHp,
                GridCoord coordinate,
                Set<String> naturewalkTerrains
        ) {
            this(actorId, actorId, actorTeamId, actorHp, coordinate, naturewalkTerrains);
        }
    }

    /** Language-neutral status instruction produced by a triggered entry hazard. */
    public record StatusApplication(
            String actorId,
            String targetId,
            String status,
            String moveName,
            String moveType,
            String moveCategory,
            String effect,
            String description,
            int remaining
    ) {}

    /** Ordered observable consequences of one effective trap entry. */
    public enum EffectStep {
        APPLY_STATUS,
        EMIT_TRAP_EVENT,
        CONSUME_TRAP
    }

    public record Trigger(
            String actorId,
            String trapKey,
            String trapName,
            String sourceId,
            Set<String> terrains,
            GridCoord coordinate,
            String description,
            int targetHp,
            StatusApplication statusApplication,
            List<EffectStep> effectOrder
    ) {
        public Trigger {
            terrains = Set.copyOf(terrains == null ? Set.of() : terrains);
            effectOrder = List.copyOf(effectOrder == null ? List.of() : effectOrder);
        }
    }

    public record Block(
            String actorId,
            String trapKey,
            String description,
            int targetHp
    ) {}

    public record Result(List<Trigger> triggers, List<Block> blocks, Set<String> consumedTrapKeys) {
        public Result {
            triggers = List.copyOf(triggers == null ? List.of() : triggers);
            blocks = List.copyOf(blocks == null ? List.of() : blocks);
            consumedTrapKeys = Set.copyOf(consumedTrapKeys == null ? Set.of() : consumedTrapKeys);
        }
    }

    public static Result resolve(EntryContext context, List<TrapLayer> traps) {
        if (context == null) throw new IllegalArgumentException("entry context is required");
        List<Trigger> triggers = new ArrayList<>();
        List<Block> blocks = new ArrayList<>();
        LinkedHashSet<String> consumed = new LinkedHashSet<>();

        for (TrapLayer trap : traps == null ? List.<TrapLayer>of() : traps) {
            if (trap == null || trap.layers() <= 0) continue;
            if (!trap.sourceId().isBlank()
                    && !trap.sourceTeamId().isBlank()
                    && trap.sourceTeamId().equals(context.actorTeamId())) {
                continue;
            }
            if (!trap.terrains().isEmpty() && overlaps(trap.terrains(), context.naturewalkTerrains())) {
                blocks.add(new Block(
                        context.actorId(),
                        trap.trapKey(),
                        "Naturewalk ignores the trap's terrain-linked effects.",
                        context.actorHp()
                ));
                continue;
            }

            String trapName = trap.trapName().isBlank() ? trap.trapKey() : trap.trapName();
            StatusApplication statusApplication = new StatusApplication(
                    context.actorId(),
                    context.actorId(),
                    "Slowed",
                    "Trap",
                    "Normal",
                    "Status",
                    "trap",
                    "The trap leaves the target Slowed until end of next turn.",
                    1
            );
            triggers.add(new Trigger(
                    context.actorId(),
                    trap.trapKey(),
                    trapName,
                    trap.sourceId(),
                    trap.terrains(),
                    context.coordinate(),
                    "A terrain trap is triggered on entry.",
                    context.actorHp(),
                    statusApplication,
                    List.of(EffectStep.APPLY_STATUS, EffectStep.EMIT_TRAP_EVENT, EffectStep.CONSUME_TRAP)
            ));
            // Python _consume_trap removes the whole key after one entry trigger.
            consumed.add(trap.trapKey());
        }
        return new Result(triggers, blocks, consumed);
    }

    private static boolean overlaps(Set<String> left, Set<String> right) {
        for (String value : left) if (right.contains(value)) return true;
        return false;
    }

    private static Set<String> normalizeSet(Set<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                String item = normalize(value);
                if (!item.isBlank()) normalized.add(item);
            }
        }
        return Set.copyOf(normalized);
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
