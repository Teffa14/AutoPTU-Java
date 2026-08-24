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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSpecialPostDamageResolutionTest {
    @Test
    void preservesSharedResultAndCarriesAlreadyAppliedDamage() {
        BattleRuntimeState state = state();
        ArrayList<String> order = new ArrayList<>();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("global", MoveSpecialPhase.POST_DAMAGE, ctx -> {
                    order.add("global");
                    assertEquals(7, ctx.damageDealt());
                    assertTrue(ctx.hit());
                    assertEquals("from-pre", ctx.result().get("marker"));
                    assertEquals(11, ((Number) ctx.result().get("damage")).intValue());
                    return List.of(new StatusSkipEvent("actor", "global", TurnPhase.ACTION, "post_damage"));
                })
                .registerMove("specific", MoveSpecialPhase.POST_DAMAGE, List.of("Test Move"), ctx -> {
                    order.add("specific");
                    assertEquals(7, ctx.damageDealt());
                    assertTrue(ctx.hit());
                    ctx.result().put("damage", 11);
                    ctx.result().put("post_marker", "set");
                    return List.of(new StatusSkipEvent("actor", "specific", TurnPhase.ACTION, "post_damage"));
                })
                .build();

        LinkedHashMap<String, Object> priorResult = new LinkedHashMap<>();
        priorResult.put("hit", true);
        priorResult.put("crit", false);
        priorResult.put("damage", 7);
        priorResult.put("type_multiplier", 1.0d);
        priorResult.put("marker", "from-pre");

        MoveSpecialPostDamageResolution.Result result = MoveSpecialPostDamageResolution.resolve(
                registry, state, "actor", "enemy", " Test Move ", "physical",
                priorResult, true, 7);

        assertEquals(List.of("specific", "global"), order);
        assertEquals(2, result.events().size());
        assertEquals("specific", ((StatusSkipEvent) result.events().get(0)).status());
        assertEquals("global", ((StatusSkipEvent) result.events().get(1)).status());
        assertEquals(11, ((Number) result.resultSnapshot().get("damage")).intValue());
        assertEquals("from-pre", result.resultSnapshot().get("marker"));
        assertEquals("set", result.resultSnapshot().get("post_marker"));
    }

    @Test
    void shieldDustSuppressesNonStatusPostDamageDispatch() {
        BattleRuntimeState state = state();
        state.requireCombatant("enemy").setAbilities(List.of("Shield Dust"));
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("blocked", MoveSpecialPhase.POST_DAMAGE, ctx -> {
                    ctx.result().put("ran", true);
                    return List.of(new StatusSkipEvent("actor", "blocked", TurnPhase.ACTION, "post_damage"));
                })
                .build();

        MoveSpecialPostDamageResolution.Result result = MoveSpecialPostDamageResolution.resolve(
                registry, state, "actor", "enemy", "strike", "physical",
                Map.of("hit", true, "damage", 5), true, 5);

        assertTrue(result.events().isEmpty());
        assertFalse(result.resultSnapshot().containsKey("ran"));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3), 50, 50, new ActionBudget());
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget());
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }
}
