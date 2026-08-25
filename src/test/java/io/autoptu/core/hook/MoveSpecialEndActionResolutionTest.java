package io.autoptu.core.hook;

import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSpecialEndActionResolutionTest {
    @Test
    void dispatchesOnceWithNoDefenderLastResultAndTotalDamage() {
        BattleRuntimeState state = state();
        ArrayList<String> order = new ArrayList<>();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("global", MoveSpecialPhase.END_ACTION, ctx -> {
                    order.add("global");
                    assertNull(ctx.defender());
                    assertEquals(12, ctx.damageDealt());
                    assertTrue(ctx.hit());
                    assertEquals("last-target", ctx.result().get("marker"));
                    assertEquals("specific-set", ctx.result().get("end_marker"));
                    return List.of(new StatusSkipEvent("actor", "global", TurnPhase.ACTION, "end_action"));
                })
                .registerMove("specific", MoveSpecialPhase.END_ACTION, List.of("Test Move"), ctx -> {
                    order.add("specific");
                    assertNull(ctx.defender());
                    assertEquals(12, ctx.damageDealt());
                    assertTrue(ctx.hit());
                    assertEquals("last-target", ctx.result().get("marker"));
                    ctx.result().put("end_marker", "specific-set");
                    return List.of(new StatusSkipEvent("actor", "specific", TurnPhase.ACTION, "end_action"));
                })
                .build();

        LinkedHashMap<String, Object> lastResult = new LinkedHashMap<>();
        lastResult.put("hit", true);
        lastResult.put("crit", false);
        lastResult.put("damage", 5);
        lastResult.put("marker", "last-target");

        MoveSpecialEndActionResolution.Result result = MoveSpecialEndActionResolution.resolve(
                registry, state, "actor", " Test Move ", "physical", lastResult, 12);

        assertEquals(List.of("global", "specific"), order);
        assertEquals(2, result.events().size());
        assertEquals(12, result.totalDamageDealt());
        assertTrue(result.hitSnapshot());
        assertEquals("last-target", result.resultSnapshot().get("marker"));
        assertEquals("specific-set", result.resultSnapshot().get("end_marker"));
        assertEquals(5, ((Number) result.resultSnapshot().get("damage")).intValue());
    }

    @Test
    void snapshotsHitFromLastResultBeforeHandlersMutateIt() {
        BattleRuntimeState state = state();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("mutate", MoveSpecialPhase.END_ACTION, ctx -> {
                    assertEquals(false, ctx.hit());
                    ctx.result().put("hit", true);
                    return List.of();
                })
                .registerMove("observe", MoveSpecialPhase.END_ACTION, List.of("test move"), ctx -> {
                    assertEquals(false, ctx.hit());
                    assertEquals(true, ctx.result().get("hit"));
                    return List.of();
                })
                .build();

        MoveSpecialEndActionResolution.Result result = MoveSpecialEndActionResolution.resolve(
                registry, state, "actor", "test move", "status", Map.of("hit", false), 0);

        assertEquals(false, result.hitSnapshot());
        assertEquals(true, result.resultSnapshot().get("hit"));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3), 50, 50, new ActionBudget());
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }
}
