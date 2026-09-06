package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure round-window retention contract for histories keyed only by battle round.
 *
 * <p>The Python oracle uses this shape for Echoed Voice, Fusion Bolt, and Fusion Flare.
 * Content families should supply their retention window as data rather than reimplementing
 * round cutoffs independently.</p>
 */
public final class RoundWindowHistoryPruning {
    private RoundWindowHistoryPruning() {}

    /**
     * Retains round entries whose value is at least {@code currentRound - roundsBack}.
     * Input order and duplicate entries are preserved.
     */
    public static List<Integer> retain(List<Integer> rounds, int currentRound, int roundsBack) {
        Objects.requireNonNull(rounds, "rounds");
        if (currentRound < 0) {
            throw new IllegalArgumentException("currentRound must be non-negative");
        }
        if (roundsBack < 0) {
            throw new IllegalArgumentException("roundsBack must be non-negative");
        }

        int minimumRound = currentRound - roundsBack;
        List<Integer> retained = new ArrayList<>();
        for (Integer round : rounds) {
            if (round == null) {
                throw new IllegalArgumentException("round history entries must be non-null");
            }
            if (round >= minimumRound) {
                retained.add(round);
            }
        }
        return List.copyOf(retained);
    }
}
