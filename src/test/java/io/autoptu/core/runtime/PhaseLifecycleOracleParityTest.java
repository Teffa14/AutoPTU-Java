package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.PhaseChangedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.LifecycleHookResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhaseLifecycleOracleParityTest {
    @Test
    void authoritativePhaseTransitionsMatchPythonContract() throws IOException {
        String fixturePath = System.getProperty("autoptu.phase.lifecycle.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        BattleRuntimeState state = state();
        AtomicReference<TurnPhase> hookPhase = new AtomicReference<>();
        LifecycleHookRegistry hooks = LifecycleHookRegistry.builder()
                .register("phase-probe", HookSource.SYSTEM, LifecycleHookPoint.PHASE_CHANGE, 100, context -> {
                    hookPhase.set(context.phase());
                    return LifecycleHookResult.events(List.of(new RuleEffectEvent(
                            "system", "phase-probe", context.actorId(), "", "", context.phase().value(), 0, 20
                    )));
                })
                .build();
        BattleRoundController controller = new BattleRoundController(
                state, 2, hooks, state.damageHistory(), new RoundInjuryHistoryState(), new BattleTurnState()
        );

        assertEquals(1, fixture.get("requires_current_actor"));
        assertThrows(IllegalStateException.class, controller::advancePhase);

        controller.beginTurn("actor");
        assertTransition(controller, TurnPhase.COMMAND, hookPhase, fixture);
        assertTransition(controller, TurnPhase.ACTION, hookPhase, fixture);
        assertTransition(controller, TurnPhase.END, hookPhase, fixture);

        assertEquals(1, fixture.get("end_phase_is_terminal"));
        assertEquals(List.of(), controller.advancePhase());
        assertEquals(TurnPhase.END, controller.turnState().phase());

        // These Python responsibilities are intentionally frozen but remain later
        // bounded slices on top of the generic PHASE_CHANGE registry.
        assertEquals(1, fixture.get("dispatches_phase_change"));
        assertEquals(1, fixture.get("runs_status_phase_effects"));
        assertEquals(1, fixture.get("consumes_pending_status_skip"));
    }

    private static void assertTransition(
            BattleRoundController controller,
            TurnPhase expected,
            AtomicReference<TurnPhase> hookPhase,
            Map<String, Integer> fixture
    ) {
        List<BattleEvent> events = controller.advancePhase();
        assertEquals(1, fixture.get("logs_phase_event"));
        assertEquals(2, events.size());
        PhaseChangedEvent phaseEvent = assertInstanceOf(PhaseChangedEvent.class, events.get(0));
        assertEquals("actor", phaseEvent.actorId());
        assertEquals(controller.round(), phaseEvent.round());
        assertEquals(expected, phaseEvent.phase());
        assertEquals(expected, controller.turnState().phase());
        assertEquals(expected, hookPhase.get());
        assertInstanceOf(RuleEffectEvent.class, events.get(1));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 1), 20, 20, new ActionBudget()
        );
        return new BattleRuntimeState(new MovementGrid(4, 4, Set.of(), Map.of()), List.of(actor));
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
