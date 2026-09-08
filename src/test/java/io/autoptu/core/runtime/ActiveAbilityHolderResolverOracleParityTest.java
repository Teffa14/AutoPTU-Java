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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveAbilityHolderResolverOracleParityTest {
    @Test
    void activeConsciousHolderOrderMatchesPinnedPython() throws IOException {
        Path fixture = Path.of("build/oracle/active-ability-holders.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        List<RuntimeCombatantState> combatants = List.of(
                combatant("arena-first", 20, "Arena Trap"),
                combatant("air", 20, "Air Lock"),
                combatant("arena-inactive", 20, "Arena Trap"),
                combatant("arena-fainted", 0, "Arena Trap"),
                combatant("arena-second", 20, "Arena Trap")
        );
        Map<String, CombatantAffiliationState> affiliations = Map.of(
                "arena-first", CombatantAffiliationState.active("players"),
                "air", CombatantAffiliationState.active("players"),
                "arena-inactive", new CombatantAffiliationState("players", false),
                "arena-fainted", CombatantAffiliationState.active("foes"),
                "arena-second", CombatantAffiliationState.active("foes")
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of(),
                Map.of(),
                Map.of(),
                affiliations
        );

        assertEquals(values(expected.get("ARENA_TRAP")), ActiveAbilityHolderResolver.resolve(state, "Arena Trap"));
        assertEquals(values(expected.get("AIR_LOCK")), ActiveAbilityHolderResolver.resolve(state, "Air Lock"));
        assertEquals(values(expected.get("MISSING")), ActiveAbilityHolderResolver.resolve(state, "Missing Ability"));
    }

    private static RuntimeCombatantState combatant(String id, int hp, String ability) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 4),
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
                List.of(ability)
        );
    }

    private static List<String> values(String row) {
        if (row == null || row.isBlank()) return List.of();
        return Arrays.asList(row.split(","));
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid fixture row: " + line);
            result.put(parts[0], parts[1]);
        }
        return result;
    }
}
