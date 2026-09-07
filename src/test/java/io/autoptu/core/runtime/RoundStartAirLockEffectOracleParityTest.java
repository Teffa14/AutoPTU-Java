package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.model.MovementGrid;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundStartAirLockEffectOracleParityTest {
    @Test
    void airLockEventsAndFinalWeatherMatchPinnedPython() throws IOException {
        Path fixture = Path.of("build/oracle/round-start-ability-dispatch.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(2, 2, Set.of(), Map.of()),
                List.of()
        );
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState(
                expected.get("WEATHER"), "", Set.of(), Map.of()
        ));

        ArrayList<RoundStartAbilityDispatchPlan.Invocation> invocations = new ArrayList<>();
        for (String holder : expected.get("AIR_LOCK_HOLDERS").split(",")) {
            invocations.add(new RoundStartAbilityDispatchPlan.Invocation(
                    RoundStartAbilityDispatchPlan.AIR_LOCK,
                    RoundStartAbilityDispatchPlan.Scope.ABILITY_HOLDER,
                    holder
            ));
        }
        var results = RoundStartAbilityInvocationExecutor.execute(
                invocations,
                state,
                RoundStartAbilityEffectRegistry.pythonParityBuiltins()
        );

        ArrayList<String> actualEvents = new ArrayList<>();
        for (var result : results) {
            assertTrue(result.handled());
            for (var rawEvent : result.events()) {
                AbilityEvent event = (AbilityEvent) rawEvent;
                actualEvents.add(String.join("|",
                        event.actorId(),
                        event.ability(),
                        event.effect(),
                        String.valueOf(event.details().get("weather")),
                        event.description()
                ));
            }
        }

        assertEquals(expected.get("AIR_LOCK_EVENTS"), String.join(";", actualEvents));
        assertEquals(expected.get("WEATHER_AFTER"), state.environment().weather());
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid fixture row: " + line);
            result.put(parts[0], parts[1]);
        }
        return Map.copyOf(result);
    }
}
