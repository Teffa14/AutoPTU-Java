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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class RuntimeOrdinaryDamageIngressOracleParityTest {
    @Test
    void matchesPinnedPythonApplyDamageIngressState() throws IOException {
        String oracle = System.getenv("AUTOPTU_RUNTIME_ORDINARY_DAMAGE_INGRESS_ORACLE");
        assumeTrue(oracle != null && !oracle.isBlank(), "Ordinary damage ingress fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        assertEquals(
                "case\thp_before\ttemp_hp_before\tincoming_damage\tpending_damage\tabsorbed_damage\thp_damage\thp_after\ttemp_hp_after",
                lines.getFirst()
        );
        assertEquals(5, lines.size(), "Expected header plus four frozen oracle cases");

        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            String caseName = fields[0];
            int hpBefore = Integer.parseInt(fields[1]);
            int tempHpBefore = Integer.parseInt(fields[2]);
            int incomingDamage = Integer.parseInt(fields[3]);

            RuntimeCombatantState combatant = new RuntimeCombatantState(
                    "actor",
                    MovementProfile.walking(new GridCoord(1, 1), 1),
                    hpBefore,
                    Math.max(20, hpBefore),
                    new ActionBudget()
            );
            combatant.addTempHpFromRuntime(tempHpBefore);
            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(4, 4, Set.of(), Map.of()),
                    List.of(combatant)
            );

            RuntimeOrdinaryDamageIngress.Result actual =
                    RuntimeOrdinaryDamageIngress.apply(state, "actor", incomingDamage);

            int expectedHpAfter = Integer.parseInt(fields[7]);
            assertEquals(Integer.parseInt(fields[4]), actual.pendingDamage(), caseName + " pending damage");
            assertEquals(Integer.parseInt(fields[5]), actual.absorbedDamage(), caseName + " absorbed damage");
            assertEquals(Integer.parseInt(fields[6]), actual.hpDamage(), caseName + " HP damage");
            assertEquals(expectedHpAfter, actual.hpAfter(), caseName + " HP after");
            assertEquals(Integer.parseInt(fields[8]), actual.tempHpAfter(), caseName + " temporary HP after");
            assertEquals(actual.hpAfter(), combatant.hp(), caseName + " persisted HP");
            assertEquals(actual.tempHpAfter(), combatant.tempHp(), caseName + " persisted temporary HP");

            RuntimePostDamageOutcomeResolution.Result outcome = actual.postDamageOutcome();
            assertEquals(hpBefore == 0, outcome.faintedBefore(), caseName + " fainted before");
            assertEquals(expectedHpAfter == 0, outcome.faintedAfter(), caseName + " fainted after");
            assertEquals(hpBefore > 0 && expectedHpAfter == 0, outcome.transitionedToFainted(), caseName + " faint transition");
        }
    }
}
