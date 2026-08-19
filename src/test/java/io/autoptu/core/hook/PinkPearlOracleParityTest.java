package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.HeldItemState;
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

class PinkPearlOracleParityTest {
    @Test
    void pinkPearlModifierAndEffectEventMatchPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.pink.pearl.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertFalse(lines.isEmpty());
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            String[] columns = line.split("\\t", -1);
            String name = columns[0];
            String moveType = columns[1];
            String category = columns[2];
            boolean hasItem = Boolean.parseBoolean(columns[3]);
            int expectedFlat = Integer.parseInt(columns[4]);
            int expectedEvents = Integer.parseInt(columns[5]);

            DamageModifierHookResult result = BuiltinDamageModifierHooks.standardRegistry()
                    .resolve(context(moveType, category, hasItem));

            int actualFlat = result.modifiers().stream()
                    .filter(modifier -> "item-pink-pearl-flat".equals(modifier.slug()))
                    .mapToInt(modifier -> (int) modifier.value())
                    .sum();
            long actualEvents = result.events().stream()
                    .filter(RuleEffectEvent.class::isInstance)
                    .map(RuleEffectEvent.class::cast)
                    .filter(event -> "item".equals(event.sourceKind()))
                    .filter(event -> "Pink Pearl".equals(event.sourceName()))
                    .filter(event -> "damage_flat".equals(event.effect()))
                    .count();

            assertEquals(expectedFlat, actualFlat, name + " flat bonus");
            assertEquals(expectedEvents, actualEvents, name + " effect events");
        }
    }

    private static DamageModifierHookContext context(String moveType, String category, boolean hasItem) {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(1, 1));
        RuntimeCombatantState target = combatant("target", new GridCoord(2, 1));
        Map<String, List<HeldItemState>> heldItems = hasItem
                ? Map.of("actor", List.of(new HeldItemState("actor-item-0", "Pink Pearl")))
                : Map.of("actor", List.of());
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor, target),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), heldItems
        );
        MoveOption move = MoveOption.standard(
                "oracle-move",
                new MoveSpec("Melee", "Melee", 1, 1, null, null, "Melee"),
                new MoveCombatProfile(1, 4, 20, category, moveType)
        );
        return new DamageModifierHookContext(
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
