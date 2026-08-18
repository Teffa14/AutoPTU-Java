package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.DamageDice;
import io.autoptu.core.model.DamageResult;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeAuthoritativeMoveFrequencyTest {
    @Test
    void sceneMoveDisappearsAfterOneServerOwnedUse() {
        MoveOption move = MoveOption.standardWithFrequency("scene-move", move("Melee", 1), "Scene");
        BattleRuntimeState state = battleState(move);

        assertTrue(hasMove(legal(state), move.moveId()));
        state.requireCombatant("actor").moveFrequencyUsage().recordUse(move);
        assertFalse(hasMove(legal(state), move.moveId()));
    }

    @Test
    void sceneX2RemainsUntilSecondUse() {
        MoveOption move = MoveOption.standardWithFrequency("scene-two", move("Melee", 1), "Scene x2");
        BattleRuntimeState state = battleState(move);
        MoveFrequencyUsage usage = state.requireCombatant("actor").moveFrequencyUsage();

        usage.recordUse(move);
        assertTrue(hasMove(legal(state), move.moveId()));
        usage.recordUse(move);
        assertFalse(hasMove(legal(state), move.moveId()));
    }

    @Test
    void eotReturnsAfterRoundReset() {
        MoveOption move = MoveOption.standardWithFrequency("eot-move", move("Melee", 1), "EOT");
        BattleRuntimeState state = battleState(move);
        RuntimeCombatantState actor = state.requireCombatant("actor");

        actor.moveFrequencyUsage().recordUse(move);
        assertFalse(hasMove(legal(state), move.moveId()));

        BattleRuntime.resetRoundMoveFrequency(state);
        assertTrue(hasMove(legal(state), move.moveId()));
        assertEquals(0, actor.moveFrequencyUsage().roundUses(move.moveId()));
    }

    @Test
    void unboundedFrequencyNeverCreatesUsageLimit() {
        MoveOption move = MoveOption.standardWithFrequency("at-will", move("Melee", 1), "At-Will");
        BattleRuntimeState state = battleState(move);
        RuntimeCombatantState actor = state.requireCombatant("actor");

        actor.moveFrequencyUsage().recordUse(move);
        actor.moveFrequencyUsage().recordUse(move);

        assertTrue(hasMove(legal(state), move.moveId()));
        assertEquals(0, actor.moveFrequencyUsage().battleUses(move.moveId()));
    }

    @Test
    void staleChoiceCannotExecuteAfterFrequencyBecomesExhausted() {
        MoveOption move = MoveOption.standardWithFrequency("scene-move", move("Melee", 1), "Scene");
        BattleRuntimeState state = battleState(move);
        MoveChoice choice = firstMove(legal(state), move.moveId());

        state.requireCombatant("actor").moveFrequencyUsage().recordUse(move);

        assertThrows(IllegalArgumentException.class, () -> MoveChoiceRevalidation.requireLegalCombatantMove(
                state,
                choice,
                move,
                "Medium",
                "Medium",
                Set.of()
        ));
    }

    @Test
    void successfulResolvedExecutionConsumesFrequencyAfterValidation() {
        MoveOption move = MoveOption.standardWithFrequency("scene-move", move("Melee", 1), "Scene");
        BattleRuntimeState state = battleState(move);
        MoveChoice choice = firstMove(legal(state), move.moveId());

        BattleRuntime.applyRevalidatedResolvedMoveOutcome(
                state,
                choice,
                move,
                "Medium",
                "Medium",
                Set.of(),
                "test",
                new AccuracyResult(true, false, 10, 2),
                new DamageResult(new DamageDice(1, 6, 0), 3, 0, 3, 3, 3, 3)
        );

        assertEquals(1, state.requireCombatant("actor").moveFrequencyUsage().battleUses(move.moveId()));
    }

    private static List<BattleChoice> legal(BattleRuntimeState state) {
        return RuntimeAutobattlerActionSpace.legalChoices(state, "actor", Set.of(), 0, ignored -> true);
    }

    private static BattleRuntimeState battleState(MoveOption actorMove) {
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
                Map.of("actor", List.of(actorMove))
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
        return new MoveSpec(targetKind, targetKind, range, range, null, null, targetKind);
    }

    private static boolean hasMove(List<BattleChoice> choices, String moveId) {
        return choices.stream()
                .filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .anyMatch(choice -> choice.moveId().equals(moveId));
    }

    private static MoveChoice firstMove(List<BattleChoice> choices, String moveId) {
        return choices.stream()
                .filter(MoveChoice.class::isInstance)
                .map(MoveChoice.class::cast)
                .filter(choice -> choice.moveId().equals(moveId))
                .findFirst()
                .orElseThrow();
    }
}
