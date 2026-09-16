package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.event.BattleEventOccurrence;
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

class RuntimeAdjacentActionReactionWindowTest {
    @Test
    void authoritativeStandUpOccurrenceOpensAttackOfOpportunityWindow() {
        BattleRuntimeState state = battle(new GridCoord(0, 0), new GridCoord(1, 0));
        BattleEventOccurrence occurrence = new BattleEventOccurrence(21,
                new ActionResolvedEvent("actor", "stand_up"));

        RuntimeReactionWindowResolver.Resolution resolution = new RuntimeReactionWindowResolver(state)
                .discoverAdjacentActionWindows(
                        "attack_of_opportunity", occurrence, ReactionEligibilityPolicy.attackOfOpportunity());

        assertEquals(List.of("reactor"), resolution.eligible().stream()
                .map(RuntimeReactionWindowResolver.Candidate::reactorId).toList());
        RuntimeReactionWindow window = resolution.eligible().get(0).window();
        assertEquals(RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_STAND_UP, window.triggerKind());
        assertEquals(occurrence.occurrenceKey(), window.triggeringEventKey());
        assertEquals("actor", window.triggeringActorId());
    }

    @Test
    void sameOccurrenceIsStableAndRepeatedStandUpGetsDistinctWindowIdentity() {
        BattleRuntimeState state = battle(new GridCoord(0, 0), new GridCoord(1, 0));
        ActionResolvedEvent event = new ActionResolvedEvent("actor", "stand_up");
        RuntimeReactionWindowResolver resolver = new RuntimeReactionWindowResolver(state);
        BattleEventOccurrence first = new BattleEventOccurrence(21, event);
        BattleEventOccurrence second = new BattleEventOccurrence(22, event);

        String firstKey = resolver.discoverAdjacentActionWindows(
                "attack_of_opportunity", first, ReactionEligibilityPolicy.attackOfOpportunity())
                .eligible().get(0).window().windowKey();
        String repeatedFirstKey = resolver.discoverAdjacentActionWindows(
                "attack_of_opportunity", first, ReactionEligibilityPolicy.attackOfOpportunity())
                .eligible().get(0).window().windowKey();
        String secondKey = resolver.discoverAdjacentActionWindows(
                "attack_of_opportunity", second, ReactionEligibilityPolicy.attackOfOpportunity())
                .eligible().get(0).window().windowKey();

        assertEquals(firstKey, repeatedFirstKey);
        assertTrue(!firstKey.equals(secondKey));
    }

    @Test
    void registryClassificationRejectsUnknownActionAndNonAdjacentActor() {
        BattleRuntimeState distant = battle(new GridCoord(0, 0), new GridCoord(2, 0));
        RuntimeReactionWindowResolver resolver = new RuntimeReactionWindowResolver(distant);

        assertTrue(resolver.discoverAdjacentActionWindows(
                "attack_of_opportunity",
                new BattleEventOccurrence(30, new ActionResolvedEvent("actor", "stand_up")),
                ReactionEligibilityPolicy.attackOfOpportunity()).eligible().isEmpty());
        assertTrue(resolver.discoverAdjacentActionWindows(
                "attack_of_opportunity",
                new BattleEventOccurrence(31, new ActionResolvedEvent("actor", "sprint")),
                ReactionEligibilityPolicy.attackOfOpportunity()).eligible().isEmpty());
    }

    @Test
    void qualifierDrivenManeuverUsesSameOccurrencePipeline() {
        BattleRuntimeState state = battle(new GridCoord(0, 0), new GridCoord(1, 0));
        RuntimeReactionWindowResolver resolver = new RuntimeReactionWindowResolver(state);

        RuntimeReactionWindowResolver.Resolution dirtyTrick = resolver.discoverAdjacentActionWindows(
                "attack_of_opportunity",
                new BattleEventOccurrence(40, new ActionResolvedEvent("actor", "maneuver", "dirty trick")),
                ReactionEligibilityPolicy.attackOfOpportunity());
        RuntimeReactionWindowResolver.Resolution sprint = resolver.discoverAdjacentActionWindows(
                "attack_of_opportunity",
                new BattleEventOccurrence(41, new ActionResolvedEvent("actor", "maneuver", "sprint")),
                ReactionEligibilityPolicy.attackOfOpportunity());

        assertEquals(RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_NON_TARGETING_MANEUVER,
                dirtyTrick.eligible().get(0).window().triggerKind());
        assertTrue(sprint.eligible().isEmpty());
    }

    private static BattleRuntimeState battle(GridCoord reactorPosition, GridCoord actorPosition) {
        List<RuntimeCombatantState> combatants = List.of(
                combatant("reactor", reactorPosition), combatant("actor", actorPosition));
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(),
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
