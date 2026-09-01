package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Compares final candidate-step displacement against deterministic pinned-Python fixtures. */
class ForcedMovementShadowAnchorExecutionOracleParityTest {
    @Test
    void matchesPinnedShadowTagCandidateStepOutcomes() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_SHADOW_ANCHOR_EXECUTION_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertFalse(lines.isEmpty());
        assertEquals(
                "case_id\tsize\tstart_x\tstart_y\tanchor_x\tanchor_y\tdx\tdy\trequested\tlimit\tdestination_x\tdestination_y\tmoved",
                lines.getFirst()
        );

        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            assertEquals(13, fields.length, "shadow anchor execution fixture row shape changed");
            String caseId = fields[0];
            String size = fields[1];
            GridCoord start = new GridCoord(integer(fields[2]), integer(fields[3]));
            GridCoord anchor = new GridCoord(integer(fields[4]), integer(fields[5]));
            GridCoord direction = new GridCoord(integer(fields[6]), integer(fields[7]));
            int requested = integer(fields[8]);
            assertEquals(5, integer(fields[9]), "pinned Shadow Tag limit changed for " + caseId);
            GridCoord expectedDestination = new GridCoord(integer(fields[10]), integer(fields[11]));
            int expectedMoved = integer(fields[12]);

            RuntimeCombatantState target = new RuntimeCombatantState(
                    "target",
                    MovementProfile.walking(start, 6),
                    20,
                    20,
                    new ActionBudget()
            );
            target.temporaryEffects().add("shadow_tag_anchor", Map.of(
                    "anchor_x", anchor.x(),
                    "anchor_y", anchor.y()
            ));
            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(40, 20, Set.of(), Map.of()),
                    List.of(target),
                    Map.of(),
                    Map.of(),
                    Map.of("target", new CombatantGeometryState(size))
            );

            ForcedDisplacementResolution.Result result = ForcedDisplacementResolution.resolve(
                    state,
                    "target",
                    direction,
                    requested
            );

            assertEquals(expectedDestination, result.destination(), "destination mismatch for " + caseId);
            assertEquals(expectedMoved, result.movedDistance(), "moved distance mismatch for " + caseId);
        }
    }

    private static int integer(String value) {
        return Integer.parseInt(value);
    }
}
