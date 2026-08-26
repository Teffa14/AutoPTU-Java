package io.autoptu.core.rules;

import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.CombatStat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Declarative target-owned ability blockers evaluated before a Combat Stage drop is committed.
 * Frozen from the pinned Python _apply_combat_stage ordering.
 */
public final class CombatStageAbilityPreventionResolution {
    private static final List<Rule> RULES = List.of(
            new Rule("Big Pecks", CombatStageStat.DEF, false),
            new Rule("Hyper Cutter", CombatStageStat.ATK, false),
            new Rule("Clear Body", null, true),
            new Rule("Full Metal Body", null, true),
            new Rule("White Smoke", null, true)
    );

    private CombatStageAbilityPreventionResolution() {}

    /** Compatibility boundary for existing five-stat callers. */
    public static Optional<String> blockingAbility(
            List<String> abilities,
            CombatStat stat,
            int requestedDelta,
            boolean externalSource,
            boolean abilitiesSuppressed
    ) {
        return blockingAbility(
                abilities,
                CombatStageStat.fromCombatStat(stat),
                requestedDelta,
                externalSource,
                abilitiesSuppressed
        );
    }

    public static Optional<String> blockingAbility(
            List<String> abilities,
            CombatStageStat stat,
            int requestedDelta,
            boolean externalSource,
            boolean abilitiesSuppressed
    ) {
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(stat, "stat");
        if (abilitiesSuppressed || requestedDelta >= 0) return Optional.empty();

        for (Rule rule : RULES) {
            if (rule.stat() != null && rule.stat() != stat) continue;
            if (rule.externalOnly() && !externalSource) continue;
            if (AbilityIdentityResolution.matchesRegistration(abilities, rule.ability())) {
                return Optional.of(rule.ability());
            }
        }
        return Optional.empty();
    }

    private record Rule(String ability, CombatStageStat stat, boolean externalOnly) {}
}
