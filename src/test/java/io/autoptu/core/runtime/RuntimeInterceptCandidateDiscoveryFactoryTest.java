package io.autoptu.core.runtime;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInterceptCandidateDiscoveryFactoryTest {
    @Test
    void genericContentOwnsCapabilityAndLoyaltyWithoutAdapterBooleans() {
        CombatantRuleContent content = new CombatantRuleContent(List.of("Living Weapon", "Mountable"), 6, "trainer-a");
        assertTrue(content.hasCapability("living weapon"));
        assertEquals(6, content.loyalty());
        assertEquals("trainer-a", content.controllerId());
    }

    @Test
    void materializesPreparedCandidateFromBattleStateAndCanonicalContent() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 0);
        RuntimeCombatantState target = combatant("target", 2, 0);
        RuntimeCombatantState ally = combatant("ally", 3, 0);
        ally.temporaryEffects().add("intercept_ready", Map.of(
                "ally", "target",
                "intercept_kind", "melee",
                "source", "Intercept"
        ));

        BattleRuntimeState state = state(List.of(attacker, target, ally), Map.of(
                "attacker", CombatantAffiliationState.active("red"),
                "target", CombatantAffiliationState.active("blue"),
                "ally", CombatantAffiliationState.active("blue")
        ), Map.of());

        InterceptCandidateDiscoveryResolution.Result result = InterceptCandidateDiscoveryResolution.resolve(
                RuntimeInterceptCandidateDiscoveryFactory.build(
                        state,
                        "attacker",
                        "target",
                        "melee",
                        Map.of(
                                "target", new CombatantRuleContent(List.of(), 5, "trainer-a"),
                                "ally", new CombatantRuleContent(List.of(), 5, "trainer-a")
                        )
                )
        );

        assertFalse(result.suppressedByNoIntercept());
        assertEquals(List.of(new InterceptCandidateDiscoveryResolution.Candidate("ally", "Intercept", false)), result.candidates());
    }

    @Test
    void loyaltyAndStatusAreDerivedInsideCore() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 0);
        RuntimeCombatantState target = combatant("target", 2, 0);
        RuntimeCombatantState ally = combatant("ally", 3, 0);
        ally.temporaryEffects().add("intercept_ready", Map.of("ally", "target", "intercept_kind", "ranged"));

        BattleRuntimeState state = state(List.of(attacker, target, ally), Map.of(
                "attacker", CombatantAffiliationState.active("red"),
                "target", CombatantAffiliationState.active("blue"),
                "ally", CombatantAffiliationState.active("blue")
        ), Map.of("ally", Set.of("Paralyzed")));

        InterceptCandidateDiscoveryResolution.Result result = InterceptCandidateDiscoveryResolution.resolve(
                RuntimeInterceptCandidateDiscoveryFactory.build(
                        state,
                        "attacker",
                        "target",
                        "ranged",
                        Map.of(
                                "target", new CombatantRuleContent(List.of(), 6, "trainer-a"),
                                "ally", new CombatantRuleContent(List.of(), 6, "trainer-a")
                        )
                )
        );
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void sentinelUsesAuthoritativeExtraShiftCount() {
        RuntimeCombatantState attacker = combatant("attacker", 0, 0);
        RuntimeCombatantState target = combatant("target", 2, 0);
        RuntimeCombatantState ally = combatant("ally", 3, 0);
        ally.temporaryEffects().add("sentinel_stance", Map.of());
        ally.actionBudget().markAction(ActionType.SHIFT, "already shifted");
        ally.actionBudget().grantExtra(ActionType.SHIFT);

        BattleRuntimeState state = state(List.of(attacker, target, ally), Map.of(
                "attacker", CombatantAffiliationState.active("red"),
                "target", CombatantAffiliationState.active("blue"),
                "ally", CombatantAffiliationState.active("blue")
        ), Map.of());

        InterceptCandidateDiscoveryResolution.Result result = InterceptCandidateDiscoveryResolution.resolve(
                RuntimeInterceptCandidateDiscoveryFactory.build(
                        state,
                        "attacker",
                        "target",
                        "melee",
                        Map.of("ally", new CombatantRuleContent(List.of(), null, ""))
                )
        );
        assertEquals(List.of(new InterceptCandidateDiscoveryResolution.Candidate("ally", "Sentinel Stance", true)), result.candidates());
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(id, MovementProfile.walking(new GridCoord(x, y), 6), 20, 20, new ActionBudget());
    }

    private static BattleRuntimeState state(
            List<RuntimeCombatantState> combatants,
            Map<String, CombatantAffiliationState> affiliations,
            Map<String, ? extends java.util.Collection<String>> statuses
    ) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                statuses,
                Map.of(),
                Map.of(),
                affiliations
        );
    }
}
