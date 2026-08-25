package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.MoveSpecialEndActionResolution;
import io.autoptu.core.hook.MoveSpecialHookRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Package-private action-wide move-special finalization seam.
 *
 * <p>Python resolves every target first, keeps only the last target result, sums applied damage,
 * then dispatches END_ACTION exactly once. This seam composes already-authoritative per-target
 * POST_DAMAGE results without exposing the mutable action accumulator to Minecraft/Cobblemon.</p>
 */
final class MoveSpecialActionFinalization {
    private MoveSpecialActionFinalization() {}

    static MultiTargetAppliedActionResult finish(
            MoveSpecialHookRegistry registry,
            BattleRuntimeState state,
            String attackerId,
            String moveName,
            String moveCategory,
            List<String> targetIds,
            List<RuntimeMoveSpecialPostDamageApplication.Result> targetResults
    ) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(state, "state");
        List<String> orderedTargetIds = targetIds == null ? List.of() : List.copyOf(targetIds);
        List<RuntimeMoveSpecialPostDamageApplication.Result> orderedResults =
                targetResults == null ? List.of() : List.copyOf(targetResults);
        if (orderedTargetIds.size() != orderedResults.size()) {
            throw new IllegalArgumentException("targetIds and targetResults must have matching sizes");
        }

        MoveSpecialActionAccumulator accumulator = new MoveSpecialActionAccumulator();
        ArrayList<BattleEvent> orderedEvents = new ArrayList<>();
        for (RuntimeMoveSpecialPostDamageApplication.Result targetResult : orderedResults) {
            RuntimeMoveSpecialPostDamageApplication.Result required =
                    Objects.requireNonNull(targetResult, "targetResult");
            orderedEvents.addAll(required.events());
            accumulator.recordTarget(required);
        }

        MoveSpecialEndActionResolution.Result endAction = accumulator.finish(
                registry,
                state,
                attackerId,
                moveName,
                moveCategory
        );
        orderedEvents.addAll(endAction.events());
        return new MultiTargetAppliedActionResult(orderedEvents, orderedTargetIds);
    }
}
