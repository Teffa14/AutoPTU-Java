package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CombatStageMutationServiceTest {
    @Test
    void appliesBaseMutationAndReportsActualClampedDelta() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of());
        target.combatStages().set(CombatStat.ATK, 5);
        BattleRuntimeState state = state(actor, target);

        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("actor", "target", "Growl", CombatStat.ATK, 3, "raise");

        assertEquals(5, result.startingStage());
        assertEquals(3, result.requestedDelta());
        assertEquals(1, result.baseAppliedDelta());
        assertEquals(6, result.baseStage());
        assertEquals(6, result.finalStage());
        assertEquals(List.of(), result.events());
    }

    @Test
    void simpleReactsToTheActualAppliedDeltaAfterBaseMutation() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of("Simple"));
        BattleRuntimeState state = state(actor, target);

        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("actor", "target", "Test Move", CombatStat.ATK, 1, "fixture");

        assertEquals(0, result.startingStage());
        assertEquals(1, result.baseAppliedDelta());
        assertEquals(1, result.baseStage());
        assertEquals(2, result.finalStage());
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("ability", event.sourceKind());
        assertEquals("Simple", event.sourceName());
        assertEquals("simple", event.effect());
        assertEquals(1.0, event.amount());
    }

    @Test
    void simpleCannotDoublePastThePtuStageClamp() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of("Simple"));
        target.combatStages().set(CombatStat.ATK, 5);
        BattleRuntimeState state = state(actor, target);

        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("actor", "target", "Test Move", CombatStat.ATK, 2, "fixture");

        assertEquals(1, result.baseAppliedDelta());
        assertEquals(6, result.baseStage());
        assertEquals(6, result.finalStage());
        assertEquals(List.of(), result.events());
    }

    @Test
    void negativeStageChangesUseTheSameAuthoritativePipeline() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of("Simple"));
        BattleRuntimeState state = state(actor, target);

        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("actor", "target", "Growl", CombatStat.DEF, -1, "drop");

        assertEquals(-1, result.baseAppliedDelta());
        assertEquals(-1, result.baseStage());
        assertEquals(-2, result.finalStage());
        assertEquals(1, result.events().size());
    }

    @Test
    void invalidIdentityFailsBeforeAnyMutation() {
        RuntimeCombatantState actor = combatant("actor", List.of());
        RuntimeCombatantState target = combatant("target", List.of());
        BattleRuntimeState state = state(actor, target);

        assertThrows(IllegalArgumentException.class, () -> CombatStageMutationService.authoritative(state)
                .apply("missing", "target", "Growl", CombatStat.ATK, -1, "drop"));
        assertEquals(0, target.combatStages().get(CombatStat.ATK));
    }

    private static BattleRuntimeState state(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(
                new MovementGrid(5, 5, Set.of(), Map.of()),
                List.of(combatants)
        );
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(id.equals("actor") ? 1 : 2, 1), 3),
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
                abilities
        );
    }
}
