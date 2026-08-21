package io.autoptu.core.runtime;

import io.autoptu.core.random.PythonRandom;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRandomOwnershipOracleParityTest {
    @Test
    void pythonDelayedAndOrdinaryResolutionShareTheBattleRng() throws IOException {
        Path fixture = Path.of("build/oracle/battle-rng-ownership.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        String line = Files.readAllLines(fixture).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow();
        String[] parts = line.split("\\t", -1);

        assertEquals("BATTLE_RNG_OWNERSHIP", parts[0]);
        assertTrue(asBool(parts[1]), "move resolution must accept an explicit RNG stream");
        assertTrue(asBool(parts[2]), "move resolution must consume the supplied RNG stream");
        assertTrue(asBool(parts[3]), "BattleState target resolution must forward self.rng");
        assertTrue(asBool(parts[4]), "target resolution must feed ordinary move resolution");
        assertTrue(asBool(parts[5]), "ROUND_START delayed resolution must receive the BattleState owner");
    }

    @Test
    void javaBattleRandomStateOwnsOneContinuousPythonCompatibleStream() {
        long seed = 918273645L;
        BattleRandomState state = new BattleRandomState(seed);
        PythonRandom expected = new PythonRandom(seed);

        assertEquals(expected.randIntInclusive(1, 20), state.random().randIntInclusive(1, 20));
        assertEquals(expected.randIntInclusive(1, 10), state.random().randIntInclusive(1, 10));
        assertEquals(expected.randIntInclusive(1, 20), state.random().randIntInclusive(1, 20));

        boolean exposesMutableRngPublicly = Arrays.stream(BattleRandomState.class.getDeclaredMethods())
                .anyMatch(method -> Modifier.isPublic(method.getModifiers())
                        && method.getReturnType().equals(PythonRandom.class));
        assertFalse(exposesMutableRngPublicly, "adapters must not get a public mutable RNG handle");
    }

    private static boolean asBool(String raw) {
        return "1".equals(raw);
    }
}
