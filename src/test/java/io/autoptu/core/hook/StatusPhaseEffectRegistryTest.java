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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusPhaseEffectRegistryTest {
    @Test
    void resolvesCanonicalStatusesInExplicitOrderAndLastPendingSkipWins() {
        ArrayList<String> calls = new ArrayList<>();
        StatusPhaseEffectRegistry registry = StatusPhaseEffectRegistry.builder()
                .register("late-freeze", List.of("Freeze", "Frozen"), TurnPhase.ACTION, 20, (context, status) -> {
                    calls.add("late:" + status);
                    return LifecycleHookResult.eventsAndPendingStatusSkip(
                            List.of(event(context, status, "freeze_check")),
                            new PendingStatusSkipRequest("Frozen", TurnPhase.ACTION, "freeze")
                    );
                })
                .register("early-flinch", List.of("Flinch", "Flinched"), TurnPhase.ACTION, 10, (context, status) -> {
                    calls.add("early:" + status);
                    return LifecycleHookResult.eventsAndPendingStatusSkip(
                            List.of(event(context, status, "flinch_check")),
                            new PendingStatusSkipRequest("Flinch", TurnPhase.ACTION, "flinch")
                    );
                })
                .build();

        LifecycleHookResult result = registry.resolve(context(state(Set.of("FLINCHED", "frozen")), TurnPhase.ACTION));

        assertEquals(List.of("early:flinched", "late:frozen"), calls);
        assertEquals(List.of("flinch_check", "freeze_check"), result.events().stream()
                .map(event -> ((RuleEffectEvent) event).effect())
                .toList());
        assertEquals("Frozen", result.pendingStatusSkip().status());
        assertEquals("freeze", result.pendingStatusSkip().reason());
    }

    @Test
    void ignoresUnownedStatusesAndWrongPhases() {
        StatusPhaseEffectRegistry registry = StatusPhaseEffectRegistry.builder()
                .register("sleep", List.of("Sleep", "Asleep"), TurnPhase.START, 10,
                        (context, status) -> LifecycleHookResult.pendingStatusSkip(
                                new PendingStatusSkipRequest(status, TurnPhase.START, "sleep")
                        ))
                .build();

        LifecycleHookResult wrongPhase = registry.resolve(context(state(Set.of("sleep")), TurnPhase.ACTION));
        LifecycleHookResult missingStatus = registry.resolve(context(state(Set.of("burned")), TurnPhase.START));

        assertNull(wrongPhase.pendingStatusSkip());
        assertNull(missingStatus.pendingStatusSkip());
        assertEquals(List.of(), wrongPhase.events());
        assertEquals(List.of(), missingStatus.events());
    }

    @Test
    void lifecycleAdapterUsesAuthoritativeActorStatuses() {
        StatusPhaseEffectRegistry statusRegistry = StatusPhaseEffectRegistry.builder()
                .register("flinch", List.of("flinch"), TurnPhase.ACTION, 10,
                        (context, status) -> LifecycleHookResult.pendingStatusSkip(
                                new PendingStatusSkipRequest(status, context.phase(), "status_rule")
                        ))
                .build();
        LifecycleHookRegistry lifecycle = LifecycleHookRegistry.builder()
                .register("status-phases", HookSource.STATUS, LifecycleHookPoint.PHASE_CHANGE, 300,
                        new StatusPhaseLifecycleHook(statusRegistry))
                .build();

        LifecycleHookResult result = lifecycle.resolve(
                LifecycleHookPoint.PHASE_CHANGE,
                context(state(Set.of("flinch")), TurnPhase.ACTION)
        );

        assertEquals("flinch", result.pendingStatusSkip().status());
        assertEquals("status_rule", result.pendingStatusSkip().reason());
    }

    @Test
    void rejectsDuplicateRuleIdsAndNonPhaseContext() {
        StatusPhaseEffectRegistry.Builder builder = StatusPhaseEffectRegistry.builder()
                .register("same", List.of("sleep"), TurnPhase.START, 1,
                        (context, status) -> LifecycleHookResult.empty());
        assertThrows(IllegalArgumentException.class, () -> builder.register(
                "SAME", List.of("freeze"), TurnPhase.START, 2,
                (context, status) -> LifecycleHookResult.empty()
        ));

        StatusPhaseEffectRegistry registry = builder.build();
        assertThrows(IllegalArgumentException.class, () -> registry.resolve(
                new LifecycleHookContext(state(Set.of("sleep")), LifecycleHookPoint.ROUND_START, 0, 1, "actor")
        ));
    }

    private static RuleEffectEvent event(LifecycleHookContext context, String status, String effect) {
        return new RuleEffectEvent(
                "status", status, context.actorId(), "", "", effect, 0,
                context.state().requireCombatant(context.actorId()).hp()
        );
    }

    private static LifecycleHookContext context(BattleRuntimeState state, TurnPhase phase) {
        return new LifecycleHookContext(
                state,
                new RoundDamageHistoryState(),
                new RoundInjuryHistoryState(),
                LifecycleHookPoint.PHASE_CHANGE,
                1,
                1,
                "actor",
                phase
        );
    }

    private static BattleRuntimeState state(Set<String> statuses) {
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
                Map.of("actor", statuses)
        );
    }
}
