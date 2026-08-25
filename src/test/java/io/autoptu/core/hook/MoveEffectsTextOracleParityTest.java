package io.autoptu.core.hook;

import io.autoptu.core.model.MoveSpec;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveEffectsTextOracleParityTest {
    @Test
    void directAndCanonicalFallbackTextMatchPinnedPython() throws IOException {
        Path fixture = Path.of(System.getProperty(
                "autoptu.move.effects.text.oracle",
                "build/oracle/move-effects-text.tsv"));
        Assumptions.assumeTrue(Files.exists(fixture));

        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            expected.put(parts[0], new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        }

        assertEquals(expected.get("direct"), resolve("Direct flinches on 17+.", "Fallback burns on 18+."));
        assertEquals(expected.get("direct_whitespace"), resolve("  Direct text  ", "Fallback burns on 18+."));
        assertEquals(expected.get("fallback"), resolve("", "Fallback burns on 18+."));
        assertEquals(expected.get("missing"), resolve("", null));
        assertEquals(expected.get("empty_fallback"), resolve("", ""));
    }

    private static String resolve(String direct, String fallback) {
        MoveSpec move = new MoveSpec(
                "Melee", "Melee", null, null, null, null, "Melee", List.of(), direct);
        return MoveEffectsTextResolution.resolve(move, fallback);
    }
}
