package io.autoptu.core.rules;

import io.autoptu.core.model.TurnPhase;

import java.util.List;

/** Pure phase sequence extracted from Python battle_state._PHASE_SEQUENCE. */
public final class PhaseSequence {
    public static final List<TurnPhase> ORDER = List.of(
            TurnPhase.START,
            TurnPhase.COMMAND,
            TurnPhase.ACTION,
            TurnPhase.END
    );

    private PhaseSequence() {
    }

    /** Match PhaseController.advance_phase: unknown/null starts from index zero, END stays END. */
    public static TurnPhase next(TurnPhase current) {
        int index = current == null ? 0 : ORDER.indexOf(current);
        if (index < 0) {
            index = 0;
        }
        if (index >= ORDER.size() - 1) {
            return ORDER.get(index);
        }
        return ORDER.get(index + 1);
    }
}
