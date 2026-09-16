package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.hook.ReactionEligibilityPolicy;
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

class RuntimeActionTransitionTest {
    @Test
    void standUpMutatesAuthoritativeStatusBeforeOpeningReactionWindow() {
        BattleRuntimeState state = battle(new GridCoord(0, 0), new GridCoord(1, 0));
        RuntimeActionTransition.Result result = new RuntimeActionTransition(state).standUp("actor", 51);

        assertFalse(state.hasStatus("actor", "prone"));
        assertEquals("action_resolved|actor|stand_up|", result.occurrence().event().stableKey());

        RuntimeReactionWindowResolver.Resolution windows = new RuntimeReactionWindowResolver(state)
                .discoverAdjacentActionWindows(
                        "attack_of_opportunity",
                        result.occurrence(),
                        ReactionEligibilityPolicy.attackOfOpportunity()
                );

        assertEquals(List.of("reactor"), windows.eligible().stream()
                .map(RuntimeReactionWindowResolver.Candidate::reactorId).toList());
        assertEquals(RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_STAND_UP,
                windows.eligible().get(0).window().triggerKind());
        assertEquals(result.occurrence().occurrenceKey(),
                windows.eligible().get(0).window().triggeringEventKey());
    }

    @Test
    void standUpRejectsActorThatIsNotProneWithoutEmittingOccurrence() {
        BattleRuntimeState state = battle(new GridCoord(0, 0), new GridCoord(1, 0));
        state.removeStatus("actor", "prone");

        assertThrows(IllegalStateException.class,
                () -> new RuntimeActionTransition(state).standUp("actor", 52));
        assertFalse(state.hasStatus("actor", "prone"));
    }

    private static BattleRuntimeState battle(GridCoord reactorPosition, GridCoord actorPosition) {
        List<RuntimeCombatantState> combatants = List.of(
                combatant("reactor", reactorPosition), combatant("actor", actorPosition));
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of("actor", List.of("prone")),
                Map.of(), Map.of(),
                Map.of(
                        "reactor", CombatantAffiliationState.active("blue"),
                        "actor", CombatantAffiliationState.active("red")
                ),
                Map.of(
                        "reactor", List.of(move("Attack of Opportunity")),
                        "actor", List.of(move("Tackle"))
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(id, MovementProfile.walking(position, 6), 20, 20, new ActionBudget());
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }
}
