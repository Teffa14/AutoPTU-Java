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
 * <p>The caller supplies geometry facts computed by the core snapshot: whether the acting foe
 * is adjacent to this reactor and whether the completed ranged attack targeted any adjacent
 * combatant. The detector remains the single owner of PTU trigger semantics; this class only
 * composes that classification with the generic AFTER_ACTION registry boundary.</p>
 */
public final class RuntimeRangedAttackOpportunityDiscovery {
    private RuntimeRangedAttackOpportunityDiscovery() {
    }

    public static List<ActionWindowCandidate> afterRangedAttack(
            ActionResolvedEvent occurrence,
            ReactorGeometry geometry,
            ActionWindowHookRegistry registry
    ) {
        Objects.requireNonNull(occurrence, "occurrence");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(registry, "registry");

        return AttackOfOpportunityTriggerDetector.detect(new AttackOfOpportunityTriggerDetector.Input(
                        geometry.actorAdjacentToReactor(),
                        AttackOfOpportunityTriggerDetector.Kind.RANGED_ATTACK,
                        occurrence.actionKey(),
                        false,
                        geometry.rangedTargetsAdjacentCombatant(),
                        false,
                        false
                ))
                .filter(trigger -> trigger == ActionWindowTrigger.ADJACENT_FOE_USES_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET)
                .map(trigger -> RuntimeActionWindowDiscovery.afterAction(occurrence, trigger, registry))
                .orElseGet(List::of);
    }

    /** Language-neutral geometry facts frozen from the authoritative battle snapshot. */
    public record ReactorGeometry(
            boolean actorAdjacentToReactor,
            boolean rangedTargetsAdjacentCombatant
    ) {
    }
}
