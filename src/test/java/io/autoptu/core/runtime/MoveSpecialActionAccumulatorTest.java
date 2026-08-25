package io.autoptu.core.runtime;

import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.MoveSpecialPhase;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSpecialActionAccumulatorTest {
    @Test
    void startsWithPythonNoTargetDefaultsAndDispatchesOnce() {
        BattleRuntimeState state = state();
        MoveSpecialActionAccumulator accumulator = new MoveSpecialActionAccumulator();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("observe", MoveSpecialPhase.END_ACTION, ctx -> {
                    assertFalse(ctx.hit());
                    assertEquals(0, ctx.damageDealt());
                    assertEquals(false, ctx.result().get("hit"));
                    return List.of(new StatusSkipEvent("actor", "end", TurnPhase.ACTION, "end_action"));
                })
                .build();

        var result = accumulator.finish(registry, state, "actor", "test move", "status");

        assertEquals(Map.of("hit", false), accumulator.lastResultSnapshot());
        assertEquals(0, accumulator.totalDamageDealt());
        assertEquals(1, result.events().size());
        assertFalse(result.hitSnapshot());
    }

    @Test
    void keepsOnlyLastTargetResultAndSumsAppliedDamageAcrossTargets() {
        BattleRuntimeState state = state();
        MoveSpecialActionAccumulator accumulator = new MoveSpecialActionAccumulator();
        LinkedHashMap<String, Object> first = new LinkedHashMap<>();
        first.put("hit", true);
        first.put("damage", 4);
        first.put("marker", "first");
        LinkedHashMap<String, Object> second = new LinkedHashMap<>();
        second.put("hit", true);
        second.put("damage", 99);
        second.put("marker", "last");
        second.put("nullable", null);

        accumulator.recordTarget(first, 4);
        accumulator.recordTarget(second, 3);

        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("observe", MoveSpecialPhase.END_ACTION, ctx -> {
                    assertTrue(ctx.hit());
                    assertEquals(7, ctx.damageDealt());
                    assertEquals("last", ctx.result().get("marker"));
                    assertEquals(99, ((Number) ctx.result().get("damage")).intValue());
                    assertTrue(ctx.result().containsKey("nullable"));
                    return List.of();
                })
                .build();
        var result = accumulator.finish(registry, state, "actor", "test move", "physical");

        assertEquals(7, accumulator.totalDamageDealt());
        assertEquals("last", accumulator.lastResultSnapshot().get("marker"));
        assertTrue(result.hitSnapshot());
        assertEquals(7, result.totalDamageDealt());
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3), 50, 50, new ActionBudget());
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }
}
