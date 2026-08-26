package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStageStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombatStageSevenStatMutationBoundaryTest {
    @Test
    void mutatesAccuracyAndEvasionThroughCanonicalBoundary() {
        BattleRuntimeState state = battle(List.of(), List.of());
        CombatStageMutationService service = CombatStageMutationService.authoritative(state);

        CombatStageMutationResult accuracy = service.apply(
                "target", "target", "Fixture", CombatStageStat.ACCURACY, 2, "fixture"
        );
        CombatStageMutationResult evasion = service.apply(
                "target", "target", "Fixture", CombatStageStat.EVASION, -9, "fixture"
        );

        assertEquals(2, accuracy.finalStage());
        assertEquals(-6, evasion.finalStage());
        assertEquals(2, state.requireCombatant("target").combatStages().get(CombatStageStat.ACCURACY));
        assertEquals(-6, state.requireCombatant("target").combatStages().get(CombatStageStat.EVASION));
    }

    @Test
    void genericExternalDropPreventionAlsoProtectsEvasion() {
        BattleRuntimeState state = battle(List.of(), List.of("Clear Body"));

        CombatStageMutationResult result = CombatStageMutationService.authoritative(state).apply(
                "attacker", "target", "Fixture", CombatStageStat.EVASION, -1, "fixture"
        );

        assertEquals(0, result.finalStage());
        assertEquals(0, state.requireCombatant("target").combatStages().get(CombatStageStat.EVASION));
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("Clear Body", event.sourceName());
        assertEquals("combat_stage_block", event.effect());
    }

    @Test
    void postApplyReactionsReceiveAccuracyIdentity() {
        BattleRuntimeState state = battle(List.of(), List.of("Simple"));

        CombatStageMutationResult result = CombatStageMutationService.authoritative(state).apply(
                "target", "target", "Fixture", CombatStageStat.ACCURACY, 1, "fixture"
        );

        assertEquals(2, result.finalStage());
        assertEquals(2, state.requireCombatant("target").combatStages().get(CombatStageStat.ACCURACY));
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("Simple", event.sourceName());
        assertEquals("simple", event.effect());
    }

    @Test
    void mirrorArmorReflectsEvasionThroughSameRecursiveBoundary() {
        BattleRuntimeState state = battle(List.of(), List.of("Mirror Armor"));

        CombatStageMutationResult result = CombatStageMutationService.authoritative(state).apply(
                "attacker", "target", "Fixture", CombatStageStat.EVASION, -1, "fixture"
        );

        assertEquals(0, result.finalStage());
        assertEquals(0, state.requireCombatant("target").combatStages().get(CombatStageStat.EVASION));
        assertEquals(-1, state.requireCombatant("attacker").combatStages().get(CombatStageStat.EVASION));
        RuleEffectEvent reflect = (RuleEffectEvent) result.events().getFirst();
        assertEquals("Mirror Armor", reflect.sourceName());
        assertEquals("reflect", reflect.effect());
    }

    private static BattleRuntimeState battle(List<String> attackerAbilities, List<String> targetAbilities) {
        RuntimeCombatantState attacker = combatant("attacker", 1, attackerAbilities);
        RuntimeCombatantState target = combatant("target", 2, targetAbilities);
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(attacker, target), Map.of(), Map.of(), Map.of(),
                Map.of(
                        "attacker", CombatantAffiliationState.active("red"),
                        "target", CombatantAffiliationState.active("blue")
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, 1), 3),
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
