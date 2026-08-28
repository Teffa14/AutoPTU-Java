package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.PreResolutionTargetContext;
import io.autoptu.core.hook.PreResolutionTargetHookRegistry;
import io.autoptu.core.hook.PreResolutionTargetResult;

import java.util.List;

/**
 * Runtime-only bridge from ordered pre-resolution target hooks to the effective move choice.
 *
 * <p>This boundary is intentionally package-private. The authoritative runtime calls it only
 * after the originally declared combatant move has been revalidated and before accuracy or
 * damage RNG is consumed. A replacement target is looked up in {@link BattleRuntimeState} and
 * its current authoritative position becomes the effective target anchor. Adapters therefore
 * cannot inject either a target id or a renderer-owned position into the PTU move pipeline.</p>
 */
public final class RuntimePreResolutionTargetApplication {
    private RuntimePreResolutionTargetApplication() {}

    public record Result(MoveChoice effectiveChoice, List<BattleEvent> events) {
        public Result {
            if (effectiveChoice == null) throw new IllegalArgumentException("effectiveChoice is required");
            events = events == null ? List.of() : List.copyOf(events);
            if (events.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("events cannot contain null");
            }
        }
    }

    static Result resolve(
            BattleRuntimeState state,
            MoveChoice declaredChoice,
            PreResolutionTargetHookRegistry registry
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (declaredChoice == null) throw new IllegalArgumentException("declaredChoice is required");
        if (registry == null) throw new IllegalArgumentException("registry is required");
        if (declaredChoice.targetMode() != ChoiceTargetMode.COMBATANT || declaredChoice.targetId().isBlank()) {
            throw new IllegalArgumentException("pre-resolution target replacement requires a combatant target");
        }

        state.requireCombatant(declaredChoice.actorId());
        RuntimeCombatantState originalTarget = state.requireCombatant(declaredChoice.targetId());
        PreResolutionTargetResult resolved = registry.resolve(new PreResolutionTargetContext(
                state,
                declaredChoice.actorId(),
                declaredChoice.moveId(),
                declaredChoice.targetId(),
                originalTarget.position()
        ));
        RuntimeCombatantState effectiveTarget = state.requireCombatant(resolved.targetId());
        MoveChoice effectiveChoice = new MoveChoice(
                declaredChoice.actorId(),
                declaredChoice.moveId(),
                declaredChoice.targetMode(),
                effectiveTarget.combatantId(),
                effectiveTarget.position(),
                declaredChoice.actionType()
        );
        return new Result(effectiveChoice, resolved.events());
    }
}
