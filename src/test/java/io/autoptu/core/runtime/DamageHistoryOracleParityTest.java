package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageHistoryOracleParityTest {
    @Test
    void exchangeAndAccumulationContractMatchesPinnedPythonBehavior() throws IOException {
        String fixturePath = System.getProperty("autoptu.damage.history.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());

        Map<String, String> fixture = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(fixturePath))) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            fixture.put(parts[0], parts.length > 1 ? parts[1] : "");
        }

        RoundDamageHistoryState history = new RoundDamageHistoryState();
        history.recordDamageExchange("attacker-a", "target");
        history.recordDamageExchange("attacker-b", "target");
        history.recordDamageExchange("", "source-less-target");
        history.recordDamageReceivedThisRound("target", 7);
        history.recordDamageReceivedThisRound("target", 5);

        assertEquals(
                Set.copyOf(ListParser.csv(fixture.get("damage_this_round"))),
                history.damageThisRound()
        );
        assertEquals(
                Set.copyOf(ListParser.csv(fixture.get("target_sources"))),
                history.damageTakenFromThisRound().get("target")
        );
        assertEquals(
                Set.copyOf(ListParser.csv(fixture.get("source_less_target_sources"))),
                history.damageTakenFromThisRound().getOrDefault("source-less-target", Set.of())
        );
        assertEquals("dict", fixture.get("damage_received_container"));
        assertEquals(
                Integer.parseInt(fixture.get("damage_received_accumulated")),
                history.damageReceivedThisRound().get("target")
        );
    }

    private static final class ListParser {
        private ListParser() {}

        static java.util.List<String> csv(String value) {
            if (value == null || value.isBlank()) return java.util.List.of();
            return java.util.Arrays.stream(value.split(","))
                    .filter(part -> !part.isBlank())
                    .toList();
        }
    }
}
