package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeReactionTriggerOracleParityTest {
    @Test
    void matchesPinnedPythonTriggerFamiliesWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_TRIGGER_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<RuntimeReactionTriggerRegistry.TriggerDefinition> triggers =
                RuntimeReactionTriggerRegistry.builtin().resolve("attack_of_opportunity").orElseThrow();
        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        assertEquals(triggers.size() + 1, rows.size());

        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            int ordinal = Integer.parseInt(parts[1]);
            RuntimeReactionTriggerRegistry.TriggerDefinition actual = triggers.get(ordinal);
            assertEquals(RuntimeReactionTriggerRegistry.TriggerKind.valueOf(parts[2]), actual.kind(), parts[0] + "#" + ordinal);
            Set<String> expectedQualifiers = parts[3].isBlank()
                    ? Set.of()
                    : Set.copyOf(Arrays.asList(parts[3].split(",")));
            assertEquals(expectedQualifiers, actual.qualifiers(), parts[0] + "#" + ordinal);
        }
    }
}
