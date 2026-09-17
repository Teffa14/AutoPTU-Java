package io.autoptu.core.runtime;

import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.hook.ActionWindow;
import io.autoptu.core.hook.ActionWindowCandidate;
import io.autoptu.core.hook.ActionWindowHookRegistry;
import io.autoptu.core.hook.ActionWindowTrigger;
import io.autoptu.core.hook.HookSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeActionWindowDiscoveryTest {
    @Test
    void projectsCompletedRangedOccurrenceIntoOrderedAfterActionCandidates() {
        ActionResolvedEvent occurrence = ActionResolvedEvent.targeted(
                "attacker",
                "ranged_attack",
                List.of("target-a", "target-b")
        );
        ActionWindowHookRegistry registry = ActionWindowHookRegistry.builder()
                .register("late", HookSource.REACTION, Set.of(ActionWindow.AFTER_ACTION), 20,
                        context -> List.of(new ActionWindowCandidate(
                                "reactor-b", "attack_of_opportunity", context.triggerKey())))
                .register("early", HookSource.REACTION, Set.of(ActionWindow.AFTER_ACTION), 10,
                        context -> {
                            assertEquals("attacker", context.actingCombatantId());
                            assertEquals(occurrence.stableKey(), context.triggerKey());
                            assertEquals(ActionWindowTrigger.ADJACENT_FOE_USES_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET,
                                    context.trigger());
                            return List.of(new ActionWindowCandidate(
                                    "reactor-a", "attack_of_opportunity", context.triggerKey()));
                        })
                .register("wrong-window", HookSource.SYSTEM, Set.of(ActionWindow.BEFORE_ACTION), 1,
                        context -> List.of(new ActionWindowCandidate(
                                "ignored", "wrong", context.triggerKey())))
                .build();

        List<ActionWindowCandidate> candidates = RuntimeActionWindowDiscovery.afterAction(
                occurrence,
                ActionWindowTrigger.ADJACENT_FOE_USES_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET,
                registry
        );

        assertEquals(List.of("reactor-a", "reactor-b"),
                candidates.stream().map(ActionWindowCandidate::reactingCombatantId).toList());
        assertEquals(List.of(occurrence.stableKey(), occurrence.stableKey()),
                candidates.stream().map(ActionWindowCandidate::triggerKey).toList());
    }

    @Test
    void requiresAClassifiedTriggerBeforeOpeningAWindow() {
        ActionWindowHookRegistry registry = ActionWindowHookRegistry.builder().build();
        ActionResolvedEvent occurrence = new ActionResolvedEvent("attacker", "ranged_attack");

        assertThrows(IllegalArgumentException.class,
                () -> RuntimeActionWindowDiscovery.afterAction(
                        occurrence,
                        ActionWindowTrigger.UNSPECIFIED,
                        registry));
    }
}
