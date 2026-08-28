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

class PreResolutionTargetHookRegistryTest {
    @Test
    void orderedHooksComposeTargetReplacementAndEvents() {
        BattleRuntimeState state = state();
        ArrayList<String> visited = new ArrayList<>();
        PreResolutionTargetHookRegistry registry = PreResolutionTargetHookRegistry.builder()
                .register("later", HookSource.ABILITY, 20, (context, current) -> {
                    visited.add("later:" + current.targetId());
                    return current.replaceTarget("redirect-b", List.of(event("ability", "Redirect B", "redirect-b")));
                })
                .register("first", HookSource.REACTION, 10, (context, current) -> {
                    visited.add("first:" + current.targetId());
                    return current.replaceTarget("redirect-a", List.of(event("reaction", "Redirect A", "redirect-a")));
                })
                .build();

        PreResolutionTargetResult result = registry.resolve(new PreResolutionTargetContext(
                state, "attacker", "Tackle", "target", new GridCoord(3, 1)
        ));

        assertEquals(List.of("first:target", "later:redirect-a"), visited);
        assertEquals("redirect-b", result.targetId());
        assertEquals(List.of("Redirect A", "Redirect B"), result.events().stream()
                .map(event -> ((RuleEffectEvent) event).sourceName())
                .toList());
    }

    @Test
    void unknownReplacementCannotEscapeAuthoritativeBattleState() {
        PreResolutionTargetHookRegistry registry = PreResolutionTargetHookRegistry.builder()
                .register("bad", HookSource.SYSTEM, 0, (context, current) ->
                        current.replaceTarget("minecraft-only-id", List.of()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.resolve(new PreResolutionTargetContext(
                state(), "attacker", "Tackle", "target", new GridCoord(3, 1)
        )));
    }

    @Test
    void duplicateSourceAndIdRegistrationIsRejected() {
        PreResolutionTargetHookRegistry.Builder builder = PreResolutionTargetHookRegistry.builder()
                .register("redirect", HookSource.ABILITY, 0, (context, current) -> current);

        assertThrows(IllegalArgumentException.class, () -> builder.register(
                "redirect", HookSource.ABILITY, 1, (context, current) -> current
        ));
    }

    private static RuleEffectEvent event(String sourceKind, String sourceName, String targetId) {
        return new RuleEffectEvent(sourceKind, sourceName, "attacker", targetId, "Tackle", "target_redirect", 0.0, 20);
    }

    private static BattleRuntimeState state() {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(
                        combatant("attacker", 0, 1),
                        combatant("target", 3, 1),
                        combatant("redirect-a", 2, 1),
                        combatant("redirect-b", 1, 1)
                ),
                123L
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                new ActionBudget()
        );
    }
}
