package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.BuiltinLifecycleHooks;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CombatantSwitchBallFetchRuntimeOracleParityTest {
    private static final Map<String, String> ORACLE_TO_JAVA_IDS = Map.of(
            "a-2", "a-2",
            "a-3", "a-3",
            "b-1", "b-1"
    );

    @Test
    void productionSwitchPathExecutesBallFetchAndPublishesPinnedTrace() throws IOException {
        Path oracle = Path.of("build/oracle/ball-fetch-switch.tsv");
        Assumptions.assumeTrue(Files.exists(oracle));
        Map<String, GridCoord> positions = readPositions(oracle);
        List<OracleAbilityEvent> expectedEvents = readAbilityEvents(oracle);

        RuntimeCombatantState outgoing = combatant("a-1", positions.get("REPLACEMENT_POSITION"), 60, List.of());
        RuntimeCombatantState replacement = combatant("a-2", new GridCoord(0, 0), 60, List.of());
        RuntimeCombatantState fetcher = combatant(
                "a-3", positions.get("FETCHER_BEFORE"), oracleHp(expectedEvents, "a-3"), List.of("Ball Fetch"));
        RuntimeCombatantState enemyFetcher = combatant(
                "b-1", positions.get("ENEMY_BEFORE"), oracleHp(expectedEvents, "b-1"), List.of("Ball Fetch"));

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(outgoing, replacement, fetcher, enemyFetcher),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "a-1", CombatantAffiliationState.active("players"),
                        "a-2", new CombatantAffiliationState("players", false),
                        "a-3", CombatantAffiliationState.active("players"),
                        "b-1", CombatantAffiliationState.active("foes")
                )
        );
        state.syncCurrentRoundFromLifecycle(1);
        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(
                Map.of(
                        "a-1", positions.get("REPLACEMENT_POSITION"),
                        "a-3", positions.get("FETCHER_BEFORE"),
                        "b-1", positions.get("ENEMY_BEFORE")
                )
        );
        ArrayList<BattleEvent> sink = new ArrayList<>();

        CombatantSwitchExecutor.ExecutionResult result = CombatantSwitchExecutor.execute(
                state,
                presence,
                BuiltinLifecycleHooks.registry(),
                sink::add,
                "a-1",
                "a-2"
        );

        assertEquals(CombatantSwitchPostEntryDispatcher.StageStatus.EXECUTED,
                result.postEntryDispatchResult().stages().get(0).status());
        assertEquals(List.of(
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING,
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING,
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING,
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING),
                result.postEntryDispatchResult().stages().subList(1, 5).stream()
                        .map(CombatantSwitchPostEntryDispatcher.StageResult::status)
                        .toList());
        assertEquals(positions.get("REPLACEMENT_POSITION"), replacement.position());
        assertEquals(positions.get("FETCHER_AFTER"), fetcher.position());
        assertEquals(positions.get("ENEMY_AFTER"), enemyFetcher.position());
        assertEquals(1, fetcher.temporaryEffects().count("ball_fetch_shift"));
        assertEquals(1, enemyFetcher.temporaryEffects().count("ball_fetch_shift"));
        assertEquals(expectedEvents.size(), sink.size());
        assertEquals(sink, result.postEntryDispatchResult().orderedEvents());

        for (int index = 0; index < expectedEvents.size(); index++) {
            OracleAbilityEvent expected = expectedEvents.get(index);
            AbilityEvent actual = (AbilityEvent) sink.get(index);
            assertEquals(javaId(expected.actorId()), actual.actorId());
            assertEquals(javaId(expected.targetId()), actual.target());
            assertEquals(expected.ability(), actual.ability());
            assertEquals(expected.effect(), actual.effect());
            assertEquals(expected.description(), actual.description());
            assertEquals(expected.targetHp(), actual.targetHp());
            assertEquals(expected.from().x(), actual.details().get("fromX"));
            assertEquals(expected.from().y(), actual.details().get("fromY"));
            assertEquals(expected.to().x(), actual.details().get("toX"));
            assertEquals(expected.to().y(), actual.details().get("toY"));
            assertEquals(expected.phase(), actual.details().get("phase"));
            assertEquals(expected.round(), actual.details().get("round"));
        }
    }

    private static RuntimeCombatantState combatant(
            String id,
            GridCoord position,
            int hp,
            List<String> abilities
    ) {
        return new RuntimeCombatantState(
                id, MovementProfile.walking(position, 4), hp, 60, new ActionBudget(),
                null, null, 0, false, false, false, false,
                List.of("Normal"), List.of(), abilities
        );
    }

    private static int oracleHp(List<OracleAbilityEvent> events, String actorId) {
        return events.stream()
                .filter(event -> event.actorId().equals(actorId))
                .mapToInt(OracleAbilityEvent::targetHp)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing oracle actor: " + actorId));
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

    private static List<OracleAbilityEvent> readAbilityEvents(Path path) throws IOException {
        ArrayList<OracleAbilityEvent> events = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("\\t", -1);
            if (parts.length != 14 || !parts[0].equals("BALL_FETCH_EVENT_STRUCT")) continue;
            events.add(new OracleAbilityEvent(
                    Integer.parseInt(parts[1]), parts[2], parts[3], parts[4], parts[5],
                    new GridCoord(Integer.parseInt(parts[6]), Integer.parseInt(parts[7])),
                    new GridCoord(Integer.parseInt(parts[8]), Integer.parseInt(parts[9])),
                    parts[10], Integer.parseInt(parts[11]), parts[12], Integer.parseInt(parts[13])
            ));
        }
        events.sort(java.util.Comparator.comparingInt(OracleAbilityEvent::index));
        return List.copyOf(events);
    }

    private static String javaId(String oracleId) {
        String mapped = ORACLE_TO_JAVA_IDS.get(oracleId);
        if (mapped == null) throw new IllegalStateException("Unmapped oracle combatant id: " + oracleId);
        return mapped;
    }

    private record OracleAbilityEvent(
            int index,
            String actorId,
            String targetId,
            String ability,
            String effect,
            GridCoord from,
            GridCoord to,
            String description,
            int targetHp,
            String phase,
            int round
    ) {}
}
