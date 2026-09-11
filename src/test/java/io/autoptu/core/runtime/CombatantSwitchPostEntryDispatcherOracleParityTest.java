package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CombatantSwitchPostEntryDispatcherOracleParityTest {
    private static final Map<String, String> ORACLE_TO_JAVA_IDS = Map.of(
            "a-2", "replacement",
            "a-3", "fetcher",
            "b-1", "enemy_fetcher"
    );

    @Test
    void dispatchesFrozenStageOrderAndPublishesRegisteredBallFetchTrace() throws IOException {
        Path orderFixture = Path.of("build/oracle/switch-post-entry-calls.tsv");
        Path ballFetchFixture = Path.of("build/oracle/ball-fetch-switch.tsv");
        Assumptions.assumeTrue(Files.exists(orderFixture));
        Assumptions.assumeTrue(Files.exists(ballFetchFixture));

        String expectedOrder = readString(orderFixture, "ORDER");
        Map<String, GridCoord> positions = readPositions(ballFetchFixture);
        List<OracleAbilityEvent> oracleEvents = readAbilityEvents(ballFetchFixture);

        RuntimeCombatantState replacement = combatant("replacement", positions.get("REPLACEMENT_POSITION"), 60, List.of());
        RuntimeCombatantState fetcher = combatant(
                "fetcher",
                positions.get("FETCHER_BEFORE"),
                oracleHp(oracleEvents, "fetcher"),
                List.of("Ball Fetch")
        );
        RuntimeCombatantState enemyFetcher = combatant(
                "enemy_fetcher",
                positions.get("ENEMY_BEFORE"),
                oracleHp(oracleEvents, "enemy_fetcher"),
                List.of("Ball Fetch")
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(replacement, fetcher, enemyFetcher),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "replacement", CombatantAffiliationState.active("players"),
                        "fetcher", CombatantAffiliationState.active("players"),
                        "enemy_fetcher", CombatantAffiliationState.active("foes")
                )
        );

        CombatantSwitchPostEntryDispatcher dispatcher = CombatantSwitchPostEntryDispatchers.paritySafe();

        ArrayList<BattleEvent> sink = new ArrayList<>();
        CombatantSwitchPostEntryDispatcher.DispatchResult result = dispatcher.dispatch(
                CombatantSwitchPostEntryPlan.pinnedContract(),
                new CombatantSwitchPostEntryDispatcher.DispatchContext(state, "replacement", "start", 1),
                sink::add
        );

        assertEquals(expectedOrder, result.stages().stream()
                .map(stage -> stage.stage().name())
                .collect(Collectors.joining(",")));
        assertEquals(
                List.of(
                        CombatantSwitchPostEntryDispatcher.StageStatus.EXECUTED,
                        CombatantSwitchPostEntryDispatcher.StageStatus.EXECUTED,
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING,
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING,
                        CombatantSwitchPostEntryDispatcher.StageStatus.PENDING
                ),
                result.stages().stream().map(CombatantSwitchPostEntryDispatcher.StageResult::status).toList()
        );
        assertEquals(oracleEvents.size(), sink.size());
        assertEquals(sink, result.orderedEvents());
        assertEquals(positions.get("FETCHER_AFTER"), fetcher.position());
        assertEquals(positions.get("ENEMY_AFTER"), enemyFetcher.position());
        assertEquals(1, fetcher.temporaryEffects().count("ball_fetch_shift"));
        assertEquals(1, enemyFetcher.temporaryEffects().count("ball_fetch_shift"));

        for (int index = 0; index < oracleEvents.size(); index++) {
            OracleAbilityEvent expected = oracleEvents.get(index);
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

    private static int oracleHp(List<OracleAbilityEvent> events, String javaActorId) {
        return events.stream()
                .filter(event -> javaId(event.actorId()).equals(javaActorId))
                .mapToInt(OracleAbilityEvent::targetHp)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing Ball Fetch oracle actor: " + javaActorId));
    }

    private static String readString(Path path, String key) throws IOException {
        for (String line : Files.readAllLines(path)) {
            String[] parts = line.split("\\t", 2);
            if (parts.length == 2 && parts[0].equals(key)) return parts[1];
        }
        throw new IllegalStateException("Missing oracle key: " + key);
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
