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
    void rangedAttackWithoutAdjacentTargetOpensAttackOfOpportunityWindow() {
        BattleRuntimeState state = battleWithTarget(new GridCoord(0, 0), new GridCoord(1, 0), new GridCoord(4, 0));
        BattleEventOccurrence occurrence = new BattleEventOccurrence(50,
                ActionResolvedEvent.targeted("actor", "ranged_attack", List.of("target")));

        RuntimeReactionWindowResolver.Resolution resolution = new RuntimeReactionWindowResolver(state)
                .discoverAdjacentActionWindows(
                        "attack_of_opportunity", occurrence, ReactionEligibilityPolicy.attackOfOpportunity());

        assertEquals(List.of("reactor"), resolution.eligible().stream()
                .map(RuntimeReactionWindowResolver.Candidate::reactorId).toList());
        assertEquals(RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET,
                resolution.eligible().get(0).window().triggerKind());
    }

    @Test
    void rangedAttackWithAnyAdjacentTargetSuppressesAttackOfOpportunityWindow() {
        BattleRuntimeState state = battleWithTarget(new GridCoord(0, 0), new GridCoord(1, 0), new GridCoord(2, 0));
        BattleEventOccurrence occurrence = new BattleEventOccurrence(51,
                ActionResolvedEvent.targeted("actor", "ranged_attack", List.of("target")));

        RuntimeReactionWindowResolver.Resolution resolution = new RuntimeReactionWindowResolver(state)
                .discoverAdjacentActionWindows(
                        "attack_of_opportunity", occurrence, ReactionEligibilityPolicy.attackOfOpportunity());

        assertTrue(resolution.eligible().isEmpty());
    }

    @Test
    void rangedAttackUsesAllAuthoritativeTargetsForAdjacencyPredicate() {
        BattleRuntimeState state = battleWithTargets(
                new GridCoord(0, 0), new GridCoord(1, 0),
                new GridCoord(4, 0), new GridCoord(2, 0));
        BattleEventOccurrence occurrence = new BattleEventOccurrence(52,
                ActionResolvedEvent.targeted("actor", "ranged_attack", List.of("far_target", "near_target")));

        RuntimeReactionWindowResolver.Resolution resolution = new RuntimeReactionWindowResolver(state)
                .discoverAdjacentActionWindows(
                        "attack_of_opportunity", occurrence, ReactionEligibilityPolicy.attackOfOpportunity());

        assertTrue(resolution.eligible().isEmpty());
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
        return battleState(
                List.of(combatant("reactor", reactorPosition), combatant("actor", actorPosition)),
                Map.of("reactor", "blue", "actor", "red"));
    }

    private static BattleRuntimeState battleWithTarget(
            GridCoord reactorPosition, GridCoord actorPosition, GridCoord targetPosition
    ) {
        return battleState(
                List.of(combatant("reactor", reactorPosition), combatant("actor", actorPosition),
                        combatant("target", targetPosition)),
                Map.of("reactor", "blue", "actor", "red", "target", "blue"));
    }

    private static BattleRuntimeState battleWithTargets(
            GridCoord reactorPosition, GridCoord actorPosition, GridCoord farTargetPosition, GridCoord nearTargetPosition
    ) {
        return battleState(
                List.of(combatant("reactor", reactorPosition), combatant("actor", actorPosition),
                        combatant("far_target", farTargetPosition), combatant("near_target", nearTargetPosition)),
                Map.of("reactor", "blue", "actor", "red", "far_target", "blue", "near_target", "blue"));
    }

    private static BattleRuntimeState battleState(List<RuntimeCombatantState> combatants, Map<String, String> teams) {
        Map<String, CombatantAffiliationState> affiliations = teams.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> CombatantAffiliationState.active(entry.getValue())));
        Map<String, List<MoveOption>> moves = combatants.stream().collect(
                java.util.stream.Collectors.toMap(RuntimeCombatantState::combatantId,
                        combatant -> List.of(move(combatant.combatantId().equals("reactor")
                                ? "Attack of Opportunity" : "Tackle"))));
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(), affiliations, moves
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(id, MovementProfile.walking(position, 6), 20, 20, new ActionBudget());
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }
}
