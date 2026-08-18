package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeMovesetTest {
    @Test
    void preferredActionSpaceReadsMovesFromCanonicalBattleState() {
        MoveOption tackle = MoveOption.standard("tackle", move("Melee", 1));
        BattleRuntimeState state = battleState(List.of(tackle));

        List<BattleChoice> choices = RuntimeAutobattlerActionSpace.legalChoices(
                state,
                "actor",
                Set.of(),
                0,
                ignored -> true
        );

        assertTrue(hasMove(choices, "tackle"));
    }

    @Test
    void adapterCannotGrantMoveOnceRuntimeOwnsMoveset() {
        MoveOption tackle = MoveOption.standard("tackle", move("Melee", 1));
        MoveOption forged = MoveOption.standard("forged-hyper-beam", move("Ranged", 6));
        BattleRuntimeState state = battleState(List.of(tackle));

        List<BattleChoice> choices = RuntimeAutobattlerActionSpace.legalChoices(
                state,
                "actor",
                List.of(forged),
                Set.of(),
                0,
                ignored -> true
        );

        assertTrue(hasMove(choices, "tackle"));
        assertFalse(hasMove(choices, "forged-hyper-beam"));
    }

    @Test
    void explicitEmptyCanonicalMovesetCannotBeReplacedByAdapter() {
        BattleRuntimeState state = battleState(List.of());
        MoveOption forged = MoveOption.standard("forged-hyper-beam", move("Ranged", 6));

        List<BattleChoice> choices = RuntimeAutobattlerActionSpace.legalChoices(
                state,
                "actor",
                List.of(forged),
                Set.of(),
                0,
                ignored -> true
        );

        assertFalse(hasMove(choices, "forged-hyper-beam"));
        assertTrue(choices.stream().anyMatch(ShiftChoice.class::isInstance));
    }

    @Test
    void movesetIsDefensivelyCopiedIntoBattleSnapshot() {
        ArrayList<MoveOption> mutableMoves = new ArrayList<>();
        mutableMoves.add(MoveOption.standard("tackle", move("Melee", 1)));
        BattleRuntimeState state = battleState(mutableMoves);

        mutableMoves.add(MoveOption.standard("forged-after-snapshot", move("Ranged", 6)));

        List<BattleChoice> choices = RuntimeAutobattlerActionSpace.legalChoices(
                state,
                "actor",
                Set.of(),
                0,
                ignored -> true
        );
        assertTrue(hasMove(choices, "tackle"));
        assertFalse(hasMove(choices, "forged-after-snapshot"));
    }

    @Test
    void duplicateMoveIdsFailClosedWhenSnapshotIsMaterialized() {
        MoveOption first = MoveOption.standard("tackle", move("Melee", 1));
        MoveOption duplicate = MoveOption.standard("tackle", move("Ranged", 4));

        assertThrows(IllegalArgumentException.class, () -> battleState(List.of(first, duplicate)));
    }

    private static BattleRuntimeState battleState(List<MoveOption> actorMoves) {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1));
        RuntimeCombatantState enemy = combatant("enemy", new GridCoord(2, 1));
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "actor", CombatantAffiliationState.active("red"),
                        "enemy", CombatantAffiliationState.active("blue")
                ),
                Map.of("actor", actorMoves)
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 1),
                30,
                30,
                new ActionBudget()
        );
    }

    private static MoveSpec move(String targetKind, Integer range) {
        return new MoveSpec(
                targetKind,
                targetKind,
                range,
                range,
                null,
                null,
                targetKind
        );
    }

    private static boolean hasMove(List<BattleChoice> choices, String moveId) {
        return choices.stream()
                .filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .anyMatch(choice -> choice.moveId().equals(moveId));
    }
}
