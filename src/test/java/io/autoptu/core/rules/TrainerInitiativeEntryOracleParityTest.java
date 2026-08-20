package io.autoptu.core.rules;

import io.autoptu.core.model.InitiativeEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TrainerInitiativeEntryOracleParityTest {
    @Test
    void matchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.trainer.initiative.entry.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertEquals(
                "name\ttrainer_id\tspeed\tinitiative_bonus\ttailwind\tactor_id\tentry_trainer_id\tentry_speed\ttrainer_modifier\troll\ttotal",
                lines.getFirst()
        );

        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            String name = parts[0];
            InitiativeEntry actual = TrainerInitiativeEntryResolution.resolve(
                    parts[1],
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]),
                    "1".equals(parts[4])
            );

            assertEquals(parts[5], actual.actorId(), name + " actorId");
            assertEquals(parts[6], actual.trainerId(), name + " trainerId");
            assertEquals(Integer.parseInt(parts[7]), actual.speed(), name + " speed");
            assertEquals(Integer.parseInt(parts[8]), actual.trainerModifier(), name + " trainerModifier");
            assertEquals(Integer.parseInt(parts[9]), actual.roll(), name + " roll");
            assertEquals(Integer.parseInt(parts[10]), actual.total(), name + " total");
        }
    }

    @Test
    void rejectsBlankTrainerIdentity() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> TrainerInitiativeEntryResolution.resolve("  ", 10, 0, false)
        );
    }
}
