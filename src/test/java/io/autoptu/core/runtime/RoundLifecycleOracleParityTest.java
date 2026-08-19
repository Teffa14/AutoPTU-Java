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
    }

    private static void seedRoundScopedTemporaryEffects(RuntimeCombatantState actor) {
        for (String effect : List.of("intercept_ready", "extra_action", "delayed", "riposte_ready")) {
            actor.temporaryEffects().add(effect, Map.of("round", 2, "source", "first"));
            actor.temporaryEffects().add(effect, Map.of("round", 3, "source", "second"));
            assertEquals(2, actor.temporaryEffects().count(effect));
        }
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
