package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.PhaseChangedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DefaultPhaseDispatcherTest {
    @Test
    void defaultLifecycleRunsAuthoritativeAbilityPhaseEffects() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                List.of("Lancer")
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
        );
        BattleRoundController controller = new BattleRoundController(state, 2);
        controller.beginTurn("actor");

        controller.advancePhase(); // START -> COMMAND
        controller.advancePhase(); // COMMAND -> ACTION
        List<BattleEvent> endEvents = controller.advancePhase(); // ACTION -> END

        assertEquals(2, endEvents.size());
        assertInstanceOf(PhaseChangedEvent.class, endEvents.get(0));
        assertInstanceOf(RuleEffectEvent.class, endEvents.get(1));
        assertEquals(1, actor.temporaryEffects().count("damage_reduction"));
        assertEquals(5, actor.temporaryEffects().getAll("damage_reduction").getFirst().payload().get("amount"));
    }
}
