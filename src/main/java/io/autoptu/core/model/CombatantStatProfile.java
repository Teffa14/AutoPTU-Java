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

    public CombatantStatProfile(
            Map<CombatStat, Integer> baseStats,
            Map<CombatStat, Integer> stages,
            Map<CombatStat, StatModifier> modifiers,
            Set<StatFlag> flags
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
    }

    public int base(CombatStat stat) {
        return baseStats.get(stat);
    }

    public int stage(CombatStat stat) {
        return stages.get(stat);
    }

    public StatModifier modifier(CombatStat stat) {
        return modifiers.get(stat);
    }

    public boolean has(StatFlag flag) {
        return flags.contains(flag);
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
        return new CombatantStatProfile(baseStats, stages, modifiers, nextFlags);
    }
}
