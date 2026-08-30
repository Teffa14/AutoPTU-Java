package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatantRuleContentNaturewalkParityTest {
    @Test
    void projectsSpeciesThenCapabilityNaturewalkLikePinnedPython() {
        CombatantRuleContent content = new CombatantRuleContent(
                List.of("Naturewalk (Forest)", "Naturewalk Tundra", "Naturewalk (grassland)"),
                null,
                "",
                Map.of(),
                List.of("Survivalist"),
                List.of("Grassland")
        );

        assertTrue(content.hasTrainerFeature("survivalist"));
        assertEquals(List.of("Grassland", "Forest", "Tundra"), content.effectiveNaturewalkLabels());
    }

    @Test
    void pinnedOracleFreezesSpeciesAndCapabilityNaturewalkProjection() throws IOException {
        String oraclePath = System.getProperty("autoptu.intercept.terrain.oracle", "").strip();
        if (oraclePath.isEmpty()) return;

        Map<String, String> oracle = readContract(Path.of(oraclePath));
        String labelsSource = oracle.getOrDefault("naturewalk_labels_source", "");
        String speciesSource = oracle.getOrDefault("species_naturewalk_source", "");

        assertTrue(labelsSource.contains("_species_naturewalk"), labelsSource);
        assertTrue(labelsSource.contains("capability_names"), labelsSource);
        assertTrue(labelsSource.contains("startswith('naturewalk')") || labelsSource.contains("startswith(\"naturewalk\")"), labelsSource);
        assertTrue(labelsSource.contains("re.search"), labelsSource);
        assertTrue(speciesSource.contains("species.json"), speciesSource);
        assertTrue(speciesSource.contains("naturewalk"), speciesSource);
    }

    private static Map<String, String> readContract(Path path) throws IOException {
        LinkedHashMap<String, String> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            int separator = line.indexOf('\t');
            if (separator <= 0) continue;
            rows.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return rows;
    }
}
