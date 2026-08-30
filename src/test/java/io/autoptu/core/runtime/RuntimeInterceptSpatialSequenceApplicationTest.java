package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInterceptSpatialSequenceApplicationTest {
    @Test
    void unreachableSelectedCandidateAbortsWithoutTryingLaterCandidate() {
        RuntimeCombatantState target = combatant("target", 3, 1, 6);
        RuntimeCombatantState first = combatant("first", 0, 8, 0);
        RuntimeCombatantState second = combatant("second", 1, 1, 6);
        first.temporaryEffects().add("intercept_ready");
        second.temporaryEffects().add("intercept_ready");
        second.temporaryEffects().add("coaching_intercept");
        BattleRuntimeState state = state(List.of(target, first, second), Set.of(), 12345L);

        RuntimeInterceptSpatialSequenceApplication.Result result = RuntimeInterceptSpatialSequenceApplication.apply(
                state,
                "target",
                false,
                List.of(new GridCoord(2, 1)),
                List.of(attempt("first"), attempt("second"))
        );

        assertFalse(result.intercepted());
        assertEquals("target", result.replacementTargetId());
        assertTrue(result.sequence().attemptedCandidates().isEmpty());
        assertTrue(first.temporaryEffects().has("intercept_ready"));
        assertTrue(second.temporaryEffects().has("intercept_ready"));
        assertTrue(second.temporaryEffects().has("coaching_intercept"));
        assertEquals(new GridCoord(0, 8), first.position());
        assertEquals(new GridCoord(1, 1), second.position());
        assertEquals(new GridCoord(3, 1), target.position());
        assertNull(result.interceptMovement());
        assertNull(result.meleeMovement());
    }

    @Test
    void failedSelectedCheckConsumesItsResourceAndDoesNotTryLaterCandidate() {
        RuntimeCombatantState target = combatant("target", 3, 1, 6);
        RuntimeCombatantState first = combatant("first", 0, 0, 8);
        RuntimeCombatantState second = combatant("second", 1, 1, 6);
        first.temporaryEffects().add("intercept_ready");
        second.temporaryEffects().add("intercept_ready");
        second.temporaryEffects().add("coaching_intercept");
        BattleRuntimeState state = state(List.of(target, first, second), Set.of(), 777L);

        RuntimeInterceptSpatialSequenceApplication.Result result = RuntimeInterceptSpatialSequenceApplication.apply(
                state,
                "target",
                false,
                List.of(new GridCoord(8, 0)),
                List.of(attempt("first"), attempt("second"))
        );

        assertFalse(result.intercepted());
        assertEquals(1, result.sequence().attemptedCandidates().size());
        assertEquals("first", result.sequence().attemptedCandidates().get(0).interceptorId());
        assertFalse(first.temporaryEffects().has("intercept_ready"));
        assertTrue(second.temporaryEffects().has("intercept_ready"));
        assertTrue(second.temporaryEffects().has("coaching_intercept"));
        assertEquals(new GridCoord(0, 0), first.position());
        assertEquals(new GridCoord(1, 1), second.position());
        assertNull(result.interceptMovement());
        assertNull(result.meleeMovement());
    }

    @Test
    void meleeWinnerUsesResolvedLineTileThenSharedPushAndPythonAnchor() {
        RuntimeCombatantState target = combatant("target", 3, 1, 6);
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1, 6);
        interceptor.temporaryEffects().add("intercept_ready");
        interceptor.temporaryEffects().add("coaching_intercept");
        BattleRuntimeState state = state(List.of(target, interceptor), Set.of(), 222L);

        RuntimeInterceptSpatialSequenceApplication.Result result = RuntimeInterceptSpatialSequenceApplication.apply(
                state,
                "target",
                true,
                List.of(new GridCoord(2, 1)),
                List.of(attempt("interceptor"))
        );

        assertTrue(result.intercepted());
        assertEquals("interceptor", result.replacementTargetId());
        assertEquals(1, result.meleeMovement().targetPush().requestedDistance());
        assertEquals(1, result.meleeMovement().targetPush().movedDistance());
        assertEquals(new GridCoord(3, 1), interceptor.position());
        assertEquals(new GridCoord(4, 1), target.position());
    }

    @Test
    void blockedMeleePushKeepsSharedPartialStopAfterServerOwnedPositionResolution() {
        RuntimeCombatantState target = combatant("target", 3, 1, 6);
        RuntimeCombatantState interceptor = combatant("interceptor", 1, 1, 6);
        interceptor.temporaryEffects().add("intercept_ready");
        interceptor.temporaryEffects().add("coaching_intercept");
        BattleRuntimeState state = state(
                List.of(target, interceptor),
                Set.of(new GridCoord(4, 1)),
                333L
        );

        RuntimeInterceptSpatialSequenceApplication.Result result = RuntimeInterceptSpatialSequenceApplication.apply(
                state,
                "target",
                true,
                List.of(new GridCoord(2, 1)),
                List.of(attempt("interceptor"))
        );

        assertEquals(0, result.meleeMovement().targetPush().movedDistance());
        assertTrue(result.meleeMovement().targetPush().stoppedEarly());
        assertEquals(new GridCoord(3, 1), target.position());
        assertEquals(new GridCoord(3, 1), interceptor.position());
    }

    @Test
    void noReachableSelectedCandidateDoesNotRollConsumeResourcesOrMove() {
        RuntimeCombatantState target = combatant("target", 3, 1, 6);
        RuntimeCombatantState first = combatant("first", 0, 8, 0);
        RuntimeCombatantState second = combatant("second", 8, 8, 0);
        first.temporaryEffects().add("intercept_ready");
        second.temporaryEffects().add("intercept_ready");
        BattleRuntimeState state = state(List.of(target, first, second), Set.of(), 444L);

        RuntimeInterceptSpatialSequenceApplication.Result result = RuntimeInterceptSpatialSequenceApplication.apply(
                state,
                "target",
                true,
                List.of(new GridCoord(2, 1)),
                List.of(attempt("first"), attempt("second"))
        );

        assertFalse(result.intercepted());
        assertEquals("target", result.replacementTargetId());
        assertTrue(result.sequence().attemptedCandidates().isEmpty());
        assertTrue(first.temporaryEffects().has("intercept_ready"));
        assertTrue(second.temporaryEffects().has("intercept_ready"));
        assertEquals(new GridCoord(0, 8), first.position());
        assertEquals(new GridCoord(8, 8), second.position());
        assertEquals(new GridCoord(3, 1), target.position());
        assertNull(result.interceptMovement());
        assertNull(result.meleeMovement());
    }

    @Test
    void spatialAttemptBoundaryRejectsPrecomputedCheckAndPosition() {
        assertTrue(Arrays.stream(RuntimeInterceptSpatialSequenceApplication.Attempt.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(RuntimeInterceptCheckApplication.Input.class)));
        assertTrue(Arrays.stream(RuntimeInterceptSpatialSequenceApplication.Attempt.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(GridCoord.class)));
        assertTrue(Arrays.stream(RuntimeInterceptSpatialSequenceApplication.Attempt.class.getRecordComponents())
                .anyMatch(component -> component.getType().equals(CombatantRuleContent.class)));
    }

    @Test
    void adaptersCannotInvokeSpatialSequenceBoundaryPublicly() throws Exception {
        Method method = RuntimeInterceptSpatialSequenceApplication.class.getDeclaredMethod(
                "apply",
                BattleRuntimeState.class,
                String.class,
                boolean.class,
                Collection.class,
                List.class
        );
        assertFalse(Modifier.isPublic(method.getModifiers()));
    }

    private static RuntimeInterceptSpatialSequenceApplication.Attempt attempt(String interceptorId) {
        return new RuntimeInterceptSpatialSequenceApplication.Attempt(
                new InterceptCandidateDiscoveryResolution.Candidate(interceptorId, "Intercept", false),
                CombatantRuleContent.empty()
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y, int overland) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), overland),
                20,
                20,
                new ActionBudget()
        );
    }

    private static BattleRuntimeState state(
            List<RuntimeCombatantState> combatants,
            Set<GridCoord> blocked,
            long seed
    ) {
        return new BattleRuntimeState(
                new MovementGrid(9, 9, blocked, Map.of()),
                combatants,
                seed
        );
    }
}
