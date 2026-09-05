package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;

import java.util.Collection;

/**
 * Server-owned mutation boundary for effects that expire canonical moves by identity.
 *
 * The pure selection semantics live in {@link CanonicalMoveSetRemoval}; this service
 * is the only layer that persists that result back into battle state. Adapters remain
 * read-only consumers of the resulting moveset.
 */
public final class RuntimeCanonicalMoveSetRemoval {
    private RuntimeCanonicalMoveSetRemoval() {
    }

    public static CanonicalMoveSetRemoval.Result apply(
            BattleRuntimeState state,
            String combatantId,
            Collection<String> moveIdentities
    ) {
        if (state == null) {
            throw new IllegalArgumentException("battle state is required");
        }
        state.requireCombatant(combatantId);
        if (!state.hasCanonicalMoves(combatantId)) {
            throw new IllegalStateException("combatant has no canonical moveset: " + combatantId);
        }

        CanonicalMoveSetRemoval.Result result = CanonicalMoveSetRemoval.resolve(
                state.moveOptions(combatantId),
                moveIdentities
        );
        if (!result.removed().isEmpty()) {
            state.replaceMoveOptionsFromRuntime(combatantId, result.kept());
        }
        return result;
    }
}
