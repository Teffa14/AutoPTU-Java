package io.autoptu.core.runtime;

import io.autoptu.core.random.PythonRandom;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRandomOwnershipOracleParityTest {
    @Test
    void pythonBattleOwnsTheRngUsedByMoveAndDelayedResolution() throws IOException {
        Path fixture = Path.of("build/oracle/battle-rng-ownership.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        String line = Files.readAllLines(fixture).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow();
        String[] parts = line.split("\\t", -1);

        assertEquals("BATTLE_RNG_OWNERSHIP", parts[0]);
        assertTrue(asBool(parts[1]), "Python BattleState must own/reference a battle RNG");
        assertFalse(parts[3].isBlank(), "at least one BattleState method must consume the battle RNG");
        assertTrue(asBool(parts[4]), "ordinary move resolution must consume the battle-owned RNG");
        assertTrue(asBool(parts[5]), "target resolution must feed the ordinary move-resolution path");
        assertTrue(asBool(parts[6]), "ROUND_START delayed resolution must receive the BattleState owner");
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
