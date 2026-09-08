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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntimidateTriggerContractOracleParityTest {
    @Test
    void triggerGateAndAdjacentTargetOrderMatchPinnedPython() throws IOException {
        Path fixture = Path.of("build/oracle/intimidate-trigger.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Expected> expected = parse(Files.readAllLines(fixture));

        for (Map.Entry<String, Expected> entry : expected.entrySet()) {
            BattleRuntimeState state = scenario(entry.getKey());
            IntimidateTriggerContract.Plan actual = IntimidateTriggerContract.plan(state, "holder");
            assertEquals(entry.getValue().shouldTrigger(), actual.shouldTrigger(), entry.getKey());
            assertEquals(entry.getValue().targets(), actual.adjacentOpponentIds(), entry.getKey());
        }
    }

    private static BattleRuntimeState scenario(String name) {
        boolean holderActive = !name.equals("inactive_holder");
        int holderHp = name.equals("fainted_holder") ? 0 : 20;
        List<RuntimeCombatantState> combatants = List.of(
                combatant("holder", new GridCoord(2, 2), holderHp, "Intimidate"),
                combatant("near-first", new GridCoord(2, 3), 20, null),
                combatant("near-second", new GridCoord(3, 3), 20, null),
                combatant("far", new GridCoord(4, 2), 20, null),
                combatant("ally", new GridCoord(3, 2), 20, null),
                combatant("inactive", new GridCoord(6, 6), 20, null),
                combatant("fainted", new GridCoord(1, 2), 0, null)
        );
        Map<String, CombatantAffiliationState> affiliations = new LinkedHashMap<>();
        affiliations.put("holder", new CombatantAffiliationState("players", holderActive));
        affiliations.put("near-first", CombatantAffiliationState.active("foes"));
        affiliations.put("near-second", CombatantAffiliationState.active("foes"));
        affiliations.put("far", CombatantAffiliationState.active("foes"));
        affiliations.put("ally", CombatantAffiliationState.active("players"));
        affiliations.put("inactive", new CombatantAffiliationState("foes", false));
        affiliations.put("fainted", CombatantAffiliationState.active("foes"));

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of(), Map.of(), Map.of(), affiliations
        );
        state.syncCurrentRoundFromLifecycle(3);
        RuntimeCombatantState holder = state.requireCombatant("holder");
        if (!name.equals("not_joined")) {
            holder.temporaryEffects().add(IntimidateTriggerContract.JOINED_ROUND, Map.of("round", 3));
        }
        if (name.equals("already_used")) {
            holder.temporaryEffects().add(IntimidateTriggerContract.USED, Map.of("round", 3));
        }
        return state;
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, int hp, String ability) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                hp,
                20,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of("Normal"),
                List.of(),
                ability == null ? List.of() : List.of(ability)
        );
    }

    private static Map<String, Expected> parse(List<String> lines) {
        LinkedHashMap<String, Expected> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            if (parts.length != 4 || !parts[0].equals("CASE")) {
                throw new IllegalArgumentException("invalid fixture row: " + line);
            }
            List<String> targets = parts[3].isBlank() ? List.of() : new ArrayList<>(Arrays.asList(parts[3].split(",")));
            result.put(parts[1], new Expected(Boolean.parseBoolean(parts[2]), List.copyOf(targets)));
        }
        return result;
    }

    private record Expected(boolean shouldTrigger, List<String> targets) {}
}
