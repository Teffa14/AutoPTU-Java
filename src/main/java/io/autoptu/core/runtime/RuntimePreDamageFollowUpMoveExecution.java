package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.hook.BuiltinPostDamageHooks;
import io.autoptu.core.hook.BuiltinPreDamageReactionHooks;
import io.autoptu.core.hook.PreDamageFollowUpExecutionPolicy;
import io.autoptu.core.hook.PreDamageFollowUpMoveExecutor;
import io.autoptu.core.hook.PreDamageFollowUpMoveRequest;
import io.autoptu.core.hook.PreDamageFollowUpMoveResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.random.PythonRandom;

/**
 * Runtime-owned synchronous re-entry for PRE-damage reactions such as Sway and Magic Coat.
 *
 * <p>The original move is reused while attacker/target identity is replaced by the hook request.
 * The same battle RNG is consumed, PRE-damage reactions remain enabled, and the nested move does
 * not spend action economy or move frequency a second time.</p>
 */
final class RuntimePreDamageFollowUpMoveExecution {
    private RuntimePreDamageFollowUpMoveExecution() {
    }

    static PreDamageFollowUpMoveExecutor executor(
            BattleRuntimeState state,
            MoveOption originalMove,
            String source,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (originalMove == null) throw new IllegalArgumentException("originalMove is required");
        if (rng == null) throw new IllegalArgumentException("rng is required");
        if (legacyInput == null) throw new IllegalArgumentException("legacyInput is required");
        PreDamageFollowUpExecutionPolicy policy = PreDamageFollowUpExecutionPolicy.pythonParity();
        if (!policy.synchronous() || !policy.reuseOriginalMove() || !policy.runPreDamageReactions()
                || policy.spendAction() || policy.spendMoveFrequency()) {
            throw new IllegalStateException("unsupported PRE-damage follow-up execution policy");
        }
        return request -> execute(
                state,
                originalMove,
                source,
                rng,
                legacyInput,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage,
                request
        );
    }

    private static PreDamageFollowUpMoveResult execute(
            BattleRuntimeState state,
            MoveOption originalMove,
            String source,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage,
            PreDamageFollowUpMoveRequest request
    ) {
        if (request == null) throw new IllegalArgumentException("request is required");
        RuntimeCombatantState target = state.requireCombatant(request.targetId());
        GridCoord targetPosition = request.targetPosition() == null
                ? target.position()
                : request.targetPosition();
        MoveChoice choice = new MoveChoice(
                request.attackerId(),
                originalMove.moveId(),
                ChoiceTargetMode.COMBATANT,
                request.targetId(),
                targetPosition,
                originalMove.actionType()
        );
        RuntimeAuthoritativeMovePreparation.Prepared prepared = RuntimeAuthoritativeMovePreparation.prepare(
                state,
                choice,
                originalMove,
                legacyInput,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage
        );
        AppliedActionResult result = BattleRuntime.applyAuthoritativeAreaMoveTarget(
                state,
                choice,
                originalMove,
                targetPosition,
                source,
                rng,
                prepared.input(),
                prepared.preResolutionEvents(),
                BuiltinPreDamageReactionHooks.registry(),
                BuiltinPostDamageHooks.standardRegistry(),
                prepared.effectiveMetadata()
        );
        return new PreDamageFollowUpMoveResult(result.events());
    }
}
