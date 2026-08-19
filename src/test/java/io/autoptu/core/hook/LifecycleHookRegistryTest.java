package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifecycleHookRegistryTest {
    @Test
    void resolvesOnlyRequestedPointInStableExplicitOrder() {
        ArrayList<String> calls = new ArrayList<>();
        LifecycleHookRegistry registry = LifecycleHookRegistry.builder()
                .register("later", HookSource.ABILITY, LifecycleHookPoint.ROUND_START, 20, context -> {
                    calls.add("later");
                    return LifecycleHookResult.empty();
                })
                .register("first-a", HookSource.STATUS, LifecycleHookPoint.ROUND_START, 10, context -> {
                    calls.add("first-a");
                    return LifecycleHookResult.empty();
                })
                .register("turn-only", HookSource.ITEM, LifecycleHookPoint.TURN_END, 1, context -> {
                    calls.add("turn-only");
                    return LifecycleHookResult.empty();
                })
                .register("first-b", HookSource.TERRAIN, LifecycleHookPoint.ROUND_START, 10, context -> {
                    calls.add("first-b");
                    return LifecycleHookResult.events(List.of(new RuleEffectEvent(
                            "terrain", "test terrain", "actor", "", "", "round_tick", 1, 20
                    )));
                })
                .build();

        LifecycleHookResult result = registry.resolve(
                LifecycleHookPoint.ROUND_START,
                new LifecycleHookContext(state(), LifecycleHookPoint.ROUND_START, 2, 3, "")
        );

        assertEquals(List.of("first-a", "first-b", "later"), calls);
        assertEquals(1, result.events().size());
        assertEquals("round_tick", ((RuleEffectEvent) result.events().getFirst()).effect());
    }

    @Test
    void rejectsDuplicateRegistrationWithinSamePointAndSource() {
        LifecycleHookRegistry.Builder builder = LifecycleHookRegistry.builder()
                .register("same", HookSource.STATUS, LifecycleHookPoint.ROUND_START, 1,
                        context -> LifecycleHookResult.empty());

        assertThrows(IllegalArgumentException.class, () -> builder.register(
                "same", HookSource.STATUS, LifecycleHookPoint.ROUND_START, 2,
                context -> LifecycleHookResult.empty()
        ));
    }

    @Test
    void rejectsMismatchedContextPoint() {
        LifecycleHookRegistry registry = LifecycleHookRegistry.builder().build();
        LifecycleHookContext context = new LifecycleHookContext(
                state(), LifecycleHookPoint.TURN_END, 2, 2, "actor"
        );

        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(LifecycleHookPoint.ROUND_START, context));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor)
        );
    }
}
