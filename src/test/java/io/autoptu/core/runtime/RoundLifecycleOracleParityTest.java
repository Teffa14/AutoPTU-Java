package io.autoptu.core.runtime;

import io.autoptu.core.model.ActionType;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundLifecycleOracleParityTest {
    @Test
    void javaRoundLifecycleMatchesExtractedPythonContract() throws IOException {
        String fixturePath = System.getProperty("autoptu.round.lifecycle.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
        actor.actionBudget().markAction(ActionType.STANDARD, "already used");
        seedRoundScopedTemporaryEffects(actor);
        actor.temporaryEffects().add("persistent_marker", Map.of("round", 99));

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
        );
        BattleRoundController rounds = new BattleRoundController(state, 3);
        seedDamageHistory(rounds.damageHistory());
        rounds.injuryHistory().setCurrentInjuries("actor", 2);
        rounds.injuryHistory().setCurrentInjuries("target", 1);

        int before = rounds.round();
        int after = rounds.startRound();

        assertEquals(fixture.get("round_increment"), after - before);
        assertEquals(1, fixture.get("trainer_actions_reset_at_round_start"));
        assertEquals(0, fixture.get("pokemon_actions_reset_at_round_start"));
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));

        assertCleanupMatchesOracle(actor, fixture, "intercept_ready", "remove_intercept_ready");
        assertCleanupMatchesOracle(actor, fixture, "extra_action", "remove_extra_action");
        assertCleanupMatchesOracle(actor, fixture, "delayed", "remove_delayed");
        assertCleanupMatchesOracle(actor, fixture, "riposte_ready", "remove_riposte_ready");
        assertTrue(actor.temporaryEffects().has("persistent_marker"));
        assertEquals(99, actor.temporaryEffects().getAll("persistent_marker").get(0).payload().get("round"));

        assertDamageHistoryMatchesOracle(rounds.damageHistory(), fixture);
        assertInjuryHistoryMatchesOracle(rounds, fixture);
    }

    private static void seedRoundScopedTemporaryEffects(RuntimeCombatantState actor) {
        for (String effect : List.of("intercept_ready", "extra_action", "delayed", "riposte_ready")) {
            actor.temporaryEffects().add(effect, Map.of("round", 2, "source", "first"));
            actor.temporaryEffects().add(effect, Map.of("round", 3, "source", "second"));
            assertEquals(2, actor.temporaryEffects().count(effect));
        }
    }

    private static void seedDamageHistory(RoundDamageHistoryState history) {
        history.recordDamageThisRound("actor");
        history.recordDamageThisRound("target");
        history.recordDamageTakenFrom("target", "actor");
        history.recordDamageTakenFrom("target", "hazard");
        history.recordDamageReceivedThisRound("target");
    }

    private static void assertDamageHistoryMatchesOracle(
            RoundDamageHistoryState history,
            Map<String, Integer> fixture
    ) {
        assertEquals(1, fixture.get("rotate_damage_last_round"));
        assertEquals(Set.of("actor", "target"), history.damageLastRound());
        assertEquals(1, fixture.get("rotate_damage_taken_from_last_round"));
        assertEquals(Set.of("actor", "hazard"), history.damageTakenFromLastRound().get("target"));

        assertEquals(1, fixture.get("clear_damage_this_round"));
        assertTrue(history.damageThisRound().isEmpty());
        assertEquals(1, fixture.get("clear_damage_taken_from"));
        assertTrue(history.damageTakenFromThisRound().isEmpty());
        assertEquals(1, fixture.get("clear_damage_received_this_round"));
        assertTrue(history.damageReceivedThisRound().isEmpty());
    }

    private static void assertInjuryHistoryMatchesOracle(
            BattleRoundController rounds,
            Map<String, Integer> fixture
    ) {
        RoundInjuryHistoryState history = rounds.injuryHistory();
        assertEquals(1, fixture.get("snapshot_injuries_last_round"));
        assertEquals(Map.of("actor", 2, "target", 1), history.injuriesLastRound());
        assertTrue(history.injuriesPreviousRound().isEmpty());

        history.setCurrentInjuries("actor", 3);
        history.setCurrentInjuries("target", 1);
        rounds.startRound();

        assertEquals(1, fixture.get("rotate_injuries_previous_round"));
        assertEquals(Map.of("actor", 2, "target", 1), history.injuriesPreviousRound());
        assertEquals(Map.of("actor", 3, "target", 1), history.injuriesLastRound());
    }

    private static void assertCleanupMatchesOracle(
            RuntimeCombatantState actor,
            Map<String, Integer> fixture,
            String effectName,
            String fixtureKey
    ) {
        assertEquals(1, fixture.get(fixtureKey));
        assertEquals(0, actor.temporaryEffects().count(effectName));
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
