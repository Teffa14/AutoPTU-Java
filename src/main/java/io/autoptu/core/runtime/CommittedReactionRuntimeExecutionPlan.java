package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.hook.PreDamageReactionHookRegistry;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.random.PythonRandom;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validated server-owned inputs for the final committed-reaction handoff into BattleRuntime.
 *
 * <p>This projection performs no PTU resolution and consumes no action, frequency, or RNG. It
 * exists so the final runtime seam can delegate once to the ordinary move pipeline without
 * adapters rebuilding hook registries or substituting identities after commitment.</p>
 */
public record CommittedReactionRuntimeExecutionPlan(
        MoveChoice choice,
        MoveOption move,
        String actorSize,
        String targetSize,
        Set<GridCoord> lineOfSightBlockers,
        String source,
        PythonRandom rng,
        MoveResolutionInput input,
        List<? extends BattleEvent> preResolutionEvents,
        MoveSpecialHookRegistry moveSpecialHookRegistry,
        PreDamageReactionHookRegistry preDamageReactionHooks,
        PostDamageHookRegistry postDamageHooks,
        MoveCombatProfile effectiveMetadata,
        BattleRuntimeDependencies dependencies
) {
    public CommittedReactionRuntimeExecutionPlan {
        Objects.requireNonNull(choice, "choice");
        Objects.requireNonNull(move, "move");
        actorSize = actorSize == null ? "" : actorSize;
        targetSize = targetSize == null ? "" : targetSize;
        lineOfSightBlockers = lineOfSightBlockers == null ? Set.of() : Set.copyOf(lineOfSightBlockers);
        source = source == null ? "" : source;
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(input, "input");
        preResolutionEvents = preResolutionEvents == null ? List.of() : List.copyOf(preResolutionEvents);
        Objects.requireNonNull(moveSpecialHookRegistry, "moveSpecialHookRegistry");
        Objects.requireNonNull(preDamageReactionHooks, "preDamageReactionHooks");
        Objects.requireNonNull(postDamageHooks, "postDamageHooks");
        Objects.requireNonNull(effectiveMetadata, "effectiveMetadata");
        Objects.requireNonNull(dependencies, "dependencies");
    }

    /**
     * Carries the committed-reaction identity into the authoritative move resolver. The context is
     * the single owner of declaration-validation, action-spend, move-frequency, and PRE-reaction policy.
     */
    MoveRuntimeExecutionContext executionContext() {
        return MoveRuntimeExecutionContext.committedReaction();
    }

    /**
     * Transitional action-spend projection retained for callers that have not yet moved to
     * executionContext(). The action window already owns the committed reaction cost. Move-frequency
     * ownership remains an independent execution-context decision and must not be inferred here.
     */
    public boolean spendOrdinaryMoveResources() {
        return executionContext().ownsActionSpend();
    }

    /**
     * Transitional projection retained for callers that have not yet moved to executionContext().
     * Participant and move identity validation is completed by prepare() before this plan exists.
     */
    public boolean declarationAlreadyValidated() {
        return executionContext().declarationAlreadyValidated();
    }

    public static CommittedReactionRuntimeExecutionPlan prepare(
            BattleRuntimeState state,
            CommittedReactionMoveExecution execution
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(execution, "execution");
        CommittedReactionRuntimeExecutionGuard.requireBoundParticipants(state, execution);

        BattleRuntimeDependencies dependencies = execution.dependencies();
        return new CommittedReactionRuntimeExecutionPlan(
                execution.binding().choice(),
                execution.binding().move(),
                execution.actorSize(),
                execution.targetSize(),
                execution.lineOfSightBlockers(),
                execution.source(),
                execution.rng(),
                execution.input(),
                execution.preResolutionEvents(),
                execution.moveSpecialHookRegistry(),
                dependencies.preDamageReactionHooks(),
                dependencies.postDamageHooks(),
                execution.effectiveMetadata(),
                dependencies
        );
    }
}
