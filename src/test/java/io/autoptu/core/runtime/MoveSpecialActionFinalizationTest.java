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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSpecialActionFinalizationTest {
    @Test
    void appendsOneEndActionAfterOrderedTargetEventsUsingLastResultAndTotalDamage() {
        BattleRuntimeState state = state();
        RuntimeMoveSpecialPostDamageApplication.Result first = targetResult("first", true, 4);
        RuntimeMoveSpecialPostDamageApplication.Result second = targetResult("second", false, 3);
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("end", MoveSpecialPhase.END_ACTION, ctx -> {
                    assertFalse(ctx.hit());
                    assertEquals(7, ctx.damageDealt());
                    assertEquals("second", ctx.result().get("marker"));
                    assertEquals(false, ctx.result().get("hit"));
                    return List.of(event("end_action"));
                })
                .build();

        MultiTargetAppliedActionResult result = MoveSpecialActionFinalization.finish(
                registry,
                state,
                "actor",
                "test move",
                "physical",
                List.of("enemy", "enemy-2"),
                List.of(first, second)
        );

        assertEquals(List.of("enemy", "enemy-2"), result.targetIds());
        assertEquals(3, result.events().size());
        assertEquals("target:first", ((StatusSkipEvent) result.events().get(0)).reason());
        assertEquals("target:second", ((StatusSkipEvent) result.events().get(1)).reason());
        assertEquals("end_action", ((StatusSkipEvent) result.events().get(2)).reason());
    }

    @Test
    void dispatchesEndActionWithPythonDefaultsWhenDeclarationHasNoTargets() {
        BattleRuntimeState state = state();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("end", MoveSpecialPhase.END_ACTION, ctx -> {
                    assertFalse(ctx.hit());
                    assertEquals(0, ctx.damageDealt());
                    assertEquals(false, ctx.result().get("hit"));
                    assertEquals(true, ctx.result().get("immutable_mind"));
                    return List.of(event("empty_end"));
                })
                .build();

        MultiTargetAppliedActionResult result = MoveSpecialActionFinalization.finish(
                registry, state, "actor", "test move", "status", List.of(), List.of());

        assertTrue(result.targetIds().isEmpty());
        assertEquals(1, result.events().size());
        assertEquals("empty_end", ((StatusSkipEvent) result.events().get(0)).reason());
    }

    @Test
    void rejectsMismatchedTargetAndResultCountsBeforeDispatch() {
        BattleRuntimeState state = state();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("should-not-run", MoveSpecialPhase.END_ACTION, ctx -> {
                    throw new AssertionError("END_ACTION must not run for invalid declaration state");
                })
                .build();

        assertThrows(IllegalArgumentException.class, () -> MoveSpecialActionFinalization.finish(
                registry,
                state,
                "actor",
                "test move",
                "physical",
                List.of("enemy"),
                List.of()
        ));
    }

    private static RuntimeMoveSpecialPostDamageApplication.Result targetResult(
            String marker,
            boolean hit,
            int damageDealt
    ) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hit", hit);
        snapshot.put("marker", marker);
        return new RuntimeMoveSpecialPostDamageApplication.Result(
                new AppliedActionResult(List.of(event("target:" + marker))),
                snapshot,
                damageDealt
        );
    }

    private static StatusSkipEvent event(String reason) {
        return new StatusSkipEvent("actor", "move-special", TurnPhase.ACTION, reason);
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3), 50, 50, new ActionBudget());
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        RuntimeCombatantState enemy2 = new RuntimeCombatantState(
                "enemy-2", MovementProfile.walking(new GridCoord(3, 1), 3), 50, 50, new ActionBudget());
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy, enemy2)
        );
    }
}
