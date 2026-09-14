package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeReactionOwnershipOracleParityTest {
    @Test
    void resolvesPinnedPythonMovesetOwnershipWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_OWNERSHIP_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        RuntimeReactionOwnershipResolver resolver = new RuntimeReactionOwnershipResolver();
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            String combatantId = parts[0];
            String reactionKey = parts[1];
            List<MoveOption> moves = parts[2].isBlank()
                    ? List.of()
                    : Arrays.stream(parts[2].split(","))
                    .map(RuntimeReactionOwnershipOracleParityTest::move)
                    .toList();
            RuntimeReactionOwnershipResolver.Status expected = RuntimeReactionOwnershipResolver.Status.valueOf(parts[3]);

            RuntimeCombatantState combatant = new RuntimeCombatantState(
                    combatantId,
                    MovementProfile.walking(new GridCoord(0, 0), 5),
                    20,
                    20,
                    new ActionBudget()
            );
            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(2, 1, Set.of(), Map.of()),
                    List.of(combatant),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(combatantId, moves)
            );

            assertEquals(expected, resolver.resolve(state, combatantId, reactionKey).status(), row);
        }
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }
}
