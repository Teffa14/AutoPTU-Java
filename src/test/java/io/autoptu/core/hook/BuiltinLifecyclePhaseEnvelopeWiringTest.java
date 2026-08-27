package io.autoptu.core.hook;

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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BuiltinLifecyclePhaseEnvelopeWiringTest {
    @Test
    void turnStartAndPhaseChangeUseTheSharedStatusControllerEnvelope() {
        LifecycleHookRegistry registry = BuiltinLifecycleHooks.registry();

        LifecycleHookRegistry.Registration turnStart = registration(
                registry,
                LifecycleHookPoint.TURN_START,
                "combatant-turn-start-effects"
        );
        LifecycleHookRegistry.Registration phaseChange = registration(
                registry,
                LifecycleHookPoint.PHASE_CHANGE,
                "combatant-phase-effects"
        );

        assertInstanceOf(StatusControllerPhaseEnvelopeDispatcher.class, turnStart.hook());
        assertInstanceOf(StatusControllerPhaseEnvelopeDispatcher.class, phaseChange.hook());
        assertSame(turnStart.hook(), phaseChange.hook());
    }

    @Test
    void liveTurnStartStillPropagatesCombatantStatusSkipThroughEnvelope() {
        LifecycleHookRegistry registry = BuiltinLifecycleHooks.registry();
        BattleRuntimeState state = stateWithFlinch();

        LifecycleHookResult result = registry.resolve(
                LifecycleHookPoint.TURN_START,
                new LifecycleHookContext(
                        state,
                        new RoundDamageHistoryState(),
                        new RoundInjuryHistoryState(),
                        LifecycleHookPoint.TURN_START,
                        1,
                        1,
                        "actor",
                        TurnPhase.START
                )
        );

        assertNotNull(result.pendingStatusSkip());
        assertEquals("flinch", result.pendingStatusSkip().status());
        assertEquals(TurnPhase.START, result.pendingStatusSkip().phase());
        assertEquals(1, result.events().size());
    }

    private static LifecycleHookRegistry.Registration registration(
            LifecycleHookRegistry registry,
            LifecycleHookPoint point,
            String id
    ) {
        return registry.registrations().stream()
                .filter(entry -> entry.point() == point && entry.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static BattleRuntimeState stateWithFlinch() {
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
                Map.of("actor", Set.of("flinch"))
        );
    }
}
