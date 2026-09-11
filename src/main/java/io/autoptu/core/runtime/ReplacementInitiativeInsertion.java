package io.autoptu.core.runtime;

import io.autoptu.core.model.InitiativeEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server-authoritative mutation for inserting a replacement combatant into the current
 * initiative order without rebuilding the round.
 *
 * <p>The ordering contract is frozen from Python BattleState._insert_replacement_initiative.
 * This mutation preserves the currently acting combatant when a naturally earlier entry is
 * inserted and only grants the replacement the next slot when the caller explicitly allows
 * an immediate replacement turn.</p>
 */
public final class ReplacementInitiativeInsertion {
    private static final Comparator<InitiativeEntry> PYTHON_REPLACEMENT_ORDER =
            Comparator.comparingInt(InitiativeEntry::total).reversed()
                    .thenComparing(Comparator.comparingInt(InitiativeEntry::roll).reversed())
                    .thenComparing(Comparator.comparingInt(InitiativeEntry::speed).reversed())
                    .thenComparing(InitiativeEntry::actorId);

    private ReplacementInitiativeInsertion() {
    }

    public static Result apply(
            InitiativeProgressState progress,
            InitiativeEntry candidate,
            boolean allowImmediate
    ) {
        if (progress == null) {
            throw new IllegalArgumentException("initiative progress is required");
        }

        int cursorBefore = progress.cursor();
        if (candidate == null) {
            return new Result(Status.MISSING_ENTRY, -1, -1, cursorBefore, cursorBefore);
        }
        if (candidate.actorId() == null || candidate.actorId().isBlank()) {
            throw new IllegalArgumentException("replacement initiative actor id is required");
        }
        if (progress.actorIndex(candidate.actorId()) >= 0) {
            return new Result(Status.DUPLICATE, -1, -1, cursorBefore, cursorBefore);
        }
        if (!progress.hasDetailedOrder()) {
            throw new IllegalStateException(
                    "replacement initiative insertion requires the authoritative detailed order"
            );
        }

        List<InitiativeEntry> current = progress.orderedEntries();
        int naturalIndex = naturalInsertionIndex(current, candidate);
        int insertionIndex = naturalIndex;
        int cursorAfter = cursorBefore;

        if (cursorBefore >= 0 && naturalIndex <= cursorBefore) {
            if (allowImmediate) {
                insertionIndex = Math.min(cursorBefore + 1, current.size());
            } else {
                cursorAfter = cursorBefore + 1;
            }
        }

        ArrayList<InitiativeEntry> next = new ArrayList<>(current);
        next.add(insertionIndex, candidate);
        progress.replaceDetailedOrderFromLifecycle(next);
        progress.setCursorFromLifecycle(cursorAfter);

        return new Result(Status.INSERTED, naturalIndex, insertionIndex, cursorBefore, cursorAfter);
    }

    static int naturalInsertionIndex(List<InitiativeEntry> current, InitiativeEntry candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("replacement initiative entry is required");
        }
        List<InitiativeEntry> safeCurrent = current == null ? List.of() : current;
        for (int index = 0; index < safeCurrent.size(); index++) {
            if (PYTHON_REPLACEMENT_ORDER.compare(candidate, safeCurrent.get(index)) < 0) {
                return index;
            }
        }
        return safeCurrent.size();
    }

    public enum Status {
        INSERTED,
        DUPLICATE,
        MISSING_ENTRY
    }

    public record Result(
            Status status,
            int naturalIndex,
            int insertionIndex,
            int cursorBefore,
            int cursorAfter
    ) {
        public Result {
            if (status == null) throw new IllegalArgumentException("status is required");
            if (cursorBefore < -1 || cursorAfter < -1) {
                throw new IllegalArgumentException("initiative cursor cannot be less than -1");
            }
        }
    }
}
