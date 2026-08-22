package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;

/** Resolves delayed-hit scheduling state into authoritative target and execution requests. */
public final class DelayedHitBindingResolver {
    private DelayedHitBindingResolver() {
    }

    /**
     * Preferred target-resolution boundary.
     *
     * A live target id resolves to the defender's current authoritative position. A stale
     * target id is preserved and falls back to the stored position exactly as the pinned
     * Python resolve_move_targets contract does. This method deliberately does not rewrite
     * the move targeting model when the defender is missing.
     */
    public static DelayedHitTargetRequest resolveTargetRequest(BattleRuntimeState state, DelayedHitEntry entry) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (entry == null) throw new IllegalArgumentException("entry is required");

        RuntimeCombatantState attacker = state.requireCombatant(entry.attackerId());
        MoveOption move = requireCanonicalMove(state, attacker, entry.moveId());

        if (entry.targetId() != null) {
            RuntimeCombatantState target = state.combatants().get(entry.targetId());
            GridCoord resolvedPosition = target == null ? entry.targetPosition() : target.position();
            return new DelayedHitTargetRequest(
                    entry,
                    move,
                    entry.targetId(),
                    resolvedPosition,
                    target != null
            );
        }
        if (entry.targetPosition() != null) {
            return new DelayedHitTargetRequest(entry, move, null, entry.targetPosition(), false);
        }
        throw new IllegalArgumentException("delayed hit requires targetId or targetPosition");
    }

    /**
     * Direct execution binding retained for branches Java can already resolve fully.
     * Missing combatant targets remain a target-resolution case and fail before any queue
     * mutation instead of being incorrectly coerced to a TILE attack.
     */
    public static DelayedHitBinding bind(BattleRuntimeState state, DelayedHitEntry entry) {
        DelayedHitTargetRequest targetRequest = resolveTargetRequest(state, entry);
        RuntimeCombatantState attacker = state.requireCombatant(entry.attackerId());
        MoveOption move = targetRequest.move();

        MoveChoice choice;
        if (targetRequest.targetId() != null) {
            if (!targetRequest.targetPresent()) {
                throw new UnsupportedOperationException(
                        "missing delayed combatant target requires target-resolution execution"
                );
            }
            choice = new MoveChoice(
                    attacker.combatantId(),
                    move.moveId(),
                    ChoiceTargetMode.COMBATANT,
                    targetRequest.targetId(),
                    targetRequest.resolvedTargetPosition(),
                    move.actionType()
            );
        } else {
            choice = new MoveChoice(
                    attacker.combatantId(),
                    move.moveId(),
                    ChoiceTargetMode.TILE,
                    "",
                    targetRequest.resolvedTargetPosition(),
                    move.actionType()
            );
        }

        return new DelayedHitBinding(entry, move, choice);
    }

    private static MoveOption requireCanonicalMove(
            BattleRuntimeState state,
            RuntimeCombatantState attacker,
            String moveId
    ) {
        if (!state.hasCanonicalMoves(attacker.combatantId())) {
            throw new IllegalStateException("delayed-hit attacker has no canonical moveset: " + attacker.combatantId());
        }
        return state.moveOptions(attacker.combatantId()).stream()
                .filter(candidate -> candidate.moveId().equals(moveId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "delayed-hit move is not in canonical moveset: " + moveId));
    }
}
