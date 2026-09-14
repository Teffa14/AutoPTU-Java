package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShiftReactionWindowDiscoveryTest {
    @Test
    void preservesAuthoritativeReactorOrderAndFiltersByShiftAwayGeometry() {
        List<ShiftReactionWindowDiscovery.DiscoveredWindow> discovered = ShiftReactionWindowDiscovery.discover(
                List.of(
                        reactor("beta", 2, 2, "Medium"),
                        reactor("alpha", 1, 1, "Medium"),
                        reactor("near-destination", 4, 2, "Medium")
                ),
                "foe",
                new GridCoord(2, 1),
                new GridCoord(4, 1),
                "Medium",
                "shift:foe"
        );

        assertEquals(List.of("beta", "alpha"),
                discovered.stream().map(ShiftReactionWindowDiscovery.DiscoveredWindow::reactingCombatantId).toList());
        for (ShiftReactionWindowDiscovery.DiscoveredWindow window : discovered) {
            assertEquals(ActionWindow.BEFORE_ACTION, window.context().window());
            assertEquals("foe", window.context().actingCombatantId());
            assertEquals("shift:foe", window.context().triggerKey());
            assertEquals(ActionWindowTrigger.ADJACENT_FOE_SHIFTS_AWAY, window.context().trigger());
        }
    }

    @Test
    void noFootprintTransitionProducesNoWindow() {
        assertTrue(ShiftReactionWindowDiscovery.discover(
                List.of(reactor("reactor", 1, 1, "Medium")),
                "foe",
                new GridCoord(2, 1),
                new GridCoord(2, 2),
                "Medium",
                "shift:foe"
        ).isEmpty());
    }

    @Test
    void matchesPinnedPythonOrderedDiscoveryWhenFixtureIsProvided() throws IOException {
        String fixturePath = System.getenv("AUTOPTU_SHIFT_REACTION_WINDOW_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixturePath));
        assertEquals("case\treactors\tshifted_id\tbefore\tafter\tshifted_size\texpected_reactors", lines.getFirst());
        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split("\\t", -1);
            assertEquals(7, parts.length, "malformed fixture row: " + line);
            List<ShiftReactionWindowDiscovery.Reactor> reactors = parts[1].isBlank()
                    ? List.of()
                    : Arrays.stream(parts[1].split(";", -1)).map(ShiftReactionWindowDiscoveryTest::parseReactor).toList();
            List<String> expected = parts[6].isBlank() ? List.of() : List.of(parts[6].split(";", -1));

            List<String> actual = ShiftReactionWindowDiscovery.discover(
                    reactors,
                    parts[2],
                    parseCoord(parts[3]),
                    parseCoord(parts[4]),
                    parts[5],
                    "shift:" + parts[2]
            ).stream().map(ShiftReactionWindowDiscovery.DiscoveredWindow::reactingCombatantId).toList();

            assertEquals(expected, actual, parts[0]);
        }
    }

    private static ShiftReactionWindowDiscovery.Reactor reactor(String id, int x, int y, String size) {
        return new ShiftReactionWindowDiscovery.Reactor(id, new GridCoord(x, y), size);
    }

    private static ShiftReactionWindowDiscovery.Reactor parseReactor(String encoded) {
        String[] parts = encoded.split("@", -1);
        if (parts.length != 3) throw new IllegalArgumentException("malformed reactor: " + encoded);
        return new ShiftReactionWindowDiscovery.Reactor(parts[0], parseCoord(parts[1]), parts[2]);
    }

    private static GridCoord parseCoord(String encoded) {
        String[] parts = encoded.split(",", -1);
        return new GridCoord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
