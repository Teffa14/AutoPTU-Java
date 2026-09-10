package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RuntimeApproachShiftDestinationResolverOracleParityTest {
    @Test
    void ballFetchApproachDestinationsMatchPinnedPythonSwitchRuntime() throws IOException {
        Path oracle = Path.of("build/oracle/ball-fetch-switch.tsv");
        if (!Files.exists(oracle)) {
            return;
        }
        Map<String, GridCoord> fixture = readPositions(oracle);

        RuntimeCombatantState replacement = combatant("replacement", fixture.get("REPLACEMENT_POSITION"), 4);
        RuntimeCombatantState fetcher = combatant("fetcher", fixture.get("FETCHER_BEFORE"), 4);
        RuntimeCombatantState enemyFetcher = combatant("enemy_fetcher", fixture.get("ENEMY_BEFORE"), 4);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(replacement, fetcher, enemyFetcher),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "replacement", CombatantAffiliationState.active("players"),
                        "fetcher", CombatantAffiliationState.active("players"),
                        "enemy_fetcher", CombatantAffiliationState.active("foes")
                )
        );

        assertEquals(
                fixture.get("FETCHER_AFTER"),
                RuntimeApproachShiftDestinationResolver.closestLegalShiftToward(state, "fetcher", "replacement").orElseThrow()
        );
        assertEquals(
                fixture.get("ENEMY_AFTER"),
                RuntimeApproachShiftDestinationResolver.closestLegalShiftToward(state, "enemy_fetcher", "replacement").orElseThrow()
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, int overland) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, overland),
                60,
                60,
                new ActionBudget()
        );
    }

    private static Map<String, GridCoord> readPositions(Path path) throws IOException {
        Map<String, GridCoord> positions = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("\\t");
            if (parts.length != 3) {
                continue;
            }
            if (!parts[0].equals("REPLACEMENT_POSITION")
                    && !parts[0].equals("FETCHER_BEFORE")
                    && !parts[0].equals("FETCHER_AFTER")
                    && !parts[0].equals("ENEMY_BEFORE")
                    && !parts[0].equals("ENEMY_AFTER")) {
                continue;
            }
            positions.put(parts[0], new GridCoord(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }
        return positions;
    }
}
