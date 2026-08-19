package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RoundDamageHistoryState;
import io.autoptu.core.runtime.RoundInjuryHistoryState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.StatusEntry;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuiltinStatusPhaseEffectsOracleParityTest {
    @Test
    void flinchStartEffectMatchesPythonContractForCanonicalAliases() throws IOException {
        Map<String, Integer> fixture = oracleFixture();
        assertEquals(1, fixture.get("flinch_start_emits_flinch_event"));
        assertEquals(1, fixture.get("flinch_start_sets_skip_turn"));

        assertFlinch("flinch");
        assertFlinch("flinched");
    }

    @Test
    void metadataBearingFlinchExpiresAfterItsAppliedRoundWithoutSkipping() throws IOException {
        Map<String, Integer> fixture = oracleFixture();
        assertEquals(1, fixture.get("flinch_expires_when_round_advances"));
        assertEquals(1, fixture.get("flinch_expiry_removes_status"));
        assertEquals(1, fixture.get("flinch_expiry_emits_status_ends_without_skip"));

        BattleRuntimeState state = state("flinched");
        state.putStatus("actor", new StatusEntry("flinched", Map.of("applied_round", 1)));
        LifecycleHookResult result = BuiltinStatusPhaseEffects.flinchRegistry().resolve(context(state, 2));

        assertEquals(1, result.events().size());
        assertNull(result.pendingStatusSkip());
        assertFalse(state.hasStatus("actor", "flinched"));
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("status", event.sourceKind());
        assertEquals("flinched", event.sourceName());
        assertEquals("status_ends", event.effect());
    }

    @Test
    void flinchStillSkipsDuringTheRoundItWasApplied() throws IOException {
        Map<String, Integer> fixture = oracleFixture();
        assertEquals(1, fixture.get("flinch_phase_reads_applied_round_metadata"));

        BattleRuntimeState state = state("flinch");
        state.putStatus("actor", new StatusEntry("flinch", Map.of("applied_round", 2)));
        LifecycleHookResult result = BuiltinStatusPhaseEffects.flinchRegistry().resolve(context(state, 2));

        assertNotNull(result.pendingStatusSkip());
        assertEquals("flinch", ((RuleEffectEvent) result.events().getFirst()).effect());
        assertEquals(true, state.hasStatus("actor", "flinch"));
    }

    private static void assertFlinch(String status) {
        StatusPhaseEffectRegistry registry = BuiltinStatusPhaseEffects.flinchRegistry();
        LifecycleHookResult result = registry.resolve(context(state(status), 1));
        assertEquals(1, result.events().size());
        assertNotNull(result.pendingStatusSkip());

        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("status", event.sourceKind());
        assertEquals(status, event.sourceName());
        assertEquals("actor", event.actorId());
        assertEquals("flinch", event.effect());

        PendingStatusSkipRequest pending = result.pendingStatusSkip();
        assertEquals(status, pending.status());
        assertEquals(TurnPhase.START, pending.phase());
        assertEquals("flinch", pending.reason());
    }

    private static LifecycleHookContext context(BattleRuntimeState state, int round) {
        return new LifecycleHookContext(
                state,
                new RoundDamageHistoryState(),
                new RoundInjuryHistoryState(),
                LifecycleHookPoint.PHASE_CHANGE,
                Math.max(0, round - 1),
                round,
                "actor",
                TurnPhase.START
        );
    }

    private static BattleRuntimeState state(String status) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor),
                Map.of("actor", Set.of(status))
        );
    }

    private static Map<String, Integer> oracleFixture() throws IOException {
        String oraclePath = System.getProperty("autoptu.phase.lifecycle.oracle");
        Assumptions.assumeTrue(oraclePath != null && !oraclePath.isBlank());
        return readFixture(Path.of(oraclePath));
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
