package io.autoptu.core.hook;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Pure semantic detector for the five Attack of Opportunity trigger families in the pinned oracle. */
public final class AttackOfOpportunityTriggerDetector {
    private static final Set<String> MANEUVERS = Set.of("push", "grapple", "disarm", "trip", "dirty trick");

    private AttackOfOpportunityTriggerDetector() {}

    public static Optional<ActionWindowTrigger> detect(Input input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        if (!input.adjacent()) return Optional.empty();

        return switch (input.kind()) {
            case MANEUVER -> MANEUVERS.contains(normalize(input.actionName())) && !input.targetsReactor()
                    ? Optional.of(ActionWindowTrigger.ADJACENT_FOE_USES_NON_TARGETING_MANEUVER)
                    : Optional.empty();
            case STAND_UP -> Optional.of(ActionWindowTrigger.ADJACENT_FOE_STANDS_UP);
            case RANGED_ATTACK -> !input.rangedTargetsAdjacentCombatant()
                    ? Optional.of(ActionWindowTrigger.ADJACENT_FOE_USES_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET)
                    : Optional.empty();
            case ITEM_RETRIEVAL -> input.standardAction()
                    ? Optional.of(ActionWindowTrigger.ADJACENT_FOE_RETRIEVES_ITEM)
                    : Optional.empty();
            case SHIFT -> input.shiftsAway()
                    ? Optional.of(ActionWindowTrigger.ADJACENT_FOE_SHIFTS_AWAY)
                    : Optional.empty();
            case OTHER -> Optional.empty();
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    public enum Kind {
        MANEUVER,
        STAND_UP,
        RANGED_ATTACK,
        ITEM_RETRIEVAL,
        SHIFT,
        OTHER
    }

    public record Input(
            boolean adjacent,
            Kind kind,
            String actionName,
            boolean targetsReactor,
            boolean rangedTargetsAdjacentCombatant,
            boolean standardAction,
            boolean shiftsAway
    ) {
        public Input {
            if (kind == null) throw new IllegalArgumentException("kind is required");
        }
    }
}
