package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.Targeting;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Generic spatial ability blockers for status application, derived from the pinned Python oracle. */
public final class SpatialStatusAbilityPreventionResolution {
    private static final List<Rule> RULES = List.of(
            new Rule("Aroma Veil", "Aroma Veil [Errata]", 3, 1, Set.of("confused", "enraged", "suppressed")),
            new Rule("Pastel Veil", "", 3, 3, Set.of("poison", "poisoned", "badly poisoned")),
            new Rule("Sweet Veil", "", 3, 3, Set.of("sleep", "asleep"))
    );

    private SpatialStatusAbilityPreventionResolution() {
    }

    public static Optional<Blocker> findBlocker(BattleRuntimeState state, String targetId, String status) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState target = state.requireCombatant(targetId);
        if (target.abilitiesSuppressed()) return Optional.empty();

        String statusKey = normalize(status);
        if (statusKey.isEmpty()) return Optional.empty();
        GridCoord targetPosition = target.position();

        for (Rule rule : RULES) {
            if (!rule.statuses().contains(statusKey)) continue;
            for (String candidateId : state.combatantIds()) {
                RuntimeCombatantState candidate = state.requireCombatant(candidateId);
                if (candidate.hp() <= 0 || !state.isActive(candidateId)) continue;

                boolean errata = !rule.errataAbility().isBlank() && candidate.hasAbilityExact(rule.errataAbility());
                if (!errata && !candidate.hasAbilityExact(rule.baseAbility())) continue;

                GridCoord sourcePosition = candidate.position();
                if (sourcePosition == null || targetPosition == null) {
                    return Optional.of(new Blocker(candidateId, rule.baseAbility()));
                }
                int radius = errata ? rule.errataRadius() : rule.baseRadius();
                int distance = Targeting.footprintDistance(
                        sourcePosition,
                        state.geometry(candidateId).sizeLabel(),
                        targetPosition,
                        "Medium"
                );
                if (distance <= radius) {
                    return Optional.of(new Blocker(candidateId, rule.baseAbility()));
                }
            }
        }
        return Optional.empty();
    }

    public record Blocker(String combatantId, String abilityName) {
        public Blocker {
            if (combatantId == null || combatantId.isBlank()) throw new IllegalArgumentException("combatantId is required");
            if (abilityName == null || abilityName.isBlank()) throw new IllegalArgumentException("abilityName is required");
            combatantId = combatantId.strip();
            abilityName = abilityName.strip();
        }
    }

    private record Rule(String baseAbility, String errataAbility, int baseRadius, int errataRadius, Set<String> statuses) {
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
