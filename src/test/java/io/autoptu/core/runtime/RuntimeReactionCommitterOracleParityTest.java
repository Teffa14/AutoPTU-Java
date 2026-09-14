package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.hook.ReactionEligibilityPolicy;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
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

class RuntimeReactionCommitterOracleParityTest {
    private static RuntimeCombatantState alpha() {
        return new RuntimeCombatantState(
                "alpha",
                MovementProfile.walking(new GridCoord(0, 0), 5),
                20,
                20,
                new ActionBudget()
        );
    }

    private static MoveOption attackOfOpportunity() {
        return MoveOption.standard(
                "Attack of Opportunity",
                new MoveSpec("Self", "Self", 0, 0, null, null, "Self")
        );
    }

    @Test
    void matchesPinnedPythonCommitContractWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_COMMIT_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            String caseName = parts[0];
            Set<String> statuses = parts[1].isBlank() ? Set.of() : Set.of(parts[1]);
            int initialUses = Integer.parseInt(parts[2]);
            RuntimeReactionCommitter.Status expectedStatus = RuntimeReactionCommitter.Status.valueOf(parts[3]);
            ReactionEligibilityPolicy.Reason expectedReason = ReactionEligibilityPolicy.Reason.valueOf(parts[4]);
            int expectedFinalUses = Integer.parseInt(parts[5]);

            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(2, 1, Set.of(), Map.of()),
                    List.of(alpha()),
                    Map.of("alpha", statuses),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of("alpha", List.of(attackOfOpportunity()))
            );
            RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(state);
            for (int i = 0; i < initialUses; i++) {
                tracker.recordUseFromRuntime("alpha", "attack_of_opportunity");
            }

            RuntimeReactionCommitter.Result result = new RuntimeReactionCommitter(state).commit(
                    "alpha",
                    "attack_of_opportunity",
                    ReactionEligibilityPolicy.attackOfOpportunity()
            );

            assertEquals(expectedStatus, result.status(), caseName);
            assertEquals(expectedReason, result.eligibility().orElseThrow().reason(), caseName);
            assertEquals(expectedFinalUses, result.usesThisRound(), caseName);
            assertEquals(expectedFinalUses, tracker.usesThisRound("alpha", "attack_of_opportunity"), caseName);
        }
    }
}
