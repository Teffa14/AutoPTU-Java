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
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSpecialPreDamageResolutionTest {
    @Test
    void appliesMutableResultBeforeDefenderReactionBoundary() {
        BattleRuntimeState state = state();
        ArrayList<Boolean> hitSnapshots = new ArrayList<>();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("cancel", MoveSpecialPhase.PRE_DAMAGE, ctx -> {
                    hitSnapshots.add(ctx.hit());
                    ctx.result().put("hit", false);
                    ctx.result().put("damage", 0);
                    ctx.result().put("type_multiplier", 0.0d);
                    return List.of(new StatusSkipEvent("actor", "global", TurnPhase.ACTION, "pre_damage"));
                })
                .registerMove("specific", MoveSpecialPhase.PRE_DAMAGE, List.of("Test Move"), ctx -> {
                    hitSnapshots.add(ctx.hit());
                    assertFalse(ctx.result().hit());
                    ctx.result().put("crit", false);
                    return List.of(new StatusSkipEvent("actor", "specific", TurnPhase.ACTION, "pre_damage"));
                })
                .build();

        MoveSpecialPreDamageResolution.Result result = MoveSpecialPreDamageResolution.resolve(
                registry, state, "actor", "enemy", " Test Move ", "physical",
                true, true, 17, 2.0d);

        assertFalse(result.hit());
        assertFalse(result.crit());
        assertEquals(0, result.damage());
        assertEquals(0.0d, result.typeMultiplier());
        assertEquals(List.of(true, true), hitSnapshots);
        assertEquals(2, result.events().size());
        assertEquals("global", ((StatusSkipEvent) result.events().get(0)).status());
        assertEquals("specific", ((StatusSkipEvent) result.events().get(1)).status());
        assertEquals(false, result.resultSnapshot().get("hit"));
    }

    @Test
    void leavesOrdinaryResultUnchangedWhenRegistryIsEmpty() {
        MoveSpecialPreDamageResolution.Result result = MoveSpecialPreDamageResolution.resolve(
                MoveSpecialHookRegistry.builder().build(), state(), "actor", "enemy",
                "tackle", "physical", true, false, 9, 1.5d);

        assertTrue(result.hit());
        assertFalse(result.crit());
        assertEquals(9, result.damage());
        assertEquals(1.5d, result.typeMultiplier());
        assertTrue(result.events().isEmpty());
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3), 50, 50, new ActionBudget());
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }
}
