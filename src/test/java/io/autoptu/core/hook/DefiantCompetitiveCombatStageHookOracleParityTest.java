package io.autoptu.core.hook;

import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatStageMutationResult;
import io.autoptu.core.runtime.CombatStageMutationService;
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

class DefiantCompetitiveCombatStageHookOracleParityTest {
    @Test
    void reactionRequestsMatchPinnedPythonOracle() throws IOException {
        String oracle = System.getProperty("autoptu.combat.stage.hooks.oracle", "").strip();
        assumeTrue(!oracle.isBlank(), "combat-stage hook oracle fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            if (fields.length <= 8 || fields[8].isBlank()) continue;

            String reaction = fields[8];
            if (!reaction.equals("defiant") && !reaction.equals("competitive")) continue;
            String scenario = fields[0];
            int appliedDelta = Integer.parseInt(fields[2]);
            String attackerId = fields[9];
            String targetId = fields[10];
            String moveName = fields[11];
            List<String> abilities = fields[12].isBlank() ? List.of() : List.of(fields[12].split(","));
            int recursiveCalls = Integer.parseInt(fields[13]);
            String recursiveMove = fields[14];
            String recursiveStat = fields[15];
            int recursiveDelta = Integer.parseInt(fields[16]);

            RuntimeCombatantState actor = SimpleCombatStageHookOracleParityTest.combatant("actor", List.of());
            RuntimeCombatantState target = SimpleCombatStageHookOracleParityTest.combatant("target", abilities);
            BattleRuntimeState state = new BattleRuntimeState(new MovementGrid(5, 5, Set.of(), Map.of()), List.of(actor, target));
            CombatStageHookContext context = new CombatStageHookContext(
                    state, attackerId, targetId, moveName, CombatStat.DEF, appliedDelta, appliedDelta, "fixture"
            );

            CombatStageHookResult result = BuiltinCombatStageHooks.registry().apply(CombatStageHookPhase.POST_APPLY, context);
            assertEquals(0, result.events().size(), scenario);

            int expectedAtk = recursiveCalls == 1 && recursiveStat.equals("atk") ? recursiveDelta : 0;
            int expectedSpAtk = recursiveCalls == 1 && recursiveStat.equals("spatk") ? recursiveDelta : 0;
            assertEquals(expectedAtk, target.combatStages().get(CombatStat.ATK), scenario + " / " + reaction + " / " + recursiveMove);
            assertEquals(expectedSpAtk, target.combatStages().get(CombatStat.SPATK), scenario);
        }
    }

    @Test
    void defiantUsesAppliedDropAfterClampThroughAuthoritativeMutationService() {
        RuntimeCombatantState actor = SimpleCombatStageHookOracleParityTest.combatant("actor", List.of());
        RuntimeCombatantState target = SimpleCombatStageHookOracleParityTest.combatant("target", List.of("Defiant"));
        target.combatStages().set(CombatStat.DEF, -5);
        BattleRuntimeState state = new BattleRuntimeState(new MovementGrid(5, 5, Set.of(), Map.of()), List.of(actor, target));

        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("actor", "target", "Tail Whip", CombatStat.DEF, -3, "lower_defense");

        assertEquals(-1, result.baseAppliedDelta());
        assertEquals(-6, target.combatStages().get(CombatStat.DEF));
        assertEquals(3, target.combatStages().get(CombatStat.ATK));
    }

    @Test
    void competitiveReentersAgainstAlreadyLoweredSpecialAttack() {
        RuntimeCombatantState actor = SimpleCombatStageHookOracleParityTest.combatant("actor", List.of());
        RuntimeCombatantState target = SimpleCombatStageHookOracleParityTest.combatant("target", List.of("Competitive"));
        BattleRuntimeState state = new BattleRuntimeState(new MovementGrid(5, 5, Set.of(), Map.of()), List.of(actor, target));

        CombatStageMutationResult result = CombatStageMutationService.authoritative(state)
                .apply("actor", "target", "Fake Tears", CombatStat.SPATK, -1, "lower_special_attack");

        assertEquals(-1, result.baseAppliedDelta());
        assertEquals(1, target.combatStages().get(CombatStat.SPATK));
    }
}
