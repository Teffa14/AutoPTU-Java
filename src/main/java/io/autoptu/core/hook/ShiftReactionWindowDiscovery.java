package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure discovery boundary between an authoritative Shift transition and reaction action windows.
 *
 * <p>The caller supplies reactors that are already eligible foes in authoritative reaction order.
 * This class preserves that order and applies only the Python-compatible footprint transition
 * detector. It does not decide teams, initiative, reaction availability, resource consumption,
 * targeting, RNG, or execution.</p>
 */
public final class ShiftReactionWindowDiscovery {
    private ShiftReactionWindowDiscovery() {
    }

    public static List<DiscoveredWindow> discover(
            List<Reactor> orderedEligibleReactors,
            String shiftedCombatantId,
            GridCoord shiftedBeforeAnchor,
            GridCoord shiftedAfterAnchor,
            String shiftedSize,
            String triggerKey
    ) {
        Objects.requireNonNull(orderedEligibleReactors, "ordered eligible reactors");
        Objects.requireNonNull(shiftedBeforeAnchor, "shifted before anchor");
        Objects.requireNonNull(shiftedAfterAnchor, "shifted after anchor");
        String normalizedShiftedCombatantId = requireText(shiftedCombatantId, "shifted combatant id");
        String normalizedShiftedSize = requireText(shiftedSize, "shifted size");
        String normalizedTriggerKey = requireText(triggerKey, "trigger key");

        ArrayList<DiscoveredWindow> discovered = new ArrayList<>();
        for (Reactor reactor : orderedEligibleReactors) {
            Objects.requireNonNull(reactor, "eligible reactor");
            ShiftAwayReactionTriggerDetector.detect(
                    reactor.anchor(),
                    reactor.size(),
                    shiftedBeforeAnchor,
                    shiftedAfterAnchor,
                    normalizedShiftedSize
            ).ifPresent(trigger -> discovered.add(new DiscoveredWindow(
                    reactor.combatantId(),
                    new ActionWindowContext(
                            ActionWindow.BEFORE_ACTION,
                            normalizedShiftedCombatantId,
                            normalizedTriggerKey,
                            trigger
                    )
            )));
        }
        return List.copyOf(discovered);
    }

    public record Reactor(String combatantId, GridCoord anchor, String size) {
        public Reactor {
            combatantId = requireText(combatantId, "reactor combatant id");
            anchor = Objects.requireNonNull(anchor, "reactor anchor");
            size = requireText(size, "reactor size");
        }
    }

    public record DiscoveredWindow(String reactingCombatantId, ActionWindowContext context) {
        public DiscoveredWindow {
            reactingCombatantId = requireText(reactingCombatantId, "reacting combatant id");
            context = Objects.requireNonNull(context, "action window context");
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.strip();
    }
}
