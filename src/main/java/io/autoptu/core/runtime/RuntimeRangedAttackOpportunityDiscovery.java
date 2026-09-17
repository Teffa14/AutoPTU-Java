package io.autoptu.core.runtime;

import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.hook.ActionWindowCandidate;
import io.autoptu.core.hook.ActionWindowHookRegistry;
import io.autoptu.core.hook.ActionWindowTrigger;
import io.autoptu.core.hook.AttackOfOpportunityTriggerDetector;

import java.util.List;
import java.util.Objects;

/**
 * Reactor-specific authoritative bridge for ranged Attack of Opportunity discovery.
 *
 * <p>The core derives adjacency from one authoritative geometry snapshot and the completed
 * action's target ids. The detector remains the single owner of PTU trigger semantics; this
 * class composes snapshot geometry, trigger classification and the generic AFTER_ACTION
 * registry boundary.</p>
 */
public final class RuntimeRangedAttackOpportunityDiscovery {
    private RuntimeRangedAttackOpportunityDiscovery() {
    }

    public static List<ActionWindowCandidate> afterRangedAttack(
            ActionResolvedEvent occurrence,
            String reactorId,
            AuthoritativeCombatGeometrySnapshot geometry,
            ActionWindowHookRegistry registry
    ) {
        Objects.requireNonNull(occurrence, "occurrence");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(registry, "registry");

        boolean actorAdjacentToReactor = geometry.adjacent(reactorId, occurrence.actorId());
        boolean rangedTargetsAdjacentCombatant = geometry.anyAdjacentTo(reactorId, occurrence.targetIds());

        return AttackOfOpportunityTriggerDetector.detect(new AttackOfOpportunityTriggerDetector.Input(
                        actorAdjacentToReactor,
                        AttackOfOpportunityTriggerDetector.Kind.RANGED_ATTACK,
                        occurrence.actionKey(),
                        false,
                        rangedTargetsAdjacentCombatant,
                        false,
                        false
                ))
                .filter(trigger -> trigger == ActionWindowTrigger.ADJACENT_FOE_USES_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET)
                .map(trigger -> RuntimeActionWindowDiscovery.afterAction(occurrence, trigger, registry))
                .orElseGet(List::of);
    }
}
