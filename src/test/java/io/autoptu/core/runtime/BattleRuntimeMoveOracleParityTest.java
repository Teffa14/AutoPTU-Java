package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.DamageDice;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BattleRuntimeMoveOracleParityTest {
    @Test
    void runtimeMoveOutcomeMatchesPinnedPythonSemanticEventsAndHp() throws IOException {
        String fixturePath = System.getProperty("autoptu.move.events.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python move-event oracle fixture path not configured");

        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        assertEquals(expected, javaResults());
    }

    private static Map<String, String> javaResults() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("miss", apply(
                "Player", "pikachu", "bulbasaur", "thunder-shock", 35,
                new AccuracyResult(false, false, 12, 6), null
        ));
        out.put("hit", apply(
                "Player", "pikachu", "bulbasaur", "thunder-shock", 35,
                new AccuracyResult(true, false, 8, 6), damage(12)
        ));
        out.put("critical", apply(
                "Foe", "charizard", "venusaur", "flamethrower", 35,
                new AccuracyResult(true, true, 20, 6), damage(20)
        ));
        return out;
    }

    private static String apply(
            String source,
            String attackerId,
            String targetId,
            String moveId,
            int targetHp,
            AccuracyResult accuracy,
            DamageResult damage
    ) {
        RuntimeCombatantState attacker = combatant(attackerId, new GridCoord(1, 1), 50, 50);
        RuntimeCombatantState target = combatant(targetId, new GridCoord(2, 1), targetHp, 50);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(attacker, target)
        );
        MoveChoice choice = new MoveChoice(
                attackerId,
                moveId,
                ChoiceTargetMode.COMBATANT,
                targetId,
                target.position(),
                ActionType.STANDARD
        );

        AppliedActionResult result = BattleRuntime.applyResolvedMoveOutcome(
                state, choice, source, accuracy, damage
        );
        MoveResolvedEvent event = (MoveResolvedEvent) result.events().getFirst();
        assertEquals(event.targetHp(), state.requireCombatant(targetId).hp());
        return event.stableKey();
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            int hp,
            int maxHp
    ) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                hp,
                maxHp,
                new ActionBudget()
        );
    }

    private static DamageResult damage(int value) {
        return new DamageResult(new DamageDice(1, 6, 0), value, 0, value, value, value, value);
    }

    private static Map<String, String> readFixtures(Path path) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid move-event fixture: " + line);
            out.put(parts[0], parts[1]);
        }
        return out;
    }
}
