package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.AbilityState;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MegaLauncherOracleParityTest {
    @Test
    void effectiveDbAndAbilityEventMatchPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.mega.launcher.oracle");
        if (oraclePath == null || oraclePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertFalse(lines.isEmpty());
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            String[] columns = line.split("\\t", -1);
            String name = columns[0];
            String moveName = columns[1];
            int baseDb = Integer.parseInt(columns[2]);
            String ability = columns[3];
            int expectedDb = Integer.parseInt(columns[4]);
            int expectedAmount = Integer.parseInt(columns[5]);
            int expectedEvents = Integer.parseInt(columns[6]);

            MoveProfileHookResult result = BuiltinMoveProfileHooks.standardRegistry()
                    .resolve(context(moveName, baseDb, ability));

            int actualAmount = result.events().stream()
                    .filter(RuleEffectEvent.class::isInstance)
                    .map(RuleEffectEvent.class::cast)
                    .filter(event -> "ability".equals(event.sourceKind()))
                    .filter(event -> "db_bonus".equals(event.effect()))
                    .mapToInt(RuleEffectEvent::amount)
                    .sum();
            long actualEvents = result.events().stream()
                    .filter(RuleEffectEvent.class::isInstance)
                    .map(RuleEffectEvent.class::cast)
                    .filter(event -> "ability".equals(event.sourceKind()))
                    .filter(event -> "db_bonus".equals(event.effect()))
                    .count();

            assertEquals(expectedDb, result.profile().damageBase(), name + " effective DB");
            assertEquals(expectedAmount, actualAmount, name + " event amount");
            assertEquals(expectedEvents, actualEvents, name + " event count");
        }
    }

    private static MoveProfileHookContext context(String moveName, int baseDb, String ability) {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1));
        RuntimeCombatantState target = combatant("target", new GridCoord(2, 1));
        Map<String, List<AbilityState>> abilities = ability.isBlank()
                ? Map.of("actor", List.of())
                : Map.of("actor", List.of(new AbilityState("actor-ability-0", ability)));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), abilities
        );
        MoveOption move = MoveOption.standard(
                moveName,
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(2, baseDb, 20, "special", "Water")
        );
        return new MoveProfileHookContext(
                state, "actor", "target", actor, target, move, move.requireCombatProfile()
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                20,
                20,
                new ActionBudget()
        );
    }
}
