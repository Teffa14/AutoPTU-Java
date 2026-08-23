package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;

import java.util.List;

/**
 * Server-authoritative bridge from a legal TILE move choice to its effective combatant targets.
 *
 * <p>The action-space contract already freezes which tile anchors are legal. The effective-target
 * resolver already freezes Python's area/footprint/LoS target expansion. This boundary composes
 * those two contracts against the current {@link BattleRuntimeState}: the selected move must still
 * belong to the actor, its action/frequency must still be available, and the exact TILE choice must
 * still be present in the live action space before any target list is produced. Minecraft/Cobblemon
 * may retain a previously displayed choice, but cannot supply the authoritative move metadata,
 * anchor legality, combatant positions, footprints, blockers, HP eligibility, or target list.</p>
 */
public final class RuntimeAreaMoveTargeting {
    private RuntimeAreaMoveTargeting() {
    }

    public static EffectiveMoveTargetResolution resolve(
            BattleRuntimeState state,
            MoveChoice choice
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (choice.targetMode() != ChoiceTargetMode.TILE || !choice.targetId().isBlank()) {
            throw new IllegalArgumentException("area target expansion requires a TILE move choice");
        }

        MoveOption move = requireCanonicalMove(state, choice.actorId(), choice.moveId());
        if (choice.actionType() != move.actionType()) {
            throw new IllegalArgumentException("move metadata does not match choice action type");
        }
        if (!state.requireCombatant(choice.actorId()).moveFrequencyUsage().available(move)) {
            throw new IllegalArgumentException("move frequency is exhausted in current runtime state");
        }

        List<BattleChoice> legalChoices = RuntimeAutobattlerActionSpace.legalChoices(
                state,
                choice.actorId(),
                state.grid().blockers(),
                0,
                ignored -> true
        );
        boolean exactChoiceStillLegal = legalChoices.stream()
                .anyMatch(candidate -> candidate.stableKey().equals(choice.stableKey()));
        if (!exactChoiceStillLegal) {
            throw new IllegalArgumentException("tile move choice is no longer legal in current runtime state");
        }

        return EffectiveMoveTargetResolver.resolve(
                state,
                choice.actorId(),
                move,
                choice.targetAnchor(),
                ""
        );
    }

    private static MoveOption requireCanonicalMove(
            BattleRuntimeState state,
            String actorId,
            String moveId
    ) {
        for (MoveOption move : state.moveOptions(actorId)) {
            if (move != null && move.moveId().equals(moveId)) {
                return move;
            }
        }
        throw new IllegalArgumentException("move is not present in the actor's canonical moveset: " + moveId);
    }
}
