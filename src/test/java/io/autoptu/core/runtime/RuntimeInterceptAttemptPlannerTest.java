package io.autoptu.core.runtime;

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

class RuntimeInterceptAttemptPlannerTest {
    @Test
    void discoversAndOrdersPreparedCandidatesInsideCore() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 4);
        RuntimeCombatantState target = combatant("target", 4, 4);
        RuntimeCombatantState far = combatant("far", 1, 1);
        RuntimeCombatantState close = combatant("close", 4, 3);
        far.temporaryEffects().add("intercept_ready", Map.of(
                "ally", "target", "intercept_kind", "ranged", "source", "Intercept"
        ));
        close.temporaryEffects().add("intercept_ready", Map.of(
                "ally", "target", "intercept_kind", "ranged", "source", "Intercept"
        ));

        BattleRuntimeState state = state(List.of(attacker, target, far, close));
        Map<String, CombatantRuleContent> content = Map.of(
                "target", new CombatantRuleContent(List.of(), 5, "trainer-a"),
                "far", new CombatantRuleContent(List.of(), 5, "trainer-a"),
                "close", new CombatantRuleContent(List.of(), 5, "trainer-a")
        );

        RuntimeInterceptAttemptPlanner.Result result = RuntimeInterceptAttemptPlanner.plan(
                state, "attacker", "target", "ranged", content
        );

        assertEquals(2, result.discovery().candidates().size());
        assertEquals(List.of("close", "far"), result.attempts().stream()
                .map(attempt -> attempt.candidate().combatantId())
                .toList());
        assertEquals(content.get("close"), result.attempts().get(0).interceptorContent());
    }

    @Test
    void activeNoInterceptSuppressesAttemptMaterialization() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 0);
        RuntimeCombatantState target = combatant("target", 2, 0);
        RuntimeCombatantState ally = combatant("ally", 2, 1);
        attacker.temporaryEffects().add("no_intercept", Map.of());
        ally.temporaryEffects().add("intercept_ready", Map.of(
                "ally", "target", "intercept_kind", "melee"
        ));

        RuntimeInterceptAttemptPlanner.Result result = RuntimeInterceptAttemptPlanner.plan(
                state(List.of(attacker, target, ally)),
                "attacker",
                "target",
                "melee",
                Map.of(
                        "target", new CombatantRuleContent(List.of(), 5, "trainer-a"),
                        "ally", new CombatantRuleContent(List.of(), 5, "trainer-a")
                )
        );

        assertTrue(result.discovery().suppressedByNoIntercept());
        assertTrue(result.attempts().isEmpty());
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 8),
                20,
                20,
                new ActionBudget()
        );
    }

    private static BattleRuntimeState state(List<RuntimeCombatantState> combatants) {
        Map<String, CombatantAffiliationState> affiliations = new java.util.LinkedHashMap<>();
        for (RuntimeCombatantState combatant : combatants) {
            affiliations.put(
                    combatant.combatantId(),
                    CombatantAffiliationState.active(combatant.combatantId().equals("attacker") ? "red" : "blue")
            );
        }
        return new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                combatants,
                Map.of(),
                Map.of(),
                Map.of(),
                affiliations
        );
    }
}
