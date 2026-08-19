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
    void flinchAndAliasMatchPythonPhaseEventAndPendingSkip() throws IOException {
        String oraclePath = System.getProperty("autoptu.status.phase.effect.oracle");
        if (oraclePath == null || oraclePath.isBlank()) return;

        Map<String, String> fixtures = readFixtures(Path.of(oraclePath));
        assertEquals(fixtures.get("flinch"), resolve("flinch"));
        assertEquals(fixtures.get("flinched_alias"), resolve("flinched"));
    }

    private static String resolve(String status) {
        StatusPhaseEffectRegistry registry = BuiltinStatusPhaseEffects.flinchRegistry();
        LifecycleHookResult result = registry.resolve(context(state(status)));
        assertEquals(1, result.events().size());
        assertNotNull(result.pendingStatusSkip());

        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        PendingStatusSkipRequest pending = result.pendingStatusSkip();
        return String.join("|",
                event.sourceKind(),
                event.actorId(),
                event.sourceName(),
                pending.phase().value(),
                event.effect(),
                "true"
        );
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

    private static Map<String, String> readFixtures(Path path) throws IOException {
        LinkedHashMap<String, String> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            rows.put(parts[0], parts[1]);
        }
        return rows;
    }
}
