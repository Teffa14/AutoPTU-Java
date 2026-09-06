package io.autoptu.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Semantic event emitted when temporary borrowed moves expire at TURN_END.
 *
 * <p>The authoritative core supplies the normalized, ordered move identities. Adapters
 * render this snapshot and must not recompute which moves expired.</p>
 */
public record BorrowMoveEndedEvent(
        String combatantId,
        List<String> moves
) implements BattleEvent {
    public BorrowMoveEndedEvent {
        combatantId = combatantId == null ? "" : combatantId.strip();
        if (combatantId.isBlank()) throw new IllegalArgumentException("combatantId is required");
        Objects.requireNonNull(moves, "moves");
        ArrayList<String> copied = new ArrayList<>(moves.size());
        for (String move : moves) {
            String normalized = move == null ? "" : move.strip().toLowerCase(Locale.ROOT);
            if (normalized.isBlank()) throw new IllegalArgumentException("move identity is required");
            copied.add(normalized);
        }
        moves = List.copyOf(copied);
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.BORROW_MOVE_END;
    }

    @Override
    public String stableKey() {
        return kind().value() + "|" + combatantId + "|" + String.join(",", moves);
    }
}
