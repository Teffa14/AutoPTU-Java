package io.autoptu.core.model;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * BattleState-independent stat snapshot.
 *
 * Ability/item/trainer-feature hooks resolve into modifiers and flags before the
 * pure stat resolver runs. This keeps the formula testable without depending on
 * the Python monolith or Minecraft state.
 */
public final class CombatantStatProfile {
    private final EnumMap<CombatStat, Integer> baseStats;
    private final EnumMap<CombatStat, Integer> stages;
    private final EnumMap<CombatStat, StatModifier> modifiers;
    private final Set<StatFlag> flags;
    private final int intrinsicAccuracyCs;

    public CombatantStatProfile(
            Map<CombatStat, Integer> baseStats,
            Map<CombatStat, Integer> stages,
            Map<CombatStat, StatModifier> modifiers,
            Set<StatFlag> flags
    ) {
        this(baseStats, stages, modifiers, flags, 0);
    }

    /**
     * Full content-owned stat profile including Python PokemonSpec.accuracy_cs.
     * The intrinsic value remains separate from mutable battle Combat Stages and
     * runtime bonuses until effective Accuracy is projected for a move.
     */
    public CombatantStatProfile(
            Map<CombatStat, Integer> baseStats,
            Map<CombatStat, Integer> stages,
            Map<CombatStat, StatModifier> modifiers,
            Set<StatFlag> flags,
            int intrinsicAccuracyCs
    ) {
        this.baseStats = new EnumMap<>(CombatStat.class);
        this.stages = new EnumMap<>(CombatStat.class);
        this.modifiers = new EnumMap<>(CombatStat.class);
        for (CombatStat stat : CombatStat.values()) {
            this.baseStats.put(stat, Math.max(1, baseStats == null ? 1 : baseStats.getOrDefault(stat, 1)));
            this.stages.put(stat, stages == null ? 0 : stages.getOrDefault(stat, 0));
            this.modifiers.put(stat, modifiers == null ? StatModifier.identity() : modifiers.getOrDefault(stat, StatModifier.identity()));
        }
        this.flags = flags == null ? Set.of() : Set.copyOf(flags);
        this.intrinsicAccuracyCs = intrinsicAccuracyCs;
    }

    public int base(CombatStat stat) {
        return baseStats.get(stat);
    }

    public int stage(CombatStat stat) {
        return stages.get(stat);
    }

    /** Immutable combat-stage snapshot for authoritative runtime materialization. */
    public Map<CombatStat, Integer> stages() {
        return Map.copyOf(stages);
    }

    /** Python PokemonSpec.accuracy_cs stored as immutable trusted content. */
    public int intrinsicAccuracyCs() {
        return intrinsicAccuracyCs;
    }

    public StatModifier modifier(CombatStat stat) {
        return modifiers.get(stat);
    }

    public boolean has(StatFlag flag) {
        return flags.contains(flag);
    }

    /** Rebind this pure profile to the current server-owned combat stages. */
    public CombatantStatProfile withStages(Map<CombatStat, Integer> nextStages) {
        return new CombatantStatProfile(baseStats, nextStages, modifiers, flags, intrinsicAccuracyCs);
    }

    public CombatantStatProfile withFlag(StatFlag flag, boolean enabled) {
        if (flag == null || flags.contains(flag) == enabled) {
            return this;
        }
        Set<StatFlag> nextFlags = new HashSet<>(flags);
        if (enabled) {
            nextFlags.add(flag);
        } else {
            nextFlags.remove(flag);
        }
        return new CombatantStatProfile(baseStats, stages, modifiers, nextFlags, intrinsicAccuracyCs);
    }
}
