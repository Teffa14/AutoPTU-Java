package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemporaryEffectRefreshLifecycleHookTest {
    @Test
    void actorRefreshReplacesAllPriorEntriesWithOneFreshPayload() {
        RuntimeCombatantState actor = combatant("actor", new GridCoord(0, 0));
        actor.temporaryEffects().add("last_turn_round", Map.of("round", 1));
        actor.temporaryEffects().add("last_turn_round", Map.of("round", 2));
        RuntimeCombatantState other = combatant("other", new GridCoord(1, 0));
        other.temporaryEffects().add("last_turn_round", Map.of("round", 1));
        BattleRuntimeState state = state(actor, other);

        TemporaryEffectRefreshLifecycleHook hook = new TemporaryEffectRefreshLifecycleHook(
                TemporaryEffectRefreshLifecycleHook.Scope.ACTOR,
                "last_turn_round",
                (context, combatantId) -> Map.of("round", context.round(), "owner", combatantId)
        );
        hook.apply(context(state, 4, "actor"));

        assertEquals(1, actor.temporaryEffects().getAll("last_turn_round").size());
        assertEquals(4, actor.temporaryEffects().getAll("last_turn_round").get(0).payload().get("round"));
        assertEquals("actor", actor.temporaryEffects().getAll("last_turn_round").get(0).payload().get("owner"));
        assertEquals(1, other.temporaryEffects().getAll("last_turn_round").size());
        assertEquals(1, other.temporaryEffects().getAll("last_turn_round").get(0).payload().get("round"));
    }

    @Test
    void allCombatantsRefreshUsesStableRosterTraversal() {
        RuntimeCombatantState first = combatant("first", new GridCoord(0, 0));
        RuntimeCombatantState second = combatant("second", new GridCoord(1, 0));
        BattleRuntimeState state = state(first, second);

        TemporaryEffectRefreshLifecycleHook hook = new TemporaryEffectRefreshLifecycleHook(
                TemporaryEffectRefreshLifecycleHook.Scope.ALL_COMBATANTS,
                "round_marker",
                (context, combatantId) -> Map.of("round", context.round(), "owner", combatantId)
        );
        hook.apply(context(state, 7, "first"));

        assertEquals("first", first.temporaryEffects().getAll("round_marker").get(0).payload().get("owner"));
        assertEquals("second", second.temporaryEffects().getAll("round_marker").get(0).payload().get("owner"));
        assertEquals(7, first.temporaryEffects().getAll("round_marker").get(0).payload().get("round"));
        assertEquals(7, second.temporaryEffects().getAll("round_marker").get(0).payload().get("round"));
    }

    @Test
    void actorRefreshRequiresActorIdentity() {
        TemporaryEffectRefreshLifecycleHook hook = new TemporaryEffectRefreshLifecycleHook(
                TemporaryEffectRefreshLifecycleHook.Scope.ACTOR,
                "last_turn_round",
                (context, combatantId) -> Map.of("round", context.round())
        );

        assertThrows(IllegalArgumentException.class, () -> hook.apply(context(state(combatant("actor", new GridCoord(0, 0))), 2, "")));
    }

    @Test
    void builtinTurnEndUsesOrderedCleanupRefreshThenEffectBoundary() {
        List<LifecycleHookRegistry.Registration> hooks = BuiltinLifecycleHooks.registry().registrations().stream()
                .filter(registration -> registration.point() == LifecycleHookPoint.TURN_END)
                .toList();

        assertEquals(List.of(
                        "turn-extra-action-cleanup",
                        "turn-last-turn-round-refresh",
                        "turn-end-effects"
                ), hooks.stream().map(LifecycleHookRegistry.Registration::id).toList());
        assertEquals(List.of(490, 500, 510), hooks.stream().map(LifecycleHookRegistry.Registration::order).toList());

        TemporaryEffectCleanupLifecycleHook cleanup = (TemporaryEffectCleanupLifecycleHook) hooks.get(0).hook();
        assertEquals(TemporaryEffectCleanupLifecycleHook.Scope.ACTOR, cleanup.scope());
        assertEquals(List.of("extra_action"), cleanup.effectNames());

        TemporaryEffectRefreshLifecycleHook refresh = (TemporaryEffectRefreshLifecycleHook) hooks.get(1).hook();
        assertEquals(TemporaryEffectRefreshLifecycleHook.Scope.ACTOR, refresh.scope());
        assertEquals("last_turn_round", refresh.effectName());

        assertEquals(TurnEndEffectHook.class, hooks.get(2).hook().getClass());
    }

    private static LifecycleHookContext context(BattleRuntimeState state, int round, String actorId) {
        return new LifecycleHookContext(
                state,
                state.damageHistory(),
                state.injuryHistory(),
                LifecycleHookPoint.TURN_END,
                round,
                round,
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
