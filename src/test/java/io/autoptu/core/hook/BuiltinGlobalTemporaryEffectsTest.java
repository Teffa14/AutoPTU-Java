package io.autoptu.core.hook;

import io.autoptu.core.event.AbilityEvent;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinGlobalTemporaryEffectsTest {
    @Test
    void corrosiveToxinsAppliesOnePoisonTickAndPreservesActiveActorInEvent() {
        RuntimeCombatantState active = combatant("active", 30, 30);
        RuntimeCombatantState target = combatant("target", 40, 40);
        target.temporaryEffects().add("corrosive_tick", Map.of("round", 3));
        BattleRuntimeState state = state(
                List.of(active, target),
                Map.of("target", List.of("Poisoned"))
        );

        LifecycleHookResult result = new GlobalTemporaryEffectPhaseHook(BuiltinGlobalTemporaryEffects.registry())
                .apply(context(state, 3, "active", TurnPhase.END));

        assertEquals(36, target.hp());
        assertFalse(target.temporaryEffects().has("corrosive_tick"));
        assertEquals(1, result.events().size());
        AbilityEvent event = assertInstanceOf(AbilityEvent.class, result.events().get(0));
        assertEquals("active", event.actorId());
        assertEquals("target", event.target());
        assertEquals("Corrosive Toxins", event.ability());
        assertEquals("tick", event.effect());
        assertEquals(36, event.targetHp());
        assertEquals(4, event.details().get("amount"));
        assertEquals("end", event.details().get("phase"));
        assertEquals("Corrosive Toxins applies a poison tick.", event.description());
    }

    @Test
    void corrosiveToxinsConsumesStaleOrUnqualifiedEntriesWithoutDamageOrEvents() {
        RuntimeCombatantState stale = combatant("stale", 40, 40);
        stale.temporaryEffects().add("corrosive_tick", Map.of("round", 2));
        RuntimeCombatantState noPoison = combatant("no-poison", 40, 40);
        noPoison.temporaryEffects().add("corrosive_tick", Map.of("round", 3));
        BattleRuntimeState state = state(List.of(stale, noPoison), Map.of());

        LifecycleHookResult result = new GlobalTemporaryEffectPhaseHook(BuiltinGlobalTemporaryEffects.registry())
                .apply(context(state, 3, "stale", TurnPhase.END));

        assertEquals(40, stale.hp());
        assertEquals(40, noPoison.hp());
        assertFalse(stale.temporaryEffects().has("corrosive_tick"));
        assertFalse(noPoison.temporaryEffects().has("corrosive_tick"));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void globalTemporaryRegistryTraversesTargetsInStableBattleOrderAndOnlyDuringRegisteredPhase() {
        RuntimeCombatantState first = combatant("first", 20, 20);
        RuntimeCombatantState second = combatant("second", 20, 20);
        RuntimeCombatantState third = combatant("third", 20, 20);
        second.temporaryEffects().add("test_effect", Map.of("round", 1));
        third.temporaryEffects().add("test_effect", Map.of("round", 1));
        BattleRuntimeState state = state(List.of(first, second, third), Map.of());
        List<String> observed = new ArrayList<>();
        GlobalTemporaryEffectPhaseRegistry registry = GlobalTemporaryEffectPhaseRegistry.builder()
                .register("test.end", "test_effect", TurnPhase.END, 100, (context, targetId, entry) -> {
                    observed.add(targetId);
                    return LifecycleHookResult.empty();
                })
                .build();

        registry.resolve(context(state, 1, "first", TurnPhase.COMMAND));
        assertTrue(observed.isEmpty());

        registry.resolve(context(state, 1, "first", TurnPhase.END));
        assertEquals(List.of("second", "third"), observed);
    }

    @Test
    void builtinLifecyclePlacesGlobalTemporaryEffectsAfterCombatantPhaseEnvelope() {
        List<LifecycleHookRegistry.Registration> phaseHooks = BuiltinLifecycleHooks.registry().registrations().stream()
                .filter(registration -> registration.point() == LifecycleHookPoint.PHASE_CHANGE)
                .toList();

        assertEquals(List.of("combatant-phase-effects", "global-temporary-phase-effects"),
                phaseHooks.stream().map(LifecycleHookRegistry.Registration::id).toList());
        assertTrue(phaseHooks.get(0).order() < phaseHooks.get(1).order());
    }

    private static LifecycleHookContext context(
            BattleRuntimeState state,
            int round,
            String actorId,
            TurnPhase phase
    ) {
        return new LifecycleHookContext(
                state,
                state.damageHistory(),
                state.injuryHistory(),
                LifecycleHookPoint.PHASE_CHANGE,
                Math.max(0, round - 1),
                round,
                actorId,
                phase
        );
    }

    private static BattleRuntimeState state(
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends List<String>> statuses
    ) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                combatants,
                statuses
        );
    }

    private static RuntimeCombatantState combatant(String id, int hp, int maxHp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 1),
                hp,
                maxHp,
                new ActionBudget()
        );
    }
}
