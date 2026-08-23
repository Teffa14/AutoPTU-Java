package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimePreDamageReactionContextFactoryOracleParityTest {
    @Test
    void nonAreaMoveProducesNoThreatenedReactionTiles() {
        BattleRuntimeState state = state(8, 8, new GridCoord(1, 1), new GridCoord(3, 1));
        MoveSpec move = new MoveSpec("Ranged", "Ranged", 6, 6, null, null, "Ranged");

        assertEquals(
                List.of(),
                RuntimePreDamageReactionContextFactory.threatenedTiles(
                        state,
                        state.requireCombatant("attacker").position(),
                        state.requireCombatant("defender").position(),
                        move
                )
        );
    }

    @Test
    void matchesPinnedPythonTargetingWhenFixtureIsProvided() throws IOException {
        String fixturePath = System.getenv("AUTOPTU_PRE_DAMAGE_THREATENED_AREA_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixturePath));
        assertTrue(lines.size() > 1, "pre-damage threatened-area oracle fixture must contain cases");
        assertEquals(
                "case\twidth\theight\tattacker\tdefender\ttarget_kind\trange_kind\ttarget_range\t"
                        + "range_value\tarea_kind\tarea_value\trange_text\tthreatened",
                lines.getFirst()
        );

        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split("\\t", -1);
            assertEquals(13, parts.length, "malformed fixture row: " + line);
            GridCoord attacker = parseCoord(parts[3]);
            GridCoord defender = parseCoord(parts[4]);
            BattleRuntimeState state = state(
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    attacker,
                    defender
            );
            MoveSpec move = new MoveSpec(
                    emptyToNull(parts[5]),
                    emptyToNull(parts[6]),
                    parseInteger(parts[7]),
                    parseInteger(parts[8]),
                    emptyToNull(parts[9]),
                    parseInteger(parts[10]),
                    emptyToNull(parts[11])
            );

            List<GridCoord> actual = sorted(RuntimePreDamageReactionContextFactory.threatenedTiles(
                    state, attacker, defender, move));
            List<GridCoord> expected = sorted(parseCoords(parts[12]));
            assertEquals(expected, actual, parts[0]);
        }
    }

    private static BattleRuntimeState state(
            int width,
            int height,
            GridCoord attacker,
            GridCoord defender
    ) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "attacker", MovementProfile.walking(attacker, 4), 30, 30, new ActionBudget());
        RuntimeCombatantState target = new RuntimeCombatantState(
                "defender", MovementProfile.walking(defender, 4), 30, 30, new ActionBudget());
        return new BattleRuntimeState(
                new MovementGrid(width, height, Set.of(), Map.of()),
                List.of(actor, target)
        );
    }

    private static List<GridCoord> sorted(List<GridCoord> coords) {
        ArrayList<GridCoord> ordered = new ArrayList<>(coords);
        ordered.sort(Comparator.comparingInt(GridCoord::x).thenComparingInt(GridCoord::y));
        return List.copyOf(ordered);
    }

    private static GridCoord parseCoord(String encoded) {
        String[] parts = encoded.split(",", -1);
        return new GridCoord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static List<GridCoord> parseCoords(String encoded) {
        if (encoded.isEmpty()) return List.of();
        ArrayList<GridCoord> coords = new ArrayList<>();
        for (String item : encoded.split("\\|", -1)) coords.add(parseCoord(item));
        return List.copyOf(coords);
    }

    private static Integer parseInteger(String value) {
        return value == null || value.isEmpty() ? null : Integer.valueOf(value);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
