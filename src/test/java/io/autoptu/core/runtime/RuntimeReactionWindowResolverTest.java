package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.ShiftResolvedEvent;
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

class RuntimeReactionWindowResolverTest {
    private static final String REACTION = "attack_of_opportunity";

    @Test
    void discoversEligibleReactorsInStableBattleInsertionOrder() {
        BattleRuntimeState state = battle(
                List.of(
                        combatant("reactor-b", new GridCoord(0, 1)),
                        combatant("actor", new GridCoord(2, 0)),
                        combatant("reactor-a", new GridCoord(0, 0)),
                        combatant("far", new GridCoord(8, 8))
                ),
                Map.of(),
                Map.of(
                        "reactor-b", List.of(move("Attack of Opportunity")),
                        "actor", List.of(move("Tackle")),
                        "reactor-a", List.of(move("Attack of Opportunity")),
                        "far", List.of(move("Attack of Opportunity"))
                )
        );
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0));

        RuntimeReactionWindowResolver.Resolution result = new RuntimeReactionWindowResolver(state)
                .discoverShiftWindows(REACTION, event, ReactionEligibilityPolicy.attackOfOpportunity());

        assertEquals(List.of("reactor-b", "reactor-a"), result.eligible().stream()
                .map(RuntimeReactionWindowResolver.Candidate::reactorId)
                .toList());
        assertTrue(result.unresolved().isEmpty());
        assertEquals("actor", result.eligible().get(0).triggeringActorId());
    }

    @Test
    void filtersBlockedStatusAndExhaustedUseWithoutMutatingOtherCandidates() {
        BattleRuntimeState state = battle(
                List.of(
                        combatant("sleeping", new GridCoord(0, 0)),
                        combatant("exhausted", new GridCoord(0, 1)),
                        combatant("ready", new GridCoord(1, 1)),
                        combatant("actor", new GridCoord(2, 0))
                ),
                Map.of("sleeping", Set.of("Sleeping")),
                Map.of(
                        "sleeping", List.of(move("Attack of Opportunity")),
                        "exhausted", List.of(move("Attack of Opportunity")),
                        "ready", List.of(move("Attack of Opportunity")),
                        "actor", List.of(move("Tackle"))
                )
        );
        RuntimeReactionUsageTracker usage = new RuntimeReactionUsageTracker(state);
        usage.recordUseFromRuntime("exhausted", REACTION);
        Map<String, Integer> before = usage.snapshotForCurrentRound();

        RuntimeReactionWindowResolver.Resolution result = new RuntimeReactionWindowResolver(state)
                .discoverShiftWindows(
                        REACTION,
                        new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0)),
                        ReactionEligibilityPolicy.attackOfOpportunity()
                );

        assertEquals(List.of("ready"), result.eligible().stream()
                .map(RuntimeReactionWindowResolver.Candidate::reactorId)
                .toList());
        assertTrue(result.unresolved().isEmpty());
        assertEquals(before, usage.snapshotForCurrentRound());
    }

    @Test
    void preservesUnknownOwnershipInsteadOfConvertingPartialSnapshotToDenial() {
        BattleRuntimeState state = battle(
                List.of(
                        combatant("known", new GridCoord(0, 0)),
                        combatant("unknown", new GridCoord(0, 1)),
                        combatant("actor", new GridCoord(2, 0))
                ),
                Map.of(),
                Map.of(
                        "known", List.of(move("Attack of Opportunity")),
                        "actor", List.of(move("Tackle"))
                )
        );

        RuntimeReactionWindowResolver.Resolution result = new RuntimeReactionWindowResolver(state)
                .discoverShiftWindows(
                        REACTION,
                        new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0)),
                        ReactionEligibilityPolicy.attackOfOpportunity()
                );

        assertEquals(List.of("known"), result.eligible().stream()
                .map(RuntimeReactionWindowResolver.Candidate::reactorId)
                .toList());
        assertEquals(List.of("unknown"), result.unresolved().stream()
                .map(RuntimeReactionWindowResolver.UnresolvedCandidate::reactorId)
                .toList());
    }

    private static BattleRuntimeState battle(
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends Set<String>> statuses,
            Map<String, ? extends List<MoveOption>> moves
    ) {
        Map<String, CombatantAffiliationState> affiliations = Map.of(
                "actor", CombatantAffiliationState.active("red"),
                "reactor-a", CombatantAffiliationState.active("blue"),
                "reactor-b", CombatantAffiliationState.active("blue"),
                "far", CombatantAffiliationState.active("blue"),
                "sleeping", CombatantAffiliationState.active("blue"),
                "exhausted", CombatantAffiliationState.active("blue"),
                "ready", CombatantAffiliationState.active("blue"),
                "known", CombatantAffiliationState.active("blue"),
                "unknown", CombatantAffiliationState.active("blue")
        );
        return new BattleRuntimeState(
                new MovementGrid(12, 12, Set.of(), Map.of()),
                combatants,
                statuses,
                Map.of(),
                Map.of(),
                affiliations.entrySet().stream()
                        .filter(entry -> combatants.stream().anyMatch(c -> c.combatantId().equals(entry.getKey())))
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (left, right) -> left,
                                java.util.LinkedHashMap::new
                        )),
                moves
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
