package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnEndEffectRegistryTest {
    @Test
    void resolvesActorAndGlobalEffectsInRegistrationOrderWithStableRosterTraversal() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        RuntimeCombatantState other = combatant("other", new GridCoord(1, 0));
        BattleRuntimeState state = state(actor, other);
        List<String> calls = new ArrayList<>();

        TurnEndEffectRegistry registry = TurnEndEffectRegistry.builder()
                .register("global-second", TurnEndEffectRegistry.Scope.ALL_COMBATANTS, 20, (context, combatantId) -> {
                    calls.add("global:" + combatantId);
                    return LifecycleHookResult.empty();
                })
                .register("actor-first", TurnEndEffectRegistry.Scope.ACTOR, 10, (context, combatantId) -> {
                    calls.add("actor:" + combatantId);
                    return LifecycleHookResult.empty();
                })
                .build();

        registry.resolve(context(state, "actor", LifecycleHookPoint.TURN_END));

        assertEquals(List.of("actor:actor", "global:actor", "global:other"), calls);
        assertEquals(List.of("actor-first", "global-second"),
                registry.registrations().stream().map(TurnEndEffectRegistry.Registration::id).toList());
    }

    @Test
    void actorScopedEffectRequiresActorIdentity() {
        TurnEndEffectRegistry registry = TurnEndEffectRegistry.builder()
                .register("actor-only", TurnEndEffectRegistry.Scope.ACTOR, 10,
                        (context, combatantId) -> LifecycleHookResult.empty())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(context(state(combatant("actor", new GridCoord(0, 0))), "", LifecycleHookPoint.TURN_END)));
    }

    @Test
    void rejectsNonTurnEndLifecycleContext() {
        TurnEndEffectRegistry registry = TurnEndEffectRegistry.builder().build();

        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(context(state(combatant("actor", new GridCoord(0, 0))), "actor", LifecycleHookPoint.PHASE_CHANGE)));
    }

    @Test
    void rejectsDuplicateIdsCaseInsensitively() {
        TurnEndEffectRegistry.Builder builder = TurnEndEffectRegistry.builder()
                .register("adaptive", TurnEndEffectRegistry.Scope.ACTOR, 10,
                        (context, combatantId) -> LifecycleHookResult.empty());

        assertThrows(IllegalArgumentException.class,
                () -> builder.register("ADAPTIVE", TurnEndEffectRegistry.Scope.ACTOR, 20,
                        (context, combatantId) -> LifecycleHookResult.empty()));
    }

    @Test
    void builtinTurnEndBoundaryFollowsCleanupAndRefresh() {
        List<LifecycleHookRegistry.Registration> hooks = BuiltinLifecycleHooks.registry().registrations().stream()
                .filter(registration -> registration.point() == LifecycleHookPoint.TURN_END)
                .toList();

        assertEquals(List.of(
                        "turn-extra-action-cleanup",
                        "turn-last-turn-round-refresh",
                        "turn-end-effects"
                ), hooks.stream().map(LifecycleHookRegistry.Registration::id).toList());
        assertEquals(List.of(490, 500, 510), hooks.stream().map(LifecycleHookRegistry.Registration::order).toList());
    }

    private static LifecycleHookContext context(
            BattleRuntimeState state,
            String actorId,
            LifecycleHookPoint point
    ) {
        return new LifecycleHookContext(
                state,
                state.damageHistory(),
                state.injuryHistory(),
                point,
                4,
                4,
                actorId,
                TurnPhase.END
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 1),
                20,
                20,
                new ActionBudget()
        );
    }

    private static BattleRuntimeState state(RuntimeCombatantState... combatants) {
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(combatants),
                Map.of()
        );
    }
}
