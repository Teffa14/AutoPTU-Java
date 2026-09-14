package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.hook.ReactionEligibilityPolicy;
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

class RuntimeReactionShiftWindowOracleParityTest {
    @Test
    void matchesPinnedPythonWindowFactsWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_SHIFT_WINDOW_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            String caseName = parts[0];
            List<MoveOption> moves = parts[1].isBlank()
                    ? List.of()
                    : Arrays.stream(parts[1].split(",")).map(RuntimeReactionShiftWindowOracleParityTest::move).toList();
            String status = parts[2];
            GridCoord reactorPosition = new GridCoord(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
            GridCoord actorOrigin = new GridCoord(Integer.parseInt(parts[5]), Integer.parseInt(parts[6]));
            String reactorTeam = parts[7];
            String actorTeam = parts[8];
            boolean expected = Boolean.parseBoolean(parts[9]);

            RuntimeCombatantState reactor = combatant("reactor", reactorPosition);
            RuntimeCombatantState actor = combatant("actor", new GridCoord(actorOrigin.x() + 1, actorOrigin.y()));
            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(12, 12, Set.of(), Map.of()),
                    List.of(reactor, actor),
                    status.isBlank() ? Map.of() : Map.of("reactor", Set.of(status)),
                    Map.of(),
                    Map.of(),
                    Map.of(
                            "reactor", CombatantAffiliationState.active(reactorTeam),
                            "actor", CombatantAffiliationState.active(actorTeam)
                    ),
                    Map.of(
                            "reactor", moves,
                            "actor", List.of(move("Tackle"))
                    )
            );

            RuntimeReactionWindowResolver.Resolution result = new RuntimeReactionWindowResolver(state)
                    .discoverShiftWindows(
                            "attack_of_opportunity",
                            new ShiftResolvedEvent("actor", actorOrigin, actor.position()),
                            ReactionEligibilityPolicy.attackOfOpportunity()
                    );

            assertEquals(expected, !result.eligible().isEmpty(), caseName);
            assertEquals(0, result.unresolved().size(), caseName + " should have canonical ownership evidence");
        }
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(id, MovementProfile.walking(position, 6), 20, 20, new ActionBudget());
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }
}
