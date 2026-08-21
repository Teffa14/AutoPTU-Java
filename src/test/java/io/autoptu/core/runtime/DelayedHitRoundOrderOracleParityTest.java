package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DelayedHitRoundOrderOracleParityTest {
    @Test
    void pythonRoundStartResolvesDelayedHitsAfterFieldAdvanceAndBeforeExpiries() throws IOException {
        Path fixture = Path.of("build/oracle/delayed-hit-round-order.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        String[] parts = Files.readAllLines(fixture).stream()
                .filter(line -> line != null && !line.isBlank())
                .findFirst()
                .orElseThrow()
                .split("\\t", -1);

        assertEquals("DELAYED_HIT_ROUND_ORDER", parts[0]);
        assertEquals(List.of(
                "_advance_terrain",
                "_advance_zone_effects",
                "_advance_room_effects",
                "_resolve_delayed_hits",
                "_clear_expired_follow_me",
                "_clear_expired_foresight"
        ), List.of(parts).subList(1, parts.length));
    }
}
