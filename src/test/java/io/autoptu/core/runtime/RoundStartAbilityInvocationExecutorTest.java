package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.model.MovementGrid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundStartAbilityInvocationExecutorTest {
    @Test
    void airLockEmitsSuppressionEventWithoutChangingCanonicalWeather() {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(2, 2, Set.of(), Map.of()),
                List.of()
        );
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState("Rain", "", Set.of(), Map.of()));

        List<RoundStartAbilityDispatchPlan.Invocation> invocations = List.of(
                new RoundStartAbilityDispatchPlan.Invocation(
                        RoundStartAbilityDispatchPlan.AIR_LOCK,
                        RoundStartAbilityDispatchPlan.Scope.ABILITY_HOLDER,
                        "air-two"
                ),
                new RoundStartAbilityDispatchPlan.Invocation(
                        RoundStartAbilityDispatchPlan.ARENA_TRAP,
                        RoundStartAbilityDispatchPlan.Scope.GLOBAL,
                        ""
                )
        );

        var results = RoundStartAbilityInvocationExecutor.execute(
                invocations,
                state,
                RoundStartAbilityEffectRegistry.pythonParityBuiltins()
        );

        assertEquals(2, results.size());
        assertTrue(results.get(0).handled());
        assertEquals(1, results.get(0).events().size());
        AbilityEvent event = (AbilityEvent) results.get(0).events().get(0);
        assertEquals("air-two", event.actorId());
        assertEquals("Air Lock", event.ability());
        assertEquals("weather_suppress", event.effect());
        assertEquals("Rain", event.details().get("weather"));
        assertEquals("Air Lock suppresses the active weather.", event.description());
        assertEquals("Rain", state.environment().weather());

        assertFalse(results.get(1).handled());
        assertTrue(results.get(1).events().isEmpty());
    }
}
