package io.autoptu.core.hook;

import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinTurnEndEffectsPsionicOverloadTest {
    @Test
    void registryPlacesPsionicOverloadAfterActorScopedTurnEndFamilies() {
        List<TurnEndEffectRegistry.Registration> registrations = BuiltinTurnEndEffects.registry().registrations();

        assertEquals(
                List.of(
                        "adaptive-geography-terrain-alias-cleanup",
                        "psionic-sponge-borrowed-move-cleanup",
                        "psionic-overload-telekinesis-tick"
                ),
                registrations.stream().map(TurnEndEffectRegistry.Registration::id).toList()
        );
        assertEquals(TurnEndEffectRegistry.Scope.ALL_COMBATANTS, registrations.get(2).scope());
        assertEquals(30, registrations.get(2).order());
    }

    @Test
    void liftedTargetTakesOneTickAndEmitsTrainerFeatureEventFromFirstBindingSource() {
        RuntimeCombatantState actor = combatant("active", 30, 30);
        RuntimeCombatantState target = combatant("target", 37, 40);
        target.temporaryEffects().add("psionic_overload_telekinesis", Map.of("source_id", "trainer-a"));
        target.temporaryEffects().add("psionic_overload_telekinesis", Map.of("source_id", "trainer-b"));
        BattleRuntimeState state = state(List.of(actor, target), Map.of("target", List.of("Lifted")));

        LifecycleHookResult result = BuiltinTurnEndEffects.registry().resolve(context(state, "active"));

        assertEquals(33, target.hp());
        assertEquals(2, target.temporaryEffects().getAll("psionic_overload_telekinesis").size());
        assertEquals(1, result.events().size());
        TrainerFeatureEvent event = assertInstanceOf(TrainerFeatureEvent.class, result.events().get(0));
        assertEquals("trainer-a", event.actorId());
        assertEquals("Psionic Overload", event.feature());
        assertEquals("telekinesis_tick", event.effect());
        assertEquals("target", event.details().get("target"));
        assertEquals(4, event.amount());
        assertEquals(33, event.targetHp());
    }

    @Test
    void losingLiftedClearsAllBindingsWithoutDamageOrEvent() {
        RuntimeCombatantState target = combatant("target", 40, 40);
        target.temporaryEffects().add("psionic_overload_telekinesis", Map.of("source_id", "trainer-a"));
        target.temporaryEffects().add("psionic_overload_telekinesis", Map.of("source_id", "trainer-b"));
        BattleRuntimeState state = state(List.of(target), Map.of());

        LifecycleHookResult result = BuiltinTurnEndEffects.registry().resolve(context(state, "target"));

        assertEquals(40, target.hp());
        assertFalse(target.temporaryEffects().has("psionic_overload_telekinesis"));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void alreadyFaintedTargetIsSkippedAndKeepsBinding() {
        RuntimeCombatantState target = combatant("target", 0, 40);
        target.temporaryEffects().add("psionic_overload_telekinesis", Map.of("source_id", "trainer-a"));
        BattleRuntimeState state = state(List.of(target), Map.of("target", List.of("Lifted")));

        LifecycleHookResult result = BuiltinTurnEndEffects.registry().resolve(context(state, "target"));

        assertEquals(0, target.hp());
        assertTrue(target.temporaryEffects().has("psionic_overload_telekinesis"));
        assertTrue(result.events().isEmpty());
    }

    @Test
    void lethalTickReportsClampedHpDamageAndZeroTargetHp() {
        RuntimeCombatantState target = combatant("target", 2, 40);
        target.temporaryEffects().add("psionic_overload_telekinesis", Map.of("source_id", "trainer-a"));
        BattleRuntimeState state = state(List.of(target), Map.of("target", List.of("Lifted")));

        LifecycleHookResult result = BuiltinTurnEndEffects.registry().resolve(context(state, "target"));

        assertEquals(0, target.hp());
        TrainerFeatureEvent event = assertInstanceOf(TrainerFeatureEvent.class, result.events().get(0));
        assertEquals(2, event.amount());
        assertEquals(0, event.targetHp());
    }

    private static LifecycleHookContext context(BattleRuntimeState state, String actorId) {
        return new LifecycleHookContext(state, LifecycleHookPoint.TURN_END, 2, 3, actorId);
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
