package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.Accuracy;

import java.util.List;
import java.util.Set;

/**
 * Authoritative zero-damage execution boundary for non-damaging Status moves.
 *
 * <p>This intentionally freezes only the pinned Python Status branch: ordinary accuracy is
 * resolved, crit is always false, damage is always zero, HP/history are unchanged, and the
 * ordinary action/frequency spend happens once. Status/move-special effects remain downstream
 * contracts and are not inferred here.</p>
 */
public final class StatusMoveRuntimeResolution {
    private StatusMoveRuntimeResolution() {
    }

    public static AppliedActionResult applyAuthoritativeCombatantStatusMove(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers,
            String source,
            PythonRandom rng,
            MoveResolutionInput input
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (rng == null) throw new IllegalArgumentException("rng is required");
        if (input == null) throw new IllegalArgumentException("input is required");
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("status move execution currently requires a combatant target");
        }

        MoveCombatProfile profile = move.requireCombatProfile();
        if (!profile.damageCategory().equals("status")) {
            throw new IllegalArgumentException("status move execution requires damageCategory=status");
        }

        MoveChoiceRevalidation.requireLegalCombatantMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers);

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());

        int roll = rng.randIntInclusive(1, 20);
        AccuracyResult accuracy = Accuracy.resolve(input.accuracyCheck(roll, null));
        if (!accuracy.hit() && input.rerollOnMiss()) {
            actor.consumeProbabilityControl();
            int reroll = rng.randIntInclusive(1, 20);
            accuracy = Accuracy.resolve(input.accuracyCheck(roll, reroll));
        }

        if (!actor.actionBudget().consume(choice.actionType(), choice.moveId())) {
            throw new IllegalStateException(choice.actionType().value() + " action is already consumed");
        }
        actor.moveFrequencyUsage().recordUse(move);

        MoveResolvedEvent event = new MoveResolvedEvent(
                source,
                actor.combatantId(),
                target.combatantId(),
                choice.moveId(),
                accuracy.hit(),
                false,
                0,
                target.hp()
        );
        return new AppliedActionResult(List.of(event));
    }
}
