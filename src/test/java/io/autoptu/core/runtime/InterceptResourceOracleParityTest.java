package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterceptResourceOracleParityTest {
    @Test
    void successfulInterceptResourceContractMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.intercept.resource.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        assertEquals(1, fixture.get("mentions_intercept_ready"), "prepared interception resource must exist");
        assertEquals(1, fixture.get("mentions_coaching_intercept"), "coaching resource must exist");
        assertEquals(1, fixture.get("mentions_sentinel_stance"), "Sentinel Stance resource must exist");
        assertEquals(1, fixture.get("removes_intercept_ready"), "successful prepared interception consumes intercept_ready");
        assertEquals(1, fixture.get("removes_coaching_intercept"), "successful interception consumes coaching_intercept");
        assertEquals(0, fixture.get("removes_sentinel_stance"), "successful Sentinel interception retains the stance token");
        assertEquals(1, fixture.get("consumes_shift_action"), "Sentinel interception consumes the base SHIFT bucket when available");
        assertEquals(1, fixture.get("calls_extra_action_consumer"), "Sentinel interception can consume an extra SHIFT");
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.equals("key\tvalue") || line.startsWith("statement\t")) continue;
            String[] parts = line.split("\\t");
            result.put(parts[0], Integer.parseInt(parts[1]));
        }
        return result;
    }
}
