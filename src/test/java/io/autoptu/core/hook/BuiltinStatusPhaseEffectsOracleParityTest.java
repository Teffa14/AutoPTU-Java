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
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BuiltinStatusPhaseEffectsOracleParityTest {
    @Test
    void flinchStartEffectMatchesPythonContractForCanonicalAliases() throws IOException {
        String oraclePath = System.getProperty("autoptu.phase.lifecycle.oracle");
        Assumptions.assumeTrue(oraclePath != null && !oraclePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(oraclePath));
        assertEquals(1, fixture.get("flinch_start_emits_flinch_event"));
        assertEquals(1, fixture.get("flinch_start_sets_skip_turn"));

        assertFlinch("flinch");
        assertFlinch("flinched");
    }

    private static void assertFlinch(String status) {
        StatusPhaseEffectRegistry registry = BuiltinStatusPhaseEffects.flinchRegistry();
        LifecycleHookResult result = registry.resolve(context(state(status)));
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

    private static LifecycleHookContext context(BattleRuntimeState state) {
        return new LifecycleHookContext(
                state,
                new RoundDamageHistoryState(),
                new RoundInjuryHistoryState(),
                LifecycleHookPoint.PHASE_CHANGE,
                1,
                1,
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
