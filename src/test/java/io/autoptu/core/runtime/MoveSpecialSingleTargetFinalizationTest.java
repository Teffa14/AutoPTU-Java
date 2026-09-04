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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSpecialSingleTargetFinalizationTest {
    @Test
    void appendsEndActionAfterTargetEventsUsingTargetSnapshotAndAppliedDamage() {
        BattleRuntimeState state = state();
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hit", true);
        snapshot.put("marker", "post_damage");
        MoveSpecialTargetResult targetResult = new MoveSpecialTargetResult(
                new AppliedActionResult(List.of(event("post_damage"))), snapshot, 9);

        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("end", MoveSpecialPhase.END_ACTION, ctx -> {
                    assertTrue(ctx.hit());
                    assertEquals(9, ctx.damageDealt());
                    assertEquals("post_damage", ctx.result().get("marker"));
                    return List.of(event("end_action"));
                })
                .build();

        AppliedActionResult result = MoveSpecialActionFinalization.finishSingleTarget(
                registry,
                state,
                "actor",
                "test move",
                "physical",
                "enemy",
                targetResult
        );

        assertEquals(2, result.events().size());
        assertEquals("post_damage", ((StatusSkipEvent) result.events().get(0)).reason());
        assertEquals("end_action", ((StatusSkipEvent) result.events().get(1)).reason());
        assertEquals(50, state.requireCombatant("enemy").hp());
    }

    private static StatusSkipEvent event(String reason) {
        return new StatusSkipEvent("actor", "move-special", TurnPhase.ACTION, reason);
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3), 50, 50, new ActionBudget());
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor, enemy)
        );
    }
}
