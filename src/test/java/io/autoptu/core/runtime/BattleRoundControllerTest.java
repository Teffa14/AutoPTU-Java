package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.ActionType;
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

class BattleRoundControllerTest {
    @Test
    void startRoundIncrementsRoundAndClearsOnlyRoundScopedFrequency() {
        RuntimeCombatantState actor = combatant("actor");
        BattleRuntimeState state = state(actor);
        MoveOption scene = MoveOption.standardWithFrequency("scene", move(), "Scene");
        MoveOption eot = MoveOption.standardWithFrequency("eot", move(), "EOT");
        actor.moveFrequencyUsage().recordUse(scene);
        actor.moveFrequencyUsage().recordUse(eot);

        BattleRoundController rounds = new BattleRoundController(state);
        assertEquals(1, rounds.startRound());

        assertEquals(1, actor.moveFrequencyUsage().battleUses("scene"));
        assertEquals(0, actor.moveFrequencyUsage().roundUses("eot"));
    }

    @Test
    void roundStartDoesNotResetPokemonActionBuckets() {
        RuntimeCombatantState actor = combatant("actor");
        actor.actionBudget().markAction(ActionType.STANDARD, "used standard");
        actor.actionBudget().markAction(ActionType.SHIFT, "used shift");
        actor.actionBudget().grantExtra(ActionType.STANDARD);

        BattleRoundController rounds = new BattleRoundController(state(actor));
        rounds.startRound();

        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, actor.actionBudget().extraCount(ActionType.STANDARD));
    }

    @Test
    void configuredInitialRoundAdvancesDeterministically() {
        BattleRoundController rounds = new BattleRoundController(state(combatant("actor")), 4);
        assertEquals(5, rounds.startRound());
        assertEquals(6, rounds.startRound());
    }

    @Test
    void injuryHistoryRotatesWithoutChangingCurrentInjuries() {
        BattleRoundController rounds = new BattleRoundController(state(combatant("actor")));
        rounds.injuryHistory().setCurrentInjuries("actor", 1);

        rounds.startRound();
        assertEquals(Map.of("actor", 1), rounds.injuryHistory().injuriesLastRound());
        assertEquals(1, rounds.injuryHistory().currentInjuries("actor"));

        rounds.injuryHistory().setCurrentInjuries("actor", 2);
        rounds.startRound();

        assertEquals(Map.of("actor", 1), rounds.injuryHistory().injuriesPreviousRound());
        assertEquals(Map.of("actor", 2), rounds.injuryHistory().injuriesLastRound());
        assertEquals(2, rounds.injuryHistory().currentInjuries("actor"));
    }

    @Test
    void injuryStateRejectsInvalidValues() {
        RoundInjuryHistoryState history = new RoundInjuryHistoryState();
        assertThrows(IllegalArgumentException.class, () -> history.setCurrentInjuries("", 1));
        assertThrows(IllegalArgumentException.class, () -> history.setCurrentInjuries("actor", -1));
    }

    private static BattleRuntimeState state(RuntimeCombatantState actor) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
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

    private static MoveSpec move() {
        return new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee");
    }
}
