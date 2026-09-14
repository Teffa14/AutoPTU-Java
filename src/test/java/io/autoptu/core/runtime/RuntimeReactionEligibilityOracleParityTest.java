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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeReactionEligibilityOracleParityTest {
    @Test
    void resolvesPinnedPythonEligibilityCasesFromAuthoritativeRuntimeStateWhenProvided() throws Exception {
        String fixturePath = System.getenv("AUTOPTU_REACTION_ELIGIBILITY_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> rows = Files.readAllLines(Path.of(fixturePath));
        for (String row : rows.subList(1, rows.size())) {
            String[] parts = row.split("\\t", -1);
            boolean ownsReaction = Boolean.parseBoolean(parts[0]);
            Set<String> statuses = parts[1].isBlank()
                    ? Set.of()
                    : Arrays.stream(parts[1].split(",")).collect(Collectors.toSet());
            int usesThisRound = Integer.parseInt(parts[2]);
            ReactionEligibilityPolicy.Reason expected = ReactionEligibilityPolicy.Reason.valueOf(parts[3]);

            RuntimeCombatantState alpha = new RuntimeCombatantState(
                    "alpha",
                    MovementProfile.walking(new GridCoord(0, 0), 5),
                    20,
                    20,
                    new ActionBudget()
            );
            List<MoveOption> moves = List.of(move(ownsReaction ? "Attack of Opportunity" : "Tackle"));
            BattleRuntimeState state = new BattleRuntimeState(
                    new MovementGrid(2, 1, Set.of(), Map.of()),
                    List.of(alpha),
                    Map.of("alpha", statuses),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of("alpha", moves)
            );
            new BattleRoundController(state, 1);
            RuntimeReactionUsageTracker tracker = new RuntimeReactionUsageTracker(state);
            for (int use = 0; use < usesThisRound; use++) {
                tracker.recordUseFromRuntime("alpha", "attack_of_opportunity");
            }

            RuntimeReactionEligibilityResolver resolver = new RuntimeReactionEligibilityResolver(state, tracker);
            RuntimeReactionEligibilityResolver.Resolution actual = resolver.evaluate(
                    "alpha",
                    "attack_of_opportunity",
                    ReactionEligibilityPolicy.attackOfOpportunity()
            );
            assertEquals(RuntimeReactionOwnershipResolver.Status.valueOf(ownsReaction ? "OWNED" : "NOT_OWNED"), actual.ownership().status(), row);
            assertEquals(expected, actual.eligibility().orElseThrow().reason(), row);
        }
    }

    private static MoveOption move(String id) {
        return MoveOption.standard(id, new MoveSpec("Self", "Self", 0, 0, null, null, "Self"));
    }
}
