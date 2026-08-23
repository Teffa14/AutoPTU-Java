package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.runtime.BattleRuntimeState;

import java.util.Collection;
import java.util.List;

/** Server-owned semantic context shared by PRE-damage reaction families. */
public record PreDamageReactionContext(
        BattleRuntimeState state,
        String attackerId,
        String defenderId,
        String moveName,
        String effectiveMoveName,
        List<GridCoord> threatenedTiles,
        OutOfTurnDecisionGate outOfTurnDecision
) {
    public PreDamageReactionContext {
        if (state == null) throw new IllegalArgumentException("state is required");
        attackerId = require(attackerId, "attackerId");
        defenderId = require(defenderId, "defenderId");
        moveName = normalize(moveName);
        effectiveMoveName = normalize(effectiveMoveName);
        threatenedTiles = threatenedTiles == null ? List.of() : List.copyOf(threatenedTiles);
        outOfTurnDecision = outOfTurnDecision == null
                ? OutOfTurnDecisionGate.allowWhenUnconfigured()
                : outOfTurnDecision;
    }

    public static PreDamageReactionContext of(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            Collection<GridCoord> threatenedTiles
    ) {
        return of(state, attackerId, defenderId, moveName, moveName, threatenedTiles);
    }

    public static PreDamageReactionContext of(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            String effectiveMoveName,
            Collection<GridCoord> threatenedTiles
    ) {
        return new PreDamageReactionContext(
                state, attackerId, defenderId, moveName, effectiveMoveName,
                threatenedTiles == null ? List.of() : List.copyOf(threatenedTiles),
                OutOfTurnDecisionGate.allowWhenUnconfigured()
        );
    }

    public OutOfTurnDecisionRequest decisionRequest(String actorId, String label, boolean optional) {
        return OutOfTurnDecisionRequest.preDamageInterrupt(
                actorId, label, moveName, effectiveMoveName, attackerId, defenderId, optional
        );
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
