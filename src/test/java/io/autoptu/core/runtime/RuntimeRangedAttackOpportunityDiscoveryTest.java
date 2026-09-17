package io.autoptu.core.runtime;

import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.hook.ActionWindow;
import io.autoptu.core.hook.ActionWindowCandidate;
import io.autoptu.core.hook.ActionWindowHookRegistry;
import io.autoptu.core.hook.ActionWindowTrigger;
import io.autoptu.core.hook.HookSource;
import io.autoptu.core.model.GridCoord;
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

    private static AuthoritativeCombatantGeometry combatant(String id, int x, int y, String size) {
        return new AuthoritativeCombatantGeometry(id, new GridCoord(x, y), size);
    }

    @Test
    void opensAfterActionWindowFromAuthoritativeFootprints() {
        ActionResolvedEvent occurrence = ActionResolvedEvent.targeted(
                "attacker", "ranged_attack", List.of("far-target"));
        AuthoritativeCombatGeometrySnapshot geometry = new AuthoritativeCombatGeometrySnapshot(List.of(
                combatant("reactor", 0, 0, "Medium"),
                combatant("attacker", 1, 0, "Medium"),
                combatant("far-target", 5, 0, "Medium")
        ));

        List<ActionWindowCandidate> candidates = RuntimeRangedAttackOpportunityDiscovery.afterRangedAttack(
                occurrence, "reactor", geometry, registry());

        assertEquals(List.of("reactor"), candidates.stream().map(ActionWindowCandidate::reactingCombatantId).toList());
        assertEquals(List.of(occurrence.stableKey()), candidates.stream().map(ActionWindowCandidate::triggerKey).toList());
    }

    @Test
    void largeActorFootprintCanBeAdjacentWithoutAdjacentAnchors() {
        ActionResolvedEvent occurrence = ActionResolvedEvent.targeted(
                "attacker", "ranged_attack", List.of("far-target"));
        AuthoritativeCombatGeometrySnapshot geometry = new AuthoritativeCombatGeometrySnapshot(List.of(
                combatant("reactor", 0, 0, "Medium"),
                combatant("attacker", 2, 0, "Large"),
                combatant("far-target", 6, 0, "Medium")
        ));

        assertEquals(1, RuntimeRangedAttackOpportunityDiscovery.afterRangedAttack(
                occurrence, "reactor", geometry, registry()).size());
    }

    @Test
    void doesNotOpenWindowWhenActorFootprintIsNotAdjacentToReactor() {
        ActionResolvedEvent occurrence = new ActionResolvedEvent("attacker", "ranged_attack");
        AuthoritativeCombatGeometrySnapshot geometry = new AuthoritativeCombatGeometrySnapshot(List.of(
                combatant("reactor", 0, 0, "Medium"),
                combatant("attacker", 3, 0, "Medium")
        ));

        assertEquals(List.of(), RuntimeRangedAttackOpportunityDiscovery.afterRangedAttack(
                occurrence, "reactor", geometry, registry()));
    }

    @Test
    void doesNotOpenWindowWhenAnyResolvedTargetFootprintIsAdjacent() {
        ActionResolvedEvent occurrence = ActionResolvedEvent.targeted(
                "attacker", "ranged_attack", List.of("large-adjacent-target", "far-target"));
        AuthoritativeCombatGeometrySnapshot geometry = new AuthoritativeCombatGeometrySnapshot(List.of(
                combatant("reactor", 0, 0, "Medium"),
                combatant("attacker", 1, 0, "Medium"),
                combatant("large-adjacent-target", 2, 2, "Large"),
                combatant("far-target", 6, 0, "Medium")
        ));

        assertEquals(List.of(), RuntimeRangedAttackOpportunityDiscovery.afterRangedAttack(
                occurrence, "reactor", geometry, registry()));
    }
}
