package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.hook.ReactionEligibilityPolicy;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeReactionWindowArbiterTest {
    private static final String REACTION = "attack_of_opportunity";

    @Test
    void ordersByAuthoritativeOccurrenceThenBattleInsertion() {
        BattleRuntimeState state = battle();
        RuntimeReactionWindowResolver resolver = new RuntimeReactionWindowResolver(state);
        ShiftResolvedEvent shift = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0));

        List<RuntimeReactionWindowResolver.Candidate> first = resolver.discoverShiftWindows(
                REACTION,
                new BattleEventOccurrence(11, shift),
                ReactionEligibilityPolicy.attackOfOpportunity()
        ).eligible();
        List<RuntimeReactionWindowResolver.Candidate> second = resolver.discoverShiftWindows(
                REACTION,
                new BattleEventOccurrence(12, shift),
                ReactionEligibilityPolicy.attackOfOpportunity()
        ).eligible();

        ArrayList<RuntimeReactionWindowResolver.Candidate> scrambled = new ArrayList<>();
        scrambled.add(second.get(1));
        scrambled.add(first.get(1));
        scrambled.add(second.get(0));
        scrambled.add(first.get(0));

        List<RuntimeReactionWindowResolver.Candidate> ordered = new RuntimeReactionWindowArbiter(state)
                .arbitrate(scrambled);

        assertEquals(List.of("reactor-b", "reactor-a", "reactor-b", "reactor-a"), ordered.stream()
                .map(RuntimeReactionWindowResolver.Candidate::reactorId)
                .toList());
        assertEquals(List.of(11L, 11L, 12L, 12L), ordered.stream()
                .map(candidate -> RuntimeReactionWindowArbiter.occurrenceSequence(candidate.window()))
                .toList());
    }

    @Test
    void rejectsLegacySemanticWindowsWithoutOccurrenceIdentity() {
        BattleRuntimeState state = battle();
        RuntimeReactionWindowResolver.Candidate legacy = new RuntimeReactionWindowResolver(state)
                .discoverShiftWindows(
                        REACTION,
                        new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0)),
                        ReactionEligibilityPolicy.attackOfOpportunity()
                ).eligible().getFirst();

        assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeReactionWindowArbiter(state).arbitrate(List.of(legacy))
        );
    }

    @Test
    void emptyQueueRemainsEmpty() {
        assertTrue(new RuntimeReactionWindowArbiter(battle()).arbitrate(List.of()).isEmpty());
    }

    private static BattleRuntimeState battle() {
        List<RuntimeCombatantState> combatants = List.of(
                combatant("reactor-b", new GridCoord(0, 1)),
                combatant("actor", new GridCoord(2, 0)),
                combatant("reactor-a", new GridCoord(0, 0))
        );
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "reactor-b", CombatantAffiliationState.active("blue"),
                        "actor", CombatantAffiliationState.active("red"),
                        "reactor-a", CombatantAffiliationState.active("blue")
                ),
                Map.of(
                        "reactor-b", List.of(move("Attack of Opportunity")),
                        "actor", List.of(move("Tackle")),
                        "reactor-a", List.of(move("Attack of Opportunity"))
                )
        );
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
