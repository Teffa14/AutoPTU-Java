package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileEntryTrapOracleContractTest {
    @Test
    void matchesObservedPinnedPythonEntryScenarios() throws Exception {
        String fixture = System.getenv("AUTOPTU_TILE_ENTRY_TRAP_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertEquals(
                "scenario\tlayers\tsource_team\tterrains\tnaturewalk\teffect\tconsumed\tsource_id\tevent_terrains\tcoord\ttarget_hp\ttrap_name\tdescription\tstatus\tstatus_actor\tstatus_target\tstatus_move_name\tstatus_move_type\tstatus_move_category\tstatus_effect\tstatus_description\tstatus_remaining\ttrace",
                lines.getFirst()
        );

        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            assertEquals(23, fields.length, line);

            String scenario = fields[0];
            int layers = Integer.parseInt(fields[1]);
            String sourceTeam = fields[2];
            Set<String> terrains = splitSet(fields[3]);
            Set<String> naturewalk = splitSet(fields[4]);
            String expectedEffect = fields[5];
            boolean expectedConsumed = fields[6].equals("1");

            var context = new TileEntryTrapResolution.EntryContext(
                    "target", "target", "blue", 37, new GridCoord(4, 3), naturewalk);
            var trap = new TileEntryTrapResolution.TrapLayer(
                    "abrasion_trap", layers, "source", sourceTeam, terrains, "Abrasion Trap");
            var result = TileEntryTrapResolution.resolve(context, List.of(trap));

            assertEquals(expectedConsumed, result.consumedTrapKeys().contains("abrasion_trap"), scenario);
            if (expectedEffect.isBlank()) {
                assertTrue(result.triggers().isEmpty(), scenario);
                assertTrue(result.blocks().isEmpty(), scenario);
                assertEquals("", fields[22], scenario);
                continue;
            }

            if (expectedEffect.equals("trap_block")) {
                assertTrue(result.triggers().isEmpty(), scenario);
                assertEquals(1, result.blocks().size(), scenario);
                var block = result.blocks().getFirst();
                assertEquals("target", block.actorId(), scenario);
                assertEquals("abrasion_trap", block.trapKey(), scenario);
                assertEquals(Integer.parseInt(fields[10]), block.targetHp(), scenario);
                assertEquals(fields[12], block.description(), scenario);
                assertEquals("", fields[13], scenario);
                assertEquals("EMIT_TRAP_EVENT", fields[22], scenario);
                continue;
            }

            assertEquals("trigger", expectedEffect, scenario);
            assertTrue(result.blocks().isEmpty(), scenario);
            assertEquals(1, result.triggers().size(), scenario);
            var trigger = result.triggers().getFirst();
            assertEquals("target", trigger.actorId(), scenario);
            assertEquals("abrasion_trap", trigger.trapKey(), scenario);
            assertEquals(fields[11], trigger.trapName(), scenario);
            assertEquals(fields[7], trigger.sourceId(), scenario);
            assertEquals(splitSet(fields[8]), trigger.terrains(), scenario);
            assertEquals(parseCoord(fields[9]), trigger.coordinate(), scenario);
            assertEquals(Integer.parseInt(fields[10]), trigger.targetHp(), scenario);
            assertEquals(fields[12], trigger.description(), scenario);

            var status = trigger.statusApplication();
            assertEquals(fields[13], status.status(), scenario);
            assertEquals(fields[14], status.actorId(), scenario);
            assertEquals(fields[15], status.targetId(), scenario);
            assertEquals(fields[16], status.moveName(), scenario);
            assertEquals(fields[17], status.moveType(), scenario);
            assertEquals(fields[18], status.moveCategory(), scenario);
            assertEquals(fields[19], status.effect(), scenario);
            assertEquals(fields[20], status.description(), scenario);
            assertEquals(Integer.parseInt(fields[21]), status.remaining(), scenario);
            assertEquals(fields[22], encodeSteps(trigger.effectOrder()), scenario);
        }
    }

    private static Set<String> splitSet(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return new LinkedHashSet<>(Arrays.asList(value.split("\\|")));
    }

    private static GridCoord parseCoord(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 2) throw new IllegalArgumentException("expected x|y coordinate, got " + value);
        return new GridCoord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static String encodeSteps(List<TileEntryTrapResolution.EffectStep> steps) {
        return String.join("|", steps.stream().map(Enum::name).toList());
    }
}
