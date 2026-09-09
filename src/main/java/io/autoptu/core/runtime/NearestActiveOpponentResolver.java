package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Resolves the nearest eligible active opponent with Python-compatible deterministic ordering.
 *
 * Callers own content-family eligibility (for example, Pokemon-only candidates for Impostor).
 * This resolver owns only the reusable battle-state rules: active/alive filtering, opposing-team
 * filtering, Chebyshev distance, then combatant id as the stable tie-break.
 */
public final class NearestActiveOpponentResolver {
    private NearestActiveOpponentResolver() {
    }

    public static Optional<Selection> resolve(
            BattleRuntimeState battle,
            String actorId,
            Collection<String> eligibleCandidateIds
    ) {
        if (battle == null) {
            throw new IllegalArgumentException("battle state is required");
        }
        RuntimeCombatantState actor = battle.requireCombatant(actorId);
        GridCoord actorPosition = actor.position();
        String actorTeam = battle.teamId(actorId);

        if (!battle.isActive(actorId) || actor.hp() <= 0 || actorPosition == null) {
            return Optional.empty();
        }

        return (eligibleCandidateIds == null ? java.util.List.<String>of() : eligibleCandidateIds)
                .stream()
                .filter(candidateId -> candidateId != null && !candidateId.isBlank())
                .map(String::strip)
                .distinct()
                .filter(candidateId -> !candidateId.equals(actorId))
                .filter(battle.combatants()::containsKey)
                .filter(battle::isActive)
                .map(battle::requireCombatant)
                .filter(candidate -> candidate.hp() > 0)
                .filter(candidate -> !battle.teamId(candidate.combatantId()).equals(actorTeam))
                .filter(candidate -> candidate.position() != null)
                .map(candidate -> new Selection(
                        candidate.combatantId(),
                        chebyshevDistance(actorPosition, candidate.position())
                ))
                .min(Comparator.comparingInt(Selection::distance)
                        .thenComparing(Selection::targetId));
    }

    static int chebyshevDistance(GridCoord first, GridCoord second) {
        return Math.max(
                Math.abs(first.x() - second.x()),
                Math.abs(first.y() - second.y())
        );
    }

    public record Selection(String targetId, int distance) {
    }
}
