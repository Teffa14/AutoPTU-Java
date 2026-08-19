package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.PhaseChangedEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.hook.AbilityPhaseLifecycleHook;
import io.autoptu.core.hook.BuiltinAbilityPhaseEffects;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.model.ActionType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityPhaseLancerOracleParityTest {
    @Test
    void currentRoundChargeGrantsCritRangeAndDropsOnlyStaleShiftEntries() throws IOException {
        Map<String, Integer> fixture = fixture();
        assertEquals(1, fixture.get("registered_at_end"));
        assertEquals(1, fixture.get("reads_lancer_shift"));
        assertEquals(1, fixture.get("drops_stale_round_entries"));
        assertEquals(1, fixture.get("requires_shift_distance_three"));
        assertEquals(1, fixture.get("grants_crit_range_bonus_three"));
        assertEquals(1, fixture.get("crit_bonus_expires_next_round"));

        BattleRuntimeState state = state(List.of("Lancer"));
        RuntimeCombatantState actor = state.requireCombatant("actor");
        actor.temporaryEffects().add("lancer_shift", Map.of("round", 2, "distance", 9));
        actor.temporaryEffects().add("lancer_shift", Map.of("round", 3, "distance", 1));
        actor.temporaryEffects().add("lancer_shift", Map.of("round", 3, "distance", 4));

        List<BattleEvent> events = controllerAtAction(state, 3).advancePhase();

        assertEquals(2, events.size());
        assertInstanceOf(PhaseChangedEvent.class, events.get(0));
        RuleEffectEvent event = assertInstanceOf(RuleEffectEvent.class, events.get(1));
        assertEquals("Lancer", event.sourceName());
        assertEquals("crit_range", event.effect());
        assertEquals(3.0, event.amount());
        assertEquals(2, actor.temporaryEffects().count("lancer_shift"));
        TemporaryEffectEntry bonus = actor.temporaryEffects().getAll("crit_range_bonus").getFirst();
        assertEquals(3, ((Number) bonus.payload().get("bonus")).intValue());
        assertEquals(4, ((Number) bonus.payload().get("expires_round")).intValue());
    }

    @Test
    void holdingPositionGrantsDamageReduction() throws IOException {
        Map<String, Integer> fixture = fixture();
        assertEquals(1, fixture.get("hold_position_checks_shift_action"));
        assertEquals(1, fixture.get("grants_damage_reduction_five"));

        BattleRuntimeState state = state(List.of("Lancer"));
        RuntimeCombatantState actor = state.requireCombatant("actor");

        List<BattleEvent> events = controllerAtAction(state, 5).advancePhase();

        RuleEffectEvent event = assertInstanceOf(RuleEffectEvent.class, events.get(1));
        assertEquals("damage_reduction", event.effect());
        assertEquals(5.0, event.amount());
        TemporaryEffectEntry reduction = actor.temporaryEffects().getAll("damage_reduction").getFirst();
        assertEquals(5, ((Number) reduction.payload().get("amount")).intValue());
        assertEquals(6, ((Number) reduction.payload().get("expires_round")).intValue());
        assertEquals(false, reduction.payload().get("consume"));
    }

    @Test
    void aShortShiftDoesNotReceiveHoldPositionReduction() {
        BattleRuntimeState state = state(List.of("Lancer"));
        RuntimeCombatantState actor = state.requireCombatant("actor");
        actor.actionBudget().markAction(ActionType.SHIFT, "shifted");
        actor.temporaryEffects().add("lancer_shift", Map.of("round", 4, "distance", 2));

        List<BattleEvent> events = controllerAtAction(state, 4).advancePhase();

        assertEquals(1, events.size());
        assertInstanceOf(PhaseChangedEvent.class, events.getFirst());
        assertFalse(actor.temporaryEffects().has("crit_range_bonus"));
        assertFalse(actor.temporaryEffects().has("damage_reduction"));
    }

    @Test
    void registryUsesCanonicalAbilityIdentityAndRejectsDuplicateIds() {
        BattleRuntimeState state = state(List.of("Lancer [Errata]"));
        List<BattleEvent> events = controllerAtAction(state, 2).advancePhase();
        assertEquals(2, events.size());
        assertEquals("damage_reduction", assertInstanceOf(RuleEffectEvent.class, events.get(1)).effect());

        assertThrows(IllegalArgumentException.class, () -> io.autoptu.core.hook.AbilityPhaseEffectRegistry.builder()
                .register("same", "Lancer", TurnPhase.END, 10, (context, ability) -> io.autoptu.core.hook.LifecycleHookResult.empty())
                .register("SAME", "Deep Sleep", TurnPhase.END, 20, (context, ability) -> io.autoptu.core.hook.LifecycleHookResult.empty()));
    }

    private static BattleRoundController controllerAtAction(BattleRuntimeState state, int round) {
        LifecycleHookRegistry hooks = LifecycleHookRegistry.builder()
                .register(
                        "ability-phase",
                        HookSource.ABILITY,
                        LifecycleHookPoint.PHASE_CHANGE,
                        100,
                        new AbilityPhaseLifecycleHook(BuiltinAbilityPhaseEffects.lancerRegistry())
                )
                .build();
        BattleTurnState turn = new BattleTurnState();
        turn.setActiveTurn("actor", TurnPhase.ACTION);
        return new BattleRoundController(
                state,
                round,
                hooks,
                state.damageHistory(),
                new RoundInjuryHistoryState(),
                turn
        );
    }

    private static BattleRuntimeState state(List<String> abilities) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 4),
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
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor));
    }

    private static Map<String, Integer> fixture() throws IOException {
        String fixturePath = System.getProperty("autoptu.lancer.phase.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(fixturePath))) {
            if (line == null || line.isBlank() || line.startsWith("contract\t")) continue;
            String[] parts = line.split("\\t", 2);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
