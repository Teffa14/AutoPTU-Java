package io.autoptu.core.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Declarative action-window hook that exposes reaction-only moves without executing them.
 *
 * <p>Each move declares the windows and, when known, semantic triggers where it may be discovered.
 * Classification remains aligned with the pinned Python oracle through
 * {@link ReactionOnlyMoveClassifier}. This hook performs no targeting, resource consumption, RNG,
 * or battle-state mutation.</p>
 */
public final class ReactionOnlyMoveHook implements ActionWindowHook {
    private final String reactingCombatantId;
    private final List<MoveSpec> moves;

    public ReactionOnlyMoveHook(String reactingCombatantId, List<MoveSpec> moves) {
        if (reactingCombatantId == null || reactingCombatantId.isBlank()) {
            throw new IllegalArgumentException("reacting combatant id is required");
        }
        this.reactingCombatantId = reactingCombatantId.strip();
        this.moves = List.copyOf(Objects.requireNonNull(moves, "moves"));
    }

    @Override
    public List<ActionWindowCandidate> candidates(ActionWindowContext context) {
        Objects.requireNonNull(context, "context");
        ArrayList<ActionWindowCandidate> result = new ArrayList<>();
        for (MoveSpec move : moves) {
            Objects.requireNonNull(move, "reaction move spec");
            if (!move.windows().contains(context.window())) continue;
            if (!move.triggers().isEmpty() && !move.triggers().contains(context.trigger())) continue;
            if (!ReactionOnlyMoveClassifier.isReactionOnly(
                    move.activation(),
                    move.rangeText(),
                    move.effectsText(),
                    move.canonicalRangeText(),
                    move.canonicalEffectsText())) {
                continue;
            }
            result.add(new ActionWindowCandidate(
                    reactingCombatantId,
                    move.actionKey(),
                    context.triggerKey()));
        }
        return List.copyOf(result);
    }

    /** Minimal language-neutral projection needed to discover one reaction-only move. */
    public record MoveSpec(
            String actionKey,
            Set<ActionWindow> windows,
            Set<ActionWindowTrigger> triggers,
            String activation,
            String rangeText,
            String effectsText,
            String canonicalRangeText,
            String canonicalEffectsText
    ) {
        public MoveSpec(
                String actionKey,
                Set<ActionWindow> windows,
                String activation,
                String rangeText,
                String effectsText,
                String canonicalRangeText,
                String canonicalEffectsText
        ) {
            this(actionKey, windows, Set.of(), activation, rangeText, effectsText,
                    canonicalRangeText, canonicalEffectsText);
        }

        public MoveSpec {
            if (actionKey == null || actionKey.isBlank()) {
                throw new IllegalArgumentException("action key is required");
            }
            actionKey = actionKey.strip();
            windows = Set.copyOf(Objects.requireNonNull(windows, "windows"));
            if (windows.isEmpty()) {
                throw new IllegalArgumentException("at least one action window is required");
            }
            triggers = Set.copyOf(Objects.requireNonNull(triggers, "triggers"));
            if (triggers.contains(ActionWindowTrigger.UNSPECIFIED)) {
                throw new IllegalArgumentException("UNSPECIFIED cannot be a required trigger");
            }
        }
    }
}
