package io.autoptu.core.pokemon;

import io.autoptu.core.random.PythonRandom;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.autoptu.core.pokemon.PokemonCreation.*;

class PokemonCreationTest {
    @Test void matchesAllFrozenPythonCreationCasesIncludingRngConsumption() throws Exception {
        List<Nature> natures = new ArrayList<>();
        for (String row : resource("natures.tsv")) {
            String[] fields = row.split("\t", -1); natures.add(new Nature(fields[0], stats(fields[1])));
        }
        int count = 0;
        for (String row : resource("creation.tsv")) {
            String[] f = row.split("\t", -1);
            var random = new PythonRandom(Long.parseLong(f[1]));
            List<MoveProfile> moves = new ArrayList<>();
            for (String value : names(f[4], ";")) {
                String[] m = value.split(",", -1); moves.add(new MoveProfile(m[0], Integer.parseInt(m[1]), m[2]));
            }
            String[] pools = f[5].split("/", -1);
            var result = PokemonCreation.create(Integer.parseInt(f[2]), stats(f[3]), moves, natures,
                    new AbilityPools(names(pools[0], ","), names(pools[1], ","), names(pools[2], ","), names(pools[3], ",")), random);
            String scenario = "case " + f[0];
            assertEquals(f[6], result.nature(), scenario);
            assertEquals(stats(f[7]), result.allocation(), scenario);
            assertEquals(stats(f[8]), result.finalStats(), scenario);
            assertEquals(Integer.parseInt(f[9]), result.baseMaximumHp(), scenario);
            assertEquals(names(f[10], ","), result.abilities(), scenario);
            assertEquals(Long.parseLong(f[11]), random.nextUInt32(), scenario + " RNG stream");
            assertEquals(result.level() + 10, result.allocation().total(), scenario);
            count++;
        }
        assertEquals(3600, count, "Do not silently run an empty or truncated fixture");
    }
    @Test void rejectsMissingOrAmbiguousNatureAndInvalidStats() {
        var base = new Stats(5, 5, 5, 5, 5, 5);
        var pools = new AbilityPools(List.of(), List.of(), List.of(), List.of());
        var nature = new Nature("Hardy", new Stats(0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PokemonCreation.create(0, base, List.of(), List.of(nature), pools, new PythonRandom(1)));
        assertThrows(IllegalArgumentException.class, () -> PokemonCreation.create(101, base, List.of(), List.of(nature), pools, new PythonRandom(1)));
        assertThrows(IllegalArgumentException.class, () -> PokemonCreation.create(5, base, List.of(), List.of(), pools, new PythonRandom(1)));
        assertThrows(IllegalArgumentException.class, () -> PokemonCreation.create(5, new Stats(0, 1, 1, 1, 1, 1), List.of(), List.of(nature), pools, new PythonRandom(1)));
        assertThrows(IllegalArgumentException.class, () -> PokemonCreation.create(5, base, List.of(),
                List.of(nature, new Nature("Hardy", base)), pools, new PythonRandom(1)));
    }
    @Test void existingAbilitiesArePreservedAndComparedCaseInsensitively() {
        var pools = new AbilityPools(List.of(), List.of("CONFIDENCE", "Photosynthesis"), List.of(), List.of());
        assertEquals(List.of("Confidence", "Photosynthesis"), PokemonCreation.selectAbilities(pools, 20, new PythonRandom(3), List.of("Confidence")));
        assertEquals(List.of("A", "B", "C", "D"), PokemonCreation.selectAbilities(pools, 1, new PythonRandom(3), List.of("A", "B", "C", "D")));
    }
    private static Stats stats(String value) { return Stats.of(Arrays.stream(value.split(",")).mapToInt(Integer::parseInt).toArray()); }
    private static List<String> names(String value, String delimiter) { return value.isEmpty() ? List.of() : List.of(value.split(delimiter, -1)); }
    private static List<String> resource(String name) throws IOException {
        try (var stream = PokemonCreationTest.class.getResourceAsStream("/pokemon-creation/" + name)) {
            assertNotNull(stream, name);
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().toList();
        }
    }
}
