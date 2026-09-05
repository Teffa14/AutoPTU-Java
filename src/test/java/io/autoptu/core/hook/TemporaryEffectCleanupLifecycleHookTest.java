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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryEffectCleanupLifecycleHookTest {
    @Test
    void allCombatantsCleanupRemovesOnlyRegisteredFamilies() {
        RuntimeCombatantState first = combatant("first", new GridCoord(0, 0));
        RuntimeCombatantState second = combatant("second", new GridCoord(1, 0));
        for (RuntimeCombatantState combatant : List.of(first, second)) {
            combatant.temporaryEffects().add("extra_action", Map.of("source", "fixture"));
            combatant.temporaryEffects().add("riposte_ready", Map.of("source", "fixture"));
            combatant.temporaryEffects().add("persistent_fixture", Map.of("source", "fixture"));
        }
        BattleRuntimeState state = state(first, second);

        TemporaryEffectCleanupLifecycleHook hook = new TemporaryEffectCleanupLifecycleHook(
                TemporaryEffectCleanupLifecycleHook.Scope.ALL_COMBATANTS,
                List.of("extra_action", "riposte_ready")
        );
        hook.apply(context(state, ""));

        for (RuntimeCombatantState combatant : List.of(first, second)) {
            assertTrue(combatant.temporaryEffects().getAll("extra_action").isEmpty());
            assertTrue(combatant.temporaryEffects().getAll("riposte_ready").isEmpty());
            assertEquals(1, combatant.temporaryEffects().getAll("persistent_fixture").size());
        }
    }

    @Test
    void actorCleanupDoesNotTouchOtherCombatants() {
        RuntimeCombatantState first = combatant("first", new GridCoord(0, 0));
        RuntimeCombatantState second = combatant("second", new GridCoord(1, 0));
        first.temporaryEffects().add("extra_action", Map.of());
        second.temporaryEffects().add("extra_action", Map.of());
        BattleRuntimeState state = state(first, second);

        TemporaryEffectCleanupLifecycleHook hook = new TemporaryEffectCleanupLifecycleHook(
                TemporaryEffectCleanupLifecycleHook.Scope.ACTOR,
                List.of("extra_action")
        );
        hook.apply(context(state, "first"));

        assertTrue(first.temporaryEffects().getAll("extra_action").isEmpty());
        assertEquals(1, second.temporaryEffects().getAll("extra_action").size());
    }

    @Test
    void actorCleanupRequiresActorIdentity() {
        RuntimeCombatantState first = combatant("first", new GridCoord(0, 0));
        TemporaryEffectCleanupLifecycleHook hook = new TemporaryEffectCleanupLifecycleHook(
                TemporaryEffectCleanupLifecycleHook.Scope.ACTOR,
                List.of("extra_action")
        );

        assertThrows(IllegalArgumentException.class, () -> hook.apply(context(state(first), "")));
    }

    @Test
    void builtinRoundCleanupUsesDeclarativeTemporaryEffectHook() {
        LifecycleHookRegistry.Registration registration = BuiltinLifecycleHooks.registry().registrations().stream()
                .filter(entry -> entry.id().equals("round-temporary-effect-cleanup"))
                .findFirst()
                .orElseThrow();

        TemporaryEffectCleanupLifecycleHook hook =
                (TemporaryEffectCleanupLifecycleHook) registration.hook();
        assertEquals(TemporaryEffectCleanupLifecycleHook.Scope.ALL_COMBATANTS, hook.scope());
        assertEquals(
                List.of("intercept_ready", "extra_action", "delayed", "riposte_ready"),
                hook.effectNames()
        );
    }

    private static LifecycleHookContext context(BattleRuntimeState state, String actorId) {
        return new LifecycleHookContext(
                state,
                new RoundDamageHistoryState(),
                new RoundInjuryHistoryState(),
                LifecycleHookPoint.ROUND_START,
                2,
                2,
                actorId,
                TurnPhase.START
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
