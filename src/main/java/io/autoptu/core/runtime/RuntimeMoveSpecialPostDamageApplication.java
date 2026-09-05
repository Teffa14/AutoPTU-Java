package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.MoveSpecialPostDamageResolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime composition seam for Python move-special POST_DAMAGE dispatch.
 *
 * <p>The ordinary move outcome must already have committed HP and damage history before this
 * seam is called. POST_DAMAGE therefore observes the applied state and the actual HP loss while
 * preserving the shared mutable move-special result for later phases. Mutating the result during
 * POST_DAMAGE never rewrites HP or history retroactively.</p>
 */
public final class RuntimeMoveSpecialPostDamageApplication {
    private RuntimeMoveSpecialPostDamageApplication() {}

    public static Result resolveAfterAppliedOutcome(
            MoveSpecialHookRegistry registry,
            BattleRuntimeState state,
            MoveChoice choice,
            String moveName,
            String moveCategory,
            Map<String, ?> resultSnapshot,
            boolean hit,
            int targetHpBefore,
            AppliedActionResult resolvedOutcome
    ) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(resolvedOutcome, "resolvedOutcome");
        if (targetHpBefore < 0) throw new IllegalArgumentException("targetHpBefore must be non-negative");

        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        int damageDealt = hit ? Math.max(0, targetHpBefore - target.hp()) : 0;

        LinkedHashMap<String, Object> prior = new LinkedHashMap<>();
        if (resultSnapshot != null) resultSnapshot.forEach(prior::put);

        if (!hit || registry.isEmpty()) {
            return new Result(resolvedOutcome, Collections.unmodifiableMap(prior), damageDealt);
        }

        MoveSpecialPostDamageResolution.Result special = MoveSpecialPostDamageResolution.resolve(
                registry,
                state,
                choice.actorId(),
                choice.targetId(),
                moveName,
                moveCategory,
                prior,
                true,
                damageDealt
        );

        ArrayList<BattleEvent> ordered = new ArrayList<>(
                special.events().size() + resolvedOutcome.events().size());
        ordered.addAll(special.events());
        ordered.addAll(resolvedOutcome.events());

        return new Result(
                new AppliedActionResult(ordered),
                special.resultSnapshot(),
                damageDealt
        );
    }

    public record Result(
            AppliedActionResult actionResult,
            Map<String, Object> resultSnapshot,
            int damageDealt
    ) {
        public Result {
            actionResult = Objects.requireNonNull(actionResult, "actionResult");
            resultSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(
                    resultSnapshot == null ? Map.of() : resultSnapshot));
            damageDealt = Math.max(0, damageDealt);
        }

        /**
         * Package-private bridge into the action-wide move-special transport. Keeping the
         * conversion beside POST_DAMAGE prevents BattleRuntime callers from reconstructing the
         * snapshot or applied-damage bookkeeping after the result has already been resolved.
         */
        MoveSpecialTargetResult targetResult() {
            return MoveSpecialTargetResult.from(this);
        }

        public List<BattleEvent> events() {
            return actionResult.events();
        }
    }
}
