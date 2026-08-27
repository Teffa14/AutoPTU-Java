package io.autoptu.core.runtime;

import io.autoptu.core.hook.StatusControllerPhaseOrderingPolicy;
import io.autoptu.core.model.TurnPhase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.autoptu.core.hook.StatusControllerPhaseOrderingPolicy.Step.COMBATANT_PHASE_EFFECTS;
import static io.autoptu.core.hook.StatusControllerPhaseOrderingPolicy.Step.FOOD_BUFF_START;
import static io.autoptu.core.hook.StatusControllerPhaseOrderingPolicy.Step.FOOD_REGEN;
import static io.autoptu.core.hook.StatusControllerPhaseOrderingPolicy.Step.HELD_ITEM_END;
import static io.autoptu.core.hook.StatusControllerPhaseOrderingPolicy.Step.HELD_ITEM_START;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusControllerPhaseOrderingOracleParityTest {
    @Test
    void statusControllerEnvelopeMatchesPythonOrdering() throws IOException {
        String fixturePath = System.getProperty("autoptu.status.controller.phase.order.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        assertEquals(1, fixture.get("start_held_item_before_food_regen"));
        assertEquals(1, fixture.get("start_food_regen_before_food_buff"));
        assertEquals(1, fixture.get("start_food_buff_before_combatant"));
        assertEquals(1, fixture.get("end_combatant_before_held_item"));
        assertEquals(1, fixture.get("non_start_end_combatant_still_present"));

        assertEquals(
                List.of(HELD_ITEM_START, FOOD_REGEN, FOOD_BUFF_START, COMBATANT_PHASE_EFFECTS),
                StatusControllerPhaseOrderingPolicy.sequence(TurnPhase.START)
        );
        assertEquals(
                List.of(COMBATANT_PHASE_EFFECTS, HELD_ITEM_END),
                StatusControllerPhaseOrderingPolicy.sequence(TurnPhase.END)
        );
        assertEquals(List.of(COMBATANT_PHASE_EFFECTS), StatusControllerPhaseOrderingPolicy.sequence(TurnPhase.COMMAND));
        assertEquals(List.of(COMBATANT_PHASE_EFFECTS), StatusControllerPhaseOrderingPolicy.sequence(TurnPhase.ACTION));
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
