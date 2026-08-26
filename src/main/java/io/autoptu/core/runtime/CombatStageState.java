package io.autoptu.core.runtime;

import io.autoptu.core.model.CombatStageStat;
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
    private final EnumMap<CombatStageStat, Integer> stages = new EnumMap<>(CombatStageStat.class);

    public CombatStageState() {
        this(Map.of());
    }

    /** Compatibility constructor for the five core CombatStat stages. */
    public CombatStageState(Map<CombatStat, Integer> initialStages) {
        for (CombatStageStat stat : CombatStageStat.values()) {
            stages.put(stat, 0);
        }
        if (initialStages != null) {
            for (CombatStat stat : CombatStat.values()) {
                stages.put(
                        CombatStageStat.fromCombatStat(stat),
                        clamp(initialStages.getOrDefault(stat, 0))
                );
            }
        }
    }

    public int get(CombatStageStat stat) {
        if (stat == null) throw new IllegalArgumentException("combat stage stat is required");
        return stages.get(stat);
    }

    public int set(CombatStageStat stat, int value) {
        if (stat == null) throw new IllegalArgumentException("combat stage stat is required");
        int clamped = clamp(value);
        stages.put(stat, clamped);
        return clamped;
    }

    /** Adjust one canonical stage and return the final clamped value. */
    public int adjust(CombatStageStat stat, int delta) {
        if (stat == null) throw new IllegalArgumentException("combat stage stat is required");
        return set(stat, get(stat) + delta);
    }

    /** Compatibility access for deterministic arithmetic that still uses CombatStat. */
    public int get(CombatStat stat) {
        return get(CombatStageStat.fromCombatStat(stat));
    }

    /** Compatibility mutation for callers that still use the five core CombatStat values. */
    public int set(CombatStat stat, int value) {
        return set(CombatStageStat.fromCombatStat(stat), value);
    }

    /** Compatibility adjustment for callers that still use the five core CombatStat values. */
    public int adjust(CombatStat stat, int delta) {
        return adjust(CombatStageStat.fromCombatStat(stat), delta);
    }

    /**
     * Legacy five-stat snapshot used by CombatantStatProfile arithmetic.
     * Accuracy and Evasion are deliberately excluded because they are not base stats.
     */
    public Map<CombatStat, Integer> snapshot() {
        EnumMap<CombatStat, Integer> result = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) {
            result.put(stat, get(stat));
        }
        return Map.copyOf(result);
    }

    /** Complete seven-stage snapshot for battle state, persistence, replay, and hooks. */
    public Map<CombatStageStat, Integer> fullSnapshot() {
        return Map.copyOf(stages);
    }

    private static int clamp(int value) {
        return Math.max(-6, Math.min(6, value));
    }
}
