package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;

/** Resolves delayed-hit scheduling state into an authoritative move execution request. */
public final class DelayedHitBindingResolver {
    private DelayedHitBindingResolver() {
    }

    public static DelayedHitBinding bind(BattleRuntimeState state, DelayedHitEntry entry) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (entry == null) throw new IllegalArgumentException("entry is required");

        RuntimeCombatantState attacker = state.requireCombatant(entry.attackerId());
        if (!state.hasCanonicalMoves(attacker.combatantId())) {
            throw new IllegalStateException("delayed-hit attacker has no canonical moveset: " + attacker.combatantId());
        }
        MoveOption move = state.moveOptions(attacker.combatantId()).stream()
                .filter(candidate -> candidate.moveId().equals(entry.moveId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "delayed-hit move is not in canonical moveset: " + entry.moveId()));

        MoveChoice choice;
        if (entry.targetPosition() != null) {
            choice = new MoveChoice(
                    attacker.combatantId(),
                    move.moveId(),
                    ChoiceTargetMode.TILE,
                    "",
                    entry.targetPosition(),
                    move.actionType()
            );
        } else if (entry.targetId() != null) {
            RuntimeCombatantState target = state.requireCombatant(entry.targetId());
            GridCoord anchor = target.position();
            choice = new MoveChoice(
                    attacker.combatantId(),
                    move.moveId(),
                    ChoiceTargetMode.COMBATANT,
                    target.combatantId(),
                    anchor,
                    move.actionType()
            );
        } else {
            throw new IllegalArgumentException("delayed hit requires targetId or targetPosition");
        }

        return new DelayedHitBinding(entry, move, choice);
    }
}
