package io.autoptu.core.runtime;

import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.event.BattleEventOccurrence;

import java.util.Objects;

/**
 * Server-authoritative transition boundary for non-move combat actions that can open reaction windows.
 *
 * <p>The transition mutates core battle state first and only then emits the semantic occurrence used by
 * reaction discovery. Minecraft/Cobblemon/Craftics consume the result; they do not decide whether the
 * transition is legal or synthesize reaction triggers.</p>
 */
public final class RuntimeActionTransition {
    public static final String PRONE_STATUS = "prone";

    private final BattleRuntimeState battleState;

    public RuntimeActionTransition(BattleRuntimeState battleState) {
        this.battleState = Objects.requireNonNull(battleState, "battleState");
    }

    /**
     * Resolves standing up from Prone and emits the canonical stand_up occurrence.
     *
     * <p>Occurrence sequencing remains owned by the caller/battle event stream so this pure transition
     * does not introduce a second event counter.</p>
     */
    public Result standUp(String actorId, long occurrenceSequence) {
        battleState.requireCombatant(actorId);
        if (!battleState.hasStatus(actorId, PRONE_STATUS)) {
            throw new IllegalStateException("stand up requires prone status: " + actorId);
        }
        if (!battleState.removeStatus(actorId, PRONE_STATUS)) {
            throw new IllegalStateException("failed to remove prone status: " + actorId);
        }
        BattleEventOccurrence occurrence = new BattleEventOccurrence(
                occurrenceSequence,
                new ActionResolvedEvent(actorId, "stand_up")
        );
        return new Result(occurrence);
    }

    public record Result(BattleEventOccurrence occurrence) {
        public Result {
            occurrence = Objects.requireNonNull(occurrence, "occurrence");
        }
    }
}
