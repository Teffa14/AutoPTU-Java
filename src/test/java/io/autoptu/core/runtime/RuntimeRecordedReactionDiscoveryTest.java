package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeRecordedReactionDiscoveryTest {
    private static final String REACTION = "attack_of_opportunity";

    @Test
    void recordedShiftFeedsOccurrenceIdentityDirectlyIntoReactionDiscovery() {
        RuntimeCombatantState reactor = combatant("reactor", new GridCoord(0, 0));
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 0));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(reactor, actor),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "reactor", CombatantAffiliationState.active("blue"),
                        "actor", CombatantAffiliationState.active("red")
                ),
                Map.of(
                        "reactor", List.of(move("Attack of Opportunity")),
                        "actor", List.of(move("Tackle"))
                )
        );
        BattleRuntimeExecutionContext context = new BattleRuntimeExecutionContext(state);

        RecordedActionResult recorded = context.applyAction(
                new ShiftChoice("actor", new GridCoord(2, 0)),
                ignored -> true
        );
        RuntimeReactionWindowResolver.Resolution resolution = new RuntimeRecordedReactionDiscovery(state)
                .discoverShiftWindows(REACTION, recorded, ReactionEligibilityPolicy.attackOfOpportunity());

        assertEquals(1, resolution.eligible().size());
        RuntimeReactionWindow window = resolution.eligible().getFirst().window();
        assertEquals("reactor", window.reactorId());
        assertEquals("actor", window.triggeringActorId());
        assertEquals(recorded.occurrences().getFirst().occurrenceKey(), window.triggeringEventKey());
    }

    @Test
    void actionWithoutShiftOccurrenceProducesNoShiftReactionWindow() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of());
        RecordedActionResult recorded = new RecordedActionResult(
                new AppliedActionResult(List.of()),
                List.of()
        );

        RuntimeReactionWindowResolver.Resolution resolution = new RuntimeRecordedReactionDiscovery(state)
                .discoverShiftWindows(REACTION, recorded, ReactionEligibilityPolicy.attackOfOpportunity());

        assertTrue(resolution.eligible().isEmpty());
        assertTrue(resolution.unresolved().isEmpty());
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 6),
                20,
                20,
                new ActionBudget()
        );
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }
}
