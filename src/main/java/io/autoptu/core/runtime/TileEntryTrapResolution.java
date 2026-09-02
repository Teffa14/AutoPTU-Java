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
            if (layers < 0) throw new IllegalArgumentException("layers cannot be negative");
        }
    }

    public record EntryContext(
            String actorId,
            String actorTeamId,
            int actorHp,
            GridCoord coordinate,
            Set<String> naturewalkTerrains
    ) {
        public EntryContext {
            actorId = safe(actorId);
            actorTeamId = safe(actorTeamId);
            naturewalkTerrains = normalizeSet(naturewalkTerrains);
            if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
            if (actorHp < 0) throw new IllegalArgumentException("actorHp cannot be negative");
            if (coordinate == null) throw new IllegalArgumentException("coordinate is required");
        }
    }

    public record Trigger(
            String actorId,
            String trapKey,
            String trapName,
            String sourceId,
            Set<String> terrains,
            GridCoord coordinate,
            int targetHp
    ) {}

    public record Result(List<Trigger> triggers, Set<String> consumedTrapKeys) {
        public Result {
            triggers = List.copyOf(triggers == null ? List.of() : triggers);
            consumedTrapKeys = Set.copyOf(consumedTrapKeys == null ? Set.of() : consumedTrapKeys);
        }
    }

    public static Result resolve(EntryContext context, List<TrapLayer> traps) {
        if (context == null) throw new IllegalArgumentException("entry context is required");
        List<Trigger> triggers = new ArrayList<>();
        LinkedHashSet<String> consumed = new LinkedHashSet<>();

        for (TrapLayer trap : traps == null ? List.<TrapLayer>of() : traps) {
            if (trap == null || trap.layers() <= 0) continue;
            if (!trap.sourceId().isBlank()
                    && !trap.sourceTeamId().isBlank()
                    && trap.sourceTeamId().equals(context.actorTeamId())) {
                continue;
            }
            if (!trap.terrains().isEmpty() && overlaps(trap.terrains(), context.naturewalkTerrains())) {
                continue;
            }
            triggers.add(new Trigger(
                    context.actorId(),
                    trap.trapKey(),
                    trap.trapName().isBlank() ? trap.trapKey() : trap.trapName(),
                    trap.sourceId(),
                    trap.terrains(),
                    context.coordinate(),
                    context.actorHp()
            ));
            // Python _consume_trap removes the whole key after one entry trigger.
            consumed.add(trap.trapKey());
        }
        return new Result(triggers, consumed);
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
