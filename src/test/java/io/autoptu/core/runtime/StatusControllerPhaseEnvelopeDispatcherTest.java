package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookResult;
import io.autoptu.core.hook.PendingStatusSkipRequest;
import io.autoptu.core.hook.StatusControllerPhaseEnvelopeDispatcher;
import io.autoptu.core.hook.StatusControllerPhaseOrderingPolicy;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.TurnPhase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StatusControllerPhaseEnvelopeDispatcherTest {
    @Test
    void startRunsHeldItemFoodAndCombatantHooksInOracleOrder() {
        PendingStatusSkipRequest ignoredEnvelopeSkip =
                new PendingStatusSkipRequest("Sleep", TurnPhase.START, "held item should not own status skip");
        PendingStatusSkipRequest combatantSkip =
                new PendingStatusSkipRequest("Flinch", TurnPhase.START, "combatant phase effect");

        StatusControllerPhaseEnvelopeDispatcher dispatcher = StatusControllerPhaseEnvelopeDispatcher.builder()
                .step(StatusControllerPhaseOrderingPolicy.Step.HELD_ITEM_START, hook("held-start", ignoredEnvelopeSkip))
                .step(StatusControllerPhaseOrderingPolicy.Step.FOOD_REGEN, hook("food-regen", null))
                .step(StatusControllerPhaseOrderingPolicy.Step.FOOD_BUFF_START, hook("food-buff", null))
                .step(StatusControllerPhaseOrderingPolicy.Step.COMBATANT_PHASE_EFFECTS, hook("combatant", combatantSkip))
                .step(StatusControllerPhaseOrderingPolicy.Step.HELD_ITEM_END, hook("held-end", null))
                .build();

        LifecycleHookResult result = dispatcher.apply(context(TurnPhase.START));

        assertEquals(List.of("held-start", "food-regen", "food-buff", "combatant"), effects(result));
        assertEquals(combatantSkip, result.pendingStatusSkip());
    }

    @Test
    void endRunsCombatantBeforeHeldItemAndDoesNotLetEnvelopeReplaceStatusSkip() {
        PendingStatusSkipRequest combatantSkip =
                new PendingStatusSkipRequest("Freeze", TurnPhase.END, "combatant phase effect");
        PendingStatusSkipRequest ignoredEnvelopeSkip =
                new PendingStatusSkipRequest("Sleep", TurnPhase.END, "held item should not own status skip");

        StatusControllerPhaseEnvelopeDispatcher dispatcher = StatusControllerPhaseEnvelopeDispatcher.builder()
                .step(StatusControllerPhaseOrderingPolicy.Step.COMBATANT_PHASE_EFFECTS, hook("combatant", combatantSkip))
                .step(StatusControllerPhaseOrderingPolicy.Step.HELD_ITEM_END, hook("held-end", ignoredEnvelopeSkip))
                .build();

        LifecycleHookResult result = dispatcher.apply(context(TurnPhase.END));

        assertEquals(List.of("combatant", "held-end"), effects(result));
        assertEquals(combatantSkip, result.pendingStatusSkip());
    }

    @Test
    void commandAndActionRunOnlyCombatantPhaseEffects() {
        StatusControllerPhaseEnvelopeDispatcher dispatcher = StatusControllerPhaseEnvelopeDispatcher.builder()
                .step(StatusControllerPhaseOrderingPolicy.Step.HELD_ITEM_START, hook("held-start", null))
                .step(StatusControllerPhaseOrderingPolicy.Step.FOOD_REGEN, hook("food-regen", null))
                .step(StatusControllerPhaseOrderingPolicy.Step.FOOD_BUFF_START, hook("food-buff", null))
                .step(StatusControllerPhaseOrderingPolicy.Step.COMBATANT_PHASE_EFFECTS, hook("combatant", null))
                .step(StatusControllerPhaseOrderingPolicy.Step.HELD_ITEM_END, hook("held-end", null))
                .build();

        LifecycleHookResult command = dispatcher.apply(context(TurnPhase.COMMAND));
        LifecycleHookResult action = dispatcher.apply(context(TurnPhase.ACTION));

        assertEquals(List.of("combatant"), effects(command));
        assertEquals(List.of("combatant"), effects(action));
        assertNull(command.pendingStatusSkip());
        assertNull(action.pendingStatusSkip());
    }

    private static LifecycleHook hook(String effect, PendingStatusSkipRequest pendingStatusSkip) {
        return context -> LifecycleHookResult.eventsAndPendingStatusSkip(
                List.of(new RuleEffectEvent("test", effect, "actor", "", "", effect, 0.0, 100)),
                pendingStatusSkip
        );
    }

    private static List<String> effects(LifecycleHookResult result) {
        return result.events().stream()
                .map(event -> ((RuleEffectEvent) event).effect())
                .toList();
    }

    private static LifecycleHookContext context(TurnPhase phase) {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(2, 2, Set.of(), Map.of()),
                List.of()
        );
        return new LifecycleHookContext(
                state,
                new RoundDamageHistoryState(),
                new RoundInjuryHistoryState(),
                LifecycleHookPoint.PHASE_CHANGE,
                0,
                1,
                "actor",
                phase
        );
    }
}
