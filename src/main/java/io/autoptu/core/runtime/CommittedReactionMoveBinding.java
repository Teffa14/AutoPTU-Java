package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;

/**
 * Server-owned move identity and target binding for a committed reaction attack.
 *
 * <p>The action-window instruction has already frozen trigger legality and resource payment.
 * This binding freezes the ordinary move inputs that execution needs without allowing an
 * adapter to choose a different actor, target, or move after the reaction was committed.</p>
 */
public record CommittedReactionMoveBinding(
        MoveOption move,
        MoveChoice choice
) {
    public CommittedReactionMoveBinding {
        if (move == null) throw new IllegalArgumentException("move is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (!move.moveId().equals(choice.moveId())) {
            throw new IllegalArgumentException("move identity does not match committed reaction choice");
        }
    }

    public void requireParticipants(String reactorId, String triggeringCombatantId) {
        if (reactorId == null || reactorId.isBlank()) throw new IllegalArgumentException("reactorId is required");
        if (triggeringCombatantId == null || triggeringCombatantId.isBlank()) {
            throw new IllegalArgumentException("triggeringCombatantId is required");
        }
        if (!reactorId.strip().equals(choice.actorId())) {
            throw new IllegalArgumentException("choice actor does not match committed reactor");
        }
        if (!choice.targetIds().equals(java.util.List.of(triggeringCombatantId.strip()))) {
            throw new IllegalArgumentException("choice target does not match committed triggering combatant");
        }
    }
}
