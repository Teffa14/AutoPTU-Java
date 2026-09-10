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

final class AbilityApproachShiftExecutorOracleParityTest {
    @Test
    void ballFetchHoldersShiftSequentiallyLikePinnedPythonSwitchRuntime() throws IOException {
        Path oracle = Path.of("build/oracle/ball-fetch-switch.tsv");
        Assumptions.assumeTrue(Files.exists(oracle));
        Map<String, GridCoord> fixture = readPositions(oracle);
        int oracleEventCount = readInt(oracle, "BALL_FETCH_EVENT_COUNT");

        RuntimeCombatantState replacement = combatant("replacement", fixture.get("REPLACEMENT_POSITION"), List.of());
        RuntimeCombatantState fetcher = combatant("fetcher", fixture.get("FETCHER_BEFORE"), List.of("Ball Fetch"));
        RuntimeCombatantState enemyFetcher = combatant("enemy_fetcher", fixture.get("ENEMY_BEFORE"), List.of("Ball Fetch"));
        RuntimeCombatantState observer = combatant("observer", new GridCoord(1, 7), List.of());
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(replacement, fetcher, enemyFetcher, observer),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "replacement", CombatantAffiliationState.active("players"),
                        "fetcher", CombatantAffiliationState.active("players"),
                        "enemy_fetcher", CombatantAffiliationState.active("foes"),
                        "observer", CombatantAffiliationState.active("foes")
                )
        );

        List<AbilityApproachShiftEffectExecutor.EffectResult> results =
                AbilityApproachShiftEffectExecutor.execute(state, "Ball Fetch", "replacement", "ball_fetch_shift");

        assertEquals(List.of("fetcher", "enemy_fetcher"), results.stream().map(AbilityApproachShiftEffectExecutor.EffectResult::actorId).toList());
        assertEquals(oracleEventCount, results.size(), "Python emits one Ball Fetch ability event per successful holder Shift");
        assertEquals(List.of("replacement", "replacement"), results.stream().map(AbilityApproachShiftEffectExecutor.EffectResult::targetId).toList());
        assertEquals(List.of("Ball Fetch", "Ball Fetch"), results.stream().map(AbilityApproachShiftEffectExecutor.EffectResult::ability).toList());
        assertEquals(fixture.get("FETCHER_AFTER"), fetcher.position());
        assertEquals(fixture.get("ENEMY_AFTER"), enemyFetcher.position());
        assertEquals(new GridCoord(1, 7), observer.position());
        assertEquals(1, fetcher.temporaryEffects().count("ball_fetch_shift"));
        assertEquals(1, enemyFetcher.temporaryEffects().count("ball_fetch_shift"));
        assertEquals(0, observer.temporaryEffects().count("ball_fetch_shift"));
        assertEquals(List.of("ball_fetch_shift", "ball_fetch_shift"), results.stream().map(AbilityApproachShiftEffectExecutor.EffectResult::temporaryEffect).toList());
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                60,
                60,
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
                abilities
        );
    }

    private static Map<String, GridCoord> readPositions(Path path) throws IOException {
        Map<String, GridCoord> positions = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("\\t");
            if (parts.length != 3) continue;
            if (!parts[0].equals("REPLACEMENT_POSITION")
                    && !parts[0].equals("FETCHER_BEFORE")
                    && !parts[0].equals("FETCHER_AFTER")
                    && !parts[0].equals("ENEMY_BEFORE")
                    && !parts[0].equals("ENEMY_AFTER")) continue;
            positions.put(parts[0], new GridCoord(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }
        return Map.copyOf(positions);
    }

    private static int readInt(Path path, String key) throws IOException {
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("\\t");
            if (parts.length == 2 && parts[0].equals(key)) {
                return Integer.parseInt(parts[1]);
            }
        }
        throw new IllegalStateException("Missing oracle key: " + key);
    }
}
