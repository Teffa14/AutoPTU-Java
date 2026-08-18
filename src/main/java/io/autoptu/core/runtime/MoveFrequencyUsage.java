package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.rules.MoveFrequency;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable server-owned usage counters for PTU move frequency limits. */
public final class MoveFrequencyUsage {
    private final LinkedHashMap<String, Integer> battleUses = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> roundUses = new LinkedHashMap<>();

    public boolean available(MoveOption move) {
        if (move == null) throw new IllegalArgumentException("move is required");
        return MoveFrequency.available(
                move.frequency(),
                battleUses.getOrDefault(move.moveId(), 0),
                roundUses.getOrDefault(move.moveId(), 0)
        );
    }

    public void recordUse(MoveOption move) {
        if (move == null) throw new IllegalArgumentException("move is required");
        MoveFrequency.parse(move.frequency()).ifPresent(definition -> {
            LinkedHashMap<String, Integer> usage = definition.scope() == MoveFrequency.Scope.ROUND
                    ? roundUses
                    : battleUses;
            usage.merge(move.moveId(), 1, Integer::sum);
        });
    }

    public int battleUses(String moveId) {
        return battleUses.getOrDefault(moveId, 0);
    }

    public int roundUses(String moveId) {
        return roundUses.getOrDefault(moveId, 0);
    }

    public Map<String, Integer> battleUsage() {
        return Map.copyOf(battleUses);
    }

    public Map<String, Integer> roundUsage() {
        return Map.copyOf(roundUses);
    }

    public void resetRound() {
        roundUses.clear();
    }
}
