package io.autoptu.core.runtime;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaTrapRuntimeCandidateProjectionOracleParityTest {
    @Test
    void authoritativeRuntimeProjectionMatchesPinnedPythonTargetEligibility() throws IOException {
        Path fixture = Path.of("build/oracle/arena-trap-targeting.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        List<RuntimeCombatantState> combatants = List.of(
                combatant("holder", 2, 2, List.of("Normal"), List.of("Arena Trap"), 0, 0),
                combatant("normal", 2, 3, List.of("Normal"), List.of(), 0, 0),
                combatant("far", 9, 9, List.of("Normal"), List.of(), 0, 0),
                combatant("flying", 2, 4, List.of("Flying"), List.of(), 0, 0),
                combatant("levitate", 2, 5, List.of("Normal"), List.of("Levitate"), 0, 0),
                combatant("capability_levitate", 3, 5, List.of("Normal"), List.of(), 0, 0),
                combatant("sky4", 3, 2, List.of("Normal"), List.of(), 4, 0),
                combatant("burrow4", 3, 3, List.of("Normal"), List.of(), 0, 4),
                combatant("inactive", 0, 0, List.of("Normal"), List.of(), 0, 0),
                combatant("ally", 2, 1, List.of("Normal"), List.of(), 0, 0)
        );

        Map<String, CombatantAffiliationState> affiliations = new LinkedHashMap<>();
        affiliations.put("holder", CombatantAffiliationState.active("players"));
        affiliations.put("normal", CombatantAffiliationState.active("foes"));
        affiliations.put("far", CombatantAffiliationState.active("foes"));
        affiliations.put("flying", CombatantAffiliationState.active("foes"));
        affiliations.put("levitate", CombatantAffiliationState.active("foes"));
        affiliations.put("capability_levitate", CombatantAffiliationState.active("foes"));
        affiliations.put("sky4", CombatantAffiliationState.active("foes"));
        affiliations.put("burrow4", CombatantAffiliationState.active("foes"));
        affiliations.put("inactive", new CombatantAffiliationState("foes", false));
        affiliations.put("ally", CombatantAffiliationState.active("players"));

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(12, 12, Set.of(), Map.of()),
                combatants,
                Map.of(),
                Map.of(),
                Map.of(),
                affiliations
        );
        CombatantRuleContentRegistry content = new CombatantRuleContentRegistry(Map.of(
                "capability_levitate", new CombatantRuleContent(List.of("Levitate"), null)
        ));

        List<ArenaTrapTargetingContract.Candidate> candidates =
                ArenaTrapRuntimeCandidateProjection.candidatesForHolder(state, content, "holder");

        assertTrue(candidates.stream()
                .filter(candidate -> candidate.actorId().equals("capability_levitate"))
                .findFirst()
                .orElseThrow()
                .capabilities()
                .contains("levitate"));
        assertEquals(
                expected.get("SLOWED"),
                String.join(",", ArenaTrapTargetingContract.regularTargets("players", candidates))
        );
    }

    private static RuntimeCombatantState combatant(
            String id,
            int x,
            int y,
            List<String> types,
            List<String> abilities,
            int sky,
            int burrow
    ) {
        MovementProfile movement = new MovementProfile(
                new GridCoord(x, y),
                4,
                0,
                sky,
                burrow,
                1.0,
                sky > 0,
                false,
                burrow > 0,
                false,
                false,
                false,
                0
        );
        return new RuntimeCombatantState(
                id,
                movement,
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
                types,
                List.of(),
                abilities
        );
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid fixture row: " + line);
            result.put(parts[0], parts[1]);
        }
        return Map.copyOf(result);
    }
}
