package io.autoptu.core.runtime;

import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeReactionShiftTriggerOracleParityTest {
    @Test
    void matchesPinnedPythonShiftTriggerGeometryWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_SHIFT_TRIGGER_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        RuntimeReactionTriggerMatcher matcher = RuntimeReactionTriggerMatcher.builtin();
        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            String caseName = parts[0];
            GridCoord reactorPosition = new GridCoord(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            String reactorSize = parts[3];
            String reactorTeam = parts[4];
            GridCoord actorOrigin = new GridCoord(Integer.parseInt(parts[5]), Integer.parseInt(parts[6]));
            String actorSize = parts[7];
            String actorTeam = parts[8];
            GridCoord destination = new GridCoord(Integer.parseInt(parts[9]), Integer.parseInt(parts[10]));
            boolean expected = Boolean.parseBoolean(parts[11]);

            BattleRuntimeState state = battle(
                    reactorPosition,
                    reactorSize,
                    reactorTeam,
                    destination,
                    actorSize,
                    actorTeam
            );
            ShiftResolvedEvent event = new ShiftResolvedEvent("actor", actorOrigin, destination);
            boolean actual = matcher
                    .matchShift("attack_of_opportunity", "reactor", state, event)
                    .isPresent();

            assertEquals(expected, actual, caseName);
        }
    }

    private static BattleRuntimeState battle(
            GridCoord reactorPosition,
            String reactorSize,
            String reactorTeam,
            GridCoord actorCurrentPosition,
            String actorSize,
            String actorTeam
    ) {
        RuntimeCombatantState reactor = combatant("reactor", reactorPosition);
        RuntimeCombatantState actor = combatant("actor", actorCurrentPosition);
        return new BattleRuntimeState(
                new MovementGrid(12, 12, Set.of(), Map.of()),
                List.of(reactor, actor),
                Map.of(),
                Map.of(),
                Map.of(
                        "reactor", new CombatantGeometryState(reactorSize),
                        "actor", new CombatantGeometryState(actorSize)
                ),
                Map.of(
                        "reactor", CombatantAffiliationState.active(reactorTeam),
                        "actor", CombatantAffiliationState.active(actorTeam)
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 6),
                20,
                20,
                new ActionBudget()
        );
    }
}
