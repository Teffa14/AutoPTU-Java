package io.autoptu.core.runtime;

import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.random.PythonRandom;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInterceptCheckApplicationTest {
    @Test
    void consumesExactlyOneBattleOwnedPythonD20AndAppliesCheckArithmetic() {
        long seed = 918273645L;
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(),
                seed
        );
        PythonRandom expected = new PythonRandom(seed);
        int expectedRoll = expected.randIntInclusive(1, 20);

        InterceptCheckResolution.Result result = RuntimeInterceptCheckApplication.resolve(
                state,
                new RuntimeInterceptCheckApplication.Input(3, 2, 4, 1, 2, false)
        );

        assertEquals(expectedRoll, result.roll());
        assertEquals(4, result.skillBonus());
        assertEquals(expectedRoll + 7, result.total());
        assertEquals(9, result.dc());
        assertEquals(expectedRoll >= 2, result.success());

        assertEquals(
                expected.randIntInclusive(1, 20),
                state.delayedHitStateFromRuntime().randomFromRuntime().randIntInclusive(1, 20),
                "interception must advance the same continuous battle RNG stream exactly once"
        );
    }

    @Test
    void coachingStillConsumesTheD20BeforeAutomaticSuccess() {
        long seed = 7L;
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(2, 2, Set.of(), Map.of()),
                List.of(),
                seed
        );
        PythonRandom expected = new PythonRandom(seed);
        int expectedRoll = expected.randIntInclusive(1, 20);

        InterceptCheckResolution.Result result = RuntimeInterceptCheckApplication.resolve(
                state,
                new RuntimeInterceptCheckApplication.Input(99, 0, 0, 0, 0, true)
        );

        assertEquals(expectedRoll, result.roll());
        assertTrue(result.success());
        assertTrue(result.coachingAutomaticSuccess());
        assertEquals(
                expected.randIntInclusive(1, 20),
                state.delayedHitStateFromRuntime().randomFromRuntime().randIntInclusive(1, 20)
        );
    }

    @Test
    void adaptersCannotCallTheRngConsumingBoundaryPublicly() throws Exception {
        Method method = RuntimeInterceptCheckApplication.class.getDeclaredMethod(
                "resolve",
                BattleRuntimeState.class,
                RuntimeInterceptCheckApplication.Input.class
        );
        assertFalse(Modifier.isPublic(method.getModifiers()));
    }
}
