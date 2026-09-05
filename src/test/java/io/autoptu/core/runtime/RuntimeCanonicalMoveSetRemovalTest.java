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
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RuntimeCanonicalMoveSetRemovalTest {
    private static final MoveSpec SPEC = new MoveSpec("1 Target", "Melee", 1, 1, null, null, "Melee");

    @Test
    void persistsSelectedRemovalIntoCanonicalBattleState() {
        MoveOption tackle = move("Tackle");
        MoveOption confusion = move("Confusion");
        MoveOption psybeam = move("Psybeam");
        BattleRuntimeState state = state(
                List.of(combatant("actor")),
                Map.of("actor", List.of(tackle, confusion, psybeam))
        );

        CanonicalMoveSetRemoval.Result result = RuntimeCanonicalMoveSetRemoval.apply(
                state,
                "actor",
                List.of(" psybeam ", "CONFUSION")
        );

        assertEquals(List.of(tackle), state.moveOptions("actor"));
        assertEquals(List.of(confusion, psybeam), result.removed());
        assertEquals(List.of(tackle), result.kept());
    }

    @Test
    void mutationIsIsolatedToRequestedCombatant() {
        MoveOption actorMove = move("Confusion");
        MoveOption otherMove = move("Confusion");
        BattleRuntimeState state = state(
                List.of(combatant("actor"), combatant("other")),
                Map.of(
                        "actor", List.of(actorMove),
                        "other", List.of(otherMove)
                )
        );

        RuntimeCanonicalMoveSetRemoval.apply(state, "actor", List.of("Confusion"));

        assertEquals(List.of(), state.moveOptions("actor"));
        assertEquals(List.of(otherMove), state.moveOptions("other"));
    }

    @Test
    void absentIdentityIsAStatePreservingNoOp() {
        MoveOption tackle = move("Tackle");
        BattleRuntimeState state = state(
                List.of(combatant("actor")),
                Map.of("actor", List.of(tackle))
        );

        CanonicalMoveSetRemoval.Result result = RuntimeCanonicalMoveSetRemoval.apply(
                state,
                "actor",
                List.of("Psybeam")
        );

        assertEquals(List.of(tackle), state.moveOptions("actor"));
        assertEquals(List.of(), result.removed());
    }

    @Test
    void refusesToMaterializeMovesetWhenCanonicalOwnershipIsMissing() {
        BattleRuntimeState state = state(List.of(combatant("actor")), Map.of());

        assertThrows(
                IllegalStateException.class,
                () -> RuntimeCanonicalMoveSetRemoval.apply(state, "actor", List.of("Confusion"))
        );
        assertEquals(false, state.hasCanonicalMoves("actor"));
    }

    @Test
    void rejectsUnknownCombatantBeforeMutation() {
        BattleRuntimeState state = state(
                List.of(combatant("actor")),
                Map.of("actor", List.of(move("Tackle")))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeCanonicalMoveSetRemoval.apply(state, "missing", List.of("Tackle"))
        );
        assertEquals(List.of(move("Tackle")), state.moveOptions("actor"));
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, SPEC);
    }

    private static BattleRuntimeState state(
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends List<MoveOption>> moves
    ) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                combatants,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                moves
        );
    }

    private static RuntimeCombatantState combatant(String id) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
    }
}
