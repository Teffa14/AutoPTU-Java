package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeReactionOwnershipResolverTest {
    private static RuntimeCombatantState alpha() {
        return new RuntimeCombatantState(
                "alpha",
                MovementProfile.walking(new GridCoord(0, 0), 5),
                20,
                20,
                new ActionBudget()
        );
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }

    private static BattleRuntimeState stateWithMoves(List<MoveOption> moves) {
        return new BattleRuntimeState(
                new MovementGrid(2, 1, Set.of(), Map.of()),
                List.of(alpha()),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("alpha", moves)
        );
    }

    @Test
    void canonicalMoveIdentityOwnsReactionAcrossSpacingAndSeparatorVariants() {
        RuntimeReactionOwnershipResolver resolver = new RuntimeReactionOwnershipResolver();
        RuntimeReactionOwnershipResolver.Result result = resolver.resolve(
                stateWithMoves(List.of(move("Attack of Opportunity"))),
                "alpha",
                "attack_of_opportunity"
        );

        assertEquals(RuntimeReactionOwnershipResolver.Status.OWNED, result.status());
        assertTrue(result.ownsReaction());
        assertEquals(List.of(MoveReactionOwnershipSource.SOURCE_ID), result.sourceIds());
    }

    @Test
    void canonicalMovesetWithoutReactionProvesNotOwned() {
        RuntimeReactionOwnershipResolver.Result result = new RuntimeReactionOwnershipResolver().resolve(
                stateWithMoves(List.of(move("Tackle"))),
                "alpha",
                "attack-of-opportunity"
        );

        assertEquals(RuntimeReactionOwnershipResolver.Status.NOT_OWNED, result.status());
        assertFalse(result.ownsReaction());
    }

    @Test
    void absentCanonicalMovesetRemainsUnknownInsteadOfSilentlyDenying() {
        BattleRuntimeState partial = new BattleRuntimeState(
                new MovementGrid(2, 1, Set.of(), Map.of()),
                List.of(alpha())
        );
        RuntimeReactionOwnershipResolver.Result result = new RuntimeReactionOwnershipResolver().resolve(
                partial,
                "alpha",
                "attack_of_opportunity"
        );

        assertEquals(RuntimeReactionOwnershipResolver.Status.UNKNOWN, result.status());
        assertFalse(result.ownsReaction());
    }

    @Test
    void ownedSourceWinsWhileUnknownSourcesKeepNegativeAggregateUnknown() {
        ReactionOwnershipSource unknown = new ReactionOwnershipSource() {
            @Override public String sourceId() { return "ability"; }
            @Override public Resolution resolve(BattleRuntimeState state, String combatantId, String reactionKey) {
                return Resolution.UNKNOWN;
            }
        };
        RuntimeReactionOwnershipResolver mixed = new RuntimeReactionOwnershipResolver(
                List.of(new MoveReactionOwnershipSource(), unknown)
        );

        assertEquals(RuntimeReactionOwnershipResolver.Status.OWNED,
                mixed.resolve(stateWithMoves(List.of(move("Attack of Opportunity"))), "alpha", "attack_of_opportunity").status());
        assertEquals(RuntimeReactionOwnershipResolver.Status.UNKNOWN,
                mixed.resolve(stateWithMoves(List.of(move("Tackle"))), "alpha", "attack_of_opportunity").status());
    }
}
