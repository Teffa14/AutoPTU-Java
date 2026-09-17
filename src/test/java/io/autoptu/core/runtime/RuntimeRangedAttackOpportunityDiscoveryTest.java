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

class RuntimeRangedAttackOpportunityDiscoveryTest {
    private static ActionWindowHookRegistry registry() {
        return ActionWindowHookRegistry.builder()
                .register("aoo", HookSource.REACTION, Set.of(ActionWindow.AFTER_ACTION), 10,
                        context -> context.trigger() == ActionWindowTrigger.ADJACENT_FOE_USES_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET
                                ? List.of(new ActionWindowCandidate("reactor", "attack_of_opportunity", context.triggerKey()))
                                : List.of())
                .build();
    }

    @Test
    void opensAfterActionWindowForAdjacentRangedAttackWithoutAdjacentTarget() {
        ActionResolvedEvent occurrence = ActionResolvedEvent.targeted(
                "attacker", "ranged_attack", List.of("far-target"));

        List<ActionWindowCandidate> candidates = RuntimeRangedAttackOpportunityDiscovery.afterRangedAttack(
                occurrence,
                new RuntimeRangedAttackOpportunityDiscovery.ReactorGeometry(true, false),
                registry());

        assertEquals(List.of("reactor"), candidates.stream().map(ActionWindowCandidate::reactingCombatantId).toList());
        assertEquals(List.of(occurrence.stableKey()), candidates.stream().map(ActionWindowCandidate::triggerKey).toList());
    }

    @Test
    void doesNotOpenWindowWhenActorIsNotAdjacentToReactor() {
        ActionResolvedEvent occurrence = new ActionResolvedEvent("attacker", "ranged_attack");

        assertEquals(List.of(), RuntimeRangedAttackOpportunityDiscovery.afterRangedAttack(
                occurrence,
                new RuntimeRangedAttackOpportunityDiscovery.ReactorGeometry(false, false),
                registry()));
    }

    @Test
    void doesNotOpenWindowWhenRangedAttackTargetsAnyAdjacentCombatant() {
        ActionResolvedEvent occurrence = ActionResolvedEvent.targeted(
                "attacker", "ranged_attack", List.of("adjacent-target", "far-target"));

        assertEquals(List.of(), RuntimeRangedAttackOpportunityDiscovery.afterRangedAttack(
                occurrence,
                new RuntimeRangedAttackOpportunityDiscovery.ReactorGeometry(true, true),
                registry()));
    }
}
