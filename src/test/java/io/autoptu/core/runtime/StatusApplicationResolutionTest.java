package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.BuiltinStatusApplicationHooks;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.StatusApplicationHookRegistry;
import io.autoptu.core.hook.StatusApplicationHookResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusApplicationResolutionTest {
    @Test
    void innerFocusBlocksFlinchBeforeCanonicalMutation() {
        BattleRuntimeState state = state(List.of("Inner Focus"));

        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target",
                new StatusEntry("Flinched", Map.of("applied_round", 4)),
                "move", "Fake Out", "fake-out"
        );

        assertFalse(result.applied());
        assertFalse(state.hasStatus("target", "flinched"));
        assertEquals(1, result.events().size());
        RuleEffectEvent event = (RuleEffectEvent) result.events().getFirst();
        assertEquals("ability", event.sourceKind());
        assertEquals("Inner Focus", event.sourceName());
        assertEquals("status_block", event.effect());
        assertEquals("target", event.actorId());
    }

    @Test
    void unrelatedStatusStillAppliesWithInnerFocus() {
        BattleRuntimeState state = state(List.of("Inner Focus"));
        StatusEntry burned = new StatusEntry("Burned", Map.of("source", "ember"));

        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "target", burned,
                "move", "Ember", "ember"
        );

        assertTrue(result.applied());
        assertEquals(burned, state.statusEntry("target", "burned").orElseThrow());
    }

    @Test
    void registryIsOrderedAndStopsAfterFirstBlock() {
        BattleRuntimeState state = state(List.of());
        StatusApplicationHookRegistry hooks = StatusApplicationHookRegistry.builder()
                .register("first", HookSource.SYSTEM, 10, context -> StatusApplicationHookResult.allow())
                .register("block", HookSource.SYSTEM, 20, context -> StatusApplicationHookResult.block(List.of()))
                .register("must-not-run", HookSource.SYSTEM, 30, context -> {
                    throw new AssertionError("hook after block executed");
                })
                .build();

        StatusApplicationResult result = StatusApplicationResolution.apply(
                state, hooks, "source", "target", new StatusEntry("Burned"), "move", "Ember", "ember"
        );

        assertFalse(result.applied());
        assertFalse(state.hasStatus("target", "burned"));
    }

    @Test
    void unknownTargetFailsBeforeHooksOrMutation() {
        BattleRuntimeState state = state(List.of());
        assertThrows(IllegalArgumentException.class, () -> StatusApplicationResolution.apply(
                state, BuiltinStatusApplicationHooks.registry(), "source", "intruder",
                new StatusEntry("Burned"), "move", "Ember", "ember"
        ));
    }

    private static BattleRuntimeState state(List<String> targetAbilities) {
        RuntimeCombatantState source = combatant("source", List.of());
        RuntimeCombatantState target = combatant("target", targetAbilities);
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(source, target)
        );
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 3),
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
                List.of("Normal"),
                List.of(),
                abilities
        );
    }
}
