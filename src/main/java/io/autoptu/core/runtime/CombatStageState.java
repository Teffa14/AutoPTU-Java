package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStat;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mutable server-owned PTU combat stages for one combatant.
 *
 * Stage mutation is battle state. Minecraft/Cobblemon may render these values but
 * must never supply them while a move, ability, status, item, or Trainer Feature
 * is resolving.
 */
public final class CombatStageState {
    private final EnumMap<CombatStat, Integer> stages = new EnumMap<>(CombatStat.class);

    public CombatStageState() {
        this(Map.of());
    }

    public CombatStageState(Map<CombatStat, Integer> initialStages) {
        for (CombatStat stat : CombatStat.values()) {
            stages.put(stat, clamp(initialStages == null ? 0 : initialStages.getOrDefault(stat, 0)));
        }
    }

    public int get(CombatStat stat) {
        if (stat == null) throw new IllegalArgumentException("combat stat is required");
        return stages.get(stat);
    }

    public int set(CombatStat stat, int value) {
        if (stat == null) throw new IllegalArgumentException("combat stat is required");
        int clamped = clamp(value);
        stages.put(stat, clamped);
        return clamped;
    }

    /** Adjust one stage and return the final clamped value. */
    public int adjust(CombatStat stat, int delta) {
        if (stat == null) throw new IllegalArgumentException("combat stat is required");
        return set(stat, get(stat) + delta);
    }

    public Map<CombatStat, Integer> snapshot() {
        return Map.copyOf(stages);
    }

    private static int clamp(int value) {
        return Math.max(-6, Math.min(6, value));
    }
}
