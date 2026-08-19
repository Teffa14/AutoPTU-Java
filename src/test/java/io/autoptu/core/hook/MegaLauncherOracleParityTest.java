package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
            String ability = columns[1];
            String moveName = columns[2];
            int baseDb = Integer.parseInt(columns[3]);
            int expectedDb = Integer.parseInt(columns[4]);
            int expectedEventAmount = Integer.parseInt(columns[5]);

            EffectiveMoveHookResult result = BuiltinEffectiveMoveHooks.standardRegistry()
                    .resolve(context(ability, moveName, baseDb));

            int actualEventAmount = result.events().stream()
                    .filter(RuleEffectEvent.class::isInstance)
                    .map(RuleEffectEvent.class::cast)
                    .filter(event -> "ability".equals(event.sourceKind()))
                    .filter(event -> ability.equals(event.sourceName()))
                    .filter(event -> "db_bonus".equals(event.effect()))
                    .mapToInt(event -> (int) event.amount())
                    .sum();

            assertEquals(expectedDb, result.profile().damageBase(), name + " effective DB");
            assertEquals(expectedEventAmount, actualEventAmount, name + " event amount");
        }
    }

    private static EffectiveMoveHookContext context(String ability, String moveName, int baseDb) {
        RuntimeCombatantState actor = combatant(
                "actor", new GridCoord(1, 1), ability.isBlank() ? List.of() : List.of(ability));
        RuntimeCombatantState target = combatant("target", new GridCoord(2, 1), List.of());
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor, target)
        );
        String moveId = moveName.toLowerCase(Locale.ROOT).replace(' ', '-');
        MoveOption move = MoveOption.standard(
                moveId,
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(2, baseDb, 20, "special", "Water")
        );
        MoveCombatProfile profile = move.requireCombatProfile();
        return new EffectiveMoveHookContext(
                state, "actor", "target", actor, target, move, profile, profile
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 3),
                20,
                20,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of("Water"),
                List.of(),
                abilities
        );
    }
}
