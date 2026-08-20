package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class BattleRuntimeInjuryHistoryTest {
    @Test
    void defaultRoundControllerSharesBattleRuntimeInjuryHistory() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
        );

        state.injuryHistory().setCurrentInjuries("actor", 2);
        BattleRoundController rounds = new BattleRoundController(state, 3);

        assertSame(state.injuryHistory(), rounds.injuryHistory());
        assertEquals(2, rounds.injuryHistory().currentInjuries("actor"));

        rounds.startRound();

        assertEquals(Map.of("actor", 2), state.injuryHistory().injuriesLastRound());
        assertEquals(Map.of("actor", 2), rounds.injuryHistory().injuriesLastRound());
    }

    @Test
    void currentInjuriesAreAvailableFromTheAuthoritativeBattleState() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(0, 0), 1),
                10,
                10,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(2, 2, Set.of(), Map.of()),
                List.of(actor)
        );

        state.injuryHistory().setCurrentInjuries("actor", 3);

        assertEquals(3, state.injuryHistory().currentInjuries("actor"));
    }
}
