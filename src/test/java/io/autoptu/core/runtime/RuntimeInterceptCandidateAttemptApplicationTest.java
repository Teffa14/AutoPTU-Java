package io.autoptu.core.runtime;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInterceptCandidateAttemptApplicationTest {
    @Test
    void failedPreparedCheckStillConsumesPreparedTokenAfterOneD20() {
        RuntimeCombatantState interceptor = combatant("ally");
        interceptor.temporaryEffects().add("intercept_ready", Map.of("source", "Intercept"));
        BattleRuntimeState state = state(interceptor, 12345L);

        RuntimeInterceptCandidateAttemptApplication.Result result = RuntimeInterceptCandidateAttemptApplication.apply(
                state,
                new InterceptCandidateDiscoveryResolution.Candidate("ally", "Intercept", false),
                new RuntimeInterceptCheckApplication.Input(99, 0, 0, 0, 0, false)
        );

        assertFalse(result.check().success(), "distance 99 cannot pass on a d20 with zero bonuses");
        assertTrue(result.resources().interceptReadyConsumed());
        assertEquals(0, interceptor.temporaryEffects().count("intercept_ready"));
    }

    @Test
    void failedSentinelCheckStillConsumesShiftAndKeepsStance() {
        RuntimeCombatantState interceptor = combatant("ally");
        interceptor.temporaryEffects().add("sentinel_stance");
        interceptor.actionBudget().grantExtra(ActionType.SHIFT);
        BattleRuntimeState state = state(interceptor, 9876L);

        RuntimeInterceptCandidateAttemptApplication.Result result = RuntimeInterceptCandidateAttemptApplication.apply(
                state,
                new InterceptCandidateDiscoveryResolution.Candidate("ally", "Sentinel Stance", true),
                new RuntimeInterceptCheckApplication.Input(99, 0, 0, 0, 0, false)
        );

        assertFalse(result.check().success());
        assertTrue(result.resources().baseShiftConsumed());
        assertFalse(result.resources().extraShiftConsumed());
        assertFalse(interceptor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, interceptor.actionBudget().extraCount(ActionType.SHIFT));
        assertEquals(1, interceptor.temporaryEffects().count("sentinel_stance"));
        assertEquals(5, result.resources().damageReduction());
    }

    @Test
    void adaptersCannotInvokeAttemptMutationBoundaryPublicly() throws Exception {
        Method method = RuntimeInterceptCandidateAttemptApplication.class.getDeclaredMethod(
                "apply",
                BattleRuntimeState.class,
                InterceptCandidateDiscoveryResolution.Candidate.class,
                RuntimeInterceptCheckApplication.Input.class
        );
        assertFalse(Modifier.isPublic(method.getModifiers()));
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

    private static BattleRuntimeState state(RuntimeCombatantState combatant, long seed) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(combatant),
                seed
        );
    }
}
