package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.MoveSpecialSecondaryCombatStageResolution;
import io.autoptu.core.model.CombatStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Runtime composition boundary for generic text-driven secondary Combat Stage effects.
 *
 * <p>Every request is validated before any mutation. Supported PTU stat changes then
 * flow through {@link CombatStageMutationService}, preserving prevention, reflection,
 * clamping and post-apply reactions. Accuracy/Evasion remain fail-closed until those
 * stages participate in the same canonical mutation model.</p>
 */
final class MoveSpecialSecondaryCombatStageApplication {
    private MoveSpecialSecondaryCombatStageApplication() {}

    static Result apply(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveId,
            List<MoveSpecialSecondaryCombatStageResolution.StageRequest> requests
    ) {
        Objects.requireNonNull(state, "state");
        String canonicalAttackerId = required(attackerId, "attackerId");
        String canonicalDefenderId = required(defenderId, "defenderId");
        String canonicalMoveId = required(moveId, "moveId");
        state.requireCombatant(canonicalAttackerId);
        state.requireCombatant(canonicalDefenderId);

        List<MoveSpecialSecondaryCombatStageResolution.StageRequest> safeRequests =
                List.copyOf(requests == null ? List.of() : requests);
        ArrayList<ResolvedRequest> resolved = new ArrayList<>(safeRequests.size());
        for (MoveSpecialSecondaryCombatStageResolution.StageRequest request : safeRequests) {
            if (request == null) {
                throw new IllegalArgumentException("stage request is required");
            }
            resolved.add(new ResolvedRequest(
                    request,
                    request.target() == MoveSpecialSecondaryCombatStageResolution.TargetRole.USER
                            ? canonicalAttackerId : canonicalDefenderId,
                    combatStat(request.stat())
            ));
        }

        CombatStageMutationService mutationService = CombatStageMutationService.authoritative(state);
        ArrayList<AppliedRequest> applications = new ArrayList<>(resolved.size());
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (ResolvedRequest request : resolved) {
            CombatStageMutationResult mutation = mutationService.apply(
                    canonicalAttackerId,
                    request.targetId(),
                    canonicalMoveId,
                    request.stat(),
                    request.request().delta(),
                    "move_special_secondary"
            );
            applications.add(new AppliedRequest(request.request(), request.targetId(), request.stat(), mutation));
            events.addAll(mutation.events());
        }
        return new Result(applications, events);
    }

    private static CombatStat combatStat(String raw) {
        String normalized = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "atk" -> CombatStat.ATK;
            case "def" -> CombatStat.DEF;
            case "spatk" -> CombatStat.SPATK;
            case "spdef" -> CombatStat.SPDEF;
            case "spd" -> CombatStat.SPD;
            case "accuracy", "evasion" -> throw new UnsupportedOperationException(
                    normalized + " Combat Stage is not yet represented by CombatStageMutationService"
            );
            default -> throw new IllegalArgumentException("unsupported Combat Stage stat: " + raw);
        };
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.strip();
    }

    private record ResolvedRequest(
            MoveSpecialSecondaryCombatStageResolution.StageRequest request,
            String targetId,
            CombatStat stat
    ) {}

    record AppliedRequest(
            MoveSpecialSecondaryCombatStageResolution.StageRequest request,
            String targetId,
            CombatStat stat,
            CombatStageMutationResult mutation
    ) {
        AppliedRequest {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(stat, "stat");
            Objects.requireNonNull(mutation, "mutation");
        }
    }

    record Result(List<AppliedRequest> applications, List<BattleEvent> events) {
        Result {
            applications = List.copyOf(applications == null ? List.of() : applications);
            events = List.copyOf(events == null ? List.of() : events);
        }
    }
}
