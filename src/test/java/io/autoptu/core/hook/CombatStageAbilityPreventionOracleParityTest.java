package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.CombatStageAbilityPreventionResolution;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatStageMutationResult;
import io.autoptu.core.runtime.CombatStageMutationService;
import io.autoptu.core.runtime.CombatantAffiliationState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CombatStageAbilityPreventionOracleParityTest {
    @Test
    void oracleContractIsFrozen() throws IOException {
        String oracle = System.getenv("AUTOPTU_COMBAT_STAGE_ABILITY_PREVENTION_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "combat-stage ability prevention fixture not configured");
        Map<String, String> values = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Path.of(oracle));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            values.put(fields[0], fields[1]);
        }
        for (String key : List.of(
                "big_pecks_def_only", "hyper_cutter_atk_only", "clear_body_external_only",
                "full_metal_body_external_only", "white_smoke_external_only", "all_negative_only",
                "all_suppression_guarded", "all_emit_combat_stage_block", "all_return_before_mutation",
                "python_priority_order", "big_pecks_has_no_external_guard", "hyper_cutter_has_no_external_guard",
                "event_actor_is_target", "event_target_is_attacker")) {
            assertEquals("1", values.get(key), key);
        }
    }

    @Test
    void bigPecksAndHyperCutterBlockMatchingSelfDropsButNotOtherStats() {
        CombatStageMutationResult defense = mutate("Big Pecks", "target", CombatStat.DEF, -1);
        assertBlockedBy(defense, "Big Pecks");
        assertEquals(-1, mutate("Big Pecks", "target", CombatStat.ATK, -1).finalStage());

        CombatStageMutationResult attack = mutate("Hyper Cutter", "target", CombatStat.ATK, -1);
        assertBlockedBy(attack, "Hyper Cutter");
        assertEquals(-1, mutate("Hyper Cutter", "target", CombatStat.DEF, -1).finalStage());
    }

    @Test
    void clearBodyFullMetalBodyAndWhiteSmokeBlockOnlyExternalDrops() {
        for (String ability : List.of("Clear Body", "Full Metal Body", "White Smoke")) {
            CombatStageMutationResult external = mutate(ability, "attacker", CombatStat.SPATK, -2);
            assertBlockedBy(external, ability);
            assertEquals(-1, mutate(ability, "target", CombatStat.SPATK, -1).finalStage(), ability);
        }
    }

    @Test
    void pythonPriorityIsPreservedWhenTargetHasSeveralBlockers() {
        BattleRuntimeState state = battle(List.of("Full Metal Body", "Clear Body", "Big Pecks", "White Smoke"));
        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("attacker", "target", "Growl", CombatStat.DEF, -1, "fixture");
        assertBlockedBy(result, "Big Pecks");
    }

    @Test
    void suppressionAndPositiveChangesBypassThisFamily() {
        assertTrue(CombatStageAbilityPreventionResolution.blockingAbility(
                List.of("Big Pecks", "Clear Body"), CombatStat.DEF, -1, true, true).isEmpty());
        assertTrue(CombatStageAbilityPreventionResolution.blockingAbility(
                List.of("Clear Body"), CombatStat.DEF, 1, true, false).isEmpty());
        assertEquals("Clear Body", CombatStageAbilityPreventionResolution.blockingAbility(
                List.of("Clear Body"), CombatStat.DEF, -1, true, false).orElseThrow());
    }

    private static CombatStageMutationResult mutate(String ability, String attackerId, CombatStat stat, int delta) {
        return CombatStageMutationService.authoritative(battle(List.of(ability)))
                .apply(attackerId, "target", "Fixture Move", stat, delta, "fixture");
    }

    private static void assertBlockedBy(CombatStageMutationResult result, String ability) {
        assertEquals(0, result.baseAppliedDelta(), ability);
        assertEquals(0, result.finalStage(), ability);
        assertEquals(1, result.events().size(), ability);
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals(ability, event.sourceName());
        assertEquals("target", event.actorId());
        assertEquals("combat_stage_block", event.effect());
    }

    private static BattleRuntimeState battle(List<String> targetAbilities) {
        RuntimeCombatantState attacker = combatant("attacker", List.of());
        RuntimeCombatantState target = combatant("target", targetAbilities);
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(attacker, target), Map.of(), Map.of(), Map.of(),
                Map.of(
                        "attacker", CombatantAffiliationState.active("red"),
                        "target", CombatantAffiliationState.active("blue")
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id, MovementProfile.walking(new GridCoord(id.equals("attacker") ? 1 : 2, 1), 3),
                20, 20, new ActionBudget(), null, null, 0,
                false, false, false, false, List.of(), List.of(), abilities
        );
    }
}
