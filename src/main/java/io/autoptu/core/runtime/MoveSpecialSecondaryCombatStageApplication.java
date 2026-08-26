package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.MoveSpecialSecondaryCombatStageResolution;
import io.autoptu.core.model.CombatStageStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Runtime composition boundary for generic text-driven secondary Combat Stage effects.
 *
 * <p>Every request is validated before any mutation. All seven PTU Combat Stages
 * flow through {@link CombatStageMutationService}, preserving prevention, reflection,
 * clamping and post-apply reactions without a side path for Accuracy or Evasion.</p>
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
                    combatStageStat(request.stat())
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

    private static CombatStageStat combatStageStat(String raw) {
        String normalized = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "atk" -> CombatStageStat.ATK;
            case "def" -> CombatStageStat.DEF;
            case "spatk" -> CombatStageStat.SPATK;
            case "spdef" -> CombatStageStat.SPDEF;
            case "spd" -> CombatStageStat.SPD;
            case "accuracy" -> CombatStageStat.ACCURACY;
            case "evasion" -> CombatStageStat.EVASION;
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
            CombatStageStat stat
    ) {}

    record AppliedRequest(
            MoveSpecialSecondaryCombatStageResolution.StageRequest request,
            String targetId,
            CombatStageStat stat,
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
