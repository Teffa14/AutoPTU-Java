package io.autoptu.core.runtime;

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

class RuntimeInterceptCandidateSequenceApplicationTest {
    @Test
    void failedCandidateConsumesItsResourceThenNextSuccessReplacesTarget() {
        RuntimeCombatantState target = combatant("target");
        RuntimeCombatantState first = combatant("first");
        RuntimeCombatantState second = combatant("second");
        first.temporaryEffects().add("intercept_ready", Map.of("source", "Intercept"));
        second.temporaryEffects().add("intercept_ready", Map.of("source", "Intercept"));
        second.temporaryEffects().add("coaching_intercept");
        BattleRuntimeState state = state(List.of(target, first, second), 12345L);

        RuntimeInterceptCandidateSequenceApplication.Result result = RuntimeInterceptCandidateSequenceApplication.apply(
                state,
                "target",
                List.of(
                        attempt("first", 99, false),
                        attempt("second", 1, true)
                )
        );

        assertTrue(result.intercepted());
        assertEquals("second", result.replacementTargetId());
        assertEquals(List.of("first", "second"), result.attemptedCandidates().stream()
                .map(RuntimeInterceptCandidateSequenceApplication.AttemptResult::interceptorId)
                .toList());
        assertFalse(result.attemptedCandidates().get(0).attempt().check().success());
        assertTrue(result.attemptedCandidates().get(1).attempt().check().success());
        assertEquals(0, first.temporaryEffects().count("intercept_ready"));
        assertEquals(0, second.temporaryEffects().count("intercept_ready"));
        assertEquals(0, second.temporaryEffects().count("coaching_intercept"));
    }

    @Test
    void firstSuccessStopsBeforeLaterCandidateCanConsumeAnything() {
        RuntimeCombatantState target = combatant("target");
        RuntimeCombatantState first = combatant("first");
        RuntimeCombatantState later = combatant("later");
        first.temporaryEffects().add("intercept_ready");
        first.temporaryEffects().add("coaching_intercept");
        later.temporaryEffects().add("intercept_ready");
        BattleRuntimeState state = state(List.of(target, first, later), 222L);

        RuntimeInterceptCandidateSequenceApplication.Result result = RuntimeInterceptCandidateSequenceApplication.apply(
                state,
                "target",
                List.of(
                        attempt("first", 1, true),
                        attempt("later", 99, false)
                )
        );

        assertEquals("first", result.replacementTargetId());
        assertEquals(1, result.attemptedCandidates().size());
        assertEquals(0, first.temporaryEffects().count("intercept_ready"));
        assertEquals(0, first.temporaryEffects().count("coaching_intercept"));
        assertEquals(1, later.temporaryEffects().count("intercept_ready"));
    }

    @Test
    void allFailuresKeepOriginalTargetAfterOrderedMutations() {
        RuntimeCombatantState target = combatant("target");
        RuntimeCombatantState first = combatant("first");
        RuntimeCombatantState second = combatant("second");
        first.temporaryEffects().add("intercept_ready");
        second.temporaryEffects().add("intercept_ready");
        BattleRuntimeState state = state(List.of(target, first, second), 333L);

        RuntimeInterceptCandidateSequenceApplication.Result result = RuntimeInterceptCandidateSequenceApplication.apply(
                state,
                "target",
                List.of(
                        attempt("first", 99, false),
                        attempt("second", 99, false)
                )
        );

        assertFalse(result.intercepted());
        assertEquals("target", result.replacementTargetId());
        assertEquals(List.of("first", "second"), result.attemptedCandidates().stream()
                .map(RuntimeInterceptCandidateSequenceApplication.AttemptResult::interceptorId)
                .toList());
        assertEquals(0, first.temporaryEffects().count("intercept_ready"));
        assertEquals(0, second.temporaryEffects().count("intercept_ready"));
    }

    @Test
    void adaptersCannotInvokeSequenceBoundaryPublicly() throws Exception {
        Method method = RuntimeInterceptCandidateSequenceApplication.class.getDeclaredMethod(
                "apply",
                BattleRuntimeState.class,
                String.class,
                List.class
        );
        assertFalse(Modifier.isPublic(method.getModifiers()));
    }

    private static RuntimeInterceptCandidateSequenceApplication.Attempt attempt(
            String interceptorId,
            int distance,
            boolean coachingAutomaticSuccess
    ) {
        return new RuntimeInterceptCandidateSequenceApplication.Attempt(
                new InterceptCandidateDiscoveryResolution.Candidate(interceptorId, "Intercept", false),
                new RuntimeInterceptCheckApplication.Input(distance, 0, 0, 0, 0, coachingAutomaticSuccess)
        );
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

    private static BattleRuntimeState state(List<RuntimeCombatantState> combatants, long seed) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                seed
        );
    }
}
