package io.autoptu.core.runtime;

import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeReactionTriggerMatcherTest {
    private final RuntimeReactionTriggerMatcher matcher = RuntimeReactionTriggerMatcher.builtin();

    @Test
    void matchesFoeShiftWhoseOriginWasAdjacent() {
        BattleRuntimeState state = battle(
                new GridCoord(0, 0), "Medium", "blue",
                new GridCoord(2, 0), "Medium", "red"
        );
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0));

        RuntimeReactionTriggerMatcher.TriggerMatch match = matcher
                .matchShift("Attack of Opportunity", "reactor", state, event)
                .orElseThrow();

        assertEquals("reactor", match.reactorId());
        assertEquals("actor", match.triggeringActorId());
        assertEquals(RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_SHIFT_AWAY, match.trigger().kind());
    }

    @Test
    void diagonalAdjacencyUsesCanonicalFootprintChebyshevGeometry() {
        BattleRuntimeState state = battle(
                new GridCoord(0, 0), "Medium", "blue",
                new GridCoord(2, 2), "Medium", "red"
        );
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(1, 1), new GridCoord(2, 2));

        assertTrue(matcher.matchShift("attack_of_opportunity", "reactor", state, event).isPresent());
    }

    @Test
    void adjacentOriginStillMatchesWhenDestinationRemainsAdjacent() {
        BattleRuntimeState state = battle(
                new GridCoord(0, 0), "Medium", "blue",
                new GridCoord(0, 1), "Medium", "red"
        );
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(0, 1));

        assertTrue(matcher.matchShift("attack_of_opportunity", "reactor", state, event).isPresent());
    }

    @Test
    void largeFootprintAdjacencyUsesCanonicalGeometryFromBattleState() {
        BattleRuntimeState state = battle(
                new GridCoord(0, 0), "Large", "blue",
                new GridCoord(3, 0), "Medium", "red"
        );
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(2, 0), new GridCoord(3, 0));

        assertTrue(matcher.matchShift("attack_of_opportunity", "reactor", state, event).isPresent());
    }

    @Test
    void nonAdjacentOriginDoesNotMatch() {
        BattleRuntimeState state = battle(
                new GridCoord(0, 0), "Medium", "blue",
                new GridCoord(3, 0), "Medium", "red"
        );
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(2, 0), new GridCoord(3, 0));

        assertTrue(matcher.matchShift("attack_of_opportunity", "reactor", state, event).isEmpty());
    }

    @Test
    void alliedShiftDoesNotMatch() {
        BattleRuntimeState state = battle(
                new GridCoord(0, 0), "Medium", "blue",
                new GridCoord(2, 0), "Medium", "blue"
        );
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0));

        assertTrue(matcher.matchShift("attack_of_opportunity", "reactor", state, event).isEmpty());
    }

    @Test
    void unknownReactionDoesNotMatch() {
        BattleRuntimeState state = battle(
                new GridCoord(0, 0), "Medium", "blue",
                new GridCoord(2, 0), "Medium", "red"
        );
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0));

        assertTrue(matcher.matchShift("future_reaction", "reactor", state, event).isEmpty());
    }

    private static BattleRuntimeState battle(
            GridCoord reactorPosition,
            String reactorSize,
            String reactorTeam,
            GridCoord actorCurrentPosition,
            String actorSize,
            String actorTeam
    ) {
        RuntimeCombatantState reactor = combatant("reactor", reactorPosition);
        RuntimeCombatantState actor = combatant("actor", actorCurrentPosition);
        return new BattleRuntimeState(
                new MovementGrid(12, 12, Set.of(), Map.of()),
                List.of(reactor, actor),
                Map.of(),
                Map.of(),
                Map.of(
                        "reactor", new CombatantGeometryState(reactorSize),
                        "actor", new CombatantGeometryState(actorSize)
                ),
                Map.of(
                        "reactor", CombatantAffiliationState.active(reactorTeam),
                        "actor", CombatantAffiliationState.active(actorTeam)
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
}
