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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundHistoryInitiativeOrderOracleParityTest {
    @Test
    @SuppressWarnings("deprecation")
    void authoritativeRolloverRotatesRoundHistoriesAfterInitiativeRebuildLikePython() throws IOException {
        Path fixture = Path.of("build/oracle/round-history-order.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        String[] parts = Files.readAllLines(fixture).stream()
                .filter(line -> line != null && !line.isBlank())
                .findFirst()
                .orElseThrow()
                .split("\\t", -1);

        assertEquals("ROUND_HISTORY_ORDER", parts[0]);
        assertEquals(List.of(
                "initiative_rebuild",
                "damage_last_round",
                "damage_taken_from_last_round",
                "clear_damage_this_round",
                "clear_damage_taken_from",
                "clear_damage_received_this_round",
                "injuries_previous_round",
                "injuries_last_round"
        ), Arrays.asList(parts).subList(1, parts.length));

        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
        );
        state.damageHistory().recordDamageExchange("source", "actor");
        state.damageHistory().recordDamageReceivedThisRound("actor", 7);
        state.injuryHistory().setCurrentInjuries("actor", 2);

        BattleRoundController rounds = new BattleRoundController(state, 1);
        InitiativeTurnAdvanceResult result = rounds.advanceInitiativeTurnWithRollover((runtime, round) -> {
            assertEquals(Set.of("actor"), runtime.damageHistory().damageThisRound());
            assertEquals(Set.of("source"), runtime.damageHistory().damageTakenFromThisRound().get("actor"));
            assertEquals(7, runtime.damageHistory().damageReceivedThisRound().get("actor"));
            assertEquals(2, runtime.injuryHistory().currentInjuries("actor"));
            assertTrue(runtime.damageHistory().damageLastRound().isEmpty());
            assertTrue(runtime.injuryHistory().injuriesLastRound().isEmpty());
            return List.of("actor");
        });

        assertTrue(result.hasActor());
        assertEquals("actor", result.actorId());
        assertEquals(Set.of("actor"), state.damageHistory().damageLastRound());
        assertEquals(Set.of("source"), state.damageHistory().damageTakenFromLastRound().get("actor"));
        assertTrue(state.damageHistory().damageThisRound().isEmpty());
        assertTrue(state.damageHistory().damageTakenFromThisRound().isEmpty());
        assertTrue(state.damageHistory().damageReceivedThisRound().isEmpty());
        assertEquals(Map.of("actor", 2), state.injuryHistory().injuriesLastRound());
    }
}
