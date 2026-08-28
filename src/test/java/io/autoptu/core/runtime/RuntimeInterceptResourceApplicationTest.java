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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInterceptResourceApplicationTest {
    @Test
    void preparedSuccessConsumesOnePreparedAndOneCoachingToken() {
        RuntimeCombatantState interceptor = combatant("ally");
        interceptor.temporaryEffects().add("intercept_ready", Map.of("source", "first"));
        interceptor.temporaryEffects().add("intercept_ready", Map.of("source", "second"));
        interceptor.temporaryEffects().add("coaching_intercept", Map.of("source", "first"));
        interceptor.temporaryEffects().add("coaching_intercept", Map.of("source", "second"));
        interceptor.temporaryEffects().add("unrelated");

        RuntimeInterceptResourceApplication.Result result = RuntimeInterceptResourceApplication.apply(
                state(interceptor),
                new InterceptCandidateDiscoveryResolution.Candidate("ally", "Intercept", false)
        );

        assertEquals(RuntimeInterceptResourceApplication.SourceKind.PREPARED, result.sourceKind());
        assertTrue(result.interceptReadyConsumed());
        assertTrue(result.coachingConsumed());
        assertFalse(result.baseShiftConsumed());
        assertFalse(result.extraShiftConsumed());
        assertEquals(0, result.damageReduction());
        assertEquals("", result.damageReductionSource());
        assertEquals(1, interceptor.temporaryEffects().count("intercept_ready"));
        assertEquals(1, interceptor.temporaryEffects().count("coaching_intercept"));
        assertEquals(1, interceptor.temporaryEffects().count("unrelated"));
        assertTrue(interceptor.actionBudget().hasActionAvailable(ActionType.SHIFT));
    }

    @Test
    void sentinelConsumesBaseShiftFirstAndRetainsStance() {
        RuntimeCombatantState interceptor = combatant("ally");
        interceptor.temporaryEffects().add("sentinel_stance");
        interceptor.temporaryEffects().add("coaching_intercept");
        interceptor.actionBudget().grantExtra(ActionType.SHIFT, 2);

        RuntimeInterceptResourceApplication.Result result = RuntimeInterceptResourceApplication.apply(
                state(interceptor),
                new InterceptCandidateDiscoveryResolution.Candidate("ally", "Sentinel Stance", true)
        );

        assertEquals(RuntimeInterceptResourceApplication.SourceKind.SENTINEL_STANCE, result.sourceKind());
        assertFalse(result.interceptReadyConsumed());
        assertTrue(result.coachingConsumed());
        assertTrue(result.baseShiftConsumed());
        assertFalse(result.extraShiftConsumed());
        assertEquals(5, result.damageReduction());
        assertEquals("Sentinel Stance", result.damageReductionSource());
        assertEquals(1, interceptor.temporaryEffects().count("sentinel_stance"));
        assertEquals(0, interceptor.temporaryEffects().count("coaching_intercept"));
        assertFalse(interceptor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(2, interceptor.actionBudget().extraCount(ActionType.SHIFT));
    }

    @Test
    void sentinelConsumesExactlyOneExtraShiftWhenBaseBucketAlreadySpent() {
        RuntimeCombatantState interceptor = combatant("ally");
        interceptor.temporaryEffects().add("sentinel_stance");
        interceptor.actionBudget().markAction(ActionType.SHIFT, "ordinary shift");
        interceptor.actionBudget().grantExtra(ActionType.SHIFT, 2);

        RuntimeInterceptResourceApplication.Result result = RuntimeInterceptResourceApplication.apply(
                state(interceptor),
                new InterceptCandidateDiscoveryResolution.Candidate("ally", "Sentinel Stance", true)
        );

        assertFalse(result.baseShiftConsumed());
        assertTrue(result.extraShiftConsumed());
        assertEquals(1, interceptor.actionBudget().extraCount(ActionType.SHIFT));
        assertEquals("ordinary shift", interceptor.actionBudget().consumedDetail(ActionType.SHIFT).orElseThrow());
        assertEquals(1, interceptor.temporaryEffects().count("sentinel_stance"));
    }

    @Test
    void weaponizeConsumesOnlyCoachingAndNoMovementAction() {
        RuntimeCombatantState interceptor = combatant("ally");
        interceptor.temporaryEffects().add("coaching_intercept");
        interceptor.actionBudget().grantExtra(ActionType.SHIFT);

        RuntimeInterceptResourceApplication.Result result = RuntimeInterceptResourceApplication.apply(
                state(interceptor),
                new InterceptCandidateDiscoveryResolution.Candidate("ally", "Weaponize", false)
        );

        assertEquals(RuntimeInterceptResourceApplication.SourceKind.WEAPONIZE, result.sourceKind());
        assertTrue(result.coachingConsumed());
        assertTrue(interceptor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, interceptor.actionBudget().extraCount(ActionType.SHIFT));
        assertEquals(0, result.damageReduction());
    }

    @Test
    void stalePreparedCandidateFailsBeforeConsumingCoaching() {
        RuntimeCombatantState interceptor = combatant("ally");
        interceptor.temporaryEffects().add("coaching_intercept");

        assertThrows(IllegalStateException.class, () -> RuntimeInterceptResourceApplication.apply(
                state(interceptor),
                new InterceptCandidateDiscoveryResolution.Candidate("ally", "Intercept", false)
        ));

        assertEquals(1, interceptor.temporaryEffects().count("coaching_intercept"));
    }

    @Test
    void staleSentinelCandidateFailsBeforeConsumingCoaching() {
        RuntimeCombatantState interceptor = combatant("ally");
        interceptor.temporaryEffects().add("sentinel_stance");
        interceptor.temporaryEffects().add("coaching_intercept");
        interceptor.actionBudget().markAction(ActionType.SHIFT, "ordinary shift");

        assertThrows(IllegalStateException.class, () -> RuntimeInterceptResourceApplication.apply(
                state(interceptor),
                new InterceptCandidateDiscoveryResolution.Candidate("ally", "Sentinel Stance", true)
        ));

        assertEquals(1, interceptor.temporaryEffects().count("sentinel_stance"));
        assertEquals(1, interceptor.temporaryEffects().count("coaching_intercept"));
    }

    private static RuntimeCombatantState combatant(String id) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 6),
                20,
                20,
                new ActionBudget()
        );
    }

    private static BattleRuntimeState state(RuntimeCombatantState combatant) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(combatant)
        );
    }
}
