package io.autoptu.core.rules;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.action.TargetCandidate;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AutobattlerActionSpaceOracleParityTest {
    @Test
    void matchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.actionspace.oracle", "");
        assumeTrue(!oraclePath.isBlank(), "action-space oracle path not configured");

        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(oraclePath))) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t", 2);
            expected.put(parts[0], parts.length == 2 ? parts[1] : "");
        }

        Map<String, String> actual = generatedFixtures();
        assertEquals(expected.keySet(), actual.keySet());
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), actual.get(entry.getKey()), entry.getKey());
        }
    }

    private static Map<String, String> generatedFixtures() {
        Map<String, String> fixtures = new LinkedHashMap<>();

        List<ShiftChoice> shifts = AutobattlerActionSpace.legalShiftChoices(
                "actor",
                grid(5, 5),
                MovementProfile.walking(new GridCoord(2, 2), 1),
                new ActionBudget(),
                0,
                ignored -> true
        );
        fixtures.put("shift_walking_1", join(shifts.stream().map(ShiftChoice::stableKey).toList()));

        List<MoveChoice> melee = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Large",
                grid(8, 8),
                new GridCoord(0, 0),
                new ActionBudget(),
                List.of(MoveOption.standard("tackle", move("Melee", 1, null, null))),
                List.of(
                        new TargetCandidate("touching", new GridCoord(2, 0), "Medium"),
                        new TargetCandidate("too-far", new GridCoord(3, 0), "Medium")
                ),
                Set.of()
        );
        fixtures.put("large_melee_targets", join(melee.stream().map(MoveChoice::stableKey).toList()));

        List<MoveChoice> ranged = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Medium",
                grid(7, 7),
                new GridCoord(1, 1),
                new ActionBudget(),
                List.of(MoveOption.standard("water-gun", move("Ranged", 3, null, null))),
                List.of(
                        new TargetCandidate("near", new GridCoord(3, 1), "Medium"),
                        new TargetCandidate("blocked", new GridCoord(1, 3), "Medium"),
                        new TargetCandidate("far", new GridCoord(5, 5), "Medium")
                ),
                Set.of(new GridCoord(1, 2))
        );
        fixtures.put("ranged_los_targets", join(ranged.stream().map(MoveChoice::stableKey).toList()));

        List<MoveChoice> line = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Medium",
                grid(5, 5),
                new GridCoord(2, 2),
                new ActionBudget(),
                List.of(MoveOption.standard("flamethrower", move("Self", 0, "Line", 2))),
                List.of(),
                Set.of()
        );
        fixtures.put("line_tile_aim", join(line.stream().map(MoveChoice::stableKey).toList()));

        List<MoveChoice> self = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Medium",
                grid(4, 4),
                new GridCoord(1, 1),
                new ActionBudget(),
                List.of(MoveOption.standard("focus", move("Self", 0, null, null))),
                List.of(),
                Set.of()
        );
        fixtures.put("self_choice", join(self.stream().map(MoveChoice::stableKey).toList()));

        List<MoveChoice> field = AutobattlerActionSpace.legalMoveChoices(
                "actor",
                "Medium",
                grid(4, 4),
                new GridCoord(1, 1),
                new ActionBudget(),
                List.of(MoveOption.standard("weather", move("Field", null, null, null))),
                List.of(),
                Set.of()
        );
        fixtures.put("field_choice", join(field.stream().map(MoveChoice::stableKey).toList()));

        return fixtures;
    }

    private static String join(List<String> keys) {
        return keys.stream().sorted().reduce((left, right) -> left + ";" + right).orElse("");
    }

    private static MovementGrid grid(int width, int height) {
        return new MovementGrid(width, height, Set.of(), Map.of());
    }

    private static MoveSpec move(String targetKind, Integer targetRange, String areaKind, Integer areaValue) {
        return new MoveSpec(
                targetKind,
                targetKind,
                targetRange,
                targetRange,
                areaKind,
                areaValue,
                targetKind
        );
    }
}
