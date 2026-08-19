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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrangeTempoStatusPhaseOracleParityTest {
    @Test
    void strangeTempoConfusionBranchMatchesPinnedPythonContract() throws IOException {
        String oraclePath = System.getProperty("autoptu.phase.lifecycle.oracle");
        Assumptions.assumeTrue(oraclePath != null && !oraclePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(oraclePath));
        assertEquals(1, fixture.get("strange_tempo_confusion_checks_sleep_block"));
        assertEquals(1, fixture.get("strange_tempo_confusion_emits_control_event"));
        assertEquals(1, fixture.get("strange_tempo_confusion_does_not_skip"));

        assertControlled("confusion", List.of("Strange Tempo"));
        assertControlled("confused", List.of("Strange Tempo [Errata]"));
    }

    @Test
    void sleepingOrSleepBlockedActorDoesNotRunStrangeTempoConfusionBranch() {
        RuntimeCombatantState sleepingActor = actor(List.of("Strange Tempo"));
        BattleRuntimeState sleeping = state(sleepingActor, Set.of("confused", "sleep"));
        LifecycleHookResult sleepingResult = BuiltinStatusPhaseEffects.strangeTempoRegistry().resolve(context(sleeping));
        assertTrue(sleepingResult.events().isEmpty());
        assertNull(sleepingResult.pendingStatusSkip());

        RuntimeCombatantState blockedActor = actor(List.of("Strange Tempo"));
        blockedActor.temporaryEffects().add("sleep_blocked");
        BattleRuntimeState blocked = state(blockedActor, Set.of("confusion"));
        LifecycleHookResult blockedResult = BuiltinStatusPhaseEffects.strangeTempoRegistry().resolve(context(blocked));
        assertTrue(blockedResult.events().isEmpty());
        assertNull(blockedResult.pendingStatusSkip());
    }

    @Test
    void confusionWithoutStrangeTempoIsLeftForLaterConfusionRules() {
        BattleRuntimeState state = state(actor(List.of()), Set.of("confused"));
        LifecycleHookResult result = BuiltinStatusPhaseEffects.strangeTempoRegistry().resolve(context(state));
        assertTrue(result.events().isEmpty());
        assertNull(result.pendingStatusSkip());
    }

    private static void assertControlled(String status, List<String> abilities) {
        BattleRuntimeState state = state(actor(abilities), Set.of(status));
        LifecycleHookResult result = BuiltinStatusPhaseEffects.strangeTempoRegistry().resolve(context(state));
        assertEquals(1, result.events().size());
        assertNull(result.pendingStatusSkip());

        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("ability", event.sourceKind());
        assertEquals("Strange Tempo", event.sourceName());
        assertEquals("actor", event.actorId());
        assertEquals("confusion_control", event.effect());
        assertEquals(20, event.targetHp());
    }

    private static RuntimeCombatantState actor(List<String> abilities) {
        return new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                abilities
        );
    }

    private static BattleRuntimeState state(RuntimeCombatantState actor, Set<String> statuses) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor),
                Map.of("actor", statuses)
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
