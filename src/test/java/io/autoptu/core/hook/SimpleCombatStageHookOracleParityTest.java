package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.GridCoord;
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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SimpleCombatStageHookOracleParityTest {
    @Test
    void simplePostApplyMatchesPinnedPythonOracle() throws IOException {
        String oracle = System.getProperty("autoptu.combat.stage.hooks.oracle", "").strip();
        assumeTrue(!oracle.isBlank(), "combat-stage hook oracle fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            String scenario = fields[0];
            int startStage = Integer.parseInt(fields[1]);
            int appliedDelta = Integer.parseInt(fields[2]);
            boolean hasSimple = Integer.parseInt(fields[3]) == 1;
            int expectedStage = Integer.parseInt(fields[4]);
            int expectedEventCount = Integer.parseInt(fields[5]);
            String expectedEffect = fields[6];
            int expectedAmount = Integer.parseInt(fields[7]);

            RuntimeCombatantState actor = combatant("actor", List.of());
            RuntimeCombatantState target = combatant("target", hasSimple ? List.of("Simple") : List.of());
            target.combatStages().set(CombatStat.ATK, startStage);
            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(5, 5, Set.of(), Map.of()),
                    List.of(actor, target)
            );
            CombatStageHookContext context = new CombatStageHookContext(
                    state,
                    "actor",
                    "target",
                    "Test Move",
                    CombatStat.ATK,
                    appliedDelta,
                    appliedDelta,
                    "fixture"
            );

            CombatStageHookResult result = BuiltinCombatStageHooks.registry()
                    .apply(CombatStageHookPhase.POST_APPLY, context);

            assertEquals(expectedStage, target.combatStages().get(CombatStat.ATK), scenario);
            assertEquals(expectedEventCount, result.events().size(), scenario);
            if (expectedEventCount > 0) {
                RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
                assertEquals("ability", event.sourceKind(), scenario);
                assertEquals("Simple", event.sourceName(), scenario);
                assertEquals("target", event.actorId(), scenario);
                assertEquals("target", event.targetId(), scenario);
                assertEquals(expectedEffect, event.effect(), scenario);
                assertEquals((double) expectedAmount, event.amount(), scenario);
                assertEquals(20, event.actorHp(), scenario);
            }
        }
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(id.equals("actor") ? 1 : 2, 1), 3),
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
                List.of(),
                List.of(),
                abilities
        );
    }
}
