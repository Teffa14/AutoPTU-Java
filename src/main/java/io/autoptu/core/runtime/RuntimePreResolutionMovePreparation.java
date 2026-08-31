package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.PreResolutionTargetHookRegistry;
import io.autoptu.core.model.MoveCombatProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-owned composition point between pre-resolution target replacement and move preparation.
 *
 * <p>The declared choice has already passed its original legality validation before this boundary
 * is used. Target hooks may then replace the defender. All defender-bound move inputs are rebuilt
 * from the resulting authoritative combatant before accuracy RNG is consumed. This prevents a
 * redirected attack from retaining the original target's evasion, defense, type interaction or
 * other target-scoped preparation.</p>
 */
final class RuntimePreResolutionMovePreparation {
    private RuntimePreResolutionMovePreparation() {}

    static Result prepare(
            BattleRuntimeState state,
            MoveChoice declaredChoice,
            MoveOption move,
            MoveResolutionInput legacyInput,
            PreResolutionTargetHookRegistry targetRegistry,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (declaredChoice == null) throw new IllegalArgumentException("declaredChoice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (legacyInput == null) throw new IllegalArgumentException("legacyInput is required");
        if (targetRegistry == null) throw new IllegalArgumentException("targetRegistry is required");

        RuntimePreResolutionTargetApplication.Result targetResult =
                RuntimePreResolutionTargetApplication.resolve(state, declaredChoice, move, targetRegistry);
        MoveChoice effectiveChoice = targetResult.effectiveChoice();
        RuntimeAuthoritativeMovePreparation.Prepared prepared = RuntimeAuthoritativeMovePreparation.prepare(
                state,
                effectiveChoice,
                move,
                legacyInput,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage
        );

        ArrayList<BattleEvent> events = new ArrayList<>(targetResult.events());
        events.addAll(prepared.preResolutionEvents());
        return new Result(
                effectiveChoice,
                prepared.input(),
                List.copyOf(events),
                prepared.effectiveMetadata()
        );
    }

    record Result(
            MoveChoice effectiveChoice,
            MoveResolutionInput input,
            List<BattleEvent> preResolutionEvents,
            MoveCombatProfile effectiveMetadata
    ) {
        Result {
            if (effectiveChoice == null) throw new IllegalArgumentException("effectiveChoice is required");
            if (input == null) throw new IllegalArgumentException("input is required");
            preResolutionEvents = preResolutionEvents == null ? List.of() : List.copyOf(preResolutionEvents);
            if (preResolutionEvents.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("preResolutionEvents cannot contain null");
            }
            if (effectiveMetadata == null) throw new IllegalArgumentException("effectiveMetadata is required");
        }
    }
}
