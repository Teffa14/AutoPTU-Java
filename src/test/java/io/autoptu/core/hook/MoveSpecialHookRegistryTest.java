package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSpecialHookRegistryTest {
    @Test
    void postDamageRunsSpecificBeforeGlobalAndNormalizesMoveName() {
        ArrayList<String> order = new ArrayList<>();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("global", MoveSpecialPhase.POST_DAMAGE, ctx -> { order.add("global"); return List.of(); })
                .registerMove("specific", MoveSpecialPhase.POST_DAMAGE, List.of("  Growl  "), ctx -> { order.add("specific"); return List.of(); })
                .build();

        registry.dispatch(context(state(false), " GROWL ", "status", MoveSpecialPhase.POST_DAMAGE));
        assertEquals(List.of("specific", "global"), order);
    }

    @Test
    void nonPostDamageRunsGlobalBeforeSpecific() {
        ArrayList<String> order = new ArrayList<>();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("global", MoveSpecialPhase.PRE_DAMAGE, ctx -> { order.add("global"); return List.of(); })
                .registerMove("specific", MoveSpecialPhase.PRE_DAMAGE, List.of("growl"), ctx -> { order.add("specific"); return List.of(); })
                .build();

        registry.dispatch(context(state(false), "growl", "status", MoveSpecialPhase.PRE_DAMAGE));
        assertEquals(List.of("global", "specific"), order);
    }

    @Test
    void handlersShareMutableResultButHitRemainsDispatchStartSnapshot() {
        BattleRuntimeState state = state(false);
        MoveSpecialResultState result = new MoveSpecialResultState(Map.of("hit", false, "damage", 7));
        ArrayList<Boolean> observations = new ArrayList<>();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("mutate", MoveSpecialPhase.PRE_DAMAGE, ctx -> {
                    assertSame(result, ctx.result());
                    assertFalse(ctx.hit());
                    ctx.result().put("hit", true);
                    ctx.result().put("damage", 11);
                    return List.of();
                })
                .registerMove("observe", MoveSpecialPhase.PRE_DAMAGE, List.of("growl"), ctx -> {
                    observations.add(ctx.result().hit());
                    observations.add(ctx.hit());
                    return List.of();
                })
                .build();

        registry.dispatch(new MoveSpecialHookContext(
                state, "actor", "enemy", "growl", "status", result, MoveSpecialPhase.PRE_DAMAGE));

        assertEquals(List.of(true, false), observations);
        assertTrue(result.hit());
        assertEquals(11, result.get("damage"));
    }

    @Test
    void shieldDustSkipsDamagingPostDamageButNotStatus() {
        ArrayList<String> calls = new ArrayList<>();
        MoveSpecialHookRegistry registry = MoveSpecialHookRegistry.builder()
                .registerGlobal("global", MoveSpecialPhase.POST_DAMAGE, ctx -> { calls.add("called"); return List.of(); })
                .build();
        BattleRuntimeState state = state(true);

        assertTrue(registry.dispatch(context(state, "water-gun", "special", MoveSpecialPhase.POST_DAMAGE)).isEmpty());
        assertTrue(calls.isEmpty());
        registry.dispatch(context(state, "growl", "status", MoveSpecialPhase.POST_DAMAGE));
        assertEquals(List.of("called"), calls);
    }

    @Test
    void unknownPythonPhaseDefaultsToPostDamage() {
        assertEquals(MoveSpecialPhase.POST_DAMAGE, MoveSpecialPhase.fromPythonPhase("mystery"));
        assertEquals(MoveSpecialPhase.POST_DAMAGE, MoveSpecialPhase.fromPythonPhase(null));
    }

    private static MoveSpecialHookContext context(BattleRuntimeState state, String moveName, String category, MoveSpecialPhase phase) {
        return new MoveSpecialHookContext(state, "actor", "enemy", moveName, category, true, phase);
    }

    private static BattleRuntimeState state(boolean shieldDust) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 3), 50, 50, new ActionBudget());
        RuntimeCombatantState enemy = new RuntimeCombatantState(
                "enemy", MovementProfile.walking(new GridCoord(2, 1), 3), 50, 50, new ActionBudget(),
                null, null, 0, false, false, false, false,
                List.of(), List.of(), shieldDust ? List.of("Shield Dust") : List.of());
        return new BattleRuntimeState(new MovementGrid(6, 6, Set.of(), Map.of()), List.of(actor, enemy));
    }
}
